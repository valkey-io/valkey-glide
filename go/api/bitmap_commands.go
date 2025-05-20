// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "Bitmap" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#bitmap
type BitmapCommands interface {
	SetBit(ctx context.Context, key string, offset int64, value int64) (int64, error)

	GetBit(ctx context.Context, key string, offset int64) (int64, error)

	BitCount(ctx context.Context, key string) (int64, error)

	BitCountWithOptions(ctx context.Context, key string, options options.BitCountOptions) (int64, error)

	BitPos(ctx context.Context, key string, bit int64) (int64, error)

	BitPosWithOptions(ctx context.Context, key string, bit int64, options options.BitPosOptions) (int64, error)

	BitField(ctx context.Context, key string, subCommands []options.BitFieldSubCommands) ([]Result[int64], error)

	BitFieldRO(ctx context.Context, key string, commands []options.BitFieldROCommands) ([]Result[int64], error)

	BitOp(ctx context.Context, bitwiseOperation options.BitOpType, destination string, keys []string) (int64, error)
}
