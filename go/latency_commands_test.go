// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_LatencyHistory() {
	var client *Client = getExampleClient()

	entries, err := client.LatencyHistory(context.Background(), "command")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyHistory returned entries: %v\n", entries)
	}

	// Output:
	// LatencyHistory returned entries: []
}

func ExampleClient_LatencyLatest() {
	var client *Client = getExampleClient()

	entries, err := client.LatencyLatest(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyLatest returned entries: %v\n", entries)
	}

	// Output:
	// LatencyLatest returned entries: []
}

func ExampleClient_LatencyReset() {
	var client *Client = getExampleClient()

	resetCount, err := client.LatencyReset(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("LatencyReset count: %v\n", resetCount)

	// Output:
	// LatencyReset count: 0
}

func ExampleClient_LatencyReset_withEvents() {
	var client *Client = getExampleClient()

	resetCount, err := client.LatencyReset(context.Background(), "command")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("LatencyReset count: %v\n", resetCount)

	// Output:
	// LatencyReset count: 0
}

func ExampleClusterClient_LatencyHistory() {
	var client *ClusterClient = getExampleClusterClient()

	val, err := client.LatencyHistory(context.Background(), "command")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyHistory IsMultiValue=%v\n", val.IsMultiValue())
	}

	// Output:
	// LatencyHistory IsMultiValue=true
}

func ExampleClusterClient_LatencyLatest() {
	var client *ClusterClient = getExampleClusterClient()

	val, err := client.LatencyLatest(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyLatest IsMultiValue=%v\n", val.IsMultiValue())
	}

	// Output:
	// LatencyLatest IsMultiValue=true
}

func ExampleClusterClient_LatencyReset() {
	var client *ClusterClient = getExampleClusterClient()

	resetCount, err := client.LatencyReset(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("LatencyReset count: %v\n", resetCount)

	// Output:
	// LatencyReset count: 0
}

func ExampleClusterClient_LatencyResetWithOptions() {
	var client *ClusterClient = getExampleClusterClient()

	resetCount, err := client.LatencyResetWithOptions(context.Background(), options.RouteOption{})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("LatencyReset count: %v\n", resetCount)

	// Output:
	// LatencyReset count: 0
}
