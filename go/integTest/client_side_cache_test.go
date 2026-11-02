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
	"github.com/valkey-io/valkey-glide/go/v2/models"
	"github.com/valkey-io/valkey-glide/go/v2/options"
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
	ClientTrackingInfo(ctx context.Context) (models.ClientTrackingInfo, error)
	FlushAll(ctx context.Context) (string, error)
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

// writerClientFor returns a cache-less client of the same kind (standalone or cluster) as baseClient,
// used to modify keys from "outside" the cached clients under test.
func (suite *GlideTestSuite) writerClientFor(baseClient interfaces.BaseClientCommands) interfaces.BaseClientCommands {
	switch baseClient.(type) {
	case *glide.ClusterClient:
		return suite.defaultClusterClient()
	default:
		return suite.defaultClient()
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

func (suite *GlideTestSuite) TestClientSideCache_ServerAssisted_Invalidation() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cache, err := config.NewClientSideCache(defaultTestCacheKb, defaultTestTtlMs)
		require.NoError(suite.T(), err)
		cache.WithServerAssisted(true)

		testClientA, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "server_assisted_inv_" + uuid.New().String()

		// Client A caches the key
		result, err := testClientA.Set(ctx, key, "v1")
		suite.verifyOK(result, err)

		value, err := testClientA.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "v1", value.Value()) // miss, populates cache

		value, err = testClientA.Get(ctx, key)
		assert.NoError(suite.T(), err)
		assert.Equal(suite.T(), "v1", value.Value()) // hit

		// Client B modifies the key — triggers server invalidation to Client A
		switch client.(type) {
		case *glide.Client:
			clientB := suite.defaultClient()
			_, err = clientB.Set(ctx, key, "v2")
			assert.NoError(suite.T(), err)
		case *glide.ClusterClient:
			clientB := suite.defaultClusterClient()
			_, err = clientB.Set(ctx, key, "v2")
			assert.NoError(suite.T(), err)
		}

		// Poll until invalidation is processed and Client A sees the new value
		suite.waitFor(func() bool {
			val, err := testClientA.Get(ctx, key)
			return err == nil && val.Value() == "v2"
		}, "Cache was not invalidated after key was modified by another client")
	})
}

// ==================== Tracking prefix ====================

// newPrefixedCache builds a server-assisted cache configuration with metrics that only tracks keys under prefix.
func newPrefixedCache(t require.TestingT, prefix string) *config.ClientSideCache {
	cache, err := config.NewClientSideCache(1024, 0)
	require.NoError(t, err)
	cache.WithMetrics(true).WithServerAssisted(true).WithTrackingPrefix(prefix)
	return cache
}

// cacheEntries returns the number of entries in the client's cache.
func (suite *GlideTestSuite) cacheEntries(ctx context.Context, client CacheClient) int64 {
	suite.T().Helper()
	count, err := client.GetCacheEntryCount(ctx)
	require.NoError(suite.T(), err)
	return count
}

// cacheLookups returns the total number of cache lookups (hits + misses) of the client's cache.
func (suite *GlideTestSuite) cacheLookups(ctx context.Context, client CacheClient) int64 {
	suite.T().Helper()
	lookups, err := client.GetCacheTotalLookups(ctx)
	require.NoError(suite.T(), err)
	return lookups
}

// cacheRates returns the hit and miss rates of the client's cache.
func (suite *GlideTestSuite) cacheRates(ctx context.Context, client CacheClient) (float64, float64) {
	suite.T().Helper()
	hitRate, err := client.GetCacheHitRate(ctx)
	require.NoError(suite.T(), err)
	missRate, err := client.GetCacheMissRate(ctx)
	require.NoError(suite.T(), err)
	return hitRate, missRate
}

// readUntilCached reads key through the cached client until the cache holds exactly want entries and
// returns the value read last.
//
// A write to a tracked key makes the server broadcast an invalidation on every tracking connection. In
// cluster mode the client holds several connections per shard (primary, replicas, management connection),
// so the same write yields several invalidations that are delivered at different times. One of them may
// arrive after a later GET repopulated the cache and evict the fresh entry; reading again repopulates it
// once all invalidations have been delivered. Call this after every write to a tracked key before
// asserting on the entry count.
func (suite *GlideTestSuite) readUntilCached(ctx context.Context, client CacheClient, key string, want int64) string {
	suite.T().Helper()
	var last string
	suite.waitFor(func() bool {
		value, err := client.Get(ctx, key)
		if err != nil || value.IsNil() {
			return false
		}
		last = value.Value()
		return suite.cacheEntries(ctx, client) == want
	}, fmt.Sprintf("cache never settled at %d entries after reading %s", want, key))
	return last
}

// settleCacheAfter repeatedly runs read until the cache holds exactly want entries. See readUntilCached for
// why a write to a tracked key needs this before asserting on the entry count.
func (suite *GlideTestSuite) settleCacheAfter(ctx context.Context, client CacheClient, want int64, read func() error) {
	suite.T().Helper()
	suite.waitFor(func() bool {
		return read() == nil && suite.cacheEntries(ctx, client) == want
	}, fmt.Sprintf("cache never settled at %d entries", want))
}

// getValue reads key and returns its string value, failing the test on error or nil.
func (suite *GlideTestSuite) getValue(ctx context.Context, client interfaces.BaseClientCommands, key string) string {
	suite.T().Helper()
	value, err := client.Get(ctx, key)
	require.NoError(suite.T(), err)
	require.False(suite.T(), value.IsNil(), "unexpected nil value for key %s", key)
	return value.Value()
}

// mgetValues runs MGET and returns the values in order, failing the test on error or nil entries.
func (suite *GlideTestSuite) mgetValues(ctx context.Context, client interfaces.BaseClientCommands, keys ...string) []string {
	suite.T().Helper()
	results, err := client.MGet(ctx, keys)
	require.NoError(suite.T(), err)
	require.Len(suite.T(), results, len(keys))
	values := make([]string, len(results))
	for i, result := range results {
		require.False(suite.T(), result.IsNil(), "unexpected nil value for key %s", keys[i])
		values[i] = result.Value()
	}
	return values
}

// (a) The configured prefix must be registered on every server connection, so the server only broadcasts
// invalidations for keys under that prefix.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_RegisteredWithServer() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cache := newPrefixedCache(suite.T(), "xxx:")
		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		info, err := testClient.ClientTrackingInfo(ctx)
		require.NoError(suite.T(), err)
		assert.Contains(suite.T(), info.Flags, "on")
		assert.Contains(suite.T(), info.Flags, "bcast")
		assert.Equal(suite.T(), []string{"xxx:"}, info.Prefixes)

		// In cluster mode every node connection must carry the same prefix.
		if clusterClient, ok := testClient.(*glide.ClusterClient); ok {
			route := options.RouteOption{Route: config.Route(config.AllNodes)}
			multi, err := clusterClient.ClientTrackingInfoWithOptions(ctx, route)
			require.NoError(suite.T(), err)
			require.True(suite.T(), multi.IsMultiValue())
			require.NotEmpty(suite.T(), multi.MultiValue())
			for node, nodeInfo := range multi.MultiValue() {
				assert.Contains(suite.T(), nodeInfo.Flags, "on", node)
				assert.Contains(suite.T(), nodeInfo.Flags, "bcast", node)
				assert.Equal(suite.T(), []string{"xxx:"}, nodeInfo.Prefixes, node)
			}
		}
	})
}

// Without a prefix the server tracks every key (the empty prefix): pre-existing behaviour must not change.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_NoPrefixTracksAllKeys() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cache, err := config.NewClientSideCache(defaultTestCacheKb, defaultTestTtlMs)
		require.NoError(suite.T(), err)
		cache.WithServerAssisted(true)
		assert.Equal(suite.T(), "", cache.TrackingPrefix)

		testClient, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		info, err := testClient.ClientTrackingInfo(context.Background())
		require.NoError(suite.T(), err)
		assert.Contains(suite.T(), info.Flags, "bcast")
		assert.Equal(suite.T(), []string{""}, info.Prefixes, "empty prefix = all keys")
	})
}

// (b) Two clients with different prefixes: each one caches and is invalidated only for its own namespace,
// and keys outside the prefix are read from the server and never cached.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_ScopesInvalidationsBetweenClients() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		clientA, err := suite.createClientWithCache(client, newPrefixedCache(suite.T(), "xxx:"))
		require.NoError(suite.T(), err)
		clientB, err := suite.createClientWithCache(client, newPrefixedCache(suite.T(), "yyy:"))
		require.NoError(suite.T(), err)
		writer := suite.writerClientFor(client)

		ctx := context.Background()
		suffix := uuid.New().String()
		keyA := "xxx:" + suffix
		keyB := "yyy:" + suffix

		result, err := writer.Set(ctx, keyA, "a1")
		suite.verifyOK(result, err)
		result, err = writer.Set(ctx, keyB, "b1")
		suite.verifyOK(result, err)

		// Each client caches its own namespace.
		assert.Equal(suite.T(), "a1", suite.readUntilCached(ctx, clientA, keyA, 1))
		assert.Equal(suite.T(), "b1", suite.readUntilCached(ctx, clientB, keyB, 1))
		assert.Positive(suite.T(), suite.cacheLookups(ctx, clientA))
		assert.Positive(suite.T(), suite.cacheLookups(ctx, clientB))

		// Keys outside the prefix are served by the server and never cached, nor counted as lookups.
		lookupsA, lookupsB := suite.cacheLookups(ctx, clientA), suite.cacheLookups(ctx, clientB)
		hitRateA, missRateA := suite.cacheRates(ctx, clientA)
		assert.Equal(suite.T(), "b1", suite.getValue(ctx, clientA, keyB))
		assert.Equal(suite.T(), "a1", suite.getValue(ctx, clientB, keyA))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, clientA), "client A must not cache yyy:*")
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, clientB), "client B must not cache xxx:*")
		assert.Equal(suite.T(), lookupsA, suite.cacheLookups(ctx, clientA), "bypassed keys are not lookups")
		assert.Equal(suite.T(), lookupsB, suite.cacheLookups(ctx, clientB), "bypassed keys are not lookups")
		newHitRateA, newMissRateA := suite.cacheRates(ctx, clientA)
		assert.Equal(suite.T(), hitRateA, newHitRateA, "bypassed reads must not change the hit rate")
		assert.Equal(suite.T(), missRateA, newMissRateA, "bypassed reads must not change the miss rate")

		// A write in namespace A invalidates client A only.
		result, err = writer.Set(ctx, keyA, "a2")
		suite.verifyOK(result, err)
		suite.waitFor(func() bool {
			val, err := clientA.Get(ctx, keyA)
			return err == nil && val.Value() == "a2"
		}, "client A did not observe the xxx:* invalidation")
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, clientB), "client B keeps its yyy:* entry")
		assert.Equal(suite.T(), "b1", suite.getValue(ctx, clientB, keyB))
		assert.Equal(suite.T(), "a2", suite.readUntilCached(ctx, clientA, keyA, 1))

		// A write in namespace B invalidates client B only.
		result, err = writer.Set(ctx, keyB, "b2")
		suite.verifyOK(result, err)
		suite.waitFor(func() bool {
			val, err := clientB.Get(ctx, keyB)
			return err == nil && val.Value() == "b2"
		}, "client B did not observe the yyy:* invalidation")
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, clientA), "client A keeps its xxx:* entry")
		assert.Equal(suite.T(), "a2", suite.getValue(ctx, clientA, keyA))
		assert.Equal(suite.T(), "b2", suite.readUntilCached(ctx, clientB, keyB, 1))

		// Client A reads the updated yyy:* value straight from the server (never stale) and still
		// does not cache it.
		lookupsA = suite.cacheLookups(ctx, clientA)
		assert.Equal(suite.T(), "b2", suite.getValue(ctx, clientA, keyB))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, clientA))
		assert.Equal(suite.T(), lookupsA, suite.cacheLookups(ctx, clientA))
	})
}

// (c) A prefixed client never caches keys outside its prefix, for GET and for MGET: the mixed MGET returns a
// complete ordered result and only the tracked key is stored.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_BypassesCacheOutsidePrefix() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cached, err := suite.createClientWithCache(client, newPrefixedCache(suite.T(), "xxx:"))
		require.NoError(suite.T(), err)
		writer := suite.writerClientFor(client)

		ctx := context.Background()
		suffix := uuid.New().String()
		tracked := "xxx:" + suffix
		untracked := "zzz:" + suffix

		result, err := writer.Set(ctx, tracked, "t1")
		suite.verifyOK(result, err)
		result, err = writer.Set(ctx, untracked, "u1")
		suite.verifyOK(result, err)

		// GET on an untracked key: served by the server, not cached, not counted.
		assert.Equal(suite.T(), "u1", suite.getValue(ctx, cached, untracked))
		assert.Equal(suite.T(), "u1", suite.getValue(ctx, cached, untracked))
		assert.Equal(suite.T(), int64(0), suite.cacheEntries(ctx, cached), "untracked GETs bypass the cache")
		assert.Equal(suite.T(), int64(0), suite.cacheLookups(ctx, cached), "untracked GETs are not lookups")

		// The writer changes it and the prefixed client sees the new value immediately.
		result, err = writer.Set(ctx, untracked, "u2")
		suite.verifyOK(result, err)
		assert.Equal(suite.T(), "u2", suite.getValue(ctx, cached, untracked))

		// MGET mixing tracked and untracked keys: both values returned in order, only the tracked key stored.
		// The tracked key's invalidations from the initial SET may still be in flight, so settle first.
		suite.readUntilCached(ctx, cached, tracked, 1)
		lookups := suite.cacheLookups(ctx, cached)
		assert.Equal(suite.T(), []string{"t1", "u2"}, suite.mgetValues(ctx, cached, tracked, untracked))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, cached), "only the tracked key is stored")
		assert.Equal(suite.T(), lookups+1, suite.cacheLookups(ctx, cached), "one lookup for the tracked key only")

		// Reverse order to prove ordering is preserved regardless of which key is tracked; the untracked
		// key reflects a fresh write while the tracked key is served from the cache.
		result, err = writer.Set(ctx, untracked, "u3")
		suite.verifyOK(result, err)
		hitRate, _ := suite.cacheRates(ctx, cached)
		assert.Equal(suite.T(), []string{"u3", "t1"}, suite.mgetValues(ctx, cached, untracked, tracked))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, cached))
		assert.Equal(suite.T(), lookups+2, suite.cacheLookups(ctx, cached), "one more lookup for the tracked key")
		newHitRate, _ := suite.cacheRates(ctx, cached)
		assert.Greater(suite.T(), newHitRate, hitRate, "the tracked key was served from the cache")

		// The tracked key is still invalidated by the server.
		result, err = writer.Set(ctx, tracked, "t2")
		suite.verifyOK(result, err)
		suite.waitFor(func() bool {
			results, err := cached.MGet(ctx, []string{tracked, untracked})
			return err == nil && len(results) == 2 && results[0].Value() == "t2" && results[1].Value() == "u3"
		}, "the tracked MGET key was not invalidated")
	})
}

// (d) A prefix without server-assisted caching is a configuration error, reported before connecting.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_RequiresServerAssisted() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cache, err := config.NewClientSideCache(defaultTestCacheKb, defaultTestTtlMs)
		require.NoError(suite.T(), err)
		cache.WithTrackingPrefix("xxx:")
		assert.False(suite.T(), cache.ServerAssisted)

		_, err = suite.createClientWithCache(client, cache)
		require.Error(suite.T(), err)
		assert.Contains(suite.T(), err.Error(), "invalid client-side cache configuration")
		assert.Contains(suite.T(), err.Error(), "ServerAssisted")
	})
}

// (e) Two clients created with the same prefixed ClientSideCache instance share the cache.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_SharedCache() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cache := newPrefixedCache(suite.T(), "xxx:")
		client1, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)
		client2, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "xxx:" + uuid.New().String()
		result, err := client1.Set(ctx, key, "shared")
		suite.verifyOK(result, err)

		// Client 1 populates the entry; client 2 sees it and hits it without a new entry.
		assert.Equal(suite.T(), "shared", suite.readUntilCached(ctx, client1, key, 1))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, client2), "client 2 sees the same cache")
		lookups := suite.cacheLookups(ctx, client1)
		hitRate, _ := suite.cacheRates(ctx, client2)
		assert.Equal(suite.T(), "shared", suite.getValue(ctx, client2, key))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, client2))
		assert.Equal(suite.T(), lookups+1, suite.cacheLookups(ctx, client2), "metrics are shared too")
		assert.Equal(suite.T(), lookups+1, suite.cacheLookups(ctx, client1))
		newHitRate, _ := suite.cacheRates(ctx, client1)
		assert.Greater(suite.T(), newHitRate, hitRate, "client 2 hit the entry populated by client 1")

		// An untracked key is not stored by either client.
		other := "zzz:" + uuid.New().String()
		result, err = client2.Set(ctx, other, "x")
		suite.verifyOK(result, err)
		assert.Equal(suite.T(), "x", suite.getValue(ctx, client2, other))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, client1), "untracked key is not cached")
		assert.Equal(suite.T(), lookups+1, suite.cacheLookups(ctx, client1), "untracked key is not a lookup")
	})
}

// (f) Two clients may share a cache id only when they track the same prefix. The setters mutate the
// ClientSideCache in place, so changing the prefix between two client creations keeps the cacheId but
// changes the prefix, which the core must reject.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_SharedCacheIdRejectsDifferentPrefix() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cache := newPrefixedCache(suite.T(), "xxx:")
		first, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		// Same id, different prefix: rejected before connecting.
		cache.WithTrackingPrefix("yyy:")
		_, err = suite.createClientWithCache(client, cache)
		require.Error(suite.T(), err)
		assert.Contains(suite.T(), err.Error(), "different tracking prefix")

		// The first client keeps working with its original prefix.
		ctx := context.Background()
		info, err := first.ClientTrackingInfo(ctx)
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), []string{"xxx:"}, info.Prefixes)

		// Same id, same prefix: shares the cache.
		cache.WithTrackingPrefix("xxx:")
		second, err := suite.createClientWithCache(client, cache)
		require.NoError(suite.T(), err)

		key := "xxx:" + uuid.New().String()
		result, err := first.Set(ctx, key, "shared")
		suite.verifyOK(result, err)
		assert.Equal(suite.T(), "shared", suite.readUntilCached(ctx, first, key, 1))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, second), "both clients see the same cache")
		assert.Equal(suite.T(), "shared", suite.getValue(ctx, second, key))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, second), "the shared entry is reused, not duplicated")
	})
}

// A flush still clears the whole cache when a prefix is registered: the server sends the null
// invalidation to every tracking client regardless of its prefixes.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_FlushClearsCache() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cached, err := suite.createClientWithCache(client, newPrefixedCache(suite.T(), "xxx:"))
		require.NoError(suite.T(), err)

		ctx := context.Background()
		key := "xxx:" + uuid.New().String()
		result, err := cached.Set(ctx, key, "v1")
		suite.verifyOK(result, err)
		assert.Equal(suite.T(), "v1", suite.readUntilCached(ctx, cached, key, 1))

		result, err = cached.FlushAll(ctx)
		suite.verifyOK(result, err)
		suite.waitFor(func() bool {
			return suite.cacheEntries(ctx, cached) == 0
		}, "FLUSHALL did not clear the prefixed cache")

		value, err := cached.Get(ctx, key)
		require.NoError(suite.T(), err)
		assert.True(suite.T(), value.IsNil(), "the key is gone from the server and not served from the cache")
	})
}

// HGETALL and SMEMBERS honour the prefix like GET: tracked hashes/sets are cached and hit on the second
// read, untracked ones are never cached and do not count as lookups.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_HashAndSetCommands() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		cached, err := suite.createClientWithCache(client, newPrefixedCache(suite.T(), "xxx:"))
		require.NoError(suite.T(), err)
		writer := suite.writerClientFor(client)

		ctx := context.Background()
		suffix := uuid.New().String()
		trackedHash, untrackedHash := "xxx:hash:"+suffix, "zzz:hash:"+suffix
		trackedSet, untrackedSet := "xxx:set:"+suffix, "zzz:set:"+suffix
		fields := map[string]string{"f1": "v1", "f2": "v2"}
		members := map[string]struct{}{"m1": {}, "m2": {}}

		for _, key := range []string{trackedHash, untrackedHash} {
			added, err := writer.HSet(ctx, key, fields)
			require.NoError(suite.T(), err)
			assert.Equal(suite.T(), int64(2), added)
		}
		for _, key := range []string{trackedSet, untrackedSet} {
			added, err := writer.SAdd(ctx, key, []string{"m1", "m2"})
			require.NoError(suite.T(), err)
			assert.Equal(suite.T(), int64(2), added)
		}

		// Tracked hash: cached, second read is a hit.
		suite.settleCacheAfter(ctx, cached, 1, func() error {
			_, err := cached.HGetAll(ctx, trackedHash)
			return err
		})
		lookups := suite.cacheLookups(ctx, cached)
		hitRate, _ := suite.cacheRates(ctx, cached)
		hash, err := cached.HGetAll(ctx, trackedHash)
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), fields, hash)
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, cached))
		assert.Equal(suite.T(), lookups+1, suite.cacheLookups(ctx, cached))
		newHitRate, _ := suite.cacheRates(ctx, cached)
		assert.Greater(suite.T(), newHitRate, hitRate, "second HGETALL of a tracked hash is a hit")

		// Tracked set: cached as a second entry, second read is a hit.
		suite.settleCacheAfter(ctx, cached, 2, func() error {
			_, err := cached.SMembers(ctx, trackedSet)
			return err
		})
		lookups = suite.cacheLookups(ctx, cached)
		hitRate, _ = suite.cacheRates(ctx, cached)
		set, err := cached.SMembers(ctx, trackedSet)
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), members, set)
		assert.Equal(suite.T(), int64(2), suite.cacheEntries(ctx, cached))
		assert.Equal(suite.T(), lookups+1, suite.cacheLookups(ctx, cached))
		newHitRate, _ = suite.cacheRates(ctx, cached)
		assert.Greater(suite.T(), newHitRate, hitRate, "second SMEMBERS of a tracked set is a hit")

		// Untracked hash and set: full responses from the server, never cached, no lookups.
		lookups = suite.cacheLookups(ctx, cached)
		hitRate, missRate := suite.cacheRates(ctx, cached)
		for range 2 {
			hash, err = cached.HGetAll(ctx, untrackedHash)
			require.NoError(suite.T(), err)
			assert.Equal(suite.T(), fields, hash)
			set, err = cached.SMembers(ctx, untrackedSet)
			require.NoError(suite.T(), err)
			assert.Equal(suite.T(), members, set)
		}
		assert.Equal(suite.T(), int64(2), suite.cacheEntries(ctx, cached), "untracked hash/set are not cached")
		assert.Equal(suite.T(), lookups, suite.cacheLookups(ctx, cached), "untracked hash/set are not lookups")
		newHitRate, newMissRate := suite.cacheRates(ctx, cached)
		assert.Equal(suite.T(), hitRate, newHitRate)
		assert.Equal(suite.T(), missRate, newMissRate)

		// A write to the untracked hash is visible immediately (never stale).
		_, err = writer.HSet(ctx, untrackedHash, map[string]string{"f3": "v3"})
		require.NoError(suite.T(), err)
		hash, err = cached.HGetAll(ctx, untrackedHash)
		require.NoError(suite.T(), err)
		assert.Equal(suite.T(), "v3", hash["f3"])

		// A write to the tracked hash is invalidated by the server.
		_, err = writer.HSet(ctx, trackedHash, map[string]string{"f3": "v3"})
		require.NoError(suite.T(), err)
		suite.waitFor(func() bool {
			hash, err := cached.HGetAll(ctx, trackedHash)
			return err == nil && hash["f3"] == "v3"
		}, "the tracked hash was not invalidated")
	})
}

// A binary (non UTF-8) prefix is passed to the server byte for byte and applied to the local cache.
func (suite *GlideTestSuite) TestClientSideCache_TrackingPrefix_BinaryPrefix() {
	suite.runWithDefaultClients(func(client interfaces.BaseClientCommands) {
		prefix := "\xff\x00:"
		cached, err := suite.createClientWithCache(client, newPrefixedCache(suite.T(), prefix))
		require.NoError(suite.T(), err)
		writer := suite.writerClientFor(client)

		ctx := context.Background()
		info, err := cached.ClientTrackingInfo(ctx)
		require.NoError(suite.T(), err)
		require.Len(suite.T(), info.Prefixes, 1)
		assert.Equal(suite.T(), []byte{0xff, 0x00, ':'}, []byte(info.Prefixes[0]))

		key := prefix + uuid.New().String()
		result, err := writer.Set(ctx, key, "v1")
		suite.verifyOK(result, err)
		assert.Equal(suite.T(), "v1", suite.readUntilCached(ctx, cached, key, 1))

		// A key that shares only the first byte of the prefix is not tracked.
		other := "\xff\x01:" + uuid.New().String()
		result, err = writer.Set(ctx, other, "o1")
		suite.verifyOK(result, err)
		lookups := suite.cacheLookups(ctx, cached)
		assert.Equal(suite.T(), "o1", suite.getValue(ctx, cached, other))
		assert.Equal(suite.T(), int64(1), suite.cacheEntries(ctx, cached))
		assert.Equal(suite.T(), lookups, suite.cacheLookups(ctx, cached))

		// A write from the writer invalidates the tracked key.
		result, err = writer.Set(ctx, key, "v2")
		suite.verifyOK(result, err)
		suite.waitFor(func() bool {
			val, err := cached.Get(ctx, key)
			return err == nil && val.Value() == "v2"
		}, "the binary-prefixed key was not invalidated")
	})
}
