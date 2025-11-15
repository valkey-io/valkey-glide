//! This module provides async functionality for Redis Cluster.
//!
//! By default, [`ClusterConnection`] makes use of [`MultiplexedConnection`] and maintains a pool
//! of connections to each node in the cluster. While it  generally behaves similarly to
//! the sync cluster module, certain commands do not route identically, due most notably to
//! a current lack of support for routing commands to multiple nodes.
//!
//! Also note that pubsub functionality is not currently provided by this module.
//!
//! # Example
//! ```rust,no_run
//! use redis::cluster::ClusterClient;
//! use redis::AsyncCommands;
//!
//! async fn fetch_an_integer() -> String {
//!     let nodes = vec!["redis://127.0.0.1/"];
//!     let client = ClusterClient::new(nodes).unwrap();
//!     let mut connection = client.get_async_connection(None).await.unwrap();
//!     let _: () = connection.set("test", "test_data").await.unwrap();
//!     let rv: String = connection.get("test").await.unwrap();
//!     return rv;
//! }
//! ```

mod connections_container;
mod connections_logic;
mod pipeline_routing;
/// Exposed only for testing.
pub mod testing {
    pub use super::connections_container::ConnectionDetails;
    pub use super::connections_logic::*;
}
use crate::{
    client::GlideConnectionOptions,
    cluster_routing::{Routable, RoutingInfo, ShardUpdateResult},
    cluster_topology::{
        calculate_topology, get_slot, SlotRefreshState, DEFAULT_NUMBER_OF_REFRESH_SLOTS_RETRIES,
        DEFAULT_REFRESH_SLOTS_RETRY_BASE_DURATION_MILLIS, DEFAULT_REFRESH_SLOTS_RETRY_BASE_FACTOR,
    },
    cmd,
    commands::cluster_scan::{cluster_scan, ClusterScanArgs, ScanStateRC},
    types::ServerError,
    FromRedisValue, InfoDict, PipelineRetryStrategy,
};
use connections_container::{RefreshTaskNotifier, RefreshTaskState, RefreshTaskStatus};
use dashmap::DashMap;
use pipeline_routing::{
    collect_and_send_pending_requests, map_pipeline_to_nodes, process_and_retry_pipeline_responses,
    route_for_pipeline, PipelineResponses, ResponsePoliciesMap,
};

use logger_core::{log_error, log_warn};
use rand::seq::IteratorRandom;

use std::{
    collections::{HashMap, HashSet},
    fmt, io, mem,
    net::{IpAddr, SocketAddr},
    pin::Pin,
    sync::{
        atomic::{self, AtomicUsize, Ordering},
        Arc, Mutex,
    },
    task::{self, Poll},
    time::SystemTime,
};
use strum_macros::Display;
#[cfg(feature = "tokio-comp")]
use tokio::task::JoinHandle;

#[cfg(feature = "tokio-comp")]
use crate::aio::DisconnectNotifier;
use telemetrylib::{GlideOpenTelemetry, Telemetry};

use crate::{
    aio::{get_socket_addrs, ConnectionLike, MultiplexedConnection, Runtime},
    cluster::slot_cmd,
    cluster_async::connections_logic::{
        get_host_and_port_from_addr, get_or_create_conn, ConnectionFuture, RefreshConnectionType,
    },
    cluster_client::{ClusterParams, RetryParams},
    cluster_routing::{
        self, MultipleNodeRoutingInfo, Redirect, ResponsePolicy, Route, SingleNodeRoutingInfo,
        SlotAddr,
    },
    connection::{PubSubSubscriptionInfo, PubSubSubscriptionKind},
    push_manager::PushInfo,
    Cmd, ConnectionInfo, ErrorKind, IntoConnectionInfo, RedisError, RedisFuture, RedisResult,
    Value,
};
use futures::{
    future::Shared,
    stream::{FuturesUnordered, StreamExt},
};
use std::time::Duration;

#[cfg(feature = "tokio-comp")]
use async_trait::async_trait;
#[cfg(feature = "tokio-comp")]
use tokio_retry2::strategy::{jitter_range, ExponentialFactorBackoff};
#[cfg(feature = "tokio-comp")]
use tokio_retry2::{Retry, RetryError};

#[cfg(feature = "tokio-comp")]
use tokio::{sync::Notify, time::timeout};

use dispose::{Disposable, Dispose};
use futures::{future::BoxFuture, prelude::*, ready};
use pin_project_lite::pin_project;
use std::sync::RwLock as StdRwLock;
use tokio::sync::{
    mpsc,
    oneshot::{self, Receiver},
    RwLock as TokioRwLock,
};
use tracing::{debug, info, trace, warn};

use self::{
    connections_container::{ConnectionAndAddress, ConnectionType, ConnectionsMap},
    connections_logic::connect_and_check,
};
use crate::types::RetryMethod;

pub(crate) const MUTEX_READ_ERR: &str = "Failed to obtain read lock. Poisoned mutex?";
const MUTEX_WRITE_ERR: &str = "Failed to obtain write lock. Poisoned mutex?";
/// This represents an async Cluster connection. It stores the
/// underlying connections maintained for each node in the cluster, as well
/// as common parameters for connecting to nodes and executing commands.
#[derive(Clone)]
pub struct ClusterConnection<C = MultiplexedConnection>(mpsc::Sender<Message<C>>);

impl<C> ClusterConnection<C>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + Unpin + 'static,
{
    pub(crate) async fn new(
        initial_nodes: &[ConnectionInfo],
        cluster_params: ClusterParams,
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    ) -> RedisResult<ClusterConnection<C>> {
        ClusterConnInner::new(initial_nodes, cluster_params, push_sender)
            .await
            .map(|inner| {
                let (tx, mut rx) = mpsc::channel::<Message<_>>(100);
                let stream = async move {
                    let _ = stream::poll_fn(move |cx| rx.poll_recv(cx))
                        .map(Ok)
                        .forward(inner)
                        .await;
                };
                #[cfg(feature = "tokio-comp")]
                tokio::spawn(stream);
                ClusterConnection(tx)
            })
    }

    /// Special handling for `SCAN` command, using `cluster_scan_with_pattern`.
    /// It is a special case of [`cluster_scan`], with an additional match pattern.
    /// Perform a `SCAN` command on a cluster, using scan state object in order to handle changes in topology
    /// and make sure that all keys that were in the cluster from start to end of the scan are scanned.
    /// In order to make sure all keys in the cluster scanned, topology refresh occurs more frequently and may affect performance.
    ///
    /// # Arguments
    ///
    /// * `scan_state_rc` - A reference to the scan state. For initiating a new scan, send [`ScanStateRC::new()`].
    ///   For each subsequent iteration, use the returned [`ScanStateRC`].
    /// * `cluster_scan_args` - A [`ClusterScanArgs`] struct containing the arguments for the cluster scan command:
    ///   match pattern, count, object type, and the `allow_non_covered_slots` flag.
    ///
    /// # Returns
    ///
    /// A [`ScanStateRC`] for the updated state of the scan and the vector of keys that were found in the scan.
    /// structure of returned value:
    /// `Ok((ScanStateRC, Vec<Value>))`
    ///
    /// When the scan is finished [`ScanStateRC`] will be None, and can be checked by calling `scan_state_wrapper.is_finished()`.
    ///
    /// # Example
    /// ```rust,no_run
    /// use redis::cluster::ClusterClient;
    /// use redis::{ScanStateRC, from_redis_value, Value, ObjectType, ClusterScanArgs};
    ///
    /// async fn scan_all_cluster() -> Vec<String> {
    ///     let nodes = vec!["redis://127.0.0.1/"];
    ///     let client = ClusterClient::new(nodes).unwrap();
    ///     let mut connection = client.get_async_connection(None).await.unwrap();
    ///     let mut scan_state_rc = ScanStateRC::new();
    ///     let mut keys: Vec<String> = vec![];
    ///     let cluster_scan_args = ClusterScanArgs::builder().with_count(1000).with_object_type(ObjectType::String).build();
    ///     loop {
    ///         let (next_cursor, scan_keys): (ScanStateRC, Vec<Value>) =
    ///         connection.cluster_scan(scan_state_rc, cluster_scan_args.clone()).await.unwrap();
    ///         scan_state_rc = next_cursor;
    ///         let mut scan_keys = scan_keys
    ///             .into_iter()
    ///             .map(|v| from_redis_value(&v).unwrap())
    ///             .collect::<Vec<String>>(); // Change the type of `keys` to `Vec<String>`
    ///         keys.append(&mut scan_keys);
    ///         if scan_state_rc.is_finished() {
    ///             break;
    ///             }
    ///         }
    ///     keys
    ///     }
    /// ```
    pub async fn cluster_scan(
        &mut self,
        scan_state_rc: ScanStateRC,
        mut cluster_scan_args: ClusterScanArgs,
    ) -> RedisResult<(ScanStateRC, Vec<Value>)> {
        cluster_scan_args.set_scan_state_cursor(scan_state_rc);
        self.route_cluster_scan(cluster_scan_args).await
    }

    /// Route cluster scan to be handled by internal cluster_scan command
    async fn route_cluster_scan(
        &mut self,
        cluster_scan_args: ClusterScanArgs,
    ) -> RedisResult<(ScanStateRC, Vec<Value>)> {
        let (sender, receiver) = oneshot::channel();
        self.0
            .send(Message {
                cmd: CmdArg::ClusterScan { cluster_scan_args },
                sender,
            })
            .await
            .map_err(|e| {
                RedisError::from(io::Error::new(
                    io::ErrorKind::BrokenPipe,
                    format!("Cluster: Error occurred while trying to send SCAN command to internal send task. {e:?}"),
                ))
            })?;
        receiver
            .await
            .unwrap_or_else(|e| {
                Err(RedisError::from(io::Error::new(
                    io::ErrorKind::BrokenPipe,
                    format!("Cluster: Failed to receive SCAN command response from internal send task. {e:?}"),
                )))
            })
            .map(|response| match response {
                Response::ClusterScanResult(new_scan_state_ref, key) => (new_scan_state_ref, key),
                Response::Single(_) | Response::Multiple(_) => unreachable!(),
            })
    }

    /// Send a command to the given `routing`. If `routing` is [None], it will be computed from `cmd`.
    pub async fn route_command(
        &mut self,
        cmd: &Cmd,
        routing: cluster_routing::RoutingInfo,
    ) -> RedisResult<Value> {
        trace!("route_command");
        let (sender, receiver) = oneshot::channel();
        self.0
            .send(Message {
                cmd: CmdArg::Cmd {
                    cmd: Arc::new(cmd.clone()),
                    routing: routing.into(),
                },
                sender,
            })
            .await
            .map_err(|e| {
                RedisError::from(io::Error::new(
                    io::ErrorKind::BrokenPipe,
                    format!("Cluster: Error occurred while trying to send command to internal sender. {e:?}"),
                ))
            })?;
        receiver
            .await
            .unwrap_or_else(|e| {
                Err(RedisError::from(io::Error::new(
                    io::ErrorKind::BrokenPipe,
                    format!(
                        "Cluster: Failed to receive command response from internal sender. {e:?}"
                    ),
                )))
            })
            .map(|response| match response {
                Response::Single(value) => value,
                Response::ClusterScanResult(..) | Response::Multiple(_) => unreachable!(),
            })
    }

    /// Send commands in `pipeline` to the given `route`. If `route` is [None], it will be computed from `pipeline`.
    /// - `pipeline_retry_strategy`: Configures retry behavior for pipeline commands.  
    ///   - `retry_server_error`: If `true`, retries commands on server errors (may cause reordering).  
    ///   - `retry_connection_error`: If `true`, retries on connection errors (may lead to duplicate executions).  
    ///     TODO: add wiki link.
    pub async fn route_pipeline<'a>(
        &'a mut self,
        pipeline: &'a crate::Pipeline,
        offset: usize,
        count: usize,
        route: Option<SingleNodeRoutingInfo>,
        pipeline_retry_strategy: Option<PipelineRetryStrategy>,
    ) -> RedisResult<Vec<Value>> {
        let (sender, receiver) = oneshot::channel();
        self.0
            .send(Message {
                cmd: CmdArg::Pipeline {
                    pipeline: Arc::new(pipeline.clone()),
                    offset,
                    count,
                    route: route.map(|r| Some(r).into()),
                    sub_pipeline: false,
                    pipeline_retry_strategy: pipeline_retry_strategy.unwrap_or_default(),
                },
                sender,
            })
            .await
            .map_err(|err| {
                RedisError::from(io::Error::new(io::ErrorKind::BrokenPipe, err.to_string()))
            })?;

        receiver
            .await
            .unwrap_or_else(|err| {
                Err(RedisError::from(io::Error::new(
                    io::ErrorKind::BrokenPipe,
                    err.to_string(),
                )))
            })
            .map(|response| match response {
                Response::Multiple(values) => values,
                Response::ClusterScanResult(..) | Response::Single(_) => unreachable!(),
            })
    }
    /// Update the password used to authenticate with all cluster servers
    pub async fn update_connection_password(
        &mut self,
        password: Option<String>,
    ) -> RedisResult<Value> {
        self.route_operation_request(Operation::UpdateConnectionPassword(password))
            .await
    }

    /// Update the database ID used for all cluster connections
    pub async fn update_connection_database(&mut self, database_id: i64) -> RedisResult<Value> {
        self.route_operation_request(Operation::UpdateConnectionDatabase(database_id))
            .await
    }

    /// Update the name used for all cluster connections
    pub async fn update_connection_client_name(
        &mut self,
        client_name: Option<String>,
    ) -> RedisResult<Value> {
        self.route_operation_request(Operation::UpdateConnectionClientName(client_name))
            .await
    }

    /// Get the username used to authenticate with all cluster servers
    pub async fn get_username(&mut self) -> RedisResult<Value> {
        self.route_operation_request(Operation::GetUsername).await
    }

    /// Routes an operation request to the appropriate handler.
    async fn route_operation_request(
        &mut self,
        operation_request: Operation,
    ) -> RedisResult<Value> {
        let (sender, receiver) = oneshot::channel();
        self.0
            .send(Message {
                cmd: CmdArg::OperationRequest(operation_request),
                sender,
            })
            .await
            .map_err(|_| RedisError::from(io::Error::from(io::ErrorKind::BrokenPipe)))?;

        receiver
            .await
            .unwrap_or_else(|err| {
                Err(RedisError::from(io::Error::new(
                    io::ErrorKind::BrokenPipe,
                    err.to_string(),
                )))
            })
            .map(|response| match response {
                Response::Single(values) => values,
                Response::ClusterScanResult(..) | Response::Multiple(_) => unreachable!(),
            })
    }
}

#[cfg(feature = "tokio-comp")]
#[derive(Clone)]
struct TokioDisconnectNotifier {
    disconnect_notifier: Arc<Notify>,
}

#[cfg(feature = "tokio-comp")]
#[async_trait]
impl DisconnectNotifier for TokioDisconnectNotifier {
    fn notify_disconnect(&mut self) {
        self.disconnect_notifier.notify_one();
    }

    async fn wait_for_disconnect_with_timeout(&self, max_wait: &Duration) {
        let _ = timeout(*max_wait, async {
            self.disconnect_notifier.notified().await;
        })
        .await;
    }

    fn clone_box(&self) -> Box<dyn DisconnectNotifier> {
        Box::new(self.clone())
    }
}

#[cfg(feature = "tokio-comp")]
impl TokioDisconnectNotifier {
    fn new() -> TokioDisconnectNotifier {
        TokioDisconnectNotifier {
            disconnect_notifier: Arc::new(Notify::new()),
        }
    }
}

type ConnectionMap<C> = connections_container::ConnectionsMap<ConnectionFuture<C>>;
type ConnectionsContainer<C> =
    self::connections_container::ConnectionsContainer<ConnectionFuture<C>>;

pub(crate) struct InnerCore<C> {
    pub(crate) conn_lock: StdRwLock<ConnectionsContainer<C>>,
    cluster_params: StdRwLock<ClusterParams>,
    pending_requests: Mutex<Vec<PendingRequest<C>>>,
    slot_refresh_state: SlotRefreshState,
    initial_nodes: Vec<ConnectionInfo>,
    subscriptions_by_address: TokioRwLock<HashMap<String, PubSubSubscriptionInfo>>,
    unassigned_subscriptions: TokioRwLock<PubSubSubscriptionInfo>,
    glide_connection_options: GlideConnectionOptions,
}

pub(crate) type Core<C> = Arc<InnerCore<C>>;

impl<C> InnerCore<C>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    fn get_cluster_param<T, F>(&self, f: F) -> Result<T, RedisError>
    where
        F: FnOnce(&ClusterParams) -> T,
        T: Clone,
    {
        self.cluster_params
            .read()
            .map(|guard| f(&guard).clone())
            .map_err(|_| RedisError::from((ErrorKind::ClientError, MUTEX_READ_ERR)))
    }

    fn set_cluster_param<F>(&self, f: F) -> Result<(), RedisError>
    where
        F: FnOnce(&mut ClusterParams),
    {
        self.cluster_params
            .write()
            .map(|mut params| {
                f(&mut params);
            })
            .map_err(|_| RedisError::from((ErrorKind::ClientError, MUTEX_WRITE_ERR)))
    }

    // return epoch of node
    pub(crate) async fn address_epoch(&self, node_address: &str) -> Result<u64, RedisError> {
        let command = cmd("CLUSTER").arg("INFO").to_owned();
        let node_conn = self
            .conn_lock
            .read()
            .expect(MUTEX_READ_ERR)
            .connection_for_address(node_address)
            .ok_or(RedisError::from((
                ErrorKind::ResponseError,
                "Failed to parse cluster info",
            )))?;

        let cluster_info = node_conn.1.await.req_packed_command(&command).await;
        match cluster_info {
            Ok(value) => {
                let info_dict: Result<InfoDict, RedisError> =
                    FromRedisValue::from_redis_value(&value);
                if let Ok(info_dict) = info_dict {
                    let epoch = info_dict.get("cluster_my_epoch");
                    if let Some(epoch) = epoch {
                        Ok(epoch)
                    } else {
                        Err(RedisError::from((
                            ErrorKind::ResponseError,
                            "Failed to get epoch from cluster info",
                        )))
                    }
                } else {
                    Err(RedisError::from((
                        ErrorKind::ResponseError,
                        "Failed to parse cluster info",
                    )))
                }
            }
            Err(redis_error) => Err(redis_error),
        }
    }

    /// return slots of node
    pub(crate) async fn slots_of_address(&self, node_address: Arc<String>) -> Vec<u16> {
        self.conn_lock
            .read()
            .expect(MUTEX_READ_ERR)
            .slot_map
            .get_slots_of_node(node_address)
    }

    /// Get connection for address
    pub(crate) async fn connection_for_address(
        &self,
        address: &str,
    ) -> Option<ConnectionFuture<C>> {
        self.conn_lock
            .read()
            .expect(MUTEX_READ_ERR)
            .connection_for_address(address)
            .map(|(_, conn)| conn)
    }
}

pub(crate) struct ClusterConnInner<C> {
    pub(crate) inner: Core<C>,
    state: ConnectionState,
    #[allow(clippy::complexity)]
    in_flight_requests: stream::FuturesUnordered<Pin<Box<Request<C>>>>,
    refresh_error: Option<RedisError>,
    // Handler of the periodic check task.
    periodic_checks_handler: Option<JoinHandle<()>>,
    // Handler of fast connection validation task
    connections_validation_handler: Option<JoinHandle<()>>,
}

impl<C> Dispose for ClusterConnInner<C> {
    fn dispose(self) {
        if let Ok(conn_lock) = self.inner.conn_lock.try_read() {
            // Each node may contain user and *maybe* a management connection
            let mut count = 0usize;
            for node in conn_lock.connection_map() {
                count = node.connections_count();
            }
            Telemetry::decr_total_connections(count);
        }

        if let Some(handle) = self.periodic_checks_handler {
            #[cfg(feature = "tokio-comp")]
            handle.abort()
        }

        if let Some(handle) = self.connections_validation_handler {
            #[cfg(feature = "tokio-comp")]
            handle.abort()
        }

        // Reduce the number of clients
        Telemetry::decr_total_clients(1);
    }
}

#[derive(Clone)]
pub(crate) enum InternalRoutingInfo<C> {
    SingleNode(InternalSingleNodeRouting<C>),
    MultiNode((MultipleNodeRoutingInfo, Option<ResponsePolicy>)),
}

#[derive(PartialEq, Clone, Debug)]
/// Represents different policies for refreshing the cluster slots.
pub(crate) enum RefreshPolicy {
    /// `Throttable` indicates that the refresh operation can be throttled,
    /// meaning it can be delayed or rate-limited if necessary.
    Throttable,
    /// `NotThrottable` indicates that the refresh operation should not be throttled,
    /// meaning it should be executed immediately without any delay or rate-limiting.
    NotThrottable,
}

impl<C> From<cluster_routing::RoutingInfo> for InternalRoutingInfo<C> {
    fn from(value: cluster_routing::RoutingInfo) -> Self {
        match value {
            cluster_routing::RoutingInfo::SingleNode(route) => {
                InternalRoutingInfo::SingleNode(route.into())
            }
            cluster_routing::RoutingInfo::MultiNode(routes) => {
                InternalRoutingInfo::MultiNode(routes)
            }
        }
    }
}

impl<C> From<InternalSingleNodeRouting<C>> for InternalRoutingInfo<C> {
    fn from(value: InternalSingleNodeRouting<C>) -> Self {
        InternalRoutingInfo::SingleNode(value)
    }
}

#[derive(Clone)]
pub(crate) enum InternalSingleNodeRouting<C> {
    Random,
    SpecificNode(Route),
    ByAddress(String),
    Connection {
        address: String,
        conn: ConnectionFuture<C>,
    },
    Redirect {
        redirect: Redirect,
        previous_routing: Box<InternalSingleNodeRouting<C>>,
    },
}

impl<C> Default for InternalSingleNodeRouting<C> {
    fn default() -> Self {
        Self::Random
    }
}

impl<C> From<SingleNodeRoutingInfo> for InternalSingleNodeRouting<C> {
    fn from(value: SingleNodeRoutingInfo) -> Self {
        match value {
            SingleNodeRoutingInfo::Random => InternalSingleNodeRouting::Random,
            SingleNodeRoutingInfo::SpecificNode(route) => {
                InternalSingleNodeRouting::SpecificNode(route)
            }
            SingleNodeRoutingInfo::RandomPrimary => {
                InternalSingleNodeRouting::SpecificNode(Route::new_random_primary())
            }
            SingleNodeRoutingInfo::ByAddress { host, port } => {
                InternalSingleNodeRouting::ByAddress(format!("{host}:{port}"))
            }
        }
    }
}

impl<C> From<Option<SingleNodeRoutingInfo>> for InternalSingleNodeRouting<C> {
    fn from(value: Option<SingleNodeRoutingInfo>) -> Self {
        match value {
            Some(single) => single.into(),
            None => InternalSingleNodeRouting::Random,
        }
    }
}

#[derive(Clone)]
enum CmdArg<C> {
    Cmd {
        cmd: Arc<Cmd>,
        routing: InternalRoutingInfo<C>,
    },
    Pipeline {
        pipeline: Arc<crate::Pipeline>,
        offset: usize,
        count: usize,
        route: Option<InternalSingleNodeRouting<C>>,
        sub_pipeline: bool,
        /// Configures retry behavior for pipeline commands.  
        ///   - `retry_server_error`: If `true`, retries commands on server errors (may cause reordering).  
        ///   - `retry_connection_error`: If `true`, retries on connection errors (may lead to duplicate executions).  
        pipeline_retry_strategy: PipelineRetryStrategy,
    },
    ClusterScan {
        // struct containing the arguments for the cluster scan command - scan state cursor, match pattern, count and object type.
        cluster_scan_args: ClusterScanArgs,
    },
    // Operational requests which are connected to the internal state of the connection and not send as a command to the server.
    OperationRequest(Operation),
}

// Operation requests which are connected to the internal state of the connection and not send as a command to the server.
#[derive(Clone)]
enum Operation {
    UpdateConnectionPassword(Option<String>),
    UpdateConnectionDatabase(i64),
    UpdateConnectionClientName(Option<String>),
    GetUsername,
}

fn boxed_sleep(duration: Duration) -> BoxFuture<'static, ()> {
    Box::pin(tokio::time::sleep(duration))
}

#[derive(Debug, Display)]
pub(crate) enum Response {
    Single(Value),
    ClusterScanResult(ScanStateRC, Vec<Value>),
    Multiple(Vec<Value>),
}

#[derive(Debug)]
pub(crate) enum OperationTarget {
    Node { address: String },
    FanOut,
    NotFound,
    FatalError,
}
type OperationResult = Result<Response, (OperationTarget, RedisError)>;

impl From<String> for OperationTarget {
    fn from(address: String) -> Self {
        OperationTarget::Node { address }
    }
}

/// Represents a node to which a `MOVED` or `ASK` error redirects.
#[derive(Clone, Debug)]
pub(crate) struct RedirectNode {
    /// The address of the redirect node.
    pub address: String,
    /// The slot of the redirect node.
    pub slot: u16,
}

impl RedirectNode {
    /// Constructs a `RedirectNode` from an optional tuple containing an address and a slot number.
    pub(crate) fn from_option_tuple(option: Option<(&str, u16)>) -> Option<Self> {
        option.map(|(address, slot)| RedirectNode {
            address: address.to_string(),
            slot,
        })
    }
}

struct Message<C: Sized> {
    cmd: CmdArg<C>,
    sender: oneshot::Sender<RedisResult<Response>>,
}

enum RecoverFuture {
    RefreshingSlots(JoinHandle<RedisResult<()>>),
    ReconnectToInitialNodes(BoxFuture<'static, ()>),
    Reconnect(BoxFuture<'static, ()>),
}

enum ConnectionState {
    PollComplete,
    Recover(RecoverFuture),
}

impl fmt::Debug for ConnectionState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{}",
            match self {
                ConnectionState::PollComplete => "PollComplete",
                ConnectionState::Recover(_) => "Recover",
            }
        )
    }
}

#[derive(Clone)]
struct RequestInfo<C> {
    cmd: CmdArg<C>,
}

impl<C> RequestInfo<C> {
    fn set_redirect(&mut self, redirect: Option<Redirect>) {
        if let Some(redirect) = redirect {
            match &mut self.cmd {
                CmdArg::Cmd { routing, .. } => match routing {
                    InternalRoutingInfo::SingleNode(route) => {
                        let redirect = InternalSingleNodeRouting::Redirect {
                            redirect,
                            previous_routing: Box::new(std::mem::take(route)),
                        }
                        .into();
                        *routing = redirect;
                    }
                    InternalRoutingInfo::MultiNode(_) => {
                        panic!("Cannot redirect multinode requests")
                    }
                },
                CmdArg::Pipeline { route, .. } => {
                    let redirect = InternalSingleNodeRouting::Redirect {
                        redirect,
                        previous_routing: Box::new(
                            route.take().unwrap_or(InternalSingleNodeRouting::Random),
                        ),
                    };
                    *route = Some(redirect);
                }
                // cluster_scan is sent as a normal command internally so we will not reach that point.
                CmdArg::ClusterScan { .. } => {
                    unreachable!()
                }
                // Operation requests are not routed.
                CmdArg::OperationRequest(_) => {
                    unreachable!()
                }
            }
        }
    }

    fn reset_routing(&mut self) {
        let fix_route = |route: &mut InternalSingleNodeRouting<C>| {
            match route {
                InternalSingleNodeRouting::Redirect {
                    previous_routing, ..
                } => {
                    let previous_routing = std::mem::take(previous_routing.as_mut());
                    *route = previous_routing;
                }
                // If a specific connection is specified, then reconnecting without resetting the routing
                // will mean that the request is still routed to the old connection.
                InternalSingleNodeRouting::Connection { address, .. } => {
                    *route = InternalSingleNodeRouting::ByAddress(address.to_string());
                }
                _ => {}
            }
        };
        match &mut self.cmd {
            CmdArg::Cmd { routing, .. } => {
                if let InternalRoutingInfo::SingleNode(route) = routing {
                    fix_route(route);
                }
            }
            CmdArg::Pipeline { route, .. } => {
                if route.is_some() {
                    let route = route.as_mut().unwrap();
                    fix_route(route);
                }
            }
            // cluster_scan is sent as a normal command internally so we will not reach that point.
            CmdArg::ClusterScan { .. } => {
                unreachable!()
            }
            // Operation requests are not routed.
            CmdArg::OperationRequest { .. } => {
                unreachable!()
            }
        }
    }
}

pin_project! {
    #[project = RequestStateProj]
    enum RequestState<F> {
        None,
        Future {
            #[pin]
            future: F,
        },
        Sleep {
            #[pin]
            sleep: BoxFuture<'static, ()>,
        },
        UpdateMoved {
            #[pin]
            future: BoxFuture<'static, RedisResult<()>>,
        },
    }
}

struct PendingRequest<C> {
    retry: u32,
    sender: oneshot::Sender<RedisResult<Response>>,
    info: RequestInfo<C>,
}

pin_project! {
    struct Request<C> {
        retry_params: RetryParams,
        request: Option<PendingRequest<C>>,
        #[pin]
        future: RequestState<BoxFuture<'static, OperationResult>>,
    }
}

#[must_use]
enum Next<C> {
    Retry {
        request: PendingRequest<C>,
    },
    RetryBusyLoadingError {
        request: PendingRequest<C>,
        address: String,
    },
    Reconnect {
        // if not set, then a reconnect should happen without sending a request afterwards
        request: Option<PendingRequest<C>>,
        target: String,
    },
    RefreshSlots {
        // if not set, then a slot refresh should happen without sending a request afterwards
        request: Option<PendingRequest<C>>,
        sleep_duration: Option<Duration>,
        moved_redirect: Option<RedirectNode>,
    },
    ReconnectToInitialNodes {
        // if not set, then a reconnect should happen without sending a request afterwards
        request: Option<PendingRequest<C>>,
    },
    Done,
}

impl<C> Future for Request<C> {
    type Output = Next<C>;

    fn poll(mut self: Pin<&mut Self>, cx: &mut task::Context) -> Poll<Self::Output> {
        let mut this = self.as_mut().project();
        // If the sender is closed, the caller is no longer waiting for the reply, and it is ambiguous
        // whether they expect the side-effect of the request to happen or not.
        if this.request.is_none() || this.request.as_ref().unwrap().sender.is_closed() {
            return Poll::Ready(Next::Done);
        }
        let future = match this.future.as_mut().project() {
            RequestStateProj::Future { future } => future,
            RequestStateProj::Sleep { sleep } => {
                ready!(sleep.poll(cx));
                return Next::Retry {
                    request: self.project().request.take().unwrap(),
                }
                .into();
            }
            RequestStateProj::UpdateMoved { future } => {
                if let Err(err) = ready!(future.poll(cx)) {
                    // Updating the slot map based on the MOVED error is an optimization.
                    // If it fails, proceed by retrying the request with the redirected node,
                    // and allow the slot refresh task to correct the slot map.
                    info!(
                        "Failed to update the slot map based on the received MOVED error.
                        Error: {err:?}"
                    );
                }
                if let Some(request) = self.project().request.take() {
                    return Next::Retry { request }.into();
                }
                return Next::Done.into();
            }
            _ => panic!("Request future must be Some"),
        };

        match ready!(future.poll(cx)) {
            Ok(item) => {
                self.respond(Ok(item));
                Next::Done.into()
            }
            Err((target, err)) => {
                let request = this.request.as_mut().unwrap();
                // TODO - would be nice if we didn't need to repeat this code twice, with & without retries.
                if request.retry >= this.retry_params.number_of_retries {
                    let retry_method = err.retry_method();
                    let next = if err.kind() == ErrorKind::AllConnectionsUnavailable {
                        Next::ReconnectToInitialNodes { request: None }.into()
                    } else if matches!(err.retry_method(), RetryMethod::MovedRedirect)
                        || matches!(target, OperationTarget::NotFound)
                    {
                        Next::RefreshSlots {
                            request: None,
                            sleep_duration: None,
                            moved_redirect: RedirectNode::from_option_tuple(err.redirect_node()),
                        }
                        .into()
                    } else if matches!(retry_method, RetryMethod::Reconnect)
                        || matches!(retry_method, RetryMethod::ReconnectAndRetry)
                    {
                        if let OperationTarget::Node { address } = target {
                            Next::Reconnect {
                                request: None,
                                target: address,
                            }
                            .into()
                        } else {
                            Next::Done.into()
                        }
                    } else {
                        Next::Done.into()
                    };
                    self.respond(Err(err));
                    return next;
                }
                request.retry = request.retry.saturating_add(1);
                // Record retry attempts metric if telemetry is initialized
                if let Err(e) = GlideOpenTelemetry::record_retry_attempt() {
                    log_error(
                        "OpenTelemetry:retry_error",
                        format!("Failed to record retry attempt: {e}"),
                    );
                }

                if err.kind() == ErrorKind::AllConnectionsUnavailable {
                    return Next::ReconnectToInitialNodes {
                        request: Some(this.request.take().unwrap()),
                    }
                    .into();
                }

                let sleep_duration = this.retry_params.wait_time_for_retry(request.retry);

                let address = match target {
                    OperationTarget::Node { address } => address,
                    OperationTarget::FanOut => {
                        trace!("Request error `{}` multi-node request", err);

                        // Fanout operation are retried per internal request, and don't need additional retries.
                        self.respond(Err(err));
                        return Next::Done.into();
                    }
                    OperationTarget::NotFound => {
                        // TODO - this is essentially a repeat of the retirable error. probably can remove duplication.
                        let mut request = this.request.take().unwrap();
                        request.info.reset_routing();
                        return Next::RefreshSlots {
                            request: Some(request),
                            sleep_duration: Some(sleep_duration),
                            moved_redirect: None,
                        }
                        .into();
                    }
                    OperationTarget::FatalError => {
                        trace!("Fatal error encountered: {:?}", err);
                        self.respond(Err(err));
                        return Next::Done.into();
                    }
                };

                warn!("Received request error {} on node {:?}.", err, address);

                match err.retry_method() {
                    RetryMethod::AskRedirect => {
                        let mut request = this.request.take().unwrap();
                        request.info.set_redirect(
                            err.redirect_node()
                                .map(|(node, _slot)| Redirect::Ask(node.to_string(), true)),
                        );
                        Next::Retry { request }.into()
                    }
                    RetryMethod::MovedRedirect => {
                        let mut request = this.request.take().unwrap();
                        let redirect_node = err.redirect_node();
                        request.info.set_redirect(
                            err.redirect_node()
                                .map(|(node, _slot)| Redirect::Moved(node.to_string())),
                        );
                        Next::RefreshSlots {
                            request: Some(request),
                            sleep_duration: None,
                            moved_redirect: RedirectNode::from_option_tuple(redirect_node),
                        }
                        .into()
                    }
                    RetryMethod::WaitAndRetry => {
                        let sleep_duration = this.retry_params.wait_time_for_retry(request.retry);
                        // Sleep and retry.
                        this.future.set(RequestState::Sleep {
                            sleep: boxed_sleep(sleep_duration),
                        });
                        self.poll(cx)
                    }
                    RetryMethod::Reconnect | RetryMethod::ReconnectAndRetry => {
                        let mut request = this.request.take().unwrap();
                        // TODO should we reset the redirect here?
                        request.info.reset_routing();
                        warn!("disconnected from {:?}", address);
                        let should_retry =
                            matches!(err.retry_method(), RetryMethod::ReconnectAndRetry);
                        Next::Reconnect {
                            request: should_retry.then_some(request),
                            target: address,
                        }
                        .into()
                    }
                    RetryMethod::WaitAndRetryOnPrimaryRedirectOnReplica => {
                        Next::RetryBusyLoadingError {
                            request: this.request.take().unwrap(),
                            address,
                        }
                        .into()
                    }
                    RetryMethod::RetryImmediately => Next::Retry {
                        request: this.request.take().unwrap(),
                    }
                    .into(),
                    RetryMethod::NoRetry => {
                        self.respond(Err(err));
                        Next::Done.into()
                    }
                }
            }
        }
    }
}

impl<C> Request<C> {
    fn respond(self: Pin<&mut Self>, msg: RedisResult<Response>) {
        // If `send` errors the receiver has dropped and thus does not care about the message
        let _ = self
            .project()
            .request
            .take()
            .expect("Result should only be sent once")
            .sender
            .send(msg);
    }
}

enum ConnectionCheck<C> {
    Found((String, ConnectionFuture<C>)),
    OnlyAddress(String),
    RandomConnection,
}

impl<C> ClusterConnInner<C>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    async fn new(
        initial_nodes: &[ConnectionInfo],
        cluster_params: ClusterParams,
        push_sender: Option<mpsc::UnboundedSender<PushInfo>>,
    ) -> RedisResult<Disposable<Self>> {
        let disconnect_notifier = {
            #[cfg(feature = "tokio-comp")]
            {
                Some::<Box<dyn DisconnectNotifier>>(Box::new(TokioDisconnectNotifier::new()))
            }
            #[cfg(not(feature = "tokio-comp"))]
            None
        };

        let discover_az = matches!(
            cluster_params.read_from_replicas,
            crate::cluster_slotmap::ReadFromReplicaStrategy::AZAffinity(_)
                | crate::cluster_slotmap::ReadFromReplicaStrategy::AZAffinityReplicasAndPrimary(_)
        );

        let connection_retry_strategy = cluster_params.reconnect_retry_strategy.unwrap_or_default();

        let glide_connection_options = GlideConnectionOptions {
            push_sender,
            disconnect_notifier,
            discover_az,
            connection_timeout: Some(cluster_params.connection_timeout),
            connection_retry_strategy: Some(connection_retry_strategy),
        };

        let connections = Self::create_initial_connections(
            initial_nodes,
            &cluster_params,
            glide_connection_options.clone(),
        )
        .await?;

        let topology_checks_interval = cluster_params.topology_checks_interval;
        let slots_refresh_rate_limiter = cluster_params.slots_refresh_rate_limit;
        let inner = Arc::new(InnerCore {
            conn_lock: StdRwLock::new(ConnectionsContainer::new(
                Default::default(),
                connections,
                cluster_params.read_from_replicas.clone(),
                0,
            )),
            cluster_params: StdRwLock::new(cluster_params.clone()),
            pending_requests: Mutex::new(Vec::new()),
            slot_refresh_state: SlotRefreshState::new(slots_refresh_rate_limiter),
            initial_nodes: initial_nodes.to_vec(),
            unassigned_subscriptions: TokioRwLock::new(
                if let Some(subs) = cluster_params.pubsub_subscriptions {
                    subs.clone()
                } else {
                    PubSubSubscriptionInfo::new()
                },
            ),
            subscriptions_by_address: TokioRwLock::new(Default::default()),
            glide_connection_options,
        });
        let mut connection = ClusterConnInner {
            inner,
            in_flight_requests: Default::default(),
            refresh_error: None,
            state: ConnectionState::PollComplete,
            periodic_checks_handler: None,
            connections_validation_handler: None,
        };
        Self::refresh_slots_and_subscriptions_with_retries(
            connection.inner.clone(),
            &RefreshPolicy::NotThrottable,
        )
        .await?;

        if let Some(duration) = topology_checks_interval {
            let periodic_task =
                ClusterConnInner::periodic_topology_check(connection.inner.clone(), duration);
            #[cfg(feature = "tokio-comp")]
            {
                connection.periodic_checks_handler = Some(tokio::spawn(periodic_task));
            }
        }

        let connections_validation_interval = cluster_params.connections_validation_interval;
        if let Some(duration) = connections_validation_interval {
            let connections_validation_handler =
                ClusterConnInner::connections_validation_task(connection.inner.clone(), duration);
            #[cfg(feature = "tokio-comp")]
            {
                connection.connections_validation_handler =
                    Some(tokio::spawn(connections_validation_handler));
            }
        }

        // New client added
        Telemetry::incr_total_clients(1);
        Ok(Disposable::new(connection))
    }

    /// Go through each of the initial nodes and attempt to retrieve all IP entries from them.
    /// If there's a DNS endpoint that directs to several IP addresses, add all addresses to the initial nodes list.
    /// Returns a vector of tuples, each containing a node's address (including the hostname) and its corresponding SocketAddr if retrieved.
    pub(crate) async fn try_to_expand_initial_nodes(
        initial_nodes: &[ConnectionInfo],
    ) -> Vec<(String, Option<SocketAddr>)> {
        stream::iter(initial_nodes)
            .fold(
                Vec::with_capacity(initial_nodes.len()),
                |mut acc, info| async {
                    let (host, port) = match &info.addr {
                        crate::ConnectionAddr::Tcp(host, port) => (host, port),
                        crate::ConnectionAddr::TcpTls {
                            host,
                            port,
                            insecure: _,
                            tls_params: _,
                        } => (host, port),
                        crate::ConnectionAddr::Unix(_) => {
                            // We don't support multiple addresses for a Unix address. Store the initial node address and continue
                            acc.push((info.addr.to_string(), None));
                            return acc;
                        }
                    };
                    match get_socket_addrs(host, *port).await {
                        Ok(socket_addrs) => {
                            for addr in socket_addrs {
                                acc.push((info.addr.to_string(), Some(addr)));
                            }
                        }
                        Err(_) => {
                            // Couldn't find socket addresses, store the initial node address and continue
                            acc.push((info.addr.to_string(), None));
                        }
                    };
                    acc
                },
            )
            .await
    }

    async fn create_initial_connections(
        initial_nodes: &[ConnectionInfo],
        params: &ClusterParams,
        glide_connection_options: GlideConnectionOptions,
    ) -> RedisResult<ConnectionMap<C>> {
        let initial_nodes: Vec<(String, Option<SocketAddr>)> =
            Self::try_to_expand_initial_nodes(initial_nodes).await;
        let connections = stream::iter(initial_nodes.iter().cloned())
            .map(|(node_addr, socket_addr)| {
                let mut params: ClusterParams = params.clone();
                let glide_connection_options = glide_connection_options.clone();
                // set subscriptions to none, they will be applied upon the topology discovery
                params.pubsub_subscriptions = None;

                async move {
                    let result = connect_and_check(
                        &node_addr,
                        params,
                        socket_addr,
                        RefreshConnectionType::AllConnections,
                        None,
                        glide_connection_options,
                    )
                    .await
                    .get_node();
                    let node_address = if let Some(socket_addr) = socket_addr {
                        socket_addr.to_string()
                    } else {
                        node_addr
                    };
                    result.map(|node| (node_address, node))
                }
            })
            .buffer_unordered(initial_nodes.len())
            .fold(
                (
                    ConnectionsMap(DashMap::with_capacity(initial_nodes.len())),
                    None,
                ),
                |connections: (ConnectionMap<C>, Option<String>), addr_conn_res| async move {
                    match addr_conn_res {
                        Ok((addr, node)) => {
                            connections.0 .0.insert(addr, node);
                            (connections.0, None)
                        }
                        Err(e) => (connections.0, Some(e.to_string())),
                    }
                },
            )
            .await;
        if connections.0 .0.is_empty() {
            return Err(RedisError::from((
                ErrorKind::IoError,
                "Failed to create initial connections",
                connections.1.unwrap_or("".to_string()),
            )));
        }
        info!("Connected to initial nodes:\n{}", connections.0);
        Ok(connections.0)
    }

    // Reconnect to the initial nodes provided by the user in the creation of the client,
    // and try to refresh the slots based on the initial connections.
    // Being used when all cluster connections are unavailable.
    fn reconnect_to_initial_nodes(inner: Arc<InnerCore<C>>) -> impl Future<Output = ()> {
        let inner = inner.clone();
        let cluster_params = match inner.get_cluster_param(|params| params.clone()) {
            Ok(params) => params,
            Err(err) => {
                warn!("Failed to get cluster params: {}", err);
                return async {}.boxed();
            }
        };
        Box::pin(async move {
            let connection_map = match Self::create_initial_connections(
                &inner.initial_nodes,
                &cluster_params,
                inner.glide_connection_options.clone(),
            )
            .await
            {
                Ok(map) => map,
                Err(err) => {
                    warn!("Can't reconnect to initial nodes: `{err}`");
                    return;
                }
            };
            inner
                .conn_lock
                .write()
                .expect(MUTEX_WRITE_ERR)
                .extend_connection_map(connection_map);
            if let Err(err) = Self::refresh_slots_and_subscriptions_with_retries(
                inner.clone(),
                &RefreshPolicy::NotThrottable,
            )
            .await
            {
                warn!("Can't refresh slots with initial nodes: `{err}`");
            };
        })
    }

    // Validate all existing user connections and try to reconnect if necessary.
    // In addition, as a safety measure, drop nodes that do not have any assigned slots.
    // This function serves as a cheap alternative to slot_refresh() and thus can be used much more frequently.
    // The function does not discover the topology from the cluster and assumes the cached topology is valid.
    // In addition, the validation is done by peeking at the state of the underlying transport w/o overhead of additional commands to server.
    async fn validate_all_user_connections(inner: Arc<InnerCore<C>>) {
        let mut all_valid_conns = HashMap::new();
        // prep connections and clean out these w/o assigned slots, as we might have established connections to unwanted hosts
        let mut nodes_to_delete = Vec::new();
        let all_nodes_with_slots: HashSet<Arc<String>>;
        {
            let connections_container = inner.conn_lock.read().expect(MUTEX_READ_ERR);

            all_nodes_with_slots = connections_container.slot_map.all_node_addresses();

            connections_container
                .all_node_connections()
                .for_each(|(addr, con)| {
                    if all_nodes_with_slots.contains(&addr) {
                        all_valid_conns.insert(addr.clone(), con.clone());
                    } else {
                        nodes_to_delete.push(addr.clone());
                    }
                });

            for addr in &nodes_to_delete {
                connections_container.remove_node(addr);
            }
        }

        // identify nodes with closed connection
        let mut addrs_to_refresh = HashSet::new();
        for (addr, con_fut) in &all_valid_conns {
            let con = con_fut.clone().await;
            // connection object might be present despite the transport being closed
            if con.is_closed() {
                // transport is closed, need to refresh
                addrs_to_refresh.insert(addr.clone());
            }
        }

        // identify missing nodes
        addrs_to_refresh.extend(
            all_nodes_with_slots
                .iter()
                .filter(|addr| !all_valid_conns.contains_key(addr.as_str()))
                .map(|addr| addr.to_string()),
        );

        if !addrs_to_refresh.is_empty() {
            // don't try existing nodes since we know a. it does not exist. b. exist but its connection is closed
            Self::trigger_refresh_connection_tasks(
                inner.clone(),
                addrs_to_refresh,
                RefreshConnectionType::AllConnections,
                false,
            )
            .await;
        }
    }

    // Creates refresh tasks and await on the tasks' notifier.
    // Awaiting on the notifier guaranties at least one reconnect attempt on each address.
    async fn refresh_and_update_connections(
        inner: Arc<InnerCore<C>>,
        addresses: HashSet<String>,
        conn_type: RefreshConnectionType,
        check_existing_conn: bool,
    ) {
        trace!("refresh_and_update_connections: calling trigger_refresh_connection_tasks");
        let refresh_task_notifiers = Self::trigger_refresh_connection_tasks(
            inner.clone(),
            addresses,
            conn_type,
            check_existing_conn,
        )
        .await;

        trace!("refresh_and_update_connections: Await on all tasks' refresh notifier");
        futures::future::join_all(
            refresh_task_notifiers
                .iter()
                .map(|notify| notify.notified()),
        )
        .await;
    }

    // Triggers a reconnection Tokio task for each supplied address.
    // If a refresh task is already running for an address, no new task is created;
    // instead, the notifier from the existing task is returned.
    // Returns a vector of notifiers for the refresh tasks (new or existing) corresponding to the supplied addresses.
    async fn trigger_refresh_connection_tasks(
        inner: Arc<InnerCore<C>>,
        addresses: HashSet<String>,
        conn_type: RefreshConnectionType,
        check_existing_conn: bool,
    ) -> Vec<Arc<Notify>> {
        debug!("Triggering refresh connections tasks to {:?} ", addresses);

        let mut notifiers = Vec::<Arc<Notify>>::new();

        for address in addresses {
            if let Some(existing_task) = inner
                .conn_lock
                .read()
                .expect(MUTEX_READ_ERR)
                .refresh_conn_state
                .refresh_address_in_progress
                .get(&address)
            {
                if let RefreshTaskStatus::Reconnecting(ref notifier) = existing_task.status {
                    // Store the notifier
                    notifiers.push(notifier.get_notifier());
                }
                debug!("Skipping refresh for {}: already in progress", address);
                continue; // Skip creating a new refresh task
            }

            let inner_clone = inner.clone();
            let address_clone_for_task = address.clone();

            let mut node_option = inner
                .conn_lock
                .read()
                .expect(MUTEX_READ_ERR)
                .remove_node(&address);

            if !check_existing_conn {
                node_option = None;
            }

            let handle = tokio::spawn(async move {
                info!(
                    "refreshing connection task to {:?} started",
                    address_clone_for_task
                );

                // We run infinite retries to reconnect until it succeeds or it's aborted from outside.
                let infinite_backoff_iter = inner_clone
                    .glide_connection_options
                    .connection_retry_strategy
                    .unwrap_or_default()
                    .get_infinite_backoff_dur_iterator();

                let mut node_result = Err(RedisError::from((
                    ErrorKind::ClientError,
                    "No attempts performed",
                )));
                let mut first_attempt = true;
                for backoff_duration in infinite_backoff_iter {
                    let mut cluster_params = inner_clone
                        .cluster_params
                        .read()
                        .expect(MUTEX_READ_ERR)
                        .clone();
                    let subs_guard = inner_clone.subscriptions_by_address.read().await;
                    cluster_params.pubsub_subscriptions =
                        subs_guard.get(&address_clone_for_task).cloned();
                    drop(subs_guard);

                    node_result = get_or_create_conn(
                        &address_clone_for_task,
                        node_option.clone(),
                        &cluster_params,
                        conn_type,
                        inner_clone.glide_connection_options.clone(),
                    )
                    .await;

                    match node_result {
                        Ok(_) => {
                            break;
                        }
                        Err(ref err) => {
                            if first_attempt {
                                if let Some(ref mut conn_state) = inner_clone
                                    .conn_lock
                                    .write()
                                    .expect(MUTEX_WRITE_ERR)
                                    .refresh_conn_state
                                    .refresh_address_in_progress
                                    .get_mut(&address_clone_for_task)
                                {
                                    conn_state.status.flip_status_to_too_long();
                                }

                                first_attempt = false;
                            }
                            debug!(
                                "Failed to refresh connection for node {}. Error: `{:?}`. Retrying in {:?}",
                                address_clone_for_task, err, backoff_duration
                            );
                            tokio::time::sleep(backoff_duration).await;
                        }
                    }
                }

                match node_result {
                    Ok(node) => {
                        info!(
                            "Succeeded to refresh connection for node {}.",
                            address_clone_for_task
                        );
                        inner_clone
                            .conn_lock
                            .read()
                            .expect(MUTEX_READ_ERR)
                            .replace_or_add_connection_for_address(&address_clone_for_task, node);
                    }
                    Err(err) => {
                        warn!(
                            "Failed to refresh connection for node {}. Error: `{:?}`",
                            address_clone_for_task, err
                        );
                    }
                }

                inner_clone
                    .conn_lock
                    .write()
                    .expect(MUTEX_READ_ERR)
                    .refresh_conn_state
                    .refresh_address_in_progress
                    .remove(&address_clone_for_task);

                debug!(
                    "Refreshing connection task to {:?} is done",
                    address_clone_for_task
                );
            });

            let notifier = RefreshTaskNotifier::new();
            notifiers.push(notifier.get_notifier());

            // Keep the task handle and notifier into the RefreshState of this address
            let refresh_task_state = RefreshTaskState::new(handle, notifier);

            inner
                .conn_lock
                .write()
                .expect(MUTEX_READ_ERR)
                .refresh_conn_state
                .refresh_address_in_progress
                .insert(address.clone(), refresh_task_state);
        }
        debug!("trigger_refresh_connection_tasks: Done");
        notifiers
    }

    fn spawn_refresh_slots_task(
        inner: Arc<InnerCore<C>>,
        policy: &RefreshPolicy,
    ) -> JoinHandle<RedisResult<()>> {
        // Clone references for task
        let inner_clone = inner.clone();
        let policy_clone = policy.clone();

        // Spawn the background task and return its handle
        tokio::spawn(async move {
            Self::refresh_slots_and_subscriptions_with_retries(inner_clone, &policy_clone).await
        })
    }

    /// Asynchronously collects and aggregates responses from multiple cluster nodes according to a specified policy.
    ///
    /// This function drives the fan‐out of a multi‐node command, awaiting individual node replies
    /// and combining or selecting results based on `response_policy`. It covers these high‐level steps:
    ///
    /// # Arguments
    ///
    /// * `receivers`: A list of `(node_address, oneshot::Receiver<RedisResult<Response>>)` pairs.
    ///   Each receiver will yield exactly one `Response` (or error) from its node.
    /// * `routing` – The routing information of the command (e.g., multi‐slot, `AllNodes`, `AllPrimaries`).
    /// * `response_policy` – An `Option<ResponsePolicy>` that dictates how to aggregate the results from the different nodes.
    ///
    /// # Returns
    ///
    /// A `RedisResult<Value>` representing the aggregated result.
    pub async fn aggregate_results(
        receivers: Vec<(Option<String>, oneshot::Receiver<RedisResult<Response>>)>,
        routing: &MultipleNodeRoutingInfo,
        response_policy: Option<ResponsePolicy>,
    ) -> RedisResult<Value> {
        // Helper: extract a single Value from a Response::Single
        let extract_result = |response| match response {
            Response::Single(value) => value,
            Response::Multiple(_) | Response::ClusterScanResult(_, _) => unreachable!(
                "aggregate_results only handles `Response::Single` for multi-node commands"
            ),
        };

        // Converts a Result<RedisResult<Response>, _> into RedisResult<Value>
        let convert_result = |res: Result<RedisResult<Response>, _>| {
            res.map_err(|_| RedisError::from((ErrorKind::ResponseError, "Internal failure: receiver was dropped before delivering a response"))) // this happens only if the result sender is dropped before usage.
            .and_then(|res| res.map(extract_result))
        };

        // Helper: await a (addr, receiver) and return (addr, RedisResult<Value>)
        let get_receiver = |(_, receiver): (_, oneshot::Receiver<RedisResult<Response>>)| async {
            convert_result(receiver.await)
        };

        // Sanity check: if there are no receivers at all, this is a client‐error
        if receivers.is_empty() {
            return Err(RedisError::from((
                ErrorKind::ClientError,
                "Client internal error",
                "Failed to aggregate results for multi-slot command. Maybe a malformed command?"
                    .to_string(),
            )));
        }

        match response_policy {
            // ────────────────────────────────────────────────────────────────
            // ResponsePolicy::OneSucceeded:
            // Waits for the *first* successful response and returns it.
            // Any other in-flight responses are dropped.
            // ────────────────────────────────────────────────────────────────
            Some(ResponsePolicy::OneSucceeded) => {
                return future::select_ok(receivers.into_iter().map(|tuple| {
                    Box::pin(get_receiver(tuple).map(|res| {
                        res.map(|val| {
                            // Each future in `receivers` represents a single response from a node.
                            // If an error occurs, the value will be a `ServerError` directly, not a structure
                            // like `Value::Array`  or `Value::Map` containing a `ServerError` inside.
                            // Therefore, checking that val is a `ServerError` is sufficient—we do not need to check for nested errors within
                            // composite values.
                            if let Value::ServerError(err) = val {
                                return Err(err.into());
                            }
                            Ok(val)
                        })
                    }))
                }))
                .await
                .map(|(result, _)| result)?;
            }
            // ────────────────────────────────────────────────────────────────
            // ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty:
            // Waits for each response:
            //   - Returns the first non-Nil success immediately.
            //   - If all are Ok(Nil), returns Value::Nil.
            //   - If any error occurs (and no success is returned), returns the last error.
            // ────────────────────────────────────────────────────────────────
            Some(ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty) => {
                // We want to see each response as it arrives, and:
                //  • If we see `Ok(Value::Nil)`, increment a counter.
                //  • If we see `Ok(other_value)`, return it immediately.
                //  • If we see `Err(e)` or `Ok(Value::ServerError)`, remember it as `last_err`.
                //
                // Once the stream is exhausted:
                //  – if all successes were Nil → return Value::Nil (indicates that all shards are empty).
                //  – else → return the last error we saw (or a generic “all‐unavailable” error).
                //
                // If we received a mix of errors and `Nil`s, we can't determine if all shards are empty, thus we return the last received error instead of `Nil`.
                let num_of_results: usize = receivers.len();
                let mut futures = receivers
                    .into_iter()
                    .map(get_receiver)
                    .collect::<FuturesUnordered<_>>();
                let mut nil_counter = 0;
                let mut last_err = None;
                while let Some(result) = futures.next().await {
                    match result {
                        Ok(Value::Nil) => nil_counter += 1,
                        // If the value is a `ServerError` → the command failed for this node.
                        Ok(Value::ServerError(err)) => {
                            last_err = Some(err.into());
                        }
                        Ok(val) => return Ok(val),
                        // If we received a RedisError, it means that this receiver returned a RecvError
                        Err(e) => last_err = Some(e),
                    }
                }

                if nil_counter == num_of_results {
                    // All received results are `Nil`
                    Ok(Value::Nil)
                } else {
                    Err(last_err.unwrap_or_else(|| {
                        (
                            ErrorKind::AllConnectionsUnavailable,
                            "Couldn't find any connection",
                        )
                            .into()
                    }))
                }
            }

            // ────────────────────────────────────────────────────────────────
            // All other policies (e.g., AllSucceeded, Aggregate, CombineArrays, etc):
            // Waits for all responses, collects them, and delegates to
            // `aggregate_resolved_results` for interpretation.
            // ────────────────────────────────────────────────────────────────
            Some(ResponsePolicy::AllSucceeded)
            | Some(ResponsePolicy::Aggregate(_))
            | Some(ResponsePolicy::AggregateLogical(_))
            | Some(ResponsePolicy::CombineArrays)
            | Some(ResponsePolicy::CombineMaps)
            | Some(ResponsePolicy::Special)
            | None => {
                let collected = future::try_join_all(receivers.into_iter().map(
                    |(addr, receiver)| async move {
                        let res = convert_result(receiver.await)?;
                        Ok::<(Option<String>, Value), RedisError>((addr, res))
                    },
                ))
                .await?;
                Self::aggregate_resolved_results(collected, routing, response_policy)
            }
        }
    }

    /// Synchronously folds a fully‐collected `Vec<(Option<String>, Value)>` according to `response_policy`.
    ///
    /// This helper is called after all node replies have been received and converted to `(addr, Value)`.
    /// Each policy’s logic is applied to that vector of resolved results, returning a single `Value` or error.
    ///
    /// This function is used to handle the results of multi‐node commands, where the replies from multiple nodes are not collected using receivers,
    /// but rather already collected into a vector of `(Option<String>, Value)` pairs (e.g. within a pipeline).
    ///
    /// # Arguments
    ///
    /// * `resolved` – A vector of `(Option<String>, Value)` pairs, where:
    ///     - `Option<String>` is the node address (used for map/policy keys).
    ///     - `Value` is the node’s reply.
    /// * `routing` – The routing information of the command (e.g., multi‐slot, `AllNodes`, `AllPrimaries`).
    /// * `response_policy` – An `Option<ResponsePolicy>` that dictates how to aggregate the results from the different nodes.
    ///
    /// # Returns
    ///
    /// A `RedisResult<Value>` representing the aggregated result.
    fn aggregate_resolved_results(
        resolved: Vec<(Option<String>, Value)>,
        routing: &MultipleNodeRoutingInfo,
        response_policy: Option<ResponsePolicy>,
    ) -> RedisResult<Value> {
        // TODO: add support for returning partial results
        let should_check_errors = match response_policy {
            Some(ResponsePolicy::CombineArrays)
            | Some(ResponsePolicy::Special)
            | Some(ResponsePolicy::AllSucceeded)
            | Some(ResponsePolicy::Aggregate(_))
            | Some(ResponsePolicy::AggregateLogical(_))
            | Some(ResponsePolicy::CombineMaps)
            | None => true,
            Some(ResponsePolicy::OneSucceeded)
            | Some(ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty) => false,
        };

        // If we should check for errors, we iterate through the resolved values
        // and return the first error we find.
        if should_check_errors {
            for (_addr, val) in resolved.iter() {
                if let Value::ServerError(err) = val {
                    return Err(err.clone().into());
                }
            }
        }

        let total = resolved.len();

        match response_policy {
            // ——————————————————————————————————————————
            // AllSucceeded: fail if any Err, otherwise return the last Ok value.
            // ——————————————————————————————————————————
            Some(ResponsePolicy::AllSucceeded) => resolved
                .into_iter()
                .next_back()
                .map(|(_, val)| val)
                .ok_or_else(|| {
                    RedisError::from((
                        ErrorKind::ResponseError,
                        "No responses to aggregate for AllSucceeded",
                    ))
                }),

            // ——————————————————————————————————————————
            // Aggregate(op): fail on any Err, otherwise call cluster_routing::aggregate
            // ——————————————————————————————————————————
            Some(ResponsePolicy::Aggregate(op)) => {
                let all_vals: Vec<Value> = resolved.into_iter().map(|(_addr, val)| val).collect();
                crate::cluster_routing::aggregate(all_vals, op)
            }

            // ——————————————————————————————————————————
            // AggregateLogical(op): fail on any Err, otherwise call cluster_routing::logical_aggregate
            // ——————————————————————————————————————————
            Some(ResponsePolicy::AggregateLogical(op)) => {
                let all_vals: Vec<Value> = resolved.into_iter().map(|(_addr, val)| val).collect();
                crate::cluster_routing::logical_aggregate(all_vals, op)
            }

            // ——————————————————————————————————————————
            // CombineArrays: collect all values, then call combine_array_results
            // (or combine_and_sort_array_results if `routing` is MultiSlot)
            // ——————————————————————————————————————————
            Some(ResponsePolicy::CombineArrays) => {
                let all_vals: Vec<Value> = resolved.into_iter().map(|(_addr, val)| val).collect();
                match routing {
                    MultipleNodeRoutingInfo::MultiSlot((keys_vec, args_pattern)) => {
                        crate::cluster_routing::combine_and_sort_array_results(
                            all_vals,
                            keys_vec,
                            args_pattern,
                        )
                    }
                    _ => crate::cluster_routing::combine_array_results(all_vals),
                }
            }

            // ——————————————————————————————————————————
            // CombineMaps: fail on any Err, otherwise call cluster_routing:combine_map_results
            // ——————————————————————————————————————————
            Some(ResponsePolicy::CombineMaps) => {
                let all_vals: Vec<Value> = resolved.into_iter().map(|(_addr, val)| val).collect();
                crate::cluster_routing::combine_map_results(all_vals)
            }

            // ——————————————————————————————————————————
            // Special or None:
            // ——————————————————————————————————————————
            Some(ResponsePolicy::Special) | None => {
                let mut pairs: Vec<(Value, Value)> = Vec::with_capacity(total);
                for (addr_opt, value) in resolved {
                    let key_bytes = match addr_opt {
                        Some(addr) => addr.into_bytes(),
                        None => return Err(RedisError::from((
                            ErrorKind::ResponseError,
                            "No address provided for response in Special or None response policy",
                        ))),
                    };
                    pairs.push((Value::BulkString(key_bytes), value));
                }
                Ok(Value::Map(pairs))
            }

            // ——————————————————————————————————————————
            // If we reach here, it means that the replies from multiple nodes are not collected using `oneshot::Receiver`,
            // but rather already collected into a vector of `(Option<String>, Value)` pairs (e.g. within a pipeline).
            // ——————————————————————————————————————————

            // ────────────────────────────────────────────────────────────────
            // ResponsePolicy::OneSucceeded:
            // Waits for the *first* successful response and returns it.
            // Any other in-flight responses are dropped.
            // ────────────────────────────────────────────────────────────────
            Some(ResponsePolicy::OneSucceeded) => {
                // Return the first successful (non-error) response, or the last error if all failed.
                let mut last_err = None;
                for (_addr, val) in resolved {
                    match val {
                        Value::ServerError(err) => {
                            last_err = Some(err.into());
                        }
                        val => return Ok(val),
                    }
                }
                Err(last_err.unwrap_or_else(|| {
                    (
                        ErrorKind::AllConnectionsUnavailable,
                        "Couldn't find any connection",
                    )
                        .into()
                }))
            }
            // ────────────────────────────────────────────────────────────────
            // ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty:
            // Waits for each response:
            //   - Returns the first non-Nil success immediately.
            //   - If all are Ok(Nil), returns Value::Nil.
            //   - If any error occurs (and no success is returned), returns the last error.
            // ────────────────────────────────────────────────────────────────
            Some(ResponsePolicy::FirstSucceededNonEmptyOrAllEmpty) => {
                // We want to see each response as it arrives, and:
                //  • If we see `Value::Nil`, increment a counter.
                //  • If we see `Value::ServerError`, remember it as `last_err`.
                //  • If we see `other_value`, return it immediately.
                //
                // Once the stream is exhausted:
                //  – if all successes were Nil → return Value::Nil (indicates that all shards are empty).
                //  – else → return the last error we saw (or a generic “all‐unavailable” error).
                //
                // If we received a mix of errors and `Nil`s, we can't determine if all shards are empty, thus we return the last received error instead of `Nil`.
                let mut nil_counter = 0;
                let mut last_err = None;
                let num_results = resolved.len();

                for (_addr, val) in resolved {
                    match val {
                        Value::Nil => nil_counter += 1,
                        Value::ServerError(err) => {
                            last_err = Some(err.into());
                        }
                        val => return Ok(val),
                    }
                }

                if nil_counter == num_results {
                    Ok(Value::Nil)
                } else {
                    Err(last_err.unwrap_or_else(|| {
                        (
                            ErrorKind::AllConnectionsUnavailable,
                            "Couldn't find any connection",
                        )
                            .into()
                    }))
                }
            }
        }
    }

    // Query a node to discover slot-> master mappings with retries
    async fn refresh_slots_and_subscriptions_with_retries(
        inner: Arc<InnerCore<C>>,
        policy: &RefreshPolicy,
    ) -> RedisResult<()> {
        let SlotRefreshState {
            in_progress,
            last_run,
            rate_limiter,
        } = &inner.slot_refresh_state;
        // Ensure only a single slot refresh operation occurs at a time
        if in_progress
            .compare_exchange(false, true, Ordering::Relaxed, Ordering::Relaxed)
            .is_err()
        {
            return Ok(());
        }
        let mut should_refresh_slots = true;
        if *policy == RefreshPolicy::Throttable {
            // Check if the current slot refresh is triggered before the wait duration has passed
            let last_run_rlock = last_run.read().await;
            if let Some(last_run_time) = *last_run_rlock {
                let passed_time = SystemTime::now()
                    .duration_since(last_run_time)
                    .unwrap_or_else(|err| {
                        warn!(
                            "Failed to get the duration since the last slot refresh, received error: {:?}",
                            err
                        );
                        // Setting the passed time to 0 will force the current refresh to continue and reset the stored last_run timestamp with the current one
                        Duration::from_secs(0)
                    });
                let wait_duration = rate_limiter.wait_duration();
                if passed_time <= wait_duration {
                    debug!("Skipping slot refresh as the wait duration hasn't yet passed. Passed time = {:?},
                            Wait duration = {:?}", passed_time, wait_duration);
                    should_refresh_slots = false;
                }
            }
        }

        let mut res = Ok(());
        if should_refresh_slots {
            let retry_strategy = ExponentialFactorBackoff::from_millis(
                DEFAULT_REFRESH_SLOTS_RETRY_BASE_DURATION_MILLIS,
                DEFAULT_REFRESH_SLOTS_RETRY_BASE_FACTOR,
            )
            .map(jitter_range(0.8, 1.2))
            .take(DEFAULT_NUMBER_OF_REFRESH_SLOTS_RETRIES);
            let retries_counter = AtomicUsize::new(0);
            res = Retry::spawn(retry_strategy, || async {
                let curr_retry = retries_counter.fetch_add(1, atomic::Ordering::Relaxed);
                Self::refresh_slots(inner.clone(), curr_retry)
                    .await
                    .map_err(|err| {
                        if err.kind() == ErrorKind::AllConnectionsUnavailable {
                            RetryError::permanent(err)
                        } else {
                            RetryError::transient(err)
                        }
                    })
            })
            .await;
        }
        in_progress.store(false, Ordering::Relaxed);

        Self::refresh_pubsub_subscriptions(inner).await;

        res
    }

    /// Determines if the cluster topology has changed and refreshes slots and subscriptions if needed.
    /// Returns `RedisResult` with `true` if changes were detected and slots were refreshed,
    /// or `false` if no changes were found. Raises an error if refreshing the topology fails.
    pub(crate) async fn check_topology_and_refresh_if_diff(
        inner: Arc<InnerCore<C>>,
        policy: &RefreshPolicy,
    ) -> RedisResult<bool> {
        let topology_changed = Self::check_for_topology_diff(inner.clone()).await;
        if topology_changed {
            Self::refresh_slots_and_subscriptions_with_retries(inner.clone(), policy).await?;
        }
        Ok(topology_changed)
    }

    async fn periodic_topology_check(inner: Arc<InnerCore<C>>, interval_duration: Duration) {
        loop {
            let _ = boxed_sleep(interval_duration).await;
            // Check and refresh topology if needed
            let should_refresh_pubsub = match Self::check_topology_and_refresh_if_diff(
                inner.clone(),
                &RefreshPolicy::Throttable,
            )
            .await
            {
                Ok(topology_changed) => !topology_changed,
                Err(err) => {
                    warn!(
                        "Failed to refresh slots during periodic topology checks:\n{:?}",
                        err
                    );
                    true
                }
            };

            // Refresh pubsub subscriptions if topology wasn't changed or an error occurred.
            // This serves as a safety measure for validating pubsub subscriptions state in case it has drifted
            // while topology stayed the same.
            // For example, a failed attempt to refresh a connection which is triggered from refresh_pubsub_subscriptions(),
            // might leave a node unconnected indefinitely in case topology is stable and no request are attempted to this node.
            if should_refresh_pubsub {
                Self::refresh_pubsub_subscriptions(inner.clone()).await;
            }
        }
    }

    async fn connections_validation_task(inner: Arc<InnerCore<C>>, interval_duration: Duration) {
        loop {
            if let Some(disconnect_notifier) =
                inner.glide_connection_options.disconnect_notifier.clone()
            {
                disconnect_notifier
                    .wait_for_disconnect_with_timeout(&interval_duration)
                    .await;
            } else {
                let _ = boxed_sleep(interval_duration).await;
            }

            Self::validate_all_user_connections(inner.clone()).await;
        }
    }

    async fn refresh_pubsub_subscriptions(inner: Arc<InnerCore<C>>) {
        if inner.cluster_params.read().expect(MUTEX_READ_ERR).protocol
            != crate::types::ProtocolVersion::RESP3
        {
            return;
        }

        let mut addrs_to_refresh: HashSet<String> = HashSet::new();
        {
            let mut subs_by_address_guard = inner.subscriptions_by_address.write().await;
            let mut unassigned_subs_guard = inner.unassigned_subscriptions.write().await;
            let conns_read_guard = inner.conn_lock.read().expect(MUTEX_READ_ERR);
            // validate active subscriptions location
            subs_by_address_guard.retain(|current_address, address_subs| {
                address_subs.retain(|kind, channels_patterns| {
                    channels_patterns.retain(|channel_pattern| {
                        let new_slot = get_slot(channel_pattern);
                        let valid = if let Some((new_address, _)) = conns_read_guard
                            .connection_for_route(&Route::new(new_slot, SlotAddr::Master))
                        {
                            *new_address == *current_address
                        } else {
                            false
                        };
                        // no new address or new address differ - move to unassigned and store this address for connection reset
                        if !valid {
                            // need to drop the original connection for clearing the subscription in the server, avoiding possible double-receivers
                            if conns_read_guard
                                .connection_for_address(current_address)
                                .is_some()
                            {
                                addrs_to_refresh.insert(current_address.clone());
                            }

                            unassigned_subs_guard
                                .entry(*kind)
                                .and_modify(|channels_patterns| {
                                    channels_patterns.insert(channel_pattern.clone());
                                })
                                .or_insert(HashSet::from([channel_pattern.clone()]));
                        }
                        valid
                    });
                    !channels_patterns.is_empty()
                });
                !address_subs.is_empty()
            });

            // try to assign new addresses
            unassigned_subs_guard.retain(|kind: &PubSubSubscriptionKind, channels_patterns| {
                channels_patterns.retain(|channel_pattern| {
                    let new_slot = get_slot(channel_pattern);
                    if let Some((new_address, _)) = conns_read_guard
                        .connection_for_route(&Route::new(new_slot, SlotAddr::Master))
                    {
                        // need to drop the new connection so the subscription will be picked up in setup_connection()
                        addrs_to_refresh.insert(new_address.clone());

                        let e = subs_by_address_guard
                            .entry(new_address.clone())
                            .or_insert(PubSubSubscriptionInfo::new());

                        e.entry(*kind)
                            .or_insert(HashSet::new())
                            .insert(channel_pattern.clone());

                        return false;
                    }
                    true
                });
                !channels_patterns.is_empty()
            });
        }

        if !addrs_to_refresh.is_empty() {
            // immediately trigger connection reestablishment
            Self::refresh_and_update_connections(
                inner.clone(),
                addrs_to_refresh,
                RefreshConnectionType::AllConnections,
                false,
            )
            .await;
        }
    }

    /// Queries log2n nodes (where n represents the number of cluster nodes) to determine whether their
    /// topology view differs from the one currently stored in the connection manager.
    /// Returns true if change was detected, otherwise false.
    async fn check_for_topology_diff(inner: Arc<InnerCore<C>>) -> bool {
        let num_of_nodes = inner.conn_lock.read().expect(MUTEX_READ_ERR).len();
        let num_of_nodes_to_query =
            std::cmp::max(num_of_nodes.checked_ilog2().unwrap_or(0) as usize, 1);
        let (res, failed_connections) = calculate_topology_from_random_nodes(
            &inner,
            num_of_nodes_to_query,
            DEFAULT_NUMBER_OF_REFRESH_SLOTS_RETRIES,
        )
        .await;

        if let Ok((_, found_topology_hash)) = res {
            if inner
                .conn_lock
                .read()
                .expect(MUTEX_READ_ERR)
                .get_current_topology_hash()
                != found_topology_hash
            {
                return true;
            }
        }

        if !failed_connections.is_empty() {
            trace!("check_for_topology_diff: calling trigger_refresh_connection_tasks");
            Self::trigger_refresh_connection_tasks(
                inner,
                failed_connections,
                RefreshConnectionType::OnlyManagementConnection,
                true,
            )
            .await;
        }

        false
    }

    async fn refresh_slots(inner: Arc<InnerCore<C>>, curr_retry: usize) -> RedisResult<()> {
        // Update the slot refresh last run timestamp
        let now = SystemTime::now();
        let mut last_run_wlock = inner.slot_refresh_state.last_run.write().await;
        *last_run_wlock = Some(now);
        drop(last_run_wlock);
        Self::refresh_slots_inner(inner, curr_retry).await
    }

    // Query a node to discover slot-> master mappings
    async fn refresh_slots_inner(inner: Arc<InnerCore<C>>, curr_retry: usize) -> RedisResult<()> {
        let num_of_nodes = inner.conn_lock.read().expect(MUTEX_READ_ERR).len();
        const MAX_REQUESTED_NODES: usize = 10;
        let num_of_nodes_to_query = std::cmp::min(num_of_nodes, MAX_REQUESTED_NODES);
        let (new_slots, topology_hash) =
            calculate_topology_from_random_nodes(&inner, num_of_nodes_to_query, curr_retry)
                .await
                .0?;
        // Create a new connection vector of the found nodes
        let nodes = new_slots.all_node_addresses();
        let nodes_len = nodes.len();
        let addresses_and_connections_iter = stream::iter(nodes)
            .fold(
                Vec::with_capacity(nodes_len),
                |mut addrs_and_conns, addr| {
                    let inner = inner.clone();
                    async move {
                        let addr = addr.to_string();
                        if let Some(node) = inner
                            .conn_lock
                            .read()
                            .expect(MUTEX_READ_ERR)
                            .node_for_address(addr.as_str())
                        {
                            addrs_and_conns.push((addr, Some(node)));
                            return addrs_and_conns;
                        }
                        // If it's a DNS endpoint, it could have been stored in the existing connections vector using the resolved IP address instead of the DNS endpoint's name.
                        // We shall check if a connection is already exists under the resolved IP name.
                        let Some((host, port)) = get_host_and_port_from_addr(&addr) else {
                            addrs_and_conns.push((addr, None));
                            return addrs_and_conns;
                        };
                        let conn = get_socket_addrs(host, port)
                            .await
                            .ok()
                            .map(|mut socket_addresses| {
                                socket_addresses.find_map(|addr| {
                                    inner
                                        .conn_lock
                                        .read()
                                        .expect(MUTEX_READ_ERR)
                                        .node_for_address(&addr.to_string())
                                })
                            })
                            .unwrap_or(None);
                        addrs_and_conns.push((addr, conn));
                        addrs_and_conns
                    }
                },
            )
            .await;
        let new_connections: ConnectionMap<C> = stream::iter(addresses_and_connections_iter)
            .fold(
                ConnectionsMap(DashMap::with_capacity(nodes_len)),
                |connections, (addr, node)| async {
                    let mut cluster_params = inner
                        .get_cluster_param(|params| params.clone())
                        .expect(MUTEX_READ_ERR);
                    let subs_guard = inner.subscriptions_by_address.read().await;
                    cluster_params.pubsub_subscriptions = subs_guard.get(&addr).cloned();
                    drop(subs_guard);
                    let node = get_or_create_conn(
                        &addr,
                        node,
                        &cluster_params,
                        RefreshConnectionType::AllConnections,
                        inner.glide_connection_options.clone(),
                    )
                    .await;
                    if let Ok(node) = node {
                        connections.0.insert(addr, node);
                    }
                    connections
                },
            )
            .await;

        info!("refresh_slots found nodes:\n{new_connections}");
        // Reset the current slot map and connection vector with the new ones
        let mut write_guard = inner.conn_lock.write().expect(MUTEX_WRITE_ERR);
        // Clear the refresh tasks of the prev instance
        // TODO - Maybe we can take the running refresh tasks and use them instead of running new connection creation
        write_guard.refresh_conn_state.clear_refresh_state();
        let read_from_replicas = inner
            .get_cluster_param(|params| params.read_from_replicas.clone())
            .expect(MUTEX_READ_ERR);
        *write_guard = ConnectionsContainer::new(
            new_slots,
            new_connections,
            read_from_replicas,
            topology_hash,
        );
        Ok(())
    }

    /// Handles MOVED errors by updating the client's slot and node mappings based on the new primary's role:
    ///
    /// 1. **No Change**: If the new primary is already the current slot owner, no updates are needed.
    /// 2. **Failover**: If the new primary is a replica within the same shard (indicating a failover),
    ///    the slot ownership is updated by promoting the replica to the primary in the existing shard addresses.
    /// 3. **Slot Migration**: If the new primary is an existing primary in another shard, this indicates a slot migration,
    ///    and the slot mapping is updated to point to the new shard addresses.
    /// 4. **Replica Moved to a Different Shard**: If the new primary is a replica in a different shard, it can be due to:
    ///    - The replica became the primary of its shard after a failover, with new slots migrated to it.
    ///    - The replica has moved to a different shard as the primary.
    ///      Since further information is unknown, the replica is removed from its original shard and added as the primary of a new shard.
    /// 5. **New Node**: If the new primary is unknown, it is added as a new node in a new shard, possibly indicating scale-out.
    ///
    /// # Arguments
    /// * `inner` - Shared reference to InnerCore containing connection and slot state.
    /// * `slot` - The slot number reported as moved.
    /// * `new_primary` - The address of the node now responsible for the slot.
    ///
    /// # Returns
    /// * `RedisResult<()>` indicating success or failure in updating slot mappings.
    async fn update_upon_moved_error(
        inner: Arc<InnerCore<C>>,
        slot: u16,
        new_primary: Arc<String>,
    ) -> RedisResult<()> {
        let curr_shard_addrs = inner
            .conn_lock
            .read()
            .expect(MUTEX_READ_ERR)
            .slot_map
            .shard_addrs_for_slot(slot);
        // let curr_shard_addrs = connections_container.slot_map.shard_addrs_for_slot(slot);
        // Check if the new primary is part of the current shard and update if required
        if let Some(curr_shard_addrs) = curr_shard_addrs {
            match curr_shard_addrs.attempt_shard_role_update(new_primary.clone()) {
                // Scenario 1: No changes needed as the new primary is already the current slot owner.
                // Scenario 2: Failover occurred and the new primary was promoted from a replica.
                ShardUpdateResult::AlreadyPrimary | ShardUpdateResult::Promoted => return Ok(()),
                // The node was not found in this shard, proceed with further scenarios.
                ShardUpdateResult::NodeNotFound => {}
            }
        }

        // Scenario 3 & 4: Check if the new primary exists in other shards

        let mut wlock_conn_container = inner.conn_lock.write().expect(MUTEX_READ_ERR);
        let mut nodes_iter = wlock_conn_container.slot_map_nodes();
        for (node_addr, shard_addrs_arc) in &mut nodes_iter {
            if node_addr == new_primary {
                let is_existing_primary = shard_addrs_arc.primary().eq(&new_primary);
                if is_existing_primary {
                    // Scenario 3: Slot Migration - The new primary is an existing primary in another shard
                    // Update the associated addresses for `slot` to `shard_addrs`.
                    drop(nodes_iter);
                    return wlock_conn_container
                        .slot_map
                        .update_slot_range(slot, shard_addrs_arc.clone());
                } else {
                    // Scenario 4: The MOVED error redirects to `new_primary` which is known as a replica in a shard that doesn’t own `slot`.
                    // Remove the replica from its existing shard and treat it as a new node in a new shard.
                    shard_addrs_arc.remove_replica(new_primary.clone())?;
                    drop(nodes_iter);
                    return wlock_conn_container
                        .slot_map
                        .add_new_primary(slot, new_primary);
                }
            }
        }

        drop(nodes_iter);
        // Scenario 5: New Node - The new primary is not present in the current slots map, add it as a primary of a new shard.
        wlock_conn_container
            .slot_map
            .add_new_primary(slot, new_primary)
    }

    async fn execute_on_multiple_nodes<'a>(
        cmd: &'a Arc<Cmd>,
        routing: &'a MultipleNodeRoutingInfo,
        core: Core<C>,
        response_policy: Option<ResponsePolicy>,
    ) -> OperationResult {
        trace!("execute_on_multiple_nodes");

        // This function maps the connections to senders & receivers of one-shot channels, and the receivers are mapped to `PendingRequest`s.
        // This allows us to pass the new `PendingRequest`s to `try_request`, while letting `execute_on_multiple_nodes` wait on the receivers
        // for all of the individual requests to complete.
        #[allow(clippy::type_complexity)] // The return value is complex, but indentation and linebreaks make it human readable.
        fn into_channels<C>(
            iterator: impl Iterator<
                Item = Option<(Arc<Cmd>, ConnectionAndAddress<ConnectionFuture<C>>)>,
            >,
        ) -> (
            Vec<(Option<String>, Receiver<Result<Response, RedisError>>)>,
            Vec<Option<PendingRequest<C>>>,
        ) {
            iterator
                .map(|tuple_opt| {
                    let (sender, receiver) = oneshot::channel();
                    if let Some((cmd, conn, address)) =
                        tuple_opt.map(|(cmd, (address, conn))| (cmd, conn, address))
                    {
                        (
                            (Some(address.clone()), receiver),
                            Some(PendingRequest {
                                retry: 0,
                                sender,
                                info: RequestInfo {
                                    cmd: CmdArg::Cmd {
                                        cmd,
                                        routing: InternalSingleNodeRouting::Connection {
                                            address,
                                            conn,
                                        }
                                        .into(),
                                    },
                                },
                            }),
                        )
                    } else {
                        let _ = sender.send(Err((
                            ErrorKind::ConnectionNotFoundForRoute,
                            "Connection not found",
                        )
                            .into()));
                        ((None, receiver), None)
                    }
                })
                .unzip()
        }
        let (receivers, requests): (Vec<_>, Vec<_>);
        {
            let connections_container = core.conn_lock.read().expect(MUTEX_READ_ERR);
            if connections_container.is_empty() {
                return OperationResult::Err((
                    OperationTarget::FanOut,
                    (
                        ErrorKind::AllConnectionsUnavailable,
                        "No connections found for multi-node operation",
                    )
                        .into(),
                ));
            }

            (receivers, requests) = match routing {
                MultipleNodeRoutingInfo::AllNodes => into_channels(
                    connections_container
                        .all_node_connections()
                        .map(|tuple| Some((cmd.clone(), tuple))),
                ),
                MultipleNodeRoutingInfo::AllMasters => into_channels(
                    connections_container
                        .all_primary_connections()
                        .map(|tuple| Some((cmd.clone(), tuple))),
                ),
                MultipleNodeRoutingInfo::MultiSlot((slots, _)) => {
                    into_channels(slots.iter().map(|(route, indices)| {
                        connections_container
                            .connection_for_route(route)
                            .map(|tuple| {
                                let new_cmd =
                                    crate::cluster_routing::command_for_multi_slot_indices(
                                        cmd.as_ref(),
                                        indices.iter(),
                                    );
                                (Arc::new(new_cmd), tuple)
                            })
                    }))
                }
            };
        }
        core.pending_requests
            .lock()
            .unwrap()
            .extend(requests.into_iter().flatten());

        Self::aggregate_results(receivers, routing, response_policy)
            .await
            .map(Response::Single)
            .map_err(|err| (OperationTarget::FanOut, err))
    }

    pub(crate) async fn try_cmd_request(
        cmd: Arc<Cmd>,
        routing: InternalRoutingInfo<C>,
        core: Core<C>,
    ) -> OperationResult {
        let routing = match routing {
            // commands that are sent to multiple nodes are handled here.
            InternalRoutingInfo::MultiNode((multi_node_routing, response_policy)) => {
                return Self::execute_on_multiple_nodes(
                    &cmd,
                    &multi_node_routing,
                    core,
                    response_policy,
                )
                .await;
            }

            InternalRoutingInfo::SingleNode(routing) => routing,
        };
        trace!("route request to single node");

        // if we reached this point, we're sending the command only to single node, and we need to find the
        // right connection to the node.
        let (address, mut conn) = Self::get_connection(routing, core, Some(cmd.clone()))
            .await
            .map_err(|err| (OperationTarget::NotFound, err))?;
        conn.req_packed_command(&cmd)
            .await
            .map(Response::Single)
            .map_err(|err| (address.into(), err))
    }

    async fn try_pipeline_request(
        pipeline: Arc<crate::Pipeline>,
        offset: usize,
        count: usize,
        conn: impl Future<Output = RedisResult<(String, C)>>,
    ) -> OperationResult {
        trace!("try_pipeline_request");
        let (address, mut conn) = conn.await.map_err(|err| (OperationTarget::NotFound, err))?;
        conn.req_packed_commands(&pipeline, offset, count, None)
            .await
            .map(Response::Multiple)
            .map_err(|err| (OperationTarget::Node { address }, err))
    }

    async fn try_request(info: RequestInfo<C>, core: Core<C>) -> OperationResult {
        match info.cmd {
            CmdArg::Cmd { cmd, routing } => Self::try_cmd_request(cmd, routing, core).await,
            CmdArg::Pipeline {
                pipeline,
                offset,
                count,
                route,
                sub_pipeline,
                pipeline_retry_strategy,
            } => {
                if pipeline.is_atomic() || sub_pipeline {
                    // If the pipeline is atomic (i.e., a transaction) or if the pipeline is already splitted into sub-pipelines (i.e., the pipeline is already routed to a specific node), we can send it as is, with no need to split it into sub-pipelines.
                    Self::try_pipeline_request(
                        pipeline,
                        offset,
                        count,
                        Self::get_connection(
                            route.unwrap_or(InternalSingleNodeRouting::Random),
                            core,
                            None,
                        ),
                    )
                    .await
                } else {
                    // The pipeline is not atomic and not already splitted, we need to split it into sub-pipelines and send them separately.
                    Self::handle_non_atomic_pipeline_request(
                        pipeline,
                        core,
                        0,
                        pipeline_retry_strategy,
                        route,
                    )
                    .await
                }
            }
            CmdArg::ClusterScan {
                cluster_scan_args, ..
            } => {
                let core = core;
                let scan_result = cluster_scan(core, cluster_scan_args).await;
                match scan_result {
                    Ok((scan_state_ref, values)) => {
                        Ok(Response::ClusterScanResult(scan_state_ref, values))
                    }
                    // TODO: After routing issues with sending to random node on not-key based commands are resolved,
                    // this error should be handled in the same way as other errors and not fan-out.
                    Err(err) => Err((OperationTarget::FanOut, err)),
                }
            }
            CmdArg::OperationRequest(operation_request) => match operation_request {
                Operation::UpdateConnectionPassword(password) => {
                    core.set_cluster_param(|params| params.password = password)
                        .expect(MUTEX_WRITE_ERR);
                    Ok(Response::Single(Value::Okay))
                }
                Operation::UpdateConnectionDatabase(database_id) => {
                    core.set_cluster_param(|params| params.database_id = database_id)
                        .expect(MUTEX_WRITE_ERR);
                    Ok(Response::Single(Value::Okay))
                }
                Operation::UpdateConnectionClientName(client_name) => {
                    core.set_cluster_param(|params| params.client_name = client_name)
                        .expect(MUTEX_WRITE_ERR);
                    Ok(Response::Single(Value::Okay))
                }
                Operation::GetUsername => {
                    let username = match core
                        .get_cluster_param(|params| params.username.clone())
                        .expect(MUTEX_READ_ERR)
                    {
                        Some(username) => Value::SimpleString(username),
                        None => Value::Nil,
                    };
                    Ok(Response::Single(username))
                }
            },
        }
    }

    /// Handles the execution of a non-atomic pipeline request by splitting it into sub-pipelines and sending them to the appropriate cluster nodes.
    ///
    /// This function distributes the commands in the pipeline across the cluster nodes based on routing information, collects the responses,
    /// and aggregates them if necessary according to the specified response policies.
    async fn handle_non_atomic_pipeline_request(
        pipeline: Arc<crate::Pipeline>,
        core: Core<C>,
        retry: u32,
        pipeline_retry_strategy: PipelineRetryStrategy,
        route: Option<InternalSingleNodeRouting<C>>,
    ) -> OperationResult {
        // Distribute pipeline commands across cluster nodes based on routing information.
        // Returns:
        // - pipelines_by_node: Map of node addresses to their pipeline contexts
        // - response_policies: Map of routing info and response aggregation policies for multi-node commands (by command's index).
        let (pipelines_by_node, mut response_policies) =
            map_pipeline_to_nodes(&pipeline, core.clone(), route).await?;

        // Send the requests to each node and collect the responses
        // Returns a tuple containing:
        // - A vector of results for each sub-pipeline execution.
        // - A vector of (address, indices, ignore) tuples indicating where each response should be placed, or if the response should be ignored (e.g. ASKING command).
        let (responses, addresses_and_indices) = collect_and_send_pending_requests(
            pipelines_by_node,
            core.clone(),
            retry,
            pipeline_retry_strategy,
        )
        .await;

        // Process the responses and update the pipeline_responses, retrying the commands if needed.
        let pipeline_responses = process_and_retry_pipeline_responses(
            responses,
            addresses_and_indices,
            &pipeline,
            core,
            &mut response_policies,
            pipeline_retry_strategy,
        )
        .await?;

        // Process response policies after all tasks are complete and aggregate the relevant commands.
        Ok(Response::Multiple(
            Self::aggregate_pipeline_multi_node_commands(pipeline_responses, response_policies)
                .await,
        ))
    }

    /// Aggregates pipeline responses for multi-node commands and produces a final vector of responses.
    ///
    /// Pipeline commands with multi-node routing info, will be splitted into multiple pipelines, therefore, after executing each pipeline and storing the results in `pipeline_responses`,
    /// the multi-node commands will contain more than one response (one for each sub-pipeline that contained the command). This responses must be aggregated into a single response, based on the proper response policy.
    ///
    /// This function processes the provided `response_policies`, which  is a sorted (by command index) vector containing, for each multi-node command:
    /// - The index of the command in the pipeline.
    /// - The routing information (`MultipleNodeRoutingInfo`) for that command.
    /// - An optional `ResponsePolicy` specifying how to aggregate the responses (e.g., sum, all succeeded).
    ///
    /// For each command:
    /// - If a response policy exists for that command, the function aggregates the multiple responses
    ///   (collected from the sub-pipelines) into a single response using the provided routing info and response policy.
    /// - If no response policy is provided, the function simply takes the last response (which is a single response, removing its node address).
    ///
    /// The aggregated result replaces the multiple responses for each command, ensuring that every entry in
    /// the final output vector corresponds to a single, aggregated response for the original pipeline command.
    ///
    /// # Arguments
    ///
    /// * `pipeline_responses` - A vector of vectors, where each inner vector holds tuples of
    ///   `(Value, String)`, representing the responses from the sub-pipelines along with their node addresses.
    /// * `response_policies` - A `ResponsePoliciesMap` containing:
    ///     - An entry of the index of the command in the pipeline with multi-node routing information.
    ///     - The routing information (`MultipleNodeRoutingInfo`) for the command.
    ///     - An optional `ResponsePolicy` that dictates how the responses should be aggregated.
    ///
    /// # Returns
    ///
    /// * `Vec<Value>` - A vector of aggregated responses, one for each command in the original pipeline.
    ///
    /// # Example
    /// ```rust,compile_fail
    /// // Example pipeline responses for multi-node commands:
    /// let mut pipeline_responses = vec![
    ///     vec![(Value::Int(1), "node1".to_string()), (Value::Int(2), "node2".to_string()), (Value::Int(3), "node3".to_string())], // represents `DBSIZE command split across nodes
    ///     vec![(Value::Int(3), "node3".to_string())],
    ///     vec![(Value::SimpleString("PONG".to_string()), "node1".to_string()), (Value::SimpleString("PONG".to_string()), "node2".to_string()), (Value::SimpleString("PONG".to_string()), "node3".to_string())], // represents `PING` command split across nodes
    /// ];
    ///
    /// let response_policies = HashMap::from([
    ///     (0, (MultipleNodeRoutingInfo::AllNodes, Some(ResponsePolicy::Aggregate(AggregateOp::Sum)))),
    ///     (2, (MultipleNodeRoutingInfo::AllNodes, Some(ResponsePolicy::AllSucceeded))),
    /// ]);
    ///
    /// // Aggregation of responses
    /// let final_responses = aggregate_pipeline_multi_node_commands(pipeline_responses, response_policies).await.unwrap();
    ///
    /// // After aggregation, each command has a single aggregated response:
    /// assert_eq!(final_responses[0], Value::Int(6)); // Sum of 1+2+3
    /// assert_eq!(final_responses[1], Value::Int(3));
    /// assert_eq!(final_responses[2], Value::SimpleString("PONG".to_string()));
    /// ```
    async fn aggregate_pipeline_multi_node_commands(
        pipeline_responses: PipelineResponses,
        response_policies: ResponsePoliciesMap,
    ) -> Vec<Value> {
        let mut final_responses = Vec::with_capacity(pipeline_responses.len());

        for (index, mut responses) in pipeline_responses.into_iter().enumerate() {
            // If the first policy in the sorted vector matches the current command index, use it.
            if let Some(&(ref routing_info, response_policy)) = response_policies.get(&index) {
                let aggregated_response = match Self::aggregate_resolved_results(
                    responses,
                    routing_info,
                    response_policy,
                ) {
                    Ok(value) => value,
                    Err(err) => Value::ServerError(err.into()),
                };

                final_responses.push(aggregated_response);
            } else {
                // If there's no policy for this index, use the first response if available.
                if responses.len() == 1 {
                    final_responses.push(responses.pop().unwrap().1);
                } else {
                    final_responses.push(Value::ServerError(ServerError::ExtensionError {
                        code: "PipelineResponseError".to_string(),
                        detail: Some(format!(
                            "Expected exactly one response for command {}, got {}",
                            index,
                            responses.len(),
                        )),
                    }));
                }
            }
        }

        final_responses
    }

    async fn get_connection(
        routing: InternalSingleNodeRouting<C>,
        core: Core<C>,
        cmd: Option<Arc<Cmd>>,
    ) -> RedisResult<(String, C)> {
        let mut asking = false;

        let conn_check = match routing {
            InternalSingleNodeRouting::Redirect {
                redirect: Redirect::Moved(moved_addr),
                ..
            } => core
                .conn_lock
                .read()
                .expect(MUTEX_READ_ERR)
                .connection_for_address(moved_addr.as_str())
                .map_or(
                    ConnectionCheck::OnlyAddress(moved_addr),
                    ConnectionCheck::Found,
                ),
            InternalSingleNodeRouting::Redirect {
                redirect: Redirect::Ask(ask_addr, should_exec_asking),
                ..
            } => {
                asking = should_exec_asking;
                core.conn_lock
                    .read()
                    .expect(MUTEX_READ_ERR)
                    .connection_for_address(ask_addr.as_str())
                    .map_or(
                        ConnectionCheck::OnlyAddress(ask_addr),
                        ConnectionCheck::Found,
                    )
            }
            InternalSingleNodeRouting::SpecificNode(route) => {
                // Step 1: Attempt to get the connection directly using the route.
                let conn_check = {
                    let conn_lock = core.conn_lock.read().expect(MUTEX_READ_ERR);
                    conn_lock
                        .connection_for_route(&route)
                        .map(ConnectionCheck::Found)
                };

                if let Some(conn_check) = conn_check {
                    conn_check
                } else {
                    // Step 2: Handle cases where no connection is found for the route.
                    // - For key-based commands, attempt redirection to a random node,
                    //   hopefully to be redirected afterwards by a MOVED error.
                    // - For non-key-based commands, avoid attempting redirection to a random node
                    //   as it wouldn't result in MOVED hints and can lead to unwanted results
                    //   (e.g., sending management command to a different node than the user asked for); instead, raise the error.
                    let mut conn_check = ConnectionCheck::RandomConnection;

                    let routable_cmd = cmd.and_then(|cmd| Routable::command(&*cmd));
                    if routable_cmd.is_some()
                        && !RoutingInfo::is_key_routing_command(&routable_cmd.unwrap())
                    {
                        return Err((
                            ErrorKind::ConnectionNotFoundForRoute,
                            "Requested connection not found for route",
                            format!("{route:?}"),
                        )
                            .into());
                    }

                    debug!(
                        "SpecificNode: No connection found for route `{route:?}`.
                        Checking for reconnect tasks before redirecting to a random node."
                    );

                    // Step 3: Obtain the reconnect notifier, ensuring the lock is released immediately after.
                    let reconnect_notifier = {
                        let conn_lock = core.conn_lock.read().expect(MUTEX_READ_ERR);
                        conn_lock.notifier_for_route(&route).clone()
                    };

                    // Step 4: If a notifier exists, wait for it to signal completion.
                    if let Some(notifier) = reconnect_notifier {
                        debug!(
                            "SpecificNode: Waiting on reconnect notifier for route `{route:?}`."
                        );

                        notifier.notified().await;

                        debug!(
                            "SpecificNode: Finished waiting on notifier for route `{route:?}`. Retrying connection lookup."
                        );

                        // Step 5: Retry the connection lookup after waiting for the reconnect task.
                        if let Some((conn, address)) = core
                            .conn_lock
                            .read()
                            .expect(MUTEX_READ_ERR)
                            .connection_for_route(&route)
                        {
                            conn_check = ConnectionCheck::Found((conn, address));
                        } else {
                            debug!(
                                "SpecificNode: No connection found for route `{route:?}` after waiting on reconnect notifier. Proceeding to random node."
                            );
                        }
                    } else {
                        debug!(
                            "SpecificNode: No active reconnect task for route `{route:?}`. Proceeding to random node."
                        );
                    }

                    conn_check
                }
            }
            InternalSingleNodeRouting::Random => ConnectionCheck::RandomConnection,
            InternalSingleNodeRouting::Connection { address, conn } => {
                return Ok((address, conn.await));
            }
            InternalSingleNodeRouting::ByAddress(address) => {
                let conn_option = core
                    .conn_lock
                    .read()
                    .expect(MUTEX_READ_ERR)
                    .connection_for_address(&address);
                if let Some((address, conn)) = conn_option {
                    return Ok((address, conn.await));
                } else {
                    return Err((
                        ErrorKind::ConnectionNotFoundForRoute,
                        "Requested connection not found",
                        address,
                    )
                        .into());
                }
            }
        };

        let (address, mut conn) = match conn_check {
            ConnectionCheck::Found((address, connection)) => (address, connection.await),
            ConnectionCheck::OnlyAddress(address) => {
                // Trigger refresh task and get the single notifier
                let mut notifiers = Self::trigger_refresh_connection_tasks(
                    core.clone(),
                    HashSet::from([address.clone()]),
                    RefreshConnectionType::AllConnections,
                    false,
                )
                .await;

                // Extract the single notifier (if any)
                if let Some(refresh_notifier) = notifiers.pop() {
                    debug!(
                        "get_connection: Waiting on the refresh notifier for address: {}",
                        address
                    );
                    // Wait for the refresh task to notify that it's done reconnecting (or transitioning).
                    refresh_notifier.notified().await;
                    debug!(
                        "get_connection: After waiting on the refresh notifier for address: {}",
                        address
                    );
                } else {
                    debug!(
                        "get_connection: No notifier to wait on for address: {}",
                        address
                    );
                }

                // Try fetching the connection after the notifier resolves
                let conn_option = core
                    .conn_lock
                    .read()
                    .expect(MUTEX_READ_ERR)
                    .connection_for_address(&address);

                if let Some((address, conn)) = conn_option {
                    debug!("get_connection: Connection found for address: {}", address);
                    (address, conn.await)
                } else {
                    return Err((
                        ErrorKind::ConnectionNotFoundForRoute,
                        "Requested connection not found",
                        address,
                    )
                        .into());
                }
            }
            ConnectionCheck::RandomConnection => {
                let random_conn = core
                    .conn_lock
                    .read()
                    .expect(MUTEX_READ_ERR)
                    .random_connections(1, ConnectionType::User);
                let (random_address, random_conn_future) =
                    match random_conn.and_then(|conn_iter| conn_iter.into_iter().next()) {
                        Some((address, future)) => (address, future),
                        None => {
                            return Err(RedisError::from((
                                ErrorKind::AllConnectionsUnavailable,
                                "No random connection found",
                            )));
                        }
                    };

                (random_address, random_conn_future.await)
            }
        };

        if asking {
            let _ = conn.req_packed_command(&crate::cmd::cmd("ASKING")).await;
        }
        Ok((address, conn))
    }

    fn poll_recover(&mut self, cx: &mut task::Context<'_>) -> Poll<Result<(), RedisError>> {
        trace!("entered poll_recover");

        let recover_future = match &mut self.state {
            ConnectionState::PollComplete => return Poll::Ready(Ok(())),
            ConnectionState::Recover(future) => future,
        };

        match recover_future {
            RecoverFuture::RefreshingSlots(handle) => {
                // Check if the task has completed
                match handle.now_or_never() {
                    Some(Ok(Ok(()))) => {
                        // Task succeeded
                        trace!("Slot refresh completed successfully!");
                        self.state = ConnectionState::PollComplete;
                        return Poll::Ready(Ok(()));
                    }
                    Some(Ok(Err(e))) => {
                        // Task completed but returned an engine error
                        trace!("Slot refresh failed: {:?}", e);

                        if e.kind() == ErrorKind::AllConnectionsUnavailable {
                            // If all connections unavailable, try reconnect
                            self.state =
                                ConnectionState::Recover(RecoverFuture::ReconnectToInitialNodes(
                                    Box::pin(ClusterConnInner::reconnect_to_initial_nodes(
                                        self.inner.clone(),
                                    )),
                                ));
                            return Poll::Ready(Err(e));
                        } else {
                            // Retry refresh
                            let new_handle = Self::spawn_refresh_slots_task(
                                self.inner.clone(),
                                &RefreshPolicy::Throttable,
                            );
                            self.state = ConnectionState::Recover(RecoverFuture::RefreshingSlots(
                                new_handle,
                            ));
                            return Poll::Ready(Ok(()));
                        }
                    }
                    Some(Err(join_err)) => {
                        if join_err.is_cancelled() {
                            // Task was intentionally aborted - don't treat as an error
                            trace!("Slot refresh task was aborted");
                            self.state = ConnectionState::PollComplete;
                            return Poll::Ready(Ok(()));
                        } else {
                            // Task panicked - try reconnecting to initial nodes as a recovery strategy
                            warn!("Slot refresh task panicked: {:?} - attempting recovery by reconnecting to initial nodes", join_err);

                            // TODO - consider a gracefully closing of the client
                            // Since a panic indicates a bug in the refresh logic,
                            // it might be safer to close the client entirely
                            self.state =
                                ConnectionState::Recover(RecoverFuture::ReconnectToInitialNodes(
                                    Box::pin(ClusterConnInner::reconnect_to_initial_nodes(
                                        self.inner.clone(),
                                    )),
                                ));

                            // Report this critical error to clients
                            let err = RedisError::from((
                                ErrorKind::ClientError,
                                "Slot refresh task panicked",
                                format!("{join_err:?}"),
                            ));
                            return Poll::Ready(Err(err));
                        }
                    }
                    None => {
                        // Task is still running
                        // Just continue and return Ok to not block poll_flush
                    }
                }

                // Always return Ready to not block poll_flush
                Poll::Ready(Ok(()))
            }
            // Other cases remain unchanged
            RecoverFuture::ReconnectToInitialNodes(ref mut future) => {
                ready!(future.as_mut().poll(cx));
                trace!("Reconnected to initial nodes");
                self.state = ConnectionState::PollComplete;
                Poll::Ready(Ok(()))
            }
            RecoverFuture::Reconnect(ref mut future) => {
                ready!(future.as_mut().poll(cx));
                trace!("Reconnected connections");
                self.state = ConnectionState::PollComplete;
                Poll::Ready(Ok(()))
            }
        }
    }

    async fn handle_loading_error_and_retry(
        core: Core<C>,
        info: RequestInfo<C>,
        address: String,
        retry: u32,
        retry_params: RetryParams,
    ) -> OperationResult {
        Self::handle_loading_error(core.clone(), address, retry, retry_params).await;
        Self::try_request(info, core).await
    }

    async fn handle_loading_error(
        core: Core<C>,
        address: String,
        retry: u32,
        retry_params: RetryParams,
    ) {
        let is_primary = core
            .conn_lock
            .read()
            .expect(MUTEX_READ_ERR)
            .is_primary(&address);

        if !is_primary {
            // If the connection is a replica, remove the connection and retry.
            // The connection will be established again on the next call to refresh slots once the replica is no longer in loading state.
            core.conn_lock
                .read()
                .expect(MUTEX_READ_ERR)
                .remove_node(&address);
        } else {
            // If the connection is primary, just sleep and retry
            let sleep_duration = retry_params.wait_time_for_retry(retry);
            boxed_sleep(sleep_duration).await;
        }
    }

    fn poll_complete(&mut self, cx: &mut task::Context<'_>) -> Poll<PollFlushAction> {
        let retry_params = self
            .inner
            .get_cluster_param(|params| params.retry_params.clone())
            .expect(MUTEX_READ_ERR);
        let mut poll_flush_action = PollFlushAction::None;
        let mut pending_requests_guard = self.inner.pending_requests.lock().unwrap();
        if !pending_requests_guard.is_empty() {
            let mut pending_requests = mem::take(&mut *pending_requests_guard);
            for request in pending_requests.drain(..) {
                // Drop the request if none is waiting for a response to free up resources for
                // requests callers care about (load shedding). It will be ambiguous whether the
                // request actually goes through regardless.
                if request.sender.is_closed() {
                    continue;
                }

                let future = Self::try_request(request.info.clone(), self.inner.clone()).boxed();
                self.in_flight_requests.push(Box::pin(Request {
                    retry_params: retry_params.clone(),
                    request: Some(request),
                    future: RequestState::Future { future },
                }));
            }
            *pending_requests_guard = pending_requests;
        }
        drop(pending_requests_guard);

        loop {
            let retry_params = retry_params.clone();
            let result = match Pin::new(&mut self.in_flight_requests).poll_next(cx) {
                Poll::Ready(Some(result)) => result,
                Poll::Ready(None) | Poll::Pending => break,
            };
            match result {
                Next::Done => {}
                Next::Retry { request } => {
                    let future = Self::try_request(request.info.clone(), self.inner.clone());
                    self.in_flight_requests.push(Box::pin(Request {
                        retry_params: retry_params.clone(),
                        request: Some(request),
                        future: RequestState::Future {
                            future: Box::pin(future),
                        },
                    }));
                }
                Next::RetryBusyLoadingError { request, address } => {
                    // TODO - do we also want to try and reconnect to replica if it is loading?
                    let future = Self::handle_loading_error_and_retry(
                        self.inner.clone(),
                        request.info.clone(),
                        address,
                        request.retry,
                        retry_params.clone(),
                    );
                    self.in_flight_requests.push(Box::pin(Request {
                        retry_params: retry_params.clone(),
                        request: Some(request),
                        future: RequestState::Future {
                            future: Box::pin(future),
                        },
                    }));
                }
                Next::RefreshSlots {
                    request,
                    sleep_duration,
                    moved_redirect,
                } => {
                    poll_flush_action =
                        poll_flush_action.change_state(PollFlushAction::RebuildSlots);
                    let future: Option<
                        RequestState<Pin<Box<dyn Future<Output = OperationResult> + Send>>>,
                    > = if let Some(moved_redirect) = moved_redirect {
                        Some(RequestState::UpdateMoved {
                            future: Box::pin(ClusterConnInner::update_upon_moved_error(
                                self.inner.clone(),
                                moved_redirect.slot,
                                moved_redirect.address.into(),
                            )),
                        })
                    } else if let Some(ref request) = request {
                        match sleep_duration {
                            Some(sleep_duration) => Some(RequestState::Sleep {
                                sleep: boxed_sleep(sleep_duration),
                            }),
                            None => Some(RequestState::Future {
                                future: Box::pin(Self::try_request(
                                    request.info.clone(),
                                    self.inner.clone(),
                                )),
                            }),
                        }
                    } else {
                        None
                    };
                    if let Some(future) = future {
                        self.in_flight_requests.push(Box::pin(Request {
                            retry_params,
                            request,
                            future,
                        }));
                    }
                }
                Next::Reconnect { request, target } => {
                    poll_flush_action = poll_flush_action
                        .change_state(PollFlushAction::Reconnect(HashSet::from_iter([target])));
                    if let Some(request) = request {
                        self.inner.pending_requests.lock().unwrap().push(request);
                    }
                }
                Next::ReconnectToInitialNodes { request } => {
                    poll_flush_action = poll_flush_action
                        .change_state(PollFlushAction::ReconnectFromInitialConnections);
                    if let Some(request) = request {
                        self.inner.pending_requests.lock().unwrap().push(request);
                    }
                }
            }
        }

        if matches!(poll_flush_action, PollFlushAction::None) {
            if self.in_flight_requests.is_empty() {
                Poll::Ready(poll_flush_action)
            } else {
                Poll::Pending
            }
        } else {
            Poll::Ready(poll_flush_action)
        }
    }

    fn send_refresh_error(&mut self) {
        if self.refresh_error.is_some() {
            if let Some(mut request) = Pin::new(&mut self.in_flight_requests)
                .iter_pin_mut()
                .find(|request| request.request.is_some())
            {
                (*request)
                    .as_mut()
                    .respond(Err(self.refresh_error.take().unwrap()));
            } else if let Some(request) = self.inner.pending_requests.lock().unwrap().pop() {
                let _ = request.sender.send(Err(self.refresh_error.take().unwrap()));
            }
        }
    }
}

enum PollFlushAction {
    None,
    RebuildSlots,
    Reconnect(HashSet<String>),
    ReconnectFromInitialConnections,
}

impl PollFlushAction {
    fn change_state(self, next_state: PollFlushAction) -> PollFlushAction {
        match (self, next_state) {
            (PollFlushAction::None, next_state) => next_state,
            (next_state, PollFlushAction::None) => next_state,
            (PollFlushAction::ReconnectFromInitialConnections, _)
            | (_, PollFlushAction::ReconnectFromInitialConnections) => {
                PollFlushAction::ReconnectFromInitialConnections
            }

            (PollFlushAction::RebuildSlots, _) | (_, PollFlushAction::RebuildSlots) => {
                PollFlushAction::RebuildSlots
            }

            (PollFlushAction::Reconnect(mut addrs), PollFlushAction::Reconnect(new_addrs)) => {
                addrs.extend(new_addrs);
                Self::Reconnect(addrs)
            }
        }
    }
}

impl<C> Sink<Message<C>> for Disposable<ClusterConnInner<C>>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + Unpin + 'static,
{
    type Error = ();

    fn poll_ready(self: Pin<&mut Self>, _cx: &mut task::Context) -> Poll<Result<(), Self::Error>> {
        Poll::Ready(Ok(()))
    }

    fn start_send(self: Pin<&mut Self>, msg: Message<C>) -> Result<(), Self::Error> {
        let Message { cmd, sender } = msg;

        let info = RequestInfo { cmd };

        self.inner
            .pending_requests
            .lock()
            .unwrap()
            .push(PendingRequest {
                retry: 0,
                sender,
                info,
            });
        Ok(())
    }

    fn poll_flush(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
    ) -> Poll<Result<(), Self::Error>> {
        trace!("poll_flush: {:?}", self.state);
        loop {
            self.send_refresh_error();

            if let Err(err) = ready!(self.as_mut().poll_recover(cx)) {
                // We failed to reconnect, while we will try again we will report the
                // error if we can to avoid getting trapped in an infinite loop of
                // trying to reconnect
                self.refresh_error = Some(err);

                // Give other tasks a chance to progress before we try to recover
                // again. Since the future may not have registered a wake up we do so
                // now so the task is not forgotten
                cx.waker().wake_by_ref();
                return Poll::Pending;
            }

            match ready!(self.poll_complete(cx)) {
                PollFlushAction::None => return Poll::Ready(Ok(())),
                PollFlushAction::RebuildSlots => {
                    // Spawn refresh task
                    let task_handle = ClusterConnInner::spawn_refresh_slots_task(
                        self.inner.clone(),
                        &RefreshPolicy::Throttable,
                    );

                    // Update state
                    self.state =
                        ConnectionState::Recover(RecoverFuture::RefreshingSlots(task_handle));
                }
                PollFlushAction::ReconnectFromInitialConnections => {
                    self.state =
                        ConnectionState::Recover(RecoverFuture::ReconnectToInitialNodes(Box::pin(
                            ClusterConnInner::reconnect_to_initial_nodes(self.inner.clone()),
                        )));
                }
                PollFlushAction::Reconnect(addresses) => {
                    self.state = ConnectionState::Recover(RecoverFuture::Reconnect(Box::pin(
                        ClusterConnInner::trigger_refresh_connection_tasks(
                            self.inner.clone(),
                            addresses,
                            RefreshConnectionType::OnlyUserConnection,
                            true,
                        )
                        .map(|_| ()), // Convert Vec<Arc<Notify>> to () as it's not needed here
                    )));
                }
            }
        }
    }

    fn poll_close(
        mut self: Pin<&mut Self>,
        cx: &mut task::Context,
    ) -> Poll<Result<(), Self::Error>> {
        // Try to drive any in flight requests to completion
        match self.poll_complete(cx) {
            Poll::Ready(PollFlushAction::None) => (),
            Poll::Ready(_) => Err(())?,
            Poll::Pending => (),
        };
        // If we no longer have any requests in flight we are done (skips any reconnection
        // attempts)
        if self.in_flight_requests.is_empty() {
            return Poll::Ready(Ok(()));
        }

        self.poll_flush(cx)
    }
}

// Retrieves random connections from initial seed nodes after resolving their addresses.
async fn get_random_connections_from_initial_nodes<C>(
    inner: &Core<C>,
    num_of_nodes_to_query: usize,
) -> RedisResult<Vec<(String, Shared<Pin<Box<dyn Future<Output = C> + Send>>>)>>
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    // Resolve initial nodes to get their addresses.
    // The resolved addresses are tuples of (host, Option<IpAddr>).
    // Representing the host and its resolved IP address (if available).
    let resolved_addresses =
        ClusterConnInner::<C>::try_to_expand_initial_nodes(&inner.initial_nodes).await;

    // Filter the resolved addresses to keep only those with valid IP addresses.
    let valid_addresses: Vec<String> = resolved_addresses
        .into_iter()
        .filter_map(|(host, socket_addr)| match socket_addr {
            Some(addr) => Some(addr.to_string()),
            None => {
                log_warn("No valid IP address found for host: {}", host);
                None
            }
        })
        .collect();

    if valid_addresses.is_empty() {
        return Err(RedisError::from((
            ErrorKind::AllConnectionsUnavailable,
            "No valid addresses found",
        )));
    }

    let selected_addresses: Vec<String> = {
        let mut rng = rand::rng();
        valid_addresses
            .clone()
            .into_iter()
            .choose_multiple(&mut rng, num_of_nodes_to_query)
    };

    // Run refresh_and_update_connections with a timeout of connection timeout
    // If selected hosts do not have active connections, this will initiate connections to them and will add them to the connection map.
    // If they already have active connections, it will return those connections.
    let _ = tokio::time::timeout(
        inner.get_cluster_param(|p| p.connection_timeout)?,
        ClusterConnInner::refresh_and_update_connections(
            inner.clone(),
            selected_addresses.iter().cloned().collect(),
            RefreshConnectionType::OnlyManagementConnection,
            true,
        ),
    )
    .await;

    // Create a future for each selected address to establish a connection.
    let connections = selected_addresses
        .into_iter()
        .filter_map(|address| {
            let conn = inner
                .conn_lock
                .read()
                .expect(MUTEX_READ_ERR)
                .management_connection_for_address(&address);
            conn
        })
        .collect::<Vec<_>>();

    Ok(connections)
}

async fn calculate_topology_from_random_nodes<C>(
    inner: &Core<C>,
    num_of_nodes_to_query: usize,
    curr_retry: usize,
) -> (
    RedisResult<(
        crate::cluster_slotmap::SlotMap,
        crate::cluster_topology::TopologyHash,
    )>,
    std::collections::HashSet<String>,
)
where
    C: ConnectionLike + Connect + Clone + Send + Sync + 'static,
{
    let refresh_topology_from_initial_nodes = inner
        .get_cluster_param(|p| p.refresh_topology_from_initial_nodes)
        .unwrap_or(false);

    // Get connections either from seed nodes or random existing connections.
    let requested_nodes = match refresh_topology_from_initial_nodes {
        true => match get_random_connections_from_initial_nodes(inner, num_of_nodes_to_query).await
        {
            Ok(connections_futures) => connections_futures,
            Err(err) => {
                return (Err(err), std::collections::HashSet::new());
            }
        },
        false => {
            if let Some(random_conns) = inner
                .conn_lock
                .read()
                .expect(MUTEX_READ_ERR)
                .random_connections(num_of_nodes_to_query, ConnectionType::PreferManagement)
            {
                random_conns
            } else {
                return (
                    Err(RedisError::from((
                        ErrorKind::AllConnectionsUnavailable,
                        "No available connections to refresh slots from",
                    ))),
                    std::collections::HashSet::new(),
                );
            }
        }
    };

    let topology_join_results =
        futures::future::join_all(requested_nodes.into_iter().map(|(addr, conn)| async move {
            let mut conn: C = conn.await;
            let res = conn.req_packed_command(&slot_cmd()).await;
            (addr, res)
        }))
        .await;

    // Add topology command failures (with unrecoverable errors) to the existing connection failures set.
    let failed_addresses = topology_join_results
        .iter()
        .filter_map(|(address, res)| match res {
            Err(err) if err.is_unrecoverable_error() => Some(address.clone()),
            _ => None,
        })
        .collect::<std::collections::HashSet<String>>();

    let topology_values = topology_join_results.iter().filter_map(|(addr, res)| {
        res.as_ref()
            .ok()
            .and_then(|value| get_host_and_port_from_addr(addr).map(|(host, _)| (host, value)))
    });
    let tls_mode = inner
        .get_cluster_param(|params| params.tls)
        .expect(MUTEX_READ_ERR);

    let read_from_replicas = inner
        .get_cluster_param(|params| params.read_from_replicas.clone())
        .expect(MUTEX_READ_ERR);
    (
        calculate_topology(
            topology_values,
            curr_retry,
            tls_mode,
            num_of_nodes_to_query,
            read_from_replicas,
        ),
        failed_addresses,
    )
}

impl<C> ConnectionLike for ClusterConnection<C>
where
    C: ConnectionLike + Send + Clone + Unpin + Sync + Connect + 'static,
{
    fn req_packed_command<'a>(&'a mut self, cmd: &'a Cmd) -> RedisFuture<'a, Value> {
        let routing = cluster_routing::RoutingInfo::for_routable(cmd).unwrap_or(
            cluster_routing::RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random),
        );
        self.route_command(cmd, routing).boxed()
    }

    fn req_packed_commands<'a>(
        &'a mut self,
        pipeline: &'a crate::Pipeline,
        offset: usize,
        count: usize,
        pipeline_retry_strategy: Option<PipelineRetryStrategy>,
    ) -> RedisFuture<'a, Vec<Value>> {
        async move {
            let route = route_for_pipeline(pipeline)?;
            self.route_pipeline(
                pipeline,
                offset,
                count,
                route.map(|r| Some(r).into()),
                pipeline_retry_strategy,
            )
            .await
        }
        .boxed()
    }

    fn get_db(&self) -> i64 {
        0
    }

    fn is_closed(&self) -> bool {
        false
    }
}

/// Implements the process of connecting to a Redis server
/// and obtaining a connection handle.
pub trait Connect: Sized {
    /// Connect to a node.
    /// For TCP connections, returning a tuple of handle for command execution and the node's IP address.
    /// For UNIX connections, returning a tuple of handle for command execution and None.
    fn connect<'a, T>(
        info: T,
        response_timeout: Duration,
        connection_timeout: Duration,
        socket_addr: Option<SocketAddr>,
        glide_connection_options: GlideConnectionOptions,
    ) -> RedisFuture<'a, (Self, Option<IpAddr>)>
    where
        T: IntoConnectionInfo + Send + 'a;
}

impl Connect for MultiplexedConnection {
    fn connect<'a, T>(
        info: T,
        response_timeout: Duration,
        connection_timeout: Duration,
        socket_addr: Option<SocketAddr>,
        glide_connection_options: GlideConnectionOptions,
    ) -> RedisFuture<'a, (MultiplexedConnection, Option<IpAddr>)>
    where
        T: IntoConnectionInfo + Send + 'a,
    {
        async move {
            let connection_info = info.into_connection_info()?;
            let client = crate::Client::open(connection_info)?;

            match Runtime::locate() {
                #[cfg(feature = "tokio-comp")]
                rt @ Runtime::Tokio => {
                    rt.timeout(
                        connection_timeout,
                        client.get_multiplexed_async_connection_inner::<crate::aio::tokio::Tokio>(
                            response_timeout,
                            socket_addr,
                            glide_connection_options,
                        ),
                    )
                    .await?
                }
            }
        }
        .boxed()
    }
}

#[cfg(test)]
mod pipeline_routing_tests {
    use std::collections::HashMap;

    use futures::executor::block_on;

    use super::pipeline_routing::route_for_pipeline;
    use crate::{
        aio::MultiplexedConnection,
        cluster_async::{pipeline_routing::PipelineResponses, ClusterConnInner},
        cluster_routing::{
            AggregateOp, MultiSlotArgPattern, MultipleNodeRoutingInfo, ResponsePolicy, Route,
            SlotAddr,
        },
        cmd,
        types::{ServerError, ServerErrorKind},
        Value,
    };

    #[test]
    fn test_first_route_is_found() {
        let mut pipeline = crate::Pipeline::new();
        pipeline.atomic();

        pipeline
            .add_command(cmd("FLUSHALL")) // route to all masters
            .get("foo") // route to slot 12182
            .add_command(cmd("EVAL")); // route randomly

        assert_eq!(
            route_for_pipeline(&pipeline),
            Ok(Some(Route::new(12182, SlotAddr::ReplicaOptional)))
        );
    }

    #[test]
    fn test_numerical_response_aggregation_logic() {
        let pipeline_responses: PipelineResponses = vec![
            vec![
                (Some("node1".to_string()), Value::Int(3)),
                (Some("node2".to_string()), Value::Int(7)),
                (Some("node3".to_string()), Value::Int(0)),
            ],
            vec![(
                Some("node3".to_string()),
                Value::BulkString(b"unchanged".to_vec()),
            )],
            vec![
                (Some("node1".to_string()), Value::Int(5)),
                (Some("node2".to_string()), Value::Int(11)),
            ],
        ];
        let response_policies = HashMap::from([
            (
                0,
                (
                    MultipleNodeRoutingInfo::AllNodes,
                    Some(ResponsePolicy::Aggregate(AggregateOp::Sum)),
                ),
            ),
            (
                2,
                (
                    MultipleNodeRoutingInfo::AllMasters,
                    Some(ResponsePolicy::Aggregate(AggregateOp::Min)),
                ),
            ),
        ]);
        let responses = block_on(
            ClusterConnInner::<MultiplexedConnection>::aggregate_pipeline_multi_node_commands(
                pipeline_responses,
                response_policies,
            ),
        );

        // Command 0 should be aggregated to 3 + 7 + 0 = 10.
        // Command 1 should remain unchanged.
        assert_eq!(
            responses,
            vec![
                Value::Int(10),
                Value::BulkString(b"unchanged".to_vec()),
                Value::Int(5)
            ],
            "{responses:?}"
        );
    }

    #[test]
    fn test_combine_arrays_response_aggregation_logic() {
        let pipeline_responses: PipelineResponses = vec![
            vec![
                (Some("node1".to_string()), Value::Array(vec![Value::Int(1)])),
                (Some("node2".to_string()), Value::Array(vec![Value::Int(2)])),
            ],
            vec![
                (
                    Some("node2".to_string()),
                    Value::Array(vec![
                        Value::BulkString("key1".into()),
                        Value::BulkString("key3".into()),
                    ]),
                ),
                (
                    Some("node1".to_string()),
                    Value::Array(vec![
                        Value::BulkString("key2".into()),
                        Value::BulkString("key4".into()),
                    ]),
                ),
            ],
        ];
        let response_policies = HashMap::from([
            (
                0,
                (
                    MultipleNodeRoutingInfo::AllNodes,
                    Some(ResponsePolicy::CombineArrays),
                ),
            ),
            (
                1,
                (
                    MultipleNodeRoutingInfo::MultiSlot((
                        vec![
                            (Route::new(1, SlotAddr::Master), vec![0, 2]),
                            (Route::new(2, SlotAddr::Master), vec![1, 3]),
                        ],
                        MultiSlotArgPattern::KeysOnly,
                    )),
                    Some(ResponsePolicy::CombineArrays),
                ),
            ),
        ]);

        let responses = block_on(
            ClusterConnInner::<MultiplexedConnection>::aggregate_pipeline_multi_node_commands(
                pipeline_responses,
                response_policies,
            ),
        );

        let mut expected = Value::Array(vec![Value::Int(1), Value::Int(2)]);
        assert_eq!(
            responses[0], expected,
            "Expected combined array to include all elements"
        );
        expected = Value::Array(vec![
            Value::BulkString("key1".into()),
            Value::BulkString("key2".into()),
            Value::BulkString("key3".into()),
            Value::BulkString("key4".into()),
        ]);
        assert_eq!(
            responses[1], expected,
            "Expected combined array to include all elements"
        );
    }

    #[test]
    fn test_aggregate_pipeline_multi_node_commands_with_error_response() {
        let pipeline_responses: PipelineResponses = vec![
            vec![
                (Some("node1".to_string()), Value::Int(3)),
                (Some("node2".to_string()), Value::Int(7)),
                (
                    Some("node3".to_string()),
                    Value::ServerError(ServerError::KnownError {
                        kind: ServerErrorKind::Moved,
                        detail: Some("127.0.0.1".to_string()),
                    }),
                ),
            ],
            vec![(
                Some("node3".to_string()),
                Value::BulkString(b"unchanged".to_vec()),
            )],
        ];
        let mut response_policies = HashMap::new();
        response_policies.insert(
            0,
            (
                MultipleNodeRoutingInfo::AllNodes,
                Some(ResponsePolicy::CombineArrays),
            ),
        );

        let responses = block_on(
            ClusterConnInner::<MultiplexedConnection>::aggregate_pipeline_multi_node_commands(
                pipeline_responses,
                response_policies,
            ),
        );

        assert_eq!(
            responses,
            vec![
                Value::ServerError(ServerError::KnownError {
                    kind: ServerErrorKind::Moved,
                    detail: Some("An error was signalled by the server: 127.0.0.1".to_string()),
                }),
                Value::BulkString(b"unchanged".to_vec()),
            ]
        );
    }

    #[test]
    fn test_aggregate_pipeline_multi_node_commands_with_no_response_for_command() {
        let pipeline_responses: PipelineResponses =
            vec![vec![(Some("node1".to_string()), Value::Int(1))], vec![]];
        let response_policies = HashMap::new();

        let responses = block_on(
            ClusterConnInner::<MultiplexedConnection>::aggregate_pipeline_multi_node_commands(
                pipeline_responses,
                response_policies,
            ),
        );

        assert_eq!(
            responses,
            vec![
                Value::Int(1),
                Value::ServerError(ServerError::ExtensionError {
                    code: "PipelineResponseError".to_string(),
                    detail: Some("Expected exactly one response for command 1, got 0".to_string())
                })
            ]
        );
    }

    #[test]
    fn test_aggregate_pipeline_responses_with_multiple_responses_for_command() {
        let pipeline_responses: PipelineResponses = vec![
            vec![(Some("node1".to_string()), Value::Int(1))],
            vec![
                (Some("node2".to_string()), Value::Int(2)),
                (Some("node3".to_string()), Value::Int(3)),
            ],
        ];
        let response_policies = HashMap::new();

        let responses = block_on(
            ClusterConnInner::<MultiplexedConnection>::aggregate_pipeline_multi_node_commands(
                pipeline_responses,
                response_policies,
            ),
        );

        assert_eq!(
            responses,
            vec![
                Value::Int(1),
                Value::ServerError(ServerError::ExtensionError {
                    code: "PipelineResponseError".to_string(),
                    detail: Some("Expected exactly one response for command 1, got 2".to_string())
                })
            ]
        );
    }

    #[test]
    fn test_return_none_if_no_route_is_found() {
        let mut pipeline = crate::Pipeline::new();
        pipeline.atomic();
        pipeline
            .add_command(cmd("FLUSHALL")) // route to all masters
            .add_command(cmd("EVAL")); // route randomly

        assert_eq!(route_for_pipeline(&pipeline), Ok(None));
    }

    #[test]
    fn test_prefer_primary_route_over_replica() {
        let mut pipeline = crate::Pipeline::new();
        pipeline.atomic();
        pipeline
            .get("foo") // route to replica of slot 12182
            .add_command(cmd("FLUSHALL")) // route to all masters
            .add_command(cmd("EVAL"))// route randomly
            .cmd("CONFIG").arg("GET").arg("timeout") // unkeyed command
            .set("foo", "bar"); // route to primary of slot 12182

        assert_eq!(
            route_for_pipeline(&pipeline),
            Ok(Some(Route::new(12182, SlotAddr::Master)))
        );
    }

    #[test]
    fn test_raise_cross_slot_error_on_conflicting_slots() {
        let mut pipeline = crate::Pipeline::new();
        pipeline.atomic();
        pipeline
            .add_command(cmd("FLUSHALL")) // route to all masters
            .set("baz", "bar") // route to slot 4813
            .get("foo"); // route to slot 12182

        assert_eq!(
            route_for_pipeline(&pipeline).unwrap_err().kind(),
            crate::ErrorKind::CrossSlot
        );
    }

    #[test]
    fn unkeyed_commands_dont_affect_route() {
        let mut pipeline = crate::Pipeline::new();
        pipeline.atomic();
        pipeline
            .set("{foo}bar", "baz") // route to primary of slot 12182
            .cmd("CONFIG").arg("GET").arg("timeout") // unkeyed command
            .set("foo", "bar") // route to primary of slot 12182
            .cmd("DEBUG").arg("PAUSE").arg("100") // unkeyed command
            .cmd("ECHO").arg("hello world"); // unkeyed command

        assert_eq!(
            route_for_pipeline(&pipeline),
            Ok(Some(Route::new(12182, SlotAddr::Master)))
        );
    }
}
