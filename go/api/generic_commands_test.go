// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
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
