// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// FtAggregate runs an aggregation pipeline against an index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index to aggregate.
//	query     - The filter query string.
//	opts      - Optional aggregation parameters. Pass nil to use defaults. If opts.WithCursor is
//	            set, use [FtAggregateWithCursor]/[ClusterFtAggregateWithCursor] instead -- the
//	            reply shape becomes a two-element [rows, cursorId] array rather than this plain
//	            row slice.
//
// Return value:
//
//	A slice of maps, each representing one result row with field name keys.
//
// [valkey.io]: https://valkey.io/commands/ft.aggregate/
func FtAggregate(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	query string,
	opts *options.FtAggregateOptions,
) ([]map[string]any, error) {
	args, err := buildAggregateArgs(indexName, query, opts)
	if err != nil {
		return nil, err
	}
	result, execErr := execStandalone(client, ctx, args)
	return parseFtAggregateResponse(result, execErr)
}

// ClusterFtAggregate is the cluster variant of [FtAggregate].
func ClusterFtAggregate(
	ctx context.Context,
	client clusterClient,
	indexName string,
	query string,
	opts *options.FtAggregateOptions,
) ([]map[string]any, error) {
	args, err := buildAggregateArgs(indexName, query, opts)
	if err != nil {
		return nil, err
	}
	result, execErr := execCluster(client, ctx, args)
	return parseFtAggregateResponse(result, execErr)
}

// FtAggregateWithCursor is [FtAggregate] for an opts.WithCursor query, returning the
// [rows, cursorId] pair directly. cursorId is 0 once the cursor is exhausted; otherwise pass it
// to [FtCursorRead]/[ClusterFtCursorRead] to fetch the next page.
func FtAggregateWithCursor(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	query string,
	opts *options.FtAggregateOptions,
) ([]map[string]any, int64, error) {
	args, err := buildAggregateArgs(indexName, query, opts)
	if err != nil {
		return nil, 0, err
	}
	result, execErr := execStandalone(client, ctx, args)
	return parseCursorWrappedAggregateResponse(result, execErr)
}

// ClusterFtAggregateWithCursor is the cluster variant of [FtAggregateWithCursor]. Routed to the
// PRIMARY of indexName's slot regardless of the client's ReadFrom configuration -- a cursor is
// a per-process, node-local resource, so every subsequent page must be read from the same node
// that opened it.
func ClusterFtAggregateWithCursor(
	ctx context.Context,
	client clusterClient,
	indexName string,
	query string,
	opts *options.FtAggregateOptions,
) ([]map[string]any, int64, error) {
	args, err := buildAggregateArgs(indexName, query, opts)
	if err != nil {
		return nil, 0, err
	}
	result, execErr := execClusterPrimaryPinned(client, ctx, indexName, args)
	return parseCursorWrappedAggregateResponse(result, execErr)
}

func buildAggregateArgs(indexName, query string, opts *options.FtAggregateOptions) ([]string, error) {
	args := []string{ftAggregateCommand, indexName, query}

	if opts != nil {
		optArgs, err := opts.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, optArgs...)
	}

	return args, nil
}
