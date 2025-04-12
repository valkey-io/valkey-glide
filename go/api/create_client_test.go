// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package api

import (
	"fmt"
)

func ExampleNewGlideClient() {
	config := NewGlideClientConfiguration().
		WithAddress(&getStandaloneAddresses()[0]).
		WithUseTLS(false).
		WithReconnectStrategy(NewBackoffStrategy(5, 1000, 2)).
		WithDatabaseId(1)
	client, err := NewGlideClient(config)
	if err != nil {
		fmt.Println("Failed to create a client and connect: ", err)
	}
	fmt.Printf("Client created and connected: %T", client)

	// Output:
	// Client created and connected: *api.GlideClient
}

func ExampleNewGlideClusterClient() {
	config := NewGlideClusterClientConfiguration().
		WithAddress(&getClusterAddresses()[0]).
		WithRequestTimeout(5000).
		WithUseTLS(false)
	client, err := NewGlideClusterClient(config)
	if err != nil {
		fmt.Println("Failed to create a client and connect: ", err)
	}
	fmt.Printf("Client created and connected: %T", client)

	// Output:
	// Client created and connected: *api.GlideClusterClient
}
