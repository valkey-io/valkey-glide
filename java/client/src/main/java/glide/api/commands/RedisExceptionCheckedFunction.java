package glide.api.commands;

import glide.api.models.exceptions.RedisException;

@FunctionalInterface
public interface RedisExceptionCheckedFunction<R, T> {

  /**
   * Functional response handler that takes a protobuf Response object. <br>
   * Returns a typed object on a successful Redis response. <br>
   * Throws RedisException when receiving a Redis error response. <br>
   *
   * @param response - Redis Response
   * @return T - response payload type
   * @throws RedisException
   */
  T apply(R response) throws RedisException;
}
