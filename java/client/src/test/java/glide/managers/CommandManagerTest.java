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

import glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotType;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConnectionException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import glide.connectors.handlers.ChannelHandler;
import glide.managers.models.Command;
import java.util.Map;
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

    @ParameterizedTest
    @EnumSource(value = SimpleRoute.class)
    public void prepare_request_with_simple_routes(SimpleRoute routeType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        var command =
                Command.builder().requestType(Command.RequestType.CUSTOM_COMMAND).route(routeType).build();

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        var protobufToClientRouteMapping =
                Map.of(
                        SimpleRoutes.AllNodes, SimpleRoute.ALL_NODES,
                        SimpleRoutes.AllPrimaries, SimpleRoute.ALL_PRIMARIES,
                        SimpleRoutes.Random, SimpleRoute.RANDOM);

        service.submitNewCommand(command, r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSimpleRoutes()),
                () ->
                        assertEquals(
                                routeType,
                                protobufToClientRouteMapping.get(requestBuilder.getRoute().getSimpleRoutes())),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }

    @ParameterizedTest
    @EnumSource(value = SlotType.class)
    public void prepare_request_with_slot_id_routes(SlotType slotType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        var command =
                Command.builder()
                        .requestType(Command.RequestType.CUSTOM_COMMAND)
                        .route(new SlotIdRoute(42, slotType))
                        .build();

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(command, r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        var protobufToClientRouteMapping =
                Map.of(
                        SlotTypes.Primary, SlotType.PRIMARY,
                        SlotTypes.Replica, SlotType.REPLICA);

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSlotIdRoute()),
                () ->
                        assertEquals(
                                slotType,
                                protobufToClientRouteMapping.get(
                                        requestBuilder.getRoute().getSlotIdRoute().getSlotType())),
                () -> assertEquals(42, requestBuilder.getRoute().getSlotIdRoute().getSlotId()),
                () -> assertFalse(requestBuilder.getRoute().hasSimpleRoutes()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotKeyRoute()));
    }

    @ParameterizedTest
    @EnumSource(value = SlotType.class)
    public void prepare_request_with_slot_key_routes(SlotType slotType) {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        var command =
                Command.builder()
                        .requestType(Command.RequestType.CUSTOM_COMMAND)
                        .route(new SlotKeyRoute("TEST", slotType))
                        .build();

        ArgumentCaptor<RedisRequest.Builder> captor =
                ArgumentCaptor.forClass(RedisRequest.Builder.class);

        service.submitNewCommand(command, r -> null);
        verify(channelHandler).write(captor.capture(), anyBoolean());
        var requestBuilder = captor.getValue();

        var protobufToClientRouteMapping =
                Map.of(
                        SlotTypes.Primary, SlotType.PRIMARY,
                        SlotTypes.Replica, SlotType.REPLICA);

        assertAll(
                () -> assertTrue(requestBuilder.hasRoute()),
                () -> assertTrue(requestBuilder.getRoute().hasSlotKeyRoute()),
                () ->
                        assertEquals(
                                slotType,
                                protobufToClientRouteMapping.get(
                                        requestBuilder.getRoute().getSlotKeyRoute().getSlotType())),
                () -> assertEquals("TEST", requestBuilder.getRoute().getSlotKeyRoute().getSlotKey()),
                () -> assertFalse(requestBuilder.getRoute().hasSimpleRoutes()),
                () -> assertFalse(requestBuilder.getRoute().hasSlotIdRoute()));
    }

    @Test
    public void prepare_request_with_unknown_route_type() {
        CompletableFuture<Response> future = new CompletableFuture<>();
        when(channelHandler.write(any(), anyBoolean())).thenReturn(future);
        var command =
                Command.builder()
                        .requestType(Command.RequestType.CUSTOM_COMMAND)
                        .route(() -> false)
                        .build();

        var exception =
                assertThrows(
                        IllegalArgumentException.class, () -> service.submitNewCommand(command, r -> null));
        assertEquals("Unknown type of route", exception.getMessage());
    }
}
