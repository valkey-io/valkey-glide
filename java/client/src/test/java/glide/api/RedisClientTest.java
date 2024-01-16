package glide.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RedisClientTest {

  RedisClient service;

  ConnectionManager connectionManager;

  CommandManager commandManager;

  @BeforeEach
  public void setUp() {
    connectionManager = mock(ConnectionManager.class);
    commandManager = mock(CommandManager.class);
    service = new RedisClient(connectionManager, commandManager);
  }

  @Test
  public void customCommand_success() throws ExecutionException, InterruptedException {
    // setup
    String key = "testKey";
    Object value = "testValue";
    String cmd = "GETSTRING";
    CompletableFuture<Object> testResponse = mock(CompletableFuture.class);
    when(testResponse.get()).thenReturn(value);
    when(commandManager.submitNewCommand(any(), any())).thenReturn(testResponse);

    // exercise
    CompletableFuture<Object> response = service.customCommand(new String[] {cmd, key});
    String payload = (String) response.get();

    // verify
    assertEquals(testResponse, response);
    assertEquals(value, payload);

    // teardown
  }

  @Test
  public void customCommand_interruptedException() throws ExecutionException, InterruptedException {
    // setup
    String key = "testKey";
    Object value = "testValue";
    String cmd = "GETSTRING";
    CompletableFuture<Object> testResponse = mock(CompletableFuture.class);
    InterruptedException interruptedException = new InterruptedException();
    when(testResponse.get()).thenThrow(interruptedException);
    when(commandManager.submitNewCommand(any(), any())).thenReturn(testResponse);

    // exercise
    InterruptedException exception =
        assertThrows(
            InterruptedException.class,
            () -> {
              CompletableFuture<Object> response = service.customCommand(new String[] {cmd, key});
              response.get();
            });

    // verify
    assertEquals(interruptedException, exception);
  }
}
