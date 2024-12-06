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
	//  When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.
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

	// Unlink (delete) multiple keys from the database. A key is ignored if it does not exist.
	// This command, similar to Del However, this command does not block the server
	//
	// Note:
	//	 In cluster mode, if keys in keys map to different hash slots, the command
	//   will be split across these slots and executed separately for each. This means the command
	//   is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//   call will return the first encountered error, even though some requests may have succeeded
	//   while others did not. If this behavior impacts your application logic, consider splitting
	//   the request into sub-requests per slot to ensure atomicity.
	//
	// Parameters:
	//  keys - One or more keys to unlink.
	//
	// Return value:
	//  Return the number of keys that were unlinked.
	//
	// Example:
	//	result, err := client.Unlink([]string{"key1", "key2", "key3"})
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: int
	//
	// [valkey.io]: Https://valkey.io/commands/unlink/
	Unlink(keys []string) (Result[int64], error)

	// Alters the last access time of a key(s). A key is ignored if it does not exist.
	//
	// Note:
	//	 In cluster mode, if keys in keys map to different hash slots, the command
	//   will be split across these slots and executed separately for each. This means the command
	//   is atomic only at the slot level. If one or more slot-specific requests fail, the entire
	//   call will return the first encountered error, even though some requests may have succeeded
	//   while others did not. If this behavior impacts your application logic, consider splitting
	//   the request into sub-requests per slot to ensure atomicity.
	//
	// Parameters:
	//  The keys to update last access time.
	//
	// Return value:
	//  The number of keys that were updated.
	//
	// Example:
	//	result, err := client.Touch([]string{"key1", "key2", "key3"})
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: int
	//
	// [valkey.io]: Https://valkey.io/commands/touch/
	Touch(keys []string) (Result[int64], error)

	// Type returns the string representation of the type of the value stored at key.
	// The different types that can be returned are: string, list, set, zset, hash and stream.
	//
	// Parameters:
	//  keys - string
	//
	// Return value:
	//  If the key exists, the type of the stored value is returned. Otherwise, a none" string is returned.
	//
	// Example:
	//	result, err := client.Type([]string{"key"})
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: string
	//
	// [valkey.io]: Https://valkey.io/commands/type/
	Type(key string) (Result[string], error)
}
