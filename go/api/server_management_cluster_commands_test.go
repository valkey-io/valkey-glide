// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"strings"

	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

func ExampleGlideClusterClient_Info() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	response, err := client.Info()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	for _, data := range response {
		if strings.Contains(data, "cluster_enabled:1") {
			fmt.Println("OK")
			break
		}
	}

	// Output: OK
}

func ExampleGlideClusterClient_InfoWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Cluster}},
	}

	response, err := client.InfoWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	for _, data := range response.MultiValue() {
		if strings.Contains(data, "cluster_enabled:1") {
			fmt.Println("OK")
			break
		}
	}

	// Output: OK
}

func ExampleGlideClusterClient_TimeWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	route := config.Route(config.RandomRoute)
	opts := options.RouteOption{
		Route: route,
	}
	clusterResponse, err := client.TimeWithOptions(opts) // gives: {1 [1738714595 942076] map[]}
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(len(clusterResponse.SingleValue()) == 2)

	// Output: true
}

func ExampleGlideClusterClient_DBSizeWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	route := config.SimpleNodeRoute(config.RandomRoute)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.DBSizeWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// 0
}

func ExampleGlideClusterClient_ConfigRewrite() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	client.CustomCommand([]string{"CONFIG", "SET", "timeout", "1000"})
	response, err := client.ConfigRewrite()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClusterClient_ConfigRewriteWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	client.CustomCommand([]string{"CONFIG", "SET", "timeout", "1000"})
	route := config.Route(config.RandomRoute)
	opts := options.RouteOption{Route: route}
	response, err := client.ConfigRewriteWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(response)

	// Output: OK
}

func ExampleGlideClusterClient_FlushAll() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.FlushAll()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClusterClient_FlushDB() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.FlushDB()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClusterClient_FlushAllWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	route := config.SimpleNodeRoute(config.AllPrimaries)
	routeOption := &options.RouteOption{
		Route: route,
	}

	asyncMode := options.ASYNC

	flushOptions := options.FlushClusterOptions{
		FlushMode:   &asyncMode,
		RouteOption: routeOption,
	}

	result, err := client.FlushAllWithOptions(flushOptions)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClusterClient_FlushDBWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	route := config.SimpleNodeRoute(config.AllPrimaries)
	routeOption := &options.RouteOption{
		Route: route,
	}

	syncMode := options.SYNC

	flushOptions := options.FlushClusterOptions{
		FlushMode:   &syncMode,
		RouteOption: routeOption,
	}

	result, err := client.FlushDBWithOptions(flushOptions)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClient_ConfigRewriteCluster() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: []options.Section{options.Server}},
	}
	res, err := client.InfoWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	for _, data := range res.MultiValue() {
		lines := strings.Split(data, "\n")
		var configFile string
		for _, line := range lines {
			if strings.HasPrefix(line, "config_file:") {
				configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
				break
			}
		}
		if len(configFile) > 0 {
			fmt.Println("ConfigFile: ", configFile)
			responseRewrite, err := client.ConfigRewrite()
			if err != nil && responseRewrite != "OK" {
				fmt.Println("Glide example failed with an error: ", err)
			}
		}
	}
	fmt.Println("OK")

	// Output: OK
}
