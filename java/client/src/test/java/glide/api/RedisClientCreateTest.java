package glide.api;

import static glide.api.RedisClient.CreateClient;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import glide.api.models.configuration.RedisClientConfiguration;
import glide.ffi.resolvers.SocketListenerResolver;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class RedisClientCreateTest {

  @Test
  @SneakyThrows
  public void createClient_withConfig_successfullyReturnsRedisClient() {
    try (MockedStatic<RedisClient> mockedClient = Mockito.mockStatic(RedisClient.class);
        MockedStatic<SocketListenerResolver> mockedSocketListener =
            Mockito.mockStatic(SocketListenerResolver.class)) {

      // setup
      CompletableFuture<RedisClient> testFuture = new CompletableFuture<>();
      RedisClientConfiguration config = RedisClientConfiguration.builder().build();

      mockedSocketListener.when(SocketListenerResolver::getSocket).thenReturn("test_socket");
      mockedClient.when(() -> CreateClient(any(), any(), any())).thenReturn(testFuture);

      // method under test
      mockedClient.when(() -> CreateClient(config)).thenCallRealMethod();

      // exercise
      CompletableFuture<RedisClient> result = CreateClient(config);

      // verify
      mockedClient.verify(() -> CreateClient(any(), any(), any()));
      assertEquals(testFuture, result);
    }
  }

  @Test
  @SneakyThrows
  public void createClient_noArgs_successfullyReturnsRedisClient() {
    try (MockedStatic<RedisClient> mockedClient =
            Mockito.mockStatic(RedisClient.class, withSettings().verboseLogging());
        MockedStatic<SocketListenerResolver> mockedSocketListener =
            Mockito.mockStatic(SocketListenerResolver.class, withSettings().verboseLogging())) {

      // setup
      mockedSocketListener.when(SocketListenerResolver::getSocket).thenReturn("test_socket");
      CompletableFuture<RedisClient> testFuture = new CompletableFuture<>();
      mockedClient.when(() -> CreateClient(any(), any(), any())).thenReturn(testFuture);

      // method under test
      mockedClient.when(RedisClient::CreateClient).thenCallRealMethod();
      mockedClient.when(() -> CreateClient(any())).thenCallRealMethod();

      // exercise
      CompletableFuture<RedisClient> result = CreateClient();

      // verify
      assertEquals(testFuture, result);
    }
  }

  @Test
  @SneakyThrows
  public void createClient_withHostPort_successfullyReturnsRedisClient() {
    try (MockedStatic<RedisClient> mockedClient =
            Mockito.mockStatic(RedisClient.class, withSettings().verboseLogging());
        MockedStatic<SocketListenerResolver> mockedSocketListener =
            Mockito.mockStatic(SocketListenerResolver.class, withSettings().verboseLogging())) {

      // setup
      String host = "testhost";
      int port = 999;

      mockedSocketListener.when(SocketListenerResolver::getSocket).thenReturn("test_socket");
      CompletableFuture<RedisClient> testFuture = new CompletableFuture<>();
      mockedClient.when(() -> CreateClient(any())).thenReturn(testFuture);

      // method under test
      mockedClient.when(RedisClient::CreateClient).thenCallRealMethod();
      mockedClient.when(() -> CreateClient(host, port)).thenCallRealMethod();

      // exercise
      CompletableFuture<RedisClient> result = CreateClient();

      // verify
      assertEquals(testFuture, result);
    }
  }

  @SneakyThrows
  @Test
  public void createClient_successfulConnectionReturnsRedisClient() {

    // setup
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    CommandManager commandManager = mock(CommandManager.class);
    RedisClientConfiguration configuration = RedisClientConfiguration.builder().build();
    CompletableFuture<Void> connectionFuture = new CompletableFuture<>();
    connectionFuture.complete(null);
    when(connectionManager.connectToRedis(eq(configuration))).thenReturn(connectionFuture);

    // exercise
    CompletableFuture<RedisClient> response =
        RedisClient.CreateClient(configuration, connectionManager, commandManager);
    RedisClient client = response.get();

    // verify
    assertEquals(connectionManager, client.connectionManager);
    assertEquals(commandManager, client.commandManager);

    // teardown
  }

  @SneakyThrows
  @Test
  public void createClient_errorOnConnectionThrowsExecutionException() {

    // setup
    ConnectionManager connectionManager = mock(ConnectionManager.class);
    CommandManager commandManager = mock(CommandManager.class);
    RedisClientConfiguration configuration = RedisClientConfiguration.builder().build();
    CompletableFuture<Void> connectionFuture = new CompletableFuture<>();
    RuntimeException exception = new RuntimeException("disconnected");
    connectionFuture.completeExceptionally(exception);
    when(connectionManager.connectToRedis(eq(configuration))).thenReturn(connectionFuture);

    // exercise
    CompletableFuture<RedisClient> response =
        RedisClient.CreateClient(configuration, connectionManager, commandManager);

    ExecutionException executionException =
        assertThrows(ExecutionException.class, () -> response.get());

    // verify
    assertEquals(exception, executionException.getCause());

    // teardown
  }
}
