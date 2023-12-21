package babushka.utils;

import babushka.connectors.resources.Platform;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.redis.ArrayRedisMessage;
import io.netty.handler.codec.redis.ErrorRedisMessage;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.IntegerRedisMessage;
import io.netty.handler.codec.redis.RedisArrayAggregator;
import io.netty.handler.codec.redis.RedisBulkStringAggregator;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.RedisEncoder;
import io.netty.handler.codec.redis.RedisMessage;
import io.netty.handler.codec.redis.SimpleStringRedisMessage;
import io.netty.util.CharsetUtil;
import java.net.InetSocketAddress;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import lombok.Setter;

public class RedisServerMock {

  public abstract static class ServerMock {
    /** Return `null` to do not reply. */
    public abstract RedisMessage reply(String cmd);

    protected RedisMessage reply0(String cmd) {
      return reply(cmd);
    }

    public static RedisMessage error(String text) {
      return new ErrorRedisMessage(text);
    }

    public static RedisMessage error(String prefix, String text) {
      // https://redis.io/docs/reference/protocol-spec/#simple-errors
      if (prefix.contains(" ") || prefix.contains("\r") || prefix.contains("\n")) {
        throw new IllegalArgumentException();
      }
      return new ErrorRedisMessage(prefix.toUpperCase() + " " + text);
    }

    public static RedisMessage simpleString(String text) {
      return new SimpleStringRedisMessage(text);
    }

    public static RedisMessage OK() {
      return simpleString("OK");
    }

    public static RedisMessage number(long value) {
      return new IntegerRedisMessage(value);
    }

    /** A multi-line message. */
    public static RedisMessage multiString(String text) {
      return new FullBulkStringRedisMessage(Unpooled.copiedBuffer(text.getBytes()));
    }
  }

  public abstract static class ServerMockConnectAll extends ServerMock {
    @Override
    protected RedisMessage reply0(String cmd) {
      if (cmd.startsWith("CLIENT SETINFO")) {
        return OK();
      } else if (cmd.startsWith("INFO REPLICATION")) {
        var response =
            "# Replication\r\n"
                + "role:master\r\n"
                + "connected_slaves:0\r\n"
                + "master_failover_state:no-failover\r\n"
                + "master_replid:d7646c8d14901de9347f1f675c70bcf269a503eb\r\n"
                + "master_replid2:0000000000000000000000000000000000000000\r\n"
                + "master_repl_offset:0\r\n"
                + "second_repl_offset:-1\r\n"
                + "repl_backlog_active:0\r\n"
                + "repl_backlog_size:1048576\r\n"
                + "repl_backlog_first_byte_offset:0\r\n"
                + "repl_backlog_histlen:0\r\n";
        return multiString(response);
      }
      return reply(cmd);
    }
  }

  // TODO support configurable port to test cluster mode
  public static final int PORT = 6380;

  /** Thread pool supplied to <em>Netty</em> to perform all async IO. */
  private EventLoopGroup group;

  private Channel channel;

  private static RedisServerMock instance;

  private ServerMock messageProcessor;

  /** Update {@link ServerMock} into a running {@link RedisServerMock}. */
  public static void updateServerMock(ServerMock newMock) {
    instance.messageProcessor = newMock;
  }

  private final AtomicBoolean failed = new AtomicBoolean(false);

  /** Get and clear failure status. */
  public static boolean failed() {
    return instance.failed.compareAndSet(true, false);
  }

  @Setter private static boolean debugLogging = false;

  private RedisServerMock() {
    try {
      channel =
          new ServerBootstrap()
              .group(group = Platform.createNettyThreadPool("RedisMock", OptionalInt.empty()))
              .channel(Platform.getServerTcpNettyChannelType())
              .childHandler(
                  new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                      ch.pipeline()
                          // https://github.com/netty/netty/blob/4.1/example/src/main/java/io/netty/example/redis/RedisClient.java
                          .addLast(new RedisDecoder())
                          .addLast(new RedisBulkStringAggregator())
                          .addLast(new RedisArrayAggregator())
                          .addLast(new RedisEncoder())
                          .addLast(
                              new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg)
                                    throws Exception {
                                  RedisMessage redisMessage = (RedisMessage) msg;
                                  var str = RedisMessageToString(redisMessage);
                                  if (debugLogging) {
                                    System.out.printf("-- Received%n  %s%n", str);
                                  }
                                  var response = messageProcessor.reply0(str);
                                  if (response != null) {
                                    if (debugLogging) {
                                      System.out.printf(
                                          "-- Replying with%n  %s%n",
                                          RedisMessageToString(response));
                                    }
                                    ctx.writeAndFlush(response);
                                  } else if (debugLogging) {
                                    System.out.printf("-- Ignoring%n");
                                  }
                                }

                                @Override
                                public void exceptionCaught(
                                    ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                  cause.printStackTrace();
                                  ctx.close();
                                  failed.setPlain(true);
                                }
                              });
                    }
                  })
              .bind(new InetSocketAddress(PORT))
              // .sync()
              .channel();
    } catch (Exception e) {
      System.err.printf(
          "Failed to create a channel %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
      e.printStackTrace(System.err);
    }
  }

  public static void start(ServerMock messageProcessor) {
    if (instance != null) {
      stop();
    }
    instance = new RedisServerMock();
    instance.messageProcessor = messageProcessor;
  }

  public static void stop() {
    instance.channel.close();
    instance.group.shutdownGracefully();
    instance = null;
  }

  private static String RedisMessageToString(RedisMessage msg) {
    if (msg instanceof SimpleStringRedisMessage) {
      return ((SimpleStringRedisMessage) msg).content();
    } else if (msg instanceof ErrorRedisMessage) {
      return ((ErrorRedisMessage) msg).content();
    } else if (msg instanceof IntegerRedisMessage) {
      return String.valueOf(((IntegerRedisMessage) msg).value());
    } else if (msg instanceof FullBulkStringRedisMessage) {
      return getString((FullBulkStringRedisMessage) msg);
    } else if (msg instanceof ArrayRedisMessage) {
      return ((ArrayRedisMessage) msg)
          .children().stream()
              .map(RedisServerMock::RedisMessageToString)
              .collect(Collectors.joining(" "));
    } else {
      throw new CodecException("unknown message type: " + msg);
    }
  }

  private static String getString(FullBulkStringRedisMessage msg) {
    if (msg.isNull()) {
      return "(null)";
    }
    return msg.content().toString(CharsetUtil.UTF_8);
  }
}
