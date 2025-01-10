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

	// Returns the logarithmic access frequency counter of a Valkey object stored at key.
	//
	// Parameters:
	//  key - The key of the object to get the logarithmic access frequency counter of.
	//
	// Return value:
	//  If key exists, returns the logarithmic access frequency counter of the
	//  object stored at key as a long. Otherwise, returns null.
	//
	// Example:
	//  result, err := client.ObjectFreq(key)
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: 1
	//
	// [valkey.io]: https://valkey.io/commands/object-freq/
	ObjectFreq(key string) (Result[int64], error)

	// Returns the logarithmic access frequency counter of a Valkey object stored at key.
	//
	// Parameters:
	//  key - The key of the object to get the logarithmic access frequency counter of.
	//
	// Return value:
	//  If key exists, returns the idle time in seconds. Otherwise, returns null.
	//
	// Example:
	//  result, err := client.ObjectIdle(key)
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: 1
	//
	// [valkey.io]: https://valkey.io/commands/object-idletime/
	ObjectIdle(key string) (Result[int64], error)

	// Returns the reference count of the object stored at key.
	//
	// Parameters:
	//  key - The key of the object to get the reference count of.
	//
	// Return value:
	//  If key exists, returns the reference count of the object stored at key
	//	as a long. Otherwise, returns null.
	//
	// Example:
	//  result, err := client.ObjectRefCount(key)
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: 1
	//
	// [valkey.io]: https://valkey.io/commands/object-refcount/
	ObjectRefCount(key string) (Result[int64], error)
}
