package babushka.connectors.handlers;

import babushka.connectors.resources.Platform;
import babushka.connectors.resources.ThreadPoolAllocator;
import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.unix.DomainSocketAddress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.Response;

/**
 * Class responsible for manipulations with Netty's {@link Channel}.<br>
 * Uses a {@link CallbackDispatcher} to record callbacks of every request sent.
 */
public class ChannelHandler {
  private final Channel channel;
  private final CallbackDispatcher callbackDispatcher;

  /** Open a new channel for a new client. */
  public ChannelHandler(CallbackDispatcher callbackDispatcher, String socketPath) {
    channel =
        new Bootstrap()
            // TODO let user specify the thread pool or pool size as an option
            .group(ThreadPoolAllocator.createNettyThreadPool("babushka-channel", Optional.empty()))
            .channel(Platform.getClientUdsNettyChannelType())
            .handler(new ProtobufSocketChannelInitializer(callbackDispatcher))
            .connect(new DomainSocketAddress(socketPath))
            // TODO call here .sync() if needed or remove this comment
            .channel();
    this.callbackDispatcher = callbackDispatcher;
  }

  /** Write a protobuf message to the socket. */
  public CompletableFuture<Response> write(RedisRequest.Builder request, boolean flush) {
    var commandId = callbackDispatcher.registerRequest();
    request.setCallbackIdx(commandId.getKey());

    if (flush) {
      channel.writeAndFlush(request.build());
    } else {
      channel.write(request.build());
    }
    return commandId.getValue();
  }

  /** Write a protobuf message to the socket. */
  public CompletableFuture<Response> connect(ConnectionRequest request) {
    channel.writeAndFlush(request);
    return callbackDispatcher.registerConnection();
  }

  /** Closes the UDS connection and frees corresponding resources. */
  public void close() {
    channel.close();
    callbackDispatcher.shutdownGracefully();
  }
}
