// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"context"
	"errors"
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

// GlideClusterClient interface compliance check.
var _ interfaces.GlideClusterClientCommands = (*ClusterClient)(nil)

// Client used for connection to cluster servers.
// Use [NewClusterClient] to request a client.
//
// For full documentation refer to [Valkey Glide Wiki].
//
// [Valkey Glide Wiki]: https://github.com/valkey-io/valkey-glide/wiki/Golang-wrapper#cluster
type ClusterClient struct {
	baseClient
}

// Creates a new [ClusterClient] instance and establishes a connection to a Valkey Cluster.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	config - The configuration options for the client, including cluster addresses, authentication credentials, TLS settings,
//	   periodic checks, and Pub/Sub subscriptions.
//
// Return value:
//
//	A connected [ClusterClient] instance.
//
// Remarks:
//
//	Use this static method to create and connect a `GlideClusterClient` to a Valkey Cluster.
//	The client will automatically handle connection establishment, including cluster topology discovery and handling
//	    of authentication and TLS configurations.
//
//	  - **Cluster Topology Discovery**: The client will automatically discover the cluster topology
//	      based on the seed addresses provided.
//	  - **Authentication**: If `ServerCredentials` are provided, the client will attempt to authenticate
//	      using the specified username and password.
//	  - **TLS**: If `UseTLS` is set to `true`, the client will establish a secure connection using TLS.
//	  - **Reconnection Strategy**: The `BackoffStrategy` settings define how the client will attempt to reconnect
//	      in case of disconnections.
//	  - **Pub/Sub Subscriptions**: Predefine Pub/Sub channels and patterns to subscribe to upon connection establishment.
//	      Supports exact channels, patterns, and sharded channels (available since Valkey version 7.0).
func NewClusterClient(config *config.ClusterClientConfiguration) (*ClusterClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}
	if config.HasSubscription() {
		subConfig := config.GetSubscription()
		client.setMessageHandler(NewMessageHandler(subConfig.GetCallback(), subConfig.GetContext()))
	}

	return &ClusterClient{*client}, nil
}

// Executes a batch by processing the queued commands.
//
// See [Valkey Transactions (Atomic Batches)] and [Valkey Pipelines (Non-Atomic Batches)] for details.
//
// Routing Behavior:
//
//   - Atomic batches (Transactions): Routed to the slot owner of the first key in the batch.
//     If no key is found, the request is sent to a random node.
//   - Non-atomic batches (Pipelines): Each command is routed to the node owning the corresponding
//     key's slot. If no key is present, routing follows the command's default request policy.
//     Multi-node commands are automatically split and dispatched to the appropriate nodes.
//
// Behavior notes:
//
// Atomic Batches (Transactions): All key-based commands must map to the same hash slot.
// If keys span different slots, the transaction will fail. If the transaction fails due to a
// `WATCH` command, `Exec` will return `nil`.
//
// Retry and Redirection:
//
// If a redirection error occurs:
//   - Atomic batches (Transactions): The entire transaction will be redirected.
//   - Non-atomic batches (Pipelines): Only commands that encountered redirection errors will be redirected.
//
// Retries for failures will be handled according to the `retry_server_error` and `retry_connection_error` parameters.
//
// Parameters:
//
//	ctx - The context for controlling the command execution
//	batch - A `ClusterBatch` object containing a list of commands to be executed.
//	raiseOnError - Determines how errors are handled within the batch response. When set to
//	  `true`, the first encountered error in the batch will be raised as an error
//	  after all retries and reconnections have been executed. When set to `false`,
//	  errors will be included as part of the batch response array, allowing the caller to process both
//	  successful and failed commands together. In this case, error details will be provided as
//	  instances of `RequestError`.
//
// Return value:
//
// A list of results corresponding to the execution of each command in the batch.
// If a command returns a value, it will be included in the list. If a command doesn't return a value,
// the list entry will be `nil`. If the batch failed due to a `WATCH` command, `Exec` will return `nil`.
//
// [Valkey Transactions (Atomic Batches)]: https://valkey.io/docs/topics/transactions/
// [Valkey Pipelines (Non-Atomic Batches)]: https://valkey.io/docs/topics/pipelining/
func (client *ClusterClient) Exec(ctx context.Context, batch pipeline.ClusterBatch, raiseOnError bool) ([]any, error) {
	return client.executeBatch(ctx, batch.Batch, raiseOnError, nil)
}

// Executes a batch by processing the queued commands.
//
// See [Valkey Transactions (Atomic Batches)] and [Valkey Pipelines (Non-Atomic Batches)] for details.
//
// # Routing Behavior
//
// If a `route` is specified:
//   - The entire batch is sent to the specified node.
//
// If no `route` is specified:
//   - Atomic batches (Transactions): Routed to the slot owner of the first key in the batch.
//     If no key is found, the request is sent to a random node.
//   - Non-atomic batches (Pipelines): Each command is routed to the node owning the corresponding
//     key's slot. If no key is present, routing follows the command's default request policy.
//     Multi-node commands are automatically split and dispatched to the appropriate nodes.
//
// # Behavior notes
//
// Atomic Batches (Transactions): All key-based commands must map to the same hash slot.
// If keys span different slots, the transaction will fail. If the transaction fails due to a
// `WATCH` command, `Exec` will return `nil`.
//
// # Retry and Redirection
//
// If a redirection error occurs:
//   - Atomic batches (Transactions): The entire transaction will be redirected.
//   - Non-atomic batches (Pipelines): Only commands that encountered redirection errors will be redirected.
//
// Retries for failures will be handled according to the `retry_server_error` and `retry_connection_error` parameters.
//
// Parameters:
//
//	ctx - The context for controlling the command execution
//	batch - A `ClusterBatch` object containing a list of commands to be executed.
//	raiseOnError - Determines how errors are handled within the batch response. When set to
//	  `true`, the first encountered error in the batch will be raised as an error
//	  after all retries and reconnections have been executed. When set to `false`,
//	  errors will be included as part of the batch response array, allowing the caller to process both
//	  successful and failed commands together. In this case, error details will be provided as
//	  instances of `RequestError`.
//	options - A [ClusterBatchOptions] object containing execution options.
//
// Return value:
//
// A list of results corresponding to the execution of each command in the batch.
// If a command returns a value, it will be included in the list. If a command doesn't return a value,
// the list entry will be `nil`. If the batch failed due to a `WATCH` command, `ExecWithOptions` will return `nil`.
//
// [Valkey Transactions (Atomic Batches)]: https://valkey.io/docs/topics/transactions/
// [Valkey Pipelines (Non-Atomic Batches)]: https://valkey.io/docs/topics/pipelining/
func (client *ClusterClient) ExecWithOptions(
	ctx context.Context,
	batch pipeline.ClusterBatch,
	raiseOnError bool,
	options pipeline.ClusterBatchOptions,
) ([]any, error) {
	if batch.Batch.IsAtomic && options.RetryStrategy != nil {
		return nil, errors.New("retry strategy is not supported for atomic batches (transactions)")
	}
	converted := options.Convert()
	return client.executeBatch(ctx, batch.Batch, raiseOnError, &converted)
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed command.
//
// The command will be routed automatically based on the passed command's default request policy.
//
// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
//
// This function should only be used for single-response commands. Commands that don't return complete response and awaits
// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	args - Arguments for the custom command including the command name.
//
// Return value:
//
//	The returned value for the custom command.
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *ClusterClient) CustomCommand(ctx context.Context, args []string) (models.ClusterValue[any], error) {
	res, err := client.executeCommand(ctx, C.CustomCommand, args)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	return models.CreateClusterValue[any](data), nil
}

// Gets information and statistics about the server.
// The command will be routed to all primary nodes.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	A map where each address is the key and its corresponding node response is the information for the default sections.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *ClusterClient) Info(ctx context.Context) (map[string]string, error) {
	result, err := client.executeCommand(ctx, C.Info, []string{})
	if err != nil {
		return nil, err
	}

	return handleStringToStringMapResponse(result)
}

// Gets information and statistics about the server.
// The command will be routed to all primary nodes, unless `route` in [options.ClusterInfoOptions] is provided.
//
// Note:
//
// Starting from server version 7, command supports multiple section arguments.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	options - Additional command parameters, see [ClusterInfoOptions] for more details.
//
// Return value:
//
//	When specifying a route other than a single node or when route is not given,
//	it returns a map where each address is the key and its corresponding node response is the value.
//	When a single node route is given, command returns a string containing the information for the sections requested.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *ClusterClient) InfoWithOptions(
	ctx context.Context,
	options options.ClusterInfoOptions,
) (models.ClusterValue[string], error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	if options.RouteOption == nil || options.RouteOption.Route == nil {
		response, err := client.executeCommand(ctx, C.Info, optionArgs)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		return models.CreateClusterMultiValue[string](data), nil
	}
	response, err := client.executeCommandWithRoute(ctx, C.Info, optionArgs, options.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	if options.Route.IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		return models.CreateClusterMultiValue[string](data), nil
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	return models.CreateClusterSingleValue[string](data), nil
}

// CustomCommandWithRoute executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed command.
//
// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	args  - Arguments for the custom command including the command name.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	The returning value depends on the executed command and route.
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *ClusterClient) CustomCommandWithRoute(ctx context.Context,
	args []string,
	route config.Route,
) (models.ClusterValue[any], error) {
	res, err := client.executeCommandWithRoute(ctx, C.CustomCommand, args, route)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	if !route.IsMultiNode() {
		return models.CreateClusterSingleValue[any](data), err
	}
	return models.CreateClusterValue[any](data), nil
}

// Select changes the currently selected database on cluster nodes.
// The command will be routed to all primary nodes by default.
//
// WARNING: This command is NOT RECOMMENDED for production use.
// Upon reconnection, nodes will revert to the database_id specified
// in the client configuration (default: 0), NOT the database selected
// via this command.
//
// RECOMMENDED APPROACH: Use the database_id parameter in client
// configuration instead:
//
//	config := &config.ClusterClientConfiguration{
//		Addresses: []config.NodeAddress{{Host: "localhost", Port: 6379}},
//		DatabaseId: &databaseId, // Recommended: persists across reconnections
//	}
//	client, err := NewClusterClient(config)
//
// CLUSTER BEHAVIOR: This command routes to all nodes by default
// to maintain consistency across the cluster.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	index - The index of the database to select.
//
// Return value:
//
//	A simple `"OK"` response.
//
// [valkey.io]: https://valkey.io/commands/select/
func (client *ClusterClient) Select(ctx context.Context, index int64) (string, error) {
	result, err := client.executeCommand(ctx, C.Select, []string{utils.IntToString(index)})
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// Select changes the currently selected database on cluster nodes.
//
// WARNING: This command is NOT RECOMMENDED for production use.
// Upon reconnection, nodes will revert to the database_id specified
// in the client configuration (default: 0), NOT the database selected
// via this command.
//
// RECOMMENDED APPROACH: Use the database_id parameter in client
// configuration instead.
//
// CLUSTER BEHAVIOR: This command routes to all nodes by default
// to maintain consistency across the cluster.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	index - The index of the database to select.
//	routeOption - Specifies the routing configuration for the command. The client will route the
//	              command to the nodes defined by routeOption.Route. Defaults to all primary nodes.
//
// Return value:
//
//	A simple `"OK"` response.
//
// [valkey.io]: https://valkey.io/commands/select/
func (client *ClusterClient) SelectWithOptions(
	ctx context.Context,
	index int64,
	routeOption options.RouteOption,
) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.Select, []string{utils.IntToString(index)}, routeOption.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// Pings the server.
// The command will be routed to all primary nodes.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	Returns "PONG".
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *ClusterClient) Ping(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.Ping, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Pings the server.
// The command will be routed to all primary nodes, unless `Route` is provided in `pingOptions`.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	pingOptions - The [ClusterPingOptions] type.
//
// Return value:
//
//	Returns the copy of message.
//
// For example:
//
//	route := options.RouteOption{config.RandomRoute}
//	opts  := options.ClusterPingOptions{ &options.PingOptions{ "Hello" }, &route }
//	result, err := clusterClient.PingWithOptions(context.Background(), opts)
//	fmt.Println(result) // Output: Hello
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *ClusterClient) PingWithOptions(
	ctx context.Context,
	pingOptions options.ClusterPingOptions,
) (string, error) {
	args, err := pingOptions.ToArgs()
	if err != nil {
		return models.DefaultStringResponse, err
	}
	if pingOptions.RouteOption == nil || pingOptions.RouteOption.Route == nil {
		response, err := client.executeCommand(ctx, C.Ping, args)
		if err != nil {
			return models.DefaultStringResponse, err
		}
		return handleStringResponse(response)
	}

	response, err := client.executeCommandWithRoute(ctx, C.Ping, args, pingOptions.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleStringResponse(response)
}

// Returns the server time.
// The command will be routed to a random node, unless Route in opts is provided.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	options - The [RouteOption] type.
//
// Return value:
//
// The current server time as a String array with two elements: A UNIX TIME and the amount
// of microseconds already elapsed in the current second.
// The returned array is in a [UNIX TIME, Microseconds already elapsed] format.
// [valkey.io]: https://valkey.io/commands/time/
func (client *ClusterClient) TimeWithOptions(
	ctx context.Context,
	opts options.RouteOption,
) (models.ClusterValue[[]string], error) {
	result, err := client.executeCommandWithRoute(ctx, C.Time, []string{}, opts.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[[]string](), err
	}
	return handleTimeClusterResponse(result)
}

// Returns the number of keys in the database.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	options - The [RouteOption] type.
//
// Return value:
//
//	The number of keys in the database.
//
// [valkey.io]: https://valkey.io/commands/dbsize/
func (client *ClusterClient) DBSizeWithOptions(ctx context.Context, opts options.RouteOption) (int64, error) {
	result, err := client.executeCommandWithRoute(ctx, C.DBSize, []string{}, opts.Route)
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Deletes all the keys of all the existing databases.
// The command will be routed to all primary nodes.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (client *ClusterClient) FlushAll(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FlushAll, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Deletes all the keys of all the existing databases.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	flushOptions - The [FlushClusterOptions] type.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (client *ClusterClient) FlushAllWithOptions(
	ctx context.Context,
	flushOptions options.FlushClusterOptions,
) (string, error) {
	if flushOptions.RouteOption == nil || flushOptions.RouteOption.Route == nil {
		result, err := client.executeCommand(ctx, C.FlushAll, flushOptions.ToArgs())
		if err != nil {
			return models.DefaultStringResponse, err
		}
		return handleOkResponse(result)
	}
	result, err := client.executeCommandWithRoute(ctx, C.FlushAll, flushOptions.ToArgs(), flushOptions.RouteOption.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Deletes all the keys of the currently selected database.
// The command will be routed to all primary nodes.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *ClusterClient) FlushDB(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FlushDB, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Deletes all the keys of the currently selected database.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	flushOptions - The [FlushClusterOptions] type.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *ClusterClient) FlushDBWithOptions(
	ctx context.Context,
	flushOptions options.FlushClusterOptions,
) (string, error) {
	if flushOptions.RouteOption == nil || flushOptions.RouteOption.Route == nil {
		result, err := client.executeCommand(ctx, C.FlushDB, flushOptions.ToArgs())
		if err != nil {
			return models.DefaultStringResponse, err
		}
		return handleOkResponse(result)
	}
	result, err := client.executeCommandWithRoute(ctx, C.FlushDB, flushOptions.ToArgs(), flushOptions.RouteOption.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Echo the provided message back.
// The command will be routed to a random node.
//
// Parameters:
//
//	message - The provided message.
//
// Return value:
//
//	The provided message
//
// [valkey.io]: https://valkey.io/commands/echo/
func (client *ClusterClient) Echo(ctx context.Context, message string) (models.Result[string], error) {
	return client.echo(ctx, message)
}

// Echo the provided message back.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx     - The context for controlling the command execution.
//	message - The message to be echoed back.
//	opts    - Specifies the routing configuration for the command. The client will route the
//	          command to the nodes defined by `opts.Route`.
//
// Return value:
//
//	The message to be echoed back.
//
// [valkey.io]: https://valkey.io/commands/echo/
func (client *ClusterClient) EchoWithOptions(
	ctx context.Context,
	message string,
	opts options.RouteOption,
) (models.ClusterValue[string], error) {
	response, err := client.executeCommandWithRoute(ctx, C.Echo, []string{message}, opts.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	if (opts.Route).IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		return models.CreateClusterMultiValue[string](data), nil
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	return models.CreateClusterSingleValue[string](data), nil
}

// Helper function to perform the cluster scan.
func (client *ClusterClient) clusterScan(
	ctx context.Context,
	cursor models.ClusterScanCursor,
	opts options.ClusterScanOptions,
) (*C.struct_CommandResponse, error) {
	// Check if context is already done
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	default:
		// Continue with execution
	}

	// make the channel buffered, so that we don't need to acquire the client.mu in the successCallback and failureCallback.
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, NewClosingError("Cluster Scan failed. The client is closed.")
	}
	client.pending[resultChannelPtr] = struct{}{}

	c_cursor := C.CString(cursor.GetCursor())
	// These will be run in LIFO order; make sure not to free c_cursor before remove_cluster_scan_cursor
	defer C.free(unsafe.Pointer(c_cursor))
	defer C.remove_cluster_scan_cursor(c_cursor)

	args, err := opts.ToArgs()
	if err != nil {
		return nil, err
	}

	var cArgsPtr *C.uintptr_t = nil
	var argLengthsPtr *C.ulong = nil
	if len(args) > 0 {
		cArgs, argLengths := toCStrings(args)
		cArgsPtr = &cArgs[0]
		argLengthsPtr = &argLengths[0]
	}

	C.request_cluster_scan(
		client.coreClient,
		C.uintptr_t(pinnedChannelPtr),
		c_cursor,
		C.size_t(len(args)),
		cArgsPtr,
		argLengthsPtr,
	)
	client.mu.Unlock()

	// Wait for result or context cancellation
	var payload payload
	select {
	case <-ctx.Done():
		client.mu.Lock()
		if client.pending != nil {
			delete(client.pending, resultChannelPtr)
		}
		client.mu.Unlock()
		// Start cleanup goroutine
		go func() {
			// Wait for payload on separate channel
			if payload := <-resultChannel; payload.value != nil {
				C.free_command_response(payload.value)
			}
		}()
		return nil, ctx.Err()
	case payload = <-resultChannel:
		// Continue with normal processing
	}

	client.mu.Lock()
	if client.pending != nil {
		delete(client.pending, resultChannelPtr)
	}
	client.mu.Unlock()

	if payload.error != nil {
		return nil, payload.error
	}

	return payload.value, nil
}

// Incrementally iterates over the keys in the cluster.
// The method returns a list containing the next cursor and a list of keys.
//
// This command is similar to the SCAN command but is designed to work in a cluster environment.
// For each iteration, a new cursor object should be used to continue the scan.
// Using the same cursor object for multiple iterations will result in the same keys or unexpected behavior.
// For more information about the Cluster Scan implementation, see
// https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan.
//
// Like the SCAN command, the method can be used to iterate over the keys in the database,
// returning all keys the database has from when the scan started until the scan ends.
// The same key can be returned in multiple scan iterations.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	cursor - The [ClusterScanCursor] object that wraps the scan state.
//	   To start a new scan, create a new empty `ClusterScanCursor` using [models.NewClusterScanCursor()].
//
// Returns:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *ClusterClient) Scan(
	ctx context.Context,
	cursor models.ClusterScanCursor,
) (models.ClusterScanResult, error) {
	response, err := client.clusterScan(ctx, cursor, *options.NewClusterScanOptions())
	if err != nil {
		return models.ClusterScanResult{}, err
	}

	res, err := handleScanResponse(response)
	return models.ClusterScanResult{Cursor: models.NewClusterScanCursorWithId(res.Cursor.String()), Keys: res.Data}, err
}

// Incrementally iterates over the keys in the cluster.
// The method returns a list containing the next cursor and a list of keys.
//
// This command is similar to the SCAN command but is designed to work in a cluster environment.
// For each iteration, a new cursor object should be used to continue the scan.
// Using the same cursor object for multiple iterations will result in the same keys or unexpected behavior.
// For more information about the Cluster Scan implementation, see
// https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#cluster-scan.
//
// Like the SCAN command, the method can be used to iterate over the keys in the database,
// returning all keys the database has from when the scan started until the scan ends.
// The same key can be returned in multiple scan iterations.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	cursor - The [ClusterScanCursor] object that wraps the scan state.
//	   To start a new scan, create a new empty `ClusterScanCursor` using [models.NewClusterScanCursor()].
//	opts - The scan options. Can specify MATCH, COUNT, and TYPE configurations.
//
// Returns:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *ClusterClient) ScanWithOptions(
	ctx context.Context,
	cursor models.ClusterScanCursor,
	opts options.ClusterScanOptions,
) (models.ClusterScanResult, error) {
	response, err := client.clusterScan(ctx, cursor, opts)
	if err != nil {
		return models.ClusterScanResult{}, err
	}

	res, err := handleScanResponse(response)
	return models.ClusterScanResult{Cursor: models.NewClusterScanCursorWithId(res.Cursor.String()), Keys: res.Data}, err
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (client *ClusterClient) Lolwut(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.Lolwut, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	lolwutOptions - The [LolwutOptions] type.
//
// Return value:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (client *ClusterClient) LolwutWithOptions(
	ctx context.Context,
	lolwutOptions options.ClusterLolwutOptions,
) (models.ClusterValue[string], error) {
	args, err := lolwutOptions.ToArgs()
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}

	if lolwutOptions.RouteOption == nil || lolwutOptions.RouteOption.Route == nil {
		response, err := client.executeCommand(ctx, C.Lolwut, args)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		data, err := handleStringResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		return models.CreateClusterSingleValue[string](data), nil
	}

	route := lolwutOptions.RouteOption.Route
	response, err := client.executeCommandWithRoute(ctx, C.Lolwut, args, route)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}

	if route.IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		return models.CreateClusterMultiValue[string](data), nil
	}

	data, err := handleStringResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	return models.CreateClusterSingleValue[string](data), nil
}

// Gets the current connection id.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	The id of the client.
//
// [valkey.io]: https://valkey.io/commands/client-id/
func (client *ClusterClient) ClientId(ctx context.Context) (models.ClusterValue[int64], error) {
	response, err := client.executeCommand(ctx, C.ClientId, []string{})
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	return models.CreateClusterSingleValue[int64](data), nil
}

// Gets the current connection id.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	The id of the client.
//
// [valkey.io]: https://valkey.io/commands/client-id/
func (client *ClusterClient) ClientIdWithOptions(
	ctx context.Context,
	opts options.RouteOption,
) (models.ClusterValue[int64], error) {
	response, err := client.executeCommandWithRoute(ctx, C.ClientId, []string{}, opts.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	if opts.Route != nil &&
		(opts.Route).IsMultiNode() {
		data, err := handleStringIntMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[int64](), err
		}
		return models.CreateClusterMultiValue[int64](data), nil
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	return models.CreateClusterSingleValue[int64](data), nil
}

// Returns UNIX TIME of the last DB save timestamp or startup timestamp if no save was made since then.
// The command is routed to a random node by default, which is safe for read-only commands.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	UNIX TIME of the last DB save executed with success.
//
// [valkey.io]: https://valkey.io/commands/lastsave/
func (client *ClusterClient) LastSave(ctx context.Context) (models.ClusterValue[int64], error) {
	response, err := client.executeCommand(ctx, C.LastSave, []string{})
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	return models.CreateClusterSingleValue[int64](data), nil
}

// Returns UNIX TIME of the last DB save timestamp or startup timestamp if no save was made since then.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	UNIX TIME of the last DB save executed with success.
//
// [valkey.io]: https://valkey.io/commands/lastsave/
func (client *ClusterClient) LastSaveWithOptions(
	ctx context.Context,
	opts options.RouteOption,
) (models.ClusterValue[int64], error) {
	response, err := client.executeCommandWithRoute(ctx, C.LastSave, []string{}, opts.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	if opts.Route != nil &&
		(opts.Route).IsMultiNode() {
		data, err := handleStringIntMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[int64](), err
		}
		return models.CreateClusterMultiValue[int64](data), nil
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[int64](), err
	}
	return models.CreateClusterSingleValue[int64](data), nil
}

// Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	OK to confirm that the statistics were successfully reset.
//
// [valkey.io]: https://valkey.io/commands/config-resetstat/
func (client *ClusterClient) ConfigResetStat(ctx context.Context) (string, error) {
	response, err := client.executeCommand(ctx, C.ConfigResetStat, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(response)
}

// Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	OK to confirm that the statistics were successfully reset.
//
// [valkey.io]: https://valkey.io/commands/config-resetstat/
func (client *ClusterClient) ConfigResetStatWithOptions(ctx context.Context, opts options.RouteOption) (string, error) {
	response, err := client.executeCommandWithRoute(ctx, C.ConfigResetStat, []string{}, opts.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(response)
}

// Sets configuration parameters to the specified values.
// Starting from server version 7, command supports multiple parameters.
// The command will be sent to all nodes.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	parameters -  A map consisting of configuration parameters and their respective values to set.
//
// Return value:
//
//	OK if all configurations have been successfully set. Otherwise, raises an error.
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (client *ClusterClient) ConfigSet(ctx context.Context,
	parameters map[string]string,
) (string, error) {
	result, err := client.executeCommand(ctx, C.ConfigSet, utils.MapToString(parameters))
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Sets configuration parameters to the specified values
// Starting from server version 7, command supports multiple parameters.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	parameters -  A map consisting of configuration parameters and their respective values to set.
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	OK if all configurations have been successfully set. Otherwise, raises an error.
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (client *ClusterClient) ConfigSetWithOptions(ctx context.Context,
	parameters map[string]string, opts options.RouteOption,
) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.ConfigSet, utils.MapToString(parameters), opts.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Get the values of configuration parameters.
// The command will be sent to a random node.
//
// Note:
//
// Prior to Version 7.0.0, only one parameter can be send.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	parameters -  An array of configuration parameter names to retrieve values for.
//
// Return value:
//
//	A map of values corresponding to the configuration parameters.
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *ClusterClient) ConfigGet(ctx context.Context,
	parameters []string,
) (map[string]string, error) {
	res, err := client.executeCommand(ctx, C.ConfigGet, parameters)
	if err != nil {
		return nil, err
	}
	data, err := handleStringToStringMapResponse(res)
	if err != nil {
		return nil, err
	}
	return data, nil
}

// Get the values of configuration parameters.
//
// Note:
//
// Prior to Version 7.0.0, only one parameter can be send.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	parameters - An array of configuration parameter names to retrieve values for.
//	opts - Specifies the routing configuration for the command. The client will route the
//	       command to the nodes defined by route.
//
// Return value:
//
//	A map of values corresponding to the configuration parameters.
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *ClusterClient) ConfigGetWithOptions(ctx context.Context,
	parameters []string, opts options.RouteOption,
) (models.ClusterValue[map[string]string], error) {
	res, err := client.executeCommandWithRoute(ctx, C.ConfigGet, parameters, opts.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[map[string]string](), err
	}
	if opts.Route == nil || !opts.Route.IsMultiNode() {
		data, err := handleStringToStringMapResponse(res)
		if err != nil {
			return models.CreateEmptyClusterValue[map[string]string](), err
		}
		return models.CreateClusterSingleValue[map[string]string](data), nil
	}
	data, err := handleMapOfStringMapResponse(res)
	if err != nil {
		return models.CreateEmptyClusterValue[map[string]string](), err
	}
	return models.CreateClusterMultiValue[map[string]string](data), nil
}

// Set the name of the current connection.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	connectionName - Connection name of the current connection.
//
// Return value:
//
//	OK - when connection name is set
//
// [valkey.io]: https://valkey.io/commands/client-setname/
func (client *ClusterClient) ClientSetName(ctx context.Context, connectionName string) (string, error) {
	response, err := client.executeCommand(ctx, C.ClientSetName, []string{connectionName})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	data, err := handleOkResponse(response)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return data, nil
}

// Set the name of the current connection.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	connectionName - Connection name of the current connection.
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	OK - when connection name is set
//
// [valkey.io]: https://valkey.io/commands/client-setname/
func (client *ClusterClient) ClientSetNameWithOptions(ctx context.Context,
	connectionName string,
	opts options.RouteOption,
) (string, error) {
	response, err := client.executeCommandWithRoute(ctx, C.ClientSetName, []string{connectionName}, opts.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	data, err := handleOkResponse(response)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return data, nil
}

// Gets the name of the current connection.
// The command will be routed to a random node.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	If a name is set, returns the name of the client connection as a models.Result[string].
//	Otherwise, returns [models.CreateNilStringResult()] if no name is assigned.
//
// [valkey.io]: https://valkey.io/commands/client-getname/
func (client *ClusterClient) ClientGetName(ctx context.Context) (models.Result[string], error) {
	response, err := client.executeCommand(ctx, C.ClientGetName, []string{})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	data, err := handleStringOrNilResponse(response)
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return data, nil
}

// Gets the name of the current connection.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	If a name is set, returns the name of the client connection as a ClusterValue wrapped models.Result[string].
//	Otherwise, returns [models.CreateEmptyClusterValue[models.Result[string]]()] if no name is assigned.
//
// [valkey.io]: https://valkey.io/commands/client-getname/
func (client *ClusterClient) ClientGetNameWithOptions(
	ctx context.Context,
	opts options.RouteOption,
) (models.ClusterValue[models.Result[string]], error) {
	response, err := client.executeCommandWithRoute(ctx, C.ClientGetName, []string{}, opts.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[models.Result[string]](), err
	}
	if opts.Route != nil &&
		(opts.Route).IsMultiNode() {
		data, err := handleStringToStringOrNilMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[models.Result[string]](), err
		}
		return models.CreateClusterMultiValue[models.Result[string]](data), nil
	}
	data, err := handleStringOrNilResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[models.Result[string]](), err
	}
	return models.CreateClusterSingleValue[models.Result[string]](data), nil
}

// Rewrites the configuration file with the current configuration.
// The command will be routed a random node.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	"OK" when the configuration was rewritten properly, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/config-rewrite/
func (client *ClusterClient) ConfigRewrite(ctx context.Context) (string, error) {
	response, err := client.executeCommand(ctx, C.ConfigRewrite, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(response)
}

// Rewrites the configuration file with the current configuration.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	"OK" when the configuration was rewritten properly, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/config-rewrite/
func (client *ClusterClient) ConfigRewriteWithOptions(ctx context.Context, opts options.RouteOption) (string, error) {
	response, err := client.executeCommandWithRoute(ctx, C.ConfigRewrite, []string{}, opts.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(response)
}

// Returns a random key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	A random key from the database.
//
// [valkey.io]: https://valkey.io/commands/randomkey/
func (client *ClusterClient) RandomKey(ctx context.Context) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.RandomKey, []string{})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Returns a random key.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	 opts - specifies the routing configuration for the command.
//
//		 The client will route the command to the nodes defined by route,
//		 and will return the first successful result.
//
// Return value:
//
//	A random key from the database.
//
// [valkey.io]: https://valkey.io/commands/randomkey/
func (client *ClusterClient) RandomKeyWithRoute(ctx context.Context, opts options.RouteOption) (models.Result[string], error) {
	result, err := client.executeCommandWithRoute(ctx, C.RandomKey, []string{}, opts.Route)
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Loads a library to Valkey.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	libraryCode - The source code that implements the library.
//	replace - Whether the given library should overwrite a library with the same name if it
//	already exists.
//	route - Specifies the routing configuration for the command. The client will route the
//	command to the nodes defined by route.
//
// Return value:
//
//	The library name that was loaded.
//
// [valkey.io]: https://valkey.io/commands/function-load/
func (client *ClusterClient) FunctionLoadWithRoute(ctx context.Context,
	libraryCode string,
	replace bool,
	route options.RouteOption,
) (string, error) {
	args := []string{}
	if replace {
		args = append(args, constants.ReplaceKeyword)
	}
	args = append(args, libraryCode)
	result, err := client.executeCommandWithRoute(ctx, C.FunctionLoad, args, route.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Deletes all function libraries.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (client *ClusterClient) FunctionFlushWithRoute(ctx context.Context, route options.RouteOption) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.FunctionFlush, []string{}, route.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Deletes all function libraries in synchronous mode.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (client *ClusterClient) FunctionFlushSyncWithRoute(ctx context.Context, route options.RouteOption) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.FunctionFlush, []string{string(options.SYNC)}, route.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Deletes all function libraries in asynchronous mode.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (client *ClusterClient) FunctionFlushAsyncWithRoute(ctx context.Context, route options.RouteOption) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.FunctionFlush, []string{string(options.ASYNC)}, route.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Invokes a previously loaded function.
// To route to a replica please refer to [ClusterClient.FCallReadOnly].
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall/
func (client *ClusterClient) FCallWithRoute(
	ctx context.Context,
	function string,
	route options.RouteOption,
) (models.ClusterValue[any], error) {
	result, err := client.executeCommandWithRoute(
		ctx,
		C.FCall,
		[]string{function, utils.IntToString(0)},
		route.Route,
	)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	if route.Route != nil &&
		(route.Route).IsMultiNode() {
		data, err := handleStringToAnyMapResponse(result)
		if err != nil {
			return models.CreateEmptyClusterValue[any](), err
		}
		return models.CreateClusterMultiValue[any](data), nil
	}
	data, err := handleAnyResponse(result)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	return models.CreateClusterSingleValue[any](data), nil
}

// Invokes a previously loaded read-only function.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall_ro/
func (client *ClusterClient) FCallReadOnlyWithRoute(ctx context.Context,
	function string,
	route options.RouteOption,
) (models.ClusterValue[any], error) {
	result, err := client.executeCommandWithRoute(
		ctx,
		C.FCallReadOnly,
		[]string{function, utils.IntToString(0)},
		route.Route,
	)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	if route.Route != nil &&
		(route.Route).IsMultiNode() {
		data, err := handleStringToAnyMapResponse(result)
		if err != nil {
			return models.CreateEmptyClusterValue[any](), err
		}
		return models.CreateClusterMultiValue[any](data), nil
	}
	data, err := handleAnyResponse(result)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	return models.CreateClusterSingleValue[any](data), nil
}

// Invokes a previously loaded function.
// The command will be routed to a random primary node.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	args - An `array` of `function` arguments. `args` should not represent names of keys.
//
// Return value:
//
//	The invoked function's return value wrapped by a [models.ClusterValue].
//
// [valkey.io]: https://valkey.io/commands/fcall/
func (client *ClusterClient) FCallWithArgs(
	ctx context.Context,
	function string,
	args []string,
) (models.ClusterValue[any], error) {
	return client.FCallWithArgsWithRoute(ctx, function, args, options.RouteOption{})
}

// Invokes a previously loaded function.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	arguments - An `array` of `function` arguments. `arguments` should not represent names of keys.
//	route - Specifies the routing configuration for the command. The client will route the
//	    command to the nodes defined by `route`.
//
// Return value:
//
//	The invoked function's return value wrapped by a [models.ClusterValue].
//
// [valkey.io]: https://valkey.io/commands/fcall/
func (client *ClusterClient) FCallWithArgsWithRoute(ctx context.Context,
	function string,
	args []string,
	route options.RouteOption,
) (models.ClusterValue[any], error) {
	cmdArgs := append([]string{function, utils.IntToString(0)}, args...)
	result, err := client.executeCommandWithRoute(
		ctx,
		C.FCall,
		cmdArgs,
		route.Route,
	)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	if route.Route != nil &&
		(route.Route).IsMultiNode() {
		data, err := handleStringToAnyMapResponse(result)
		if err != nil {
			return models.CreateEmptyClusterValue[any](), err
		}
		return models.CreateClusterMultiValue[any](data), nil
	}
	data, err := handleAnyResponse(result)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	return models.CreateClusterSingleValue[any](data), nil
}

// Invokes a previously loaded read-only function.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	args - An `array` of `function` arguments. `args` should not represent names of keys.
//	route - Specifies the routing configuration for the command. The client will route the
//	    command to the nodes defined by `route`.
//
// Return value:
//
//	The invoked function's return value wrapped by a [models.ClusterValue].
//
// [valkey.io]: https://valkey.io/commands/fcall_ro/
func (client *ClusterClient) FCallReadOnlyWithArgsWithRoute(ctx context.Context,
	function string,
	args []string,
	route options.RouteOption,
) (models.ClusterValue[any], error) {
	cmdArgs := append([]string{function, utils.IntToString(0)}, args...)
	result, err := client.executeCommandWithRoute(
		ctx,
		C.FCallReadOnly,
		cmdArgs,
		route.Route,
	)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	if route.Route != nil &&
		(route.Route).IsMultiNode() {
		data, err := handleStringToAnyMapResponse(result)
		if err != nil {
			return models.CreateEmptyClusterValue[any](), err
		}
		return models.CreateClusterMultiValue[any](data), nil
	}
	data, err := handleAnyResponse(result)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	return models.CreateClusterSingleValue[any](data), nil
}

// Invokes a previously loaded read-only function.
// The command will be routed to a random primary node.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	function - The function name.
//	args - An `array` of `function` arguments. `args` should not represent names of keys.
//
// Return value:
//
//	The invoked function's return value wrapped by a [models.ClusterValue].
//
// [valkey.io]: https://valkey.io/commands/fcall_ro/
func (client *ClusterClient) FCallReadOnlyWithArgs(
	ctx context.Context,
	function string,
	args []string,
) (models.ClusterValue[any], error) {
	return client.FCallReadOnlyWithArgsWithRoute(ctx, function, args, options.RouteOption{})
}

// FunctionStats returns information about the function that's currently running and information about the
// available execution engines.
// The command will be routed to all nodes by default.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	A map of node addresses to their function statistics represented by
//	[FunctionStatsResult] object containing the following information:
//	running_script - Information about the running script.
//	engines - Information about available engines and their stats.
//
// [valkey.io]: https://valkey.io/commands/function-stats/
func (client *ClusterClient) FunctionStats(ctx context.Context) (
	map[string]models.FunctionStatsResult, error,
) {
	response, err := client.executeCommand(ctx, C.FunctionStats, []string{})
	if err != nil {
		return nil, err
	}

	stats, err := handleFunctionStatsResponse(response)
	if err != nil {
		return nil, err
	}

	// For multi-node routes, return the map of node addresses to FunctionStatsResult
	return stats, nil
}

// FunctionStatsWithRoute returns information about the function that's currently running and information about the
// available execution engines.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	opts - Specifies the routing configuration for the command. The client will route the
//	       command to the nodes defined by route. If no route is specified, the command
//	       will be routed to all nodes.
//
// Return value:
//
//	A [models.ClusterValue] containing a map of node addresses to their function statistics.
//
// [valkey.io]: https://valkey.io/commands/function-stats/
func (client *ClusterClient) FunctionStatsWithRoute(ctx context.Context,
	opts options.RouteOption,
) (models.ClusterValue[models.FunctionStatsResult], error) {
	response, err := client.executeCommandWithRoute(ctx, C.FunctionStats, []string{}, opts.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[models.FunctionStatsResult](), err
	}

	stats, err := handleFunctionStatsResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[models.FunctionStatsResult](), err
	}

	// single node routes return a single stat response
	if len(stats) == 1 {
		for _, result := range stats {
			return models.CreateClusterSingleValue[models.FunctionStatsResult](result), nil
		}
	}

	// For multi-node routes, return the map of node addresses to FunctionStatsResult
	return models.CreateClusterMultiValue[models.FunctionStatsResult](stats), nil
}

// Deletes a library and all its functions.
// The command will be routed to all primary nodes.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	libName - The library name to delete.
//
// Return value:
//
//	"OK" if the library exists, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/function-delete/
func (client *ClusterClient) FunctionDelete(ctx context.Context, libName string) (string, error) {
	return client.FunctionDeleteWithRoute(ctx, libName, options.RouteOption{})
}

// Deletes a library and all its functions.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	libName - The library name to delete.
//	route - Specifies the routing configuration for the command. The client will route the
//	    command to the nodes defined by `route`.
//
// Return value:
//
//	"OK" if the library exists, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/function-delete/
func (client *ClusterClient) FunctionDeleteWithRoute(
	ctx context.Context,
	libName string,
	route options.RouteOption,
) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.FunctionDelete, []string{libName}, route.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Kills a function that is currently executing.
//
// `FUNCTION KILL` terminates read-only functions only.
//
// Since:
//
//	Valkey 7.0 and above.
//
// Note:
//
//	This command will be routed to all nodes.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	`OK` if function is terminated. Otherwise, throws an error.
//
// [valkey.io]: https://valkey.io/commands/function-kill/
func (client *ClusterClient) FunctionKill(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionKill, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Kills a function that is currently executing.
//
// `FUNCTION KILL` terminates read-only functions only.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	`OK` if function is terminated. Otherwise, throws an error.
//
// [valkey.io]: https://valkey.io/commands/function-kill/
func (client *ClusterClient) FunctionKillWithRoute(ctx context.Context, route options.RouteOption) (string, error) {
	result, err := client.executeCommandWithRoute(
		ctx,
		C.FunctionKill,
		[]string{},
		route.Route,
	)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Returns information about the functions and libraries.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	query - The query to use to filter the functions and libraries.
//
// Return value:
//
//	A list of info about queried libraries and their functions.
//
// [valkey.io]: https://valkey.io/commands/function-list/
func (client *ClusterClient) FunctionList(ctx context.Context, query models.FunctionListQuery) ([]models.LibraryInfo, error) {
	response, err := client.executeCommand(ctx, C.FunctionList, query.ToArgs())
	if err != nil {
		return nil, err
	}
	return handleFunctionListResponse(response)
}

// Returns information about the functions and libraries.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	query - The query to use to filter the functions and libraries.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	A [models.ClusterValue] containing a list of info about queried libraries and their functions.
//
// [valkey.io]: https://valkey.io/commands/function-list/
func (client *ClusterClient) FunctionListWithRoute(ctx context.Context,
	query models.FunctionListQuery,
	route options.RouteOption,
) (models.ClusterValue[[]models.LibraryInfo], error) {
	response, err := client.executeCommandWithRoute(ctx, C.FunctionList, query.ToArgs(), route.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[[]models.LibraryInfo](), err
	}

	if route.Route != nil && route.Route.IsMultiNode() {
		multiNodeLibs, err := handleFunctionListMultiNodeResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[[]models.LibraryInfo](), err
		}
		return models.CreateClusterMultiValue[[]models.LibraryInfo](multiNodeLibs), nil
	}

	libs, err := handleFunctionListResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[[]models.LibraryInfo](), err
	}
	return models.CreateClusterSingleValue[[]models.LibraryInfo](libs), nil
}

// Publish posts a message to the specified channel. Returns the number of clients that received the message.
//
// Channel can be any string, but common patterns include using "." to create namespaces like
// "news.sports" or "news.weather".
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	channel - The channel to publish the message to.
//	message - The message to publish.
//	sharded - Whether the channel is sharded.
//
// Return value:
//
//	The number of clients that received the message.
//
// [valkey.io]: https://valkey.io/commands/publish
func (client *ClusterClient) Publish(ctx context.Context, channel string, message string, sharded bool) (int64, error) {
	args := []string{channel, message}

	var requestType C.RequestType
	if sharded {
		requestType = C.SPublish
	} else {
		requestType = C.Publish
	}
	result, err := client.executeCommand(ctx, requestType, args)
	if err != nil {
		return 0, err
	}

	return handleIntResponse(result)
}

// Returns a list of all sharded channels.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	A list of shard channels.
//
// [valkey.io]: https://valkey.io/commands/pubsub-shard-channels
func (client *ClusterClient) PubSubShardChannels(ctx context.Context) ([]string, error) {
	result, err := client.executeCommand(ctx, C.PubSubShardChannels, []string{})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// Returns a list of all sharded channels that match the given pattern.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	pattern - A glob-style pattern to match active shard channels.
//
// Return value:
//
//	A list of shard channels that match the given pattern.
//
// [valkey.io]: https://valkey.io/commands/pubsub-shard-channels-with-pattern
func (client *ClusterClient) PubSubShardChannelsWithPattern(ctx context.Context, pattern string) ([]string, error) {
	result, err := client.executeCommand(ctx, C.PubSubShardChannels, []string{pattern})
	if err != nil {
		return nil, err
	}

	return handleStringArrayResponse(result)
}

// Returns the number of subscribers for a sharded channel.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	channels - The channel to get the number of subscribers for.
//
// Return value:
//
//	The number of subscribers for the sharded channel.
//
// [valkey.io]: https://valkey.io/commands/pubsub-shard-numsub
func (client *ClusterClient) PubSubShardNumSub(ctx context.Context, channels ...string) (map[string]int64, error) {
	result, err := client.executeCommand(ctx, C.PubSubShardNumSub, channels)
	if err != nil {
		return nil, err
	}

	return handleStringIntMapResponse(result)
}

// Returns the serialized payload of all loaded libraries.
//
// Note:
//
//	The command will be routed to a random node.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	The serialized payload of all loaded libraries.
//
// [valkey.io]: https://valkey.io/commands/function-dump/
func (client *ClusterClient) FunctionDump(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionDump, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Returns the serialized payload of all loaded libraries.
// The command will be routed to the nodes defined by the route parameter.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command.
//
// Return value:
//
//	A [models.ClusterValue] containing the serialized payload of all loaded libraries.
//
// [valkey.io]: https://valkey.io/commands/function-dump/
func (client *ClusterClient) FunctionDumpWithRoute(
	ctx context.Context,
	route config.Route,
) (models.ClusterValue[string], error) {
	response, err := client.executeCommandWithRoute(ctx, C.FunctionDump, []string{}, route)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	if route != nil && route.IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[string](), err
		}
		return models.CreateClusterMultiValue[string](data), nil
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return models.CreateEmptyClusterValue[string](), err
	}
	return models.CreateClusterSingleValue[string](data), nil
}

// Restores libraries from the serialized payload returned by `FunctionDump`.
//
// Note:
//
//	The command will be routed to all primary nodes.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	payload - The serialized data from `FunctionDump`.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (client *ClusterClient) FunctionRestore(ctx context.Context, payload string) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionRestore, []string{payload})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Restores libraries from the serialized payload.
// The command will be routed to the nodes defined by the route parameter.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	payload - The serialized data from dump operation.
//	route - Specifies the routing configuration for the command.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (client *ClusterClient) FunctionRestoreWithRoute(
	ctx context.Context,
	payload string,
	route config.Route,
) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.FunctionRestore, []string{payload}, route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Restores libraries from the serialized payload returned by `FunctionDump`.
//
// Note:
//
//	The command will be routed to all primary nodes.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	payload - The serialized data from `FunctionDump`.
//	policy - A policy for handling existing libraries.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (client *ClusterClient) FunctionRestoreWithPolicy(
	ctx context.Context,
	payload string,
	policy constants.FunctionRestorePolicy,
) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionRestore, []string{payload, string(policy)})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Restores libraries from the serialized payload.
// The command will be routed to the nodes defined by the route parameter.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	payload - The serialized data from dump operation.
//	policy - A policy for handling existing libraries.
//	route - Specifies the routing configuration for the command.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (client *ClusterClient) FunctionRestoreWithPolicyWithRoute(
	ctx context.Context,
	payload string,
	policy constants.FunctionRestorePolicy,
	route config.Route,
) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.FunctionRestore, []string{payload, string(policy)}, route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Executes a Lua script on the server with routing information.
//
// This function simplifies the process of invoking scripts on the server by using an object that
// represents a Lua script. The script loading and execution will all be handled internally. If
// the script has not already been loaded, it will be loaded automatically using the
// `SCRIPT LOAD` command. After that, it will be invoked using the `EVALSHA`
// command.
//
// Note:
//
//	The command will be routed to a random node, unless `route` is provided.
//
// See [LOAD] and [EVALSHA] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	script - The Lua script to execute.
//	route - Routing information for the script execution.
//
// Return value:
//
//	The result of the script execution.
//
// [LOAD]: https://valkey.io/commands/script-load/
// [EVALSHA]: https://valkey.io/commands/evalsha/
func (client *ClusterClient) InvokeScriptWithRoute(
	ctx context.Context,
	script options.Script,
	route options.RouteOption,
) (models.ClusterValue[any], error) {
	response, err := client.baseClient.executeScriptWithRoute(ctx, script.GetHash(), []string{}, []string{}, route.Route)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}
	if route.Route != nil && route.Route.IsMultiNode() {
		data, err := handleStringToAnyMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[any](), err
		}
		return models.CreateClusterMultiValue[any](data), nil
	}

	return models.CreateClusterSingleValue[any](response), nil
}

// Executes a Lua script on the server with cluster script options.
//
// This function simplifies the process of invoking scripts on the server by using an object that
// represents a Lua script. The script loading, argument preparation, and execution will all be
// handled internally. If the script has not already been loaded, it will be loaded automatically
// using the `SCRIPT LOAD` command. After that, it will be invoked using the
// `EVALSHA` command.
//
// Note:
//
//   - all `keys` in `clusterScriptOptions` must map to the same hash slot.
//   - the command will be routed based on the Route specified in clusterScriptOptions.
//
// See [LOAD] and [EVALSHA] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	script - The script to execute.
//	clusterScriptOptions - Combined options for script execution including keys, arguments, and routing information.
//
// Return value:
//
//	The result of the script execution.
//
// [LOAD]: https://valkey.io/commands/script-load/
// [EVALSHA]: https://valkey.io/commands/evalsha/
func (client *ClusterClient) InvokeScriptWithClusterOptions(
	ctx context.Context,
	script options.Script,
	clusterScriptOptions options.ClusterScriptOptions,
) (models.ClusterValue[any], error) {
	args := clusterScriptOptions.Args
	route := clusterScriptOptions.Route

	response, err := client.baseClient.executeScriptWithRoute(ctx, script.GetHash(), []string{}, args, route)
	if err != nil {
		return models.CreateEmptyClusterValue[any](), err
	}

	if route != nil && route.IsMultiNode() {
		data, err := handleStringToAnyMapResponse(response)
		if err != nil {
			return models.CreateEmptyClusterValue[any](), err
		}
		return models.CreateClusterMultiValue[any](data), nil
	}

	return models.CreateClusterSingleValue[any](response), nil
}

// Checks existence of scripts in the script cache by their SHA1 digest.
//
// Note:
//
//	The command will be routed to all primary nodes by default.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	sha1s - SHA1 digests of Lua scripts to be checked.
//
// Return value:
//
//	An array of boolean values indicating the existence of each script.
//
// [valkey.io]: https://valkey.io/commands/script-exists
func (client *ClusterClient) ScriptExists(ctx context.Context, sha1s []string) ([]bool, error) {
	response, err := client.executeCommand(ctx, C.ScriptExists, sha1s)
	if err != nil {
		return nil, err
	}

	return handleBoolArrayResponse(response)
}

// Checks existence of scripts in the script cache by their SHA1 digest.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	sha1s - SHA1 digests of Lua scripts to be checked.
//	route - Specifies the routing configuration for the command. The client will route the
//		    command to the nodes defined by `route`.
//
// Return value:
//
//	An array of boolean values indicating the existence of each script.
//
// [valkey.io]: https://valkey.io/commands/script-exists
func (client *ClusterClient) ScriptExistsWithRoute(
	ctx context.Context,
	sha1s []string,
	route options.RouteOption,
) ([]bool, error) {
	response, err := client.executeCommandWithRoute(ctx, C.ScriptExists, sha1s, route.Route)
	if err != nil {
		return nil, err
	}

	return handleBoolArrayResponse(response)
}

// Removes all the scripts from the script cache.
// The command will be routed to all nodes.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	OK on success.
//
// [valkey.io]: https://valkey.io/commands/script-flush/
func (client *ClusterClient) ScriptFlush(ctx context.Context) (string, error) {
	return client.baseClient.ScriptFlush(ctx)
}

// Removes all the scripts from the script cache with the specified route options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	options - The ScriptFlushOptions containing the flush mode and route.
//			  The mode can be either SYNC or ASYNC.
//
// Return value:
//
//	OK on success.
//
// [valkey.io]: https://valkey.io/commands/script-flush/
func (client *ClusterClient) ScriptFlushWithOptions(
	ctx context.Context,
	options options.ScriptFlushOptions,
) (string, error) {
	args := []string{}
	if options.Mode != "" {
		args = append(args, string(options.Mode))
	}
	if options.Route == nil {
		result, err := client.executeCommand(ctx, C.ScriptFlush, args)
		if err != nil {
			return models.DefaultStringResponse, err
		}
		return handleOkResponse(result)
	}
	result, err := client.executeCommandWithRoute(ctx, C.ScriptFlush, args, options.RouteOption.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Kills the currently executing Lua script, assuming no write operation was yet performed by the
// script.
//
// See [valkey.io] for more details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by `route`.
//
// Return value:
//
//	`OK` if script is terminated. Otherwise, throws an error.
//
// [valkey.io]: https://valkey.io/commands/script-kill
func (client *ClusterClient) ScriptKillWithRoute(ctx context.Context, route options.RouteOption) (string, error) {
	result, err := client.executeCommandWithRoute(
		ctx,
		C.ScriptKill,
		[]string{},
		route.Route,
	)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Flushes all the previously watched keys for a transaction. Executing a transaction will
// automatically flush all previously watched keys.
// The command will be routed to all primary nodes.
//
// See [valkey.io] and [Valkey Glide Wiki] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	A simple "OK" response.
//
// [valkey.io]: https://valkey.io/commands/unwatch
// [Valkey Glide Wiki]: https://valkey.io/topics/transactions/#cas
func (client *ClusterClient) Unwatch(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.UnWatch, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Flushes all the previously watched keys for a transaction. Executing a transaction will
// automatically flush all previously watched keys.
//
// See [valkey.io] and [Valkey Glide Wiki] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by `route`.
//
// Return value:
//
//	A simple "OK" response.
//
// [valkey.io]: https://valkey.io/commands/unwatch
// [Valkey Glide Wiki]: https://valkey.io/topics/transactions/#cas
func (client *ClusterClient) UnwatchWithOptions(ctx context.Context, route options.RouteOption) (string, error) {
	result, err := client.executeCommandWithRoute(ctx, C.UnWatch, []string{}, route.Route)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}
