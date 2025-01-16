// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/valkey-io/valkey-glide/go/glide/api/options"

// Supports commands and transactions for the "Stream" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#stream
type StreamCommands interface {

	XAdd(key string, values [][]string) (Result[string], error)

	XAddWithOptions(key string, values [][]string, options *options.XAddOptions) (Result[string], error)

	XTrim(key string, options *options.XTrimOptions) (int64, error)

	XLen(key string) (int64, error)

	XRead(keysAndIds map[string]string) (map[string]map[string][][]string, error)

	XReadWithOptions(keysAndIds map[string]string, options *options.XReadOptions) (map[string]map[string][][]string, error)

	XDel(key string, ids []string) (int64, error)
}
