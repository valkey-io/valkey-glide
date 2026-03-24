/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { EvictionPolicy } from "./EvictionPolicy.js";

/**
 * Generates a unique cache ID using timestamp and random numbers.
 * @returns A unique string identifier
 */
function generateCacheId(): string {
    const timestamp = Date.now().toString(36);
    const randomPart = Math.random().toString(36).substring(2, 15);
    return `cache_${timestamp}_${randomPart}`;
}

/**
 * Configuration options for creating a ClientSideCache.
 */
export interface ClientSideCacheConfig {
    /**
     * Unique identifier for the cache instance.
     * If not provided, a unique ID will be auto-generated.
     */
    cacheId?: string;

    /**
     * Maximum memory limit for the cache in kilobytes.
     * Must be a positive number.
     */
    maxCacheKb: number;

    /**
     * Optional Time-To-Live for cache entries in seconds.
     * If not specified, entries will not expire based on time.
     */
    entryTtlSeconds?: number;

    /**
     * Optional eviction policy to use when cache reaches memory limit.
     * Defaults to LRU if not specified.
     */
    evictionPolicy?: EvictionPolicy;

    /**
     * Whether to enable metrics collection for this cache.
     * Defaults to true if not specified.
     */
    enableMetrics?: boolean;
}

/**
 * Optional configuration options for the static create method.
 */
export interface ClientSideCacheOptions {
    /**
     * Optional Time-To-Live for cache entries in seconds.
     */
    entryTtlSeconds?: number;

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
 * const cache = ClientSideCache.create(1024); // 1MB cache
 *
 * // Create cache with custom configuration
 * const customCache = new ClientSideCache({
 *   cacheId: "my-cache",
 *   maxCacheKb: 2048,
 *   entryTtlSeconds: 300,
 *   evictionPolicy: EvictionPolicy.LFU,
 *   enableMetrics: true
 * });
 * ```
 */
export class ClientSideCache {
    /**
     * Unique identifier for the cache instance.
     */
    readonly cacheId: string;

    /**
     * Maximum memory limit for the cache in kilobytes.
     */
    readonly maxCacheKb: number;

    /**
     * Optional Time-To-Live for cache entries in seconds.
     */
    readonly entryTtlSeconds?: number;

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
     * @throws {Error} If entryTtlSeconds is provided but not a positive number
     */
    constructor(config: ClientSideCacheConfig) {
        if (config.maxCacheKb <= 0) {
            throw new Error("maxCacheKb must be a positive number");
        }

        if (
            config.entryTtlSeconds !== undefined &&
            config.entryTtlSeconds <= 0
        ) {
            throw new Error(
                "entryTtlSeconds must be a positive number when provided",
            );
        }

        this.cacheId = config.cacheId ?? generateCacheId();
        this.maxCacheKb = config.maxCacheKb;
        this.entryTtlSeconds = config.entryTtlSeconds;
        this.evictionPolicy = config.evictionPolicy;
        this.enableMetrics = config.enableMetrics ?? true;
    }

    /**
     * Factory method to create a ClientSideCache with auto-generated cache ID.
     *
     * @param maxCacheKb - Maximum memory limit for the cache in kilobytes
     * @param options - Optional configuration options
     * @returns A new ClientSideCache instance with auto-generated cache ID
     * @throws {Error} If maxCacheKb is not a positive number
     * @throws {Error} If entryTtlSeconds is provided but not a positive number
     *
     * @example
     * ```typescript
     * // Simple cache with 1MB limit
     * const cache = ClientSideCache.create(1024);
     *
     * // Cache with TTL and LFU eviction
     * const cacheWithOptions = ClientSideCache.create(2048, {
     *   entryTtlSeconds: 300,
     *   evictionPolicy: EvictionPolicy.LFU,
     *   enableMetrics: false
     * });
     * ```
     */
    static create(
        maxCacheKb: number,
        options?: Partial<ClientSideCacheOptions>,
    ): ClientSideCache {
        return new ClientSideCache({
            maxCacheKb,
            entryTtlSeconds: options?.entryTtlSeconds,
            evictionPolicy: options?.evictionPolicy,
            enableMetrics: options?.enableMetrics,
        });
    }
}
