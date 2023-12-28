package glide.api.models.exceptions;

public class ClosingException extends RedisException {
  public ClosingException() {
    super();
  }

  public ClosingException(String message) {
    super(message);
  }

  public ClosingException(Throwable cause) {
    super(cause);
  }

  public ClosingException(String message, Throwable cause) {
    super(message, cause);
  }
}
