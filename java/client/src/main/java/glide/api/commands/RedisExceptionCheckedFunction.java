package glide.api.commands;

import glide.api.models.exceptions.RedisException;

@FunctionalInterface
public interface RedisExceptionCheckedFunction<R, T> {

  /**
   * Functional response handler that throws RedisException on a fail
   *
   * @param response - Redis Response
   * @return T - response payload type
   * @throws RedisException
   */
  T apply(R response) throws RedisException;
}
