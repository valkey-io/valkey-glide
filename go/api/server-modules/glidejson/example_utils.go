// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
package glidejson

import (
	"fmt"

	"github.com/valkey-io/valkey-glide/go/api"
)

// getExampleGlideClient returns a GlideClient instance for testing purposes.
// This function is used in the examples of the GlideClient methods.
func getExampleGlideClient() *api.GlideClient {
	config := api.NewGlideClientConfiguration().
		WithAddress(new(api.NodeAddress)) // use default address

	client, err := api.NewGlideClient(config)
	if err != nil {
		fmt.Println("error connecting to database: ", err)
	}

	_, err = client.CustomCommand([]string{"FLUSHALL"}) // todo: replace with client.FlushAll() when implemented
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client.(*api.GlideClient)
}

func getExampleGlideClusterClient() *api.GlideClusterClient {
	config := api.NewGlideClusterClientConfiguration().
		WithAddress(&api.NodeAddress{Host: "localhost", Port: 7001}).
		WithRequestTimeout(5000)

	client, err := api.NewGlideClusterClient(config)
	if err != nil {
		fmt.Println("error connecting to database: ", err)
	}

	_, err = client.CustomCommand([]string{"FLUSHALL"}) // todo: replace with client.FlushAll() when implemented
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client.(*api.GlideClusterClient)
}
