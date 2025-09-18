/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async registry for correlating native callbacks with Java {@link CompletableFuture}s.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Maintain a thread-safe mapping from correlation id to the original future
 *   <li>Enforce per-client max inflight requests in Java (0 = defer to core default)
 *   <li>Perform atomic cleanup on completion to avoid races and leaks
 *   <li>Provide batched completion helpers to reduce native call overhead
 * </ul>
 *
 * <p>Timeouts, backpressure defaults, and concurrency tuning are handled by the Rust core.
 */
public final class AsyncRegistry {

    /** Thread-safe storage for active futures Using ConcurrentHashMap for lock-free operations */
    private static final ConcurrentHashMap<Long, CompletableFuture<Object>> activeFutures =
            // Size based on max inflight requests with a small margin
            new ConcurrentHashMap<>(estimateInitialCapacity());

    /**
     * Per-client inflight request counters Maps client handle to the number of active requests for
     * that client
     */
    private static final ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicInteger>
            clientInflightCounts = new ConcurrentHashMap<>();

    /** Thread-safe ID generator */
    private static final AtomicLong nextId = new AtomicLong(1);

    // ==================== CONFIGURABLE CONSTANTS ====================

    /** Estimate initial capacity for the active futures map using inflight limit with margin */
    private static int estimateInitialCapacity() {
        String env = System.getenv("GLIDE_MAX_INFLIGHT_REQUESTS");
        if (env != null) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0) return Math.max(16, v * 2);
            } catch (NumberFormatException ignored) {
            }
        }

        String prop = System.getProperty("glide.maxInflightRequests");
        if (prop != null) {
            try {
                int v = Integer.parseInt(prop.trim());
                if (v > 0) return Math.max(16, v * 2);
            } catch (NumberFormatException ignored) {
            }
        }

        // Fall back to Rust core default (1000) with margin
        return 2000;
    }

    /**
     * Register future with client-specific inflight limit and client handle for per-client tracking
     */
    public static <T> long register(
            CompletableFuture<T> future, int maxInflightRequests, long clientHandle) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }

        // Client-specific inflight limit check
        // 0 means "use native/core defaults" - no limit enforcement in Java layer
        if (maxInflightRequests > 0) {
            // Get or create per-client counter
            java.util.concurrent.atomic.AtomicInteger clientCount =
                    clientInflightCounts.computeIfAbsent(
                            clientHandle, k -> new java.util.concurrent.atomic.AtomicInteger(0));

            // Check if this specific client has reached its limit
            if (clientCount.get() >= maxInflightRequests) {
                // Use same error type and message as glide-core implementation
                throw new glide.api.models.exceptions.RequestException(
                        "Client reached maximum inflight requests");
            }

            // Increment the client's inflight count
            clientCount.incrementAndGet();
        }

        long correlationId = nextId.getAndIncrement();

        // Store the original future
        @SuppressWarnings("unchecked")
        CompletableFuture<Object> originalFuture = (CompletableFuture<Object>) future;

        // Store original future for completion by native code
        activeFutures.put(correlationId, originalFuture);

        // Set up cleanup on the original future
        // This ensures proper resource cleanup when completed
        originalFuture.whenComplete(
                (result, throwable) -> {
                    // Atomic cleanup - no race conditions
                    activeFutures.remove(correlationId);

                    // Decrement per-client counter if applicable
                    if (maxInflightRequests > 0) {
                        java.util.concurrent.atomic.AtomicInteger clientCount =
                                clientInflightCounts.get(clientHandle);
                        // Null check needed: cleanupClient() may have been called concurrently
                        if (clientCount != null) {
                            int remaining = clientCount.decrementAndGet();
                            // Clean up the entry when no more inflight requests
                            // This prevents memory leak for inactive clients
                            if (remaining == 0) {
                                clientInflightCounts.remove(clientHandle, clientCount);
                            }
                        }
                    }
                });

        return correlationId;
    }

    /** Register with default settings */
    public static <T> long register(CompletableFuture<T> future) {
        return register(future, 0, 0L);
    }

    /** Register with client-specific inflight limit */
    public static <T> long register(CompletableFuture<T> future, int maxInflightRequests) {
        return register(future, maxInflightRequests, 0L);
    }

    /**
     * Complete callback with proper race condition handling. Supports both regular Java objects and
     * DirectByteBuffer for large responses (>16KB).
     */
    public static boolean completeCallback(long correlationId, Object result) {
        CompletableFuture<Object> future = activeFutures.get(correlationId);

        if (future == null) {
            // Future already completed or timed out
            return false;
        }

        // complete() returns false if already completed
        // This prevents IllegalStateException from completing twice
        boolean completed = future.complete(result);

        // Note: cleanup happens automatically in whenComplete()
        // No manual removal needed, which eliminates race conditions

        return completed;
    }

    /**
     * Complete with error using a structured error code from native layer. Codes map to glide-core
     * RequestErrorType: 0-Unspecified, 1-ExecAbort, 2-Timeout, 3-Disconnect.
     */
    public static boolean completeCallbackWithErrorCode(
            long correlationId, int errorTypeCode, String errorMessage) {
        CompletableFuture<Object> future = activeFutures.get(correlationId);
        if (future == null) {
            return false;
        }

        String msg =
                (errorMessage == null || errorMessage.isBlank())
                        ? "Unknown error from native code"
                        : errorMessage;

        // Map error codes directly to exception types
        RuntimeException ex;
        switch (errorTypeCode) {
            case 2: // TIMEOUT
                ex = new glide.api.models.exceptions.TimeoutException(msg);
                break;
            case 3: // DISCONNECT
                ex = new glide.api.models.exceptions.ClosingException(msg);
                break;
            case 1: // EXEC_ABORT
                ex = new glide.api.models.exceptions.ExecAbortException(msg);
                break;
            case 0: // UNSPECIFIED
            default:
                ex = new glide.api.models.exceptions.RequestException(msg);
                break;
        }

        return future.completeExceptionally(ex);
    }

    /**
     * Complete multiple callbacks in a single batch operation. This reduces native crossing overhead
     * by processing multiple completions together.
     */
    public static int completeBatchedCallbacks(long[] correlationIds, Object[] results) {
        if (correlationIds == null || results == null) {
            throw new IllegalArgumentException("Arrays cannot be null");
        }
        if (correlationIds.length != results.length) {
            throw new IllegalArgumentException("Array lengths must match");
        }
        if (correlationIds.length == 0) {
            return 0;
        }

        int completed = 0;
        for (int i = 0; i < correlationIds.length; i++) {
            if (completeCallback(correlationIds[i], results[i])) {
                completed++;
            }
        }

        return completed;
    }

    /** Get current pending operation count */
    public static int getPendingCount() {
        return activeFutures.size();
    }

    /** Shutdown cleanup - cancel all pending operations during client shutdown */
    public static void shutdown() {
        // Complete all pending futures with cancellation
        activeFutures
                .values()
                .forEach(
                        future -> {
                            if (!future.isDone()) {
                                future.cancel(true);
                            }
                        });

        // Clear the map
        activeFutures.clear();
        clientInflightCounts.clear();
    }

    /** Clean up per-client tracking when a client is closed */
    public static void cleanupClient(long clientHandle) {
        clientInflightCounts.remove(clientHandle);
    }

    /** Reset all internal state. Intended for test isolation and client shutdown cleanup. */
    public static void reset() {
        activeFutures.clear();
        clientInflightCounts.clear();
        nextId.set(1);
    }

    /** Register shutdown hook for clean termination */
    static {
        Runtime.getRuntime()
                .addShutdownHook(new Thread(AsyncRegistry::shutdown, "AsyncRegistry-Shutdown"));
    }
}
