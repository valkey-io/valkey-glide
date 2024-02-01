/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import glide.managers.models.Command;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import redis_request.RedisRequestOuterClass;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RedisRequest;
import redis_request.RedisRequestOuterClass.RequestType;
import redis_request.RedisRequestOuterClass.Routes;
import redis_request.RedisRequestOuterClass.SimpleRoutes;
import redis_request.RedisRequestOuterClass.SlotTypes;
import response.ResponseOuterClass.Response;

/**
 * Service responsible for submitting command requests to a socket channel handler and unpack
 * responses from the same socket channel handler.
 */
@RequiredArgsConstructor
public class CommandManager {

    /** UDS connection representation. */
    private final ChannelHandler channel;

    /**
     * Build a command and send.
     *
     * @param command The command to execute
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            Command command, RedisExceptionCheckedFunction<Response, T> responseHandler) {
        // write command request to channel
        // when complete, convert the response to our expected type T using the given responseHandler
        return channel
                .write(
                        prepareRedisRequest(
                                command.getRequestType(),
                                command.getArguments(),
                                Optional.ofNullable(command.getRoute())),
                        true)
                .exceptionally(this::exceptionHandler)
                .thenApplyAsync(responseHandler::apply);
    }

    /**
     * Exception handler for future pipeline.
     *
     * @param e An exception thrown in the pipeline before
     * @return Nothing, it rethrows the exception
     */
    private Response exceptionHandler(Throwable e) {
        if (e instanceof ClosingException) {
            channel.close();
        }
        if (e instanceof RuntimeException) {
            // RedisException also goes here
            throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
    }

    /**
     * Build a protobuf command/transaction request object.<br>
     * Used by {@link CommandManager}.
     *
     * @param command - Redis command
     * @param args - Redis command arguments as string array
     * @return An uncompleted request. CallbackDispatcher is responsible to complete it by adding a
     *     callback id.
     */
    private RedisRequestOuterClass.RedisRequest.Builder prepareRedisRequest(
            Command.RequestType command, String[] args) {
        RedisRequestOuterClass.Command.ArgsArray.Builder commandArgs =
                RedisRequestOuterClass.Command.ArgsArray.newBuilder();
        for (var arg : args) {
            commandArgs.addArgs(arg);
        }

        // TODO: set route properly when no RouteOptions given
        return RedisRequestOuterClass.RedisRequest.newBuilder()
                .setSingleCommand(
                        RedisRequestOuterClass.Command.newBuilder()
                                .setRequestType(mapRequestTypes(command))
                                .setArgsArray(commandArgs.build())
                                .build())
                .setRoute(
                        RedisRequestOuterClass.Routes.newBuilder()
                                .setSimpleRoutes(RedisRequestOuterClass.SimpleRoutes.AllNodes)
                                .build());
    }

    private RequestType mapRequestTypes(Command.RequestType inType) {
        switch (inType) {
            case CUSTOM_COMMAND:
                return RequestType.CustomCommand;
        }
        throw new RuntimeException("Unsupported request type");
    }

    /**
     * Build a protobuf command/transaction request object with routing options.<br>
     * Used by {@link CommandManager}.
     *
     * @param command Redis command type
     * @param args Redis command arguments
     * @param route Command routing parameters
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    private RedisRequest.Builder prepareRedisRequest(
            Command.RequestType command, String[] args, Optional<Route> route) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (var arg : args) {
            commandArgs.addArgs(arg);
        }

        var builder =
                RedisRequest.newBuilder()
                        .setSingleCommand(
                                RedisRequestOuterClass.Command.newBuilder()
                                        .setRequestType(mapRequestTypes(command))
                                        .setArgsArray(commandArgs.build())
                                        .build());

        if (route.isEmpty()) {
            return builder;
        }

        if (route.get() instanceof SimpleRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSimpleRoutes(SimpleRoutes.forNumber(((SimpleRoute) route.get()).ordinal()))
                            .build());
        } else if (route.get() instanceof SlotIdRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSlotIdRoute(
                                    RedisRequestOuterClass.SlotIdRoute.newBuilder()
                                            .setSlotId(((SlotIdRoute) route.get()).getSlotId())
                                            .setSlotType(
                                                    SlotTypes.forNumber(
                                                            ((SlotIdRoute) route.get()).getSlotType().ordinal()))));
        } else if (route.get() instanceof SlotKeyRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSlotKeyRoute(
                                    RedisRequestOuterClass.SlotKeyRoute.newBuilder()
                                            .setSlotKey(((SlotKeyRoute) route.get()).getSlotKey())
                                            .setSlotType(
                                                    SlotTypes.forNumber(
                                                            ((SlotKeyRoute) route.get()).getSlotType().ordinal()))));
        } else {
            throw new IllegalArgumentException("Unknown type of route");
        }
        return builder;
    }
}
