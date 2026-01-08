/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import command_request.CommandRequestOuterClass;
import command_request.CommandRequestOuterClass.Command;
import command_request.CommandRequestOuterClass.Command.ArgsArray;
import command_request.CommandRequestOuterClass.CommandRequest;
import command_request.CommandRequestOuterClass.RequestType;
import command_request.CommandRequestOuterClass.Routes;
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
import glide.utils.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

/**
 * CommandManager that submits command requests directly to the Rust glide-core. Handles command
 * serialization, routing, and response processing for all client operations.
 */
@RequiredArgsConstructor
public class CommandManager {

    /** Core client connection. */
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
         * Returns the cursor ID for the bridge.
         *
         * @return the cursor ID string.
         */
        String getCursorId();
    }

    /** Build a command and submit it. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments);
        return submitCommandToJni(
                command, responseHandler, false, true); // String arguments -> expect UTF-8 response
    }

    /** Build a command and submit it. */
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

    /** Build a command with route and explicit response type expectation. */
    public <T> CompletableFuture<T> submitNewCommandWithResponseType(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean expectUtf8Response) {
        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments, route);
        return submitCommandToJni(command, responseHandler, true, expectUtf8Response);
    }

    /** Build a command and submit it. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments, route);
        return submitCommandToJni(
                command, responseHandler, false, true); // String arguments -> expect UTF-8 response
    }

    /** Build a command and submit it. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        CommandRequest.Builder command = prepareCommandRequest(requestType, arguments, route);
        return submitCommandToJni(
                command, responseHandler, true, false); // GlideString arguments -> expect binary response
    }

    /** Specialized path for ObjectEncoding with GlideString args but textual response. */
    public <T> CompletableFuture<T> submitObjectEncoding(
            GlideString[] arguments, GlideExceptionCheckedFunction<Response, T> responseHandler) {
        CommandRequest.Builder command = prepareCommandRequest(RequestType.ObjectEncoding, arguments);
        return submitCommandToJni(command, responseHandler, true, true);
    }

    /** Specialized path for ObjectEncoding with route. */
    public <T> CompletableFuture<T> submitObjectEncoding(
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        CommandRequest.Builder command =
                prepareCommandRequest(RequestType.ObjectEncoding, arguments, route);
        return submitCommandToJni(command, responseHandler, true, true);
    }

    /** Build a Batch and submit it. */
    public <T> CompletableFuture<T> submitNewBatch(
            Batch batch,
            boolean raiseOnError,
            Optional<BatchOptions> options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        CommandRequest.Builder command = prepareCommandRequest(batch, raiseOnError, options);
        boolean expectUtf8Response = !batch.isBinaryOutput();
        return submitBatchToJni(command, responseHandler, expectUtf8Response);
    }

    /** Build a Script (by hash) request to send to Valkey. */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<GlideString> keys,
            List<GlideString> args,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        if (!coreClient.isConnected()) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit script."));
            return errorFuture;
        }

        try {
            byte[][] keyArgs = toByteMatrix(keys);
            byte[][] argArgs = toByteMatrix(args);

            final boolean expectUtf8Response =
                    script.getBinaryOutput() == null || !script.getBinaryOutput();

            CompletableFuture<Object> jniFuture =
                    coreClient.executeScriptAsync(
                            script.getHash(),
                            keyArgs,
                            argArgs, /* hasRoute */
                            false, /* routeType */
                            0, /* routeParam */
                            null,
                            expectUtf8Response);

            return jniFuture
                    .thenApply(result -> createDirectResponse(result, expectUtf8Response))
                    .thenApply(responseHandler::apply)
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /** Build a Script (by hash) request with route to send to Valkey. */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<GlideString> args,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        if (!coreClient.isConnected()) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit script."));
            return errorFuture;
        }

        try {
            byte[][] keyArgs = GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
            byte[][] argArgs = toByteMatrix(args);
            final boolean expectUtf8Response =
                    script.getBinaryOutput() == null || !script.getBinaryOutput();

            // Map Route to simple route tuple via centralized helper
            ScriptRouteArgs routeArgs = computeScriptRouteArgs(route);

            CompletableFuture<Object> jniFuture =
                    coreClient.executeScriptAsync(
                            script.getHash(),
                            keyArgs,
                            argArgs,
                            routeArgs.hasRoute,
                            routeArgs.routeType,
                            routeArgs.routeParam,
                            expectUtf8Response);

            return jniFuture
                    .thenApply(result -> createDirectResponse(result, expectUtf8Response))
                    .thenApply(responseHandler::apply)
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /** Lightweight container for script routing arguments. */
    private static final class ScriptRouteArgs {
        final boolean hasRoute;
        final int routeType;
        final String routeParam;

        ScriptRouteArgs(boolean hasRoute, int routeType, String routeParam) {
            this.hasRoute = hasRoute;
            this.routeType = routeType;
            this.routeParam = routeParam;
        }
    }

    /** Centralized mapping from RouteInfo to script routing tuple. */
    private ScriptRouteArgs computeScriptRouteArgs(Route route) {
        if (route == null) {
            return new ScriptRouteArgs(false, 0, null);
        }
        if (route instanceof SimpleMultiNodeRoute) {
            return new ScriptRouteArgs(true, ((SimpleMultiNodeRoute) route).getOrdinal(), null);
        }
        if (route instanceof SimpleSingleNodeRoute) {
            return new ScriptRouteArgs(true, ((SimpleSingleNodeRoute) route).getOrdinal(), null);
        }
        if (route instanceof SlotKeyRoute) {
            int routeType = ((SlotKeyRoute) route).getSlotType().ordinal();
            String routeParam = ((SlotKeyRoute) route).getSlotKey();
            return new ScriptRouteArgs(true, routeType, routeParam);
        }
        if (route instanceof SlotIdRoute) {
            int routeType = ((SlotIdRoute) route).getSlotType().ordinal();
            String routeParam = Integer.toString(((SlotIdRoute) route).getSlotId());
            return new ScriptRouteArgs(true, routeType, routeParam);
        }
        if (route instanceof ByAddressRoute) {
            String hostPort =
                    ((ByAddressRoute) route).getHost() + ":" + ((ByAddressRoute) route).getPort();
            return new ScriptRouteArgs(true, -1, hostPort); // -1 => ByAddress special case
        }
        throw new RequestException(
                String.format(
                        "Unsupported route type for script invocation: %s", route.getClass().getSimpleName()));
    }

    /** Build a Cluster Batch and submit it. */
    public <T> CompletableFuture<T> submitNewBatch(
            ClusterBatch batch,
            boolean raiseOnError,
            Optional<ClusterBatchOptions> options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        CommandRequest.Builder command = prepareCommandRequest(batch, raiseOnError, options);
        boolean expectUtf8Response = !batch.isBinaryOutput();
        return submitBatchToJni(command, responseHandler, expectUtf8Response);
    }

    private static byte[][] toByteMatrix(List<GlideString> values) {
        if (values == null || values.isEmpty()) {
            return GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
        }
        byte[][] result = new byte[values.size()][];
        for (int i = 0; i < values.size(); i++) {
            GlideString value = values.get(i);
            result[i] = value != null ? value.getBytes() : null;
        }
        return result;
    }

    /** Submit a scan request with cursor. */
    public <T> CompletableFuture<T> submitClusterScan(
            ClusterScanCursor cursor,
            @NonNull ScanOptions options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitClusterScanToJni(cursor, options, responseHandler, true);
    }

    /** Internal: Submit a scan request with explicit response encoding expectation. */
    public <T> CompletableFuture<T> submitClusterScanToJni(
            ClusterScanCursor cursor,
            @NonNull ScanOptions options,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean expectUtf8Response) {

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
            ScanOptions.ObjectType type = options.getType();
            String objectType = null;
            if (type != null) {
                objectType = type.getNativeName();
                if (objectType == null || objectType.isEmpty()) {
                    objectType = type.name();
                }
            }

            // Execute via enhanced cluster scan bridge
            return coreClient
                    .executeClusterScanAsync(
                            cursorId, matchPattern, count != null ? count : 0L, objectType, expectUtf8Response)
                    .thenApply(
                            result -> {
                                // Create a minimal Response for compatibility with the handler
                                Response.Builder builder = Response.newBuilder();
                                Object normalized;
                                if (result == null) {
                                    normalized =
                                            new Object[] {
                                                glide.ffi.resolvers.ClusterScanCursorResolver
                                                        .getFinishedCursorHandleConstant(),
                                                new Object[0]
                                            };
                                } else {
                                    // Normalize cluster scan result: ensure cursor is String and
                                    // items decode as String (UTF-8) or GlideString (binary)
                                    normalized = normalizeScanResult(result, expectUtf8Response);
                                }
                                long objectId = JniResponseRegistry.storeObject(normalized);
                                builder.setRespPointer(objectId);
                                T out = responseHandler.apply(builder.build());
                                if (out == null) {
                                    @SuppressWarnings("unchecked")
                                    T fallback =
                                            (T)
                                                    new Object[] {
                                                        glide.ffi.resolvers.ClusterScanCursorResolver
                                                                .getFinishedCursorHandleConstant(),
                                                        new Object[0]
                                                    };
                                    return fallback;
                                }
                                return out;
                            })
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            var errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    // Ensure scan result shape is [String cursor, Object[] items] and element encoding matches
    // expectation
    private Object normalizeScanResult(Object result, boolean expectUtf8) {
        if (!(result instanceof Object[])) {
            return result;
        }
        Object[] arr = (Object[]) result;
        if (arr.length != 2) {
            return result;
        }
        // Normalize cursor to String
        Object cursorObj = arr[0];
        String cursor;
        if (cursorObj instanceof byte[]) {
            cursor = new String((byte[]) cursorObj, java.nio.charset.StandardCharsets.UTF_8);
        } else {
            cursor = String.valueOf(cursorObj);
        }

        // Normalize items array
        Object itemsObj = arr[1];
        if (itemsObj instanceof Object[]) {
            Object[] items = (Object[]) itemsObj;
            if (expectUtf8) {
                // Convert any stray byte[] to UTF-8 Strings
                for (int i = 0; i < items.length; i++) {
                    if (items[i] instanceof byte[]) {
                        items[i] = new String((byte[]) items[i], java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                return new Object[] {cursor, items};
            } else {
                // Binary path: convert byte[] to GlideString for nice toString()
                for (int i = 0; i < items.length; i++) {
                    if (items[i] instanceof byte[]) {
                        items[i] = glide.api.models.GlideString.gs((byte[]) items[i]);
                    }
                }
                return new Object[] {cursor, items};
            }
        }
        return new Object[] {cursor, itemsObj};
    }

    /** Submit a password update request to GLIDE core. */
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

    /** Submit an IAM token refresh request to GLIDE core. */
    public <T> CompletableFuture<T> submitRefreshIamToken(
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        return coreClient
                .refreshIamToken()
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

    /** Take a command request and submit it (backward compatibility). */
    protected <T> CompletableFuture<T> submitCommandToJni(
            CommandRequest.Builder command,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean binaryMode) {
        // For backward compatibility, default expectUtf8Response based on binaryMode
        // binaryMode=true means GlideString args, expect binary response
        // binaryMode=false means String args, expect UTF-8 response
        return submitCommandToJni(command, responseHandler, binaryMode, !binaryMode);
    }

    /** Take a command request and submit it. */
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
                                Response.Builder builder = Response.newBuilder();
                                Object toStore = result;
                                if (result == null) {
                                    builder.setRespPointer(0L);
                                } else if ("OK".equals(result)) {
                                    builder.setConstantResponse(ConstantResponse.OK);
                                } else {
                                    if (result instanceof ByteBuffer) {
                                        toStore = normalizeDirectBuffer((ByteBuffer) result, expectUtf8Response);
                                    }
                                    long objectId = JniResponseRegistry.storeObject(toStore);
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

    private Object normalizeDirectBuffer(ByteBuffer buffer, boolean expectUtf8Response) {
        ByteBuffer dup = buffer.duplicate();
        dup.rewind();
        if (dup.remaining() == 0) {
            return expectUtf8Response ? "" : glide.api.models.GlideString.gs(new byte[0]);
        }
        byte marker = dup.get();
        dup.rewind();
        if (marker == '*') {
            // Serialized array/map (custom wire format)
            return deserializeByteBufferArray(dup, expectUtf8Response);
        } else if (marker == '%') {
            return deserializeByteBufferMap(dup, expectUtf8Response);
        }
        // Bulk string bytes
        if (expectUtf8Response) {
            // Decode UTF-8 directly from buffer
            return BufferUtils.decodeUtf8(dup);
        } else {
            byte[] bytes = new byte[dup.remaining()];
            dup.get(bytes);
            return glide.api.models.GlideString.gs(bytes);
        }
    }

    /**
     * Deserialize a ByteBuffer containing a serialized map back to Map<?,?>. Format: '%' + count(u32
     * BE) + repeated [keyLen(u32) + keyBytes + valLen(u32) + valBytes]
     */
    private java.util.LinkedHashMap<Object, Object> deserializeByteBufferMap(
            ByteBuffer buffer, boolean expectUtf8) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.rewind();

        byte marker = buffer.get();
        if (marker != '%') {
            throw new IllegalArgumentException("Expected map marker '%', got: " + (char) marker);
        }
        int count = buffer.getInt();
        java.util.LinkedHashMap<Object, Object> map =
                new java.util.LinkedHashMap<>(Math.max(16, count));
        for (int i = 0; i < count; i++) {
            int klen = buffer.getInt();
            Object key;
            if (expectUtf8) {
                // Decode UTF-8 directly from buffer
                key = BufferUtils.decodeUtf8(buffer, klen);
            } else {
                byte[] kbytes = new byte[klen];
                buffer.get(kbytes);
                key = glide.api.models.GlideString.gs(kbytes);
            }

            int vlen = buffer.getInt();
            Object val;
            if (expectUtf8) {
                // Decode UTF-8 directly from buffer
                val = BufferUtils.decodeUtf8(buffer, vlen);
            } else {
                byte[] vbytes = new byte[vlen];
                buffer.get(vbytes);
                val = glide.api.models.GlideString.gs(vbytes);
            }
            map.put(key, val);
        }
        return map;
    }

    // Removed blocking command detection - Rust handles all timeout logic

    /** Submit batch request via JNI. */
    protected <T> CompletableFuture<T> submitBatchToJni(
            CommandRequest.Builder command,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean expectUtf8Response) {

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
                    .executeBatchAsync(requestBytes, expectUtf8Response)
                    .thenApply(result -> convertJniToProtobufResponse(result, expectUtf8Response))
                    .thenApply(responseHandler::apply)
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
    private Response convertJniToProtobufResponse(Object jniResult, boolean expectUtf8Response) {
        Response.Builder builder = Response.newBuilder();

        if (jniResult == null) {
            // Null response - set pointer to 0
            builder.setRespPointer(0L);
        } else if ("OK".equals(jniResult)) {
            // OK constant response
            builder.setConstantResponse(ConstantResponse.OK);
        } else if (jniResult instanceof ByteBuffer) {
            // DirectByteBuffer from JNI. Could be a serialized array/map or a large bulk string.
            ByteBuffer buffer = (ByteBuffer) jniResult;
            ByteBuffer dup = buffer.duplicate();
            dup.rewind();
            Object toStore;
            if (dup.remaining() > 0) {
                byte marker = dup.get();
                if (marker == '*') {
                    dup.rewind();
                    toStore = deserializeByteBufferArray(dup, expectUtf8Response);
                } else if (marker == '%') {
                    dup.rewind();
                    toStore = deserializeByteBufferMap(dup, expectUtf8Response);
                } else {
                    dup.rewind();
                    if (expectUtf8Response) {
                        toStore = BufferUtils.decodeUtf8(dup);
                    } else {
                        byte[] bytes = new byte[dup.remaining()];
                        dup.get(bytes);
                        toStore = glide.api.models.GlideString.gs(bytes);
                    }
                }
            } else {
                toStore = expectUtf8Response ? "" : glide.api.models.GlideString.gs(new byte[0]);
            }
            long objectId = JniResponseRegistry.storeObject(toStore);
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
    private Response createDirectResponse(Object jniResult, boolean expectUtf8Response) {
        Response.Builder builder = Response.newBuilder();

        if (jniResult == null) {
            // Null response - no pointer needed
            builder.setRespPointer(0L);
        } else if ("OK".equals(jniResult)) {
            // OK constant response
            builder.setConstantResponse(ConstantResponse.OK);
        } else if (jniResult instanceof ByteBuffer) {
            // DirectByteBuffer from JNI. Could be serialized array/map or large bulk string.
            ByteBuffer buffer = (ByteBuffer) jniResult;
            ByteBuffer dup = buffer.duplicate();
            dup.rewind();
            Object toStore;
            if (dup.remaining() > 0) {
                byte marker = dup.get();
                if (marker == '*') {
                    dup.rewind();
                    toStore = deserializeByteBufferArray(dup, expectUtf8Response);
                } else if (marker == '%') {
                    dup.rewind();
                    toStore = deserializeByteBufferMap(dup, expectUtf8Response);
                } else {
                    dup.rewind();
                    if (expectUtf8Response) {
                        toStore = BufferUtils.decodeUtf8(dup);
                    } else {
                        byte[] bytes = new byte[dup.remaining()];
                        dup.get(bytes);
                        toStore = glide.api.models.GlideString.gs(bytes);
                    }
                }
            } else {
                toStore = expectUtf8Response ? "" : glide.api.models.GlideString.gs(new byte[0]);
            }
            long objectId = JniResponseRegistry.storeObject(toStore);
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
    private Object[] deserializeByteBufferArray(ByteBuffer buffer, boolean expectUtf8Response) {
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
                        if (expectUtf8Response) {
                            // Decode UTF-8 directly from buffer
                            result[i] = BufferUtils.decodeUtf8(buffer, bulkLen);
                        } else {
                            byte[] data = new byte[bulkLen];
                            buffer.get(data);
                            result[i] = glide.api.models.GlideString.gs(data);
                        }
                    }
                    break;

                case '+': // Simple string (includes "OK")
                    int simpleLen = buffer.getInt();
                    // Simple strings are always UTF-8
                    String simpleString = BufferUtils.decodeUtf8(buffer, simpleLen);
                    result[i] = simpleString.equalsIgnoreCase("ok") ? "OK" : simpleString;
                    break;

                case ':': // Integer
                    long intValue = buffer.getLong();
                    result[i] = intValue;
                    break;

                case '#': // Complex type (serialized as string)
                    int complexLen = buffer.getInt();
                    if (expectUtf8Response) {
                        // Decode UTF-8 directly from buffer
                        result[i] = BufferUtils.decodeUtf8(buffer, complexLen);
                    } else {
                        byte[] complexData = new byte[complexLen];
                        buffer.get(complexData);
                        result[i] = glide.api.models.GlideString.gs(complexData);
                    }
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
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (ArgType value : arguments) {
            appendArgument(commandArgs, value);
        }
        outputBuilder.setArgsArray(commandArgs);
    }

    /** Add the given set of arguments to the output Command.Builder. */
    private static void populateCommandWithArgs(
            GlideString[] arguments, Command.Builder outputBuilder) {
        ArgsArray.Builder commandArgs = ArgsArray.newBuilder();
        for (GlideString argument : arguments) {
            if (argument == null) {
                throw new NullPointerException("Argument cannot be null");
            }
            commandArgs.addArgs(UnsafeByteOperations.unsafeWrap(argument.getBytes()));
        }
        outputBuilder.setArgsArray(commandArgs);
    }

    private static void appendArgument(ArgsArray.Builder commandArgs, Object value) {
        if (value instanceof GlideString) {
            commandArgs.addArgs(UnsafeByteOperations.unsafeWrap(((GlideString) value).getBytes()));
        } else if (value instanceof byte[]) {
            commandArgs.addArgs(UnsafeByteOperations.unsafeWrap((byte[]) value));
        } else if (value instanceof String) {
            commandArgs.addArgs(ByteString.copyFromUtf8((String) value));
        } else {
            if (value == null) {
                throw new NullPointerException("Argument cannot be null");
            }
            commandArgs.addArgs(ByteString.copyFromUtf8(value.toString()));
        }
    }
}
