// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
)

// FtAliasAdd adds an alias to an existing index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	alias     - The alias name to add.
//	indexName - The index to associate the alias with.
//
// Return value:
//
//	"OK" on success.
//
// [valkey.io]: https://valkey.io/commands/ft.aliasadd/
func FtAliasAdd(ctx context.Context, client standaloneClient, alias, indexName string) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{ftAliasAddCommand, alias, indexName}))
}

// ClusterFtAliasAdd is the cluster variant of [FtAliasAdd].
func ClusterFtAliasAdd(ctx context.Context, client clusterClient, alias, indexName string) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{ftAliasAddCommand, alias, indexName}))
}

// FtAliasDel removes an alias from an index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx   - The context for controlling the command execution.
//	client - The Valkey GLIDE client to execute the command.
//	alias  - The alias name to remove.
//
// Return value:
//
//	"OK" on success.
//
// [valkey.io]: https://valkey.io/commands/ft.aliasdel/
func FtAliasDel(ctx context.Context, client standaloneClient, alias string) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{ftAliasDelCommand, alias}))
}

// ClusterFtAliasDel is the cluster variant of [FtAliasDel].
func ClusterFtAliasDel(ctx context.Context, client clusterClient, alias string) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{ftAliasDelCommand, alias}))
}

// FtAliasUpdate updates an existing alias to point to a different index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	alias     - The alias name to update.
//	indexName - The new index to associate the alias with.
//
// Return value:
//
//	"OK" on success.
//
// [valkey.io]: https://valkey.io/commands/ft.aliasupdate/
func FtAliasUpdate(ctx context.Context, client standaloneClient, alias, indexName string) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{ftAliasUpdateCommand, alias, indexName}))
}

// ClusterFtAliasUpdate is the cluster variant of [FtAliasUpdate].
func ClusterFtAliasUpdate(ctx context.Context, client clusterClient, alias, indexName string) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{ftAliasUpdateCommand, alias, indexName}))
}

// FtAliasList returns a map of all aliases to their associated index names.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx    - The context for controlling the command execution.
//	client - The Valkey GLIDE client to execute the command.
//
// Return value:
//
//	A map where keys are alias names and values are index names.
//
// [valkey.io]: https://valkey.io/commands/ft._aliaslist/
func FtAliasList(ctx context.Context, client standaloneClient) (map[string]string, error) {
	return toStringMapResult(execStandalone(client, ctx, []string{ftAliasListCommand}))
}

// ClusterFtAliasList is the cluster variant of [FtAliasList].
func ClusterFtAliasList(ctx context.Context, client clusterClient) (map[string]string, error) {
	return toStringMapResult(execCluster(client, ctx, []string{ftAliasListCommand}))
}
