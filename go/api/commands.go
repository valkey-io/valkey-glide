// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// StringCommands defines an interface for the "String Commands" group of commands for standalone and cluster clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=string
type StringCommands interface {
	// Set the given key with the given value. The return value is a response from Valkey containing the string "OK".
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key to store.
	//  value - The value to store with the given key.
	//
	// Return value:
	//  A simple "OK" response on success.
	//
	// For example:
	//	result, err := client.Set("key", "value")
	//
	// [valkey.io]: https://valkey.io/commands/set/
	Set(key string, value string) (string, error)

	// SetWithOptions sets the given key with the given value using the given options. The return value is dependent on the
	// passed options. If the value is successfully set, "OK" is returned. If value isn't set because of [OnlyIfExists] or
	// [OnlyIfDoesNotExist] conditions, an empty string is returned (""). If [SetOptions#ReturnOldValue] is set, the old
	// value is returned.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key     - The key to store.
	//  value   - The value to store with the given key.
	//  options - The Set options.
	//
	// Return value:
	//  If the value is successfully set, return "OK".
	//  If value isn't set because of ConditionalSet.OnlyIfExists or ConditionalSet.OnlyIfDoesNotExist conditions, return ("").
	//  If SetOptions.returnOldValue is set, return the old value as a String.
	//
	// For example:
	//  result, err := client.SetWithOptions("key", "value", &api.SetOptions{
	//      ConditionalSet: api.OnlyIfExists,
	//      Expiry: &api.Expiry{
	//          Type: api.Seconds,
	//          Count: uint64(5),
	//      },
	//  })
	//
	// [valkey.io]: https://valkey.io/commands/set/
	SetWithOptions(key string, value string, options *SetOptions) (string, error)

	// Get string value associated with the given key, or an empty string is returned ("") if no such value exists.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key to be retrieved from the database.
	//
	// Return value:
	//  If key exists, returns the value of key as a String. Otherwise, return ("").
	//
	// For example:
	//	result, err := client.Get("key")
	//
	// [valkey.io]: https://valkey.io/commands/get/
	Get(key string) (string, error)

	// Increments the number stored at key by one. If key does not exist, it is set to 0 before performing the operation.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key to increment its value.
	//
	// Return value:
	//  The value of key after the increment.
	//
	// For example:
	//  result, err := client.Incr("key");
	//
	// [valkey.io]: https://valkey.io/commands/incr/
	Incr(key string) (int64, error)

	// Increments the number stored at key by amount. If key does not exist, it is set to 0 before performing the operation.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key    - The key to increment its value.
	//  amount - The amount to increment.
	//
	// Return value:
	//  The value of key after the increment.
	//
	// For example:
	//  result, err := client.IncrBy("key", 2);
	//
	// [valkey.io]: https://valkey.io/commands/incrby/
	IncrBy(key string, amount int64) (int64, error)

	// Increments the string representing a floating point number stored at key by amount. By using a negative increment value,
	// the result is that the value stored at key is decremented. If key does not exist, it is set to 0 before performing the
	// operation.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key    - The key to increment its value.
	//  amount - The amount to increment.
	//
	// Return value:
	//  The value of key after the increment.
	//
	// For example:
	//  result, err := client.IncrBy("key", 0.5);
	//
	// [valkey.io]: https://valkey.io/commands/incrbyfloat/
	IncrByFloat(key string, amount float64) (float64, error)

	// Decrements the number stored at key by one. If key does not exist, it is set to 0 before performing the operation.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key to decrement its value.
	//
	// Return value:
	//  The value of key after the decrement.
	//
	// For example:
	//  result, err := client.Decr("key");
	//
	// [valkey.io]: https://valkey.io/commands/decr/
	Decr(key string) (int64, error)

	// Decrements the number stored at code by amount. If key does not exist, it is set to 0 before performing the operation.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key    - The key to decrement its value.
	//  amount - The amount to decrement.
	//
	// Return value:
	//  The value of key after the decrement.
	//
	// For example:
	//  result, err := client.Decr("key");
	//
	// [valkey.io]: https://valkey.io/commands/decrby/
	DecrBy(key string, amount int64) (int64, error)

	// GetDel gets the value associated with the given key and deletes the key.
	//
	// Parameters:
	//  key - The key to get and delete.
	//
	// Return value:
	//  If key exists, returns the value of the key as a String and deletes the key.
	//  If key does not exist, returns an empty string ("").
	//
	// For example:
	//	result, err := client.GetDel("key")
	//
	//[valkey.io]: https://valkey.io/commands/getdel/
	GetDel(key string) (string, error)
}
