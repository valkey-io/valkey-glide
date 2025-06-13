// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// SortedSetCommands supports commands and transactions for the "Sorted Set Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#sorted-set
type SortedSetCommands interface {
	ZAdd(ctx context.Context, key string, membersScoreMap map[string]float64) (int64, error)

	ZAddWithOptions(
		ctx context.Context,
		key string,
		membersScoreMap map[string]float64,
		opts options.ZAddOptions,
	) (int64, error)

	ZAddIncr(ctx context.Context, key string, member string, increment float64) (float64, error)

	ZAddIncrWithOptions(
		ctx context.Context,
		key string,
		member string,
		increment float64,
		opts options.ZAddOptions,
	) (models.Result[float64], error)

	ZIncrBy(ctx context.Context, key string, increment float64, member string) (float64, error)

	ZPopMin(ctx context.Context, key string) (map[string]float64, error)

	ZPopMinWithOptions(ctx context.Context, key string, options options.ZPopOptions) (map[string]float64, error)

	ZPopMax(ctx context.Context, key string) (map[string]float64, error)

	ZPopMaxWithOptions(ctx context.Context, key string, options options.ZPopOptions) (map[string]float64, error)

	ZRem(ctx context.Context, key string, members []string) (int64, error)

	ZCard(ctx context.Context, key string) (int64, error)

	BZPopMin(ctx context.Context, keys []string, timeout time.Duration) (models.Result[models.KeyWithMemberAndScore], error)

	BZMPop(
		ctx context.Context,
		keys []string,
		scoreFilter constants.ScoreFilter,
		timeout time.Duration,
	) (models.Result[models.KeyWithArrayOfMembersAndScores], error)

	BZMPopWithOptions(
		ctx context.Context,
		keys []string,
		scoreFilter constants.ScoreFilter,
		timeout time.Duration,
		options options.ZMPopOptions,
	) (models.Result[models.KeyWithArrayOfMembersAndScores], error)

	ZRange(ctx context.Context, key string, rangeQuery options.ZRangeQuery) ([]string, error)

	BZPopMax(ctx context.Context, keys []string, timeout time.Duration) (models.Result[models.KeyWithMemberAndScore], error)

	ZMPop(
		ctx context.Context,
		keys []string,
		scoreFilter constants.ScoreFilter,
	) (models.Result[models.KeyWithArrayOfMembersAndScores], error)

	ZMPopWithOptions(
		ctx context.Context,
		keys []string,
		scoreFilter constants.ScoreFilter,
		opts options.ZMPopOptions,
	) (models.Result[models.KeyWithArrayOfMembersAndScores], error)

	ZRangeWithScores(
		ctx context.Context,
		key string,
		rangeQuery options.ZRangeQueryWithScores,
	) ([]models.MemberAndScore, error)

	ZRangeStore(ctx context.Context, destination string, key string, rangeQuery options.ZRangeQuery) (int64, error)

	ZRank(ctx context.Context, key string, member string) (models.Result[int64], error)

	ZRankWithScore(ctx context.Context, key string, member string) (models.Result[int64], models.Result[float64], error)

	ZRevRank(ctx context.Context, key string, member string) (models.Result[int64], error)

	ZRevRankWithScore(ctx context.Context, key string, member string) (models.Result[int64], models.Result[float64], error)

	ZScore(ctx context.Context, key string, member string) (models.Result[float64], error)

	ZCount(ctx context.Context, key string, rangeOptions options.ZCountRange) (int64, error)

	ZScan(ctx context.Context, key string, cursor models.Cursor) (models.ScanResult, error)

	ZScanWithOptions(
		ctx context.Context,
		key string,
		cursor models.Cursor,
		options options.ZScanOptions,
	) (models.ScanResult, error)

	ZRemRangeByLex(ctx context.Context, key string, rangeQuery options.RangeByLex) (int64, error)

	ZRemRangeByRank(ctx context.Context, key string, start int64, stop int64) (int64, error)

	ZRemRangeByScore(ctx context.Context, key string, rangeQuery options.RangeByScore) (int64, error)

	ZDiff(ctx context.Context, keys []string) ([]string, error)

	ZDiffWithScores(ctx context.Context, keys []string) ([]models.MemberAndScore, error)

	ZRandMember(ctx context.Context, key string) (models.Result[string], error)

	ZRandMemberWithCount(ctx context.Context, key string, count int64) ([]string, error)

	ZRandMemberWithCountWithScores(ctx context.Context, key string, count int64) ([]models.MemberAndScore, error)

	ZMScore(ctx context.Context, key string, members []string) ([]models.Result[float64], error)

	ZDiffStore(ctx context.Context, destination string, keys []string) (int64, error)

	ZInter(ctx context.Context, keys options.KeyArray) ([]string, error)

	ZInterWithScores(
		ctx context.Context,
		keysOrWeightedKeys options.KeysOrWeightedKeys,
		options options.ZInterOptions,
	) ([]models.MemberAndScore, error)

	ZInterStore(ctx context.Context, destination string, keysOrWeightedKeys options.KeysOrWeightedKeys) (int64, error)

	ZInterStoreWithOptions(
		ctx context.Context,
		destination string,
		keysOrWeightedKeys options.KeysOrWeightedKeys,
		options options.ZInterOptions,
	) (int64, error)

	ZUnion(ctx context.Context, keys options.KeyArray) ([]string, error)

	ZUnionWithScores(
		ctx context.Context,
		keysOrWeightedKeys options.KeysOrWeightedKeys,
		options options.ZUnionOptions,
	) ([]models.MemberAndScore, error)

	ZUnionStore(ctx context.Context, destination string, keysOrWeightedKeys options.KeysOrWeightedKeys) (int64, error)

	ZUnionStoreWithOptions(
		ctx context.Context,
		destination string,
		keysOrWeightedKeys options.KeysOrWeightedKeys,
		zUnionOptions options.ZUnionOptions,
	) (int64, error)

	ZInterCard(ctx context.Context, keys []string) (int64, error)

	ZInterCardWithOptions(ctx context.Context, keys []string, options options.ZInterCardOptions) (int64, error)

	ZLexCount(ctx context.Context, key string, rangeQuery options.RangeByLex) (int64, error)
}
