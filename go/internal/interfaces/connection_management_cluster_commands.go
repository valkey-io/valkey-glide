// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "Connection Management" group of commands for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementClusterCommands interface {
	Ping(ctx context.Context) (string, error)

	PingWithOptions(ctx context.Context, pingOptions options.ClusterPingOptions) (string, error)

	Echo(ctx context.Context, message string) (models.Result[string], error)

	EchoWithOptions(ctx context.Context, message string, routeOptions options.RouteOption) (models.ClusterValue[string], error)

	ClientId(ctx context.Context) (models.ClusterValue[int64], error)

	ClientIdWithOptions(ctx context.Context, routeOptions options.RouteOption) (models.ClusterValue[int64], error)

	ClientSetName(ctx context.Context, connectionName string) (models.ClusterValue[string], error)

	ClientSetNameWithOptions(
		ctx context.Context,
		connectionName string,
		routeOptions options.RouteOption,
	) (models.ClusterValue[string], error)

	ClientGetName(ctx context.Context) (models.ClusterValue[string], error)

	ClientGetNameWithOptions(ctx context.Context, routeOptions options.RouteOption) (models.ClusterValue[string], error)
}
