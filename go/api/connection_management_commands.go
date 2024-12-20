// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "Connection Management" group of commands for standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementCommands interface {
	// Pings the server.
	//
	// Return value:
	//  If no argument is provided, returns "PONG".
	//
	// For example:
	//  result, err := client.Ping()
	//
	// [valkey.io]: https://valkey.io/commands/ping/
	Ping() (string, error)

	// Pings the server with a custom message.
	//
	// Return value:
	//  If an argument is provided, returns the argument.
	//
	// For example:
	//  result, err := client.PingWithMessage("Hello")
	//
	// [valkey.io]: https://valkey.io/commands/ping/
	PingWithMessage(message string) (string, error)
}
