// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
)

func startDedicatedValkeyServer(suite *GlideTestSuite, clusterMode bool) (string, error) {
	// Build command arguments
	args := []string{}
	args = append(args, "start")
	if clusterMode {
		args = append(args, "--cluster-mode")
	}

	args = append(args, fmt.Sprintf("-r %d", 0))

	// Execute cluster manager script
	output := runClusterManager(suite, args, false)

	return output, nil
}

func stopDedicatedValkeyServer(suite *GlideTestSuite, clusterFolder string) {
	args := []string{}
	args = append(args, "stop", "--cluster-folder", clusterFolder)

	runClusterManager(suite, args, false)
}

func createDedicatedClient(
	addresses []config.NodeAddress,
	clusterMode bool,
	lazyConnect bool,
) (interfaces.BaseClientCommands, error) {
	if clusterMode {
		cfg := config.NewClusterClientConfiguration()
		for _, addr := range addresses {
			cfg.WithAddress(&addr)
		}

		cfg.WithRequestTimeout(3 * time.Second)
		advCfg := config.NewAdvancedClusterClientConfiguration()
		advCfg.WithConnectionTimeout(3 * time.Second)
		cfg.WithAdvancedConfiguration(advCfg)
		cfg.WithLazyConnect(lazyConnect)

		return glide.NewClusterClient(cfg)
	}

	cfg := config.NewClientConfiguration()
	for _, addr := range addresses {
		cfg.WithAddress(&addr)
	}

	cfg.WithRequestTimeout(3 * time.Second)
	advCfg := config.NewAdvancedClientConfiguration()
	advCfg.WithConnectionTimeout(3 * time.Second)
	cfg.WithAdvancedConfiguration(advCfg)
	cfg.WithLazyConnect(lazyConnect)

	return glide.NewClient(cfg)
}

// getClientListOutputCount parses CLIENT LIST output and returns the number of clients
func getClientListOutputCount(output interface{}) int {
	if output == nil {
		return 0
	}

	text := output.(string)

	if text = strings.TrimSpace(text); text == "" {
		return 0
	}

	return len(strings.Split(text, "\n"))
}

// getClientCount returns the number of connected clients
func getClientCount(ctx context.Context, client interfaces.BaseClientCommands) (int, error) {
	if clusterClient, ok := client.(interfaces.GlideClusterClientCommands); ok {
		// For cluster client, execute CLIENT LIST on all nodes
		result, err := clusterClient.CustomCommandWithRoute(ctx, []string{"CLIENT", "LIST"}, config.AllNodes)
		if err != nil {
			return 0, err
		}

		// Result will be a map with node addresses as keys and CLIENT LIST output as values
		totalCount := 0
		for _, nodeOutput := range result.MultiValue() {
			totalCount += getClientListOutputCount(nodeOutput)
		}
		return totalCount, nil
	}

	// For standalone client, execute CLIENT LIST directly
	glideClient := client.(interfaces.GlideClientCommands)
	result, err := glideClient.CustomCommand(ctx, []string{"CLIENT", "LIST"})
	if err != nil {
		return 0, err
	}
	return getClientListOutputCount(result), nil
}

// getExpectedNewConnections returns the expected number of new connections when a lazy client is initialized
func getExpectedNewConnections(ctx context.Context, client interfaces.BaseClientCommands) (int, error) {
	if clusterClient, ok := client.(interfaces.GlideClusterClientCommands); ok {
		// For cluster, get node count and multiply by 2 (2 connections per node)
		result, err := clusterClient.CustomCommand(ctx, []string{"CLUSTER", "NODES"})
		if err != nil {
			return 0, err
		}

		nodesInfo := result.SingleValue().(string)

		if nodesInfo = strings.TrimSpace(nodesInfo); nodesInfo == "" {
			return 0, nil
		}

		return len(strings.Split(nodesInfo, "\n")) * 2, nil
	}

	// For standalone, always expect 1 new connection
	return 1, nil
}

func (suite *GlideTestSuite) TestStandaloneConnect() {
	config := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0])
	client, err := glide.NewClient(config)

	suite.NoError(err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect() {
	config := config.NewClusterClientConfiguration()
	for _, host := range suite.clusterHosts {
		config.WithAddress(&host)
	}

	client, err := glide.NewClusterClient(config)

	suite.NoError(err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect_singlePort() {
	config := config.NewClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0])

	client, err := glide.NewClusterClient(config)

	suite.NoError(err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestConnectWithInvalidAddress() {
	config := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{Host: "invalid-host"})
	client, err := glide.NewClient(config)

	suite.Nil(client)
	suite.Error(err)
	var connErr *glide.ConnectionError
	suite.ErrorAs(err, &connErr)
}

func (suite *GlideTestSuite) TestConnectionTimeout() {
	suite.runWithTimeoutClients(func(client interfaces.BaseClientCommands) {
		backoffStrategy := config.NewBackoffStrategy(2, 100, 1)
		_, clusterMode := client.(interfaces.GlideClusterClientCommands)

		// Runnable for long-running DEBUG SLEEP command
		debugSleepTask := func() {
			defer func() {
				if r := recover(); r != nil {
					suite.T().Errorf("Recovered in debugSleepTask: %v", r)
				}
			}()
			if clusterClient, ok := client.(interfaces.GlideClusterClientCommands); ok {
				_, err := clusterClient.CustomCommandWithRoute(
					context.Background(),
					[]string{"DEBUG", "sleep", "7"},
					config.AllNodes,
				)
				if err != nil {
					suite.T().Errorf("Error during DEBUG SLEEP command: %v", err)
				}
			} else if glideClient, ok := client.(interfaces.GlideClientCommands); ok {
				_, err := glideClient.CustomCommand(context.Background(), []string{"DEBUG", "sleep", "7"})
				if err != nil {
					suite.T().Errorf("Error during DEBUG SLEEP command: %v", err)
				}
			}
		}

		// Runnable for testing connection failure due to timeout
		failToConnectTask := func() {
			defer func() {
				if r := recover(); r != nil {
					suite.T().Errorf("Recovered in failToConnectTask: %v", r)
				}
			}()
			time.Sleep(1 * time.Second) // Wait to ensure the debug sleep command is running
			var err error
			if clusterMode {
				_, err = suite.createConnectionTimeoutClusterClient(10*time.Millisecond, 250*time.Millisecond)
			} else {
				_, err = suite.createConnectionTimeoutClient(10*time.Millisecond, 250*time.Millisecond, backoffStrategy)
			}
			assert.Error(suite.T(), err)
			assert.True(suite.T(), strings.Contains(err.Error(), "timed out"))
		}

		// Runnable for testing successful connection
		connectToClientTask := func() {
			defer func() {
				if r := recover(); r != nil {
					suite.T().Errorf("Recovered in connectToClientTask: %v", r)
				}
			}()
			time.Sleep(1 * time.Second) // Wait to ensure the debug sleep command is running
			var timeoutClient interfaces.BaseClientCommands
			var err error
			if clusterMode {
				timeoutClient, err = suite.createConnectionTimeoutClusterClient(10*time.Second, 250*time.Millisecond)
			} else {
				timeoutClient, err = suite.createConnectionTimeoutClient(10*time.Second, 250*time.Millisecond, backoffStrategy)
			}
			assert.NoError(suite.T(), err)
			if timeoutClient != nil {
				defer timeoutClient.Close()
				result, err := timeoutClient.Set(context.Background(), "key", "value")
				assert.NoError(suite.T(), err)
				assert.Equal(suite.T(), "OK", result)
			}
		}

		// Execute all tasks concurrently
		var wg sync.WaitGroup
		wg.Add(3)
		go func() {
			defer wg.Done()
			debugSleepTask()
		}()
		go func() {
			defer wg.Done()
			failToConnectTask()
		}()
		go func() {
			defer wg.Done()
			connectToClientTask()
		}()
		wg.Wait()

		// Clean up the main client
		if client != nil {
			client.Close()
		}
	})
}

func (suite *GlideTestSuite) TestLazyConnectionEstablishesOnFirstCommand() {
	// Run test for both standalone and cluster modes
	suite.runWithTimeoutClients(func(client interfaces.BaseClientCommands) {
		ctx := context.Background()
		_, isCluster := client.(interfaces.GlideClusterClientCommands)

		// Create a monitoring client (eagerly connected)
		output, err := startDedicatedValkeyServer(suite, isCluster)
		suite.NoError(err)
		clusterFolder := extractClusterFolder(suite, output)
		addresses := extractAddresses(suite, output)
		defer stopDedicatedValkeyServer(suite, clusterFolder)
		monitoringClient, err := createDedicatedClient(addresses, isCluster, false)
		suite.NoError(err)
		defer monitoringClient.Close()

		// Get initial client count
		clientsBeforeLazyInit, err := getClientCount(ctx, monitoringClient)
		suite.NoError(err)

		// Create the "lazy" client
		lazyClient, err := createDedicatedClient(addresses, isCluster, true)
		suite.NoError(err)
		defer lazyClient.Close()

		// Check count (should not change)
		clientsAfterLazyInit, err := getClientCount(ctx, monitoringClient)
		suite.NoError(err)
		suite.Equal(clientsBeforeLazyInit, clientsAfterLazyInit,
			"Lazy client should not connect before the first command")

		// Send the first command using the lazy client
		var result interface{}
		if isCluster {
			clusterClient := lazyClient.(interfaces.GlideClusterClientCommands)
			result, err = clusterClient.Ping(ctx)
		} else {
			glideClient := lazyClient.(interfaces.GlideClientCommands)
			result, err = glideClient.Ping(ctx)
		}
		suite.NoError(err)

		// Assert PING success for both modes
		suite.Equal("PONG", result)

		// Check client count after the first command
		clientsAfterFirstCommand, err := getClientCount(ctx, monitoringClient)
		suite.NoError(err)

		expectedNewConnections, err := getExpectedNewConnections(ctx, monitoringClient)
		suite.NoError(err)

		suite.Equal(clientsBeforeLazyInit+expectedNewConnections, clientsAfterFirstCommand,
			"Lazy client should establish expected number of new connections after the first command")
	})
}

func (suite *GlideTestSuite) TestTcpNoDelayConfiguration() {
	// Test TCP_NODELAY configuration for both standalone and cluster modes
	suite.runWithTimeoutClients(func(client interfaces.BaseClientCommands) {
		ctx := context.Background()
		_, isCluster := client.(interfaces.GlideClusterClientCommands)

		// Start a dedicated server
		output, err := startDedicatedValkeyServer(suite, isCluster)
		suite.NoError(err)
		clusterFolder := extractClusterFolder(suite, output)
		addresses := extractAddresses(suite, output)
		defer stopDedicatedValkeyServer(suite, clusterFolder)

		// Test with TCP_NODELAY enabled (true)
		var clientWithTcpNoDelayTrue interfaces.BaseClientCommands
		if isCluster {
			cfg := config.NewClusterClientConfiguration()
			for _, addr := range addresses {
				cfg.WithAddress(&addr)
			}
			cfg.WithAdvancedConfiguration(
				config.NewAdvancedClusterClientConfiguration().WithTcpNoDelay(true),
			)
			clientWithTcpNoDelayTrue, err = glide.NewClusterClient(cfg)
		} else {
			cfg := config.NewClientConfiguration()
			for _, addr := range addresses {
				cfg.WithAddress(&addr)
			}
			cfg.WithAdvancedConfiguration(
				config.NewAdvancedClientConfiguration().WithTcpNoDelay(true),
			)
			clientWithTcpNoDelayTrue, err = glide.NewClient(cfg)
		}
		suite.NoError(err)
		defer clientWithTcpNoDelayTrue.Close()

		// Verify client can connect and execute commands
		var result interface{}
		if isCluster {
			clusterClient := clientWithTcpNoDelayTrue.(interfaces.GlideClusterClientCommands)
			result, err = clusterClient.Ping(ctx)
		} else {
			glideClient := clientWithTcpNoDelayTrue.(interfaces.GlideClientCommands)
			result, err = glideClient.Ping(ctx)
		}
		suite.NoError(err)
		suite.Equal("PONG", result)

		// Test with TCP_NODELAY disabled (false)
		var clientWithTcpNoDelayFalse interfaces.BaseClientCommands
		if isCluster {
			cfg := config.NewClusterClientConfiguration()
			for _, addr := range addresses {
				cfg.WithAddress(&addr)
			}
			cfg.WithAdvancedConfiguration(
				config.NewAdvancedClusterClientConfiguration().WithTcpNoDelay(false),
			)
			clientWithTcpNoDelayFalse, err = glide.NewClusterClient(cfg)
		} else {
			cfg := config.NewClientConfiguration()
			for _, addr := range addresses {
				cfg.WithAddress(&addr)
			}
			cfg.WithAdvancedConfiguration(
				config.NewAdvancedClientConfiguration().WithTcpNoDelay(false),
			)
			clientWithTcpNoDelayFalse, err = glide.NewClient(cfg)
		}
		suite.NoError(err)
		defer clientWithTcpNoDelayFalse.Close()

		// Verify client can connect and execute commands
		if isCluster {
			clusterClient := clientWithTcpNoDelayFalse.(interfaces.GlideClusterClientCommands)
			result, err = clusterClient.Ping(ctx)
		} else {
			glideClient := clientWithTcpNoDelayFalse.(interfaces.GlideClientCommands)
			result, err = glideClient.Ping(ctx)
		}
		suite.NoError(err)
		suite.Equal("PONG", result)

		// Test with TCP_NODELAY not set (default behavior)
		var clientWithDefaultTcpNoDelay interfaces.BaseClientCommands
		if isCluster {
			cfg := config.NewClusterClientConfiguration()
			for _, addr := range addresses {
				cfg.WithAddress(&addr)
			}
			clientWithDefaultTcpNoDelay, err = glide.NewClusterClient(cfg)
		} else {
			cfg := config.NewClientConfiguration()
			for _, addr := range addresses {
				cfg.WithAddress(&addr)
			}
			clientWithDefaultTcpNoDelay, err = glide.NewClient(cfg)
		}
		suite.NoError(err)
		defer clientWithDefaultTcpNoDelay.Close()

		// Verify client can connect and execute commands
		if isCluster {
			clusterClient := clientWithDefaultTcpNoDelay.(interfaces.GlideClusterClientCommands)
			result, err = clusterClient.Ping(ctx)
		} else {
			glideClient := clientWithDefaultTcpNoDelay.(interfaces.GlideClientCommands)
			result, err = glideClient.Ping(ctx)
		}
		suite.NoError(err)
		suite.Equal("PONG", result)
	})
}
