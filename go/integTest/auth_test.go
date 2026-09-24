// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"os"
	"sync/atomic"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
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
	require.NoError(suite.T(), err)

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
	require.NoError(suite.T(), err, "Failed to create client - ensure AWS mock credentials are set")
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
	require.NoError(suite.T(), err)

	clusterConfig := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.clusterHosts[0].Host,
			Port: suite.clusterHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(suite.tls)

	client, err := glide.NewClusterClient(clusterConfig)
	require.NoError(suite.T(), err, "Failed to create client - ensure AWS mock credentials are set")
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
	require.NoError(suite.T(), err)

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
	require.NoError(suite.T(), err, "Failed to create client - ensure AWS mock credentials are set")
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
	require.NoError(suite.T(), err)

	standaloneConfig := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.standaloneHosts[0].Host,
			Port: suite.standaloneHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(suite.tls)

	client, err := glide.NewClient(standaloneConfig)
	require.NoError(suite.T(), err, "Failed to create client - ensure AWS mock credentials are set")
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

// TestIamPoolWithCustomCredentialsProvider tests that a ClientPool with a custom IAM credential
// provider correctly invokes the provider and executes commands via a borrowed client.
func (suite *GlideTestSuite) TestIamPoolWithCustomCredentialsProvider() {
	var invocations int32
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		atomic.AddInt32(&invocations, 1)
		return config.AwsCredentials{
			AccessKeyID:     os.Getenv("AWS_ACCESS_KEY_ID"),
			SecretAccessKey: os.Getenv("AWS_SECRET_ACCESS_KEY"),
			SessionToken:    os.Getenv("AWS_SESSION_TOKEN"),
		}, nil
	})

	iamConfig := config.NewIamAuthConfig(
		TestClusterName,
		config.ElastiCache,
		TestRegionUsEast1,
	).WithRefreshIntervalSeconds(5).
		WithCredentialProvider(provider)

	credentials, err := config.NewServerCredentialsWithIam(TestIamUsername, iamConfig)
	require.NoError(suite.T(), err)

	clientCfg := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.standaloneHosts[0].Host,
			Port: suite.standaloneHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(suite.tls)

	poolCfg := glide.DefaultPoolConfig()
	pool, err := glide.NewClientPool(clientCfg, poolCfg)
	require.NoError(suite.T(), err, "Failed to create IAM pool - ensure AWS mock credentials are set")
	defer pool.Close()

	ctx := context.Background()
	clientID, err := pool.Acquire(ctx)
	require.NoError(suite.T(), err)
	defer pool.Release(clientID)

	client, err := pool.GetClient(clientID)
	require.NoError(suite.T(), err)

	setResult, err := client.Set(ctx, "iam_pool_custom_provider_key", "iam_pool_custom_provider_value")
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "OK", setResult)

	getResult, err := client.Get(ctx, "iam_pool_custom_provider_key")
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "iam_pool_custom_provider_value", getResult.Value())

	assert.Greater(suite.T(), atomic.LoadInt32(&invocations), int32(0),
		"Custom credentials provider was never invoked for pool client")
}
