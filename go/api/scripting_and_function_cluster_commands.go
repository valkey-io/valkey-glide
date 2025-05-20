// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

// Supports commands and transactions for the "Scripting and Function" group for a cluster
// client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=scripting
type ScriptingAndFunctionClusterCommands interface {
	FunctionLoadWithRoute(libraryCode string, replace bool, route options.RouteOption) (string, error)

	FunctionFlushWithRoute(route options.RouteOption) (string, error)

	FunctionFlushSyncWithRoute(route options.RouteOption) (string, error)

	FunctionFlushAsyncWithRoute(route options.RouteOption) (string, error)

	FCallWithRoute(function string, route options.RouteOption) (ClusterValue[any], error)

	FCallReadOnlyWithRoute(function string, route options.RouteOption) (ClusterValue[any], error)

	FCallWithArgs(function string, args []string) (ClusterValue[any], error)

	FCallReadOnlyWithArgs(function string, args []string) (ClusterValue[any], error)

	FCallWithArgsWithRoute(function string, args []string, route options.RouteOption) (ClusterValue[any], error)

	FCallReadOnlyWithArgsWithRoute(function string, args []string, route options.RouteOption) (ClusterValue[any], error)

	FunctionStats() (map[string]FunctionStatsResult, error)

	FunctionStatsWithRoute(route options.RouteOption) (ClusterValue[FunctionStatsResult], error)

	FunctionDelete(libName string) (string, error)

	FunctionDeleteWithRoute(libName string, route options.RouteOption) (string, error)

	FunctionKillWithRoute(route options.RouteOption) (string, error)

	InvokeScriptWithRoute(script options.Script, route options.RouteOption) (ClusterValue[any], error)

	InvokeScriptWithClusterOptions(
		script options.Script,
		clusterScriptOptions options.ClusterScriptOptions,
	) (ClusterValue[any], error)

	ScriptExists(sha1s []string) ([]bool, error)

	ScriptExistsWithRoute(sha1s []string, route options.RouteOption) ([]bool, error)

	ScriptFlush() (string, error)

	ScriptFlushWithOptions(options options.ScriptFlushOptions) (string, error)

	ScriptKill() (string, error)

	ScriptKillWithRoute(route options.RouteOption) (string, error)

	FunctionListWithRoute(query FunctionListQuery, route options.RouteOption) (ClusterValue[[]LibraryInfo], error)

	FunctionDumpWithRoute(route config.Route) (ClusterValue[string], error)

	FunctionRestoreWithRoute(payload string, route config.Route) (string, error)

	FunctionRestoreWithPolicyWithRoute(
		payload string,
		policy options.FunctionRestorePolicy,
		route config.Route,
	) (string, error)
}
