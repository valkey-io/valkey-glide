package glide.api.models.exceptions;

/** Error returned from Redis client: Redis is closing or unavailable to the client */
public class ClosingException extends RedisException {
  public ClosingException(String message) {
    super(message);
  }
}
