package babushka.connectors.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.Response;

/** Handler for inbound traffic though UDS. Used by Netty. */
@RequiredArgsConstructor
public class ReadHandler extends ChannelInboundHandlerAdapter {

  private final CallbackDispatcher callbackDispatcher;

  /** Submit responses from babushka to an instance {@link CallbackDispatcher} to handle them. */
  @Override
  public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) {
    callbackDispatcher.completeRequest((Response) msg);
  }

  /** Handles uncaught exceptions from {@link #channelRead(ChannelHandlerContext, Object)}. */
  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    System.out.printf("=== exceptionCaught %s %s %n", ctx, cause);
    cause.printStackTrace(System.err);
    super.exceptionCaught(ctx, cause);
  }
}
