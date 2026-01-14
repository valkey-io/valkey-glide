/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

/**
 * Configuration for client-side caching. Client-side caching reduces network round-trips and server
 * load by storing frequently accessed data locally on the client.
 *
 * @example
 *     <pre>{@code
 * // Create cache with auto-generated ID
 * ClientSideCache cache = ClientSideCache.create(1024);
 *
 * // Create cache with custom configuration
 * ClientSideCache cache = ClientSideCache.builder()
 *     .maxCacheKb(2048)
 *     .entryTtlSeconds(300)
 *     .evictionPolicy(EvictionPolicy.LRU)
 *     .enableMetrics(true)
 *     .build();
 * }</pre>
 */
@Getter
@Builder
public class ClientSideCache {

    /** Unique identifier for the cache instance. */
    @NonNull @Builder.Default private final String cacheId = generateCacheId();

    /** Maximum memory limit for the cache in kilobytes. */
    @NonNull private final Long maxCacheKb;

    /** Optional TTL (Time-To-Live) for cache entries in seconds. */
    private final Long entryTtlSeconds;

    /** Eviction policy to use when cache reaches memory limit. Defaults to LRU if not specified. */
    @Builder.Default private final EvictionPolicy evictionPolicy = EvictionPolicy.LRU;

    /** Whether to enable metrics collection for the cache. Defaults to true. */
    @Builder.Default private final boolean enableMetrics = true;

    /**
     * Creates a ClientSideCache with auto-generated cache ID and default settings.
     *
     * @param maxCacheKb Maximum memory limit for the cache in kilobytes.
     * @return A new ClientSideCache instance with auto-generated ID.
     */
    public static ClientSideCache create(long maxCacheKb) {
        return ClientSideCache.builder().maxCacheKb(maxCacheKb).build();
    }

    /**
     * Generates a unique cache identifier.
     *
     * @return A unique cache ID string.
     */
    private static String generateCacheId() {
        return "cache-" + UUID.randomUUID().toString();
    }
}
