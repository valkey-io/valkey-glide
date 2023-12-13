package babushka.connectors.handlers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/** Handler for outbound traffic though UDS. Used by Netty. */
public class WriteHandler extends ChannelOutboundHandlerAdapter {
  /**
   * Converts objects submitted to {@link Channel#write(Object)} and {@link
   * Channel#writeAndFlush(Object)} to a {@link ByteBuf}.
   */
  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
      throws Exception {
    var bytes = (byte[]) msg;

    super.write(ctx, Unpooled.copiedBuffer(bytes), promise);
  }
}
