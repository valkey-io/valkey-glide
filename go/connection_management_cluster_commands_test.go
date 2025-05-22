// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClusterClient_Ping() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Ping(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleClusterClient_PingWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
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

func ExampleClusterClient_Echo() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Echo(context.Background(), "Hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello false}
}

func ExampleClusterClient_EchoWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.EchoWithOptions(context.Background(), "Hello World", options.RouteOption{Route: config.RandomRoute})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: Hello World
}

func ExampleClusterClient_ClientId() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.ClientId(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleClusterClient_ClientIdWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientIdWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleClusterClient_ClientSetName() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	result, err := client.ClientSetName(context.Background(), connectionName)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: OK
}

func ExampleClusterClient_ClientGetName() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(context.Background(), connectionName)
	result, err := client.ClientGetName(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue() == connectionName)

	// Output: true
}

func ExampleClusterClient_ClientSetNameWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientSetNameWithOptions(context.Background(), connectionName, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: OK
}

func ExampleClusterClient_ClientGetNameWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
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
