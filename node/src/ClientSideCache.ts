/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { randomUUID } from "crypto";

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
     * Creates a new ClientSideCache instance.
     *
     * @param config - Configuration options for the cache
     * @throws {Error} If maxCacheKb is not a positive number
     * @throws {Error} If entryTtlMs is negative
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

        this.cacheId = randomUUID();
        this.maxCacheKb = config.maxCacheKb;
        this.entryTtlMs = config.entryTtlMs;
        this.evictionPolicy = config.evictionPolicy;
        this.enableMetrics = config.enableMetrics ?? false;
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
        });
    }
}
