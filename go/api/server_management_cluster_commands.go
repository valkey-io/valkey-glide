// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

// ServerManagementCommands supports commands for the "Server Management" group for a cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementClusterCommands interface {
	Info(ctx context.Context) (map[string]string, error)

	InfoWithOptions(ctx context.Context, options options.ClusterInfoOptions) (ClusterValue[string], error)

	TimeWithOptions(ctx context.Context, routeOption options.RouteOption) (ClusterValue[[]string], error)

	DBSizeWithOptions(ctx context.Context, routeOption options.RouteOption) (int64, error)

	FlushAll(ctx context.Context) (string, error)

	FlushAllWithOptions(ctx context.Context, options options.FlushClusterOptions) (string, error)

	FlushDB(ctx context.Context) (string, error)

	FlushDBWithOptions(ctx context.Context, options options.FlushClusterOptions) (string, error)

	Lolwut(ctx context.Context) (string, error)

	LolwutWithOptions(ctx context.Context, lolwutOptions options.ClusterLolwutOptions) (ClusterValue[string], error)

	LastSave(ctx context.Context) (ClusterValue[int64], error)

	LastSaveWithOptions(ctx context.Context, routeOption options.RouteOption) (ClusterValue[int64], error)

	ConfigResetStat(ctx context.Context) (string, error)

	ConfigResetStatWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)

	ConfigSet(ctx context.Context, parameters map[string]string) (string, error)

	ConfigSetWithOptions(ctx context.Context, parameters map[string]string, routeOption options.RouteOption) (string, error)

	ConfigGet(ctx context.Context, parameters []string) (map[string]string, error)

	ConfigGetWithOptions(
		ctx context.Context,
		parameters []string,
		routeOption options.RouteOption,
	) (ClusterValue[map[string]string], error)

	ConfigRewrite(ctx context.Context) (string, error)

	ConfigRewriteWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)
}
