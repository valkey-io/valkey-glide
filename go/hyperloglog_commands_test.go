// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/google/uuid"
)

func ExampleClient_PfAdd() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.PfAdd(context.Background(), uuid.New().String(), []string{"value1", "value2", "value3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: true
}

func ExampleClusterClient_PfAdd() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.PfAdd(context.Background(), uuid.New().String(), []string{"value1", "value2", "value3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: true
}

func ExampleClient_PfCount() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.New().String()
	result, err := client.PfAdd(context.Background(), key, []string{"value1", "value2", "value3"})
	result1, err := client.PfCount(context.Background(), []string{key})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// true
	// 3
}

func ExampleClusterClient_PfCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.New().String()
	result, err := client.PfAdd(context.Background(), key, []string{"value1", "value2", "value3"})
	result1, err := client.PfCount(context.Background(), []string{key})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// true
	// 3
}

func ExampleClient_PfMerge() {
	var client *Client = getExampleClient() // example helper function

	// Create source keys with some values
	sourceKey1 := uuid.New().String() + "{group}"
	sourceKey2 := uuid.New().String() + "{group}"

	// Add values to source keys
	_, err := client.PfAdd(context.Background(), sourceKey1, []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	_, err = client.PfAdd(context.Background(), sourceKey2, []string{"value2", "value3", "value4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Merge the source keys into a destination key
	destKey := uuid.New().String() + "{group}"
	result, err := client.PfMerge(context.Background(), destKey, []string{sourceKey1, sourceKey2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)
	// Output: OK
}

func ExampleClusterClient_PfMerge() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// Create source keys with some values
	sourceKey1 := uuid.New().String() + "{group}"
	sourceKey2 := uuid.New().String() + "{group}"

	// Add values to source keys
	_, err := client.PfAdd(context.Background(), sourceKey1, []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	_, err = client.PfAdd(context.Background(), sourceKey2, []string{"value2", "value3", "value4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Merge the source keys into a destination key
	destKey := uuid.New().String() + "{group}"
	result, err := client.PfMerge(context.Background(), destKey, []string{sourceKey1, sourceKey2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)
	// Output: OK
}
