// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// GenericCommands supports commands for the "Generic Commands" group for standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#generic
type GenericCommands interface {
	// CustomCommand executes a single command, specified by args, without checking inputs. Every part of the command,
	// including the command name and subcommands, should be added as a separate value in args. The returning value depends on
	// the executed
	// command.
	//
	// See [Valkey GLIDE Wiki] for details on the restrictions and limitations of the custom command API.
	//
	// This function should only be used for single-response commands. Commands that don't return complete response and awaits
	// (such as SUBSCRIBE), or that return potentially more than a single response (such as XREAD), or that change the client's
	// behavior (such as entering pub/sub mode on RESP2 connections) shouldn't be called using this function.
	//
	// Parameters:
	//	args - Arguments for the custom command including the command name.
	//
	// Return value:
	//	The returned value for the custom command.
	//
	// For example:
	//	result, err := client.CustomCommand([]string{"ping"})
	//	result.(string): "PONG"
	//
	// [Valkey GLIDE Wiki]: https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#custom-command
	CustomCommand(args []string) (interface{}, error)

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
}
