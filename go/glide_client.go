// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

// #include "lib.h"
import "C"

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/config"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

// Client interface compliance check.
var _ interfaces.GlideClientCommands = (*Client)(nil)

// Client used for connection to standalone servers.
// Use [NewClient] to request a client.
//
// For full documentation refer to [Valkey Glide Wiki].
//
// [Valkey Glide Wiki]: https://github.com/valkey-io/valkey-glide/wiki/Golang-wrapper#standalone
type Client struct {
	baseClient
}

// Creates a new [Client] instance and establishes a connection to a standalone Valkey server.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	config - The configuration options for the client, including server addresses, authentication credentials,
//	    TLS settings, database selection, reconnection strategy, and Pub/Sub subscriptions.
//
// Return value:
//
//	A connected [Client] instance.
//
// Remarks:
//
//	Use this static method to create and connect a `Client` to a standalone Valkey server.
//	The client will automatically handle connection establishment, including any authentication and TLS configurations.
//
//	  - **Authentication**: If `ServerCredentials` are provided, the client will attempt to authenticate
//	      using the specified username and password.
//	  - **TLS**: If `UseTLS` is set to `true`, the client will establish a secure connection using TLS.
//	  - **Reconnection Strategy**: The `BackoffStrategy` settings define how the client will attempt to reconnect
//	      in case of disconnections.
//	  - **Pub/Sub Subscriptions**: Predefine Pub/Sub channels and patterns to subscribe to upon connection establishment.
func NewClient(config *config.ClientConfiguration) (*Client, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}
	if config.HasSubscription() {
		subConfig := config.GetSubscription()
		client.setMessageHandler(NewMessageHandler(subConfig.GetCallback(), subConfig.GetContext()))
	}

	return &Client{*client}, nil
}

// Executes a batch by processing the queued commands.
//
// See [Valkey Transactions (Atomic Batches)] and [Valkey Pipelines (Non-Atomic Batches)] for details.
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
//	  instances of error.
//
// Return value:
//
// A list of results corresponding to the execution of each command in the batch.
// If a command returns a value, it will be included in the list. If a command doesn't return a value,
// the list entry will be `nil`. If the batch failed due to a `WATCH` command, `Exec` will return `nil`.
//
// [Valkey Transactions (Atomic Batches)]: https://valkey.io/docs/topics/transactions/
// [Valkey Pipelines (Non-Atomic Batches)]: https://valkey.io/docs/topics/pipelining/
func (client *Client) Exec(ctx context.Context, batch pipeline.StandaloneBatch, raiseOnError bool) ([]any, error) {
	return client.executeBatch(ctx, batch.Batch, raiseOnError, nil)
}

// Executes a batch by processing the queued commands.
//
// See [Valkey Transactions (Atomic Batches)] and [Valkey Pipelines (Non-Atomic Batches)] for details.
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
//	options - A [StandaloneBatchOptions] object containing execution options.
//
// Return value:
//
// A list of results corresponding to the execution of each command in the batch.
// If a command returns a value, it will be included in the list. If a command doesn't return a value,
// the list entry will be `nil`. If the batch failed due to a `WATCH` command, `ExecWithOptions` will return `nil`.
//
// [Valkey Transactions (Atomic Batches)]: https://valkey.io/docs/topics/transactions/
// [Valkey Pipelines (Non-Atomic Batches)]: https://valkey.io/docs/topics/pipelining/
func (client *Client) ExecWithOptions(
	ctx context.Context,
	batch pipeline.StandaloneBatch,
	raiseOnError bool,
	options pipeline.StandaloneBatchOptions,
) ([]any, error) {
	converted := options.Convert()
	return client.executeBatch(ctx, batch.Batch, raiseOnError, &converted)
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed command.
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
func (client *Client) CustomCommand(ctx context.Context, args []string) (any, error) {
	res, err := client.executeCommand(ctx, C.CustomCommand, args)
	if err != nil {
		return nil, err
	}
	return handleInterfaceResponse(res)
}

// Sets configuration parameters to the specified values.
//
// Note:
//
// Prior to Version 7.0.0, only one parameter can be send.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	parameters - A map consisting of configuration parameters and their respective values to set.
//
// Return value:
//
//	`"OK"` if all configurations have been successfully set. Otherwise, raises an error.
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (client *Client) ConfigSet(ctx context.Context, parameters map[string]string) (string, error) {
	result, err := client.executeCommand(ctx, C.ConfigSet, utils.MapToString(parameters))
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Gets the values of configuration parameters.
//
// Note:
//
// Prior to Version 7.0.0, only one parameter can be send.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	args - A slice of configuration parameter names to retrieve values for.
//
// Return value:
//
//	A map of values corresponding to the configuration parameters.
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *Client) ConfigGet(ctx context.Context, args []string) (map[string]string, error) {
	res, err := client.executeCommand(ctx, C.ConfigGet, args)
	if err != nil {
		return nil, err
	}
	return handleStringToStringMapResponse(res)
}

// Select changes the currently selected database.
//
// WARNING: This command is NOT RECOMMENDED for production use.
// Upon reconnection, the client will revert to the database_id specified
// in the client configuration (default: 0), NOT the database selected
// via this command.
//
// RECOMMENDED APPROACH: Use the database_id parameter in client
// configuration instead:
//
//	config := &config.ClientConfiguration{
//		Addresses: []config.NodeAddress{{Host: "localhost", Port: 6379}},
//		DatabaseId: &databaseId, // Recommended: persists across reconnections
//	}
//	client, err := NewClient(config)
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
func (client *Client) Select(ctx context.Context, index int64) (string, error) {
	result, err := client.executeCommand(ctx, C.Select, []string{utils.IntToString(index)})
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleOkResponse(result)
}

// Gets information and statistics about the server.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	A string with the information for the default sections.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *Client) Info(ctx context.Context) (string, error) {
	return client.InfoWithOptions(ctx, options.InfoOptions{Sections: []constants.Section{}})
}

// Gets information and statistics about the server.
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
//	options - Additional command parameters, see [InfoOptions] for more details.
//
// Return value:
//
//	A string containing the information for the sections requested.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *Client) InfoWithOptions(ctx context.Context, options options.InfoOptions) (string, error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return models.DefaultStringResponse, err
	}
	result, err := client.executeCommand(ctx, C.Info, optionArgs)
	if err != nil {
		return models.DefaultStringResponse, err
	}

	return handleStringResponse(result)
}

// Returns the number of keys in the currently selected database.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	The number of keys in the currently selected database.
//
// [valkey.io]: https://valkey.io/commands/dbsize/
func (client *Client) DBSize(ctx context.Context) (int64, error) {
	result, err := client.executeCommand(ctx, C.DBSize, []string{})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Echo the provided message back.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	message - The provided message.
//
// Return value:
//
//	The provided message
//
// [valkey.io]: https://valkey.io/commands/echo/
func (client *Client) Echo(ctx context.Context, message string) (models.Result[string], error) {
	return client.echo(ctx, message)
}

// Pings the server.
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
func (client *Client) Ping(ctx context.Context) (string, error) {
	return client.PingWithOptions(ctx, options.PingOptions{})
}

// Pings the server.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	pingOptions - The PingOptions type.
//
// Return value:
//
//	Returns the copy of message.
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *Client) PingWithOptions(ctx context.Context, pingOptions options.PingOptions) (string, error) {
	optionArgs, err := pingOptions.ToArgs()
	if err != nil {
		return models.DefaultStringResponse, err
	}
	result, err := client.executeCommand(ctx, C.Ping, optionArgs)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// FlushAll deletes all the keys of all the existing databases.
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
func (client *Client) FlushAll(ctx context.Context) (string, error) {
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
//	mode - The flushing mode, could be either [options.SYNC] or [options.ASYNC}.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (client *Client) FlushAllWithOptions(ctx context.Context, mode options.FlushMode) (string, error) {
	result, err := client.executeCommand(ctx, C.FlushAll, []string{string(mode)})
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
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *Client) FlushDB(ctx context.Context) (string, error) {
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
//	mode - The flushing mode, could be either [options.SYNC] or [options.ASYNC}.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *Client) FlushDBWithOptions(ctx context.Context, mode options.FlushMode) (string, error) {
	result, err := client.executeCommand(ctx, C.FlushDB, []string{string(mode)})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
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
func (client *Client) Lolwut(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.Lolwut, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	opts - The [LolwutOptions] type.
//
// Return value:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (client *baseClient) LolwutWithOptions(ctx context.Context, opts options.LolwutOptions) (string, error) {
	commandArgs, err := opts.ToArgs()
	if err != nil {
		return models.DefaultStringResponse, err
	}
	result, err := client.executeCommand(ctx, C.Lolwut, commandArgs)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
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
func (client *Client) ClientId(ctx context.Context) (int64, error) {
	result, err := client.executeCommand(ctx, C.ClientId, []string{})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns UNIX TIME of the last DB save timestamp or startup timestamp if no save was made since then.
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
func (client *Client) LastSave(ctx context.Context) (int64, error) {
	response, err := client.executeCommand(ctx, C.LastSave, []string{})
	if err != nil {
		return models.DefaultIntResponse, err
	}
	return handleIntResponse(response)
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
func (client *Client) ConfigResetStat(ctx context.Context) (string, error) {
	response, err := client.executeCommand(ctx, C.ConfigResetStat, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(response)
}

// Gets the name of the current connection.
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
func (client *Client) ClientGetName(ctx context.Context) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.ClientGetName, []string{})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Set the name of the current connection.
//
// See [valkey.io] for details.
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
func (client *Client) ClientSetName(ctx context.Context, connectionName string) (string, error) {
	result, err := client.executeCommand(ctx, C.ClientSetName, []string{connectionName})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Move key from the currently selected database to the database specified by `dbIndex`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	key - The key to move.
//	dbIndex - The index of the database to move key to.
//
// Return value:
//
//	`true` if `key` was moved, or `false` if the `key` already exists in the destination
//	database or does not exist in the source database.
//
// [valkey.io]: https://valkey.io/commands/move/
func (client *Client) Move(ctx context.Context, key string, dbIndex int64) (bool, error) {
	result, err := client.executeCommand(ctx, C.Move, []string{key, utils.IntToString(dbIndex)})
	if err != nil {
		return models.DefaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// Iterates incrementally over a database for matching keys.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	cursor - The cursor that points to the next iteration of results.
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *Client) Scan(ctx context.Context, cursor models.Cursor) (models.ScanResult, error) {
	res, err := client.executeCommand(ctx, C.Scan, []string{cursor.String()})
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(res)
}

// Iterates incrementally over a database for matching keys.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//	cursor - The cursor that points to the next iteration of results.
//	scanOptions - Additional command parameters, see [ScanOptions] for more details.
//
// Return value:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *Client) ScanWithOptions(
	ctx context.Context,
	cursor models.Cursor,
	scanOptions options.ScanOptions,
) (models.ScanResult, error) {
	optionArgs, err := scanOptions.ToArgs()
	if err != nil {
		return models.ScanResult{}, err
	}
	res, err := client.executeCommand(ctx, C.Scan, append([]string{cursor.String()}, optionArgs...))
	if err != nil {
		return models.ScanResult{}, err
	}
	return handleScanResponse(res)
}

// Rewrites the configuration file with the current configuration.
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
func (client *Client) ConfigRewrite(ctx context.Context) (string, error) {
	response, err := client.executeCommand(ctx, C.ConfigRewrite, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(response)
}

// Returns a random existing key name from the currently selected database.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx - The context for controlling the command execution.
//
// Return value:
//
//	A random existing key name from the currently selected database.
//
// [valkey.io]: https://valkey.io/commands/randomkey/
func (client *Client) RandomKey(ctx context.Context) (models.Result[string], error) {
	result, err := client.executeCommand(ctx, C.RandomKey, []string{})
	if err != nil {
		return models.CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
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
//	When in cluster mode, this command will be routed to all nodes.
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
func (client *Client) FunctionKill(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionKill, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Returns information about the function that's currently running and information about the
// available execution engines.
// `FUNCTION STATS` runs on all nodes of the server, including primary and replicas.
// The response includes a mapping from node address to the command response for that node.
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
//	[models.FunctionStatsResult] object containing the following information:
//	running_script - Information about the running script.
//	engines - Information about available engines and their stats.
//
// [valkey.io]: https://valkey.io/commands/function-stats/
func (client *Client) FunctionStats(ctx context.Context) (map[string]models.FunctionStatsResult, error) {
	response, err := client.executeCommand(ctx, C.FunctionStats, []string{})
	if err != nil {
		return nil, err
	}
	return handleFunctionStatsResponse(response)
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
//
// Return value:
//
//	"OK" if the library exists, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/function-delete/
func (client *Client) FunctionDelete(ctx context.Context, libName string) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionDelete, []string{libName})
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
func (client *Client) FunctionList(ctx context.Context, query models.FunctionListQuery) ([]models.LibraryInfo, error) {
	response, err := client.executeCommand(ctx, C.FunctionList, query.ToArgs())
	if err != nil {
		return nil, err
	}
	return handleFunctionListResponse(response)
}

// Returns the serialized payload of all loaded libraries.
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
func (client *Client) FunctionDump(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionDump, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Restores libraries from the serialized payload returned by [Client.FunctionDump].
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
//	payload - The serialized data from [FunctionDump].
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (client *Client) FunctionRestore(ctx context.Context, payload string) (string, error) {
	result, err := client.executeCommand(ctx, C.FunctionRestore, []string{payload})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}

// Restores libraries from the serialized payload returned by [Client.FunctionDump].
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
//	payload - The serialized data from [FunctionDump].
//	policy - A policy for handling existing libraries.
//
// Return value:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (client *Client) FunctionRestoreWithPolicy(
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
//
// Return value:
//
//	The number of clients that received the message.
//
// [valkey.io]: https://valkey.io/commands/publish
func (client *Client) Publish(ctx context.Context, channel string, message string) (int64, error) {
	args := []string{channel, message}
	result, err := client.executeCommand(ctx, C.Publish, args)
	if err != nil {
		return 0, err
	}

	return handleIntResponse(result)
}

// Flushes all the previously watched keys for a transaction. Executing a transaction will
// automatically flush all previously watched keys.
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
func (client *Client) Unwatch(ctx context.Context) (string, error) {
	result, err := client.executeCommand(ctx, C.UnWatch, []string{})
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return handleOkResponse(result)
}
