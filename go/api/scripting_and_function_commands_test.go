// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/google/uuid"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
	"github.com/valkey-io/valkey-glide/go/integTest"
)

var (
	libraryCode         = integTest.GenerateLuaLibCode("mylib", map[string]string{"myfunc": "return 42"}, true)
	libraryCodeWithArgs = integTest.GenerateLuaLibCode("mylib", map[string]string{"myfunc": "return args[1]"}, true)
)

// FunctionLoad Examples
func ExampleGlideClient_FunctionLoad() {
	client := getExampleGlideClient()

	result, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

func ExampleGlideClusterClient_FunctionLoad() {
	client := getExampleGlideClusterClient()

	result, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

func ExampleGlideClusterClient_FunctionLoadWithRoute() {
	client := getExampleGlideClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionLoadWithRoute(libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

// FunctionFlush Examples
func ExampleGlideClient_FunctionFlush() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlush()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClusterClient_FunctionFlush() {
	client := getExampleGlideClusterClient()

	result, err := client.FunctionFlush()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClusterClient_FunctionFlushWithRoute() {
	client := getExampleGlideClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionFlushWithRoute(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClient_FunctionFlushSync() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlushSync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClusterClient_FunctionFlushSync() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlushSync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClusterClient_FunctionFlushSyncWithRoute() {
	client := getExampleGlideClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionFlushSyncWithRoute(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClient_FunctionFlushAsync() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlushAsync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleGlideClusterClient_FunctionFlushAsync() {
	client := getExampleGlideClusterClient()

	result, err := client.FunctionFlushAsync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)
}

func ExampleGlideClusterClient_FunctionFlushAsyncWithRoute() {
	client := getExampleGlideClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionFlushAsyncWithRoute(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

// FCall Examples
func ExampleGlideClient_FCall() {
	client := getExampleGlideClient()

	// Load function
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCall("myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleGlideClusterClient_FCall() {
	client := getExampleGlideClusterClient()

	// Load function
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCall("myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleGlideClusterClient_FCallWithRoute() {
	client := getExampleGlideClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallWithRoute("myfunc", opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	for _, value := range result.MultiValue() {
		fmt.Println(value)
		break
	}

	// Output:
	// 42
}

func ExampleGlideClient_FCallWithKeysAndArgs() {
	client := getExampleGlideClient()

	// Load function
	_, err := client.FunctionLoad(libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallWithKeysAndArgs("myfunc", []string{key1, key2}, []string{"3", "4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleGlideClusterClient_FCallWithKeysAndArgs() {
	client := getExampleGlideClusterClient()

	// Load function
	_, err := client.FunctionLoad(libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallWithKeysAndArgs("myfunc", []string{key1, key2}, []string{"3", "4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleGlideClusterClient_FCallWithArgs() {
	client := getExampleGlideClusterClient()

	// Load function
	_, err := client.FunctionLoad(libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallWithArgs("myfunc", []string{"1", "2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result.SingleValue())

	// Output:
	// 1
}

func ExampleGlideClusterClient_FCallWithArgsWithRoute() {
	client := getExampleGlideClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(libraryCodeWithArgs, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallWithArgsWithRoute("myfunc", []string{"1", "2"}, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	for _, value := range result.MultiValue() {
		fmt.Println(value)
		break
	}

	// Output:
	// 1
}

func ExampleGlideClient_FCallReadOnly() {
	client := getExampleGlideClient()

	// Load function
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCallReadOnly("myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleGlideClusterClient_FCallReadOnly() {
	client := getExampleGlideClusterClient()

	// Load function
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCallReadOnly("myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleGlideClusterClient_FCallReadOnlyWithRoute() {
	client := getExampleGlideClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallReadOnlyWithRoute("myfunc", opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	for _, value := range result.MultiValue() {
		fmt.Println(value)
		break
	}

	// Output:
	// 42
}

func ExampleGlideClient_FCallReadOnlyWithKeysAndArgs() {
	client := getExampleGlideClient()

	// Load function
	_, err := client.FunctionLoad(libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallReadOnlyWithKeysAndArgs("myfunc", []string{key1, key2}, []string{"3", "4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleGlideClusterClient_FCallReadOnlyWithKeysAndArgs() {
	client := getExampleGlideClusterClient()

	// Load function
	_, err := client.FunctionLoad(libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallReadOnlyWithKeysAndArgs("myfunc", []string{key1, key2}, []string{"3", "4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleGlideClusterClient_FCallReadOnlyWithArgs() {
	client := getExampleGlideClusterClient()

	// Load function
	_, err := client.FunctionLoad(libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallReadOnlyWithArgs("myfunc", []string{"1", "2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result.SingleValue())

	// Output:
	// 1
}

func ExampleGlideClusterClient_FCallReadOnlyWithArgsWithRoute() {
	client := getExampleGlideClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(libraryCodeWithArgs, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallReadOnlyWithArgsWithRoute("myfunc", []string{"1", "2"}, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	for _, value := range result.MultiValue() {
		fmt.Println(value)
		break
	}

	// Output:
	// 1
}
