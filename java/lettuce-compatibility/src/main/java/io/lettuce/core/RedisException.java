/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

/** Base exception for all Redis-related exceptions in the Lettuce compatibility layer. */
public class RedisException extends RuntimeException {

    public RedisException(String message) {
        super(message);
    }

    public RedisException(String message, Throwable cause) {
        super(message, cause);
    }

    public RedisException(Throwable cause) {
        super(cause);
    }
}
