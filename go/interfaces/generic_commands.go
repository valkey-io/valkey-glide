// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// GenericCommands supports commands for the "Generic Commands" group for standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#generic
type GenericCommands interface {
	CustomCommand(ctx context.Context, args []string) (any, error)

	Move(ctx context.Context, key string, dbIndex int64) (bool, error)

	Scan(ctx context.Context, cursor models.Cursor) (models.ScanResult, error)

	ScanWithOptions(ctx context.Context, cursor models.Cursor, scanOptions options.ScanOptions) (models.ScanResult, error)

	RandomKey(ctx context.Context) (models.Result[string], error)

	// MigrateKeys transfers keys to a destination Valkey instance.
	//
	// See [valkey.io] for details.
	//
	// [valkey.io]: https://valkey.io/commands/migrate/
	MigrateKeys(
		ctx context.Context,
		host string,
		port int64,
		keys []string,
		destinationDB int64,
		timeout int64,
	) (string, error)

	// MigrateKeysWithOptions transfers keys to a destination Valkey instance with additional options.
	//
	// See [valkey.io] for details.
	//
	// [valkey.io]: https://valkey.io/commands/migrate/
	MigrateKeysWithOptions(
		ctx context.Context,
		host string,
		port int64,
		keys []string,
		destinationDB int64,
		timeout int64,
		migrateOptions options.MigrateOptions,
	) (string, error)
}
