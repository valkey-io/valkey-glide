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

	// Create a key associated with a value that is obtained by
	// deserializing the provided serialized value (obtained via dump).
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
	//	fmt.Println(result.Value()) // Output: string
	//
	// [valkey.io]: https://valkey.io/commands/restore/
	Restore(key string, ttl int64, value string) (Result[string], error)
}
