// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"errors"
	"strings"
	"sync/atomic"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/models"
)

func customIamCredentials(
	t require.TestingT,
	provider config.GlideCredentialProvider,
	refreshIntervalSeconds uint32,
) *config.ServerCredentials {
	iamConfig := config.NewIamAuthConfig(TestClusterName, config.ElastiCache, TestRegionUsEast1).
		WithRefreshIntervalSeconds(refreshIntervalSeconds).
		WithCredentialProvider(provider)
	credentials, err := config.NewServerCredentialsWithIam(TestIamUsername, iamConfig)
	require.NoError(t, err)
	return credentials
}

func assertIamClientOperations(t require.TestingT, client interface {
	Set(context.Context, string, string) (string, error)
	Get(context.Context, string) (models.Result[string], error)
},
) {
	key := uuid.NewString()
	value := "iam_test_value"
	setResult, err := client.Set(context.Background(), key, value)
	require.NoError(t, err)
	assert.Equal(t, glide.OK, setResult)
	getResult, err := client.Get(context.Background(), key)
	require.NoError(t, err)
	assert.Equal(t, value, getResult.Value())
}

// TestIamAuthenticationWithMockCredentials verifies cluster initial authentication and manual refresh
// through the direct custom credential-provider callback.
func (suite *GlideTestSuite) TestIamAuthenticationWithMockCredentials() {
	var invocations atomic.Int32
	provider := func() (config.AwsCredentials, error) {
		invocations.Add(1)
		return config.AwsCredentials{
			AccessKeyID:          "test_access_key",
			SecretAccessKey:      "test_secret_key",
			SessionToken:         "test_session_token",
			ExpiresAtEpochMillis: time.Now().Add(time.Minute).UnixMilli(),
		}, nil
	}
	clusterConfig := suite.defaultClusterClientConfig().
		WithCredentials(customIamCredentials(suite.T(), provider, 300))

	client, err := glide.NewClusterClient(clusterConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
	assertIamClientOperations(suite.T(), client)
	afterInitialAuth := invocations.Load()
	assert.Greater(suite.T(), afterInitialAuth, int32(0))

	_, err = client.RefreshIamToken(context.Background())
	require.NoError(suite.T(), err)
	assert.Greater(suite.T(), invocations.Load(), afterInitialAuth)
	assertIamClientOperations(suite.T(), client)
}

// TestIamAuthenticationAutomaticTokenRefresh verifies cluster automatic refresh uses the direct provider.
func (suite *GlideTestSuite) TestIamAuthenticationAutomaticTokenRefresh() {
	var invocations atomic.Int32
	provider := func() (config.AwsCredentials, error) {
		invocations.Add(1)
		return config.AwsCredentials{AccessKeyID: "test_access_key", SecretAccessKey: "test_secret_key"}, nil
	}
	clusterConfig := suite.defaultClusterClientConfig().
		WithCredentials(customIamCredentials(suite.T(), provider, 2))

	client, err := glide.NewClusterClient(clusterConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
	afterInitialAuth := invocations.Load()
	require.Greater(suite.T(), afterInitialAuth, int32(0))
	require.Eventually(suite.T(), func() bool {
		return invocations.Load() > afterInitialAuth
	}, 5*time.Second, 100*time.Millisecond)
	assertIamClientOperations(suite.T(), client)
}

// TestIamAuthenticationWithMockCredentialsStandalone verifies standalone initial auth and manual refresh
// with optional token and expiry omitted.
func (suite *GlideTestSuite) TestIamAuthenticationWithMockCredentialsStandalone() {
	var invocations atomic.Int32
	provider := func() (config.AwsCredentials, error) {
		invocations.Add(1)
		return config.AwsCredentials{AccessKeyID: "test_access_key", SecretAccessKey: "test_secret_key"}, nil
	}
	standaloneConfig := suite.defaultClientConfig().
		WithCredentials(customIamCredentials(suite.T(), provider, 300))

	client, err := glide.NewClient(standaloneConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
	assertIamClientOperations(suite.T(), client)
	afterInitialAuth := invocations.Load()
	assert.Greater(suite.T(), afterInitialAuth, int32(0))

	_, err = client.RefreshIamToken(context.Background())
	require.NoError(suite.T(), err)
	assert.Greater(suite.T(), invocations.Load(), afterInitialAuth)
	assertIamClientOperations(suite.T(), client)
}

// TestIamAuthenticationAutomaticTokenRefreshStandalone verifies standalone automatic refresh uses the direct provider.
func (suite *GlideTestSuite) TestIamAuthenticationAutomaticTokenRefreshStandalone() {
	var invocations atomic.Int32
	provider := func() (config.AwsCredentials, error) {
		invocations.Add(1)
		return config.AwsCredentials{AccessKeyID: "test_access_key", SecretAccessKey: "test_secret_key"}, nil
	}
	standaloneConfig := suite.defaultClientConfig().
		WithCredentials(customIamCredentials(suite.T(), provider, 2))

	client, err := glide.NewClient(standaloneConfig)
	require.NoError(suite.T(), err)
	defer client.Close()

	assertConnected(suite.T(), client)
	afterInitialAuth := invocations.Load()
	require.Greater(suite.T(), afterInitialAuth, int32(0))
	require.Eventually(suite.T(), func() bool {
		return invocations.Load() > afterInitialAuth
	}, 5*time.Second, 100*time.Millisecond)
	assertIamClientOperations(suite.T(), client)
}

func (suite *GlideTestSuite) TestIamCustomCredentialProviderFailure() {
	provider := func() (config.AwsCredentials, error) {
		return config.AwsCredentials{}, errors.New("custom provider failed")
	}

	standaloneConfig := suite.defaultClientConfig().
		WithCredentials(customIamCredentials(suite.T(), provider, 300))
	client, err := glide.NewClient(standaloneConfig)
	assert.Nil(suite.T(), client)
	require.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Custom credentials provider callback returned failure")

	clusterConfig := suite.defaultClusterClientConfig().
		WithCredentials(customIamCredentials(suite.T(), provider, 300))
	clusterClient, err := glide.NewClusterClient(clusterConfig)
	assert.Nil(suite.T(), clusterClient)
	require.Error(suite.T(), err)
	assert.Contains(suite.T(), err.Error(), "Custom credentials provider callback returned failure")
}

func (suite *GlideTestSuite) TestIamCustomCredentialProviderRejectsWhitespaceRequiredValues() {
	standaloneProvider := func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: " \t\r\n", SecretAccessKey: "secret"}, nil
	}
	standaloneConfig := suite.defaultClientConfig().
		WithCredentials(customIamCredentials(suite.T(), standaloneProvider, 300))
	client, err := glide.NewClient(standaloneConfig)
	assert.Nil(suite.T(), client)
	require.Error(suite.T(), err)

	clusterProvider := func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "access", SecretAccessKey: " \t\r\n"}, nil
	}
	clusterConfig := suite.defaultClusterClientConfig().
		WithCredentials(customIamCredentials(suite.T(), clusterProvider, 300))
	clusterClient, err := glide.NewClusterClient(clusterConfig)
	assert.Nil(suite.T(), clusterClient)
	require.Error(suite.T(), err)
}

func (suite *GlideTestSuite) TestIamCustomCredentialProviderNegotiatesLargeSessionToken() {
	largeSessionToken := strings.Repeat("t", 9*1024)

	var standaloneInvocations atomic.Int32
	standaloneProvider := func() (config.AwsCredentials, error) {
		standaloneInvocations.Add(1)
		return config.AwsCredentials{
			AccessKeyID:     "test_access_key",
			SecretAccessKey: "test_secret_key",
			SessionToken:    largeSessionToken,
		}, nil
	}
	standaloneConfig := suite.defaultClientConfig().
		WithCredentials(customIamCredentials(suite.T(), standaloneProvider, 300))
	client, err := glide.NewClient(standaloneConfig)
	require.NoError(suite.T(), err)
	assertConnected(suite.T(), client)
	client.Close()
	assert.GreaterOrEqual(suite.T(), standaloneInvocations.Load(), int32(2))

	var clusterInvocations atomic.Int32
	clusterProvider := func() (config.AwsCredentials, error) {
		clusterInvocations.Add(1)
		return config.AwsCredentials{
			AccessKeyID:          "test_access_key",
			SecretAccessKey:      "test_secret_key",
			SessionToken:         largeSessionToken,
			ExpiresAtEpochMillis: time.Now().Add(time.Minute).UnixMilli(),
		}, nil
	}
	clusterConfig := suite.defaultClusterClientConfig().
		WithCredentials(customIamCredentials(suite.T(), clusterProvider, 300))
	clusterClient, err := glide.NewClusterClient(clusterConfig)
	require.NoError(suite.T(), err)
	assertConnected(suite.T(), clusterClient)
	clusterClient.Close()
	assert.GreaterOrEqual(suite.T(), clusterInvocations.Load(), int32(2))
}
