// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"strconv"
	"time"

	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClient_Select() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	result, err := client.Select(2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClient_ConfigGet() {
	var client *GlideClient = getExampleGlideClient()                          // example helper function
	client.ConfigSet(map[string]string{"timeout": "1000", "maxmemory": "1GB"}) // example configuration
	result, err := client.ConfigGet([]string{"timeout", "maxmemory"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// map[maxmemory:1073741824 timeout:1000]
}

func ExampleGlideClient_ConfigSet() {
	var client *GlideClient = getExampleGlideClient()                                         // example helper function
	result, err := client.ConfigSet(map[string]string{"timeout": "1000", "maxmemory": "1GB"}) // example configuration
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClient_DBSize() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	// Assume flushed client, so no keys are currently stored
	client.Set("key", "val")
	result, err := client.DBSize()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 1
}

func ExampleGlideClient_Time() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	timeMargin := int64(5)
	clientTime := time.Now().Unix()
	result, err := client.Time()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	serverTime, _ := strconv.ParseInt(result[0], 10, 64)
	fmt.Println((serverTime - clientTime) < timeMargin)

	// Output: true
}

func ExampleGlideClusterClient_Time() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	timeMargin := int64(5)
	clientTime := time.Now().Unix()

	result, err := client.Time()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	serverTime, _ := strconv.ParseInt(result[0], 10, 64)
	fmt.Println((serverTime - clientTime) < timeMargin)

	// Output: true
}

func ExampleGlideClient_Info() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	response, err := client.Info()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("response is of type %T\n", response)

	// Output: response is of type string
}

func ExampleGlideClient_InfoWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function

	opts := options.InfoOptions{Sections: []options.Section{options.Server}}
	response, err := client.InfoWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("response is of type %T\n", response)

	// Output: response is of type string
}

func ExampleGlideClient_LolwutWithOptions() {
	var client *GlideClient = getExampleGlideClient() // example helper function
	// Test with only version
	opts := options.NewLolwutOptions(6)
	result, err := client.LolwutWithOptions(*opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Printf("LOLWUT version result is of type %T\n", result)
	}

	// Test with version and arguments
	opts = options.NewLolwutOptions(6).SetArgs([]int{10, 20})
	result, err = client.LolwutWithOptions(*opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Printf("LOLWUT version with args result is of type %T\n", result)
	}

	// Output:
	// LOLWUT version result is of type string
	// LOLWUT version with args result is of type string
}
