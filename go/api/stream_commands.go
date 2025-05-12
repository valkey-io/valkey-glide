// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/api/models"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "Stream" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#stream
type StreamCommands interface {
	XAdd(key string, values [][]string) (models.Result[string], error)

	XAddWithOptions(key string, values [][]string, options options.XAddOptions) (models.Result[string], error)

	XTrim(key string, options options.XTrimOptions) (int64, error)

	XLen(key string) (int64, error)

	XAutoClaim(key string, group string, consumer string, minIdleTime int64, start string) (models.XAutoClaimResponse, error)

	XAutoClaimWithOptions(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		start string,
		options options.XAutoClaimOptions,
	) (models.XAutoClaimResponse, error)

	XAutoClaimJustId(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		start string,
	) (models.XAutoClaimJustIdResponse, error)

	XAutoClaimJustIdWithOptions(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		start string,
		options options.XAutoClaimOptions,
	) (models.XAutoClaimJustIdResponse, error)

	XReadGroup(group string, consumer string, keysAndIds map[string]string) (map[string]map[string][][]string, error)

	XReadGroupWithOptions(
		group string,
		consumer string,
		keysAndIds map[string]string,
		options options.XReadGroupOptions,
	) (map[string]map[string][][]string, error)

	XRead(keysAndIds map[string]string) (map[string]map[string][][]string, error)

	XReadWithOptions(keysAndIds map[string]string, options options.XReadOptions) (map[string]map[string][][]string, error)

	XDel(key string, ids []string) (int64, error)

	XPending(key string, group string) (models.XPendingSummary, error)

	XPendingWithOptions(key string, group string, options options.XPendingOptions) ([]models.XPendingDetail, error)

	XGroupSetId(key string, group string, id string) (string, error)

	XGroupSetIdWithOptions(key string, group string, id string, opts options.XGroupSetIdOptions) (string, error)

	XGroupCreate(key string, group string, id string) (string, error)

	XGroupCreateWithOptions(key string, group string, id string, opts options.XGroupCreateOptions) (string, error)

	XGroupDestroy(key string, group string) (bool, error)

	XGroupCreateConsumer(key string, group string, consumer string) (bool, error)

	XGroupDelConsumer(key string, group string, consumer string) (int64, error)

	XAck(key string, group string, ids []string) (int64, error)

	XClaim(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
	) (map[string][][]string, error)

	XClaimWithOptions(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
		options options.XClaimOptions,
	) (map[string][][]string, error)

	XClaimJustId(key string, group string, consumer string, minIdleTime int64, ids []string) ([]string, error)

	XClaimJustIdWithOptions(
		key string,
		group string,
		consumer string,
		minIdleTime int64,
		ids []string,
		options options.XClaimOptions,
	) ([]string, error)

	XInfoStream(key string) (map[string]any, error)

	XInfoStreamFullWithOptions(key string, options *options.XInfoStreamOptions) (map[string]any, error)

	XInfoConsumers(key string, group string) ([]models.XInfoConsumerInfo, error)

	XInfoGroups(key string) ([]models.XInfoGroupInfo, error)

	XRange(key string, start options.StreamBoundary, end options.StreamBoundary) ([]models.XRangeResponse, error)

	XRangeWithOptions(
		key string,
		start options.StreamBoundary,
		end options.StreamBoundary,
		options options.XRangeOptions,
	) ([]models.XRangeResponse, error)

	XRevRange(key string, start options.StreamBoundary, end options.StreamBoundary) ([]models.XRangeResponse, error)

	XRevRangeWithOptions(
		key string,
		start options.StreamBoundary,
		end options.StreamBoundary,
		options options.XRangeOptions,
	) ([]models.XRangeResponse, error)
}
