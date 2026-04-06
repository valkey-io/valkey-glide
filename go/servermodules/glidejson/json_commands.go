// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glidejson

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

const (
	jsonSetCommand    = "JSON.SET"
	jsonGetCommand    = "JSON.GET"
	jsonDelCommand    = "JSON.DEL"
	jsonForgetCommand = "JSON.FORGET"
	jsonClearCommand  = "JSON.CLEAR"
	jsonMGetCommand   = "JSON.MGET"
	jsonTypeCommand   = "JSON.TYPE"
)

// standaloneClient is the interface for standalone client JSON operations.
type standaloneClient interface {
	CustomCommand(ctx context.Context, args []string) (any, error)
}

// clusterClient is the interface for cluster client JSON operations.
type clusterClient interface {
	CustomCommand(ctx context.Context, args []string) (models.ClusterValue[any], error)
}

func execStandalone(client standaloneClient, ctx context.Context, args []string) (any, error) {
	return client.CustomCommand(ctx, args)
}

func execCluster(client clusterClient, ctx context.Context, args []string) (any, error) {
	result, err := client.CustomCommand(ctx, args)
	if err != nil {
		return nil, err
	}
	return result.SingleValue(), nil
}

func toStringResult(result any, err error) (string, error) {
	if err != nil {
		return "", err
	}
	if result == nil {
		return "", nil
	}
	return result.(string), nil
}

func toAnyResult(result any, err error) (any, error) {
	return result, err
}

// --- JSON.SET ---

// JsonSet sets the JSON value at the specified path stored at key.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path within the JSON document where the value will be set.
//	value  - The value to set at the specific path, in JSON formatted string.
//
// Return value:
//
//	A simple "OK" response if the value is successfully set.
//
// [valkey.io]: https://valkey.io/commands/json.set/
func JsonSet(client standaloneClient, ctx context.Context, key string, path string, value string) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{jsonSetCommand, key, path, value}))
}

// JsonSetWithCondition sets the JSON value with a conditional set option.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client       - The Valkey GLIDE client to execute the command.
//	ctx          - The context for controlling the command execution.
//	key          - The key of the JSON document.
//	path         - The path within the JSON document where the value will be set.
//	value        - The value to set at the specific path, in JSON formatted string.
//	setCondition - Use [constants.OnlyIfExists] ("XX") or [constants.OnlyIfDoesNotExist] ("NX").
//
// Return value:
//
//	"OK" if set, empty string if condition not met.
//
// [valkey.io]: https://valkey.io/commands/json.set/
func JsonSetWithCondition(
	client standaloneClient, ctx context.Context, key, path, value string, setCondition constants.ConditionalSet,
) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{jsonSetCommand, key, path, value, string(setCondition)}))
}

// ClusterJsonSet is the cluster variant of [JsonSet].
func ClusterJsonSet(client clusterClient, ctx context.Context, key, path, value string) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{jsonSetCommand, key, path, value}))
}

// ClusterJsonSetWithCondition is the cluster variant of [JsonSetWithCondition].
func ClusterJsonSetWithCondition(
	client clusterClient, ctx context.Context, key, path, value string, setCondition constants.ConditionalSet,
) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{jsonSetCommand, key, path, value, string(setCondition)}))
}

// --- JSON.GET ---

// JsonGet retrieves the JSON value stored at key.
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
//	A string representation of the JSON document, or empty string if key doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func JsonGet(client standaloneClient, ctx context.Context, key string) (string, error) {
	return toStringResult(execStandalone(client, ctx, []string{jsonGetCommand, key}))
}

// JsonGetWithPaths retrieves the JSON value at the specified paths.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func JsonGetWithPaths(client standaloneClient, ctx context.Context, key string, paths []string) (string, error) {
	return toStringResult(execStandalone(client, ctx, append([]string{jsonGetCommand, key}, paths...)))
}

// JsonGetWithOptions retrieves the JSON value with formatting options.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func JsonGetWithOptions(
	client standaloneClient, ctx context.Context, key string, paths []string, opts *options.JsonGetOptions,
) (string, error) {
	args := []string{jsonGetCommand, key}
	if opts != nil {
		args = append(args, opts.ToArgs()...)
	}
	return toStringResult(execStandalone(client, ctx, append(args, paths...)))
}

// ClusterJsonGet is the cluster variant of [JsonGet].
func ClusterJsonGet(client clusterClient, ctx context.Context, key string) (string, error) {
	return toStringResult(execCluster(client, ctx, []string{jsonGetCommand, key}))
}

// ClusterJsonGetWithPaths is the cluster variant of [JsonGetWithPaths].
func ClusterJsonGetWithPaths(client clusterClient, ctx context.Context, key string, paths []string) (string, error) {
	return toStringResult(execCluster(client, ctx, append([]string{jsonGetCommand, key}, paths...)))
}

// ClusterJsonGetWithOptions is the cluster variant of [JsonGetWithOptions].
func ClusterJsonGetWithOptions(
	client clusterClient, ctx context.Context, key string, paths []string, opts *options.JsonGetOptions,
) (string, error) {
	args := []string{jsonGetCommand, key}
	if opts != nil {
		args = append(args, opts.ToArgs()...)
	}
	return toStringResult(execCluster(client, ctx, append(args, paths...)))
}

// --- JSON.DEL ---

// JsonDel deletes the JSON value at the specified path.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path within the JSON document. If not provided, deletes the entire document.
//
// Return value:
//
//	The number of elements deleted. 0 if the key does not exist, or the path is invalid.
//
// [valkey.io]: https://valkey.io/commands/json.del/
func JsonDel(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonDelCommand, key}))
}

// JsonDelWithPath deletes the JSON value at the specified path.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.del/
func JsonDelWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonDelCommand, key, path}))
}

// ClusterJsonDel is the cluster variant of [JsonDel].
func ClusterJsonDel(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonDelCommand, key}))
}

// ClusterJsonDelWithPath is the cluster variant of [JsonDelWithPath].
func ClusterJsonDelWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonDelCommand, key, path}))
}

// --- JSON.FORGET (alias for JSON.DEL) ---

// JsonForget deletes the JSON value at the specified path. Alias for [JsonDel].
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.forget/
func JsonForget(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonForgetCommand, key}))
}

// JsonForgetWithPath deletes the JSON value at the specified path. Alias for [JsonDelWithPath].
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.forget/
func JsonForgetWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonForgetCommand, key, path}))
}

// ClusterJsonForget is the cluster variant of [JsonForget].
func ClusterJsonForget(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonForgetCommand, key}))
}

// ClusterJsonForgetWithPath is the cluster variant of [JsonForgetWithPath].
func ClusterJsonForgetWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonForgetCommand, key, path}))
}

// --- JSON.CLEAR ---

// JsonClear clears arrays and objects at the root of the JSON document.
// Numeric values are set to 0, booleans to false, strings to empty.
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
//	The number of containers cleared, or 0 if already empty.
//
// [valkey.io]: https://valkey.io/commands/json.clear/
func JsonClear(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonClearCommand, key}))
}

// JsonClearWithPath clears values at the specified path.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.clear/
func JsonClearWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonClearCommand, key, path}))
}

// ClusterJsonClear is the cluster variant of [JsonClear].
func ClusterJsonClear(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonClearCommand, key}))
}

// ClusterJsonClearWithPath is the cluster variant of [JsonClearWithPath].
func ClusterJsonClearWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonClearCommand, key, path}))
}

// --- JSON.MGET ---

// JsonMGet retrieves the JSON values at the specified path from multiple keys.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	keys   - The keys of the JSON documents.
//	path   - The path within the JSON documents.
//
// Return value:
//
//	An array of values for each key. Nil elements for keys that don't exist.
//
// [valkey.io]: https://valkey.io/commands/json.mget/
func JsonMGet(client standaloneClient, ctx context.Context, keys []string, path string) (any, error) {
	args := append(append([]string{jsonMGetCommand}, keys...), path)
	return toAnyResult(execStandalone(client, ctx, args))
}

// ClusterJsonMGet is the cluster variant of [JsonMGet].
func ClusterJsonMGet(client clusterClient, ctx context.Context, keys []string, path string) (any, error) {
	args := append(append([]string{jsonMGetCommand}, keys...), path)
	return toAnyResult(execCluster(client, ctx, args))
}

// --- JSON.TYPE ---

// JsonType reports the type of the JSON value at the specified path.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path within the JSON document.
//
// Return value:
//
//	For JSONPath: Returns an array of strings for each matched path.
//	For legacy path: Returns a string of the type.
//	If key doesn't exist, returns nil.
//
// [valkey.io]: https://valkey.io/commands/json.type/
func JsonType(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonTypeCommand, key}))
}

// JsonTypeWithPath reports the type at the specified path.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.type/
func JsonTypeWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonTypeCommand, key, path}))
}

// ClusterJsonType is the cluster variant of [JsonType].
func ClusterJsonType(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonTypeCommand, key}))
}

// ClusterJsonTypeWithPath is the cluster variant of [JsonTypeWithPath].
func ClusterJsonTypeWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonTypeCommand, key, path}))
}
