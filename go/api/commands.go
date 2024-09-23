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
	//  result : "OK"
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
	//  key: initialValue
	//  result, err := client.SetWithOptions("key", "value", &api.SetOptions{
	//      ConditionalSet: api.OnlyIfExists,
	//      Expiry: &api.Expiry{
	//          Type: api.Seconds,
	//          Count: uint64(5),
	//      },
	//  })
	//  result: "OK"
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
	//  1. key: value
	//	   result, err := client.Get("key")
	//     result: "value"
	//  2. result, err := client.Get("nonExistentKey")
	//     result: ""
	//
	// [valkey.io]: https://valkey.io/commands/get/
	Get(key string) (string, error)

	// Sets multiple keys to multiple values in a single operation.
	//
	// Note:
	//  When in cluster mode, the command may route to multiple nodes when keys in keyValueMap map to different hash slots.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  keyValueMap - A key-value map consisting of keys and their respective values to set.
	//
	// Return value:
	//  "OK" on success.
	//
	// For example:
	//	result, err := client.MSet(map[string]string{"key1": "value1", "key2": "value2"})
	//  result: "OK"
	//
	// [valkey.io]: https://valkey.io/commands/mset/
	MSet(keyValueMap map[string]string) (string, error)

	// Retrieves the values of multiple keys.
	//
	// Note:
	//  When in cluster mode, the command may route to multiple nodes when keys map to different hash slots.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  keys - A list of keys to retrieve values for.
	//
	// Return value:
	//  An array of values corresponding to the provided keys.
	//  If a key is not found, its corresponding value in the list will be an empty string("").
	//
	// For example:
	//  key1: value1, key2: value2
	//	result, err := client.MGet([]string{"key1", "key2", "key3"})
	//  result : {"value1", "value2", ""}
	//
	// [valkey.io]: https://valkey.io/commands/mget/
	MGet(keys []string) ([]string, error)

	// Sets multiple keys to values if the key does not exist. The operation is atomic, and if one or more keys already exist,
	// the entire operation fails.
	//
	// Note:
	//  When in cluster mode, all keys in keyValueMap must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  keyValueMap - A key-value map consisting of keys and their respective values to set.
	//
	// Return value:
	//  true, if all keys were set. false, if no key was set.
	//
	// For example:
	//  1. result, err := client.MSetNX(map[string]string{"key1": "value1", "key2": "value2"})
	//     result: true
	//  2. key3: initialValue
	//	   result, err := client.MSetNX(map[string]string{"key3": "value3", "key4": "value4"})
	//     result: false
	//
	// [valkey.io]: https://valkey.io/commands/msetnx/
	MSetNX(keyValueMap map[string]string) (bool, error)

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
	//  key: 1
	//  result, err := client.Incr("key");
	//  result: 2
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
	//  key: 1
	//  result, err := client.IncrBy("key", 2);
	//  result: 3
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
	//  key: 1
	//  result, err := client.IncrBy("key", 0.5);
	//  result: 1.5
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
	//  key: 1
	//  result, err := client.Decr("key");
	//  result: 0
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
	//  key: 1
	//  result, err := client.DecrBy("key", 2);
	//  result: -1
	//
	// [valkey.io]: https://valkey.io/commands/decrby/
	DecrBy(key string, amount int64) (int64, error)

	// Returns the length of the string value stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key to check its length.
	//
	// Return value:
	//  The length of the string value stored at key.
	//  If key does not exist, it is treated as an empty string, and the command returns 0.
	//
	// For example:
	//  key: value
	//  result, err := client.Strlen("key");
	//  result: 5
	//
	// [valkey.io]: https://valkey.io/commands/strlen/
	Strlen(key string) (int64, error)

	// Overwrites part of the string stored at key, starting at the specified byte's offset, for the entire length of value.
	// If the offset is larger than the current length of the string at key, the string is padded with zero bytes to make
	// offset fit.
	// Creates the key if it doesn't exist.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key    - The key of the string to update.
	//  offset - The position in the string where value should be written.
	//  value  - The string written with offset.
	//
	// Return value:
	//  The length of the string stored at key after it was modified.
	//
	// For example:
	//  1. result, err := client.SetRange("key", 6, "GLIDE");
	//     result: 11 (New key created with length of 11 bytes)
	//     value, err  := client.Get("key")
	//     value: "\x00\x00\x00\x00\x00\x00GLIDE"
	//  2. "key": "愛" (value char takes 3 bytes)
	//     result, err = client.SetRange("key", 1, "a")
	//     "key": �a� // (becomes an invalid UTF-8 string)
	//
	// [valkey.io]: https://valkey.io/commands/setrange/
	SetRange(key string, offset int, value string) (int64, error)

	// Returns the substring of the string value stored at key, determined by the byte's offsets start and end (both are
	// inclusive).
	// Negative offsets can be used in order to provide an offset starting from the end of the string. So -1 means the last
	// character, -2 the penultimate and so forth.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the string.
	//  start - The starting offset.
	//  end   - The ending offset.
	//
	// Return value:
	//  A substring extracted from the value stored at key.
	//  An ("") empty string is returned if the offset is out of bounds.
	//
	// For example:
	//  1. mykey: "This is a string"
	//     result, err := client.GetRange("mykey", 0, 3);
	//     result: "This"
	//     result, err := client.GetRange("mykey", -3, -1);
	//     result: "ing" (extracted last 3 characters of a string)
	//  2. "key": "愛" (value char takes 3 bytes)
	//     result, err = client.GetRange("key", 0, 1)
	//     result: � // (returns an invalid UTF-8 string)
	//
	// [valkey.io]: https://valkey.io/commands/getrange/
	GetRange(key string, start int, end int) (string, error)

	// Appends a value to a key. If key does not exist it is created and set as an empty string, so APPEND will be similar to
	// SET in this special case.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the string.
	//  value - The value to append.
	//
	// Return value:
	//  The length of the string after appending the value.
	//
	// For example:
	//  result, err := client.Append("key", "value");
	//  result: 5
	//
	// [valkey.io]: https://valkey.io/commands/append/
	Append(key string, value string) (int64, error)

	// Returns the longest common subsequence between strings stored at key1 and key2.
	//
	// Since:
	//  Valkey 7.0 and above.
	//
	// Note:
	//  When in cluster mode, key1 and key2 must map to the same hash slot.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key1 - The key that stores the first string.
	//  key2 - The key that stores the second string.
	//
	// Return value:
	//  A String containing the longest common subsequence between the 2 strings.
	//  An empty String is returned if the keys do not exist or have no common subsequences.
	//
	// For example:
	//  testKey1: foo, testKey2: fao
	//  result, err := client.LCS("testKey1", "testKey2");
	//  result: "fo"
	//
	// [valkey.io]: https://valkey.io/commands/lcs/
	LCS(key1 string, key2 string) (string, error)
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

// HashCommands supports commands and transactions for the "Hash Commands" group for standalone and cluster
// clients.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=hash
type HashCommands interface {
	// HGet returns the value associated with field in the hash stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the hash.
	//  field - The field in the hash stored at key to retrieve from the database.
	//
	// Return value:
	//  The value associated with field, or an empty string when field is not present in the hash or key does not exist.
	//
	// For example:
	//  Assume we have the following hash:
	//  my_hash := map[string]string{"field1": "value", "field2": "another_value"}
	//  payload, err := client.HGet("my_hash", "field1")
	//  // payload equals "value"
	//  payload, err = client.HGet("my_hash", "nonexistent_field")
	//  // payload equals ""
	//
	// [valkey.io]: https://valkey.io/commands/hget/
	HGet(key string, field string) (string, error)

	// HGetAll returns all fields and values of the hash stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key of the hash.
	//
	// Return value:
	//  A map of all fields and their values in the hash, or an empty map when key does not exist.
	//
	// For example:
	//  fieldValueMap, err := client.HGetAll("my_hash")
	//  // fieldValueMap equals map[string]string{"field1": "value1", "field2": "value2"}
	//
	// [valkey.io]: https://valkey.io/commands/hgetall/
	HGetAll(key string) (map[string]string, error)

	// HMGet returns the values associated with the specified fields in the hash stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key    - The key of the hash.
	//  fields - The fields in the hash stored at key to retrieve from the database.
	//
	// Return value:
	//  An array of values associated with the given fields, in the same order as they are requested.
	//  For every field that does not exist in the hash, a null value is returned.
	//  If key does not exist, returns an empty string array.
	//
	// For example:
	//  values, err := client.HMGet("my_hash", []string{"field1", "field2"})
	//  // values equals []string{"value1", "value2"}
	//
	// [valkey.io]: https://valkey.io/commands/hmget/
	HMGet(key string, fields []string) ([]string, error)

	// HSet sets the specified fields to their respective values in the hash stored at key.
	// This command overwrites the values of specified fields that exist in the hash.
	// If key doesn't exist, a new key holding a hash is created.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key    - The key of the hash.
	//  values - A map of field-value pairs to set in the hash.
	//
	// Return value:
	//  The number of fields that were added or updated.
	//
	// For example:
	//  num, err := client.HSet("my_hash", map[string]string{"field": "value", "field2": "value2"})
	//  // num equals 2
	//
	// [valkey.io]: https://valkey.io/commands/hset/
	HSet(key string, values map[string]string) (int64, error)

	// HSetNX sets field in the hash stored at key to value, only if field does not yet exist.
	// If key does not exist, a new key holding a hash is created.
	// If field already exists, this operation has no effect.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the hash.
	//  field - The field to set.
	//  value - The value to set.
	//
	// Return value:
	//  true if field is a new field in the hash and value was set.
	//  false if field already exists in the hash and no operation was performed.
	//
	// For example:
	//  payload1, err := client.HSetNX("myHash", "field", "value")
	//  // payload1 equals true
	//  payload2, err := client.HSetNX("myHash", "field", "newValue")
	//  // payload2 equals false
	//
	// [valkey.io]: https://valkey.io/commands/hsetnx/
	HSetNX(key string, field string, value string) (bool, error)

	// HDel removes the specified fields from the hash stored at key.
	// Specified fields that do not exist within this hash are ignored.
	// If key does not exist, it is treated as an empty hash and this command returns 0.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key    - The key of the hash.
	//  fields - The fields to remove from the hash stored at key.
	//
	// Return value:
	//  The number of fields that were removed from the hash, not including specified but non-existing fields.
	//
	// For example:
	//  num, err := client.HDel("my_hash", []string{"field_1", "field_2"})
	//  // num equals 2
	//
	// [valkey.io]: https://valkey.io/commands/hdel/
	HDel(key string, fields []string) (int64, error)

	// HLen returns the number of fields contained in the hash stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key of the hash.
	//
	// Return value:
	//  The number of fields in the hash, or 0 when key does not exist.
	//  If key holds a value that is not a hash, an error is returned.
	//
	// For example:
	//  num1, err := client.HLen("myHash")
	//  // num1 equals 3
	//  num2, err := client.HLen("nonExistingKey")
	//  // num2 equals 0
	//
	// [valkey.io]: https://valkey.io/commands/hlen/
	HLen(key string) (int64, error)

	// HVals returns all values in the hash stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key of the hash.
	//
	// Return value:
	//  A slice of strings containing all the values in the hash, or an empty slice when key does not exist.
	//
	// For example:
	//  values, err := client.HVals("myHash")
	//  // values equals []string{"value1", "value2", "value3"}
	//
	// [valkey.io]: https://valkey.io/commands/hvals/
	HVals(key string) ([]string, error)

	// HExists returns if field is an existing field in the hash stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the hash.
	//  field - The field to check in the hash stored at key.
	//
	// Return value:
	//  true if the hash contains the specified field.
	//  false if the hash does not contain the field, or if the key does not exist.
	//
	// For example:
	//  exists, err := client.HExists("my_hash", "field1")
	//  // exists equals true
	//  exists, err = client.HExists("my_hash", "non_existent_field")
	//  // exists equals false
	//
	// [valkey.io]: https://valkey.io/commands/hexists/
	HExists(key string, field string) (bool, error)

	// HKeys returns all field names in the hash stored at key.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key - The key of the hash.
	//
	// Return value:
	//  A slice of strings containing all the field names in the hash, or an empty slice when key does not exist.
	//
	// For example:
	//  names, err := client.HKeys("my_hash")
	//  // names equals []string{"field_1", "field_2"}
	//
	// [valkey.io]: https://valkey.io/commands/hkeys/
	HKeys(key string) ([]string, error)

	// HStrLen returns the string length of the value associated with field in the hash stored at key.
	// If the key or the field do not exist, 0 is returned.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//  key   - The key of the hash.
	//  field - The field to get the string length of its value.
	//
	// Return value:
	//  The length of the string value associated with field, or 0 when field or key do not exist.
	//
	// For example:
	//  strlen, err := client.HStrLen("my_hash", "my_field")
	//  // strlen equals 10
	//
	// [valkey.io]: https://valkey.io/commands/hstrlen/
	HStrLen(key string, field string) (int64, error)
}
