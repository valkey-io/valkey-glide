// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package options

// ScriptDebugMode represents the mode for the SCRIPT DEBUG command.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/script-debug/
type ScriptDebugMode string

const (
	// ScriptDebugModeYes enables non-blocking asynchronous debugging of Lua scripts (using fork).
	ScriptDebugModeYes ScriptDebugMode = "YES"

	// ScriptDebugModeSync enables blocking synchronous debugging of Lua scripts.
	ScriptDebugModeSync ScriptDebugMode = "SYNC"

	// ScriptDebugModeNo disables Lua script debugging.
	ScriptDebugModeNo ScriptDebugMode = "NO"
)

// ScriptDebugClusterOptions provides optional arguments for SCRIPT DEBUG in cluster mode.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/script-debug/
type ScriptDebugClusterOptions struct {
	*RouteOption
}
