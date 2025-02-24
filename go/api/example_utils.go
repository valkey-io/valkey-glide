// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"flag"
	"fmt"
	"strconv"
	"strings"
	"sync"
)

var clusterNodes = flag.String("clusternodes", "", "AddressNodes for running Valkey/Redis cluster nodes")

var (
	clusterClient *GlideClusterClient
	clusterOnce   sync.Once
)

var (
	standaloneClient *GlideClient
	standaloneOnce   sync.Once
)

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
		addresses := parseHosts(*clusterNodes)
		config := NewGlideClusterClientConfiguration().
			WithAddress(&addresses[0]).
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

func parseHosts(addresses string) []NodeAddress {
	var result []NodeAddress

	addressList := strings.Split(addresses, ",")
	for _, address := range addressList {
		parts := strings.Split(address, ":")
		port, err := strconv.Atoi(parts[1])
		if err != nil {
			fmt.Printf("Failed to parse port from string %s: %s", parts[1], err.Error())
			continue
		}

		result = append(result, NodeAddress{Host: parts[0], Port: port})
	}
	return result
}
