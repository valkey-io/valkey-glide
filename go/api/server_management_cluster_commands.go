// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/api/models"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// ServerManagementCommands supports commands for the "Server Management" group for a cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementClusterCommands interface {
	Info() (map[string]string, error)

	InfoWithOptions(options options.ClusterInfoOptions) (models.ClusterValue[string], error)

	TimeWithOptions(routeOption options.RouteOption) (models.ClusterValue[[]string], error)

	DBSizeWithOptions(routeOption options.RouteOption) (int64, error)

	FlushAll() (string, error)

	FlushAllWithOptions(options options.FlushClusterOptions) (string, error)

	FlushDB() (string, error)

	FlushDBWithOptions(options options.FlushClusterOptions) (string, error)

	Lolwut() (string, error)

	LolwutWithOptions(lolwutOptions options.ClusterLolwutOptions) (models.ClusterValue[string], error)

	LastSave() (models.ClusterValue[int64], error)

	LastSaveWithOptions(routeOption options.RouteOption) (models.ClusterValue[int64], error)

	ConfigResetStat() (string, error)

	ConfigResetStatWithOptions(routeOption options.RouteOption) (string, error)

	ConfigSet(parameters map[string]string) (string, error)

	ConfigSetWithOptions(parameters map[string]string, routeOption options.RouteOption) (string, error)

	ConfigGet(parameters []string) (map[string]string, error)

	ConfigGetWithOptions(parameters []string, routeOption options.RouteOption) (models.ClusterValue[map[string]string], error)

	ConfigRewrite() (string, error)

	ConfigRewriteWithOptions(routeOption options.RouteOption) (string, error)
}
