// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "Generic Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=Generic
type GenericBaseCommands interface {

	Del(keys []string) (int64, error)

	Exists(keys []string) (int64, error)

	Expire(key string, seconds int64) (bool, error)

	ExpireWithOptions(key string, seconds int64, expireCondition ExpireCondition) (bool, error)

	ExpireAt(key string, unixTimestampInSeconds int64) (bool, error)

	ExpireAtWithOptions(key string, unixTimestampInSeconds int64, expireCondition ExpireCondition) (bool, error)

	PExpire(key string, milliseconds int64) (bool, error)

	PExpireWithOptions(key string, milliseconds int64, expireCondition ExpireCondition) (bool, error)

	PExpireAt(key string, unixTimestampInMilliSeconds int64) (bool, error)

	PExpireAtWithOptions(key string, unixTimestampInMilliSeconds int64, expireCondition ExpireCondition) (bool, error)

	ExpireTime(key string) (int64, error)

	PExpireTime(key string) (int64, error)

	TTL(key string) (int64, error)

	PTTL(key string) (int64, error)

	Unlink(keys []string) (int64, error)

	Touch(keys []string) (int64, error)

	Type(key string) (Result[string], error)

	Rename(key string, newKey string) (Result[string], error)

	Renamenx(key string, newKey string) (bool, error)

	Persist(key string) (bool, error)
}
