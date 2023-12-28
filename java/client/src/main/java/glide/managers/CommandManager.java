package glide.managers;

import glide.api.commands.Command;
import glide.api.commands.RedisExceptionCheckedFunction;
import java.util.concurrent.CompletableFuture;
import lombok.AllArgsConstructor;
import response.ResponseOuterClass.Response;

@AllArgsConstructor
public class CommandManager {

  CompletableFuture<Response> channel;

  /**
   * @param command
   * @param responseHandler
   * @return
   */
  public <T> CompletableFuture<T> submitNewCommand(
      Command command, RedisExceptionCheckedFunction<Response, T> responseHandler) {
    // register callback
    // create protobuf message from command
    // submit async call
    return channel.thenApplyAsync(response -> responseHandler.apply(response));
  }

  public CompletableFuture<Void> closeConnection() {
    return new CompletableFuture<>();
  }
}
