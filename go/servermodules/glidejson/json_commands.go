// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glidejson

import (
	"context"
	"strconv"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

const (
	jsonSetCommand        = "JSON.SET"
	jsonGetCommand        = "JSON.GET"
	jsonDelCommand        = "JSON.DEL"
	jsonForgetCommand     = "JSON.FORGET"
	jsonClearCommand      = "JSON.CLEAR"
	jsonMGetCommand       = "JSON.MGET"
	jsonTypeCommand       = "JSON.TYPE"
	jsonNumIncrByCommand  = "JSON.NUMINCRBY"
	jsonNumMultByCommand  = "JSON.NUMMULTBY"
	jsonToggleCommand     = "JSON.TOGGLE"
	jsonStrAppendCommand  = "JSON.STRAPPEND"
	jsonStrLenCommand     = "JSON.STRLEN"
	jsonObjLenCommand     = "JSON.OBJLEN"
	jsonObjKeysCommand    = "JSON.OBJKEYS"
	jsonRespCommand       = "JSON.RESP"
	jsonDebugCommand      = "JSON.DEBUG"
	jsonDebugMemorySubCmd = "MEMORY"
	jsonDebugFieldsSubCmd = "FIELDS"
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

// --- JSON.NUMINCRBY ---

// JsonNumIncrBy increments the numeric value at the specified path by the given number.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path within the JSON document.
//	number - The number to increment by.
//
// Return value:
//
//	For JSONPath: A string representation of an array of new values after increment, or null for non-numbers.
//	For legacy path: A string representation of the new value.
//
// [valkey.io]: https://valkey.io/commands/json.numincrby/
func JsonNumIncrBy(
	client standaloneClient, ctx context.Context, key, path string, number float64,
) (string, error) {
	return toStringResult(
		execStandalone(client, ctx, []string{jsonNumIncrByCommand, key, path, strconv.FormatFloat(number, 'f', -1, 64)}),
	)
}

// ClusterJsonNumIncrBy is the cluster variant of [JsonNumIncrBy].
func ClusterJsonNumIncrBy(
	client clusterClient, ctx context.Context, key, path string, number float64,
) (string, error) {
	return toStringResult(
		execCluster(client, ctx, []string{jsonNumIncrByCommand, key, path, strconv.FormatFloat(number, 'f', -1, 64)}),
	)
}

// --- JSON.NUMMULTBY ---

// JsonNumMultBy multiplies the numeric value at the specified path by the given number.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path within the JSON document.
//	number - The number to multiply by.
//
// Return value:
//
//	For JSONPath: A string representation of an array of new values after multiplication, or null for non-numbers.
//	For legacy path: A string representation of the new value.
//
// [valkey.io]: https://valkey.io/commands/json.nummultby/
func JsonNumMultBy(
	client standaloneClient, ctx context.Context, key, path string, number float64,
) (string, error) {
	return toStringResult(
		execStandalone(client, ctx, []string{jsonNumMultByCommand, key, path, strconv.FormatFloat(number, 'f', -1, 64)}),
	)
}

// ClusterJsonNumMultBy is the cluster variant of [JsonNumMultBy].
func ClusterJsonNumMultBy(
	client clusterClient, ctx context.Context, key, path string, number float64,
) (string, error) {
	return toStringResult(
		execCluster(client, ctx, []string{jsonNumMultByCommand, key, path, strconv.FormatFloat(number, 'f', -1, 64)}),
	)
}

// --- JSON.TOGGLE ---

// JsonToggle toggles a boolean value at the root of the JSON document.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.toggle/
func JsonToggle(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonToggleCommand, key}))
}

// JsonToggleWithPath toggles a boolean value at the specified path.
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
//	For JSONPath: An array of booleans for each matched path, or nil for non-booleans.
//	For legacy path: The toggled boolean value.
//
// [valkey.io]: https://valkey.io/commands/json.toggle/
func JsonToggleWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonToggleCommand, key, path}))
}

// ClusterJsonToggle is the cluster variant of [JsonToggle].
func ClusterJsonToggle(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonToggleCommand, key}))
}

// ClusterJsonToggleWithPath is the cluster variant of [JsonToggleWithPath].
func ClusterJsonToggleWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonToggleCommand, key, path}))
}

// --- JSON.STRAPPEND ---

// JsonStrAppend appends a string value to the string at the root of the JSON document.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.strappend/
func JsonStrAppend(client standaloneClient, ctx context.Context, key, value string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonStrAppendCommand, key, value}))
}

// JsonStrAppendWithPath appends a string value at the specified path.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path within the JSON document.
//	value  - The string to append. Must be JSON-encoded (e.g., "\"foo\"" to append foo).
//
// Return value:
//
//	For JSONPath: An array of integers (new string lengths), or nil for non-strings.
//	For legacy path: The new string length.
//
// [valkey.io]: https://valkey.io/commands/json.strappend/
func JsonStrAppendWithPath(
	client standaloneClient, ctx context.Context, key, path, value string,
) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonStrAppendCommand, key, path, value}))
}

// ClusterJsonStrAppend is the cluster variant of [JsonStrAppend].
func ClusterJsonStrAppend(client clusterClient, ctx context.Context, key, value string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonStrAppendCommand, key, value}))
}

// ClusterJsonStrAppendWithPath is the cluster variant of [JsonStrAppendWithPath].
func ClusterJsonStrAppendWithPath(
	client clusterClient, ctx context.Context, key, path, value string,
) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonStrAppendCommand, key, path, value}))
}

// --- JSON.STRLEN ---

// JsonStrLen returns the length of the string at the root of the JSON document.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.strlen/
func JsonStrLen(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonStrLenCommand, key}))
}

// JsonStrLenWithPath returns the length of the string at the specified path.
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
//	For JSONPath: An array of integers (string lengths), or nil for non-strings.
//	For legacy path: The string length, or nil if key doesn't exist.
//
// [valkey.io]: https://valkey.io/commands/json.strlen/
func JsonStrLenWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonStrLenCommand, key, path}))
}

// ClusterJsonStrLen is the cluster variant of [JsonStrLen].
func ClusterJsonStrLen(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonStrLenCommand, key}))
}

// ClusterJsonStrLenWithPath is the cluster variant of [JsonStrLenWithPath].
func ClusterJsonStrLenWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonStrLenCommand, key, path}))
}

// --- JSON.OBJLEN ---

// JsonObjLen returns the number of keys in the object at the root of the JSON document.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.objlen/
func JsonObjLen(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonObjLenCommand, key}))
}

// JsonObjLenWithPath returns the number of keys in the object at the specified path.
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
//	For JSONPath: An array of integers (object sizes), or nil for non-objects.
//	For legacy path: The number of keys in the object.
//
// [valkey.io]: https://valkey.io/commands/json.objlen/
func JsonObjLenWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonObjLenCommand, key, path}))
}

// ClusterJsonObjLen is the cluster variant of [JsonObjLen].
func ClusterJsonObjLen(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonObjLenCommand, key}))
}

// ClusterJsonObjLenWithPath is the cluster variant of [JsonObjLenWithPath].
func ClusterJsonObjLenWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonObjLenCommand, key, path}))
}

// --- JSON.OBJKEYS ---

// JsonObjKeys returns the key names in the object at the root of the JSON document.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.objkeys/
func JsonObjKeys(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonObjKeysCommand, key}))
}

// JsonObjKeysWithPath returns the key names in the object at the specified path.
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
//	For JSONPath: A nested array of key names for each matched object, or nil for non-objects.
//	For legacy path: An array of key names.
//
// [valkey.io]: https://valkey.io/commands/json.objkeys/
func JsonObjKeysWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonObjKeysCommand, key, path}))
}

// ClusterJsonObjKeys is the cluster variant of [JsonObjKeys].
func ClusterJsonObjKeys(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonObjKeysCommand, key}))
}

// ClusterJsonObjKeysWithPath is the cluster variant of [JsonObjKeysWithPath].
func ClusterJsonObjKeysWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonObjKeysCommand, key, path}))
}

// --- JSON.RESP ---

// JsonResp returns the JSON document in RESP format.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.resp/
func JsonResp(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonRespCommand, key}))
}

// JsonRespWithPath returns the JSON value at the specified path in RESP format.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.resp/
func JsonRespWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonRespCommand, key, path}))
}

// ClusterJsonResp is the cluster variant of [JsonResp].
func ClusterJsonResp(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonRespCommand, key}))
}

// ClusterJsonRespWithPath is the cluster variant of [JsonRespWithPath].
func ClusterJsonRespWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonRespCommand, key, path}))
}

// --- JSON.DEBUG MEMORY ---

// JsonDebugMemory reports total memory usage in bytes of the JSON document.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.debug-memory/
func JsonDebugMemory(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonDebugCommand, jsonDebugMemorySubCmd, key}))
}

// JsonDebugMemoryWithPath reports memory usage at the specified path.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.debug-memory/
func JsonDebugMemoryWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonDebugCommand, jsonDebugMemorySubCmd, key, path}))
}

// ClusterJsonDebugMemory is the cluster variant of [JsonDebugMemory].
func ClusterJsonDebugMemory(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonDebugCommand, jsonDebugMemorySubCmd, key}))
}

// ClusterJsonDebugMemoryWithPath is the cluster variant of [JsonDebugMemoryWithPath].
func ClusterJsonDebugMemoryWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonDebugCommand, jsonDebugMemorySubCmd, key, path}))
}

// --- JSON.DEBUG FIELDS ---

// JsonDebugFields reports the total number of fields in the JSON document.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.debug-fields/
func JsonDebugFields(client standaloneClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonDebugCommand, jsonDebugFieldsSubCmd, key}))
}

// JsonDebugFieldsWithPath reports the number of fields at the specified path.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.debug-fields/
func JsonDebugFieldsWithPath(client standaloneClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execStandalone(client, ctx, []string{jsonDebugCommand, jsonDebugFieldsSubCmd, key, path}))
}

// ClusterJsonDebugFields is the cluster variant of [JsonDebugFields].
func ClusterJsonDebugFields(client clusterClient, ctx context.Context, key string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonDebugCommand, jsonDebugFieldsSubCmd, key}))
}

// ClusterJsonDebugFieldsWithPath is the cluster variant of [JsonDebugFieldsWithPath].
func ClusterJsonDebugFieldsWithPath(client clusterClient, ctx context.Context, key, path string) (any, error) {
	return toAnyResult(execCluster(client, ctx, []string{jsonDebugCommand, jsonDebugFieldsSubCmd, key, path}))
}
