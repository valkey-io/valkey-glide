package glide.api.models.exceptions;

/** Error returned from Redis client: request has timed out */
public class TimeoutException extends RedisException {
  public TimeoutException(String message) {
    super(message);
  }
}
