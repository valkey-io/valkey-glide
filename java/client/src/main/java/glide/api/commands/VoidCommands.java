package glide.api.commands;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import java.util.concurrent.CompletableFuture;
import response.ResponseOuterClass.RequestError;
import response.ResponseOuterClass.Response;

/** String Commands interface to handle single commands that have no payload. */
public interface VoidCommands {

  /**
   * Check for errors in the Response and return null Throws an error if an unexpected value is
   * returned
   *
   * @return null if the response is empty
   */
  static Void handleVoidResponse(Object respObject) {
    Response response = (Response) respObject;
    if (response.hasRequestError()) {
      RequestError error = response.getRequestError();
      String msg = error.getMessage();
      switch (error.getType()) {
        case Unspecified:
          throw new RedisException("Unexpected result: " + msg);
        case ExecAbort:
          throw new ExecAbortException("ExecAbortException: " + msg);
        case Timeout:
          throw new TimeoutException("TimeoutException: " + msg);
        case Disconnect:
          throw new ConnectionException("Disconnection: " + msg);
      }
      throw new RequestException(response.getRequestError().getMessage());
    }
    if (response.hasClosingError()) {
      throw new ClosingException(response.getClosingError());
    }
    if (response.hasRespPointer()) {
      throw new RuntimeException(
          "Unexpected object returned in response - expected constantResponse or null");
    }
    if (response.hasConstantResponse()) {
      return null; // Void
    }
    // TODO commented out due to #710: empty response means a successful connection
    // https://github.com/aws/babushka/issues/710
    return null;
  }

  CompletableFuture<?> set(String key, String value);
}
