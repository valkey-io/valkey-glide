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
	ServerManagementClusterCommands
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
func (client *glideClusterClient) Info() (map[string]string, error) {
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
func (client *glideClusterClient) InfoWithOptions(options ClusterInfoOptions) (ClusterValue[string], error) {
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
	if (*options.Route).isMultiNode() {
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
