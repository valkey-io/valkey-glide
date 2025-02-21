// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"flag"
	"fmt"
	"sync"
)

var clusterNodes = flag.String("clusternodes", "", "AddressNodes for running Valkey/Redis cluster nodes")
var standaloneNode = flag.String("standalonenode", "", "Address for running Valkey/Redis standalone node")

var clusterClient *GlideClusterClient
var clusterOnce sync.Once

var standaloneClient *GlideClient
var standaloneOnce sync.Once

var initOnce sync.Once

// getExampleGlideClient returns a GlideClient instance for testing purposes.
// This function is used in the examples of the GlideClient methods.
func getExampleGlideClient() *GlideClient {
	// Parse flags only once after the test framework has had a chance to set the flags.
	// This is necessary because the test framework sets the flags after the init function is called.
	initOnce.Do(func() {
		flag.Parse()
	})

	standaloneOnce.Do(func() {
		config := NewGlideClientConfiguration().
			WithAddress(new(NodeAddress)) // use default address

		client, err := NewGlideClient(config)
		if err != nil {
			fmt.Println("error connecting to database: ", err)
		}

		standaloneClient = client.(*GlideClient)
	})

	// Flush the database before each test to ensure a clean state.
	_, err := standaloneClient.CustomCommand([]string{"FLUSHALL"}) // todo: replace with client.FlushAll() when implemented
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return standaloneClient
}

func getExampleGlideClusterClient() *GlideClusterClient {
	clusterOnce.Do(func() {
		config := NewGlideClusterClientConfiguration().
			WithAddress(&NodeAddress{Host: "localhost", Port: 7001}).
			WithRequestTimeout(5000)

		client, err := NewGlideClusterClient(config)
		if err != nil {
			fmt.Println("error connecting to database: ", err)
		}

		clusterClient = client.(*GlideClusterClient)
	})

	// Flush the database before each test to ensure a clean state.
	_, err := clusterClient.CustomCommand([]string{"FLUSHALL"}) // todo: replace with client.FlushAll() when implemented
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return clusterClient
}
