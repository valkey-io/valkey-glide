// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_HGet() {
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "someValue",
		"field2": "someOtherValue",
	}

	result, err := client.HSet(context.Background(), "my_hash", fields)
	payload, err := client.HGet(context.Background(), "my_hash", "field1")
	payload2, err := client.HGet(context.Background(), "my_hash", "nonexistent_field")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	payload, err := client.HGet(context.Background(), "my_hash", "field1")
	payload2, err := client.HGet(context.Background(), "my_hash", "nonexistent_field")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	payload, err := client.HGetAll(context.Background(), "my_hash")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	payload, err := client.HGetAll(context.Background(), "my_hash")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	values, err := client.HMGet(context.Background(), "my_hash", []string{"field1", "field2"})
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	values, err := client.HMGet(context.Background(), "my_hash", []string{"field1", "field2"})
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HGet(context.Background(), "my_hash", "field1")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HGet(context.Background(), "my_hash", "field1")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HSetNX(context.Background(), "my_hash", "field3", "value")
	payload, err := client.HGet(context.Background(), "my_hash", "field3")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HSetNX(context.Background(), "my_hash", "field3", "value")
	payload, err := client.HGet(context.Background(), "my_hash", "field3")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HDel(context.Background(), "my_hash", []string{"field1", "field2"})
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HDel(context.Background(), "my_hash", []string{"field1", "field2"})
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HLen(context.Background(), "my_hash")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HLen(context.Background(), "my_hash")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HVals(context.Background(), "my_hash")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HVals(context.Background(), "my_hash")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HExists(context.Background(), "my_hash", "field1")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HExists(context.Background(), "my_hash", "field1")
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

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HKeys(context.Background(), "my_hash")
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

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HKeys(context.Background(), "my_hash")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HStrLen(context.Background(), "my_hash", "field1")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HStrLen(context.Background(), "my_hash", "field1")
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HIncrBy(context.Background(), "my_hash", "field1", 1)
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HIncrBy(context.Background(), "my_hash", "field1", 1)
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HIncrByFloat(context.Background(), "my_hash", "field1", 1.5)
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

	result, err := client.HSet(context.Background(), "my_hash", fields)
	result1, err := client.HIncrByFloat(context.Background(), "my_hash", "field1", 1.5)
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

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HScan(context.Background(), "my_hash", models.NewCursor())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", result.Cursor)
	fmt.Println("Collection:", result.Data)

	// Output:
	// Cursor: 0
	// Collection: [field1 someValue]
}

func ExampleClusterClient_HScan() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// For this example we only use 1 field to ensure a consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here
	}

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HScan(context.Background(), "my_hash", models.NewCursor())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", result.Cursor)
	fmt.Println("Collection:", result.Data)

	// Output:
	// Cursor: 0
	// Collection: [field1 someValue]
}

func ExampleClient_HRandField() {
	var client *Client = getExampleClient() // example helper function

	// For this example we only use 1 field to ensure consistent output
	fields := map[string]string{
		"field1": "someValue",
		// other fields here...
	}

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HRandField(context.Background(), "my_hash")
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

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HRandField(context.Background(), "my_hash")
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

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HRandFieldWithCount(context.Background(), "my_hash", 2)
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

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HRandFieldWithCount(context.Background(), "my_hash", 2)
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
	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HRandFieldWithCountWithValues(context.Background(), "my_hash", 2)
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

	client.HSet(context.Background(), "my_hash", fields)
	result, err := client.HRandFieldWithCountWithValues(context.Background(), "my_hash", 2)
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

	client.HSet(context.Background(), "my_hash", fields)
	opts := options.NewHashScanOptions().SetMatch("a")
	result, err := client.HScanWithOptions(context.Background(), "my_hash", models.NewCursor(), *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", result.Cursor)
	fmt.Println("Collection:", result.Data)
	// The collection only contains the hash map entry that matches with the match option provided with the command

	// Output:
	// Cursor: 0
	// Collection: [a 1]
}

func ExampleClusterClient_HScanWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"a": "1",
		"b": "2",
	}

	client.HSet(context.Background(), "my_hash", fields)
	opts := options.NewHashScanOptions().SetMatch("a")
	result, err := client.HScanWithOptions(context.Background(), "my_hash", models.NewCursor(), *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", result.Cursor)
	fmt.Println("Collection:", result.Data)
	// The collection only contains the hash map entry that matches with the match option provided with the command

	// Output:
	// Cursor: 0
	// Collection: [a 1]
}

func ExampleClient_HSetEx() {
	// This command requires Valkey 9.0+
	var client *Client = getExampleClient() // example helper function

	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}

	// Set fields with 10 second expiration
	options := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(10 * time.Second))
	result, err := client.HSetEx(context.Background(), "my_hash", fields, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClusterClient_HSetEx() {
	// This command requires Valkey 9.0+
	var client *ClusterClient = getExampleClusterClient() // example helper function

	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}

	// Set fields with 10 second expiration
	options := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(10 * time.Second))
	result, err := client.HSetEx(context.Background(), "my_hash", fields, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClient_HGetEx() {
	// This command requires Valkey 9.0+
	var client *Client = getExampleClient() // example helper function

	// First set some fields
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	client.HSet(context.Background(), "my_hash", fields)

	// Get fields and set 5 second expiration
	options := options.NewHGetExOptions().SetExpiry(options.NewExpiryIn(5 * time.Second))
	result, err := client.HGetEx(context.Background(), "my_hash", []string{"field1", "field2"}, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result[0].Value())
	fmt.Println(result[1].Value())

	// Output:
	// value1
	// value2
}

func ExampleClusterClient_HGetEx() {
	// This command requires Valkey 9.0+
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// First set some fields
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	client.HSet(context.Background(), "my_hash", fields)

	// Get fields and set 5 second expiration
	options := options.NewHGetExOptions().SetExpiry(options.NewExpiryIn(5 * time.Second))
	result, err := client.HGetEx(context.Background(), "my_hash", []string{"field1", "field2"}, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result[0].Value())
	fmt.Println(result[1].Value())

	// Output:
	// value1
	// value2
}

func ExampleClient_HExpire() {
	// This command requires Valkey 9.0+
	var client *Client = getExampleClient() // example helper function

	// First set some fields
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	client.HSet(context.Background(), "my_hash", fields)

	// Set 30 second expiration on fields
	result, err := client.HExpire(
		context.Background(),
		"my_hash",
		30*time.Second,
		[]string{"field1", "field2"},
		options.HExpireOptions{},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result[0]) // 1 means expiration was set
	fmt.Println(result[1]) // 1 means expiration was set

	// Output:
	// 1
	// 1
}

func ExampleClusterClient_HExpire() {
	// This command requires Valkey 9.0+
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// First set some fields
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	client.HSet(context.Background(), "my_hash", fields)

	// Set 30 second expiration on fields
	result, err := client.HExpire(
		context.Background(),
		"my_hash",
		30*time.Second,
		[]string{"field1", "field2"},
		options.HExpireOptions{},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result[0]) // 1 means expiration was set
	fmt.Println(result[1]) // 1 means expiration was set

	// Output:
	// 1
	// 1
}

func ExampleClient_HTtl() {
	// This command requires Valkey 9.0+
	var client *Client = getExampleClient() // example helper function

	// First set some fields with expiration
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	options := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(60 * time.Second))
	client.HSetEx(context.Background(), "my_hash", fields, options)

	// Get TTL for fields
	result, err := client.HTtl(context.Background(), "my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Field1 TTL > 0: %t\n", result[0] > 0)
	fmt.Printf("Field2 TTL > 0: %t\n", result[1] > 0)

	// Output:
	// Field1 TTL > 0: true
	// Field2 TTL > 0: true
}

func ExampleClusterClient_HTtl() {
	// This command requires Valkey 9.0+
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// First set some fields with expiration
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	options := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(60 * time.Second))
	client.HSetEx(context.Background(), "my_hash", fields, options)

	// Get TTL for fields
	result, err := client.HTtl(context.Background(), "my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("Field1 TTL > 0: %t\n", result[0] > 0)
	fmt.Printf("Field2 TTL > 0: %t\n", result[1] > 0)

	// Output:
	// Field1 TTL > 0: true
	// Field2 TTL > 0: true
}

func ExampleClient_HPersist() {
	// This command requires Valkey 9.0+
	var client *Client = getExampleClient() // example helper function

	// First set some fields with expiration
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	options := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(60 * time.Second))
	client.HSetEx(context.Background(), "my_hash", fields, options)

	// Remove expiration from fields
	result, err := client.HPersist(context.Background(), "my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result[0]) // 1 means expiration was removed
	fmt.Println(result[1]) // 1 means expiration was removed

	// Output:
	// 1
	// 1
}

func ExampleClusterClient_HPersist() {
	// This command requires Valkey 9.0+
	var client *ClusterClient = getExampleClusterClient() // example helper function

	// First set some fields with expiration
	fields := map[string]string{
		"field1": "value1",
		"field2": "value2",
	}
	options := options.NewHSetExOptions().SetExpiry(options.NewExpiryIn(60 * time.Second))
	client.HSetEx(context.Background(), "my_hash", fields, options)

	// Remove expiration from fields
	result, err := client.HPersist(context.Background(), "my_hash", []string{"field1", "field2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result[0]) // 1 means expiration was removed
	fmt.Println(result[1]) // 1 means expiration was removed

	// Output:
	// 1
	// 1
}
