// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"flag"
	"os/exec"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/suite"
	"github.com/valkey-io/valkey-glide/go/api"
	"github.com/valkey-io/valkey-glide/go/api/config"
	"github.com/valkey-io/valkey-glide/go/api/options"
)

type AdvancedTestSuite struct {
	suite.Suite
	client          api.BaseClient
	standaloneHosts []api.NodeAddress
	clusterHosts    []api.NodeAddress
	tls             bool
	serverVersion   string
	clients         []api.GlideClientCommands
	clusterClients  []api.GlideClusterClientCommands
}

var (
	advancedTls             = flag.Bool("advanced-tls", false, "use TLS for connections")
	advancedStandaloneHosts = flag.String("advanced-standalone-endpoints", "", "standalone Redis endpoints")
)

func (suite *AdvancedTestSuite) runClusterManager(args []string, ignoreExitCode bool) string {
	cmd := exec.Command("python3", append([]string{"../../utils/cluster_manager.py"}, args...)...)
	output, err := cmd.CombinedOutput()
	if err != nil && !ignoreExitCode {
		suite.T().Fatalf("Failed to run cluster manager: %v\nOutput: %s", err, output)
	}
	return string(output)
}

func (suite *AdvancedTestSuite) extractAddresses(output string) []api.NodeAddress {
	lines := strings.Split(output, "\n")
	for _, line := range lines {
		if strings.HasPrefix(line, "CLUSTER_NODES=") {
			addresses := strings.TrimPrefix(line, "CLUSTER_NODES=")
			return suite.parseHosts(addresses)
		}
	}
	suite.T().Fatalf("Could not find CLUSTER_NODES in output: %s", output)
	return nil
}

func (suite *AdvancedTestSuite) parseHosts(addresses string) []api.NodeAddress {
	var hosts []api.NodeAddress
	for _, addr := range strings.Split(addresses, ",") {
		parts := strings.Split(addr, ":")
		if len(parts) != 2 {
			suite.T().Fatalf("Invalid address format: %s", addr)
		}
		port, err := strconv.Atoi(parts[1])
		if err != nil {
			suite.T().Fatalf("Invalid port number: %s", parts[1])
		}
		hosts = append(hosts, api.NodeAddress{Host: parts[0], Port: port})
	}
	return hosts
}

func (suite *AdvancedTestSuite) SetupSuite() {
	// Stop any existing clusters
	suite.runClusterManager([]string{"stop", "--prefix", "cluster"}, true)

	// Start a standalone instance if no endpoints are provided
	if *advancedStandaloneHosts == "" {
		clusterManagerOutput := suite.runClusterManager([]string{"start", "-r", "3"}, false)
		suite.standaloneHosts = suite.extractAddresses(clusterManagerOutput)
	} else {
		suite.standaloneHosts = suite.parseHosts(*advancedStandaloneHosts)
	}

	// Start a cluster instance
	clusterManagerOutput := suite.runClusterManager([]string{"start", "--cluster-mode"}, false)
	suite.clusterHosts = suite.extractAddresses(clusterManagerOutput)

	suite.tls = *advancedTls

	// Get server version
	suite.serverVersion = suite.getServerVersion()
}

func (suite *AdvancedTestSuite) getServerVersion() string {
	client := suite.defaultClient().(api.GlideClientCommands)
	defer client.Close()

	info, err := client.Info()
	assert.Nil(suite.T(), err)

	// Extract version from INFO output
	lines := strings.Split(info, "\n")
	for _, line := range lines {
		if strings.HasPrefix(line, "redis_version:") {
			return strings.TrimSpace(strings.TrimPrefix(line, "redis_version:"))
		}
	}
	return ""
}

func (suite *AdvancedTestSuite) TearDownSuite() {
	// Stop the cluster when done
	suite.runClusterManager([]string{"stop", "--prefix", "cluster"}, true)
}

func (suite *AdvancedTestSuite) SetupTest() {
	// Initialize client using the default configuration
	suite.client = suite.defaultClient()
}

func (suite *AdvancedTestSuite) defaultClientConfig() *api.GlideClientConfiguration {
	return api.NewGlideClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5000)
}

func (suite *AdvancedTestSuite) defaultClient() api.BaseClient {
	config := suite.defaultClientConfig()
	client, err := api.NewGlideClient(config)
	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)
	return client
}

func (suite *AdvancedTestSuite) defaultClusterClientConfig() *api.GlideClusterClientConfiguration {
	return api.NewGlideClusterClientConfiguration().
		WithAddress(&suite.clusterHosts[0]).
		WithUseTLS(suite.tls).
		WithRequestTimeout(5000)
}

func (suite *AdvancedTestSuite) defaultClusterClient() api.GlideClusterClientCommands {
	config := suite.defaultClusterClientConfig()
	return suite.clusterClient(config)
}

func (suite *AdvancedTestSuite) clusterClient(config *api.GlideClusterClientConfiguration) api.GlideClusterClientCommands {
	client, err := api.NewGlideClusterClient(config)
	assert.Nil(suite.T(), err)
	assert.NotNil(suite.T(), client)
	suite.clusterClients = append(suite.clusterClients, client)
	return client
}

func (suite *AdvancedTestSuite) TestFunctionKillNoWrite() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClient().(api.GlideClientCommands)
	libName := "functionKill_no_write"
	funcName := "deadlock"
	key := libName
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, true)

	// Flush all functions
	result, err := client.FunctionFlushSync()
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKill()
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoad(code, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	// Create a new client with longer timeout
	config := suite.defaultClientConfig().WithRequestTimeout(10000)
	testClient, err := api.NewGlideClient(config)
	assert.NoError(suite.T(), err)
	defer testClient.Close()

	// Channel to signal when function is killed
	killed := make(chan bool)

	// Start a goroutine to kill the function
	go func() {
		defer close(killed)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(100 * time.Millisecond) // interval of 100ms for kill attempts
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				killed <- false
				return
			case <-killTicker.C:
				result, err = client.FunctionKill()
				if err == nil {
					// A successful kill returns "OK"
					killed <- result == "OK"
					return
				}
			}
		}
	}()

	// Call the function - this should block until killed and return a script kill error
	_, err = testClient.FCallWithKeysAndArgs(funcName, []string{key}, []string{})
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "script killed"))

	// Wait for kill confirmation
	functionKilled := <-killed
	assert.True(suite.T(), functionKilled, "Function Kill did not return OK")
}

func (suite *AdvancedTestSuite) TestFunctionKillWriteFunction() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClient().(api.GlideClientCommands)
	libName := "functionKill_write_function"
	funcName := "deadlock_write_function"
	key := libName
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, false)

	// Flush all functions
	result, err := client.FunctionFlushSync()
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKill()
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoad(code, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	// Create a new client with longer timeout
	config := suite.defaultClientConfig().WithRequestTimeout(10000)
	testClient, err := api.NewGlideClient(config)
	assert.NoError(suite.T(), err)
	defer testClient.Close()

	// Channel to signal when unkillable is found
	unkillable := make(chan bool)

	// Start a goroutine to attempt killing the function
	go func() {
		defer close(unkillable)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(100 * time.Millisecond)
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				unkillable <- false
				return
			case <-killTicker.C:
				_, err = client.FunctionKill()
				if err != nil && strings.Contains(strings.ToLower(err.Error()), "unkillable") {
					unkillable <- true
					return
				}
			}
		}
	}()

	// Calling the function should block until timeout since it's a write function
	_, err = testClient.FCallWithKeysAndArgs(funcName, []string{key}, []string{})
	assert.NoError(suite.T(), err)

	// Wait for unkillable confirmation
	foundUnkillable := <-unkillable
	assert.True(suite.T(), foundUnkillable, "Function should have been unkillable")

	// Wait for the function to complete
	time.Sleep(6 * time.Second)
}

func (suite *AdvancedTestSuite) TestFunctionKillNoWriteWithoutRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	libName := "functionKill_no_write_without_route"
	funcName := "deadlock_without_route"
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, true)

	// Flush before setup
	result, err := client.FunctionFlushSync()
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing loaded, nothing to kill
	_, err = client.FunctionKill()
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoad(code, true)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	// Create a new client with longer timeout
	clientConfig := suite.defaultClusterClientConfig().WithRequestTimeout(10000)
	testClient, err := api.NewGlideClusterClient(clientConfig)
	assert.NoError(suite.T(), err)
	defer testClient.Close()

	// Channel to signal when function is killed
	killed := make(chan bool)

	// key for routing to a primary node
	randomKey := uuid.NewString()
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, randomKey),
	}

	// Start a goroutine to kill the function
	go func() {
		defer close(killed)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(100 * time.Millisecond) // 100ms interval
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				killed <- false
				return
			case <-killTicker.C:
				result, err = client.FunctionKill()
				if err == nil {
					// successful kill
					killed <- result == "OK"
					return
				}
			}
		}
	}()

	// Call the function - blocking until killed and return a script kill error
	_, err = testClient.FCallWithRoute(funcName, route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "script killed"))

	// Wait for kill confirmation
	functionKilled := <-killed
	assert.True(suite.T(), functionKilled, "Function kill should have returned OK")

	// Wait for function kill to return not busy
	notBusyTimeout := time.After(2 * time.Second)
	notBusyTicker := time.NewTicker(100 * time.Millisecond)
	defer notBusyTicker.Stop()

	for {
		select {
		case <-notBusyTimeout:
			suite.T().Fatal("Timed out waiting for function to be not busy")
			return
		case <-notBusyTicker.C:
			_, err = client.FunctionKill()
			if err != nil && strings.Contains(strings.ToLower(err.Error()), "notbusy") {
				return
			}
		}
	}
}

func (suite *AdvancedTestSuite) TestFunctionKillNoWriteWithRoute() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	libName := "functionKill_no_write_with_route"
	funcName := "deadlock_with_route"
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, true)

	// key for routing to a primary node
	randomKey := uuid.NewString()
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, randomKey),
	}

	// Flush all functions with route
	result, err := client.FunctionFlushSyncWithRoute(route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKillWithRoute(route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoadWithRoute(code, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	testConfig := suite.defaultClusterClientConfig().WithRequestTimeout(10000)
	testClient := suite.clusterClient(testConfig)
	defer testClient.Close()

	// Channel to signal when function is killed
	killed := make(chan bool)

	// Start a goroutine to kill the function
	go func() {
		defer close(killed)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(500 * time.Millisecond) // 500ms interval
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				killed <- false
				return
			case <-killTicker.C:
				result, err = client.FunctionKillWithRoute(route)
				if err == nil {
					// successful kill
					killed <- result == "OK"
					return
				}
			}
		}
	}()

	// Call the function - blocking until killed and return a script kill error
	_, err = testClient.FCallWithRoute(funcName, route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "script killed"))

	// Wait for kill confirmation
	functionKilled := <-killed
	assert.True(suite.T(), functionKilled, "Function kill should have returned OK")

	// Wait for function kill to return not busy
	notBusyTimeout := time.After(2 * time.Second)
	notBusyTicker := time.NewTicker(100 * time.Millisecond)
	defer notBusyTicker.Stop()

	for {
		select {
		case <-notBusyTimeout:
			suite.T().Fatal("Timed out waiting for function to be not busy")
			return
		case <-notBusyTicker.C:
			_, err = client.FunctionKillWithRoute(route)
			if err != nil && strings.Contains(strings.ToLower(err.Error()), "notbusy") {
				return
			}
		}
	}
}

func (suite *AdvancedTestSuite) TestFunctionKillKeyBasedWriteFunction() {
	if suite.serverVersion < "7.0.0" {
		suite.T().Skip("This feature is added in version 7")
	}

	client := suite.defaultClusterClient()
	libName := "functionKill_key_based_write_function"
	funcName := "deadlock_write_function_with_key_based_route"
	key := libName
	code := createLuaLibWithLongRunningFunction(libName, funcName, 6, false)

	// Create route using the key
	route := options.RouteOption{
		Route: config.NewSlotKeyRoute(config.SlotTypePrimary, key),
	}

	// Flush all functions with route
	result, err := client.FunctionFlushSyncWithRoute(route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", result)

	// Nothing to kill
	_, err = client.FunctionKillWithRoute(route)
	assert.Error(suite.T(), err)
	assert.True(suite.T(), strings.Contains(strings.ToLower(err.Error()), "notbusy"))

	// Load the lib
	result, err = client.FunctionLoadWithRoute(code, true, route)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), libName, result)

	testConfig := suite.defaultClusterClientConfig().WithRequestTimeout(10000)
	testClient := suite.clusterClient(testConfig)
	defer testClient.Close()

	// Channel to signal when unkillable error is found
	unkillable := make(chan bool)

	// Start a goroutine to attempt killing the function
	go func() {
		defer close(unkillable)
		timeout := time.After(4 * time.Second)
		killTicker := time.NewTicker(500 * time.Millisecond) // 500ms interval
		defer killTicker.Stop()

		for {
			select {
			case <-timeout:
				unkillable <- false
				return
			case <-killTicker.C:
				_, err = client.FunctionKillWithRoute(route)
				// Look for unkillable error
				if err != nil && strings.Contains(strings.ToLower(err.Error()), "unkillable") {
					unkillable <- true
					return
				}
			}
		}
	}()

	// Call the function with the key - this will block until completion
	testClient.FCallWithKeysAndArgs(funcName, []string{key}, []string{})
	// Function completed as expected

	// Wait for unkillable confirmation
	foundUnkillable := <-unkillable
	assert.True(suite.T(), foundUnkillable, "Function should be unkillable")
}

func TestAdvancedTestSuite(t *testing.T) {
	suite.Run(t, new(AdvancedTestSuite))
}
