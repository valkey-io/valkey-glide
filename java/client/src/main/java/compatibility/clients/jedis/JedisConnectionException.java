/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

/**
 * JedisConnectionException compatibility class for Valkey GLIDE wrapper. This exception is thrown
 * when connection to Redis/Valkey server fails.
 */
public class JedisConnectionException extends JedisException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a new JedisConnectionException with a message.
     *
     * @param message the error message
     */
    public JedisConnectionException(String message) {
        super(message);
    }

    /**
     * Create a new JedisConnectionException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public JedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new JedisConnectionException with a cause.
     *
     * @param cause the underlying cause
     */
    public JedisConnectionException(Throwable cause) {
        super(cause);
    }
}
