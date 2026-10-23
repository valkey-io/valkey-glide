// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// TestIamConfigCreation tests IAM configuration creation without requiring AWS credentials
func TestIamConfigCreation(t *testing.T) {
	// Test ElastiCache IAM configuration
	iamConfig := config.NewIamAuthConfig(
		"test-cluster",
		config.ElastiCache,
		"us-east-1",
	)
	assert.NotNil(t, iamConfig, "IAM config should not be nil")

	// Test MemoryDB IAM configuration with custom refresh interval
	memoryDBConfig := config.NewIamAuthConfig(
		"test-memorydb-cluster",
		config.MemoryDB,
		"us-west-2",
	).WithRefreshIntervalSeconds(600)
	assert.NotNil(t, memoryDBConfig, "MemoryDB IAM config should not be nil")

	// Test server credentials creation with IAM
	credentials, err := config.NewServerCredentialsWithIam("test-user", iamConfig)
	assert.NoError(t, err, "Failed to create IAM credentials")
	assert.NotNil(t, credentials, "Credentials should not be nil")

	// Test error case: empty username
	_, err = config.NewServerCredentialsWithIam("", iamConfig)
	assert.Error(t, err, "Should fail with empty username")

	// Test error case: nil IAM config
	_, err = config.NewServerCredentialsWithIam("test-user", nil)
	assert.Error(t, err, "Should fail with nil IAM config")
}
