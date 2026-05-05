// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

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
	CustomCommand(ctx context.Context, args []string) (models.ClusterValue[any], error)

	CustomCommandWithRoute(ctx context.Context, args []string, route config.Route) (models.ClusterValue[any], error)

	Scan(ctx context.Context, cursor models.ClusterScanCursor) (models.ClusterScanResult, error)

	ScanWithOptions(
		ctx context.Context,
		cursor models.ClusterScanCursor,
		opts options.ClusterScanOptions,
	) (models.ClusterScanResult, error)

	RandomKey(ctx context.Context) (models.Result[string], error)

	RandomKeyWithRoute(ctx context.Context, opts options.RouteOption) (models.Result[string], error)
}
