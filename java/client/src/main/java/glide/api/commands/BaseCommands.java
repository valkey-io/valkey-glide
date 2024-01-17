package glide.api.commands;

import glide.ffi.resolvers.RedisValueResolver;
import glide.managers.BaseCommandResponseResolver;
import java.util.concurrent.CompletableFuture;
import response.ResponseOuterClass.Response;

/** Base Commands interface to handle generic command and transaction requests. */
public interface BaseCommands {

  /**
   * Extracts the response from the Protobuf response and either throws an exception or returns the
   * appropriate response has an Object
   *
   * @param response Redis protobuf message
   * @return Response Object
   */
  static Object handleObjectResponse(Response response) {
    // return function to convert protobuf.Response into the response object by
    // calling valueFromPointer
    return (new BaseCommandResponseResolver(RedisValueResolver::valueFromPointer)).apply(response);
  }

  /**
   * Execute a @see{Command} by sending command via socket manager
   *
   * @param args arguments for the custom command
   * @return a CompletableFuture with response result from Redis
   */
  CompletableFuture<Object> customCommand(String[] args);
}
