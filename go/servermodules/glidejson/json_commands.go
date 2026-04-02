// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glidejson

import (
	"context"

	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

const (
	jsonSetCommand = "JSON.SET"
	jsonGetCommand = "JSON.GET"
)

// standaloneClient is the interface for standalone client JSON operations.
type standaloneClient interface {
	CustomCommand(ctx context.Context, args []string) (any, error)
}

// clusterClient is the interface for cluster client JSON operations.
type clusterClient interface {
	CustomCommand(ctx context.Context, args []string) (models.ClusterValue[any], error)
}

// JsonSet sets the JSON value at the specified path stored at key.
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
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.set/
func JsonSet(client standaloneClient, ctx context.Context, key string, path string, value string) (string, error) {
	result, err := client.CustomCommand(ctx, []string{jsonSetCommand, key, path, value})
	if err != nil {
		return "", err
	}
	if result == nil {
		return "", nil
	}
	return result.(string), nil
}

// JsonSetWithCondition sets the JSON value at the specified path stored at key with a conditional set option.
//
// Parameters:
//
//	client       - The Valkey GLIDE client to execute the command.
//	ctx          - The context for controlling the command execution.
//	key          - The key of the JSON document.
//	path         - The path within the JSON document where the value will be set.
//	value        - The value to set at the specific path, in JSON formatted string.
//	setCondition - Set the value only if the given condition is met (within the key or path).
//	               Use [constants.OnlyIfExists] ("XX") or [constants.OnlyIfDoesNotExist] ("NX").
//
// Return value:
//
//	A simple "OK" response if the value is successfully set. If value isn't set because of
//	setCondition, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.set/
func JsonSetWithCondition(
	client standaloneClient,
	ctx context.Context,
	key string,
	path string,
	value string,
	setCondition constants.ConditionalSet,
) (string, error) {
	result, err := client.CustomCommand(
		ctx,
		[]string{jsonSetCommand, key, path, value, string(setCondition)},
	)
	if err != nil {
		return "", err
	}
	if result == nil {
		return "", nil
	}
	return result.(string), nil
}

// JsonGet retrieves the JSON value stored at key.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//
// Return value:
//
//	Returns a string representation of the JSON document. If key doesn't exist, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func JsonGet(client standaloneClient, ctx context.Context, key string) (string, error) {
	result, err := client.CustomCommand(ctx, []string{jsonGetCommand, key})
	if err != nil {
		return "", err
	}
	if result == nil {
		return "", nil
	}
	return result.(string), nil
}

// JsonGetWithPaths retrieves the JSON value at the specified paths stored at key.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	paths  - List of paths within the JSON document.
//
// Return value:
//
//	If one path is given:
//	  - For JSONPath (path starts with $): Returns a stringified JSON list of values.
//	  - For legacy path: Returns a string representation of the value.
//	If multiple paths are given: Returns a stringified JSON where each path is a key
//	and its corresponding value is the result.
//	If key doesn't exist, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func JsonGetWithPaths(
	client standaloneClient,
	ctx context.Context,
	key string,
	paths []string,
) (string, error) {
	args := append([]string{jsonGetCommand, key}, paths...)
	result, err := client.CustomCommand(ctx, args)
	if err != nil {
		return "", err
	}
	if result == nil {
		return "", nil
	}
	return result.(string), nil
}

// JsonGetWithOptions retrieves the JSON value at the specified paths stored at key with formatting options.
//
// Parameters:
//
//	client  - The Valkey GLIDE client to execute the command.
//	ctx     - The context for controlling the command execution.
//	key     - The key of the JSON document.
//	paths   - List of paths within the JSON document.
//	opts    - Options for formatting the JSON response (indent, newline, space).
//
// Return value:
//
//	Returns a formatted string representation of the JSON value(s).
//	If key doesn't exist, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func JsonGetWithOptions(
	client standaloneClient,
	ctx context.Context,
	key string,
	paths []string,
	opts *options.JsonGetOptions,
) (string, error) {
	args := []string{jsonGetCommand, key}
	if opts != nil {
		args = append(args, opts.ToArgs()...)
	}
	args = append(args, paths...)
	result, err := client.CustomCommand(ctx, args)
	if err != nil {
		return "", err
	}
	if result == nil {
		return "", nil
	}
	return result.(string), nil
}

// ClusterJsonSet sets the JSON value at the specified path stored at key (cluster client).
//
// Parameters:
//
//	client - The Valkey GLIDE cluster client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	path   - The path within the JSON document where the value will be set.
//	value  - The value to set at the specific path, in JSON formatted string.
//
// Return value:
//
//	A simple "OK" response if the value is successfully set.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.set/
func ClusterJsonSet(
	client clusterClient,
	ctx context.Context,
	key string,
	path string,
	value string,
) (string, error) {
	result, err := client.CustomCommand(ctx, []string{jsonSetCommand, key, path, value})
	if err != nil {
		return "", err
	}
	val := result.SingleValue()
	if val == nil {
		return "", nil
	}
	return val.(string), nil
}

// ClusterJsonSetWithCondition sets the JSON value at the specified path stored at key with a conditional
// set option (cluster client).
//
// Parameters:
//
//	client       - The Valkey GLIDE cluster client to execute the command.
//	ctx          - The context for controlling the command execution.
//	key          - The key of the JSON document.
//	path         - The path within the JSON document where the value will be set.
//	value        - The value to set at the specific path, in JSON formatted string.
//	setCondition - Set the value only if the given condition is met.
//
// Return value:
//
//	A simple "OK" response if the value is successfully set. If value isn't set because of
//	setCondition, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.set/
func ClusterJsonSetWithCondition(
	client clusterClient,
	ctx context.Context,
	key string,
	path string,
	value string,
	setCondition constants.ConditionalSet,
) (string, error) {
	result, err := client.CustomCommand(
		ctx,
		[]string{jsonSetCommand, key, path, value, string(setCondition)},
	)
	if err != nil {
		return "", err
	}
	val := result.SingleValue()
	if val == nil {
		return "", nil
	}
	return val.(string), nil
}

// ClusterJsonGet retrieves the JSON value stored at key (cluster client).
//
// Parameters:
//
//	client - The Valkey GLIDE cluster client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//
// Return value:
//
//	Returns a string representation of the JSON document. If key doesn't exist, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func ClusterJsonGet(client clusterClient, ctx context.Context, key string) (string, error) {
	result, err := client.CustomCommand(ctx, []string{jsonGetCommand, key})
	if err != nil {
		return "", err
	}
	val := result.SingleValue()
	if val == nil {
		return "", nil
	}
	return val.(string), nil
}

// ClusterJsonGetWithPaths retrieves the JSON value at the specified paths stored at key (cluster client).
//
// Parameters:
//
//	client - The Valkey GLIDE cluster client to execute the command.
//	ctx    - The context for controlling the command execution.
//	key    - The key of the JSON document.
//	paths  - List of paths within the JSON document.
//
// Return value:
//
//	Returns a string representation of the JSON value(s). If key doesn't exist, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func ClusterJsonGetWithPaths(
	client clusterClient,
	ctx context.Context,
	key string,
	paths []string,
) (string, error) {
	args := append([]string{jsonGetCommand, key}, paths...)
	result, err := client.CustomCommand(ctx, args)
	if err != nil {
		return "", err
	}
	val := result.SingleValue()
	if val == nil {
		return "", nil
	}
	return val.(string), nil
}

// ClusterJsonGetWithOptions retrieves the JSON value at the specified paths stored at key with
// formatting options (cluster client).
//
// Parameters:
//
//	client  - The Valkey GLIDE cluster client to execute the command.
//	ctx     - The context for controlling the command execution.
//	key     - The key of the JSON document.
//	paths   - List of paths within the JSON document.
//	opts    - Options for formatting the JSON response (indent, newline, space).
//
// Return value:
//
//	Returns a formatted string representation of the JSON value(s).
//	If key doesn't exist, returns an empty string.
//
// See [valkey.io] for details.
//
// [valkey.io]: https://valkey.io/commands/json.get/
func ClusterJsonGetWithOptions(
	client clusterClient,
	ctx context.Context,
	key string,
	paths []string,
	opts *options.JsonGetOptions,
) (string, error) {
	args := []string{jsonGetCommand, key}
	if opts != nil {
		args = append(args, opts.ToArgs()...)
	}
	args = append(args, paths...)
	result, err := client.CustomCommand(ctx, args)
	if err != nil {
		return "", err
	}
	val := result.SingleValue()
	if val == nil {
		return "", nil
	}
	return val.(string), nil
}
