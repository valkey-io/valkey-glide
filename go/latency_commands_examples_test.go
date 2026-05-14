// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_LatencyHistory() {
	var client *Client = getExampleClient() // example helper function

	// Enable latency monitoring so the server records spikes; without this,
	// `LATENCY HISTORY` returns an empty slice even when commands are slow.
	_, _ = client.ConfigSet(context.Background(), map[string]string{"latency-monitor-threshold": "100"})

	entries, err := client.LatencyHistory(context.Background(), "command")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyHistory returned %T with %d entries\n", entries, len(entries))
	}

	// Output:
	// LatencyHistory returned []models.LatencyHistoryEntry with 0 entries
}

func ExampleClient_LatencyLatest() {
	var client *Client = getExampleClient() // example helper function

	_, _ = client.ConfigSet(context.Background(), map[string]string{"latency-monitor-threshold": "100"})

	entries, err := client.LatencyLatest(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyLatest returned %T\n", entries)
	}

	// Output:
	// LatencyLatest returned []models.LatencyLatestEntry
}

func ExampleClient_LatencyReset() {
	var client *Client = getExampleClient() // example helper function

	resetCount, err := client.LatencyReset(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	// On a freshly started server with no recorded events, 0 series get reset.
	fmt.Printf("reset %d event series\n", resetCount)

	// Output:
	// reset 0 event series
}

func ExampleClient_LatencyResetWithEvents() {
	var client *Client = getExampleClient() // example helper function

	resetCount, err := client.LatencyResetWithEvents(context.Background(), []string{"command", "fork"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("reset %d event series\n", resetCount)

	// Output:
	// reset 0 event series
}

func ExampleClusterClient_LatencyHistory() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

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
	var client *ClusterClient = getExampleClusterClient() // example helper function

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
	var client *ClusterClient = getExampleClusterClient() // example helper function

	resetCount, err := client.LatencyReset(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("reset %d event series across the cluster\n", resetCount)

	// Output:
	// reset 0 event series across the cluster
}

func ExampleClusterClient_LatencyResetWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	resetCount, err := client.LatencyResetWithOptions(context.Background(), options.RouteOption{})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("reset %d event series\n", resetCount)

	// Output:
	// reset 0 event series
}
