package api

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api/config"
)

func ExampleGlideClusterClient_CustomCommand() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function
	result, err := client.CustomCommand([]string{"ping"})
	if err != nil {
		fmt.Println("Glide example failed with an error: ", err)
	}
	fmt.Println(result.SingleValue().(string))

	// Output: PONG
}

func ExampleGlideClusterClient_CustomCommandWithRoute() {
	var client *GlideClusterClient = getExampleGlideClusterClient() // example helper function

	route := config.SimpleNodeRoute(config.RandomRoute)
	result, _ := client.CustomCommandWithRoute([]string{"ping"}, route)
	fmt.Println(result.SingleValue().(string))

	// Output: PONG
}
