// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_SetBit() {
	var client *Client = getExampleClient() // example helper function

	client.SetBit(context.Background(), "my_key", 1, 1) // initialize bit 1 with a value of 1

	result, err := client.SetBit(context.Background(), "my_key", 1, 1) // set bit should return the previous value of bit 1
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClusterClient_SetBit() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.SetBit(context.Background(), "my_key", 1, 1) // initialize bit 1 with a value of 1

	result, err := client.SetBit(context.Background(), "my_key", 1, 1) // set bit should return the previous value of bit 1
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClient_GetBit() {
	var client *Client = getExampleClient() // example helper function

	client.SetBit(context.Background(), "my_key", 1, 1)
	result, err := client.GetBit(context.Background(), "my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClusterClient_GetBit() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.SetBit(context.Background(), "my_key", 1, 1)
	client.SetBit(context.Background(), "my_key", 1, 1)
	result, err := client.GetBit(context.Background(), "my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleClient_BitCount() {
	var client *Client = getExampleClient() // example helper function

	client.SetBit(context.Background(), "my_key", 1, 1)
	client.SetBit(context.Background(), "my_key", 2, 1)
	result, err := client.BitCount(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClusterClient_BitCount() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.SetBit(context.Background(), "my_key", 1, 1)
	client.SetBit(context.Background(), "my_key", 2, 1)
	result, err := client.BitCount(context.Background(), "my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleClient_BitCountWithOptions() {
	var client *Client = getExampleClient() // example helper function

	options := options.NewBitCountOptions().
		SetStart(1).
		SetEnd(1).
		SetBitmapIndexType(options.BYTE)
	result, err := client.BitCountWithOptions(context.Background(), "my_key", options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 0
}

func ExampleClusterClient_BitCountWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	options := options.NewBitCountOptions().
		SetStart(1).
		SetEnd(1).
		SetBitmapIndexType(options.BYTE)
	result, err := client.BitCountWithOptions(context.Background(), "my_key", options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 0
}

func ExampleClient_BitField() {
	var client *Client = getExampleClient() // example helper function

	commands := []options.BitFieldSubCommands{
		options.NewBitFieldGet(options.SignedInt, 8, 16),
		options.NewBitFieldOverflow(options.SAT),
		options.NewBitFieldSet(options.UnsignedInt, 4, 0, 7),
		options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
	}
	result, err := client.BitField(context.Background(), "mykey", commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{0 false} {0 false} {1 false}]
}

func ExampleClusterClient_BitField() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	commands := []options.BitFieldSubCommands{
		options.NewBitFieldGet(options.SignedInt, 8, 16),
		options.NewBitFieldOverflow(options.SAT),
		options.NewBitFieldSet(options.UnsignedInt, 4, 0, 7),
		options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
	}
	result, err := client.BitField(context.Background(), "mykey", commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{0 false} {0 false} {1 false}]
}

func ExampleClient_BitFieldRO() {
	var client *Client = getExampleClient() // example helper function
	key := "mykey"

	bfcommands := []options.BitFieldSubCommands{
		options.NewBitFieldSet(options.UnsignedInt, 8, 0, 24),
	}
	client.BitField(context.Background(), key, bfcommands)

	commands := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	result, err := client.BitFieldRO(context.Background(), key, commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{24 false}]
}

func ExampleClusterClient_BitFieldRO() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	key := "mykey"

	bfcommands := []options.BitFieldSubCommands{
		options.NewBitFieldSet(options.UnsignedInt, 8, 0, 24),
	}
	client.BitField(context.Background(), key, bfcommands)

	commands := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	result, err := client.BitFieldRO(context.Background(), key, commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{24 false}]
}

func ExampleClient_BitOp() {
	var client *Client = getExampleClient()

	bitopkey1 := "{bitop_test}key1"
	bitopkey2 := "{bitop_test}key2"
	destKey := "{bitop_test}dest"

	// Set initial values
	client.Set(context.Background(), bitopkey1, "foobar")
	client.Set(context.Background(), bitopkey2, "abcdef")

	// Perform BITOP AND
	result, err := client.BitOp(context.Background(), options.AND, destKey, []string{bitopkey1, bitopkey2})
	if err != nil {
		fmt.Println("BitOp AND failed:", err)
	} else {
		fmt.Println("BitOp AND Result:", result)
	}

	// Perform BITOP OR
	result, err = client.BitOp(context.Background(), options.OR, destKey, []string{bitopkey1, bitopkey2})
	if err != nil {
		fmt.Println("BitOp OR failed:", err)
	} else {
		fmt.Println("BitOp OR Result:", result)
	}

	// Perform BITOP XOR
	result, err = client.BitOp(context.Background(), options.XOR, destKey, []string{bitopkey1, bitopkey2})
	if err != nil {
		fmt.Println("BitOp XOR failed:", err)
	} else {
		fmt.Println("BitOp XOR Result:", result)
	}

	// Perform BITOP NOT (only one source key allowed)
	result, err = client.BitOp(context.Background(), options.NOT, destKey, []string{bitopkey1})
	if err != nil {
		fmt.Println("BitOp NOT failed:", err)
	} else {
		fmt.Println("BitOp NOT Result:", result)
	}

	// Output:
	// BitOp AND Result: 6
	// BitOp OR Result: 6
	// BitOp XOR Result: 6
	// BitOp NOT Result: 6
}

func ExampleClusterClient_BitOp() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	bitopkey1 := "{bitop_test}key1"
	bitopkey2 := "{bitop_test}key2"
	destKey := "{bitop_test}dest"

	// Set initial values
	client.Set(context.Background(), bitopkey1, "foobar")
	client.Set(context.Background(), bitopkey2, "abcdef")

	// Perform BITOP AND
	result, err := client.BitOp(context.Background(), options.AND, destKey, []string{bitopkey1, bitopkey2})
	if err != nil {
		fmt.Println("BitOp AND failed:", err)
	} else {
		fmt.Println("BitOp AND Result:", result)
	}

	// Perform BITOP OR
	result, err = client.BitOp(context.Background(), options.OR, destKey, []string{bitopkey1, bitopkey2})
	if err != nil {
		fmt.Println("BitOp OR failed:", err)
	} else {
		fmt.Println("BitOp OR Result:", result)
	}

	// Perform BITOP XOR
	result, err = client.BitOp(context.Background(), options.XOR, destKey, []string{bitopkey1, bitopkey2})
	if err != nil {
		fmt.Println("BitOp XOR failed:", err)
	} else {
		fmt.Println("BitOp XOR Result:", result)
	}

	// Perform BITOP NOT (only one source key allowed)
	result, err = client.BitOp(context.Background(), options.NOT, destKey, []string{bitopkey1})
	if err != nil {
		fmt.Println("BitOp NOT failed:", err)
	} else {
		fmt.Println("BitOp NOT Result:", result)
	}

	// Output:
	// BitOp AND Result: 6
	// BitOp OR Result: 6
	// BitOp XOR Result: 6
	// BitOp NOT Result: 6
}

func ExampleClient_BitPos() {
	var client *Client = getExampleClient() // example helper function

	client.SetBit(context.Background(), "my_key", 7, 1)

	result, err := client.BitPos(context.Background(), "my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 7
}

func ExampleClusterClient_BitPos() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.SetBit(context.Background(), "my_key", 7, 1)

	result, err := client.BitPos(context.Background(), "my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 7
}

func ExampleClient_BitPosWithOptions() {
	var client *Client = getExampleClient() // example helper function

	client.Set(context.Background(), "my_key", "\x00\x01\x00")

	options := options.NewBitPosOptions().
		SetStart(0).
		SetEnd(1)

	result, err := client.BitPosWithOptions(context.Background(), "my_key", 1, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 15
}

func ExampleClusterClient_BitPosWithOptions() {
	var client *ClusterClient = getExampleClusterClient() // example helper function

	client.Set(context.Background(), "my_key", "\x00\x10\x00")

	options := options.NewBitPosOptions().
		SetStart(10).
		SetEnd(14).
		SetBitmapIndexType(options.BIT)

	result, err := client.BitPosWithOptions(context.Background(), "my_key", 1, options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 11
}
