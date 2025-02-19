/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import com.google.protobuf.ByteString;
import command_request.CommandRequestOuterClass;
import command_request.CommandRequestOuterClass.Command;
import command_request.CommandRequestOuterClass.Command.ArgsArray;
import command_request.CommandRequestOuterClass.CommandRequest;
import command_request.CommandRequestOuterClass.RequestType;
import command_request.CommandRequestOuterClass.Routes;
import command_request.CommandRequestOuterClass.ScriptInvocation;
import command_request.CommandRequestOuterClass.ScriptInvocationPointers;
import command_request.CommandRequestOuterClass.SimpleRoutes;
import command_request.CommandRequestOuterClass.SlotTypes;
import command_request.CommandRequestOuterClass.UpdateConnectionPassword;
import glide.api.models.ClusterTransaction;
import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.Transaction;
import glide.api.models.commands.scan.ClusterScanCursor;
import glide.api.models.commands.scan.ScanOptions;
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
import glide.ffi.resolvers.GlideValueResolver;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
     * Internal interface for exposing implementation details about a ClusterScanCursor. This is an
     * interface so that it can be mocked in tests.
     */
    public interface ClusterScanCursorDetail extends ClusterScanCursor {
        /**
         * Returns the handle String representing the cursor.
         *
         * @return the handle String representing the cursor.
         */
        String getCursorHandle();
    }

    /**
     * Build a command and send.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a command and send.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a command and send.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param route Command routing parameters
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments, route);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a command and send.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param route Command routing parameters
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments, route);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a Transaction and send.
     *
     * @param transaction Transaction request with multiple commands
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewTransaction(
            Transaction transaction, GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(transaction);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a Script (by hash) request to send to Valkey.
     *
     * @param script Lua script hash object
     * @param keys The keys that are used in the script
     * @param args The arguments for the script
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<GlideString> keys,
            List<GlideString> args,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareScript(script, keys, args);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a Script (by hash) request with route to send to Valkey.
     *
     * @param script Lua script hash object
     * @param args The arguments for the script
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<GlideString> args,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareScript(script, args, route);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Build a Cluster Transaction and send.
     *
     * @param transaction Transaction request with multiple commands
     * @param route Transaction routing parameters
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitNewTransaction(
            ClusterTransaction transaction,
            Optional<Route> route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(transaction, route);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Submits a scan request with cursor
     *
     * @param cursor Iteration cursor
     * @param options {@link ScanOptions}
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    public <T> CompletableFuture<T> submitClusterScan(
            ClusterScanCursor cursor,
            @NonNull ScanOptions options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        final CommandRequest.Builder command = prepareCursorRequest(cursor, options);
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Submit a password update request to GLIDE core.
     *
     * @param password A new password to set or empty value to remove the password.
     * @param immediateAuth immediately perform auth.
     * @param responseHandler A response handler.
     * @return A request promise.
     * @param <T> Type of the response.
     */
    public <T> CompletableFuture<T> submitPasswordUpdate(
            Optional<String> password,
            boolean immediateAuth,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        var builder = UpdateConnectionPassword.newBuilder().setImmediateAuth(immediateAuth);
        password.ifPresent(builder::setPassword);

        var command = CommandRequest.newBuilder().setUpdateConnectionPassword(builder.build());
        return submitCommandToChannel(command, responseHandler);
    }

    /**
     * Take a command request and send to channel.
     *
     * @param command The command request as a builder to execute
     * @param responseHandler The handler for the response object
     * @return A result promise of type T
     */
    protected <T> CompletableFuture<T> submitCommandToChannel(
            CommandRequest.Builder command, GlideExceptionCheckedFunction<Response, T> responseHandler) {
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
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param route Command routing parameters
     * @return An incomplete request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, String[] arguments, Route route) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        var builder =
                CommandRequest.newBuilder()
                        .setSingleCommand(commandBuilder.setRequestType(requestType).build());

        return prepareCommandRequestRoute(builder, route);
    }

    /**
     * Build a protobuf command request object with routing options.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @param route Command routing parameters
     * @return An incomplete request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, GlideString[] arguments, Route route) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        var builder =
                CommandRequest.newBuilder()
                        .setSingleCommand(commandBuilder.setRequestType(requestType).build());

        return prepareCommandRequestRoute(builder, route);
    }

    /**
     * Build a protobuf transaction request object with routing options.
     *
     * @param transaction Valkey transaction with commands
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareCommandRequest(Transaction transaction) {
        // TODO: rename to getProtobufBatch
        return CommandRequest.newBuilder().setBatch(transaction.getProtobufTransaction().build());
    }

    /**
     * Build a protobuf Script Invoke request.
     *
     * @param script Valkey Script
     * @param keys keys for the Script
     * @param args args for the Script
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareScript(
            Script script, List<GlideString> keys, List<GlideString> args) {

        if (keys.stream().mapToLong(key -> key.getBytes().length).sum()
                        + args.stream().mapToLong(key -> key.getBytes().length).sum()
                > GlideValueResolver.MAX_REQUEST_ARGS_LENGTH_IN_BYTES) {
            return CommandRequest.newBuilder()
                    .setScriptInvocationPointers(
                            ScriptInvocationPointers.newBuilder()
                                    .setHash(script.getHash())
                                    .setArgsPointer(
                                            GlideValueResolver.createLeakedBytesVec(
                                                    args.stream().map(GlideString::getBytes).toArray(byte[][]::new)))
                                    .setKeysPointer(
                                            GlideValueResolver.createLeakedBytesVec(
                                                    keys.stream().map(GlideString::getBytes).toArray(byte[][]::new)))
                                    .build());
        }

        return CommandRequest.newBuilder()
                .setScriptInvocation(
                        ScriptInvocation.newBuilder()
                                .setHash(script.getHash())
                                .addAllKeys(
                                        keys.stream()
                                                .map(GlideString::getBytes)
                                                .map(ByteString::copyFrom)
                                                .collect(Collectors.toList()))
                                .addAllArgs(
                                        args.stream()
                                                .map(GlideString::getBytes)
                                                .map(ByteString::copyFrom)
                                                .collect(Collectors.toList()))
                                .build());
    }

    /**
     * Build a protobuf Script Invoke request with route.
     *
     * @param script Valkey Script
     * @param args args for the Script
     * @param route route specified for the Script Invoke request
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareScript(
            Script script, List<GlideString> args, Route route) {
        CommandRequest.Builder builder = prepareScript(script, List.of(), args);
        return prepareCommandRequestRoute(builder, route);
    }

    /**
     * Build a protobuf transaction request object with routing options.
     *
     * @param transaction Valkey transaction with commands
     * @param route Command routing parameters
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareCommandRequest(
            ClusterTransaction transaction, Optional<Route> route) {

        // TODO: rename to getProtobufBatch
        CommandRequest.Builder builder =
                CommandRequest.newBuilder().setBatch(transaction.getProtobufTransaction().build());

        return route.isPresent() ? prepareCommandRequestRoute(builder, route.get()) : builder;
    }

    /**
     * Build a protobuf cursor scan request.
     *
     * @param cursor Iteration cursor
     * @param options {@link ScanOptions}
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareCursorRequest(
            @NonNull ClusterScanCursor cursor, @NonNull ScanOptions options) {

        CommandRequestOuterClass.ClusterScan.Builder clusterScanBuilder =
                CommandRequestOuterClass.ClusterScan.newBuilder();

        if (cursor != ClusterScanCursor.INITIAL_CURSOR_INSTANCE) {
            if (cursor instanceof ClusterScanCursorDetail) {
                clusterScanBuilder.setCursor(((ClusterScanCursorDetail) cursor).getCursorHandle());
            } else {
                throw new IllegalArgumentException("Illegal cursor submitted.");
            }
        }

        // Use the binary match pattern first
        if (options.getMatchPattern() != null) {
            clusterScanBuilder.setMatchPattern(ByteString.copyFrom(options.getMatchPattern().getBytes()));
        }

        if (options.getCount() != null) {
            clusterScanBuilder.setCount(options.getCount());
        }

        if (options.getType() != null) {
            clusterScanBuilder.setObjectType(options.getType().getNativeName());
        }

        if (options.getAllowNonCoveredSlots() != null) {
            clusterScanBuilder.setAllowNonCoveredSlots(options.getAllowNonCoveredSlots());
        }

        return CommandRequest.newBuilder().setClusterScan(clusterScanBuilder.build());
    }

    /**
     * Build a protobuf command request object.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, String[] arguments) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        return CommandRequest.newBuilder()
                .setSingleCommand(commandBuilder.setRequestType(requestType).build());
    }

    /**
     * Build a protobuf command request object.
     *
     * @param requestType Valkey command type
     * @param arguments Valkey command arguments
     * @return An uncompleted request. {@link CallbackDispatcher} is responsible to complete it by
     *     adding a callback id.
     */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, GlideString[] arguments) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        return CommandRequest.newBuilder()
                .setSingleCommand(commandBuilder.setRequestType(requestType).build());
    }

    private CommandRequest.Builder prepareCommandRequestRoute(
            CommandRequest.Builder builder, Route route) {

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
                                    CommandRequestOuterClass.SlotIdRoute.newBuilder()
                                            .setSlotId(((SlotIdRoute) route).getSlotId())
                                            .setSlotType(
                                                    SlotTypes.forNumber(((SlotIdRoute) route).getSlotType().ordinal()))));
        } else if (route instanceof SlotKeyRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setSlotKeyRoute(
                                    CommandRequestOuterClass.SlotKeyRoute.newBuilder()
                                            .setSlotKey(((SlotKeyRoute) route).getSlotKey())
                                            .setSlotType(
                                                    SlotTypes.forNumber(((SlotKeyRoute) route).getSlotType().ordinal()))));
        } else if (route instanceof ByAddressRoute) {
            builder.setRoute(
                    Routes.newBuilder()
                            .setByAddressRoute(
                                    CommandRequestOuterClass.ByAddressRoute.newBuilder()
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
            // GlideException also goes here
            throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
    }

    /**
     * Add the given set of arguments to the output Command.Builder.
     *
     * @param arguments The arguments to add to the builder.
     * @param outputBuilder The builder to populate with arguments.
     */
    public static <ArgType> void populateCommandWithArgs(
            ArgType[] arguments, Command.Builder outputBuilder) {
        populateCommandWithArgs(
                Arrays.stream(arguments)
                        .map(value -> GlideString.of(value).getBytes())
                        .collect(Collectors.toList()),
                outputBuilder);
    }

    /**
     * Add the given set of arguments to the output Command.Builder.
     *
     * @param arguments The arguments to add to the builder.
     * @param outputBuilder The builder to populate with arguments.
     */
    private static void populateCommandWithArgs(
            GlideString[] arguments, Command.Builder outputBuilder) {
        populateCommandWithArgs(
                Arrays.stream(arguments).map(GlideString::getBytes).collect(Collectors.toList()),
                outputBuilder);
    }

    /**
     * Add the given set of arguments to the output Command.Builder.
     *
     * <p>Implementation note: When the length in bytes of all arguments supplied to the given command
     * exceed {@link GlideValueResolver#MAX_REQUEST_ARGS_LENGTH_IN_BYTES}, the Command will hold a
     * handle to leaked vector of byte arrays in the native layer in the <code>ArgsVecPointer</code>
     * field. In the normal case where the command arguments are small, they'll be serialized as to an
     * {@link ArgsArray} message.
     *
     * @param arguments The arguments to add to the builder.
     * @param outputBuilder The builder to populate with arguments.
     */
    private static void populateCommandWithArgs(
            List<byte[]> arguments, Command.Builder outputBuilder) {
        final long totalArgSize = arguments.stream().mapToLong(arg -> arg.length).sum();
        if (totalArgSize < GlideValueResolver.MAX_REQUEST_ARGS_LENGTH_IN_BYTES) {
            ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
            arguments.forEach(arg -> commandArgs.addArgs(ByteString.copyFrom(arg)));
            outputBuilder.setArgsArray(commandArgs);
        } else {
            outputBuilder.setArgsVecPointer(
                    GlideValueResolver.createLeakedBytesVec(arguments.toArray(new byte[][] {})));
        }
    }
}
