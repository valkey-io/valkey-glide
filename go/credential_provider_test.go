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
