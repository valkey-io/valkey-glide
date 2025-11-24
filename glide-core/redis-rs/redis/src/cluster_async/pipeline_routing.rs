use crate::aio::ConnectionLike;
use crate::cluster_async::ClusterConnInner;
use crate::cluster_async::Connect;
use crate::cluster_async::MUTEX_READ_ERR;
use crate::cluster_routing::RoutingInfo;
use crate::cluster_routing::SlotAddr;
use crate::cluster_routing::{
    command_for_multi_slot_indices, MultipleNodeRoutingInfo, ResponsePolicy, SingleNodeRoutingInfo,
};
use crate::types::{RetryMethod, ServerError};
use crate::Pipeline;
use crate::{cluster_routing, RedisResult, Value};
use crate::{cluster_routing::Route, Cmd, ErrorKind, RedisError};
use cluster_routing::RoutingInfo::{MultiNode, SingleNode};
use futures::FutureExt;
use logger_core::log_error;
use rand::prelude::IteratorRandom;
use std::collections::HashMap;
use std::collections::HashSet;
use std::sync::Arc;
use telemetrylib::GlideOpenTelemetry;
use tokio::sync::oneshot;
use tokio::sync::oneshot::error::RecvError;

use super::boxed_sleep;
use super::testing::RefreshConnectionType;
use super::CmdArg;
use super::PendingRequest;
use super::PipelineRetryStrategy;
use super::RedirectNode;
use super::RequestInfo;
use super::{Core, InternalSingleNodeRouting, OperationTarget, Response};

/// Represents a pipeline command execution context for a specific node
#[derive(Default)]
pub(crate) struct NodePipelineContext<C> {
    /// The pipeline of commands to be executed
    pub pipeline: Pipeline,
    /// The connection to the node
    pub connection: C,
    /// Vector of (command_index, inner_index) pairs tracking command order
    /// command_index: Position in the original pipeline
    /// inner_index: Optional sub-index for multi-node operations (e.g. MSET)
    /// ignore: Whether to ignore this commands response (e.g. `ASKING` command).
    pub command_indices: Vec<(usize, Option<usize>, bool)>,
}

/// Maps node addresses to their pipeline execution contexts
pub(crate) type NodePipelineMap<C> = HashMap<String, NodePipelineContext<C>>;

impl<C> NodePipelineContext<C> {
    fn new(connection: C) -> Self {
        Self {
            pipeline: Pipeline::new(),
            connection,
            command_indices: Vec::new(),
        }
    }

    // Adds a command to the pipeline and records its index, and whether to ignore the response
    fn add_command(
        &mut self,
        cmd: Arc<Cmd>,
        index: usize,
        inner_index: Option<usize>,
        ignore: bool,
    ) {
        self.pipeline.add_command_with_arc(cmd);
        self.command_indices.push((index, inner_index, ignore));
    }
}

/// `NodeResponse` represents a response from a node along with its source node address.
type NodeResponse = (Option<String>, Value);
/// `PipelineResponses` represents the responses for each pipeline command.
/// The outer `Vec` represents the pipeline commands, and each inner `Vec` contains (response, address) pairs.
/// Since some commands can be executed across multiple nodes (e.g., multi-node commands), a single command
/// might produce multiple responses, each from a different node. By storing the responses with their
/// respective node addresses, we ensure that we have all the information needed to aggregate the results later.
pub(crate) type PipelineResponses = Vec<Vec<NodeResponse>>;

/// `AddressAndIndices` represents the address of a node and the indices of commands associated with that node.
type AddressAndIndices = Vec<(String, Vec<(usize, Option<usize>, bool)>)>;

/// A mapping of command indices to their respective routing information and response aggregation policies
/// for multi-node commands.
///
///
/// - The `usize` key represents the index of the command in the pipeline.
/// - The `MultipleNodeRoutingInfo` stores routing information for the command (e.g., `AllNodes`).
/// - The `Option<ResponsePolicy>` specifies how the responses should be aggregated (e.g `AllSucceeded`).
///
/// This is used to track response policies when sending commands to multiple nodes and aggregating their responses into a single response.
pub(crate) type ResponsePoliciesMap =
    HashMap<usize, (MultipleNodeRoutingInfo, Option<ResponsePolicy>)>;

/// Adds a command to the pipeline map for a specific node address.
///
/// `add_asking` is a boolean flag that determines whether to add an `ASKING` command before the command.
/// `is_retrying` is a boolean flag that indicates whether this is a retry attempt.
#[allow(clippy::too_many_arguments)]
fn add_command_to_node_pipeline_map<C>(
    pipeline_map: &mut NodePipelineMap<C>,
    address: String,
    connection: C,
    cmd: Arc<Cmd>,
    index: usize,
    inner_index: Option<usize>,
    add_asking: bool,
    is_retrying: bool,
) where
    C: Clone,
{
    if is_retrying {
        // Record retry attempt metric if telemetry is initialized
        if let Err(e) = GlideOpenTelemetry::record_retry_attempt() {
            log_error(
                "OpenTelemetry:retry_error",
                format!("Failed to record retry attempt: {e}"),
            );
        }
    }
    if add_asking {
        let asking_cmd = Arc::new(crate::cmd::cmd("ASKING"));
        pipeline_map
            .entry(address.clone())
            .or_insert_with(|| NodePipelineContext::new(connection.clone()))
            .add_command(asking_cmd, index, inner_index, true); // mark it as ignore, as this commands response is `OK`
    }
    pipeline_map
        .entry(address)
        .or_insert_with(|| NodePipelineContext::new(connection))
        .add_command(cmd, index, inner_index, false);
}

/// Maps the commands in a pipeline to the appropriate nodes based on their routing information.
///
/// This function processes each command in the given pipeline, determines its routing information,
/// and organizes it into a map of node pipelines. It handles both single-node and multi-node routing
/// strategies and ensures that the commands are distributed accordingly.
///
/// It also collects response policies for multi-node routing and returns them along with the pipeline map.
/// This is to ensure we can aggregate responses from properly from the different nodes.
///
/// # Arguments
///
/// * `pipeline` - A reference to the pipeline containing the commands to route.
/// * `core` - The core object that provides access to connection locks and other resources.
///
/// # Returns
///
/// A `Result` containing a tuple:
/// - A `NodePipelineMap<C>` where commands are grouped by their corresponding nodes (as pipelines).
/// - A `ResponsePoliciesMap` mapping command indices to their routing information and response policies
///   for multi-node commands, used for aggregating responses later.
pub(crate) async fn map_pipeline_to_nodes<C>(
    pipeline: &crate::Pipeline,
    core: Core<C>,
    route: Option<InternalSingleNodeRouting<C>>,
) -> Result<(NodePipelineMap<C>, ResponsePoliciesMap), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    let mut pipelines_per_node = NodePipelineMap::new();
    let mut response_policies = HashMap::new();

    if let Some(route) = route {
        // If we have a route, we will use it to route the commands to the given route, instead of finding the route for each command

        let (addr, conn) = ClusterConnInner::get_connection(route, core, None)
            .await
            .map_err(|err| (OperationTarget::NotFound, err))?;

        let entry = pipelines_per_node
            .entry(addr)
            .or_insert_with(|| NodePipelineContext::new(conn));

        for (index, cmd) in pipeline.cmd_iter().enumerate() {
            entry.add_command(cmd.clone(), index, None, false);
        }
    } else {
        for (index, cmd) in pipeline.cmd_iter().enumerate() {
            match RoutingInfo::for_routable(cmd.as_ref())
                .unwrap_or(SingleNode(SingleNodeRoutingInfo::Random))
            {
                SingleNode(route) => {
                    handle_pipeline_single_node_routing(
                        &mut pipelines_per_node,
                        cmd.clone(),
                        route.into(),
                        core.clone(),
                        index,
                    )
                    .await?;
                }
                MultiNode((multi_node_routing, response_policy)) => {
                    //save the routing info and response policy, so we will be able to aggregate the results later
                    response_policies
                        .entry(index)
                        .or_insert((multi_node_routing.clone(), response_policy));
                    match multi_node_routing {
                        MultipleNodeRoutingInfo::AllNodes | MultipleNodeRoutingInfo::AllMasters => {
                            let connections: Vec<_> = {
                                let lock = core.conn_lock.read().expect(MUTEX_READ_ERR);
                                if matches!(multi_node_routing, MultipleNodeRoutingInfo::AllNodes) {
                                    lock.all_node_connections().collect()
                                } else {
                                    lock.all_primary_connections().collect()
                                }
                            };

                            if connections.is_empty() {
                                let error_message = if matches!(
                                    multi_node_routing,
                                    MultipleNodeRoutingInfo::AllNodes
                                ) {
                                    "No available connections to any nodes"
                                } else {
                                    "No available connections to primary nodes"
                                };
                                return Err((
                                    OperationTarget::NotFound,
                                    RedisError::from((
                                        ErrorKind::AllConnectionsUnavailable,
                                        error_message,
                                    )),
                                ));
                            }
                            for (inner_index, (address, conn)) in
                                connections.into_iter().enumerate()
                            {
                                add_command_to_node_pipeline_map(
                                    &mut pipelines_per_node,
                                    address,
                                    conn.await,
                                    cmd.clone(),
                                    index,
                                    Some(inner_index),
                                    false,
                                    false,
                                );
                            }
                        }
                        MultipleNodeRoutingInfo::MultiSlot((slots, _)) => {
                            handle_pipeline_multi_slot_routing(
                                &mut pipelines_per_node,
                                core.clone(),
                                cmd.clone(),
                                index,
                                slots,
                            )
                            .await?;
                        }
                    }
                }
            }
        }
    }
    Ok((pipelines_per_node, response_policies))
}

/// Handles pipeline commands that require single-node routing.
///
/// This function processes commands with `SingleNode` routing information and determines
/// the appropriate handling based on the routing type.
///
/// ### Parameters:
/// - `pipeline_map`: A mutable reference to the `NodePipelineMap`, representing the pipelines grouped by nodes.
/// - `cmd`: The command to process and add to the appropriate node pipeline.
/// - `routing`: The single-node routing information, which determines how the command is routed.
/// - `core`: The core object responsible for managing connections and routing logic.
/// - `index`: The position of the command in the overall pipeline.
async fn handle_pipeline_single_node_routing<C>(
    pipeline_map: &mut NodePipelineMap<C>,
    cmd: Arc<Cmd>,
    routing: InternalSingleNodeRouting<C>,
    core: Core<C>,
    index: usize,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    if matches!(routing, InternalSingleNodeRouting::Random) && !pipeline_map.is_empty() {
        // Adds the command to a random existing node pipeline in the pipeline map
        let mut rng = rand::rng();
        if let Some(node_context) = pipeline_map.values_mut().choose(&mut rng) {
            node_context.add_command(cmd, index, None, false);
            return Ok(());
        }
    }

    let (address, conn) = ClusterConnInner::get_connection(routing, core, Some(cmd.clone()))
        .await
        .map_err(|err| (OperationTarget::NotFound, err))?;

    add_command_to_node_pipeline_map(pipeline_map, address, conn, cmd, index, None, false, false);
    Ok(())
}

/// Handles multi-slot commands within a pipeline.
///
/// This function processes commands with routing information indicating multiple slots
/// (e.g., `MSET` or `MGET`), splits them into sub-commands based on their target slots,
/// and assigns these sub-commands to the appropriate pipelines for the corresponding nodes.
///
/// ### Parameters:
/// - `pipelines_by_connection`: A mutable map of node pipelines where the commands will be added.
/// - `core`: The core structure that provides access to connection management.
/// - `cmd`: The original multi-slot command that needs to be split.
/// - `index`: The index of the original command within the pipeline.
/// - `slots`: A vector containing routing information. Each entry includes:
///   - `Route`: The specific route for the slot.
///   - `Vec<usize>`: Indices of the keys within the command that map to this slot.
async fn handle_pipeline_multi_slot_routing<C>(
    pipelines_by_connection: &mut NodePipelineMap<C>,
    core: Core<C>,
    cmd: Arc<Cmd>,
    index: usize,
    slots: Vec<(Route, Vec<usize>)>,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone,
{
    // inner_index is used to keep track of the index of the sub-commands in the multi slot routing info vector.
    for (inner_index, (route, indices)) in slots.iter().enumerate() {
        let conn = {
            let lock = core.conn_lock.read().expect(MUTEX_READ_ERR);
            lock.connection_for_route(route)
        };
        if let Some((address, conn)) = conn {
            // create the sub-command for the slot
            let new_cmd = Arc::new(command_for_multi_slot_indices(cmd.as_ref(), indices.iter()));
            add_command_to_node_pipeline_map(
                pipelines_by_connection,
                address,
                conn.await,
                new_cmd,
                index,
                Some(inner_index),
                false,
                false,
            );
        } else {
            return Err((
                OperationTarget::NotFound,
                RedisError::from((
                    ErrorKind::ConnectionNotFoundForRoute,
                    "No available connections for route: ",
                    format!("Slot: {} Slot Address: {}", route.slot(), route.slot_addr()),
                )),
            ));
        }
    }
    Ok(())
}

/// Collects and sends pending requests for the given pipeline map, and waits for their responses.
///
/// This function creates `PendingRequest` objects for each pipeline in the provided pipeline map,
/// adds them to the core's pending requests queue, and waits for all responses to be received.
///
/// # Arguments
///
/// * `pipeline_map` - A map of node pipelines where the commands are grouped by their corresponding nodes.
/// * `core` - The core object that provides access to connection locks and other resources.
/// * `retry` - The retry counter.
/// - `pipeline_retry_strategy`: Configures retry behavior for pipeline commands.  
///   - `retry_server_error`: If `true`, retries commands on server errors (may cause reordering).  
///   - `retry_connection_error`: If `true`, retries on connection errors (may lead to duplicate executions).  
///
/// # Returns
///
/// A tuple containing:
/// - A vector of results for each sub-pipeline execution.
/// - A vector of (address, indices, bool) pairs indicating where each response should be placed and if the response should be ignored.
pub(crate) async fn collect_and_send_pending_requests<C>(
    pipeline_map: NodePipelineMap<C>,
    core: Core<C>,
    retry: u32,
    pipeline_retry_strategy: PipelineRetryStrategy,
) -> (
    Vec<Result<RedisResult<Response>, RecvError>>,
    AddressAndIndices,
)
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    // Processes the sub-pipelines to generate pending requests for execution on specific nodes.
    // Each pending request encapsulates all the necessary details for executing commands on a node.
    let (receivers, pending_requests, addresses_and_indices) =
        collect_pipeline_requests(pipeline_map, retry, pipeline_retry_strategy);

    // Add the pending requests to the pending_requests queue
    core.pending_requests
        .lock()
        .unwrap()
        .extend(pending_requests.into_iter());

    // Wait for all receivers to complete and collect the responses
    let responses: Vec<_> = futures::future::join_all(receivers.into_iter())
        .await
        .into_iter()
        .collect();

    (responses, addresses_and_indices)
}

/// Creates `PendingRequest` objects for each pipeline in the provided pipeline map.
///
/// This function processes the given map of node pipelines and prepares each sub-pipeline for execution
/// by creating a `PendingRequest` containing all necessary details for execution.
/// Additionally, it sets up communication channels to asynchronously receive the results of each sub-pipeline's execution.
///
/// Returns a tuple containing:
/// - **receivers**: A vector of `oneshot::Receiver` objects to receive the responses of the sub-pipeline executions.
/// - **pending_requests**: A vector of `PendingRequest` objects, each representing a pipeline scheduled for execution on a node.
/// - **addresses_and_indices**: A vector of tuples containing node addresses and their associated command indices for each sub-pipeline,
///   allowing the results to be mapped back to their original command within the original pipeline.
#[allow(clippy::type_complexity)]
fn collect_pipeline_requests<C>(
    pipelines_by_connection: NodePipelineMap<C>,
    retry: u32,
    pipeline_retry_strategy: PipelineRetryStrategy,
) -> (
    Vec<oneshot::Receiver<RedisResult<Response>>>,
    Vec<PendingRequest<C>>,
    AddressAndIndices,
)
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    let mut receivers = Vec::new();
    let mut pending_requests = Vec::new();
    let mut addresses_and_indices = Vec::new();

    for (address, context) in pipelines_by_connection {
        // Create a channel to receive the pipeline execution results
        let (sender, receiver) = oneshot::channel();
        // Add the receiver to the list of receivers
        receivers.push(receiver);
        pending_requests.push(PendingRequest {
            retry,
            sender,
            info: RequestInfo {
                cmd: CmdArg::Pipeline {
                    count: context.pipeline.len(),
                    pipeline: context.pipeline.into(),
                    offset: 0,
                    route: Some(InternalSingleNodeRouting::Connection {
                        address: address.clone(),
                        conn: async { context.connection }.boxed().shared(),
                    }),
                    // mark it as a sub-pipeline mode
                    sub_pipeline: true,
                    pipeline_retry_strategy,
                },
            },
        });
        // Record the node address and its associated command indices for result mapping
        addresses_and_indices.push((address, context.command_indices));
    }

    (receivers, pending_requests, addresses_and_indices)
}

/// Adds the result of a pipeline command to the `pipeline_responses` collection.
///
/// This function updates the `pipeline_responses` vector at the given `index` and optionally at the
/// `inner_index` if provided. If `inner_index` is `Some`, it ensures that the vector at that index is large enough
/// to hold the value and address at the specified position, resizing it if necessary. If `inner_index` is `None`,
/// the value and address are simply appended to the vector.
///
/// # Parameters
/// - `pipeline_responses`: A mutable reference to a vector of vectors that stores the results of pipeline commands.
/// - `index`: The index in `pipeline_responses` where the result should be stored.
/// - `inner_index`: An optional index within the vector at `index`, used to store the result at a specific position.
/// - `value`: The result value to store.
/// - `address`: The address associated with the result.
fn add_pipeline_result(
    pipeline_responses: &mut PipelineResponses,
    index: usize,
    inner_index: Option<usize>,
    value: Value,
    address: String,
) -> Result<(), (OperationTarget, RedisError)> {
    if let Some(responses) = pipeline_responses.get_mut(index) {
        match inner_index {
            Some(inner_index) => {
                // Ensure the vector at the given index is large enough to hold the value and address at the specified position
                if responses.len() <= inner_index {
                    // TODO - change the pipeline_responses to hold in [index] a vector already sized with the expected responses length
                    responses.resize(
                        inner_index + 1,
                        (
                            None,
                            Value::ServerError(ServerError::ExtensionError {
                                code: "PipelineNoResponse".to_string(),
                                detail: (Some("no response from node".to_string())), // we initialize it with an error, but it should be overwritten
                            }),
                        ),
                    );
                }
                responses[inner_index] = (Some(address), value);
            }
            None => {
                // If we have no `inner_index`, we expect this command to be a single node command, and therefore, have a single response
                // If the vector responses is empty, we add the value and address
                // If the vector is not empty, we check if it's only response is a ServerError, if so, we override it with the new value and address, since that means we have retried the command (e.g. on `MOVED` or `ASK` errors)
                if responses.is_empty() {
                    responses.push((Some(address), value));
                } else if let Value::ServerError(_) = responses[0].1 {
                    responses[0] = (Some(address), value);
                } else {
                    return Err((
                        OperationTarget::FatalError,
                        RedisError::from((
                            ErrorKind::ClientError,
                            "Existing response is not a ServerError; cannot override.",
                        )),
                    ));
                }
            }
        }
        Ok(())
    } else {
        Err((
            OperationTarget::FatalError,
            RedisError::from((
                ErrorKind::ClientError,
                "Index not found in pipeline responses",
            )),
        ))
    }
}

/// A retry entry representing a failed command in the pipeline.
///
/// This tuple contains:
/// - **Command Indices**: A tuple `(usize, Option<usize>)` where the first element is the index
///   of the command in the pipeline and the optional second element represents a nested or sub-index.
/// - **Node Address**: A `String` indicating the address of the node that originally handled the command.
/// - **Server Error**: A `ServerError` describing the error encountered.
type RetryEntry = ((usize, Option<usize>), String, ServerError);

/// A mapping from a retry method to the corresponding list of retry entries.
///
/// Each key is a `RetryMethod` that categorizes the type of redirection or error,
/// and each value is a vector of `RetryEntry` items that failed with that retry method.
type RetryMap = HashMap<RetryMethod, Vec<RetryEntry>>;

/// Processes pipeline responses and updates the provided `pipeline_responses` with the corresponding results.
///
/// This function iterates over the pipeline responses paired with their original command addresses and indices.
/// For each response, it:
/// - Adds each sub-response into the `pipeline_responses` at the appropriate index (if the command is not marked to be ignored).
/// - If a sub-response is a `ServerError`, it converts the error to determine the applicable retry method,
///   and then records the failed command in a retry map for later reprocessing.
/// - Generates a suitable `ServerError` if the response format is not as expected (for example, receiving a single response
///   for a pipeline containing multiple commands or a cluster scan result).
///
/// # Parameters
///
/// - `pipeline_responses`: A mutable collection that holds the responses corresponding to each command in the pipeline.
/// - `responses`: A vector of results from sub-pipelines, where each item is a `Result` containing either a successful
///   `RedisResult<Response>` or a `RecvError`.
/// - `addresses_and_indices`: A collection of pairs where each pair associates a node address with the indices
///   of commands in the pipeline that were sent to that node.
/// - `pipeline_retry_strategy`: Configures retry behavior for pipeline commands.  
///   - `retry_server_error`: If `true`, retries commands on server errors (may cause reordering).  
///   - `retry_connection_error`: If `true`, retries on connection errors (may lead to duplicate executions).  
///
/// # Returns
///
/// - **Ok**: A `RetryMap` mapping each retry method to the list of commands that failed and
///   should be retried.
/// - **Err**: A tuple `(OperationTarget, RedisError)` if a node-level or reception error occurs while processing responses.
fn process_pipeline_responses(
    pipeline_responses: &mut PipelineResponses,
    responses: Vec<Result<RedisResult<Response>, RecvError>>,
    addresses_and_indices: AddressAndIndices,
    pipeline_retry_strategy: PipelineRetryStrategy,
) -> Result<RetryMap, (OperationTarget, RedisError)> {
    let mut retry_map: RetryMap = HashMap::new();
    for ((address, command_indices), response_result) in
        addresses_and_indices.into_iter().zip(responses)
    {
        let (server_error, retry_method) = match response_result {
            Ok(Ok(Response::Multiple(values))) => {
                // Add each response to the pipeline_responses vector at the appropriate index
                for ((index, inner_index, ignore), value) in command_indices.into_iter().zip(values)
                {
                    // If the commands response is not marked to be ignored
                    if let Value::ServerError(error) = &value {
                        // Convert error and determine retry method
                        let retry_method = RedisError::from(error.clone()).retry_method();
                        update_retry_map(
                            &mut retry_map,
                            retry_method,
                            (index, inner_index),
                            address.clone(),
                            error.clone(),
                            pipeline_retry_strategy,
                        );
                    }
                    if !ignore {
                        // Add to pipeline responses
                        add_pipeline_result(
                            pipeline_responses,
                            index,
                            inner_index,
                            value,
                            address.clone(),
                        )?;
                    }
                }
                continue;
            }
            // If we received a single response for a pipeline, we will create a ServerError and append it to the relevant indices
            // We are not supposed to get in here, but it's better than using unreachable!()
            Ok(Ok(Response::Single(_))) => (
                ServerError::ExtensionError {
                    code: ("SingleResponseError".to_string()),
                    detail: (Some(
                        "Received a single response for a pipeline with multiple commands."
                            .to_string(),
                    )),
                },
                RetryMethod::NoRetry,
            ),
            // If we received a cluster scan response for a pipeline, we will create a ServerError and append it to the relevant indices
            // We are not supposed to get in here, but it's better than using unreachable!()
            Ok(Ok(Response::ClusterScanResult(_, _))) => (
                ServerError::ExtensionError {
                    code: ("ClusterScanError".to_string()),
                    detail: (Some("Received a cluster scan result inside a pipeline.".to_string())),
                },
                RetryMethod::NoRetry,
            ),

            // If we received a redis error, we will convert it to a ServerError and append it to the relevant indices
            Ok(Err(err)) => {
                let retry_method = err.retry_method();
                (err.into(), retry_method)
            }
            // If we received a receive (connection) error, we will create a ServerError and append it to the relevant indices.
            // If the pipeline retry strategy is set to retry connection errors, we will retry the commands, if not, we will trigger reconnection.
            Err(err) => (
                ServerError::ExtensionError {
                    code: ("BrokenPipe".to_string()),
                    detail: (Some(format!(
                        "Cluster: Failed to receive command response from internal sender. {err:?}"
                    ))),
                },
                if pipeline_retry_strategy.retry_connection_error {
                    RetryMethod::ReconnectAndRetry
                } else {
                    RetryMethod::Reconnect
                },
            ),
        };

        // Add the error to the matching indices in the pipeline_responses
        for (index, inner_index, ignore) in command_indices {
            // If the commands response is not marked to be ignored
            update_retry_map(
                &mut retry_map,
                retry_method,
                (index, inner_index),
                address.clone(),
                server_error.clone(),
                pipeline_retry_strategy,
            );
            if !ignore {
                add_pipeline_result(
                    pipeline_responses,
                    index,
                    inner_index,
                    Value::ServerError(server_error.clone()),
                    address.clone(),
                )?;
            }
        }
    }

    Ok(retry_map)
}

/// Updates the retry map with the given retry method and error information.
fn update_retry_map(
    retry_map: &mut RetryMap,
    retry_method: RetryMethod,
    indices: (usize, Option<usize>),
    address: String,
    error: ServerError,
    pipeline_retry_strategy: PipelineRetryStrategy,
) {
    let (index, inner_index) = indices;
    match retry_method {
        RetryMethod::NoRetry => {
            // Do nothing
        }
        RetryMethod::Reconnect | RetryMethod::ReconnectAndRetry => {
            // If we the retry method is reconnect and the user has set the retry_connection_error flag to true,
            // we will retry the commands, if not, we will trigger reconnection.
            let effective_retry_method = if pipeline_retry_strategy.retry_connection_error {
                RetryMethod::ReconnectAndRetry
            } else {
                retry_method
            };

            retry_map.entry(effective_retry_method).or_default().push((
                (index, inner_index),
                address,
                error,
            ));
        }
        RetryMethod::AskRedirect | RetryMethod::MovedRedirect => {
            // If the error is a redirect, we add it to the retry map regardless
            retry_map
                .entry(retry_method)
                .or_default()
                .push(((index, inner_index), address, error));
        }
        RetryMethod::RetryImmediately
        | RetryMethod::WaitAndRetry
        | RetryMethod::WaitAndRetryOnPrimaryRedirectOnReplica => {
            if pipeline_retry_strategy.retry_server_error {
                // Only add to the retry map if retries for failed commands are enabled
                retry_map.entry(retry_method).or_default().push((
                    (index, inner_index),
                    address,
                    error,
                ));
            }
        }
    }
}

/// Processes the pipeline responses and handles errors by retrying the commands.
///
/// This function serves as a loop that processes the pipeline responses and handles any errors
/// by retrying the commands that encountered the error, based on their retry method.
/// If there are no commands to retry, or we've reached the maximum number of retries, the loop exits.
///
/// # Arguments
///
/// * `responses` - A list of responses corresponding to each sub-pipeline.
/// * `addresses_and_indices` - A list of (address, indices) pairs indicating where each response should be placed.
/// * `pipeline` - A reference to the original pipeline containing the commands.
/// * `core` - The core object that provides access to connection locks and other resources.
/// * `response_policies` - A HashMap of routing info and response policies to the pipeline commands.
/// - `pipeline_retry_strategy`: Configures retry behavior for pipeline commands.  
///   - `retry_server_error`: If `true`, retries commands on server errors (may cause reordering).  
///   - `retry_connection_error`: If `true`, retries on connection errors (may lead to duplicate executions).  
pub(crate) async fn process_and_retry_pipeline_responses<C>(
    mut responses: Vec<Result<RedisResult<Response>, RecvError>>,
    mut addresses_and_indices: AddressAndIndices,
    pipeline: &crate::Pipeline,
    core: Core<C>,
    response_policies: &mut ResponsePoliciesMap,
    pipeline_retry_strategy: PipelineRetryStrategy,
) -> Result<PipelineResponses, (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    // TODO: add support for user-defined retry configurations
    let retry_params = core
        .get_cluster_param(|params| params.retry_params.clone())
        .expect(MUTEX_READ_ERR);

    let mut retry = 0;

    // Initialize `PipelineResponses` to store responses for each pipeline command.
    // This will be used to store the responses from the different sub-pipelines to the pipeline commands.
    // A command can have one or more responses (e.g MultiNode commands).
    // Each entry in `PipelineResponses` corresponds to a command in the original pipeline and contains
    // a vector of tuples where each tuple holds a response to the command and the address of the node that provided it.
    let mut pipeline_responses: PipelineResponses = vec![Vec::new(); pipeline.len()];
    loop {
        match process_pipeline_responses(
            &mut pipeline_responses,
            responses,
            addresses_and_indices,
            pipeline_retry_strategy,
        ) {
            Ok(retry_map) => {
                // If there are no retirable errors, or we have reached the maximum number of retries, we're done
                if retry_map.is_empty() || retry >= retry_params.number_of_retries {
                    return Ok(pipeline_responses);
                }

                retry = retry.saturating_add(1);
                // TODO: consider moving this logic into sub-pipelines
                match handle_retry_map(
                    retry_map,
                    core.clone(),
                    pipeline,
                    retry,
                    &mut pipeline_responses,
                    response_policies,
                    pipeline_retry_strategy,
                )
                .await
                {
                    Ok((new_responses, new_addresses_and_indices)) => {
                        // Update responses for the retried commands
                        responses = new_responses;
                        addresses_and_indices = new_addresses_and_indices;
                    }
                    Err(e) => return Err(e), // If we get into here, it's because `add_pipeline_result` failed to find the matching index
                }
            }

            // If we get into here, it's because `add_pipeline_result` failed to find the matching index
            Err(e) => return Err(e),
        }
    }
}

/// Handles the retry logic for a given retry map.
///
/// This function processes the retry map, which contains commands that need to be retried based on their retry method.
/// It handles the different retry methods such as reconnecting, waiting and retrying, or redirecting commands to the appropriate nodes.
///
/// # Arguments
///
/// * `retry_map` - A map where the key is the `RetryMethod` and the value is a vector of tuples containing:
///   - The index and optional inner index of the command in the pipeline.
///   - The address of the node where the command was originally sent.
///   - The `ServerError` that occurred.
/// * `core` - The core object that provides access to connection locks and other resources.
/// * `pipeline` - A reference to the original pipeline containing the commands.
/// * `retry` - The retry counter.
/// * `pipeline_responses` - A mutable reference to the collection of pipeline responses.
/// * `response_policies` - A HashMap of routing info and response policies to the pipeline commands.
/// - `pipeline_retry_strategy`: Configures retry behavior for pipeline commands.  
///   - `retry_server_error`: If `true`, retries commands on server errors (may cause reordering).  
///   - `retry_connection_error`: If `true`, retries on connection errors (may lead to duplicate executions).  
///
/// # Returns
///
/// A `Result` containing a tuple:
/// - A vector of results for each sub-pipeline execution.
/// - A vector of (address, indices, bool) pairs indicating where each response should be placed, and if the response should be ignored.
async fn handle_retry_map<C>(
    retry_map: RetryMap,
    core: Core<C>,
    pipeline: &crate::Pipeline,
    retry: u32,
    pipeline_responses: &mut PipelineResponses,
    response_policies: &mut ResponsePoliciesMap,
    pipeline_retry_strategy: PipelineRetryStrategy,
) -> Result<
    (
        Vec<Result<RedisResult<Response>, RecvError>>,
        AddressAndIndices,
    ),
    (OperationTarget, RedisError),
>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    // Create a new pipeline map to map the retried commands to their corresponding node.
    let mut pipeline_map = NodePipelineMap::new();
    for (retry_method, indices_addresses_and_error) in retry_map {
        match retry_method {
            RetryMethod::NoRetry => {
                // The server error was already added to the pipeline responses, so we can just continue.
            }
            RetryMethod::Reconnect | RetryMethod::ReconnectAndRetry => {
                handle_reconnect_logic(
                    indices_addresses_and_error,
                    core.clone(),
                    pipeline,
                    pipeline_responses,
                    matches!(retry_method, RetryMethod::ReconnectAndRetry),
                    &mut pipeline_map,
                    response_policies,
                )
                .await?;
            }
            RetryMethod::RetryImmediately
            | RetryMethod::WaitAndRetry
            | RetryMethod::WaitAndRetryOnPrimaryRedirectOnReplica => {
                handle_retry_logic(
                    retry_method,
                    retry,
                    core.clone(),
                    indices_addresses_and_error,
                    pipeline,
                    pipeline_responses,
                    &mut pipeline_map,
                    response_policies,
                )
                .await?;
            }
            RetryMethod::MovedRedirect | RetryMethod::AskRedirect => {
                handle_redirect_logic(
                    retry_method,
                    core.clone(),
                    pipeline,
                    indices_addresses_and_error,
                    pipeline_responses,
                    &mut pipeline_map,
                    response_policies,
                )
                .await?;
            }
        }
    }

    Ok(collect_and_send_pending_requests(pipeline_map, core, retry, pipeline_retry_strategy).await)
}

/// Handles the reconnection logic for pipeline commands that encountered errors requiring a reconnect.
///
/// This function refreshes the connections for the nodes corresponding to the failure errors,
/// and if configured to retry, it will invoke the retry mechanism for the affected commands.
/// If not retrying, it triggers an asynchronous refresh without waiting.
async fn handle_reconnect_logic<C>(
    indices_addresses_and_error: Vec<((usize, Option<usize>), String, ServerError)>,
    core: Core<C>,
    pipeline: &Pipeline,
    pipeline_responses: &mut PipelineResponses,
    should_retry: bool,
    pipeline_map: &mut NodePipelineMap<C>,
    response_policies: &mut ResponsePoliciesMap,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    // Extract unique addresses from the provided error entries.
    let addresses: HashSet<String> = indices_addresses_and_error
        .iter()
        .map(|(_, address, _)| address.clone())
        .collect();

    // If we're supposed to retry, refresh connections and retry commands.
    // Since the commands are marked for retry, we will wait fot the refresh to complete, and then retry the commands.
    if should_retry {
        ClusterConnInner::refresh_and_update_connections(
            core.clone(),
            addresses,
            RefreshConnectionType::OnlyUserConnection,
            true,
        )
        .await;

        append_commands_to_retry(
            pipeline_map,
            pipeline,
            core.clone(),
            indices_addresses_and_error,
            pipeline_responses,
            response_policies,
        )
        .await?;
    } else {
        // Since the commands are not marked for retry, we will just trigger the refresh connection tasks, but we wont wait for it to complete.
        ClusterConnInner::trigger_refresh_connection_tasks(
            core.clone(),
            addresses.clone(),
            RefreshConnectionType::OnlyUserConnection,
            true,
        )
        .await;
    }

    Ok(())
}

/// Handles retry logic for commands within a pipeline that encountered errors requiring a retry.
///
/// This function applies a retry strategy to the failed commands in the pipeline.
/// Depending on the specified retry method, it may wait, process primary redirects, or perform no pre-retry work.
/// After handling these conditions, the function reassigns the failed commands to their appropriate node pipelines
/// using `retry_commands`, and schedules them for re-execution.
#[allow(clippy::too_many_arguments)]
async fn handle_retry_logic<C>(
    retry_method: RetryMethod,
    retry: u32,
    core: Core<C>,
    indices_addresses_and_error: Vec<((usize, Option<usize>), String, ServerError)>,
    pipeline: &Pipeline,
    pipeline_responses: &mut PipelineResponses,
    pipeline_map: &mut NodePipelineMap<C>,
    response_policies: &mut ResponsePoliciesMap,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + Sync + ConnectionLike + Send + Connect + 'static,
{
    let retry_params = core
        .get_cluster_param(|params| params.retry_params.clone())
        .expect(MUTEX_READ_ERR);

    if matches!(retry_method, RetryMethod::WaitAndRetry) {
        let sleep_duration = retry_params.wait_time_for_retry(retry);
        boxed_sleep(sleep_duration).await;
    } else if matches!(
        retry_method,
        RetryMethod::WaitAndRetryOnPrimaryRedirectOnReplica
    ) {
        let futures = indices_addresses_and_error
            .iter()
            .fold(HashSet::new(), |mut set, (_, address, _)| {
                set.insert(address.clone());
                set
            })
            .into_iter()
            .map(|address| {
                ClusterConnInner::handle_loading_error(
                    core.clone(),
                    address,
                    retry,
                    retry_params.clone(),
                )
            });

        futures::future::join_all(futures).await;
    }

    // Retry commands after handling retry conditions
    append_commands_to_retry(
        pipeline_map,
        pipeline,
        core,
        indices_addresses_and_error,
        pipeline_responses,
        response_policies,
    )
    .await?;

    Ok(())
}

/// Handles the redirection logic for commands that need to be retried due to redirection errors.
///
/// This function processes the retry map entries that indicate a redirection error (e.g., MOVED or ASK).
/// It attempts to obtain a new connection based on the redirection information and reassigns the command
/// to the appropriate node pipeline for execution.
async fn handle_redirect_logic<C>(
    retry_method: RetryMethod,
    core: Core<C>,
    pipeline: &Pipeline,
    indices_addresses_and_error: Vec<((usize, Option<usize>), String, ServerError)>,
    pipeline_responses: &mut PipelineResponses,
    pipeline_map: &mut NodePipelineMap<C>,
    response_policies: &mut ResponsePoliciesMap,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    for (indices, address, mut error) in indices_addresses_and_error {
        // Convert the ServerError to a RedisError and try to extract redirect info.
        let redis_error: RedisError = error.clone().into();
        let (index, inner_index) = indices;

        // Handle MOVED redirect by updating the topology
        if matches!(retry_method, RetryMethod::MovedRedirect) {
            if let Err(server_error) =
                pipeline_handle_moved_redirect(core.clone(), &redis_error).await
            {
                // A failure occurred, so we will append the error and continue to the next entry
                error.append_detail(&server_error);
                add_pipeline_result(
                    pipeline_responses,
                    index,
                    inner_index,
                    Value::ServerError(error),
                    address,
                )?;
                continue;
            }
        }

        if let Some(redirect_info) = redis_error.redirect(false) {
            let routing = InternalSingleNodeRouting::Redirect {
                redirect: redirect_info,
                previous_routing: Box::new(InternalSingleNodeRouting::ByAddress(address.clone())),
            };

            // Retrieve the original command and attempt to get a new connection.
            match get_original_cmd(pipeline, index, inner_index, Some(response_policies)) {
                Ok(cmd) => {
                    match ClusterConnInner::get_connection(routing, core.clone(), Some(cmd.clone()))
                        .await
                    {
                        Ok((address, conn)) => {
                            // Add the command to the node pipeline map to retry.
                            add_command_to_node_pipeline_map(
                                pipeline_map,
                                address,
                                conn,
                                cmd,
                                index,
                                inner_index,
                                matches!(retry_method, RetryMethod::AskRedirect),
                                true,
                            );
                            continue;
                        }
                        Err(err) => error.append_detail(&err.into()), // Failed to get a connection.
                    }
                }
                Err(cmd_err) => error.append_detail(&cmd_err), // Failed to get the original command.
            }
        } else {
            let server_error = ServerError::ExtensionError {
                code: "RedirectError".to_string(),
                detail: Some("Failed to find redirect info".to_string()),
            };
            // Failed to find redirect info.
            error.append_detail(&server_error);
        }

        // Append the error if a failure occurred.
        add_pipeline_result(
            pipeline_responses,
            index,
            inner_index,
            Value::ServerError(error),
            address,
        )?;
    }
    Ok(())
}

/// Handles a MOVED redirection error by updating the cluster topology.
/// If updating the topology fails, the error is returned.
async fn pipeline_handle_moved_redirect<C>(
    core: Core<C>,
    redis_error: &RedisError,
) -> Result<(), ServerError>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    let redirect_node =
        RedirectNode::from_option_tuple(redis_error.redirect_node()).ok_or_else(|| {
            ServerError::ExtensionError {
                code: "ParsingError".to_string(),
                detail: Some("Failed to parse MOVED error".to_string()),
            }
        })?;

    ClusterConnInner::update_upon_moved_error(
        core.clone(),
        redirect_node.slot,
        redirect_node.address.into(),
    )
    .await
    .map_err(Into::into)
}

/// Append the commands that encountered errors during pipeline execution for later retry.
///
/// This function processes the commands that need to be retried based on the provided indices,
/// addresses, and errors. It attempts to retrieve the original commands from the pipeline and
/// reassigns them to the appropriate node pipelines for execution.
///
/// # Arguments
///
/// * `pipeline_map` - A mutable reference to the map of node pipelines where the commands will be added.
/// * `pipeline` - A reference to the original pipeline containing the commands.
/// * `core` - The core object that provides access to connection locks and other resources.
/// * `indices_addresses_and_errors` - A vector of tuples containing:
///   - The index and optional inner index of the command in the pipeline.
///   - The address of the node where the command was originally sent.
///   - The `ServerError` that occurred.
/// * `pipeline_responses` - A mutable reference to the collection of pipeline responses.
/// * `response_policies` - A HashMap of routing info and response policies to the pipeline commands.
async fn append_commands_to_retry<C>(
    pipeline_map: &mut NodePipelineMap<C>,
    pipeline: &crate::Pipeline,
    core: Core<C>,
    indices_addresses_and_errors: Vec<((usize, Option<usize>), String, ServerError)>,
    pipeline_responses: &mut PipelineResponses,
    response_policies: &mut ResponsePoliciesMap,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    for ((index, inner_index), address, mut error) in indices_addresses_and_errors {
        // Retrieve the original command for retry.
        let cmd = match get_original_cmd(pipeline, index, inner_index, Some(response_policies)) {
            Ok(cmd) => cmd,
            Err(server_error) => {
                // Failed to retrieve the original command. Append this error and continue.
                error.append_detail(&server_error);
                add_pipeline_result(
                    pipeline_responses,
                    index,
                    inner_index,
                    Value::ServerError(error),
                    address.clone(),
                )?;
                continue;
            }
        };

        let routing = InternalSingleNodeRouting::ByAddress(address.clone());
        let connection = ClusterConnInner::get_connection(routing, core.clone(), None).await;

        // Add the command to the node pipeline map for retry. Otherwise, append the error.
        match connection {
            Ok((addr, conn)) => {
                add_command_to_node_pipeline_map(
                    pipeline_map,
                    addr,
                    conn,
                    cmd,
                    index,
                    inner_index,
                    false,
                    true,
                );
            }
            Err(redis_error) => {
                error.append_detail(&redis_error.into());
                add_pipeline_result(
                    pipeline_responses,
                    index,
                    inner_index,
                    Value::ServerError(error),
                    address,
                )?;
            }
        }
    }
    Ok(())
}

/// Retrieves the original command from the pipeline based on the provided index and inner index.
/// If the command requires multi-slot handling, the function extracts the indices for the sub-commands.
fn get_original_cmd(
    pipeline: &crate::Pipeline,
    index: usize,
    inner_index: Option<usize>,
    response_policies: Option<&ResponsePoliciesMap>,
) -> Result<Arc<Cmd>, ServerError> {
    // Retrieve the command from the pipeline by index.
    let cmd = pipeline
        .get_command(index)
        .ok_or_else(|| ServerError::ExtensionError {
            code: "IndexNotFoundInPipelineResponses".to_string(),
            detail: Some(format!("Index {index} was not found in pipeline")),
        })?;

    // If the command requires multi-slot handling, the `inner_index` helps identify the corresponding sub-command.
    // When the pipeline is splitted into sub-pipelines, routing information and response policies for multi-node commands are stored in a HashMap (response_policies)
    // to facilitate future aggregation of the sub-commands' results, each sub-commands has it own `inner_index`.
    // The `RoutingInfo` for multi-slot commands provides details on how the command is splitted into sub-commands, one for each unique slot.
    // The `inner_index` is used to extract the correct sub-command from the list of sub-commands based on the routing information.
    if inner_index.is_some() {
        // Search for the response policy based on the index.
        let routing_info =
            response_policies.and_then(|map| map.get(&index).map(|tuple| tuple.0.clone()));

        // Check if the response policy requires multi-slot handling
        if let Some(MultipleNodeRoutingInfo::MultiSlot((slots, _))) = routing_info {
            let inner_index = inner_index.ok_or_else(|| ServerError::ExtensionError {
                code: "IndexNotFoundInPipelineResponses".to_string(),
                detail: Some(format!(
                    "Inner index is required for a multi-slot command: {cmd:?}"
                )),
            })?;

            let indices = slots
                .get(inner_index)
                .ok_or_else(|| ServerError::ExtensionError {
                    code: "IndexNotFoundInPipelineResponses".to_string(),
                    detail: Some(format!(
                "Inner index {inner_index} for multi-slot command {cmd:?} was not found in command slots {slots:?}"
            )),
                })?;

            // Return the sub-command, using the indices for the sub-command.
            return Ok(command_for_multi_slot_indices(cmd.as_ref(), indices.1.iter()).into());
        }
    }
    // For non-multi-slot commands, simply return the original command.
    Ok(cmd)
}

/// This function returns the route for a given pipeline.
/// The function goes over the commands in the pipeline, checks that all key-based commands are routed to the same slot,
/// and returns the route for that specific node.
/// If the pipeline contains no key-based commands, the function returns None.
/// For non-atomic pipelines, the function will return None, regardless of the commands in it.
pub(crate) fn route_for_pipeline(pipeline: &crate::Pipeline) -> RedisResult<Option<Route>> {
    fn route_for_command(cmd: &Cmd) -> Option<Route> {
        match cluster_routing::RoutingInfo::for_routable(cmd) {
            Some(cluster_routing::RoutingInfo::SingleNode(SingleNodeRoutingInfo::Random)) => None,
            Some(cluster_routing::RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::SpecificNode(route),
            )) => Some(route),
            Some(cluster_routing::RoutingInfo::SingleNode(
                SingleNodeRoutingInfo::RandomPrimary,
            )) => Some(Route::new_random_primary()),
            Some(cluster_routing::RoutingInfo::MultiNode(_)) => None,
            Some(cluster_routing::RoutingInfo::SingleNode(SingleNodeRoutingInfo::ByAddress {
                ..
            })) => None,
            None => None,
        }
    }

    if pipeline.is_atomic() {
        // Find first specific slot and send to it. There's no need to check If later commands
        // should be routed to a different slot, since the server will return an error indicating this.
        pipeline
            .cmd_iter()
            .map(|cmd| route_for_command(cmd.as_ref()))
            .try_fold(None, |chosen_route, next_cmd_route| {
                match (chosen_route, next_cmd_route) {
                    (None, _) => Ok(next_cmd_route),
                    (_, None) => Ok(chosen_route),
                    (Some(chosen_route), Some(next_cmd_route)) => {
                        if chosen_route.slot() != next_cmd_route.slot() {
                            Err((ErrorKind::CrossSlot, "Received crossed slots in pipeline").into())
                        } else if chosen_route.slot_addr() == SlotAddr::ReplicaOptional {
                            Ok(Some(next_cmd_route))
                        } else {
                            Ok(Some(chosen_route))
                        }
                    }
                }
            })
    } else {
        // Pipeline is not atomic, so we can have commands with different slots.
        Ok(None)
    }
}
