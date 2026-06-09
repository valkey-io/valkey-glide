// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_MemoryDoctor() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.MemoryDoctor(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	fmt.Printf("MemoryDoctor result is of type %T\n", result)

	// Output:
	// MemoryDoctor result is of type string
}

func ExampleClient_MemoryMallocStats() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.MemoryMallocStats(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	fmt.Printf("MemoryMallocStats result is of type %T\n", result)

	// Output:
	// MemoryMallocStats result is of type string
}

func ExampleClient_MemoryPurge() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.MemoryPurge(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClient_MemoryStats() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.MemoryStats(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	// Verify we get expected keys in the stats map
	hasExpectedKeys := false
	if result != nil {
		// Check for common memory stats keys
		_, hasPeakAllocated := result["peak.allocated"]
		_, hasTotalAllocated := result["total.allocated"]
		_, hasUsedMemory := result["used_memory"]
		hasExpectedKeys = hasPeakAllocated || hasTotalAllocated || hasUsedMemory
	}
	fmt.Println(hasExpectedKeys)

	// Output:
	// true
}

func ExampleClusterClient_MemoryDoctor() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.MemoryDoctor(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	// Default routing returns multi-value (all primaries)
	fmt.Println(result.IsMultiValue())

	// Output:
	// true
}

func ExampleClusterClient_MemoryDoctorWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryDoctorWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	// RandomRoute returns single value
	fmt.Println(result.IsSingleValue())

	// Output:
	// true
}

func ExampleClusterClient_MemoryMallocStats() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.MemoryMallocStats(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	// Default routing returns multi-value (all primaries)
	fmt.Println(result.IsMultiValue())

	// Output:
	// true
}

func ExampleClusterClient_MemoryMallocStatsWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryMallocStatsWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	// RandomRoute returns single value
	fmt.Println(result.IsSingleValue())

	// Output:
	// true
}

func ExampleClusterClient_MemoryPurge() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.MemoryPurge(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_MemoryPurgeWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.RouteOption{Route: config.AllNodes}
	result, err := client.MemoryPurgeWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_MemoryStats() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.MemoryStats(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	// Default routing returns multi-value (all primaries)
	fmt.Println(result.IsMultiValue())

	// Output:
	// true
}

func ExampleClusterClient_MemoryStatsWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	opts := options.RouteOption{Route: config.RandomRoute}
	result, err := client.MemoryStatsWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	}
	// RandomRoute returns single value
	fmt.Println(result.IsSingleValue())

	// Output:
	// true
}
