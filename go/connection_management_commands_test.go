// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_ClientPause() {
	var client *Client = getExampleClient()
	result, err := client.ClientPause(context.Background(), 0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_ClientPauseWithMode() {
	var client *Client = getExampleClient()
	result, err := client.ClientPauseWithMode(context.Background(), 0, options.ClientPauseModeWrite)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_ClientUnpause() {
	var client *Client = getExampleClient()
	result, err := client.ClientUnpause(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_Ping() {
	var client *Client = getExampleClient()
	result, err := client.Ping(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleClient_PingWithOptions() {
	var client *Client = getExampleClient()
	options := options.PingOptions{Message: "hello"}
	result, err := client.PingWithOptions(context.Background(), options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: hello
}

func ExampleClient_Echo() {
	var client *Client = getExampleClient()
	result, err := client.Echo(context.Background(), "Hello World")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello World false}
}

func ExampleClient_ClientId() {
	var client *Client = getExampleClient()
	result, err := client.ClientId(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	assert := result > 0
	fmt.Println(assert)

	// Output: true
}

func ExampleClient_ClientSetName() {
	var client *Client = getExampleClient()
	result, err := client.ClientSetName(context.Background(), "ConnectionName")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_ClientGetName() {
	var client *Client = getExampleClient()
	connectionName := "ConnectionName-" + uuid.NewString()
	client.ClientSetName(context.Background(), connectionName)
	result, err := client.ClientGetName(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.Value() == connectionName)

	// Output: true
}
