// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"github.com/stretchr/testify/assert"
)

func (suite *GlideTestSuite) TestGetStatistics() {
	client := suite.defaultClient()
	defer client.Close()

	stats := client.GetStatistics()

	// Verify all expected keys are present
	expectedKeys := []string{
		"total_connections",
		"total_clients",
		"total_values_compressed",
		"total_values_decompressed",
		"total_original_bytes",
		"total_bytes_compressed",
		"total_bytes_decompressed",
		"compression_skipped_count",
		"subscription_out_of_sync_count",
		"subscription_last_sync_timestamp",
	}

	for _, key := range expectedKeys {
		_, exists := stats[key]
		assert.True(suite.T(), exists, "Expected key %s to exist in statistics", key)
	}

	// Verify we have at least one connection and one client
	assert.GreaterOrEqual(suite.T(), stats["total_connections"], uint64(1), "Should have at least 1 connection")
	assert.GreaterOrEqual(suite.T(), stats["total_clients"], uint64(1), "Should have at least 1 client")
}

func (suite *GlideTestSuite) TestGetStatisticsCluster() {
	client := suite.defaultClusterClient()
	defer client.Close()

	stats := client.GetStatistics()

	// Verify all expected keys are present
	expectedKeys := []string{
		"total_connections",
		"total_clients",
		"total_values_compressed",
		"total_values_decompressed",
		"total_original_bytes",
		"total_bytes_compressed",
		"total_bytes_decompressed",
		"compression_skipped_count",
		"subscription_out_of_sync_count",
		"subscription_last_sync_timestamp",
	}

	for _, key := range expectedKeys {
		_, exists := stats[key]
		assert.True(suite.T(), exists, "Expected key %s to exist in statistics", key)
	}

	// Verify we have at least one connection and one client
	assert.GreaterOrEqual(suite.T(), stats["total_connections"], uint64(1), "Should have at least 1 connection")
	assert.GreaterOrEqual(suite.T(), stats["total_clients"], uint64(1), "Should have at least 1 client")
}
