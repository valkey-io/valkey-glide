package babushka.connectors.handlers;

import babushka.managers.CallbackManager;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import io.netty.channel.Channel;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.Response;

/**
 * Class responsible for manipulations with Netty's {@link Channel}.<br>
 * Uses a {@link CallbackManager} to record callbacks of every request sent.
 */
@RequiredArgsConstructor
public class ChannelHandler {
  private final Channel channel;
  private final CallbackManager callbackManager;

  /** Write a protobuf message to the socket. */
  public CompletableFuture<Response> write(RedisRequest.Builder request, boolean flush) {
    var commandId = callbackManager.registerRequest();
    request.setCallbackIdx(commandId.getKey());

    if (flush) {
      channel.writeAndFlush(request.build().toByteArray());
    } else {
      channel.write(request.build().toByteArray());
    }
    return commandId.getValue();
  }

  /** Write a protobuf message to the socket. */
  public CompletableFuture<Response> connect(ConnectionRequest request) {
    channel.writeAndFlush(request.toByteArray());
    return callbackManager.getConnectionPromise();
  }

  /** Closes the UDS connection and frees corresponding resources. */
  public void close() {
    channel.close();
    callbackManager.shutdownGracefully();
  }
}
