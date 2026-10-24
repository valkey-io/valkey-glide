// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
	"strings"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

const ftHybridCommand = "FT.HYBRID"

// FtHybrid combines a text/tag/numeric query (the SEARCH arm) with a vector similarity search
// (the VSIM arm) in a single query, fusing both arms' results into one ranked reply.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The name of the index to query.
//	searchArm - The text/tag/numeric query arm. See [options.FtHybridSearchArm].
//	vsimArm   - The vector similarity search arm. See [options.FtHybridVsimArm].
//	opts      - Optional arguments for the FT.HYBRID command, covering COMBINE, LOAD,
//	            FILTER/LIMIT/GROUPBY/SORTBY/APPLY clauses, WITHCURSOR, LOCALONLY, TIMEOUT, PARAMS
//	            and DIALECT. Pass nil to use defaults.
//
// Return value:
//
//	A slice of maps, each representing one result row with field name keys, after both arms'
//	results have been fused and any clauses in opts applied. With no LOAD clause in opts (the
//	default), each map contains just the document key and the fused score. When
//	opts.WithCursor is set, use [FtHybridWithCursor]/[ClusterFtHybridWithCursor] instead, since
//	the reply shape becomes a two-element [rows, cursorId] array rather than this plain row
//	slice.
//
// [valkey.io]: https://valkey.io/commands/ft.hybrid/
func FtHybrid(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	searchArm options.FtHybridSearchArm,
	vsimArm options.FtHybridVsimArm,
	opts *options.FtHybridOptions,
) ([]map[string]any, error) {
	args, err := buildHybridArgs(indexName, searchArm, vsimArm, opts)
	if err != nil {
		return nil, err
	}
	result, execErr := execStandalone(client, ctx, args)
	return parseFtAggregateResponse(result, execErr)
}

// ClusterFtHybrid is the cluster variant of [FtHybrid].
func ClusterFtHybrid(
	ctx context.Context,
	client clusterClient,
	indexName string,
	searchArm options.FtHybridSearchArm,
	vsimArm options.FtHybridVsimArm,
	opts *options.FtHybridOptions,
) ([]map[string]any, error) {
	args, err := buildHybridArgs(indexName, searchArm, vsimArm, opts)
	if err != nil {
		return nil, err
	}
	result, execErr := execCluster(client, ctx, args)
	return parseFtAggregateResponse(result, execErr)
}

// FtHybridWithCursor is [FtHybrid] for a query with opts.WithCursor set, returning the
// [rows, cursorId] pair directly rather than through [parseFtAggregateResponse]'s plain-row
// shape.
func FtHybridWithCursor(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	searchArm options.FtHybridSearchArm,
	vsimArm options.FtHybridVsimArm,
	opts *options.FtHybridOptions,
) ([]map[string]any, int64, error) {
	args, err := buildHybridArgs(indexName, searchArm, vsimArm, opts)
	if err != nil {
		return nil, 0, err
	}
	result, execErr := execStandalone(client, ctx, args)
	return parseCursorWrappedAggregateResponse(result, execErr)
}

// ClusterFtHybridWithCursor is the cluster variant of [FtHybridWithCursor]. Routed to the
// PRIMARY of indexName's slot regardless of the client's ReadFrom configuration -- a cursor is
// a per-process, node-local resource, so every subsequent page must be read from the same node
// that opened it.
func ClusterFtHybridWithCursor(
	ctx context.Context,
	client clusterClient,
	indexName string,
	searchArm options.FtHybridSearchArm,
	vsimArm options.FtHybridVsimArm,
	opts *options.FtHybridOptions,
) ([]map[string]any, int64, error) {
	args, err := buildHybridArgs(indexName, searchArm, vsimArm, opts)
	if err != nil {
		return nil, 0, err
	}
	result, execErr := execClusterPrimaryPinned(client, ctx, indexName, args)
	return parseCursorWrappedAggregateResponse(result, execErr)
}

// buildHybridArgs assembles the FT.HYBRID argv in the server's required tail order: SEARCH arm,
// VSIM arm, COMBINE (if any), then the FtHybridOptions tail (LOCALONLY, LOAD, TIMEOUT, PARAMS,
// clauses, WITHCURSOR, DIALECT). If vsimArm was given raw Vector bytes rather than VectorParam,
// a $-prefixed parameter name is generated here and merged into the PARAMS list by
// FtHybridOptions.ToArgs.
func buildHybridArgs(
	indexName string,
	searchArm options.FtHybridSearchArm,
	vsimArm options.FtHybridVsimArm,
	opts *options.FtHybridOptions,
) ([]string, error) {
	args := []string{ftHybridCommand, indexName}
	args = append(args, searchArm.ToArgs()...)

	generatedParamName := ""
	generatedParamValue := ""
	if vsimArm.IsParamReference() {
		vsimArgs, err := vsimArm.ToArgs("")
		if err != nil {
			return nil, err
		}
		args = append(args, vsimArgs...)
	} else {
		// Dashes stripped to match Python's uuid4().hex / Node's
		// randomUUID().replace(/-/g, "") -- the server rejects a PARAMS
		// name containing a dash ("contains an invalid character"),
		// confirmed live.
		generatedParamName = "$" + strings.ReplaceAll(uuid.New().String(), "-", "")
		generatedParamValue = string(vsimArm.Vector)
		vsimArgs, err := vsimArm.ToArgs(generatedParamName)
		if err != nil {
			return nil, err
		}
		args = append(args, vsimArgs...)
	}

	if opts != nil && opts.Combine != nil {
		combineArgs, err := opts.Combine.ToArgs()
		if err != nil {
			return nil, err
		}
		args = append(args, combineArgs...)
	}

	tailOpts := opts
	if tailOpts == nil {
		tailOpts = &options.FtHybridOptions{}
	}
	generatedKeyWithoutDollar := ""
	if generatedParamName != "" {
		generatedKeyWithoutDollar = generatedParamName[1:]
	}
	tailArgs, err := tailOpts.ToArgs(generatedKeyWithoutDollar, generatedParamValue)
	if err != nil {
		return nil, err
	}
	args = append(args, tailArgs...)

	return args, nil
}
