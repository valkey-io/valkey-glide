// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/google/uuid"
)

func ExampleGlideClient_CustomCommand() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.CustomCommand([]string{"ping"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleGlideClient_Move() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.New().String()
	_, err := client.Set(key, "hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	result, err := client.Move(key, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: true
}
