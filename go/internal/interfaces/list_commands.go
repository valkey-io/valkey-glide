// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "List" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#list
type ListCommands interface {
	LPush(ctx context.Context, key string, elements []string) (int64, error)

	LPop(ctx context.Context, key string) (models.Result[string], error)

	LPopCount(ctx context.Context, key string, count int64) ([]string, error)

	LPos(ctx context.Context, key string, element string) (models.Result[int64], error)

	LPosWithOptions(ctx context.Context, key string, element string, options options.LPosOptions) (models.Result[int64], error)

	LPosCount(ctx context.Context, key string, element string, count int64) ([]int64, error)

	LPosCountWithOptions(
		ctx context.Context,
		key string,
		element string,
		count int64,
		options options.LPosOptions,
	) ([]int64, error)

	RPush(ctx context.Context, key string, elements []string) (int64, error)

	LRange(ctx context.Context, key string, start int64, end int64) ([]string, error)

	LIndex(ctx context.Context, key string, index int64) (models.Result[string], error)

	LTrim(ctx context.Context, key string, start int64, end int64) (string, error)

	LLen(ctx context.Context, key string) (int64, error)

	LRem(ctx context.Context, key string, count int64, element string) (int64, error)

	RPop(ctx context.Context, key string) (models.Result[string], error)

	RPopCount(ctx context.Context, key string, count int64) ([]string, error)

	LInsert(
		ctx context.Context,
		key string,
		insertPosition constants.InsertPosition,
		pivot string,
		element string,
	) (int64, error)

	BLPop(ctx context.Context, keys []string, timeout time.Duration) ([]string, error)

	BRPop(ctx context.Context, keys []string, timeout time.Duration) ([]string, error)

	RPushX(ctx context.Context, key string, elements []string) (int64, error)

	LPushX(ctx context.Context, key string, elements []string) (int64, error)

	LMPop(ctx context.Context, keys []string, listDirection constants.ListDirection) ([]models.KeyValues, error)

	LMPopCount(
		ctx context.Context,
		keys []string,
		listDirection constants.ListDirection,
		count int64,
	) ([]models.KeyValues, error)

	BLMPop(
		ctx context.Context,
		keys []string,
		listDirection constants.ListDirection,
		timeout time.Duration,
	) ([]models.KeyValues, error)

	BLMPopCount(
		ctx context.Context,
		keys []string,
		listDirection constants.ListDirection,
		count int64,
		timeout time.Duration,
	) ([]models.KeyValues, error)

	LSet(ctx context.Context, key string, index int64, element string) (string, error)

	LMove(
		ctx context.Context,
		source string,
		destination string,
		whereFrom constants.ListDirection,
		whereTo constants.ListDirection,
	) (models.Result[string], error)

	BLMove(
		ctx context.Context,
		source string,
		destination string,
		whereFrom constants.ListDirection,
		whereTo constants.ListDirection,
		timeout time.Duration,
	) (models.Result[string], error)
}
