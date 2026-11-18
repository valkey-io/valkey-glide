// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func credentialProviderRegistrySize() int {
	size := 0
	credentialProviderRegistry.Range(func(_, _ any) bool {
		size++
		return true
	})
	return size
}

func testCredentialProvider() (config.AwsCredentials, error) {
	return config.AwsCredentials{AccessKeyID: "access", SecretAccessKey: "secret"}, nil
}

func testIamCredentials(t *testing.T, provider config.GlideCredentialProvider) *config.ServerCredentials {
	t.Helper()
	iam := config.NewIamAuthConfig("test-cluster", config.ElastiCache, "us-east-1")
	if provider != nil {
		iam.WithCredentialProvider(provider)
	}
	credentials, err := config.NewServerCredentialsWithIam("default", iam)
	require.NoError(t, err)
	return credentials
}

func TestNewClientPoolRejectsCustomCredentialProviderBeforeNativeWork(t *testing.T) {
	beforeID := clientIDCounter.Load()
	beforeRegistry := credentialProviderRegistrySize()
	calls := 0
	provider := func() (config.AwsCredentials, error) {
		calls++
		return testCredentialProvider()
	}
	cfg := config.NewClientConfiguration().
		WithCredentials(testIamCredentials(t, provider)).
		WithRequestTimeout(-time.Second)

	pool, err := NewClientPool(cfg, PoolConfig{MaxSize: 1})

	assert.Nil(t, pool)
	assert.EqualError(t, err, customCredentialProviderPoolError)
	assert.Equal(t, 0, calls)
	assert.Equal(t, beforeID, clientIDCounter.Load())
	assert.Equal(t, beforeRegistry, credentialProviderRegistrySize())
}

func TestNewClusterClientPoolRejectsCustomCredentialProviderBeforeNativeWork(t *testing.T) {
	beforeID := clientIDCounter.Load()
	beforeRegistry := credentialProviderRegistrySize()
	calls := 0
	provider := func() (config.AwsCredentials, error) {
		calls++
		return testCredentialProvider()
	}
	cfg := config.NewClusterClientConfiguration().
		WithCredentials(testIamCredentials(t, provider)).
		WithRequestTimeout(-time.Second)

	pool, err := NewClusterClientPool(cfg, PoolConfig{MaxSize: 1})

	assert.Nil(t, pool)
	assert.EqualError(t, err, customCredentialProviderPoolError)
	assert.Equal(t, 0, calls)
	assert.Equal(t, beforeID, clientIDCounter.Load())
	assert.Equal(t, beforeRegistry, credentialProviderRegistrySize())
}

func TestClientPoolsDoNotRejectOrdinaryIamAsCustomProvider(t *testing.T) {
	tests := map[string]func() error{
		"standalone": func() error {
			cfg := config.NewClientConfiguration().
				WithCredentials(testIamCredentials(t, nil)).
				WithRequestTimeout(-time.Second)
			_, err := NewClientPool(cfg, PoolConfig{MaxSize: 1})
			return err
		},
		"cluster": func() error {
			cfg := config.NewClusterClientConfiguration().
				WithCredentials(testIamCredentials(t, nil)).
				WithRequestTimeout(-time.Second)
			_, err := NewClusterClientPool(cfg, PoolConfig{MaxSize: 1})
			return err
		},
	}

	for name, run := range tests {
		t.Run(name, func(t *testing.T) {
			err := run()
			assert.EqualError(t, err, "setting request timeout returned an error: invalid duration was specified")
		})
	}
}
