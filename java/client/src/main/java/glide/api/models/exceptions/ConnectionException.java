package glide.api.models.exceptions;

/**
 * Redis client error: Errors that are thrown when a connection disconnects. These errors can be
 * temporary, as the client will attempt to reconnect.
 */
public class ConnectionException extends RedisException {
    public ConnectionException(String message) {
        super(message);
    }
}
