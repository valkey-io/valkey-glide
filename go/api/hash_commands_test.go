// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_HGet() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HGet() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HGetAll() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HGetAll() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HMGet() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HMGet() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HSet() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HSet() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HSetNX() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HSetNX() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HDel() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HDel() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HLen() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HLen() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HVals() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HVals() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HExists() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HExists() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HKeys() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HKeys() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HStrLen() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HStrLen() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HIncrBy() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HIncrBy() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HIncrByFloat() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HIncrByFloat() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HScan() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HScan() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HRandField() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HRandField() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HRandFieldWithCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HRandFieldWithCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HRandFieldWithCountWithValues() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HRandFieldWithCountWithValues() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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

func ExampleGlideClient_HScanWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

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

func ExampleGlideClusterClient_HScanWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

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
