// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package pipeline

// #include "../lib.h"
import "C"

import (
	"errors"
	"math"
	"reflect"
	"strconv"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"

	"github.com/valkey-io/valkey-glide/go/v2/internal"
	"github.com/valkey-io/valkey-glide/go/v2/internal/utils"
)

// Executes a single command, specified by args, without checking inputs. Every part of the command,
// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
// the executed command.
//
// See [Valkey GLIDE Documentation] for details on the restrictions and limitations of the custom command API.
//
// This function should only be used for single-response commands. Commands that don't return complete response and awaits
// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
//
// Parameters:
//
//	args - Arguments for the custom command.
//
// Command Response:
//
//	The returned value for the custom command.
//
// [Valkey GLIDE Documentation]: https://glide.valkey.io/concepts/client-features/custom-commands/
func (b *BaseBatch[T]) CustomCommand(args []string) *T {
	return b.addCmd(C.CustomCommand, args)
}

// Retrieves the value associated with the given key, or `nil` if no such key exists.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to retrieve from the database.
//
// Command Response:
//
//	If key exists, returns the value of key.
//	Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/get/
func (b *BaseBatch[T]) Get(key string) *T {
	return b.addCmdAndTypeChecker(C.Get, []string{key}, reflect.String, true)
}

// Sets the given key with the given value.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to store.
//	value - The value to store with the given key.
//
// Command Response:
//
//	If the value is successfully set, returns OK.
//
// [valkey.io]: https://valkey.io/commands/set/
func (b *BaseBatch[T]) Set(key string, value string) *T {
	return b.addCmdAndTypeChecker(C.Set, []string{key, value}, reflect.String, false)
}

// Sets the given key with the given value using the given options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key to store.
//	value   - The value to store with the given key.
//	options - The [options.SetOptions].
//
// Command Response:
//
//	If the value is successfully set, returns "OK".
//	If value isn't set because of ConditionalSet.OnlyIfExists or ConditionalSet.OnlyIfDoesNotExist
//	or ConditionalSet.OnlyIfEquals conditions, returns `nil`.
//	If SetOptions.returnOldValue is set, returns the old value as a String.
//
// [valkey.io]: https://valkey.io/commands/set/
func (b *BaseBatch[T]) SetWithOptions(key string, value string, options options.SetOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("SetWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.Set, append([]string{key, value}, optionArgs...), reflect.String, true)
}

// Retrieves the value associated with the given key and optionally sets the expiration of the key.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to retrieve from the database.
//
// Command Response:
//
//	If key exists, returns the value of key.
//	Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/getex/
func (b *BaseBatch[T]) GetEx(key string) *T {
	return b.addCmdAndTypeChecker(C.GetEx, []string{key}, reflect.String, true)
}

// Retrieves the value associated with the given key and optionally sets the expiration of the key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key to retrieve from the database.
//	options - The [options.GetExOptions].
//
// Command Response:
//
//	If key exists, returns the value of key.
//	Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/getex/
func (b *BaseBatch[T]) GetExWithOptions(key string, options options.GetExOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("GetExWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.GetEx, append([]string{key}, optionArgs...), reflect.String, true)
}

// Sets multiple keys to multiple values in a single operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keyValueMap - A key-value map consisting of keys and their respective values to set.
//
// Command Response:
//
//	"OK" on success.
//
// [valkey.io]: https://valkey.io/commands/mset/
func (b *BaseBatch[T]) MSet(keyValueMap map[string]string) *T {
	return b.addCmdAndTypeChecker(C.MSet, utils.MapToString(keyValueMap), reflect.String, false)
}

// Sets multiple keys to values if the key does not exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keyValueMap - A key-value map consisting of keys and their respective values to set.
//
// Command Response:
//
//	true, if all keys were set.
//	false, if no key was set.
//
// [valkey.io]: https://valkey.io/commands/msetnx/
func (b *BaseBatch[T]) MSetNX(keyValueMap map[string]string) *T {
	return b.addCmdAndTypeChecker(C.MSetNX, utils.MapToString(keyValueMap), reflect.Bool, false)
}

// Moves key from the currently selected database to the database specified by `dbIndex`.
//
// Note:
//
//	In cluster mode move is available since Valkey 9.0.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to move.
//	dbIndex - The index of the database to move key to.
//
// Command Response:
//
//	`true` if `key` was moved, or `false` if the `key` already exists in the destination
//	database or does not exist in the source database.
//
// [valkey.io]: https://valkey.io/commands/move/
func (b *BaseBatch[T]) Move(key string, dbIndex int64) *T {
	return b.addCmdAndTypeChecker(C.Move, []string{key, utils.IntToString(dbIndex)}, reflect.Bool, false)
}

// Retrieves the values of multiple keys.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - A list of keys to retrieve values for.
//
// Command Response:
//
//	An array of [models.Result[string]] values corresponding to the provided keys.
//	If a key is not found, its corresponding value in the list will be a [models.CreateNilStringResult()].
//
// [valkey.io]: https://valkey.io/commands/mget/
func (b *BaseBatch[T]) MGet(keys []string) *T {
	return b.addCmdAndConverter(
		C.MGet,
		keys,
		reflect.Slice,
		false,
		internal.ConvertArrayOfNilOr[string],
	)
}

// Increments the number stored at key by one. If key does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to increment its value.
//
// Command Response:
//
//	The value of `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/incr/
func (b *BaseBatch[T]) Incr(key string) *T {
	return b.addCmdAndTypeChecker(C.Incr, []string{key}, reflect.Int64, false)
}

// Increments the number stored at key by amount. If key does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key to increment its value.
//	amount - The amount to increment.
//
// Command Response:
//
//	The value of `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/incrby/
func (b *BaseBatch[T]) IncrBy(key string, amount int64) *T {
	return b.addCmdAndTypeChecker(C.IncrBy, []string{key, utils.IntToString(amount)}, reflect.Int64, false)
}

// Increments the string representing a floating point number stored at key by amount. By using a negative increment value,
// the result is that the value stored at key is decremented. If key does not exist, it is set to `0` before performing the
// operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key to increment its value.
//	amount - The amount to increment.
//
// Command Response:
//
//	The value of `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/incrbyfloat/
func (b *BaseBatch[T]) IncrByFloat(key string, amount float64) *T {
	return b.addCmdAndTypeChecker(C.IncrByFloat, []string{key, utils.FloatToString(amount)}, reflect.Float64, false)
}

// Decrements the number stored at key by one. If key does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to decrement its value.
//
// Command Response:
//
//	The value of `key` after the decrement.
//
// [valkey.io]: https://valkey.io/commands/decr/
func (b *BaseBatch[T]) Decr(key string) *T {
	return b.addCmdAndTypeChecker(C.Decr, []string{key}, reflect.Int64, false)
}

// Decrements the number stored at code by amount. If key does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key to decrement its value.
//	amount - The amount to decrement.
//
// Command Response:
//
//	The value of `key` after the decrement.
//
// [valkey.io]: https://valkey.io/commands/decrby/
func (b *BaseBatch[T]) DecrBy(key string, amount int64) *T {
	return b.addCmdAndTypeChecker(C.DecrBy, []string{key, utils.IntToString(amount)}, reflect.Int64, false)
}

// Returns the length of the string value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to check its length.
//
// Command Response:
//
//	The length of the string value stored at `key`.
//	If key does not exist, it is treated as an empty string, and the command returns `0`.
//
// [valkey.io]: https://valkey.io/commands/strlen/
func (b *BaseBatch[T]) Strlen(key string) *T {
	return b.addCmdAndTypeChecker(C.Strlen, []string{key}, reflect.Int64, false)
}

// Overwrites part of the string stored at key, starting at the specified byte's offset, for the entire length of value.
// If the offset is larger than the current length of the string at key, the string is padded with zero bytes to make
// offset fit.
// Creates the key if it doesn't exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the string to update.
//	offset - The position in the string where value should be written.
//	value  - The string written with offset.
//
// Command Response:
//
//	The length of the string stored at `key` after it was modified.
//
// [valkey.io]: https://valkey.io/commands/setrange/
func (b *BaseBatch[T]) SetRange(key string, offset int, value string) *T {
	return b.addCmdAndTypeChecker(C.SetRange, []string{key, strconv.Itoa(offset), value}, reflect.Int64, false)
}

// Returns the substring of the string value stored at key, determined by the byte's offsets start and end (both are
// inclusive).
// Negative offsets can be used in order to provide an offset starting from the end of the string. So `-1` means the last
// character, `-2` the penultimate and so forth.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the string.
//	start - The starting offset.
//	end   - The ending offset.
//
// Command Response:
//
//	A substring extracted from the value stored at key. Returns empty string if the offset is out of bounds.
//
// [valkey.io]: https://valkey.io/commands/getrange/
func (b *BaseBatch[T]) GetRange(key string, start int, end int) *T {
	return b.addCmdAndTypeChecker(C.GetRange, []string{key, strconv.Itoa(start), strconv.Itoa(end)}, reflect.String, false)
}

// Appends a value to a key. If key does not exist it is created and set as an empty string, so APPEND will be similar to
// SET in this special case.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the string.
//	value - The value to append.
//
// Command Response:
//
//	The length of the string after appending the value.
//
// [valkey.io]: https://valkey.io/commands/append/
func (b *BaseBatch[T]) Append(key string, value string) *T {
	return b.addCmdAndTypeChecker(C.Append, []string{key, value}, reflect.Int64, false)
}

// Returns the longest common subsequence between strings stored at `key1` and `key2`.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key1 - The key that stores the first string.
//	key2 - The key that stores the second string.
//
// Command Response:
//
//	A string containing all the longest common subsequences combined between the 2 strings.
//	An empty string is returned if the keys do not exist or have no common subsequences.
//
// [valkey.io]: https://valkey.io/commands/lcs/
func (b *BaseBatch[T]) LCS(key1 string, key2 string) *T {
	return b.addCmdAndTypeChecker(C.LCS, []string{key1, key2}, reflect.String, false)
}

// Returns the total length of all the longest common subsequences between strings stored at `key1` and `key2`.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key1 - The key that stores the first string.
//	key2 - The key that stores the second string.
//
// Command Response:
//
//	The total length of all the longest common subsequences the 2 strings.
//
// [valkey.io]: https://valkey.io/commands/lcs/
func (b *BaseBatch[T]) LCSLen(key1 string, key2 string) *T {
	return b.addCmdAndTypeChecker(C.LCS, []string{key1, key2, options.LCSLenCommand}, reflect.Int64, false)
}

// Returns the longest common subsequence between strings stored at `key1` and `key2`.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key1 - The key that stores the first string.
//	key2 - The key that stores the second string.
//	opts - The [LCSIdxOptions] type.
//
// Command Response:
//
//	A Map containing the indices of the longest common subsequence between the 2 strings
//	and the total length of all the longest common subsequences. The resulting map contains
//	two keys, "matches" and "len":
//	  - "len" is mapped to the total length of the all longest common subsequences between
//	     the 2 strings.
//	  - "matches" is mapped to a array that stores pairs of indices that represent the location
//	     of the common subsequences in the strings held by key1 and key2.
//
// [valkey.io]: https://valkey.io/commands/lcs/
func (b *BaseBatch[T]) LCSWithOptions(key1 string, key2 string, opts options.LCSIdxOptions) *T {
	optArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("LCSWithOptions", err)
	}
	return b.addCmdAndConverter(C.LCS, append([]string{key1, key2}, optArgs...), reflect.Map, false, internal.ConvertLCSResult)
}

// Gets the value associated with the given key and deletes the key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to get and delete.
//
// Command Response:
//
//	If key exists, returns the value of the key and deletes the key.
//	If key does not exist, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/getdel/
func (b *BaseBatch[T]) GetDel(key string) *T {
	return b.addCmdAndTypeChecker(C.GetDel, []string{key}, reflect.String, true)
}

// Returns the value associated with field in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the hash.
//	field - The field in the hash stored at key to retrieve from the database.
//
// Command Response:
//
//	The value associated with field, or `nil` when field is not present in the hash or key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hget/
func (b *BaseBatch[T]) HGet(key string, field string) *T {
	return b.addCmdAndTypeChecker(C.HGet, []string{key, field}, reflect.String, true)
}

// Returns all fields and values of the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//
// Command Response:
//
//	A map of all fields and their values in the hash, or an empty map when key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hgetall/
func (b *BaseBatch[T]) HGetAll(key string) *T {
	return b.addCmdAndConverter(C.HGetAll, []string{key}, reflect.Map, false, internal.ConvertMapOf[string])
}

// Returns the values associated with the specified fields in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	fields - The fields in the hash stored at key to retrieve from the database.
//
// Command Response:
//
//	An array of [models.Result[string]] values associated with the given fields, in the same order as they are requested.
//	For every field that does not exist in the hash, a [models.CreateNilStringResult()] is returned.
//	If key does not exist, returns an empty string array.
//
// [valkey.io]: https://valkey.io/commands/hmget/
func (b *BaseBatch[T]) HMGet(key string, fields []string) *T {
	return b.addCmdAndConverter(
		C.HMGet,
		append([]string{key}, fields...),
		reflect.Slice,
		false,
		internal.ConvertArrayOfNilOr[string],
	)
}

// Sets the specified fields to their respective values in the hash stored at key.
// This command overwrites the values of specified fields that exist in the hash.
// If key doesn't exist, a new key holding a hash is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	values - A map of field-value pairs to set in the hash.
//
// Command Response:
//
//	The number of fields that were added or updated.
//
// [valkey.io]: https://valkey.io/commands/hset/
func (b *BaseBatch[T]) HSet(key string, values map[string]string) *T {
	return b.addCmdAndTypeChecker(C.HSet, utils.ConvertMapToKeyValueStringArray(key, values), reflect.Int64, false)
}

// Sets field in the hash stored at key to value, only if field does not yet exist.
// If key does not exist, a new key holding a hash is created.
// If field already exists, this operation has no effect.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the hash.
//	field - The field to set.
//	value - The value to set.
//
// Command Response:
//
//	`true` if field is a new field in the hash and value was set.
//	`false` if field already exists in the hash and no operation was performed.
//
// [valkey.io]: https://valkey.io/commands/hsetnx/
func (b *BaseBatch[T]) HSetNX(key string, field string, value string) *T {
	return b.addCmdAndTypeChecker(C.HSetNX, []string{key, field, value}, reflect.Bool, false)
}

// Removes the specified fields from the hash stored at key.
// Specified fields that do not exist within this hash are ignored.
// If key does not exist, it is treated as an empty hash and this command returns `0`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	fields - The fields to remove from the hash stored at key.
//
// Command Response:
//
//	The number of fields that were removed from the hash, not including specified but non-existing fields.
//
// [valkey.io]: https://valkey.io/commands/hdel/
func (b *BaseBatch[T]) HDel(key string, fields []string) *T {
	return b.addCmdAndTypeChecker(C.HDel, append([]string{key}, fields...), reflect.Int64, false)
}

// Returns the number of fields contained in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//
// Command Response:
//
//	The number of fields in the hash, or `0` when key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hlen/
func (b *BaseBatch[T]) HLen(key string) *T {
	return b.addCmdAndTypeChecker(C.HLen, []string{key}, reflect.Int64, false)
}

// Returns all values in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//
// Command Response:
//
//	A slice containing all the values in the hash, or an empty slice when key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hvals/
func (b *BaseBatch[T]) HVals(key string) *T {
	return b.addCmdAndConverter(C.HVals, []string{key}, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns if field is an existing field in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the hash.
//	field - The field to check in the hash stored at key.
//
// Command Response:
//
//	`true` if the hash contains the specified field.
//	`false` if the hash does not contain the field, or if the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hexists/
func (b *BaseBatch[T]) HExists(key string, field string) *T {
	return b.addCmdAndTypeChecker(C.HExists, []string{key, field}, reflect.Bool, false)
}

// Returns all field names in the hash stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//
// Command Response:
//
//	A slice containing all the field names in the hash, or an empty slice when key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hkeys/
func (b *BaseBatch[T]) HKeys(key string) *T {
	return b.addCmdAndConverter(C.HKeys, []string{key}, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the string length of the value associated with field in the hash stored at key.
// If the key or the field do not exist, `0` is returned.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the hash.
//	field - The field to get the string length of its value.
//
// Command Response:
//
//	The length of the string value associated with field, or `0` when field or key do not exist.
//
// [valkey.io]: https://valkey.io/commands/hstrlen/
func (b *BaseBatch[T]) HStrLen(key string, field string) *T {
	return b.addCmdAndTypeChecker(C.HStrlen, []string{key, field}, reflect.Int64, false)
}

// Increments the number stored at `field` in the hash stored at `key` by increment.
// By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
// If `field` or `key` does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//	field - The field in the hash stored at key to increment its value.
//	increment - The amount to increment.
//
// Command Response:
//
//	The value of `field` in the hash stored at `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/hincrby/
func (b *BaseBatch[T]) HIncrBy(key string, field string, increment int64) *T {
	return b.addCmdAndTypeChecker(C.HIncrBy, []string{key, field, utils.IntToString(increment)}, reflect.Int64, false)
}

// Increments the string representing a floating point number stored at `field` in the hash stored at `key` by increment.
// By using a negative increment value, the value stored at `field` in the hash stored at `key` is decremented.
// If `field` or `key` does not exist, it is set to `0` before performing the operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//	field - The field in the hash stored at key to increment its value.
//	increment - The amount to increment.
//
// Command Response:
//
//	The value of `field` in the hash stored at `key` after the increment.
//
// [valkey.io]: https://valkey.io/commands/hincrbyfloat/
func (b *BaseBatch[T]) HIncrByFloat(key string, field string, increment float64) *T {
	return b.addCmdAndTypeChecker(C.HIncrByFloat, []string{key, field, utils.FloatToString(increment)}, reflect.Float64, false)
}

// Iterates fields of Hash types and their associated values.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//	cursor - The cursor that points to the next iteration of results.
//	         A value of `"0"` indicates the start of the search.
//	         For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always a flattened series of string pairs, where the hash field names
//	are at even indices, and the hash field value are at odd indices.
//
// [valkey.io]: https://valkey.io/commands/hscan/
func (b *BaseBatch[T]) HScan(key string, cursor string) *T {
	return b.addCmdAndConverter(C.HScan, []string{key, cursor}, reflect.Slice, false, internal.ConvertScanResult)
}

// Iterates fields of Hash types and their associated values with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//	cursor - The cursor that points to the next iteration of results.
//	         A value of `"0"` indicates the start of the search.
//	         For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
//	options - The [options.HashScanOptions].
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always a flattened series of string pairs, where the hash field names
//	are at even indices, and the hash field value are at odd indices.
//
// [valkey.io]: https://valkey.io/commands/hscan/
func (b *BaseBatch[T]) HScanWithOptions(key string, cursor string, options options.HashScanOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("HScanWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.HScan,
		append([]string{key, cursor}, optionArgs...),
		reflect.Slice,
		false,
		internal.ConvertScanResult,
	)
}

// Returns a random field name from the hash value stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//
// Command Response:
//
//	A random field name from the hash stored at `key`, or `nil` when the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hrandfield/
func (b *BaseBatch[T]) HRandField(key string) *T {
	return b.addCmdAndTypeChecker(C.HRandField, []string{key}, reflect.String, true)
}

// Retrieves up to `count` random field names from the hash value stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//	count - The number of field names to return.
//
// Command Response:
//
//	An array of random field names from the hash stored at `key`,
//	or an empty array when the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/hrandfield/
func (b *BaseBatch[T]) HRandFieldWithCount(key string, count int64) *T {
	return b.addCmdAndConverter(
		C.HRandField,
		[]string{key, utils.IntToString(count)},
		reflect.Slice,
		false,
		internal.ConvertArrayOf[string],
	)
}

// Retrieves up to `count` random field names along with their values from the hash
// value stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the hash.
//	count - The number of field names to return.
//	  	If `count` is positive, returns unique elements.
//		If negative, allows for duplicates.
//
// Command Response:
//
//	A 2D array of `[field, value]` arrays, where `field` is a random
//	field name from the hash and `value` is the associated value of the field name.
//	If the hash does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/hrandfield/
func (b *BaseBatch[T]) HRandFieldWithCountWithValues(key string, count int64) *T {
	return b.addCmdAndConverter(
		C.HRandField,
		[]string{key, utils.IntToString(count), constants.WithValuesKeyword},
		reflect.Slice,
		false,
		internal.Convert2DArrayOfString,
	)
}

// Hash field expiration commands (Valkey 9.0+)

// Sets the value of one or more fields of a given hash key, and optionally set their expiration time or time-to-live
// (TTL).
// This command overwrites the values and expirations of specified fields that exist in the hash.
// If `key` doesn't exist, a new key holding a hash is created.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key              - The key of the hash.
//	fieldsAndValues  - A map of field-value pairs to set in the hash.
//	options          - Optional arguments for the command.
//
// Command Response:
//
//   - 1 if all fields were set successfully.
//   - 0 if no fields were set due to conditional restrictions.
//
// [valkey.io]: https://valkey.io/commands/hsetex/
func (b *BaseBatch[T]) HSetEx(key string, fieldsAndValues map[string]string, opts options.HSetExOptions) *T {
	args, err := internal.BuildHSetExArgs(key, fieldsAndValues, opts)
	if err != nil {
		return b.addError("HSetEx", err)
	}
	return b.addCmdAndTypeChecker(C.HSetEx, args, reflect.Int64, false)
}

// Gets the values of one or more fields of a given hash key and optionally sets their expiration time or time-to-live
// (TTL).
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key of the hash.
//	fields  - The fields in the hash stored at key to retrieve from the database.
//	options - Optional arguments for the command.
//
// Command Response:
//
//	An array of [models.Result[string]] values associated with the given fields, in the same order as they are requested.
//	- For every field that does not exist in the hash, a [models.CreateNilStringResult()] is returned.
//	- If key does not exist, returns an empty string array.
//
// [valkey.io]: https://valkey.io/commands/hgetex/
func (b *BaseBatch[T]) HGetEx(key string, fields []string, opts options.HGetExOptions) *T {
	args, err := internal.BuildHGetExArgs(key, fields, opts)
	if err != nil {
		return b.addError("HGetEx", err)
	}
	return b.addCmdAndConverter(C.HGetEx, args, reflect.Slice, false, internal.ConvertArrayOfNilOr[string])
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key. You must specify at least one
// field.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
// Field expirations will only be cleared by commands that delete or overwrite the contents of the hash fields, including HDEL
// and HSET commands. This means that all the operations that conceptually alter the value stored at a hash key's field without
// replacing it with a new one will leave the TTL untouched.
// You can clear the TTL of a specific field by specifying 0 for the `seconds` argument.
//
// Note:
//
//	Calling HEXPIRE/HPEXPIRE with a time in the past will result in the hash field being deleted immediately.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key        - The key of the hash.
//	expireTime - The expiration time as a duration.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Command Response:
//
//	An array of integers indicating the result for each field:
//		- -2: Field does not exist in the hash, or key does not exist.
//		- 0: The specified condition was not met.
//		- 1: The expiration time was applied.
//		- 2: When called with 0 seconds.
//
// [valkey.io]: https://valkey.io/commands/hexpire/
func (b *BaseBatch[T]) HExpire(key string, expireTime time.Duration, fields []string, opts options.HExpireOptions) *T {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, false)
	if err != nil {
		return b.addError("HExpire", err)
	}
	return b.addCmdAndConverter(C.HExpire, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key using an absolute Unix
// timestamp. A timestamp in the past will delete the field immediately.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key        - The key of the hash.
//	expireTime - The expiration time as a time.Time.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Command Response:
//
//	An array of integers indicating the result for each field:
//		- -2: Field does not exist in the hash, or hash is empty.
//		- 0: The specified condition was not met.
//		- 1: The expiration time was applied.
//		- 2: When called with 0 seconds or past Unix time.
//
// [valkey.io]: https://valkey.io/commands/hexpireat/
func (b *BaseBatch[T]) HExpireAt(key string, expireTime time.Time, fields []string, opts options.HExpireOptions) *T {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, false)
	if err != nil {
		return b.addError("HExpireAt", err)
	}
	return b.addCmdAndConverter(C.HExpireAt, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key        - The key of the hash.
//	expireTime - The expiration time as a duration.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Command Response:
//
//	An array of integers indicating the result for each field:
//		- -2: Field does not exist in the hash, or hash is empty.
//		- 0: The specified condition was not met.
//		- 1: The expiration time was applied.
//		- 2: When called with 0 milliseconds.
//
// [valkey.io]: https://valkey.io/commands/hpexpire/
func (b *BaseBatch[T]) HPExpire(key string, expireTime time.Duration, fields []string, opts options.HExpireOptions) *T {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, true)
	if err != nil {
		return b.addError("HPExpire", err)
	}
	return b.addCmdAndConverter(C.HPExpire, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Sets an expiration (TTL or time to live) on one or more fields of a given hash key using an absolute Unix
// timestamp. A timestamp in the past will delete the field immediately.
// Field(s) will automatically be deleted from the hash key when their TTLs expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key        - The key of the hash.
//	expireTime - The expiration time as a time.Time.
//	fields     - The fields to set expiration for.
//	options    - Optional arguments for the command, see [options.HExpireOptions].
//
// Command Response:
//
//	An array of integers indicating the result for each field:
//		- -2: Field does not exist in the hash, or hash is empty.
//		- 0: The specified condition was not met.
//		- 1: The expiration time was applied.
//		- 2: When called with 0 milliseconds or past Unix time.
//
// [valkey.io]: https://valkey.io/commands/hpexpireat/
func (b *BaseBatch[T]) HPExpireAt(key string, expireTime time.Time, fields []string, opts options.HExpireOptions) *T {
	args, err := internal.BuildHExpireArgs(key, expireTime, fields, opts, true)
	if err != nil {
		return b.addError("HPExpireAt", err)
	}
	return b.addCmdAndConverter(C.HPExpireAt, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Removes the existing expiration on a hash key's field(s), turning the field(s) from volatile (a field with
// expiration set) to persistent (a field that will never expire as no TTL (time to live) is associated).
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	fields - The fields to remove expiration from.
//
// Command Response:
//
//	An array of integers indicating the result for each field:
//		- -2: Field does not exist in the hash, or hash does not exist.
//		- -1: Field exists but has no expiration.
//		- 1: The expiration was successfully removed from the field.
//
// [valkey.io]: https://valkey.io/commands/hpersist/
func (b *BaseBatch[T]) HPersist(key string, fields []string) *T {
	args, err := internal.BuildHPersistArgs(key, fields)
	if err != nil {
		return b.addError("HPersist", err)
	}
	return b.addCmdAndConverter(C.HPersist, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Returns the remaining TTL (time to live) of a hash key's field(s) that have a set expiration.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	fields - The fields to get TTL for.
//
// Command Response:
//
//	An array of integers indicating the TTL for each field in seconds:
//	- Positive number: remaining TTL.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/httl/
func (b *BaseBatch[T]) HTtl(key string, fields []string) *T {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return b.addError("HTtl", err)
	}
	return b.addCmdAndConverter(C.HTtl, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Returns the remaining TTL (time to live) of a hash key's field(s) that have a set expiration, in milliseconds.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	fields - The fields to get TTL for.
//
// Command Response:
//
//	An array of integers indicating the TTL for each field in milliseconds:
//	- Positive number: remaining TTL.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/hpttl/
func (b *BaseBatch[T]) HPTtl(key string, fields []string) *T {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return b.addError("HPTtl", err)
	}
	return b.addCmdAndConverter(C.HPTtl, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Returns the absolute Unix timestamp in seconds since Unix epoch at which the given key's field(s) will expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	fields - The fields to get expiration time for.
//
// Command Response:
//
//	An array of integers indicating the expiration timestamp for each field in seconds:
//	- Positive number: expiration timestamp.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/hexpiretime/
func (b *BaseBatch[T]) HExpireTime(key string, fields []string) *T {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return b.addError("HExpireTime", err)
	}
	return b.addCmdAndConverter(C.HExpireTime, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Returns the absolute Unix timestamp in milliseconds since Unix epoch at which the given key's field(s) will
// expire.
//
// Since:
//
//	Valkey 9.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the hash.
//	fields - The fields to get expiration time for.
//
// Command Response:
//
//	An array of integers indicating the expiration timestamp for each field in milliseconds:
//	- Positive number: expiration timestamp.
//	- -1: field exists but has no expiration.
//	- -2: field doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/hpexpiretime/
func (b *BaseBatch[T]) HPExpireTime(key string, fields []string) *T {
	args, err := internal.BuildHTTLAndExpireTimeArgs(key, fields)
	if err != nil {
		return b.addError("HPExpireTime", err)
	}
	return b.addCmdAndConverter(C.HPExpireTime, args, reflect.Slice, false, internal.ConvertArrayOf[int64])
}

// Inserts all the specified values at the head of the list stored at key. elements are inserted one after the other to the
// head of the list, from the leftmost element to the rightmost element. If key does not exist, it is created as an empty
// list before performing the push operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key      - The key of the list.
//	elements - The elements to insert at the head of the list stored at key.
//
// Command Response:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/lpush/
func (b *BaseBatch[T]) LPush(key string, elements []string) *T {
	return b.addCmdAndTypeChecker(C.LPush, append([]string{key}, elements...), reflect.Int64, false)
}

// Removes and returns the first elements of the list stored at key. The command pops a single element from the beginning
// of the list.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list.
//
// Command Response:
//
//	The value of the first element, or `nil` if key does not exist.
//
// [valkey.io]: https://valkey.io/commands/lpop/
func (b *BaseBatch[T]) LPop(key string) *T {
	return b.addCmdAndTypeChecker(C.LPop, []string{key}, reflect.String, true)
}

// Removes and returns up to `count` elements of the list stored at key, depending on the list's length.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the list.
//	count - The count of the elements to pop from the list.
//
// Command Response:
//
//	An array of the popped elements will be returned depending on the list's length.
//	If key does not exist, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/lpop/
func (b *BaseBatch[T]) LPopCount(key string, count int64) *T {
	return b.addCmdAndConverter(
		C.LPop,
		[]string{key, utils.IntToString(count)},
		reflect.Slice,
		true,
		internal.ConvertArrayOf[string],
	)
}

// Returns the index of the first occurrence of element inside the list specified by key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The name of the list.
//	element - The value to search for within the list.
//
// Command Response:
//
//	The index of the first occurrence of element, or `nil` if element is not in the list.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (b *BaseBatch[T]) LPos(key string, element string) *T {
	return b.addCmdAndTypeChecker(C.LPos, []string{key, element}, reflect.Int64, true)
}

// Returns the index of an occurrence of element within a list based on the given options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The name of the list.
//	element - The value to search for within the list.
//	options - The LPos options.
//
// Command Response:
//
//	The index of element, or `nil` if element is not in the list.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (b *BaseBatch[T]) LPosWithOptions(key string, element string, options options.LPosOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("LPosWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.LPos, append([]string{key, element}, optionArgs...), reflect.Int64, true)
}

// Returns an array of indices of matching elements within a list.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The name of the list.
//	element - The value to search for within the list.
//	count   - The number of matches wanted.
//
// Command Response:
//
//	An array that holds the indices of the matching elements within the list.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (b *BaseBatch[T]) LPosCount(key string, element string, count int64) *T {
	return b.addCmdAndConverter(
		C.LPos,
		[]string{key, element, constants.CountKeyword, utils.IntToString(count)},
		reflect.Slice,
		false,
		internal.ConvertArrayOf[int64],
	)
}

// Returns an array of indices of matching elements within a list based on the given options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The name of the list.
//	element - The value to search for within the list.
//	count   - The number of matches wanted.
//	opts    - The LPos options.
//
// Command Response:
//
//	An array that holds the indices of the matching elements within the list.
//	If no match is found, an empty array is returned.
//
// [valkey.io]: https://valkey.io/commands/lpos/
func (b *BaseBatch[T]) LPosCountWithOptions(key string, element string, count int64, opts options.LPosOptions) *T {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("LPosCountWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.LPos,
		append([]string{key, element, constants.CountKeyword, utils.IntToString(count)}, optionArgs...),
		reflect.Slice,
		false,
		internal.ConvertArrayOf[int64],
	)
}

// Inserts all the specified values at the tail of the list stored at key.
// elements are inserted one after the other to the tail of the list, from the leftmost element to the rightmost element.
// If key does not exist, it is created as an empty list before performing the push operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key      - The key of the list.
//	elements - The elements to insert at the tail of the list stored at key.
//
// Command Response:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/rpush/
func (b *BaseBatch[T]) RPush(key string, elements []string) *T {
	return b.addCmdAndTypeChecker(C.RPush, append([]string{key}, elements...), reflect.Int64, false)
}

// Adds specified members to the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key where members will be added to its set.
//	members - A list of members to add to the set stored at key.
//
// Command Response:
//
//	The number of members that were added to the set, excluding members already present.
//
// [valkey.io]: https://valkey.io/commands/sadd/
func (b *BaseBatch[T]) SAdd(key string, members []string) *T {
	return b.addCmdAndTypeChecker(C.SAdd, append([]string{key}, members...), reflect.Int64, false)
}

// Removes specified members from the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key from which members will be removed.
//	members - A list of members to remove from the set stored at key.
//
// Command Response:
//
//	The number of members that were removed from the set, excluding non-existing members.
//
// [valkey.io]: https://valkey.io/commands/srem/
func (b *BaseBatch[T]) SRem(key string, members []string) *T {
	return b.addCmdAndTypeChecker(C.SRem, append([]string{key}, members...), reflect.Int64, false)
}

// Stores the members of the union of all given sets specified by `keys` into a new set at `destination`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The key of the destination set.
//	keys - The keys from which to retrieve the set members.
//
// Command Response:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/sunionstore/
func (b *BaseBatch[T]) SUnionStore(destination string, keys []string) *T {
	return b.addCmdAndTypeChecker(C.SUnionStore, append([]string{destination}, keys...), reflect.Int64, false)
}

// Retrieves all the members of the set value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key from which to retrieve the set members.
//
// Command Response:
//
//	A `map[string]struct{}` containing all members of the set.
//	Returns an empty collection if key does not exist.
//
// [valkey.io]: https://valkey.io/commands/smembers/
func (b *BaseBatch[T]) SMembers(key string) *T {
	return b.addCmdAndTypeChecker(C.SMembers, []string{key}, reflect.Map, false)
}

// Retrieves the set cardinality (number of elements) of the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key from which to retrieve the number of set members.
//
// Command Response:
//
//	The cardinality (number of elements) of the set, or `0` if the key does not exist.
//
// [valkey.io]: https://valkey.io/commands/scard/
func (b *BaseBatch[T]) SCard(key string) *T {
	return b.addCmdAndTypeChecker(C.SCard, []string{key}, reflect.Int64, false)
}

// Returns if member is a member of the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key    - The key of the set.
//	member - The member to check for existence in the set.
//
// Command Response:
//
//	`true` if the member exists in the set, `false` otherwise.
//	If key doesn't exist, it is treated as an empty set and returns `false`.
//
// [valkey.io]: https://valkey.io/commands/sismember/
func (b *BaseBatch[T]) SIsMember(key string, member string) *T {
	return b.addCmdAndTypeChecker(C.SIsMember, []string{key, member}, reflect.Bool, false)
}

// Computes the difference between the first set and all the successive sets in keys.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sets to diff.
//
// Command Response:
//
//	A `map[string]struct{}` representing the difference between the sets.
//	If a key does not exist, it is treated as an empty set.
//
// [valkey.io]: https://valkey.io/commands/sdiff/
func (b *BaseBatch[T]) SDiff(keys []string) *T {
	return b.addCmdAndTypeChecker(C.SDiff, keys, reflect.Map, false)
}

// Stores the difference between the first set and all the successive sets in `keys`
// into a new set at `destination`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The key of the destination set.
//	keys        - The keys of the sets to diff.
//
// Command Response:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/sdiffstore/
func (b *BaseBatch[T]) SDiffStore(destination string, keys []string) *T {
	return b.addCmdAndTypeChecker(C.SDiffStore, append([]string{destination}, keys...), reflect.Int64, false)
}

// Gets the intersection of all the given sets.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sets to intersect.
//
// Command Response:
//
//	A `map[string]struct{}` containing members which are present in all given sets.
//	If one or more sets do not exist, an empty collection will be returned.
//
// [valkey.io]: https://valkey.io/commands/sinter/
func (b *BaseBatch[T]) SInter(keys []string) *T {
	return b.addCmdAndTypeChecker(C.SInter, keys, reflect.Map, false)
}

// Stores the members of the intersection of all given sets specified by `keys` into a new set at `destination`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The key of the destination set.
//	keys - The keys from which to retrieve the set members.
//
// Command Response:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/sinterstore/
func (b *BaseBatch[T]) SInterStore(destination string, keys []string) *T {
	return b.addCmdAndTypeChecker(C.SInterStore, append([]string{destination}, keys...), reflect.Int64, false)
}

// Gets the cardinality of the intersection of all the given sets.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sets to intersect.
//
// Command Response:
//
//	The cardinality of the intersection result. If one or more sets do not exist, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/sintercard/
func (b *BaseBatch[T]) SInterCard(keys []string) *T {
	return b.addCmdAndTypeChecker(C.SInterCard, append([]string{strconv.Itoa(len(keys))}, keys...), reflect.Int64, false)
}

// Gets the cardinality of the intersection of all the given sets, up to the specified limit.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys  - The keys of the sets to intersect.
//	limit - The limit for the intersection cardinality value.
//
// Command Response:
//
//	The cardinality of the intersection result, or the limit if reached.
//	If one or more sets do not exist, `0` is returned.
//	If the intersection cardinality reaches 'limit' partway through the computation, returns 'limit' as the cardinality.
//
// [valkey.io]: https://valkey.io/commands/sintercard/
func (b *BaseBatch[T]) SInterCardLimit(keys []string, limit int64) *T {
	args := utils.Concat(
		[]string{utils.IntToString(int64(len(keys)))},
		keys,
		[]string{constants.LimitKeyword, utils.IntToString(limit)},
	)
	return b.addCmdAndTypeChecker(C.SInterCard, args, reflect.Int64, false)
}

// Returns a random element from the set value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key from which to retrieve the set member.
//
// Command Response:
//
//	A random element from the set.
//	Returns `nil` if key does not exist.
//
// [valkey.io]: https://valkey.io/commands/srandmember/
func (b *BaseBatch[T]) SRandMember(key string) *T {
	return b.addCmdAndTypeChecker(C.SRandMember, []string{key}, reflect.String, true)
}

// SRandMemberCount returns multiple random members from the set value stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key from which to retrieve the set members.
//	count - The number of members to return.
//	       If count is positive, returns unique elements (no repetition) up to count or the set size, whichever is smaller.
//	       If count is negative, returns elements with possible repetition (the same element may be returned multiple times),
//	       and the number of returned elements is the absolute value of count.
//
// Command Response:
//
//	An array of random elements from the set.
//	When count is positive, the returned elements are unique (no repetitions).
//	When count is negative, the returned elements may contain duplicates.
//	If the set does not exist or is empty, an empty array is returned.
//
// [valkey.io]: https://valkey.io/commands/srandmember/
func (b *BaseBatch[T]) SRandMemberCount(key string, count int64) *T {
	return b.addCmdAndConverter(
		C.SRandMember,
		[]string{key, utils.IntToString(count)},
		reflect.Slice,
		false,
		internal.ConvertArrayOf[string],
	)
}

// Removes and returns one random member from the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//
// Command Response:
//
//	The value of the popped member.
//	Returns `nil` if key does not exist.
//
// [valkey.io]: https://valkey.io/commands/spop/
func (b *BaseBatch[T]) SPop(key string) *T {
	return b.addCmdAndTypeChecker(C.SPop, []string{key}, reflect.String, true)
}

// SPopCount removes and returns up to count random members from the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//	count - The number of members to return.
//		If count is positive, returns unique elements.
//		If count is larger than the set's cardinality, returns the entire set.
//
// Command Response:
//
//	A `map[string]struct{}` of popped elements.
//	If key does not exist, an empty collection will be returned.
//
// [valkey.io]: https://valkey.io/commands/spop/
func (b *BaseBatch[T]) SPopCount(key string, count int64) *T {
	return b.addCmdAndTypeChecker(C.SPop, []string{key, utils.IntToString(count)}, reflect.Map, false)
}

// Returns whether each member is a member of the set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//	members - The members to check.
//
// Command Response:
//
//	A boolean array containing whether each member is a member of the set stored at key.
//
// [valkey.io]: https://valkey.io/commands/smismember/
func (b *BaseBatch[T]) SMIsMember(key string, members []string) *T {
	return b.addCmdAndConverter(
		C.SMIsMember,
		append([]string{key}, members...),
		reflect.Slice,
		false,
		internal.ConvertArrayOf[bool],
	)
}

// Gets the union of all the given sets.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sets.
//
// Command Response:
//
//	A `map[string]struct{}` containing members which are present in at least one of the given sets.
//	If none of the sets exist, an empty collection will be returned.
//
// [valkey.io]: https://valkey.io/commands/sunion/
func (b *BaseBatch[T]) SUnion(keys []string) *T {
	return b.addCmdAndTypeChecker(C.SUnion, keys, reflect.Map, false)
}

// Iterates incrementally over a set.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//	cursor - The cursor that points to the next iteration of results.
//	         A value of `"0"` indicates the start of the search.
//	         For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the set held in `key`.
//
// [valkey.io]: https://valkey.io/commands/sscan/
func (b *BaseBatch[T]) SScan(key string, cursor string) *T {
	return b.addCmdAndConverter(C.SScan, []string{key, cursor}, reflect.Slice, false, internal.ConvertScanResult)
}

// Iterates incrementally over a set with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//	cursor - The cursor that points to the next iteration of results.
//	         A value of `"0"` indicates the start of the search.
//	         For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
//	options - [options.BaseScanOptions]
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the set held in `key`.
//
// [valkey.io]: https://valkey.io/commands/sscan/
func (b *BaseBatch[T]) SScanWithOptions(key string, cursor string, options options.BaseScanOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("SScanWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.SScan,
		append([]string{key, cursor}, optionArgs...),
		reflect.Slice,
		false,
		internal.ConvertScanResult,
	)
}

// Moves `member` from the set at `source` to the set at `destination`, removing it from the source set.
// Creates a new destination set if needed. The operation is atomic.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	source - The key of the set to remove the element from.
//	destination - The key of the set to add the element to.
//	member - The set element to move.
//
// Command Response:
//
//	`true` on success, or `false` if the source set does not exist or the element is not a member of the source set.
//
// [valkey.io]: https://valkey.io/commands/smove/
func (b *BaseBatch[T]) SMove(source string, destination string, member string) *T {
	return b.addCmdAndTypeChecker(C.SMove, []string{source, destination, member}, reflect.Bool, false)
}

// Returns the specified elements of the list stored at key.
// The offsets start and end are zero-based indexes, with `0` being the first element of the list, `1` being the next element
// and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list, with `-1` being
// the last element of the list, `-2` being the penultimate, and so on.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the list.
//	start - The starting point of the range.
//	end   - The end of the range.
//
// Command Response:
//
//	Array of strings in the specified range.
//	If `start` exceeds the end of the list, or if `start` is greater than `end`, an empty array will be returned.
//	If `end` exceeds the actual end of the list, the range will stop at the actual end of the list.
//	If key does not exist an empty array will be returned.
//
// [valkey.io]: https://valkey.io/commands/lrange/
func (b *BaseBatch[T]) LRange(key string, start int64, end int64) *T {
	return b.addCmdAndConverter(
		C.LRange,
		[]string{key, utils.IntToString(start), utils.IntToString(end)},
		reflect.Slice,
		false,
		internal.ConvertArrayOf[string],
	)
}

// Returns the element at index from the list stored at key.
// The index is zero-based, so `0` means the first element, `1` the second element and so on. Negative indices can be used to
// designate elements starting at the tail of the list. Here, `-1` means the last element, `-2` means the penultimate and so
// forth.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the list.
//	index - The index of the element in the list to retrieve.
//
// Command Response:
//
//	The element at index in the list stored at key.
//	If index is out of range or if key does not exist, `nil` is returned.
//
// [valkey.io]: https://valkey.io/commands/lindex/
func (b *BaseBatch[T]) LIndex(key string, index int64) *T {
	return b.addCmdAndTypeChecker(C.LIndex, []string{key, utils.IntToString(index)}, reflect.String, true)
}

// Trims an existing list so that it will contain only the specified range of elements.
// The offsets start and end are zero-based indexes, with `0` being the first element of the list, `1` being the next element
// and so on. These offsets can also be negative numbers indicating offsets starting at the end of the list, with `-1` being
// the last element of the list, `-2` being the penultimate, and so on.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the list.
//	start - The starting point of the range.
//	end   - The end of the range.
//
// Command Response:
//
//	Always "OK".
//	If `start` exceeds the end of the list, or if `start` is greater than `end`, the list is emptied
//	and the key is removed.
//	If `end` exceeds the actual end of the list, it will be treated like the last element of the list.
//	If key does not exist, `"OK"` will be returned without changes to the database.
//
// [valkey.io]: https://valkey.io/commands/ltrim/
func (b *BaseBatch[T]) LTrim(key string, start int64, end int64) *T {
	return b.addCmdAndTypeChecker(
		C.LTrim,
		[]string{key, utils.IntToString(start), utils.IntToString(end)},
		reflect.String,
		false,
	)
}

// Returns the length of the list stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list.
//
// Command Response:
//
//	The length of the list at `key`.
//	If `key` does not exist, it is interpreted as an empty list and `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/llen/
func (b *BaseBatch[T]) LLen(key string) *T {
	return b.addCmdAndTypeChecker(C.LLen, []string{key}, reflect.Int64, false)
}

// Removes the first `count` occurrences of elements equal to `element` from the list stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key of the list.
//	count   - The count of the occurrences of elements equal to element to remove.
//			  If count is positive: Removes elements equal to element moving from head to tail.
//			  If count is negative: Removes elements equal to element moving from tail to head.
//			  If count is 0 or count is greater than the occurrences of elements equal to element,
//			  it removes all elements equal to element.
//	element - The element to remove from the list.
//
// Command Response:
//
//	The number of the removed elements.
//	If key does not exist, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/lrem/
func (b *BaseBatch[T]) LRem(key string, count int64, element string) *T {
	return b.addCmdAndTypeChecker(C.LRem, []string{key, utils.IntToString(count), element}, reflect.Int64, false)
}

// Removes and returns the last elements of the list stored at key.
// The command pops a single element from the end of the list.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list.
//
// Command Response:
//
//	The value of the last element.
//	If key does not exist, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/rpop/
func (b *BaseBatch[T]) RPop(key string) *T {
	return b.addCmdAndTypeChecker(C.RPop, []string{key}, reflect.String, true)
}

// Removes and returns up to `count` elements from the list stored at key, depending on the list's length.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the list.
//	count - The count of the elements to pop from the list.
//
// Command Response:
//
//	An array of popped elements will be returned depending on the list's length.
//	If key does not exist, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/rpop/
func (b *BaseBatch[T]) RPopCount(key string, count int64) *T {
	return b.addCmdAndConverter(
		C.RPop,
		[]string{key, utils.IntToString(count)},
		reflect.Slice,
		true,
		internal.ConvertArrayOf[string],
	)
}

// Inserts element in the list at key either before or after the pivot.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key            - The key of the list.
//	insertPosition - The relative position to insert into - either options.Before or options.After the pivot.
//	pivot          - An element of the list.
//	element        - The new element to insert.
//
// Command Response:
//
//	The list length after a successful insert operation.
//	If the key doesn't exist returns `-1`.
//	If the pivot wasn't found, returns `0`.
//
// [valkey.io]: https://valkey.io/commands/linsert/
func (b *BaseBatch[T]) LInsert(key string, insertPosition constants.InsertPosition, pivot string, element string) *T {
	insertPositionStr, err := insertPosition.ToString()
	if err != nil {
		return b.addError("LInsert", err)
	}
	return b.addCmdAndTypeChecker(C.LInsert, []string{key, insertPositionStr, pivot, element}, reflect.Int64, false)
}

// Pops an element from the head of the first list that is non-empty, with the given keys being checked in the order that
// they are given.
// Blocks the connection when there are no elements to pop from any of the given lists.
//
// Note:
//
// BLPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys        - The keys of the lists to pop from.
//	timeout     - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//
// Command Response:
//
//	A two-element array containing the key from which the element was popped and the value of the popped
//	element, formatted as `[key, value]`.
//	If no element could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/blpop/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BLPop(keys []string, timeout time.Duration) *T {
	return b.addCmdAndConverter(
		C.BLPop,
		append(keys, utils.FloatToString(timeout.Seconds())),
		reflect.Slice,
		true,
		internal.ConvertArrayOf[string],
	)
}

// Pops an element from the tail of the first list that is non-empty, with the given keys being checked in the order that
// they are given.
// Blocks the connection when there are no elements to pop from any of the given lists.
//
// Note:
//
// BLPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys        - The keys of the lists to pop from.
//	timeout     - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//
// Command Response:
//
//	A two-element array containing the key from which the element was popped and the value of the popped
//	element, formatted as `[key, value]`.
//	If no element could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/brpop/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BRPop(keys []string, timeout time.Duration) *T {
	return b.addCmdAndConverter(
		C.BRPop,
		append(keys, utils.FloatToString(timeout.Seconds())),
		reflect.Slice,
		true,
		internal.ConvertArrayOf[string],
	)
}

// Inserts all the specified values at the tail of the list stored at `key`, only if key exists and holds a list. If key is
// not a list, this performs no operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key      - The key of the list.
//	elements - The elements to insert at the tail of the list stored at key.
//
// Command Response:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/rpushx/
func (b *BaseBatch[T]) RPushX(key string, elements []string) *T {
	return b.addCmdAndTypeChecker(C.RPushX, append([]string{key}, elements...), reflect.Int64, false)
}

// Inserts all the specified values at the head of the list stored at `key`, only if key exists and holds a list. If key is
// not a list, this performs no operation.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key      - The key of the list.
//	elements - The elements to insert at the head of the list stored at key.
//
// Command Response:
//
//	The length of the list after the push operation.
//
// [valkey.io]: https://valkey.io/commands/lpushx/
func (b *BaseBatch[T]) LPushX(key string, elements []string) *T {
	return b.addCmdAndTypeChecker(C.LPushX, append([]string{key}, elements...), reflect.Int64, false)
}

// Pops one element from the first non-empty list from the provided keys.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [constants.ListDirection].
//
// Command Response:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no elements could be popped, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/lmpop/
func (b *BaseBatch[T]) LMPop(keys []string, listDirection constants.ListDirection) *T {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return b.addError("LMPop", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-2 {
		return b.addError("LMPop", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 2 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+2)
	args = append(args, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr)
	return b.addCmdAndConverter(C.LMPop, args, reflect.Map, true, internal.ConvertKeyValuesArrayOrNilForBatch)
}

// Pops one or more elements from the first non-empty list from the provided keys.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [constants.ListDirection].
//	count         - The maximum number of popped elements.
//
// Command Response:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no elements could be popped, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/lmpop/
func (b *BaseBatch[T]) LMPopCount(keys []string, listDirection constants.ListDirection, count int64) *T {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return b.addError("LMPopCount", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-4 {
		return b.addError("LMPopCount", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 4 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+4)
	args = append(args, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr, constants.CountKeyword, utils.IntToString(count))
	return b.addCmdAndConverter(C.LMPop, args, reflect.Map, true, internal.ConvertKeyValuesArrayOrNilForBatch)
}

// Blocks the connection until it pops one element from the first non-empty list from the provided keys.
// BLMPop is the blocking variant of [glide.LMPop].
//
// Note:
//
// BLMPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [constants.ListDirection].
//	timeout       - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//
// Command Response:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no member could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/blmpop/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BLMPop(keys []string, listDirection constants.ListDirection, timeout time.Duration) *T {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return b.addError("BLMPop", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-3 {
		return b.addError("BLMPop", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 3 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+3)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr)
	return b.addCmdAndConverter(C.BLMPop, args, reflect.Map, true, internal.ConvertKeyValuesArrayOrNilForBatch)
}

// Blocks the connection until it pops one or more elements from the first non-empty list.
// BLMPopCount is the blocking variant of [BaseBatch.LMPopCount].
//
// Note:
//
// BLMPop is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys          - An array of keys to lists.
//	listDirection - The direction based on which elements are popped from - see [constants.ListDirection].
//	count         - The maximum number of popped elements.
//	timeout       - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//
// Command Response:
//
//	A slice of [models.KeyValues], each containing a key name and an array of popped elements.
//	If no member could be popped and the timeout expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/blmpop/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BLMPopCount(
	keys []string,
	listDirection constants.ListDirection,
	count int64,
	timeout time.Duration,
) *T {
	listDirectionStr, err := listDirection.ToString()
	if err != nil {
		return b.addError("BLMPopCount", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-5 {
		return b.addError("BLMPopCount", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 5 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+5)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, listDirectionStr, constants.CountKeyword, utils.IntToString(count))
	return b.addCmdAndConverter(C.BLMPop, args, reflect.Map, true, internal.ConvertKeyValuesArrayOrNilForBatch)
}

// Sets the list element at index to element.
// The index is zero-based, so `0` means the first element, `1` the second element and so on. Negative indices can be used to
// designate elements starting at the tail of the list. Here, `-1` means the last element, `-2` means the penultimate and so
// forth.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key of the list.
//	index   - The index of the element in the list to be set.
//	element - The element to be set.
//
// Command Response:
//
//	"OK".
//
// [valkey.io]: https://valkey.io/commands/lset/
func (b *BaseBatch[T]) LSet(key string, index int64, element string) *T {
	return b.addCmdAndTypeChecker(C.LSet, []string{key, utils.IntToString(index), element}, reflect.String, false)
}

// Atomically pops and removes the left/right-most element to the list stored at source depending on `whereFrom`, and pushes
// the element at the first/last element of the list stored at destination depending on `whereTo`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	source      - The key to the source list.
//	destination - The key to the destination list.
//	wherefrom   - The ListDirection the element should be removed from.
//	whereto     - The ListDirection the element should be added to.
//
// Command Response:
//
//	The popped element or `nil` if source does not exist.
//
// [valkey.io]: https://valkey.io/commands/lmove/
func (b *BaseBatch[T]) LMove(
	source string,
	destination string,
	whereFrom constants.ListDirection,
	whereTo constants.ListDirection,
) *T {
	whereFromStr, err := whereFrom.ToString()
	if err != nil {
		return b.addError("LMove", err)
	}
	whereToStr, err := whereTo.ToString()
	if err != nil {
		return b.addError("LMove", err)
	}
	return b.addCmdAndTypeChecker(C.LMove, []string{source, destination, whereFromStr, whereToStr}, reflect.String, true)
}

// Blocks the connection until it pops atomically and removes the left/right-most element to the list stored at source
// depending on whereFrom, and pushes the element at the first/last element of the list stored at <destination depending on
// wherefrom.
// BLMove is the blocking variant of [BaseBatch.LMove].
//
// Note:
//
// BLMove is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	source      - The key to the source list.
//	destination - The key to the destination list.
//	wherefrom   - The ListDirection the element should be removed from.
//	whereto     - The ListDirection the element should be added to.
//	timeout     - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//
// Command Response:
//
//	The popped element or `nil` if source does not exist or if the operation timed-out.
//
// [valkey.io]: https://valkey.io/commands/blmove/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BLMove(
	source string,
	destination string,
	whereFrom constants.ListDirection,
	whereTo constants.ListDirection,
	timeout time.Duration,
) *T {
	whereFromStr, err := whereFrom.ToString()
	if err != nil {
		return b.addError("BLMove", err)
	}
	whereToStr, err := whereTo.ToString()
	if err != nil {
		return b.addError("BLMove", err)
	}
	return b.addCmdAndTypeChecker(
		C.BLMove,
		[]string{source, destination, whereFromStr, whereToStr, utils.FloatToString(timeout.Seconds())},
		reflect.String,
		true,
	)
}

// Removes the specified keys from the database. A key is ignored if it does not exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - One or more keys to delete.
//
// Command Response:
//
//	Returns the number of keys that were removed.
//
// [valkey.io]: https://valkey.io/commands/del/
func (b *BaseBatch[T]) Del(keys []string) *T {
	return b.addCmdAndTypeChecker(C.Del, keys, reflect.Int64, false)
}

// Returns the number of keys that exist in the database.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - One or more keys to check if they exist.
//
// Command Response:
//
//	Returns the number of existing keys.
//
// [valkey.io]: https://valkey.io/commands/exists/
func (b *BaseBatch[T]) Exists(keys []string) *T {
	return b.addCmdAndTypeChecker(C.Exists, keys, reflect.Int64, false)
}

// Sets a timeout on key. After the timeout has expired, the key will automatically be deleted.
//
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to expire.
//	expireTime - Duration for the key to expire
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expire/
func (b *BaseBatch[T]) Expire(key string, expireTime time.Duration) *T {
	return b.addCmdAndTypeChecker(C.Expire, []string{key, utils.FloatToString(expireTime.Seconds())}, reflect.Bool, false)
}

// Sets a timeout on key. After the timeout has expired, the key will automatically be deleted.
//
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to expire.
//	expireTime - Duration for the key to expire.
//	expireCondition - The option to set expiry, see [constants.ExpireCondition].
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expire/
func (b *BaseBatch[T]) ExpireWithOptions(key string, expireTime time.Duration, expireCondition constants.ExpireCondition) *T {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return b.addError("ExpireWithOptions", err)
	}
	return b.addCmdAndTypeChecker(
		C.Expire,
		[]string{key, utils.FloatToString(expireTime.Seconds()), expireConditionStr},
		reflect.Bool,
		false,
	)
}

// Sets a timeout on key using an absolute Unix timestamp. It takes an absolute Unix timestamp (seconds since January 1, 1970)
// instead of specifying the number of seconds. A timestamp in the past will delete the key immediately. After the timeout has
// expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to expire.
//	expireTime - The timestamp for expiry.
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expireat/
func (b *BaseBatch[T]) ExpireAt(key string, expireTime time.Time) *T {
	return b.addCmdAndTypeChecker(C.ExpireAt, []string{key, utils.IntToString(expireTime.Unix())}, reflect.Bool, false)
}

// Sets a timeout on key using an absolute Unix timestamp. It takes an absolute Unix timestamp (seconds since January 1, 1970)
// instead of specifying the number of seconds. A timestamp in the past will delete the key immediately. After the timeout has
// expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to expire.
//	expireTime - The timestamp for expiry.
//	expireCondition - The option to set expiry - see [constants.ExpireCondition].
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/expireat/
func (b *BaseBatch[T]) ExpireAtWithOptions(
	key string,
	expireTime time.Time,
	expireCondition constants.ExpireCondition,
) *T {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return b.addError("ExpireAtWithOptions", err)
	}
	return b.addCmdAndTypeChecker(
		C.ExpireAt,
		[]string{key, utils.IntToString(expireTime.Unix()), expireConditionStr},
		reflect.Bool,
		false,
	)
}

// Sets a timeout on key in milliseconds. After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to set timeout on it.
//	expireTime - Duration for the key to expire.
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpire/
func (b *BaseBatch[T]) PExpire(key string, expireTime time.Duration) *T {
	return b.addCmdAndTypeChecker(C.PExpire, []string{key, utils.IntToString(expireTime.Milliseconds())}, reflect.Bool, false)
}

// Sets a timeout on key in milliseconds. After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// If expireTime is a non-positive number, the key will be deleted rather than expired.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to set timeout on it.
//	expireTime - Duration for the key to expire.
//	expireCondition - The option to set expiry, see [constants.ExpireCondition].
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpire/
func (b *BaseBatch[T]) PExpireWithOptions(key string, expireTime time.Duration, expireCondition constants.ExpireCondition) *T {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return b.addError("PExpireWithOptions", err)
	}
	return b.addCmdAndTypeChecker(
		C.PExpire,
		[]string{key, utils.IntToString(expireTime.Milliseconds()), expireConditionStr},
		reflect.Bool,
		false,
	)
}

// Sets a timeout on key. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of
// specifying the number of milliseconds. A timestamp in the past will delete the key immediately.
// After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to set timeout on it.
//	expireTime - The timestamp for expiry.
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpireat/
func (b *BaseBatch[T]) PExpireAt(key string, expireTime time.Time) *T {
	return b.addCmdAndTypeChecker(
		C.PExpireAt,
		[]string{key, utils.IntToString(expireTime.UnixMilli())},
		reflect.Bool,
		false,
	)
}

// Sets a timeout on key. It takes an absolute Unix timestamp (milliseconds since January 1, 1970) instead of
// specifying the number of milliseconds. A timestamp in the past will delete the key immediately.
// After the timeout has expired, the key will automatically be deleted.
// If key already has an existing expire set, the time to live is updated to the new value.
// The timeout will only be cleared by commands that delete or overwrite the contents of key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to set timeout on it.
//	expireTime - The timestamp for expiry.
//	expireCondition - The option to set expiry, see [constants.ExpireCondition].
//
// Command Response:
//
//	`true` if the timeout was set. `false` if the timeout was not set. e.g. key doesn't exist,
//	or operation skipped due to the provided arguments.
//
// [valkey.io]: https://valkey.io/commands/pexpireat/
func (b *BaseBatch[T]) PExpireAtWithOptions(
	key string,
	expireTime time.Time,
	expireCondition constants.ExpireCondition,
) *T {
	expireConditionStr, err := expireCondition.ToString()
	if err != nil {
		return b.addError("PExpireAtWithOptions", err)
	}
	return b.addCmdAndTypeChecker(
		C.PExpireAt,
		[]string{key, utils.IntToString(expireTime.UnixMilli()), expireConditionStr},
		reflect.Bool,
		false,
	)
}

// Returns the absolute Unix timestamp (since January 1, 1970) at which the given key will expire, in seconds.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to determine the expiration value of.
//
// Command Response:
//
//	The expiration Unix timestamp in seconds.
//	`-2` if key does not exist or `-1` is key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/expiretime/
func (b *BaseBatch[T]) ExpireTime(key string) *T {
	return b.addCmdAndTypeChecker(C.ExpireTime, []string{key}, reflect.Int64, false)
}

// Returns the absolute Unix timestamp (since January 1, 1970) at which the given key will expire, in milliseconds.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to determine the expiration value of.
//
// Command Response:
//
//	The expiration Unix timestamp in milliseconds.
//	`-2` if key does not exist or `-1` is key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/pexpiretime/
func (b *BaseBatch[T]) PExpireTime(key string) *T {
	return b.addCmdAndTypeChecker(C.PExpireTime, []string{key}, reflect.Int64, false)
}

// Returns the remaining time to live of key that has a timeout, in seconds.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to return its timeout.
//
// Command Response:
//
//	Returns TTL in seconds,
//	`-2` if key does not exist, or `-1` if key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/ttl/
func (b *BaseBatch[T]) TTL(key string) *T {
	return b.addCmdAndTypeChecker(C.TTL, []string{key}, reflect.Int64, false)
}

// Returns the remaining time to live of key that has a timeout, in milliseconds.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to return its timeout.
//
// Command Response:
//
//	Returns TTL in milliseconds,
//	`-2` if key does not exist, or `-1` if key exists but has no associated expiration.
//
// [valkey.io]: https://valkey.io/commands/pttl/
func (b *BaseBatch[T]) PTTL(key string) *T {
	return b.addCmdAndTypeChecker(C.PTTL, []string{key}, reflect.Int64, false)
}

// Adds all elements to the HyperLogLog data structure stored at the specified key.
// Creates a new structure if the key does not exist.
// When no elements are provided, and key exists and is a HyperLogLog, then no operation is performed.
// If key does not exist, then the HyperLogLog structure is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the HyperLogLog data structure to add elements into.
//	elements - An array of members to add to the HyperLogLog stored at key.
//
// Command Response:
//
//	If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
//	altered, then returns `true`. Otherwise, returns `false`.
//
// [valkey.io]: https://valkey.io/commands/pfadd/
func (b *BaseBatch[T]) PfAdd(key string, elements []string) *T {
	return b.addCmdAndTypeChecker(C.PfAdd, append([]string{key}, elements...), reflect.Bool, false)
}

// Estimates the cardinality of the data stored in a HyperLogLog structure for a single key or
// calculates the combined cardinality of multiple keys by merging their HyperLogLogs temporarily.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the HyperLogLog data structures to be analyzed.
//
// Command Response:
//
//	The approximated cardinality of given HyperLogLog data structures.
//	The cardinality of a key that does not exist is `0`.
//
// [valkey.io]: https://valkey.io/commands/pfcount/
func (b *BaseBatch[T]) PfCount(keys []string) *T {
	return b.addCmdAndTypeChecker(C.PfCount, keys, reflect.Int64, false)
}

// Merges multiple HyperLogLog values into a unique value.
// If the destination variable exists, it is treated as one of the source HyperLogLog data sets,
// otherwise a new HyperLogLog is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The key of the destination HyperLogLog where the merged data sets will be stored.
//	sourceKeys - An array of sourceKeys of the HyperLogLog structures to be merged.
//
// Command Response:
//
//	If the HyperLogLog values is successfully merged it returns "OK".
//
// [valkey.io]: https://valkey.io/commands/pfmerge/
func (b *BaseBatch[T]) PfMerge(destination string, sourceKeys []string) *T {
	return b.addCmdAndTypeChecker(C.PfMerge, append([]string{destination}, sourceKeys...), reflect.String, false)
}

// Unlinks (deletes) multiple keys from the database. A key is ignored if it does not exist.
// This command, similar to [BaseBatch.Del], however, this command does not block the server.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - One or more keys to unlink.
//
// Command Response:
//
//	Return the number of keys that were unlinked.
//
// [valkey.io]: https://valkey.io/commands/unlink/
func (b *BaseBatch[T]) Unlink(keys []string) *T {
	return b.addCmdAndTypeChecker(C.Unlink, keys, reflect.Int64, false)
}

// Type returns the string representation of the type of the value stored at key.
// The different types that can be returned are: `"string"`, `"list"`, `"set"`, `"zset"`, `"hash"` and `"stream"`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The `key` to check its type.
//
// Command Response:
//
//	If the `key` exists, the type of the stored value is returned. Otherwise, a `"none"` string is returned.
//
// [valkey.io]: https://valkey.io/commands/type/
func (b *BaseBatch[T]) Type(key string) *T {
	return b.addCmdAndTypeChecker(C.Type, []string{key}, reflect.String, false)
}

// Alters the last access time of a key(s). A key is ignored if it does not exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys to update last access time.
//
// Command Response:
//
//	The number of keys that were updated.
//
// [valkey.io]: https://valkey.io/commands/touch/
func (b *BaseBatch[T]) Touch(keys []string) *T {
	return b.addCmdAndTypeChecker(C.Touch, keys, reflect.Int64, false)
}

// Renames a `key` to `newKey`.
// If `newKey` already exists it is overwritten.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to rename.
//	newKey - The new name of the key.
//
// Command Response:
//
//	If the key was successfully renamed, return "OK". If key does not exist, an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/rename/
func (b *BaseBatch[T]) Rename(key string, newKey string) *T {
	return b.addCmdAndTypeChecker(C.Rename, []string{key, newKey}, reflect.String, false)
}

// Renames `key` to `newkey` if `newKey` does not yet exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to rename.
//	newKey - The new name of the key.
//
// Command Response:
//
//	`true` if `key` was renamed to `newKey`, `false` if `newKey` already exists.
//
// [valkey.io]: https://valkey.io/commands/renamenx/
func (b *BaseBatch[T]) RenameNX(key string, newKey string) *T {
	return b.addCmdAndTypeChecker(C.RenameNX, []string{key, newKey}, reflect.Bool, false)
}

// Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key      - The key of the stream.
//	values   - Field-value pairs to be added to the entry.
//
// Command Response:
//
//	The id of the added entry.
//
// [valkey.io]: https://valkey.io/commands/xadd/
func (b *BaseBatch[T]) XAdd(key string, values []models.FieldValue) *T {
	return b.XAddWithOptions(key, values, *options.NewXAddOptions())
}

// Adds an entry to the specified stream stored at `key`. If the `key` doesn't exist, the stream is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key      - The key of the stream.
//	values   - Field-value pairs to be added to the entry.
//	options  - Stream add options.
//
// Command Response:
//
//	The id of the added entry, or `nil` if [options.XAddOptions.MakeStream] is set to `false`
//	and no stream with the matching `key` exists.
//
// [valkey.io]: https://valkey.io/commands/xadd/
func (b *BaseBatch[T]) XAddWithOptions(key string, values []models.FieldValue, options options.XAddOptions) *T {
	args := []string{}
	args = append(args, key)
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("XAddWithOptions", err)
	}
	args = append(args, optionArgs...)
	for _, pair := range values {
		args = append(args, []string{pair.Field, pair.Value}...)
	}
	return b.addCmdAndTypeChecker(C.XAdd, args, reflect.String, true)
}

// Reads entries from the given streams.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keysAndIds - A map of keys and entry IDs to read from.
//
// Command Response:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: []FieldValue array of field-value pairs for the entry.
//
// [valkey.io]: https://valkey.io/commands/xread/
func (b *BaseBatch[T]) XRead(keysAndIds map[string]string) *T {
	return b.XReadWithOptions(keysAndIds, *options.NewXReadOptions())
}

// Reads entries from the given streams with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keysAndIds - A map of keys and entry IDs to read from.
//	opts - Options detailing how to read the stream.
//
// Command Response:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: []FieldValue array of field-value pairs for the entry.
//
// [valkey.io]: https://valkey.io/commands/xread/
func (b *BaseBatch[T]) XReadWithOptions(keysAndIds map[string]string, opts options.XReadOptions) *T {
	args, err := internal.CreateStreamCommandArgs(make([]string, 0, 5+2*len(keysAndIds)), keysAndIds, &opts)
	if err != nil {
		return b.addError("XReadWithOptions", err)
	}
	return b.addCmdAndConverter(C.XRead, args, reflect.Map, true, internal.ConvertXReadResponse)
}

// Reads entries from the given streams owned by a consumer group.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	group - The consumer group name.
//	consumer - The group consumer.
//	keysAndIds - A map of keys and entry IDs to read from.
//
// Command Response:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: map[string]string of field-value pairs for the entry
//
// [valkey.io]: https://valkey.io/commands/xreadgroup/
func (b *BaseBatch[T]) XReadGroup(group string, consumer string, keysAndIds map[string]string) *T {
	return b.XReadGroupWithOptions(group, consumer, keysAndIds, *options.NewXReadGroupOptions())
}

// Reads entries from the given streams owned by a consumer group with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	group - The consumer group name.
//	consumer - The group consumer.
//	keysAndIds - A map of keys and entry IDs to read from.
//	opts - Options detailing how to read the stream.
//
// Command Response:
//
//	A map[string]models.StreamResponse where:
//	- Each key is a stream name
//	- Each value is a StreamResponse containing:
//	  - Entries: []StreamEntry, where each StreamEntry has:
//	    - ID: The unique identifier of the entry
//	    - Fields: map[string]string of field-value pairs for the entry
//
// [valkey.io]: https://valkey.io/commands/xreadgroup/
func (b *BaseBatch[T]) XReadGroupWithOptions(
	group string,
	consumer string,
	keysAndIds map[string]string,
	opts options.XReadGroupOptions,
) *T {
	args, err := internal.CreateStreamCommandArgs([]string{constants.GroupKeyword, group, consumer}, keysAndIds, &opts)
	if err != nil {
		return b.addError("XReadGroupWithOptions", err)
	}
	return b.addCmdAndConverter(C.XReadGroup, args, reflect.Map, true, internal.ConvertXReadResponse)
}

// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//	membersScoreMap - A map of members to their scores.
//
// Command Response:
//
//	The number of members added to the set.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (b *BaseBatch[T]) ZAdd(key string, membersScoreMap map[string]float64) *T {
	return b.addCmdAndTypeChecker(
		C.ZAdd,
		append([]string{key}, utils.ConvertMapToValueKeyStringArray(membersScoreMap)...),
		reflect.Int64,
		false,
	)
}

// Adds one or more members to a sorted set, or updates their scores. Creates the key if it doesn't exist.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//	membersScoreMap - A map of members to their scores.
//	opts - The options for the command. See [ZAddOptions] for details.
//
// Command Response:
//
//	The number of members added to the set. If `CHANGED` is set, the number of members that were updated.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (b *BaseBatch[T]) ZAddWithOptions(key string, membersScoreMap map[string]float64, opts options.ZAddOptions) *T {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("ZAddWithOptions", err)
	}
	commandArgs := append([]string{key}, optionArgs...)
	return b.addCmdAndTypeChecker(
		C.ZAdd,
		append(commandArgs, utils.ConvertMapToValueKeyStringArray(membersScoreMap)...),
		reflect.Int64,
		false,
	)
}

// Increments the score of member in the sorted set stored at `key` by `increment`.
//
// If `member` does not exist in the sorted set, it is added with `increment` as its
// score (as if its previous score was `0.0`).
// If `key` does not exist, a new sorted set with the specified member as its sole member
// is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member - A member in the sorted set to increment.
//	increment - The score to increment the member.
//
// Command Response:
//
//	The new score of the member.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (b *BaseBatch[T]) ZAddIncr(key string, member string, increment float64) *T {
	options, err := options.NewZAddOptions().SetIncr(true, increment, member)
	if err != nil {
		return b.addError("ZAddIncr", err)
	}
	return b.zAddIncrBase(key, options)
}

// Increments the score of member in the sorted set stored at `key` by `increment`.
//
// If `member` does not exist in the sorted set, it is added with `increment` as its
// score (as if its previous score was `0.0`).
// If `key` does not exist, a new sorted set with the specified member as its sole member
// is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member - A member in the sorted set to increment.
//	increment - The score to increment the member.
//	opts - The options for the command. See [options.ZAddOptions] for details.
//
// Command Response:
//
//	The new score of the member.
//	If there was a conflict with the options, the operation aborts and `nil` is returned.
//
// [valkey.io]: https://valkey.io/commands/zadd/
func (b *BaseBatch[T]) ZAddIncrWithOptions(key string, member string, increment float64, opts options.ZAddOptions) *T {
	incrOpts, err := opts.SetIncr(true, increment, member)
	if err != nil {
		return b.addError("ZAddIncrWithOptions", err)
	}
	return b.zAddIncrBase(key, incrOpts)
}

func (b *BaseBatch[T]) zAddIncrBase(key string, opts *options.ZAddOptions) *T {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("ZAddIncrWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.ZAdd, append([]string{key}, optionArgs...), reflect.Float64, true)
}

// Increments the score of member in the sorted set stored at key by `increment`.
// If member does not exist in the sorted set, it is added with `increment` as its score.
// If key does not exist, a new sorted set with the specified member as its sole member
// is created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	increment - The score increment.
//	member - A member of the sorted set.
//
// Command Response:
//
//	The new score of member.
//
// [valkey.io]: https://valkey.io/commands/zincrby/
func (b *BaseBatch[T]) ZIncrBy(key string, increment float64, member string) *T {
	return b.addCmdAndTypeChecker(C.ZIncrBy, []string{key, utils.FloatToString(increment), member}, reflect.Float64, false)
}

// Removes and returns the member with the lowest score from the sorted set
// stored at the specified `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//
// Command Response:
//
//	A map containing the removed member and its corresponding score.
//	If `key` doesn't exist, it will be treated as an empty sorted set and returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmin/
func (b *BaseBatch[T]) ZPopMin(key string) *T {
	return b.addCmdAndConverter(C.ZPopMin, []string{key}, reflect.Map, false, internal.ConvertMapOf[float64])
}

// Removes and returns multiple members with the lowest scores from the sorted set
// stored at the specified `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	options - Pop options, see [options.ZPopOptions].
//
// Command Response:
//
//	A map containing the removed members and their corresponding scores.
//	If `key` doesn't exist, it will be treated as an empty sorted set and returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmin/
func (b *BaseBatch[T]) ZPopMinWithOptions(key string, options options.ZPopOptions) *T {
	optArgs, err := options.ToArgs(false)
	if err != nil {
		return b.addError("ZPopMinWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.ZPopMin,
		append([]string{key}, optArgs...),
		reflect.Map,
		false,
		internal.ConvertMapOf[float64],
	)
}

// Removes and returns the member with the highest score from the sorted set stored at the
// specified `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//
// Command Response:
//
//	A map containing the removed member and its corresponding score.
//	If `key` doesn't exist, it will be treated as an empty sorted set and returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmax/
func (b *BaseBatch[T]) ZPopMax(key string) *T {
	return b.addCmdAndConverter(C.ZPopMax, []string{key}, reflect.Map, false, internal.ConvertMapOf[float64])
}

// Removes and returns up to `count` members with the highest scores from the sorted set
// stored at the specified `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	options - Pop options, see [options.ZPopOptions].
//
// Command Response:
//
//	A map containing the removed members and their corresponding scores.
//	If `key` doesn't exist, it will be treated as an empty sorted set and returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zpopmax/
func (b *BaseBatch[T]) ZPopMaxWithOptions(key string, options options.ZPopOptions) *T {
	optArgs, err := options.ToArgs(false)
	if err != nil {
		return b.addError("ZPopMaxWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.ZPopMax,
		append([]string{key}, optArgs...),
		reflect.Map,
		false,
		internal.ConvertMapOf[float64],
	)
}

// Removes the specified members from the sorted set stored at `key`.
// Specified members that are not a member of this set are ignored.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	members - The members to remove.
//
// Command Response:
//
//	The number of members that were removed from the sorted set, not including non-existing members.
//	If `key` does not exist, it is treated as an empty sorted set, and this command returns `0`.
//
// [valkey.io]: https://valkey.io/commands/zrem/
func (b *BaseBatch[T]) ZRem(key string, members []string) *T {
	return b.addCmdAndTypeChecker(C.ZRem, append([]string{key}, members...), reflect.Int64, false)
}

// Returns the cardinality (number of elements) of the sorted set stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//
// Command Response:
//
//	The number of elements in the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and this command returns `0`.
//
// [valkey.io]: https://valkey.io/commands/zcard/
func (b *BaseBatch[T]) ZCard(key string) *T {
	return b.addCmdAndTypeChecker(C.ZCard, []string{key}, reflect.Int64, false)
}

// Blocks the connection until it pops and returns a member-score pair
// with the lowest score from the first non-empty sorted set.
// The given `keys` being checked in the order they are provided.
// BZPopMin is the blocking variant of [BaseBatch.ZPopMin].
//
// Note:
//
// BZPopMin is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sorted sets.
//	timeout - The duration to wait for a blocking operation to complete. A value of
//	  `0` will block indefinitely.
//
// Command Response:
//
//	An array containing the key where the member was popped out, the member itself, and the member score.
//	If no member could be popped and the `timeout` expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/bzpopmin/
//
// [Blocking commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BZPopMin(keys []string, timeout time.Duration) *T {
	return b.addCmdAndConverter(
		C.BZPopMin,
		append(keys, utils.FloatToString(timeout.Seconds())),
		reflect.Slice,
		true,
		internal.ConvertKeyWithMemberAndScore,
	)
}

// Blocks the connection until it pops and returns a member-score pair from the first non-empty sorted set, with the
// given keys being checked in the order they are provided.
// BZMPop is the blocking variant of [BaseBatch.ZMPop].
//
// Note:
//
// `BZMPop` is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys          - An array of keys to lists.
//	scoreFilter   - The element pop criteria - either [options.MIN] or [options.MAX] to pop members with the lowest/highest
//					scores accordingly.
//	timeout       - The duration to wait for a blocking operation to complete. A value of `0` will block
//					indefinitely.
//
// Command Response:
//
//	An object containing the following elements:
//	- The key name of the set from which the element was popped.
//	- An array of member scores of the popped elements.
//	Returns `nil` if no member could be popped and the timeout expired.
//
// [valkey.io]: https://valkey.io/commands/bzmpop/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BZMPop(keys []string, scoreFilter constants.ScoreFilter, timeout time.Duration) *T {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return b.addError("BZMPop", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-3 {
		return b.addError("BZMPop", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 3 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+3)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, scoreFilterStr)
	return b.addCmdAndConverter(C.BZMPop, args, reflect.Slice, true, internal.ConvertKeyWithArrayOfMembersAndScores)
}

// Blocks the connection until it pops and returns a member-score pair from the first non-empty sorted set, with the
// given keys being checked in the order they are provided.
// BZMPop is the blocking variant of [BaseBatch.ZMPop].
//
// Note:
//
// `BZMPop` is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys          - An array of keys to lists.
//	scoreFilter   - The element pop criteria - either [options.MIN] or [options.MAX] to pop members with the lowest/highest
//					scores accordingly.
//	timeout       - The duration to wait for a blocking operation to complete. A value of `0` will block indefinitely.
//	opts          - Pop options, see [options.ZMPopOptions].
//
// Command Response:
//
//	An object containing the following elements:
//	- The key name of the set from which the element was popped.
//	- An array of member scores of the popped elements.
//	Returns `nil` if no member could be popped and the timeout expired.
//
// [valkey.io]: https://valkey.io/commands/bzmpop/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BZMPopWithOptions(
	keys []string,
	scoreFilter constants.ScoreFilter,
	timeout time.Duration,
	opts options.ZMPopOptions,
) *T {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return b.addError("BZMPopWithOptions", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-5 {
		return b.addError("BZMPopWithOptions", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 5 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+5)
	args = append(args, utils.FloatToString(timeout.Seconds()), strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, scoreFilterStr)
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("BZMPopWithOptions", err)
	}
	args = append(args, optionArgs...)
	return b.addCmdAndConverter(C.BZMPop, args, reflect.Slice, true, internal.ConvertKeyWithArrayOfMembersAndScores)
}

// Returns the specified range of elements in the sorted set stored at `key`.
// `ZRANGE` can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
//
// To get the elements with their scores, see [BaseBatch.ZRangeWithScores].
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the type of range query to perform.
//	  - For range queries by index (rank), use [RangeByIndex].
//	  - For range queries by lexicographical order, use [RangeByLex].
//	  - For range queries by score, use [RangeByScore].
//
// Command Response:
//
//	An array of elements within the specified range.
//	If `key` does not exist, it is treated as an empty sorted set, and returns an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrange/
func (b *BaseBatch[T]) ZRange(key string, rangeQuery options.ZRangeQuery) *T {
	args := make([]string, 0, 10)
	args = append(args, key)
	queryArgs, err := rangeQuery.ToArgs()
	if err != nil {
		return b.addError("ZRange", err)
	}
	args = append(args, queryArgs...)
	return b.addCmdAndConverter(C.ZRange, args, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the specified range of elements with their scores in the sorted set stored at `key`.
// `ZRANGE` can perform different types of range queries: by index (rank), by the score, or by lexicographical order.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the type of range query to perform.
//	  - For range queries by index (rank), use [RangeByIndex].
//	  - For range queries by score, use [RangeByScore].
//
// Command Response:
//
//	An array of elements and their scores within the specified range.
//	If `key` does not exist, it is treated as an empty sorted set, and returns an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrange/
func (b *BaseBatch[T]) ZRangeWithScores(key string, rangeQuery options.ZRangeQueryWithScores) *T {
	args := make([]string, 0, 10)
	args = append(args, key)
	queryArgs, err := rangeQuery.ToArgs()
	if err != nil {
		return b.addError("ZRangeWithScores", err)
	}
	args = append(args, queryArgs...)
	args = append(args, constants.WithScoresKeyword)

	needsReverse := false
	for _, arg := range args {
		if arg == "REV" {
			needsReverse = true
			break
		}
	}
	return b.addCmdAndConverter(C.ZRange, args, reflect.Map, false, internal.MakeConvertMapOfMemberAndScore(needsReverse))
}

// Stores a specified range of elements from the sorted set at `key`, into a new
// sorted set at `destination`. If `destination` doesn't exist, a new sorted
// set is created; if it exists, it's overwritten.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The key for the destination sorted set.
//	key - The key of the source sorted set.
//	rangeQuery - The range query object representing the type of range query to perform.
//	 - For range queries by index (rank), use [RangeByIndex].
//	 - For range queries by lexicographical order, use [RangeByLex].
//	 - For range queries by score, use [RangeByScore].
//
// Command Response:
//
//	The number of elements in the resulting sorted set.
//
// [valkey.io]: https://valkey.io/commands/zrangestore/
func (b *BaseBatch[T]) ZRangeStore(destination string, key string, rangeQuery options.ZRangeQuery) *T {
	args := make([]string, 0, 10)
	args = append(args, destination)
	args = append(args, key)
	rqArgs, err := rangeQuery.ToArgs()
	if err != nil {
		return b.addError("ZRangeStore", err)
	}
	args = append(args, rqArgs...)
	return b.addCmdAndTypeChecker(C.ZRangeStore, args, reflect.Int64, false)
}

// Removes the existing timeout on key, turning the key from volatile
// (a key with an expire set) to persistent (a key that will never expire as no timeout is associated).
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to remove the existing timeout on.
//
// Command Response:
//
//	`false` if key does not exist or does not have an associated timeout, `true` if the timeout has been removed.
//
// [valkey.io]: https://valkey.io/commands/persist/
func (b *BaseBatch[T]) Persist(key string) *T {
	return b.addCmdAndTypeChecker(C.Persist, []string{key}, reflect.Bool, false)
}

// Returns the number of members in the sorted set stored at `key` with scores between `min` and `max` score.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the set.
//	rangeOptions - Contains `min` and `max` score. `min` contains the minimum score to count from.
//		`max` contains the maximum score to count up to. Can be positive/negative infinity, or
//		specific score and inclusivity.
//
// Command Response:
//
//	The number of members in the specified score range.
//
// [valkey.io]: https://valkey.io/commands/zcount/
func (b *BaseBatch[T]) ZCount(key string, rangeOptions options.ZCountRange) *T {
	zCountRangeArgs, err := rangeOptions.ToArgs()
	if err != nil {
		return b.addError("ZCount", err)
	}
	return b.addCmdAndTypeChecker(C.ZCount, append([]string{key}, zCountRangeArgs...), reflect.Int64, false)
}

// Returns the rank of `member` in the sorted set stored at `key`, with
// scores ordered from low to high, starting from `0`.
// To get the rank of `member` with its score, see [BaseBatch.ZRankWithScore].
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Command Response:
//
//	The rank of member in the sorted set.
//	If key doesn't exist, or if member is not present in the set, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrank/
func (b *BaseBatch[T]) ZRank(key string, member string) *T {
	return b.addCmdAndTypeChecker(C.ZRank, []string{key, member}, reflect.Int64, true)
}

// Returns the rank of `member` in the sorted set stored at `key` with its
// score, where scores are ordered from the lowest to highest, starting from `0`.
//
// Since:
//
//	Valkey 7.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Command Response:
//
//	A [models.RankAndScore] containing the rank of `member` and its score.
//	If `key` doesn't exist, or if `member` is not present in the set, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrank/
func (b *BaseBatch[T]) ZRankWithScore(key string, member string) *T {
	return b.addCmdAndConverter(
		C.ZRank,
		[]string{key, member, constants.WithScoreKeyword},
		reflect.Slice,
		true,
		internal.ConvertRankAndScoreResponse,
	)
}

// Returns the rank of `member` in the sorted set stored at `key`, where
// scores are ordered from the highest to lowest, starting from `0`.
// To get the rank of `member` with its score, see [BaseBatch.ZRevRankWithScore].
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Command Response:
//
//	The rank of `member` in the sorted set, where ranks are ordered from high to low based on scores.
//	If `key` doesn't exist, or if `member` is not present in the set, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrevrank/
func (b *BaseBatch[T]) ZRevRank(key string, member string) *T {
	return b.addCmdAndTypeChecker(C.ZRevRank, []string{key, member}, reflect.Int64, true)
}

// Returns the rank of `member` in the sorted set stored at `key`, where
// scores are ordered from the highest to lowest, starting from `0`.
//
// Since:
//
//	Valkey 7.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member - The member to get the rank of.
//
// Command Response:
//
//	A [models.RankAndScore] containing the rank of `member` and its score.
//	If `key` doesn't exist, or if `member` is not present in the set, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/zrevrank/
func (b *BaseBatch[T]) ZRevRankWithScore(key string, member string) *T {
	return b.addCmdAndConverter(
		C.ZRevRank,
		[]string{key, member, constants.WithScoreKeyword},
		reflect.Slice,
		true,
		internal.ConvertRankAndScoreResponse,
	)
}

// Trims the stream by evicting older entries.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key of the stream.
//	options - Stream trim options
//
// Command Response:
//
//	The number of entries deleted from the stream.
//
// [valkey.io]: https://valkey.io/commands/xtrim/
func (b *BaseBatch[T]) XTrim(key string, options options.XTrimOptions) *T {
	xTrimArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("XTrim", err)
	}
	return b.addCmdAndTypeChecker(C.XTrim, append([]string{key}, xTrimArgs...), reflect.Int64, false)
}

// Returns the number of entries in the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//
// Command Response:
//
//	The number of entries in the stream. If `key` does not exist, return `0`.
//
// [valkey.io]: https://valkey.io/commands/xlen/
func (b *BaseBatch[T]) XLen(key string) *T {
	return b.addCmdAndTypeChecker(C.XLen, []string{key}, reflect.Int64, false)
}

// Transfers ownership of pending stream entries that match the specified criteria.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//
// Command Response:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - A array of the claimed entries as `[]models.StreamEntry`.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (b *BaseBatch[T]) XAutoClaim(key string, group string, consumer string, minIdleTime time.Duration, start string) *T {
	return b.XAutoClaimWithOptions(key, group, consumer, minIdleTime, start, *options.NewXAutoClaimOptions())
}

// Transfers ownership of pending stream entries that match the specified criteria.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//	options - Options detailing how to read the stream. Count has a default value of 100.
//
// Command Response:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - A array of the claimed entries as `[]models.StreamEntry`.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (b *BaseBatch[T]) XAutoClaimWithOptions(
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	start string,
	options options.XAutoClaimOptions,
) *T {
	args := []string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds()), start}
	optArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("XAutoClaimWithOptions", err)
	}
	args = append(args, optArgs...)
	return b.addCmdAndConverter(C.XAutoClaim, args, reflect.Slice, false, internal.ConvertXAutoClaimResponse)
}

// Transfers ownership of pending stream entries and returns just the IDs.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//
// Command Response:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - An array of IDs for the claimed entries.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (b *BaseBatch[T]) XAutoClaimJustId(
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	start string,
) *T {
	return b.XAutoClaimJustIdWithOptions(key, group, consumer, minIdleTime, start, *options.NewXAutoClaimOptions())
}

// Transfers ownership of pending stream entries that match the specified criteria.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The group consumer.
//	minIdleTime - The minimum idle time for the message to be claimed.
//	start - Filters the claimed entries to those that have an ID equal or greater than the specified value.
//	opts - Options detailing how to read the stream. Count has a default value of 100.
//
// Command Response:
//
//	An object containing the following elements:
//	  - A stream ID to be used as the start argument for the next call to `XAUTOCLAIM`. This ID is
//	    equivalent to the next ID in the stream after the entries that were scanned, or "0-0" if
//	    the entire stream was scanned.
//	  - An array of IDs for the claimed entries.
//	  - If you are using Valkey 7.0.0 or above, the response will also include an array containing
//	    the message IDs that were in the Pending Entries List but no longer exist in the stream.
//	    These IDs are deleted from the Pending Entries List.
//
// [valkey.io]: https://valkey.io/commands/xautoclaim/
func (b *BaseBatch[T]) XAutoClaimJustIdWithOptions(
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	start string,
	options options.XAutoClaimOptions,
) *T {
	args := []string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds()), start}
	optArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("XAutoClaimJustIdWithOptions", err)
	}
	args = append(args, optArgs...)
	args = append(args, constants.JustIdKeyword)
	return b.addCmdAndConverter(C.XAutoClaim, args, reflect.Slice, false, internal.ConvertXAutoClaimJustIdResponse)
}

// Removes the specified entries by id from a stream, and returns the number of entries deleted.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	ids - An array of entry ids.
//
// Command Response:
//
//	The number of entries removed from the stream. This number may be less than the number
//	of entries in `ids`, if the specified `ids` don't exist in the stream.
//
// [valkey.io]: https://valkey.io/commands/xdel/
func (b *BaseBatch[T]) XDel(key string, ids []string) *T {
	return b.addCmdAndTypeChecker(C.XDel, append([]string{key}, ids...), reflect.Int64, false)
}

// Returns the score of `member` in the sorted set stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member - The member whose score is to be retrieved.
//
// Command Response:
//
//	The score of the member. If member does not exist in the sorted set, `nil` is returned.
//	If key does not exist, `nil` is returned.
//
// [valkey.io]: https://valkey.io/commands/zscore/
func (b *BaseBatch[T]) ZScore(key string, member string) *T {
	return b.addCmdAndTypeChecker(C.ZScore, []string{key, member}, reflect.Float64, true)
}

// Iterates incrementally over a sorted set.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	cursor - The cursor that points to the next iteration of results.
//	         A value of "0" indicates the start of the search.
//	         For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the sorted set held in `key`.
//	The array is a flattened series of `string` pairs, where the value is at even indices and the score is at odd indices.
//
// [valkey.io]: https://valkey.io/commands/zscan/
func (b *BaseBatch[T]) ZScan(key string, cursor string) *T {
	return b.addCmdAndConverter(C.ZScan, []string{key, cursor}, reflect.Slice, false, internal.ConvertScanResult)
}

// Iterates incrementally over a sorted set.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	cursor - The cursor that points to the next iteration of results.
//	         A value of `"0"` indicates the start of the search.
//	         For Valkey 8.0 and above, negative cursors are treated like the initial cursor("0").
//	options - The options for the command. See [options.ZScanOptions] for details.
//
// Command Response:
//
//	An object which holds the next cursor and the subset of the hash held by `key`.
//	The cursor will return `false` from `IsFinished()` method on the last iteration of the subset.
//	The data array in the result is always an array of the subset of the sorted set held in `key`.
//	The array is a flattened series of `string` pairs, where the value is at even indices and the score is at odd indices.
//
// [valkey.io]: https://valkey.io/commands/zscan/
func (b *BaseBatch[T]) ZScanWithOptions(key string, cursor string, options options.ZScanOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("ZScanWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.ZScan,
		append([]string{key, cursor}, optionArgs...),
		reflect.Slice,
		false,
		internal.ConvertScanResult,
	)
}

// Returns stream message summary information for pending messages matching a stream and group.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//
// Command Response:
//
// An [models.XPendingSummary] struct that includes a summary with the following fields:
//
//	NumOfMessages - The total number of pending messages for this consumer group.
//	StartId - The smallest ID among the pending messages or nil if no pending messages exist.
//	EndId - The greatest ID among the pending messages or nil if no pending messages exists.
//	GroupConsumers - An array of ConsumerPendingMessages with the following fields:
//	ConsumerName - The name of the consumer.
//	MessageCount - The number of pending messages for this consumer.
//
// [valkey.io]: https://valkey.io/commands/xpending/
func (b *BaseBatch[T]) XPending(key string, group string) *T {
	return b.addCmdAndConverter(C.XPending, []string{key, group}, reflect.Slice, false, internal.ConvertXPendingResponse)
}

// Returns stream message summary information for pending messages matching a given range of IDs.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	opts - The options for the command. See [options.XPendingOptions] for details.
//
// Command Response:
//
// A slice of [models.XPendingDetail] structs, where each detail struct includes the following fields:
//
//	Id - The ID of the pending message.
//	ConsumerName - The name of the consumer that fetched the message and has still to acknowledge it.
//	IdleTime - The time in milliseconds since the last time the message was delivered to the consumer.
//	DeliveryCount - The number of times this message was delivered.
//
// [valkey.io]: https://valkey.io/commands/xpending/
func (b *BaseBatch[T]) XPendingWithOptions(key string, group string, opts options.XPendingOptions) *T {
	optionArgs, _ := opts.ToArgs()
	args := append([]string{key, group}, optionArgs...)
	return b.addCmdAndConverter(C.XPending, args, reflect.Slice, false, internal.ConvertXPendingWithOptionsResponse)
}

// Creates a new consumer group uniquely identified by `group` for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The newly created consumer group name.
//	id - Stream entry ID that specifies the last delivered entry in the stream from the new
//	    group's perspective. The special ID "$" can be used to specify the last entry in the stream.
//
// Command Response:
//
//	"OK".
//
// [valkey.io]: https://valkey.io/commands/xgroup-create/
func (b *BaseBatch[T]) XGroupCreate(key string, group string, id string) *T {
	return b.XGroupCreateWithOptions(key, group, id, *options.NewXGroupCreateOptions())
}

// Creates a new consumer group uniquely identified by `group` for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The newly created consumer group name.
//	id - Stream entry ID that specifies the last delivered entry in the stream from the new
//	    group's perspective. The special ID "$" can be used to specify the last entry in the stream.
//	opts - The options for the command. See [options.XGroupCreateOptions] for details.
//
// Command Response:
//
//	"OK".
//
// [valkey.io]: https://valkey.io/commands/xgroup-create/
func (b *BaseBatch[T]) XGroupCreateWithOptions(key string, group string, id string, opts options.XGroupCreateOptions) *T {
	optionArgs, _ := opts.ToArgs()
	args := append([]string{key, group, id}, optionArgs...)
	return b.addCmdAndTypeChecker(C.XGroupCreate, args, reflect.String, false)
}

// Creates a key associated with a value that is obtained by
// deserializing the provided serialized value (obtained via [BaseBatch.Dump]).
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to create.
//	ttl - The expiry time. If `0`, the key will persist.
//	value - The serialized value to deserialize and assign to key.
//
// Command Response:
//
//	Return OK if successfully create a key with a value.
//
// [valkey.io]: https://valkey.io/commands/restore/
func (b *BaseBatch[T]) Restore(key string, ttl time.Duration, value string) *T {
	return b.RestoreWithOptions(key, ttl, value, *options.NewRestoreOptions())
}

// Creates a key associated with a value that is obtained by
// deserializing the provided serialized value (obtained via [BaseBatch.Dump]).
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to create.
//	ttl - The expiry time. If `0`, the key will persist.
//	value - The serialized value to deserialize and assign to key.
//	restoreOptions - Set restore options with replace and absolute TTL modifiers, object idletime and frequency.
//
// Command Response:
//
//	Return OK if successfully create a key with a value.
//
// [valkey.io]: https://valkey.io/commands/restore/
func (b *BaseBatch[T]) RestoreWithOptions(
	key string,
	ttl time.Duration,
	value string,
	restoreOptions options.RestoreOptions,
) *T {
	optionArgs, err := restoreOptions.ToArgs()
	if err != nil {
		return b.addError("RestoreWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.Restore, append([]string{
		key,
		utils.IntToString(ttl.Milliseconds()), value,
	}, optionArgs...), reflect.String, false)
}

// Serializess the value stored at key in a Valkey-specific format.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key to serialize.
//
// Command Response:
//
//	The serialized value of the data stored at key.
//	If key does not exist, `nil` will be returned.
//
// [valkey.io]: https://valkey.io/commands/dump/
func (b *BaseBatch[T]) Dump(key string) *T {
	return b.addCmdAndTypeChecker(C.Dump, []string{key}, reflect.String, true)
}

// Returns the internal encoding for the Valkey object stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the object to get the internal encoding of.
//
// Command Response:
//
//	If key exists, returns the internal encoding of the object stored at
//	key as a String. Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/object-encoding/
func (b *BaseBatch[T]) ObjectEncoding(key string) *T {
	return b.addCmdAndTypeChecker(C.ObjectEncoding, []string{key}, reflect.String, true)
}

// Destroys the consumer group for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name to delete.
//
// Command Response:
//
//	`true` if the consumer group is destroyed. Otherwise, `false`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-destroy/
func (b *BaseBatch[T]) XGroupDestroy(key string, group string) *T {
	return b.addCmdAndTypeChecker(C.XGroupDestroy, []string{key, group}, reflect.Bool, false)
}

// Sets the last delivered ID for a consumer group.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	id - The stream entry ID that should be set as the last delivered ID for the consumer group.
//
// Command Response:
//
//	"OK".
//
// [valkey.io]: https://valkey.io/commands/xgroup-setid/
func (b *BaseBatch[T]) XGroupSetId(key string, group string, id string) *T {
	return b.XGroupSetIdWithOptions(key, group, id, *options.NewXGroupSetIdOptionsOptions())
}

// Sets the last delivered ID for a consumer group with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	id - The stream entry ID that should be set as the last delivered ID for the consumer group.
//	opts - The options for the command. See [options.XGroupSetIdOptions] for details.
//
// Command Response:
//
//	"OK".
//
// [valkey.io]: https://valkey.io/commands/xgroup-setid/
func (b *BaseBatch[T]) XGroupSetIdWithOptions(key string, group string, id string, opts options.XGroupSetIdOptions) *T {
	optionArgs, _ := opts.ToArgs()
	args := append([]string{key, group, id}, optionArgs...)
	return b.addCmdAndTypeChecker(C.XGroupSetId, args, reflect.String, false)
}

// Removes all elements in the sorted set stored at `key` with a lexicographical order
// between `rangeQuery.Start` and `rangeQuery.End`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the minimum and maximum bound of the lexicographical range.
//
// Command Response:
//
//	The number of members removed from the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
//	If `rangeQuery.Start` is greater than `rangeQuery.End`, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/zremrangebylex/
func (b *BaseBatch[T]) ZRemRangeByLex(key string, rangeQuery options.RangeByLex) *T {
	queryArgs, err := rangeQuery.ToArgsRemRange()
	if err != nil {
		return b.addError("ZRemRangeByLex", err)
	}
	return b.addCmdAndTypeChecker(C.ZRemRangeByLex, append([]string{key}, queryArgs...), reflect.Int64, false)
}

// Removes all elements in the sorted set stored at `key` with a rank between `start` and `stop`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	start - The start rank.
//	stop - The stop rank.
//
// Command Response:
//
//	The number of members removed from the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
//	If `start` is greater than `stop`, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/zremrangebyrank/
func (b *BaseBatch[T]) ZRemRangeByRank(key string, start int64, stop int64) *T {
	return b.addCmdAndTypeChecker(
		C.ZRemRangeByRank,
		[]string{key, utils.IntToString(start), utils.IntToString(stop)},
		reflect.Int64,
		false,
	)
}

// Removes all elements in the sorted set stored at key with a score between min and max.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	rangeQuery - The range query object representing the minimum and maximum bound of the score range.
//	  can be an implementation of [options.RangeByScore].
//
// Command Response:
//
//	The number of members removed from the sorted set.
//	If `key` does not exist, it is treated as an empty sorted set, and the command returns `0`.
//	If `rangeQuery.Start` is greater than `rangeQuery.End`, `0` is returned.
//
// [valkey.io]: https://valkey.io/commands/zremrangebyscore/
func (b *BaseBatch[T]) ZRemRangeByScore(key string, rangeQuery options.RangeByScore) *T {
	queryArgs, err := rangeQuery.ToArgsRemRange()
	if err != nil {
		return b.addError("ZRemRangeByScore", err)
	}
	return b.addCmdAndTypeChecker(C.ZRemRangeByScore, append([]string{key}, queryArgs...), reflect.Int64, false)
}

// Returns a random member from the sorted set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//
// Command Response:
//
//	A string representing a random member from the sorted set.
//	If the sorted set does not exist or is empty, the response will be `nil`.
//
// [valkey.io]: https://valkey.io/commands/zrandmember/
func (b *BaseBatch[T]) ZRandMember(key string) *T {
	return b.addCmdAndTypeChecker(C.ZRandMember, []string{key}, reflect.String, true)
}

// Returns multiple random members from the sorted set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	count - The number of field names to return.
//	  If `count` is positive, returns unique elements. If negative, allows for duplicates.
//
// Command Response:
//
//	An array of members from the sorted set.
//	If the sorted set does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrandmember/
func (b *BaseBatch[T]) ZRandMemberWithCount(key string, count int64) *T {
	return b.addCmdAndConverter(
		C.ZRandMember,
		[]string{key, utils.IntToString(count)},
		reflect.Slice,
		false,
		internal.ConvertArrayOf[string],
	)
}

// Returns random members with scores from the sorted set stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	count - The number of field names to return.
//	  If `count` is positive, returns unique elements. If negative, allows for duplicates.
//
// Command Response:
//
//	An array of [models.MemberAndScore] objects, which store member names and their respective scores.
//	If the sorted set does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/zrandmember/
func (b *BaseBatch[T]) ZRandMemberWithCountWithScores(key string, count int64) *T {
	return b.addCmdAndConverter(
		C.ZRandMember,
		[]string{key, utils.IntToString(count), constants.WithScoresKeyword},
		reflect.Slice,
		false,
		internal.ConvertArrayOfMemberAndScore,
	)
}

// Returns the scores associated with the specified `members` in the sorted set stored at `key`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key     - The key of the sorted set.
//	members - A list of members in the sorted set.
//
// Command Response:
//
//	An array of scores corresponding to `members`.
//	If a member does not exist in the sorted set, the corresponding value in the list will be `nil`.
//
// [valkey.io]: https://valkey.io/commands/zmscore/
func (b *BaseBatch[T]) ZMScore(key string, members []string) *T {
	return b.addCmdAndConverter(
		C.ZMScore,
		append([]string{key}, members...),
		reflect.Slice,
		false,
		internal.ConvertArrayOfNilOr[float64],
	)
}

// Returns the logarithmic access frequency counter of a Valkey object stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the object to get the logarithmic access frequency counter of.
//
// Command Response:
//
//	If key exists, returns the logarithmic access frequency counter of the
//	object stored at key as a long. Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/object-freq/
func (b *BaseBatch[T]) ObjectFreq(key string) *T {
	return b.addCmdAndTypeChecker(C.ObjectFreq, []string{key}, reflect.Int64, true)
}

// Returns the idle time in seconds of a Valkey object stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the object to get the idle time of.
//
// Command Response:
//
//	If key exists, returns the idle time in seconds. Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/object-idletime/
func (b *BaseBatch[T]) ObjectIdleTime(key string) *T {
	return b.addCmdAndTypeChecker(C.ObjectIdleTime, []string{key}, reflect.Int64, true)
}

// Returns the reference count of the object stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the object to get the reference count of.
//
// Command Response:
//
//	If key exists, returns the reference count of the object stored at key.
//	Otherwise, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/object-refcount/
func (b *BaseBatch[T]) ObjectRefCount(key string) *T {
	return b.addCmdAndTypeChecker(C.ObjectRefCount, []string{key}, reflect.Int64, true)
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The sort command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To store the result into a new key, see the sortStore function.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list, set, or sorted set to be sorted.
//
// Command Response:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (b *BaseBatch[T]) Sort(key string) *T {
	return b.addCmdAndConverter(C.Sort, []string{key}, reflect.Slice, false, internal.ConvertArrayOfNilOr[string])
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The sort command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To store the result into a new key, see the [BaseBatch.SortStoreWithOptions] function.
//
// Note:
//
// The use of [SortOptions.byPattern] and [SortOptions.getPatterns] in cluster mode is
// supported since Valkey version 8.0.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list, set, or sorted set to be sorted.
//	options - The SortOptions type.
//
// Command Response:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (b *BaseBatch[T]) SortWithOptions(key string, options options.SortOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("SortWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.Sort,
		append([]string{key}, optionArgs...),
		reflect.Slice,
		false,
		internal.ConvertArrayOfNilOr[string],
	)
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The SortReadOnly command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list, set, or sorted set to be sorted.
//
// Command Response:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort_ro/
func (b *BaseBatch[T]) SortReadOnly(key string) *T {
	return b.addCmdAndConverter(C.SortReadOnly, []string{key}, reflect.Slice, false, internal.ConvertArrayOfNilOr[string])
}

// Sorts the elements in the list, set, or sorted set at key and returns the result.
// The SortReadOnly command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
//
// Note:
//
// The use of [SortOptions.byPattern] and [SortOptions.getPatterns] in cluster mode is
// supported since Valkey version 8.0.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list, set, or sorted set to be sorted.
//	options - The SortOptions type.
//
// Command Response:
//
//	An Array of sorted elements.
//
// [valkey.io]: https://valkey.io/commands/sort_ro/
func (b *BaseBatch[T]) SortReadOnlyWithOptions(key string, options options.SortOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("SortReadOnlyWithOptions", err)
	}
	return b.addCmdAndConverter(
		C.SortReadOnly,
		append([]string{key}, optionArgs...),
		reflect.Slice,
		false,
		internal.ConvertArrayOfNilOr[string],
	)
}

// Sorts the elements in the list, set, or sorted set at key and stores the result in
// destination. The sort command can be used to sort elements based on
// different criteria, apply transformations on sorted elements, and store the result in a new key.
// The SortStore command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To get the sort result without storing it into a key, see the [BaseBatch.Sort] or [BaseBatch.SortReadOnly] function.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list, set, or sorted set to be sorted.
//	destination - The key where the sorted result will be stored.
//
// Command Response:
//
//	The number of elements in the sorted key stored at destination.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (b *BaseBatch[T]) SortStore(key string, destination string) *T {
	return b.addCmdAndTypeChecker(C.Sort, []string{key, constants.StoreKeyword, destination}, reflect.Int64, false)
}

// Sorts the elements in the list, set, or sorted set at key and stores the result in
// destination. The sort command can be used to sort elements based on
// different criteria, apply transformations on sorted elements, and store the result in a new key.
// The SortStore command can be used to sort elements based on different criteria and apply
// transformations on sorted elements.
// To get the sort result without storing it into a key, see the [BaseBatch.Sort] or [BaseBatch.SortReadOnly] function.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the list, set, or sorted set to be sorted.
//	destination - The key where the sorted result will be stored.
//	options - The SortOptions type.
//
// Command Response:
//
//	The number of elements in the sorted key stored at destination.
//
// [valkey.io]: https://valkey.io/commands/sort/
func (b *BaseBatch[T]) SortStoreWithOptions(key string, destination string, options options.SortOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("SortStoreWithOptions", err)
	}
	return b.addCmdAndTypeChecker(
		C.Sort,
		append([]string{key, constants.StoreKeyword, destination}, optionArgs...),
		reflect.Int64,
		false,
	)
}

// Creates a consumer named `consumer` in the consumer group `group` for the
// stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The newly created consumer.
//
// Command Response:
//
//	Returns `true` if the consumer is created. Otherwise, returns `false`.
//
// [valkey.io]: https://valkey.io/commands/xgroup-createconsumer/
func (b *BaseBatch[T]) XGroupCreateConsumer(key string, group string, consumer string) *T {
	return b.addCmdAndTypeChecker(C.XGroupCreateConsumer, []string{key, group, consumer}, reflect.Bool, false)
}

// Deletes a consumer named consumer in the `consumer` group `group`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//	group - The consumer group name.
//	consumer - The consumer to delete.
//
// Command Response:
//
//	The number of pending messages the `consumer` had before it was deleted.
//
// [valkey.io]: https://valkey.io/commands/xgroup-delconsumer/
func (b *BaseBatch[T]) XGroupDelConsumer(key string, group string, consumer string) *T {
	return b.addCmdAndTypeChecker(C.XGroupDelConsumer, []string{key, group, consumer}, reflect.Int64, false)
}

// Returns the number of messages that were successfully acknowledged by the consumer group member
// of a stream. This command should be called on a pending message so that such message does not
// get processed again.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the stream.
//	group - The consumer group name.
//	ids   - Stream entry IDs to acknowledge and purge messages.
//
// Command Response:
//
//	The number of messages that were successfully acknowledged.
//
// [valkey.io]: https://valkey.io/commands/xack/
func (b *BaseBatch[T]) XAck(key string, group string, ids []string) *T {
	return b.addCmdAndTypeChecker(C.XAck, append([]string{key, group}, ids...), reflect.Int64, false)
}

// Sets or clears the bit at offset in the string value stored at key.
// The offset is a zero-based index, with `0` being the first element of
// the list, `1` being the next element, and so on. The offset must be
// less than `2^32` and greater than or equal to `0` If a key is
// non-existent then the bit at offset is set to value and the preceding
// bits are set to `0`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the string.
//	offset - The index of the bit to be set.
//	value - The bit value to set at offset The value must be `0` or `1`.
//
// Command Response:
//
//	The bit value that was previously stored at offset.
//
// [valkey.io]: https://valkey.io/commands/setbit/
func (b *BaseBatch[T]) SetBit(key string, offset int64, value int64) *T {
	return b.addCmdAndTypeChecker(
		C.SetBit,
		[]string{key, utils.IntToString(offset), utils.IntToString(value)},
		reflect.Int64,
		false,
	)
}

// Returns the bit value at offset in the string value stored at key.
// offset should be greater than or equal to zero.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the string.
//	offset - The index of the bit to return. Should be greater than or equal to zero.
//
// Command Response:
//
//	The bit at offset of the string. Returns zero if the key is empty or if the positive
//	offset exceeds the length of the string.
//
// [valkey.io]: https://valkey.io/commands/getbit/
func (b *BaseBatch[T]) GetBit(key string, offset int64) *T {
	return b.addCmdAndTypeChecker(C.GetBit, []string{key, utils.IntToString(offset)}, reflect.Int64, false)
}

// Blocks the current client until all the previous write commands are successfully
// transferred and acknowledged by at least the specified number of replicas or if the timeout is reached,
// whichever is earlier.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	numberOfReplicas - The number of replicas to reach.
//	timeout - The timeout value. A value of `0` will block indefinitely.
//
// Command Response:
//
//	The number of replicas reached by all the writes performed in the context of the current connection.
//
// [valkey.io]: https://valkey.io/commands/wait/
func (b *BaseBatch[T]) Wait(numberOfReplicas int64, timeout time.Duration) *T {
	return b.addCmdAndTypeChecker(
		C.Wait,
		[]string{utils.IntToString(numberOfReplicas), utils.IntToString(timeout.Milliseconds())},
		reflect.Int64,
		false,
	)
}

// Counts the number of set bits (population counting) in a string stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key for the string to count the set bits of.
//
// Command Response:
//
//	The number of set bits in the string. Returns `0` if the key is missing as it is
//	treated as an empty string.
//
// [valkey.io]: https://valkey.io/commands/bitcount/
func (b *BaseBatch[T]) BitCount(key string) *T {
	return b.addCmdAndTypeChecker(C.BitCount, []string{key}, reflect.Int64, false)
}

// Performs a bitwise operation between multiple keys (containing string values) and store the result in the destination.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	bitwiseOperation - The bitwise operation to perform.
//	destination      - The key that will store the resulting string.
//	keys             - The list of keys to perform the bitwise operation on.
//
// Command Response:
//
//	The size of the string stored in destination.
//
// [valkey.io]: https://valkey.io/commands/bitop/
func (b *BaseBatch[T]) BitOp(bitwiseOperation options.BitOpType, destination string, keys []string) *T {
	bitOp, err := options.NewBitOp(bitwiseOperation, destination, keys)
	if err != nil {
		return b.addError("BitOp", err)
	}
	args, err := bitOp.ToArgs()
	if err != nil {
		return b.addError("BitOp", err)
	}
	return b.addCmdAndTypeChecker(C.BitOp, args, reflect.Int64, false)
}

// Counts the number of set bits (population counting) in a string stored at key. The
// offsets start and end are zero-based indexes, with `0` being the first element of the
// list, `1` being the next element and so on. These offsets can also be negative numbers
// indicating offsets starting at the end of the list, with `-1` being the last element
// of the list, `-2` being the penultimate, and so on.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key for the string to count the set bits of.
//	options - The offset options - see [options.BitCountOptions].
//
// Command Response:
//
//	The number of set bits in the string interval specified by start, end, and options.
//	Returns zero if the key is missing as it is treated as an empty string.
//
// [valkey.io]: https://valkey.io/commands/bitcount/
func (b *BaseBatch[T]) BitCountWithOptions(key string, opts options.BitCountOptions) *T {
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("BitCountWithOptions", err)
	}
	commandArgs := append([]string{key}, optionArgs...)
	return b.addCmdAndTypeChecker(C.BitCount, commandArgs, reflect.Int64, false)
}

// Changes the ownership of a pending message.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time in milliseconds.
//	ids         - The ids of the entries to claim.
//
// Command Response:
//
//	A map[string]models.XClaimResponse where:
//	- Each key is a message/entry ID
//	- Each value is an XClaimResponse containing:
//	  - Fields: []FieldValue array of field-value pairs for the claimed entry
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (b *BaseBatch[T]) XClaim(key string, group string, consumer string, minIdleTime time.Duration, ids []string) *T {
	return b.XClaimWithOptions(key, group, consumer, minIdleTime, ids, *options.NewXClaimOptions())
}

// Changes the ownership of a pending message with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time in milliseconds.
//	ids         - The ids of the entries to claim.
//	options     - Stream claim options.
//
// Command Response:
//
//	A map[string]models.XClaimResponse where:
//	- Each key is a message/entry ID
//	- Each value is an XClaimResponse containing:
//	  - Fields: []FieldValue array of field-value pairs for the claimed entry
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (b *BaseBatch[T]) XClaimWithOptions(
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	ids []string,
	opts options.XClaimOptions,
) *T {
	args := append([]string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds())}, ids...)
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("XClaimWithOptions", err)
	}
	args = append(args, optionArgs...)
	return b.addCmdAndConverter(C.XClaim, args, reflect.Map, false, internal.ConvertXClaimResponse)
}

// Changes the ownership of a pending message. This function returns an `array` with
// only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time in milliseconds.
//	ids         - The ids of the entries to claim.
//
// Command Response:
//
//	A map of message entries with the format `{"entryId": [["entry", "data"], ...], ...}` that were claimed by
//	the consumer.
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (b *BaseBatch[T]) XClaimJustId(key string, group string, consumer string, minIdleTime time.Duration, ids []string) *T {
	return b.XClaimJustIdWithOptions(key, group, consumer, minIdleTime, ids, *options.NewXClaimOptions())
}

// Changes the ownership of a pending message. This function returns an `array` with
// only the message/entry IDs, and is equivalent to using `JUSTID` in the Valkey API.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key         - The key of the stream.
//	group       - The name of the consumer group.
//	consumer    - The name of the consumer.
//	minIdleTime - The minimum idle time in milliseconds.
//	ids         - The ids of the entries to claim.
//	options     - Stream claim options.
//
// Command Response:
//
//	An array of the ids of the entries that were claimed by the consumer.
//
// [valkey.io]: https://valkey.io/commands/xclaim/
func (b *BaseBatch[T]) XClaimJustIdWithOptions(
	key string,
	group string,
	consumer string,
	minIdleTime time.Duration,
	ids []string,
	opts options.XClaimOptions,
) *T {
	args := append([]string{key, group, consumer, utils.IntToString(minIdleTime.Milliseconds())}, ids...)
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("XClaimJustIdWithOptions", err)
	}
	args = append(args, optionArgs...)
	args = append(args, constants.JustIdKeyword)
	return b.addCmdAndConverter(C.XClaim, args, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the position of the first bit matching the given bit value.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the string.
//	bit - The bit value to match. The value must be `0` or `1`.
//
// Command Response:
//
//	The position of the first occurrence matching bit in the binary value of
//	the string held at key. If bit is not found, a `-1` is returned.
//
// [valkey.io]: https://valkey.io/commands/bitpos/
func (b *BaseBatch[T]) BitPos(key string, bit int64) *T {
	return b.addCmdAndTypeChecker(C.BitPos, []string{key, utils.IntToString(bit)}, reflect.Int64, false)
}

// Returns the position of the first bit matching the given bit value.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the string.
//	bit - The bit value to match. The value must be `0` or `1`.
//	bitposOptions - The [options.BitPosOptions] type.
//
// Command Response:
//
//	The position of the first occurrence matching bit in the binary value of
//	the string held at key. If bit is not found, a `-1` is returned.
//
// [valkey.io]: https://valkey.io/commands/bitpos/
func (b *BaseBatch[T]) BitPosWithOptions(key string, bit int64, bitposOptions options.BitPosOptions) *T {
	optionArgs, err := bitposOptions.ToArgs()
	if err != nil {
		return b.addError("BitPosWithOptions", err)
	}
	commandArgs := append([]string{key, utils.IntToString(bit)}, optionArgs...)
	return b.addCmdAndTypeChecker(C.BitPos, commandArgs, reflect.Int64, false)
}

// Copies the value stored at the source to the destination key if the
// destination key does not yet exist.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	source - The key to the source value.
//	destination - The key where the value should be copied to.
//
// Command Response:
//
//	`true` if source was copied, `false` if source was not copied.
//
// [valkey.io]: https://valkey.io/commands/copy/
func (b *BaseBatch[T]) Copy(source string, destination string) *T {
	return b.addCmdAndTypeChecker(C.Copy, []string{source, destination}, reflect.Bool, false)
}

// Copies the value stored at the source to the destination key. When
// `replace` in `options` is `true`, removes the destination key first if it already
// exists, otherwise performs no action.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	source - The key to the source value.
//	destination - The key where the value should be copied to.
//	options - Set copy options with replace and DB destination-db
//
// Command Response:
//
//	`true` if source was copied, `false` if source was not copied.
//
// [valkey.io]: https://valkey.io/commands/copy/
func (b *BaseBatch[T]) CopyWithOptions(source string, destination string, options options.CopyOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("CopyWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.Copy, append([]string{
		source, destination,
	}, optionArgs...), reflect.Bool, false)
}

// Returns stream entries matching a given range of IDs.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//
// Command Response:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//
// [valkey.io]: https://valkey.io/commands/xrange/
func (b *BaseBatch[T]) XRange(key string, start options.StreamBoundary, end options.StreamBoundary) *T {
	return b.XRangeWithOptions(key, start, end, *options.NewXRangeOptions())
}

// Returns stream entries matching a given range of IDs with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	opts  - Stream range options.
//
// Command Response:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//	Returns `nil` if `count` is non-positive.
//
// [valkey.io]: https://valkey.io/commands/xrange/
func (b *BaseBatch[T]) XRangeWithOptions(
	key string,
	start options.StreamBoundary,
	end options.StreamBoundary,
	opts options.XRangeOptions,
) *T {
	args := []string{key, string(start), string(end)}
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("XRangeWithOptions", err)
	}
	args = append(args, optionArgs...)
	return b.addCmdAndConverter(C.XRange, args, reflect.Map, true, internal.MakeConvertStreamEntryArray(false))
}

// Returns stream entries matching a given range of IDs in reverse order.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//
// Command Response:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//
// [valkey.io]: https://valkey.io/commands/xrevrange/
func (b *BaseBatch[T]) XRevRange(key string, start options.StreamBoundary, end options.StreamBoundary) *T {
	return b.XRevRangeWithOptions(key, start, end, *options.NewXRangeOptions())
}

// Returns stream entries matching a given range of IDs in reverse order with options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the stream.
//	start - The start position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary.
//	end   - The end position.
//	        Use `options.NewStreamBoundary()` to specify a stream entry ID and its inclusive/exclusive status.
//	        Use `options.NewInfiniteStreamBoundary()` to specify an infinite stream boundary..
//	opts  - Stream range options.
//
// Command Response:
//
//	An `array` of [models.StreamEntry], where entry data stores an array of
//	pairings with format `[[field, entry], [field, entry], ...]`.
//	Returns `nil` if `count` is non-positive.
//
// [valkey.io]: https://valkey.io/commands/xrevrange/
func (b *BaseBatch[T]) XRevRangeWithOptions(
	key string,
	start options.StreamBoundary,
	end options.StreamBoundary,
	opts options.XRangeOptions,
) *T {
	args := []string{key, string(start), string(end)}
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("XRevRangeWithOptions", err)
	}
	args = append(args, optionArgs...)
	return b.addCmdAndConverter(C.XRevRange, args, reflect.Map, true, internal.MakeConvertStreamEntryArray(true))
}

// Returns information about the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//
// Command Response:
//
//	A [models.XInfoStreamResponse] containing information about the stream stored at key:
//	- Length: the number of entries in the stream
//	- RadixTreeKeys: the number of keys in the underlying radix data structure
//	- RadixTreeNodes: the number of nodes in the underlying radix data structure
//	- Groups: the number of consumer groups defined for the stream
//	- LastGeneratedID: the ID of the least-recently entry that was added to the stream
//	- MaxDeletedEntryID: the maximal entry ID that was deleted from the stream
//	- EntriesAdded: the count of all entries added to the stream during its lifetime
//	- FirstEntry: the ID and field-value tuples of the first entry in the stream
//	- LastEntry: the ID and field-value tuples of the last entry in the stream
//
// [valkey.io]: https://valkey.io/commands/xinfo-stream/
func (b *BaseBatch[T]) XInfoStream(key string) *T {
	return b.addCmdAndConverter(C.XInfoStream, []string{key}, reflect.Map, false, internal.ConvertXInfoStreamResponse)
}

// Returns detailed information about the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key  - The key of the stream.
//	opts - Stream info options.
//
// Command Response:
//
//	A detailed stream information for the given `key`. See [models.XInfoStreamFullOptionsResponse].
//
// [valkey.io]: https://valkey.io/commands/xinfo-stream/
func (b *BaseBatch[T]) XInfoStreamFullWithOptions(key string, opts *options.XInfoStreamOptions) *T {
	args := []string{key, constants.FullKeyword}
	if opts != nil {
		optionArgs, err := opts.ToArgs()
		if err != nil {
			return b.addError("XInfoStreamFullWithOptions", err)
		}
		args = append(args, optionArgs...)
	}
	return b.addCmdAndConverter(C.XInfoStream, args, reflect.Map, false, internal.ConvertXInfoStreamFullResponse)
}

// Returns the list of all consumers and their attributes for the given consumer group of the
// stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key   - The key of the stream.
//	group - The consumer group name.
//
// Command Response:
//
//	An array of [models.XInfoConsumerInfo], where each element contains the attributes
//	of a consumer for the given consumer group of the stream at `key`.
//
// [valkey.io]: https://valkey.io/commands/xinfo-consumers/
func (b *BaseBatch[T]) XInfoConsumers(key string, group string) *T {
	return b.addCmdAndConverter(
		C.XInfoConsumers,
		[]string{key, group},
		reflect.Slice,
		false,
		internal.ConvertXInfoConsumersResponse,
	)
}

// Returns the list of all consumer groups and their attributes for the stream stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the stream.
//
// Command Response:
//
//	An array of [models.XInfoGroupInfo], where each element represents the
//	attributes of a consumer group for the stream at `key`.
//
// [valkey.io]: https://valkey.io/commands/xinfo-groups/
func (b *BaseBatch[T]) XInfoGroups(key string) *T {
	return b.addCmdAndConverter(C.XInfoGroups, []string{key}, reflect.Slice, false, internal.ConvertXInfoGroupsResponse)
}

// Reads or modifies the array of bits representing the string that is held at key
// based on the specified sub commands.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key          - The key of the string.
//	subCommands  - The subCommands to be performed on the binary value of the string at
//	                key, which could be any of the following:
//	                  - [BitFieldGet].
//	                  - [BitFieldSet].
//	                  - [BitFieldIncrby].
//	                  - [BitFieldOverflow].
//		            Use `options.NewBitFieldGet()` to specify a  BitField GET command.
//		            Use `options.NewBitFieldSet()` to specify a BitField SET command.
//		            Use `options.NewBitFieldIncrby()` to specify a BitField INCRYBY command.
//		            Use `options.BitFieldOverflow()` to specify a BitField OVERFLOW command.
//
// Command Response:
//
//	Result from the executed subcommands.
//	  - BitFieldGet returns the value in the binary representation of the string.
//	  - BitFieldSet returns the previous value before setting the new value in the binary representation.
//	  - BitFieldIncrBy returns the updated value after increasing or decreasing the bits.
//	  - BitFieldOverflow controls the behavior of subsequent operations and returns
//	    a result based on the specified overflow type (WRAP, SAT, FAIL).
//
// [valkey.io]: https://valkey.io/commands/bitfield/
func (b *BaseBatch[T]) BitField(key string, subCommands []options.BitFieldSubCommands) *T {
	args := make([]string, 0, 10)
	args = append(args, key)

	for _, cmd := range subCommands {
		cmdArgs, err := cmd.ToArgs()
		if err != nil {
			return b.addError("BitField", err)
		}
		args = append(args, cmdArgs...)
	}

	return b.addCmdAndConverter(C.BitField, args, reflect.Slice, false, internal.ConvertArrayOfNilOr[int64])
}

// Reads the array of bits representing the string that is held at key
// based on the specified sub commands.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key          - The key of the string.
//	subCommands  - The read-only subCommands to be performed on the binary value
//	               of the string at key, which could be:
//	                 - [BitFieldGet].
//		           Use `options.NewBitFieldGet()` to specify a BitField GET command.
//
// Command Response:
//
//	Result from the executed GET subcommands.
//	  - BitFieldGet returns the value in the binary representation of the string.
//
// [valkey.io]: https://valkey.io/commands/bitfield_ro/
func (b *BaseBatch[T]) BitFieldRO(key string, subCommands []options.BitFieldROCommands) *T {
	args := make([]string, 0, 10)
	args = append(args, key)

	for _, cmd := range subCommands {
		cmdArgs, err := cmd.ToArgs()
		if err != nil {
			return b.addError("BitFieldRO", err)
		}
		args = append(args, cmdArgs...)
	}

	return b.addCmdAndConverter(C.BitFieldReadOnly, args, reflect.Slice, false, internal.ConvertArrayOfNilOr[int64])
}

// Returns the server time.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	The current server time as a String array with two elements:
//	A UNIX TIME and the amount of microseconds already elapsed in the current second.
//	The returned array is in a [UNIX TIME, Microseconds already elapsed] format.
//
// [valkey.io]: https://valkey.io/commands/time/
func (b *BaseBatch[T]) Time() *T {
	return b.addCmdAndConverter(C.Time, []string{}, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the intersection of members from sorted sets specified by the given `keys`.
// To get the elements with their scores, see [BaseBatch.ZInterWithScores].
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sorted sets, see - [options.KeyArray].
//
// Command Response:
//
//	The resulting sorted set from the intersection.
//
// [valkey.io]: https://valkey.io/commands/zinter/
func (b *BaseBatch[T]) ZInter(keys options.KeyArray) *T {
	args, err := keys.ToArgs()
	if err != nil {
		return b.addError("ZInter", err)
	}
	return b.addCmdAndConverter(C.ZInter, args, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the intersection of members and their scores from sorted sets specified by the given
// `keysOrWeightedKeys`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//	options - The options for the ZInter command, see - [options.ZInterOptions].
//	           Optional `aggregate` option specifies the aggregation strategy to apply when combining the scores of
//	           elements.
//
// Command Response:
//
//	An array of member and score pairs from the sorted set.
//	If the sorted set does not exist or is empty, the response will be an empty array.
//
// [valkey.io]: https://valkey.io/commands/zinter/
func (b *BaseBatch[T]) ZInterWithScores(
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zInterOptions options.ZInterOptions,
) *T {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return b.addError("ZInterWithScores", err)
	}
	optionsArgs, err := zInterOptions.ToArgs()
	if err != nil {
		return b.addError("ZInterWithScores", err)
	}
	args = append(args, optionsArgs...)
	args = append(args, constants.WithScoresKeyword)

	needsReverse := false
	for _, arg := range args {
		if arg == "REV" {
			needsReverse = true
			break
		}
	}
	return b.addCmdAndConverter(C.ZInter, args, reflect.Map, false, internal.MakeConvertMapOfMemberAndScore(needsReverse))
}

// Computes the intersection of sorted sets given by the specified `keysOrWeightedKeys`
// and stores the result in `destination`. If `destination` already exists, it is overwritten.
// Otherwise, a new sorted set will be created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The destination key for the result.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//
// Command Response:
//
//	The number of elements in the resulting sorted set at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zinterstore/
func (b *BaseBatch[T]) ZInterStore(
	destination string,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
) *T {
	return b.ZInterStoreWithOptions(destination, keysOrWeightedKeys, *options.NewZInterOptions())
}

// Computes the intersection of sorted sets given by the specified `keysOrWeightedKeys`
// and stores the result in `destination`. If `destination` already exists, it is overwritten.
// Otherwise, a new sorted set will be created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The destination key for the result.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//	options - The options for the ZInterStore command, see - [options.ZInterOptions].
//	          Optional `aggregate` option specifies the aggregation strategy to apply when combining the scores of
//	          elements.
//
// Command Response:
//
//	The number of elements in the resulting sorted set at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zinterstore/
func (b *BaseBatch[T]) ZInterStoreWithOptions(
	destination string,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zInterOptions options.ZInterOptions,
) *T {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return b.addError("ZInterStore", err)
	}
	args = append([]string{destination}, args...)
	optionsArgs, err := zInterOptions.ToArgs()
	if err != nil {
		return b.addError("ZInterStore", err)
	}
	args = append(args, optionsArgs...)
	return b.addCmdAndTypeChecker(C.ZInterStore, args, reflect.Int64, false)
}

// Returns the difference between the first sorted set and all the successive sorted sets.
// To get the elements with their scores, see [BaseBatch.ZDiffWithScores].
//
// See [valkey.io] for details.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Parameters:
//
//	keys - The keys of the sorted sets.
//
// Command Response:
//
//	An array of elements representing the difference between the sorted sets.
//	If the first `key` does not exist, it is treated as an empty sorted set, and the
//	command returns an empty array.
//
// [valkey.io]: https://valkey.io/commands/zdiff/
func (b *BaseBatch[T]) ZDiff(keys []string) *T {
	args := append([]string{}, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	return b.addCmdAndConverter(C.ZDiff, args, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the difference between the first sorted set and all the successive sorted sets.
//
// See [valkey.io] for details.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Parameters:
//
//	keys - The keys of the sorted sets.
//
// Command Response:
//
//	An map of elements and their scores representing the difference between the sorted sets.
//	If the first `key` does not exist, it is treated as an empty sorted set, and the
//	command returns an empty map.
//
// [valkey.io]: https://valkey.io/commands/zdiff/
func (b *BaseBatch[T]) ZDiffWithScores(keys []string) *T {
	args := append([]string{}, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, constants.WithScoresKeyword)
	return b.addCmdAndConverter(C.ZDiff, args, reflect.Map, false, internal.MakeConvertMapOfMemberAndScore(false))
}

// Calculates the difference between the first sorted set and all the successive sorted sets at
// `keys` and stores the difference as a sorted set to `destination`,
// overwriting it if it already exists. Non-existent keys are treated as empty sets.
//
// See [valkey.io] for details.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Parameters:
//
//	destination - The key where the resulting sorted set will be stored.
//	keys - The keys of the sorted sets.
//
// Command Response:
//
//	The number of elements in the resulting sorted set at destination.
//
// [valkey.io]: https://valkey.io/commands/zdiffstore/
func (b *BaseBatch[T]) ZDiffStore(destination string, keys []string) *T {
	return b.addCmdAndTypeChecker(C.ZDiffStore,
		append([]string{destination, strconv.Itoa(len(keys))}, keys...),
		reflect.Int64,
		false,
	)
}

// Returns the union of members from sorted sets specified by the given `keys`.
// To get the elements with their scores, see [BaseBatch.ZUnionWithScores].
//
// See [valkey.io] for details.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// Parameters:
//
//	keys - The keys of the sorted sets.
//
// Command Response:
//
//	The resulting sorted set from the union.
//
// [valkey.io]: https://valkey.io/commands/zunion/
func (b *BaseBatch[T]) ZUnion(keys options.KeyArray) *T {
	args, err := keys.ToArgs()
	if err != nil {
		return b.addError("ZUnion", err)
	}
	return b.addCmdAndConverter(C.ZUnion, args, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the union of members and their scores from sorted sets specified by the given
// `keysOrWeightedKeys`.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keysOrWeightedKeys - The keys of the sorted sets with possible formats:
//	                     - Use `KeyArray` for keys only.
//	                     - Use `WeightedKeys` for weighted keys with score multipliers.
//	zUnionOptions - The options for the ZUnionStore command, see - [options.ZUnionOptions].
//	                Optional `aggregate` option specifies the aggregation strategy to apply when
//	                combining the scores of elements.
//
// Command Response:
//
//	The resulting sorted set from the union
//
// [valkey.io]: https://valkey.io/commands/zunion/
func (b *BaseBatch[T]) ZUnionWithScores(
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zUnionOptions options.ZUnionOptions,
) *T {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return b.addError("ZUnionWithScores", err)
	}
	optionsArgs, err := zUnionOptions.ToArgs()
	if err != nil {
		return b.addError("ZUnionWithScores", err)
	}
	args = append(args, optionsArgs...)
	args = append(args, constants.WithScoresKeyword)
	return b.addCmdAndConverter(C.ZUnion, args, reflect.Map, false, internal.MakeConvertMapOfMemberAndScore(false))
}

// Computes the union of sorted sets given by the specified `KeysOrWeightedKeys`, and
// stores the result in `destination`. If `destination` already exists, it
// is overwritten. Otherwise, a new sorted set will be created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The key of the destination sorted set.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                   - Use `options.NewKeyArray()` for keys only.
//	                   - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//
// Command Response:
//
//	The number of elements in the resulting sorted set stored at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zunionstore/
func (b *BaseBatch[T]) ZUnionStore(destination string, keysOrWeightedKeys options.KeysOrWeightedKeys) *T {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return b.addError("ZUnionStoreWithOptions", err)
	}
	args = append([]string{destination}, args...)
	return b.addCmdAndTypeChecker(C.ZUnionStore, args, reflect.Int64, false)
}

// Computes the union of sorted sets given by the specified `KeysOrWeightedKeys`, and
// stores the result in `destination`. If `destination` already exists, it
// is overwritten. Otherwise, a new sorted set will be created.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destination - The key of the destination sorted set.
//	keysOrWeightedKeys - The keys or weighted keys of the sorted sets, see - [options.KeysOrWeightedKeys].
//	                     - Use `options.NewKeyArray()` for keys only.
//	                     - Use `options.NewWeightedKeys()` for weighted keys with score multipliers.
//	zUnionOptions - The options for the ZUnionStore command, see - [options.ZUnionOptions].
//	                Optional `aggregate` option specifies the aggregation strategy to apply when
//	                combining the scores of elements.
//
// Command Response:
//
//	The number of elements in the resulting sorted set stored at `destination`.
//
// [valkey.io]: https://valkey.io/commands/zunionstore/
func (b *BaseBatch[T]) ZUnionStoreWithOptions(
	destination string,
	keysOrWeightedKeys options.KeysOrWeightedKeys,
	zUnionOptions options.ZUnionOptions,
) *T {
	args, err := keysOrWeightedKeys.ToArgs()
	if err != nil {
		return b.addError("ZUnionStoreWithOptions", err)
	}
	args = append([]string{destination}, args...)
	optionsArgs, err := zUnionOptions.ToArgs()
	if err != nil {
		return b.addError("ZUnionStoreWithOptions", err)
	}
	args = append(args, optionsArgs...)
	return b.addCmdAndTypeChecker(C.ZUnionStore, args, reflect.Int64, false)
}

// Pops one or more member-score pairs from the first non-empty sorted set,
// with the given keys being checked in the order provided.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - An array of keys to sorted sets.
//	scoreFilter - Pop criteria - either [constants.MIN] or [constants.MAX] to pop members with the lowest/highest scores.
//
// Command Response:
//
//	A array containing:
//	- The key from which the elements were popped.
//	- An array of member-score pairs of the popped elements.
//	Returns `nil` if no member could be popped.
//
// [valkey.io]: https://valkey.io/commands/zmpop/
func (b *BaseBatch[T]) ZMPop(keys []string, scoreFilter constants.ScoreFilter) *T {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return b.addError("ZMPop", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-2 {
		return b.addError("ZMPop", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 2 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+2)
	args = append(args, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, scoreFilterStr)
	return b.addCmdAndConverter(C.ZMPop, args, reflect.Slice, true, internal.ConvertKeyWithArrayOfMembersAndScores)
}

// Removes and returns up to `count` members from the first non-empty sorted set
// among the provided `keys`, based on the specified `scoreFilter` criteria.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - A list of keys representing sorted sets to check for elements.
//	scoreFilter - Pop criteria - either [constants.MIN] or [constants.MAX] to pop members with the lowest/highest scores.
//	opts - Additional options, such as specifying the maximum number of elements to pop.
//
// Command Response:
//
//	A array containing:
//	- The key from which the elements were popped.
//	- An array of member-score pairs of the popped elements.
//	Returns `nil` if no member could be popped.
//
// [valkey.io]: https://valkey.io/commands/zmpop/
func (b *BaseBatch[T]) ZMPopWithOptions(keys []string, scoreFilter constants.ScoreFilter, opts options.ZMPopOptions) *T {
	scoreFilterStr, err := scoreFilter.ToString()
	if err != nil {
		return b.addError("ZMPopWithOptions", err)
	}

	// Check for potential length overflow.
	if len(keys) > math.MaxInt-4 {
		return b.addError("ZMPopWithOptions", errors.New("length overflow for the provided keys"))
	}

	// args slice will have 4 more arguments with the keys provided.
	args := make([]string, 0, len(keys)+4)
	args = append(args, strconv.Itoa(len(keys)))
	args = append(args, keys...)
	args = append(args, scoreFilterStr)
	optionArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("ZMPopWithOptions", err)
	}
	args = append(args, optionArgs...)
	return b.addCmdAndConverter(C.ZMPop, args, reflect.Slice, true, internal.ConvertKeyWithArrayOfMembersAndScores)
}

// Returns the cardinality of the intersection of the sorted sets specified by `keys`.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sorted sets.
//
// Command Response:
//
//	The cardinality of the intersection of the sorted sets.
//
// [valkey.io]: https://valkey.io/commands/zintercard/
func (b *BaseBatch[T]) ZInterCard(keys []string) *T {
	args := append([]string{strconv.Itoa(len(keys))}, keys...)
	return b.addCmdAndTypeChecker(C.ZInterCard, args, reflect.Int64, false)
}

// Returns the cardinality of the intersection of the sorted sets specified by `keys`.
// If the intersection cardinality reaches `options.limit` partway through the computation, the
// algorithm will exit early and yield `options.limit` as the cardinality.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - The keys of the sorted sets.
//	options - The options for the ZInterCard command, see - [options.ZInterCardOptions].
//
// Command Response:
//
//	The cardinality of the intersection of the sorted sets.
//
// [valkey.io]: https://valkey.io/commands/zintercard/
func (b *BaseBatch[T]) ZInterCardWithOptions(keys []string, options options.ZInterCardOptions) *T {
	args := append([]string{strconv.Itoa(len(keys))}, keys...)
	optionsArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("ZInterCardWithOptions", err)
	}
	args = append(args, optionsArgs...)
	return b.addCmdAndTypeChecker(C.ZInterCard, args, reflect.Int64, false)
}

// Returns the number of elements in the sorted set at key with a value between min and max.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	rangeQuery - The range query to apply to the sorted set.
//
// Command Response:
//
//	The number of elements in the sorted set at key with a value between min and max.
//
// [valkey.io]: https://valkey.io/commands/zlexcount/
func (b *BaseBatch[T]) ZLexCount(key string, rangeQuery options.RangeByLex) *T {
	args := []string{key}
	args = append(args, rangeQuery.ToArgsLexCount()...)
	return b.addCmdAndTypeChecker(C.ZLexCount, args, reflect.Int64, false)
}

// Blocks the connection until it pops and returns a member-score pair
// with the highest score from the first non-empty sorted set.
// The given `keys` being checked in the order they are provided.
// BZPopMax is the blocking variant of [BaseBatch.ZPopMax].
//
// Note:
//
// BZPopMax is a client blocking command, see [Blocking Commands] for more details and best practices.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	keys - An array of keys to check for elements.
//	timeout - The maximum duration to block (`0` blocks indefinitely).
//
// Command Response:
//
//	An array containing the key where the member was popped out, the member itself, and the member score.
//	If no member could be popped and the `timeout` expired, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/bzpopmax/
// [Blocking Commands]: https://glide.valkey.io/how-to/connection-management/#blocking-commands
func (b *BaseBatch[T]) BZPopMax(keys []string, timeout time.Duration) *T {
	args := append(keys, utils.FloatToString(timeout.Seconds()))
	return b.addCmdAndConverter(C.BZPopMax, args, reflect.Slice, true, internal.ConvertKeyWithMemberAndScore)
}

// Adds geospatial members with their positions to the specified sorted set stored at `key`.
// If a member is already a part of the sorted set, its position is updated.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	membersToGeospatialData - A map of member names to their corresponding positions. See [options.GeospatialData].
//	  The command will report an error when index coordinates are out of the specified range.
//
// Command Response:
//
//	The number of elements added to the sorted set.
//
// [valkey.io]: https://valkey.io/commands/geoadd/
func (b *BaseBatch[T]) GeoAdd(key string, membersToGeospatialData map[string]options.GeospatialData) *T {
	return b.addCmdAndTypeChecker(C.GeoAdd,
		append([]string{key}, options.MapGeoDataToArray(membersToGeospatialData)...),
		reflect.Int64,
		false,
	)
}

// Adds geospatial members with their positions to the specified sorted set stored at `key`.
// If a member is already a part of the sorted set, its position is updated.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	membersToGeospatialData - A map of member names to their corresponding positions. See [options.GeospatialData].
//	  The command will report an error when index coordinates are out of the specified range.
//	geoAddOptions - The options for the GeoAdd command, see - [options.GeoAddOptions].
//
// Command Response:
//
//	The number of elements added to the sorted set.
//
// [valkey.io]: https://valkey.io/commands/geoadd/
func (b *BaseBatch[T]) GeoAddWithOptions(
	key string,
	membersToGeospatialData map[string]options.GeospatialData,
	geoAddOptions options.GeoAddOptions,
) *T {
	args := []string{key}
	optionsArgs, err := geoAddOptions.ToArgs()
	if err != nil {
		return b.addError("GeoAddWithOptions", err)
	}
	args = append(args, optionsArgs...)
	args = append(args, options.MapGeoDataToArray(membersToGeospatialData)...)
	return b.addCmdAndTypeChecker(C.GeoAdd, args, reflect.Int64, false)
}

// Returns the GeoHash strings representing the positions of all the specified
// `members` in the sorted set stored at the `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	members - The members to get the geohash for.
//
// Command Response:
//
//	An array of GeoHash strings (of type models.Result[string]) representing the positions of the specified
//	members stored at key. If a member does not exist in the sorted set, a `nil` value is returned
//	for that member.
//
// [valkey.io]: https://valkey.io/commands/geohash/
func (b *BaseBatch[T]) GeoHash(key string, members []string) *T {
	return b.addCmdAndConverter(C.GeoHash,
		append([]string{key}, members...),
		reflect.Slice,
		false,
		internal.ConvertArrayOfNilOr[string],
	)
}

// Returns the positions (longitude,latitude) of all the specified members of the
// geospatial index represented by the sorted set at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	members - The members of the sorted set.
//
// Command Response:
//
//	A 2D array which represent positions (longitude and latitude) corresponding to the given members.
//	If a member does not exist, its position will be `nil`.
//
// [valkey.io]: https://valkey.io/commands/geopos/
func (b *BaseBatch[T]) GeoPos(key string, members []string) *T {
	args := []string{key}
	args = append(args, members...)
	return b.addCmdAndConverter(C.GeoPos, args, reflect.Slice, false, internal.Convert2DArrayOfFloat)
}

// Returns the distance between `member1` and `member2` saved in the
// geospatial index stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member1 - The name of the first member.
//	member2 - The name of the second member.
//
// Command Response:
//
//	The distance between `member1` and `member2`. If one or both members do not exist,
//	or if the key does not exist, returns `nil`. The default unit is meters, see - [options.Meters]
//
// [valkey.io]: https://valkey.io/commands/geodist/
func (b *BaseBatch[T]) GeoDist(key string, member1 string, member2 string) *T {
	return b.addCmdAndTypeChecker(C.GeoDist,
		[]string{key, member1, member2},
		reflect.Float64,
		true,
	)
}

// Returns the distance between `member1` and `member2` saved in the
// geospatial index stored at `key`.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	member1 - The name of the first member.
//	member2 - The name of the second member.
//	unit - The unit of distance.
//
// Command Response:
//
//	The distance between member1 and member2 in the specified unit.
//	If one or both members do not exist, or if the key does not exist, returns `nil`.
//
// [valkey.io]: https://valkey.io/commands/geodist/
func (b *BaseBatch[T]) GeoDistWithUnit(key string, member1 string, member2 string, unit constants.GeoUnit) *T {
	return b.addCmdAndTypeChecker(C.GeoDist,
		[]string{key, member1, member2, string(unit)},
		reflect.Float64,
		true,
	)
}

// Returns the members of a sorted set populated with geospatial information using [BaseBatch.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//	infoOptions - The optional inputs to request additional information.
//
// Command Response:
//
//	An array of [options.Location] containing the following information:
//	 - The coordinates as a [options.GeospatialData] object.
//	 - The member (location) name.
//	 - The distance from the center as a `float64`, in the same unit specified for `searchByShape`.
//	 - The geohash of the location as a `int64`.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (b *BaseBatch[T]) GeoSearchWithFullOptions(
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
	infoOptions options.GeoSearchInfoOptions,
) *T {
	args := []string{key}
	searchFromArgs, err := searchFrom.ToArgs()
	if err != nil {
		return b.addError("GeoSearchWithFullOptions", err)
	}
	args = append(args, searchFromArgs...)
	searchByShapeArgs, err := searchByShape.ToArgs()
	if err != nil {
		return b.addError("GeoSearchWithFullOptions", err)
	}
	args = append(args, searchByShapeArgs...)
	infoOptionsArgs, err := infoOptions.ToArgs()
	if err != nil {
		return b.addError("GeoSearchWithFullOptions", err)
	}
	args = append(args, infoOptionsArgs...)
	resultOptionsArgs, err := resultOptions.ToArgs()
	if err != nil {
		return b.addError("GeoSearchWithFullOptions", err)
	}
	args = append(args, resultOptionsArgs...)
	return b.addCmdAndConverter(C.GeoSearch, args, reflect.Slice, false, internal.ConvertLocationArrayResponse)
}

// Returns the members of a sorted set populated with geospatial information using [BaseBatch.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted
//	         set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//
// Command Response:
//
//	An array of members that are within the specified area.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (b *BaseBatch[T]) GeoSearch(
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
) *T {
	return b.GeoSearchWithResultOptions(key, searchFrom, searchByShape, *options.NewGeoSearchResultOptions())
}

// Returns the members of a sorted set populated with geospatial information using [BaseBatch.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//
// Command Response:
//
//	An array of matched member names.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (b *BaseBatch[T]) GeoSearchWithResultOptions(
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
) *T {
	args := []string{key}
	searchFromArgs, err := searchFrom.ToArgs()
	if err != nil {
		return b.addError("GeoSearchWithResultOptions", err)
	}
	args = append(args, searchFromArgs...)
	searchByShapeArgs, err := searchByShape.ToArgs()
	if err != nil {
		return b.addError("GeoSearchWithResultOptions", err)
	}
	args = append(args, searchByShapeArgs...)
	resultOptionsArgs, err := resultOptions.ToArgs()
	if err != nil {
		return b.addError("GeoSearchWithResultOptions", err)
	}
	args = append(args, resultOptionsArgs...)

	return b.addCmdAndConverter(C.GeoSearch, args, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the members of a sorted set populated with geospatial information using [BaseBatch.GeoAdd],
// which are within the borders of the area specified by a given shape.
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	key - The key of the sorted set.
//	searchFrom - The query's center point options, could be one of:
//		- `MemberOrigin` to use the position of the given existing member in the sorted set.
//		- `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		- `BYRADIUS` to search inside circular area according to given radius.
//		- `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	infoOptions - The optional inputs to request additional information.
//
// Command Response:
//
//	An array of arrays containing the following information:
//	 - The coordinates.
//	 - The member (location) name.
//	 - The distance from the center as a `float64`, in the same unit specified for `searchByShape`.
//	 - The geohash of the location as a `int64`.
//
// [valkey.io]: https://valkey.io/commands/geosearch/
func (b *BaseBatch[T]) GeoSearchWithInfoOptions(
	key string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	infoOptions options.GeoSearchInfoOptions,
) *T {
	return b.GeoSearchWithFullOptions(
		key,
		searchFrom,
		searchByShape,
		*options.NewGeoSearchResultOptions(),
		infoOptions,
	)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [BaseBatch.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//	infoOptions - The optional inputs to request additional information.
//
// Command Response:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (b *BaseBatch[T]) GeoSearchStoreWithFullOptions(
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
	infoOptions options.GeoSearchStoreInfoOptions,
) *T {
	args := []string{destinationKey, sourceKey}
	searchFromArgs, err := searchFrom.ToArgs()
	if err != nil {
		return b.addError("GeoSearchStoreWithFullOptions", err)
	}
	args = append(args, searchFromArgs...)
	searchByShapeArgs, err := searchByShape.ToArgs()
	if err != nil {
		return b.addError("GeoSearchStoreWithFullOptions", err)
	}
	args = append(args, searchByShapeArgs...)
	infoOptionsArgs, err := infoOptions.ToArgs()
	if err != nil {
		return b.addError("GeoSearchStoreWithFullOptions", err)
	}
	args = append(args, infoOptionsArgs...)
	resultOptionsArgs, err := resultOptions.ToArgs()
	if err != nil {
		return b.addError("GeoSearchStoreWithFullOptions", err)
	}
	args = append(args, resultOptionsArgs...)
	return b.addCmdAndTypeChecker(C.GeoSearchStore, args, reflect.Int64, false)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [BaseBatch.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//
// Command Response:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (b *BaseBatch[T]) GeoSearchStore(
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
) *T {
	return b.GeoSearchStoreWithFullOptions(
		destinationKey,
		sourceKey,
		searchFrom,
		searchByShape,
		*options.NewGeoSearchResultOptions(),
		*options.NewGeoSearchStoreInfoOptions(),
	)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [BaseBatch.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	resultOptions - Optional inputs for sorting/limiting the results.
//
// Command Response:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (b *BaseBatch[T]) GeoSearchStoreWithResultOptions(
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	resultOptions options.GeoSearchResultOptions,
) *T {
	return b.GeoSearchStoreWithFullOptions(
		destinationKey,
		sourceKey,
		searchFrom,
		searchByShape,
		resultOptions,
		*options.NewGeoSearchStoreInfoOptions(),
	)
}

// Searches for members in a sorted set stored at `sourceKey` representing geospatial data
// within a circular or rectangular area and stores the result in `destinationKey`. If
// `destinationKey` already exists, it is overwritten. Otherwise, a new sorted set will be
// created. To get the result directly, see [BaseBatch.GeoSearchWithFullOptions].
//
// Since:
//
//	Valkey 6.2.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	destinationKey - The key of the sorted set to store the result.
//	sourceKey - The key of the sorted set to search.
//	searchFrom - The query's center point options, could be one of:
//		 - `MemberOrigin` to use the position of the given existing member in the sorted set.
//		 - `CoordOrigin` to use the given longitude and latitude coordinates.
//	searchByShape - The query's shape options:
//		 - `BYRADIUS` to search inside circular area according to given radius.
//		 - `BYBOX` to search inside an axis-aligned rectangle, determined by height and width.
//	infoOptions - The optional inputs to request additional information.
//
// Command Response:
//
//	The number of elements in the resulting set.
//
// [valkey.io]: https://valkey.io/commands/geosearchstore/
func (b *BaseBatch[T]) GeoSearchStoreWithInfoOptions(
	destinationKey string,
	sourceKey string,
	searchFrom options.GeoSearchOrigin,
	searchByShape options.GeoSearchShape,
	infoOptions options.GeoSearchStoreInfoOptions,
) *T {
	return b.GeoSearchStoreWithFullOptions(
		destinationKey,
		sourceKey,
		searchFrom,
		searchByShape,
		*options.NewGeoSearchResultOptions(),
		infoOptions,
	)
}

// Loads a library to Valkey.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	libraryCode - The source code that implements the library.
//	replace - Whether the given library should overwrite a library with the same name if it already exists.
//
// Command Response:
//
//	The library name that was loaded.
//
// [valkey.io]: https://valkey.io/commands/function-load/
func (b *BaseBatch[T]) FunctionLoad(libraryCode string, replace bool) *T {
	args := []string{}
	if replace {
		args = append(args, constants.ReplaceKeyword)
	}
	args = append(args, libraryCode)
	return b.addCmdAndTypeChecker(C.FunctionLoad, args, reflect.String, false)
}

// Deletes a library and all its functions.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	libName - The library name to delete.
//
// Command Response:
//
//	"OK" if the library exists, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/function-delete/
func (b *BaseBatch[T]) FunctionDelete(libName string) *T {
	return b.addCmdAndTypeChecker(C.FunctionDelete, []string{libName}, reflect.String, false)
}

// Deletes all function libraries.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	OK if the libraries were deleted successfully.
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (b *BaseBatch[T]) FunctionFlush() *T {
	return b.addCmdAndTypeChecker(C.FunctionFlush, []string{}, reflect.String, false)
}

// Deletes all function libraries in synchronous mode.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	OK
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (b *BaseBatch[T]) FunctionFlushSync() *T {
	return b.addCmdAndTypeChecker(C.FunctionFlush, []string{string(options.SYNC)}, reflect.String, false)
}

// Deletes all function libraries in asynchronous mode.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	OK
//
// [valkey.io]: https://valkey.io/commands/function-flush/
func (b *BaseBatch[T]) FunctionFlushAsync() *T {
	return b.addCmdAndTypeChecker(C.FunctionFlush, []string{string(options.ASYNC)}, reflect.String, false)
}

// Invokes a previously loaded function.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	function - The function name.
//	keys - The keys that the function will operate on.
//	args - The arguments to pass to the function.
//
// Command Response:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall/
func (b *BaseBatch[T]) FCall(function string) *T {
	return b.addCmd(C.FCall, []string{function, "0"})
}

// Invokes a previously loaded function.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	function - The function name.
//	keys - An `array` of keys accessed by the function. To ensure the correct
//	   execution of functions, both in standalone and clustered deployments, all names of keys
//	   that a function accesses must be explicitly provided as `keys`.
//	arguments - An `array` of `function` arguments. `arguments` should not represent names of keys.
//
// Command Response:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall/
func (b *BaseBatch[T]) FCallWithKeysAndArgs(function string, keys []string, args []string) *T {
	commandArgs := []string{function, strconv.Itoa(len(keys))}
	commandArgs = append(commandArgs, keys...)
	commandArgs = append(commandArgs, args...)
	return b.addCmd(C.FCall, commandArgs)
}

// Invokes a previously loaded read-only function.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	function - The function name.
//	keys - The keys that the function will operate on.
//	args - The arguments to pass to the function.
//
// Command Response:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall_ro/
func (b *BaseBatch[T]) FCallReadOnly(function string) *T {
	return b.addCmd(C.FCallReadOnly, []string{function, "0"})
}

// Invokes a previously loaded read-only function.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	function - The function name.
//	keys - An `array` of keys accessed by the function. To ensure the correct
//	   execution of functions, both in standalone and clustered deployments, all names of keys
//	   that a function accesses must be explicitly provided as `keys`.
//	arguments - An `array` of `function` arguments. `arguments` should not represent names of keys.
//
// Command Response:
//
//	The invoked function's return value.
//
// [valkey.io]: https://valkey.io/commands/fcall_ro/
func (b *BaseBatch[T]) FCallReadOnlyWithKeysAndArgs(function string, keys []string, args []string) *T {
	commandArgs := []string{function, strconv.Itoa(len(keys))}
	commandArgs = append(commandArgs, keys...)
	commandArgs = append(commandArgs, args...)
	return b.addCmd(C.FCallReadOnly, commandArgs)
}

// Returns information about the functions and libraries.
//
// See [valkey.io] for details.
//
// Since:
//
//	Valkey 7.0 and above.
//
// Parameters:
//
//	query - The query to use to filter the functions and libraries.
//
// Command Response:
//
//	A list of info about queried libraries and their functions.
//
// [valkey.io]: https://valkey.io/commands/function-list/
func (b *BaseBatch[T]) FunctionList(query models.FunctionListQuery) *T {
	return b.addCmdAndConverter(C.FunctionList, query.ToArgs(), reflect.Slice, false, internal.ConvertFunctionListResponse)
}

// Returns the serialized payload of all loaded libraries.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	The serialized payload of all loaded libraries.
//
// [valkey.io]: https://valkey.io/commands/function-dump/
func (b *BaseBatch[T]) FunctionDump() *T {
	return b.addCmdAndTypeChecker(C.FunctionDump, []string{}, reflect.String, false)
}

// Restores libraries from the serialized payload returned by [BaseBatch.FunctionDump].
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	payload - The serialized data from [FunctionDump].
//
// Command Response:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (b *BaseBatch[T]) FunctionRestore(payload string) *T {
	return b.addCmdAndTypeChecker(C.FunctionRestore, []string{payload}, reflect.String, false)
}

// Restores libraries from the serialized payload returned by [BaseBatch.FunctionDump].
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	payload - The serialized data from [FunctionDump].
//	policy - A policy for handling existing libraries.
//
// Command Response:
//
//	`OK`
//
// [valkey.io]: https://valkey.io/commands/function-restore/
func (b *BaseBatch[T]) FunctionRestoreWithPolicy(payload string, policy constants.FunctionRestorePolicy) *T {
	return b.addCmdAndTypeChecker(C.FunctionRestore, []string{payload, string(policy)}, reflect.String, false)
}

// Lists the currently active channels.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	An array of active channel names.
//
// [valkey.io]: https://valkey.io/commands/pubsub-channels
func (b *BaseBatch[T]) PubSubChannels() *T {
	return b.addCmdAndConverter(C.PubSubChannels, []string{}, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Lists the currently active channels matching the specified pattern.
//
// Pattern can be any glob-style pattern:
// - h?llo matches hello, hallo and hxllo
// - h*llo matches hllo and heeeello
// - h[ae]llo matches hello and hallo, but not hillo
//
// See [valkey.io] for details.
//
// Parameters:
//
//	pattern - The pattern to match channel names against.
//
// Command Response:
//
//	An array of active channel names matching the pattern.
//
// [valkey.io]: https://valkey.io/commands/pubsub-channels
func (b *BaseBatch[T]) PubSubChannelsWithPattern(pattern string) *T {
	return b.addCmdAndConverter(C.PubSubChannels, []string{pattern}, reflect.Slice, false, internal.ConvertArrayOf[string])
}

// Returns the number of patterns that are subscribed to by clients.
//
// This returns the total number of unique patterns that all clients are subscribed to,
// not the count of clients subscribed to patterns.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	The number of patterns that are subscribed to by clients.
//
// [valkey.io]: https://valkey.io/commands/pubsub-numpat
func (b *BaseBatch[T]) PubSubNumPat() *T {
	return b.addCmdAndTypeChecker(C.PubSubNumPat, []string{}, reflect.Int64, false)
}

// Returns the number of subscribers for the specified channels.
//
// The count only includes clients subscribed to exact channels, not pattern subscriptions.
// If no channels are specified, an empty map is returned.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	channels - The channels to get the number of subscribers for.
//
// Command Response:
//
//	A map of channel names to the number of subscribers.
//
// [valkey.io]: https://valkey.io/commands/pubsub-numsub
func (b *BaseBatch[T]) PubSubNumSub(channels []string) *T {
	return b.addCmdAndConverter(C.PubSubNumSub, channels, reflect.Map, false, internal.ConvertMapOf[int64])
}

// Kills a function that is currently executing.
//
// `FUNCTION KILL` terminates read-only functions only.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	`OK` if function is terminated. Otherwise, throws an error.
//
// [valkey.io]: https://valkey.io/commands/function-kill/
func (b *BaseBatch[T]) FunctionKill() *T {
	return b.addCmdAndTypeChecker(C.FunctionKill, []string{}, reflect.String, false)
}

// Publish posts a message to the specified channel. Returns the number of clients that received the message.
//
// Channel can be any string, but common patterns include using "." to create namespaces like
// "news.sports" or "news.weather".
//
// See [valkey.io] for details.
//
// Parameters:
//
//	channel - The channel to publish the message to.
//	message - The message to publish.
//
// Command Response:
//
//	The number of clients that received the message.
//
// [valkey.io]: https://valkey.io/commands/publish/
func (b *BaseBatch[T]) Publish(channel string, message string) *T {
	return b.addCmdAndTypeChecker(C.Publish, []string{channel, message}, reflect.Int64, false)
}

// Checks existence of scripts in the script cache by their SHA1 digest.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	sha1s - SHA1 digests of Lua scripts to be checked.
//
// Command Response:
//
//	An array of boolean values indicating the existence of each script.
//
// [valkey.io]: https://valkey.io/commands/script-exists
func (b *BaseBatch[T]) ScriptExists(sha1s []string) *T {
	return b.addCmdAndConverter(C.ScriptExists, sha1s, reflect.Slice, false, internal.ConvertArrayOf[bool])
}

// Removes all the scripts from the script cache.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	OK on success.
//
// [valkey.io]: https://valkey.io/commands/script-flush/
func (b *BaseBatch[T]) ScriptFlush() *T {
	return b.addCmdAndTypeChecker(C.ScriptFlush, []string{}, reflect.String, false)
}

// Removes all the scripts from the script cache with the specified flush mode.
// The mode can be either SYNC or ASYNC.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	mode - The flush mode (SYNC or ASYNC).
//
// Command Response:
//
//	OK on success.
//
// [valkey.io]: https://valkey.io/commands/script-flush/
func (b *BaseBatch[T]) ScriptFlushWithMode(mode options.FlushMode) *T {
	return b.addCmdAndTypeChecker(C.ScriptFlush, []string{string(mode)}, reflect.String, false)
}

// Returns the original source code of a script in the script cache.
//
// Since:
//
//	Valkey 8.0.0
//
// See [valkey.io] for details.
//
// Parameters:
//
//	sha1 - The SHA1 digest of the script.
//
// Command Response:
//
//	The original source code of the script, if present in the cache.
//	If the script is not found in the cache, an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/script-show
func (b *BaseBatch[T]) ScriptShow(sha1 string) *T {
	return b.addCmdAndTypeChecker(C.ScriptShow, []string{sha1}, reflect.String, false)
}

// Kills the currently executing Lua script, assuming no write operation was yet performed by the script.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	`OK` if script is terminated. Otherwise, throws an error.
//
// [valkey.io]: https://valkey.io/commands/script-kill
func (b *BaseBatch[T]) ScriptKill() *T {
	return b.addCmdAndTypeChecker(C.ScriptKill, []string{}, reflect.String, false)
}

// Sets configuration parameters to the specified values.
//
// Note:
//
// Prior to Version 7.0.0, only one parameter can be send.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	parameters - A map consisting of configuration parameters and their respective values to set.
//
// Command Response:
//
//	`"OK"` if all configurations have been successfully set. Otherwise, raises an error.
//
// [valkey.io]: https://valkey.io/commands/config-set/
func (b *BaseBatch[T]) ConfigSet(parameters map[string]string) *T {
	return b.addCmdAndTypeChecker(C.ConfigSet, utils.MapToString(parameters), reflect.String, false)
}

// Gets the values of configuration parameters.
//
// Note:
//
// Prior to Version 7.0.0, only one parameter can be send.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	args - A slice of configuration parameter names to retrieve values for.
//
// Command Response:
//
//	A map of values corresponding to the configuration parameters.
//
// [valkey.io]: https://valkey.io/commands/config-get/
func (b *BaseBatch[T]) ConfigGet(args []string) *T {
	return b.addCmdAndConverter(C.ConfigGet, args, reflect.Map, false, internal.ConvertMapOf[string])
}

// Gets information and statistics about the server.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	A string with the information for the default sections.
//
// [valkey.io]: https://valkey.io/commands/info/
func (b *BaseBatch[T]) Info() *T {
	return b.InfoWithOptions(options.InfoOptions{Sections: []constants.Section{}})
}

// Gets information and statistics about the server.
//
// Note:
//
// Starting from server version 7, command supports multiple section arguments.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	options - Additional command parameters, see [InfoOptions] for more details.
//
// Command Response:
//
//	A string containing the information for the sections requested.
//
// [valkey.io]: https://valkey.io/commands/info/
func (b *BaseBatch[T]) InfoWithOptions(options options.InfoOptions) *T {
	optionArgs, err := options.ToArgs()
	if err != nil {
		return b.addError("InfoWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.Info, optionArgs, reflect.String, false)
}

// Returns the number of keys in the currently selected database.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	The number of keys in the currently selected database.
//
// [valkey.io]: https://valkey.io/commands/dbsize/
func (b *BaseBatch[T]) DBSize() *T {
	return b.addCmdAndTypeChecker(C.DBSize, []string{}, reflect.Int64, false)
}

// Echoes the provided message back.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	message - The provided message.
//
// Command Response:
//
//	The provided message
//
// [valkey.io]: https://valkey.io/commands/echo/
func (b *BaseBatch[T]) Echo(message string) *T {
	return b.addCmdAndTypeChecker(C.Echo, []string{message}, reflect.String, false)
}

// Pings the server.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	Returns "PONG".
//
// [valkey.io]: https://valkey.io/commands/ping/
func (b *BaseBatch[T]) Ping() *T {
	return b.PingWithOptions(options.PingOptions{})
}

// Pings the server.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	pingOptions - The PingOptions type.
//
// Command Response:
//
//	Returns the copy of message.
//
// [valkey.io]: https://valkey.io/commands/ping/
func (b *BaseBatch[T]) PingWithOptions(pingOptions options.PingOptions) *T {
	optionArgs, err := pingOptions.ToArgs()
	if err != nil {
		return b.addError("PingWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.Ping, optionArgs, reflect.String, false)
}

// Deletes all the keys of all the existing databases.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (b *BaseBatch[T]) FlushAll() *T {
	return b.addCmdAndTypeChecker(C.FlushAll, []string{}, reflect.String, false)
}

// Deletes all the keys of all the existing databases.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	mode - The flushing mode, could be either [options.SYNC] or [options.ASYNC}.
//
// Command Response:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushall/
func (b *BaseBatch[T]) FlushAllWithOptions(mode options.FlushMode) *T {
	return b.addCmdAndTypeChecker(C.FlushAll, []string{string(mode)}, reflect.String, false)
}

// Deletes all the keys of the currently selected database.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (b *BaseBatch[T]) FlushDB() *T {
	return b.addCmdAndTypeChecker(C.FlushDB, []string{}, reflect.String, false)
}

// Deletes all the keys of the currently selected database.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	mode - The flushing mode, could be either [options.SYNC] or [options.ASYNC}.
//
// Command Response:
//
//	`"OK"` response on success.
//
// [valkey.io]: https://valkey.io/commands/flushdb/
func (b *BaseBatch[T]) FlushDBWithOptions(mode options.FlushMode) *T {
	return b.addCmdAndTypeChecker(C.FlushDB, []string{string(mode)}, reflect.String, false)
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// See [valkey.io] for details.
//
// Command Response:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (b *BaseBatch[T]) Lolwut() *T {
	return b.addCmdAndTypeChecker(C.Lolwut, []string{}, reflect.String, false)
}

// Displays a piece of generative computer art of the specific Valkey version and it's optional arguments.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	opts - The [LolwutOptions] type.
//
// Command Response:
//
// A piece of generative computer art of that specific valkey version along with the Valkey version.
//
// [valkey.io]: https://valkey.io/commands/lolwut/
func (b *BaseBatch[T]) LolwutWithOptions(opts options.LolwutOptions) *T {
	commandArgs, err := opts.ToArgs()
	if err != nil {
		return b.addError("LolwutWithOptions", err)
	}
	return b.addCmdAndTypeChecker(C.Lolwut, commandArgs, reflect.String, false)
}

// Gets the current connection id.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	The id of the client.
//
// [valkey.io]: https://valkey.io/commands/client-id/
func (b *BaseBatch[T]) ClientId() *T {
	return b.addCmdAndTypeChecker(C.ClientId, []string{}, reflect.Int64, false)
}

// Returns UNIX TIME of the last DB save timestamp or startup timestamp if no save was made since then.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	UNIX TIME of the last DB save executed with success.
//
// [valkey.io]: https://valkey.io/commands/lastsave/
func (b *BaseBatch[T]) LastSave() *T {
	return b.addCmdAndTypeChecker(C.LastSave, []string{}, reflect.Int64, false)
}

// Resets the statistics reported by the server using the INFO and LATENCY HISTOGRAM.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	OK to confirm that the statistics were successfully reset.
//
// [valkey.io]: https://valkey.io/commands/config-resetstat/
func (b *BaseBatch[T]) ConfigResetStat() *T {
	return b.addCmdAndTypeChecker(C.ConfigResetStat, []string{}, reflect.String, false)
}

// Gets the name of the current connection.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	The name of the client connection as a string if a name is set, or `nil` if no name is assigned.
//
// [valkey.io]: https://valkey.io/commands/client-getname/
func (b *BaseBatch[T]) ClientGetName() *T {
	return b.addCmdAndTypeChecker(C.ClientGetName, []string{}, reflect.String, true)
}

// Sets the name of the current connection.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	connectionName - Connection name of the current connection.
//
// Command Response:
//
//	OK - when connection name is set
//
// [valkey.io]: https://valkey.io/commands/client-setname/
func (b *BaseBatch[T]) ClientSetName(connectionName string) *T {
	return b.addCmdAndTypeChecker(C.ClientSetName, []string{connectionName}, reflect.String, false)
}

// Rewrites the configuration file with the current configuration.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	"OK" when the configuration was rewritten properly, otherwise an error is thrown.
//
// [valkey.io]: https://valkey.io/commands/config-rewrite/
func (b *BaseBatch[T]) ConfigRewrite() *T {
	return b.addCmdAndTypeChecker(C.ConfigRewrite, []string{}, reflect.String, false)
}

// Returns a random existing key name from the currently selected database.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	A random existing key name from the currently selected database.
//
// [valkey.io]: https://valkey.io/commands/randomkey/
func (b *BaseBatch[T]) RandomKey() *T {
	return b.addCmdAndTypeChecker(C.RandomKey, []string{}, reflect.String, true)
}

// Returns information about the function that's currently running and information about the
// available execution engines.
//
// Since:
//
//	Valkey 7.0 and above.
//
// See [valkey.io] for details.
//
// Command Response:
//
//	A map of function statistics containing the following information:
//	running_script - Information about the running script.
//	engines - Information about available engines and their stats.
//
// [valkey.io]: https://valkey.io/commands/function-stats/
func (b *BaseBatch[T]) FunctionStats() *T {
	return b.addCmdAndConverter(C.FunctionStats, []string{}, reflect.Map, false, internal.ConvertFunctionStatsResponse)
}
