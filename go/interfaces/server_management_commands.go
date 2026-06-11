// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package interfaces

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// ServerManagementCommands supports commands for the "Server Management" group for a standalone client.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/#server
type ServerManagementCommands interface {
	Select(ctx context.Context, index int64) (string, error)

	ConfigGet(ctx context.Context, args []string) (map[string]string, error)

	ConfigSet(ctx context.Context, parameters map[string]string) (string, error)

	Info(ctx context.Context) (string, error)

	InfoWithOptions(ctx context.Context, options options.InfoOptions) (string, error)

	DBSize(ctx context.Context) (int64, error)

	Time(ctx context.Context) ([]string, error)

	FlushAll(ctx context.Context) (string, error)

	FlushAllWithOptions(ctx context.Context, mode options.FlushMode) (string, error)

	FlushDB(ctx context.Context) (string, error)

	FlushDBWithOptions(ctx context.Context, mode options.FlushMode) (string, error)

	Lolwut(ctx context.Context) (string, error)

	LolwutWithOptions(ctx context.Context, opts options.LolwutOptions) (string, error)

	LastSave(ctx context.Context) (int64, error)

	Save(ctx context.Context) (string, error)

	BgSave(ctx context.Context) (string, error)

	BgSaveSchedule(ctx context.Context) (string, error)

	BgSaveCancel(ctx context.Context) (string, error)

	BgRewriteAof(ctx context.Context) (string, error)

	ConfigResetStat(ctx context.Context) (string, error)

	ConfigRewrite(ctx context.Context) (string, error)

	MemoryDoctor(ctx context.Context) (string, error)

	MemoryMallocStats(ctx context.Context) (string, error)

	MemoryPurge(ctx context.Context) (string, error)

	MemoryStats(ctx context.Context) (map[string]any, error)

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

	LatencyHistory(ctx context.Context, event string) ([]models.LatencyEntry, error)

	LatencyLatest(ctx context.Context) ([]models.LatencyInfo, error)

	LatencyReset(ctx context.Context, events ...string) (int64, error)
}
