// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// SortedSetCommands supports commands and transactions for the "Sorted Set Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#sorted-set
type SortedSetCommands interface {
	ZAdd(key string, membersScoreMap map[string]float64) (int64, error)

	ZAddWithOptions(key string, membersScoreMap map[string]float64, opts options.ZAddOptions) (int64, error)

	ZAddIncr(key string, member string, increment float64) (Result[float64], error)

	ZAddIncrWithOptions(key string, member string, increment float64, opts options.ZAddOptions) (Result[float64], error)

	ZIncrBy(key string, increment float64, member string) (float64, error)

	ZPopMin(key string) (map[string]float64, error)

	ZPopMinWithOptions(key string, options options.ZPopOptions) (map[string]float64, error)

	ZPopMax(key string) (map[string]float64, error)

	ZPopMaxWithOptions(key string, options options.ZPopOptions) (map[string]float64, error)

	ZRem(key string, members []string) (int64, error)

	ZCard(key string) (int64, error)

	BZPopMin(keys []string, timeoutSecs float64) (Result[KeyWithMemberAndScore], error)

	BZMPop(keys []string, scoreFilter options.ScoreFilter, timeoutSecs float64) (Result[KeyWithArrayOfMembersAndScores], error)

	BZMPopWithOptions(
		keys []string,
		scoreFilter options.ScoreFilter,
		timeoutSecs float64,
		options options.ZMPopOptions,
	) (Result[KeyWithArrayOfMembersAndScores], error)

	ZRange(key string, rangeQuery options.ZRangeQuery) ([]string, error)

	ZRangeWithScores(key string, rangeQuery options.ZRangeQueryWithScores) (map[string]float64, error)

	ZRangeStore(destination string, key string, rangeQuery options.ZRangeQuery) (int64, error)

	ZRank(key string, member string) (Result[int64], error)

	ZRankWithScore(key string, member string) (Result[int64], Result[float64], error)

	ZRevRank(key string, member string) (Result[int64], error)

	ZRevRankWithScore(key string, member string) (Result[int64], Result[float64], error)

	ZScore(key string, member string) (Result[float64], error)

	ZCount(key string, rangeOptions options.ZCountRange) (int64, error)

	ZScan(key string, cursor string) (string, []string, error)

	ZScanWithOptions(key string, cursor string, options options.ZScanOptions) (string, []string, error)

	ZRemRangeByLex(key string, rangeQuery options.RangeByLex) (int64, error)

	ZRemRangeByRank(key string, start int64, stop int64) (int64, error)

	ZRemRangeByScore(key string, rangeQuery options.RangeByScore) (int64, error)

	ZDiff(keys []string) ([]string, error)

	ZDiffWithScores(keys []string) (map[string]float64, error)

	ZRandMember(key string) (Result[string], error)

	ZRandMemberWithCount(key string, count int64) ([]string, error)

	ZRandMemberWithCountWithScores(key string, count int64) ([]MemberAndScore, error)

	ZMScore(key string, members []string) ([]Result[float64], error)

	ZDiffStore(destination string, keys []string) (int64, error)

	ZInter(keys options.KeyArray) ([]string, error)

	ZInterWithScores(keysOrWeightedKeys options.KeysOrWeightedKeys, options options.ZInterOptions) (map[string]float64, error)

	ZInterStore(destination string, keysOrWeightedKeys options.KeysOrWeightedKeys) (int64, error)

	ZInterStoreWithOptions(
		destination string,
		keysOrWeightedKeys options.KeysOrWeightedKeys,
		options options.ZInterOptions,
	) (int64, error)

	ZUnion(keys options.KeyArray) ([]string, error)

	ZUnionWithScores(keysOrWeightedKeys options.KeysOrWeightedKeys, options *options.ZUnionOptions) (map[string]float64, error)

	ZUnionStore(destination string, keysOrWeightedKeys options.KeysOrWeightedKeys) (int64, error)

	ZUnionStoreWithOptions(
		destination string,
		keysOrWeightedKeys options.KeysOrWeightedKeys,
		zUnionOptions *options.ZUnionOptions,
	) (int64, error)

	ZInterCard(keys []string) (int64, error)

	ZInterCardWithOptions(keys []string, options *options.ZInterCardOptions) (int64, error)

	ZLexCount(key string, rangeQuery *options.RangeByLex) (int64, error)
}
