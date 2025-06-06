// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "Generic Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=Generic
type GenericBaseCommands interface {
	Del(ctx context.Context, keys []string) (int64, error)

	Exists(ctx context.Context, keys []string) (int64, error)

	Expire(ctx context.Context, key string, expireTime time.Duration) (bool, error)

	ExpireWithOptions(ctx context.Context, key string, expireTime time.Duration, expireCondition constants.ExpireCondition) (bool, error)

	ExpireAt(ctx context.Context, key string, expireTime time.Time) (bool, error)

	ExpireAtWithOptions(
		ctx context.Context,
		key string,
		expireTime time.Time,
		expireCondition constants.ExpireCondition,
	) (bool, error)

	PExpire(ctx context.Context, key string, expireTime time.Duration) (bool, error)

	PExpireWithOptions(
		ctx context.Context,
		key string,
		expireTime time.Duration,
		expireCondition constants.ExpireCondition,
	) (bool, error)

	PExpireAt(ctx context.Context, key string, expireTime time.Time) (bool, error)

	PExpireAtWithOptions(
		ctx context.Context,
		key string,
		expireTime time.Time,
		expireCondition constants.ExpireCondition,
	) (bool, error)

	ExpireTime(ctx context.Context, key string) (int64, error)

	PExpireTime(ctx context.Context, key string) (int64, error)

	TTL(ctx context.Context, key string) (int64, error)

	PTTL(ctx context.Context, key string) (int64, error)

	Unlink(ctx context.Context, keys []string) (int64, error)

	Touch(ctx context.Context, keys []string) (int64, error)

	Type(ctx context.Context, key string) (string, error)

	Rename(ctx context.Context, key string, newKey string) (string, error)

	RenameNX(ctx context.Context, key string, newKey string) (bool, error)

	Persist(ctx context.Context, key string) (bool, error)

	Restore(ctx context.Context, key string, ttl time.Duration, value string) (string, error)

	RestoreWithOptions(ctx context.Context, key string, ttl time.Duration, value string, option options.RestoreOptions) (string, error)

	ObjectEncoding(ctx context.Context, key string) (models.Result[string], error)

	Dump(ctx context.Context, key string) (models.Result[string], error)

	ObjectFreq(ctx context.Context, key string) (models.Result[int64], error)

	ObjectIdleTime(ctx context.Context, key string) (models.Result[int64], error)

	ObjectRefCount(ctx context.Context, key string) (models.Result[int64], error)

	Sort(ctx context.Context, key string) ([]models.Result[string], error)

	SortWithOptions(ctx context.Context, key string, sortOptions options.SortOptions) ([]models.Result[string], error)

	SortStore(ctx context.Context, key string, destination string) (int64, error)

	SortStoreWithOptions(ctx context.Context, key string, destination string, sortOptions options.SortOptions) (int64, error)

	SortReadOnly(ctx context.Context, key string) ([]models.Result[string], error)

	SortReadOnlyWithOptions(ctx context.Context, key string, sortOptions options.SortOptions) ([]models.Result[string], error)

	Wait(ctx context.Context, numberOfReplicas int64, timeout time.Duration) (int64, error)

	Copy(ctx context.Context, source string, destination string) (bool, error)

	CopyWithOptions(ctx context.Context, source string, destination string, option options.CopyOptions) (bool, error)

	UpdateConnectionPassword(ctx context.Context, password string, immediateAuth bool) (string, error)

	ResetConnectionPassword(ctx context.Context) (string, error)
}
