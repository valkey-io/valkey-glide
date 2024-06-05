/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import com.google.protobuf.ByteString;
import glide.api.models.ClusterTransaction;
import glide.api.models.Script;
import glide.api.models.Transaction;
import glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute;
import glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.RequestException;
import glide.connectors.handlers.CallbackDispatcher;
import glide.connectors.handlers.ChannelHandler;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import redis_request.RedisRequestOuterClass;
import redis_request.RedisRequestOuterClass.Command;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;
import redis_request.RedisRequestOuterClass.RedisRequest;
import redis_request.RedisRequestOuterClass.RequestType;
import redis_request.RedisRequestOuterClass.Routes;
import redis_request.RedisRequestOuterClass.ScriptInvocation;
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
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(requestType, arguments);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a command and send.
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            List<byte[]> arguments,
            RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(requestType, arguments);
        return submitCommandToChannel(command, responseHandler);
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
            Route route,
            RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(requestType, arguments, route);
        return submitCommandToChannel(command, responseHandler);
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
            List<byte[]> arguments,
            Route route,
            RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(requestType, arguments, route);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a Transaction and send.
     *
     * @param transaction Redis Transaction request with multiple commands
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewTransaction(
            Transaction transaction, RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(transaction);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a Script (by hash) request to send to Redis.
     *
     * @param script Lua script hash object
     * @param keys The keys that are used in the script
     * @param args The arguments for the script
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<String> keys,
            List<String> args,
            RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(script, keys, args);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a Cluster Transaction and send.
     *
     * @param transaction Redis Transaction request with multiple commands
     * @param route Transaction routing parameters
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewTransaction(
            ClusterTransaction transaction,
            Optional<Route> route,
            RedisExceptionCheckedFunction<Response, T> responseHandler) {

        RedisRequest.Builder command = prepareRedisRequest(transaction, route);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Take a redis request and send to channel.
     *
     * @param command The Redis command request as a builder to execute
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    protected <T> CompletableFuture<T> submitCommandToChannel(
            RedisRequest.Builder command, RedisExceptionCheckedFunction<Response, T> responseHandler) {
        if (channel.isClosed()) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Channel closed: Unable to submit command."));
            return errorFuture;
        }

        // write command request to channel
        // when complete, convert the response to our expected type T using the given responseHandler
        return channel
                .write(command, true)
                .exceptionally(this::exceptionHandler)
                .thenApplyAsync(responseHandler::apply);
    }

    /**
     * Build a protobuf command request object with routing options.
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @param route Command routing parameters
     * @return An incomplete request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(
            RequestType requestType, String[] arguments, Route route) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (var arg : arguments) {
            commandArgs.addArgs(ByteString.copyFromUtf8(arg));
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
     * Build a protobuf command request object with routing options.
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @param route Command routing parameters
     * @return An incomplete request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(
            RequestType requestType, List<byte[]> arguments, Route route) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (var arg : arguments) {
            commandArgs.addArgs(ByteString.copyFrom(arg));
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
     * Build a protobuf transaction request object with routing options.
     *
     * @param transaction Redis transaction with commands
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(Transaction transaction) {
        return RedisRequest.newBuilder().setTransaction(transaction.getProtobufTransaction().build());
    }

    /**
     * Build a protobuf Script Invoke request.
     *
     * @param script Redis Script
     * @param keys keys for the Script
     * @param args args for the Script
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(
            Script script, List<String> keys, List<String> args) {
        return RedisRequest.newBuilder()
                .setScriptInvocation(
                        ScriptInvocation.newBuilder()
                                .setHash(script.getHash())
                                .addAllKeys(keys)
                                .addAllArgs(args)
                                .build());
    }

    /**
     * Build a protobuf transaction request object with routing options.
     *
     * @param transaction Redis transaction with commands
     * @param route Command routing parameters
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(
            ClusterTransaction transaction, Optional<Route> route) {

        RedisRequest.Builder builder =
                RedisRequest.newBuilder().setTransaction(transaction.getProtobufTransaction().build());

        return route.isPresent() ? prepareRedisRequestRoute(builder, route.get()) : builder;
    }

    /**
     * Build a protobuf command request object.
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(RequestType requestType, String[] arguments) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (var arg : arguments) {
            commandArgs.addArgs(ByteString.copyFromUtf8(arg));
        }

        return RedisRequest.newBuilder()
                .setSingleCommand(
                        Command.newBuilder()
                                .setRequestType(requestType)
                                .setArgsArray(commandArgs.build())
                                .build());
    }

    /**
     * Build a protobuf command request object.
     *
     * @param requestType Redis command type
     * @param arguments Redis command arguments
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected RedisRequest.Builder prepareRedisRequest(
            RequestType requestType, List<byte[]> arguments) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (var arg : arguments) {
            commandArgs.addArgs(ByteString.copyFrom(arg));
        }

        return RedisRequest.newBuilder()
                .setSingleCommand(
                        Command.newBuilder()
                                .setRequestType(requestType)
                                .setArgsArray(commandArgs.build())
                                .build());
    }

    private RedisRequest.Builder prepareRedisRequestRoute(RedisRequest.Builder builder, Route route) {

        if (route instanceof SimpleMultiNodeRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSimpleRoutes(SimpleRoutes.forNumber(((SimpleMultiNodeRoute) route).getOrdinal()))
                            .build());
        } else if (route instanceof SimpleSingleNodeRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSimpleRoutes(SimpleRoutes.forNumber(((SimpleSingleNodeRoute) route).getOrdinal()))
                            .build());
        } else if (route instanceof SlotIdRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSlotIdRoute(
                                    RedisRequestOuterClass.SlotIdRoute.newBuilder()
                                            .setSlotId(((SlotIdRoute) route).getSlotId())
                                            .setSlotType(
                                                    SlotTypes.forNumber(((SlotIdRoute) route).getSlotType().ordinal()))));
        } else if (route instanceof SlotKeyRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSlotKeyRoute(
                                    RedisRequestOuterClass.SlotKeyRoute.newBuilder()
                                            .setSlotKey(((SlotKeyRoute) route).getSlotKey())
                                            .setSlotType(
                                                    SlotTypes.forNumber(((SlotKeyRoute) route).getSlotType().ordinal()))));
        } else if (route instanceof ByAddressRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setByAddressRoute(
                                    RedisRequestOuterClass.ByAddressRoute.newBuilder()
                                            .setHost(((ByAddressRoute) route).getHost())
                                            .setPort(((ByAddressRoute) route).getPort())));
        } else {
            throw new RequestException(
                    String.format("Unknown type of route: %s", route.getClass().getSimpleName()));
        }
        return builder;
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
}
