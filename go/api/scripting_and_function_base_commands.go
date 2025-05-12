// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
)

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

	FunctionList(ctx context.Context, query models.FunctionListQuery) ([]models.LibraryInfo, error)

	FunctionDump(ctx context.Context) (string, error)

	FunctionRestore(ctx context.Context, payload string) (string, error)

	FunctionRestoreWithPolicy(ctx context.Context, payload string, policy options.FunctionRestorePolicy) (string, error)

	InvokeScript(ctx context.Context, script options.Script) (any, error)

	InvokeScriptWithOptions(ctx context.Context, script options.Script, scriptOptions options.ScriptOptions) (any, error)

	ScriptExists(ctx context.Context, sha1s []string) ([]bool, error)

	ScriptFlush(ctx context.Context) (string, error)

	ScriptFlushWithMode(ctx context.Context, mode options.FlushMode) (string, error)

	ScriptShow(ctx context.Context, sha1 string) (string, error)

	ScriptKill(ctx context.Context) (string, error)
}
