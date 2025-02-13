// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package glidejson

import (
	"fmt"
	"reflect"

	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/errors"
	"github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson/options"
)

const (
	JsonSet = "JSON.SET"
	JsonGet = "JSON.GET"
)

func executeCommandWithReturnMap(client api.BaseClient, args []string, returnMap bool) (interface{}, error) {
	fmt.Println("type===")
	fmt.Println(reflect.TypeOf(client))
	switch client.(type) {
	case *api.GlideClient:
		return (client.(*api.GlideClient)).CustomCommand(args)
	case *api.GlideClusterClient:
		result, err := (client.(*api.GlideClusterClient)).CustomCommand(args)
		fmt.Println("result===")
		fmt.Println(result.SingleValue())
		fmt.Println(reflect.TypeOf(result.SingleValue()))

		if returnMap {
			if result.IsEmpty() {
				return "", err
			}
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
	if err != nil {
		return api.DefaultStringResponse, err
	}
	return result.(string), err
}

func SetWithOptions(
	client api.BaseClient,
	key string,
	path string,
	value string,
	options *options.JsonSetOptions,
) (string, error) {
	args := []string{JsonSet, key, path, value}
	optionalArgs, err := options.ToArgs()
	if err != nil {
		return api.DefaultStringResponse, err
	}
	args = append(args, optionalArgs...)
	result, err := executeCommand(client, args)
	return result.(string), err
}

func Get(client api.BaseClient, key string) (string, error) {
	result, err := executeCommand(client, []string{JsonGet, key})
	if err != nil {
		return api.DefaultStringResponse, err
	}
	return result.(string), err
}

func GetWithOptions(client api.BaseClient, key string, options *options.JsonGetOptions) (string, error) {
	args := []string{JsonGet, key}
	optionalArgs, err := options.ToArgs()
	if err != nil {
		return api.DefaultStringResponse, err
	}
	args = append(args, optionalArgs...)
	result, err := executeCommand(client, args)
	return result.(string), err
}
