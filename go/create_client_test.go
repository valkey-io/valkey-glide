// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"fmt"
	"time"

	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

func ExampleNewClient() {
	clientConf := config.NewClientConfiguration().
		WithAddress(&getStandaloneAddresses()[0]).
		WithUseTLS(false).
		WithReconnectStrategy(config.NewBackoffStrategy(5, 1000, 2)).
		WithDatabaseId(1).
		WithSubscriptionConfig(
			config.NewStandaloneSubscriptionConfig().
				WithSubscription(config.PatternChannelMode, "news.*").
				WithCallback(func(message *models.PubSubMessage, ctx any) {
					fmt.Printf("Received message on '%s': %s", message.Channel, message.Message)
				}, nil),
		)
	client, err := NewClient(clientConf)
	if err != nil {
		fmt.Println("Failed to create a client and connect: ", err)
	}
	fmt.Printf("Client created and connected: %T", client)

	// Output:
	// Client created and connected: *glide.Client
}

func ExampleNewClusterClient() {
	clientConf := config.NewClusterClientConfiguration().
		WithAddress(&getClusterAddresses()[0]).
		WithRequestTimeout(5 * time.Second).
		WithUseTLS(false).
		WithSubscriptionConfig(
			config.NewClusterSubscriptionConfig().
				WithSubscription(config.PatternClusterChannelMode, "news.*").
				WithCallback(func(message *models.PubSubMessage, ctx any) {
					fmt.Printf("Received message on '%s': %s", message.Channel, message.Message)
				}, nil),
		)
	client, err := NewClusterClient(clientConf)
	if err != nil {
		fmt.Println("Failed to create a client and connect: ", err)
	}
	fmt.Printf("Client created and connected: %T", client)

	// Output:
	// Client created and connected: *glide.ClusterClient
}

func ExampleNewClusterClient_withDatabaseId() {
	// This WithDatabaseId for cluster requires Valkey 9.0+
	clientConf := config.NewClusterClientConfiguration().
		WithAddress(&getClusterAddresses()[0]).
		WithRequestTimeout(5 * time.Second).
		WithUseTLS(false).
		WithDatabaseId(1).
		WithSubscriptionConfig(
			config.NewClusterSubscriptionConfig().
				WithSubscription(config.PatternClusterChannelMode, "news.*").
				WithCallback(func(message *models.PubSubMessage, ctx any) {
					fmt.Printf("Received message on '%s': %s", message.Channel, message.Message)
				}, nil),
		)
	client, err := NewClusterClient(clientConf)
	if err != nil {
		fmt.Println("Failed to create a client and connect: ", err)
	}
	fmt.Printf("Client created and connected: %T", client)

	// Output:
	// Client created and connected: *glide.ClusterClient
}
