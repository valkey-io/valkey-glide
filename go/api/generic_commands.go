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

	// Copy duplicates the value stored at `sourceKey` into `destinationKey`.
	//
	// By default, the destination key is created in the same logical database as the connection.
	// The `destinationDB` parameter allows specifying an alternative logical database index for the destination key.
	// If the `replace` option is true, the destination key will be removed if it already exists.
	//
	// Note:
	// - In cluster mode, both `sourceKey` and `destinationKey` must be managed by the same node for this command to succeed.
	//
	// Parameters:
	//  - sourceKey      (string): The key from which to copy the value.
	//  - destinationKey (string): The key to copy the value to.
	//  - destinationDB  (*int): An optional integer specifying the database index for the destination key.
	//                           If nil, the current database is used.
	//  - replace        (bool): If true, removes `destinationKey` before copying the value if it already exists.
	//
	// Return value:
	// - Result[bool]: Returns true if the copy was successful; returns false if the destination key exists and `replace` is
	// not specified.
	//
	// Example:
	//	result, err := client.Copy("dolly", "clone", nil, false)
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: true or false
	//
	// [valkey.io]: https://valkey.io/commands/copy/
	Copy(sourceKey string, destinationKey string, destinationDB *int, replace bool) (Result[bool], error)
}
