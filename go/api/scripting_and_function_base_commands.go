// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

// Supports commands and transactions for the "Scripting and Function" group for a standalone
// or cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=scripting
type ScriptingAndFunctionBaseCommands interface {
	FunctionLoad(libraryCode string, replace bool) (string, error)

	FunctionFlush() (string, error)

	FunctionFlushSync() (string, error)

	FunctionFlushAsync() (string, error)

	FCall(function string) (any, error)

	FCallReadOnly(function string) (any, error)

	FCallWithKeysAndArgs(function string, keys []string, args []string) (any, error)

	FCallReadOnlyWithKeysAndArgs(function string, keys []string, args []string) (any, error)
}
