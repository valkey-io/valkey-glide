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
import command_request.CommandRequestOuterClass.SimpleRoutes;
import command_request.CommandRequestOuterClass.SlotTypes;
import glide.api.OpenTelemetry;
import glide.api.models.Batch;
import glide.api.models.ClusterBatch;
import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.commands.batch.BaseBatchOptions;
import glide.api.models.commands.batch.BatchOptions;
import glide.api.models.commands.batch.ClusterBatchOptions;
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
import glide.ffi.resolvers.OpenTelemetryResolver;
import glide.internal.GlideCoreClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

/**
 * CommandManager that submits command requests directly to the Rust glide-core via JNI calls. This
 * replaces the previous UDS-based socket communication while preserving the existing protobuf
 * serialization format for compatibility.
 */
@RequiredArgsConstructor
public class CommandManager {

    /** JNI connection representation. */
    private final GlideCoreClient coreClient;

    /** Internal interface for exposing implementation details about a ClusterScanCursor. */
    public interface ClusterScanCursorDetail extends ClusterScanCursor {
        /**
         * Returns the handle String representing the cursor.
         *
         * @return the handle String representing the cursor.
         */
        String getCursorHandle();

        /**
         * Returns the cursor ID for the fixed JNI bridge.
         *
         * @return the cursor ID string.
         */
        String getCursorId();
    }

    /** Build a command and send via JNI. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments);
        return submitCommandToJni(
                command, responseHandler, false, true); // String arguments -> expect UTF-8 response
    }

    /** Build a command and send via JNI. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments);
        return submitCommandToJni(
                command, responseHandler, true, false); // GlideString arguments -> expect binary response
    }

    /** Build a command with explicit response type expectation. */
    public <T> CompletableFuture<T> submitNewCommandWithResponseType(
            RequestType requestType,
            GlideString[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean expectUtf8Response) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments);
        return submitCommandToJni(
                command, responseHandler, true, expectUtf8Response); // Override response expectation
    }

    /** Build a command and send via JNI. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments, route);
        return submitCommandToJni(
                command, responseHandler, false, true); // String arguments -> expect UTF-8 response
    }

    /** Build a command and send via JNI. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments, route);
        return submitCommandToJni(
                command, responseHandler, true, false); // GlideString arguments -> expect binary response
    }

    /** Build a Batch and send via JNI. */
    public <T> CompletableFuture<T> submitNewBatch(
            Batch batch,
            boolean raiseOnError,
            Optional<BatchOptions> options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        CommandRequest.Builder command = prepareCommandRequest(batch, raiseOnError, options);
        return submitBatchToJni(command, responseHandler);
    }

    /** Build a Script (by hash) request to send to Valkey via JNI. */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<GlideString> keys,
            List<GlideString> args,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareScript(script, keys, args);
        return submitCommandToJni(
                command, responseHandler, true, false); // Script with GlideString -> expect binary response
    }

    /** Build a Script (by hash) request with route to send to Valkey via JNI. */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<GlideString> args,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareScript(script, args, route);
        return submitCommandToJni(
                command, responseHandler, true, false); // Script with GlideString -> expect binary response
    }

    /** Build a Cluster Batch and send via JNI. */
    public <T> CompletableFuture<T> submitNewBatch(
            ClusterBatch batch,
            boolean raiseOnError,
            Optional<ClusterBatchOptions> options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        CommandRequest.Builder command = prepareCommandRequest(batch, raiseOnError, options);
        return submitBatchToJni(command, responseHandler);
    }

    /** Submit a scan request with cursor via JNI. */
    public <T> CompletableFuture<T> submitClusterScan(
            ClusterScanCursor cursor,
            @NonNull ScanOptions options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        return submitClusterScanToJni(cursor, options, responseHandler);
    }

    /** Submit a password update request to GLIDE core via JNI. */
    public <T> CompletableFuture<T> submitPasswordUpdate(
            Optional<String> password,
            boolean immediateAuth,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        return coreClient
                .updateConnectionPassword(password.orElse(null), immediateAuth)
                .thenApply(
                        result -> {
                            // Convert JNI result to protobuf Response format
                            Response.Builder responseBuilder = Response.newBuilder();
                            if ("OK".equals(result)) {
                                responseBuilder.setConstantResponse(ConstantResponse.OK);
                            }
                            return responseHandler.apply(responseBuilder.build());
                        });
    }

    /** Take a command request and send via JNI (backward compatibility). */
    protected <T> CompletableFuture<T> submitCommandToJni(
            CommandRequest.Builder command,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean binaryMode) {
        // For backward compatibility, default expectUtf8Response based on binaryMode
        // binaryMode=true means GlideString args, expect binary response
        // binaryMode=false means String args, expect UTF-8 response
        return submitCommandToJni(command, responseHandler, binaryMode, !binaryMode);
    }

    /** Take a command request and send via JNI. */
    protected <T> CompletableFuture<T> submitCommandToJni(
            CommandRequest.Builder command,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean binaryMode,
            boolean expectUtf8Response) {

        if (!coreClient.isConnected()) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit command."));
            return errorFuture;
        }

        try {
            // Serialize the protobuf command request
            byte[] requestBytes = command.build().toByteArray();

            // Execute via JNI - returns converted Java objects directly
            // No need to wrap in Response since JNI already provides the final object
            // Use binary or UTF-8 mode based on expected response type, not argument type
            CompletableFuture<Object> jniFuture =
                    expectUtf8Response
                            ? coreClient.executeCommandAsync(requestBytes) // Force UTF-8 conversion
                            : coreClient.executeBinaryCommandAsync(requestBytes); // Allow binary conversion

            return jniFuture
                    .thenApply(
                            result -> {
                                // Create a minimal Response just for compatibility with the handler
                                // The handler will extract the object from it
                                Response.Builder builder = Response.newBuilder();
                                if (result == null) {
                                    // Null response
                                    builder.setRespPointer(0L);
                                } else if ("OK".equals(result)) {
                                    // OK constant response
                                    builder.setConstantResponse(ConstantResponse.OK);
                                } else {
                                    // Store the object temporarily and pass its ID
                                    // This allows BaseResponseResolver to retrieve it
                                    long objectId = JniResponseRegistry.storeObject(result);
                                    builder.setRespPointer(objectId);
                                }
                                return responseHandler.apply(builder.build());
                            })
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /** Submit batch request via JNI. */
    protected <T> CompletableFuture<T> submitBatchToJni(
            CommandRequest.Builder command, GlideExceptionCheckedFunction<Response, T> responseHandler) {

        if (!coreClient.isConnected()) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit batch."));
            return errorFuture;
        }

        try {
            // Serialize the protobuf batch request
            byte[] requestBytes = command.build().toByteArray();

            // Execute via JNI and convert response
            return coreClient
                    .executeBatchAsync(requestBytes)
                    .thenApply(result -> convertJniToProtobufResponse(result))
                    .thenApply(responseHandler::apply)
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /** Submit cluster scan request via enhanced JNI bridge. */
    protected <T> CompletableFuture<T> submitClusterScanToJni(
            ClusterScanCursor cursor,
            @NonNull ScanOptions options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        if (!coreClient.isConnected()) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit cluster scan."));
            return errorFuture;
        }

        try {
            // Extract cursor information
            String cursorId = getCursorId(cursor);
            String matchPattern =
                    options.getMatchPattern() != null ? options.getMatchPattern().toString() : null;
            Long count = options.getCount() != null ? options.getCount() : null;
            String objectType = null; // ObjectType is not available in current ScanOptions API

            // Execute via enhanced cluster scan JNI bridge
            return coreClient
                    .executeClusterScanAsync(cursorId, matchPattern, count != null ? count : 0L, objectType)
                    .thenApply(
                            result -> {
                                // Create a minimal Response for compatibility with the handler
                                Response.Builder builder = Response.newBuilder();
                                if (result == null) {
                                    builder.setRespPointer(0L);
                                } else {
                                    // Store the object temporarily and pass its ID
                                    long objectId = JniResponseRegistry.storeObject(result);
                                    builder.setRespPointer(objectId);
                                }
                                return responseHandler.apply(builder.build());
                            })
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /** Extract cursor ID from ClusterScanCursor. */
    private String getCursorId(ClusterScanCursor cursor) {
        if (cursor instanceof ClusterScanCursorDetail) {
            return ((ClusterScanCursorDetail) cursor).getCursorId();
        }

        // For initial cursor, return null/empty to indicate start
        if (!cursor.isFinished()) {
            return null; // Initial cursor
        }

        // This shouldn't happen if isFinished() is true
        return null;
    }

    /**
     * Convert JNI result to protobuf Response format. This bridges the gap between JNI responses and
     * the expected protobuf Response.
     */
    private Response convertJniToProtobufResponse(Object jniResult) {
        Response.Builder builder = Response.newBuilder();

        if (jniResult == null) {
            // Null response - set pointer to 0
            builder.setRespPointer(0L);
        } else if ("OK".equals(jniResult)) {
            // OK constant response
            builder.setConstantResponse(ConstantResponse.OK);
        } else if (jniResult instanceof ByteBuffer) {
            // DirectByteBuffer from JNI containing serialized array for large responses
            // Deserialize it back to Object[] for batch responses
            ByteBuffer buffer = (ByteBuffer) jniResult;
            Object[] deserializedArray = deserializeByteBufferArray(buffer);
            // Store the deserialized array
            long objectId = JniResponseRegistry.storeObject(deserializedArray);
            builder.setRespPointer(objectId);
        } else {
            // Store the object in the registry and get its ID
            // This allows BaseResponseResolver to retrieve it correctly
            long objectId = JniResponseRegistry.storeObject(jniResult);
            builder.setRespPointer(objectId);
        }

        return builder.build();
    }

    /**
     * Create a direct Response from a JNI result object. Since JNI now returns converted Java objects
     * directly (not pointers), we store the object in a temporary registry and pass an ID.
     */
    private Response createDirectResponse(Object jniResult) {
        Response.Builder builder = Response.newBuilder();

        if (jniResult == null) {
            // Null response - no pointer needed
            builder.setRespPointer(0L);
        } else if ("OK".equals(jniResult)) {
            // OK constant response
            builder.setConstantResponse(ConstantResponse.OK);
        } else if (jniResult instanceof ByteBuffer) {
            // DirectByteBuffer from JNI containing serialized array for large responses
            ByteBuffer buffer = (ByteBuffer) jniResult;
            Object[] deserializedArray = deserializeByteBufferArray(buffer);
            // Store the deserialized array
            long objectId = JniResponseRegistry.storeObject(deserializedArray);
            builder.setRespPointer(objectId);
        } else {
            // Store the Java object and get an ID for it
            // This ID will be used to retrieve the object in valueFromPointer
            long objectId = JniResponseRegistry.storeObject(jniResult);
            builder.setRespPointer(objectId);
        }

        return builder.build();
    }

    /**
     * Deserialize a ByteBuffer containing a serialized array back to Object[]. This handles
     * DirectByteBuffer responses for large data (>16KB). Format uses Redis-like protocol: '*' +
     * array_len(4 bytes BE) + elements Each element: type_marker + data
     */
    private Object[] deserializeByteBufferArray(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN); // Rust uses big-endian
        buffer.rewind();

        // Read array marker ('*')
        byte marker = buffer.get();
        if (marker != '*') {
            throw new IllegalArgumentException("Expected array marker '*', got: " + (char) marker);
        }

        // Read array element count (4 bytes, big-endian)
        int count = buffer.getInt();
        Object[] result = new Object[count];

        for (int i = 0; i < count; i++) {
            // Read element type marker
            byte typeMarker = buffer.get();

            switch (typeMarker) {
                case '$': // Bulk string
                    int bulkLen = buffer.getInt();
                    if (bulkLen == -1) {
                        result[i] = null;
                    } else {
                        byte[] data = new byte[bulkLen];
                        buffer.get(data);
                        // Return as string for normal responses
                        result[i] = new String(data, StandardCharsets.UTF_8);
                    }
                    break;

                case '+': // Simple string (includes "OK")
                    int simpleLen = buffer.getInt();
                    byte[] simpleData = new byte[simpleLen];
                    buffer.get(simpleData);
                    // Rust side normalizes "OK" to uppercase for compatibility
                    result[i] = new String(simpleData, StandardCharsets.UTF_8);
                    break;

                case ':': // Integer
                    long intValue = buffer.getLong();
                    result[i] = intValue;
                    break;

                case '#': // Complex type (serialized as string)
                    int complexLen = buffer.getInt();
                    byte[] complexData = new byte[complexLen];
                    buffer.get(complexData);
                    result[i] = new String(complexData, StandardCharsets.UTF_8);
                    break;

                default:
                    throw new IllegalArgumentException("Unknown type marker: " + (char) typeMarker);
            }
        }

        return result;
    }

    /** Exception handler for future pipeline. */
    private <T> T exceptionHandler(Throwable e) {
        if (e instanceof ClosingException) {
            coreClient.close();
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
    }

    // ============================================================================
    // Command preparation methods (copied from original CommandManager)
    // ============================================================================

    /** Build a protobuf command request object with routing options. */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, String[] arguments, Route route) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        long spanPtr = 0;
        if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
            spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(requestType.name());
        }

        var builder =
                CommandRequest.newBuilder()
                        .setSingleCommand(commandBuilder.setRequestType(requestType).build());

        if (spanPtr != 0) {
            builder.setRootSpanPtr(spanPtr);
        }

        return prepareCommandRequestRoute(builder, route);
    }

    /** Build a protobuf command request object with routing options. */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, GlideString[] arguments, Route route) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        long spanPtr = 0;
        if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
            spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(requestType.name());
        }

        var builder =
                CommandRequest.newBuilder()
                        .setSingleCommand(commandBuilder.setRequestType(requestType).build());

        if (spanPtr != 0) {
            builder.setRootSpanPtr(spanPtr);
        }

        return prepareCommandRequestRoute(builder, route);
    }

    /** Build a protobuf Batch request object. */
    protected CommandRequest.Builder prepareCommandRequest(
            Batch batch, boolean raiseOnError, Optional<BatchOptions> options) {
        CommandRequest.Builder builder = CommandRequest.newBuilder();

        long spanPtr = 0;
        if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
            spanPtr = OpenTelemetryResolver.createLeakedOtelSpan("Batch");
        }

        if (spanPtr != 0) {
            builder.setRootSpanPtr(spanPtr);
        }

        if (options.isPresent()) {
            BatchOptions opts = options.get();
            var batchBuilder = prepareCommandRequestBatchOptions(batch.getProtobufBatch(), opts);
            builder.setBatch(batchBuilder.setRaiseOnError(raiseOnError).build());
        } else {
            builder.setBatch(batch.getProtobufBatch().setRaiseOnError(raiseOnError).build());
        }

        return builder;
    }

    /**
     * Build a protobuf Script Invoke request. DirectByteBuffer handles large responses automatically,
     * use standard protobuf for requests.
     */
    protected CommandRequest.Builder prepareScript(
            Script script, List<GlideString> keys, List<GlideString> args) {
        // Always use ScriptInvocation (not pointers) - DirectByteBuffer handles response size
        // optimization
        CommandRequest.Builder builder =
                CommandRequest.newBuilder()
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

        return builder;
    }

    /** Build a protobuf Script Invoke request with route. */
    protected CommandRequest.Builder prepareScript(
            Script script, List<GlideString> args, Route route) {
        CommandRequest.Builder builder = prepareScript(script, List.of(), args);
        return prepareCommandRequestRoute(builder, route);
    }

    /** Build a protobuf Batch request object with options. */
    protected CommandRequest.Builder prepareCommandRequest(
            ClusterBatch batch, boolean raiseOnError, Optional<ClusterBatchOptions> options) {

        CommandRequest.Builder builder = CommandRequest.newBuilder();

        long spanPtr = 0;
        if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
            spanPtr = OpenTelemetryResolver.createLeakedOtelSpan("Batch");
        }

        if (spanPtr != 0) {
            builder.setRootSpanPtr(spanPtr);
        }

        if (options.isPresent()) {
            ClusterBatchOptions opts = options.get();
            CommandRequestOuterClass.Batch.Builder batchBuilder =
                    prepareCommandRequestBatchOptions(batch.getProtobufBatch(), opts);

            if (opts.getRetryStrategy() != null) {
                if (batchBuilder.getIsAtomic()) {
                    throw new RequestException("Retry strategy is not supported for atomic batches.");
                }
                batchBuilder.setRetryServerError(opts.getRetryStrategy().isRetryServerError());
                batchBuilder.setRetryConnectionError(opts.getRetryStrategy().isRetryConnectionError());
            }

            builder.setBatch(batchBuilder.setRaiseOnError(raiseOnError).build());

            if (opts.getRoute() != null) {
                return prepareCommandRequestRoute(builder, opts.getRoute());
            }
        } else {
            builder.setBatch(batch.getProtobufBatch().setRaiseOnError(raiseOnError).build());
        }

        return builder;
    }

    /** Build a protobuf cursor scan request. */
    protected CommandRequest.Builder prepareCursorRequest(
            @NonNull ClusterScanCursor cursor, @NonNull ScanOptions options) {

        long spanPtr = 0;
        if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
            spanPtr = OpenTelemetryResolver.createLeakedOtelSpan("ClusterScan");
        }

        CommandRequestOuterClass.ClusterScan.Builder clusterScanBuilder =
                CommandRequestOuterClass.ClusterScan.newBuilder();

        if (cursor != ClusterScanCursor.INITIAL_CURSOR_INSTANCE) {
            if (cursor instanceof ClusterScanCursorDetail) {
                clusterScanBuilder.setCursor(((ClusterScanCursorDetail) cursor).getCursorHandle());
            } else {
                throw new IllegalArgumentException("Illegal cursor submitted.");
            }
        }

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

        CommandRequest.Builder builder =
                CommandRequest.newBuilder().setClusterScan(clusterScanBuilder.build());

        if (spanPtr != 0) {
            builder.setRootSpanPtr(spanPtr);
        }

        return builder;
    }

    /** Build a protobuf command request object. */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, String[] arguments) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        long spanPtr = 0;
        if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
            spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(requestType.name());
        }

        CommandRequest.Builder builder =
                CommandRequest.newBuilder()
                        .setSingleCommand(commandBuilder.setRequestType(requestType).build());

        if (spanPtr != 0) {
            builder.setRootSpanPtr(spanPtr);
        }
        return builder;
    }

    /** Build a protobuf command request object. */
    protected CommandRequest.Builder prepareCommandRequest(
            RequestType requestType, GlideString[] arguments) {
        final Command.Builder commandBuilder = Command.newBuilder();
        populateCommandWithArgs(arguments, commandBuilder);

        long spanPtr = 0;
        if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
            spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(requestType.name());
        }

        CommandRequest.Builder builder =
                CommandRequest.newBuilder()
                        .setSingleCommand(commandBuilder.setRequestType(requestType).build());

        if (spanPtr != 0) {
            builder.setRootSpanPtr(spanPtr);
        }

        return builder;
    }

    private CommandRequestOuterClass.Batch.Builder prepareCommandRequestBatchOptions(
            CommandRequestOuterClass.Batch.Builder batchBuilder, BaseBatchOptions options) {
        if (options.getTimeout() != null) {
            batchBuilder.setTimeout(options.getTimeout());
        }
        return batchBuilder;
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

    /** Add the given set of arguments to the output Command.Builder. */
    public static <ArgType> void populateCommandWithArgs(
            ArgType[] arguments, Command.Builder outputBuilder) {
        populateCommandWithArgs(
                Arrays.stream(arguments)
                        .map(value -> GlideString.of(value).getBytes())
                        .collect(Collectors.toList()),
                outputBuilder);
    }

    /** Add the given set of arguments to the output Command.Builder. */
    private static void populateCommandWithArgs(
            GlideString[] arguments, Command.Builder outputBuilder) {
        populateCommandWithArgs(
                Arrays.stream(arguments).map(GlideString::getBytes).collect(Collectors.toList()),
                outputBuilder);
    }

    /**
     * Add the given set of arguments to the output Command.Builder. DirectByteBuffer implementation
     * handles large arguments automatically via size-based routing.
     */
    private static void populateCommandWithArgs(
            List<byte[]> arguments, Command.Builder outputBuilder) {
        // Always use ArgsArray - DirectByteBuffer handles large responses, not large requests
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        arguments.forEach(arg -> commandArgs.addArgs(ByteString.copyFrom(arg)));
        outputBuilder.setArgsArray(commandArgs);
    }
}
