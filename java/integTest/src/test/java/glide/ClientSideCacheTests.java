/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.TestUtilities.getRandomString;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClientSideCache;
import glide.api.models.EvictionPolicy;
import glide.api.models.exceptions.RequestException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Integration tests for client-side caching functionality.
 *
 * <p>These tests verify the behavior of client-side caching across both standalone and cluster
 * modes. The tests cover cache hit/miss metrics, TTL expiration, eviction policies, and various
 * edge cases.
 */
@Timeout(35) // seconds
public class ClientSideCacheTests {

    /** Creates test clients with cache configuration for both standalone and cluster modes. */
    @SneakyThrows
    public static Stream<Arguments> getCacheEnabledClients() {
        // Create separate cache instances to avoid sharing between standalone and cluster clients
        ClientSideCache standaloneCache =
                ClientSideCache.builder().maxCacheKb(1L).entryTtlSeconds(60L).enableMetrics(true).build();

        ClientSideCache clusterCache =
                ClientSideCache.builder().maxCacheKb(1L).entryTtlSeconds(60L).enableMetrics(true).build();

        GlideClient standaloneClient =
                GlideClient.createClient(commonClientConfig().clientSideCache(standaloneCache).build())
                        .get();

        GlideClusterClient clusterClient =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().clientSideCache(clusterCache).build())
                        .get();

        return Stream.of(
                Arguments.of(named("GlideClient", standaloneClient)),
                Arguments.of(named("GlideClusterClient", clusterClient)));
    }

    /** Creates test clients without cache configuration for both standalone and cluster modes. */
    @SneakyThrows
    public static Stream<Arguments> getNoCacheClients() {
        GlideClient standaloneClient = GlideClient.createClient(commonClientConfig().build()).get();

        GlideClusterClient clusterClient =
                GlideClusterClient.createClient(commonClusterClientConfig().build()).get();

        return Stream.of(
                Arguments.of(named("GlideClient", standaloneClient)),
                Arguments.of(named("GlideClusterClient", clusterClient)));
    }

    /** Creates test clients with cache but metrics disabled. */
    @SneakyThrows
    public static Stream<Arguments> getCacheNoMetricsClients() {
        // Create separate cache instances to avoid sharing between standalone and cluster clients
        ClientSideCache standaloneCache =
                ClientSideCache.builder().maxCacheKb(1L).entryTtlSeconds(60L).enableMetrics(false).build();

        ClientSideCache clusterCache =
                ClientSideCache.builder().maxCacheKb(1L).entryTtlSeconds(60L).enableMetrics(false).build();

        GlideClient standaloneClient =
                GlideClient.createClient(commonClientConfig().clientSideCache(standaloneCache).build())
                        .get();

        GlideClusterClient clusterClient =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().clientSideCache(clusterCache).build())
                        .get();

        return Stream.of(
                Arguments.of(named("GlideClient", standaloneClient)),
                Arguments.of(named("GlideClusterClient", clusterClient)));
    }

    /**
     * Test basic cache hit/miss behavior with metrics tracking.
     *
     * <p>This test verifies:
     *
     * <ul>
     *   <li>First GET results in cache miss
     *   <li>Subsequent GETs result in cache hits
     *   <li>Cache entry count is tracked correctly
     *   <li>Hit rate and miss rate calculations are accurate
     * </ul>
     */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_basic_cache_hit_with_metrics(BaseClient client) {
        String key = "cache_test_key_" + getRandomString(10);
        String value = "cache_test_value";

        // Set a key
        assertEquals(OK, client.set(key, value).get());

        // First GET - cache miss
        assertEquals(value, client.get(key).get());

        // Entry count should be 1
        assertEquals(1L, client.getCacheEntryCount().get(), "Expected 1 entry in cache");

        // Second GET - cache hit
        assertEquals(value, client.get(key).get());

        // Third GET - cache hit
        assertEquals(value, client.get(key).get());

        // Verify metrics: 1 miss + 2 hits = 3 total
        Double hitRate = client.getCacheHitRate().get();
        Double missRate = client.getCacheMissRate().get();

        assertEquals(2.0 / 3.0, hitRate, 0.001, "Expected 66.67% hit rate");
        assertEquals(1.0 / 3.0, missRate, 0.001, "Expected 33.33% miss rate");
        assertEquals(1.0, hitRate + missRate, 0.0001, "Rates should sum to 1.0");
    }

    /** Test that cache works but metrics are disabled when configured. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheNoMetricsClients")
    @SneakyThrows
    public void test_cache_without_metrics(BaseClient client) {
        String key = "key_" + getRandomString(10);
        String value = "value";

        // Cache should work
        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());
        assertEquals(value, client.get(key).get()); // Should be cached

        // Entry count should still work
        assertEquals(1L, client.getCacheEntryCount().get(), "Expected 1 entry in cache");

        // Metrics should fail
        ExecutionException hitRateException =
                assertThrows(ExecutionException.class, () -> client.getCacheHitRate().get());
        assertTrue(hitRateException.getCause().getMessage().toLowerCase().contains("metrics"));

        ExecutionException missRateException =
                assertThrows(ExecutionException.class, () -> client.getCacheMissRate().get());
        assertTrue(missRateException.getCause().getMessage().toLowerCase().contains("metrics"));

        ExecutionException evictionsException =
                assertThrows(ExecutionException.class, () -> client.getCacheEvictions().get());
        assertTrue(evictionsException.getCause().getMessage().toLowerCase().contains("metrics"));

        ExecutionException expirationsException =
                assertThrows(ExecutionException.class, () -> client.getCacheExpirations().get());
        assertTrue(expirationsException.getCause().getMessage().toLowerCase().contains("metrics"));
    }

    /** Test that NIL (null) values are not cached. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_nil_values_not_cached(BaseClient client) {
        String nonExistentKey = "nonexistent_key_" + getRandomString(10);

        // GET non-existent key (returns null)
        assertEquals(null, client.get(nonExistentKey).get());

        // Entry count should be 0
        assertEquals(0L, client.getCacheEntryCount().get(), "Expected 0 entries in cache");

        // GET again - should NOT be cached (NIL values not cached)
        assertEquals(null, client.get(nonExistentKey).get());

        // Miss rate should be 100%
        Double missRate = client.getCacheMissRate().get();
        assertEquals(1.0, missRate, 0.001, "Expected 100% miss rate");
    }

    /** Test that cache entries expire after their TTL. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_ttl_expiration(BaseClient client) {
        // Create cache with short TTL
        ClientSideCache shortTtlCache =
                ClientSideCache.builder()
                        .maxCacheKb(1L)
                        .entryTtlSeconds(2L) // 2 seconds
                        .enableMetrics(true)
                        .build();

        BaseClient shortTtlClient;
        if (client instanceof GlideClient) {
            shortTtlClient =
                    GlideClient.createClient(commonClientConfig().clientSideCache(shortTtlCache).build())
                            .get();
        } else {
            shortTtlClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().clientSideCache(shortTtlCache).build())
                            .get();
        }

        try {
            String key = "ttl_key_" + getRandomString(10);
            String value = "ttl_value";

            // Set and GET
            assertEquals(OK, shortTtlClient.set(key, value).get());
            assertEquals(value, shortTtlClient.get(key).get());

            assertEquals(1L, shortTtlClient.getCacheEntryCount().get(), "Expected 1 entry in cache");

            // Second GET - from cache
            assertEquals(value, shortTtlClient.get(key).get());

            // Wait for TTL to expire
            Thread.sleep(3000);

            // GET after expiration - should fetch from server again
            assertEquals(value, shortTtlClient.get(key).get());

            // Expiration count should be 1
            Long expirations = shortTtlClient.getCacheExpirations().get();
            assertEquals(1L, expirations, "Expected 1 expiration");

            // Miss rate should be 2 misses out of 3 total = 66.67%
            Double missRate = shortTtlClient.getCacheMissRate().get();
            assertEquals(2.0 / 3.0, missRate, 0.001, "Expected 66.67% miss rate");
        } finally {
            shortTtlClient.close();
        }
    }

    /** Test caching behavior with multiple keys. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_multiple_keys(BaseClient client) {
        // Create keys with consistent names
        String[] keys = new String[3];
        String[] values = new String[3];

        // Set 3 keys
        for (int i = 0; i < 3; i++) {
            keys[i] = "key" + (i + 1) + "_" + getRandomString(5);
            values[i] = "value" + (i + 1);
            assertEquals(OK, client.set(keys[i], values[i]).get());
        }

        // GET each key twice (miss + hit)
        for (int i = 0; i < 3; i++) {
            assertEquals(values[i], client.get(keys[i]).get()); // First GET - cache miss
            assertEquals(values[i], client.get(keys[i]).get()); // Second GET - cache hit
        }

        // Entry count should be 3
        assertEquals(3L, client.getCacheEntryCount().get(), "Expected 3 entries in cache");

        // Verify metrics: 3 misses + 3 hits = 50% hit rate
        Double hitRate = client.getCacheHitRate().get();
        assertEquals(0.5, hitRate, 0.001, "Expected 50% hit rate");
    }

    /** Test that clients without cache configuration cannot access cache metrics. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getNoCacheClients")
    @SneakyThrows
    public void test_no_cache_metrics(BaseClient client) {
        String key = "key_" + getRandomString(10);
        String value = "value";

        // Set and GET multiple times
        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());
        assertEquals(value, client.get(key).get());
        assertEquals(value, client.get(key).get());

        // Metrics should error
        ExecutionException hitRateException =
                assertThrows(ExecutionException.class, () -> client.getCacheHitRate().get());
        assertTrue(hitRateException.getCause().getMessage().toLowerCase().contains("not enabled"));

        ExecutionException missRateException =
                assertThrows(ExecutionException.class, () -> client.getCacheMissRate().get());
        assertTrue(missRateException.getCause().getMessage().toLowerCase().contains("not enabled"));

        ExecutionException evictionsException =
                assertThrows(ExecutionException.class, () -> client.getCacheEvictions().get());
        assertTrue(evictionsException.getCause().getMessage().toLowerCase().contains("not enabled"));

        ExecutionException expirationsException =
                assertThrows(ExecutionException.class, () -> client.getCacheExpirations().get());
        assertTrue(expirationsException.getCause().getMessage().toLowerCase().contains("not enabled"));

        ExecutionException entryCountException =
                assertThrows(ExecutionException.class, () -> client.getCacheEntryCount().get());
        assertTrue(entryCountException.getCause().getMessage().toLowerCase().contains("not enabled"));
    }

    /** Test LRU (Least Recently Used) eviction policy. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_eviction_policy_lru(BaseClient client) {
        // Create cache with LRU eviction policy
        ClientSideCache lruCache =
                ClientSideCache.builder()
                        .maxCacheKb(1L) // 1 KB to force eviction
                        .entryTtlSeconds(null)
                        .evictionPolicy(EvictionPolicy.LRU)
                        .enableMetrics(true)
                        .build();

        BaseClient lruClient;
        if (client instanceof GlideClient) {
            lruClient =
                    GlideClient.createClient(commonClientConfig().clientSideCache(lruCache).build()).get();
        } else {
            lruClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().clientSideCache(lruCache).build())
                            .get();
        }

        try {
            // Use larger values to force eviction
            String value = "x".repeat(250); // ~250 bytes
            String randomSuffix = getRandomString(5);

            // Set and cache 3 keys - store key names for later reuse
            String[] keys = new String[5];
            for (int i = 0; i < 3; i++) {
                keys[i] = "lru_key" + (i + 1) + "_" + randomSuffix;
                assertEquals(OK, lruClient.set(keys[i], value).get());
                assertEquals(value, lruClient.get(keys[i]).get());
            }

            // Cache should have 3 entries now
            assertEquals(3L, lruClient.getCacheEntryCount().get(), "Expected 3 entries in cache");

            // Access key1 again to make it recently used (this creates a cache hit)
            assertEquals(value, lruClient.get(keys[0]).get());

            // Add 2 more keys - should evict some entries due to memory limit
            for (int i = 3; i < 5; i++) {
                keys[i] = "lru_key" + (i + 1) + "_" + randomSuffix;
                assertEquals(OK, lruClient.set(keys[i], value).get());
                assertEquals(value, lruClient.get(keys[i]).get());
            }

            // Verify evictions occurred
            Long evictions = lruClient.getCacheEvictions().get();
            assertTrue(evictions >= 1, "Expected at least 1 eviction");

            // Verify cache is working (hit rate > 0)
            // We had 1 cache hit (accessing keys[0] again) out of 6 total GETs
            Double hitRate = lruClient.getCacheHitRate().get();
            assertTrue(hitRate > 0, "Cache should have some hits");
        } finally {
            lruClient.close();
        }
    }

    /** Test LFU (Least Frequently Used) eviction policy. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_eviction_policy_lfu(BaseClient client) {
        // Create cache with LFU eviction policy
        ClientSideCache lfuCache =
                ClientSideCache.builder()
                        .maxCacheKb(1L) // 1 KB - small cache to trigger evictions
                        .entryTtlSeconds(null)
                        .evictionPolicy(EvictionPolicy.LFU)
                        .enableMetrics(true)
                        .build();

        BaseClient lfuClient;
        if (client instanceof GlideClient) {
            lfuClient =
                    GlideClient.createClient(commonClientConfig().clientSideCache(lfuCache).build()).get();
        } else {
            lfuClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().clientSideCache(lfuCache).build())
                            .get();
        }

        try {
            String value = "x".repeat(250); // ~250 bytes

            // Set key1 and access it multiple times (high frequency)
            String key1 = "key1_" + getRandomString(5);
            assertEquals(OK, lfuClient.set(key1, value).get());
            for (int i = 0; i < 5; i++) {
                assertEquals(value, lfuClient.get(key1).get());
            }
            // key1 frequency: 5

            // Set key2 and access it a few times (medium frequency)
            String key2 = "key2_" + getRandomString(5);
            assertEquals(OK, lfuClient.set(key2, value).get());
            for (int i = 0; i < 2; i++) {
                assertEquals(value, lfuClient.get(key2).get());
            }
            // key2 frequency: 2

            // Set key3 with minimal access (low frequency)
            String key3 = "key3_" + getRandomString(5);
            assertEquals(OK, lfuClient.set(key3, value).get());
            assertEquals(value, lfuClient.get(key3).get());
            // key3 frequency: 1

            // Verify cache is working
            Double hitRate = lfuClient.getCacheHitRate().get();
            assertTrue(hitRate > 0, "Cache should have some hits");

            // Cache should have 3 entries now
            assertEquals(3L, lfuClient.getCacheEntryCount().get(), "Expected 3 entries in cache");

            // Set key4 - this should trigger eviction of key3 (lowest frequency)
            String key4 = "key4_" + getRandomString(5);
            assertEquals(OK, lfuClient.set(key4, value).get());
            assertEquals(value, lfuClient.get(key4).get());
            // key4 frequency: 1

            // Check that cache entry count is still 3
            assertEquals(3L, lfuClient.getCacheEntryCount().get(), "Expected 3 entries in cache");

            // Verify 1 eviction occurred
            assertEquals(1L, lfuClient.getCacheEvictions().get(), "Expected 1 eviction");

            // Check that key1 (highest frequency) is still cached
            Double oldHitRate = lfuClient.getCacheHitRate().get();
            assertEquals(value, lfuClient.get(key1).get());
            Double newHitRate = lfuClient.getCacheHitRate().get();
            assertTrue(newHitRate > oldHitRate, "key1 (highest frequency) should still be cached");
        } finally {
            lfuClient.close();
        }
    }

    /** Test that cache respects maximum memory limits. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_max_memory_limit(BaseClient client) {
        // Create cache with memory limit
        ClientSideCache memoryLimitCache =
                ClientSideCache.builder()
                        .maxCacheKb(1L) // 1 KB
                        .entryTtlSeconds(null)
                        .enableMetrics(true)
                        .evictionPolicy(EvictionPolicy.LRU)
                        .build();

        BaseClient memoryClient;
        if (client instanceof GlideClient) {
            memoryClient =
                    GlideClient.createClient(commonClientConfig().clientSideCache(memoryLimitCache).build())
                            .get();
        } else {
            memoryClient =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().clientSideCache(memoryLimitCache).build())
                            .get();
        }

        try {
            // Create values that are ~400 bytes each
            String largeValue = "x".repeat(400);

            // Add 10 keys to force eviction
            for (int i = 1; i <= 10; i++) {
                String key = "key" + i + "_" + getRandomString(5);
                assertEquals(OK, memoryClient.set(key, largeValue).get());
                assertEquals(largeValue, memoryClient.get(key).get());
                assertEquals(largeValue, memoryClient.get(key).get());
            }

            Double currentHitRate = memoryClient.getCacheHitRate().get();
            assertTrue(currentHitRate > 0, "Cache should have some hits");

            // Verify that evictions occurred
            Long evictions = memoryClient.getCacheEvictions().get();
            assertTrue(evictions > 0, "Expected evictions due to max memory limit");
        } finally {
            memoryClient.close();
        }
    }

    /** Test shared cache behavior between multiple clients. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_shared_cache(BaseClient client) {
        // Create shared cache instance
        ClientSideCache sharedCache =
                ClientSideCache.builder()
                        .maxCacheKb(1024L)
                        .entryTtlSeconds(60L)
                        .evictionPolicy(null)
                        .enableMetrics(true)
                        .build();

        // Create two clients with the same cache
        BaseClient client1, client2;
        if (client instanceof GlideClient) {
            client1 =
                    GlideClient.createClient(commonClientConfig().clientSideCache(sharedCache).build()).get();
            client2 =
                    GlideClient.createClient(commonClientConfig().clientSideCache(sharedCache).build()).get();
        } else {
            client1 =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().clientSideCache(sharedCache).build())
                            .get();
            client2 =
                    GlideClusterClient.createClient(
                                    commonClusterClientConfig().clientSideCache(sharedCache).build())
                            .get();
        }

        try {
            String key = "shared_key_" + getRandomString(10);
            String value = "value";

            assertEquals(OK, client1.set(key, value).get());
            assertEquals(value, client1.get(key).get());

            // Entry count should be 1
            Long entryCount = client2.getCacheEntryCount().get();
            assertEquals(1L, entryCount, "Expected 1 entry in shared cache");

            assertEquals(value, client2.get(key).get());

            assertEquals(0.5, client2.getCacheHitRate().get(), 0.001);
            assertEquals(0.5, client1.getCacheHitRate().get(), 0.001);
        } finally {
            client1.close();
            client2.close();
        }
    }

    /** Test that attempting to use wrong key types raises appropriate errors. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_wrong_key_type_raises_error(BaseClient client) {
        String key = "string-key_" + getRandomString(10);
        String value = "value";

        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());

        ExecutionException exception =
                assertThrows(ExecutionException.class, () -> client.hgetall(key).get()); // Wrong type

        assertTrue(exception.getCause() instanceof RequestException);
        assertTrue(exception.getCause().getMessage().contains("WRONGTYPE"));
    }

    /** Test that only cacheable commands are actually cached. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cacheable_commands(BaseClient client) {
        String keyPrefix = getRandomString(10);

        // SET command - not cacheable
        assertEquals(OK, client.set(keyPrefix + "_key", "value").get());

        // GET command - cacheable
        assertEquals("value", client.get(keyPrefix + "_key").get());

        // Check that now the cache entry count is 1
        Long entryCount = client.getCacheEntryCount().get();
        assertEquals(1L, entryCount, "Expected 1 entry in cache after GET");

        // HGETALL command - cacheable
        String hashKey = keyPrefix + "_hashkey";
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put("field1", "val1");
        assertEquals(1L, client.hset(hashKey, hashMap).get());

        Map<String, String> result = client.hgetall(hashKey).get();
        assertEquals("val1", result.get("field1"));

        entryCount = client.getCacheEntryCount().get();
        assertEquals(2L, entryCount, "Expected 2 entries in cache after HGETALL");

        // SMEMBERS command - cacheable
        String setKey = keyPrefix + "_setkey";
        assertEquals(1L, client.sadd(setKey, new String[] {"member1"}).get());
        Set<String> members = client.smembers(setKey).get();
        assertTrue(members.contains("member1"));

        entryCount = client.getCacheEntryCount().get();
        assertEquals(3L, entryCount, "Expected 3 entries in cache after SMEMBERS");

        // Clean up
        if (client instanceof GlideClient) {
            ((GlideClient) client).flushall().get();
        } else {
            ((GlideClusterClient) client).flushall().get();
        }
    }
}
