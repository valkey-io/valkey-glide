package javababushka;

import static connection_request.ConnectionRequestOuterClass.AddressInfo;
import static connection_request.ConnectionRequestOuterClass.AuthenticationInfo;
import static connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import static connection_request.ConnectionRequestOuterClass.ConnectionRetryStrategy;
import static connection_request.ConnectionRequestOuterClass.ReadFromReplicaStrategy;
import static connection_request.ConnectionRequestOuterClass.TlsMode;
import static redis_request.RedisRequestOuterClass.Command;
import static redis_request.RedisRequestOuterClass.Command.ArgsArray;
import static redis_request.RedisRequestOuterClass.RedisRequest;
import static redis_request.RedisRequestOuterClass.RequestType;
import static redis_request.RedisRequestOuterClass.Routes;
import static redis_request.RedisRequestOuterClass.SimpleRoutes;
import static response.ResponseOuterClass.Response;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollDomainSocketChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.UnixChannel;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.tuple.Pair;

public class Client implements AutoCloseable {

  private static final int RESPONSE_TIMEOUT_MILLISECONDS = 250;
  private static final int CLIENT_CREATION_TIMEOUT_MILLISECONDS = 250;
  private static final int HIGH_WRITE_WATERMARK = 4096;
  private static final int LOW_WRITE_WATERMARK = 1024;
  private static final long DEFAULT_TIMEOUT_MILLISECONDS = 1000;
  public static boolean ALWAYS_FLUSH_ON_WRITE = true;

  // https://netty.io/3.6/api/org/jboss/netty/handler/queue/BufferedWriteHandler.html
  // Flush every N bytes if !ALWAYS_FLUSH_ON_WRITE
  public static int AUTO_FLUSH_THRESHOLD_BYTES = 512; // 1024;
  private final AtomicInteger nonFlushedBytesCounter = new AtomicInteger(0);

  // Flush every N writes if !ALWAYS_FLUSH_ON_WRITE
  public static int AUTO_FLUSH_THRESHOLD_WRITES = 10;
  private final AtomicInteger nonFlushedWritesCounter = new AtomicInteger(0);

  // If !ALWAYS_FLUSH_ON_WRITE and a command has no response in N millis, flush (probably it wasn't
  // send)
  public static int AUTO_FLUSH_RESPONSE_TIMEOUT_MILLIS = 100;
  // If !ALWAYS_FLUSH_ON_WRITE flush on timer (like a cron)
  public static int AUTO_FLUSH_TIMER_MILLIS = 200;

  public static int PENDING_RESPONSES_ON_CLOSE_TIMEOUT_MILLIS = 1000;

  // Futures to handle responses. Index is callback id, starting from 1 (0 index is for connection
  // request always).
  // Is it not a concurrent nor sync collection, but it is synced on adding. No removes.
  private final List<CompletableFuture<Response>> responses = new ArrayList<>();
  // Unique offset for every client to avoid having multiple commands with the same id at a time.
  // For debugging replace with: new Random().nextInt(1000) * 1000
  private final int callbackOffset = new Random().nextInt();

  // TODO move to a [static] constructor.
  private final String unixSocket = getSocket();

  private static String getSocket() {
    try {
      return BabushkaCoreNativeDefinitions.startSocketListenerExternal();
    } catch (Exception | UnsatisfiedLinkError e) {
      System.err.printf("Failed to get UDS from babushka and dedushka: %s%n%n", e);
      throw new RuntimeException(e);
    }
  }

  private Channel channel = null;
  private EventLoopGroup group = null;

  // We support MacOS and Linux only, because Babushka does not support Windows, because tokio does
  // not support it.
  // Probably we should use NIO (NioEventLoopGroup) for Windows.
  private static final boolean isMacOs = isMacOs();

  private static boolean isMacOs() {
    try {
      Class.forName("io.netty.channel.kqueue.KQueue");
      return KQueue.isAvailable();
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  static {
    // TODO fix: netty still doesn't use slf4j nor log4j
    InternalLoggerFactory.setDefaultFactory(Slf4JLoggerFactory.INSTANCE);
  }

  private void createChannel() {
    // TODO maybe move to constructor or to static?
    try {
      channel =
          new Bootstrap()
              .option(
                  ChannelOption.WRITE_BUFFER_WATER_MARK,
                  new WriteBufferWaterMark(LOW_WRITE_WATERMARK, HIGH_WRITE_WATERMARK))
              .option(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT)
              .group(group = isMacOs ? new KQueueEventLoopGroup() : new EpollEventLoopGroup())
              .channel(isMacOs ? KQueueDomainSocketChannel.class : EpollDomainSocketChannel.class)
              .handler(
                  new ChannelInitializer<UnixChannel>() {
                    @Override
                    public void initChannel(UnixChannel ch) throws Exception {
                      ch.pipeline()
                          .addLast("logger", new LoggingHandler(LogLevel.DEBUG))
                          // https://netty.io/4.1/api/io/netty/handler/codec/protobuf/ProtobufEncoder.html
                          .addLast("protobufDecoder", new ProtobufVarint32FrameDecoder())
                          .addLast("protobufEncoder", new ProtobufVarint32LengthFieldPrepender())
                          .addLast(
                              new ChannelInboundHandlerAdapter() {
                                @Override
                                public void channelRead(ChannelHandlerContext ctx, Object msg)
                                    throws Exception {
                                  // System.out.printf("=== channelRead %s %s %n", ctx, msg);
                                  var buf = (ByteBuf) msg;
                                  var bytes = new byte[buf.readableBytes()];
                                  buf.readBytes(bytes);
                                  // TODO surround parsing with try-catch, set error to future if
                                  // parsing failed.
                                  var response = Response.parseFrom(bytes);
                                  int callbackId = response.getCallbackIdx();
                                  if (callbackId != 0) {
                                    // connection request has hardcoded callback id = 0
                                    // https://github.com/aws/babushka/issues/600
                                    callbackId -= callbackOffset;
                                  }
                                  // System.out.printf("== Received response with callback %d%n",
                                  // response.getCallbackIdx());
                                  responses.get(callbackId).complete(response);
                                  responses.set(callbackId, null);
                                  super.channelRead(ctx, bytes);
                                }

                                @Override
                                public void exceptionCaught(
                                    ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                  System.out.printf("=== exceptionCaught %s %s %n", ctx, cause);
                                  cause.printStackTrace();
                                  super.exceptionCaught(ctx, cause);
                                }
                              })
                          .addLast(
                              new ChannelOutboundHandlerAdapter() {
                                @Override
                                public void write(
                                    ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                                    throws Exception {
                                  // System.out.printf("=== write %s %s %s %n", ctx, msg, promise);
                                  var bytes = (byte[]) msg;

                                  boolean needFlush = false;
                                  if (!ALWAYS_FLUSH_ON_WRITE) {
                                    synchronized (nonFlushedBytesCounter) {
                                      if (nonFlushedBytesCounter.addAndGet(bytes.length)
                                              >= AUTO_FLUSH_THRESHOLD_BYTES
                                          || nonFlushedWritesCounter.incrementAndGet()
                                              >= AUTO_FLUSH_THRESHOLD_WRITES) {
                                        nonFlushedBytesCounter.set(0);
                                        nonFlushedWritesCounter.set(0);
                                        needFlush = true;
                                      }
                                    }
                                  }
                                  super.write(ctx, Unpooled.copiedBuffer(bytes), promise);
                                  if (needFlush) {
                                    // flush outside the sync block
                                    flush(ctx);
                                    // System.out.println("-- auto flush - buffer");
                                  }
                                }
                              });
                    }
                  })
              .connect(new DomainSocketAddress(unixSocket))
              .sync()
              .channel();

    } catch (Exception e) {
      System.err.printf(
          "Failed to create a channel %s: %s%n", e.getClass().getSimpleName(), e.getMessage());
      e.printStackTrace(System.err);
    }

    if (!ALWAYS_FLUSH_ON_WRITE) {
      new Timer(true)
          .scheduleAtFixedRate(
              new TimerTask() {
                @Override
                public void run() {
                  channel.flush();
                  nonFlushedBytesCounter.set(0);
                  nonFlushedWritesCounter.set(0);
                }
              },
              0,
              AUTO_FLUSH_TIMER_MILLIS);
    }
  }

  public void closeConnection() {

    // flush and close the channel
    channel.flush();
    channel.close();
    // TODO: check that the channel is closed

    // shutdown the event loop group gracefully by waiting for the remaining response
    // and then shutting down the connection
    try {
      long waitStarted = System.nanoTime();
      long waitUntil =
          waitStarted + PENDING_RESPONSES_ON_CLOSE_TIMEOUT_MILLIS * 100_000; // in nanos
      for (var responseFuture : responses) {
        if (responseFuture == null || responseFuture.isDone()) {
          continue;
        }
        try {
          responseFuture.get(waitUntil - System.nanoTime(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | ExecutionException ignored) {
          // TODO: print warning
        } catch (TimeoutException e) {
          responseFuture.cancel(true);
          // TODO: cancel the rest
          break;
        }
      }
    } finally {
      var shuttingDown = group.shutdownGracefully();
      try {
        shuttingDown.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
      assert group.isShutdown() : "Redis connection did not shutdown gracefully";
    }
  }

  public void set(String key, String value) {
    waitForResult(asyncSet(key, value));
    // TODO parse response and rethrow an exception if there is an error
  }

  public String get(String key) {
    return waitForResult(asyncGet(key));
    // TODO support non-strings
  }

  private synchronized Pair<Integer, CompletableFuture<Response>> getNextCallback() {
    var future = new CompletableFuture<Response>();
    responses.add(future);
    return Pair.of(responses.size() - 1, future);
  }

  @Override
  public void close() throws Exception {
    closeConnection();
  }

  public Future<Response> asyncConnectToRedis(
      String host, int port, boolean useSsl, boolean clusterMode) {
    createChannel();

    var request =
        ConnectionRequest.newBuilder()
            .addAddresses(AddressInfo.newBuilder().setHost(host).setPort(port).build())
            .setTlsMode(
                useSsl // TODO: secure or insecure TLS?
                    ? TlsMode.SecureTls
                    : TlsMode.NoTls)
            .setClusterModeEnabled(clusterMode)
            .setResponseTimeout(RESPONSE_TIMEOUT_MILLISECONDS)
            .setClientCreationTimeout(CLIENT_CREATION_TIMEOUT_MILLISECONDS)
            .setReadFromReplicaStrategy(ReadFromReplicaStrategy.AlwaysFromPrimary)
            .setConnectionRetryStrategy(
                ConnectionRetryStrategy.newBuilder()
                    .setNumberOfRetries(1)
                    .setFactor(1)
                    .setExponentBase(1)
                    .build())
            .setAuthenticationInfo(
                AuthenticationInfo.newBuilder().setPassword("").setUsername("default").build())
            .setDatabaseId(0)
            .build();

    var future = new CompletableFuture<Response>();
    responses.add(future);
    channel.writeAndFlush(request.toByteArray());
    return future;
  }

  private CompletableFuture<Response> submitNewCommand(RequestType command, List<String> args) {
    var commandId = getNextCallback();
    // System.out.printf("== %s(%s), callback %d%n", command, String.join(", ", args), commandId);

    return CompletableFuture.supplyAsync(
            () -> {
              var commandArgs = ArgsArray.newBuilder();
              for (var arg : args) {
                commandArgs.addArgs(arg);
              }

              RedisRequest request =
                  RedisRequest.newBuilder()
                      .setCallbackIdx(commandId.getKey() + callbackOffset)
                      .setSingleCommand(
                          Command.newBuilder()
                              .setRequestType(command)
                              .setArgsArray(commandArgs.build())
                              .build())
                      .setRoute(Routes.newBuilder().setSimpleRoutes(SimpleRoutes.AllNodes).build())
                      .build();
              if (ALWAYS_FLUSH_ON_WRITE) {
                channel.writeAndFlush(request.toByteArray());
                return commandId.getRight();
              }
              channel.write(request.toByteArray());
              return autoFlushFutureWrapper(commandId.getRight());
            })
        .thenCompose(f -> f);
  }

  private <T> CompletableFuture<T> autoFlushFutureWrapper(Future<T> future) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return future.get(AUTO_FLUSH_RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
          } catch (TimeoutException e) {
            // System.out.println("-- auto flush - timeout");
            channel.flush();
            nonFlushedBytesCounter.set(0);
            nonFlushedWritesCounter.set(0);
          }
          try {
            return future.get();
          } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
          }
        });
  }

  public Future<Response> asyncSet(String key, String value) {
    // System.out.printf("== set(%s, %s), callback %d%n", key, value, callbackId);
    return submitNewCommand(RequestType.SetString, List.of(key, value));
  }

  public Future<String> asyncGet(String key) {
    // System.out.printf("== get(%s), callback %d%n", key, callbackId);
    return submitNewCommand(RequestType.GetString, List.of(key))
        .thenApply(
            response ->
                response.getRespPointer() != 0
                    ? BabushkaCoreNativeDefinitions.valueFromPointer(response.getRespPointer())
                        .toString()
                    : null);
  }

  public <T> T waitForResult(Future<T> future) {
    return waitForResult(future, DEFAULT_TIMEOUT_MILLISECONDS);
  }

  public <T> T waitForResult(Future<T> future, long timeout) {
    try {
      return future.get(timeout, TimeUnit.MILLISECONDS);
    } catch (Exception ignored) {
      return null;
    }
  }
}
