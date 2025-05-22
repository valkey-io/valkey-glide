// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"flag"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

var (
	clusterNodes   = flag.String("clusternodes", "", "AddressNodes for running Valkey/Redis cluster nodes")
	standaloneNode = flag.String("standalonenode", "", "AddressNode for running Valkey/Redis standalone node")
)

var (
	clusterClients      []*ClusterClient
	clusterOnce         sync.Once
	clusterSubOnce      sync.Once
	clusterAddresses    []config.NodeAddress
	standaloneClients   []*Client
	standaloneOnce      sync.Once
	standaloneSubOnce   sync.Once
	standaloneAddresses []config.NodeAddress
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

func getStandaloneAddresses() []config.NodeAddress {
	initFlags()
	return standaloneAddresses
}

func getClusterAddresses() []config.NodeAddress {
	initFlags()
	return clusterAddresses
}

// getExampleClient returns a Client instance for testing purposes.
// This function is used in the examples of the Client methods.
func getExampleClient() *Client {
	standaloneOnce.Do(func() {
		initFlags()
	})
	config := config.NewClientConfiguration().
		WithAddress(&standaloneAddresses[0])

	client, err := NewClient(context.Background(), config)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	standaloneClients = append(standaloneClients, client)

	// Flush the database before each test to ensure a clean state.
	_, err = client.FlushAllWithOptions(context.Background(), options.SYNC)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client
}

func getExampleClusterClient() *ClusterClient {
	clusterOnce.Do(func() {
		initFlags()
	})
	cConfig := config.NewClusterClientConfiguration().
		WithAddress(&clusterAddresses[0]).
		WithRequestTimeout(5 * time.Second)

	client, err := NewClusterClient(context.Background(), cConfig)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	clusterClients = append(clusterClients, client)

	// Flush the database before each test to ensure a clean state.
	mode := options.SYNC
	_, err = client.FlushAllWithOptions(context.Background(),
		options.FlushClusterOptions{FlushMode: &mode, RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client
}

func getExampleClientWithSubscription(mode config.PubSubChannelMode, channelOrPattern string) *Client {
	standaloneSubOnce.Do(func() {
		initFlags()
	})
	sConfig := config.NewStandaloneSubscriptionConfig().
		WithSubscription(mode, channelOrPattern)

	config := config.NewClientConfiguration().
		WithAddress(&standaloneAddresses[0]).
		WithSubscriptionConfig(sConfig)

	client, err := NewClient(context.Background(), config)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	standaloneClients = append(standaloneClients, client)

	// Flush the database before each test to ensure a clean state.
	_, err = client.FlushAllWithOptions(context.Background(), options.SYNC)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client
}

func getExampleClusterClientWithSubscription(
	mode config.PubSubClusterChannelMode,
	channelOrPattern string,
) *ClusterClient {
	clusterSubOnce.Do(func() {
		initFlags()
	})
	cConfig := config.NewClusterSubscriptionConfig().
		WithSubscription(mode, channelOrPattern)

	ccConfig := config.NewClusterClientConfiguration().
		WithAddress(&clusterAddresses[0]).
		WithSubscriptionConfig(cConfig)

	client, err := NewClusterClient(context.Background(), ccConfig)
	if err != nil {
		fmt.Println("error connecting to server: ", err)
	}

	clusterClients = append(clusterClients, client)

	// Flush the database before each test to ensure a clean state.
	syncmode := options.SYNC
	_, err = client.FlushAllWithOptions(context.Background(),
		options.FlushClusterOptions{FlushMode: &syncmode, RouteOption: &options.RouteOption{Route: config.AllPrimaries}},
	)
	if err != nil {
		fmt.Println("error flushing database: ", err)
	}

	return client
}

func parseHosts(addresses string) []config.NodeAddress {
	var result []config.NodeAddress

	if addresses == "" {
		result = append(result, *new(config.NodeAddress))
	} else {
		addressList := strings.Split(addresses, ",")
		for _, address := range addressList {
			parts := strings.Split(address, ":")
			port, err := strconv.Atoi(parts[1])
			if err != nil {
				fmt.Printf("Failed to parse port from string %s: %s", parts[1], err.Error())
				continue
			}

			result = append(result, config.NodeAddress{Host: parts[0], Port: port})
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
