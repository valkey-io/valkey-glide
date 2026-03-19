/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

/**
 * Exception thrown when a Redis connection fails or encounters an error. This is the Lettuce
 * compatibility layer exception that wraps underlying GLIDE exceptions.
 */
public class RedisConnectionException extends RedisException {

    public RedisConnectionException(String message) {
        super(message);
    }

    public RedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedisConnectionException(Throwable cause) {
        super(cause);
    }
}
