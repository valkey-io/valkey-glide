package glide.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import glide.connectors.handlers.ChannelHandler;
import glide.managers.models.Command;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import response.ResponseOuterClass.Response;

public class CommandManagerTest {

    ChannelHandler channelHandler;

    CommandManager service;

    Command command;

    @BeforeEach
    void init() {
        command = Command.builder().requestType(Command.RequestType.CUSTOM_COMMAND).build();

        channelHandler = mock(ChannelHandler.class);
        service = new CommandManager(channelHandler);
    }

    @Test
    @SneakyThrows
    public void submitNewCommand_return_Object_result() {

        // setup
        long pointer = -1;
        Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();
        Object respObject = mock(Object.class);

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        // exercise
        CompletableFuture<Object> result =
                service.submitNewCommand(
                        command, new BaseCommandResponseResolver((ptr) -> ptr == pointer ? respObject : null));
        Object respPointer = result.get();

        // verify
        assertEquals(respObject, respPointer);
    }

    @Test
    @SneakyThrows
    public void submitNewCommand_return_Null_result() {
        // setup
        Response respPointerResponse = Response.newBuilder().build();
        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        // exercise
        CompletableFuture<Object> result =
                service.submitNewCommand(
                        command, new BaseCommandResponseResolver((p) -> new RuntimeException("")));
        Object respPointer = result.get();

        // verify
        assertNull(respPointer);
    }

    @Test
    @SneakyThrows
    public void submitNewCommand_return_String_result() {

        // setup
        long pointer = 123;
        String testString = "TEST STRING";

        Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        // exercise
        CompletableFuture<Object> result =
                service.submitNewCommand(
                        command, new BaseCommandResponseResolver((p) -> p == pointer ? testString : null));
        Object respPointer = result.get();

        // verify
        assertTrue(respPointer instanceof String);
        assertEquals(testString, respPointer);
    }
}
