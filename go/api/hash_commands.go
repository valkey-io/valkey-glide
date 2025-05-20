// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "Hash" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#hash
type HashCommands interface {
	HGet(ctx context.Context, key string, field string) (Result[string], error)

	HGetAll(ctx context.Context, key string) (map[string]string, error)

	HMGet(ctx context.Context, key string, fields []string) ([]Result[string], error)

	HSet(ctx context.Context, key string, values map[string]string) (int64, error)

	HSetNX(ctx context.Context, key string, field string, value string) (bool, error)

	HDel(ctx context.Context, key string, fields []string) (int64, error)

	HLen(ctx context.Context, key string) (int64, error)

	HVals(ctx context.Context, key string) ([]string, error)

	HExists(ctx context.Context, key string, field string) (bool, error)

	HKeys(ctx context.Context, key string) ([]string, error)

	HStrLen(ctx context.Context, key string, field string) (int64, error)

	HIncrBy(ctx context.Context, key string, field string, increment int64) (int64, error)

	HIncrByFloat(ctx context.Context, key string, field string, increment float64) (float64, error)

	HScan(ctx context.Context, key string, cursor string) (string, []string, error)

	HRandField(ctx context.Context, key string) (Result[string], error)

	HRandFieldWithCount(ctx context.Context, key string, count int64) ([]string, error)

	HRandFieldWithCountWithValues(ctx context.Context, key string, count int64) ([][]string, error)

	HScanWithOptions(ctx context.Context, key string, cursor string, options options.HashScanOptions) (string, []string, error)
}
