package glide.api.models.exceptions;

/** Encapsulated an error returned from the Redis or during processing of a Redis request */
public class RedisException extends RuntimeException {
  public RedisException(String message) {
    super(message);
  }
}
