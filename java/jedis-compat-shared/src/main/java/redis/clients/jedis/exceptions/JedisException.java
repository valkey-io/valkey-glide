/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.exceptions;

/**
 * JedisException compatibility class for Valkey GLIDE wrapper. Base exception for all Jedis-related
 * errors.
 */
public class JedisException extends RuntimeException {

    public JedisException(String message) {
        super(message);
    }

    public JedisException(String message, Throwable cause) {
        super(message, cause);
    }

    public JedisException(Throwable cause) {
        super(cause);
    }
}
