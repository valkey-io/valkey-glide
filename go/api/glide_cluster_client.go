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

// Returns the number of keys in the currently selected database.
//
// Return value:
//
//	The number of keys in the currently selected database.
//
// Example:
//
//	result, err := client.DBSize()
//	if err != nil {
//		// handle error
//	}
//	fmt.Println(result) // Output: 1
//
// [valkey.io]: https://valkey.io/commands/dbsize/
func (client *glideClusterClient) DBSizeWithOptions(opts *options.DBSizeOptions) (int64, error) {
	result, err := client.executeCommandWithRoute(C.DBSize, []string{}, opts.Route)
	if err != nil {
		return handleIntOrNilResponse, err
	}
	return handleIntResponse(result)
}
