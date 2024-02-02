/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.exceptions.ClosingException;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import redis_request.RedisRequestOuterClass;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RedisRequest;
import redis_request.RedisRequestOuterClass.RequestType;
import redis_request.RedisRequestOuterClass.Routes;
import redis_request.RedisRequestOuterClass.SimpleRoutes;
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
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
        RedisRequestOuterClass.RequestType requestType,
        String[] arguments,
        RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(requestType, arguments);
        return submitNewCommand(command, responseHandler);
    }

    /**
     * Build a command and send.
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @param route Command routing parameters
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            Optional<Route> route,
            RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(requestType, arguments, route);
        return submitNewCommand(command, responseHandler);
    }

    /**
     * Take a redis request and send to channel.
     *
     * @param command The Redis command request as a builder to execute
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    protected <T> CompletableFuture<T> submitNewCommand(
            RedisRequest.Builder command, RedisExceptionCheckedFunction<Response, T> responseHandler) {
        // write command request to channel
        // when complete, convert the response to our expected type T using the given responseHandler
        return channel
                .write(command, true)
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
     * Build a protobuf command request object with routing options.<br>
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @param route Command routing parameters
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(
            RequestType requestType, String[] arguments, Optional<Route> route) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (var arg : arguments) {
            commandArgs.addArgs(arg);
        }

        var builder =
                RedisRequest.newBuilder()
                        .setSingleCommand(
                                Command.newBuilder()
                                        .setRequestType(requestType)
                                        .setArgsArray(commandArgs.build())
                                        .build());

        return prepareRedisRequestRoute(builder, route);
    }

    /**
     * Build a protobuf command request object with routing options.<br>
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(RequestType requestType, String[] arguments) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (var arg : arguments) {
            commandArgs.addArgs(arg);
        }

        var builder =
                RedisRequest.newBuilder()
                        .setSingleCommand(
                                Command.newBuilder()
                                        .setRequestType(requestType)
                                        .setArgsArray(commandArgs.build())
                                        .build());

        return prepareRedisRequestRoute(builder, Optional.empty());
    }

    private RedisRequest.Builder prepareRedisRequestRoute(
            RedisRequest.Builder builder, Optional<Route> route) {
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
                                                    RedisRequestOuterClass.SlotTypes.forNumber(
                                                            ((SlotIdRoute) route.get()).getSlotType().ordinal()))));
        } else if (route.get() instanceof SlotKeyRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSlotKeyRoute(
                                    RedisRequestOuterClass.SlotKeyRoute.newBuilder()
                                            .setSlotKey(((SlotKeyRoute) route.get()).getSlotKey())
                                            .setSlotType(
                                                    RedisRequestOuterClass.SlotTypes.forNumber(
                                                            ((SlotKeyRoute) route.get()).getSlotType().ordinal()))));
        } else {
            throw new IllegalArgumentException("Unknown type of route");
        }
        return builder;
    }
}
