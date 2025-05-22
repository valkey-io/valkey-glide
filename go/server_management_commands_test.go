// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/constants"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/options"
)

func ExampleClient_Select() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.Select(context.Background(), 2)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_ConfigGet() {
	var client *Client = getExampleClient()                                                          // example helper function
	client.ConfigSet(context.Background(), map[string]string{"timeout": "1000", "maxmemory": "1GB"}) // example configuration
	result, err := client.ConfigGet(context.Background(), []string{"timeout", "maxmemory"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// map[maxmemory:1073741824 timeout:1000]
}

func ExampleClient_ConfigSet() {
	var client *Client = getExampleClient() // example helper function
	result, err := client.ConfigSet(
		context.Background(),
		map[string]string{"timeout": "1000", "maxmemory": "1GB"},
	) // example configuration
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClient_DBSize() {
	var client *Client = getExampleClient() // example helper function
	// Assume flushed client, so no keys are currently stored
	client.Set(context.Background(), "key", "val")
	result, err := client.DBSize(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 1
}

func ExampleClient_Time() {
	var client *Client = getExampleClient() // example helper function
	timeMargin := int64(5)
	clientTime := time.Now().Unix()
	result, err := client.Time(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	serverTime, _ := strconv.ParseInt(result[0], 10, 64)
	fmt.Println((serverTime - clientTime) < timeMargin)

	// Output: true
}

func ExampleClusterClient_Time() {
	var client *ClusterClient = getExampleClusterClient() // example helper function
	timeMargin := int64(5)
	clientTime := time.Now().Unix()

	result, err := client.Time(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	serverTime, _ := strconv.ParseInt(result[0], 10, 64)
	fmt.Println((serverTime - clientTime) < timeMargin)

	// Output: true
}

func ExampleClient_Info() {
	var client *Client = getExampleClient() // example helper function

	response, err := client.Info(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("response is of type %T\n", response)

	// Output: response is of type string
}

func ExampleClient_InfoWithOptions() {
	var client *Client = getExampleClient() // example helper function

	opts := options.InfoOptions{Sections: []constants.Section{constants.Server}}
	response, err := client.InfoWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Printf("response is of type %T\n", response)

	// Output: response is of type string
}

func ExampleClient_FlushAll() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.FlushAll(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_FlushAllWithOptions() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.FlushAllWithOptions(context.Background(), options.ASYNC)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_FlushDB() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.FlushDB(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_FlushDBWithOptions() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.FlushDBWithOptions(context.Background(), options.SYNC)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleClient_Lolwut() {
	var client *Client = getExampleClient() // example helper function

	result, err := client.Lolwut(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Printf("LOLWUT result is of type %T\n", result)
	}

	// Output:
	// LOLWUT result is of type string
}

func ExampleClient_LolwutWithOptions() {
	var client *Client = getExampleClient() // example helper function
	// Test with only version
	opts := options.NewLolwutOptions(6)
	result, err := client.LolwutWithOptions(context.Background(), *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Printf("LOLWUT version result is of type %T\n", result)
	}

	// Test with version and arguments
	opts = options.NewLolwutOptions(6).SetArgs([]int{10, 20})
	result, err = client.LolwutWithOptions(context.Background(), *opts)
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		fmt.Printf("LOLWUT version with args result is of type %T\n", result)
	}

	// Output:
	// LOLWUT version result is of type string
	// LOLWUT version with args result is of type string
}

func ExampleClient_LastSave() {
	var client *Client = getExampleClient() // example helper function
	key := "key-" + uuid.NewString()
	client.Set(context.Background(), key, "hello")
	response, err := client.LastSave(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response > 0)

	// Output: true
}

func ExampleClient_ConfigResetStat() {
	var client *Client = getExampleClient() // example helper function
	response, err := client.ConfigResetStat(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output:
	// OK
}

func ExampleClient_ConfigRewrite() {
	var client *Client = getExampleClient() // example helper function
	opts := options.InfoOptions{Sections: []constants.Section{constants.Server}}
	var resultRewrite string
	response, err := client.InfoWithOptions(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	lines := strings.Split(response, "\n")
	var configFile string
	for _, line := range lines {
		if strings.HasPrefix(line, "config_file:") {
			configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
			break
		}
	}
	if len(configFile) > 0 {
		response, err = client.ConfigRewrite(context.Background())
		if err != nil {
			fmt.Println("Glide example failed with an error: ", err)
		}
		resultRewrite = response
	} else {
		resultRewrite = "OK"
	}
	fmt.Println(resultRewrite)

	// Output:
	// OK
}
