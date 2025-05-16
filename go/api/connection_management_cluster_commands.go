// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/valkey-io/valkey-glide/go/api/options"

// Supports commands and transactions for the "Connection Management" group of commands for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementClusterCommands interface {
	Ping() (string, error)

	PingWithOptions(pingOptions options.ClusterPingOptions) (string, error)

	Echo(message string) (Result[string], error)

	EchoWithOptions(message string, routeOptions options.RouteOption) (ClusterValue[string], error)

	ClientId() (ClusterValue[int64], error)

	ClientIdWithOptions(routeOptions options.RouteOption) (ClusterValue[int64], error)

	ClientSetName(connectionName string) (ClusterValue[string], error)

	ClientSetNameWithOptions(connectionName string, routeOptions options.RouteOption) (ClusterValue[string], error)

	ClientGetName() (ClusterValue[string], error)

	ClientGetNameWithOptions(routeOptions options.RouteOption) (ClusterValue[string], error)
}
