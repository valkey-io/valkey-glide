// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
)

func ExampleGlideClient_CustomCommand() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.CustomCommand([]string{"ping"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: PONG
}

func ExampleGlideClient_Scan() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	resCursor, resCollection, err := client.Scan(0)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Scan Cursor: ", resCursor, "Collection: ", resCollection)

	// Output: Cursor: [0],  Collection: [key456 key123 key789 keylist1]
}

func ExampleGlideClient_ScanWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	opts := options.NewScanOptions().SetCount(10).SetType(options.ObjectTypeList)
	resCursor, resCollection, err := client.ScanWithOptions(0, *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println("Scan Cursor: ", resCursor, "Collection: ", resCollection)

	// Output: Cursor: [0],  Collection [keylist1]
}
