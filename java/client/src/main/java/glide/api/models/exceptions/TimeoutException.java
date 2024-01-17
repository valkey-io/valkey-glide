package glide.api.models.exceptions;

/** Redis client error: Errors that are thrown when a request times out. */
public class TimeoutException extends RedisException {
    public TimeoutException(String message) {
        super(message);
    }
}
