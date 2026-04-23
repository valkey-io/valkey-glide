// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package glide

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func TestCacheMetrics_WithoutCaching(t *testing.T) {
	// Test that cache metrics methods return appropriate errors when caching is not enabled
	config := config.NewClientConfiguration()
	client, err := NewClient(config)
	if err != nil {
		t.Skip("Failed to create client, skipping cache metrics test")
	}
	defer client.Close()

	ctx := context.Background()

	// Test GetCacheHitRate
	_, err = client.GetCacheHitRate(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheMissRate
	_, err = client.GetCacheMissRate(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheEntryCount
	_, err = client.GetCacheEntryCount(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheEvictions
	_, err = client.GetCacheEvictions(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheExpirations
	_, err = client.GetCacheExpirations(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheTotalLookups
	_, err = client.GetCacheTotalLookups(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")
}

func TestCacheMetrics_WithCachingButMetricsDisabled(t *testing.T) {
	// Test that hit/miss rate methods return appropriate errors when metrics are disabled
	cache, err := config.NewClientSideCache(1024, 0)
	require.NoError(t, err)
	cache.WithMetrics(false)
	clientConfig := config.NewClientConfiguration().WithClientSideCache(cache)
	client, err := NewClient(clientConfig)
	if err != nil {
		t.Skip("Failed to create client with cache, skipping cache metrics test")
	}
	defer client.Close()

	ctx := context.Background()

	// Test GetCacheHitRate - should fail when metrics are disabled
	_, err = client.GetCacheHitRate(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "metrics not enabled")

	// Test GetCacheMissRate - should fail when metrics are disabled
	_, err = client.GetCacheMissRate(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "metrics not enabled")

	// Test GetCacheEntryCount - should work even when metrics are disabled
	entryCount, err := client.GetCacheEntryCount(ctx)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, entryCount, int64(0))

	// Test GetCacheEvictions - should fail when metrics are disabled
	_, err = client.GetCacheEvictions(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "metrics not enabled")

	// Test GetCacheExpirations - should fail when metrics are disabled
	_, err = client.GetCacheExpirations(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "metrics not enabled")

	// Test GetCacheTotalLookups - should fail when metrics are disabled
	_, err = client.GetCacheTotalLookups(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "metrics not enabled")
}

func TestCacheMetrics_WithCachingAndMetricsEnabled(t *testing.T) {
	// Test that cache metrics methods work when caching and metrics are enabled
	cache, err := config.NewClientSideCache(1024, 0)
	require.NoError(t, err)
	cache.WithMetrics(true)
	clientConfig := config.NewClientConfiguration().WithClientSideCache(cache)
	client, err := NewClient(clientConfig)
	if err != nil {
		t.Skip("Failed to create client with cache and metrics, skipping cache metrics test")
	}
	defer client.Close()

	ctx := context.Background()

	// Test GetCacheHitRate
	hitRate, err := client.GetCacheHitRate(ctx)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, hitRate, 0.0)
	assert.LessOrEqual(t, hitRate, 1.0)

	// Test GetCacheMissRate
	missRate, err := client.GetCacheMissRate(ctx)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, missRate, 0.0)
	assert.LessOrEqual(t, missRate, 1.0)

	// Test GetCacheEntryCount
	entryCount, err := client.GetCacheEntryCount(ctx)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, entryCount, int64(0))

	// Test GetCacheEvictions
	evictions, err := client.GetCacheEvictions(ctx)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, evictions, int64(0))

	// Test GetCacheExpirations
	expirations, err := client.GetCacheExpirations(ctx)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, expirations, int64(0))

	// Test GetCacheTotalLookups
	totalLookups, err := client.GetCacheTotalLookups(ctx)
	assert.NoError(t, err)
	assert.GreaterOrEqual(t, totalLookups, int64(0))
}

func TestCacheMetrics_ClusterClient_WithoutCaching(t *testing.T) {
	// Test that cache metrics methods return appropriate errors when caching is not enabled for cluster client
	config := config.NewClusterClientConfiguration()
	client, err := NewClusterClient(config)
	if err != nil {
		t.Skip("Failed to create cluster client, skipping cache metrics test")
	}
	defer client.Close()

	ctx := context.Background()

	// Test GetCacheHitRate
	_, err = client.GetCacheHitRate(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheMissRate
	_, err = client.GetCacheMissRate(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheEntryCount
	_, err = client.GetCacheEntryCount(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheEvictions
	_, err = client.GetCacheEvictions(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheExpirations
	_, err = client.GetCacheExpirations(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")

	// Test GetCacheTotalLookups
	_, err = client.GetCacheTotalLookups(ctx)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "caching not enabled")
}

func TestNewClientSideCache_ZeroMaxCacheKb(t *testing.T) {
	// Test that NewClientSideCache returns an error when maxCacheKb is 0
	cache, err := config.NewClientSideCache(0, 60000)
	assert.Nil(t, cache)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "maxCacheKb must be positive")
}
