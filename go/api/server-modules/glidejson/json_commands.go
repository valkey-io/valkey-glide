// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package glidejson

import (
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/errors"
)

const (
	JsonSet = "JSON.SET"
	JsonGet = "JSON.GET"
)

func executeCommandWithReturnMap(client api.BaseClient, args []string, returnMap bool) (interface{}, error) {
	switch client.(type) {
	case api.GlideClient:
		return (client.(*api.GlideClusterClient)).CustomCommand(args)
	case api.GlideClusterClient:
		result, err := (client.(*api.GlideClusterClient)).CustomCommand(args)
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

func Set(
	client api.BaseClient,
	key string,
	path string,
	value string,
) (string, error) {
	result, err := executeCommand(client, []string{JsonSet, key, path, value})
	return result.(string), err
}

func SetWithOptions(
	client api.BaseClient,
	key string,
	path string,
	value string,
	options *JsonSetOptions,
) (string, error) {
	result, err := executeCommand(client, []string{JsonSet, key, path, value})
	return result.(string), err
}

func Get(client api.BaseClient, key string) (string, error) {
	result, err := executeCommand(client, []string{JsonGet, key})
	return result.(string), err
}
