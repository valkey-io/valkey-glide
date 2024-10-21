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

	// Copy copies the value stored at the source key to the destination key.
	//
	// By default, the destination key is created in the same logical database used by the connection. However, the `DB` option allows specifying an alternative logical database index for the destination key. If the destination key already exists, the command returns zero. The `REPLACE` option can be used to remove the destination key before copying the value.
	//
	// Note:
	// - When in cluster mode, the command may route to multiple nodes depending on the location of the source and destination keys.
	//
	// Parameters:
	//  sourceKey      - The key from which to copy the value.
	//  destinationKey - The key to copy the value to.
	//  destinationDB  - An optional integer representing the database index for the destination key. If nil, the current database is used.
	//  replace        - A boolean flag indicating whether to replace the destination key if it exists.
	//
	// Return value:
	//  Returns 1 if the copy was successful, or 0 if the destination key already exists without the `REPLACE` option.
	//
	// Example:
	//	result, err := client.Copy("dolly", "clone", nil, false)
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result) // Output: 1
	//
	// [valkey.io]: https://valkey.io/commands/copy/
	Copy(sourceKey string, destinationKey string, destinationDB *int, replace bool) (Result[int64], error)
}
