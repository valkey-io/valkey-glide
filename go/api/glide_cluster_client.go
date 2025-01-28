// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"

// GlideClusterClient interface compliance check.
var _ GlideClusterClientCommands = (*GlideClusterClient)(nil)

// GlideClusterClientCommands is a client used for connection in cluster mode.
type GlideClusterClientCommands interface {
	BaseClient
	GenericClusterCommands
	ServerManagementClusterCommands
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
// Example:
//
//	response, err := clusterClient.Info(opts)
//	if err != nil {
//		// handle error
//	}
//	for node, data := range response {
//		fmt.Printf("%s node returned %s\n", node, data)
//	}
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
// Example:
//
//	opts := api.ClusterInfoOptions{
//		InfoOptions: &api.InfoOptions{Sections: []api.Section{api.Server}},
//		Route: api.RandomRoute.ToPtr(),
//	}
//	response, err := clusterClient.InfoWithOptions(opts)
//	if err != nil {
//		// handle error
//	}
//	// Command sent to a single random node via RANDOM route, expecting SingleValue result as a `string`.
//	fmt.Println(response.SingleValue())
//
// [valkey.io]: https://valkey.io/commands/info/
func (client *GlideClusterClient) InfoWithOptions(options ClusterInfoOptions) (ClusterValue[string], error) {
	if options.Route == nil {
		response, err := client.executeCommand(C.Info, options.toArgs())
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		data, err := handleStringToStringMapResponse(response)
		if err != nil {
			return createEmptyClusterValue[string](), err
		}
		return createClusterMultiValue[string](data), nil
	}
	response, err := client.executeCommandWithRoute(C.Info, options.toArgs(), *options.Route)
	if err != nil {
		return createEmptyClusterValue[string](), err
	}
	if (*options.Route).IsMultiNode() {
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
