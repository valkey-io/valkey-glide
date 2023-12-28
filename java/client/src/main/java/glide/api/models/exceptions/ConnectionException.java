package glide.api.models.exceptions;

public class ConnectionException extends RedisException {
  public ConnectionException() {
    super();
  }

  public ConnectionException(String message) {
    super(message);
  }

  public ConnectionException(Throwable cause) {
    super(cause);
  }

  public ConnectionException(String message, Throwable cause) {
    super(message, cause);
  }
}
