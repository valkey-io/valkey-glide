// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

var (
	libraryCode = `#!lua name=mylib
redis.register_function{ function_name = 'myfunc', callback = function(keys, args) return 42 end, flags = { 'no-writes' } }`
	libraryCodeWithArgs = `#!lua name=mylib
redis.register_function{ function_name = 'myfunc', callback = function(keys, args) return args[1] end, flags = { 'no-writes' } }`
)

// FunctionLoad Examples
func ExampleClient_FunctionLoad() {
	client := getExampleGlideClient()

	result, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

func ExampleClusterClient_FunctionLoad() {
	client := getExampleGlideClusterClient()

	result, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

func ExampleClusterClient_FunctionLoadWithRoute() {
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
func ExampleClient_FunctionFlush() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlush()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlush() {
	client := getExampleGlideClusterClient()

	result, err := client.FunctionFlush()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushWithRoute() {
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

func ExampleClient_FunctionFlushSync() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlushSync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushSync() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlushSync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushSyncWithRoute() {
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

func ExampleClient_FunctionFlushAsync() {
	client := getExampleGlideClient()

	result, err := client.FunctionFlushAsync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushAsync() {
	client := getExampleGlideClusterClient()

	result, err := client.FunctionFlushAsync()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)
}

func ExampleClusterClient_FunctionFlushAsyncWithRoute() {
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
func ExampleClient_FCall() {
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

func ExampleClusterClient_FCall() {
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

func ExampleClusterClient_FCallWithRoute() {
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

func ExampleClient_FCallWithKeysAndArgs() {
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

func ExampleClusterClient_FCallWithKeysAndArgs() {
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

func ExampleClusterClient_FCallWithArgs() {
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

func ExampleClusterClient_FCallWithArgsWithRoute() {
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

func ExampleClient_FCallReadOnly() {
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

func ExampleClusterClient_FCallReadOnly() {
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

func ExampleClusterClient_FCallReadOnlyWithRoute() {
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

func ExampleClient_FCallReadOnlyWithKeysAndArgs() {
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

func ExampleClusterClient_FCallReadOnlyWithKeysAndArgs() {
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

func ExampleClusterClient_FCallReadOnlyWithArgs() {
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

func ExampleClusterClient_FCallReadOnlyWithArgsWithRoute() {
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

func ExampleClient_FunctionStats() {
	client := getExampleGlideClient()

	// Load a function first
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Get function statistics
	stats, err := client.FunctionStats()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Print statistics for each node
	for _, nodeStats := range stats {
		fmt.Println("Example stats:")
		for engineName, engine := range nodeStats.Engines {
			fmt.Printf("  Engine %s: %d functions, %d libraries\n",
				engineName, engine.FunctionCount, engine.LibraryCount)
		}
	}

	// Output:
	// Example stats:
	//   Engine LUA: 1 functions, 1 libraries
}

func ExampleClusterClient_FunctionStats() {
	client := getExampleGlideClusterClient()

	// Load a function first
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Get function statistics
	stats, err := client.FunctionStats()
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Print statistics
	fmt.Printf("Nodes reached: %d\n", len(stats))
	for _, nodeStats := range stats {
		fmt.Println("Example stats:")
		for engineName, engine := range nodeStats.Engines {
			fmt.Printf("  Engine %s: %d functions, %d libraries\n",
				engineName, engine.FunctionCount, engine.LibraryCount)
		}
		break
	}

	// Output:
	// Nodes reached: 6
	// Example stats:
	//   Engine LUA: 1 functions, 1 libraries
}

func ExampleClusterClient_FunctionStatsWithRoute() {
	client := getExampleGlideClusterClient()

	// Load a function first
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Get function statistics with route
	stats, err := client.FunctionStatsWithRoute(opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Print statistics
	fmt.Printf("Nodes reached: %d\n", len(stats.MultiValue()))
	for _, nodeStats := range stats.MultiValue() {
		fmt.Println("Example stats:")
		for engineName, engine := range nodeStats.Engines {
			fmt.Printf("  Engine %s: %d functions, %d libraries\n",
				engineName, engine.FunctionCount, engine.LibraryCount)
		}
		break
	}

	// Output:
	// Nodes reached: 3
	// Example stats:
	//   Engine LUA: 1 functions, 1 libraries
}

func ExampleClient_FunctionDelete() {
	client := getExampleGlideClient()

	// Load a function first
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Delete function
	result, err := client.FunctionDelete("mylib")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionDelete() {
	client := getExampleGlideClusterClient()

	// Load a function first
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Delete function
	result, err := client.FunctionDelete("mylib")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionDeleteWithRoute() {
	client := getExampleGlideClusterClient()

	// Load a function first
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Delete function with route
	result, err := client.FunctionDeleteWithRoute("mylib", opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClient_FunctionKill() {
	client := getExampleGlideClient()

	// Try to kill when no function is running
	_, err := client.FunctionKill()
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClusterClient_FunctionKill() {
	client := getExampleGlideClusterClient()

	// Try to kill when no function is running
	_, err := client.FunctionKill()
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClusterClient_FunctionKillWithRoute() {
	client := getExampleGlideClusterClient()

	// Try to kill with route when no function is running
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionKillWithRoute(opts)
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClient_FunctionList() {
	client := getExampleGlideClient()

	// Load a function first
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	query := models.FunctionListQuery{
		LibraryName: "mylib",
		WithCode:    true,
	}

	libs, err := client.FunctionList(query)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Printf("There are %d libraries loaded.\n", len(libs))
	for i, lib := range libs {
		fmt.Printf("%d) Library name '%s', on engine %s, with %d functions\n", i+1, lib.Name, lib.Engine, len(lib.Functions))
		for j, fn := range lib.Functions {
			fmt.Printf("   %d) function '%s'\n", j+1, fn.Name)
		}
	}
	// Output:
	// There are 1 libraries loaded.
	// 1) Library name 'mylib', on engine LUA, with 1 functions
	//    1) function 'myfunc'
}

func ExampleClusterClient_FunctionList() {
	client := getExampleGlideClusterClient()

	// Load a function first
	_, err := client.FunctionLoad(libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	query := models.FunctionListQuery{
		LibraryName: "mylib",
		WithCode:    true,
	}

	libs, err := client.FunctionList(query)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Printf("There are %d libraries loaded.\n", len(libs))
	for i, lib := range libs {
		fmt.Printf("%d) Library name '%s', on engine %s, with %d functions\n", i+1, lib.Name, lib.Engine, len(lib.Functions))
		for j, fn := range lib.Functions {
			fmt.Printf("   %d) function '%s'\n", j+1, fn.Name)
		}
	}
	// Output:
	// There are 1 libraries loaded.
	// 1) Library name 'mylib', on engine LUA, with 1 functions
	//    1) function 'myfunc'
}

func ExampleClusterClient_FunctionListWithRoute() {
	client := getExampleGlideClusterClient()

	// Load a function first
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// List functions with route
	query := models.FunctionListQuery{
		WithCode: true,
	}
	result, err := client.FunctionListWithRoute(query, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Print results for each node
	for _, libs := range result.MultiValue() {
		fmt.Println("Example Node:")
		for _, lib := range libs {
			fmt.Printf("  Library: %s\n", lib.Name)
			fmt.Printf("  Engine: %s\n", lib.Engine)
			fmt.Printf("  Functions: %d\n", len(lib.Functions))
		}
		break
	}

	// Output:
	// Example Node:
	//   Library: mylib
	//   Engine: LUA
	//   Functions: 1
}
