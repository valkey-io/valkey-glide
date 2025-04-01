// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/integTest"
)

var libraryCode = integTest.GenerateLuaLibCode("mylib", map[string]string{"myfunc": "return args[1]"}, true)

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
