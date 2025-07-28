/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

/**
 * JedisException compatibility class for Valkey GLIDE wrapper. This exception is thrown when
 * Redis/Valkey operations fail.
 */
public class JedisException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a new JedisException with a message.
     *
     * @param message the error message
     */
    public JedisException(String message) {
        super(message);
    }

    /**
     * Create a new JedisException with a message and cause.
     *
     * @param message the error message
     * @param cause the underlying cause
     */
    public JedisException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new JedisException with a cause.
     *
     * @param cause the underlying cause
     */
    public JedisException(Throwable cause) {
        super(cause);
    }
}
