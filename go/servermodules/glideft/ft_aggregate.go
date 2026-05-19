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
//	opts      - Optional aggregation parameters. Pass nil to use defaults.
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
