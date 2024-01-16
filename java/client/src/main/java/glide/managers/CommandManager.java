package glide.managers;

import glide.api.commands.Command;
import glide.api.commands.RedisExceptionCheckedFunction;
import glide.connectors.handlers.ChannelHandler;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import redis_request.RedisRequestOuterClass;
import response.ResponseOuterClass.Response;

/**
 * Service responsible for submitting command requests to a socket channel handler and unpack
 * responses from the same socket channel handler.
 */
@RequiredArgsConstructor
public class CommandManager {

  /** UDS connection representation. */
  private final ChannelHandler channel;

  /**
   * Build a command and send.
   *
   * @param command
   * @param responseHandler - to handle the response object
   * @return A result promise of type T
   */
  public <T> CompletableFuture<T> submitNewCommand(
      Command command, RedisExceptionCheckedFunction<Response, T> responseHandler) {
    // register callback
    // create protobuf message from command
    // submit async call
    return channel
        .write(prepareRedisRequest(command.getRequestType(), command.getArguments()), true)
        .thenApplyAsync(response -> responseHandler.apply(response));
  }

  /**
   * Build a protobuf command/transaction request object.<br>
   * Used by {@link CommandManager}.
   *
   * @return An uncompleted request. CallbackDispatcher is responsible to complete it by adding a
   *     callback id.
   */
  private RedisRequestOuterClass.RedisRequest.Builder prepareRedisRequest(
      Command.RequestType command, String[] args) {
    RedisRequestOuterClass.Command.ArgsArray.Builder commandArgs =
        RedisRequestOuterClass.Command.ArgsArray.newBuilder();
    for (var arg : args) {
      commandArgs.addArgs(arg);
    }

    return RedisRequestOuterClass.RedisRequest.newBuilder()
        .setSingleCommand(
            RedisRequestOuterClass.Command.newBuilder()
                .setRequestType(mapRequestTypes(command))
                .setArgsArray(commandArgs.build())
                .build())
        .setRoute(
            RedisRequestOuterClass.Routes.newBuilder()
                .setSimpleRoutes(RedisRequestOuterClass.SimpleRoutes.AllNodes)
                .build());
  }

  private RedisRequestOuterClass.RequestType mapRequestTypes(Command.RequestType inType) {
    switch (inType) {
      case CUSTOM_COMMAND:
        return RedisRequestOuterClass.RequestType.CustomCommand;
    }
    throw new RuntimeException("Unsupported request type");
  }
}
