// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"

	"github.com/google/uuid"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/constants"
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
	client := getExampleClient()

	result, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

func ExampleClusterClient_FunctionLoad() {
	client := getExampleClusterClient()

	result, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

func ExampleClusterClient_FunctionLoadWithRoute() {
	client := getExampleClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionLoadWithRoute(context.Background(), libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// mylib
}

// FunctionFlush Examples
func ExampleClient_FunctionFlush() {
	client := getExampleClient()

	result, err := client.FunctionFlush(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlush() {
	client := getExampleClusterClient()

	result, err := client.FunctionFlush(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushWithRoute() {
	client := getExampleClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionFlushWithRoute(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClient_FunctionFlushSync() {
	client := getExampleClient()

	result, err := client.FunctionFlushSync(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushSync() {
	client := getExampleClient()

	result, err := client.FunctionFlushSync(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushSyncWithRoute() {
	client := getExampleClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionFlushSyncWithRoute(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClient_FunctionFlushAsync() {
	client := getExampleClient()

	result, err := client.FunctionFlushAsync(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionFlushAsync() {
	client := getExampleClusterClient()

	result, err := client.FunctionFlushAsync(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)
}

func ExampleClusterClient_FunctionFlushAsyncWithRoute() {
	client := getExampleClusterClient()

	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	result, err := client.FunctionFlushAsyncWithRoute(context.Background(), opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

// FCall Examples
func ExampleClient_FCall() {
	client := getExampleClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCall(context.Background(), "myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleClusterClient_FCall() {
	client := getExampleClusterClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCall(context.Background(), "myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleClusterClient_FCallWithRoute() {
	client := getExampleClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(context.Background(), libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallWithRoute(context.Background(), "myfunc", opts)
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
	client := getExampleClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallWithKeysAndArgs(context.Background(), "myfunc", []string{key1, key2}, []string{"3", "4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleClusterClient_FCallWithKeysAndArgs() {
	client := getExampleClusterClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallWithKeysAndArgs(context.Background(), "myfunc", []string{key1, key2}, []string{"3", "4"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleClusterClient_FCallWithArgs() {
	client := getExampleClusterClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallWithArgs(context.Background(), "myfunc", []string{"1", "2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result.SingleValue())

	// Output:
	// 1
}

func ExampleClusterClient_FCallWithArgsWithRoute() {
	client := getExampleClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(context.Background(), libraryCodeWithArgs, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallWithArgsWithRoute(context.Background(), "myfunc", []string{"1", "2"}, opts)
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
	client := getExampleClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCallReadOnly(context.Background(), "myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleClusterClient_FCallReadOnly() {
	client := getExampleClusterClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	fcallResult, err := client.FCallReadOnly(context.Background(), "myfunc")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(fcallResult)

	// Output:
	// 42
}

func ExampleClusterClient_FCallReadOnlyWithRoute() {
	client := getExampleClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(context.Background(), libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallReadOnlyWithRoute(context.Background(), "myfunc", opts)
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
	client := getExampleClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallReadOnlyWithKeysAndArgs(
		context.Background(),
		"myfunc",
		[]string{key1, key2},
		[]string{"3", "4"},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleClusterClient_FCallReadOnlyWithKeysAndArgs() {
	client := getExampleClusterClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	key1 := "{testKey}-" + uuid.New().String()
	key2 := "{testKey}-" + uuid.New().String()
	result, err := client.FCallReadOnlyWithKeysAndArgs(
		context.Background(),
		"myfunc",
		[]string{key1, key2},
		[]string{"3", "4"},
	)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// 3
}

func ExampleClusterClient_FCallReadOnlyWithArgs() {
	client := getExampleClusterClient()

	// Load function
	_, err := client.FunctionLoad(context.Background(), libraryCodeWithArgs, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallReadOnlyWithArgs(context.Background(), "myfunc", []string{"1", "2"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result.SingleValue())

	// Output:
	// 1
}

func ExampleClusterClient_FCallReadOnlyWithArgsWithRoute() {
	client := getExampleClusterClient()

	// Load function
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(context.Background(), libraryCodeWithArgs, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	// Call function
	result, err := client.FCallReadOnlyWithArgsWithRoute(context.Background(), "myfunc", []string{"1", "2"}, opts)
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
	client := getExampleClient()

	// Load a function first
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Get function statistics
	stats, err := client.FunctionStats(context.Background())
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
	client := getExampleClusterClient()

	// Load a function first
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Get function statistics
	stats, err := client.FunctionStats(context.Background())
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
	client := getExampleClusterClient()

	// Load a function first
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(context.Background(), libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Get function statistics with route
	stats, err := client.FunctionStatsWithRoute(context.Background(), opts)
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
	client := getExampleClient()

	// Load a function first
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Delete function
	result, err := client.FunctionDelete(context.Background(), "mylib")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionDelete() {
	client := getExampleClusterClient()

	// Load a function first
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Delete function
	result, err := client.FunctionDelete(context.Background(), "mylib")
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClusterClient_FunctionDeleteWithRoute() {
	client := getExampleClusterClient()

	// Load a function first
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(context.Background(), libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Delete function with route
	result, err := client.FunctionDeleteWithRoute(context.Background(), "mylib", opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}

	fmt.Println(result)

	// Output:
	// OK
}

func ExampleClient_FunctionKill() {
	client := getExampleClient()

	// Try to kill when no function is running
	_, err := client.FunctionKill(context.Background())
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClusterClient_FunctionKill() {
	client := getExampleClusterClient()

	// Try to kill when no function is running
	_, err := client.FunctionKill(context.Background())
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClusterClient_FunctionKillWithRoute() {
	client := getExampleClusterClient()

	// Try to kill with route when no function is running
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionKillWithRoute(context.Background(), opts)
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClient_FunctionList() {
	client := getExampleClient()

	// Load a function first
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	query := models.FunctionListQuery{
		LibraryName: "mylib",
		WithCode:    true,
	}

	libs, err := client.FunctionList(context.Background(), query)
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
	client := getExampleClusterClient()

	// Load a function first
	_, err := client.FunctionLoad(context.Background(), libraryCode, true)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	query := models.FunctionListQuery{
		LibraryName: "mylib",
		WithCode:    true,
	}

	libs, err := client.FunctionList(context.Background(), query)
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
	client := getExampleClusterClient()

	// Load a function first
	route := config.Route(config.AllPrimaries)
	opts := options.RouteOption{
		Route: route,
	}
	_, err := client.FunctionLoadWithRoute(context.Background(), libraryCode, true, opts)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// List functions with route
	query := models.FunctionListQuery{
		WithCode: true,
	}
	result, err := client.FunctionListWithRoute(context.Background(), query, opts)
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

func ExampleClient_FunctionDump() {
	client := getExampleClient()

	// Call FunctionDump to get the serialized payload of all loaded libraries
	dump, _ := client.FunctionDump(context.Background())
	if len(dump) > 0 {
		fmt.Println("Function dump got a payload")
	}

	// Output:
	// Function dump got a payload
}

func ExampleClusterClient_FunctionDump() {
	client := getExampleClusterClient()

	// Call FunctionDump to get the serialized payload of all loaded libraries
	dump, _ := client.FunctionDump(context.Background())
	if len(dump) > 0 {
		fmt.Println("Function dump got a payload")
	}

	// Output:
	// Function dump got a payload
}

func ExampleClusterClient_FunctionDumpWithRoute() {
	client := getExampleClusterClient()

	// Call FunctionDumpWithRoute to get the serialized payload of all loaded libraries with a route
	dump, _ := client.FunctionDumpWithRoute(context.Background(), config.RandomRoute)
	if len(dump.SingleValue()) > 0 {
		fmt.Println("Function dump got a payload")
	}

	// Output:
	// Function dump got a payload
}

func ExampleClient_FunctionRestore() {
	client := getExampleClient()

	// Attempt to restore with invalid dump data
	invalidDump := "invalid_dump_data"
	_, err := client.FunctionRestore(context.Background(), invalidDump)
	if err != nil {
		fmt.Println("Error:", err.Error())
	}

	// Output:
	// Error: An error was signalled by the server: - ResponseError: DUMP payload version or checksum are wrong
}

func ExampleClusterClient_FunctionRestore() {
	client := getExampleClusterClient()

	// Attempt to restore with invalid dump data
	invalidDump := "invalid_dump_data"
	_, err := client.FunctionRestore(context.Background(), invalidDump)
	if err != nil {
		fmt.Println("Error:", err.Error())
	}

	// Output:
	// Error: An error was signalled by the server: - ResponseError: DUMP payload version or checksum are wrong
}

func ExampleClusterClient_FunctionRestoreWithRoute() {
	client := getExampleClusterClient()

	// Attempt to restore with invalid dump data and route
	invalidDump := "invalid_dump_data"
	route := config.RandomRoute
	_, err := client.FunctionRestoreWithRoute(context.Background(), invalidDump, route)
	if err != nil {
		fmt.Println("Error:", err.Error())
	}

	// Output:
	// Error: An error was signalled by the server: - ResponseError: DUMP payload version or checksum are wrong
}

func ExampleClient_FunctionRestoreWithPolicy() {
	client := getExampleClient()

	// Attempt to restore with invalid dump data and policy
	invalidDump := "invalid_dump_data"
	_, err := client.FunctionRestoreWithPolicy(context.Background(), invalidDump, constants.FlushPolicy)
	if err != nil {
		fmt.Println("Error:", err.Error())
	}

	// Output:
	// Error: An error was signalled by the server: - ResponseError: DUMP payload version or checksum are wrong
}

func ExampleClusterClient_FunctionRestoreWithPolicy() {
	client := getExampleClusterClient()

	// Attempt to restore with invalid dump data and policy
	invalidDump := "invalid_dump_data"
	_, err := client.FunctionRestoreWithPolicy(context.Background(), invalidDump, constants.FlushPolicy)
	if err != nil {
		fmt.Println("Error:", err.Error())
	}

	// Output:
	// Error: An error was signalled by the server: - ResponseError: DUMP payload version or checksum are wrong
}

func ExampleClusterClient_FunctionRestoreWithPolicyWithRoute() {
	client := getExampleClusterClient()

	// Attempt to restore with invalid dump data, policy and route
	invalidDump := "invalid_dump_data"
	route := config.RandomRoute
	_, err := client.FunctionRestoreWithPolicyWithRoute(context.Background(), invalidDump, constants.FlushPolicy, route)
	if err != nil {
		fmt.Println("Error:", err.Error())
	}

	// Output:
	// Error: An error was signalled by the server: - ResponseError: DUMP payload version or checksum are wrong
}

func ExampleClient_InvokeScript() {
	client := getExampleClient()

	// Create a simple Lua script that returns a string
	script := options.NewScript("return 'Hello from Lua'")

	// Execute the script
	result, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)

	// Output:
	// Hello from Lua
}

func ExampleClusterClient_InvokeScript() {
	client := getExampleClusterClient()

	// Create a simple Lua script that returns a number
	script := options.NewScript("return 123")

	// Execute the script
	result, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)

	// Output:
	// 123
}

func ExampleClient_InvokeScriptWithOptions() {
	client := getExampleClient()

	// Create a Lua script that uses keys and arguments
	scriptText := `
		local key = KEYS[1]
		local value = ARGV[1]
		redis.call('SET', key, value)
		return redis.call('GET', key)
	`
	script := options.NewScript(scriptText)

	// Create a unique key for testing
	testKey := "test-key-" + uuid.New().String()

	// Set up script options with keys and arguments
	scriptOptions := options.NewScriptOptions().
		WithKeys([]string{testKey}).
		WithArgs([]string{"Hello World"})

	// Execute the script with options
	result, err := client.InvokeScriptWithOptions(context.Background(), *script, scriptOptions)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)

	// Output:
	// Hello World
}

func ExampleClusterClient_InvokeScriptWithOptions() {
	client := getExampleClusterClient()

	// Create a Lua script that performs calculations with arguments
	scriptText := `
		local a = tonumber(ARGV[1])
		local b = tonumber(ARGV[2])
		return a + b
	`
	script := options.NewScript(scriptText)

	// Set up script options with arguments
	scriptOptions := options.NewScriptOptions().
		WithArgs([]string{"10", "20"})

	// Execute the script with options
	result, err := client.InvokeScriptWithOptions(context.Background(), *script, scriptOptions)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(result)

	// Output:
	// 30
}

func ExampleClusterClient_InvokeScriptWithClusterOptions() {
	client := getExampleClusterClient()

	// Create a Lua script.
	scriptText := "return 'Hello'"

	script := options.NewScript(scriptText)

	// Set up cluster script options
	clusterScriptOptions := options.NewClusterScriptOptions()

	// Set the route
	clusterScriptOptions.Route = config.AllPrimaries

	// Execute the script with cluster options
	result, err := client.InvokeScriptWithClusterOptions(context.Background(), *script, clusterScriptOptions)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Print the result. The result contains response from multiple nodes.
	// We are checking and printing the response from only one node below.
	for _, value := range result.MultiValue() {
		if value != nil && value.(string) == "Hello" {
			fmt.Println(value)
			break
		}
	}

	// Output:
	// Hello
}

func ExampleClient_ScriptExists() {
	client := getExampleClient()

	// Invoke a script
	script := options.NewScript("return 'Hello World!'")
	client.InvokeScript(context.Background(), *script)

	response, err := client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(response)

	// Cleanup
	script.Close()

	// Output: [true]
}

func ExampleClusterClient_ScriptExists() {
	client := getExampleClusterClient()

	// Invoke a script
	script := options.NewScript("return 'Hello World!'")
	client.InvokeScript(context.Background(), *script)

	response, err := client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(response)

	// Cleanup
	script.Close()

	// Output: [true]
}

func ExampleClusterClient_ScriptExistsWithRoute() {
	client := getExampleClusterClient()
	route := options.RouteOption{Route: config.NewSlotKeyRoute(config.SlotTypePrimary, "1")}

	// Invoke a script
	script := options.NewScript("return 'Hello World!'")
	client.InvokeScriptWithRoute(context.Background(), *script, route)

	response, err := client.ScriptExistsWithRoute(context.Background(), []string{script.GetHash()}, route)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println(response)

	// Cleanup
	script.Close()

	// Output: [true]
}

func ExampleClient_ScriptFlush() {
	client := getExampleClient()

	// First, load a script
	script := options.NewScript("return 'Hello World!'")
	_, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Verify script exists
	exists, err := client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists before flush:", exists[0])

	// Flush all scripts
	result, err := client.ScriptFlush(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Flush result:", result)

	// Verify script no longer exists
	exists, err = client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists after flush:", exists[0])

	// Cleanup
	script.Close()

	// Output:
	// Script exists before flush: true
	// Flush result: OK
	// Script exists after flush: false
}

func ExampleClient_ScriptFlushWithMode() {
	client := getExampleClient()

	// First, load a script
	script := options.NewScript("return 'Hello World!'")
	_, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Verify script exists
	exists, err := client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists before flush:", exists[0])

	// Flush all scripts with ASYNC mode
	result, err := client.ScriptFlushWithMode(context.Background(), options.ASYNC)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Flush result:", result)

	// Verify script no longer exists
	exists, err = client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists after flush:", exists[0])

	// Cleanup
	script.Close()

	// Output:
	// Script exists before flush: true
	// Flush result: OK
	// Script exists after flush: false
}

func ExampleClusterClient_ScriptFlush() {
	client := getExampleClusterClient()

	// First, load a script
	script := options.NewScript("return 'Hello World!'")
	_, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Verify script exists
	exists, err := client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists before flush:", exists[0])

	// Flush all scripts
	result, err := client.ScriptFlush(context.Background())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Flush result:", result)

	// Verify script no longer exists
	exists, err = client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists after flush:", exists[0])

	// Cleanup
	script.Close()

	// Output:
	// Script exists before flush: true
	// Flush result: OK
	// Script exists after flush: false
}

func ExampleClusterClient_ScriptFlushWithMode() {
	client := getExampleClusterClient()

	// First, load a script
	script := options.NewScript("return 'Hello World!'")
	_, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Verify script exists
	exists, err := client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists before flush:", exists[0])

	// Flush all scripts with ASYNC mode
	result, err := client.ScriptFlushWithMode(context.Background(), options.ASYNC)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Flush result:", result)

	// Verify script no longer exists
	exists, err = client.ScriptExists(context.Background(), []string{script.GetHash()})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists after flush:", exists[0])

	// Cleanup
	script.Close()

	// Output:
	// Script exists before flush: true
	// Flush result: OK
	// Script exists after flush: false
}

func ExampleClusterClient_ScriptFlushWithOptions() {
	client := getExampleClusterClient()
	route := options.RouteOption{Route: config.AllPrimaries}

	// First, load a script on all primaries
	script := options.NewScript("return 'Hello World!'")
	_, err := client.InvokeScriptWithRoute(context.Background(), *script, route)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Verify script exists
	exists, err := client.ScriptExistsWithRoute(context.Background(), []string{script.GetHash()}, route)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists before flush:", exists[0])

	// Flush all scripts on all primaries with ASYNC mode
	scriptFlushOptions := options.NewScriptFlushOptions().WithMode(options.ASYNC).WithRoute(route)
	result, err := client.ScriptFlushWithOptions(context.Background(), scriptFlushOptions)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Flush result:", result)

	// Verify script no longer exists
	exists, err = client.ScriptExistsWithRoute(context.Background(), []string{script.GetHash()}, route)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}
	fmt.Println("Script exists after flush:", exists[0])

	// Cleanup
	script.Close()

	// Output:
	// Script exists before flush: true
	// Flush result: OK
	// Script exists after flush: false
}

func ExampleClient_ScriptKill() {
	client := getExampleClient()

	// Try to kill scripts when no scripts are running
	_, err := client.ScriptKill(context.Background())
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClusterClient_ScriptKill_withoutRoute() {
	client := getExampleClusterClient()

	// Try to kill scripts when no scripts are running
	_, err := client.ScriptKill(context.Background())
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

func ExampleClusterClient_ScriptKill_withRoute() {
	key := "{randomkey}1"
	client := getExampleClusterClient()

	// Create a route with our specified key
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, key),
	}

	// Try to kill scripts when no scripts are running
	_, err := client.ScriptKillWithRoute(context.Background(), route)
	if err != nil {
		fmt.Println("Expected error:", err)
	}

	// Output:
	// Expected error: An error was signalled by the server: - NotBusy: No scripts in execution right now.
}

// ScriptShow Examples
func ExampleClient_ScriptShow() {
	client := getExampleClient()

	// First, create and invoke a script
	scriptText := "return 'Hello, World!'"
	script := options.NewScript(scriptText)
	_, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Now show the script source using ScriptShow
	scriptSource, err := client.ScriptShow(context.Background(), script.GetHash())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(scriptSource)

	// Output:
	// return 'Hello, World!'
}

func ExampleClusterClient_ScriptShow() {
	client := getExampleClusterClient()

	// First, create and invoke a script
	scriptText := "return 'Hello World'"
	script := options.NewScript(scriptText)
	_, err := client.InvokeScript(context.Background(), *script)
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	// Now show the script source using ScriptShow
	scriptSource, err := client.ScriptShow(context.Background(), script.GetHash())
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
		return
	}

	fmt.Println(scriptSource)

	// Output:
	// return 'Hello World'
}
