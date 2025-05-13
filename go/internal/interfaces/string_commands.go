// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
    "github.com/valkey-io/valkey-glide/go/api/models"
    "github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "String" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#string
type StringCommands interface {
    Set(key string, value string) (string, error)

    SetWithOptions(key string, value string, options options.SetOptions) (models.Result[string], error)

    Get(key string) (models.Result[string], error)

    GetEx(key string) (models.Result[string], error)

    GetExWithOptions(key string, options options.GetExOptions) (models.Result[string], error)

    MSet(keyValueMap map[string]string) (string, error)

    MGet(keys []string) ([]models.Result[string], error)

    MSetNX(keyValueMap map[string]string) (bool, error)

    Incr(key string) (int64, error)

    IncrBy(key string, amount int64) (int64, error)

    IncrByFloat(key string, amount float64) (float64, error)

    Decr(key string) (int64, error)

    DecrBy(key string, amount int64) (int64, error)

    Strlen(key string) (int64, error)

    SetRange(key string, offset int, value string) (int64, error)

    GetRange(key string, start int, end int) (string, error)

    Append(key string, value string) (int64, error)

    LCS(key1 string, key2 string) (string, error)

    LCSLen(key1 string, key2 string) (int64, error)

    LCSWithOptions(key1, key2 string, opts options.LCSIdxOptions) (map[string]any, error)

    GetDel(key string) (models.Result[string], error)
}
