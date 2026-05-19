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

	// AclCat returns a list of all ACL categories.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   An array of ACL categories.
	//
	// [valkey.io]: https://valkey.io/commands/acl-cat/
	AclCat(ctx context.Context) ([]string, error)

	// AclCatWithCategory returns a list of commands within the specified ACL category.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   category - The ACL category to list commands for.
	//
	// Return value:
	//   An array of commands within the specified category.
	//
	// [valkey.io]: https://valkey.io/commands/acl-cat/
	AclCatWithCategory(ctx context.Context, category string) ([]string, error)

	// AclDelUser deletes all specified ACL users and terminates their connections.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   usernames - An array of usernames to delete.
	//
	// Return value:
	//   The number of users deleted.
	//
	// [valkey.io]: https://valkey.io/commands/acl-deluser/
	AclDelUser(ctx context.Context, usernames []string) (int64, error)

	// AclDryRun simulates the execution of a command by a user without actually executing it.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   username - The username to simulate command execution for.
	//   command - The command to simulate.
	//   args - The command arguments.
	//
	// Return value:
	//   "OK" if the user can execute the command, otherwise a string describing why the command cannot be executed.
	//
	// [valkey.io]: https://valkey.io/commands/acl-dryrun/
	AclDryRun(ctx context.Context, username string, command string, args []string) (string, error)

	// AclGenPass generates a random password for ACL users.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   A randomly generated password string (64 hex characters by default).
	//
	// [valkey.io]: https://valkey.io/commands/acl-genpass/
	AclGenPass(ctx context.Context) (string, error)

	// AclGenPassWithBits generates a random password with the specified number of bits.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   bits - The number of bits for the password (must be between 1 and 4096).
	//
	// Return value:
	//   A randomly generated password string.
	//
	// [valkey.io]: https://valkey.io/commands/acl-genpass/
	AclGenPassWithBits(ctx context.Context, bits int64) (string, error)

	// AclGetUser returns all ACL rules for the specified user.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   username - The username to get ACL rules for.
	//
	// Return value:
	//   A value describing the ACL rules for the user, or nil if user doesn't exist.
	//
	// [valkey.io]: https://valkey.io/commands/acl-getuser/
	AclGetUser(ctx context.Context, username string) (any, error)

	// AclList returns a list of all ACL users and their rules in ACL configuration file format.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   An array of ACL rules for all users.
	//
	// [valkey.io]: https://valkey.io/commands/acl-list/
	AclList(ctx context.Context) ([]string, error)

	// AclLoad reloads ACL rules from the configured ACL configuration file.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   "OK" on success.
	//
	// [valkey.io]: https://valkey.io/commands/acl-load/
	AclLoad(ctx context.Context) (string, error)

	// AclLog returns the ACL security events log.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   An array of ACL security events.
	//
	// [valkey.io]: https://valkey.io/commands/acl-log/
	AclLog(ctx context.Context) ([]any, error)

	// AclLogWithCount returns the specified number of ACL security events from the log.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   count - The number of entries to return.
	//
	// Return value:
	//   An array of ACL security events.
	//
	// [valkey.io]: https://valkey.io/commands/acl-log/
	AclLogWithCount(ctx context.Context, count int64) ([]any, error)

	// AclLogReset resets the ACL log.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   "OK" on success.
	//
	// [valkey.io]: https://valkey.io/commands/acl-log/
	AclLogReset(ctx context.Context) (string, error)

	// AclSave saves the current ACL rules to the configured ACL configuration file.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   "OK" on success.
	//
	// [valkey.io]: https://valkey.io/commands/acl-save/
	AclSave(ctx context.Context) (string, error)

	// AclSetUser creates or modifies an ACL user and its rules.
	//
	// See [valkey.io] for details.
	//
	// Parameters:
	//   username - The username for the ACL user.
	//   rules - An array of ACL rules to apply to the user.
	//
	// Return value:
	//   "OK" on success.
	//
	// [valkey.io]: https://valkey.io/commands/acl-setuser/
	AclSetUser(ctx context.Context, username string, rules []string) (string, error)

	// AclUsers returns a list of all ACL usernames.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   An array of ACL usernames.
	//
	// [valkey.io]: https://valkey.io/commands/acl-users/
	AclUsers(ctx context.Context) ([]string, error)

	// AclWhoAmI returns the username of the current connection.
	//
	// See [valkey.io] for details.
	//
	// Return value:
	//   The username of the current connection.
	//
	// [valkey.io]: https://valkey.io/commands/acl-whoami/
	AclWhoAmI(ctx context.Context) (string, error)
}
