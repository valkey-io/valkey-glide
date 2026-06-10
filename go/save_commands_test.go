// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"strings"
)

func ExampleClient_Save() {
	var client *Client = getExampleClient() // example helper function
	response, err := client.Save(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Println(response)
	}

	// Output: OK
}

func ExampleClient_Bgsave() {
	var client *Client = getExampleClient() // example helper function
	response, err := client.Bgsave(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Println(strings.Contains(response, "Background saving"))
	}

	// Output: true
}

func ExampleClient_BgRewriteAof() {
	var client *Client = getExampleClient() // example helper function
	response, err := client.BgRewriteAof(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Println(strings.Contains(response, "Background append only file rewriting"))
	}

	// Output: true
}

func ExampleClient_ReplicaOfNoOne() {
	var client *Client = getExampleClient() // example helper function
	response, err := client.ReplicaOfNoOne(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Println(response)
	}

	// Output: OK
}

func ExampleClusterClient_Save() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	response, err := client.Save(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Println(response)
	}

	// Output: OK
}

func ExampleClusterClient_Bgsave() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Bgsave(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}
	hasExpectedResponse := false
	for _, value := range result.MultiValue() {
		if strings.Contains(value, "Background saving") {
			hasExpectedResponse = true
			break
		}
	}
	fmt.Println(hasExpectedResponse)

	// Output: true
}

func ExampleClusterClient_BgRewriteAof() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.BgRewriteAof(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
		return
	}
	hasExpectedResponse := false
	for _, value := range result.MultiValue() {
		if strings.Contains(value, "Background append only file rewriting") {
			hasExpectedResponse = true
			break
		}
	}
	fmt.Println(hasExpectedResponse)

	// Output: true
}
