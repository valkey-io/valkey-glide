// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "Scripting and Function" group for a standalone
// client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=scripting
type ScriptingAndFunctionStandaloneCommands interface {
	FunctionStats() (map[string]FunctionStatsResult, error)

	FunctionDelete(libName string) (string, error)
}
