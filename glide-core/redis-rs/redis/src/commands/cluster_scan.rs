//! This module implements cluster-wide scanning operations for clusters.
//!
//! # Overview
//!
//! The module provides functionality to scan keys across all nodes in a cluster,
//! handling topology changes, failovers, and partial cluster coverage scenarios.
//! It maintains state between scan iterations and ensures consistent scanning even
//! when cluster topology changes.
//!
//! # Key Components
//!
//! - [`ClusterScanArgs`]: Configuration for scan operations including filtering and behavior options
//! - [`ScanStateRC`]: Thread-safe reference-counted wrapper for scan state management
//! - [`ScanState`]: Internal state tracking for cluster-wide scanning progress
//! - [`ObjectType`]: Supported data types for filtering scan results
//!
//! # Key Features
//!
//! - Resumable scanning across cluster nodes
//! - Automatic handling of cluster topology changes
//! - Support for all regular SCAN options
//! - Resilient to node failures and resharding
//!
//! # Implementation Details
//!
//! The scanning process is implemented using a bitmap to track scanned slots and
//! maintains epoch information to handle topology changes. The implementation:
//!
//! - Uses a 64-bit aligned bitmap for efficient slot tracking
//! - Maintains cursor position per node
//! - Handles partial cluster coverage scenarios
//! - Provides automatic recovery from node failures
//! - Ensures consistent scanning across topology changes
//!
//! # Error Handling
//!
//! The module handles various error scenarios including:
//! - Node failures during scanning
//! - Cluster topology changes
//! - Network connectivity issues
//! - Invalid routing scenarios

use crate::aio::ConnectionLike;
use crate::cluster_async::{ClusterConnInner, Connect, InnerCore, RefreshPolicy, MUTEX_READ_ERR};
use crate::cluster_routing::SlotAddr;
use crate::cluster_topology::SLOT_SIZE;
use crate::{cmd, from_redis_value, ErrorKind, RedisError, RedisResult, Value};
use std::sync::Arc;
use strum_macros::{Display, EnumString};

const BITS_PER_U64: u16 = u64::BITS as u16;
const NUM_OF_SLOTS: u16 = SLOT_SIZE;
const BITS_ARRAY_SIZE: u16 = NUM_OF_SLOTS / BITS_PER_U64;
const END_OF_SCAN: u16 = NUM_OF_SLOTS;
type SlotsBitsArray = [u64; BITS_ARRAY_SIZE as usize];

/// Holds configuration for a cluster scan operation.
///
/// # Fields
/// - `scan_state_cursor`: Internal state tracking scan progress
/// - `match_pattern`: Optional pattern to filter keys
/// - `count`: Optional limit on number of keys returned per iteration
/// - `object_type`: Optional filter for specific data types
/// - `allow_non_covered_slots`: Whether to continue if some slots are uncovered
///
/// See examples below for usage with the builder pattern.
/// # Examples
///
/// Create a new `ClusterScanArgs` instance using the builder pattern:
///
/// ```rust,no_run
/// use redis::ClusterScanArgs;
/// use redis::ObjectType;
///
/// // Create basic scan args with defaults
/// let basic_scan = ClusterScanArgs::builder().build();
///
/// // Create scan args with custom options
/// let custom_scan = ClusterScanArgs::builder()
///     .with_match_pattern("user:*")      // Match keys starting with "user:"
///     .with_count(100)                   // Return 100 keys per iteration
///     .with_object_type(ObjectType::Hash) // Only scan hash objects
///     .allow_non_covered_slots(true)     // Continue scanning even if some slots aren't covered
///     .build();
///
/// // The builder can be used to create multiple configurations
/// let another_scan = ClusterScanArgs::builder()
///     .with_match_pattern("session:*")
///     .with_object_type(ObjectType::String)
///     .build();
/// ```

#[derive(Clone, Default)]
pub struct ClusterScanArgs {
    /// Reference-counted scan state cursor, managed internally.
    pub scan_state_cursor: ScanStateRC,

    /// Optional pattern to match keys during the scan.
    pub match_pattern: Option<Vec<u8>>,

    /// A "hint" to the cluster of how much keys to return per scan iteration, if none is sent to the server, the default value is 10.
    pub count: Option<u32>,

    /// Optional filter to include only keys of a specific data type.
    pub object_type: Option<ObjectType>,

    /// Flag indicating whether to allow scanning when there are slots not covered by the cluster, by default it is set to false and the scan will stop if some slots are not covered.
    pub allow_non_covered_slots: bool,
}

impl ClusterScanArgs {
    /// Creates a new [`ClusterScanArgsBuilder`] instance.
    ///
    /// # Returns
    ///
    /// A [`ClusterScanArgsBuilder`] instance for configuring cluster scan arguments.
    pub fn builder() -> ClusterScanArgsBuilder {
        ClusterScanArgsBuilder::default()
    }
    pub(crate) fn set_scan_state_cursor(&mut self, scan_state_cursor: ScanStateRC) {
        self.scan_state_cursor = scan_state_cursor;
    }
}

#[derive(Default)]
/// Builder pattern for creating cluster scan arguments.
///
/// This struct allows configuring various parameters for scanning keys in a cluster:
/// * Pattern matching for key filtering
/// * Count limit for returned keys
/// * Filtering by object type
/// * Control over scanning non-covered slots
///
/// # Example
/// ```
/// use redis::{ClusterScanArgs, ObjectType};
///
/// let args = ClusterScanArgs::builder()
///     .with_match_pattern(b"user:*")
///     .with_count(100)
///     .with_object_type(ObjectType::Hash)
///     .build();
/// ```
pub struct ClusterScanArgsBuilder {
    /// By default, the match pattern is set to `None` and no filtering is applied.
    match_pattern: Option<Vec<u8>>,
    /// A "hint" to the cluster of how much keys to return per scan iteration, by default none is sent to the server, the default value is 10.
    count: Option<u32>,
    /// By default, the object type is set to `None` and no filtering is applied.
    object_type: Option<ObjectType>,
    /// By default, the flag to allow scanning non-covered slots is set to `false`, meaning scanning will stop if some slots are not covered.
    allow_non_covered_slots: Option<bool>,
}

impl ClusterScanArgsBuilder {
    /// Sets the match pattern for the scan operation.
    ///
    /// # Arguments
    ///
    /// * `pattern` - The pattern to match keys against.
    ///
    /// # Returns
    ///
    /// The updated [`ClusterScanArgsBuilder`] instance.
    pub fn with_match_pattern<T: Into<Vec<u8>>>(mut self, pattern: T) -> Self {
        self.match_pattern = Some(pattern.into());
        self
    }

    /// Sets the count for the scan operation.
    ///
    /// # Arguments
    ///
    /// * `count` - A "hint" to the cluster of how much keys to return per scan iteration.
    ///
    /// The actual number of keys returned may be less or more than the count specified.
    ///
    /// 4,294,967,295 is the maximum keys possible in a cluster, so higher values will be capped.
    /// Hence the count is represented as a `u32` instead of `usize`.
    ///
    /// The default value is 10, if nothing is sent to the server, meaning nothing set in the builder.
    ///
    /// # Returns
    ///
    /// The updated [`ClusterScanArgsBuilder`] instance.
    pub fn with_count(mut self, count: u32) -> Self {
        self.count = Some(count);
        self
    }

    /// Sets the object type for the scan operation.
    ///
    /// # Arguments
    ///
    /// * `object_type` - The type of object to filter keys by.
    ///
    /// See [`ObjectType`] for supported data types.
    ///
    /// # Returns
    ///
    /// The updated [`ClusterScanArgsBuilder`] instance.
    pub fn with_object_type(mut self, object_type: ObjectType) -> Self {
        self.object_type = Some(object_type);
        self
    }

    /// Sets the flag to allow scanning non-covered slots.
    ///
    /// # Arguments
    ///
    /// * `allow` - A boolean flag indicating whether to allow scanning non-covered slots.
    ///
    /// # Returns
    ///
    /// The updated [`ClusterScanArgsBuilder`] instance.
    pub fn allow_non_covered_slots(mut self, allow: bool) -> Self {
        self.allow_non_covered_slots = Some(allow);
        self
    }

    /// Builds the [`ClusterScanArgs`] instance with the provided configuration.
    ///
    /// # Returns
    ///
    /// A [`ClusterScanArgs`] instance with the configured options.
    pub fn build(self) -> ClusterScanArgs {
        ClusterScanArgs {
            scan_state_cursor: ScanStateRC::new(),
            match_pattern: self.match_pattern,
            count: self.count,
            object_type: self.object_type,
            allow_non_covered_slots: self.allow_non_covered_slots.unwrap_or(false),
        }
    }
}

/// Represents the type of an object used to filter keys by data type.
///
/// This enum is used with the `TYPE` option in the `SCAN` command to
/// filter keys by their data type.
#[derive(Debug, Clone, Display, PartialEq, EnumString)]
pub enum ObjectType {
    /// String data type.
    String,
    /// List data type.
    List,
    /// Set data type.
    Set,
    /// Sorted set data type.
    ZSet,
    /// Hash data type.
    Hash,
    /// Stream data type.
    Stream,
}

impl From<String> for ObjectType {
    fn from(s: String) -> Self {
        match s.to_lowercase().as_str() {
            "string" => ObjectType::String,
            "list" => ObjectType::List,
            "set" => ObjectType::Set,
            "zset" => ObjectType::ZSet,
            "hash" => ObjectType::Hash,
            "stream" => ObjectType::Stream,
            _ => ObjectType::String,
        }
    }
}

#[derive(PartialEq, Debug, Clone, Default)]
pub enum ScanStateStage {
    #[default]
    Initiating,
    InProgress,
    Finished,
}

/// Wrapper struct for managing the state of a cluster scan operation.
///
/// This struct holds an `Arc` to the actual scan state and a status indicating
/// whether the scan is initiating, in progress, or finished.
#[derive(Debug, Clone, Default)]
pub struct ScanStateRC {
    scan_state_rc: Arc<Option<ScanState>>,
    status: ScanStateStage,
}

impl ScanStateRC {
    /// Creates a new instance of [`ScanStateRC`] from a given [`ScanState`].
    fn from_scan_state(scan_state: ScanState) -> Self {
        Self {
            scan_state_rc: Arc::new(Some(scan_state)),
            status: ScanStateStage::InProgress,
        }
    }

    /// Creates a new instance of [`ScanStateRC`].
    ///
    /// This method initializes the [`ScanStateRC`] with a reference to a [`ScanState`] that is initially set to `None`.
    /// An empty ScanState is equivalent to a 0 cursor.
    pub fn new() -> Self {
        Self {
            scan_state_rc: Arc::new(None),
            status: ScanStateStage::Initiating,
        }
    }
    /// create a new instance of [`ScanStateRC`] with finished state and empty scan state.
    fn create_finished() -> Self {
        Self {
            scan_state_rc: Arc::new(None),
            status: ScanStateStage::Finished,
        }
    }
    /// Returns `true` if the scan state is finished.
    pub fn is_finished(&self) -> bool {
        self.status == ScanStateStage::Finished
    }

    /// Returns a clone of the scan state, if it exist.
    pub(crate) fn state_from_wrapper(&self) -> Option<ScanState> {
        if self.status == ScanStateStage::Initiating || self.status == ScanStateStage::Finished {
            None
        } else {
            self.scan_state_rc.as_ref().clone()
        }
    }
}

/// Represents the state of a cluster scan operation.
///
/// This struct keeps track of the current cursor, which slots have been scanned,
/// the address currently being scanned, and the epoch of that address.
#[derive(PartialEq, Debug, Clone)]
pub(crate) struct ScanState {
    // the real cursor in the scan operation
    cursor: u64,
    // a map of the slots that have been scanned
    scanned_slots_map: SlotsBitsArray,
    // the address that is being scanned currently, based on the next slot set to 0 in the scanned_slots_map, and the address that "owns" the slot
    // in the SlotMap
    pub(crate) address_in_scan: Arc<String>,
    // epoch represent the version of the address, when a failover happens or slots migrate in the epoch will be updated to +1
    address_epoch: u64,
    // the status of the scan operation
    scan_status: ScanStateStage,
}

impl ScanState {
    /// Create a new instance of ScanState.
    ///
    /// # Arguments
    ///
    /// * `cursor` - The cursor position.
    /// * `scanned_slots_map` - The scanned slots map.
    /// * `address_in_scan` - The address being scanned.
    /// * `address_epoch` - The epoch of the address being scanned.
    /// * `scan_status` - The status of the scan operation.
    ///
    /// # Returns
    ///
    /// A new instance of ScanState.
    pub fn new(
        cursor: u64,
        scanned_slots_map: SlotsBitsArray,
        address_in_scan: Arc<String>,
        address_epoch: u64,
        scan_status: ScanStateStage,
    ) -> Self {
        Self {
            cursor,
            scanned_slots_map,
            address_in_scan,
            address_epoch,
            scan_status,
        }
    }

    fn create_finished_state() -> Self {
        Self {
            cursor: 0,
            scanned_slots_map: [0; BITS_ARRAY_SIZE as usize],
            address_in_scan: Default::default(),
            address_epoch: 0,
            scan_status: ScanStateStage::Finished,
        }
    }

    /// Initialize a new scan operation.
    /// This method creates a new scan state with the cursor set to 0, the scanned slots map initialized to 0,
    /// and the address set to the address associated with slot 0.
    /// The address epoch is set to the epoch of the address.
    /// If the address epoch cannot be retrieved, the method returns an error.
    async fn initiate_scan<C>(
        core: &InnerCore<C>,
        allow_non_covered_slots: bool,
    ) -> RedisResult<ScanState>
    where
        C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
    {
        let mut new_scanned_slots_map: SlotsBitsArray = [0; BITS_ARRAY_SIZE as usize];
        let new_cursor = 0;
        let address =
            next_address_to_scan(core, 0, &mut new_scanned_slots_map, allow_non_covered_slots)?;

        match address {
            NextNodeResult::AllSlotsCompleted => Ok(ScanState::create_finished_state()),
            NextNodeResult::Address(address) => {
                let address_epoch = core.address_epoch(&address).await.unwrap_or(0);
                Ok(ScanState::new(
                    new_cursor,
                    new_scanned_slots_map,
                    address,
                    address_epoch,
                    ScanStateStage::InProgress,
                ))
            }
        }
    }

    /// Update the scan state without updating the scanned slots map.
    /// This method is used when the address epoch has changed, and we can't determine which slots are new.
    /// In this case, we skip updating the scanned slots map and only update the address and cursor.
    async fn new_scan_state<C>(
        &self,
        core: Arc<InnerCore<C>>,
        allow_non_covered_slots: bool,
        new_scanned_slots_map: Option<SlotsBitsArray>,
    ) -> RedisResult<ScanState>
    where
        C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
    {
        // If the new scanned slots map is not provided, use the current scanned slots map.
        // The new scanned slots map is provided in the general case when the address epoch has not changed,
        // meaning that we could safely update the scanned slots map with the slots owned by the node.
        // Epoch change means that some slots are new, and we can't determine which slots been there from the beginning and which are new.
        let mut scanned_slots_map = new_scanned_slots_map.unwrap_or(self.scanned_slots_map);
        let next_slot = next_slot(&scanned_slots_map).unwrap_or(0);
        match next_address_to_scan(
            &core,
            next_slot,
            &mut scanned_slots_map,
            allow_non_covered_slots,
        ) {
            Ok(NextNodeResult::Address(new_address)) => {
                let new_epoch = core.address_epoch(&new_address).await.unwrap_or(0);
                Ok(ScanState::new(
                    0,
                    scanned_slots_map,
                    new_address,
                    new_epoch,
                    ScanStateStage::InProgress,
                ))
            }
            Ok(NextNodeResult::AllSlotsCompleted) => Ok(ScanState::create_finished_state()),
            Err(err) => Err(err),
        }
    }

    /// Update the scan state and get the next address to scan.
    /// This method is called when the cursor reaches 0, indicating that the current address has been scanned.
    /// This method updates the scan state based on the scanned slots map and retrieves the next address to scan.
    /// If the address epoch has changed, the method skips updating the scanned slots map and only updates the address and cursor.
    /// If the address epoch has not changed, the method updates the scanned slots map with the slots owned by the address.
    /// The method returns the new scan state with the updated cursor, scanned slots map, address, and epoch.
    async fn create_updated_scan_state_for_completed_address<C>(
        &mut self,
        core: Arc<InnerCore<C>>,
        allow_non_covered_slots: bool,
    ) -> RedisResult<ScanState>
    where
        C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
    {
        ClusterConnInner::check_topology_and_refresh_if_diff(
            core.clone(),
            &RefreshPolicy::NotThrottable,
        )
        .await?;

        let mut scanned_slots_map = self.scanned_slots_map;
        // If the address epoch changed it mean that some slots in the address are new, so we cant know which slots been there from the beginning and which are new, or out and in later.
        // In this case we will skip updating the scanned_slots_map and will just update the address and the cursor
        let new_address_epoch = core.address_epoch(&self.address_in_scan).await.unwrap_or(0);
        if new_address_epoch != self.address_epoch {
            return self
                .new_scan_state(core, allow_non_covered_slots, None)
                .await;
        }
        // If epoch wasn't changed, the slots owned by the address after the refresh are all valid as slots that been scanned
        // So we will update the scanned_slots_map with the slots owned by the address
        let slots_scanned = core.slots_of_address(self.address_in_scan.clone()).await;
        for slot in slots_scanned {
            mark_slot_as_scanned(&mut scanned_slots_map, slot);
        }
        // Get the next address to scan and its param base on the next slot set to 0 in the scanned_slots_map
        self.new_scan_state(core, allow_non_covered_slots, Some(scanned_slots_map))
            .await
    }
}

fn mark_slot_as_scanned(scanned_slots_map: &mut SlotsBitsArray, slot: u16) {
    let slot_index = (slot as u64 / BITS_PER_U64 as u64) as usize;
    let slot_bit = slot as u64 % (BITS_PER_U64 as u64);
    scanned_slots_map[slot_index] |= 1 << slot_bit;
}

#[derive(PartialEq, Debug, Clone)]
/// The address type representing a connection address
///
/// # Fields
///
/// * `Address` - A thread-safe shared string containing the server address
/// * `AllSlotsCompleted` - Indicates that all slots have been scanned
enum NextNodeResult {
    Address(Arc<String>),
    AllSlotsCompleted,
}

/// Determines the next node address to scan within the cluster.
///
/// This asynchronous function iterates through cluster slots to find the next available
/// node responsible for scanning. If a slot is not covered and `allow_non_covered_slots`
/// is enabled, it marks the slot as scanned and proceeds to the next one. The process
/// continues until a valid address is found or all slots have been scanned.
///
/// # Arguments
///
/// * `core` - Reference to the cluster's inner core connection.
/// * `slot` - The current slot number to scan.
/// * `scanned_slots_map` - Mutable reference to the bitmap tracking scanned slots.
/// * `allow_non_covered_slots` - Flag indicating whether to allow scanning of uncovered slots.
///
/// # Returns
///
/// * `RedisResult<NextNodeResult>` - Returns the next node address to scan or indicates completion.
///
/// # Type Parameters
///
/// * `C`: The connection type that must implement `ConnectionLike`, `Connect`, `Clone`, `Send`, `Sync`, and `'static`.
///
fn next_address_to_scan<C>(
    core: &InnerCore<C>,
    mut slot: u16,
    scanned_slots_map: &mut SlotsBitsArray,
    allow_non_covered_slots: bool,
) -> RedisResult<NextNodeResult>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    loop {
        if slot == END_OF_SCAN {
            return Ok(NextNodeResult::AllSlotsCompleted);
        }

        if let Some(addr) = core
            .conn_lock
            .read()
            .expect(MUTEX_READ_ERR)
            .slot_map
            .node_address_for_slot(slot, SlotAddr::ReplicaRequired)
        {
            // Found a valid address for the slot
            return Ok(NextNodeResult::Address(addr));
        } else if allow_non_covered_slots {
            // Mark the current slot as scanned
            mark_slot_as_scanned(scanned_slots_map, slot);
            slot = next_slot(scanned_slots_map).unwrap();
        } else {
            // Error if slots are not covered and scanning is not allowed
            return Err(RedisError::from((
                    ErrorKind::NotAllSlotsCovered,
                    "Could not find an address covering a slot, SCAN operation cannot continue \n 
                    If you want to continue scanning even if some slots are not covered, set allow_non_covered_slots to true \n 
                    Note that this may lead to incomplete scanning, and the SCAN operation lose its all guarantees ",
                )));
        }
    }
}

/// Get the next slot to be scanned based on the scanned slots map.
/// If all slots have been scanned, the method returns [`END_OF_SCAN`].
fn next_slot(scanned_slots_map: &SlotsBitsArray) -> Option<u16> {
    let all_slots_scanned = scanned_slots_map.iter().all(|&word| word == u64::MAX);
    if all_slots_scanned {
        return Some(END_OF_SCAN);
    }
    for (i, slot) in scanned_slots_map.iter().enumerate() {
        let mut mask = 1;
        for j in 0..BITS_PER_U64 {
            if (slot & mask) == 0 {
                return Some(i as u16 * BITS_PER_U64 + j);
            }
            mask <<= 1;
        }
    }
    None
}

/// Performs a cluster-wide `SCAN` operation.
///
/// This function scans the cluster for keys based on the provided arguments.
/// It handles the initiation of a new scan or continues an existing scan, manages
/// scan state, handles routing failures, and ensures consistent scanning across
/// cluster topology changes.
///
/// # Arguments
///
/// * `core` - An `Arc`-wrapped reference to the cluster connection (`InnerCore<C>`).
/// * `cluster_scan_args` - Configuration and arguments for the scan operation.
///
/// # Returns
///
/// * `RedisResult<(ScanStateRC, Vec<Value>)>` -
///   - On success: A tuple containing the updated scan state (`ScanStateRC`) and a vector of `Value`s representing the found keys.
///   - On failure: A `RedisError` detailing the reason for the failure.
///
/// # Type Parameters
///
/// * `C`: The connection type that must implement `ConnectionLike`, `Connect`, `Clone`, `Send`, `Sync`, and `'static`.
///
pub(crate) async fn cluster_scan<C>(
    core: Arc<InnerCore<C>>,
    cluster_scan_args: ClusterScanArgs,
) -> RedisResult<(ScanStateRC, Vec<Value>)>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    // Extract the current scan state cursor and the flag for non-covered slots
    let scan_state_cursor = &cluster_scan_args.scan_state_cursor;
    let allow_non_covered_slots = cluster_scan_args.allow_non_covered_slots;

    // Determine the current scan state:
    // - If an existing scan state is present, use it.
    // - Otherwise, initiate a new scan.
    let scan_state = match scan_state_cursor.state_from_wrapper() {
        Some(state) => state,
        None => match ScanState::initiate_scan(&core, allow_non_covered_slots).await {
            Ok(state) => state,
            Err(err) => {
                // Early return if initiating the scan fails
                return Err(err);
            }
        },
    };
    // Send the SCAN command using the current scan state and scan arguments
    let ((new_cursor, new_keys), mut scan_state) =
        try_scan(&scan_state, &cluster_scan_args, core.clone()).await?;

    // Check if the cursor indicates the end of the current scan segment
    if new_cursor == 0 {
        // Update the scan state to move to the next address/node in the cluster
        scan_state = scan_state
            .create_updated_scan_state_for_completed_address(core, allow_non_covered_slots)
            .await?;
    }

    // Verify if the entire cluster has been scanned
    if scan_state.scan_status == ScanStateStage::Finished {
        // Return the final scan state and the collected keys
        return Ok((ScanStateRC::create_finished(), new_keys));
    }

    // Update the scan state with the new cursor and maintain the progress
    scan_state = ScanState::new(
        new_cursor,
        scan_state.scanned_slots_map,
        scan_state.address_in_scan,
        scan_state.address_epoch,
        ScanStateStage::InProgress,
    );

    // Return the updated scan state and the newly found keys
    Ok((ScanStateRC::from_scan_state(scan_state), new_keys))
}

/// Sends the `SCAN` command to the specified address.
///
/// # Arguments
///
/// * `scan_state` - The current scan state.
/// * `cluster_scan_args` - Arguments for the scan operation, including match pattern, count, object type, and allow_non_covered_slots.
/// * `core` - The cluster connection.
///
/// # Returns
///
/// A `RedisResult` containing the response from the `SCAN` command.
async fn send_scan<C>(
    scan_state: &ScanState,
    cluster_scan_args: &ClusterScanArgs,
    core: Arc<InnerCore<C>>,
) -> RedisResult<Value>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    if let Some(conn_future) = core
        .connection_for_address(&scan_state.address_in_scan)
        .await
    {
        let mut conn = conn_future.await;
        let mut scan_command = cmd("SCAN");
        scan_command.arg(scan_state.cursor);
        if let Some(match_pattern) = cluster_scan_args.match_pattern.as_ref() {
            scan_command.arg("MATCH").arg(match_pattern);
        }
        if let Some(count) = cluster_scan_args.count {
            scan_command.arg("COUNT").arg(count);
        }
        if let Some(object_type) = &cluster_scan_args.object_type {
            scan_command.arg("TYPE").arg(object_type.to_string());
        }
        conn.req_packed_command(&scan_command).await
    } else {
        Err(RedisError::from((
            ErrorKind::ConnectionNotFoundForRoute,
            "Cluster scan failed. No connection available for address: ",
            format!("{}", scan_state.address_in_scan),
        )))
    }
}

/// Checks if the error is retryable during scanning.
/// Retryable errors include network issues, cluster topology changes, and unavailable connections.
/// Scan operations are not keyspace operations, so they are not affected by keyspace errors like `MOVED`.
fn is_scanwise_retryable_error(err: &RedisError) -> bool {
    matches!(
        err.kind(),
        ErrorKind::IoError
            | ErrorKind::AllConnectionsUnavailable
            | ErrorKind::ConnectionNotFoundForRoute
            | ErrorKind::ClusterDown
            | ErrorKind::FatalSendError
    )
}

/// Gets the next scan state by finding the next address to scan.
/// The method updates the scanned slots map and retrieves the next address to scan.
/// If the address epoch has changed, the method creates a new scan state without updating the scanned slots map.
/// If the address epoch has not changed, the method updates the scanned slots map with the slots owned by the address.
/// The method returns the new scan state with the updated cursor, scanned slots map, address, and epoch.
/// The method is used to continue scanning the cluster after completing a scan segment.
async fn next_scan_state<C>(
    core: &Arc<InnerCore<C>>,
    scan_state: &ScanState,
    cluster_scan_args: &ClusterScanArgs,
) -> RedisResult<Option<ScanState>>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    let next_slot = next_slot(&scan_state.scanned_slots_map).unwrap_or(0);
    let mut scanned_slots_map = scan_state.scanned_slots_map;
    match next_address_to_scan(
        core,
        next_slot,
        &mut scanned_slots_map,
        cluster_scan_args.allow_non_covered_slots,
    ) {
        Ok(NextNodeResult::Address(new_address)) => {
            let new_epoch = core.address_epoch(&new_address).await.unwrap_or(0);
            Ok(Some(ScanState::new(
                0,
                scanned_slots_map,
                new_address,
                new_epoch,
                ScanStateStage::InProgress,
            )))
        }
        Ok(NextNodeResult::AllSlotsCompleted) => Ok(None),
        Err(err) => Err(err),
    }
}

/// Attempts to scan the cluster for keys based on the current scan state.
/// Sends the `SCAN` command to the current address and processes the response.
/// On retryable errors, refreshes the cluster topology and retries the scan.
/// Returns the new cursor and keys found upon success.
async fn try_scan<C>(
    scan_state: &ScanState,
    cluster_scan_args: &ClusterScanArgs,
    core: Arc<InnerCore<C>>,
) -> RedisResult<((u64, Vec<Value>), ScanState)>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    let mut new_scan_state = scan_state.clone();

    loop {
        match send_scan(&new_scan_state, cluster_scan_args, core.clone()).await {
            Ok(scan_response) => {
                let (new_cursor, new_keys) = from_redis_value::<(u64, Vec<Value>)>(&scan_response)?;
                return Ok(((new_cursor, new_keys), new_scan_state));
            }
            Err(err) if is_scanwise_retryable_error(&err) => {
                ClusterConnInner::check_topology_and_refresh_if_diff(
                    core.clone(),
                    &RefreshPolicy::NotThrottable,
                )
                .await?;

                if let Some(next_scan_state) =
                    next_scan_state(&core, &new_scan_state, cluster_scan_args).await?
                {
                    new_scan_state = next_scan_state;
                } else {
                    return Ok(((0, Vec::new()), ScanState::create_finished_state()));
                }
            }
            Err(err) => return Err(err),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_cluster_scan_args_builder() {
        let args = ClusterScanArgs::builder()
            .with_match_pattern("user:*")
            .with_count(100)
            .with_object_type(ObjectType::Hash)
            .allow_non_covered_slots(true)
            .build();

        assert_eq!(args.match_pattern, Some(b"user:*".to_vec()));
        assert_eq!(args.count, Some(100));
        assert_eq!(args.object_type, Some(ObjectType::Hash));
        assert!(args.allow_non_covered_slots);
    }

    #[tokio::test]
    async fn test_scan_state_new() {
        let address = Arc::new("127.0.0.1:6379".to_string());
        let scan_state = ScanState::new(
            0,
            [0; BITS_ARRAY_SIZE as usize],
            address.clone(),
            1,
            ScanStateStage::InProgress,
        );

        assert_eq!(scan_state.cursor, 0);
        assert_eq!(scan_state.scanned_slots_map, [0; BITS_ARRAY_SIZE as usize]);
        assert_eq!(scan_state.address_in_scan, address);
        assert_eq!(scan_state.address_epoch, 1);
        assert_eq!(scan_state.scan_status, ScanStateStage::InProgress);
    }

    #[tokio::test]
    async fn test_scan_state_create_finished() {
        let scan_state = ScanState::create_finished_state();

        assert_eq!(scan_state.cursor, 0);
        assert_eq!(scan_state.scanned_slots_map, [0; BITS_ARRAY_SIZE as usize]);
        assert_eq!(scan_state.address_in_scan, Arc::new(String::new()));
        assert_eq!(scan_state.address_epoch, 0);
        assert_eq!(scan_state.scan_status, ScanStateStage::Finished);
    }

    #[tokio::test]
    async fn test_mark_slot_as_scanned() {
        let mut scanned_slots_map = [0; BITS_ARRAY_SIZE as usize];
        mark_slot_as_scanned(&mut scanned_slots_map, 5);

        assert_eq!(scanned_slots_map[0], 1 << 5);
    }

    #[tokio::test]
    async fn test_next_slot() {
        let scan_state = ScanState::new(
            0,
            [0; BITS_ARRAY_SIZE as usize],
            Arc::new("127.0.0.1:6379".to_string()),
            1,
            ScanStateStage::InProgress,
        );
        let next_slot = next_slot(&scan_state.scanned_slots_map);

        assert_eq!(next_slot, Some(0));
    }
}
