/** Copyright GLIDE-for-redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static response.ResponseOuterClass.RequestErrorType.UNRECOGNIZED;
import static response.ResponseOuterClass.RequestErrorType.Unspecified;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import glide.connectors.handlers.ChannelHandler;
import glide.managers.models.Command;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import response.ResponseOuterClass;
import response.ResponseOuterClass.RequestError;
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
    public void submitNewCommand_returnObjectResult()
            throws ExecutionException, InterruptedException {

        // setup
        long pointer = -1;
        Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();
        Object respObject = mock(Object.class);

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        // exercise
        CompletableFuture result =
                service.submitNewCommand(
                        command, new BaseCommandResponseResolver((ptr) -> ptr == pointer ? respObject : null));
        Object respPointer = result.get();

        // verify
        assertEquals(respObject, respPointer);
    }

    @Test
    public void submitNewCommand_returnNullResult() throws ExecutionException, InterruptedException {
        // setup
        Response respPointerResponse = Response.newBuilder().build();
        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        // exercise
        CompletableFuture result =
                service.submitNewCommand(
                        command, new BaseCommandResponseResolver((p) -> new RuntimeException("")));
        Object respPointer = result.get();

        // verify
        assertNull(respPointer);
    }

    @Test
    public void submitNewCommand_returnStringResult()
            throws ExecutionException, InterruptedException {

        // setup
        long pointer = 123;
        String testString = "TEST STRING";

        Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(respPointerResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        // exercise
        CompletableFuture result =
                service.submitNewCommand(
                        command, new BaseCommandResponseResolver((p) -> p == pointer ? testString : null));
        Object respPointer = result.get();

        // verify
        assertTrue(respPointer instanceof String);
        assertEquals(testString, respPointer);
    }

    @Test
    public void submitNewCommand_throwClosingException() {

        // setup
        String errorMsg = "Closing";

        Response closingErrorResponse = Response.newBuilder().setClosingError(errorMsg).build();

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(closingErrorResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        // exercise
        ExecutionException e =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            CompletableFuture result =
                                    service.submitNewCommand(
                                            command, new BaseCommandResponseResolver((ptr) -> new Object()));
                            result.get();
                        });

        // verify
        assertTrue(e.getCause() instanceof ClosingException);
        assertEquals(errorMsg, e.getCause().getMessage());
    }

    @ParameterizedTest
    @EnumSource(ResponseOuterClass.RequestErrorType.class) // six numbers
    public void BaseCommandResponseResolver_handles_all_errors(
            ResponseOuterClass.RequestErrorType requestErrorType) {
        if (requestErrorType == UNRECOGNIZED) {
            return;
        }
        Response errorResponse =
                Response.newBuilder()
                        .setRequestError(
                                RequestError.newBuilder()
                                        .setTypeValue(requestErrorType.getNumber())
                                        .setMessage(requestErrorType.toString())
                                        .build())
                        .build();

        CompletableFuture<Response> future = new CompletableFuture<>();
        future.complete(errorResponse);
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        ExecutionException executionException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            CompletableFuture result =
                                    service.submitNewCommand(command, new BaseCommandResponseResolver((ptr) -> null));
                            result.get();
                        });

        // verify
        switch (requestErrorType) {
            case Unspecified:
                // only Unspecified errors return a RequestException
                assertTrue(executionException.getCause() instanceof RequestException);
                break;
            case ExecAbort:
                assertTrue(executionException.getCause() instanceof ExecAbortException);
                break;
            case Timeout:
                assertTrue(executionException.getCause() instanceof TimeoutException);
                break;
            case Disconnect:
                assertTrue(executionException.getCause() instanceof ConnectionException);
                break;
            default:
                fail("Unexpected protobuf error type");
        }
        assertEquals(requestErrorType.toString(), executionException.getCause().getMessage());
    }
}
