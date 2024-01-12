package glide.api.models.exceptions;

/**
 * Error returned from Redis client: Redis connection is disconnected or unavailable to the client
 */
public class ConnectionException extends RedisException {
  public ConnectionException(String message) {
    super(message);
  }
}
