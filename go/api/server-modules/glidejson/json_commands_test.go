// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package glidejson

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/options"
	jsonOptions "github.com/valkey-io/valkey-glide/go/api/server-modules/glidejson/options"
)

func Example_jsonSet() {
	var client *api.GlideClient = getExampleGlideClient()
	result, err := Set(client, "my_key", "$", "{\"a\":1.0,\"b\":2}")
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClusterClient_jsonSet() {
	var client *api.GlideClusterClient = getExampleGlideClusterClient()
	result, err := Set(client, "my_key", "$", "{\"a\":1.0,\"b\":2}")
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func Example_jsonSetWithOptions() {
	var client *api.GlideClient = getExampleGlideClient()
	jsonSetResult, err := SetWithOptions(
		client,
		"key",
		"$",
		"{\"a\": 1.0, \"b\": 2}",
		*jsonOptions.NewJsonSetOptionsBuilder().SetConditionalSet(options.OnlyIfDoesNotExist),
	)
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(jsonSetResult)

	// Output: OK
}

func ExampleGlideClusterClient_jsonSetWithOptions() {
	var client *api.GlideClusterClient = getExampleGlideClusterClient()
	jsonSetResult, err := SetWithOptions(
		client,
		"key",
		"$",
		"{\"a\": 1.0, \"b\": 2}",
		*jsonOptions.NewJsonSetOptionsBuilder().SetConditionalSet(options.OnlyIfDoesNotExist),
	)
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(jsonSetResult)

	// Output: OK
}

func Example_jsonGet() {
	var client *api.GlideClient = getExampleGlideClient()
	jsonValue := "{\"a\":1.0,\"b\":2}"
	_, err := Set(client, "key", "$", jsonValue)

	jsonGetResult, err := Get(client, "key")
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(jsonGetResult.Value())

	// Output: "{\"a\":1.0,\"b\":2}"
}

func ExampleGlideClusterClient_jsonGet() {
	var client *api.GlideClusterClient = getExampleGlideClusterClient()
	jsonValue := "{\"a\":1.0,\"b\":2}"
	_, err := Set(client, "key", "$", jsonValue)

	jsonGetResult, err := Get(client, "key")
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(jsonGetResult.Value())

	// Output: "{\"a\":1.0,\"b\":2}"
}

func Example_jsonGetWithOptions() {
	var client *api.GlideClient = getExampleGlideClient()
	jsonValue := "{\"a\": {\"c\": 1, \"d\": 4}, \"b\": {\"c\": 2}, \"c\": true}"
	_, err := Set(client, "key", "$", jsonValue)
	jsonGetResult, err := GetWithOptions(
		client, "key", *jsonOptions.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c"}))
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(jsonGetResult.Value())

	// Output: "[true,1,2]"
}

func ExampleGlideClusterClient_jsonGetWithOptions() {
	var client *api.GlideClusterClient = getExampleGlideClusterClient()
	jsonValue := "{\"a\": {\"c\": 1, \"d\": 4}, \"b\": {\"c\": 2}, \"c\": true}"
	_, err := Set(client, "key", "$", jsonValue)
	jsonGetResult, err := GetWithOptions(
		client, "key", *jsonOptions.NewJsonGetOptionsBuilder().SetPaths([]string{"$..c"}))
	if err != nil {
		fmt.Println("JSON.SET example failed with an error: ", err)
	}
	fmt.Println(jsonGetResult.Value())

	// Output: "[true,1,2]"
}
