// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
)

// FtExplain returns the execution plan for a query as a string.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index.
//	query     - The query to explain.
//
// Return value:
//
//	A string describing the query execution plan.
//
// [valkey.io]: https://valkey.io/commands/ft.explain/
func FtExplain(ctx context.Context, client standaloneClient, indexName, query string) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{ftExplainCommand, indexName, query}))
}

// ClusterFtExplain is the cluster variant of [FtExplain].
func ClusterFtExplain(ctx context.Context, client clusterClient, indexName, query string) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{ftExplainCommand, indexName, query}))
}

// FtExplainCLI returns the execution plan for a query as a slice of strings (CLI format).
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index.
//	query     - The query to explain.
//
// Return value:
//
//	A slice of strings representing the query execution plan.
//
// [valkey.io]: https://valkey.io/commands/ft.explaincli/
func FtExplainCLI(ctx context.Context, client standaloneClient, indexName, query string) ([]string, error) {
	return toStringSliceResult(execStandalone(client, ctx, []string{ftExplainCLICommand, indexName, query}))
}

// ClusterFtExplainCLI is the cluster variant of [FtExplainCLI].
func ClusterFtExplainCLI(ctx context.Context, client clusterClient, indexName, query string) ([]string, error) {
	return toStringSliceResult(execCluster(client, ctx, []string{ftExplainCLICommand, indexName, query}))
}
