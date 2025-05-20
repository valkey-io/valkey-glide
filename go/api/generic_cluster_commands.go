// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// GenericClusterCommands supports commands for the "Generic Commands" group for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#generic
type GenericClusterCommands interface {
	CustomCommand(ctx context.Context, args []string) (ClusterValue[interface{}], error)

	CustomCommandWithRoute(ctx context.Context, args []string, route config.Route) (ClusterValue[interface{}], error)

	Scan(ctx context.Context, cursor options.ClusterScanCursor) (options.ClusterScanCursor, []string, error)

	ScanWithOptions(
		ctx context.Context,
		cursor options.ClusterScanCursor,
		opts options.ClusterScanOptions,
	) (options.ClusterScanCursor, []string, error)

	RandomKey(ctx context.Context) (Result[string], error)

	RandomKeyWithRoute(ctx context.Context, opts options.RouteOption) (Result[string], error)
}
