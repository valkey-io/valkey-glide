// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"strings"
	"sync"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/errors"
)

func (suite *GlideTestSuite) TestStandaloneConnect() {
	config := api.NewGlideClientConfiguration().
		WithAddress(&suite.standaloneHosts[0])
	client, err := api.NewGlideClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect() {
	config := api.NewGlideClusterClientConfiguration()
	for _, host := range suite.clusterHosts {
		config.WithAddress(&host)
	}

	client, err := api.NewGlideClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestClusterConnect_singlePort() {
	config := api.NewGlideClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0])

	client, err := api.NewGlideClusterClient(config)

	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)

	client.Close()
}

func (suite *GlideTestSuite) TestConnectWithInvalidAddress() {
	config := api.NewGlideClientConfiguration().
		WithAddress(&api.NodeAddress{Host: "invalid-host"})
	client, err := api.NewGlideClient(config)

	assert.Nil(suite.T(), client)
	assert.NotNil(suite.T(), err)
	assert.IsType(suite.T(), &errors.ConnectionError{}, err)
}

func (suite *GlideTestSuite) TestConnectionTimeout() {
	suite.runWithTimeoutClients(func(client api.BaseClient) {
		backoffStrategy := api.NewBackoffStrategy(2, 100, 1)
		_, clusterMode := client.(api.GlideClusterClientCommands)

		// Runnable for long-running DEBUG SLEEP command
		debugSleepTask := func() {
			defer func() {
				if r := recover(); r != nil {
					suite.T().Errorf("Recovered in debugSleepTask: %v", r)
				}
			}()
			if clusterClient, ok := client.(api.GlideClusterClientCommands); ok {
				_, err := clusterClient.CustomCommandWithRoute([]string{"DEBUG", "sleep", "7"}, config.AllNodes)
				if err != nil {
					suite.T().Errorf("Error during DEBUG SLEEP command: %v", err)
				}
			} else if glideClient, ok := client.(api.GlideClientCommands); ok {
				_, err := glideClient.CustomCommand([]string{"DEBUG", "sleep", "7"})
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
				_, err = suite.createConnectionTimeoutClusterClient(100, 250)
			} else {
				_, err = suite.createConnectionTimeoutClient(100, 250, backoffStrategy)
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
			var timeoutClient api.BaseClient
			var err error
			if clusterMode {
				timeoutClient, err = suite.createConnectionTimeoutClusterClient(10000, 250)
			} else {
				timeoutClient, err = suite.createConnectionTimeoutClient(10000, 250, backoffStrategy)
			}
			assert.NoError(suite.T(), err)
			if timeoutClient != nil {
				defer timeoutClient.Close()
				result, err := timeoutClient.Set("key", "value")
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
