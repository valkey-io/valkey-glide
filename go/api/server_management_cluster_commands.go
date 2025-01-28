// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// ServerManagementCommands supports commands for the "Server Management" group for a cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementClusterCommands interface {
	Info() (map[string]string, error)

	InfoWithOptions(options ClusterInfoOptions) (ClusterValue[string], error)
}
