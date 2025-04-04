// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/api/options"
	"github.com/valkey-io/valkey-glide/go/utils"
)

// GlideClient interface compliance check.
var _ GlideClientCommands = (*GlideClient)(nil)

// GlideClientCommands is a client used for connection in Standalone mode.
type GlideClientCommands interface {
	BaseClient
	GenericCommands
	ServerManagementCommands
	BitmapCommands
	ConnectionManagementCommands
}

// GlideClient implements standalone mode operations by extending baseClient functionality.
type GlideClient struct {
	*baseClient
}

// NewGlideClient creates a [GlideClientCommands] in standalone mode using the given [GlideClientConfiguration].
func NewGlideClient(config *GlideClientConfiguration) (GlideClientCommands, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClient{client}, nil
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed
// command.
//
// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
//
// This function should only be used for single-response commands. Commands that don't return complete response and awaits
// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
//
// Parameters:
//
//	args - Arguments for the custom command including the command name.
//
// Return value:
//
//	The returned value for the custom command.
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *GlideClient) CustomCommand(args []string) (interface{}, error) {
	res, err := client.executeCommand(C.CustomCommand, args)
	if err != nil {
		return nil, err
	}
	return handleInterfaceResponse(res)
}

// Sets configuration parameters to the specified values.
//
// Note: Prior to Version 7.0.0, only one parameter can be send.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	parameters - A map consisting of configuration parameters and their respective values to set.
//
// Return value:
//
//	`"OK"` if all configurations have been successfully set. Otherwise, raises an error.
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (client *GlideClient) ConfigSet(parameters map[string]string) (string, error) {
	result, err := client.executeCommand(C.ConfigSet, utils.MapToString(parameters))
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Gets the values of configuration parameters.
//
// Note: Prior to Version 7.0.0, only one parameter can be send.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	args - A slice of configuration parameter names to retrieve values for.
//
// Return value:
//
//	A map of api.Result[string] corresponding to the configuration parameters.
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *GlideClient) ConfigGet(args []string) (map[string]string, error) {
	res, err := client.executeCommand(C.ConfigGet, args)
	if err != nil {
		return nil, err
	}
	return handleStringToStringMapResponse(res)
}

// Select changes the currently selected database.
//
// Parameters:
//
//	index - The index of the database to select.
//
// Return value:
//
//	A simple `"OK"` response.
//
// [valkey.io]: https://valkey.io/commands/select/
func (client *GlideClient) Select(index int64) (string, error) {
	result, err := client.executeCommand(C.Select, []string{utils.IntToString(index)})
	if err != nil {
		return DefaultStringResponse, err
	}

	return handleStringResponse(result)
}

// Gets information and statistics about the server.
//
// See [valkey.io] for details.
//
// Return value:
//
//	A string with the information for the default sections.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *GlideClient) Info() (string, error) {
	return client.InfoWithOptions(options.InfoOptions{Sections: []options.Section{}})
}

// Gets information and statistics about the server.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	options - Additional command parameters, see [InfoOptions] for more details.
//
// Return value:
//
//	A string containing the information for the sections requested.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *GlideClient) InfoWithOptions(options options.InfoOptions) (string, error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return DefaultStringResponse, err
	}
	result, err := client.executeCommand(C.Info, optionArgs)
	if err != nil {
		return DefaultStringResponse, err
	}

	return handleStringResponse(result)
}

// Returns the number of keys in the currently selected database.
//
// Return value:
//
//	The number of keys in the currently selected database.
//
// [valkey.io]: https://valkey.io/commands/dbsize/
func (client *GlideClient) DBSize() (int64, error) {
	result, err := client.executeCommand(C.DBSize, []string{})
	if err != nil {
		return defaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Echo the provided message back.
// The command will be routed a random node.
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
func (client *GlideClient) Echo(message string) (Result[string], error) {
	result, err := client.executeCommand(C.Echo, []string{message})
	if err != nil {
		return CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Pings the server.
//
// Return value:
//
//	Returns "PONG".
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *GlideClient) Ping() (string, error) {
	return client.PingWithOptions(options.PingOptions{})
}

// Pings the server.
//
// Parameters:
//
//	pingOptions - The PingOptions type.
//
// Return value:
//
//	Returns the copy of message.
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *GlideClient) PingWithOptions(pingOptions options.PingOptions) (string, error) {
	optionArgs, err := pingOptions.ToArgs()
	if err != nil {
		return DefaultStringResponse, err
	}
	result, err := client.executeCommand(C.Ping, optionArgs)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// FlushAll deletes all the keys of all the existing databases.
//
// See [valkey.io] for details.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (client *GlideClient) FlushAll() (string, error) {
	result, err := client.executeCommand(C.FlushAll, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Deletes all the keys of all the existing databases.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	mode - The flushing mode, could be either [options.SYNC] or [options.ASYNC}.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (client *GlideClient) FlushAllWithOptions(mode options.FlushMode) (string, error) {
	result, err := client.executeCommand(C.FlushAll, []string{string(mode)})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Deletes all the keys of the currently selected database.
//
// See [valkey.io] for details.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *GlideClient) FlushDB() (string, error) {
	result, err := client.executeCommand(C.FlushDB, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Deletes all the keys of the currently selected database.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	mode - The flushing mode, could be either [options.SYNC] or [options.ASYNC}.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *GlideClient) FlushDBWithOptions(mode options.FlushMode) (string, error) {
	result, err := client.executeCommand(C.FlushDB, []string{string(mode)})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// Return value:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (client *GlideClient) Lolwut() (string, error) {
	result, err := client.executeCommand(C.Lolwut, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// Parameters:
//
//	opts - The [LolwutOptions] type.
//
// Return value:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (client *baseClient) LolwutWithOptions(opts options.LolwutOptions) (string, error) {
	commandArgs, err := opts.ToArgs()
	if err != nil {
		return DefaultStringResponse, err
	}
	result, err := client.executeCommand(C.Lolwut, commandArgs)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Gets the current connection id.
//
// Return value:
//
//	The id of the client.
//
// [valkey.io]: https://valkey.io/commands/client-id/
func (client *GlideClient) ClientId() (int64, error) {
	result, err := client.executeCommand(C.ClientId, []string{})
	if err != nil {
		return defaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Returns UNIX TIME of the last DB save timestamp or startup timestamp if no save was made since then.
//
// Return value:
//
//	UNIX TIME of the last DB save executed with success.
//
// [valkey.io]: https://valkey.io/commands/lastsave/
func (client *GlideClient) LastSave() (int64, error) {
	response, err := client.executeCommand(C.LastSave, []string{})
	if err != nil {
		return defaultIntResponse, err
	}
	return handleIntResponse(response)
}

// Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM.
//
// Return value:
//
//	OK to confirm that the statistics were successfully reset.
//
// [valkey.io]: https://valkey.io/commands/config-resetstat/
func (client *GlideClient) ConfigResetStat() (string, error) {
	response, err := client.executeCommand(C.ConfigResetStat, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(response)
}

// Gets the name of the current connection.
//
// Return value:
//
//	The name of the client connection as a string if a name is set, or nil if  no name is assigned.
//
// [valkey.io]: https://valkey.io/commands/client-getname/
func (client *GlideClient) ClientGetName() (string, error) {
	result, err := client.executeCommand(C.ClientGetName, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Set the name of the current connection.
//
// Parameters:
//
//	connectionName - Connection name of the current connection.
//
// Return value:
//
//	OK - when connection name is set
//
// [valkey.io]: https://valkey.io/commands/client-setname/
func (client *GlideClient) ClientSetName(connectionName string) (string, error) {
	result, err := client.executeCommand(C.ClientSetName, []string{connectionName})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Move key from the currently selected database to the database specified by dbIndex.
//
// Parameters:
//
//	key - The key to move.
//	dbIndex -  The index of the database to move key to.
//
// Return value:
//
//	Returns "OK".
//
// [valkey.io]: https://valkey.io/commands/move/
func (client *GlideClient) Move(key string, dbIndex int64) (bool, error) {
	result, err := client.executeCommand(C.Move, []string{key, utils.IntToString(dbIndex)})
	if err != nil {
		return defaultBoolResponse, err
	}

	return handleBoolResponse(result)
}

// Iterates incrementally over a database for matching keys.
//
// Parameters:
//
//	cursor - The cursor that points to the next iteration of results. A value of 0
//			 indicates the start of the search.
//
// Return value:
//
//	An Array of Objects. The first element is always the cursor for the next
//	iteration of results. "0" will be the cursor returned on the last iteration
//	of the scan. The second element is always an Array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *GlideClient) Scan(cursor int64) (string, []string, error) {
	res, err := client.executeCommand(C.Scan, []string{utils.IntToString(cursor)})
	if err != nil {
		return DefaultStringResponse, nil, err
	}
	return handleScanResponse(res)
}

// Iterates incrementally over a database for matching keys.
//
// Parameters:
//
//	 cursor - The cursor that points to the next iteration of results. A value of 0
//				 indicates the start of the search.
//	 scanOptions - Additional command parameters, see [ScanOptions] for more details.
//
// Return value:
//
//	An Array of Objects. The first element is always the cursor for the next
//	iteration of results. "0" will be the cursor returned on the last iteration
//	of the scan. The second element is always an Array of matched keys from the database.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *GlideClient) ScanWithOptions(cursor int64, scanOptions options.ScanOptions) (string, []string, error) {
	optionArgs, err := scanOptions.ToArgs()
	if err != nil {
		return DefaultStringResponse, nil, err
	}
	res, err := client.executeCommand(C.Scan, append([]string{utils.IntToString(cursor)}, optionArgs...))
	if err != nil {
		return DefaultStringResponse, nil, err
	}
	return handleScanResponse(res)
}

// Rewrites the configuration file with the current configuration.
//
// Return value:
//
//	"OK" when the configuration was rewritten properly, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/config-rewrite/
func (client *GlideClient) ConfigRewrite() (string, error) {
	response, err := client.executeCommand(C.ConfigRewrite, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(response)
}

// Returns a random existing key name from the currently selected database.
//
// Return value:
//
//	A random existing key name from the currently selected database.
//
// [valkey.io]: https://valkey.io/commands/randomkey/
func (client *GlideClient) RandomKey() (Result[string], error) {
	result, err := client.executeCommand(C.RandomKey, []string{})
	if err != nil {
		return CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}
