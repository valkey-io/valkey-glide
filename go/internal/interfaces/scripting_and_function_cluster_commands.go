// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// Supports commands and transactions for the "Scripting and Function" group for a cluster
// client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/?group=scripting
type ScriptingAndFunctionClusterCommands interface {
	FunctionLoadWithRoute(ctx context.Context, libraryCode string, replace bool, route options.RouteOption) (string, error)

	FunctionFlushWithRoute(ctx context.Context, route options.RouteOption) (string, error)

	FunctionFlushSyncWithRoute(ctx context.Context, route options.RouteOption) (string, error)

	FunctionFlushAsyncWithRoute(ctx context.Context, route options.RouteOption) (string, error)

	FCallWithRoute(ctx context.Context, function string, route options.RouteOption) (models.ClusterValue[any], error)

	FCallReadOnlyWithRoute(ctx context.Context, function string, route options.RouteOption) (models.ClusterValue[any], error)

	FCallWithArgs(ctx context.Context, function string, args []string) (models.ClusterValue[any], error)

	FCallReadOnlyWithArgs(ctx context.Context, function string, args []string) (models.ClusterValue[any], error)

	FCallWithArgsWithRoute(
		ctx context.Context,
		function string,
		args []string,
		route options.RouteOption,
	) (models.ClusterValue[any], error)

	FCallReadOnlyWithArgsWithRoute(
		ctx context.Context,
		function string,
		args []string,
		route options.RouteOption,
	) (models.ClusterValue[any], error)

	FunctionStats(ctx context.Context) (map[string]models.FunctionStatsResult, error)

	FunctionStatsWithRoute(
		ctx context.Context,
		route options.RouteOption,
	) (models.ClusterValue[models.FunctionStatsResult], error)

	FunctionDelete(ctx context.Context, libName string) (string, error)

	FunctionDeleteWithRoute(ctx context.Context, libName string, route options.RouteOption) (string, error)

	FunctionKill(ctx context.Context) (string, error)

	FunctionKillWithRoute(ctx context.Context, route options.RouteOption) (string, error)

	FunctionList(ctx context.Context, query models.FunctionListQuery) ([]models.LibraryInfo, error)

	FunctionListWithRoute(
		ctx context.Context,
		query models.FunctionListQuery,
		route options.RouteOption,
	) (models.ClusterValue[[]models.LibraryInfo], error)

	FunctionDump(ctx context.Context) (string, error)

	FunctionDumpWithRoute(ctx context.Context, route config.Route) (models.ClusterValue[string], error)

	FunctionRestore(ctx context.Context, payload string) (string, error)

	FunctionRestoreWithRoute(ctx context.Context, payload string, route config.Route) (string, error)

	FunctionRestoreWithPolicy(ctx context.Context, payload string, policy constants.FunctionRestorePolicy) (string, error)

	FunctionRestoreWithPolicyWithRoute(
		ctx context.Context,
		payload string,
		policy constants.FunctionRestorePolicy,
		route config.Route,
	) (string, error)

	InvokeScriptWithRoute(
		ctx context.Context,
		script options.Script,
		route options.RouteOption,
	) (models.ClusterValue[any], error)

	InvokeScriptWithClusterOptions(
		ctx context.Context,
		script options.Script,
		clusterScriptOptions options.ClusterScriptOptions,
	) (models.ClusterValue[any], error)

	ScriptExists(ctx context.Context, sha1s []string) ([]bool, error)

	ScriptExistsWithRoute(ctx context.Context, sha1s []string, route options.RouteOption) ([]bool, error)

	ScriptFlush(ctx context.Context) (string, error)

	ScriptFlushWithOptions(ctx context.Context, options options.ScriptFlushOptions) (string, error)

	ScriptKill(ctx context.Context) (string, error)

	ScriptKillWithRoute(ctx context.Context, route options.RouteOption) (string, error)
}
