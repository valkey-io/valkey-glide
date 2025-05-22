// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
)

func ExampleClient_UpdateConnectionPassword() {
	var client *Client = getExampleClient() // example helper function
	response, err := client.UpdateConnectionPassword(context.Background(), "", false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClient_ResetConnectionPassword() {
	var client *Client = getExampleClient() // example helper function
	response, err := client.ResetConnectionPassword(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClusterClient_UpdateConnectionPassword() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	response, err := client.UpdateConnectionPassword(context.Background(), "", false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleClusterClient_ResetConnectionPassword() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	response, err := client.ResetConnectionPassword(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}
