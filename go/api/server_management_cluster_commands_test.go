// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
	"strings"

	"github.com/google/uuid"
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

func ExampleGlideClusterClient_Lolwut() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	result, err := client.Lolwut()
	if err != nil {
		fmt.Println("Glide example failed with an error:", err)
	} else {
		if len(result) > 0 {
			fmt.Println("LOLWUT pattern generated successfully")
		}
	}

	// Output: LOLWUT pattern generated successfully
}

func ExampleGlideClusterClient_LolwutWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	randomRouteOptions := options.ClusterLolwutOptions{
		LolwutOptions: &options.LolwutOptions{
			Version: 6,
			Args:    &[]int{10, 20},
		},
		RouteOption: &options.RouteOption{
			Route: config.RandomRoute,
		},
	}

	result, err := client.LolwutWithOptions(randomRouteOptions)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	if len(result.SingleValue()) > 0 {
		fmt.Println("LOLWUT pattern generated successfully")
	}
	// Output: LOLWUT pattern generated successfully
}

func ExampleGlideClusterClient_LastSave() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	key := "key-" + uuid.NewString()
	client.Set(key, "hello")
	result, err := client.LastSave()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsSingleValue())

	// Output: true
}

func ExampleGlideClusterClient_LastSaveWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.RouteOption{Route: nil}
	key := "key-" + uuid.NewString()
	client.Set(key, "hello")
	result, err := client.LastSaveWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.IsSingleValue())

	// Output: true
}

func ExampleGlideClusterClient_ConfigResetStat() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.ConfigResetStat()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClusterClient_ConfigResetStatWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.RouteOption{Route: nil}
	result, err := client.ConfigResetStatWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output: OK
}

func ExampleGlideClusterClient_ConfigSet() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	configParam := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	result, err := client.ConfigSet(configParam)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClusterClient_ConfigSetWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.RouteOption{Route: config.RandomRoute}
	configParam := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	result, err := client.ConfigSetWithOptions(configParam, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClusterClient_ConfigGet() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	configParamSet := map[string]string{"timeout": "1000"}
	client.ConfigSet(configParamSet)
	configParamGet := []string{"timeout"}
	result, err := client.ConfigGet(configParamGet)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.MultiValue())

	// Output:
	// map[timeout:1000]
}

func ExampleGlideClusterClient_ConfigGetWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	opts := options.RouteOption{Route: config.RandomRoute}
	configParamSet := map[string]string{"timeout": "1000"}
	client.ConfigSetWithOptions(configParamSet, opts)
	configParamGet := []string{"timeout"}
	result, err := client.ConfigGetWithOptions(configParamGet, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.MultiValue())

	// Output:
	// map[timeout:1000]
}

func ExampleGlideClusterClient_ConfigRewrite() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	var resultRewrite string
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
			responseRewrite, err := client.ConfigRewrite()
			if err != nil {
				fmt.Println("Glide example failed with an error: ", err)
			}
			resultRewrite = responseRewrite
			break
		} else {
			resultRewrite = "OK"
		}

	}
	fmt.Println(resultRewrite)

	// Output:
	// OK
}

func ExampleGlideClusterClient_ConfigRewriteWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	sections := []options.Section{options.Server}

	// info with option or with multiple options without route
	var runResultNilRoute string
	opts := options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: nil,
	}
	response, err := client.InfoWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	for _, data := range response.MultiValue() {
		lines := strings.Split(data, "\n")
		var configFile string
		for _, line := range lines {
			if strings.HasPrefix(line, "config_file:") {
				configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
				break
			}
		}
		if len(configFile) > 0 {
			responseRewrite, err := client.ConfigRewrite()
			if err != nil {
				fmt.Println("Glide example failed with an error: ", err)
			}
			runResultNilRoute = responseRewrite
			break
		}
		runResultNilRoute = "OK"
	}

	// same sections with random route
	var runResultRandomRoute string
	opts = options.ClusterInfoOptions{
		InfoOptions: &options.InfoOptions{Sections: sections},
		RouteOption: &options.RouteOption{Route: config.RandomRoute},
	}
	response, err = client.InfoWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	lines := strings.Split(response.SingleValue(), "\n")
	var configFile string
	for _, line := range lines {
		if strings.HasPrefix(line, "config_file:") {
			configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
			break
		}
	}
	if len(configFile) > 0 {
		responseRewrite, err := client.ConfigRewrite()
		if err != nil {
			fmt.Println("Glide example failed with an error: ", err)
		}
		runResultRandomRoute = responseRewrite
	}
	runResultRandomRoute = "OK"

	// default sections, multi node route
	var runResultMultiNodeRoute string
	opts = options.ClusterInfoOptions{
		InfoOptions: nil,
		RouteOption: &options.RouteOption{Route: config.AllPrimaries},
	}
	response, err = client.InfoWithOptions(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	for _, data := range response.MultiValue() {
		lines := strings.Split(data, "\n")
		var configFile string
		for _, line := range lines {
			if strings.HasPrefix(line, "config_file:") {
				configFile = strings.TrimSpace(strings.TrimPrefix(line, "config_file:"))
				break
			}
		}
		if len(configFile) > 0 {
			responseRewrite, err := client.ConfigRewrite()
			if err != nil {
				fmt.Println("Glide example failed with an error: ", err)
			}
			runResultMultiNodeRoute = responseRewrite
			break
		}
		runResultMultiNodeRoute = "OK"
	}
	fmt.Println("Multiple options without route result:", runResultNilRoute)
	fmt.Println("Random route result:", runResultRandomRoute)
	fmt.Println("Multi node route result:", runResultMultiNodeRoute)

	// Output:
	// Multiple options without route result: OK
	// Random route result: OK
	// Multi node route result: OK
}
