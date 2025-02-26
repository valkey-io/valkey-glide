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

	// Output: 0
}

func ExampleGlideClusterClient_ConfigSetGetWithOptions() {
	client := suite.defaultClusterClient()
	t := suite.T()
	// ConfigResetStat with option or with multiple options without route
	opts := options.RouteOption{Route: nil}
	configParam := map[string]string{"timeout": "1000", "maxmemory": "1GB"}
	suite.verifyOK(client.ConfigSetWithOptions(configParam, opts))
	configGetParam := []string{"timeout", "maxmemory"}
	resp, err := client.ConfigGetWithOptions(configGetParam, opts)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))

	// same sections with random route
	route := config.Route(config.RandomRoute)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigSetWithOptions(configParam, opts))
	resp, err = client.ConfigGetWithOptions(configGetParam, opts)
	assert.NoError(t, err)
	assert.Contains(t, strings.ToLower(fmt.Sprint(resp)), strings.ToLower("timeout"))

	// default sections, multi node route
	route = config.Route(config.AllPrimaries)
	opts = options.RouteOption{Route: route}
	suite.verifyOK(client.ConfigSetWithOptions(configParam, opts))

	resp, err = client.ConfigGetWithOptions(configGetParam, opts)
	assert.NoError(t, err)
	assert.True(t, resp.IsMultiValue())
	for _, messages := range resp.MultiValue() {
		mapString := fmt.Sprint(messages)
		assert.Contains(t, strings.ToLower(mapString), strings.ToLower("timeout"))
	}
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

	// Output: OK
}

func ExampleGlideClusterClient_ConfigGetWithOptions() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	opts := options.RouteOption{Route: config.RandomRoute}
	configParam := []string{"timeout", "maxmemory"}
	result, err := client.ConfigGetWithOptions(configParam, opts)

	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output: {2 <nil> map[maxmemory:1073741824 timeout:1000]}
}
