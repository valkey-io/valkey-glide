// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

package integTest

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
	"github.com/valkey-io/valkey-glide/go/v2/interfaces"
)

// Helper interface for cache operations
type CacheClient interface {
	interfaces.BaseClientCommands
	GetCacheHitRate(ctx context.Context) (float64, error)
	GetCacheMissRate(ctx context.Context) (float64, error)
	GetCacheEntryCount(ctx context.Context) (int64, error)
	GetCacheEvictions(ctx context.Context) (int64, error)
	GetCacheExpirations(ctx context.Context) (int64, error)
	GetCacheTotalLookups(ctx context.Context) (int64, error)
}

const (
	// defaultTestCacheKb is the default cache size in KB used across cache tests.
	defaultTestCacheKb uint64 = 1
	// defaultTestTtlMs is the default TTL in milliseconds used across cache tests.
	defaultTestTtlMs uint64 = 60000
)

// Helper function to create a client with cache configuration
func (suite *GlideTestSuite) createClientWithCache(
	baseClient interfaces.BaseClientCommands,
	cache *config.ClientSideCache,
) (CacheClient, error) {
	switch baseClient.(type) {
	case *glide.Client:
		clientConfig := suite.defaultClientConfig().WithClientSideCache(cache)
		client, err := suite.client(clientConfig)
		return client, err
	case *glide.ClusterClient:
		clientConfig := suite.defaultClusterClientConfig().WithClientSideCache(cache)
		client, err := suite.clusterClient(clientConfig)
		return client, err
	default:
		return nil, assert.AnError
	}
}

func (suite *GlideTestSuite) TestClientSideCache_BasicCacheHitWithMetrics() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with metrics enabled
		cache, err := config.NewClientSideCache(defaultTestCacheKb, defaultTestTtlMs)
		require.NoError(suite.T(), err)
		cache.WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "cache_test_key_" + uuid.New().String()

		// Set a key
		result, err := testClient.Set(ctx, key, "cache_test_value")
		suite.verifyOK(result, err)

		// First GET - cache miss
		value, err := testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "cache_test_value", value.Value())

		// Entry count should be 1
		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), entryCount)

		// Second GET - cache hit
		value, err = testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "cache_test_value", value.Value())

		// Third GET - cache hit
		value, err = testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "cache_test_value", value.Value())

		// Verify metrics: 1 miss + 2 hits = 3 total
		hitRate, err := testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.InDelta(suite.T(), 2.0/3.0, hitRate, 0.001) // 66.67% hit rate

		missRate, err := testClient.GetCacheMissRate(ctx)
		assert.NoError(suite.T(), err)
		assert.InDelta(suite.T(), 1.0/3.0, missRate, 0.001) // 33.33% miss rate

		// Rates should sum to 1.0
		assert.InDelta(suite.T(), 1.0, hitRate+missRate, 0.0001)

		// Verify total lookups: 1 miss + 2 hits = 3
		totalLookups, err := testClient.GetCacheTotalLookups(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), totalLookups, "Expected 3 total lookups (1 miss + 2 hits)")
	})
}

func (suite *GlideTestSuite) TestClientSideCache_WithoutMetrics() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with metrics disabled
		cache, err := config.NewClientSideCache(defaultTestCacheKb, defaultTestTtlMs)
		require.NoError(suite.T(), err)
		cache.WithMetrics(false)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "key_" + uuid.New().String()

		// Cache should work
		result, err := testClient.Set(ctx, key, "value")
		suite.verifyOK(result, err)

		value, err := testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		// Should be cached
		value, err = testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		// Metrics should fail
		_, err = testClient.GetCacheHitRate(ctx)
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), strings.ToLower(err.Error()), "metrics")

		_, err = testClient.GetCacheMissRate(ctx)
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), strings.ToLower(err.Error()), "metrics")

		_, err = testClient.GetCacheEvictions(ctx)
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), strings.ToLower(err.Error()), "metrics")

		_, err = testClient.GetCacheExpirations(ctx)
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), strings.ToLower(err.Error()), "metrics")

		_, err = testClient.GetCacheTotalLookups(ctx)
		assert.Error(suite.T(), err)
		assert.Contains(suite.T(), strings.ToLower(err.Error()), "metrics")

		// Entry count should still work
		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), entryCount)
	})
}

func (suite *GlideTestSuite) TestClientSideCache_NilValuesNotCached() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with metrics enabled
		cache, err := config.NewClientSideCache(defaultTestCacheKb, defaultTestTtlMs)
		require.NoError(suite.T(), err)
		cache.WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "nonexistent_key_" + uuid.New().String()

		// GET non-existent key (returns nil)
		value, err := testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), value.IsNil())

		// Entry count should be 0
		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(0), entryCount)

		// GET again - should NOT be cached (NIL values not cached)
		value, err = testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.True(suite.T(), value.IsNil())

		// Miss rate should be 100%
		missRate, err := testClient.GetCacheMissRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 1.0, missRate)

		// Total lookups should be 2
		totalLookups, err := testClient.GetCacheTotalLookups(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), totalLookups, "Expected 2 total lookups")
	})
}

func (suite *GlideTestSuite) TestClientSideCache_TTLExpiration() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with short TTL
		cache, err := config.NewClientSideCache(defaultTestCacheKb, 2000) // 2 seconds
		require.NoError(suite.T(), err)
		cache.WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "ttl_key_" + uuid.New().String()

		// Set and GET
		result, err := testClient.Set(ctx, key, "ttl_value")
		suite.verifyOK(result, err)

		value, err := testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "ttl_value", value.Value())

		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), entryCount)

		// Second GET - from cache
		value, err = testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "ttl_value", value.Value())

		// Wait for TTL to expire
		time.Sleep(3 * time.Second)

		// GET after expiration - should fetch from server again
		value, err = testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "ttl_value", value.Value())

		// Expiration count should be 1
		expirations, err := testClient.GetCacheExpirations(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), expirations)

		// Miss rate should be 2 misses out of 3 total = 66.67%
		missRate, err := testClient.GetCacheMissRate(ctx)
		assert.NoError(suite.T(), err)
		assert.InDelta(suite.T(), 2.0/3.0, missRate, 0.001)

		// Total lookups should be 3
		totalLookups, err := testClient.GetCacheTotalLookups(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), totalLookups, "Expected 3 total lookups")
	})
}

func (suite *GlideTestSuite) TestClientSideCache_MultipleKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with metrics enabled
		cache, err := config.NewClientSideCache(defaultTestCacheKb, defaultTestTtlMs)
		require.NoError(suite.T(), err)
		cache.WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		prefix := uuid.New().String()[:8]

		// Set 3 keys
		for i := 1; i <= 3; i++ {
			key := fmt.Sprintf("key%d_%s", i, prefix)
			value := fmt.Sprintf("value%d", i)
			result, err := testClient.Set(ctx, key, value)
			suite.verifyOK(result, err)
		}

		// GET each key twice (miss + hit)
		for i := 1; i <= 3; i++ {
			key := fmt.Sprintf("key%d_%s", i, prefix)
			expectedValue := fmt.Sprintf("value%d", i)

			// First GET - miss
			value, err := testClient.Get(ctx, key)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), expectedValue, value.Value())

			// Second GET - hit
			value, err = testClient.Get(ctx, key)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), expectedValue, value.Value())
		}

		// Entry count should be 3
		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), entryCount)

		// Verify metrics: 3 misses + 3 hits = 50% hit rate
		hitRate, err := testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 0.5, hitRate)

		// Total lookups should be 6
		totalLookups, err := testClient.GetCacheTotalLookups(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(6), totalLookups, "Expected 6 total lookups (3 misses + 3 hits)")
	})
}

func (suite *GlideTestSuite) TestClientSideCache_NoCacheMetrics() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// No cache configured - use default client
		ctx := context.Background()
		key := "key_" + uuid.New().String()

		// Set and GET multiple times
		result, err := client.Set(ctx, key, "value")
		suite.verifyOK(result, err)

		value, err := client.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		value, err = client.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		value, err = client.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		// Metrics should error - need to cast to concrete type to access cache methods
		switch c := client.(type) {
		case *glide.Client:
			_, err = c.GetCacheHitRate(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheMissRate(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheEvictions(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheExpirations(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheTotalLookups(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheEntryCount(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

		case *glide.ClusterClient:
			_, err = c.GetCacheHitRate(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheMissRate(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheEvictions(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheExpirations(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheTotalLookups(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")

			_, err = c.GetCacheEntryCount(ctx)
			assert.Error(suite.T(), err)
			assert.Contains(suite.T(), strings.ToLower(err.Error()), "not enabled")
		}
	})
}

func (suite *GlideTestSuite) TestClientSideCache_EvictionPolicyLRU() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with LRU eviction
		cache, err := config.NewClientSideCache(defaultTestCacheKb, 0) // no TTL, to force eviction
		require.NoError(suite.T(), err)
		cache.WithEvictionPolicy(config.EvictionPolicyLRU).WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		prefix := uuid.New().String()[:8]

		// Use larger values to force eviction
		value := strings.Repeat("x", 250) // ~250 bytes

		// Set and cache 3 keys
		for i := 1; i <= 3; i++ {
			key := fmt.Sprintf("lru_key%d_%s", i, prefix)
			result, err := testClient.Set(ctx, key, value)
			suite.verifyOK(result, err)

			retrievedValue, err := testClient.Get(ctx, key)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), value, retrievedValue.Value())
		}

		// Cache should have 3 entries now
		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), entryCount)

		// Access key1 to make it recently used
		key1 := fmt.Sprintf("lru_key1_%s", prefix)
		retrievedValue, err := testClient.Get(ctx, key1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())

		// Add 2 more keys - should evict key2 and key3 (least recently used)
		for i := 4; i <= 5; i++ {
			key := fmt.Sprintf("lru_key%d_%s", i, prefix)
			result, err := testClient.Set(ctx, key, value)
			suite.verifyOK(result, err)

			retrievedValue, err := testClient.Get(ctx, key)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), value, retrievedValue.Value())
		}

		// Verify 2 evictions occurred
		evictions, err := testClient.GetCacheEvictions(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), evictions)

		// Verify cache is working (hit rate > 0)
		hitRate, err := testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), hitRate, 0.0)

		// Check that key1 is still cached
		retrievedValue, err = testClient.Get(ctx, key1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())

		newHitRate, err := testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), newHitRate, hitRate)

		// Check that key2 and key3 are evicted
		oldMissRate, err := testClient.GetCacheMissRate(ctx)
		assert.NoError(suite.T(), err)

		key2 := fmt.Sprintf("lru_key2_%s", prefix)
		retrievedValue, err = testClient.Get(ctx, key2)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())

		key3 := fmt.Sprintf("lru_key3_%s", prefix)
		retrievedValue, err = testClient.Get(ctx, key3)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())

		newMissRate, err := testClient.GetCacheMissRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), newMissRate, oldMissRate)
	})
}

func (suite *GlideTestSuite) TestClientSideCache_EvictionPolicyLFU() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with LFU eviction
		cache, err := config.NewClientSideCache(defaultTestCacheKb, 0) // small cache to trigger evictions
		require.NoError(suite.T(), err)
		cache.WithEvictionPolicy(config.EvictionPolicyLFU).WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		prefix := uuid.New().String()[:8]
		value := strings.Repeat("x", 250) // ~250 bytes

		key1 := fmt.Sprintf("key1_%s", prefix)
		key2 := fmt.Sprintf("key2_%s", prefix)
		key3 := fmt.Sprintf("key3_%s", prefix)
		key4 := fmt.Sprintf("key4_%s", prefix)

		// Set key1 and access it multiple times (high frequency)
		result, err := testClient.Set(ctx, key1, value)
		suite.verifyOK(result, err)

		for i := 0; i < 5; i++ {
			retrievedValue, err := testClient.Get(ctx, key1)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), value, retrievedValue.Value())
		}
		// key1 frequency: 5

		// Set key2 and access it a few times (medium frequency)
		result, err = testClient.Set(ctx, key2, value)
		suite.verifyOK(result, err)

		for i := 0; i < 2; i++ {
			retrievedValue, err := testClient.Get(ctx, key2)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), value, retrievedValue.Value())
		}
		// key2 frequency: 2

		// Set key3 with minimal access (low frequency)
		result, err = testClient.Set(ctx, key3, value)
		suite.verifyOK(result, err)

		retrievedValue, err := testClient.Get(ctx, key3)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())
		// key3 frequency: 1

		// Verify cache is working
		hitRate, err := testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), hitRate, 0.0)

		// Cache should have 3 entries now
		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), entryCount)

		// Set key4 - this should trigger eviction of key3 (lowest frequency)
		result, err = testClient.Set(ctx, key4, value)
		suite.verifyOK(result, err)

		retrievedValue, err = testClient.Get(ctx, key4)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())
		// key4 frequency: 1

		// Check that cache entry count is still 3
		entryCount, err = testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), entryCount)

		// Verify 1 eviction occurred
		evictions, err := testClient.GetCacheEvictions(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), evictions)

		// Check that key1 (highest frequency) is still cached
		oldHitRate, err := testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)

		retrievedValue, err = testClient.Get(ctx, key1)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())

		newHitRate, err := testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), newHitRate, oldHitRate)

		// Check that key3 (lowest frequency) was evicted
		oldMissRate, err := testClient.GetCacheMissRate(ctx)
		assert.NoError(suite.T(), err)

		retrievedValue, err = testClient.Get(ctx, key3) // Should be a miss
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())

		newMissRate, err := testClient.GetCacheMissRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), newMissRate, oldMissRate)

		// key2 (medium frequency) should still be cached
		oldHitRate, err = testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)

		retrievedValue, err = testClient.Get(ctx, key2)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), value, retrievedValue.Value())

		newHitRate, err = testClient.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), newHitRate, oldHitRate)
	})
}

func (suite *GlideTestSuite) TestClientSideCache_MaxMemoryLimit() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration with small memory limit
		cache, err := config.NewClientSideCache(defaultTestCacheKb, 0) // 1 KB
		require.NoError(suite.T(), err)
		cache.WithEvictionPolicy(config.EvictionPolicyLRU).WithMetrics(true)

		testClient1, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		testClient2, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()

		// Create smaller values to avoid Redis server eviction - use ~400 bytes each
		largeValue := strings.Repeat("x", 400)

		var keys []string

		// Add 10 keys to force cache eviction
		for i := 1; i <= 10; i++ {
			key := fmt.Sprintf("key%d_%s", i, uuid.New().String()[:8])
			keys = append(keys, key)

			result, err := testClient1.Set(ctx, key, largeValue)
			suite.verifyOK(result, err)

			retrievedValue, err := testClient1.Get(ctx, key)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), largeValue, retrievedValue.Value())

			retrievedValue, err = testClient1.Get(ctx, key)
			assert.NoError(suite.T(), err)
			assert.Equal(suite.T(), largeValue, retrievedValue.Value())
		}

		currentHitRate, err := testClient1.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), currentHitRate, 0.0)

		// Check that key1 should be evicted from cache but still exist in Redis
		// Use testClient2 to check - this should be a cache miss
		retrievedValue, err := testClient2.Get(ctx, keys[0]) // key1
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), largeValue, retrievedValue.Value())

		hitRate, err := testClient2.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Less(suite.T(), hitRate, currentHitRate)

		// Check that key10 should still be in cache
		retrievedValue, err = testClient2.Get(ctx, keys[9]) // key10
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), largeValue, retrievedValue.Value())

		newHitRate, err := testClient2.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), newHitRate, hitRate)

		// Verify that evictions occurred due to max memory limit
		evictions, err := testClient1.GetCacheEvictions(ctx)
		assert.NoError(suite.T(), err)
		assert.Greater(suite.T(), evictions, int64(0))
	})
}

func (suite *GlideTestSuite) TestClientSideCache_SharedCache() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create a single cache configuration. When multiple clients share the same
		// ClientSideCache instance, they share both the cached data and the metrics
		// (hit/miss counters). This means operations from any client contribute to
		// the same metrics pool.
		cache, err := config.NewClientSideCache(defaultTestCacheKb, 0)
		require.NoError(suite.T(), err)
		cache.WithMetrics(true)

		testClient1, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		testClient2, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "shared_key_" + uuid.New().String()

		result, err := testClient1.Set(ctx, key, "value")
		suite.verifyOK(result, err)

		value, err := testClient1.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		// Entry count should be 1
		entryCount, err := testClient2.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), entryCount)

		value, err = testClient2.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		// Both clients share the same metrics: 1 miss (client1) + 1 hit (client2) = 50% hit rate
		hitRate2, err := testClient2.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 0.5, hitRate2)

		hitRate1, err := testClient1.GetCacheHitRate(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), 0.5, hitRate1)

		// Total lookups should be 2
		totalLookups, err := testClient1.GetCacheTotalLookups(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), totalLookups, "Expected 2 total lookups on shared cache")
	})
}

func (suite *GlideTestSuite) TestClientSideCache_ErrorHandling() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration
		cache, err := config.NewClientSideCache(defaultTestCacheKb, 0)
		require.NoError(suite.T(), err)
		cache.WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "string-key-" + uuid.New().String()

		result, err := testClient.Set(ctx, key, "value")
		suite.verifyOK(result, err)

		value, err := testClient.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		// Try to use HGETALL on a string key - should error
		_, err = testClient.HGetAll(ctx, key)
		assert.Error(suite.T(), err)
	})
}

func (suite *GlideTestSuite) TestClientSideCache_CacheableCommands() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		// Create cache configuration
		cache, err := config.NewClientSideCache(defaultTestCacheKb, 0)
		require.NoError(suite.T(), err)
		cache.WithMetrics(true)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		prefix := uuid.New().String()[:8]
		stringKey := fmt.Sprintf("key_%s", prefix)
		hashKey := fmt.Sprintf("hash_%s", prefix)
		setKey := fmt.Sprintf("setkey_%s", prefix)

		// SET command - not cacheable
		result, err := testClient.Set(ctx, stringKey, "value")
		suite.verifyOK(result, err)

		// GET command - cacheable
		value, err := testClient.Get(ctx, stringKey)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "value", value.Value())

		// Check that now the cache entry count is 1
		entryCount, err := testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), entryCount)

		// HGETALL command - cacheable
		count, err := testClient.HSet(ctx, hashKey, map[string]string{"field1": "val1"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), count)

		hashValue, err := testClient.HGetAll(ctx, hashKey)
		assert.NoError(suite.T(), err)
		expected := map[string]string{"field1": "val1"}
		assert.Equal(suite.T(), expected, hashValue)

		entryCount, err = testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(2), entryCount)

		// SMEMBERS command - cacheable
		count, err = testClient.SAdd(ctx, setKey, []string{"member1"})
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(1), count)

		members, err := testClient.SMembers(ctx, setKey)
		assert.NoError(suite.T(), err)
		expectedMembers := map[string]struct{}{"member1": {}}
		assert.Equal(suite.T(), expectedMembers, members)

		entryCount, err = testClient.GetCacheEntryCount(ctx)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), int64(3), entryCount)
	})
}
