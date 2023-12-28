package glide.api.commands;

import glide.api.models.exceptions.RedisException;
import java.util.concurrent.CompletableFuture;
import response.ResponseOuterClass.Response;

/** String Commands interface to handle single commands that return Strings. */
public interface StringCommands {

  /**
   * Extracts the response from the Protobuf response and either throws an exception or returns the
   * appropriate response has a String
   *
   * @param response Redis protobuf message
   * @return Response as a String
   */
  static String handleStringResponse(Response response) {
    // return function to convert protobuf.Response into the response object by
    // calling valueFromPointer
    Object value = BaseCommands.applyBaseCommandResponseResolver().apply(response);
    if (value instanceof String) {
      return (String) value;
    }
    throw new RedisException(
        "Unexpected return type from Redis: got " + value.getClass() + " expected String");
  }

  CompletableFuture<?> get(String key);
}
