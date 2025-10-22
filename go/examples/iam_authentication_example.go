// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package examples

import (
	"context"
	"fmt"

	"github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// Example demonstrating IAM authentication with Valkey GLIDE client.
//
// This example shows how to:
//   - Configure IAM authentication for ElastiCache or MemoryDB
//   - Create a client with IAM credentials
//   - Manually refresh IAM tokens

// ElastiCacheExample demonstrates connecting to ElastiCache with IAM authentication
func ElastiCacheExample() {
	// Configure IAM authentication
	iamConfig := config.NewIamAuthConfig(
		"my-elasticache-cluster",
		config.ElastiCache,
		"us-east-1",
	) // Uses default 300 second refresh interval

	// Create credentials with IAM config
	credentials, err := config.NewServerCredentialsWithIam("myIamUser", iamConfig)
	if err != nil {
		fmt.Printf("Failed to create IAM credentials: %v\n", err)
		return
	}

	// Create client configuration
	clientConfig := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: "my-cluster.cache.amazonaws.com",
			Port: 6379,
		}).
		WithCredentials(credentials).
		WithUseTLS(true) // Recommended for AWS services

	// Create and use the client
	client, err := glide.NewClient(clientConfig)
	if err != nil {
		fmt.Printf("Failed to create client: %v\n", err)
		return
	}
	defer client.Close()

	// The client will automatically refresh IAM tokens based on the refresh interval
	response, err := client.Set(context.Background(), "key", "value")
	if err != nil {
		fmt.Printf("SET failed: %v\n", err)
		return
	}
	fmt.Printf("ElastiCache SET response: %s\n", response)

	value, err := client.Get(context.Background(), "key")
	if err != nil {
		fmt.Printf("GET failed: %v\n", err)
		return
	}
	fmt.Printf("ElastiCache GET response: %s\n", value.Value())
}

// MemoryDBExample demonstrates connecting to MemoryDB with custom refresh interval
func MemoryDBExample() {
	// Configure IAM authentication with custom refresh interval
	iamConfig := config.NewIamAuthConfig(
		"my-memorydb-cluster",
		config.MemoryDB,
		"us-west-2",
	).WithRefreshIntervalSeconds(600) // Refresh every 10 minutes

	credentials, err := config.NewServerCredentialsWithIam("myIamUser", iamConfig)
	if err != nil {
		fmt.Printf("Failed to create IAM credentials: %v\n", err)
		return
	}

	clientConfig := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: "my-cluster.memorydb.amazonaws.com",
			Port: 6379,
		}).
		WithCredentials(credentials).
		WithUseTLS(true)

	client, err := glide.NewClient(clientConfig)
	if err != nil {
		fmt.Printf("Failed to create client: %v\n", err)
		return
	}
	defer client.Close()

	response, err := client.Set(context.Background(), "memorydb-key", "memorydb-value")
	if err != nil {
		fmt.Printf("SET failed: %v\n", err)
		return
	}
	fmt.Printf("MemoryDB SET response: %s\n", response)
}

// ManualRefreshExample demonstrates manually refreshing IAM token
func ManualRefreshExample() {
	iamConfig := config.NewIamAuthConfig(
		"my-cluster",
		config.ElastiCache,
		"us-east-1",
	)

	credentials, err := config.NewServerCredentialsWithIam("myIamUser", iamConfig)
	if err != nil {
		fmt.Printf("Failed to create IAM credentials: %v\n", err)
		return
	}

	clientConfig := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: "my-cluster.cache.amazonaws.com",
			Port: 6379,
		}).
		WithCredentials(credentials).
		WithUseTLS(true)

	client, err := glide.NewClient(clientConfig)
	if err != nil {
		fmt.Printf("Failed to create client: %v\n", err)
		return
	}
	defer client.Close()

	// Perform some operations
	_, err = client.Set(context.Background(), "key1", "value1")
	if err != nil {
		fmt.Printf("SET failed: %v\n", err)
		return
	}

	// Manually refresh the IAM token if needed
	// (normally this happens automatically based on refreshIntervalSeconds)
	refreshResponse, err := client.RefreshIamToken(context.Background())
	if err != nil {
		fmt.Printf("Token refresh failed: %v\n", err)
		return
	}
	fmt.Printf("Token refresh response: %s\n", refreshResponse)

	// Continue with operations using the refreshed token
	_, err = client.Set(context.Background(), "key2", "value2")
	if err != nil {
		fmt.Printf("SET failed: %v\n", err)
		return
	}
}

// ClusterExample demonstrates IAM authentication with cluster mode
func ClusterExample() {
	iamConfig := config.NewIamAuthConfig(
		"my-cluster",
		config.ElastiCache,
		"us-east-1",
	)

	credentials, err := config.NewServerCredentialsWithIam("myIamUser", iamConfig)
	if err != nil {
		fmt.Printf("Failed to create IAM credentials: %v\n", err)
		return
	}

	clusterConfig := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: "my-cluster.cache.amazonaws.com",
			Port: 6379,
		}).
		WithCredentials(credentials).
		WithUseTLS(true)

	client, err := glide.NewClusterClient(clusterConfig)
	if err != nil {
		fmt.Printf("Failed to create cluster client: %v\n", err)
		return
	}
	defer client.Close()

	response, err := client.Set(context.Background(), "cluster-key", "cluster-value")
	if err != nil {
		fmt.Printf("SET failed: %v\n", err)
		return
	}
	fmt.Printf("Cluster SET response: %s\n", response)

	// Manually refresh IAM token for cluster client
	refreshResponse, err := client.RefreshIamToken(context.Background())
	if err != nil {
		fmt.Printf("Token refresh failed: %v\n", err)
		return
	}
	fmt.Printf("Token refresh response: %s\n", refreshResponse)
}
