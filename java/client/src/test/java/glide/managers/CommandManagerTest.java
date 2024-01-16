package glide.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import glide.api.commands.BaseCommandResponseResolver;
import glide.api.commands.Command;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.TimeoutException;
import glide.connectors.handlers.ChannelHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

  @Test
  public void submitNewCommand_throwConnectionException() {

    // setup
    int disconnectedType = 3;
    String errorMsg = "Disconnected";

    Response respPointerResponse =
        Response.newBuilder()
            .setRequestError(
                RequestError.newBuilder()
                    .setTypeValue(disconnectedType)
                    .setMessage(errorMsg)
                    .build())
            .build();

    CompletableFuture<Response> future = new CompletableFuture<>();
    future.complete(respPointerResponse);
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
    assertTrue(e.getCause() instanceof ConnectionException);
    assertEquals(errorMsg, e.getCause().getMessage());
  }

  @Test
  public void submitNewCommand_throwTimeoutException() {

    // setup
    int timeoutType = 2;
    String errorMsg = "Timeout";

    Response timeoutErrorResponse =
        Response.newBuilder()
            .setRequestError(
                RequestError.newBuilder().setTypeValue(timeoutType).setMessage(errorMsg).build())
            .build();

    CompletableFuture<Response> future = new CompletableFuture<>();
    future.complete(timeoutErrorResponse);
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
    assertTrue(e.getCause() instanceof TimeoutException);
    assertEquals(errorMsg, e.getCause().getMessage());
  }

  @Test
  public void submitNewCommand_throwExecAbortException() {
    // setup
    int execAbortType = 1;
    String errorMsg = "ExecAbort";

    Response execAbortErrorResponse =
        Response.newBuilder()
            .setRequestError(
                RequestError.newBuilder().setTypeValue(execAbortType).setMessage(errorMsg).build())
            .build();

    CompletableFuture<Response> future = new CompletableFuture<>();
    future.complete(execAbortErrorResponse);
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
    assertTrue(e.getCause() instanceof ExecAbortException);
    assertEquals(errorMsg, e.getCause().getMessage());
  }

  @Test
  public void submitNewCommand_handledUnspecifiedError() {
    // setup
    int unspecifiedType = 0;
    String errorMsg = "Unspecified";

    Response unspecifiedErrorResponse =
        Response.newBuilder()
            .setRequestError(
                RequestError.newBuilder()
                    .setTypeValue(unspecifiedType)
                    .setMessage(errorMsg)
                    .build())
            .build();

    CompletableFuture<Response> future = new CompletableFuture<>();
    future.complete(unspecifiedErrorResponse);
    when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

    // exercise
    ExecutionException executionException =
        assertThrows(
            ExecutionException.class,
            () -> {
              CompletableFuture result =
                  service.submitNewCommand(
                      command, new BaseCommandResponseResolver((ptr) -> new Object()));
              result.get();
            });

    // verify
    assertTrue(executionException.getCause() instanceof RedisException);
    assertEquals(errorMsg, executionException.getCause().getMessage());
  }
}
