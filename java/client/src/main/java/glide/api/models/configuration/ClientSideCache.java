/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.models.GlideString;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for client-side caching. Client-side caching reduces network round-trips and server
 * load by storing frequently accessed data locally on the client.
 *
 * <p>Use the {@link #create} factory method or the builder to create instances. The cache ID is
 * auto-generated internally.
 *
 * <p>In order for 2 clients to share the same cache, they must be created with the same {@code
 * ClientSideCache} instance. Clients with different {@code ClientSideCache} instances will have
 * separate caches, even if the configurations are identical.
 *
 * <p>With {@link #isServerAssisted() serverAssisted} enabled, an optional {@link
 * #getTrackingPrefix() trackingPrefix} restricts both the server-side tracking and the local cache
 * to the keys that start with that prefix. Keys outside the prefix are always fetched from the
 * server and are never cached, so they can never be served stale. Without a prefix every key is
 * tracked and cacheable.
 *
 * @example
 *     <pre>{@code
 * // Create cache with default settings
 * ClientSideCache cache = ClientSideCache.create(1024, 60000);
 *
 * // Create cache with custom configuration
 * ClientSideCache cache = ClientSideCache.builder()
 *     .maxCacheKb(2048)
 *     .entryTtlMs(300)
 *     .evictionPolicy(EvictionPolicy.LRU)
 *     .enableMetrics(true)
 *     .build();
 *
 * // Server-assisted cache that only tracks and caches keys under "user:"
 * ClientSideCache cache = ClientSideCache.builder()
 *     .maxCacheKb(2048)
 *     .entryTtlMs(0)
 *     .serverAssisted(true)
 *     .trackingPrefix("user:")
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class ClientSideCache {

    /** Thread-safe counter for generating unique cache IDs, matching Python's counter approach. */
    private static final AtomicLong ID_COUNTER = new AtomicLong(0);

    /**
     * Unique identifier for the cache instance. Auto-generated internally. Used to determine cache
     * sharing between clients.
     */
    @Builder.Default private final String cacheId = generateCacheId();

    /** Maximum memory limit for the cache in kilobytes. */
    private final long maxCacheKb;

    /**
     * TTL (Time-To-Live) for cache entries in milliseconds. A value of 0 means no expiration. This
     * field is required.
     */
    private final long entryTtlMs;

    /** Eviction policy to use when cache reaches memory limit. Defaults to LRU if not specified. */
    @Builder.Default private final EvictionPolicy evictionPolicy = EvictionPolicy.LRU;

    /** Whether to enable metrics collection for the cache. Defaults to false. */
    @Builder.Default private final boolean enableMetrics = false;

    /**
     * Whether to enable server-assisted client-side caching.
     *
     * <p>When enabled, GLIDE sends CLIENT TRACKING ON BCAST during connection setup and the server
     * sends invalidation messages when tracked keys are modified. Requires RESP3 protocol. Defaults
     * to false.
     */
    @Builder.Default private final boolean serverAssisted = false;

    /**
     * Optional key prefix that scopes server-assisted caching to a key namespace. {@code null} (the
     * default) means every key is tracked and cacheable.
     *
     * <p>When set, GLIDE sends {@code CLIENT TRACKING ON BCAST PREFIX <trackingPrefix>} on every
     * connection (and on every reconnection, for every cluster node), so the server only broadcasts
     * invalidations for keys that start with the prefix. Without a prefix the server broadcasts an
     * invalidation for <em>every</em> key written by any client, which does not scale when the cached
     * working set is a small part of the keyspace.
     *
     * <p>The local cache only stores keys that start with the prefix. A key outside the prefix is
     * always fetched from the server, is never cached and does not count as a cache lookup, hit or
     * miss, so it can never be served stale. A multi-key read such as {@code MGET} that mixes tracked
     * and untracked keys still returns the full ordered result; only the tracked keys are stored.
     *
     * <p>Constraints:
     *
     * <ul>
     *   <li>Requires {@link #isServerAssisted() serverAssisted} to be {@code true} (and therefore
     *       RESP3).
     *   <li>Must not be empty. Omit it to track all keys.
     *   <li>Fixed for the lifetime of the client; it cannot be changed after connecting.
     *   <li>All clients sharing the same {@code ClientSideCache} instance use the same prefix.
     * </ul>
     */
    private final GlideString trackingPrefix;

    /**
     * Creates a ClientSideCache with auto-generated cache ID and default settings (no server-assisted
     * invalidation and no tracking prefix).
     *
     * @param maxCacheKb Maximum memory limit for the cache in kilobytes.
     * @param entryTtlMs TTL for cache entries in milliseconds. Use 0 for no expiration.
     * @return A new ClientSideCache instance with auto-generated ID.
     */
    public static ClientSideCache create(long maxCacheKb, long entryTtlMs) {
        return ClientSideCache.builder().maxCacheKb(maxCacheKb).entryTtlMs(entryTtlMs).build();
    }

    /**
     * Generates a unique cache identifier using an incrementing counter, consistent with the Python
     * client implementation.
     *
     * @return A unique cache ID string.
     */
    private static String generateCacheId() {
        return String.valueOf(ID_COUNTER.getAndIncrement());
    }

    /**
     * Customized builder that hides the {@code cacheId} setter and adds validation. The cache ID is
     * always auto-generated and should not be set by users.
     */
    public static class ClientSideCacheBuilder {
        // Hide the cacheId setter from the public API. Lombok will still use the
        // @Builder.Default initializer internally, so the auto-generated ID is applied.
        private ClientSideCacheBuilder cacheId(String cacheId) {
            return this;
        }

        /**
         * Sets the key prefix that scopes server-assisted caching. See {@link
         * ClientSideCache#getTrackingPrefix()}.
         *
         * @param trackingPrefix The key prefix, encoded as UTF-8. Omit the call to track all keys.
         * @return This builder.
         */
        public ClientSideCacheBuilder trackingPrefix(String trackingPrefix) {
            this.trackingPrefix = trackingPrefix == null ? null : GlideString.gs(trackingPrefix);
            return this;
        }

        /**
         * Sets the key prefix that scopes server-assisted caching, as raw bytes. See {@link
         * ClientSideCache#getTrackingPrefix()}.
         *
         * @param trackingPrefix The key prefix as raw bytes. Omit the call to track all keys.
         * @return This builder.
         */
        public ClientSideCacheBuilder trackingPrefix(GlideString trackingPrefix) {
            this.trackingPrefix = trackingPrefix;
            return this;
        }
    }

    // Private constructor with validation, called by Lombok's generated build() method.
    private ClientSideCache(
            String cacheId,
            long maxCacheKb,
            long entryTtlMs,
            EvictionPolicy evictionPolicy,
            boolean enableMetrics,
            boolean serverAssisted,
            GlideString trackingPrefix) {
        if (maxCacheKb <= 0) {
            throw new IllegalArgumentException("maxCacheKb must be positive");
        }
        if (entryTtlMs < 0) {
            throw new IllegalArgumentException("entryTtlMs must be non-negative (0 = no expiration)");
        }
        if (trackingPrefix != null) {
            if (trackingPrefix.getBytes().length == 0) {
                throw new IllegalArgumentException(
                        "trackingPrefix must not be empty; omit it to track all keys");
            }
            if (!serverAssisted) {
                throw new IllegalArgumentException(
                        "trackingPrefix requires serverAssisted to be enabled (and RESP3)");
            }
        }
        this.cacheId = cacheId;
        this.maxCacheKb = maxCacheKb;
        this.entryTtlMs = entryTtlMs;
        this.evictionPolicy = evictionPolicy;
        this.enableMetrics = enableMetrics;
        this.serverAssisted = serverAssisted;
        this.trackingPrefix = trackingPrefix;
    }
}
