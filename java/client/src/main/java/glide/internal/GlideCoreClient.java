/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import command_request.CommandRequestOuterClass.CacheMetricsType;
import command_request.CommandRequestOuterClass.RequestType;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.api.BaseClient;
import glide.api.logging.Logger;
import glide.api.models.GlideString;
import glide.api.models.exceptions.ClosingException;
import glide.ffi.resolvers.NativeUtils;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * GLIDE core client transport. Provides direct native access to glide-core with all routing and
 * performance optimizations.
 *
 * <p>This class wraps an existing native client handle created by ConnectionManager. It does NOT
 * create connections - that responsibility belongs to ConnectionManager.
 */
public class GlideCoreClient implements AutoCloseable {

    private static final ReferenceQueue<Object> CLEANUP_QUEUE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<PhantomReference<?>, Runnable> CLEANUP_ACTIONS =
            new ConcurrentHashMap<>();

    static {
        // Start cleanup thread
        Thread cleanupThread =
                new Thread(
                        () -> {
                            while (true) {
                                try {
                                    PhantomReference<?> ref = (PhantomReference<?>) CLEANUP_QUEUE.remove();
                                    Runnable action = CLEANUP_ACTIONS.remove(ref);
                                    if (action != null) {
                                        action.run();
                                    }
                                    ref.clear();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                } catch (Exception e) {
                                    // Log but don't stop cleanup thread
                                    Logger.log(
                                            Logger.Level.WARN,
                                            "GlideCoreClient-Cleanup",
                                            "Error in cleanup thread: " + e.getMessage());
                                }
                            }
                        },
                        "GlideCoreClient-Cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    static {
        // Load the native library
        try {
            NativeUtils.loadGlideLib();
        } catch (Exception e) {
            glide.api.logging.Logger.log(
                    glide.api.logging.Logger.Level.ERROR,
                    "GlideCoreClient",
                    "Failed to load native library: " + e.getMessage());
            throw new RuntimeException("Failed to load native library", e);
        }
        onNativeInit();
    }

    private static native void onNativeInit();

    private static native void freeNativeBuffer(long id);

    /**
     * Execute MGET through the immediate-release response path.
     *
     * <p>This entry point is private and fixes the request type to MGET on the Rust side. Its native
     * buffer is consumed synchronously by one of this class's built-in typed decoders before JNI
     * returns, so the buffer cannot escape to a caller.
     */
    private static native void executeMgetCommandAsyncNative(
            long clientPtr,
            long callbackId,
            byte[][] args,
            byte[] packedArgs,
            boolean expectUtf8Response,
            long spanPtr);

    private static final ConcurrentHashMap<Long, WeakReference<BaseClient>> clients =
            new ConcurrentHashMap<>();

    /**
     * Empty 2D byte array constant for reuse in various contexts (script params, subPattern, etc.)
     */
    public static final byte[][] EMPTY_2D_BYTE_ARRAY = new byte[0][];

    public static void registerClient(long handle, BaseClient client) {
        clients.put(handle, new WeakReference<>(client));
    }

    public static void unregisterClient(long handle) {
        clients.remove(handle);
    }

    // Called by native on push (binary-safe)
    private static void onNativePush(long handle, byte[] message, byte[] channel, byte[] pattern) {
        glide.api.models.GlideString msg = glide.api.models.GlideString.of(message);
        glide.api.models.GlideString ch = glide.api.models.GlideString.of(channel);
        glide.api.models.PubSubMessage m =
                (pattern != null && pattern.length > 0)
                        ? new glide.api.models.PubSubMessage(msg, ch, glide.api.models.GlideString.of(pattern))
                        : new glide.api.models.PubSubMessage(msg, ch);
        WeakReference<BaseClient> ref = clients.get(handle);
        if (ref != null) {
            BaseClient c = ref.get();
            if (c != null) c.__enqueuePubSubMessage(m);
        }
    }

    // Register cleanup action to free native memory when the given ByteBuffer is GC'd
    static void registerNativeBufferCleaner(java.nio.ByteBuffer buffer, long id) {
        if (buffer == null || id == 0) return;
        PhantomReference<java.nio.ByteBuffer> ref = new PhantomReference<>(buffer, CLEANUP_QUEUE);
        CLEANUP_ACTIONS.put(
                ref,
                () -> {
                    try {
                        freeNativeBuffer(id);
                    } catch (Throwable ignored) {
                    }
                });
    }

    /** Handle for the native client resource. */
    private final AtomicLong nativeClientHandle = new AtomicLong(0);

    public long getNativeHandle() {
        return nativeClientHandle.get();
    }

    /** Maximum number of inflight requests allowed for this client. */
    private final int maxInflightRequests;

    /** Request timeout in milliseconds for Java-side timeout detection. */
    private final long requestTimeoutMillis;

    public int getMaxInflightRequests() {
        return maxInflightRequests;
    }

    public long getRequestTimeoutMillis() {
        return requestTimeoutMillis;
    }

    /** Cleanup coordination flag. */
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    /** Phantom reference to ensure native cleanup. */
    private final PhantomReference<GlideCoreClient> cleanupRef;

    /** Shared state for cleanup coordination. */
    private final NativeState nativeState;

    /**
     * Constructor that wraps an existing native client handle (for BaseClient integration). This is
     * the ONLY constructor - GlideCoreClient does not create connections.
     *
     * @param existingHandle Native client handle from ConnectionManager
     * @param maxInflight Maximum inflight requests (0 = use native defaults)
     * @param requestTimeoutMs Request timeout in milliseconds for Java-side timeout detection
     */
    @SuppressFBWarnings(
            value = "CT_CONSTRUCTOR_THROW",
            justification = "Constructor fails fast on invalid handles prior to registering resources")
    public GlideCoreClient(long existingHandle, int maxInflight, long requestTimeoutMs) {
        if (existingHandle == 0) {
            throw new IllegalArgumentException("Native handle cannot be zero");
        }

        // Store the provided parameters
        this.maxInflightRequests = maxInflight > 0 ? maxInflight : 0; // 0 means use native defaults
        this.requestTimeoutMillis =
                requestTimeoutMs > 0 ? requestTimeoutMs : 0; // 0 means no Java timeout

        // Use the existing native handle
        this.nativeClientHandle.set(existingHandle);

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(existingHandle);

        // Register cleanup action - but don't double-close since handle is managed externally
        this.cleanupRef = new PhantomReference<>(this, CLEANUP_QUEUE);
        CLEANUP_ACTIONS.put(this.cleanupRef, new CleanupAction(this.nativeState));
    }

    // ==================== COMMAND EXECUTION METHODS ====================

    /** Execute a batch of commands asynchronously via JNI. */
    public CompletableFuture<Object> executeBatchAsync(
            int[] requestTypes,
            byte[][][] args,
            boolean isAtomic,
            boolean raiseOnError,
            int timeout,
            boolean retryServerError,
            boolean retryConnectionError,
            boolean hasRoute,
            int routeType,
            String routeParam,
            boolean expectUtf8Response,
            long timeoutMs,
            long spanPtr) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle, timeoutMs);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            GlideNativeBridge.executeBatchAsync(
                    handle,
                    correlationId,
                    requestTypes,
                    args,
                    isAtomic,
                    raiseOnError,
                    timeout,
                    retryServerError,
                    retryConnectionError,
                    hasRoute,
                    routeType,
                    routeParam,
                    expectUtf8Response,
                    spanPtr);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /** Execute cluster scan asynchronously with proper cursor lifecycle management */
    public CompletableFuture<Object> executeClusterScanAsync(
            String cursorId,
            String matchPattern,
            long count,
            String objectType,
            boolean expectUtf8Response) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId =
                        AsyncRegistry.register(
                                future, this.maxInflightRequests, handle, this.requestTimeoutMillis);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute cluster scan with proper cursor management via dedicated bridge
            GlideNativeBridge.executeClusterScanAsync(
                    handle, cursorId, matchPattern, count, objectType, expectUtf8Response, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /** Update connection password (for compatibility with CommandManager) */
    public CompletableFuture<String> updateConnectionPassword(
            String password, boolean immediateAuth) {
        long handle = nativeClientHandle.get();
        if (handle == 0) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new glide.api.models.exceptions.ClosingException("Client is closed"));
            return f;
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        long correlationId;
        try {
            correlationId =
                    AsyncRegistry.register(
                            future, this.maxInflightRequests, handle, this.requestTimeoutMillis);
        } catch (glide.api.models.exceptions.RequestException e) {
            future.completeExceptionally(e);
            return future;
        }

        GlideNativeBridge.updateConnectionPassword(handle, password, immediateAuth, correlationId);
        return future;
    }

    /** Refresh IAM token (for compatibility with CommandManager) */
    public CompletableFuture<String> refreshIamToken() {
        CompletableFuture<String> future = new CompletableFuture<>();

        long handle = nativeClientHandle.get();
        if (handle == 0) {
            future.completeExceptionally(
                    new glide.api.models.exceptions.ClosingException("Client is closed"));
            return future;
        }

        long correlationId;
        try {
            correlationId =
                    AsyncRegistry.register(
                            future, this.maxInflightRequests, handle, this.requestTimeoutMillis);
        } catch (glide.api.models.exceptions.RequestException e) {
            future.completeExceptionally(e);
            return future;
        }

        GlideNativeBridge.refreshIamToken(handle, correlationId);
        return future;
    }

    /** Get cache metrics */
    public CompletableFuture<Object> getCacheMetrics(CacheMetricsType metricsType) {
        CompletableFuture<Object> future = new CompletableFuture<>();

        long handle = nativeClientHandle.get();
        if (handle == 0) {
            future.completeExceptionally(
                    new glide.api.models.exceptions.ClosingException("Client is closed"));
            return future;
        }

        long correlationId;
        try {
            correlationId =
                    AsyncRegistry.register(
                            future, this.maxInflightRequests, handle, this.requestTimeoutMillis);
        } catch (glide.api.models.exceptions.RequestException e) {
            future.completeExceptionally(e);
            return future;
        }

        GlideNativeBridge.getCacheMetrics(handle, correlationId, metricsType.getNumber());
        return future;
    }

    /** Execute a single command asynchronously via JNI. */
    public CompletableFuture<Object> executeCommandAsync(
            int requestType,
            byte[][] args,
            boolean hasRoute,
            int routeType,
            String routeParam,
            boolean expectUtf8Response,
            long timeoutMs,
            long spanPtr) {
        return executeCommandAsync(
                requestType,
                args,
                null,
                hasRoute,
                routeType,
                routeParam,
                expectUtf8Response,
                timeoutMs,
                spanPtr);
    }

    /** Execute a command whose length-prefixed arguments are packed into one JNI byte array. */
    public CompletableFuture<Object> executeCommandAsyncPacked(
            int requestType,
            byte[] packedArgs,
            boolean hasRoute,
            int routeType,
            String routeParam,
            boolean expectUtf8Response,
            long timeoutMs,
            long spanPtr) {
        return executeCommandAsync(
                requestType,
                EMPTY_2D_BYTE_ARRAY,
                packedArgs,
                hasRoute,
                routeType,
                routeParam,
                expectUtf8Response,
                timeoutMs,
                spanPtr);
    }

    /**
     * Execute MGET with a caller-supplied completion handler and cleaner-owned response storage.
     *
     * <p>This compatibility path deliberately uses the generic JNI entry point. Its response buffer
     * remains valid if the handler retains or returns it. Performance-sensitive callers should use
     * one of the typed MGET methods, whose fixed decoders cannot expose native storage.
     */
    public <T> CompletableFuture<T> executeMgetCommandAsync(
            byte[][] args,
            byte[] packedArgs,
            boolean expectUtf8Response,
            long timeoutMs,
            long spanPtr,
            BiFunction<Object, Throwable, ? extends T> completionHandler) {
        if (completionHandler == null) {
            throw new IllegalArgumentException("Completion handler cannot be null");
        }
        return executeCommandAsync(
                        RequestType.MGet.getNumber(),
                        args,
                        packedArgs,
                        false,
                        0,
                        null,
                        expectUtf8Response,
                        timeoutMs,
                        spanPtr)
                .handle(completionHandler);
    }

    /** Execute UTF-8 MGET through a fixed synchronous decoder. */
    public CompletableFuture<String[]> executeMgetStringCommandAsync(
            byte[][] args, byte[] packedArgs, long timeoutMs, long spanPtr) {
        return executeTypedMgetCommandAsync(
                args, packedArgs, true, timeoutMs, spanPtr, this::completeMgetStringResponse);
    }

    /** Execute binary MGET through a fixed synchronous decoder. */
    public CompletableFuture<GlideString[]> executeMgetBinaryCommandAsync(
            byte[][] args, byte[] packedArgs, long timeoutMs, long spanPtr) {
        return executeTypedMgetCommandAsync(
                args, packedArgs, false, timeoutMs, spanPtr, this::completeMgetBinaryResponse);
    }

    private <T> CompletableFuture<T> executeTypedMgetCommandAsync(
            byte[][] args,
            byte[] packedArgs,
            boolean expectUtf8Response,
            long timeoutMs,
            long spanPtr,
            BiFunction<Object, Throwable, ? extends T> completionHandler) {
        AsyncRegistry.MgetFuturePair<T> futures = AsyncRegistry.newMgetFutures(completionHandler);
        CompletableFuture<Object> rawFuture = futures.rawFuture();
        CompletableFuture<T> resultFuture = futures.resultFuture();
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                rawFuture.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return resultFuture;
            }

            long correlationId;
            try {
                correlationId =
                        AsyncRegistry.register(rawFuture, this.maxInflightRequests, handle, timeoutMs);
            } catch (glide.api.models.exceptions.RequestException error) {
                rawFuture.completeExceptionally(error);
                return resultFuture;
            }

            if (correlationId == 0L) {
                return resultFuture;
            }

            executeMgetCommandAsyncNative(
                    handle,
                    correlationId,
                    packedArgs == null && args != null ? args : EMPTY_2D_BYTE_ARRAY,
                    packedArgs,
                    expectUtf8Response,
                    spanPtr);
            return resultFuture;
        } catch (Exception error) {
            rawFuture.completeExceptionally(error);
            return resultFuture;
        }
    }

    private String[] completeMgetStringResponse(Object result, Throwable error) {
        throwMgetError(error);
        return decodeMgetStringArray(result);
    }

    private GlideString[] completeMgetBinaryResponse(Object result, Throwable error) {
        throwMgetError(error);
        return decodeMgetBinaryArray(result);
    }

    private void throwMgetError(Throwable error) {
        if (error == null) {
            return;
        }
        if (error instanceof ClosingException) {
            close();
        }
        if (error instanceof RuntimeException) {
            throw (RuntimeException) error;
        }
        throw new RuntimeException(error);
    }

    private static String[] decodeMgetStringArray(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof ByteBuffer) {
            return deserializeMgetStringArray((ByteBuffer) result);
        }
        if (result instanceof String[]) {
            return (String[]) result;
        }
        if (!(result instanceof Object[])) {
            throw new IllegalArgumentException(
                    "Unexpected MGET UTF-8 response type: " + result.getClass().getName());
        }

        Object[] values = (Object[]) result;
        String[] decoded = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            decoded[index] = String.class.cast(values[index]);
        }
        return decoded;
    }

    private static GlideString[] decodeMgetBinaryArray(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof ByteBuffer) {
            return deserializeMgetBinaryArray((ByteBuffer) result);
        }
        if (result instanceof GlideString[]) {
            return (GlideString[]) result;
        }
        if (!(result instanceof Object[])) {
            throw new IllegalArgumentException(
                    "Unexpected MGET binary response type: " + result.getClass().getName());
        }

        Object[] values = (Object[]) result;
        GlideString[] decoded = new GlideString[values.length];
        for (int index = 0; index < values.length; index++) {
            if (values[index] != null) {
                decoded[index] = GlideString.of(byte[].class.cast(values[index]));
            }
        }
        return decoded;
    }

    private static String[] deserializeMgetStringArray(ByteBuffer buffer) {
        // The first pass validates the whole native frame and finds the largest element. The second
        // pass intentionally rewinds and reuses one byte array instead of allocating per value.
        int count = readMgetArrayLength(buffer);
        int maxValueLength = 0;
        for (int index = 0; index < count; index++) {
            int length = readMgetBulkStringLength(buffer, index);
            if (length != -1) {
                maxValueLength = Math.max(maxValueLength, length);
                buffer.position(buffer.position() + length);
            }
        }
        requireMgetFullyConsumed(buffer.remaining());

        int decodedCount = readMgetArrayLength(buffer);
        if (decodedCount != count) {
            throw new IllegalArgumentException("MGET array count changed while decoding");
        }

        byte[] decodeBuffer = new byte[maxValueLength];
        String[] decoded = new String[count];
        for (int index = 0; index < count; index++) {
            int length = readMgetBulkStringLength(buffer, index);
            if (length != -1) {
                if (length == 0) {
                    decoded[index] = "";
                } else {
                    buffer.get(decodeBuffer, 0, length);
                    decoded[index] = new String(decodeBuffer, 0, length, StandardCharsets.UTF_8);
                }
            }
        }
        requireMgetFullyConsumed(buffer.remaining());
        return decoded;
    }

    private static GlideString[] deserializeMgetBinaryArray(ByteBuffer buffer) {
        int count = readMgetArrayLength(buffer);
        GlideString[] decoded = new GlideString[count];
        for (int index = 0; index < count; index++) {
            int length = readMgetBulkStringLength(buffer, index);
            if (length != -1) {
                // The typed MGET callback releases the native buffer when it returns, so each
                // GlideString must copy its bounded slice before the buffer can escape this method.
                int originalLimit = buffer.limit();
                buffer.limit(buffer.position() + length);
                try {
                    decoded[index] = GlideString.of(buffer);
                } finally {
                    buffer.limit(originalLimit);
                }
            }
        }
        requireMgetFullyConsumed(buffer.remaining());
        return decoded;
    }

    private static int readMgetArrayLength(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.rewind();
        requireMgetBufferBytes(buffer, 5, "MGET array header");

        byte marker = buffer.get();
        if (marker != '*') {
            throw new IllegalArgumentException("Expected MGET array marker '*', got: " + (char) marker);
        }

        int count = buffer.getInt();
        validateMgetArrayCount(count, buffer.remaining());
        return count;
    }

    private static void validateMgetArrayCount(int count, int remainingBytes) {
        if (count < 0) {
            throw new IllegalArgumentException("Invalid negative MGET array count: " + count);
        }
        if (count > remainingBytes / 5) {
            throw new IllegalArgumentException(
                    "MGET array count "
                            + count
                            + " exceeds the maximum encoded by "
                            + remainingBytes
                            + " remaining bytes");
        }
    }

    private static int readMgetBulkStringLength(ByteBuffer buffer, int index) {
        requireMgetBufferBytes(buffer, 5, "MGET element " + index);
        byte marker = buffer.get();
        if (marker != '$') {
            throw new IllegalArgumentException(
                    "Expected MGET bulk string marker at element " + index + ", got: " + (char) marker);
        }

        int length = buffer.getInt();
        if (length != -1) {
            validateMgetLength(length, buffer, index);
        }
        return length;
    }

    private static void requireMgetFullyConsumed(int remaining) {
        if (remaining != 0) {
            throw new IllegalArgumentException(
                    "Unexpected trailing bytes in MGET response: " + remaining);
        }
    }

    private static void requireMgetBufferBytes(ByteBuffer buffer, int required, String context) {
        if (buffer.remaining() < required) {
            throw new IllegalArgumentException(
                    "Buffer too small for " + context + ": " + buffer.remaining() + " bytes");
        }
    }

    private static void validateMgetLength(int length, ByteBuffer buffer, int index) {
        if (length < 0) {
            throw new IllegalArgumentException(
                    "Invalid negative MGET bulk string length at element " + index + ": " + length);
        }
        if (length > buffer.remaining()) {
            throw new IllegalArgumentException(
                    "MGET bulk string length "
                            + length
                            + " exceeds buffer remaining "
                            + buffer.remaining()
                            + " at element "
                            + index);
        }
    }

    private CompletableFuture<Object> executeCommandAsync(
            int requestType,
            byte[][] args,
            byte[] packedArgs,
            boolean hasRoute,
            int routeType,
            String routeParam,
            boolean expectUtf8Response,
            long timeoutMs,
            long spanPtr) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            CompletableFuture<Object> future =
                    requestType == RequestType.MGet.getNumber()
                            ? AsyncRegistry.newManagedFuture()
                            : new CompletableFuture<>();
            long correlationId;
            try {
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle, timeoutMs);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            if (packedArgs == null) {
                GlideNativeBridge.executeCommandAsync(
                        handle,
                        correlationId,
                        requestType,
                        args != null ? args : EMPTY_2D_BYTE_ARRAY,
                        hasRoute,
                        routeType,
                        routeParam,
                        expectUtf8Response,
                        spanPtr);
            } else {
                GlideNativeBridge.executeCommandAsyncPacked(
                        handle,
                        correlationId,
                        requestType,
                        packedArgs,
                        hasRoute,
                        routeType,
                        routeParam,
                        expectUtf8Response,
                        spanPtr);
            }

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /** Execute a script asynchronously via JNI. */
    public CompletableFuture<Object> executeScriptAsync(
            String hash,
            byte[][] keys,
            byte[][] args,
            boolean hasRoute,
            int routeType,
            String routeParam,
            boolean expectUtf8Response) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId =
                        AsyncRegistry.register(
                                future, this.maxInflightRequests, handle, this.requestTimeoutMillis);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            GlideNativeBridge.executeScriptAsync(
                    handle,
                    correlationId,
                    hash,
                    keys != null ? keys : EMPTY_2D_BYTE_ARRAY,
                    args != null ? args : EMPTY_2D_BYTE_ARRAY,
                    hasRoute,
                    routeType,
                    routeParam,
                    expectUtf8Response);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    // ==================== CLIENT STATUS AND INFO METHODS ====================

    /** Check if client is connected. */
    public boolean isConnected() {
        long handle = nativeClientHandle.get();
        return handle != 0 && GlideNativeBridge.isConnected(handle);
    }

    /** Check the local lifecycle state without crossing JNI. */
    public boolean isClosed() {
        return nativeClientHandle.get() == 0;
    }

    /** Get client information for debugging and monitoring. */
    public String getClientInfo() {
        long handle = nativeClientHandle.get();
        if (handle == 0) {
            return "Client is closed";
        }
        return GlideNativeBridge.getClientInfo(handle);
    }

    /** Get the number of pending async operations. */
    public int getPendingOperations() {
        return AsyncRegistry.getPendingCount();
    }

    /** Health check to detect if client is working properly */
    public boolean isHealthy() {
        return isConnected() && AsyncRegistry.getPendingCount() < 1000;
    }

    // ==================== RESOURCE MANAGEMENT ====================

    /** Close the client and cleanup all resources */
    @Override
    public void close() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            // Cleanup already in progress or completed
            return;
        }

        long handle = nativeClientHandle.getAndSet(0);
        if (handle != 0) {
            try {
                unregisterClient(handle);
            } catch (Throwable ignore) {
            }
            try {
                // Clean up per-client inflight tracking
                AsyncRegistry.cleanupClient(handle);
                GlideNativeBridge.closeClient(handle);
            } finally {
                // Reset AsyncRegistry only when no clients remain (test isolation / full shutdown)
                if (clients.isEmpty()) {
                    AsyncRegistry.reset();
                }
            }
        }

        // Also trigger the cleanup action (safe to call multiple times)
        Runnable action = CLEANUP_ACTIONS.remove(cleanupRef);
        if (action != null) {
            action.run();
        }
        cleanupRef.clear();
    }

    /** Shared state for cleanup coordination */
    private static class NativeState {
        volatile long nativePtr;

        NativeState(long nativePtr) {
            this.nativePtr = nativePtr;
        }
    }

    /** Cleanup action for the Cleaner */
    private static class CleanupAction implements Runnable {
        private final NativeState nativeState;

        CleanupAction(NativeState nativeState) {
            this.nativeState = nativeState;
        }

        @Override
        public void run() {
            long ptr = nativeState.nativePtr;
            if (ptr != 0) {
                nativeState.nativePtr = 0;
                // Clean up per-client inflight tracking
                AsyncRegistry.cleanupClient(ptr);
                GlideNativeBridge.closeClient(ptr);
            }
        }
    }
}
