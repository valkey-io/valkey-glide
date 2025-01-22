// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// #cgo LDFLAGS: -L../target/release -lglide_rs
// #include "../lib.h"
import "C"
import "github.com/valkey-io/valkey-glide/go/glide/api/options"

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
//	options - The TimeOptions type.
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
//	options := options.NewTimeOptionsBuilder().SetRoute(route)
//	result, err := client.TimeWithOptions(route)
//	fmt.Println(result.Value()) // Output: [1737285074 67888]
//
// [valkey.io]: https://valkey.io/commands/time/
func (client *glideClusterClient) TimeWithOptions(opts *options.TimeOptions) (ClusterValue[[]string], error) {
	result, err := client.executeCommandWithRoute(C.Time, []string{}, opts.Route)
	if err != nil {
		return CreateEmptyStringArrayClusterValue(), err
	}
	return handleTimeClusterResponse(result)
}
