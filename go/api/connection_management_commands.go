// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "github.com/valkey-io/valkey-glide/go/glide/api/options"

// Supports commands and transactions for the "Connection Management" group of commands for standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementCommands interface {
	Ping() (string, error)

	PingWithOptions(pingOptions *options.PingOptions) (string, error)

	// Echo the provided message back.
	// The command will be routed a random node.
	//
	// Parameters:
	// 	message - The provided message.
	//
	// Return value:
	//  The provided message
	//
	// For example:
	//  result, err := client.Echo("Hello World")
	//	if err != nil {
	//	    // handle error
	//	}
	//	fmt.Println(result.Value()) // Output: Hello World
	//
	// [valkey.io]: https://valkey.io/commands/echo/
	Echo(message string) (Result[string], error)
}
