package glide.api.models.exceptions;

/** Error returned from Redis client: due to transaction execution abort */
public class ExecAbortException extends RedisException {
  public ExecAbortException(String message) {
    super(message);
  }
}
