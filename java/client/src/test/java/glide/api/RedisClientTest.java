/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
import static glide.api.models.commands.SetOptions.ConditionalSet.ONLY_IF_EXISTS;
import static glide.api.models.commands.SetOptions.RETURN_OLD_VALUE;
import static glide.api.models.commands.SetOptions.TimeToLiveType.KEEP_EXISTING;
import static glide.api.models.commands.SetOptions.TimeToLiveType.UNIX_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.GetString;
import static redis_request.RedisRequestOuterClass.RequestType.Info;
import static redis_request.RedisRequestOuterClass.RequestType.Ping;
import static redis_request.RedisRequestOuterClass.RequestType.SetString;

import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.SetOptions;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
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

    @SneakyThrows
    @Test
    public void customCommand_returns_success() {
        // setup
        String key = "testKey";
        Object value = "testValue";
        String cmd = "GETSTRING";
        String[] arguments = new String[] {cmd, key};
        CompletableFuture<Object> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(CustomCommand), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Object> response = service.customCommand(arguments);
        String payload = (String) response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void customCommand_throws_InterruptedException() {
        // setup
        String key = "testKey";
        Object value = "testValue";
        String cmd = "GETSTRING";
        String[] arguments = new String[] {cmd, key};
        CompletableFuture<Object> testResponse = mock(CompletableFuture.class);
        InterruptedException interruptedException = new InterruptedException();
        when(testResponse.get()).thenThrow(interruptedException);

        // match on protobuf request
        when(commandManager.submitNewCommand(eq(CustomCommand), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        InterruptedException exception =
                assertThrows(
                        InterruptedException.class,
                        () -> {
                            CompletableFuture<Object> response = service.customCommand(arguments);
                            response.get();
                        });

        // verify
        assertEquals(interruptedException, exception);
    }

    @SneakyThrows
    @Test
    public void ping_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn("PONG");

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals("PONG", payload);
    }

    @SneakyThrows
    @Test
    public void ping_with_message_returns_success() {
        // setup
        String message = "RETURN OF THE PONG";
        String[] arguments = new String[] {message};
        CompletableFuture<String> testResponse = new CompletableFuture();
        testResponse.complete(message);

        // match on protobuf request
        when(commandManager.<String>submitNewCommand(eq(Ping), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.ping(message);
        String pong = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(message, pong);
    }

    @SneakyThrows
    @Test
    public void info_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.info();
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_multiple_InfoOptions_returns_success() {
        // setup
        String[] arguments =
                new String[] {InfoOptions.Section.ALL.toString(), InfoOptions.Section.DEFAULT.toString()};
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        InfoOptions options =
                InfoOptions.builder()
                        .section(InfoOptions.Section.ALL)
                        .section(InfoOptions.Section.DEFAULT)
                        .build();
        CompletableFuture<String> response = service.info(options);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void info_with_empty_InfoOptions_returns_success() {
        // setup
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        String testPayload = "Key: Value";
        when(testResponse.get()).thenReturn(testPayload);
        when(commandManager.<String>submitNewCommand(eq(Info), eq(new String[0]), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.info(InfoOptions.builder().build());
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(testPayload, payload);
    }

    @SneakyThrows
    @Test
    public void get_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
        when(commandManager.<String>submitNewCommand(eq(GetString), eq(new String[] {key}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.get(key);
        String payload = response.get();

        // verify
        assertEquals(testResponse, response);
        assertEquals(value, payload);
    }

    @SneakyThrows
    @Test
    public void set_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        CompletableFuture<Void> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(null);
        when(commandManager.<Void>submitNewCommand(eq(SetString), eq(new String[] {key, value}), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<Void> response = service.set(key, value);
        Object nullResponse = response.get();

        // verify
        assertEquals(testResponse, response);
        assertNull(nullResponse);
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfExists_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        SetOptions setOptions =
                SetOptions.builder()
                        .conditionalSet(ONLY_IF_EXISTS)
                        .returnOldValue(false)
                        .expiry(
                                SetOptions.TimeToLive.builder()
                                        .type(SetOptions.TimeToLiveType.KEEP_EXISTING)
                                        .build())
                        .build();
        String[] arguments =
                new String[] {key, value, ONLY_IF_EXISTS.getRedisApi(), KEEP_EXISTING.getRedisApi()};

        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(null);
        when(commandManager.<String>submitNewCommand(eq(SetString), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value, setOptions);

        // verify
        assertEquals(testResponse, response);
        assertNull(response.get());
    }

    @SneakyThrows
    @Test
    public void set_with_SetOptions_OnlyIfDoesNotExist_returns_success() {
        // setup
        String key = "testKey";
        String value = "testValue";
        SetOptions setOptions =
                SetOptions.builder()
                        .conditionalSet(ONLY_IF_DOES_NOT_EXIST)
                        .returnOldValue(true)
                        .expiry(SetOptions.TimeToLive.builder().type(UNIX_SECONDS).count(60).build())
                        .build();
        String[] arguments =
                new String[] {
                    key,
                    value,
                    ONLY_IF_DOES_NOT_EXIST.getRedisApi(),
                    RETURN_OLD_VALUE,
                    UNIX_SECONDS.getRedisApi(),
                    "60"
                };
        CompletableFuture<String> testResponse = mock(CompletableFuture.class);
        when(testResponse.get()).thenReturn(value);
        when(commandManager.<String>submitNewCommand(eq(SetString), eq(arguments), any()))
                .thenReturn(testResponse);

        // exercise
        CompletableFuture<String> response = service.set(key, value, setOptions);

        // verify
        assertNotNull(response);
        assertEquals(value, response.get());
    }
}
