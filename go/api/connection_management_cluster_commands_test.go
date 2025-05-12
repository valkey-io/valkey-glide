// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClusterClient_Ping() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Ping()
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
	result, err := client.PingWithOptions(options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: hello
}

func ExampleGlideClusterClient_Echo() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Echo("Hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello false}
}

func ExampleGlideClusterClient_EchoWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.EchoWithOptions("Hello World", options.RouteOption{Route: config.RandomRoute})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.singleValue)

	// Output: Hello World
}

func ExampleGlideClusterClient_ClientId() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.ClientId()
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
	result, err := client.ClientIdWithOptions(opts)
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
	result, err := client.ClientSetName(connectionName)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: OK
}

func ExampleGlideClusterClient_ClientGetName() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(connectionName)
	result, err := client.ClientGetName()
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
	result, err := client.ClientSetNameWithOptions(connectionName, opts)
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
	client.ClientSetNameWithOptions(connectionName, opts)
	result, err := client.ClientGetNameWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue() == connectionName)

	// Output: true
}
