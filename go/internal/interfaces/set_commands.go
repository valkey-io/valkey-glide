// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "Set" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#set
type SetCommands interface {
	SAdd(ctx context.Context, key string, members []string) (int64, error)

	SRem(ctx context.Context, key string, members []string) (int64, error)

	SMembers(ctx context.Context, key string) (map[string]struct{}, error)

	SCard(ctx context.Context, key string) (int64, error)

	SIsMember(ctx context.Context, key string, member string) (bool, error)

	SDiff(ctx context.Context, keys []string) (map[string]struct{}, error)

	SDiffStore(ctx context.Context, destination string, keys []string) (int64, error)

	SInter(ctx context.Context, keys []string) (map[string]struct{}, error)

	SInterStore(ctx context.Context, destination string, keys []string) (int64, error)

	SInterCard(ctx context.Context, keys []string) (int64, error)

	SInterCardLimit(ctx context.Context, keys []string, limit int64) (int64, error)

	SRandMember(ctx context.Context, key string) (models.Result[string], error)

	SRandMemberCount(ctx context.Context, key string, count int64) ([]string, error)

	SPop(ctx context.Context, key string) (models.Result[string], error)

	SPopCount(ctx context.Context, key string, count int64) (map[string]struct{}, error)

	SMIsMember(ctx context.Context, key string, members []string) ([]bool, error)

	SUnionStore(ctx context.Context, destination string, keys []string) (int64, error)

	SUnion(ctx context.Context, keys []string) (map[string]struct{}, error)

	SScan(ctx context.Context, key string, cursor models.Cursor) (models.ScanResult, error)

	SScanWithOptions(
		ctx context.Context,
		key string,
		cursor models.Cursor,
		options options.BaseScanOptions,
	) (models.ScanResult, error)

	SMove(ctx context.Context, source string, destination string, member string) (bool, error)
}
