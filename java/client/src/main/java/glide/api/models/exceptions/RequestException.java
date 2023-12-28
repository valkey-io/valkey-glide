package glide.api.models.exceptions;

public class RequestException extends RedisException {
  public RequestException() {
    super();
  }

  public RequestException(String message) {
    super(message);
  }

  public RequestException(Throwable cause) {
    super(cause);
  }

  public RequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
