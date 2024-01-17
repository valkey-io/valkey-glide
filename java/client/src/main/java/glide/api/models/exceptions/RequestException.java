package glide.api.models.exceptions;

/** Redis client error: Errors that were reported during a request. */
public class RequestException extends RedisException {
    public RequestException(String message) {
        super(message);
    }
}
