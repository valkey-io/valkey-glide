// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_CustomCommand() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.CustomCommand([]string{"ping"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleClient_Move() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.New().String()
	_, err := client.Set(key, "hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	result, err := client.Move(key, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: true
}

func ExampleClient_Scan() {
	var client *Client = getExampleClient() // example helper function
	client.CustomCommand([]string{"FLUSHALL"})
	client.Set("key1", "hello")
	resCursor, resCollection, err := client.Scan(0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", resCursor)
	fmt.Println("Collection:", resCollection)

	// Output:
	// Cursor: 0
	// Collection: [key1]
}

func ExampleClient_ScanWithOptions() {
	var client *Client = getExampleClient() // example helper function
	opts := options.NewScanOptions().SetCount(10).SetType(constants.ObjectTypeList)
	client.CustomCommand([]string{"FLUSHALL"})
	client.LPush("key1", []string{"1", "3", "2", "4"})
	resCursor, resCollection, err := client.ScanWithOptions(0, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", resCursor)
	fmt.Println("Collection:", resCollection)

	// Output:
	// Cursor: 0
	// Collection: [key1]
}

func ExampleClient_RandomKey() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.New().String()
	client.Set(key, "Hello")
	result, err := client.RandomKey()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result.Value()) > 0)

	// Output: true
}
