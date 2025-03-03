// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

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
	fmt.Println(result.singleValue)

	// Output: Hello World
}

func ExampleGlideClusterClient_ClientIdWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts := options.RouteOption{Route: route}
	response, err = client.ClientIdWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: 1
}

func ExampleGlideClusterClient_ClientGetName() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	route := config.Route(config.RandomRoute)
	opts := options.RouteOption{Route: route}
	result, err := client.ClientGetNameWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.singleValue)

	// Output: ConnectionName
}
