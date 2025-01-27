// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

import (
	"github.com/valkey-io/valkey-glide/go/glide/api/config"
	"github.com/valkey-io/valkey-glide/go/glide/api/options"
)

// GlideClusterClient interface compliance check.
var _ GlideClusterClientCommands = (*GlideClusterClient)(nil)

// GlideClusterClientCommands is a client used for connection in cluster mode.
type GlideClusterClientCommands interface {
	BaseClient
	GenericClusterCommands
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
// For example:
//
//	result, err := client.CustomCommand([]string{"ping"})
//	result.Value().(string): "PONG"
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *GlideClusterClient) CustomCommand(args []string) (ClusterValue[interface{}], error) {
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
// For example:
//
//	route := config.SimpleNodeRoute(config.RandomRoute)
//	result, err := client.CustomCommand([]string{"ping"}, route)
//	result.Value().(string): "PONG"
//
// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
func (client *GlideClusterClient) CustomCommandWithRoute(
	args []string,
	route config.Route,
) (ClusterValue[interface{}], error) {
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
// Return value:
//
//	Returns "PONG".
//
// For example:
//
//	result, err := clusterClient.Ping()
//	fmt.Println(result) // Output: PONG
//
// [valkey.io]: https://valkey.io/commands/ping/
func (client *GlideClusterClient) Ping() (string, error) {
	result, err := client.executeCommand(C.Ping, []string{})
	if err != nil {
		return defaultStringResponse, err
	}
	return handleStringResponse(result)
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
// For example:
//
//	route := config.Route(config.RandomRoute)
//  opts := options.ClusterPingOptions{
//		PingOptions: &options.PingOptions{
//			Message: "Hello",
//		},
//		Route: &route,
//	}
//	result, err := clusterClient.PingWithOptions(opts)
//	fmt.Println(result.Value()) // Output: Hello
//
// [valkey.io]: https://valkey.io/commands/ping/

func (client *GlideClusterClient) PingWithOptions(pingOptions options.ClusterPingOptions) (ClusterValue[string], error) {
	if pingOptions.Route == nil {
		response, err := client.executeCommand(C.Ping, pingOptions.ToArgs())
		if err != nil {
			return CreateEmptyStringClusterValue(), err
		}
		data, err := handleStringResponse(response)
		if err != nil {
			return CreateEmptyStringClusterValue(), err
		}
		return CreateClusterValue(data), nil
	}

	response, err := client.executeCommandWithRoute(C.Ping, pingOptions.ToArgs(), *pingOptions.Route)
	if err != nil {
		return CreateEmptyStringClusterValue(), err
	}

	data, err := handleStringResponse(response)
	if err != nil {
		return CreateEmptyStringClusterValue(), err
	}
	return CreateClusterValue(data), nil
}
