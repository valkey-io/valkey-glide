use crate::aio::ConnectionLike;
use crate::cluster_async::ClusterConnInner;
use crate::cluster_async::Connect;
use crate::cluster_client::RetryParams;
use crate::cluster_routing::RoutingInfo;
use crate::types::RetryMethod;
use crate::types::ServerError;
use crate::types::ServerErrorKind;
use cluster_routing::RoutingInfo::{MultiNode, SingleNode};

use crate::cluster_routing::{
    command_for_multi_slot_indices, MultipleNodeRoutingInfo, ResponsePolicy, SingleNodeRoutingInfo,
};
use crate::{cluster_routing, RedisResult, Value};
use crate::{cluster_routing::Route, Cmd, ErrorKind, RedisError};
use std::collections::HashMap;
use std::collections::HashSet;
use std::sync::Arc;

use crate::cluster_async::{RefreshConnectionType, MUTEX_READ_ERR};
use crate::Pipeline;
use futures::FutureExt;
use rand::prelude::IteratorRandom;
use tokio::sync::oneshot;
use tokio::sync::oneshot::error::RecvError;

use super::boxed_sleep;
use super::CmdArg;
use super::PendingRequest;
use super::RedirectNode;
use super::RequestInfo;
use super::{Core, InternalSingleNodeRouting, OperationTarget, Response};

/// Represents a pipeline command execution context for a specific node
#[derive(Default)]
pub struct NodePipelineContext<C> {
    /// The pipeline of commands to be executed
    pub pipeline: Pipeline,
    /// The connection to the node
    pub connection: C,
    /// Vector of (command_index, inner_index) pairs tracking command order
    /// command_index: Position in the original pipeline
    /// inner_index: Optional sub-index for multi-node operations (e.g. MSET)
    pub command_indices: Vec<(usize, Option<usize>)>,
}

/// Maps node addresses to their pipeline execution contexts
pub type NodePipelineMap<C> = HashMap<String, NodePipelineContext<C>>;

impl<C> NodePipelineContext<C> {
    fn new(connection: C) -> Self {
        Self {
            pipeline: Pipeline::new(),
            connection,
            command_indices: Vec::new(),
        }
    }

    // Adds a command to the pipeline and records its index
    fn add_command(&mut self, cmd: Cmd, index: usize, inner_index: Option<usize>) {
        self.pipeline.add_command(cmd);
        self.command_indices.push((index, inner_index));
    }
}

/// `NodeResponse` represents a response from a node along with its source node address.
type NodeResponse = (Value, String);
/// `PipelineResponses` represents the responses for each pipeline command.
/// The outer `Vec` represents the pipeline commands, and each inner `Vec` contains (response, address) pairs.
/// Since some commands can be executed across multiple nodes (e.g., multi-node commands), a single command
/// might produce multiple responses, each from a different node. By storing the responses with their
/// respective node addresses, we ensure that we have all the information needed to aggregate the results later.
pub type PipelineResponses = Vec<Vec<NodeResponse>>;

/// `AddressAndIndices` represents the address of a node and the indices of commands associated with that node.
type AddressAndIndices = Vec<(String, Vec<(usize, Option<usize>)>)>;

/// Adds a command to the pipeline map for a specific node address.
pub fn add_command_to_node_pipeline_map<C>(
    pipeline_map: &mut NodePipelineMap<C>,
    address: String,
    connection: C,
    cmd: Cmd,
    index: usize,
    inner_index: Option<usize>,
) {
    pipeline_map
        .entry(address)
        .or_insert_with(|| NodePipelineContext::new(connection))
        .add_command(cmd, index, inner_index);
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
/// - A `Vec<(usize, MultipleNodeRoutingInfo, Option<ResponsePolicy>)>` containing the routing information
///   and response policies for multi-node commands, along with the index of the command in the pipeline, for aggregating the responses later.
pub async fn map_pipeline_to_nodes<C>(
    pipeline: &crate::Pipeline,
    core: Core<C>,
) -> Result<
    (
        NodePipelineMap<C>,
        Vec<(usize, MultipleNodeRoutingInfo, Option<ResponsePolicy>)>,
    ),
    (OperationTarget, RedisError),
>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    let mut pipelines_per_node = NodePipelineMap::new();
    let mut response_policies = Vec::new();

    for (index, cmd) in pipeline.cmd_iter().enumerate() {
        match RoutingInfo::for_routable(cmd).unwrap_or(SingleNode(SingleNodeRoutingInfo::Random)) {
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
                response_policies.push((index, multi_node_routing.clone(), response_policy));
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
                        for (inner_index, (address, conn)) in connections.into_iter().enumerate() {
                            add_command_to_node_pipeline_map(
                                &mut pipelines_per_node,
                                address,
                                conn.await,
                                cmd.clone(),
                                index,
                                Some(inner_index),
                            );
                        }
                    }
                    MultipleNodeRoutingInfo::MultiSlot((slots, _)) => {
                        handle_pipeline_multi_slot_routing(
                            &mut pipelines_per_node,
                            core.clone(),
                            cmd,
                            index,
                            slots,
                        )
                        .await?;
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
pub async fn handle_pipeline_single_node_routing<C>(
    pipeline_map: &mut NodePipelineMap<C>,
    cmd: Cmd,
    routing: InternalSingleNodeRouting<C>,
    core: Core<C>,
    index: usize,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    if matches!(routing, InternalSingleNodeRouting::Random) && !pipeline_map.is_empty() {
        // Adds the command to a random existing node pipeline in the pipeline map
        let mut rng = rand::thread_rng();
        if let Some(node_context) = pipeline_map.values_mut().choose(&mut rng) {
            node_context.add_command(cmd, index, None);
            return Ok(());
        }
    }

    let (address, conn) =
        ClusterConnInner::get_connection(routing, core, Some(Arc::new(cmd.clone())))
            .await
            .map_err(|err| (OperationTarget::NotFound, err))?;
    add_command_to_node_pipeline_map(pipeline_map, address, conn, cmd, index, None);
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
pub async fn handle_pipeline_multi_slot_routing<C>(
    pipelines_by_connection: &mut NodePipelineMap<C>,
    core: Core<C>,
    cmd: &Cmd,
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
            let new_cmd = command_for_multi_slot_indices(cmd, indices.iter());
            add_command_to_node_pipeline_map(
                pipelines_by_connection,
                address,
                conn.await,
                new_cmd,
                index,
                Some(inner_index),
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
///
/// # Returns
///
/// A tuple containing:
/// - A vector of results for each sub-pipeline execution.
/// - A vector of (address, indices) pairs indicating where each response should be placed.
pub async fn collect_and_send_pending_requests<C>(
    pipeline_map: NodePipelineMap<C>,
    core: Core<C>,
    retry: u32,
) -> (
    Vec<Result<RedisResult<Response>, RecvError>>,
    Vec<(String, Vec<(usize, Option<usize>)>)>,
)
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    // Processes the sub-pipelines to generate pending requests for execution on specific nodes.
    // Each pending request encapsulates all the necessary details for executing commands on a node.
    let (receivers, pending_requests, addresses_and_indices) =
        collect_pipeline_requests(pipeline_map, retry);

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
pub fn collect_pipeline_requests<C>(
    pipelines_by_connection: NodePipelineMap<C>,
    retry: u32,
) -> (
    Vec<oneshot::Receiver<RedisResult<Response>>>,
    Vec<PendingRequest<C>>,
    Vec<(String, Vec<(usize, Option<usize>)>)>,
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
                    route: InternalSingleNodeRouting::Connection {
                        address: address.clone(),
                        conn: async { context.connection }.boxed().shared(),
                    },
                    // mark it as a sub-pipeline mode
                    sub_pipeline: true,
                    retry,
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
pub fn add_pipeline_result(
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
                            Value::ServerError(ServerError::KnownError {
                                kind: (ServerErrorKind::ResponseError),
                                detail: (Some(
                                    "Pipeline response waiting for response".to_string(),
                                )),
                            }),
                            "".to_string(),
                        ),
                    );
                }
                responses[inner_index] = (value, address);
            }
            None => responses.push((value, address)),
        }
        Ok(())
    } else {
        Err((
            OperationTarget::FanOut,
            RedisError::from((
                ErrorKind::ClientError,
                "Index not found in pipeline responses: ",
                index.to_string(),
            )),
        ))
    }
}

/// Processes the responses of pipeline commands and updates the given `pipeline_responses`
/// with the corresponding results.
///
/// The function iterates over the responses along with the `addresses_and_indices` list,
/// ensuring that each response is added to its appropriate position in `pipeline_responses` along with the associated address.
/// If any response indicates an error, the function terminates early and returns the first encountered error.
///
/// # Parameters
///
/// - `pipeline_responses`: A vec that holds the original pipeline commands responses.
/// - `responses`: A list of responses corresponding to each sub-pipeline.
/// - `addresses_and_indices`: A list of (address, indices) pairs indicating where each response should be placed.
///
/// # Returns
///
/// - `Ok(HashMap<RetryMethod, Vec<((usize, Option<usize>), String, ServerError)>>)` failed commands, mapped by their retry method.
/// - `Err((OperationTarget, RedisError))` if a node-level or reception error occurs.
pub async fn process_pipeline_responses(
    pipeline_responses: &mut PipelineResponses,
    responses: Vec<Result<RedisResult<Response>, RecvError>>,
    addresses_and_indices: AddressAndIndices,
) -> Result<
    HashMap<RetryMethod, Vec<((usize, Option<usize>), String, ServerError)>>,
    (OperationTarget, RedisError),
> {
    let mut retry_map: HashMap<RetryMethod, Vec<((usize, Option<usize>), String, ServerError)>> =
        HashMap::new();
    for ((address, command_indices), response_result) in
        addresses_and_indices.into_iter().zip(responses)
    {
        let server_error: ServerError;
        match response_result {
            Ok(Ok(Response::Multiple(values))) => {
                // Add each response to the pipeline_responses vector at the appropriate index
                for ((index, inner_index), value) in command_indices.into_iter().zip(values) {
                    if let Value::ServerError(error) = &value {
                        // Convert error and determine retry method
                        let retry_method = RedisError::from(error.clone()).retry_method();

                        // Update retry map
                        retry_map.entry(retry_method).or_default().push((
                            (index, inner_index),
                            address.clone(),
                            error.clone(),
                        ));
                    }

                    // Add to pipeline responses
                    add_pipeline_result(
                        pipeline_responses,
                        index,
                        inner_index,
                        value,
                        address.clone(),
                    )?;
                }
                continue;
            }
            Ok(Err(err)) => {
                server_error = err.into();
            }
            Ok(Ok(Response::Single(_))) => {
                server_error = ServerError::KnownError {
                    kind: (ServerErrorKind::ResponseError),
                    detail: (Some(
                        "Received a single response for a pipeline with multiple commands."
                            .to_string(),
                    )),
                };
            }
            Ok(Ok(Response::ClusterScanResult(_, _))) => {
                server_error = ServerError::KnownError {
                    kind: (ServerErrorKind::ResponseError),
                    detail: (Some("Received a cluster scan result inside a pipeline.".to_string())),
                };
            }
            Err(err) => {
                server_error = ServerError::ExtensionError {
                    code: ("FatalReceiveError".to_string()),
                    detail: (Some(format!("RecvError occurred: {err}"))),
                };
            }
        }
        // Add the error to the matching indices in the pipeline_responses
        for (index, inner_index) in command_indices {
            add_pipeline_result(
                pipeline_responses,
                index,
                inner_index,
                Value::ServerError(server_error.clone()),
                address.clone(),
            )?;
        }
    }
    Ok(retry_map)
}

/// Processes the pipeline responses and handles errors by retrying the commands.
///
/// This function serves as a loop that processes the pipeline responses and handles any errors
/// by retrying the commands that encountered the error, based on their retry method.
///
/// # Arguments
///
/// * `pipeline_responses` - A mutable reference to the collection of pipeline responses.
/// * `responses` - A list of responses corresponding to each sub-pipeline.
/// * `addresses_and_indices` - A list of (address, indices) pairs indicating where each response should be placed.
/// * `pipeline` - A reference to the original pipeline containing the commands.
/// * `core` - The core object that provides access to connection locks and other resources.
/// * `retry` - The retry counter.
pub async fn process_and_retry_pipeline_responses<C>(
    pipeline_responses: &mut PipelineResponses,
    mut responses: Vec<Result<RedisResult<Response>, RecvError>>,
    mut addresses_and_indices: AddressAndIndices,
    pipeline: &crate::Pipeline,
    core: Core<C>,
    mut retry: u32,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    let retry_params = core
        .get_cluster_param(|params| params.retry_params.clone())
        .expect(MUTEX_READ_ERR);
    loop {
        match process_pipeline_responses(pipeline_responses, responses, addresses_and_indices).await
        {
            Ok(retry_map) => {
                // If there are no retryable errors, or we have reached the maximum number of retries, we're done
                if retry_map.is_empty() || retry >= retry_params.number_of_retries {
                    break Ok(());
                }

                retry = retry.saturating_add(1);
                match handle_retry_map(retry_map, core.clone(), pipeline, retry, pipeline_responses)
                    .await
                {
                    Ok((new_responses, new_addresses_and_indices)) => {
                        // Update responses for the retried commands
                        responses = new_responses;
                        addresses_and_indices = new_addresses_and_indices;
                    }
                    Err(e) => {
                        break Err(e); // If we get into here, it's because `add_pipeline_result` failed to find the matching index
                    }
                }
            }

            // If we get into here, it's because `add_pipeline_result` failed to find the matching index
            Err(e) => break Err(e),
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
///
/// # Returns
///
/// A `Result` containing a tuple:
/// - A vector of results for each sub-pipeline execution.
/// - A vector of (address, indices) pairs indicating where each response should be placed.
pub async fn handle_retry_map<C>(
    retry_map: HashMap<RetryMethod, Vec<((usize, Option<usize>), String, ServerError)>>,
    core: Core<C>,
    pipeline: &crate::Pipeline,
    retry: u32,
    pipeline_responses: &mut PipelineResponses,
) -> Result<
    (
        Vec<Result<RedisResult<Response>, RecvError>>,
        Vec<(String, Vec<(usize, Option<usize>)>)>,
    ),
    (OperationTarget, RedisError),
>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    let retry_params = core
        .get_cluster_param(|params| params.retry_params.clone())
        .expect(MUTEX_READ_ERR);
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
                )
                .await?;
            }
            RetryMethod::RetryImmediately
            | RetryMethod::WaitAndRetry
            | RetryMethod::WaitAndRetryOnPrimaryRedirectOnReplica => {
                handle_retry_logic(
                    retry_method,
                    retry,
                    retry_params.clone(),
                    core.clone(),
                    indices_addresses_and_error,
                    pipeline,
                    pipeline_responses,
                    &mut pipeline_map,
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
                )
                .await?;
            }
        }
    }

    Ok(collect_and_send_pending_requests(pipeline_map, core, retry).await)
}

pub async fn handle_reconnect_logic<C>(
    indices_addresses_and_error: Vec<((usize, Option<usize>), String, ServerError)>,
    core: Core<C>,
    pipeline: &Pipeline,
    pipeline_responses: &mut PipelineResponses,
    should_retry: bool,
    pipeline_map: &mut NodePipelineMap<C>,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    // Extract unique addresses from the provided error entries.
    let addresses: HashSet<String> = indices_addresses_and_error
        .iter()
        .map(|(_, address, _)| address.clone())
        .collect();

    // Refresh the connections for the affected addresses.
    ClusterConnInner::refresh_and_update_connections(
        core.clone(),
        addresses,
        RefreshConnectionType::OnlyUserConnection,
        true,
    )
    .await;

    // If we're supposed to retry, invoke the retry logic.
    if should_retry {
        retry_commands(
            pipeline_map,
            pipeline,
            core.clone(),
            indices_addresses_and_error,
            pipeline_responses,
        )
        .await?;
    }
    Ok(())
}

pub async fn handle_retry_logic<C>(
    retry_method: RetryMethod,
    retry: u32,
    retry_params: RetryParams,
    core: Core<C>,
    indices_addresses_and_error: Vec<((usize, Option<usize>), String, ServerError)>,
    pipeline: &Pipeline,
    pipeline_responses: &mut PipelineResponses,
    pipeline_map: &mut NodePipelineMap<C>,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    match retry_method {
        RetryMethod::WaitAndRetry => {
            let sleep_duration = retry_params.wait_time_for_retry(retry);
            boxed_sleep(sleep_duration).await;
        }
        RetryMethod::WaitAndRetryOnPrimaryRedirectOnReplica => {
            let _ = indices_addresses_and_error
                .iter()
                .map(|(_, address, _)| async {
                    ClusterConnInner::handle_loading_error(
                        core.clone(),
                        address.clone(),
                        retry,
                        retry_params.clone(),
                    )
                    .await;
                });
        }
        _ => {}
    }

    // Retry commands after handling retry conditions
    retry_commands(
        pipeline_map,
        pipeline,
        core,
        indices_addresses_and_error,
        pipeline_responses,
    )
    .await?;

    Ok(())
}

/// Handles the redirection logic for commands that need to be retried due to redirection errors.
///
/// This function processes the retry map entries that indicate a redirection error (e.g., MOVED or ASK).
/// It attempts to obtain a new connection based on the redirection information and reassigns the command
/// to the appropriate node pipeline for execution.
///
/// # Arguments
///
/// * `retry_method` - The retry method indicating the type of redirection (e.g., MOVED or ASK).
/// * `core` - The core object that provides access to connection locks and other resources.
/// * `pipeline` - A reference to the original pipeline containing the commands.
/// * `indices_addresses_and_error` - A vector of tuples containing:
///   - The index and optional inner index of the command in the pipeline.
///   - The address of the node where the command was originally sent.
///   - The `ServerError` that occurred.
/// * `pipeline_responses` - A mutable reference to the collection of pipeline responses.
/// * `pipeline_map` - A mutable reference to the map of node pipelines where the commands will be added.
pub async fn handle_redirect_logic<C>(
    retry_method: RetryMethod,
    core: Core<C>,
    pipeline: &Pipeline,
    indices_addresses_and_error: Vec<((usize, Option<usize>), String, ServerError)>,
    pipeline_responses: &mut PipelineResponses,
    pipeline_map: &mut NodePipelineMap<C>,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    for (indices, address, mut error) in indices_addresses_and_error {
        // Convert the ServerError to a RedisError and try to extract redirect info.
        let redis_error: RedisError = error.clone().into();
        let redirect_info = redis_error
            .redirect()
            .ok_or_else(|| (OperationTarget::FanOut, error.clone().into()))?;

        // Create routing for redirection.
        let routing = InternalSingleNodeRouting::Redirect {
            redirect: redirect_info,
            previous_routing: Box::new(InternalSingleNodeRouting::Random::<C>),
        };

        let (index, inner_index) = indices;

        if matches!(retry_method, RetryMethod::MovedRedirect) {
            if let Some(redirect_node) =
                RedirectNode::from_option_tuple(redis_error.redirect_node())
            {
                if let Err(update_err) = ClusterConnInner::update_upon_moved_error(
                    core.clone(),
                    redirect_node.slot,
                    redirect_node.address.clone().into(),
                )
                .await
                {
                    error.append_detail(&update_err.into());
                    add_pipeline_result(
                        pipeline_responses,
                        index,
                        inner_index,
                        Value::ServerError(error),
                        address.clone(),
                    )?;
                    continue;
                }
            } else {
                let server_error = ServerError::KnownError {
                    kind: ServerErrorKind::Moved,
                    detail: Some("Failed to parse MOVED error".to_string()),
                };
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
        }

        // Retrieve the original command from the pipeline.
        match get_original_cmd(pipeline, index, inner_index) {
            Ok(cmd) => {
                // Attempt to obtain a new connection using the redirection routing.
                match ClusterConnInner::get_connection(routing, core.clone(), None).await {
                    Ok((address, conn)) => add_command_to_node_pipeline_map(
                        pipeline_map,
                        address,
                        conn,
                        cmd.clone(),
                        index,
                        inner_index,
                    ),
                    Err(err) => {
                        error.append_detail(&err.into());
                        add_pipeline_result(
                            pipeline_responses,
                            index,
                            inner_index,
                            Value::ServerError(error),
                            address.clone(),
                        )?;
                    }
                }
            }
            Err(cmd_err) => {
                error.append_detail(&cmd_err);
                add_pipeline_result(
                    pipeline_responses,
                    index,
                    inner_index,
                    Value::ServerError(error),
                    address.clone(),
                )?;
            }
        }
    }
    Ok(())
}

/// Retries the commands that encountered errors during pipeline execution.
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
/// * `indices_addresses_and_errros` - A vector of tuples containing:
///   - The index and optional inner index of the command in the pipeline.
///   - The address of the node where the command was originally sent.
///   - The `ServerError` that occurred.
/// * `pipeline_responses` - A mutable reference to the collection of pipeline responses.
async fn retry_commands<C>(
    pipeline_map: &mut NodePipelineMap<C>,
    pipeline: &crate::Pipeline,
    core: Core<C>,
    indices_addresses_and_errros: Vec<((usize, Option<usize>), String, ServerError)>,
    pipeline_responses: &mut PipelineResponses,
) -> Result<(), (OperationTarget, RedisError)>
where
    C: Clone + ConnectionLike + Connect + Send + Sync + 'static,
{
    for ((index, inner_index), address, mut error) in indices_addresses_and_errros {
        // Retrieve the original command for retry.
        let cmd = match get_original_cmd(pipeline, index, inner_index) {
            Ok(cmd) => cmd,
            Err(server_error) => {
                add_pipeline_result(
                    pipeline_responses,
                    index,
                    inner_index,
                    Value::ServerError(server_error),
                    address.clone(),
                )?;
                continue;
            }
        };

        // Attempt to retrieve a connection for the given address.
        let connection = {
            let lock = core.conn_lock.read().expect(MUTEX_READ_ERR);
            if let Some(conn) = lock.connection_for_address(&address) {
                Ok(conn)
            } else {
                let lookup_error = ServerError::ExtensionError {
                    code: "ConnectionNotFoundForRoute".to_string(),
                    detail: Some(format!("No available connections for address {address}")),
                };
                error.append_detail(&lookup_error);
                Err(error)
            }
        };

        // Depending on whether a connection was found, either add the command to the pipeline
        // or record the error.
        match connection {
            Ok((addr, conn)) => {
                add_command_to_node_pipeline_map(
                    pipeline_map,
                    addr,
                    conn.await,
                    cmd.clone(),
                    index,
                    inner_index,
                );
            }
            Err(server_error) => {
                add_pipeline_result(
                    pipeline_responses,
                    index,
                    inner_index,
                    Value::ServerError(server_error),
                    address.clone(),
                )?;
            }
        }
    }

    Ok(())
}

/// Retrieves the original command from the pipeline based on the provided index and inner index.
fn get_original_cmd(
    pipeline: &crate::Pipeline,
    index: usize,
    inner_index: Option<usize>,
) -> Result<Cmd, ServerError> {
    // Retrieve the command from the pipeline by index.
    let cmd = pipeline
        .get_command(index)
        .ok_or_else(|| ServerError::ExtensionError {
            code: "IndexNotFoundInPipelineResponses".to_string(),
            detail: Some(format!("Index {} was not found in pipeline", index)),
        })?;

    // Determine routing info for the command.
    let routing_info = cluster_routing::RoutingInfo::for_routable(cmd);

    // If the command is a multi-slot command, an inner index is required to extract the correct indices.
    if let Some(RoutingInfo::MultiNode((MultipleNodeRoutingInfo::MultiSlot((slots, _)), _))) =
        routing_info
    {
        // Ensure that an inner index was provided.
        let inner_index = inner_index.ok_or_else(|| ServerError::ExtensionError {
            code: "IndexNotFoundInPipelineResponses".to_string(),
            detail: Some(format!(
                "Inner index doesn't exist for multi-slot command {:?}",
                cmd
            )),
        })?;

        // Attempt to retrieve the slot indices for the given inner index.
        let indices = slots
            .get(inner_index)
            .ok_or_else(|| ServerError::ExtensionError {
                code: "IndexNotFoundInPipelineResponses".to_string(),
                detail: Some(format!(
                "Inner index {} for multi-slot command {:?} was not found in command slots {:?}",
                inner_index, cmd, slots
            )),
            })?;

        // Return the transformed command using the extracted indices.
        Ok(command_for_multi_slot_indices(cmd, indices.1.iter()))
    } else {
        // For non multi-slot commands, simply return a clone.
        Ok(cmd.clone())
    }
}

/// This function returns the route for a given pipeline.
/// The function goes over the commands in the pipeline, checks that all key-based commands are routed to the same slot,
/// and returns the route for that specific node.
/// If the pipeline contains no key-based commands, the function returns None.
/// For non-atomic pipelines, the function will return None, regardless of the commands in it.
pub fn route_for_pipeline(pipeline: &crate::Pipeline) -> RedisResult<Option<Route>> {
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
            .map(route_for_command)
            .try_fold(None, |chosen_route, next_cmd_route| {
                match (chosen_route, next_cmd_route) {
                    (None, _) => Ok(next_cmd_route),
                    (_, None) => Ok(chosen_route),
                    (Some(chosen_route), Some(next_cmd_route)) => {
                        if chosen_route.slot() != next_cmd_route.slot() {
                            Err((
                                ErrorKind::CrossSlot,
                                "Received crossed slots in transaction",
                            )
                                .into())
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
