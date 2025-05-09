// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "context"

// Supports commands and transactions for the "Scripting and Function" group for a standalone
// or cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=scripting
type ScriptingAndFunctionBaseCommands interface {
	FunctionLoad(ctx context.Context, libraryCode string, replace bool) (string, error)

	FunctionFlush(ctx context.Context) (string, error)

	FunctionFlushSync(ctx context.Context) (string, error)

	FunctionFlushAsync(ctx context.Context) (string, error)

	FCall(ctx context.Context, function string) (any, error)

	FCallReadOnly(ctx context.Context, function string) (any, error)

	FCallWithKeysAndArgs(ctx context.Context, function string, keys []string, args []string) (any, error)

	FCallReadOnlyWithKeysAndArgs(ctx context.Context, function string, keys []string, args []string) (any, error)

	FunctionKill(ctx context.Context) (string, error)

	FunctionList(ctx context.Context, query FunctionListQuery) ([]LibraryInfo, error)
}
