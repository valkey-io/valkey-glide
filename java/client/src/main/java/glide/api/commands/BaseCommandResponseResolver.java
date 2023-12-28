package glide.api.commands;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import lombok.AllArgsConstructor;
import response.ResponseOuterClass;

/**
 * Response resolver responsible for evaluating the Redis response object with a success or failure.
 */
@AllArgsConstructor
public class BaseCommandResponseResolver
    implements RedisExceptionCheckedFunction<ResponseOuterClass.Response, Object> {

  private RedisExceptionCheckedFunction<Long, Object> respPointerResolver;

  /**
   * Extracts value from the RESP pointer. <br>
   * Throws errors when the response is unsuccessful.
   *
   * @return A generic Object with the Response | null if the response is empty
   */
  public Object apply(ResponseOuterClass.Response response) throws RedisException {
    // TODO: handle object if the object is small
    // TODO: handle RESP2 object if configuration is set
    if (response.hasRequestError()) {
      ResponseOuterClass.RequestError error = response.getRequestError();
      String msg = error.getMessage();
      switch (error.getType()) {
        case Unspecified:
          throw new RedisException(msg);
        case ExecAbort:
          throw new ExecAbortException(msg);
        case Timeout:
          throw new TimeoutException(msg);
        case Disconnect:
          throw new ConnectionException(msg);
      }
      throw new RequestException(response.getRequestError().getMessage());
    }
    if (response.hasClosingError()) {
      throw new ClosingException(response.getClosingError());
    }
    if (response.hasRespPointer()) {
      return respPointerResolver.apply(response.getRespPointer());
    }
    if (response.hasConstantResponse()) {
      // TODO: confirm
      return "Ok";
    }
    return null;
  }
}
