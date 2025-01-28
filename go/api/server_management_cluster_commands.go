// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/valkey-io/valkey-glide/go/glide/api/options"

// ServerManagementClusterCommands supports commands for the "Server Management Commands" group for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementClusterCommands interface {
	DBSizeWithOptions(routeOption *options.RouteOption) (int64, error)
}
