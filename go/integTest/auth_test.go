// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"os"
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
	// Save original AWS credentials
	originalAccessKey := os.Getenv("AWS_ACCESS_KEY_ID")
	originalSecretKey := os.Getenv("AWS_SECRET_ACCESS_KEY")
	originalSessionToken := os.Getenv("AWS_SESSION_TOKEN")

	// Set mock AWS credentials
	os.Setenv("AWS_ACCESS_KEY_ID", "test_access_key")
	os.Setenv("AWS_SECRET_ACCESS_KEY", "test_secret_key")
	os.Setenv("AWS_SESSION_TOKEN", "test_session_token")

	// Cleanup function to restore original credentials
	defer func() {
		if originalAccessKey != "" {
			os.Setenv("AWS_ACCESS_KEY_ID", originalAccessKey)
		} else {
			os.Unsetenv("AWS_ACCESS_KEY_ID")
		}
		if originalSecretKey != "" {
			os.Setenv("AWS_SECRET_ACCESS_KEY", originalSecretKey)
		} else {
			os.Unsetenv("AWS_SECRET_ACCESS_KEY")
		}
		if originalSessionToken != "" {
			os.Setenv("AWS_SESSION_TOKEN", originalSessionToken)
		} else {
			os.Unsetenv("AWS_SESSION_TOKEN")
		}
	}()

	// Create IAM config
	iamConfig := config.NewIamAuthConfig(
		"test-cluster",
		config.ElastiCache,
		"us-east-1",
	).WithRefreshIntervalSeconds(5) // Fast refresh for testing

	// Create credentials with IAM config
	credentials, err := config.NewServerCredentialsWithIam("default", iamConfig)
	assert.NoError(suite.T(), err)

	// Create cluster client configuration
	clusterConfig := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.clusterHosts[0].Host,
			Port: suite.clusterHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(false) // Local cluster doesn't use TLS

	// Create client with IAM authentication
	client, err := glide.NewClusterClient(clusterConfig)
	assert.NoError(suite.T(), err)
	defer client.Close()

	// Verify connection works
	pingResult, err := client.Ping(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", pingResult)

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
	// Save original AWS credentials
	originalAccessKey := os.Getenv("AWS_ACCESS_KEY_ID")
	originalSecretKey := os.Getenv("AWS_SECRET_ACCESS_KEY")
	originalSessionToken := os.Getenv("AWS_SESSION_TOKEN")

	// Set mock AWS credentials
	os.Setenv("AWS_ACCESS_KEY_ID", "test_access_key")
	os.Setenv("AWS_SECRET_ACCESS_KEY", "test_secret_key")
	os.Setenv("AWS_SESSION_TOKEN", "test_session_token")

	// Cleanup function to restore original credentials
	defer func() {
		if originalAccessKey != "" {
			os.Setenv("AWS_ACCESS_KEY_ID", originalAccessKey)
		} else {
			os.Unsetenv("AWS_ACCESS_KEY_ID")
		}
		if originalSecretKey != "" {
			os.Setenv("AWS_SECRET_ACCESS_KEY", originalSecretKey)
		} else {
			os.Unsetenv("AWS_SECRET_ACCESS_KEY")
		}
		if originalSessionToken != "" {
			os.Setenv("AWS_SESSION_TOKEN", originalSessionToken)
		} else {
			os.Unsetenv("AWS_SESSION_TOKEN")
		}
	}()

	// Create IAM config with very short refresh interval
	iamConfig := config.NewIamAuthConfig(
		"test-cluster",
		config.ElastiCache,
		"us-east-1",
	).WithRefreshIntervalSeconds(2) // Very fast refresh for testing

	credentials, err := config.NewServerCredentialsWithIam("default", iamConfig)
	assert.NoError(suite.T(), err)

	clusterConfig := config.NewClusterClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: suite.clusterHosts[0].Host,
			Port: suite.clusterHosts[0].Port,
		}).
		WithCredentials(credentials).
		WithUseTLS(false)

	client, err := glide.NewClusterClient(clusterConfig)
	assert.NoError(suite.T(), err)
	defer client.Close()

	// Verify initial connection
	pingResult, err := client.Ping(context.Background())
	assert.NoError(suite.T(), err)
	assert.Equal(suite.T(), "PONG", pingResult)

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
