// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// FtInfo returns information and statistics about an index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index to inspect.
//
// Return value:
//
//	A map of field names to their values describing the index.
//
// [valkey.io]: https://valkey.io/commands/ft.info/
func FtInfo(ctx context.Context, client standaloneClient, indexName string) (map[string]any, error) {
	return toMapResult(execStandalone(client, ctx, buildInfoArgs(indexName, nil)))
}

// ClusterFtInfo is the cluster variant of [FtInfo].
func ClusterFtInfo(ctx context.Context, client clusterClient, indexName string) (map[string]any, error) {
	return toMapResult(execCluster(client, ctx, buildInfoArgs(indexName, nil)))
}

// FtInfoWithOptions returns information and statistics about an index with additional options.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index to inspect.
//	opts      - Options controlling scope, shard participation, and consistency.
//
// Return value:
//
//	A map of field names to their values describing the index.
//
// [valkey.io]: https://valkey.io/commands/ft.info/
func FtInfoWithOptions(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	opts *options.FtInfoOptions,
) (map[string]any, error) {
	return toMapResult(execStandalone(client, ctx, buildInfoArgs(indexName, opts)))
}

// ClusterFtInfoWithOptions is the cluster variant of [FtInfoWithOptions].
func ClusterFtInfoWithOptions(
	ctx context.Context,
	client clusterClient,
	indexName string,
	opts *options.FtInfoOptions,
) (map[string]any, error) {
	return toMapResult(execCluster(client, ctx, buildInfoArgs(indexName, opts)))
}

func buildInfoArgs(indexName string, opts *options.FtInfoOptions) []string {
	args := []string{ftInfoCommand, indexName}
	if opts != nil {
		args = append(args, opts.ToArgs()...)
	}
	return args
}
