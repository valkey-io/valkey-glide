// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/api/options"
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

	FCallWithRoute(ctx context.Context, function string, route options.RouteOption) (ClusterValue[any], error)

	FCallReadOnlyWithRoute(ctx context.Context, function string, route options.RouteOption) (ClusterValue[any], error)

	FCallWithArgs(ctx context.Context, function string, args []string) (ClusterValue[any], error)

	FCallReadOnlyWithArgs(ctx context.Context, function string, args []string) (ClusterValue[any], error)

	FCallWithArgsWithRoute(
		ctx context.Context,
		function string,
		args []string,
		route options.RouteOption,
	) (ClusterValue[any], error)

	FCallReadOnlyWithArgsWithRoute(
		ctx context.Context,
		function string,
		args []string,
		route options.RouteOption,
	) (ClusterValue[any], error)

	FunctionStats(ctx context.Context) (map[string]FunctionStatsResult, error)

	FunctionStatsWithRoute(ctx context.Context, route options.RouteOption) (ClusterValue[FunctionStatsResult], error)

	FunctionDelete(ctx context.Context, libName string) (string, error)

	FunctionDeleteWithRoute(ctx context.Context, libName string, route options.RouteOption) (string, error)

	FunctionKillWithRoute(ctx context.Context, route options.RouteOption) (string, error)

	FunctionListWithRoute(
		ctx context.Context,
		query FunctionListQuery,
		route options.RouteOption,
	) (ClusterValue[[]LibraryInfo], error)
}
