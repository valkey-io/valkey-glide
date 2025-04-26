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
	clusterClients      []*GlideClusterClient
	clusterOnce         sync.Once
	clusterSubOnce      sync.Once
	clusterAddresses    []NodeAddress
	standaloneClients   []*GlideClient
	standaloneOnce      sync.Once
	standaloneSubOnce   sync.Once
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
	})
	config := NewGlideClientConfiguration().
		WithAddress(&standaloneAddresses[0])

	client, err := NewGlideClient(config)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	thisClient := client.(*GlideClient)
	standaloneClients = append(standaloneClients, thisClient)

	// Flush the database before each test to ensure a clean state.
	_, err = thisClient.FlushAllWithOptions(options.SYNC)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return thisClient
}

func getExampleGlideClusterClient() *GlideClusterClient {
	clusterOnce.Do(func() {
		initFlags()
	})
	cConfig := NewGlideClusterClientConfiguration().
		WithAddress(&clusterAddresses[0]).
		WithRequestTimeout(5000)

	client, err := NewGlideClusterClient(cConfig)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	thisClient := client.(*GlideClusterClient)
	clusterClients = append(clusterClients, thisClient)

	// Flush the database before each test to ensure a clean state.
	mode := options.SYNC
	_, err = thisClient.FlushAllWithOptions(
		options.FlushClusterOptions{FlushMode: &mode, RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return thisClient
}

func getExampleGlideClientWithSubscription(mode PubSubChannelMode, channelOrPattern string) *GlideClient {
	standaloneSubOnce.Do(func() {
		initFlags()
	})
	sConfig := NewStandaloneSubscriptionConfig().
		WithSubscription(mode, channelOrPattern)

	config := NewGlideClientConfiguration().
		WithAddress(&standaloneAddresses[0]).
		WithSubscriptionConfig(sConfig)

	client, err := NewGlideClient(config)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	thisClient := client.(*GlideClient)
	standaloneClients = append(standaloneClients, thisClient)

	// Flush the database before each test to ensure a clean state.
	_, err = thisClient.FlushAllWithOptions(options.SYNC)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return thisClient
}

func getExampleGlideClusterClientWithSubscription(mode PubSubClusterChannelMode, channelOrPattern string) *GlideClusterClient {
	clusterSubOnce.Do(func() {
		initFlags()
	})
	cConfig := NewClusterSubscriptionConfig().
		WithSubscription(mode, channelOrPattern)

	ccConfig := NewGlideClusterClientConfiguration().
		WithAddress(&clusterAddresses[0]).
		WithSubscriptionConfig(cConfig)

	client, err := NewGlideClusterClient(ccConfig)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	thisClient := client.(*GlideClusterClient)
	clusterClients = append(clusterClients, thisClient)

	// Flush the database before each test to ensure a clean state.
	syncmode := options.SYNC
	_, err = thisClient.FlushAllWithOptions(
		options.FlushClusterOptions{FlushMode: &syncmode, RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return thisClient
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

// close all clients
func closeAllClients() {
	for _, client := range standaloneClients {
		client.Close()
	}
	for _, client := range clusterClients {
		client.Close()
	}
}
