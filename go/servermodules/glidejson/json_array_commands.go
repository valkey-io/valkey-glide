// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glidejson

import (
	"context"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

const (
	jsonArrAppendCommand = "JSON.ARRAPPEND"
	jsonArrInsertCommand = "JSON.ARRINSERT"
	jsonArrIndexCommand  = "JSON.ARRINDEX"
	jsonArrLenCommand    = "JSON.ARRLEN"
	jsonArrPopCommand    = "JSON.ARRPOP"
	jsonArrTrimCommand   = "JSON.ARRTRIM"
)

// --- JSON.ARRAPPEND ---

// JsonArrAppend appends one or more values to the JSON array at the specified path.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path to the array within the JSON document.
//	values - The JSON values to append. JSON strings must be wrapped with quotes.
//
// Return value:
//
//	For JSONPath: An array of integers for each matched path (new array length), or nil for non-arrays.
//	For legacy path: The new length of the array.
//
// [valkey.io]: https://valkey.io/commands/json.arrappend/
func JsonArrAppend(
	client standaloneClient, ctx context.Context, key, path string, values []string,
) (any, error) {
	args := append([]string{jsonArrAppendCommand, key, path}, values...)
	return toAnyResult(execStandalone(client, ctx, args))
}

// ClusterJsonArrAppend is the cluster variant of [JsonArrAppend].
func ClusterJsonArrAppend(
	client clusterClient, ctx context.Context, key, path string, values []string,
) (any, error) {
	args := append([]string{jsonArrAppendCommand, key, path}, values...)
	return toAnyResult(execCluster(client, ctx, args))
}

// --- JSON.ARRINSERT ---

// JsonArrInsert inserts one or more values into the array at the specified path before the given index.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path to the array within the JSON document.
//	index  - The array index before which values are inserted.
//	values - The JSON values to insert. JSON strings must be wrapped with quotes.
//
// Return value:
//
//	For JSONPath: An array of integers for each matched path (new array length), or nil for non-arrays.
//	For legacy path: The new length of the array.
//
// [valkey.io]: https://valkey.io/commands/json.arrinsert/
func JsonArrInsert(
	client standaloneClient, ctx context.Context, key, path string, index int64, values []string,
) (any, error) {
	args := append([]string{jsonArrInsertCommand, key, path, strconv.FormatInt(index, 10)}, values...)
	return toAnyResult(execStandalone(client, ctx, args))
}

// ClusterJsonArrInsert is the cluster variant of [JsonArrInsert].
func ClusterJsonArrInsert(
	client clusterClient, ctx context.Context, key, path string, index int64, values []string,
) (any, error) {
	args := append([]string{jsonArrInsertCommand, key, path, strconv.FormatInt(index, 10)}, values...)
	return toAnyResult(execCluster(client, ctx, args))
}

// --- JSON.ARRINDEX ---

// JsonArrIndex searches for the first occurrence of a scalar JSON value in the arrays at the path.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path to the array within the JSON document.
//	scalar - The scalar value to search for (as a JSON-encoded string).
//
// Return value:
//
//	For JSONPath: An array of integers for each matched path (-1 if not found, nil for non-arrays).
//	For legacy path: The index of the matching element, or -1 if not found.
//
// [valkey.io]: https://valkey.io/commands/json.arrindex/
func JsonArrIndex(
	client standaloneClient, ctx context.Context, key, path, scalar string,
) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonArrIndexCommand, key, path, scalar}))
}

// JsonArrIndexWithOptions searches with start/end range options.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.arrindex/
func JsonArrIndexWithOptions(
	client standaloneClient, ctx context.Context, key, path, scalar string, opts *options.JsonArrIndexOptions,
) (any, error) {
	args := []string{jsonArrIndexCommand, key, path, scalar}
	if opts != nil {
		args = append(args, opts.ToArgs()...)
	}
	return toAnyResult(execStandalone(client, ctx, args))
}

// ClusterJsonArrIndex is the cluster variant of [JsonArrIndex].
func ClusterJsonArrIndex(
	client clusterClient, ctx context.Context, key, path, scalar string,
) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonArrIndexCommand, key, path, scalar}))
}

// ClusterJsonArrIndexWithOptions is the cluster variant of [JsonArrIndexWithOptions].
func ClusterJsonArrIndexWithOptions(
	client clusterClient, ctx context.Context, key, path, scalar string, opts *options.JsonArrIndexOptions,
) (any, error) {
	args := []string{jsonArrIndexCommand, key, path, scalar}
	if opts != nil {
		args = append(args, opts.ToArgs()...)
	}
	return toAnyResult(execCluster(client, ctx, args))
}

// --- JSON.ARRLEN ---

// JsonArrLen retrieves the length of the array at the root of the JSON document.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//
// Return value:
//
//	The array length at the root. If root is not an array, an error is raised.
//	If key doesn't exist, returns nil.
//
// [valkey.io]: https://valkey.io/commands/json.arrlen/
func JsonArrLen(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonArrLenCommand, key}))
}

// JsonArrLenWithPath retrieves the length of the array at the specified path.
//
// See [valkey.io] for details.
//
// Return value:
//
//	For JSONPath: An array of integers for each matched path, or nil for non-arrays.
//	For legacy path: The length of the array.
//
// [valkey.io]: https://valkey.io/commands/json.arrlen/
func JsonArrLenWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonArrLenCommand, key, path}))
}

// ClusterJsonArrLen is the cluster variant of [JsonArrLen].
func ClusterJsonArrLen(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonArrLenCommand, key}))
}

// ClusterJsonArrLenWithPath is the cluster variant of [JsonArrLenWithPath].
func ClusterJsonArrLenWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonArrLenCommand, key, path}))
}

// --- JSON.ARRPOP ---

// JsonArrPop pops the last element from the array at the root of the JSON document.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//
// Return value:
//
//	A string representing the popped JSON value, or nil if the array is empty.
//
// [valkey.io]: https://valkey.io/commands/json.arrpop/
func JsonArrPop(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonArrPopCommand, key}))
}

// JsonArrPopWithPath pops the last element from the array at the specified path.
//
// See [valkey.io] for details.
//
// Return value:
//
//	For JSONPath: An array of strings for each matched path (popped values), or nil for non-arrays/empty.
//	For legacy path: A string of the popped value, or nil if empty.
//
// [valkey.io]: https://valkey.io/commands/json.arrpop/
func JsonArrPopWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonArrPopCommand, key, path}))
}

// JsonArrPopWithPathAndIndex pops an element at the given index from the array at the specified path.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	index - The index of the element to pop. Out of boundary indexes are rounded to their respective array boundaries.
//
// [valkey.io]: https://valkey.io/commands/json.arrpop/
func JsonArrPopWithPathAndIndex(
	client standaloneClient, ctx context.Context, key, path string, index int64,
) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonArrPopCommand, key, path, strconv.FormatInt(index, 10)}))
}

// ClusterJsonArrPop is the cluster variant of [JsonArrPop].
func ClusterJsonArrPop(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonArrPopCommand, key}))
}

// ClusterJsonArrPopWithPath is the cluster variant of [JsonArrPopWithPath].
func ClusterJsonArrPopWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonArrPopCommand, key, path}))
}

// ClusterJsonArrPopWithPathAndIndex is the cluster variant of [JsonArrPopWithPathAndIndex].
func ClusterJsonArrPopWithPathAndIndex(
	client clusterClient, ctx context.Context, key, path string, index int64,
) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonArrPopCommand, key, path, strconv.FormatInt(index, 10)}))
}

// --- JSON.ARRTRIM ---

// JsonArrTrim trims the array at the specified path so that it becomes a subarray [start, end], both inclusive.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path to the array within the JSON document.
//	start  - The index of the first element to keep, inclusive.
//	end    - The index of the last element to keep, inclusive.
//
// Return value:
//
//	For JSONPath: An array of integers for each matched path (new array length), or nil for non-arrays.
//	For legacy path: The new length of the array.
//
// [valkey.io]: https://valkey.io/commands/json.arrtrim/
func JsonArrTrim(
	client standaloneClient, ctx context.Context, key, path string, start, end int64,
) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{
		jsonArrTrimCommand, key, path, strconv.FormatInt(start, 10), strconv.FormatInt(end, 10),
	}))
}

// ClusterJsonArrTrim is the cluster variant of [JsonArrTrim].
func ClusterJsonArrTrim(
	client clusterClient, ctx context.Context, key, path string, start, end int64,
) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{
		jsonArrTrimCommand, key, path, strconv.FormatInt(start, 10), strconv.FormatInt(end, 10),
	}))
}
