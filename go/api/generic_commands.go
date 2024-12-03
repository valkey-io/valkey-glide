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
	//When in cluster mode, the command may route to multiple nodes when `keys` map to different hash slots.
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

	// Renames key to new key.
	//  If new Key already exists it is overwritten.
	//
	// Note:
	//  When in cluster mode, both key and newKey must map to the same hash slot.
	//
	// Parameters:
	//  key to rename.
	//  newKey The new name of the key.
	//
	// Return value:
	// If the key was successfully renamed, return "OK". If key does not exist, an error is thrown.
	//
	// Example:
	//  result, err := client.Persist([]string{"key","newkey"})
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: string
	//
	// [valkey.io]: https://valkey.io/commands/rename/
	Rename(key string, newKey string) (Result[string], error)

	// Renames key to newkey if newKey does not yet exist.
	//
	// Note:
	//  When in cluster mode, both key and newkey must map to the same hash slot.
	//
	// Parameters:
	//  key to rename.
	//  newKey The new name of the key.
	//
	// Return value:
	// true if key was renamed to newKey, false if newKey already exists.
	//
	// Example:
	//  result, err := client.Renamenx([]string{"key","newkey"})
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: string
	//
	// [valkey.io]: https://valkey.io/commands/renamenx/
	Renamenx(key string, newKey string) (Result[bool], error)

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
	//  if err != nil {
	//      // handle error
	//  }
	//  fmt.Println(result.Value()) // Output: String
	//
	// [valkey.io]: https://valkey.io/commands/dump/
	Dump(key string) (Result[string], error)

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
	//  if err != nil {
	//      // handle error
	//  }
	//  fmt.Println(result.Value()) // Output: string
	//
	// [valkey.io]: https://valkey.io/commands/object-encoding/
	ObjectEncoding(key string) (Result[string], error)
}
