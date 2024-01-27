package glide.managers;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static response.ResponseOuterClass.RequestErrorType.UNRECOGNIZED;
import static response.ResponseOuterClass.RequestErrorType.Unspecified;

import glide.api.models.configuration.Route;
import glide.api.models.configuration.Route.RouteType;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import glide.connectors.handlers.ChannelHandler;
import glide.managers.models.Command;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import redis_request.RedisRequestOuterClass.RedisRequest;
import redis_request.RedisRequestOuterClass.SimpleRoutes;
import redis_request.RedisRequestOuterClass.SlotTypes;
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
                        command,
                        Optional.empty(),
                        new BaseCommandResponseResolver((ptr) -> ptr == pointer ? respObject : null));
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
                        command,
                        Optional.empty(),
                        new BaseCommandResponseResolver((p) -> new RuntimeException("")));
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
                        command,
                        Optional.empty(),
                        new BaseCommandResponseResolver((p) -> p == pointer ? testString : null));
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
                                            command,
                                            Optional.empty(),
                                            new BaseCommandResponseResolver((ptr) -> new Object()));
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
                                    service.submitNewCommand(
                                            command, Optional.empty(), new BaseCommandResponseResolver((ptr) -> null));
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

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"ALL_NODES", "ALL_PRIMARIES", "RANDOM"})
    public void prepare_request_with_simple_routes(RouteType routeType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        var protocSimpleRouteToClientSimpleRoute =
                Map.of(
                        SimpleRoutes.Random, RouteType.RANDOM,
                        SimpleRoutes.AllNodes, RouteType.ALL_NODES,
                        SimpleRoutes.AllPrimaries, RouteType.ALL_PRIMARIES);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(command, Optional.of(new Route.Builder(routeType).build()), r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSimpleRoutes()),
                () ->
                        assertEquals(
                                routeType,
                                protocSimpleRouteToClientSimpleRoute.get(
                                        requestBuilder.getRoute().getSimpleRoutes())),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_ID", "REPLICA_SLOT_ID"})
    public void prepare_request_with_slot_id_routes(RouteType routeType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        var protocSlotTypeToClientSlotIdRoute =
                Map.of(
                        SlotTypes.Primary, RouteType.PRIMARY_SLOT_ID,
                        SlotTypes.Replica, RouteType.REPLICA_SLOT_ID);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(
                command, Optional.of(new Route.Builder(routeType).setSlotId(42).build()), r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSlotIdRoute()),
                () ->
                        assertEquals(
                                routeType,
                                protocSlotTypeToClientSlotIdRoute.get(
                                        requestBuilder.getRoute().getSlotIdRoute().getSlotType())),
                () -> assertEquals(42, requestBuilder.getRoute().getSlotIdRoute().getSlotId()),
                () -> assertFalse(requestBuilder.getRoute().hasSimpleRoutes()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }

    @ParameterizedTest
    @EnumSource(
            value = RouteType.class,
            names = {"PRIMARY_SLOT_KEY", "REPLICA_SLOT_KEY"})
    public void prepare_request_with_slot_key_routes(RouteType routeType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);

        var protocSlotTypeToClientSlotKeyRoute =
                Map.of(
                        SlotTypes.Primary, RouteType.PRIMARY_SLOT_KEY,
                        SlotTypes.Replica, RouteType.REPLICA_SLOT_KEY);

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(
                command, Optional.of(new Route.Builder(routeType).setSlotKey("TEST").build()), r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSlotKeyRoute()),
                () ->
                        assertEquals(
                                routeType,
                                protocSlotTypeToClientSlotKeyRoute.get(
                                        requestBuilder.getRoute().getSlotKeyRoute().getSlotType())),
                () -> assertEquals("TEST", requestBuilder.getRoute().getSlotKeyRoute().getSlotKey()),
                () -> assertFalse(requestBuilder.getRoute().hasSimpleRoutes()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()));
    }
}
