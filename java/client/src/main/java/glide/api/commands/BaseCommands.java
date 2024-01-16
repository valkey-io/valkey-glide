package glide.api.commands;

import glide.ffi.resolvers.RedisValueResolver;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import response.ResponseOuterClass.Response;

/** Base Commands interface to handle generic command and transaction requests. */
public interface BaseCommands {

  /**
   * default Object handler from response
   *
   * @return BaseCommandResponseResolver to deliver the response
   */
  static BaseCommandResponseResolver applyBaseCommandResponseResolver() {
    return new BaseCommandResponseResolver(RedisValueResolver::valueFromPointer);
  }

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
    return BaseCommands.applyBaseCommandResponseResolver().apply(response);
  }

  public static List<Object> handleTransactionResponse(Response response) {
    // return function to convert protobuf.Response into the response object by
    // calling valueFromPointer

    List<Object> transactionResponse =
        (List<Object>) BaseCommands.applyBaseCommandResponseResolver().apply(response);
    return transactionResponse;
  }

  /**
   * Execute a @see{Command} by sending command via socket manager
   *
   * @param args arguments for the custom command
   * @return a CompletableFuture with response result from Redis
   */
  CompletableFuture<Object> customCommand(String[] args);
}
