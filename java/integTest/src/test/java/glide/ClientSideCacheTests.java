/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.assertClientTrackingInfo;
import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.TestUtilities.getRandomString;
import static glide.TestUtilities.waitFor;
import static glide.api.BaseClient.OK;
import static glide.api.models.GlideString.gs;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Named.named;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.ClientSideCache;
import glide.api.models.configuration.EvictionPolicy;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.exceptions.RequestException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
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
                ClientSideCache.builder().maxCacheKb(1L).entryTtlMs(60000L).enableMetrics(true).build();

        ClientSideCache clusterCache =
                ClientSideCache.builder().maxCacheKb(1L).entryTtlMs(60000L).enableMetrics(true).build();

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
                ClientSideCache.builder().maxCacheKb(1L).entryTtlMs(60000L).enableMetrics(false).build();

        ClientSideCache clusterCache =
                ClientSideCache.builder().maxCacheKb(1L).entryTtlMs(60000L).enableMetrics(false).build();

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

        // Verify total lookups: 1 miss + 2 hits = 3
        Long totalLookups = client.getCacheTotalLookups().get();
        assertEquals(3L, totalLookups, "Expected 3 total lookups (1 miss + 2 hits)");
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

        ExecutionException totalLookupsException =
                assertThrows(ExecutionException.class, () -> client.getCacheTotalLookups().get());
        assertTrue(totalLookupsException.getCause().getMessage().toLowerCase().contains("metrics"));
    }

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

        // Total lookups should be 2
        Long totalLookups = client.getCacheTotalLookups().get();
        assertEquals(2L, totalLookups, "Expected 2 total lookups");
    }

    /** Test that cache entries expire after their TTL. */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("getCacheEnabledClients")
    @SneakyThrows
    public void test_cache_ttl_expiration(BaseClient client) {
        // Create cache with short TTL
        ClientSideCache shortTtlCache =
                ClientSideCache.builder().maxCacheKb(1L).entryTtlMs(1000L).enableMetrics(true).build();

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
            Thread.sleep(1100);

            // GET after expiration - should fetch from server again
            assertEquals(value, shortTtlClient.get(key).get());

            // Expiration count should be 1
            Long expirations = shortTtlClient.getCacheExpirations().get();
            assertEquals(1L, expirations, "Expected 1 expiration");

            // Miss rate should be 2 misses out of 3 total = 66.67%
            Double missRate = shortTtlClient.getCacheMissRate().get();
            assertEquals(2.0 / 3.0, missRate, 0.001, "Expected 66.67% miss rate");

            // Total lookups should be 3
            Long totalLookups = shortTtlClient.getCacheTotalLookups().get();
            assertEquals(3L, totalLookups, "Expected 3 total lookups");
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

        // Total lookups should be 6
        Long totalLookups = client.getCacheTotalLookups().get();
        assertEquals(6L, totalLookups, "Expected 6 total lookups (3 misses + 3 hits)");
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

        ExecutionException totalLookupsException =
                assertThrows(ExecutionException.class, () -> client.getCacheTotalLookups().get());
        assertTrue(totalLookupsException.getCause().getMessage().toLowerCase().contains("not enabled"));
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
                        .entryTtlMs(0L)
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
            String value = new String(new char[250]).replace('\0', 'x'); // ~250 bytes

            // Set and cache 3 keys
            for (int i = 1; i <= 3; i++) {
                assertEquals(OK, lruClient.set("lru_key" + i, value).get());
                assertEquals(value, lruClient.get("lru_key" + i).get());
            }

            // Cache should have 3 entries now
            assertEquals(3L, lruClient.getCacheEntryCount().get(), "Expected 3 entries in cache");

            // Access key1 to make it recently used
            assertEquals(value, lruClient.get("lru_key1").get());

            // Add 2 more keys - should evict key2 and key3 (least recently used)
            for (int i = 4; i <= 5; i++) {
                assertEquals(OK, lruClient.set("lru_key" + i, value).get());
                assertEquals(value, lruClient.get("lru_key" + i).get());
            }

            // Verify 2 evictions occurred
            Long evictions = lruClient.getCacheEvictions().get();
            assertEquals(2L, evictions, "Expected 2 evictions");

            // Verify cache is working (hit rate > 0)
            Double hitRate = lruClient.getCacheHitRate().get();
            assertTrue(hitRate > 0, "Cache should have some hits");

            // Check that key1 is still cached
            assertEquals(value, lruClient.get("lru_key1").get());
            Double newHitRate = lruClient.getCacheHitRate().get();
            assertTrue(newHitRate > hitRate, "Key1 should still be in cache");

            // Check that key2 and key3 are evicted
            Double oldMissRate = lruClient.getCacheMissRate().get();
            assertEquals(value, lruClient.get("lru_key2").get());
            assertEquals(value, lruClient.get("lru_key3").get());
            Double newMissRate = lruClient.getCacheMissRate().get();
            assertTrue(newMissRate > oldMissRate, "Key2 and Key3 should be evicted from cache");
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
                        .entryTtlMs(0L)
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
            String value = new String(new char[250]).replace('\0', 'x'); // ~250 bytes

            // Set key1 and access it multiple times (high frequency)
            assertEquals(OK, lfuClient.set("key1", value).get());
            for (int i = 0; i < 5; i++) {
                assertEquals(value, lfuClient.get("key1").get());
            }
            // key1 frequency: 5

            // Set key2 and access it a few times (medium frequency)
            assertEquals(OK, lfuClient.set("key2", value).get());
            for (int i = 0; i < 2; i++) {
                assertEquals(value, lfuClient.get("key2").get());
            }
            // key2 frequency: 2

            // Set key3 with minimal access (low frequency)
            assertEquals(OK, lfuClient.set("key3", value).get());
            assertEquals(value, lfuClient.get("key3").get());
            // key3 frequency: 1

            // Verify cache is working
            Double hitRate = lfuClient.getCacheHitRate().get();
            assertTrue(hitRate > 0, "Cache should have some hits");

            // Cache should have 3 entries now
            assertEquals(3L, lfuClient.getCacheEntryCount().get(), "Expected 3 entries in cache");

            // Set key4 - this should trigger eviction of key3 (lowest frequency)
            assertEquals(OK, lfuClient.set("key4", value).get());
            assertEquals(value, lfuClient.get("key4").get());
            // key4 frequency: 1

            // Check that cache entry count is still 3
            assertEquals(3L, lfuClient.getCacheEntryCount().get(), "Expected 3 entries in cache");

            // Verify 1 eviction occurred
            assertEquals(1L, lfuClient.getCacheEvictions().get(), "Expected 1 eviction");

            // Check that key1 (highest frequency) is still cached
            Double oldHitRate = lfuClient.getCacheHitRate().get();
            assertEquals(value, lfuClient.get("key1").get());
            Double newHitRate = lfuClient.getCacheHitRate().get();
            assertTrue(newHitRate > oldHitRate, "key1 (highest frequency) should still be cached");

            // Check that key3 (lowest frequency) was evicted
            Double oldMissRate = lfuClient.getCacheMissRate().get();
            assertEquals(value, lfuClient.get("key3").get()); // Should be a miss
            Double newMissRate = lfuClient.getCacheMissRate().get();
            assertTrue(newMissRate > oldMissRate, "key3 (lowest frequency) should have been evicted");

            // key2 (medium frequency) should still be cached
            oldHitRate = lfuClient.getCacheHitRate().get();
            assertEquals(value, lfuClient.get("key2").get());
            newHitRate = lfuClient.getCacheHitRate().get();
            assertTrue(newHitRate > oldHitRate, "key2 (medium frequency) should still be cached");
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
                        .entryTtlMs(0L)
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
            String largeValue = new String(new char[400]).replace('\0', 'x');

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
                        .entryTtlMs(60000L)
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

            // Total lookups should be 2
            Long totalLookups = client1.getCacheTotalLookups().get();
            assertEquals(2L, totalLookups, "Expected 2 total lookups on shared cache");
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

    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("glide.TestSources#serverAssistedCacheClients")
    @SneakyThrows
    public void clientSideCache_set_and_get(BaseClient client) {
        // Tests server-assisted client-side caching using the CLIENT TRACKING protocol.
        // With serverAssisted=true, the server sends invalidation messages when cached keys change.
        // The SET/GET pattern validates that: first GET is a cache miss, second GET is served
        // from the local cache (validated by non-zero hit rate).
        // See: https://valkey.io/commands/client-tracking/
        String key = UUID.randomUUID().toString();
        String value = "cachedValue";

        assertEquals(OK, client.set(key, value).get());

        // First GET: cache miss, populates cache
        assertEquals(value, client.get(key).get());

        // Poll until a cache hit is observed. With server-assisted caching on cluster
        // clients, CLIENT TRACKING may not be fully active on all connections immediately,
        // so the first GET(s) may be misses until tracking is established.
        waitFor(
                () -> {
                    assertEquals(value, client.get(key).get());
                    return client.getCacheHitRate().get() > 0;
                },
                "Expected cache hit rate > 0 after GET");
    }

    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("glide.TestSources#serverAssistedCacheClients")
    @SneakyThrows
    public void clientSideCache_serverAssisted_invalidation(BaseClient clientA) {
        String key = UUID.randomUUID().toString();

        // Client A caches the key
        assertEquals(OK, clientA.set(key, "v1").get());
        assertEquals("v1", clientA.get(key).get()); // miss, populates cache
        assertEquals("v1", clientA.get(key).get()); // hit

        // Client B modifies the key — triggers server invalidation to Client A
        BaseClient clientB =
                clientA instanceof GlideClusterClient
                        ? GlideClusterClient.createClient(commonClusterClientConfig().build()).get()
                        : GlideClient.createClient(commonClientConfig().build()).get();
        assertEquals(OK, clientB.set(key, "v2").get());
        clientB.close();

        // Poll until invalidation is processed and Client A sees the new value
        waitFor(
                () -> "v2".equals(clientA.get(key).get()),
                "Cache was not invalidated after key was modified by another client");
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

    // ==================== Tracking prefix ====================

    /** Client modes the tracking-prefix tests run in. Each test creates the clients it needs. */
    public static Stream<Arguments> clientModes() {
        return Stream.of(
                Arguments.of(named("GlideClient", false)), Arguments.of(named("GlideClusterClient", true)));
    }

    /** A server-assisted cache (RESP3 only) scoped to {@code prefix}, with metrics enabled. */
    private static ClientSideCache prefixedCache(String prefix) {
        return ClientSideCache.builder()
                .maxCacheKb(1024L)
                .entryTtlMs(0L)
                .enableMetrics(true)
                .serverAssisted(true)
                .trackingPrefix(prefix)
                .build();
    }

    /** Creates a RESP3 client in the requested mode, with or without a client-side cache. */
    @SneakyThrows
    private static BaseClient createClient(boolean cluster, ClientSideCache cache) {
        if (cluster) {
            return GlideClusterClient.createClient(
                            commonClusterClientConfig()
                                    .protocol(ProtocolVersion.RESP3)
                                    .clientSideCache(cache)
                                    .build())
                    .get();
        }
        return GlideClient.createClient(
                        commonClientConfig().protocol(ProtocolVersion.RESP3).clientSideCache(cache).build())
                .get();
    }

    /** CLIENT TRACKINGINFO of one of the client's connections. */
    @SneakyThrows
    private static Map<String, Object> trackingInfo(BaseClient client) {
        if (client instanceof GlideClusterClient) {
            return ((GlideClusterClient) client).clientTrackingInfo().get();
        }
        return ((GlideClient) client).clientTrackingInfo().get();
    }

    /** Asserts that a CLIENT TRACKINGINFO reply reports BCAST tracking scoped to {@code prefix}. */
    @SuppressWarnings("unchecked")
    private static void assertTrackingPrefix(Map<String, Object> info, String prefix) {
        Set<String> flags = (Set<String>) info.get("flags");
        assertTrue(flags.contains("on"), "flags: " + flags);
        assertTrue(flags.contains("bcast"), "flags: " + flags);
        Object[] prefixes = (Object[]) info.get("prefixes");
        assertEquals(1, prefixes.length, "prefixes: " + Arrays.toString(prefixes));
        assertEquals(prefix, prefixes[0].toString());
    }

    /**
     * The configured prefix is registered on every server connection, so the server only broadcasts
     * invalidations for keys under that prefix.
     */
    @ParameterizedTest
    @MethodSource("clientModes")
    @SneakyThrows
    public void trackingPrefix_is_registered_with_the_server(boolean cluster) {
        try (BaseClient client = createClient(cluster, prefixedCache("xxx:"))) {
            assertTrackingPrefix(trackingInfo(client), "xxx:");

            if (cluster) {
                // Every node connection, primaries and replicas, carries the same prefix.
                ClusterValue<Map<String, Object>> allNodes =
                        ((GlideClusterClient) client).clientTrackingInfo(ALL_NODES).get();
                assertTrue(allNodes.getMultiValue().size() > 1, "expected a multi-node reply");
                for (Map<String, Object> nodeInfo : allNodes.getMultiValue().values()) {
                    assertTrackingPrefix(nodeInfo, "xxx:");
                }
            }
        }
    }

    /**
     * Without a prefix the server tracks every key (the empty prefix). This is the pre-existing
     * behaviour and must not change.
     */
    @ParameterizedTest(autoCloseArguments = true)
    @MethodSource("glide.TestSources#serverAssistedCacheClients")
    @SneakyThrows
    public void serverAssisted_without_prefix_tracks_all_keys(BaseClient client) {
        assertClientTrackingInfo(trackingInfo(client), true);
    }

    /** Number of cache hits so far, derived from the hit rate and the lookup count. */
    @SneakyThrows
    private static long hits(BaseClient client) {
        return Math.round(client.getCacheHitRate().get() * client.getCacheTotalLookups().get());
    }

    /**
     * Reads {@code key} until it is served from the cache.
     *
     * <p>In cluster mode the client registers tracking on every node connection, replicas included,
     * so one write produces one invalidation per node. The replica invalidations arrive after
     * replication and can evict an entry that was just re-fetched from the primary. Polling until a
     * read is a hit, after a short settle, leaves the entry in a stable state for the assertions that
     * follow. Standalone servers have a single connection, so the first attempt succeeds.
     */
    @SneakyThrows
    private static void warmCache(BaseClient client, String key, String expected) {
        waitFor(
                () -> {
                    if (!expected.equals(client.get(key).get())) {
                        return false;
                    }
                    Thread.sleep(50);
                    long hitsBefore = hits(client);
                    return expected.equals(client.get(key).get()) && hits(client) == hitsBefore + 1;
                },
                "key " + key + " was not served from the cache");
    }

    /** Asserts that reading {@code key} is served from the cache (one more lookup, one more hit). */
    @SneakyThrows
    private static void assertCacheHit(BaseClient client, String key, String expected) {
        long lookupsBefore = client.getCacheTotalLookups().get();
        long hitsBefore = hits(client);
        assertEquals(expected, client.get(key).get());
        assertEquals(lookupsBefore + 1, client.getCacheTotalLookups().get(), "lookups for " + key);
        assertEquals(hitsBefore + 1, hits(client), "hits for " + key);
    }

    /**
     * Asserts that reading {@code key} bypasses the cache entirely: it is served by the server, not
     * stored, and not counted as a lookup.
     */
    @SneakyThrows
    private static void assertCacheBypassed(BaseClient client, String key, String expected) {
        long entriesBefore = client.getCacheEntryCount().get();
        long lookupsBefore = client.getCacheTotalLookups().get();
        assertEquals(expected, client.get(key).get());
        assertEquals(entriesBefore, client.getCacheEntryCount().get(), key + " must not be cached");
        assertEquals(lookupsBefore, client.getCacheTotalLookups().get(), key + " is not a lookup");
    }

    /**
     * Two clients with different prefixes: each one caches only its own namespace, is invalidated
     * only for writes in its own namespace, and never caches keys outside its prefix.
     */
    @ParameterizedTest
    @MethodSource("clientModes")
    @SneakyThrows
    public void trackingPrefix_scopes_invalidations_between_clients(boolean cluster) {
        try (BaseClient clientA = createClient(cluster, prefixedCache("xxx:"));
                BaseClient clientB = createClient(cluster, prefixedCache("yyy:"));
                BaseClient writer = createClient(cluster, null)) {
            String suffix = getRandomString(8);
            String keyA = "xxx:" + suffix;
            String keyB = "yyy:" + suffix;
            assertEquals(OK, writer.set(keyA, "a1").get());
            assertEquals(OK, writer.set(keyB, "b1").get());

            // Each client caches its own namespace.
            warmCache(clientA, keyA, "a1");
            warmCache(clientB, keyB, "b1");
            assertEquals(1L, clientA.getCacheEntryCount().get());
            assertEquals(1L, clientB.getCacheEntryCount().get());
            assertCacheHit(clientA, keyA, "a1");
            assertCacheHit(clientB, keyB, "b1");

            // Keys outside the prefix are served by the server and never cached nor counted.
            assertCacheBypassed(clientA, keyB, "b1");
            assertCacheBypassed(clientB, keyA, "a1");

            // A write in namespace A invalidates client A only: A observes the new value while
            // B keeps serving its own entry from the cache.
            assertEquals(OK, writer.set(keyA, "a2").get());
            waitFor(
                    () -> "a2".equals(clientA.get(keyA).get()),
                    "client A did not observe the xxx:* invalidation");
            assertEquals(1L, clientB.getCacheEntryCount().get(), "client B keeps its yyy:* entry");
            assertCacheHit(clientB, keyB, "b1");

            // A write in namespace B invalidates client B only.
            warmCache(clientA, keyA, "a2");
            assertEquals(OK, writer.set(keyB, "b2").get());
            waitFor(
                    () -> "b2".equals(clientB.get(keyB).get()),
                    "client B did not observe the yyy:* invalidation");
            assertEquals(1L, clientA.getCacheEntryCount().get(), "client A keeps its xxx:* entry");
            assertCacheHit(clientA, keyA, "a2");

            // Client A reads the updated yyy:* value straight from the server (never stale) and
            // still does not cache it.
            assertCacheBypassed(clientA, keyB, "b2");
            assertEquals(1L, clientA.getCacheEntryCount().get());
        }
    }

    /**
     * A prefixed client never caches keys outside its prefix, for single-key reads and for MGET, so
     * it can never serve a stale value for a key the server will not invalidate.
     */
    @ParameterizedTest
    @MethodSource("clientModes")
    @SneakyThrows
    public void trackingPrefix_bypasses_cache_for_keys_outside_prefix(boolean cluster) {
        try (BaseClient cached = createClient(cluster, prefixedCache("xxx:"));
                BaseClient writer = createClient(cluster, null)) {
            String suffix = getRandomString(8);
            String tracked = "xxx:" + suffix;
            String tracked2 = "xxx:second:" + suffix;
            String untracked = "zzz:" + suffix;
            String untrackedHash = "zzz:hash:" + suffix;
            String untrackedSet = "zzz:set:" + suffix;
            assertEquals(OK, writer.set(tracked, "t1").get());
            assertEquals(OK, writer.set(tracked2, "s1").get());
            assertEquals(OK, writer.set(untracked, "u1").get());
            assertEquals(
                    1L, writer.hset(untrackedHash, Collections.singletonMap("field", "value")).get());
            assertEquals(1L, writer.sadd(untrackedSet, new String[] {"member"}).get());

            // GET on an untracked key: served by the server, not cached, not counted.
            assertCacheBypassed(cached, untracked, "u1");
            assertCacheBypassed(cached, untracked, "u1");
            assertEquals(0L, cached.getCacheEntryCount().get());
            assertEquals(0L, cached.getCacheTotalLookups().get());

            // The writer changes it and the prefixed client sees the new value immediately,
            // without any invalidation involved.
            assertEquals(OK, writer.set(untracked, "u2").get());
            assertCacheBypassed(cached, untracked, "u2");

            // Other cacheable commands on untracked keys are bypassed too.
            assertEquals(Collections.singletonMap("field", "value"), cached.hgetall(untrackedHash).get());
            assertEquals(Collections.singleton("member"), cached.smembers(untrackedSet).get());
            assertEquals(0L, cached.getCacheEntryCount().get());
            assertEquals(0L, cached.getCacheTotalLookups().get());

            // MGET mixing tracked and untracked keys returns the full ordered result and only
            // stores the tracked key. The tracked key is already cached, so it is a hit while the
            // untracked key is fetched from the server without being counted.
            warmCache(cached, tracked, "t1");
            assertEquals(1L, cached.getCacheEntryCount().get());
            long lookupsBefore = cached.getCacheTotalLookups().get();
            long hitsBefore = hits(cached);
            assertArrayEquals(
                    new String[] {"u2", "t1"}, cached.mget(new String[] {untracked, tracked}).get());
            assertEquals(1L, cached.getCacheEntryCount().get(), "the untracked key is not stored");
            assertEquals(lookupsBefore + 1, cached.getCacheTotalLookups().get(), "one lookup");
            assertEquals(hitsBefore + 1, hits(cached), "one hit for the tracked key");

            // A tracked key first seen through MGET is stored (one miss); the untracked key still
            // reflects a fresh write.
            assertEquals(OK, writer.set(untracked, "u3").get());
            lookupsBefore = cached.getCacheTotalLookups().get();
            hitsBefore = hits(cached);
            assertArrayEquals(
                    new String[] {"t1", "u3", "s1"},
                    cached.mget(new String[] {tracked, untracked, tracked2}).get());
            assertEquals(lookupsBefore + 2, cached.getCacheTotalLookups().get(), "two lookups");
            assertEquals(hitsBefore + 1, hits(cached), "one hit, one miss");
            // Settle on two entries: in cluster mode a late replica invalidation may evict and the
            // next MGET re-stores, so poll rather than assert once.
            waitFor(
                    () ->
                            Arrays.equals(
                                            new String[] {"t1", "u3", "s1"},
                                            cached.mget(new String[] {tracked, untracked, tracked2}).get())
                                    && cached.getCacheEntryCount().get() == 2L,
                    "both tracked keys were not stored by MGET");

            // The tracked key is still invalidated by the server.
            assertEquals(OK, writer.set(tracked, "t2").get());
            waitFor(
                    () ->
                            Arrays.equals(
                                    new String[] {"t2", "u3"}, cached.mget(new String[] {tracked, untracked}).get()),
                    "the tracked MGET key was not invalidated");
        }
    }

    /** Configuration errors are reported by the builder, before any connection is opened. */
    @Test
    public void trackingPrefix_configuration_errors() {
        // A prefix requires server-assisted tracking.
        IllegalArgumentException noServerAssisted =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ClientSideCache.builder()
                                        .maxCacheKb(1024L)
                                        .entryTtlMs(0L)
                                        .trackingPrefix("xxx:")
                                        .build());
        assertTrue(
                noServerAssisted.getMessage().contains("serverAssisted"), noServerAssisted.getMessage());

        // An empty prefix is rejected.
        IllegalArgumentException empty =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ClientSideCache.builder()
                                        .maxCacheKb(1024L)
                                        .entryTtlMs(0L)
                                        .serverAssisted(true)
                                        .trackingPrefix("")
                                        .build());
        assertTrue(empty.getMessage().contains("must not be empty"), empty.getMessage());
    }

    /** Two clients created with the same prefixed {@code ClientSideCache} share one cache. */
    @ParameterizedTest
    @MethodSource("clientModes")
    @SneakyThrows
    public void trackingPrefix_shared_cache(boolean cluster) {
        ClientSideCache sharedCache = prefixedCache("xxx:");
        try (BaseClient client1 = createClient(cluster, sharedCache);
                BaseClient client2 = createClient(cluster, sharedCache)) {
            assertTrackingPrefix(trackingInfo(client1), "xxx:");
            assertTrackingPrefix(trackingInfo(client2), "xxx:");

            String key = "xxx:shared_" + getRandomString(10);
            assertEquals(OK, client1.set(key, "value").get());
            warmCache(client1, key, "value");

            // The entry populated through client 1 is visible and served through client 2.
            assertEquals(1L, client2.getCacheEntryCount().get(), "entry visible through client 2");
            assertCacheHit(client2, key, "value");
            assertEquals(client1.getCacheTotalLookups().get(), client2.getCacheTotalLookups().get());

            // A key outside the prefix is not stored by either client.
            String outside = "yyy:shared_" + getRandomString(10);
            assertEquals(OK, client1.set(outside, "v").get());
            assertCacheBypassed(client1, outside, "v");
            assertCacheBypassed(client2, outside, "v");
            assertEquals(1L, client1.getCacheEntryCount().get());

            // An invalidation reaches the shared cache through either client's connection.
            assertEquals(OK, client2.set(key, "value2").get());
            waitFor(
                    () -> "value2".equals(client1.get(key).get()),
                    "shared prefixed cache was not invalidated");
        }
    }

    /**
     * A flush still clears the whole cache when a prefix is registered: the server sends the null
     * invalidation to every tracking client regardless of its prefixes.
     */
    @ParameterizedTest
    @MethodSource("clientModes")
    @SneakyThrows
    public void trackingPrefix_flush_clears_cache(boolean cluster) {
        try (BaseClient cached = createClient(cluster, prefixedCache("xxx:"))) {
            String key = "xxx:flush_" + getRandomString(8);
            assertEquals(OK, cached.set(key, "value").get());
            warmCache(cached, key, "value");
            assertEquals(1L, cached.getCacheEntryCount().get());

            if (cluster) {
                assertEquals(OK, ((GlideClusterClient) cached).flushall().get());
            } else {
                assertEquals(OK, ((GlideClient) cached).flushall().get());
            }

            waitFor(
                    () -> cached.getCacheEntryCount().get() == 0L,
                    "the null invalidation did not flush the prefixed cache");
        }
    }

    /** Cacheable multi-value commands on tracked keys are stored and served from the cache. */
    @ParameterizedTest
    @MethodSource("clientModes")
    @SneakyThrows
    public void trackingPrefix_caches_tracked_hash_and_set(boolean cluster) {
        try (BaseClient cached = createClient(cluster, prefixedCache("xxx:"));
                BaseClient writer = createClient(cluster, null)) {
            String suffix = getRandomString(8);
            String trackedHash = "xxx:hash:" + suffix;
            String trackedSet = "xxx:set:" + suffix;
            Map<String, String> hash = Collections.singletonMap("field", "value");
            Set<String> members = Collections.singleton("member");
            assertEquals(1L, writer.hset(trackedHash, hash).get());
            assertEquals(1L, writer.sadd(trackedSet, new String[] {"member"}).get());

            // HGETALL on a tracked key: stored on the first read, served from the cache after.
            waitFor(
                    () -> {
                        assertEquals(hash, cached.hgetall(trackedHash).get());
                        Thread.sleep(50);
                        long hitsBefore = hits(cached);
                        return hash.equals(cached.hgetall(trackedHash).get()) && hits(cached) == hitsBefore + 1;
                    },
                    "tracked hash was not served from the cache");
            assertEquals(1L, cached.getCacheEntryCount().get(), "hash entry stored");
            long lookupsBefore = cached.getCacheTotalLookups().get();
            long hitsBefore = hits(cached);
            assertEquals(hash, cached.hgetall(trackedHash).get());
            assertEquals(lookupsBefore + 1, cached.getCacheTotalLookups().get());
            assertEquals(hitsBefore + 1, hits(cached), "HGETALL hit");

            // SMEMBERS on a tracked key: same behaviour, one more entry.
            waitFor(
                    () -> {
                        assertEquals(members, cached.smembers(trackedSet).get());
                        Thread.sleep(50);
                        long before = hits(cached);
                        return members.equals(cached.smembers(trackedSet).get()) && hits(cached) == before + 1;
                    },
                    "tracked set was not served from the cache");
            assertEquals(2L, cached.getCacheEntryCount().get(), "set entry stored");
            lookupsBefore = cached.getCacheTotalLookups().get();
            hitsBefore = hits(cached);
            assertEquals(members, cached.smembers(trackedSet).get());
            assertEquals(lookupsBefore + 1, cached.getCacheTotalLookups().get());
            assertEquals(hitsBefore + 1, hits(cached), "SMEMBERS hit");

            // Writes to the tracked keys are still invalidated by the server.
            assertEquals(1L, writer.hset(trackedHash, Collections.singletonMap("f2", "v2")).get());
            waitFor(
                    () -> cached.hgetall(trackedHash).get().containsKey("f2"),
                    "tracked hash was not invalidated");
            assertEquals(1L, writer.sadd(trackedSet, new String[] {"m2"}).get());
            waitFor(
                    () -> cached.smembers(trackedSet).get().contains("m2"),
                    "tracked set was not invalidated");
        }
    }

    /**
     * CLIENT TRACKINGINFO through a binary-safe custom command: one reply per node connection in
     * cluster mode, a single reply in standalone mode.
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private static Collection<Map<GlideString, Object>> binaryTrackingInfos(BaseClient client) {
        GlideString[] args = {gs("CLIENT"), gs("TRACKINGINFO")};
        if (client instanceof GlideClusterClient) {
            Map<String, Object> perNode =
                    ((GlideClusterClient) client).customCommand(args, ALL_NODES).get().getMultiValue();
            assertTrue(perNode.size() > 1, "expected a multi-node reply");
            return perNode.values().stream()
                    .map(info -> (Map<GlideString, Object>) info)
                    .collect(Collectors.toList());
        }
        return Collections.singletonList(
                (Map<GlideString, Object>) ((GlideClient) client).customCommand(args).get());
    }

    /** A prefix made of arbitrary bytes is registered byte for byte and works end to end. */
    @ParameterizedTest
    @MethodSource("clientModes")
    @SneakyThrows
    public void trackingPrefix_binary_prefix(boolean cluster) {
        byte[] prefix = {(byte) 0xff, 0, ':'};
        ClientSideCache cache =
                ClientSideCache.builder()
                        .maxCacheKb(1024L)
                        .entryTtlMs(0L)
                        .enableMetrics(true)
                        .serverAssisted(true)
                        .trackingPrefix(gs(prefix))
                        .build();
        assertArrayEquals(prefix, cache.getTrackingPrefix().getBytes());

        try (BaseClient cached = createClient(cluster, cache);
                BaseClient writer = createClient(cluster, null)) {
            // Every server connection reports exactly the configured bytes.
            for (Map<GlideString, Object> info : binaryTrackingInfos(cached)) {
                Object[] prefixes = (Object[]) info.get(gs("prefixes"));
                assertEquals(1, prefixes.length, "prefixes: " + Arrays.toString(prefixes));
                assertArrayEquals(prefix, ((GlideString) prefixes[0]).getBytes());
            }

            // A key under the binary prefix is cached and served from the cache.
            byte[] suffix = getRandomString(8).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = new byte[prefix.length + suffix.length];
            System.arraycopy(prefix, 0, keyBytes, 0, prefix.length);
            System.arraycopy(suffix, 0, keyBytes, prefix.length, suffix.length);
            GlideString key = gs(keyBytes);
            assertEquals(OK, writer.set(key, gs("v1")).get());
            waitFor(
                    () -> {
                        if (!gs("v1").equals(cached.get(key).get())) {
                            return false;
                        }
                        Thread.sleep(50);
                        long hitsBefore = hits(cached);
                        return gs("v1").equals(cached.get(key).get()) && hits(cached) == hitsBefore + 1;
                    },
                    "binary-prefixed key was not served from the cache");
            assertEquals(1L, cached.getCacheEntryCount().get());

            // A key outside the binary prefix (same bytes, different first byte) is bypassed.
            keyBytes[0] = (byte) 0xfe;
            GlideString outside = gs(keyBytes.clone());
            assertEquals(OK, writer.set(outside, gs("o1")).get());
            long lookupsBefore = cached.getCacheTotalLookups().get();
            assertEquals(gs("o1"), cached.get(outside).get());
            assertEquals(1L, cached.getCacheEntryCount().get(), "key outside the prefix not cached");
            assertEquals(lookupsBefore, cached.getCacheTotalLookups().get());

            // A write from another client invalidates the cached binary-prefixed key.
            assertEquals(OK, writer.set(key, gs("v2")).get());
            waitFor(
                    () -> gs("v2").equals(cached.get(key).get()), "binary-prefixed key was not invalidated");
        }
    }
}
