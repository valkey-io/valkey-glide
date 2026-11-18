// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package config

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

// TestIamConfigCreation tests IAM configuration creation without requiring AWS credentials
func TestIamConfigCreation(t *testing.T) {
	// Test ElastiCache IAM configuration
	iamConfig := NewIamAuthConfig(
		"test-cluster",
		ElastiCache,
		"us-east-1",
	)
	assert.NotNil(t, iamConfig, "IAM config should not be nil")

	// Test MemoryDB IAM configuration with custom refresh interval
	memoryDBConfig := NewIamAuthConfig(
		"test-memorydb-cluster",
		MemoryDB,
		"us-west-2",
	).WithRefreshIntervalSeconds(600)
	assert.NotNil(t, memoryDBConfig, "MemoryDB IAM config should not be nil")

	// Test server credentials creation with IAM
	credentials, err := NewServerCredentialsWithIam("test-user", iamConfig)
	assert.NoError(t, err, "Failed to create IAM credentials")
	assert.NotNil(t, credentials, "Credentials should not be nil")

	// Test error case: empty username
	_, err = NewServerCredentialsWithIam("", iamConfig)
	assert.Error(t, err, "Should fail with empty username")

	// Test error case: nil IAM config
	_, err = NewServerCredentialsWithIam("test-user", nil)
	assert.Error(t, err, "Should fail with nil IAM config")
}
