// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "Connection Management" group of commands for standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#connection
type ConnectionManagementCommands interface {
	Ping() (string, error)

	PingWithMessage(message string) (string, error)

	Echo(message string) (Result[string], error)

	EchoWithOptions(echoOptions *options.EchoOptions) (string, error)
}
