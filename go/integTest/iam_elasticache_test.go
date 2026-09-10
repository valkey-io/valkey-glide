// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

// TestIamElastiCache tests IAM authentication with an ElastiCache serverless endpoint.
//
// This test intentionally does not use GlideTestSuite because it requires external AWS
// resources (an actual ElastiCache cluster). It is skipped automatically when the
// required environment variables are not set, making it safe to run in CI without
// real AWS credentials.
//
// Required environment variables:
//
//	IAM_ELASTICACHE_CLUSTER_NAME  – the ElastiCache cluster name used for IAM token signing
//	IAM_ELASTICACHE_ENDPOINT      – host:port (or host only; defaults to port 6379)
func TestIamElastiCache(t *testing.T) {
	clusterName := os.Getenv("IAM_ELASTICACHE_CLUSTER_NAME")
	if clusterName == "" {
		t.Skip("IAM_ELASTICACHE_CLUSTER_NAME not set; skipping IAM ElastiCache integration test")
	}
	endpoint := os.Getenv("IAM_ELASTICACHE_ENDPOINT")
	if endpoint == "" {
		t.Skip("IAM_ELASTICACHE_ENDPOINT not set; skipping IAM ElastiCache integration test")
	}

	// IAM configuration
	iamConfig := config.NewIamAuthConfig(
		clusterName,
		config.ElastiCache,
		"us-east-1",
	)

	// Server credentials with IAM auth
	credentials, err := config.NewServerCredentialsWithIam("iam-auth", iamConfig)
	assert.NoError(t, err, "Failed to create IAM credentials")

	// Client configuration with TLS and IAM auth
	clientConfig := config.NewClientConfiguration().
		WithAddress(&config.NodeAddress{
			Host: endpoint,
			Port: 6379,
		}).
		WithCredentials(credentials).
		WithUseTLS(true).
		WithRequestTimeout(5000)

	// Create client and test connection
	client, err := glide.NewClient(clientConfig)
	assert.NoError(t, err, "Failed to create client")
	defer client.Close()

	ctx := context.Background()

	// Basic ping test
	result, err := client.Ping(ctx)
	assert.NoError(t, err, "Ping failed")
	assert.Equal(t, "PONG", result, "Unexpected ping response")

	// Test basic operations
	setResult, err := client.Set(ctx, "test_key", "hello_elasticache")
	assert.NoError(t, err, "Set operation failed")
	assert.Equal(t, "OK", setResult, "Unexpected set response")

	getResult, err := client.Get(ctx, "test_key")
	assert.NoError(t, err, "Get operation failed")
	assert.Equal(t, "hello_elasticache", getResult.Value(), "Unexpected get response")
}
