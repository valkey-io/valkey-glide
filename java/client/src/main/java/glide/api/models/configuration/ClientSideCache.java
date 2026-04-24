/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

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
     * Creates a ClientSideCache with auto-generated cache ID and default settings.
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
    }

    // Private constructor with validation, called by Lombok's generated build() method.
    private ClientSideCache(
            String cacheId,
            long maxCacheKb,
            long entryTtlMs,
            EvictionPolicy evictionPolicy,
            boolean enableMetrics) {
        if (maxCacheKb <= 0) {
            throw new IllegalArgumentException("maxCacheKb must be positive");
        }
        if (entryTtlMs < 0) {
            throw new IllegalArgumentException("entryTtlMs must be non-negative (0 = no expiration)");
        }
        this.cacheId = cacheId;
        this.maxCacheKb = maxCacheKb;
        this.entryTtlMs = entryTtlMs;
        this.evictionPolicy = evictionPolicy;
        this.enableMetrics = enableMetrics;
    }
}
