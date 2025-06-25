// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
	"github.com/valkey-io/valkey-glide/go/v2/pipeline"
)

// TODO replace CustomCommand

func ExampleClient_Exec_transaction() {
	var client *Client = getExampleClient() // example helper function
	// Example 1: Atomic Batch (Transaction)
	batch := pipeline.NewStandaloneBatch(true)
	batch.Set("key", "1").CustomCommand([]string{"incr", "key"}).Get("key")

	result, err := client.Exec(context.Background(), *batch, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK 2 2]
}

func ExampleClient_Exec_pipeline() {
	var client *Client = getExampleClient() // example helper function
	// Example 2: Non-Atomic Batch (Pipeline)
	batch := pipeline.NewStandaloneBatch(false)
	batch.Set("key1", "value1").Set("key2", "value2").Get("key1").Get("key2")

	result, err := client.Exec(context.Background(), *batch, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK OK value1 value2]
}

func ExampleClient_ExecWithOptions_transaction() {
	var client *Client = getExampleClient() // example helper function
	// Example 1: Atomic Batch (Transaction)
	batch := pipeline.NewStandaloneBatch(true)
	batch.Set("key", "1").CustomCommand([]string{"incr", "key"}).Get("key")
	// Set a timeout of 1000 milliseconds
	options := pipeline.NewStandaloneBatchOptions().WithTimeout(1000 * time.Millisecond)

	result, err := client.ExecWithOptions(context.Background(), *batch, false, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK 2 2]
}

func ExampleClient_ExecWithOptions_pipeline() {
	var client *Client = getExampleClient() // example helper function
	// Example 2: Non-Atomic Batch (Pipeline)
	batch := pipeline.NewStandaloneBatch(false)
	batch.Set("key1", "value1").Set("key2", "value2").Get("key1").Get("key2")
	// Set a timeout of 1000 milliseconds
	options := pipeline.NewStandaloneBatchOptions().WithTimeout(1000 * time.Millisecond)

	result, err := client.ExecWithOptions(context.Background(), *batch, false, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK OK value1 value2]
}

func ExampleClusterClient_Exec_transaction() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	// Example 1: Atomic Batch (Transaction)
	batch := pipeline.NewClusterBatch(true)
	batch.Set("key", "1").CustomCommand([]string{"incr", "key"}).Get("key")

	result, err := client.Exec(context.Background(), *batch, false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK 2 2]
}

func ExampleClusterClient_Exec_pipeline() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	// Example 2: Non-Atomic Batch (Pipeline)
	batch := pipeline.NewClusterBatch(false)
	batch.Set("key1", "value1").Set("key2", "value2").Get("key1").Get("key2")

	result, err := client.Exec(context.Background(), *batch, false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK OK value1 value2]
}

func ExampleClusterClient_ExecWithOptions_transaction() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	// Example 1: Atomic Batch (Transaction)
	batch := pipeline.NewClusterBatch(true)
	batch.Set("key", "1").CustomCommand([]string{"incr", "key"}).Get("key")
	// Set a timeout of 1000 milliseconds
	options := pipeline.NewClusterBatchOptions().WithTimeout(1000 * time.Millisecond)

	result, err := client.ExecWithOptions(context.Background(), *batch, false, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK 2 2]
}

func ExampleClusterClient_ExecWithOptions_pipeline() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	// Example 2: Non-Atomic Batch (Pipeline)
	batch := pipeline.NewClusterBatch(false)
	batch.Set("key1", "value1").Set("key2", "value2").Get("key1").Get("key2")
	// Set command retry parameters
	retryStrategy := pipeline.NewClusterBatchRetryStrategy().WithRetryServerError(true).WithRetryConnectionError(true)
	options := pipeline.NewClusterBatchOptions().WithRetryStrategy(*retryStrategy)

	result, err := client.ExecWithOptions(context.Background(), *batch, false, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [OK OK value1 value2]
}

func ExampleClusterClient_ExecWithOptions_route() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	// Example 3: A transaction with a route
	batch := pipeline.NewClusterBatch(true)
	batch.CustomCommand([]string{"info", "server"})
	// Set batch route
	route := config.NewSlotKeyRoute(config.SlotTypePrimary, "abc")
	options := pipeline.NewClusterBatchOptions().WithRoute(route)

	_, err := client.ExecWithOptions(context.Background(), *batch, false, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	// Output:
}

func ExampleClient_Watch_changedKey() {
	var client *Client = getExampleClient() // example helper function
	// Example 1: key is changed before transaction and transaction didn't execute
	result, err := client.Watch(context.Background(), []string{"sampleKey"})
	fmt.Println("Watch result: ", result)

	result, err = client.Set(context.Background(), "sampleKey", "foobar")
	fmt.Println("Set result: ", result)

	transaction := pipeline.NewStandaloneBatch(true)
	transaction.Set("sampleKey", "value")
	// Set command retry parameters

	result1, err := client.Exec(context.Background(), *transaction, false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	// Transaction result is `nil`, it is not executed at all
	fmt.Printf("Transation result: %#v", result1)

	// Output:
	// Watch result:  OK
	// Set result:  OK
	// Transation result: []interface {}(nil)
}

func ExampleClient_Watch_unchangedKey() {
	var client *Client = getExampleClient() // example helper function
	// Example 2: key is unchanged before transaction
	result, err := client.Watch(context.Background(), []string{"sampleKey"})
	fmt.Println("Watch result: ", result)

	transaction := pipeline.NewStandaloneBatch(true)
	transaction.Set("sampleKey", "value")
	// Set command retry parameters

	result1, err := client.Exec(context.Background(), *transaction, false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Transation result: ", result1)

	// Output:
	// Watch result:  OK
	// Transation result:  [OK]
}

func ExampleClusterClient_Watch_changedKey() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	// Example 1: key is changed before transaction and transaction didn't execute
	result, err := client.Watch(context.Background(), []string{"sampleKey"})
	fmt.Println("Watch result: ", result)

	result, err = client.Set(context.Background(), "sampleKey", "foobar")
	fmt.Println("Set result: ", result)

	transaction := pipeline.NewClusterBatch(true)
	transaction.Set("sampleKey", "value")
	// Set command retry parameters

	result1, err := client.Exec(context.Background(), *transaction, false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	// Transaction result is `nil`, it is not executed at all
	fmt.Printf("Transation result: %#v", result1)

	// Output:
	// Watch result:  OK
	// Set result:  OK
	// Transation result: []interface {}(nil)
}

func ExampleClusterClient_Watch_unchangedKey() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	// Example 2: key is unchanged before transaction
	result, err := client.Watch(context.Background(), []string{"sampleKey"})
	fmt.Println("Watch result: ", result)

	transaction := pipeline.NewClusterBatch(true)
	transaction.Set("sampleKey", "value")
	// Set command retry parameters

	result1, err := client.Exec(context.Background(), *transaction, false)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Transation result: ", result1)

	// Output:
	// Watch result:  OK
	// Transation result:  [OK]
}

func ExampleClient_Unwatch() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.Unwatch(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_Unwatch() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.Unwatch(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_UnwatchWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.UnwatchWithOptions(context.Background(), options.RouteOption{Route: config.AllNodes})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}
