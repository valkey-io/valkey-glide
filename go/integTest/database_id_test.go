// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/internal/interfaces"
	"github.com/valkey-io/valkey-glide/go/v2/options"
)

// TestDatabaseId_StandaloneClientCreation tests creating standalone clients with database_id configuration
func (suite *GlideTestSuite) TestDatabaseId_StandaloneClientCreation() {
	// Test creating standalone client with various database IDs
	testCases := []struct {
		databaseId int
		name       string
	}{
		{0, "Database0"},
		{1, "Database1"},
		{5, "Database5"},
		{15, "Database15"},
	}

	for _, tc := range testCases {
		suite.T().Run(tc.name, func(t *testing.T) {
			config := config.NewClientConfiguration().
				WithAddress(&suite.standaloneHosts[0]).
				WithDatabaseId(tc.databaseId)

			client, err := glide.NewClient(config)
			assert.NoError(t, err)
			assert.NotNil(t, client)

			// Verify we can perform operations in the selected database
			key := uuid.New().String()
			value := uuid.New().String()

			result, err := client.Set(context.Background(), key, value)
			assert.NoError(t, err)
			assert.Equal(t, "OK", result)

			getResult, err := client.Get(context.Background(), key)
			assert.NoError(t, err)
			assert.Equal(t, value, getResult.Value())

			client.Close()
		})
	}
}

// TestDatabaseId_ClusterClientCreation tests creating cluster clients with database_id configuration
func (suite *GlideTestSuite) TestDatabaseId_ClusterClientCreation() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	// Test creating cluster client with various database IDs
	testCases := []struct {
		databaseId int
		name       string
	}{
		{0, "Database0"},
		{1, "Database1"},
		{5, "Database5"},
		{15, "Database15"},
	}

	for _, tc := range testCases {
		suite.T().Run(tc.name, func(t *testing.T) {
			config := config.NewClusterClientConfiguration().
				WithDatabaseId(tc.databaseId)

			for _, host := range suite.clusterHosts {
				config.WithAddress(&host)
			}

			client, err := glide.NewClusterClient(config)
			assert.NoError(t, err)
			assert.NotNil(t, client)

			// Verify we can perform operations in the selected database
			key := uuid.New().String()
			value := uuid.New().String()

			result, err := client.Set(context.Background(), key, value)
			assert.NoError(t, err)
			assert.Equal(t, "OK", result)

			getResult, err := client.Get(context.Background(), key)
			assert.NoError(t, err)
			assert.Equal(t, value, getResult.Value())

			client.Close()
		})
	}
}

// TestDatabaseId_StandaloneReconnection tests that database_id persists across reconnections for standalone clients
func (suite *GlideTestSuite) TestDatabaseId_StandaloneReconnection() {
	config := config.NewClientConfiguration().
		WithAddress(&suite.standaloneHosts[0]).
		WithDatabaseId(1).
		WithRequestTimeout(1 * time.Second)

	client, err := glide.NewClient(config)
	suite.NoError(err)
	defer client.Close()

	// Set a key in database 1
	key := uuid.New().String()
	value := uuid.New().String()

	result, err := client.Set(context.Background(), key, value)
	suite.NoError(err)
	suite.Equal("OK", result)

	// Verify the key exists in database 1
	getResult, err := client.Get(context.Background(), key)
	suite.NoError(err)
	suite.Equal(value, getResult.Value())

	// Force a reconnection by executing a command that might cause network issues
	// We'll use a long-running command and then try to execute another command
	go func() {
		time.Sleep(100 * time.Millisecond)
		// This should cause the connection to be reset
		client.CustomCommand(context.Background(), []string{"DEBUG", "RESTART"})
	}()

	// Wait a bit for potential reconnection
	time.Sleep(500 * time.Millisecond)

	// After reconnection, we should still be in database 1
	// Try to get the key again - it should still exist
	getResult, err = client.Get(context.Background(), key)
	suite.NoError(err)
	suite.Equal(value, getResult.Value())
}

// TestDatabaseId_ClusterReconnection tests that database_id persists across reconnections for cluster clients
func (suite *GlideTestSuite) TestDatabaseId_ClusterReconnection() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	config := config.NewClusterClientConfiguration().
		WithDatabaseId(1).
		WithRequestTimeout(1 * time.Second)

	for _, host := range suite.clusterHosts {
		config.WithAddress(&host)
	}

	client, err := glide.NewClusterClient(config)
	suite.NoError(err)
	defer client.Close()

	// Set a key in database 1
	key := uuid.New().String()
	value := uuid.New().String()

	result, err := client.Set(context.Background(), key, value)
	suite.NoError(err)
	suite.Equal("OK", result)

	// Verify the key exists in database 1
	getResult, err := client.Get(context.Background(), key)
	suite.NoError(err)
	suite.Equal(value, getResult.Value())

	// Wait a bit to simulate potential reconnection scenarios
	time.Sleep(500 * time.Millisecond)

	// After potential reconnection, we should still be in database 1
	// Try to get the key again - it should still exist
	getResult, err = client.Get(context.Background(), key)
	suite.NoError(err)
	suite.Equal(value, getResult.Value())
}

// TestDatabaseId_SelectCommandRouting tests SELECT command routing in cluster mode
func (suite *GlideTestSuite) TestDatabaseId_SelectCommandRouting() {
	suite.SkipIfServerVersionLowerThan("9.0.0", suite.T())

	client := suite.defaultClusterClient()

	// Test SELECT command with default routing (should route to all nodes)
	result, err := client.Select(context.Background(), 1)
	suite.NoError(err)
	suite.Equal("OK", result)

	// Test SELECT command with explicit AllNodes routing
	result, err = client.SelectWithOptions(context.Background(), 2, options.RouteOption{Route: config.AllNodes})
	suite.NoError(err)
	suite.Equal("OK", result)

	// Test SELECT command with AllPrimaries routing
	result, err = client.SelectWithOptions(context.Background(), 3, options.RouteOption{Route: config.AllPrimaries})
	suite.NoError(err)
	suite.Equal("OK", result)

	// Test SELECT command with RandomRoute routing
	result, err = client.SelectWithOptions(context.Background(), 0, options.RouteOption{Route: config.RandomRoute})
	suite.NoError(err)
	suite.Equal("OK", result)
}

// TestDatabaseId_ErrorHandling tests error handling for invalid database configurations
func (suite *GlideTestSuite) TestDatabaseId_ErrorHandling() {
	// Test standalone client with out-of-range database ID
	suite.T().Run("StandaloneOutOfRange", func(t *testing.T) {
		config := config.NewClientConfiguration().
			WithAddress(&suite.standaloneHosts[0]).
			WithDatabaseId(1000) // Assuming this is out of range

		client, err := glide.NewClient(config)
		if err != nil {
			// If client creation fails, that's expected for out-of-range database IDs
			assert.Contains(t, err.Error(), "DB index is out of range")
			return
		}

		if client != nil {
			defer client.Close()
			// If client creation succeeds, the first operation should fail
			_, err = client.Set(context.Background(), "test", "value")
			assert.Error(t, err)
			assert.Contains(t, err.Error(), "DB index is out of range")
		}
	})

	// Test cluster client with out-of-range database ID (only for Valkey 9+)
	suite.T().Run("ClusterOutOfRange", func(t *testing.T) {
		suite.SkipIfServerVersionLowerThan("9.0.0", t)

		config := config.NewClusterClientConfiguration().
			WithDatabaseId(1000) // Assuming this is out of range

		for _, host := range suite.clusterHosts {
			config.WithAddress(&host)
		}

		client, err := glide.NewClusterClient(config)
		if err != nil {
			// If client creation fails, that's expected for out-of-range database IDs
			assert.Contains(t, err.Error(), "DB index is out of range")
			return
		}

		if client != nil {
			defer client.Close()
			// If client creation succeeds, the first operation should fail
			_, err = client.Set(context.Background(), "test", "value")
			assert.Error(t, err)
			assert.Contains(t, err.Error(), "DB index is out of range")
		}
	})
}

// TestDatabaseId_DatabaseIsolation tests that different databases are properly isolated
func (suite *GlideTestSuite) TestDatabaseId_DatabaseIsolation() {
	// Test standalone database isolation
	suite.T().Run("StandaloneDatabaseIsolation", func(t *testing.T) {
		// Create clients for different databases
		client0, err := glide.NewClient(
			config.NewClientConfiguration().
				WithAddress(&suite.standaloneHosts[0]).
				WithDatabaseId(0),
		)
		assert.NoError(t, err)
		defer client0.Close()

		client1, err := glide.NewClient(
			config.NewClientConfiguration().
				WithAddress(&suite.standaloneHosts[0]).
				WithDatabaseId(1),
		)
		assert.NoError(t, err)
		defer client1.Close()

		// Set different values in each database
		key := uuid.New().String()
		value0 := "value_db0"
		value1 := "value_db1"

		result, err := client0.Set(context.Background(), key, value0)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		result, err = client1.Set(context.Background(), key, value1)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		// Verify isolation - each client should see only its own value
		getResult, err := client0.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value0, getResult.Value())

		getResult, err = client1.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value1, getResult.Value())
	})

	// Test cluster database isolation (only for Valkey 9+)
	suite.T().Run("ClusterDatabaseIsolation", func(t *testing.T) {
		suite.SkipIfServerVersionLowerThan("9.0.0", t)

		// Create clients for different databases
		config0 := config.NewClusterClientConfiguration().WithDatabaseId(0)
		config1 := config.NewClusterClientConfiguration().WithDatabaseId(1)

		for _, host := range suite.clusterHosts {
			config0.WithAddress(&host)
			config1.WithAddress(&host)
		}

		client0, err := glide.NewClusterClient(config0)
		assert.NoError(t, err)
		defer client0.Close()

		client1, err := glide.NewClusterClient(config1)
		assert.NoError(t, err)
		defer client1.Close()

		// Set different values in each database
		key := uuid.New().String()
		value0 := "value_db0"
		value1 := "value_db1"

		result, err := client0.Set(context.Background(), key, value0)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		result, err = client1.Set(context.Background(), key, value1)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		// Verify isolation - each client should see only its own value
		getResult, err := client0.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value0, getResult.Value())

		getResult, err = client1.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value1, getResult.Value())
	})
}

// TestDatabaseId_BackwardCompatibility tests that existing code works without modifications
func (suite *GlideTestSuite) TestDatabaseId_BackwardCompatibility() {
	// Test standalone client without database_id (should default to database 0)
	suite.T().Run("StandaloneBackwardCompatibility", func(t *testing.T) {
		config := config.NewClientConfiguration().
			WithAddress(&suite.standaloneHosts[0])
		// Note: No WithDatabaseId() call - should default to database 0

		client, err := glide.NewClient(config)
		assert.NoError(t, err)
		defer client.Close()

		// Should work in database 0 by default
		key := uuid.New().String()
		value := uuid.New().String()

		result, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		getResult, err := client.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value, getResult.Value())
	})

	// Test cluster client without database_id (should default to database 0)
	suite.T().Run("ClusterBackwardCompatibility", func(t *testing.T) {
		config := config.NewClusterClientConfiguration()
		// Note: No WithDatabaseId() call - should default to database 0

		for _, host := range suite.clusterHosts {
			config.WithAddress(&host)
		}

		client, err := glide.NewClusterClient(config)
		assert.NoError(t, err)
		defer client.Close()

		// Should work in database 0 by default
		key := uuid.New().String()
		value := uuid.New().String()

		result, err := client.Set(context.Background(), key, value)
		assert.NoError(t, err)
		assert.Equal(t, "OK", result)

		getResult, err := client.Get(context.Background(), key)
		assert.NoError(t, err)
		assert.Equal(t, value, getResult.Value())
	})
}

// TestDatabaseId_ServerCompatibility tests server compatibility detection
func (suite *GlideTestSuite) TestDatabaseId_ServerCompatibility() {
	// Test that cluster multi-database support is properly detected
	suite.T().Run("ClusterMultiDatabaseSupport", func(t *testing.T) {
		if suite.serverVersion < "9.0.0" {
			// For servers before 9.0.0, cluster clients with non-zero database_id should fail
			config := config.NewClusterClientConfiguration().
				WithDatabaseId(1)

			for _, host := range suite.clusterHosts {
				config.WithAddress(&host)
			}

			client, err := glide.NewClusterClient(config)
			if err != nil {
				// Expected failure for older servers
				assert.Contains(t, err.Error(), "SELECT is not allowed in cluster mode")
				return
			}

			if client != nil {
				defer client.Close()
				// If client creation succeeds, the first operation should fail
				_, err = client.Set(context.Background(), "test", "value")
				assert.Error(t, err)
				assert.Contains(t, err.Error(), "SELECT is not allowed in cluster mode")
			}
		} else {
			// For Valkey 9.0+, cluster clients with non-zero database_id should work
			config := config.NewClusterClientConfiguration().
				WithDatabaseId(1)

			for _, host := range suite.clusterHosts {
				config.WithAddress(&host)
			}

			client, err := glide.NewClusterClient(config)
			assert.NoError(t, err)
			defer client.Close()

			// Should work fine
			result, err := client.Set(context.Background(), "test", "value")
			assert.NoError(t, err)
			assert.Equal(t, "OK", result)
		}
	})
}

// Helper function to create a client with database_id for testing
func (suite *GlideTestSuite) createClientWithDatabaseId(clusterMode bool, databaseId int) interfaces.BaseClientCommands {
	if clusterMode {
		config := config.NewClusterClientConfiguration().
			WithDatabaseId(databaseId)

		for _, host := range suite.clusterHosts {
			config.WithAddress(&host)
		}

		client, err := glide.NewClusterClient(config)
		suite.NoError(err)
		return client
	} else {
		config := config.NewClientConfiguration().
			WithAddress(&suite.standaloneHosts[0]).
			WithDatabaseId(databaseId)

		client, err := glide.NewClient(config)
		suite.NoError(err)
		return client
	}
}

// TestDatabaseId_CrossClientCompatibility tests consistent behavior across client types
func (suite *GlideTestSuite) TestDatabaseId_CrossClientCompatibility() {
	// Test that both standalone and cluster clients behave consistently with database_id
	testCases := []struct {
		databaseId int
		name       string
	}{
		{0, "Database0"},
		{1, "Database1"},
	}

	for _, tc := range testCases {
		suite.T().Run(tc.name, func(t *testing.T) {
			// Test standalone client
			standaloneClient := suite.createClientWithDatabaseId(false, tc.databaseId)
			defer standaloneClient.Close()

			key := uuid.New().String()
			value := uuid.New().String()

			result, err := standaloneClient.Set(context.Background(), key, value)
			assert.NoError(t, err)
			assert.Equal(t, "OK", result)

			getResult, err := standaloneClient.Get(context.Background(), key)
			assert.NoError(t, err)
			assert.Equal(t, value, getResult.Value())

			// Test cluster client (only for Valkey 9+ and database 1)
			if tc.databaseId == 1 {
				suite.SkipIfServerVersionLowerThan("9.0.0", t)
			}

			clusterClient := suite.createClientWithDatabaseId(true, tc.databaseId)
			defer clusterClient.Close()

			key2 := uuid.New().String()
			value2 := uuid.New().String()

			result, err = clusterClient.Set(context.Background(), key2, value2)
			assert.NoError(t, err)
			assert.Equal(t, "OK", result)

			getResult, err = clusterClient.Get(context.Background(), key2)
			assert.NoError(t, err)
			assert.Equal(t, value2, getResult.Value())
		})
	}
}
