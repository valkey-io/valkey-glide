// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"fmt"
	"time"

	config2 "github.com/valkey-io/valkey-glide/go/v2/config"
)

func ExampleNewClient() {
	config := config2.NewClientConfiguration().
		WithAddress(&getStandaloneAddresses()[0]).
		WithUseTLS(false).
		WithReconnectStrategy(config2.NewBackoffStrategy(5, 1000, 2)).
		WithDatabaseId(1)
	client, err := NewClient(config)
	if err != nil {
		fmt.Println("Failed to create a client and connect: ", err)
	}
	fmt.Printf("Client created and connected: %T", client)

	// Output:
	// Client created and connected: *glide.Client
}

func ExampleNewClusterClient() {
	config := config2.NewClusterClientConfiguration().
		WithAddress(&getClusterAddresses()[0]).
		WithRequestTimeout(5 * time.Second).
		WithUseTLS(false)
	client, err := NewClusterClient(context.Background(), config)
	if err != nil {
		fmt.Println("Failed to create a client and connect: ", err)
	}
	fmt.Printf("Client created and connected: %T", client)

	// Output:
	// Client created and connected: *glide.ClusterClient
}
