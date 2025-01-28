// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "Connection Management" group of commands for cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementClusterCommands interface {
	Echo(message string) (Result[string], error)

	EchoWithOptions(options ClusterEchoOptions) (ClusterValue[string], error)
}
