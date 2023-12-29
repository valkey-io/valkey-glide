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
import java.util.concurrent.atomic.AtomicBoolean;

/** Factory class for creating Glide/Redis-client connections */
public class RedisClient extends BaseClient {

  public static CompletableFuture<RedisClient> CreateClient() {
    RedisClientConfiguration config =
        RedisClientConfiguration.builder()
            .address(NodeAddress.builder().build())
            .useTLS(false)
            .build();

    return CreateClient(config);
  }

  public static CompletableFuture<RedisClient> CreateClient(String host, Integer port) {
    RedisClientConfiguration config =
        RedisClientConfiguration.builder()
            .address(NodeAddress.builder().host(host).port(port).build())
            .useTLS(false)
            .build();

    return CreateClient(config);
  }

  /**
   * Async (non-blocking) connection to Redis.
   *
   * @param config - Redis Client Configuration
   * @return a promise to connect and return a RedisClient
   */
  public static CompletableFuture<RedisClient> CreateClient(RedisClientConfiguration config) {
    AtomicBoolean connectionStatus = new AtomicBoolean(false);

    CallbackDispatcher callbackDispatcher = new CallbackDispatcher();
    ChannelHandler channelHandler = new ChannelHandler(callbackDispatcher, getSocket());
    var connectionManager = new ConnectionManager();
    var commandManager = new CommandManager(new CompletableFuture<>());
    // TODO: send request with configuration to connection Manager as part of a follow-up PR
    return CreateClient(config, connectionManager, commandManager);
  }

  private static CompletableFuture<RedisClient> CreateClient(
      RedisClientConfiguration config,
      ConnectionManager connectionManager,
      CommandManager commandManager) {
    return connectionManager
        .connectToRedis(
            config.getAddresses().get(0).getHost(),
            config.getAddresses().get(0).getPort(),
            config.isUseTLS(),
            false)
        .thenApplyAsync(ignore -> new RedisClient(connectionManager, commandManager));
  }

  protected RedisClient(ConnectionManager connectionManager, CommandManager commandManager) {
    super(connectionManager, commandManager);
  }

  /**
   * Closes this resource, relinquishing any underlying resources. This method is invoked
   * automatically on objects managed by the try-with-resources statement. see: <a
   * href="https://docs.oracle.com/javase/8/docs/api/java/lang/AutoCloseable.html#close--">AutoCloseable::close()</a>
   */
  @Override
  public void close() throws ExecutionException {
    try {
      connectionManager
          .closeConnection()
          .thenComposeAsync(ignore -> commandManager.closeConnection())
          .thenApplyAsync(ignore -> this)
          .get();
    } catch (InterruptedException interruptedException) {
      // AutoCloseable functions are strongly advised to avoid throwing InterruptedExceptions
      // TODO: marking resources as closed:
      // https://github.com/orgs/Bit-Quill/projects/4/views/6?pane=issue&itemId=48063887
      throw new RuntimeException(interruptedException);
    }
  }
}
