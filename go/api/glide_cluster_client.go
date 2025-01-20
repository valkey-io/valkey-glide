// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

// GlideClusterClient interface compliance check.
var _ GlideClusterClient = (*glideClusterClient)(nil)

// GlideClusterClient is a client used for connection in cluster mode.
type GlideClusterClient interface {
	BaseClient
	GenericClusterCommands
	ConnectionManagementClusterCommands
}

// glideClusterClient implements cluster mode operations by extending baseClient functionality.
type glideClusterClient struct {
	*baseClient
}

// NewGlideClusterClient creates a [GlideClusterClient] in cluster mode using the given [GlideClusterClientConfiguration].
func NewGlideClusterClient(config *GlideClusterClientConfiguration) (GlideClusterClient, error) {
	client, err := createClient(config)
	if err != nil {
		return nil, err
	}

	return &glideClusterClient{client}, nil
}

func (client *glideClusterClient) CustomCommand(args []string) (ClusterValue[interface{}], error) {
	res, err := client.executeCommand(C.CustomCommand, args)
	if err != nil {
		return CreateEmptyClusterValue(), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return CreateEmptyClusterValue(), err
	}
	return CreateClusterValue(data), nil
}

// CustomCommandWithRoute executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed
// command.
//
// The command will be routed automatically based on the passed command's default request policy.
//
// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
//
// Parameters:
//
//	args  - Arguments for the custom command including the command name.
//	route - Specifies the routing configuration for the command. The client will route the
//	   command to the nodes defined by route.
//
// Return value:
//
//	The returning value depends on the executed command and route.
//
// For example:
//
//	route := api.SimpleNodeRoute(api.RandomRoute)
//	result, err := client.CustomCommand([]string{"ping"}, route)
//	result.Value().(string): "PONG"
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *glideClusterClient) CustomCommandWithRoute(args []string, route route) (ClusterValue[interface{}], error) {
	res, err := client.executeCommandWithRoute(C.CustomCommand, args, route)
	if err != nil {
		return CreateEmptyClusterValue(), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return CreateEmptyClusterValue(), err
	}
	return CreateClusterValue(data), nil
}

// Pings the server.
//
// Parameters:
//
//	route - Specifies the routing configuration for the command.
//	 The client will route the command to the nodes defined by route.
//
// Return value:
//
//	Returns "PONG".
//
// For example:
//
//	 route := api.SimpleNodeRoute(api.RandomRoute)
//	 result, err := client.Ping(route)
//		fmt.Println(result) // Output: "PONG"
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *glideClusterClient) PingWithRoute(route route) (string, error) {
	res, err := client.executeCommandWithRoute(C.Ping, []string{}, route)
	if err != nil {
		return defaultStringResponse, err
	}
	return handleStringResponse(res)
}

// Pings the server with a custom message.
//
// Parameters:
//
//	message - A message to include in the `PING` command.
//	route - Specifies the routing configuration for the command.
//	  The client will route the command to the nodes defined by route.
//
// Return value:
//
//	Returns the copy of message.
//
// For example:
//
//	 route := api.SimpleNodeRoute(api.RandomRoute)
//	 result, err := client.PingWithMessage("Hello", route)
//		fmt.Println(result) // Output: "Hello"
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *glideClusterClient) PingWithMessageRoute(message string, route route) (string, error) {
	res, err := client.executeCommandWithRoute(C.Ping, []string{message}, route)
	if err != nil {
		return defaultStringResponse, err
	}
	return handleStringResponse(res)
}
