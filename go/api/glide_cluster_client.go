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
		return CreateEmptyClusterValue(), err
	}
	data, err := handleInterfaceResponse(res)
	if err != nil {
		return CreateEmptyClusterValue(), err
	}
	return CreateClusterValue(data), nil
}

// Returns the server time.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	route - Specifies the routing configuration for the command. The client will route the
//			 command to the nodes defined by route.
//
// Return value:
//
// The current server time as a String array with two elements: A UNIX TIME and the amount
// of microseconds already elapsed in the current second.
// The returned array is in a [UNIX TIME, Microseconds already elapsed] format.
//
// Example:
//
//	route := api.SimpleNodeRoute(api.RandomRoute)
//	result, err := client.Time(route)
//
//	fmt.Println(result.Value()) // Output: [1737285074 67888]
//
// [valkey.io]: https://valkey.io/commands/time/
func (client *glideClusterClient) Time(route route) (ClusterValue[[]string], error) {
	res, err := client.executeCommandWithRoute(C.Time, []string{}, route)
	if err != nil {
		return ClusterValue[[]string]{
			value: Result[[]string]{isNil: true},
		}, err
	}

	if err := checkResponseType(res, C.Map, true); err == nil {

		// Multi-node response
		mapData, err := handleRawStringArrayMapResponse(res)
		if err != nil {
			return ClusterValue[[]string]{
				value: Result[[]string]{isNil: true},
			}, err
		}
		var times []string
		for _, nodeTimes := range mapData {
			times = append(times, nodeTimes...)
		}
		return CreateClusterMultiValue(times), nil
	}

	// Single node response
	data, err := handleRawStringArrayResponse(res)
	if err != nil {
		return ClusterValue[[]string]{
			value: Result[[]string]{isNil: true},
		}, err
	}
	return CreateClusterSingleValue(data), nil
}
