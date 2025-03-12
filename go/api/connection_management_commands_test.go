// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_Ping() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.Ping()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleGlideClient_PingWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	options := options.PingOptions{Message: "hello"}
	result, err := client.PingWithOptions(options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: hello
}

func ExampleGlideClient_Echo() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.Echo("Hello World")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {Hello World false}
}
