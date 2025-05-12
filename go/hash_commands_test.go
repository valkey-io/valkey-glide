// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_HGet() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	payload, err := client.HGet("my_hash", "field1")
	payload2, err := client.HGet("my_hash", "nonexistent_field")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(payload)
	fmt.Println(payload2.IsNil())

	// Output:
	// 2
	// {someValue false}
	// true
}

func ExampleClusterClient_HGet() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	payload, err := client.HGet("my_hash", "field1")
	payload2, err := client.HGet("my_hash", "nonexistent_field")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(payload)
	fmt.Println(payload2.IsNil())

	// Output:
	// 2
	// {someValue false}
	// true
}

func ExampleClient_HGetAll() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	payload, err := client.HGetAll("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(payload["field1"])
	fmt.Println(payload["field2"])
	fmt.Println(payload["notExistentField"]) // prints nothing

	// Output:
	// 2
	// someValue
	// someOtherValue
}

func ExampleClusterClient_HGetAll() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	payload, err := client.HGetAll("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(payload["field1"])
	fmt.Println(payload["field2"])
	fmt.Println(payload["notExistentField"]) // prints nothing

	// Output:
	// 2
	// someValue
	// someOtherValue
}

func ExampleClient_HMGet() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	values, err := client.HMGet("my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(values[0])
	fmt.Println(values[1])

	// Output:
	// 2
	// {someValue false}
	// {someOtherValue false}
}

func ExampleClusterClient_HMGet() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	values, err := client.HMGet("my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(values[0])
	fmt.Println(values[1])

	// Output:
	// 2
	// {someValue false}
	// {someOtherValue false}
}

func ExampleClient_HSet() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HGet("my_hash", "field1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// {someValue false}
}

func ExampleClusterClient_HSet() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HGet("my_hash", "field1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// {someValue false}
}

func ExampleClient_HSetNX() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HSetNX("my_hash", "field3", "value")
	payload, err := client.HGet("my_hash", "field3")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(payload)

	// Output:
	// 2
	// true
	// {value false}
}

func ExampleClusterClient_HSetNX() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HSetNX("my_hash", "field3", "value")
	payload, err := client.HGet("my_hash", "field3")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)
	fmt.Println(payload)

	// Output:
	// 2
	// true
	// {value false}
}

func ExampleClient_HDel() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HDel("my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 2
}

func ExampleClusterClient_HDel() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HDel("my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 2
}

func ExampleClient_HLen() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HLen("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 2
}

func ExampleClusterClient_HLen() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HLen("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 2
}

func ExampleClient_HVals() {
	var client *Client = getExampleClient() // example helper function

	// For this example, we only use 1 field for consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HVals("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 1
	// [someValue]
}

func ExampleClusterClient_HVals() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// For this example, we only use 1 field for consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HVals("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 1
	// [someValue]
}

func ExampleClient_HExists() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HExists("my_hash", "field1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// true
}

func ExampleClusterClient_HExists() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HExists("my_hash", "field1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// true
}

func ExampleClient_HKeys() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
	}

	client.HSet("my_hash", fields)
	result, err := client.HKeys("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [field1]
}

func ExampleClusterClient_HKeys() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
	}

	client.HSet("my_hash", fields)
	result, err := client.HKeys("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: [field1]
}

func ExampleClient_HStrLen() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HStrLen("my_hash", "field1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 9
}

func ExampleClusterClient_HStrLen() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HStrLen("my_hash", "field1")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 9
}

func ExampleClient_HIncrBy() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "10",
		"field2": "14",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HIncrBy("my_hash", "field1", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 11
}

func ExampleClusterClient_HIncrBy() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "10",
		"field2": "14",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HIncrBy("my_hash", "field1", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 11
}

func ExampleClient_HIncrByFloat() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "10",
		"field2": "14",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HIncrByFloat("my_hash", "field1", 1.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 11.5
}

func ExampleClusterClient_HIncrByFloat() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "10",
		"field2": "14",
	}

	result, err := client.HSet("my_hash", fields)
	result1, err := client.HIncrByFloat("my_hash", "field1", 1.5)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(result1)

	// Output:
	// 2
	// 11.5
}

func ExampleClient_HScan() {
	var client *Client = getExampleClient() // example helper function

	// For this example we only use 1 field to ensure a consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here
	}

	result, err := client.HSet("my_hash", fields)
	resCursor, resCollection, err := client.HScan("my_hash", "0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(resCollection)

	// Output:
	// 1
	// 0
	// [field1 someValue]
}

func ExampleClusterClient_HScan() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// For this example we only use 1 field to ensure a consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here
	}

	result, err := client.HSet("my_hash", fields)
	resCursor, resCollection, err := client.HScan("my_hash", "0")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(resCollection)

	// Output:
	// 1
	// 0
	// [field1 someValue]
}

func ExampleClient_HRandField() {
	var client *Client = getExampleClient() // example helper function

	// For this example we only use 1 field to ensure consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here...
	}

	client.HSet("my_hash", fields)
	result, err := client.HRandField("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {field1 false}
}

func ExampleClusterClient_HRandField() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// For this example we only use 1 field to ensure consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here...
	}

	client.HSet("my_hash", fields)
	result, err := client.HRandField("my_hash")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: {field1 false}
}

func ExampleClient_HRandFieldWithCount() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	client.HSet("my_hash", fields)
	result, err := client.HRandFieldWithCount("my_hash", 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) == 2)

	// Output: true
}

func ExampleClusterClient_HRandFieldWithCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	client.HSet("my_hash", fields)
	result, err := client.HRandFieldWithCount("my_hash", 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) == 2)

	// Output: true
}

func ExampleClient_HRandFieldWithCountWithValues() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}
	client.HSet("my_hash", fields)
	result, err := client.HRandFieldWithCountWithValues("my_hash", 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) == 2)

	// Output: true
}

func ExampleClusterClient_HRandFieldWithCountWithValues() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	client.HSet("my_hash", fields)
	result, err := client.HRandFieldWithCountWithValues("my_hash", 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result) == 2)

	// Output: true
}

func ExampleClient_HScanWithOptions() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"a": "1",
		"b": "2",
	}

	result, err := client.HSet("my_hash", fields)
	opts := options.NewHashScanOptions().SetMatch("a")
	resCursor, resCollection, err := client.HScanWithOptions("my_hash", "0", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(
		resCollection,
	) // The resCollection only contains the hash map entry that matches with the match option provided with the command

	// Output:
	// 2
	// 0
	// [a 1]
}

func ExampleClusterClient_HScanWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"a": "1",
		"b": "2",
	}

	result, err := client.HSet("my_hash", fields)
	opts := options.NewHashScanOptions().SetMatch("a")
	resCursor, resCollection, err := client.HScanWithOptions("my_hash", "0", *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)
	fmt.Println(resCursor)
	fmt.Println(
		resCollection,
	) // The resCollection only contains the hash map entry that matches with the match option provided with the command

	// Output:
	// 2
	// 0
	// [a 1]
}
