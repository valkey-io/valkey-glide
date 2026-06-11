// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// ServerManagementCommands supports commands for the "Server Management" group for a cluster client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementClusterCommands interface {
	Select(ctx context.Context, index int64) (string, error)

	Info(ctx context.Context) (map[string]string, error)

	InfoWithOptions(ctx context.Context, options options.ClusterInfoOptions) (models.ClusterValue[string], error)

	TimeWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[[]string], error)

	DBSizeWithOptions(ctx context.Context, routeOption options.RouteOption) (int64, error)

	FlushAll(ctx context.Context) (string, error)

	FlushAllWithOptions(ctx context.Context, options options.FlushClusterOptions) (string, error)

	FlushDB(ctx context.Context) (string, error)

	FlushDBWithOptions(ctx context.Context, options options.FlushClusterOptions) (string, error)

	Lolwut(ctx context.Context) (string, error)

	LolwutWithOptions(ctx context.Context, lolwutOptions options.ClusterLolwutOptions) (models.ClusterValue[string], error)

	LastSave(ctx context.Context) (models.ClusterValue[int64], error)

	LastSaveWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[int64], error)

	Save(ctx context.Context) (string, error)

	SaveWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)

	BgSave(ctx context.Context) (models.ClusterValue[string], error)

	BgSaveWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[string], error)

	BgSaveSchedule(ctx context.Context) (models.ClusterValue[string], error)

	BgSaveScheduleWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[string], error)

	BgSaveCancel(ctx context.Context) (models.ClusterValue[string], error)

	BgSaveCancelWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[string], error)

	BgRewriteAof(ctx context.Context) (models.ClusterValue[string], error)

	BgRewriteAofWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[string], error)

	ConfigResetStat(ctx context.Context) (string, error)

	ConfigResetStatWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)

	ConfigSet(ctx context.Context, parameters map[string]string) (string, error)

	ConfigSetWithOptions(ctx context.Context, parameters map[string]string, routeOption options.RouteOption) (string, error)

	ConfigGet(ctx context.Context, parameters []string) (map[string]string, error)

	ConfigGetWithOptions(
		ctx context.Context,
		parameters []string,
		routeOption options.RouteOption,
	) (models.ClusterValue[map[string]string], error)

	ConfigRewrite(ctx context.Context) (string, error)

	ConfigRewriteWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)

	MemoryDoctor(ctx context.Context) (models.ClusterValue[string], error)

	MemoryDoctorWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[string], error)

	MemoryMallocStats(ctx context.Context) (models.ClusterValue[string], error)

	MemoryMallocStatsWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[string], error)

	MemoryPurge(ctx context.Context) (string, error)

	MemoryPurgeWithOptions(ctx context.Context, routeOption options.RouteOption) (string, error)

	MemoryStats(ctx context.Context) (models.ClusterValue[map[string]any], error)

	MemoryStatsWithOptions(ctx context.Context, routeOption options.RouteOption) (models.ClusterValue[map[string]any], error)

	AclCat(ctx context.Context) ([]string, error)

	AclCatWithCategory(ctx context.Context, category string) ([]string, error)

	AclDelUser(ctx context.Context, usernames []string) (int64, error)

	AclDryRun(ctx context.Context, username string, command string, args []string) (string, error)

	AclGenPass(ctx context.Context) (string, error)

	AclGenPassWithBits(ctx context.Context, bits int64) (string, error)

	AclGetUser(ctx context.Context, username string) (any, error)

	AclList(ctx context.Context) ([]string, error)

	AclLoad(ctx context.Context) (string, error)

	AclLog(ctx context.Context) ([]any, error)

	AclLogWithCount(ctx context.Context, count int64) ([]any, error)

	AclLogReset(ctx context.Context) (string, error)

	AclSave(ctx context.Context) (string, error)

	AclSetUser(ctx context.Context, username string, rules []string) (string, error)

	AclUsers(ctx context.Context) ([]string, error)

	AclWhoAmI(ctx context.Context) (string, error)

	LatencyHistory(ctx context.Context, event string) (models.ClusterValue[[]models.LatencyEntry], error)

	LatencyHistoryWithOptions(
		ctx context.Context,
		event string,
		route options.RouteOption,
	) (models.ClusterValue[[]models.LatencyEntry], error)

	LatencyLatest(ctx context.Context) (models.ClusterValue[[]models.LatencyInfo], error)

	LatencyLatestWithOptions(
		ctx context.Context,
		route options.RouteOption,
	) (models.ClusterValue[[]models.LatencyInfo], error)

	LatencyReset(ctx context.Context, events ...string) (int64, error)

	LatencyResetWithOptions(ctx context.Context, route options.RouteOption, events ...string) (int64, error)
}
