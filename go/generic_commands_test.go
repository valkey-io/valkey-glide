// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
	"github.com/valkey-io/valkey-glide/go/v2/models"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_CustomCommand() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.CustomCommand(context.Background(), []string{"ping"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleClient_Move() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.New().String()
	_, err := client.Set(context.Background(), key, "hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	result, err := client.Move(context.Background(), key, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: true
}

func ExampleClusterClient_Move() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := uuid.New().String()
	_, err := client.Set(context.Background(), key, "hello")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	result, err := client.Move(context.Background(), key, 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: true
}

func ExampleClient_Scan() {
	var client *Client = getExampleClient() // example helper function
	client.CustomCommand(context.Background(), []string{"FLUSHALL"})
	client.Set(context.Background(), "key1", "hello")
	result, err := client.Scan(context.Background(), models.NewCursor())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", result.Cursor)
	fmt.Println("Collection:", result.Data)

	// Output:
	// Cursor: 0
	// Collection: [key1]
}

func ExampleClient_ScanWithOptions() {
	var client *Client = getExampleClient() // example helper function
	opts := options.NewScanOptions().SetCount(10).SetType(constants.ObjectTypeList)
	client.CustomCommand(context.Background(), []string{"FLUSHALL"})
	client.LPush(context.Background(), "key1", []string{"1", "3", "2", "4"})
	result, err := client.ScanWithOptions(context.Background(), models.NewCursor(), *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Cursor:", result.Cursor)
	fmt.Println("Collection:", result.Data)

	// Output:
	// Cursor: 0
	// Collection: [key1]
}

func ExampleClient_RandomKey() {
	var client *Client = getExampleClient() // example helper function
	key := uuid.New().String()
	client.Set(context.Background(), key, "Hello")
	result, err := client.RandomKey(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(len(result.Value()) > 0)

	// Output: true
}
