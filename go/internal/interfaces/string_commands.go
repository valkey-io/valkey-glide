// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "String" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#string
type StringCommands interface {
	Set(ctx context.Context, key string, value string) (string, error)

	SetWithOptions(ctx context.Context, key string, value string, options options.SetOptions) (models.Result[string], error)

	Get(ctx context.Context, key string) (models.Result[string], error)

	GetEx(ctx context.Context, key string) (models.Result[string], error)

	GetExWithOptions(ctx context.Context, key string, options options.GetExOptions) (models.Result[string], error)

	MSet(ctx context.Context, keyValueMap map[string]string) (string, error)

	MGet(ctx context.Context, keys []string) ([]models.Result[string], error)

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

	LCS(ctx context.Context, key1 string, key2 string) (*models.LCSMatch, error)

	LCSLen(ctx context.Context, key1 string, key2 string) (*models.LCSMatch, error)

	LCSWithOptions(ctx context.Context, key1, key2 string, opts options.LCSIdxOptions) (*models.LCSMatch, error)

	GetDel(ctx context.Context, key string) (models.Result[string], error)
}
