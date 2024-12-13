// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "List Commands" group for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// GenericBaseCommands defines an interface for the "Generic Commands".
//
// [valkey.io]: https://valkey.io/commands/?group=Generic
type GenericBaseCommands interface {
	// Del removes the specified keys from the database. A key is ignored if it does not exist.
	//
	// Note:
	//  In cluster mode, if keys in `keyValueMap` map to different hash slots, the command
	//  will be split across these slots and executed separately for each. This means the command
	//  is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//  call will return the first encountered error, even though some requests may have succeeded
	//  while others did not. If this behavior impacts your application logic, consider splitting
	//  the request into sub-requests per slot to ensure atomicity.
	//
	// Parameters:
	//  keys - One or more keys to delete.
	//
	// Return value:
	//  Returns the number of keys that were removed.
	//
	// Example:
	//	result, err := client.Del([]string{"key1", "key2", "key3"})
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result) // Output: 2
	//
	// [valkey.io]: https://valkey.io/commands/del/
	Del(keys []string) (Result[int64], error)

	// Exists returns the number of keys that exist in the database
	//
	// Note:
	//  In cluster mode, if keys in `keyValueMap` map to different hash slots, the command
	//  will be split across these slots and executed separately for each. This means the command
	//  is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//  call will return the first encountered error, even though some requests may have succeeded
	//  while others did not. If this behavior impacts your application logic, consider splitting
	//  the request into sub-requests per slot to ensure atomicity.
	//
	// Parameters:
	// keys - One or more keys to check if they exist.
	//
	// Return value:
	// Returns the number of existing keys.
	//
	// Example:
	// result, err := client.Exists([]string{"key1", "key2", "key3"})
	// result.Value(): 2
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/exists/
	Exists(keys []string) (Result[int64], error)

	// Expire sets a timeout on key. After the timeout has expired, the key will automatically be deleted
	//
	// If key already has an existing expire set, the time to live is updated to the new value.
	// If seconds is a non-positive number, the key will be deleted rather than expired.
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	//
	// Parameters:
	// key - The key to expire.
	// seconds - Time in seconds for the key to expire
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.Expire("key", 1)
	// result.Value(): true
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/expire/
	Expire(key string, seconds int64) (Result[bool], error)

	// Expire sets a timeout on key. After the timeout has expired, the key will automatically be deleted
	//
	// If key already has an existing expire set, the time to live is updated to the new value.
	// If seconds is a non-positive number, the key will be deleted rather than expired.
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	//
	// Parameters:
	// key - The key to expire.
	// seconds - Time in seconds for the key to expire
	// option - The option  to set expiry - NX, XX, GT, LT
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.Expire("key", 1, api.OnlyIfDoesNotExist)
	// result.Value(): true
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/expire/
	ExpireWithOptions(key string, seconds int64, expireCondition ExpireCondition) (Result[bool], error)

	// ExpireAt sets a timeout on key. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of
	// specifying the number of seconds. A timestamp in the past will delete the key immediately. After the timeout has
	// expired, the key will automatically be deleted.
	// If key already has an existing expire set, the time to live is updated to the new value.
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	// If key already has an existing expire set, the time to live is updated to the new value.
	// If seconds is a non-positive number, the key will be deleted rather than expired.
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	//
	// Parameters:
	// key - The key to expire.
	// unixTimestampInSeconds - Absolute Unix timestamp
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.ExpireAt("key", time.Now().Unix())
	// result.Value(): true
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/expireat/
	ExpireAt(key string, unixTimestampInSeconds int64) (Result[bool], error)

	// ExpireAt sets a timeout on key. It takes an absolute Unix timestamp (seconds since January 1, 1970) instead of
	// specifying the number of seconds. A timestamp in the past will delete the key immediately. After the timeout has
	// expired, the key will automatically be deleted.
	// If key already has an existing expire set, the time to live is updated to the new value.
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	// If key already has an existing expire set, the time to live is updated to the new value.
	// If seconds is a non-positive number, the key will be deleted rather than expired.
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	//
	// Parameters:
	// key - The key to expire.
	// unixTimestampInSeconds - Absolute Unix timestamp
	// option - The option  to set expiry - NX, XX, GT, LT
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.ExpireAt("key", time.Now().Unix(), api.OnlyIfDoesNotExist)
	// result.Value(): true
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/expireat/
	ExpireAtWithOptions(key string, unixTimestampInSeconds int64, expireCondition ExpireCondition) (Result[bool], error)

	// Sets a timeout on key in milliseconds. After the timeout has expired, the key will automatically be deleted.
	// If key already has an existing expire set, the time to live is updated to the new value.
	// If milliseconds is a non-positive number, the key will be deleted rather than expired
	// The timeout will only be cleared by commands that delete or overwrite the contents of key.

	// Parameters:
	// key - The key to set timeout on it.
	// milliseconds - The timeout in milliseconds.
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.PExpire("key", int64(5 * 1000))
	// result.Value(): true
	// result.IsNil(): false
	//
	//  [valkey.io]: https://valkey.io/commands/pexpire/
	PExpire(key string, milliseconds int64) (Result[bool], error)

	// Sets a timeout on key in milliseconds. After the timeout has expired, the key will automatically be deleted.
	// If key already has an existing expire set, the time to live is updated to the new value.
	// If milliseconds is a non-positive number, the key will be deleted rather than expired
	// The timeout will only be cleared by commands that delete or overwrite the contents of key.
	//
	// Parameters:
	// key - The key to set timeout on it.
	// milliseconds - The timeout in milliseconds.
	// option - The option  to set expiry - NX, XX, GT, LT
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.PExpire("key", int64(5 * 1000), api.OnlyIfDoesNotExist)
	// result.Value(): true
	// result.IsNil(): false
	//
	//	[valkey.io]: https://valkey.io/commands/pexpire/
	PExpireWithOptions(key string, milliseconds int64, expireCondition ExpireCondition) (Result[bool], error)

	// Sets a timeout on key. It takes an absolute Unix timestamp (milliseconds since
	// January 1, 1970) instead of specifying the number of milliseconds.
	// A timestamp in the past will delete the key immediately. After the timeout has
	// expired, the key will automatically be deleted
	// If key already has an existing expire set, the time to live is
	// updated to the new value/
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	//
	// Parameters:
	// key - The key to set timeout on it.
	// unixMilliseconds - The timeout in an absolute Unix timestamp.
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.PExpire("key", time.Now().Unix()*1000)
	// result.Value(): true
	// result.IsNil(): false
	//
	//	[valkey.io]: https://valkey.io/commands/pexpireat/
	PExpireAt(key string, unixTimestampInMilliSeconds int64) (Result[bool], error)

	// Sets a timeout on key. It takes an absolute Unix timestamp (milliseconds since
	// January 1, 1970) instead of specifying the number of milliseconds.
	// A timestamp in the past will delete the key immediately. After the timeout has
	// expired, the key will automatically be deleted
	// If key already has an existing expire set, the time to live is
	// updated to the new value/
	// The timeout will only be cleared by commands that delete or overwrite the contents of key
	//
	// Parameters:
	// key - The key to set timeout on it.
	// unixMilliseconds - The timeout in an absolute Unix timestamp.
	// option - The option  to set expiry - NX, XX, GT, LT
	//
	// Return value:
	// A Result[bool] containing true is expiry is set.
	//
	// Example:
	// result, err := client.PExpire("key", time.Now().Unix()*1000, api.OnlyIfDoesNotExist)
	// result.Value(): true
	// result.IsNil(): false
	//
	//	[valkey.io]: https://valkey.io/commands/pexpireat/
	PExpireAtWithOptions(key string, unixTimestampInMilliSeconds int64, expireCondition ExpireCondition) (Result[bool], error)

	// Expire Time returns the absolute Unix timestamp (since January 1, 1970) at which the given key
	// will expire, in seconds.
	//
	// Parameters:
	// key - The key to determine the expiration value of.
	//
	// Return value:
	// The expiration Unix timestamp in seconds.
	// -2 if key does not exist or -1 is key exists but has no associated expiration.
	//
	// Example:
	//
	// result, err := client.ExpireTime("key")
	// result.Value(): 1732118030
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/expiretime/
	ExpireTime(key string) (Result[int64], error)

	// PExpire Time returns the absolute Unix timestamp (since January 1, 1970) at which the given key
	// will expire, in milliseconds.
	//
	// Parameters:
	// key - The key to determine the expiration value of.
	//
	// Return value:
	// The expiration Unix timestamp in milliseconds.
	// -2 if key does not exist or -1 is key exists but has no associated expiration.
	//
	// Example:
	//
	// result, err := client.PExpireTime("key")
	// result.Value(): 33177117420000
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/pexpiretime/
	PExpireTime(key string) (Result[int64], error)

	// TTL returns the remaining time to live of key that has a timeout, in seconds.
	//
	// Parameters:
	// key - The key to return its timeout.
	//
	// Return value:
	// Returns TTL in seconds,
	// -2 if key does not exist, or -1 if key exists but has no associated expiration.
	//
	// Example:
	//
	// result, err := client.TTL("key")
	// result.Value(): 3
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/ttl/
	TTL(key string) (Result[int64], error)

	// PTTL returns the remaining time to live of key that has a timeout, in milliseconds.
	//
	// Parameters:
	// key - The key to return its timeout.
	//
	// Return value:
	// Returns TTL in milliseconds,
	// -2 if key does not exist, or -1 if key exists but has no associated expiration.
	//
	// Example:
	//
	// result, err := client.PTTL("key")
	// result.Value(): 1000
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/pttl/
	PTTL(key string) (Result[int64], error)
}
