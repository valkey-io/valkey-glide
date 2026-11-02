/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { randomUUID } from "crypto";

import type { GlideString } from "./BaseClient.js";
import { EvictionPolicy } from "./EvictionPolicy.js";

/**
 * Configuration options for creating a ClientSideCache.
 */
export interface ClientSideCacheConfig {
    /**
     * Maximum memory limit for the cache in kilobytes.
     * Must be greater than zero.
     */
    maxCacheKb: number;

    /**
     * Time-To-Live for cache entries in milliseconds.
     * Set to 0 to disable TTL expiration (entries remain until evicted or invalidated).
     */
    entryTtlMs: number;

    /**
     * Optional eviction policy to use when cache reaches memory limit.
     * Defaults to LRU if not specified.
     */
    evictionPolicy?: EvictionPolicy;

    /**
     * Whether to enable metrics collection for this cache.
     * Defaults to false if not specified.
     */
    enableMetrics?: boolean;

    /**
     * Whether to enable server-assisted client-side caching.
     *
     * When enabled, GLIDE sends CLIENT TRACKING ON BCAST during connection setup
     * and the server sends invalidation messages when tracked keys are modified.
     * Requires RESP3 protocol. Defaults to false if not specified.
     *
     * The tracking state is owned by the client: do not issue RESET or
     * CLIENT TRACKING manually on a client with server-assisted caching, because
     * that stops the invalidations while the cache keeps serving its entries.
     */
    serverAssisted?: boolean;

    /**
     * Optional key prefix that limits server-assisted tracking to a namespace.
     *
     * When set, GLIDE sends `CLIENT TRACKING ON BCAST PREFIX <prefix>` on every
     * connection (and every cluster node), so the server only broadcasts
     * invalidations for keys starting with this prefix, and the client only
     * caches those keys. Without a prefix the server broadcasts an invalidation
     * for every key written by any client, which does not scale when the cached
     * working set is a small part of the keyspace. Keys outside the prefix are
     * always fetched from the server, are never cached and therefore can never
     * be stale; an `MGET` mixing tracked and untracked keys still returns the
     * full ordered result and only the tracked keys are stored. Matching is a
     * byte-wise, case-sensitive starts-with check; a key equal to the prefix is
     * tracked.
     *
     * The prefix is fixed for the lifetime of the client and cannot be changed
     * after connecting. All clients that share the same `ClientSideCache`
     * instance use the same prefix.
     * Requires `serverAssisted: true` (and therefore RESP3) and must not be empty.
     * Defaults to undefined, meaning every key is tracked and cached.
     */
    trackingPrefix?: GlideString;
}

/**
 * Optional configuration options for the static create method.
 */
export interface ClientSideCacheOptions {
    /**
     * Optional eviction policy to use when cache reaches memory limit.
     */
    evictionPolicy?: EvictionPolicy;

    /**
     * Whether to enable metrics collection for this cache.
     */
    enableMetrics?: boolean;

    /**
     * Whether to enable server-assisted client-side caching.
     */
    serverAssisted?: boolean;

    /**
     * Optional key prefix that limits server-assisted tracking and caching to
     * keys starting with it. Requires `serverAssisted: true` and must not be empty.
     * See {@link ClientSideCacheConfig.trackingPrefix}.
     */
    trackingPrefix?: GlideString;
}

/**
 * Configuration class for client-side caching.
 *
 * Client-side caching reduces network round-trips and server load by storing
 * frequently accessed data locally on the client. This class provides
 * configurable TTL-based expiration, multiple eviction policies, and
 * comprehensive metrics tracking.
 *
 * @example
 * ```typescript
 * // Create cache with auto-generated ID
 * const cache = ClientSideCache.create(1024, 60000); // 1MB cache, 1 min TTL
 *
 * // Create cache with custom configuration
 * const customCache = new ClientSideCache({
 *   maxCacheKb: 2048,
 *   entryTtlMs: 300000,
 *   evictionPolicy: EvictionPolicy.LFU,
 *   enableMetrics: true
 * });
 * ```
 *
 * @example
 * ```typescript
 * // Server-assisted caching scoped to a key namespace with a tracking prefix.
 * // Each client only receives invalidations for, and only caches, keys in its
 * // own namespace. Keys outside the prefix are always read from the server.
 * const usersCache = ClientSideCache.create(1024, 0, {
 *   serverAssisted: true,
 *   trackingPrefix: "users:",
 * });
 * const ordersCache = ClientSideCache.create(1024, 0, {
 *   serverAssisted: true,
 *   trackingPrefix: "orders:",
 * });
 * const usersClient = await GlideClient.createClient({
 *   addresses,
 *   protocol: ProtocolVersion.RESP3,
 *   clientSideCache: usersCache,
 * });
 * const ordersClient = await GlideClient.createClient({
 *   addresses,
 *   protocol: ProtocolVersion.RESP3,
 *   clientSideCache: ordersCache,
 * });
 * await usersClient.get("users:42");   // cached, invalidated on writes to users:*
 * await usersClient.get("orders:7");   // served by the server, never cached
 * ```
 */
export class ClientSideCache {
    /**
     * @internal
     * Unique identifier for the cache instance. Auto-generated using UUID.
     */
    readonly cacheId: string;

    /**
     * Maximum memory limit for the cache in kilobytes.
     */
    readonly maxCacheKb: number;

    /**
     * Time-To-Live for cache entries in milliseconds. 0 means no expiration.
     */
    readonly entryTtlMs: number;

    /**
     * Optional eviction policy to use when cache reaches memory limit.
     */
    readonly evictionPolicy?: EvictionPolicy;

    /**
     * Whether metrics collection is enabled for this cache.
     */
    readonly enableMetrics: boolean;

    /**
     * Whether server-assisted client-side caching is enabled.
     */
    readonly serverAssisted: boolean;

    /**
     * Key prefix registered with `CLIENT TRACKING ON BCAST PREFIX <prefix>`.
     * Only keys starting with it are tracked by the server and cached locally;
     * keys outside the prefix are always fetched from the server and never cached.
     * Undefined means every key is tracked and cached.
     * See {@link ClientSideCacheConfig.trackingPrefix}.
     */
    readonly trackingPrefix?: GlideString;

    /**
     * Creates a new ClientSideCache instance.
     *
     * @param config - Configuration options for the cache
     * @throws {Error} If maxCacheKb is not a positive number
     * @throws {Error} If entryTtlMs is negative
     * @throws {Error} If trackingPrefix is empty
     * @throws {Error} If trackingPrefix is set without serverAssisted
     */
    constructor(config: ClientSideCacheConfig) {
        if (config.maxCacheKb <= 0) {
            throw new Error("maxCacheKb must be a positive number");
        }

        if (config.entryTtlMs < 0) {
            throw new Error(
                "entryTtlMs must be non-negative (0 = no expiration)",
            );
        }

        const serverAssisted = config.serverAssisted ?? false;

        if (config.trackingPrefix !== undefined) {
            if (config.trackingPrefix.length === 0) {
                throw new Error(
                    "trackingPrefix must not be empty; omit it to track all keys",
                );
            }

            if (!serverAssisted) {
                throw new Error(
                    "trackingPrefix requires serverAssisted to be true",
                );
            }
        }

        this.cacheId = randomUUID();
        this.maxCacheKb = config.maxCacheKb;
        this.entryTtlMs = config.entryTtlMs;
        this.evictionPolicy = config.evictionPolicy;
        this.enableMetrics = config.enableMetrics ?? false;
        this.serverAssisted = serverAssisted;
        this.trackingPrefix = config.trackingPrefix;
    }

    /**
     * Factory method to create a ClientSideCache with auto-generated cache ID.
     *
     * @param maxCacheKb - Maximum memory limit for the cache in kilobytes
     * @param entryTtlMs - TTL for cache entries in milliseconds. Use 0 for no expiration.
     * @param options - Optional configuration options
     * @returns A new ClientSideCache instance with auto-generated cache ID
     * @throws {Error} If maxCacheKb is not a positive number
     * @throws {Error} If entryTtlMs is negative
     * @throws {Error} If trackingPrefix is empty or set without serverAssisted
     *
     * @example
     * ```typescript
     * // Simple cache with 1MB limit and no TTL
     * const cache = ClientSideCache.create(1024, 0);
     *
     * // Cache with TTL and LFU eviction
     * const cacheWithOptions = ClientSideCache.create(2048, 300000, {
     *   evictionPolicy: EvictionPolicy.LFU,
     *   enableMetrics: false
     * });
     *
     * // Server-assisted cache that only tracks and caches keys under "users:"
     * const scopedCache = ClientSideCache.create(1024, 0, {
     *   serverAssisted: true,
     *   trackingPrefix: "users:"
     * });
     * ```
     */
    static create(
        maxCacheKb: number,
        entryTtlMs: number,
        options?: ClientSideCacheOptions,
    ): ClientSideCache {
        return new ClientSideCache({
            maxCacheKb,
            entryTtlMs,
            evictionPolicy: options?.evictionPolicy,
            enableMetrics: options?.enableMetrics,
            serverAssisted: options?.serverAssisted,
            trackingPrefix: options?.trackingPrefix,
        });
    }
}
