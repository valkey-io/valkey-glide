// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// GenericClusterCommands supports commands for the "Generic Commands" group for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#generic
type GenericClusterCommands interface {
	CustomCommand(args []string) (models.ClusterValue[any], error)

	CustomCommandWithRoute(args []string, route config.Route) (models.ClusterValue[any], error)

	Scan(cursor options.ClusterScanCursor) (options.ClusterScanCursor, []string, error)

	ScanWithOptions(
		cursor options.ClusterScanCursor,
		opts options.ClusterScanOptions,
	) (options.ClusterScanCursor, []string, error)

	RandomKey() (models.Result[string], error)

	RandomKeyWithRoute(opts options.RouteOption) (models.Result[string], error)
}
