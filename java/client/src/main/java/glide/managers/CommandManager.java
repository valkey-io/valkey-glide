/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import command_request.CommandRequestOuterClass.CacheMetricsType;
import command_request.CommandRequestOuterClass.RequestType;
import glide.api.OpenTelemetry;
import glide.api.models.BaseBatch;
import glide.api.models.Batch;
import glide.api.models.BatchCommand;
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
import glide.ffi.resolvers.ClusterScanCursorResolver;
import glide.ffi.resolvers.OpenTelemetryResolver;
import glide.internal.GlideCoreClient;
import glide.utils.Java8Utils;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;
import response.ResponseOuterClass.ConstantResponse;
import response.ResponseOuterClass.Response;

/**
 * CommandManager that submits command requests directly to the Rust glide-core. Handles command
 * serialization, routing, and response processing for all client operations.
 */
public class CommandManager {

    private static final Set<String> BLOCKING_COMMAND_NAMES =
            Collections.unmodifiableSet(
                    Java8Utils.createSet(
                            "BLPOP",
                            "BRPOP",
                            "BLMOVE",
                            "BZPOPMAX",
                            "BZPOPMIN",
                            "BRPOPLPUSH",
                            "BLMPOP",
                            "BZMPOP",
                            "XREAD",
                            "XREADGROUP",
                            "WAIT",
                            "WAITAOF"));

    /** Core client connection. */
    private final GlideCoreClient coreClient;

    public CommandManager(GlideCoreClient coreClient) {
        this.coreClient = coreClient;
    }

    /**
     * Apply a response handler with cleanup on exception. If the handler throws, the stored object in
     * JniResponseRegistry is removed to prevent memory leaks.
     *
     * @param response the Response to process
     * @param responseHandler the handler to apply
     * @return the result from the handler
     * @throws RuntimeException if the handler throws (after cleanup)
     */
    private static <T> T applyHandlerWithCleanup(
            Response response, GlideExceptionCheckedFunction<Response, T> responseHandler) {
        long objectId = response.getRespPointer();
        try {
            return responseHandler.apply(response);
        } catch (RuntimeException e) {
            // Clean up stored object on handler exception to prevent memory leak
            if (objectId != 0L) {
                JniResponseRegistry.remove(objectId);
            }
            throw e;
        }
    }

    /**
     * Apply a response handler with cleanup on exception, using a pre-computed objectId. If the
     * handler throws, the stored object in JniResponseRegistry is removed to prevent memory leaks.
     *
     * @param response the Response to process
     * @param objectId the registry ID to clean up on exception (may be 0 if nothing stored)
     * @param responseHandler the handler to apply
     * @return the result from the handler
     * @throws RuntimeException if the handler throws (after cleanup)
     */
    private static <T> T applyHandlerWithCleanup(
            Response response,
            long objectId,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        try {
            return responseHandler.apply(response);
        } catch (RuntimeException e) {
            // Clean up stored object on handler exception to prevent memory leak
            if (objectId != 0L) {
                JniResponseRegistry.remove(objectId);
            }
            throw e;
        }
    }

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

    /** String args expect UTF-8 response. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitCommandAsync(requestType, stringsToBytes(arguments), null, true, responseHandler);
    }

    /** GlideString args expect binary response. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitCommandAsync(
                requestType, glideStringsToBytes(arguments), null, false, responseHandler);
    }

    /** Submit a command with explicit response type expectation. */
    public <T> CompletableFuture<T> submitNewCommandWithResponseType(
            RequestType requestType,
            GlideString[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean expectUtf8Response) {
        return submitCommandAsync(
                requestType, glideStringsToBytes(arguments), null, expectUtf8Response, responseHandler);
    }

    /** Submit a command with route and explicit response type expectation. */
    public <T> CompletableFuture<T> submitNewCommandWithResponseType(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler,
            boolean expectUtf8Response) {
        return submitCommandAsync(
                requestType, glideStringsToBytes(arguments), route, expectUtf8Response, responseHandler);
    }

    /** Submit a command with route. String args expect UTF-8 response. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            String[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitCommandAsync(requestType, stringsToBytes(arguments), route, true, responseHandler);
    }

    /** Submit a command with route. GlideString args expect binary response. */
    public <T> CompletableFuture<T> submitNewCommand(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitCommandAsync(
                requestType, glideStringsToBytes(arguments), route, false, responseHandler);
    }

    // ==================== BLOCKING COMMAND METHODS ====================
    // These methods skip Java-side timeout because blocking commands (BLPOP, BRPOP, etc.)
    // have their own timeout in the command arguments, which Rust handles correctly.

    /** Submit a blocking command (no Java-side timeout). */
    public <T> CompletableFuture<T> submitBlockingCommand(
            RequestType requestType,
            String[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitBlockingCommandAsync(
                requestType, stringsToBytes(arguments), null, true, responseHandler);
    }

    /** Submit a blocking command (no Java-side timeout). */
    public <T> CompletableFuture<T> submitBlockingCommand(
            RequestType requestType,
            GlideString[] arguments,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitBlockingCommandAsync(
                requestType, glideStringsToBytes(arguments), null, false, responseHandler);
    }

    /** Submit a blocking command with route (no Java-side timeout). */
    public <T> CompletableFuture<T> submitBlockingCommand(
            RequestType requestType,
            String[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitBlockingCommandAsync(
                requestType, stringsToBytes(arguments), route, true, responseHandler);
    }

    /** Submit a blocking command with route (no Java-side timeout). */
    public <T> CompletableFuture<T> submitBlockingCommand(
            RequestType requestType,
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitBlockingCommandAsync(
                requestType, glideStringsToBytes(arguments), route, false, responseHandler);
    }

    // ==================== CUSTOM COMMAND METHODS ====================
    // Custom commands need special handling: if the command name is a blocking command,
    // we skip Java-side timeout; otherwise we use normal timeout handling.

    /** Submit a custom command, detecting if it's a blocking command. */
    public <T> CompletableFuture<T> submitCustomCommand(
            String[] arguments, GlideExceptionCheckedFunction<Response, T> responseHandler) {
        byte[][] args = stringsToBytes(arguments);
        if (isBlockingCustomCommand(arguments)) {
            return submitBlockingCommandAsync(
                    RequestType.CustomCommand, args, null, true, responseHandler);
        }
        return submitCommandAsync(RequestType.CustomCommand, args, null, true, responseHandler);
    }

    /** Submit a custom command with GlideString args, detecting if it's a blocking command. */
    public <T> CompletableFuture<T> submitCustomCommand(
            GlideString[] arguments, GlideExceptionCheckedFunction<Response, T> responseHandler) {
        byte[][] args = glideStringsToBytes(arguments);
        if (isBlockingCustomCommand(arguments)) {
            return submitBlockingCommandAsync(
                    RequestType.CustomCommand, args, null, false, responseHandler);
        }
        return submitCommandAsync(RequestType.CustomCommand, args, null, false, responseHandler);
    }

    /** Submit a custom command with route, detecting if it's a blocking command. */
    public <T> CompletableFuture<T> submitCustomCommand(
            String[] arguments, Route route, GlideExceptionCheckedFunction<Response, T> responseHandler) {
        byte[][] args = stringsToBytes(arguments);
        if (isBlockingCustomCommand(arguments)) {
            return submitBlockingCommandAsync(
                    RequestType.CustomCommand, args, route, true, responseHandler);
        }
        return submitCommandAsync(RequestType.CustomCommand, args, route, true, responseHandler);
    }

    /** Submit a custom command with route and GlideString args, detecting if it's blocking. */
    public <T> CompletableFuture<T> submitCustomCommand(
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        byte[][] args = glideStringsToBytes(arguments);
        if (isBlockingCustomCommand(arguments)) {
            return submitBlockingCommandAsync(
                    RequestType.CustomCommand, args, route, false, responseHandler);
        }
        return submitCommandAsync(RequestType.CustomCommand, args, route, false, responseHandler);
    }

    /** Check if a custom command is a blocking command by inspecting the first argument. */
    private boolean isBlockingCustomCommand(String[] arguments) {
        return arguments != null
                && arguments.length > 0
                && arguments[0] != null
                && BLOCKING_COMMAND_NAMES.contains(arguments[0].toUpperCase());
    }

    /** Check if a custom command is a blocking command by inspecting the first argument. */
    private boolean isBlockingCustomCommand(GlideString[] arguments) {
        return arguments != null
                && arguments.length > 0
                && arguments[0] != null
                && BLOCKING_COMMAND_NAMES.contains(arguments[0].toString().toUpperCase());
    }

    /** Specialized path for ObjectEncoding with GlideString args but textual response. */
    public <T> CompletableFuture<T> submitObjectEncoding(
            GlideString[] arguments, GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitCommandAsync(
                RequestType.ObjectEncoding, glideStringsToBytes(arguments), null, true, responseHandler);
    }

    /** Specialized path for ObjectEncoding with route. */
    public <T> CompletableFuture<T> submitObjectEncoding(
            GlideString[] arguments,
            Route route,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        return submitCommandAsync(
                RequestType.ObjectEncoding, glideStringsToBytes(arguments), route, true, responseHandler);
    }

    /** Submit a Batch. */
    public <T> CompletableFuture<T> submitNewBatch(
            Batch batch,
            boolean raiseOnError,
            Optional<BatchOptions> options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        boolean expectUtf8Response = !batch.isBinaryOutput();
        int timeout = options.map(BaseBatchOptions::getTimeout).orElse(0);
        return submitBatchAsync(
                batch, raiseOnError, timeout, false, false, null, expectUtf8Response, responseHandler);
    }

    /** Build a Script (by hash) request to send to Valkey. */
    public <T> CompletableFuture<T> submitScript(
            Script script,
            List<GlideString> keys,
            List<GlideString> args,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        if (!coreClient.isConnected()) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
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
                    .thenApply(result -> buildResponseFromJniResult(result, expectUtf8Response))
                    .thenApply(response -> applyHandlerWithCleanup(response, responseHandler))
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
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
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
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
            DirectRouteArgs routeArgs = computeRouteArgs(route);

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
                    .thenApply(result -> buildResponseFromJniResult(result, expectUtf8Response))
                    .thenApply(response -> applyHandlerWithCleanup(response, responseHandler))
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /** Lightweight container for direct routing arguments. */
    private static final class DirectRouteArgs {
        final boolean hasRoute;
        final int routeType;
        final String routeParam;

        DirectRouteArgs(boolean hasRoute, int routeType, String routeParam) {
            this.hasRoute = hasRoute;
            this.routeType = routeType;
            this.routeParam = routeParam;
        }
    }

    /** Centralized mapping from Route to direct routing tuple. */
    private static DirectRouteArgs computeRouteArgs(Route route) {
        if (route == null) {
            return new DirectRouteArgs(false, 0, null);
        }
        if (route instanceof SimpleMultiNodeRoute) {
            return new DirectRouteArgs(true, ((SimpleMultiNodeRoute) route).getOrdinal(), null);
        }
        if (route instanceof SimpleSingleNodeRoute) {
            return new DirectRouteArgs(true, ((SimpleSingleNodeRoute) route).getOrdinal(), null);
        }
        if (route instanceof SlotKeyRoute) {
            int routeType = ((SlotKeyRoute) route).getSlotType().ordinal();
            String routeParam = ((SlotKeyRoute) route).getSlotKey();
            return new DirectRouteArgs(true, routeType, routeParam);
        }
        if (route instanceof SlotIdRoute) {
            // Offset by 100 to distinguish from SlotKeyRoute on the native side
            int routeType = 100 + ((SlotIdRoute) route).getSlotType().ordinal();
            String routeParam = Integer.toString(((SlotIdRoute) route).getSlotId());
            return new DirectRouteArgs(true, routeType, routeParam);
        }
        if (route instanceof ByAddressRoute) {
            String hostPort =
                    ((ByAddressRoute) route).getHost() + ":" + ((ByAddressRoute) route).getPort();
            return new DirectRouteArgs(true, -1, hostPort);
        }
        throw new RequestException(
                String.format("Unknown type of route: %s", route.getClass().getSimpleName()));
    }

    /** Submit a Cluster Batch. */
    public <T> CompletableFuture<T> submitNewBatch(
            ClusterBatch batch,
            boolean raiseOnError,
            Optional<ClusterBatchOptions> options,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {
        boolean expectUtf8Response = !batch.isBinaryOutput();
        int timeout = options.map(BaseBatchOptions::getTimeout).orElse(0);
        boolean retryServerError = false;
        boolean retryConnectionError = false;
        Route route = null;
        if (options.isPresent()) {
            ClusterBatchOptions opts = options.get();
            if (opts.getRetryStrategy() != null) {
                if (batch.isAtomic()) {
                    throw new RequestException("Retry strategy is not supported for atomic batches.");
                }
                retryServerError = opts.getRetryStrategy().isRetryServerError();
                retryConnectionError = opts.getRetryStrategy().isRetryConnectionError();
            }
            route = opts.getRoute();
        }
        return submitBatchAsync(
                batch,
                raiseOnError,
                timeout,
                retryServerError,
                retryConnectionError,
                route,
                expectUtf8Response,
                responseHandler);
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
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
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
                                                ClusterScanCursorResolver.getFinishedCursorHandleConstant(), new Object[0]
                                            };
                                } else {
                                    // Normalize cluster scan result: ensure cursor is String and
                                    // items decode as String (UTF-8) or GlideString (binary)
                                    normalized = normalizeScanResult(result, expectUtf8Response);
                                }
                                long objectId = JniResponseRegistry.storeObject(normalized);
                                builder.setRespPointer(objectId);
                                try {
                                    T out = responseHandler.apply(builder.build());
                                    if (out == null) {
                                        @SuppressWarnings("unchecked")
                                        T fallback =
                                                (T)
                                                        new Object[] {
                                                            ClusterScanCursorResolver.getFinishedCursorHandleConstant(),
                                                            new Object[0]
                                                        };
                                        return fallback;
                                    }
                                    return out;
                                } catch (RuntimeException e) {
                                    // Clean up stored object on handler exception to prevent memory leak
                                    JniResponseRegistry.remove(objectId);
                                    throw e;
                                }
                            })
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
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
            cursor = new String((byte[]) cursorObj, StandardCharsets.UTF_8);
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
                        items[i] = new String((byte[]) items[i], StandardCharsets.UTF_8);
                    }
                }
                return new Object[] {cursor, items};
            } else {
                // Binary path: convert byte[] to GlideString for nice toString()
                for (int i = 0; i < items.length; i++) {
                    if (items[i] instanceof byte[]) {
                        items[i] = GlideString.gs((byte[]) items[i]);
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

    /** Submit a cache metrics request to GLIDE core. */
    public <T> CompletableFuture<T> submitGetCacheMetrics(
            CacheMetricsType metricsType, GlideExceptionCheckedFunction<Response, T> responseHandler) {

        return coreClient
                .getCacheMetrics(metricsType)
                .thenApply(
                        result -> {
                            // Convert JNI result to protobuf Response format
                            Response.Builder responseBuilder = Response.newBuilder();
                            if (result != null) {
                                long objectId = JniResponseRegistry.storeObject(result);
                                responseBuilder.setRespPointer(objectId);
                            } else {
                                responseBuilder.setRespPointer(0L);
                            }
                            return applyHandlerWithCleanup(responseBuilder.build(), responseHandler);
                        });
    }

    /** Submit a command asynchronously via JNI. */
    protected <T> CompletableFuture<T> submitCommandAsync(
            RequestType requestType,
            byte[][] args,
            Route route,
            boolean expectUtf8Response,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        if (!coreClient.isConnected()) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit command."));
            return errorFuture;
        }

        try {
            DirectRouteArgs routeArgs = computeRouteArgs(route);

            long spanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(requestType.name());
            }

            CompletableFuture<Object> jniFuture =
                    coreClient.executeCommandAsync(
                            requestType.getNumber(),
                            args,
                            routeArgs.hasRoute,
                            routeArgs.routeType,
                            routeArgs.routeParam,
                            expectUtf8Response,
                            coreClient.getRequestTimeoutMillis(),
                            spanPtr);

            return jniFuture
                    .thenApply(result -> buildResponseFromJniResult(result, expectUtf8Response))
                    .thenApply(response -> applyHandlerWithCleanup(response, responseHandler))
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /** Submit a blocking command asynchronously via JNI (timeout=0, Rust handles timeout). */
    protected <T> CompletableFuture<T> submitBlockingCommandAsync(
            RequestType requestType,
            byte[][] args,
            Route route,
            boolean expectUtf8Response,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        if (!coreClient.isConnected()) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit command."));
            return errorFuture;
        }

        try {
            DirectRouteArgs routeArgs = computeRouteArgs(route);

            long spanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(requestType.name());
            }

            CompletableFuture<Object> jniFuture =
                    coreClient.executeCommandAsync(
                            requestType.getNumber(),
                            args,
                            routeArgs.hasRoute,
                            routeArgs.routeType,
                            routeArgs.routeParam,
                            expectUtf8Response,
                            0,
                            spanPtr);

            return jniFuture
                    .thenApply(result -> buildResponseFromJniResult(result, expectUtf8Response))
                    .thenApply(response -> applyHandlerWithCleanup(response, responseHandler))
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    /**
     * Build a Response from JNI result, storing the result in JniResponseRegistry if needed.
     *
     * @param result the raw result from JNI
     * @param expectUtf8Response whether to expect UTF-8 encoded response
     * @return the built Response
     */
    private Response buildResponseFromJniResult(Object result, boolean expectUtf8Response) {
        Response.Builder builder = Response.newBuilder();
        Object toStore = result;
        if (result == null) {
            builder.setRespPointer(0L);
        } else if ("OK".equals(result)) {
            builder.setConstantResponse(ConstantResponse.OK);
        } else {
            if (result instanceof ByteBuffer) {
                toStore =
                        DirectBufferResolver.normalizeDirectBuffer((ByteBuffer) result, expectUtf8Response);
            }
            long objectId = JniResponseRegistry.storeObject(toStore);
            builder.setRespPointer(objectId);
        }
        return builder.build();
    }

    // Removed blocking command detection - Rust handles all timeout logic

    /** Submit a batch of commands asynchronously via JNI. */
    private <T> CompletableFuture<T> submitBatchAsync(
            BaseBatch<?> batch,
            boolean raiseOnError,
            int timeout,
            boolean retryServerError,
            boolean retryConnectionError,
            Route route,
            boolean expectUtf8Response,
            GlideExceptionCheckedFunction<Response, T> responseHandler) {

        if (!coreClient.isConnected()) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
            errorFuture.completeExceptionally(
                    new ClosingException("Client closed: Unable to submit batch."));
            return errorFuture;
        }

        try {
            // Extract commands directly from BaseBatch
            List<BatchCommand> batchCommands = batch.getCommands();
            int cmdCount = batchCommands.size();
            int[] requestTypes = new int[cmdCount];
            byte[][][] allArgs = new byte[cmdCount][][];
            for (int i = 0; i < cmdCount; i++) {
                BatchCommand cmd = batchCommands.get(i);
                requestTypes[i] = cmd.getRequestType();
                allArgs[i] = cmd.getArgs();
            }

            boolean isAtomic = batch.isAtomic();
            DirectRouteArgs routeArgs = computeRouteArgs(route);
            long timeoutMs = timeout > 0 ? timeout : coreClient.getRequestTimeoutMillis();

            long spanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                spanPtr = OpenTelemetryResolver.createLeakedOtelSpan("Batch");
            }

            return coreClient
                    .executeBatchAsync(
                            requestTypes,
                            allArgs,
                            isAtomic,
                            raiseOnError,
                            timeout,
                            retryServerError,
                            retryConnectionError,
                            routeArgs.hasRoute,
                            routeArgs.routeType,
                            routeArgs.routeParam,
                            expectUtf8Response,
                            timeoutMs,
                            spanPtr)
                    .thenApply(result -> buildResponseFromJniResult(result, expectUtf8Response))
                    .thenApply(response -> applyHandlerWithCleanup(response, responseHandler))
                    .exceptionally(this::exceptionHandler);
        } catch (Exception e) {
            CompletableFuture<T> errorFuture = new CompletableFuture<T>();
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

    /** Convert String[] to byte[][] for direct JNI passing. */
    private static byte[][] stringsToBytes(String[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
        }
        byte[][] result = new byte[arguments.length][];
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] == null) {
                throw new NullPointerException("Argument cannot be null");
            }
            result[i] = arguments[i].getBytes(StandardCharsets.UTF_8);
        }
        return result;
    }

    /** Convert GlideString[] to byte[][] for direct JNI passing. */
    private static byte[][] glideStringsToBytes(GlideString[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
        }
        byte[][] result = new byte[arguments.length][];
        for (int i = 0; i < arguments.length; i++) {
            if (arguments[i] == null) {
                throw new NullPointerException("Argument cannot be null");
            }
            result[i] = arguments[i].getBytes();
        }
        return result;
    }
}
