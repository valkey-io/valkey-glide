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
 *   <li>Perform atomic cleanup on completion to avoid races and leaks
 *   <li>Provide batched completion helpers to reduce native call overhead
 * </ul>
 *
 * <p>Inflight request limits are enforced exclusively by the Rust core via {@code
 * InflightRequestTracker}. Java does not maintain its own counter — this avoids desync between
 * Java-side and Rust-side counters that previously allowed zombie sub-command accumulation.
 */
public final class AsyncRegistry {

    /** Thread-safe storage for active futures Using ConcurrentHashMap for lock-free operations */
    private static final ConcurrentHashMap<Long, CompletableFuture<Object>> activeFutures =
            new ConcurrentHashMap<>(2000);

    /** Thread-safe ID generator */
    private static final AtomicLong nextId = new AtomicLong(1);

    /**
     * Register future for native callback correlation.
     *
     * @param future the future to register
     * @param clientHandle native client handle (reserved for future per-client tracking)
     * @return correlation ID for native callback
     */
    public static <T> long register(CompletableFuture<T> future, long clientHandle) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
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
                });

        return correlationId;
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
    }

    /** Clean up per-client tracking when a client is closed */
    public static void cleanupClient(long clientHandle) {
        // No-op: inflight tracking is handled exclusively by Rust core.
        // Method retained for API compatibility.
    }

    /** Reset all internal state. Intended for test isolation and client shutdown cleanup. */
    public static void reset() {
        activeFutures.clear();
        nextId.set(1);
    }

    /** Shutdown hook thread reference for optional removal */
    private static final Thread shutdownHook =
            new Thread(AsyncRegistry::shutdown, "AsyncRegistry-Shutdown");

    /** Register shutdown hook for clean termination */
    static {
        if (!"false".equalsIgnoreCase(System.getProperty("glide.autoShutdownHook", "true"))) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

    /**
     * Remove the automatic shutdown hook, allowing users to manage shutdown manually. Call this if
     * you want to control shutdown behavior yourself.
     */
    public static void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // Hook was never registered or already removed
        }
    }
}
