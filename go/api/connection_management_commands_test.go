// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_Ping() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.Ping(context.TODO())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleGlideClient_PingWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	options := options.PingOptions{Message: "hello"}
	result, err := client.PingWithOptions(context.TODO(), options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: hello
}

func ExampleGlideClient_Echo() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.Echo(context.TODO(), "Hello World")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello World false}
}

func ExampleGlideClient_ClientId() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.ClientId(context.TODO())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result > 0
	fmt.Println(assert)

	// Output: true
}

func ExampleGlideClient_ClientSetName() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.ClientSetName(context.TODO(), "ConnectionName")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClient_ClientGetName() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(context.TODO(), connectionName)
	result, err := client.ClientGetName(context.TODO())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result == connectionName)

	// Output: true
}
