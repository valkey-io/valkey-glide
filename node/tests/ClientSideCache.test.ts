/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
} from "@jest/globals";
import { ValkeyCluster } from "../../utils/TestUtils.js";
import {
    ClientSideCache,
    EvictionPolicy,
    GlideClient,
    GlideClusterClient,
    ProtocolVersion,
} from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLEANUP_TIMEOUT = 10000;

describe("ClientSideCache", () => {
    let standaloneCluster: ValkeyCluster;
    let clusterCluster: ValkeyCluster;

    beforeAll(async () => {
        const standaloneAddresses: string =
            global.STAND_ALONE_ENDPOINT as string;
        const clusterAddresses: string = global.CLUSTER_ENDPOINTS as string;

        // Create standalone cluster
        standaloneCluster = standaloneAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  false,
                  parseEndpoints(standaloneAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(false, 1, 1, getServerVersion);

        // Add small delay between cluster initializations
        await new Promise((resolve) => setTimeout(resolve, 100));

        // Create cluster mode cluster
        clusterCluster = clusterAddresses
            ? await ValkeyCluster.initFromExistingCluster(
                  true,
                  parseEndpoints(clusterAddresses),
                  getServerVersion,
              )
            : await ValkeyCluster.createCluster(true, 3, 1, getServerVersion);
    }, 30000);

    afterAll(async () => {
        await standaloneCluster.close();
        await new Promise((resolve) => setTimeout(resolve, 50));
        await clusterCluster.close();
    }, CLEANUP_TIMEOUT);

    /**
     * Helper to set a key on the server and GET it to populate the client-side cache.
     */
    async function setAndCacheKey(
        client: GlideClient | GlideClusterClient,
        key: string,
        value: string,
    ): Promise<void> {
        expect(await client.set(key, value)).toBe("OK");
        expect(await client.get(key)).toBe(value);
    }

    describe("Standalone Client-side Cache Tests", () => {
        let client: GlideClient;

        afterEach(async () => {
            await flushAndCloseClient(
                false,
                standaloneCluster?.getAddresses(),
                client,
            );
            await new Promise((resolve) => setTimeout(resolve, 10));
        });

        /**
         * Helper function to create a standalone client with cache configuration
         */
        async function createStandaloneClientWithCache(
            protocol: ProtocolVersion,
            cache: ClientSideCache | null,
        ): Promise<GlideClient> {
            const config = getClientConfigurationOption(
                standaloneCluster.getAddresses(),
                protocol,
                cache ? { clientSideCache: cache } : {},
            );

            return await GlideClient.createClient(config);
        }

        describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "Standalone mode with protocol %p",
            (protocol) => {
                it(
                    "test basic cache hit with metrics",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        // Set a key
                        expect(
                            await client.set(
                                "cache_test_key",
                                "cache_test_value",
                            ),
                        ).toBe("OK");

                        // First GET - cache miss
                        expect(await client.get("cache_test_key")).toBe(
                            "cache_test_value",
                        );

                        // Entry count should be 1
                        expect(await client.getCacheEntryCount()).toBe(1);

                        // Second GET - cache hit
                        expect(await client.get("cache_test_key")).toBe(
                            "cache_test_value",
                        );

                        // Third GET - cache hit
                        expect(await client.get("cache_test_key")).toBe(
                            "cache_test_value",
                        );

                        // Verify metrics: 1 miss + 2 hits = 3 total
                        const hitRate = await client.getCacheHitRate();
                        const missRate = await client.getCacheMissRate();

                        expect(hitRate).toBeCloseTo(2.0 / 3.0, 2); // ~66.67%
                        expect(missRate).toBeCloseTo(1.0 / 3.0, 2); // ~33.33%
                        expect(Math.abs(hitRate + missRate - 1.0)).toBeLessThan(
                            0.0001,
                        );

                        // Verify total lookups: 1 miss + 2 hits = 3
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(3);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache without metrics",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: false, // Disabled
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        // Cache should work
                        expect(await client.set("key", "value")).toBe("OK");
                        expect(await client.get("key")).toBe("value");
                        expect(await client.get("key")).toBe("value"); // Should be cached

                        // Metrics should fail
                        await expect(client.getCacheHitRate()).rejects.toThrow(
                            /metrics/i,
                        );
                        await expect(client.getCacheMissRate()).rejects.toThrow(
                            /metrics/i,
                        );
                        await expect(
                            client.getCacheEvictions(),
                        ).rejects.toThrow(/metrics/i);
                        await expect(
                            client.getCacheExpirations(),
                        ).rejects.toThrow(/metrics/i);
                        await expect(
                            client.getCacheTotalLookups(),
                        ).rejects.toThrow(/metrics/i);

                        // Entry count should still work
                        expect(await client.getCacheEntryCount()).toBe(1);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache nil values not cached",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        // GET non-existent key (returns null)
                        expect(await client.get("nonexistent_key")).toBeNull();

                        // Entry count should be 0
                        expect(await client.getCacheEntryCount()).toBe(0);

                        // GET again - should NOT be cached (NIL values not cached)
                        expect(await client.get("nonexistent_key")).toBeNull();

                        // Miss rate should be 100%
                        const missRate = await client.getCacheMissRate();
                        expect(missRate).toBe(1.0);

                        // Total lookups should be 2
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(2);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache ttl expiration",
                    async () => {
                        const cache = ClientSideCache.create(1, 2000, {
                            // 2 seconds
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        // Set and GET
                        expect(await client.set("ttl_key", "ttl_value")).toBe(
                            "OK",
                        );
                        expect(await client.get("ttl_key")).toBe("ttl_value");

                        expect(await client.getCacheEntryCount()).toBe(1);

                        // Second GET - from cache
                        expect(await client.get("ttl_key")).toBe("ttl_value");

                        // Wait for TTL to expire
                        await new Promise((resolve) =>
                            setTimeout(resolve, 3000),
                        );

                        // GET after expiration - should fetch from server again
                        expect(await client.get("ttl_key")).toBe("ttl_value");

                        // Expiration count should be 1
                        const expirations = await client.getCacheExpirations();
                        expect(expirations).toBe(1);

                        // Miss rate should be 2 misses out of 3 total = 66.67%
                        const missRate = await client.getCacheMissRate();
                        expect(missRate).toBeCloseTo(2.0 / 3.0, 2);

                        // Total lookups should be 3
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(3);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache multiple keys",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        // Set 3 keys
                        for (let i = 1; i <= 3; i++) {
                            expect(
                                await client.set(`key${i}`, `value${i}`),
                            ).toBe("OK");
                        }

                        // GET each key twice (miss + hit)
                        for (let i = 1; i <= 3; i++) {
                            expect(await client.get(`key${i}`)).toBe(
                                `value${i}`,
                            );
                            expect(await client.get(`key${i}`)).toBe(
                                `value${i}`,
                            );
                        }

                        // Entry count should be 3
                        expect(await client.getCacheEntryCount()).toBe(3);

                        // Verify metrics: 3 misses + 3 hits = 50% hit rate
                        const hitRate = await client.getCacheHitRate();
                        expect(hitRate).toBe(0.5);

                        // Total lookups should be 6
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(6);
                    },
                    TIMEOUT,
                );

                it(
                    "test no cache metrics",
                    async () => {
                        // No cache configured
                        client = await createStandaloneClientWithCache(
                            protocol,
                            null,
                        );

                        // Set and GET multiple times
                        expect(await client.set("key", "value")).toBe("OK");
                        expect(await client.get("key")).toBe("value");
                        expect(await client.get("key")).toBe("value");
                        expect(await client.get("key")).toBe("value");

                        // Metrics should error
                        await expect(client.getCacheHitRate()).rejects.toThrow(
                            /not enabled/i,
                        );
                        await expect(client.getCacheMissRate()).rejects.toThrow(
                            /not enabled/i,
                        );
                        await expect(
                            client.getCacheEvictions(),
                        ).rejects.toThrow(/not enabled/i);
                        await expect(
                            client.getCacheExpirations(),
                        ).rejects.toThrow(/not enabled/i);
                        await expect(
                            client.getCacheTotalLookups(),
                        ).rejects.toThrow(/not enabled/i);
                        await expect(
                            client.getCacheEntryCount(),
                        ).rejects.toThrow(/not enabled/i);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache eviction policy lru",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            // 1 KB to force eviction
                            evictionPolicy: EvictionPolicy.LRU,
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        // Use larger values to force eviction
                        const value = "x".repeat(250); // ~250 bytes

                        // Set and cache 3 keys
                        for (let i = 1; i <= 3; i++) {
                            await setAndCacheKey(client, `lru_key${i}`, value);
                        }

                        // Cache should have 3 entries now
                        expect(await client.getCacheEntryCount()).toBe(3);

                        // Access key1 to make it recently used
                        expect(await client.get("lru_key1")).toBe(value);

                        // Add 2 more keys - should evict key2 and key3 (least recently used)
                        for (let i = 4; i <= 5; i++) {
                            await setAndCacheKey(client, `lru_key${i}`, value);
                        }

                        // Verify 2 evictions occurred
                        const evictions = await client.getCacheEvictions();
                        expect(evictions).toBe(2);

                        // Verify cache is working (hit rate > 0)
                        const hitRate = await client.getCacheHitRate();
                        expect(hitRate).toBeGreaterThan(0);

                        // Check that key1 is still cached
                        expect(await client.get("lru_key1")).toBe(value);
                        const newHitRate = await client.getCacheHitRate();
                        expect(newHitRate).toBeGreaterThan(hitRate);

                        // Check that key2 and key3 are evicted
                        const oldMissRate = await client.getCacheMissRate();
                        expect(await client.get("lru_key2")).toBe(value);
                        expect(await client.get("lru_key3")).toBe(value);
                        const newMissRate = await client.getCacheMissRate();
                        expect(newMissRate).toBeGreaterThan(oldMissRate);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache eviction policy lfu",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            // 1 KB - small cache to trigger evictions
                            evictionPolicy: EvictionPolicy.LFU,
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        const value = "x".repeat(250); // ~250 bytes

                        // Set key1 and access it multiple times (high frequency)
                        expect(await client.set("key1", value)).toBe("OK");

                        for (let i = 0; i < 5; i++) {
                            expect(await client.get("key1")).toBe(value);
                        }
                        // key1 frequency: 5

                        // Set key2 and access it a few times (medium frequency)
                        expect(await client.set("key2", value)).toBe("OK");

                        for (let i = 0; i < 2; i++) {
                            expect(await client.get("key2")).toBe(value);
                        }
                        // key2 frequency: 2

                        // Set key3 with minimal access (low frequency)
                        await setAndCacheKey(client, "key3", value);
                        // key3 frequency: 1

                        // Verify cache is working
                        const hitRate = await client.getCacheHitRate();
                        expect(hitRate).toBeGreaterThan(0);

                        // Cache should have 3 entries now
                        expect(await client.getCacheEntryCount()).toBe(3);

                        // Set key4 - this should trigger eviction of key3 (lowest frequency)
                        await setAndCacheKey(client, "key4", value);
                        // key4 frequency: 1

                        // Check that cache entry count is still 3
                        expect(await client.getCacheEntryCount()).toBe(3);
                        // Verify 1 eviction occurred
                        expect(await client.getCacheEvictions()).toBe(1);

                        // Check that key1 (highest frequency) is still cached
                        const oldHitRate = await client.getCacheHitRate();
                        expect(await client.get("key1")).toBe(value);
                        const newHitRate = await client.getCacheHitRate();
                        expect(newHitRate).toBeGreaterThan(oldHitRate);

                        // Check that key3 (lowest frequency) was evicted
                        const oldMissRate = await client.getCacheMissRate();
                        expect(await client.get("key3")).toBe(value); // Should be a miss
                        const newMissRate = await client.getCacheMissRate();
                        expect(newMissRate).toBeGreaterThan(oldMissRate);

                        // key2 (medium frequency) should still be cached
                        const oldHitRate2 = await client.getCacheHitRate();
                        expect(await client.get("key2")).toBe(value);
                        const newHitRate2 = await client.getCacheHitRate();
                        expect(newHitRate2).toBeGreaterThan(oldHitRate2);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache max memory limit",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            // 1 KB
                            evictionPolicy: EvictionPolicy.LRU,
                            enableMetrics: true,
                        });

                        const client1 = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );
                        const client2 = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        try {
                            // Create values that are ~400 bytes each
                            const largeValue = "x".repeat(400);

                            // Add 10 keys to force eviction
                            for (let i = 1; i <= 10; i++) {
                                await setAndCacheKey(
                                    client1,
                                    `key${i}`,
                                    largeValue,
                                );
                                expect(await client1.get(`key${i}`)).toBe(
                                    largeValue,
                                );
                            }

                            const currentHitRate =
                                await client1.getCacheHitRate();
                            expect(currentHitRate).toBeGreaterThan(0);

                            // Check that key 1 is evicted and key 10 exists
                            expect(await client2.get("key1")).toBe(largeValue);
                            const hitRate = await client2.getCacheHitRate();
                            expect(hitRate).toBeLessThan(currentHitRate);

                            expect(await client2.get("key10")).toBe(largeValue);
                            const newHitRate = await client2.getCacheHitRate();
                            expect(newHitRate).toBeGreaterThan(hitRate);

                            // Verify that evictions occurred
                            const evictions = await client1.getCacheEvictions();
                            expect(evictions).toBeGreaterThan(0);
                        } finally {
                            client1.close();
                            client2.close();
                        }
                    },
                    TIMEOUT,
                );

                it(
                    "test shared cache",
                    async () => {
                        const cache = ClientSideCache.create(1024, 60000, {
                            enableMetrics: true,
                        });

                        const client1 = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );
                        const client2 = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        try {
                            expect(
                                await client1.set("shared_key", "value"),
                            ).toBe("OK");
                            expect(await client1.get("shared_key")).toBe(
                                "value",
                            );

                            // Entry count should be 1
                            const entryCount =
                                await client2.getCacheEntryCount();
                            expect(entryCount).toBe(1);

                            expect(await client2.get("shared_key")).toBe(
                                "value",
                            );

                            expect(await client2.getCacheHitRate()).toBe(0.5);
                            expect(await client1.getCacheHitRate()).toBe(0.5);

                            // Total lookups should be 2
                            const totalLookups =
                                await client1.getCacheTotalLookups();
                            expect(totalLookups).toBe(2);
                        } finally {
                            client1.close();
                            client2.close();
                        }
                    },
                    TIMEOUT,
                );

                it(
                    "test cache wrong key type raises error",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        expect(await client.set("string-key", "value")).toBe(
                            "OK",
                        );
                        expect(await client.get("string-key")).toBe("value");

                        await expect(
                            client.hgetall("string-key"),
                        ).rejects.toThrow(
                            expect.objectContaining({
                                message: expect.stringContaining("WRONGTYPE"),
                            }),
                        );
                    },
                    TIMEOUT,
                );

                it(
                    "test cacheable commands",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createStandaloneClientWithCache(
                            protocol,
                            cache,
                        );

                        // SET command - not cacheable
                        expect(await client.set("key", "value")).toBe("OK");

                        // GET command - cacheable
                        expect(await client.get("key")).toBe("value");

                        // Check that now the cache entry count is 1
                        let entryCount = await client.getCacheEntryCount();
                        expect(entryCount).toBe(1);

                        // HGETALL command - cacheable
                        expect(
                            await client.hset("hashkey", { field1: "val1" }),
                        ).toBe(1);
                        const hashResult = await client.hgetall("hashkey");
                        expect(hashResult).toEqual([
                            { field: "field1", value: "val1" },
                        ]);

                        entryCount = await client.getCacheEntryCount();
                        expect(entryCount).toBe(2);

                        // SMEMBERS command - cacheable
                        expect(await client.sadd("setkey", ["member1"])).toBe(
                            1,
                        );
                        const setResult = await client.smembers("setkey");
                        expect(setResult).toEqual(new Set(["member1"]));

                        entryCount = await client.getCacheEntryCount();
                        expect(entryCount).toBe(3);

                        await client.flushall();
                    },
                    TIMEOUT,
                );
            },
        );
    });

    describe("Cluster Client-side Cache Tests", () => {
        let client: GlideClusterClient;

        afterEach(async () => {
            await flushAndCloseClient(
                true,
                clusterCluster?.getAddresses(),
                client,
            );
            await new Promise((resolve) => setTimeout(resolve, 10));
        });

        /**
         * Helper function to create a cluster client with cache configuration
         */
        async function createClusterClientWithCache(
            protocol: ProtocolVersion,
            cache: ClientSideCache | null,
        ): Promise<GlideClusterClient> {
            const config = getClientConfigurationOption(
                clusterCluster.getAddresses(),
                protocol,
                cache ? { clientSideCache: cache } : {},
            );

            return await GlideClusterClient.createClient(config);
        }

        describe.each([ProtocolVersion.RESP2, ProtocolVersion.RESP3])(
            "Cluster mode with protocol %p",
            (protocol) => {
                it(
                    "test basic cache hit with metrics",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        // Set a key (use hash tag for cluster mode)
                        expect(
                            await client.set(
                                "{cache}test_key",
                                "cache_test_value",
                            ),
                        ).toBe("OK");

                        // First GET - cache miss
                        expect(await client.get("{cache}test_key")).toBe(
                            "cache_test_value",
                        );

                        // Entry count should be 1
                        expect(await client.getCacheEntryCount()).toBe(1);

                        // Second GET - cache hit
                        expect(await client.get("{cache}test_key")).toBe(
                            "cache_test_value",
                        );

                        // Third GET - cache hit
                        expect(await client.get("{cache}test_key")).toBe(
                            "cache_test_value",
                        );

                        // Verify metrics: 1 miss + 2 hits = 3 total
                        const hitRate = await client.getCacheHitRate();
                        const missRate = await client.getCacheMissRate();

                        expect(hitRate).toBeCloseTo(2.0 / 3.0, 2); // ~66.67%
                        expect(missRate).toBeCloseTo(1.0 / 3.0, 2); // ~33.33%
                        expect(Math.abs(hitRate + missRate - 1.0)).toBeLessThan(
                            0.0001,
                        );

                        // Verify total lookups: 1 miss + 2 hits = 3
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(3);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache without metrics",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: false, // Disabled
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        // Cache should work
                        expect(await client.set("{cache}key", "value")).toBe(
                            "OK",
                        );
                        expect(await client.get("{cache}key")).toBe("value");
                        expect(await client.get("{cache}key")).toBe("value"); // Should be cached

                        // Metrics should fail
                        await expect(client.getCacheHitRate()).rejects.toThrow(
                            /metrics/i,
                        );
                        await expect(client.getCacheMissRate()).rejects.toThrow(
                            /metrics/i,
                        );
                        await expect(
                            client.getCacheEvictions(),
                        ).rejects.toThrow(/metrics/i);
                        await expect(
                            client.getCacheExpirations(),
                        ).rejects.toThrow(/metrics/i);
                        await expect(
                            client.getCacheTotalLookups(),
                        ).rejects.toThrow(/metrics/i);

                        // Entry count should still work
                        expect(await client.getCacheEntryCount()).toBe(1);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache nil values not cached",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        // GET non-existent key (returns null)
                        expect(
                            await client.get("{cache}nonexistent_key"),
                        ).toBeNull();

                        // Entry count should be 0
                        expect(await client.getCacheEntryCount()).toBe(0);

                        // GET again - should NOT be cached (NIL values not cached)
                        expect(
                            await client.get("{cache}nonexistent_key"),
                        ).toBeNull();

                        // Miss rate should be 100%
                        const missRate = await client.getCacheMissRate();
                        expect(missRate).toBe(1.0);

                        // Total lookups should be 2
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(2);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache ttl expiration",
                    async () => {
                        const cache = ClientSideCache.create(1, 2000, {
                            // 2 seconds
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        // Set and GET
                        expect(
                            await client.set("{cache}ttl_key", "ttl_value"),
                        ).toBe("OK");
                        expect(await client.get("{cache}ttl_key")).toBe(
                            "ttl_value",
                        );

                        expect(await client.getCacheEntryCount()).toBe(1);

                        // Second GET - from cache
                        expect(await client.get("{cache}ttl_key")).toBe(
                            "ttl_value",
                        );

                        // Wait for TTL to expire
                        await new Promise((resolve) =>
                            setTimeout(resolve, 3000),
                        );

                        // GET after expiration - should fetch from server again
                        expect(await client.get("{cache}ttl_key")).toBe(
                            "ttl_value",
                        );

                        // Expiration count should be 1
                        const expirations = await client.getCacheExpirations();
                        expect(expirations).toBe(1);

                        // Miss rate should be 2 misses out of 3 total = 66.67%
                        const missRate = await client.getCacheMissRate();
                        expect(missRate).toBeCloseTo(2.0 / 3.0, 2);

                        // Total lookups should be 3
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(3);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache multiple keys",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        // Set 3 keys (use hash tags for cluster mode)
                        for (let i = 1; i <= 3; i++) {
                            expect(
                                await client.set(`{cache}key${i}`, `value${i}`),
                            ).toBe("OK");
                        }

                        // GET each key twice (miss + hit)
                        for (let i = 1; i <= 3; i++) {
                            expect(await client.get(`{cache}key${i}`)).toBe(
                                `value${i}`,
                            );
                            expect(await client.get(`{cache}key${i}`)).toBe(
                                `value${i}`,
                            );
                        }

                        // Entry count should be 3
                        expect(await client.getCacheEntryCount()).toBe(3);

                        // Verify metrics: 3 misses + 3 hits = 50% hit rate
                        const hitRate = await client.getCacheHitRate();
                        expect(hitRate).toBe(0.5);

                        // Total lookups should be 6
                        const totalLookups =
                            await client.getCacheTotalLookups();
                        expect(totalLookups).toBe(6);
                    },
                    TIMEOUT,
                );

                it(
                    "test no cache metrics",
                    async () => {
                        // No cache configured
                        client = await createClusterClientWithCache(
                            protocol,
                            null,
                        );

                        // Set and GET multiple times
                        expect(await client.set("{cache}key", "value")).toBe(
                            "OK",
                        );
                        expect(await client.get("{cache}key")).toBe("value");
                        expect(await client.get("{cache}key")).toBe("value");
                        expect(await client.get("{cache}key")).toBe("value");

                        // Metrics should error
                        await expect(client.getCacheHitRate()).rejects.toThrow(
                            /not enabled/i,
                        );
                        await expect(client.getCacheMissRate()).rejects.toThrow(
                            /not enabled/i,
                        );
                        await expect(
                            client.getCacheEvictions(),
                        ).rejects.toThrow(/not enabled/i);
                        await expect(
                            client.getCacheExpirations(),
                        ).rejects.toThrow(/not enabled/i);
                        await expect(
                            client.getCacheTotalLookups(),
                        ).rejects.toThrow(/not enabled/i);
                        await expect(
                            client.getCacheEntryCount(),
                        ).rejects.toThrow(/not enabled/i);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache eviction policy lru",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            // 1 KB to force eviction
                            evictionPolicy: EvictionPolicy.LRU,
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        // Use larger values to force eviction
                        const value = "x".repeat(250); // ~250 bytes

                        // Set and cache 3 keys (use hash tags for cluster mode)
                        for (let i = 1; i <= 3; i++) {
                            await setAndCacheKey(
                                client,
                                `{cache}lru_key${i}`,
                                value,
                            );
                        }

                        // Cache should have 3 entries now
                        expect(await client.getCacheEntryCount()).toBe(3);

                        // Access key1 to make it recently used
                        expect(await client.get("{cache}lru_key1")).toBe(value);

                        // Add 2 more keys - should evict key2 and key3 (least recently used)
                        for (let i = 4; i <= 5; i++) {
                            await setAndCacheKey(
                                client,
                                `{cache}lru_key${i}`,
                                value,
                            );
                        }

                        // Verify 2 evictions occurred
                        const evictions = await client.getCacheEvictions();
                        expect(evictions).toBe(2);

                        // Verify cache is working (hit rate > 0)
                        const hitRate = await client.getCacheHitRate();
                        expect(hitRate).toBeGreaterThan(0);

                        // Check that key1 is still cached
                        expect(await client.get("{cache}lru_key1")).toBe(value);
                        const newHitRate = await client.getCacheHitRate();
                        expect(newHitRate).toBeGreaterThan(hitRate);

                        // Check that key2 and key3 are evicted
                        const oldMissRate = await client.getCacheMissRate();
                        expect(await client.get("{cache}lru_key2")).toBe(value);
                        expect(await client.get("{cache}lru_key3")).toBe(value);
                        const newMissRate = await client.getCacheMissRate();
                        expect(newMissRate).toBeGreaterThan(oldMissRate);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache eviction policy lfu",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            // 1 KB - small cache to trigger evictions
                            evictionPolicy: EvictionPolicy.LFU,
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        const value = "x".repeat(250); // ~250 bytes

                        // Set key1 and access it multiple times (high frequency)
                        expect(await client.set("{cache}key1", value)).toBe(
                            "OK",
                        );

                        for (let i = 0; i < 5; i++) {
                            expect(await client.get("{cache}key1")).toBe(value);
                        }
                        // key1 frequency: 5

                        // Set key2 and access it a few times (medium frequency)
                        expect(await client.set("{cache}key2", value)).toBe(
                            "OK",
                        );

                        for (let i = 0; i < 2; i++) {
                            expect(await client.get("{cache}key2")).toBe(value);
                        }
                        // key2 frequency: 2

                        // Set key3 with minimal access (low frequency)
                        await setAndCacheKey(client, "{cache}key3", value);
                        // key3 frequency: 1

                        // Verify cache is working
                        const hitRate = await client.getCacheHitRate();
                        expect(hitRate).toBeGreaterThan(0);

                        // Cache should have 3 entries now
                        expect(await client.getCacheEntryCount()).toBe(3);

                        // Set key4 - this should trigger eviction of key3 (lowest frequency)
                        await setAndCacheKey(client, "{cache}key4", value);
                        // key4 frequency: 1

                        // Check that cache entry count is still 3
                        expect(await client.getCacheEntryCount()).toBe(3);
                        // Verify 1 eviction occurred
                        expect(await client.getCacheEvictions()).toBe(1);

                        // Check that key1 (highest frequency) is still cached
                        const oldHitRate = await client.getCacheHitRate();
                        expect(await client.get("{cache}key1")).toBe(value);
                        const newHitRate = await client.getCacheHitRate();
                        expect(newHitRate).toBeGreaterThan(oldHitRate);

                        // Check that key3 (lowest frequency) was evicted
                        const oldMissRate = await client.getCacheMissRate();
                        expect(await client.get("{cache}key3")).toBe(value); // Should be a miss
                        const newMissRate = await client.getCacheMissRate();
                        expect(newMissRate).toBeGreaterThan(oldMissRate);

                        // key2 (medium frequency) should still be cached
                        const oldHitRate2 = await client.getCacheHitRate();
                        expect(await client.get("{cache}key2")).toBe(value);
                        const newHitRate2 = await client.getCacheHitRate();
                        expect(newHitRate2).toBeGreaterThan(oldHitRate2);
                    },
                    TIMEOUT,
                );

                it(
                    "test cache max memory limit",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            // 1 KB
                            evictionPolicy: EvictionPolicy.LRU,
                            enableMetrics: true,
                        });

                        const client1 = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );
                        const client2 = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        try {
                            // Create values that are ~400 bytes each
                            const largeValue = "x".repeat(400);

                            // Add 10 keys to force eviction (use hash tags for cluster mode)
                            for (let i = 1; i <= 10; i++) {
                                await setAndCacheKey(
                                    client1,
                                    `{cache}key${i}`,
                                    largeValue,
                                );
                                expect(
                                    await client1.get(`{cache}key${i}`),
                                ).toBe(largeValue);
                            }

                            const currentHitRate =
                                await client1.getCacheHitRate();
                            expect(currentHitRate).toBeGreaterThan(0);

                            // Check that key 1 is evicted and key 10 exists
                            expect(await client2.get("{cache}key1")).toBe(
                                largeValue,
                            );
                            const hitRate = await client2.getCacheHitRate();
                            expect(hitRate).toBeLessThan(currentHitRate);

                            expect(await client2.get("{cache}key10")).toBe(
                                largeValue,
                            );
                            const newHitRate = await client2.getCacheHitRate();
                            expect(newHitRate).toBeGreaterThan(hitRate);

                            // Verify that evictions occurred
                            const evictions = await client1.getCacheEvictions();
                            expect(evictions).toBeGreaterThan(0);
                        } finally {
                            client1.close();
                            client2.close();
                        }
                    },
                    TIMEOUT,
                );

                it(
                    "test shared cache",
                    async () => {
                        const cache = ClientSideCache.create(1024, 60000, {
                            enableMetrics: true,
                        });

                        const client1 = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );
                        const client2 = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        try {
                            expect(
                                await client1.set("{cache}shared_key", "value"),
                            ).toBe("OK");
                            expect(await client1.get("{cache}shared_key")).toBe(
                                "value",
                            );

                            // Entry count should be 1
                            const entryCount =
                                await client2.getCacheEntryCount();
                            expect(entryCount).toBe(1);

                            expect(await client2.get("{cache}shared_key")).toBe(
                                "value",
                            );

                            expect(await client2.getCacheHitRate()).toBe(0.5);
                            expect(await client1.getCacheHitRate()).toBe(0.5);

                            // Total lookups should be 2
                            const totalLookups =
                                await client1.getCacheTotalLookups();
                            expect(totalLookups).toBe(2);
                        } finally {
                            client1.close();
                            client2.close();
                        }
                    },
                    TIMEOUT,
                );

                it(
                    "test cache wrong key type raises error",
                    async () => {
                        const cache = ClientSideCache.create(1, 0, {
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        expect(
                            await client.set("{cache}string-key", "value"),
                        ).toBe("OK");
                        expect(await client.get("{cache}string-key")).toBe(
                            "value",
                        );

                        await expect(
                            client.hgetall("{cache}string-key"),
                        ).rejects.toThrow(
                            expect.objectContaining({
                                message: expect.stringContaining("WRONGTYPE"),
                            }),
                        );
                    },
                    TIMEOUT,
                );

                it(
                    "test cacheable commands",
                    async () => {
                        const cache = ClientSideCache.create(1, 60000, {
                            enableMetrics: true,
                        });

                        client = await createClusterClientWithCache(
                            protocol,
                            cache,
                        );

                        // SET command - not cacheable
                        expect(await client.set("{cache}key", "value")).toBe(
                            "OK",
                        );

                        // GET command - cacheable
                        expect(await client.get("{cache}key")).toBe("value");

                        // Check that now the cache entry count is 1
                        let entryCount = await client.getCacheEntryCount();
                        expect(entryCount).toBe(1);

                        // HGETALL command - cacheable
                        expect(
                            await client.hset("{cache}hashkey", {
                                field1: "val1",
                            }),
                        ).toBe(1);
                        const hashResult =
                            await client.hgetall("{cache}hashkey");
                        expect(hashResult).toEqual([
                            { field: "field1", value: "val1" },
                        ]);

                        entryCount = await client.getCacheEntryCount();
                        expect(entryCount).toBe(2);

                        // SMEMBERS command - cacheable
                        expect(
                            await client.sadd("{cache}setkey", ["member1"]),
                        ).toBe(1);
                        const setResult =
                            await client.smembers("{cache}setkey");
                        expect(setResult).toEqual(new Set(["member1"]));

                        entryCount = await client.getCacheEntryCount();
                        expect(entryCount).toBe(3);

                        await client.flushall();
                    },
                    TIMEOUT,
                );
            },
        );
    });
});
