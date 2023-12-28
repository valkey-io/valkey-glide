package glide.api.models.exceptions;

public class ExecAbortException extends RedisException {
  public ExecAbortException() {
    super();
  }

  public ExecAbortException(String message) {
    super(message);
  }

  public ExecAbortException(Throwable cause) {
    super(cause);
  }

  public ExecAbortException(String message, Throwable cause) {
    super(message, cause);
  }
}
