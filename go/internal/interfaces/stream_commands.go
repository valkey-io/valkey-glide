// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "Stream" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#stream
type StreamCommands interface {
	XAdd(ctx context.Context, key string, values [][]string) (models.Result[string], error)

	XAddWithOptions(
		ctx context.Context,
		key string,
		values [][]string,
		options options.XAddOptions,
	) (models.Result[string], error)

	XTrim(ctx context.Context, key string, options options.XTrimOptions) (int64, error)

	XLen(ctx context.Context, key string) (int64, error)

	XAutoClaim(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		start string,
	) (models.XAutoClaimResponse, error)

	XAutoClaimWithOptions(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		start string,
		options options.XAutoClaimOptions,
	) (models.XAutoClaimResponse, error)

	XAutoClaimJustId(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		start string,
	) (models.XAutoClaimJustIdResponse, error)

	XAutoClaimJustIdWithOptions(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		start string,
		options options.XAutoClaimOptions,
	) (models.XAutoClaimJustIdResponse, error)

	XReadGroup(
		ctx context.Context,
		group string,
		consumer string,
		keysAndIds map[string]string,
	) (map[string]map[string][][]string, error)

	XReadGroupWithOptions(
		ctx context.Context,
		group string,
		consumer string,
		keysAndIds map[string]string,
		options options.XReadGroupOptions,
	) (map[string]map[string][][]string, error)

	XRead(ctx context.Context, keysAndIds map[string]string) (map[string]map[string][][]string, error)

	XReadWithOptions(
		ctx context.Context,
		keysAndIds map[string]string,
		options options.XReadOptions,
	) (map[string]map[string][][]string, error)

	XDel(ctx context.Context, key string, ids []string) (int64, error)

	XPending(ctx context.Context, key string, group string) (models.XPendingSummary, error)

	XPendingWithOptions(
		ctx context.Context,
		key string,
		group string,
		options options.XPendingOptions,
	) ([]models.XPendingDetail, error)

	XGroupSetId(ctx context.Context, key string, group string, id string) (string, error)

	XGroupSetIdWithOptions(
		ctx context.Context,
		key string,
		group string,
		id string,
		opts options.XGroupSetIdOptions,
	) (string, error)

	XGroupCreate(ctx context.Context, key string, group string, id string) (string, error)

	XGroupCreateWithOptions(
		ctx context.Context,
		key string,
		group string,
		id string,
		opts options.XGroupCreateOptions,
	) (string, error)

	XGroupDestroy(ctx context.Context, key string, group string) (bool, error)

	XGroupCreateConsumer(ctx context.Context, key string, group string, consumer string) (bool, error)

	XGroupDelConsumer(ctx context.Context, key string, group string, consumer string) (int64, error)

	XAck(ctx context.Context, key string, group string, ids []string) (int64, error)

	XClaim(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
	) (map[string][][]string, error)

	XClaimWithOptions(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
		options options.XClaimOptions,
	) (map[string][][]string, error)

	XClaimJustId(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
	) ([]string, error)

	XClaimJustIdWithOptions(
		ctx context.Context,
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
		options options.XClaimOptions,
	) ([]string, error)

	XInfoStream(ctx context.Context, key string) (map[string]any, error)

	XInfoStreamFullWithOptions(ctx context.Context, key string, options *options.XInfoStreamOptions) (map[string]any, error)

	XInfoConsumers(ctx context.Context, key string, group string) ([]models.XInfoConsumerInfo, error)

	XInfoGroups(ctx context.Context, key string) ([]models.XInfoGroupInfo, error)

	XRange(
		ctx context.Context,
		key string,
		start options.StreamBoundary,
		end options.StreamBoundary,
	) ([]models.XRangeResponse, error)

	XRangeWithOptions(
		ctx context.Context,
		key string,
		start options.StreamBoundary,
		end options.StreamBoundary,
		options options.XRangeOptions,
	) ([]models.XRangeResponse, error)

	XRevRange(
		ctx context.Context,
		key string,
		start options.StreamBoundary,
		end options.StreamBoundary,
	) ([]models.XRangeResponse, error)

	XRevRangeWithOptions(
		ctx context.Context,
		key string,
		start options.StreamBoundary,
		end options.StreamBoundary,
		options options.XRangeOptions,
	) ([]models.XRangeResponse, error)
}
