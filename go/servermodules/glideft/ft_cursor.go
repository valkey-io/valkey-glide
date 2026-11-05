// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glideft

import (
	"context"
	"strconv"
)

const (
	ftCursorCommand     = "FT.CURSOR"
	ftCursorReadKeyword = "READ"
	ftCursorDelKeyword  = "DEL"
)

// FtCursorRead reads the next page of results from a cursor opened by [FtAggregateWithCursor]/
// [ClusterFtAggregateWithCursor] or [FtHybridWithCursor]/[ClusterFtHybridWithCursor].
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The index name the cursor was opened against.
//	cursorID  - The cursor id, from the opening command's reply or a previous FtCursorRead call.
//	            0 means the cursor is already exhausted; calling this with 0 is a caller error,
//	            not something this function special-cases.
//	count     - Overrides how many rows this page returns, instead of the count the cursor was
//	            opened with. Pass nil to leave it unset.
//
// Return value:
//
//	A two-element [rows, cursorId] pair, where rows is the same per-document mapping shape
//	FtAggregate/FtHybrid return, and cursorId is 0 once the cursor is exhausted, or the id to
//	pass to the next FtCursorRead call otherwise.
//
// [valkey.io]: https://valkey.io/commands/ft.cursor/
func FtCursorRead(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	cursorID int64,
	count *int,
) ([]map[string]any, int64, error) {
	args := buildCursorReadArgs(indexName, cursorID, count)
	result, execErr := execStandalone(client, ctx, args)
	return parseCursorWrappedAggregateResponse(result, execErr)
}

// ClusterFtCursorRead is the cluster variant of [FtCursorRead]. Routed to the PRIMARY of
// indexName's slot regardless of the client's ReadFrom configuration -- the cursor is a
// per-process, node-local resource on the server that opened it, so a read that lands on a
// different node (a replica a PREFER_REPLICA/AZ-affinity client would otherwise have used)
// fails with "Cursor not found".
func ClusterFtCursorRead(
	ctx context.Context,
	client clusterClient,
	indexName string,
	cursorID int64,
	count *int,
) ([]map[string]any, int64, error) {
	args := buildCursorReadArgs(indexName, cursorID, count)
	result, execErr := execClusterPrimaryPinned(client, ctx, indexName, args)
	return parseCursorWrappedAggregateResponse(result, execErr)
}

func buildCursorReadArgs(indexName string, cursorID int64, count *int) []string {
	args := []string{ftCursorCommand, ftCursorReadKeyword, indexName, strconv.FormatInt(cursorID, 10)}
	if count != nil {
		args = append(args, "COUNT", strconv.Itoa(*count))
	}
	return args
}

func buildCursorDelArgs(indexName string, cursorID int64) []string {
	return []string{ftCursorCommand, ftCursorDelKeyword, indexName, strconv.FormatInt(cursorID, 10)}
}

// FtCursorDel deletes an open cursor, discarding it before it would otherwise be exhausted or
// time out.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	ctx       - The context for controlling the command execution.
//	client    - The Valkey GLIDE client to execute the command.
//	indexName - The index name the cursor was opened against.
//	cursorID  - The cursor id to delete.
//
// Return value:
//
//	"OK" on success.
//
// [valkey.io]: https://valkey.io/commands/ft.cursor/
func FtCursorDel(
	ctx context.Context,
	client standaloneClient,
	indexName string,
	cursorID int64,
) (string, error) {
	args := buildCursorDelArgs(indexName, cursorID)
	return toStringResult(execStandalone(client, ctx, args))
}

// ClusterFtCursorDel is the cluster variant of [FtCursorDel]. Routed to the PRIMARY of
// indexName's slot regardless of the client's ReadFrom configuration -- see
// [ClusterFtCursorRead]'s docstring.
func ClusterFtCursorDel(
	ctx context.Context,
	client clusterClient,
	indexName string,
	cursorID int64,
) (string, error) {
	args := buildCursorDelArgs(indexName, cursorID)
	return toStringResult(execClusterPrimaryPinned(client, ctx, indexName, args))
}
