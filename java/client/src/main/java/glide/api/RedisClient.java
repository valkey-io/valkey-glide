package glide.api;

import static glide.ffi.resolvers.SocketListenerResolver.getSocket;

import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Async (non-blocking) client for Redis in Standalone mode. Use {@link #CreateClient()} to request
 * a client to Redis.
 */
public class RedisClient extends BaseClient {

  /**
   * Request an async (non-blocking) Redis client in Standalone mode to a Redis service on localhost.
   *
   * @return a promise to connect and return a RedisClient
   */
  public static CompletableFuture<RedisClient> CreateClient() {
    return CreateClient(RedisClientConfiguration.builder().build());
  }

  /**
   * Request an async (non-blocking) Redis client in Standalone mode.
   *
   * @param host - host address of the Redis service
   * @param port - port of the Redis service
   * @return a promise to connect and return a RedisClient
   */
  public static CompletableFuture<RedisClient> CreateClient(String host, Integer port) {
    RedisClientConfiguration config =
        RedisClientConfiguration.builder()
            .address(NodeAddress.builder().host(host).port(port).build())
            .build();
    return CreateClient(config);
  }

  /**
   * Request an async (non-blocking) Redis client in Standalone mode.
   *
   * @param config - Redis Client Configuration
   * @return a promise to connect and return a RedisClient
   */
  public static CompletableFuture<RedisClient> CreateClient(RedisClientConfiguration config) {
    CallbackDispatcher callbackDispatcher = new CallbackDispatcher();
    ChannelHandler channelHandler = new ChannelHandler(callbackDispatcher, getSocket());
    var connectionManager = new ConnectionManager(channelHandler);
    var commandManager = new CommandManager(channelHandler);
    return CreateClient(config, connectionManager, commandManager);
  }

  protected static CompletableFuture<RedisClient> CreateClient(
      RedisClientConfiguration config,
      ConnectionManager connectionManager,
      CommandManager commandManager) {
    // TODO: Support exception throwing, including interrupted exceptions
    return connectionManager
        .connectToRedis(config)
        .thenApplyAsync(ignore -> new RedisClient(connectionManager, commandManager));
  }

  protected RedisClient(ConnectionManager connectionManager, CommandManager commandManager) {
    super(connectionManager, commandManager);
  }

  /**
   * Closes this resource, relinquishing any underlying resources. This method is invoked
   * automatically on objects managed by the try-with-resources statement.
   *
   * <p>see: <a
   * href="https://docs.oracle.com/javase/8/docs/api/java/lang/AutoCloseable.html#close--">AutoCloseable::close()</a>
   */
  @Override
  public void close() throws ExecutionException {
    try {
      connectionManager.closeConnection().get();
    } catch (InterruptedException interruptedException) {
      // AutoCloseable functions are strongly advised to avoid throwing InterruptedExceptions
      // TODO: marking resources as closed:
      // https://github.com/orgs/Bit-Quill/projects/4/views/6?pane=issue&itemId=48063887
      throw new RuntimeException(interruptedException);
    }
  }
}
