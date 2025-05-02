// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/google/uuid"
)

func ExampleGlideClient_PfAdd() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.PfAdd(uuid.New().String(), []string{"value1", "value2", "value3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClusterClient_PfAdd() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.PfAdd(uuid.New().String(), []string{"value1", "value2", "value3"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClient_PfCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := uuid.New().String()
	result, err := client.PfAdd(key, []string{"value1", "value2", "value3"})
	result1, err := client.PfCount([]string{key})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 1
	// 3
}

func ExampleGlideClusterClient_PfCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := uuid.New().String()
	result, err := client.PfAdd(key, []string{"value1", "value2", "value3"})
	result1, err := client.PfCount([]string{key})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 1
	// 3
}

func ExampleGlideClient_PfMerge() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	// Create source keys with some values
	sourceKey1 := uuid.New().String() + "{group}"
	sourceKey2 := uuid.New().String() + "{group}"

	// Add values to source keys
	_, err := client.PfAdd(sourceKey1, []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	_, err = client.PfAdd(sourceKey2, []string{"value2", "value3", "value4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Merge the source keys into a destination key
	destKey := uuid.New().String() + "{group}"
	result, err := client.PfMerge(destKey, []string{sourceKey1, sourceKey2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)
	// Output: OK
}

func ExampleGlideClusterClient_PfMerge() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	// Create source keys with some values
	sourceKey1 := uuid.New().String() + "{group}"
	sourceKey2 := uuid.New().String() + "{group}"

	// Add values to source keys
	_, err := client.PfAdd(sourceKey1, []string{"value1", "value2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	_, err = client.PfAdd(sourceKey2, []string{"value2", "value3", "value4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Merge the source keys into a destination key
	destKey := uuid.New().String() + "{group}"
	result, err := client.PfMerge(destKey, []string{sourceKey1, sourceKey2})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)
	// Output: OK
}
