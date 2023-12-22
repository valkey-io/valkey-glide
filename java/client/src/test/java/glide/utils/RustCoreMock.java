package glide.utils;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import glide.connectors.resources.Platform;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.SneakyThrows;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

public class RustCoreMock {

  public abstract static class GlideMock {
    /** Return `null` to do not reply. */
    public abstract Response connection(ConnectionRequest request);

    /** Return `null` to do not reply. */
    public abstract Response.Builder redisRequest(RedisRequest request);

    public Response redisRequestWithCallbackId(RedisRequest request) {
      var responseDraft = redisRequest(request);
      return responseDraft == null
          ? null
          : responseDraft.setCallbackIdx(request.getCallbackIdx()).build();
    }

    public static Response.Builder OK() {
      return Response.newBuilder().setConstantResponse(ConstantResponse.OK);
    }
  }

  public abstract static class GlideMockConnectAll extends GlideMock {
    @Override
    public Response connection(ConnectionRequest request) {
      return Response.newBuilder().build();
    }
  }

  /** Thread pool supplied to <em>Netty</em> to perform all async IO. */
  private EventLoopGroup group;

  private Channel channel;

  private String socketPath;

  private static RustCoreMock instance;

  private GlideMock messageProcessor;

  /** Update {@link GlideMock} into a running {@link RustCoreMock}. */
  public static void updateGlideMock(GlideMock newMock) {
    instance.messageProcessor = newMock;
  }

  private final AtomicBoolean failed = new AtomicBoolean(false);

  /** Get and clear failure status. */
  public static boolean failed() {
    return instance.failed.compareAndSet(true, false);
  }

  @SneakyThrows
  private RustCoreMock() {
    socketPath = Files.createTempFile("GlideCoreMock", null).toString();
    channel =
        new ServerBootstrap()
            .group(group = Platform.createNettyThreadPool("GlideCoreMock", Optional.empty()))
            .channel(Platform.getServerUdsNettyChannelType())
            .childHandler(
                new ChannelInitializer<DomainSocketChannel>() {

                  @Override
                  protected void initChannel(DomainSocketChannel ch) throws Exception {
                    ch.pipeline()
                        // https://netty.io/4.1/api/io/netty/handler/codec/protobuf/ProtobufEncoder.html
                        .addLast("frameDecoder", new ProtobufVarint32FrameDecoder())
                        .addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender())
                        .addLast("protobufEncoder", new ProtobufEncoder())
                        .addLast(
                            new ChannelInboundHandlerAdapter() {

                              // This works with only one connected client.
                              // TODO Rework with `channelActive` override.
                              private AtomicBoolean anybodyConnected = new AtomicBoolean(false);

                              @Override
                              public void channelRead(ChannelHandlerContext ctx, Object msg)
                                  throws Exception {
                                var buf = (ByteBuf) msg;
                                var bytes = new byte[buf.readableBytes()];
                                buf.readBytes(bytes);
                                buf.release();
                                Response response = null;
                                if (!anybodyConnected.get()) {
                                  var connection = ConnectionRequest.parseFrom(bytes);
                                  response = messageProcessor.connection(connection);
                                  anybodyConnected.setPlain(true);
                                } else {
                                  var request = RedisRequest.parseFrom(bytes);
                                  response = messageProcessor.redisRequestWithCallbackId(request);
                                }
                                if (response != null) {
                                  ctx.writeAndFlush(response);
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
            .bind(new DomainSocketAddress(socketPath))
            .syncUninterruptibly()
            .channel();
  }

  public static String start(GlideMock messageProcessor) {
    if (instance != null) {
      stop();
    }
    instance = new RustCoreMock();
    instance.messageProcessor = messageProcessor;
    return instance.socketPath;
  }

  public static void stop() {
    instance.channel.close();
    instance.group.shutdownGracefully();
    instance = null;
  }
}
