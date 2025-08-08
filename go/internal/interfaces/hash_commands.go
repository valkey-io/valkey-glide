// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "Hash" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#hash
type HashCommands interface {
	HGet(ctx context.Context, key string, field string) (models.Result[string], error)

	HGetAll(ctx context.Context, key string) (map[string]string, error)

	HMGet(ctx context.Context, key string, fields []string) ([]models.Result[string], error)

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

	HScan(ctx context.Context, key string, cursor models.Cursor) (models.ScanResult, error)

	HScanWithOptions(
		ctx context.Context,
		key string,
		cursor models.Cursor,
		options options.HashScanOptions,
	) (models.ScanResult, error)

	HRandField(ctx context.Context, key string) (models.Result[string], error)

	HRandFieldWithCount(ctx context.Context, key string, count int64) ([]string, error)

	HRandFieldWithCountWithValues(ctx context.Context, key string, count int64) ([][]string, error)

	HSetEx(ctx context.Context, key string, fieldsAndValues map[string]string, options options.HSetExOptions) (int64, error)

	HGetEx(ctx context.Context, key string, fields []string, options options.HGetExOptions) ([]models.Result[string], error)

	HExpire(
		ctx context.Context,
		key string,
		expireTime time.Duration,
		fields []string,
		options options.HExpireOptions,
	) ([]int64, error)

	HExpireAt(
		ctx context.Context,
		key string,
		expireTime time.Time,
		fields []string,
		options options.HExpireOptions,
	) ([]int64, error)

	HPExpire(
		ctx context.Context,
		key string,
		expireTime time.Duration,
		fields []string,
		options options.HExpireOptions,
	) ([]int64, error)

	HPExpireAt(
		ctx context.Context,
		key string,
		expireTime time.Time,
		fields []string,
		options options.HExpireOptions,
	) ([]int64, error)

	HPersist(ctx context.Context, key string, fields []string) ([]int64, error)

	HTtl(ctx context.Context, key string, fields []string) ([]int64, error)

	HPTtl(ctx context.Context, key string, fields []string) ([]int64, error)

	HExpireTime(ctx context.Context, key string, fields []string) ([]int64, error)

	HPExpireTime(ctx context.Context, key string, fields []string) ([]int64, error)
}
