// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// enableLatencyMonitoring sets a low latency-monitor-threshold and triggers a latency spike via
// DEBUG SLEEP so the server has at least one recorded "command" event.
func enableLatencyMonitoring(client *Client) {
	_, _ = client.ConfigSet(context.Background(), map[string]string{"latency-monitor-threshold": "1"})
	_, _ = client.CustomCommand(context.Background(), []string{"DEBUG", "SLEEP", "0.05"})
}

// enableLatencyMonitoringCluster is the cluster equivalent of enableLatencyMonitoring.
func enableLatencyMonitoringCluster(client *ClusterClient) {
	_, _ = client.ConfigSet(context.Background(), map[string]string{"latency-monitor-threshold": "1"})
	_, _ = client.CustomCommand(context.Background(), []string{"DEBUG", "SLEEP", "0.05"})
}

func ExampleClient_LatencyHistory() {
	var client *Client = getExampleClient()

	enableLatencyMonitoring(client)

	entries, err := client.LatencyHistory(context.Background(), "command")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyHistory has entries: %v\n", len(entries) > 0)
	}

	// Output:
	// LatencyHistory has entries: true
}

func ExampleClient_LatencyLatest() {
	var client *Client = getExampleClient()

	enableLatencyMonitoring(client)

	entries, err := client.LatencyLatest(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	} else {
		fmt.Printf("LatencyLatest has entries: %v\n", len(entries) > 0)
	}

	// Output:
	// LatencyLatest has entries: true
}

func ExampleClient_LatencyReset() {
	var client *Client = getExampleClient()

	enableLatencyMonitoring(client)

	resetCount, err := client.LatencyReset(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("reset at least one event series: %v\n", resetCount > 0)

	// Output:
	// reset at least one event series: true
}

func ExampleClient_LatencyReset_withEvents() {
	var client *Client = getExampleClient()

	enableLatencyMonitoring(client)

	resetCount, err := client.LatencyReset(context.Background(), "command")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("reset at least one event series: %v\n", resetCount > 0)

	// Output:
	// reset at least one event series: true
}

func ExampleClusterClient_LatencyHistory() {
	var client *ClusterClient = getExampleClusterClient()

	enableLatencyMonitoringCluster(client)

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

	enableLatencyMonitoringCluster(client)

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

	enableLatencyMonitoringCluster(client)

	resetCount, err := client.LatencyReset(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("reset at least one event series across the cluster: %v\n", resetCount > 0)

	// Output:
	// reset at least one event series across the cluster: true
}

func ExampleClusterClient_LatencyResetWithOptions() {
	var client *ClusterClient = getExampleClusterClient()

	enableLatencyMonitoringCluster(client)

	resetCount, err := client.LatencyResetWithOptions(context.Background(), options.RouteOption{})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("reset at least one event series: %v\n", resetCount > 0)

	// Output:
	// reset at least one event series: true
}
