/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core.api.sync;

/**
 * Synchronous Redis String commands interface for Lettuce compatibility. Provides blocking
 * operations on String data types.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public interface RedisStringCommands<K, V> {

    /**
     * Set the string value of a key.
     *
     * @param key the key
     * @param value the value
     * @return Simple string reply
     */
    String set(K key, V value);

    /**
     * Get the value of a key.
     *
     * @param key the key
     * @return the value of key, or null when key does not exist
     */
    V get(K key);
}
