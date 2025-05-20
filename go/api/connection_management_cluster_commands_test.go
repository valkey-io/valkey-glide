// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClusterClient_Ping() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Ping(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleGlideClusterClient_PingWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	options := options.ClusterPingOptions{
		PingOptions: &options.PingOptions{
			Message: "hello",
		},
		RouteOption: nil,
	}
	result, err := client.PingWithOptions(context.Background(), options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: hello
}

func ExampleGlideClusterClient_Echo() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Echo(context.Background(), "Hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello false}
}

func ExampleGlideClusterClient_EchoWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.EchoWithOptions(context.Background(), "Hello World", options.RouteOption{Route: config.RandomRoute})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.singleValue)

	// Output: Hello World
}

func ExampleGlideClusterClient_ClientId() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.ClientId(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleGlideClusterClient_ClientIdWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientIdWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleGlideClusterClient_ClientSetName() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	result, err := client.ClientSetName(context.Background(), connectionName)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: OK
}

func ExampleGlideClusterClient_ClientGetName() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(context.Background(), connectionName)
	result, err := client.ClientGetName(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue() == connectionName)

	// Output: true
}

func ExampleGlideClusterClient_ClientSetNameWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: OK
}

func ExampleGlideClusterClient_ClientGetNameWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	opts := options.RouteOption{Route: nil}
	client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	result, err := client.ClientGetNameWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue() == connectionName)

	// Output: true
}
