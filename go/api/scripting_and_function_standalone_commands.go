// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import "context"

// Supports commands and transactions for the "Scripting and Function" group for a standalone
// client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=scripting
type ScriptingAndFunctionStandaloneCommands interface {
	FunctionStats(ctx context.Context) (map[string]FunctionStatsResult, error)

	FunctionDelete(ctx context.Context, libName string) (string, error)
}
