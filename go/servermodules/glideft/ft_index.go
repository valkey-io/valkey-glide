// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
)

// FtDropIndex drops an existing search index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index to drop.
//
// Return value:
//
//	"OK" on success.
//
// [valkey.io]: https://valkey.io/commands/ft.dropindex/
func FtDropIndex(ctx context.Context, client standaloneClient, indexName string) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{ftDropIndexCommand, indexName}))
}

// ClusterFtDropIndex is the cluster variant of [FtDropIndex].
func ClusterFtDropIndex(ctx context.Context, client clusterClient, indexName string) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{ftDropIndexCommand, indexName}))
}

// FtList returns a list of all existing index names.
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
//	A slice of index name strings.
//
// [valkey.io]: https://valkey.io/commands/ft._list/
func FtList(ctx context.Context, client standaloneClient) ([]string, error) {
	return toStringSliceResult(execStandalone(client, ctx, []string{ftListCommand}))
}

// ClusterFtList is the cluster variant of [FtList].
func ClusterFtList(ctx context.Context, client clusterClient) ([]string, error) {
	return toStringSliceResult(execCluster(client, ctx, []string{ftListCommand}))
}
