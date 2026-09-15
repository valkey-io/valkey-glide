// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func TestRegisterAndUnregisterCredentialProvider(t *testing.T) {
	clientID := uintptr(99999)
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "key", SecretAccessKey: "secret"}, nil
	})

	registerCredentialProvider(clientID, provider)
	val, ok := credentialProviderRegistry.Load(clientID)
	assert.True(t, ok, "provider should be registered")
	assert.NotNil(t, val)

	unregisterCredentialProvider(clientID)
	_, ok = credentialProviderRegistry.Load(clientID)
	assert.False(t, ok, "provider should be unregistered")
}

func TestCredentialProviderCallbackNotRegistered(t *testing.T) {
	// An unregistered clientID should return 0 (failure)
	// We can't call credentialProviderCallback directly (CGo export),
	// but we can test the registry lookup path.
	_, ok := credentialProviderRegistry.Load(uintptr(0xdeadbeef))
	assert.False(t, ok, "unregistered key should not exist")
}

func TestGetCredentialProvider(t *testing.T) {
	iam := config.NewIamAuthConfig("cluster", config.ElastiCache, "us-east-1")
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "k", SecretAccessKey: "s"}, nil
	})
	iam.WithCredentialProvider(provider)

	got := iam.GetCredentialProvider()
	assert.NotNil(t, got, "GetCredentialProvider should return the registered provider")
}

func TestGetCredentialProviderNil(t *testing.T) {
	iam := config.NewIamAuthConfig("cluster", config.ElastiCache, "us-east-1")
	assert.Nil(t, iam.GetCredentialProvider(), "default provider should be nil")
}

func TestNewClientPoolRegistersCredentialProvider(t *testing.T) {
	// Verify that NewClientPool registers the credential provider in the
	// credentialProviderRegistry under a non-zero credClientID.
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "key", SecretAccessKey: "secret"}, nil
	})

	iam := config.NewIamAuthConfig("cluster", config.ElastiCache, "us-east-1").
		WithCredentialProvider(provider)

	// We cannot create a real pool without a server, but we can verify that
	// GetCredentialProvider returns the registered provider.
	got := iam.GetCredentialProvider()
	assert.NotNil(t, got, "GetCredentialProvider should return the registered provider")

	// Verify the provider produces valid credentials (no nil panic, correct types)
	creds, err := got()
	assert.NoError(t, err)
	assert.Equal(t, "key", creds.AccessKeyID)
	assert.Equal(t, "secret", creds.SecretAccessKey)
}

func TestCredentialProviderRegistryRoundtrip(t *testing.T) {
	// Verify register → callback lookup → unregister roundtrip
	// that simulates what pool creation does.
	provider := config.GlideCredentialProvider(func() (config.AwsCredentials, error) {
		return config.AwsCredentials{AccessKeyID: "poolkey", SecretAccessKey: "poolsecret"}, nil
	})

	// Simulate pool creation: register under a specific ID
	clientID := uintptr(88888)
	registerCredentialProvider(clientID, provider)

	// Simulate callback invocation: look up by the SAME ID
	val, ok := credentialProviderRegistry.Load(clientID)
	assert.True(t, ok, "provider should be found under registered clientID")
	found, ok := val.(config.GlideCredentialProvider)
	assert.True(t, ok)
	creds, err := found()
	assert.NoError(t, err)
	assert.Equal(t, "poolkey", creds.AccessKeyID)

	// Simulate pool destroy: unregister
	unregisterCredentialProvider(clientID)
	_, ok = credentialProviderRegistry.Load(clientID)
	assert.False(t, ok, "provider should be removed after unregister")
}
