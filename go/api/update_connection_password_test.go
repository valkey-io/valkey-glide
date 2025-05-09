// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"context"
	"fmt"
)

func ExampleGlideClient_UpdateConnectionPassword() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	response, err := client.UpdateConnectionPassword(context.TODO(), "", false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClient_ResetConnectionPassword() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	response, err := client.ResetConnectionPassword(context.TODO())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClusterClient_UpdateConnectionPassword() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	response, err := client.UpdateConnectionPassword(context.TODO(), "", false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClusterClient_ResetConnectionPassword() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	response, err := client.ResetConnectionPassword(context.TODO())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}
