// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_SetBit() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.SetBit("my_key", 1, 1) // initialize bit 1 with a value of 1

	result, err := client.SetBit("my_key", 1, 1) // set bit should return the previous value of bit 1
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClusterClient_SetBit() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.SetBit("my_key", 1, 1) // initialize bit 1 with a value of 1

	result, err := client.SetBit("my_key", 1, 1) // set bit should return the previous value of bit 1
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClient_GetBit() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.SetBit("my_key", 1, 1)
	result, err := client.GetBit("my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClusterClient_GetBit() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.SetBit("my_key", 1, 1)
	client.SetBit("my_key", 1, 1)
	result, err := client.GetBit("my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 1
}

func ExampleGlideClient_BitCount() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.SetBit("my_key", 1, 1)
	client.SetBit("my_key", 2, 1)
	result, err := client.BitCount("my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClusterClient_BitCount() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.SetBit("my_key", 1, 1)
	client.SetBit("my_key", 2, 1)
	result, err := client.BitCount("my_key")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 2
}

func ExampleGlideClient_BitCountWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	options := options.NewBitCountOptions().
		SetStart(1).
		SetEnd(1).
		SetBitmapIndexType(options.BYTE)
	result, err := client.BitCountWithOptions("my_key", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 0
}

func ExampleGlideClusterClient_BitCountWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	options := options.NewBitCountOptions().
		SetStart(1).
		SetEnd(1).
		SetBitmapIndexType(options.BYTE)
	result, err := client.BitCountWithOptions("my_key", *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 0
}

func ExampleGlideClient_BitField() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	commands := []options.BitFieldSubCommands{
		options.NewBitFieldGet(options.SignedInt, 8, 16),
		options.NewBitFieldOverflow(options.SAT),
		options.NewBitFieldSet(options.UnsignedInt, 4, 0, 7),
		options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
	}
	result, err := client.BitField("mykey", commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{0 false} {0 false} {1 false}]
}

func ExampleGlideClusterClient_BitField() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	commands := []options.BitFieldSubCommands{
		options.NewBitFieldGet(options.SignedInt, 8, 16),
		options.NewBitFieldOverflow(options.SAT),
		options.NewBitFieldSet(options.UnsignedInt, 4, 0, 7),
		options.NewBitFieldIncrBy(options.SignedInt, 5, 100, 1),
	}
	result, err := client.BitField("mykey", commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{0 false} {0 false} {1 false}]
}

func ExampleGlideClient_BitFieldRO() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	key := "mykey"

	bfcommands := []options.BitFieldSubCommands{
		options.NewBitFieldSet(options.UnsignedInt, 8, 0, 24),
	}
	client.BitField(key, bfcommands)

	commands := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	result, err := client.BitFieldRO(key, commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{24 false}]
}

func ExampleGlideClusterClient_BitFieldRO() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "mykey"

	bfcommands := []options.BitFieldSubCommands{
		options.NewBitFieldSet(options.UnsignedInt, 8, 0, 24),
	}
	client.BitField(key, bfcommands)

	commands := []options.BitFieldROCommands{
		options.NewBitFieldGet(options.UnsignedInt, 8, 0),
	}
	result, err := client.BitFieldRO(key, commands)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// output: [{24 false}]
}

func ExampleGlideClient_BitPos() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.SetBit("my_key", 7, 1)

	result, err := client.BitPos("my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 7
}

func ExampleGlideClusterClient_BitPos() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.SetBit("my_key", 7, 1)

	result, err := client.BitPos("my_key", 1)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 7
}

func ExampleGlideClient_BitPosWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	client.Set("my_key", "\x00\x01\x00")

	options := options.NewBitPosOptions().
		SetStart(0).
		SetEnd(1)

	result, err := client.BitPosWithOptions("my_key", 1, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 15
}

func ExampleGlideClusterClient_BitPosWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	client.Set("my_key", "\x00\x10\x00")

	options := options.NewBitPosOptions().
		SetStart(10).
		SetEnd(14).
		SetBitmapIndexType(options.BIT)

	result, err := client.BitPosWithOptions("my_key", 1, *options)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: 12
}
