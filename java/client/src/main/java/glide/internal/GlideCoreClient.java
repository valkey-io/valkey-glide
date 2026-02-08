/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import glide.ffi.resolvers.NativeUtils;
import java.lang.ref.Cleaner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GLIDE core client transport. Provides direct native access to glide-core with all routing and
 * performance optimizations.
 *
 * <p>This class wraps an existing native client handle created by ConnectionManager. It does NOT
 * create connections - that responsibility belongs to ConnectionManager.
 */
public class GlideCoreClient implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

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

    private static final java.util.concurrent.ConcurrentHashMap<
                    Long, java.lang.ref.WeakReference<glide.api.BaseClient>>
            clients = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Empty 2D byte array constant for reuse in various contexts (script params, subPattern, etc.)
     */
    public static final byte[][] EMPTY_2D_BYTE_ARRAY = new byte[0][];

    public static void registerClient(long handle, glide.api.BaseClient client) {
        clients.put(handle, new java.lang.ref.WeakReference<>(client));
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
        var ref = clients.get(handle);
        if (ref != null) {
            var c = ref.get();
            if (c != null) c.__enqueuePubSubMessage(m);
        }
    }

    // Register a Java Cleaner to free native memory when the given ByteBuffer is GC'd
    static void registerNativeBufferCleaner(java.nio.ByteBuffer buffer, long id) {
        if (buffer == null || id == 0) return;
        CLEANER.register(
                buffer,
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

    public int getMaxInflightRequests() {
        return maxInflightRequests;
    }

    /** Cleanup coordination flag. */
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    /** Cleaner to ensure native cleanup. */
    private final Cleaner.Cleanable cleanable;

    /** Shared state for cleanup coordination. */
    private final NativeState nativeState;

    /**
     * Constructor that wraps an existing native client handle (for BaseClient integration). This is
     * the ONLY constructor - GlideCoreClient does not create connections.
     */
    @SuppressFBWarnings(
            value = "CT_CONSTRUCTOR_THROW",
            justification = "Constructor fails fast on invalid handles prior to registering resources")
    public GlideCoreClient(long existingHandle, int maxInflight) {
        if (existingHandle == 0) {
            throw new IllegalArgumentException("Native handle cannot be zero");
        }

        // Store the provided parameters
        this.maxInflightRequests = maxInflight > 0 ? maxInflight : 0; // 0 means use native defaults

        // Use the existing native handle
        this.nativeClientHandle.set(existingHandle);

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(existingHandle);

        // Register cleanup action with Cleaner - but don't double-close since handle is managed
        // externally
        this.cleanable = CLEANER.register(this, new CleanupAction(this.nativeState));
    }

    // ==================== COMMAND EXECUTION METHODS ====================

    /**
     * Execute binary command asynchronously using raw protobuf bytes (for compatibility with
     * CommandManager)
     */
    public CompletableFuture<Object> executeBinaryCommandAsync(byte[] requestBytes) {
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
                // Rust handles all timeout logic - Java just waits for response
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute binary command directly using protobuf bytes
            GlideNativeBridge.executeBinaryCommandAsync(handle, requestBytes, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute command asynchronously using raw protobuf bytes (for compatibility with CommandManager)
     */
    public CompletableFuture<Object> executeCommandAsync(byte[] requestBytes) {
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
                // Rust handles all timeout logic - Java just waits for response
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute command directly using protobuf bytes
            GlideNativeBridge.executeCommandAsync(handle, requestBytes, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute batch asynchronously using raw protobuf bytes (for compatibility with CommandManager)
     */
    public CompletableFuture<Object> executeBatchAsync(
            byte[] batchRequestBytes, boolean expectUtf8Response) {
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
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute batch directly
            GlideNativeBridge.executeBatchAsync(
                    handle, batchRequestBytes, expectUtf8Response, correlationId);

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
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
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
            correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
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
            correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
        } catch (glide.api.models.exceptions.RequestException e) {
            future.completeExceptionally(e);
            return future;
        }

        GlideNativeBridge.refreshIamToken(handle, correlationId);
        return future;
    }

    /** Execute script via native invoke_script path */
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
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
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

        // Also trigger the cleaner cleanup (safe to call multiple times)
        cleanable.clean();
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
