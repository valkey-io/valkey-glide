// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"
)

func ExampleClient_UpdateConnectionPassword() {
	var client *Client = getExampleGlideClient() // example helper function
	response, err := client.UpdateConnectionPassword("", false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClient_ResetConnectionPassword() {
	var client *Client = getExampleGlideClient() // example helper function
	response, err := client.ResetConnectionPassword()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClusterClient_UpdateConnectionPassword() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	response, err := client.UpdateConnectionPassword("", false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClusterClient_ResetConnectionPassword() {
	var client *ClusterClient = getExampleGlideClusterClient() // example helper function
	response, err := client.ResetConnectionPassword()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}
