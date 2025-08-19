/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.exceptions;

/**
 * JedisConnectionException compatibility class for Valkey GLIDE wrapper. Thrown when
 * connection-related errors occur.
 */
public class JedisConnectionException extends JedisException {

    public JedisConnectionException(String message) {
        super(message);
    }

    public JedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public JedisConnectionException(Throwable cause) {
        super(cause);
    }
}
