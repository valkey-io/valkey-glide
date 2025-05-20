// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "Connection Management" group of commands for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementClusterCommands interface {
	Ping(ctx context.Context) (string, error)

	PingWithOptions(ctx context.Context, pingOptions options.ClusterPingOptions) (string, error)

	Echo(ctx context.Context, message string) (Result[string], error)

	EchoWithOptions(ctx context.Context, message string, routeOptions options.RouteOption) (ClusterValue[string], error)

	ClientId(ctx context.Context) (ClusterValue[int64], error)

	ClientIdWithOptions(ctx context.Context, routeOptions options.RouteOption) (ClusterValue[int64], error)

	ClientSetName(ctx context.Context, connectionName string) (ClusterValue[string], error)

	ClientSetNameWithOptions(
		ctx context.Context,
		connectionName string,
		routeOptions options.RouteOption,
	) (ClusterValue[string], error)

	ClientGetName(ctx context.Context) (ClusterValue[string], error)

	ClientGetNameWithOptions(ctx context.Context, routeOptions options.RouteOption) (ClusterValue[string], error)
}
