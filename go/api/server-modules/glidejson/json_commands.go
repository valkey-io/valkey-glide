// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package glidejson

import (
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/errors"
	jsonOptions "github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson/options"
)

const (
	JsonSet = "JSON.SET"
	JsonGet = "JSON.GET"
)

func executeCommandWithReturnMap(client api.BaseClient, args []string, returnMap bool) (interface{}, error) {
	switch client := client.(type) {
	case *api.GlideClient:
		return client.CustomCommand(args)
	case *api.GlideClusterClient:
		result, err := client.CustomCommand(args)
		if result.IsEmpty() {
			return nil, err
		}
		if returnMap {
			return result.MultiValue(), err
		} else {
			return result.SingleValue(), err
		}
	default:
		return nil, &errors.RequestError{Msg: "Unknown type of client, should be either `GlideClient` or `GlideClusterClient`"}
	}
}

func executeCommand(client api.BaseClient, args []string) (interface{}, error) {
	return executeCommandWithReturnMap(client, args, false)
}

// Sets the JSON value at the specified `path` stored at `key`. This definition of JSON.SET command
// does not include the optional arguments of the command.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	key    - The `key` of the JSON document.
//	path   - Represents the path within the JSON document where the value will be set. The key
//	    	will be modified only if `value` is added as the last child in the specified
//	        `path`, or if the specified `path` acts as the parent of a new child being added.
//	value  - The value to set at the specific path, in JSON formatted string.
//
// Return value:
//
//	A simple `"OK"` response if the value is successfully set.
//
// [valkey.io]: https://valkey.io/commands/json.set/
func Set(
	client api.BaseClient,
	key string,
	path string,
	value string,
) (string, error) {
	result, err := executeCommand(client, []string{JsonSet, key, path, value})
	if err != nil {
		return api.DefaultStringResponse, err
	}

	return result.(string), err
}

// Sets the JSON value at the specified `path` stored at `key`. This definition of JSON.SET command
// does includes the optional arguments of the command.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client  - The Valkey GLIDE client to execute the command.
//	key     - The `key` of the JSON document.
//	path    - Represents the path within the JSON document where the value will be set. The key
//		    	will be modified only if `value` is added as the last child in the specified
//		        `path`, or if the specified `path` acts as the parent of a new child being added.
//	value   - The value to set at the specific path, in JSON formatted string.
//	options - The [api.JsonGetOptions]. Contains the conditionalSet option which sets the value
//			  only if the given condition is met (within the key or path).
//
// Return value:
//
//	Returns an api.Result[string] containing a simple "OK" response if the value is
//	successfully set. If value isn't set because of `options` containing setCondition,
//	returns api.CreateNilStringResult().
//
// [valkey.io]: https://valkey.io/commands/json.set/
func SetWithOptions(
	client api.BaseClient,
	key string,
	path string,
	value string,
	options jsonOptions.JsonSetOptions,
) (api.Result[string], error) {
	args := []string{JsonSet, key, path, value}
	optionalArgs, err := options.ToArgs()
	if err != nil {
		return api.CreateNilStringResult(), err
	}
	args = append(args, optionalArgs...)
	result, err := executeCommand(client, args)
	if err != nil || result == nil {
		return api.CreateNilStringResult(), err
	}
	return api.CreateStringResult(result.(string)), err
}

// Retrieves the JSON value at the specified `path` stored at `key`. This definition of JSON.GET command
// does not include the optional arguments of the command.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client - The Valkey GLIDE client to execute the command.
//	key    - The `key` of the JSON document.
//
// Return value:
//
//	Returns an api.Result[string] containing a string representation of the JSON document.
//	If `key` doesn't exist, returns api.CreateNilStringResult().
//
// [valkey.io]: https://valkey.io/commands/json.get/
func Get(client api.BaseClient, key string) (api.Result[string], error) {
	result, err := executeCommand(client, []string{JsonGet, key})
	if err != nil || result == nil {
		return api.CreateNilStringResult(), err
	}
	return api.CreateStringResult(result.(string)), err
}

// Retrieves the JSON value at the specified `path` stored at `key`. This definition of JSON.GET includes
// optional arguments of the command.
//
// See [valkey.io] for details.
//
// Parameters:
//
//	client  - The Valkey GLIDE client to execute the command.
//	key     - The `key` of the JSON document.
//	options - The [api.JsonGetOptions].
//
// Return value:
//
//	Returns an api.Result[string] containing a string representation of the JSON document.
//	If `key` doesn't exist, returns api.CreateNilStringResult().
//
// [valkey.io]: https://valkey.io/commands/json.get/
func GetWithOptions(client api.BaseClient, key string, options jsonOptions.JsonGetOptions) (api.Result[string], error) {
	args := []string{JsonGet, key}
	optionalArgs, err := options.ToArgs()
	if err != nil {
		return api.CreateNilStringResult(), err
	}
	args = append(args, optionalArgs...)
	result, err := executeCommand(client, args)
	if err != nil || result == nil {
		return api.CreateNilStringResult(), err
	}
	return api.CreateStringResult(result.(string)), err
}
