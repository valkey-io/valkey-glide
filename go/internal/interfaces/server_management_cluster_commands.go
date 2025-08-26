// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// ServerManagementCommands supports commands for the "Server Management" group for a cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementClusterCommands interface {
	Select(ctx context.Context, index int64) (string, error)

	SelectWithOptions(ctx context.Context, index int64, routeOption options.RouteOption) (string, error)

	Info(ctx context.Context) (map[string]string, error)

	InfoWithOptions(ctx context.Context, options options.ClusterInfoOptions) (models.ClusterValue[string], error)

	TimeWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[[]string], error)

	DBSizeWithOptions(ctx context.Context, routeOption options.RouteOption) (int64, error)

	FlushAll(ctx context.Context) (string, error)

	FlushAllWithOptions(ctx context.Context, options options.FlushClusterOptions) (string, error)

	FlushDB(ctx context.Context) (string, error)

	FlushDBWithOptions(ctx context.Context, options options.FlushClusterOptions) (string, error)

	Lolwut(ctx context.Context) (string, error)

	LolwutWithOptions(ctx context.Context, lolwutOptions options.ClusterLolwutOptions) (models.ClusterValue[string], error)

	LastSave(ctx context.Context) (models.ClusterValue[int64], error)

	LastSaveWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[int64], error)

	ConfigResetStat(ctx context.Context) (string, error)

	ConfigResetStatWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)

	ConfigSet(ctx context.Context, parameters map[string]string) (string, error)

	ConfigSetWithOptions(ctx context.Context, parameters map[string]string, routeOption options.RouteOption) (string, error)

	ConfigGet(ctx context.Context, parameters []string) (map[string]string, error)

	ConfigGetWithOptions(
		ctx context.Context,
		parameters []string,
		routeOption options.RouteOption,
	) (models.ClusterValue[map[string]string], error)

	ConfigRewrite(ctx context.Context) (string, error)

	ConfigRewriteWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)
}
