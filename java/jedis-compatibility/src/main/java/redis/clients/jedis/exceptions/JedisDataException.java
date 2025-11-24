/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.exceptions;

/**
 * JedisDataException compatibility class for Valkey GLIDE wrapper. Thrown when Redis returns an
 * error response.
 */
public class JedisDataException extends JedisException {

    public JedisDataException(String message) {
        super(message);
    }

    public JedisDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public JedisDataException(Throwable cause) {
        super(cause);
    }
}
