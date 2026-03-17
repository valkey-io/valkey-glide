/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core.api;

import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import java.io.Closeable;
import java.time.Duration;

/**
 * A stateful connection to a Redis server. Provides access to synchronous and asynchronous command
 * execution through sync() and async().
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public interface StatefulRedisConnection<K, V> extends Closeable {

    /**
     * Returns the synchronous command API for this connection.
     *
     * @return the synchronous command API
     */
    RedisStringCommands<K, V> sync();

    /**
     * Returns the asynchronous command API for this connection.
     *
     * @return the asynchronous command API
     */
    RedisStringAsyncCommands<K, V> async();

    /**
     * Set the default timeout for operations on this connection.
     *
     * @param timeout the timeout duration
     */
    void setTimeout(Duration timeout);

    /** Close the connection. Must be called to release resources. */
    @Override
    void close();

    /**
     * Check if the connection is open.
     *
     * @return true if the connection is open
     */
    boolean isOpen();
}
