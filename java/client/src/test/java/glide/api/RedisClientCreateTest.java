package glide.api;

import static glide.api.RedisClient.CreateClient;
import static glide.api.RedisClient.buildCommandManager;
import static glide.api.RedisClient.buildConnectionManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.ChannelHandler;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class RedisClientCreateTest {

  private MockedStatic<RedisClient> mockedClient;
  private ChannelHandler channelHandler;
  private ConnectionManager connectionManager;
  private CommandManager commandManager;

  @BeforeEach
  public void init() {
    mockedClient = Mockito.mockStatic(RedisClient.class);

    channelHandler = mock(ChannelHandler.class);
    commandManager = mock(CommandManager.class);
    connectionManager = mock(ConnectionManager.class);

    mockedClient.when(RedisClient::buildChannelHandler).thenReturn(channelHandler);
    mockedClient.when(() -> buildConnectionManager(channelHandler)).thenReturn(connectionManager);
    mockedClient.when(() -> buildCommandManager(channelHandler)).thenReturn(commandManager);
  }

  @AfterEach
  public void teardown() {
    mockedClient.close();
  }

  @Test
  @SneakyThrows
  public void createClient_withConfig_successfullyReturnsRedisClient() {

    // setup
    CompletableFuture<Void> connectToRedisFuture = new CompletableFuture<>();
    connectToRedisFuture.complete(null);
    RedisClientConfiguration config = RedisClientConfiguration.builder().build();

    when(connectionManager.connectToRedis(eq(config))).thenReturn(connectToRedisFuture);
    mockedClient.when(() -> CreateClient(config)).thenCallRealMethod();

    // exercise
    CompletableFuture<RedisClient> result = CreateClient(config);
    RedisClient client = result.get();

    // verify
    assertEquals(connectionManager, client.connectionManager);
    assertEquals(commandManager, client.commandManager);
  }

  @SneakyThrows
  @Test
  public void createClient_errorOnConnectionThrowsExecutionException() {
    // setup
    CompletableFuture<Void> connectToRedisFuture = new CompletableFuture<>();
    ClosingException exception = new ClosingException("disconnected");
    connectToRedisFuture.completeExceptionally(exception);
    RedisClientConfiguration config = RedisClientConfiguration.builder().build();

    when(connectionManager.connectToRedis(eq(config))).thenReturn(connectToRedisFuture);
    mockedClient.when(() -> CreateClient(config)).thenCallRealMethod();

    // exercise
    CompletableFuture<RedisClient> result = CreateClient(config);

    ExecutionException executionException =
        assertThrows(ExecutionException.class, () -> result.get());

    // verify
    assertEquals(exception, executionException.getCause());
  }
}
