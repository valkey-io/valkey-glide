package glide.api;

import static glide.api.models.commands.SetOptions.CONDITIONAL_SET_ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.CONDITIONAL_SET_ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.api.models.commands.SetOptions.TIME_TO_LIVE_KEEP_EXISTING;
import static glide.api.models.commands.SetOptions.TIME_TO_LIVE_UNIX_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import glide.api.commands.Command;
import glide.api.models.commands.SetOptions;
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
    when(testResponse.get()).thenThrow(new InterruptedException());
    when(commandManager.submitNewCommand(any(), any())).thenReturn(testResponse);

    // exercise
    InterruptedException exception =
        assertThrows(
            InterruptedException.class,
            () -> {
              CompletableFuture<String> response = service.get(key);
              response.get();
            });

    // verify

    // teardown
  }

  @Test
  public void get_success() throws ExecutionException, InterruptedException {
    // setup
    // TODO: randomize keys
    String key = "testKey";
    String value = "testValue";
    Command cmd =
        Command.builder()
            .requestType(Command.RequestType.GET_STRING)
            .arguments(new String[] {key})
            .build();
    CompletableFuture<String> testResponse = mock(CompletableFuture.class);
    when(testResponse.get()).thenReturn(value);
    when(commandManager.<String>submitNewCommand(any(), any())).thenReturn(testResponse);

    // exercise
    CompletableFuture<String> response = service.get(key);
    String payload = response.get();

    // verify
    assertEquals(testResponse, response);
    assertEquals(value, payload);

    // teardown
  }

  @Test
  public void set_success() throws ExecutionException, InterruptedException {
    // setup
    // TODO: randomize keys
    String key = "testKey";
    String value = "testValue";
    Command cmd =
        Command.builder()
            .requestType(Command.RequestType.SET_STRING)
            .arguments(new String[] {key, value})
            .build();
    CompletableFuture<Void> testResponse = mock(CompletableFuture.class);
    when(testResponse.get()).thenReturn(null);
    when(commandManager.<Void>submitNewCommand(any(), any())).thenReturn(testResponse);

    // exercise
    CompletableFuture<Void> response = service.set(key, value);
    Object nullResponse = response.get();

    // verify
    assertEquals(testResponse, response);
    assertNull(nullResponse);

    // teardown
  }

  @Test
  public void set_withOptionsOnlyIfExists_success()
      throws ExecutionException, InterruptedException {
    // setup
    String key = "testKey";
    String value = "testValue";
    SetOptions setOptions =
        SetOptions.builder()
            .conditionalSet(SetOptions.ConditionalSet.ONLY_IF_EXISTS)
            .returnOldValue(false)
            .expiry(
                SetOptions.TimeToLive.builder()
                    .type(SetOptions.TimeToLiveType.KEEP_EXISTING)
                    .build())
            .build();
    Command cmd =
        Command.builder()
            .requestType(Command.RequestType.SET_STRING)
            .arguments(
                new String[] {
                  key, value, CONDITIONAL_SET_ONLY_IF_EXISTS, TIME_TO_LIVE_KEEP_EXISTING
                })
            .build();
    CompletableFuture<String> testResponse = mock(CompletableFuture.class);
    when(testResponse.get()).thenReturn(null);
    when(commandManager.<String>submitNewCommand(eq(cmd), any())).thenReturn(testResponse);

    // exercise
    CompletableFuture<String> response = service.set(key, value, setOptions);

    // verify
    assertNotNull(response);
    assertNull(response.get());

    // teardown
  }

  @Test
  public void set_withOptionsOnlyIfDoesNotExist_success()
      throws ExecutionException, InterruptedException {
    // setup
    String key = "testKey";
    String value = "testValue";
    SetOptions setOptions =
        SetOptions.builder()
            .conditionalSet(SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST)
            .returnOldValue(true)
            .expiry(
                SetOptions.TimeToLive.builder()
                    .type(SetOptions.TimeToLiveType.UNIX_SECONDS)
                    .count(60)
                    .build())
            .build();
    Command cmd =
        Command.builder()
            .requestType(Command.RequestType.SET_STRING)
            .arguments(
                new String[] {
                  key,
                  value,
                  CONDITIONAL_SET_ONLY_IF_DOES_NOT_EXIST,
                  RETURN_OLD_VALUE,
                  TIME_TO_LIVE_UNIX_SECONDS + " 60"
                })
            .build();
    CompletableFuture<String> testResponse = mock(CompletableFuture.class);
    when(testResponse.get()).thenReturn(value);
    when(commandManager.<String>submitNewCommand(eq(cmd), any())).thenReturn(testResponse);

    // exercise
    CompletableFuture<String> response = service.set(key, value, setOptions);

    // verify
    assertNotNull(response);
    assertEquals(value, response.get());

    // teardown
  }
}
