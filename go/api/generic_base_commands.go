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

	// Create a key associated with a value that is obtained by
	// deserializing the provided serialized value (obtained via [valkey.io]: Https://valkey.io/commands/dump/).
	//
	// Parameters:
	//  key - The key to create.
	//	ttl - The expiry time (in milliseconds). If 0, the key will persist.
	//  value - The serialized value to deserialize and assign to key.
	//
	// Return value:
	//  Return OK if successfully create a key with a value </code>.
	//
	// Example:
	// result, err := client.Restore("key",ttl, value)
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: OK
	//
	// [valkey.io]: https://valkey.io/commands/restore/
	Restore(key string, ttl int64, value string) (Result[string], error)

	// Create a key associated with a value that is obtained by
	// deserializing the provided serialized value (obtained via [valkey.io]: Https://valkey.io/commands/dump/).
	//
	// Parameters:
	//  key - The key to create.
	//	ttl - The expiry time (in milliseconds). If 0, the key will persist.
	//  value - The serialized value to deserialize and assign to key.
	//  restoreOptions - Set restore options with replace and absolute TTL modifiers, object idletime and frequency
	//
	// Return value:
	//  Return OK if successfully create a key with a value.
	//
	// Example:
	// restoreOptions := api.NewRestoreOptionsBuilder().SetReplace().SetABSTTL().SetEviction(api.FREQ, 10)
	// resultRestoreOpt, err := client.RestoreWithOptions(key, ttl, value, restoreOptions)
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: OK
	//
	// [valkey.io]: https://valkey.io/commands/restore/
	RestoreWithOptions(key string, ttl int64, value string, option *RestoreOptions) (Result[string], error)

	// Returns the internal encoding for the Valkey object stored at key.
	//
	// Note:
	//  When in cluster mode, both key and newkey must map to the same hash slot.
	//
	// Parameters:
	//  The key of the object to get the internal encoding of.
	//
	// Return value:
	//  If key exists, returns the internal encoding of the object stored at
	//  key as a String. Otherwise, returns null.
	//
	// Example:
	// result, err := client.ObjectEncoding("mykeyRenamenx")
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: embstr
	//
	// [valkey.io]: https://valkey.io/commands/object-encoding/
	ObjectEncoding(key string) (Result[string], error)

	// Serialize the value stored at key in a Valkey-specific format and return it to the user.
	//
	// Parameters:
	//  The key to serialize.
	//
	// Return value:
	//  The serialized value of the data stored at key
	//  If key does not exist, null will be returned.
	//
	// Example:
	//  result, err := client.Dump([]string{"key"})
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: (Serialized Value)
	//
	// [valkey.io]: https://valkey.io/commands/dump/
	Dump(key string) (Result[string], error)

	ObjectFreq(key string) (Result[int64], error)

	ObjectIdleTime(key string) (Result[int64], error)

	ObjectRefCount(key string) (Result[int64], error)
}
