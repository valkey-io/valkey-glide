// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// FtCreate creates a new search index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index to create.
//	schema    - A slice of field definitions that describe the index schema.
//	opts      - Optional index creation parameters. Pass nil to use defaults.
//
// Return value:
//
//	"OK" on success.
//
// [valkey.io]: https://valkey.io/commands/ft.create/
func FtCreate(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	schema []options.Field,
	opts *options.FtCreateOptions,
) (string, error) {
	args, err := buildCreateArgs(indexName, schema, opts)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return toStringResult(execStandalone(client, ctx, args))
}

// ClusterFtCreate is the cluster variant of [FtCreate].
func ClusterFtCreate(
	ctx context.Context,
	client clusterClient,
	indexName string,
	schema []options.Field,
	opts *options.FtCreateOptions,
) (string, error) {
	args, err := buildCreateArgs(indexName, schema, opts)
	if err != nil {
		return models.DefaultStringResponse, err
	}
	return toStringResult(execCluster(client, ctx, args))
}

func buildCreateArgs(indexName string, schema []options.Field, opts *options.FtCreateOptions) ([]string, error) {
	args := []string{ftCreateCommand, indexName}

	if opts != nil {
		optArgs, err := opts.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, optArgs...)
	}

	args = append(args, "SCHEMA")
	for _, field := range schema {
		fieldArgs, err := field.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, fieldArgs...)
	}

	return args, nil
}
