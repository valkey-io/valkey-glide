package glide.api;

import static glide.ffi.resolvers.SocketListenerResolver.getSocket;

import glide.api.models.configuration.RedisClientConfiguration;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;

/**
 * Async (non-blocking) client for Redis in Standalone mode. Use {@link
 * #CreateClient(RedisClientConfiguration)} to request a client to Redis.
 */
public class RedisClient extends BaseClient {

    /**
     * Request an async (non-blocking) Redis client in Standalone mode.
     *
     * @param config - Redis Client Configuration
     * @return a Future to connect and return a RedisClient
     */
    public static CompletableFuture<RedisClient> CreateClient(RedisClientConfiguration config) {
        ChannelHandler channelHandler = buildChannelHandler();
        ConnectionManager connectionManager = buildConnectionManager(channelHandler);
        CommandManager commandManager = buildCommandManager(channelHandler);
        // TODO: Support exception throwing, including interrupted exceptions
        return connectionManager
                .connectToRedis(config)
                .thenApply(ignore -> new RedisClient(connectionManager, commandManager));
    }

    protected static ChannelHandler buildChannelHandler() {
        CallbackDispatcher callbackDispatcher = new CallbackDispatcher();
        return new ChannelHandler(callbackDispatcher, getSocket());
    }

    protected static ConnectionManager buildConnectionManager(ChannelHandler channelHandler) {
        return new ConnectionManager(channelHandler);
    }

    protected static CommandManager buildCommandManager(ChannelHandler channelHandler) {
        return new CommandManager(channelHandler);
    }

    protected RedisClient(ConnectionManager connectionManager, CommandManager commandManager) {
        super(connectionManager, commandManager);
    }
}
