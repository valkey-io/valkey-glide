package glide.api.commands;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import lombok.AllArgsConstructor;
import response.ResponseOuterClass.RequestError;
import response.ResponseOuterClass.Response;

/**
 * Response resolver responsible for evaluating the Redis response object with a success or failure.
 */
@AllArgsConstructor
public class BaseCommandResponseResolver
    implements RedisExceptionCheckedFunction<Response, Object> {

  private RedisExceptionCheckedFunction<Long, Object> respPointerResolver;

  /**
   * Extracts value from the RESP pointer. <br>
   * Throws errors when the response is unsuccessful.
   *
   * @return A generic Object with the Response | null if the response is empty
   */
  public Object apply(Response response) throws RedisException {
    // TODO: handle object if the object is small
    // TODO: handle RESP2 object if configuration is set
    if (response.hasRequestError()) {
      RequestError error = response.getRequestError();
      String msg = error.getMessage();
      switch (error.getType()) {
        case Unspecified:
          // Unspecified error on Redis service-side
          throw new RequestException(msg);
        case ExecAbort:
          // Transactional error on Redis service-side
          throw new ExecAbortException(msg);
        case Timeout:
          // Timeout from Glide to Redis service
          throw new TimeoutException(msg);
        case Disconnect:
          // Connection problem between Glide and Redis
          throw new ConnectionException(msg);
        default:
          // Request or command error from Redis
          throw new RequestException(msg);
      }
    }
    if (response.hasClosingError()) {
      // A closing error is thrown when Rust-core is not connected to Redis
      // We want to close shop and throw a ClosingException
      // TODO: close the channel on a closing error
      // channel.close();
      throw new ClosingException(response.getClosingError());
    }
    if (response.hasConstantResponse()) {
      // Return "OK"
      return response.getConstantResponse().toString();
    }
    if (response.hasRespPointer()) {
      // Return the shared value - which may be a null value
      return respPointerResolver.apply(response.getRespPointer());
    }
    // if no response payload is provided, assume null
    return null;
  }
}
