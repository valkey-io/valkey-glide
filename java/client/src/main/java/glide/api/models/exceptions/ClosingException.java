package glide.api.models.exceptions;

/** Redis client error: Errors that report that the client has closed and is no longer usable. */
public class ClosingException extends RedisException {
    public ClosingException(String message) {
        super(message);
    }
}
