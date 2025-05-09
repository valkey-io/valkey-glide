// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "Connection Management" group of commands for standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementCommands interface {
	Ping(ctx context.Context) (string, error)

	PingWithOptions(ctx context.Context, pingOptions options.PingOptions) (string, error)

	Echo(ctx context.Context, message string) (Result[string], error)

	ClientId(ctx context.Context) (int64, error)

	ClientGetName(ctx context.Context) (string, error)

	ClientSetName(ctx context.Context, connectionName string) (string, error)
}
