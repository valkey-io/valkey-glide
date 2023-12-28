package glide.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import glide.api.commands.BaseCommandResponseResolver;
import glide.api.commands.Command;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RedisException;
import glide.api.models.exceptions.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import response.ResponseOuterClass.RequestError;
import response.ResponseOuterClass.Response;

public class CommandManagerTest {

  CommandManager service;

  // ignored for now
  Command command;

  @Test
  public void submitNewCommand_returnObjectResult()
      throws ExecutionException, InterruptedException {

    CompletableFuture<Response> channel = new CompletableFuture<>();
    CommandManager service = new CommandManager(channel);

    long pointer = -1;
    Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();
    Object respObject = mock(Object.class);

    CompletableFuture result =
        service.submitNewCommand(
            command, new BaseCommandResponseResolver((ptr) -> ptr == pointer ? respObject : null));
    channel.complete(respPointerResponse);
    Object respPointer = result.get();

    assertEquals(respObject, respPointer);
  }

  @Test
  public void submitNewCommand_returnNullResult() throws ExecutionException, InterruptedException {

    CompletableFuture<Response> channel = new CompletableFuture<>();
    CommandManager service = new CommandManager(channel);

    Response respPointerResponse = Response.newBuilder().build();

    CompletableFuture result =
        service.submitNewCommand(
            command, new BaseCommandResponseResolver((p) -> new RuntimeException("")));
    channel.complete(respPointerResponse);
    Object respPointer = result.get();

    assertNull(respPointer);
  }

  @Test
  public void submitNewCommand_returnStringResult()
      throws ExecutionException, InterruptedException {

    long pointer = 123;
    String testString = "TEST STRING";

    CompletableFuture<Response> channel = new CompletableFuture<>();
    CommandManager service = new CommandManager(channel);

    Response respPointerResponse = Response.newBuilder().setRespPointer(pointer).build();

    CompletableFuture result =
        service.submitNewCommand(
            command, new BaseCommandResponseResolver((p) -> p == pointer ? testString : null));
    channel.complete(respPointerResponse);
    Object respPointer = result.get();

    assertTrue(respPointer instanceof String);
    assertEquals(testString, respPointer);
  }

  @Test
  public void submitNewCommand_throwClosingException() {

    String errorMsg = "Closing";

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> {
              CompletableFuture<Response> channel = new CompletableFuture<>();
              CommandManager service = new CommandManager(channel);

              Response closingErrorResponse =
                  Response.newBuilder().setClosingError(errorMsg).build();

              CompletableFuture result =
                  service.submitNewCommand(
                      command, new BaseCommandResponseResolver((ptr) -> new Object()));
              channel.complete(closingErrorResponse);
              result.get();
            });

    assertTrue(e.getCause() instanceof ClosingException);
    assertEquals(errorMsg, e.getCause().getMessage());
  }

  @Test
  public void submitNewCommand_throwConnectionException() {

    int disconnectedType = 3;
    String errorMsg = "Disconnected";

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> {
              CompletableFuture<Response> channel = new CompletableFuture<>();
              CommandManager service = new CommandManager(channel);

              Response respPointerResponse =
                  Response.newBuilder()
                      .setRequestError(
                          RequestError.newBuilder()
                              .setTypeValue(disconnectedType)
                              .setMessage(errorMsg)
                              .build())
                      .build();

              CompletableFuture result =
                  service.submitNewCommand(
                      command, new BaseCommandResponseResolver((ptr) -> new Object()));
              channel.complete(respPointerResponse);
              result.get();
            });

    assertTrue(e.getCause() instanceof ConnectionException);
    assertEquals(errorMsg, e.getCause().getMessage());
  }

  @Test
  public void submitNewCommand_throwTimeoutException() {

    int timeoutType = 2;
    String errorMsg = "Timeout";

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> {
              CompletableFuture<Response> channel = new CompletableFuture<>();
              CommandManager service = new CommandManager(channel);

              Response timeoutErrorResponse =
                  Response.newBuilder()
                      .setRequestError(
                          RequestError.newBuilder()
                              .setTypeValue(timeoutType)
                              .setMessage(errorMsg)
                              .build())
                      .build();

              CompletableFuture result =
                  service.submitNewCommand(
                      command, new BaseCommandResponseResolver((ptr) -> new Object()));
              channel.complete(timeoutErrorResponse);
              result.get();
            });

    assertTrue(e.getCause() instanceof TimeoutException);
    assertEquals(errorMsg, e.getCause().getMessage());
  }

  @Test
  public void submitNewCommand_throwExecAbortException() {

    int execAbortType = 1;
    String errorMsg = "ExecAbort";

    ExecutionException e =
        assertThrows(
            ExecutionException.class,
            () -> {
              CompletableFuture<Response> channel = new CompletableFuture<>();
              CommandManager service = new CommandManager(channel);

              Response execAbortErrorResponse =
                  Response.newBuilder()
                      .setRequestError(
                          RequestError.newBuilder()
                              .setTypeValue(execAbortType)
                              .setMessage(errorMsg)
                              .build())
                      .build();

              CompletableFuture result =
                  service.submitNewCommand(
                      command, new BaseCommandResponseResolver((ptr) -> new Object()));
              channel.complete(execAbortErrorResponse);
              result.get();
            });

    assertTrue(e.getCause() instanceof ExecAbortException);
    assertEquals(errorMsg, e.getCause().getMessage());
  }

  @Test
  public void submitNewCommand_handledUnspecifiedError() {
    int unspecifiedType = 0;
    String errorMsg = "Unspecified";

    ExecutionException executionException =
        assertThrows(
            ExecutionException.class,
            () -> {
              CompletableFuture<Response> channel = new CompletableFuture<>();
              CommandManager service = new CommandManager(channel);

              Response unspecifiedErrorResponse =
                  Response.newBuilder()
                      .setRequestError(
                          RequestError.newBuilder()
                              .setTypeValue(unspecifiedType)
                              .setMessage(errorMsg)
                              .build())
                      .build();

              CompletableFuture result =
                  service.submitNewCommand(
                      command, new BaseCommandResponseResolver((ptr) -> new Object()));
              channel.complete(unspecifiedErrorResponse);
              result.get();
            });

    assertTrue(executionException.getCause() instanceof RedisException);
    assertEquals(errorMsg, executionException.getCause().getMessage());
  }
}
