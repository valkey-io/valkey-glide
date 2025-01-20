// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/valkey-io/valkey-glide/go/glide/api/options"

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

	// Sorts the elements in the list, set, or sorted set at key and returns the result.
	// The sort command can be used to sort elements based on different criteria and apply
	// transformations on sorted elements.
	// To store the result into a new key, see the sortStore function.
	//
	// Parameters:
	// key - The key of the list, set, or sorted set to be sorted.
	//
	// Return value:
	// An Array of sorted elements.
	//
	// Example:
	//
	// result, err := client.Sort("key")
	// result.Value(): [{1 false} {2 false} {3 false}]
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/sort/
	Sort(key string) ([]Result[string], error)

	// Sorts the elements in the list, set, or sorted set at key and returns the result.
	// The sort command can be used to sort elements based on different criteria and apply
	// transformations on sorted elements.
	// To store the result into a new key, see the sortStore function.
	//
	// Note:
	//  In cluster mode, if `key` map to different hash slots, the command
	//  will be split across these slots and executed separately for each. This means the command
	//  is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//  call will return the first encountered error, even though some requests may have succeeded
	//  while others did not. If this behavior impacts your application logic, consider splitting
	//  the request into sub-requests per slot to ensure atomicity.
	//  The use of SortOptions.byPattern and SortOptions.getPatterns in cluster mode is
	//  supported since Valkey version 8.0.
	//
	// Parameters:
	// key - The key of the list, set, or sorted set to be sorted.
	// sortOptions - The SortOptions type.
	//
	// Return value:
	// An Array of sorted elements.
	//
	// Example:
	//
	// options := api.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).AddGetPattern("object_*").AddGetPattern("#")
	// result, err := client.Sort("key", options)
	// result.Value(): [{Object_3 false} {c false} {Object_1 false} {a false} {Object_2 false} {b false}]
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/sort/
	SortWithOptions(key string, sortOptions *options.SortOptions) ([]Result[string], error)

	// Sorts the elements in the list, set, or sorted set at key and stores the result in
	// destination. The sort command can be used to sort elements based on
	// different criteria, apply transformations on sorted elements, and store the result in a new key.
	// The sort command can be used to sort elements based on different criteria and apply
	// transformations on sorted elements.
	// To get the sort result without storing it into a key, see the sort or sortReadOnly function.
	//
	// Note:
	//  In cluster mode, if `key` and `destination` map to different hash slots, the command
	//  will be split across these slots and executed separately for each. This means the command
	//  is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//  call will return the first encountered error, even though some requests may have succeeded
	//  while others did not. If this behavior impacts your application logic, consider splitting
	//  the request into sub-requests per slot to ensure atomicity.
	//
	// Parameters:
	// key - The key of the list, set, or sorted set to be sorted.
	// destination - The key where the sorted result will be stored.
	//
	// Return value:
	// The number of elements in the sorted key stored at destination.
	//
	// Example:
	//
	// result, err := client.SortStore("key","destkey")
	// result.Value(): 1
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/sort/
	SortStore(key string, destination string) (Result[int64], error)

	// Sorts the elements in the list, set, or sorted set at key and stores the result in
	// destination. The sort command can be used to sort elements based on
	// different criteria, apply transformations on sorted elements, and store the result in a new key.
	// The sort command can be used to sort elements based on different criteria and apply
	// transformations on sorted elements.
	// To get the sort result without storing it into a key, see the sort or sortReadOnly function.
	//
	// Note:
	//  In cluster mode, if `key` and `destination` map to different hash slots, the command
	//  will be split across these slots and executed separately for each. This means the command
	//  is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//  call will return the first encountered error, even though some requests may have succeeded
	//  while others did not. If this behavior impacts your application logic, consider splitting
	//  the request into sub-requests per slot to ensure atomicity.
	//  The use of SortOptions.byPattern and SortOptions.getPatterns
	//  in cluster mode is supported since Valkey version 8.0.
	//
	// Parameters:
	// key - The key of the list, set, or sorted set to be sorted.
	// destination - The key where the sorted result will be stored.
	// sortOptions - The SortOptions type.
	//
	// Return value:
	// The number of elements in the sorted key stored at destination.
	//
	// Example:
	//
	// options := api.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).AddGetPattern("object_*").AddGetPattern("#")
	// result, err := client.SortStore("key","destkey",options)
	// result.Value(): 1
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/sort/
	SortStoreWithOptions(key string, destination string, sortOptions *options.SortOptions) (Result[int64], error)

	// Sorts the elements in the list, set, or sorted set at key and returns the result.
	// The sortReadOnly command can be used to sort elements based on different criteria and apply
	// transformations on sorted elements.
	// This command is routed depending on the client's ReadFrom strategy.
	//
	// Parameters:
	// key - The key of the list, set, or sorted set to be sorted.
	//
	// Return value:
	// An Array of sorted elements.
	//
	// Example:
	//
	// result, err := client.SortReadOnly("key")
	// result.Value(): [{1 false} {2 false} {3 false}]
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/sort/
	SortReadOnly(key string) ([]Result[string], error)

	// Sorts the elements in the list, set, or sorted set at key and returns the result.
	// The sort command can be used to sort elements based on different criteria and apply
	// transformations on sorted elements.
	// This command is routed depending on the client's ReadFrom strategy.
	//
	// Note:
	//  In cluster mode, if `key` map to different hash slots, the command
	//  will be split across these slots and executed separately for each. This means the command
	//  is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//  call will return the first encountered error, even though some requests may have succeeded
	//  while others did not. If this behavior impacts your application logic, consider splitting
	//  the request into sub-requests per slot to ensure atomicity.
	//  The use of SortOptions.byPattern and SortOptions.getPatterns in cluster mode is
	//  supported since Valkey version 8.0.
	//
	// Parameters:
	// key - The key of the list, set, or sorted set to be sorted.
	// sortOptions - The SortOptions type.
	//
	// Return value:
	// An Array of sorted elements.
	//
	// Example:
	//
	// options := api.NewSortOptions().SetByPattern("weight_*").SetIsAlpha(false).AddGetPattern("object_*").AddGetPattern("#")
	// result, err := client.SortReadOnly("key", options)
	// result.Value(): [{Object_3 false} {c false} {Object_1 false} {a false} {Object_2 false} {b false}]
	// result.IsNil(): false
	//
	// [valkey.io]: https://valkey.io/commands/sort/
	SortReadOnlyWithOptions(key string, sortOptions *options.SortOptions) ([]Result[string], error)
}
