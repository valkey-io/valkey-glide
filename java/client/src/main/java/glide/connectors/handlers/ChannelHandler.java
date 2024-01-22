package glide.connectors.handlers;

import connection_request.ConnectionRequestOuterClass.ConnectionRequest;
import glide.connectors.resources.Platform;
import glide.connectors.resources.ThreadPoolAllocator;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.channel.unix.DomainSocketChannel;
import io.netty.channel.unix.UnixChannel;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import redis_request.RedisRequestOuterClass.RedisRequest;
import response.ResponseOuterClass.Response;

/**
 * Class responsible for handling calls to/from a netty.io {@link Channel}. Uses a {@link
 * CallbackDispatcher} to record callbacks of every request sent.
 */
public class ChannelHandler {

    private static final String THREAD_POOL_NAME = "glide-channel";

    private final Channel channel;
    private final CallbackDispatcher callbackDispatcher;

    /** Open a new channel for a new client. */
    public ChannelHandler(CallbackDispatcher callbackDispatcher, String socketPath) {
        this(
                ThreadPoolAllocator.createOrGetNettyThreadPool(THREAD_POOL_NAME, Optional.empty()),
                Platform.getClientUdsNettyChannelType(),
                new ProtobufSocketChannelInitializer(callbackDispatcher),
                new DomainSocketAddress(socketPath),
                callbackDispatcher);
    }

    /**
     * Open a new channel for a new client and running it on the provided EventLoopGroup
     *
     * @param eventLoopGroup - ELG to run handler on
     * @param domainSocketChannelClass - socket channel class for Handler
     * @param channelInitializer - UnixChannel initializer
     * @param domainSocketAddress - address to connect
     * @param callbackDispatcher - dispatcher to handle callbacks
     */
    public ChannelHandler(
            EventLoopGroup eventLoopGroup,
            Class<? extends DomainSocketChannel> domainSocketChannelClass,
            ChannelInitializer<UnixChannel> channelInitializer,
            DomainSocketAddress domainSocketAddress,
            CallbackDispatcher callbackDispatcher) {
        channel =
                new Bootstrap()
                        .group(eventLoopGroup)
                        .channel(domainSocketChannelClass)
                        .handler(channelInitializer)
                        .connect(domainSocketAddress)
                        // TODO call here .sync() if needed or remove this comment
                        .channel();
        this.callbackDispatcher = callbackDispatcher;
    }

    /**
     * Complete a protobuf message and write it to the channel (to UDS).
     *
     * @param request Incomplete request, function completes it by setting callback ID
     * @param flush True to flush immediately
     * @return A response promise
     */
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

    /**
     * Write a protobuf message to the channel (to UDS).
     *
     * @param request A connection request
     * @return A connection promise
     */
    public CompletableFuture<Response> connect(ConnectionRequest request) {
        channel.writeAndFlush(request);
        return callbackDispatcher.registerConnection();
    }

    /** Closes the UDS connection and frees corresponding resources. */
    public ChannelFuture close() {
        callbackDispatcher.shutdownGracefully();
        return channel.close();
    }
}
