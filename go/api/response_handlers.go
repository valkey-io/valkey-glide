// Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"go/types"
	"reflect"
)

type redisResponse interface {
	interface{} | string | types.Nil
}

func handleRedisResponse[T redisResponse](t reflect.Type, isNilable bool, response interface{}) (T, error) {
	if isNilable && response == nil {
		return reflect.ValueOf(nil).Interface().(T), nil
	}

	if reflect.TypeOf(response) == t {
		return reflect.ValueOf(response).Interface().(T), nil
	}

	var responseTypeName string
	if response == nil {
		responseTypeName = "nil"
	} else {
		responseTypeName = reflect.TypeOf(response).Name()
	}

	return reflect.Zero(t).Interface().(T), &RedisError{
		fmt.Sprintf("Unexpected return type from Redis: got %s, expected %s", responseTypeName, t),
	}
}

func handleStringResponse(response interface{}) (string, error) {
	return handleRedisResponse[string](reflect.TypeOf(""), false, response)
}
