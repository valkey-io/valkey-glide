// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// TestIamAuthenticationWithMockCredentials tests IAM authentication using mock AWS credentials.
//
// This test verifies:
// 1. Client can connect using IAM authentication with mock credentials
// 2. Basic operations work after IAM authentication
// 3. Operations continue to work after token refresh
func (suite *GlideTestSuite) TestIamAuthenticationWithMockCredentials() {
	// Create IAM config
	iamConfig := config.NewIamAuthConfig(
		TestClusterName,
		config.ElastiCache,
		TestRegionUsEast1,
	).WithRefreshIntervalSeconds(5)

	// Create credentials with IAM config
	credentials, err := config.NewServerCredentialsWithIam(TestIamUsername, iamConfig)
	assert.NoError(suite.T(), err)

	// Create cluster client configuration
	// Note: useTLS is set from suite.tls which respects the --tls flag
	clusterConfig := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.clusterHosts[0].Host,
			Port: suite.clusterHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(suite.tls)

	// Create client with IAM authentication
	client, err := glide.NewClusterClient(clusterConfig)
	assert.NoError(suite.T(), err)
	defer client.Close()

	// Verify connection works
	assertConnected(suite.T(), client)

	// Test basic operations
	key1 := uuid.NewString()
	value1 := "iam_test_value"
	setResult, err := client.Set(context.Background(), key1, value1)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", setResult)

	getResult, err := client.Get(context.Background(), key1)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), value1, getResult.Value())

	// Test manual token refresh
	_, err = client.RefreshIamToken(context.Background())
	assert.NoError(suite.T(), err)

	// Verify operations still work after token refresh
	key2 := uuid.NewString()
	value2 := "iam_test_value2"
	setResult2, err := client.Set(context.Background(), key2, value2)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", setResult2)

	getResult2, err := client.Get(context.Background(), key2)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), value2, getResult2.Value())
}

// TestIamAuthenticationAutomaticTokenRefresh tests automatic IAM token refresh.
//
// This test verifies that the client automatically refreshes the IAM token
// at the configured interval and continues to work correctly.
func (suite *GlideTestSuite) TestIamAuthenticationAutomaticTokenRefresh() {
	// Create IAM config with very short refresh interval
	iamConfig := config.NewIamAuthConfig(
		TestClusterName,
		config.ElastiCache,
		TestRegionUsEast1,
	).WithRefreshIntervalSeconds(2)

	credentials, err := config.NewServerCredentialsWithIam(TestIamUsername, iamConfig)
	assert.NoError(suite.T(), err)

	clusterConfig := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.clusterHosts[0].Host,
			Port: suite.clusterHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(suite.tls)

	client, err := glide.NewClusterClient(clusterConfig)
	assert.NoError(suite.T(), err)
	defer client.Close()

	// Verify initial connection
	assertConnected(suite.T(), client)

	// Wait for automatic token refresh to occur
	time.Sleep(3 * time.Second)

	// Verify client still works after automatic refresh
	key := uuid.NewString()
	value := "iam_auto_refresh_value"
	setResult, err := client.Set(context.Background(), key, value)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", setResult)

	getResult, err := client.Get(context.Background(), key)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), value, getResult.Value())
}

// TestIamAuthenticationWithMockCredentialsStandalone tests IAM authentication using mock AWS credentials in standalone mode.
func (suite *GlideTestSuite) TestIamAuthenticationWithMockCredentialsStandalone() {
	// Create IAM config
	iamConfig := config.NewIamAuthConfig(
		TestClusterName,
		config.ElastiCache,
		TestRegionUsEast1,
	).WithRefreshIntervalSeconds(5)

	// Create credentials with IAM config
	credentials, err := config.NewServerCredentialsWithIam(TestIamUsername, iamConfig)
	assert.NoError(suite.T(), err)

	// Create standalone client configuration
	standaloneConfig := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.standaloneHosts[0].Host,
			Port: suite.standaloneHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(suite.tls)

	// Create client with IAM authentication
	client, err := glide.NewClient(standaloneConfig)
	assert.NoError(suite.T(), err)
	defer client.Close()

	// Verify connection works
	assertConnected(suite.T(), client)

	// Test basic operations
	key1 := uuid.NewString()
	value1 := "iam_test_value"
	setResult, err := client.Set(context.Background(), key1, value1)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", setResult)

	getResult, err := client.Get(context.Background(), key1)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), value1, getResult.Value())

	// Test manual token refresh
	_, err = client.RefreshIamToken(context.Background())
	assert.NoError(suite.T(), err)

	// Verify operations still work after token refresh
	key2 := uuid.NewString()
	value2 := "iam_test_value2"
	setResult2, err := client.Set(context.Background(), key2, value2)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", setResult2)

	getResult2, err := client.Get(context.Background(), key2)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), value2, getResult2.Value())
}

// TestIamAuthenticationAutomaticTokenRefreshStandalone tests automatic IAM token refresh in standalone mode.
func (suite *GlideTestSuite) TestIamAuthenticationAutomaticTokenRefreshStandalone() {
	// Create IAM config with very short refresh interval
	iamConfig := config.NewIamAuthConfig(
		TestClusterName,
		config.ElastiCache,
		TestRegionUsEast1,
	).WithRefreshIntervalSeconds(2)

	credentials, err := config.NewServerCredentialsWithIam(TestIamUsername, iamConfig)
	assert.NoError(suite.T(), err)

	standaloneConfig := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.standaloneHosts[0].Host,
			Port: suite.standaloneHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(suite.tls)

	client, err := glide.NewClient(standaloneConfig)
	assert.NoError(suite.T(), err)
	defer client.Close()

	// Verify initial connection
	assertConnected(suite.T(), client)

	// Wait for automatic token refresh to occur
	time.Sleep(3 * time.Second)

	// Verify client still works after automatic refresh
	key := uuid.NewString()
	value := "iam_auto_refresh_value"
	setResult, err := client.Set(context.Background(), key, value)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", setResult)

	getResult, err := client.Get(context.Background(), key)
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), value, getResult.Value())
}
