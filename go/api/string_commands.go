// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "String" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#string
type StringCommands interface {
	Set(ctx context.Context, key string, value string) (string, error)

	SetWithOptions(ctx context.Context, key string, value string, options options.SetOptions) (Result[string], error)

	Get(ctx context.Context, key string) (Result[string], error)

	GetEx(ctx context.Context, key string) (Result[string], error)

	GetExWithOptions(ctx context.Context, key string, options options.GetExOptions) (Result[string], error)

	MSet(ctx context.Context, keyValueMap map[string]string) (string, error)

	MGet(ctx context.Context, keys []string) ([]Result[string], error)

	MSetNX(ctx context.Context, keyValueMap map[string]string) (bool, error)

	Incr(ctx context.Context, key string) (int64, error)

	IncrBy(ctx context.Context, key string, amount int64) (int64, error)

	IncrByFloat(ctx context.Context, key string, amount float64) (float64, error)

	Decr(ctx context.Context, key string) (int64, error)

	DecrBy(ctx context.Context, key string, amount int64) (int64, error)

	Strlen(ctx context.Context, key string) (int64, error)

	SetRange(ctx context.Context, key string, offset int, value string) (int64, error)

	GetRange(ctx context.Context, key string, start int, end int) (string, error)

	Append(ctx context.Context, key string, value string) (int64, error)

	LCS(ctx context.Context, key1 string, key2 string) (string, error)

	LCSLen(ctx context.Context, key1 string, key2 string) (int64, error)

	LCSWithOptions(ctx context.Context, key1, key2 string, opts options.LCSIdxOptions) (map[string]interface{}, error)

	GetDel(ctx context.Context, key string) (Result[string], error)
}
