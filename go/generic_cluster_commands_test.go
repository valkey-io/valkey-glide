// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClusterClient_CustomCommand() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	result, err := client.CustomCommand(context.Background(), []string{"ping"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue().(string))

	// Output: PONG
}

func ExampleClusterClient_CustomCommandWithRoute() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	route := config.SimpleNodeRoute(config.RandomRoute)
	result, _ := client.CustomCommandWithRoute(context.Background(), []string{"ping"}, route)
	fmt.Println(result.SingleValue().(string))

	// Output: PONG
}

func ExampleClusterClient_Scan() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	keysToSet := map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}

	_, err := client.MSet(context.Background(), keysToSet)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	cursor := *options.NewClusterScanCursor()
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.Scan(context.Background(), cursor)
		if err != nil {
			fmt.Println("Glide example failed with an error: ", err)
		}
		allKeys = append(allKeys, keys...)
	}

	// Elements will contain values [key1 key2 key3] but because order
	// can vary, we just check the length
	fmt.Println(len(allKeys))

	// Output: 3
}

func ExampleClusterClient_ScanWithOptions_match() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	keysToSet := map[string]string{
		"key-1":         "value1",
		"key-2":         "value2",
		"key3":          "value3",
		"nonPatternKey": "value4",
	}

	_, err := client.MSet(context.Background(), keysToSet)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	_, err = client.SAdd(context.Background(), "someKey", []string{"value"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	cursor := *options.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetMatch("key-*")
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(context.Background(), cursor, *opts)
		if err != nil {
			fmt.Println("Glide example failed with an error: ", err)
		}
		allKeys = append(allKeys, keys...)
	}

	// Elements will contain values [key-1 key-2] but because order
	// can vary, we just check the length
	fmt.Println(len(allKeys))

	// Output: 2
}

func ExampleClusterClient_ScanWithOptions_matchNonUTF8() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	keysToSet := map[string]string{
		"key\xc0\xc1-1": "value1",
		"key-2":         "value2",
		"key\xf9\xc1-3": "value3",
	}

	_, err := client.MSet(context.Background(), keysToSet)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	_, err = client.SAdd(context.Background(), "someKey", []string{"value"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	cursor := *options.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetMatch("key\xc0\xc1-*")
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(context.Background(), cursor, *opts)
		if err != nil {
			fmt.Println("Glide example failed with an error: ", err)
		}
		allKeys = append(allKeys, keys...)
	}

	// Elements will contain value [key\xc0\xc1-1] but since it is
	// an invalid utf8 character, we just check the length
	fmt.Println(len(allKeys))

	// Output: 1
}

func ExampleClusterClient_ScanWithOptions_count() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	keysToSet := map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}

	_, err := client.MSet(context.Background(), keysToSet)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	cursor := *options.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetCount(10)
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(context.Background(), cursor, *opts)
		if err != nil {
			fmt.Println("Glide example failed with an error: ", err)
		}
		allKeys = append(allKeys, keys...)
	}

	// Elements will contain values [key1 key2 key3] but because order
	// can vary, we just check the length
	fmt.Println(len(allKeys))

	// Output: 3
}

func ExampleClusterClient_ScanWithOptions_type() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	keysToSet := map[string]string{
		"key1": "value1",
		"key2": "value2",
		"key3": "value3",
	}

	_, err := client.MSet(context.Background(), keysToSet)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	_, err = client.SAdd(context.Background(), "someKey", []string{"value"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	cursor := *options.NewClusterScanCursor()
	opts := options.NewClusterScanOptions().SetType(constants.ObjectTypeSet)
	allKeys := []string{}

	for !cursor.HasFinished() {
		var keys []string
		cursor, keys, err = client.ScanWithOptions(context.Background(), cursor, *opts)
		if err != nil {
			fmt.Println("Glide example failed with an error: ", err)
		}
		allKeys = append(allKeys, keys...)
	}

	fmt.Println(allKeys)

	// Output: [someKey]
}

func ExampleClusterClient_RandomKey() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.New().String()
	client.Set(context.Background(), key, "Hello")
	result, err := client.RandomKey(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(len(result.Value()) > 0)

	// Output: true
}

func ExampleClusterClient_RandomKeyWithRoute() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	options := options.RouteOption{Route: nil}
	key := uuid.New().String()
	client.Set(context.Background(), key, "Hello")
	result, err := client.RandomKeyWithRoute(context.Background(), options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(len(result.Value()) > 0)

	// Output: true
}
