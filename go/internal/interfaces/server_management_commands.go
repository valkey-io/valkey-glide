// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// ServerManagementCommands supports commands for the "Server Management" group for a standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementCommands interface {
	Select(ctx context.Context, index int64) (string, error)

	ConfigGet(ctx context.Context, args []string) (map[string]string, error)

	ConfigSet(ctx context.Context, parameters map[string]string) (string, error)

	Info(ctx context.Context) (string, error)

	InfoWithOptions(ctx context.Context, options options.InfoOptions) (string, error)

	DBSize(ctx context.Context) (int64, error)

	Time(ctx context.Context) ([]string, error)

	FlushAll(ctx context.Context) (string, error)

	FlushAllWithOptions(ctx context.Context, mode options.FlushMode) (string, error)

	FlushDB(ctx context.Context) (string, error)

	FlushDBWithOptions(ctx context.Context, mode options.FlushMode) (string, error)

	Lolwut(ctx context.Context) (string, error)

	LolwutWithOptions(ctx context.Context, opts options.LolwutOptions) (string, error)

	LastSave(ctx context.Context) (int64, error)

	ConfigResetStat(ctx context.Context) (string, error)

	ConfigRewrite(ctx context.Context) (string, error)
}
