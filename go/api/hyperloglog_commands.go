// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "context"

// Supports commands and transactions for the "HyperLogLog" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#hyperloglog
type HyperLogLogCommands interface {
	PfAdd(ctx context.Context, key string, elements []string) (int64, error)

	PfCount(ctx context.Context, keys []string) (int64, error)

	PfMerge(ctx context.Context, destination string, sourceKeys []string) (string, error)
}
