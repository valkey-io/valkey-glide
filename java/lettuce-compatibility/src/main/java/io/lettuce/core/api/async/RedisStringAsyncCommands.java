/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core.api.async;

import io.lettuce.core.RedisFuture;

/**
 * Asynchronous Redis String commands for Lettuce compatibility. Methods return {@link RedisFuture}
 * for non-blocking execution.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public interface RedisStringAsyncCommands<K, V> {

    /**
     * Set the string value of a key asynchronously.
     *
     * @param key the key
     * @param value the value
     * @return a RedisFuture that completes with the simple string reply (e.g. "OK")
     */
    RedisFuture<String> set(K key, V value);

    /**
     * Get the value of a key asynchronously.
     *
     * @param key the key
     * @return a RedisFuture that completes with the value, or null when key does not exist
     */
    RedisFuture<V> get(K key);
}
