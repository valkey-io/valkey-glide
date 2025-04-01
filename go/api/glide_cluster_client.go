// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #include "../lib.h"
import "C"

import (
	"unsafe"

	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/errors"
	"github.com/valkey-io/valkey-glide/go/api/options"
	"github.com/valkey-io/valkey-glide/go/utils"
)

// GlideClusterClient interface compliance check.
var _ GlideClusterClientCommands = (*GlideClusterClient)(nil)

// GlideClusterClientCommands is a client used for connection in cluster mode.
type GlideClusterClientCommands interface {
	BaseClient
	GenericClusterCommands
	ServerManagementClusterCommands
	ConnectionManagementClusterCommands
}

// GlideClusterClient implements cluster mode operations by extending baseClient functionality.
type GlideClusterClient struct {
	*baseClient
}

// NewGlideClusterClient creates a [GlideClusterClientCommands] in cluster mode using the given
// [GlideClusterClientConfiguration].
func NewGlideClusterClient(config *GlideClusterClientConfiguration) (GlideClusterClientCommands, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &GlideClusterClient{client}, nil
}

// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed
// command.
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
//	args - Arguments for the custom command including the command name.
//
// Return value:
//
//	The returned value for the custom command.
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *GlideClusterClient) CustomCommand(args []string) (ClusterValue[interface{}], error) {
	res, err := client.executeCommand(C.CustomCommand, args)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	return createClusterValue[interface{}](data), nil
}

// Gets information and statistics about the server.
//
// The command will be routed to all primary nodes.
//
// See [valkey.io] for details.
//
// Return value:
//
//	A map where each address is the key and its corresponding node response is the information for the default sections.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *GlideClusterClient) Info() (map[string]string, error) {
	result, err := client.executeCommand(C.Info, []string{})
	if err != nil {
		return nil, err
	}

	return handleStringToStringMapResponse(result)
}

// Gets information and statistics about the server.
//
// The command will be routed to all primary nodes, unless `route` in [ClusterInfoOptions] is provided.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	options - Additional command parameters, see [ClusterInfoOptions] for more details.
//
// Return value:
//
//	When specifying a route other than a single node or when route is not given,
//	it returns a map where each address is the key and its corresponding node response is the value.
//	When a single node route is given, command returns a string containing the information for the sections requested.
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *GlideClusterClient) InfoWithOptions(options options.ClusterInfoOptions) (ClusterValue[string], error) {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	if options.RouteOption == nil || options.RouteOption.Route == nil {
		response, err := client.executeCommand(C.Info, optionArgs)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterMultiValue[string](data), nil
	}
	response, err := client.executeCommandWithRoute(C.Info, optionArgs, options.Route)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	if options.Route.IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterMultiValue[string](data), nil
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	return createClusterSingleValue[string](data), nil
}

// CustomCommandWithRoute executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed command.
//
// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
//
// Parameters:
//
//	args  - Arguments for the custom command including the command name.
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	The returning value depends on the executed command and route.
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *GlideClusterClient) CustomCommandWithRoute(
	args []string,
	route config.Route,
) (ClusterValue[interface{}], error) {
	res, err := client.executeCommandWithRoute(C.CustomCommand, args, route)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	return createClusterValue[interface{}](data), nil
}

// Pings the server.
// The command will be routed to all primary nodes.
//
// Return value:
//
//	Returns "PONG".
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *GlideClusterClient) Ping() (string, error) {
	result, err := client.executeCommand(C.Ping, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Pings the server.
// The command will be routed to all primary nodes, unless `Route` is provided in `pingOptions`.
//
// Parameters:
//
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
//	result, err := clusterClient.PingWithOptions(opts)
//	fmt.Println(result) // Output: Hello
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *GlideClusterClient) PingWithOptions(pingOptions options.ClusterPingOptions) (string, error) {
	args, err := pingOptions.ToArgs()
	if err != nil {
		return DefaultStringResponse, err
	}
	if pingOptions.RouteOption == nil || pingOptions.RouteOption.Route == nil {
		response, err := client.executeCommand(C.Ping, args)
		if err != nil {
			return DefaultStringResponse, err
		}
		return handleStringResponse(response)
	}

	response, err := client.executeCommandWithRoute(C.Ping, args, pingOptions.Route)
	if err != nil {
		return DefaultStringResponse, err
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
//	options - The [RouteOption] type.
//
// Return value:
//
// The current server time as a String array with two elements: A UNIX TIME and the amount
// of microseconds already elapsed in the current second.
// The returned array is in a [UNIX TIME, Microseconds already elapsed] format.
// [valkey.io]: https://valkey.io/commands/time/
func (client *GlideClusterClient) TimeWithOptions(opts options.RouteOption) (ClusterValue[[]string], error) {
	result, err := client.executeCommandWithRoute(C.Time, []string{}, opts.Route)
	if err != nil {
		return createEmptyClusterValue[[]string](), err
	}
	return handleTimeClusterResponse(result)
}

// Returns the number of keys in the database.
//
// Parameters:
//
//	options - The [RouteOption] type.
//
// Return value:
//
//	The number of keys in the database.
//
// [valkey.io]: https://valkey.io/commands/dbsize/
func (client *GlideClusterClient) DBSizeWithOptions(opts options.RouteOption) (int64, error) {
	result, err := client.executeCommandWithRoute(C.DBSize, []string{}, opts.Route)
	if err != nil {
		return defaultIntResponse, err
	}
	return handleIntResponse(result)
}

// Deletes all the keys of all the existing databases.
// The command will be routed to all primary nodes.
//
// See [valkey.io] for details.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (client *GlideClusterClient) FlushAll() (string, error) {
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
//	flushOptions - The [FlushClusterOptions] type.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (client *GlideClusterClient) FlushAllWithOptions(flushOptions options.FlushClusterOptions) (string, error) {
	if flushOptions.RouteOption == nil || flushOptions.RouteOption.Route == nil {
		result, err := client.executeCommand(C.FlushAll, flushOptions.ToArgs())
		if err != nil {
			return DefaultStringResponse, err
		}
		return handleStringResponse(result)
	}
	result, err := client.executeCommandWithRoute(C.FlushAll, flushOptions.ToArgs(), flushOptions.RouteOption.Route)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Deletes all the keys of the currently selected database.
// The command will be routed to all primary nodes.
//
// See [valkey.io] for details.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *GlideClusterClient) FlushDB() (string, error) {
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
//	flushOptions - The [FlushClusterOptions] type.
//
// Return value:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (client *GlideClusterClient) FlushDBWithOptions(flushOptions options.FlushClusterOptions) (string, error) {
	if flushOptions.RouteOption == nil || flushOptions.RouteOption.Route == nil {
		result, err := client.executeCommand(C.FlushDB, flushOptions.ToArgs())
		if err != nil {
			return DefaultStringResponse, err
		}
		return handleStringResponse(result)
	}
	result, err := client.executeCommandWithRoute(C.FlushDB, flushOptions.ToArgs(), flushOptions.RouteOption.Route)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Echo the provided message back.
// The command will be routed a random node, unless `Route` in `echoOptions` is provided.
//
// Parameters:
//
//	echoOptions - The [ClusterEchoOptions] type.
//
// Return value:
//
//	A map where each address is the key and its corresponding node response is the information for the default sections.
//
// [valkey.io]: https://valkey.io/commands/echo/
func (client *GlideClusterClient) EchoWithOptions(echoOptions options.ClusterEchoOptions) (ClusterValue[string], error) {
	args, err := echoOptions.ToArgs()
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	var route config.Route
	if echoOptions.RouteOption != nil && echoOptions.RouteOption.Route != nil {
		route = echoOptions.RouteOption.Route
	}
	response, err := client.executeCommandWithRoute(C.Echo, args, route)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	if echoOptions.RouteOption != nil && echoOptions.RouteOption.Route != nil &&
		(echoOptions.RouteOption.Route).IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterMultiValue[string](data), nil
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	return createClusterSingleValue[string](data), nil
}

// Helper function to perform the cluster scan.
func (client *GlideClusterClient) clusterScan(
	cursor *options.ClusterScanCursor,
	opts options.ClusterScanOptions,
) (*C.struct_CommandResponse, error) {
	// make the channel buffered, so that we don't need to acquire the client.mu in the successCallback and failureCallback.
	resultChannel := make(chan payload, 1)
	resultChannelPtr := unsafe.Pointer(&resultChannel)

	pinner := pinner{}
	pinnedChannelPtr := uintptr(pinner.Pin(resultChannelPtr))
	defer pinner.Unpin()

	client.mu.Lock()
	if client.coreClient == nil {
		client.mu.Unlock()
		return nil, &errors.ClosingError{Msg: "Cluster Scan failed. The client is closed."}
	}
	client.pending[resultChannelPtr] = struct{}{}

	cStr := C.CString(cursor.GetCursor())
	c_cursor := C.new_cluster_cursor(cStr)
	defer C.free(unsafe.Pointer(cStr))

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

	payload := <-resultChannel

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
//	cursor - The [ClusterScanCursor] object that wraps the scan state.
//	   To start a new scan, create a new empty ClusterScanCursor using NewClusterScanCursor().
//
// Returns:
//
//	The ID of the next cursor and a list of keys found for this cursor ID.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *GlideClusterClient) Scan(
	cursor options.ClusterScanCursor,
) (options.ClusterScanCursor, []string, error) {
	response, err := client.clusterScan(&cursor, *options.NewClusterScanOptions())
	if err != nil {
		return *options.NewClusterScanCursorWithId("finished"), []string{}, err
	}

	nextCursor, keys, err := handleScanResponse(response)
	return *options.NewClusterScanCursorWithId(nextCursor), keys, err
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
//	cursor - The [ClusterScanCursor] object that wraps the scan state.
//	   To start a new scan, create a new empty ClusterScanCursor using NewClusterScanCursor().
//	opts - The scan options. Can specify MATCH, COUNT, and TYPE configurations.
//
// Returns:
//
//	The ID of the next cursor and a list of keys found for this cursor ID.
//
// [valkey.io]: https://valkey.io/commands/scan/
func (client *GlideClusterClient) ScanWithOptions(
	cursor options.ClusterScanCursor,
	opts options.ClusterScanOptions,
) (options.ClusterScanCursor, []string, error) {
	response, err := client.clusterScan(&cursor, opts)
	if err != nil {
		return *options.NewClusterScanCursorWithId("finished"), []string{}, err
	}

	nextCursor, keys, err := handleScanResponse(response)
	return *options.NewClusterScanCursorWithId(nextCursor), keys, err
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// Return value:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (client *GlideClusterClient) Lolwut() (string, error) {
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
//	lolwutOptions - The [LolwutOptions] type.
//
// Return value:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (client *GlideClusterClient) LolwutWithOptions(lolwutOptions options.ClusterLolwutOptions) (ClusterValue[string], error) {
	args, err := lolwutOptions.ToArgs()
	if err != nil {
		return createEmptyClusterValue[string](), err
	}

	if lolwutOptions.RouteOption == nil || lolwutOptions.RouteOption.Route == nil {
		response, err := client.executeCommand(C.Lolwut, args)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		data, err := handleStringResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterSingleValue[string](data), nil
	}

	route := lolwutOptions.RouteOption.Route
	response, err := client.executeCommandWithRoute(C.Lolwut, args, route)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}

	if route.IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterMultiValue[string](data), nil
	}

	data, err := handleStringResponse(response)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	return createClusterSingleValue[string](data), nil
}

// Gets the current connection id.
//
// Return value:
//
//	The id of the client.
//
// [valkey.io]: https://valkey.io/commands/client-id/
func (client *GlideClusterClient) ClientId() (ClusterValue[int64], error) {
	response, err := client.executeCommand(C.ClientId, []string{})
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	return createClusterSingleValue[int64](data), nil
}

// Gets the current connection id.
//
// Parameters:
//
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	The id of the client.
//
// [valkey.io]: https://valkey.io/commands/client-id/
func (client *GlideClusterClient) ClientIdWithOptions(opts options.RouteOption) (ClusterValue[int64], error) {
	response, err := client.executeCommandWithRoute(C.ClientId, []string{}, opts.Route)
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	if opts.Route != nil &&
		(opts.Route).IsMultiNode() {
		data, err := handleStringIntMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[int64](), err
		}
		return createClusterMultiValue[int64](data), nil
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	return createClusterSingleValue[int64](data), nil
}

// Returns UNIX TIME of the last DB save timestamp or startup timestamp if no save was made since then.
// The command is routed to a random node by default, which is safe for read-only commands.
//
// Return value:
//
//	UNIX TIME of the last DB save executed with success.
//
// [valkey.io]: https://valkey.io/commands/lastsave/
func (client *GlideClusterClient) LastSave() (ClusterValue[int64], error) {
	response, err := client.executeCommand(C.LastSave, []string{})
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	return createClusterSingleValue[int64](data), nil
}

// Returns UNIX TIME of the last DB save timestamp or startup timestamp if no save was made since then.
//
// Parameters:
//
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	UNIX TIME of the last DB save executed with success.
//
// [valkey.io]: https://valkey.io/commands/lastsave/
func (client *GlideClusterClient) LastSaveWithOptions(opts options.RouteOption) (ClusterValue[int64], error) {
	response, err := client.executeCommandWithRoute(C.LastSave, []string{}, opts.Route)
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	if opts.Route != nil &&
		(opts.Route).IsMultiNode() {
		data, err := handleStringIntMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[int64](), err
		}
		return createClusterMultiValue[int64](data), nil
	}
	data, err := handleIntResponse(response)
	if err != nil {
		return createEmptyClusterValue[int64](), err
	}
	return createClusterSingleValue[int64](data), nil
}

// Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM
//
// Return value:
//
//	OK to confirm that the statistics were successfully reset.
//
// [valkey.io]: https://valkey.io/commands/config-resetstat/
func (client *GlideClusterClient) ConfigResetStat() (string, error) {
	response, err := client.executeCommand(C.ConfigResetStat, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(response)
}

// Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM.
//
// Parameters:
//
//	route - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	OK to confirm that the statistics were successfully reset.
//
// [valkey.io]: https://valkey.io/commands/config-resetstat/
func (client *GlideClusterClient) ConfigResetStatWithOptions(opts options.RouteOption) (string, error) {
	response, err := client.executeCommandWithRoute(C.ConfigResetStat, []string{}, opts.Route)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(response)
}

// Sets configuration parameters to the specified values.
// Starting from server version 7, command supports multiple parameters.
// The command will be sent to all nodes.
//
// Parameters:
//
//	parameters -  A map consisting of configuration parameters and their respective values to set.
//
// Return value:
//
//	OK if all configurations have been successfully set. Otherwise, raises an error.
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (client *GlideClusterClient) ConfigSet(
	parameters map[string]string,
) (string, error) {
	result, err := client.executeCommand(C.ConfigSet, utils.MapToString(parameters))
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Sets configuration parameters to the specified values
// Starting from server version 7, command supports multiple parameters.
//
// Parameters:
//
//	parameters -  A map consisting of configuration parameters and their respective values to set.
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	OK if all configurations have been successfully set. Otherwise, raises an error.
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (client *GlideClusterClient) ConfigSetWithOptions(
	parameters map[string]string, opts options.RouteOption,
) (string, error) {
	result, err := client.executeCommandWithRoute(C.ConfigSet, utils.MapToString(parameters), opts.Route)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(result)
}

// Get the values of configuration parameters.
// Starting from server version 7, command supports multiple parameters.
// The command will be sent to a random node.
//
// Parameters:
//
//	parameters -  An array of configuration parameter names to retrieve values for.
//
// Return value:
//
//	A map of values corresponding to the configuration parameters.
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *GlideClusterClient) ConfigGet(
	parameters []string,
) (ClusterValue[interface{}], error) {
	res, err := client.executeCommand(C.ConfigGet, parameters)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	return createClusterValue[interface{}](data), nil
}

// Get the values of configuration parameters.
// Starting from server version 7, command supports multiple parameters.
//
// Parameters:
//
//	parameters - An array of configuration parameter names to retrieve values for.
//	opts - Specifies the routing configuration for the command. The client will route the
//	       command to the nodes defined by route.
//
// Return value:
//
//	A map of values corresponding to the configuration parameters.
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (client *GlideClusterClient) ConfigGetWithOptions(
	parameters []string, opts options.RouteOption,
) (ClusterValue[interface{}], error) {
	res, err := client.executeCommandWithRoute(C.ConfigGet, parameters, opts.Route)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return createEmptyClusterValue[interface{}](), err
	}
	return createClusterValue[interface{}](data), nil
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
func (client *GlideClusterClient) ClientSetName(connectionName string) (ClusterValue[string], error) {
	response, err := client.executeCommand(C.ClientSetName, []string{connectionName})
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	return createClusterSingleValue[string](data), nil
}

// Set the name of the current connection.
//
// Parameters:
//
//	connectionName - Connection name of the current connection.
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	OK - when connection name is set
//
// [valkey.io]: https://valkey.io/commands/client-setname/
func (client *GlideClusterClient) ClientSetNameWithOptions(connectionName string, opts options.RouteOption) (ClusterValue[string], error) {
	response, err := client.executeCommandWithRoute(C.ClientSetName, []string{connectionName}, opts.Route)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	if opts.Route != nil &&
		(opts.Route).IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterMultiValue[string](data), nil
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	return createClusterSingleValue[string](data), nil
}

// Gets the name of the current connection.
//
// Return value:
//
//	The name of the client connection as a string if a name is set, or nil if  no name is assigned.
//
// [valkey.io]: https://valkey.io/commands/client-getname/
func (client *GlideClusterClient) ClientGetName() (ClusterValue[string], error) {
	response, err := client.executeCommand(C.ClientGetName, []string{})
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	return createClusterSingleValue[string](data), nil
}

// Gets the name of the current connection.
//
// Parameters:
//
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	The name of the client connection as a string if a name is set, or nil if  no name is assigned.
//
// [valkey.io]: https://valkey.io/commands/client-getname/
func (client *GlideClusterClient) ClientGetNameWithOptions(opts options.RouteOption) (ClusterValue[string], error) {
	response, err := client.executeCommandWithRoute(C.ClientGetName, []string{}, opts.Route)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	if opts.Route != nil &&
		(opts.Route).IsMultiNode() {
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterMultiValue[string](data), nil
	}
	data, err := handleStringResponse(response)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	return createClusterSingleValue[string](data), nil
}

// Rewrites the configuration file with the current configuration.
// The command will be routed a random node.
//
// Return value:
//
//	"OK" when the configuration was rewritten properly, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/config-rewrite/
func (client *GlideClusterClient) ConfigRewrite() (string, error) {
	response, err := client.executeCommand(C.ConfigRewrite, []string{})
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(response)
}

// Rewrites the configuration file with the current configuration.
//
// Parameters:
//
//	opts - Specifies the routing configuration for the command. The client will route the
//	        command to the nodes defined by route.
//
// Return value:
//
//	"OK" when the configuration was rewritten properly, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/config-rewrite/
func (client *GlideClusterClient) ConfigRewriteWithOptions(opts options.RouteOption) (string, error) {
	response, err := client.executeCommandWithRoute(C.ConfigRewrite, []string{}, opts.Route)
	if err != nil {
		return DefaultStringResponse, err
	}
	return handleStringResponse(response)
}

// Returns a random key.
//
// Return value:
//
//	A random key from the database.
//
// [valkey.io]: https://valkey.io/commands/randomkey/
func (client *GlideClusterClient) RandomKey() (Result[string], error) {
	result, err := client.executeCommand(C.RandomKey, []string{})
	if err != nil {
		return CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}

// Returns a random key.
//
// Parameters:
//
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
func (client *GlideClusterClient) RandomKeyWithRoute(opts options.RouteOption) (Result[string], error) {
	result, err := client.executeCommandWithRoute(C.RandomKey, []string{}, opts.Route)
	if err != nil {
		return CreateNilStringResult(), err
	}
	return handleStringOrNilResponse(result)
}
