package glide.api.models.exceptions;

/** Error returned from Redis client: Redis request has failed */
public class RequestException extends RedisException {
  public RequestException(String message) {
    super(message);
  }
}
