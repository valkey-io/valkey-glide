// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// TransactionBaseCommands supports commands for the "Transaction" group for a standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#transactions
type TransactionBaseCommands interface {
	Watch(keys []string) (string, error)

	Exec() error

	Discard() error
}
