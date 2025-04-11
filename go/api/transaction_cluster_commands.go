// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// TransactionCluterCommands supports commands for the "Transaction" group for a cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#transactions
type TransactionClusterCommands interface {
	UnWatch(keys string) (string, error)
}
