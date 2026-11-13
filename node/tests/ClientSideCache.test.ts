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
    ClientTrackingInfo,
    Decoder,
    EvictionPolicy,
    GlideClient,
    GlideClusterClient,
    GlideRecord,
    GlideString,
    ProtocolVersion,
} from "../build-ts";
import {
    flushAndCloseClient,
    getClientConfigurationOption,
    getServerVersion,
    parseEndpoints,
    waitFor,
} from "./TestUtilities";

const TIMEOUT = 50000;
const CLEANUP_TIMEOUT = 10000;

describe("ClientSideCache tracking prefix configuration", () => {
    const base = { maxCacheKb: 1024, entryTtlMs: 0 };

    it("defaults trackingPrefix to undefined", () => {
        expect(new ClientSideCache(base).trackingPrefix).toBeUndefined();
        expect(ClientSideCache.create(1024, 0).trackingPrefix).toBeUndefined();
        expect(
            new ClientSideCache({ ...base, serverAssisted: true })
                .trackingPrefix,
        ).toBeUndefined();
    });

    it("accepts a string prefix with serverAssisted", () => {
        const cache = new ClientSideCache({
            ...base,
            serverAssisted: true,
            trackingPrefix: "xxx:",
        });
        expect(cache.trackingPrefix).toBe("xxx:");
        expect(cache.serverAssisted).toBe(true);

        const created = ClientSideCache.create(1024, 0, {
            serverAssisted: true,
            trackingPrefix: "xxx:",
        });
        expect(created.trackingPrefix).toBe("xxx:");
        expect(created.serverAssisted).toBe(true);
    });

    it("accepts a Buffer prefix with serverAssisted", () => {
        const prefix = Buffer.from("xxx:");
        const cache = new ClientSideCache({
            ...base,
            serverAssisted: true,
            trackingPrefix: prefix,
        });
        expect(cache.trackingPrefix).toEqual(prefix);

        const created = ClientSideCache.create(1024, 0, {
            serverAssisted: true,
            trackingPrefix: prefix,
        });
        expect(created.trackingPrefix).toEqual(prefix);
    });

    it("rejects an empty prefix", () => {
        const emptyPrefixes: GlideString[] = ["", Buffer.alloc(0)];

        for (const trackingPrefix of emptyPrefixes) {
            expect(
                () =>
                    new ClientSideCache({
                        ...base,
                        serverAssisted: true,
                        trackingPrefix,
                    }),
            ).toThrow(
                "trackingPrefix must not be empty; omit it to track all keys",
            );
            expect(() =>
                ClientSideCache.create(1024, 0, {
                    serverAssisted: true,
                    trackingPrefix,
                }),
            ).toThrow(
                "trackingPrefix must not be empty; omit it to track all keys",
            );
        }
    });

    it("rejects a prefix without serverAssisted", () => {
        expect(
            () => new ClientSideCache({ ...base, trackingPrefix: "xxx:" }),
        ).toThrow("trackingPrefix requires serverAssisted to be true");
        expect(
            () =>
                new ClientSideCache({
                    ...base,
                    serverAssisted: false,
                    trackingPrefix: Buffer.from("xxx:"),
                }),
        ).toThrow("trackingPrefix requires serverAssisted to be true");
        expect(() =>
            ClientSideCache.create(1024, 0, { trackingPrefix: "xxx:" }),
        ).toThrow("trackingPrefix requires serverAssisted to be true");
        expect(() =>
            ClientSideCache.create(1024, 0, {
                serverAssisted: false,
                trackingPrefix: "xxx:",
            }),
        ).toThrow("trackingPrefix requires serverAssisted to be true");
    });
});

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
        // Use TIMEOUT to allow for cluster spin-up; not a behavioral change.
    }, TIMEOUT);

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

    type CacheClient = GlideClient | GlideClusterClient;
    type CacheClientFactory = (
        cache: ClientSideCache | null,
    ) => Promise<CacheClient>;

    /**
     * Server-assisted cache with metrics that only tracks keys starting with `prefix`.
     */
    function prefixedCache(prefix: GlideString): ClientSideCache {
        return new ClientSideCache({
            maxCacheKb: 1024,
            entryTtlMs: 0,
            serverAssisted: true,
            enableMetrics: true,
            trackingPrefix: prefix,
        });
    }

    /**
     * Tracking info of the connection behind the client. With the default route a
     * cluster client answers for a single node.
     */
    async function trackingInfo(
        client: CacheClient,
    ): Promise<ClientTrackingInfo> {
        return (await client.clientTrackingInfo()) as ClientTrackingInfo;
    }

    /** Asserts that `info` reports BCAST tracking scoped to exactly `prefixes`. */
    function expectPrefixes(info: ClientTrackingInfo, prefixes: string[]) {
        expect(info.flags.has("on")).toBe(true);
        expect(info.flags.has("bcast")).toBe(true);
        expect([...info.prefixes].sort()).toEqual([...prefixes].sort());
    }

    /**
     * Waits until the replicas applied the writes issued so far by `writer`. Both
     * the primary and the replica broadcast an invalidation for a tracked key;
     * waiting for replication makes sure both reach the caching clients before the
     * test reads the key again, otherwise a read served in between is dropped by
     * the late invalidation.
     */
    async function awaitReplication(writer: CacheClient): Promise<void> {
        const args = ["WAIT", "1", "1000"];
        // Every primary has one replica; a reply below 1 means the write was not
        // replicated in time and the assertions that follow would be racy.
        const reply =
            writer instanceof GlideClusterClient
                ? await writer.customCommand(args, { route: "allPrimaries" })
                : await writer.customCommand(args);
        const acked =
            typeof reply === "number"
                ? [reply]
                : Object.values(reply as Record<string, number>);

        expect(acked.length).toBeGreaterThan(0);

        for (const replicas of acked) {
            expect(replicas).toBeGreaterThanOrEqual(1);
        }
    }

    /** Writes a key and waits until the replica applied the write. */
    async function writeAndReplicate(
        writer: CacheClient,
        key: GlideString,
        value: string,
    ): Promise<void> {
        expect(await writer.set(key, value)).toBe("OK");
        await awaitReplication(writer);
    }

    /**
     * Polls until the client observes `value` for `key` and the cache holds
     * `expectedEntries` entries again, i.e. the invalidation was processed and the
     * fresh value was stored.
     */
    async function waitForFreshValue(
        client: CacheClient,
        key: GlideString,
        value: string,
        expectedEntries: number,
    ): Promise<void> {
        await waitFor(
            async () =>
                (await client.get(key)) === value &&
                (await client.getCacheEntryCount()) === expectedEntries,
            `${key} was not invalidated and re-cached with value ${value}`,
        );
    }

    /**
     * Two clients with different prefixes and a writer without cache: each client
     * caches only its own namespace, a write in one namespace invalidates only the
     * client tracking it, and keys outside the prefix are always served by the server.
     */
    async function checkPrefixScopesInvalidations(
        createClient: CacheClientFactory,
    ): Promise<void> {
        const clientA = await createClient(prefixedCache("xxx:"));
        const clientB = await createClient(prefixedCache("yyy:"));
        const writer = await createClient(null);

        try {
            const suffix = `{prefix_scope_${Date.now()}}`;
            const keyA = `xxx:${suffix}`;
            const keyB = `yyy:${suffix}`;
            await writeAndReplicate(writer, keyA, "a1");
            await writeAndReplicate(writer, keyB, "b1");

            // Each client caches its own namespace: one miss, one hit.
            expect(await clientA.get(keyA)).toBe("a1");
            expect(await clientA.get(keyA)).toBe("a1");
            expect(await clientB.get(keyB)).toBe("b1");
            expect(await clientB.get(keyB)).toBe("b1");
            expect(await clientA.getCacheEntryCount()).toBe(1);
            expect(await clientB.getCacheEntryCount()).toBe(1);
            expect(await clientA.getCacheTotalLookups()).toBe(2);
            expect(await clientB.getCacheTotalLookups()).toBe(2);
            expect(await clientA.getCacheHitRate()).toBe(0.5);
            expect(await clientB.getCacheHitRate()).toBe(0.5);

            // Keys outside the prefix are served by the server, never cached and
            // not counted as lookups.
            expect(await clientA.get(keyB)).toBe("b1");
            expect(await clientA.get(keyB)).toBe("b1");
            expect(await clientB.get(keyA)).toBe("a1");
            expect(await clientB.get(keyA)).toBe("a1");
            expect(await clientA.getCacheEntryCount()).toBe(1);
            expect(await clientB.getCacheEntryCount()).toBe(1);
            expect(await clientA.getCacheTotalLookups()).toBe(2);
            expect(await clientB.getCacheTotalLookups()).toBe(2);

            // A write in namespace A invalidates client A only.
            await writeAndReplicate(writer, keyA, "a2");
            await waitForFreshValue(clientA, keyA, "a2", 1);
            expect(await clientB.getCacheEntryCount()).toBe(1);
            expect(await clientB.getCacheTotalLookups()).toBe(2);
            expect(await clientB.get(keyB)).toBe("b1"); // still a hit
            expect(await clientB.getCacheTotalLookups()).toBe(3);

            // A write in namespace B invalidates client B only.
            const lookupsA = await clientA.getCacheTotalLookups();
            await writeAndReplicate(writer, keyB, "b2");
            await waitForFreshValue(clientB, keyB, "b2", 1);
            expect(await clientA.getCacheEntryCount()).toBe(1);
            expect(await clientA.getCacheTotalLookups()).toBe(lookupsA);
            expect(await clientA.get(keyA)).toBe("a2"); // still a hit
            expect(await clientA.getCacheTotalLookups()).toBe(lookupsA + 1);

            // Client A reads the new yyy:* value straight from the server (never
            // stale) and still does not cache it.
            expect(await clientA.get(keyB)).toBe("b2");
            expect(await clientA.getCacheEntryCount()).toBe(1);
            expect(await clientA.getCacheTotalLookups()).toBe(lookupsA + 1);
        } finally {
            clientA.close();
            clientB.close();
            writer.close();
        }
    }

    /**
     * A prefixed client never caches keys outside its prefix, for GET and for MGET,
     * so it can never serve a stale value for a key the server will not invalidate.
     */
    async function checkPrefixBypassesCacheOutsidePrefix(
        createClient: CacheClientFactory,
    ): Promise<void> {
        const cached = await createClient(prefixedCache("xxx:"));
        const writer = await createClient(null);

        try {
            const suffix = `{prefix_bypass_${Date.now()}}`;
            const tracked = `xxx:${suffix}`;
            const untracked = `zzz:${suffix}`;
            await writeAndReplicate(writer, tracked, "t1");
            expect(await writer.set(untracked, "u1")).toBe("OK");

            // GET on an untracked key: served by the server, not cached, not counted.
            expect(await cached.get(untracked)).toBe("u1");
            expect(await cached.get(untracked)).toBe("u1");
            expect(await cached.getCacheEntryCount()).toBe(0);
            expect(await cached.getCacheTotalLookups()).toBe(0);

            // The writer changes it and the prefixed client sees the new value
            // immediately, without any invalidation involved.
            expect(await writer.set(untracked, "u2")).toBe("OK");
            expect(await cached.get(untracked)).toBe("u2");

            // MGET mixing tracked and untracked keys: complete ordered result,
            // only the tracked key is stored.
            expect(await cached.mget([tracked, untracked])).toEqual([
                "t1",
                "u2",
            ]);
            expect(await cached.getCacheEntryCount()).toBe(1);
            expect(await cached.getCacheTotalLookups()).toBe(1);

            // Second MGET: the tracked key hits, the untracked key is fetched again
            // and reflects a fresh write.
            expect(await writer.set(untracked, "u3")).toBe("OK");
            expect(await cached.mget([untracked, tracked])).toEqual([
                "u3",
                "t1",
            ]);
            expect(await cached.getCacheEntryCount()).toBe(1);
            expect(await cached.getCacheTotalLookups()).toBe(2);
            expect(await cached.getCacheHitRate()).toBe(0.5);

            // The tracked key is still invalidated by the server, and MGET
            // re-caches the fresh value.
            await writeAndReplicate(writer, tracked, "t2");
            await waitFor(
                async () =>
                    JSON.stringify(await cached.mget([tracked, untracked])) ===
                        JSON.stringify(["t2", "u3"]) &&
                    (await cached.getCacheEntryCount()) === 1,
                "the tracked MGET key was not invalidated and re-cached",
            );
        } finally {
            cached.close();
            writer.close();
        }
    }

    /**
     * FLUSHALL still clears the whole cache when a prefix is registered: the server
     * sends the `null` invalidation to every tracking client regardless of prefixes.
     */
    async function checkPrefixFlushClearsCache(
        createClient: CacheClientFactory,
    ): Promise<void> {
        const cached = await createClient(prefixedCache("xxx:"));

        try {
            const key = `xxx:{prefix_flush_${Date.now()}}`;
            await writeAndReplicate(cached, key, "v1");
            await waitForFreshValue(cached, key, "v1", 1);

            expect(await cached.flushall()).toBe("OK");
            await waitFor(
                async () => (await cached.getCacheEntryCount()) === 0,
                "FLUSHALL did not clear the prefixed cache",
            );
            expect(await cached.get(key)).toBeNull();
        } finally {
            cached.close();
        }
    }

    /**
     * HGETALL and SMEMBERS follow the prefix like GET: tracked hashes and sets are
     * cached, untracked ones are always served by the server and never counted.
     */
    async function checkPrefixCachesHashAndSetInsidePrefixOnly(
        createClient: CacheClientFactory,
    ): Promise<void> {
        const cached = await createClient(prefixedCache("xxx:"));
        const writer = await createClient(null);

        try {
            const suffix = `{prefix_types_${Date.now()}}`;
            const trackedHash = `xxx:${suffix}:h`;
            const untrackedHash = `zzz:${suffix}:h`;
            const trackedSet = `xxx:${suffix}:s`;
            const untrackedSet = `zzz:${suffix}:s`;
            expect(await writer.hset(trackedHash, { f: "v1" })).toBe(1);
            expect(await writer.hset(untrackedHash, { f: "v1" })).toBe(1);
            expect(await writer.sadd(trackedSet, ["m"])).toBe(1);
            expect(await writer.sadd(untrackedSet, ["m"])).toBe(1);
            await awaitReplication(writer);

            const hash = [{ field: "f", value: "v1" }];
            const set = new Set(["m"]);

            // Tracked hash: miss then hit.
            expect(await cached.hgetall(trackedHash)).toEqual(hash);
            expect(await cached.hgetall(trackedHash)).toEqual(hash);
            expect(await cached.getCacheEntryCount()).toBe(1);
            expect(await cached.getCacheTotalLookups()).toBe(2);

            // Untracked hash: served by the server, not cached, not counted.
            expect(await cached.hgetall(untrackedHash)).toEqual(hash);
            expect(await cached.hgetall(untrackedHash)).toEqual(hash);
            expect(await cached.getCacheEntryCount()).toBe(1);
            expect(await cached.getCacheTotalLookups()).toBe(2);

            // Tracked set: miss then hit.
            expect(await cached.smembers(trackedSet)).toEqual(set);
            expect(await cached.smembers(trackedSet)).toEqual(set);
            expect(await cached.getCacheEntryCount()).toBe(2);
            expect(await cached.getCacheTotalLookups()).toBe(4);
            expect(await cached.getCacheHitRate()).toBe(0.5);

            // Untracked set: served by the server, not cached, not counted.
            expect(await cached.smembers(untrackedSet)).toEqual(set);
            expect(await cached.smembers(untrackedSet)).toEqual(set);
            expect(await cached.getCacheEntryCount()).toBe(2);
            expect(await cached.getCacheTotalLookups()).toBe(4);

            // Writes to untracked keys are visible immediately (nothing cached).
            expect(await writer.hset(untrackedHash, { f: "v2" })).toBe(0);
            expect(await writer.sadd(untrackedSet, ["n"])).toBe(1);
            expect(await cached.hgetall(untrackedHash)).toEqual([
                { field: "f", value: "v2" },
            ]);
            expect(await cached.smembers(untrackedSet)).toEqual(
                new Set(["m", "n"]),
            );
            expect(await cached.getCacheEntryCount()).toBe(2);
            expect(await cached.getCacheTotalLookups()).toBe(4);

            // Tracked keys are invalidated by the server and re-cached.
            expect(await writer.hset(trackedHash, { f: "v2" })).toBe(0);
            expect(await writer.sadd(trackedSet, ["n"])).toBe(1);
            await awaitReplication(writer);
            await waitFor(
                async () =>
                    JSON.stringify(await cached.hgetall(trackedHash)) ===
                        JSON.stringify([{ field: "f", value: "v2" }]) &&
                    (await cached.smembers(trackedSet)).size === 2 &&
                    (await cached.getCacheEntryCount()) === 2,
                "tracked hash and set were not invalidated and re-cached",
            );
        } finally {
            cached.close();
            writer.close();
        }
    }

    /** Raw prefixes reported by CLIENT TRACKINGINFO, decoded as bytes. */
    async function rawTrackingPrefixes(client: CacheClient): Promise<Buffer[]> {
        const info = (await client.customCommand(["CLIENT", "TRACKINGINFO"], {
            decoder: Decoder.Bytes,
        })) as unknown as GlideRecord<unknown>;
        const prefixes = info.find((entry) =>
            (entry.key as Buffer).equals(Buffer.from("prefixes")),
        );
        return prefixes?.value as Buffer[];
    }

    /**
     * A binary (non UTF-8) prefix is registered byte for byte, and binary keys under
     * it are cached and invalidated like any other tracked key.
     */
    async function checkBinaryPrefix(
        createClient: CacheClientFactory,
    ): Promise<void> {
        const prefix = Buffer.from([0xff, 0x00, 0x3a]);
        const cached = await createClient(prefixedCache(prefix));
        const writer = await createClient(null);

        try {
            expect(await rawTrackingPrefixes(cached)).toEqual([prefix]);

            const suffix = `{prefix_binary_${Date.now()}}`;
            const tracked = Buffer.concat([prefix, Buffer.from(suffix)]);
            const untracked = `zzz:${suffix}`;
            await writeAndReplicate(writer, tracked, "v1");
            expect(await writer.set(untracked, "u1")).toBe("OK");

            expect(await cached.get(tracked)).toBe("v1");
            expect(await cached.get(tracked)).toBe("v1");
            expect(await cached.getCacheEntryCount()).toBe(1);
            expect(await cached.getCacheTotalLookups()).toBe(2);

            expect(await cached.get(untracked)).toBe("u1");
            expect(await cached.getCacheEntryCount()).toBe(1);
            expect(await cached.getCacheTotalLookups()).toBe(2);

            await writeAndReplicate(writer, tracked, "v2");
            await waitForFreshValue(cached, tracked, "v2", 1);
        } finally {
            cached.close();
            writer.close();
        }
    }

    /**
     * Two clients created from the same prefixed ClientSideCache share the cache
     * and both register the prefix with the server.
     */
    async function checkSharedPrefixedCache(
        createClient: CacheClientFactory,
    ): Promise<void> {
        const cache = prefixedCache("xxx:");
        const client1 = await createClient(cache);
        const client2 = await createClient(cache);

        try {
            expectPrefixes(await trackingInfo(client1), ["xxx:"]);
            expectPrefixes(await trackingInfo(client2), ["xxx:"]);

            const key = `xxx:{prefix_shared_${Date.now()}}`;
            await writeAndReplicate(client1, key, "value");
            expect(await client1.get(key)).toBe("value"); // miss, populates cache

            expect(await client2.getCacheEntryCount()).toBe(1);
            expect(await client2.get(key)).toBe("value"); // hit from the shared cache

            expect(await client1.getCacheHitRate()).toBe(0.5);
            expect(await client2.getCacheHitRate()).toBe(0.5);
            expect(await client1.getCacheTotalLookups()).toBe(2);
            expect(await client2.getCacheTotalLookups()).toBe(2);

            // A key outside the prefix is not cached by either client.
            const outside = `yyy:{prefix_shared_${Date.now()}}`;
            expect(await client1.set(outside, "value")).toBe("OK");
            expect(await client1.get(outside)).toBe("value");
            expect(await client2.get(outside)).toBe("value");
            expect(await client1.getCacheEntryCount()).toBe(1);
            expect(await client2.getCacheTotalLookups()).toBe(2);
        } finally {
            client1.close();
            client2.close();
        }
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

        it(
            "server-assisted invalidation evicts cached key",
            async () => {
                const cache = new ClientSideCache({
                    maxCacheKb: 1,
                    entryTtlMs: 60000,
                    serverAssisted: true,
                });
                client = await createStandaloneClientWithCache(
                    ProtocolVersion.RESP3,
                    cache,
                );
                const clientB = await GlideClient.createClient(
                    getClientConfigurationOption(
                        standaloneCluster.getAddresses(),
                        ProtocolVersion.RESP3,
                    ),
                );

                try {
                    const key = `invalidation_test_${Date.now()}`;

                    // Client A caches the key
                    expect(await client.set(key, "v1")).toBe("OK");
                    expect(await client.get(key)).toBe("v1"); // miss, populates cache
                    expect(await client.get(key)).toBe("v1"); // hit

                    // Client B modifies the key — triggers server invalidation
                    expect(await clientB.set(key, "v2")).toBe("OK");

                    // Poll until invalidation is processed
                    await waitFor(
                        async () => (await client.get(key)) === "v2",
                        "Cache was not invalidated after key was modified by another client",
                    );
                } finally {
                    clientB.close();
                }
            },
            TIMEOUT,
        );

        describe("tracking prefix", () => {
            const createClient: CacheClientFactory = (cache) =>
                createStandaloneClientWithCache(ProtocolVersion.RESP3, cache);

            it.each([
                ["string", "xxx:" as GlideString],
                ["Buffer", Buffer.from("xxx:") as GlideString],
            ])(
                "is registered with the server (%s prefix)",
                async (_, prefix) => {
                    client = (await createClient(
                        prefixedCache(prefix),
                    )) as GlideClient;
                    expectPrefixes(await trackingInfo(client), ["xxx:"]);
                },
                TIMEOUT,
            );

            it(
                "without a prefix every key is tracked",
                async () => {
                    client = (await createClient(
                        new ClientSideCache({
                            maxCacheKb: 1024,
                            entryTtlMs: 0,
                            serverAssisted: true,
                        }),
                    )) as GlideClient;
                    expectPrefixes(await trackingInfo(client), [""]);
                },
                TIMEOUT,
            );

            it(
                "scopes invalidations between clients",
                async () => {
                    await checkPrefixScopesInvalidations(createClient);
                },
                TIMEOUT,
            );

            it(
                "bypasses the cache for keys outside the prefix",
                async () => {
                    await checkPrefixBypassesCacheOutsidePrefix(createClient);
                },
                TIMEOUT,
            );

            it(
                "is shared by clients using the same cache",
                async () => {
                    await checkSharedPrefixedCache(createClient);
                },
                TIMEOUT,
            );

            it(
                "is cleared by FLUSHALL",
                async () => {
                    await checkPrefixFlushClearsCache(createClient);
                },
                TIMEOUT,
            );

            it(
                "caches hashes and sets only inside the prefix",
                async () => {
                    await checkPrefixCachesHashAndSetInsidePrefixOnly(
                        createClient,
                    );
                },
                TIMEOUT,
            );

            it(
                "supports a binary prefix",
                async () => {
                    await checkBinaryPrefix(createClient);
                },
                TIMEOUT,
            );
        });
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

        it(
            "server-assisted invalidation evicts cached key",
            async () => {
                const cache = new ClientSideCache({
                    maxCacheKb: 1,
                    entryTtlMs: 60000,
                    serverAssisted: true,
                });
                client = await createClusterClientWithCache(
                    ProtocolVersion.RESP3,
                    cache,
                );
                const clientB = await GlideClusterClient.createClient(
                    getClientConfigurationOption(
                        clusterCluster.getAddresses(),
                        ProtocolVersion.RESP3,
                    ),
                );

                try {
                    const key = `{invalidation_test}_${Date.now()}`;

                    // Client A caches the key
                    expect(await client.set(key, "v1")).toBe("OK");
                    expect(await client.get(key)).toBe("v1"); // miss, populates cache
                    expect(await client.get(key)).toBe("v1"); // hit

                    // Client B modifies the key — triggers server invalidation
                    expect(await clientB.set(key, "v2")).toBe("OK");

                    // Poll until invalidation is processed
                    await waitFor(
                        async () => (await client.get(key)) === "v2",
                        "Cache was not invalidated after key was modified by another client",
                    );
                } finally {
                    clientB.close();
                }
            },
            TIMEOUT,
        );

        describe("tracking prefix", () => {
            const createClient: CacheClientFactory = (cache) =>
                createClusterClientWithCache(ProtocolVersion.RESP3, cache);

            it.each([
                ["string", "xxx:" as GlideString],
                ["Buffer", Buffer.from("xxx:") as GlideString],
            ])(
                "is registered with every node (%s prefix)",
                async (_, prefix) => {
                    client = (await createClient(
                        prefixedCache(prefix),
                    )) as GlideClusterClient;
                    expectPrefixes(await trackingInfo(client), ["xxx:"]);

                    const perNode = (await client.clientTrackingInfo({
                        route: "allNodes",
                    })) as Record<string, ClientTrackingInfo>;
                    const nodes = Object.keys(perNode);
                    expect(nodes.length).toBeGreaterThan(1);

                    for (const node of nodes) {
                        expectPrefixes(perNode[node], ["xxx:"]);
                    }
                },
                TIMEOUT,
            );

            it(
                "without a prefix every key is tracked",
                async () => {
                    client = (await createClient(
                        new ClientSideCache({
                            maxCacheKb: 1024,
                            entryTtlMs: 0,
                            serverAssisted: true,
                        }),
                    )) as GlideClusterClient;
                    expectPrefixes(await trackingInfo(client), [""]);
                },
                TIMEOUT,
            );

            it(
                "scopes invalidations between clients",
                async () => {
                    await checkPrefixScopesInvalidations(createClient);
                },
                TIMEOUT,
            );

            it(
                "bypasses the cache for keys outside the prefix",
                async () => {
                    await checkPrefixBypassesCacheOutsidePrefix(createClient);
                },
                TIMEOUT,
            );

            it(
                "is shared by clients using the same cache",
                async () => {
                    await checkSharedPrefixedCache(createClient);
                },
                TIMEOUT,
            );

            it(
                "is cleared by FLUSHALL",
                async () => {
                    await checkPrefixFlushClearsCache(createClient);
                },
                TIMEOUT,
            );

            it(
                "caches hashes and sets only inside the prefix",
                async () => {
                    await checkPrefixCachesHashAndSetInsidePrefixOnly(
                        createClient,
                    );
                },
                TIMEOUT,
            );

            it(
                "supports a binary prefix",
                async () => {
                    await checkBinaryPrefix(createClient);
                },
                TIMEOUT,
            );
        });
    });
});
