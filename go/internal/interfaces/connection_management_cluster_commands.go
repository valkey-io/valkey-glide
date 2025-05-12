// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "Connection Management" group of commands for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementClusterCommands interface {
	Ping() (string, error)

	PingWithOptions(pingOptions options.ClusterPingOptions) (string, error)

	Echo(message string) (models.Result[string], error)

	EchoWithOptions(echoOptions options.ClusterEchoOptions) (models.ClusterValue[string], error)

	ClientId() (models.ClusterValue[int64], error)

	ClientIdWithOptions(routeOptions options.RouteOption) (models.ClusterValue[int64], error)

	ClientSetName(connectionName string) (models.ClusterValue[string], error)

	ClientSetNameWithOptions(connectionName string, routeOptions options.RouteOption) (models.ClusterValue[string], error)

	ClientGetName() (models.ClusterValue[string], error)

	ClientGetNameWithOptions(routeOptions options.RouteOption) (models.ClusterValue[string], error)
}
