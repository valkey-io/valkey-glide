// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"flag"
	"fmt"
	"strconv"
	"strings"
	"sync"

	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

var (
	clusterNodes   = flag.String("clusternodes", "", "AddressNodes for running Valkey/Redis cluster nodes")
	standaloneNode = flag.String("standalonenode", "", "AddressNode for running Valkey/Redis standalone node")
)

var (
	clusterClient       *GlideClusterClient
	clusterOnce         sync.Once
	clusterAddresses    []NodeAddress
	standaloneClient    *GlideClient
	standaloneOnce      sync.Once
	standaloneAddresses []NodeAddress
	initOnce            sync.Once
)

func initFlags() {
	// Parse flags only once after the test framework has had a chance to set the flags.
	// This is necessary because the test framework sets the flags after the init function is called.
	initOnce.Do(func() {
		flag.Parse()
		standaloneAddresses = parseHosts(*standaloneNode)
		clusterAddresses = parseHosts(*clusterNodes)
	})
}

func getStandaloneAddresses() []NodeAddress {
	initFlags()
	return standaloneAddresses
}

func getClusterAddresses() []NodeAddress {
	initFlags()
	return clusterAddresses
}

// getExampleGlideClient returns a GlideClient instance for testing purposes.
// This function is used in the examples of the GlideClient methods.
func getExampleGlideClient() *GlideClient {
	standaloneOnce.Do(func() {
		initFlags()
		config := NewGlideClientConfiguration().
			WithAddress(&standaloneAddresses[0])

		client, err := NewGlideClient(config)
		if err != nil {
			fmt.Println("error connecting to server: ", err)
		}

		standaloneClient = client.(*GlideClient)
	})

	// Flush the database before each test to ensure a clean state.
	_, err := standaloneClient.FlushAllWithOptions(options.SYNC)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return standaloneClient
}

func getExampleGlideClusterClient() *GlideClusterClient {
	clusterOnce.Do(func() {
		initFlags()
		config := NewGlideClusterClientConfiguration().
			WithAddress(&clusterAddresses[0]).
			WithRequestTimeout(5000)

		client, err := NewGlideClusterClient(config)
		if err != nil {
			fmt.Println("error connecting to server: ", err)
		}

		clusterClient = client.(*GlideClusterClient)
	})

	// Flush the database before each test to ensure a clean state.
	mode := options.SYNC
	_, err := clusterClient.FlushAllWithOptions(
		options.FlushClusterOptions{FlushMode: &mode, RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return clusterClient
}

func parseHosts(addresses string) []NodeAddress {
	var result []NodeAddress

	if addresses == "" {
		result = append(result, *new(NodeAddress))
	} else {
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
	}
	return result
}
