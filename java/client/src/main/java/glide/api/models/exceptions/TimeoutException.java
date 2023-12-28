package glide.api.models.exceptions;

public class TimeoutException extends RedisException {
  public TimeoutException() {
    super();
  }

  public TimeoutException(String message) {
    super(message);
  }

  public TimeoutException(Throwable cause) {
    super(cause);
  }

  public TimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
