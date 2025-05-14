// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClusterClient_Ping() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Ping()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleClusterClient_PingWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
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

func ExampleClusterClient_Echo() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.Echo("Hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello false}
}

func ExampleClusterClient_EchoWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.ClusterEchoOptions{
		EchoOptions: &options.EchoOptions{
			Message: "Hello World",
		},
		RouteOption: &options.RouteOption{Route: nil},
	}
	result, err := client.EchoWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: Hello World
}

func ExampleClusterClient_ClientId() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.ClientId()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleClusterClient_ClientIdWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientIdWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result.IsSingleValue()
	fmt.Println(assert)

	// Output: true
}

func ExampleClusterClient_ClientSetName() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	result, err := client.ClientSetName(connectionName)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: OK
}

func ExampleClusterClient_ClientGetName() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(connectionName)
	result, err := client.ClientGetName()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue() == connectionName)

	// Output: true
}

func ExampleClusterClient_ClientSetNameWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	opts := options.RouteOption{Route: nil}
	result, err := client.ClientSetNameWithOptions(connectionName, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue())

	// Output: OK
}

func ExampleClusterClient_ClientGetNameWithOptions() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
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
