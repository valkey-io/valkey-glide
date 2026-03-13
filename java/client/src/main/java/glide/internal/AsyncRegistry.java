/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async registry for correlating native callbacks with Java {@link CompletableFuture}s.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Maintain a thread-safe mapping from correlation id to the original future
 *   <li>Enforce per-client max inflight requests in Java (0 = defer to core default)
 *   <li>Schedule optional Java-side timeouts with cancellable tasks
 *   <li>Perform atomic cleanup on completion to avoid races and leaks
 * </ul>
 *
 * <p>Timeouts can be enforced at the Java layer (for immediate user feedback) or deferred to the
 * Rust core (when timeoutMillis = 0). Backpressure defaults and concurrency tuning are handled by
 * the Rust core.
 */
public final class AsyncRegistry {

    /** Thread-safe storage for active futures. Using ConcurrentHashMap for lock-free operations. */
    private static final ConcurrentHashMap<Long, CompletableFuture<Object>> activeFutures =
            new ConcurrentHashMap<>(estimateInitialCapacity());

    /** Scheduled timeout tasks mapped by correlation ID for cancellation on completion. */
    private static final ConcurrentHashMap<Long, ScheduledFuture<?>> timeoutTasks =
            new ConcurrentHashMap<>();

    /**
     * Per-client inflight request counters. Maps client handle to the number of active requests for
     * that client.
     */
    private static final ConcurrentHashMap<Long, AtomicInteger> clientInflightCounts =
            new ConcurrentHashMap<>();

    /** Thread-safe ID generator for correlation IDs. */
    private static final AtomicLong nextId = new AtomicLong(1);

    /**
     * Shutdown flag to prevent race conditions between register() and shutdown()/failAllWithError().
     * Once set to true, register() will return pre-failed futures instead of adding to the registry.
     */
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);

    /**
     * Single-threaded scheduler for timeout tasks. Uses a daemon thread so it won't prevent JVM
     * shutdown. Tasks are cancellable via {@link ScheduledFuture#cancel(boolean)}.
     */
    private static final ScheduledExecutorService timeoutScheduler =
            Executors.newSingleThreadScheduledExecutor(
                    r -> {
                        Thread t = new Thread(r, "GlideTimeoutScheduler");
                        t.setDaemon(true);
                        return t;
                    });

    private static final Thread shutdownHook =
            new Thread(AsyncRegistry::shutdown, "AsyncRegistry-Shutdown");

    static {
        if (!"false".equalsIgnoreCase(System.getProperty("glide.autoShutdownHook", "true"))) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

    /** Estimate initial capacity for the active futures map using inflight limit with margin. */
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

        return 2000; // Default with margin over core's 1000
    }

    /**
     * Register future with client-specific inflight limit, client handle for per-client tracking, and
     * optional Java-side timeout.
     *
     * <p>If the registry is shutting down, the future will be completed exceptionally with a
     * ClosingException and a special correlation ID (0) will be returned to indicate the registration
     * failed.
     *
     * @param future the future to register
     * @param maxInflightRequests per-client limit (0 = no Java-side limit, defer to core)
     * @param clientHandle native client handle for tracking
     * @param timeoutMillis Java-side timeout in milliseconds (0 = use Rust default timeout)
     * @return correlation ID for native callback, or 0 if shutdown is in progress
     */
    public static <T> long register(
            CompletableFuture<T> future, int maxInflightRequests, long clientHandle, long timeoutMillis) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }

        // Check shutdown flag before registering to prevent race conditions
        // This ensures no futures are added after shutdown() starts clearing
        if (isShutdown.get()) {
            future.completeExceptionally(
                    new ClosingException("Client is shutting down, cannot register new requests"));
            return 0L; // Special ID indicating registration failed
        }

        // Client-specific inflight limit check
        // 0 means "use native/core defaults" - no limit enforcement in Java layer
        if (maxInflightRequests > 0) {
            enforceInflightLimit(clientHandle, maxInflightRequests);
        }

        long correlationId = nextId.getAndIncrement();

        // Store the original future
        @SuppressWarnings("unchecked")
        CompletableFuture<Object> originalFuture = (CompletableFuture<Object>) future;

        // Store original future for completion by native code
        activeFutures.put(correlationId, originalFuture);

        // Double-check shutdown flag after insertion to handle race with shutdown()
        // If shutdown started between our first check and the put(), clean up and fail
        if (isShutdown.get()) {
            activeFutures.remove(correlationId);
            if (maxInflightRequests > 0) {
                decrementInflightCount(clientHandle);
            }
            future.completeExceptionally(
                    new ClosingException("Client is shutting down, cannot register new requests"));
            return 0L;
        }

        // Schedule Java-side timeout if configured (0 = defer to Rust core timeout)
        if (timeoutMillis > 0) {
            scheduleTimeout(correlationId, originalFuture, timeoutMillis);
        }

        // Set up cleanup on the original future
        // This ensures proper resource cleanup when completed
        setupCleanup(correlationId, originalFuture, maxInflightRequests, clientHandle);

        return correlationId;
    }

    /** Enforce per-client inflight limit, throwing RequestException if exceeded. */
    private static void enforceInflightLimit(long clientHandle, int maxInflightRequests) {
        clientInflightCounts.compute(
                clientHandle,
                (key, counter) -> {
                    AtomicInteger value = counter != null ? counter : new AtomicInteger(0);
                    if (value.incrementAndGet() > maxInflightRequests) {
                        value.decrementAndGet();
                        throw new RequestException("Client reached maximum inflight requests");
                    }
                    return value;
                });
    }

    /**
     * Schedule a cancellable timeout task. If the request doesn't complete within timeoutMillis, the
     * future is completed exceptionally with TimeoutException and the native layer is notified.
     */
    private static void scheduleTimeout(
            long correlationId, CompletableFuture<Object> future, long timeoutMillis) {
        ScheduledFuture<?> task =
                timeoutScheduler.schedule(
                        () -> {
                            timeoutTasks.remove(correlationId);
                            if (future.completeExceptionally(new TimeoutException("Request timed out"))) {
                                GlideNativeBridge.markTimedOut(correlationId);
                            }
                        },
                        timeoutMillis,
                        TimeUnit.MILLISECONDS);
        timeoutTasks.put(correlationId, task);
    }

    /**
     * Set up cleanup handler for when the future completes (success, error, or timeout). Performs
     * atomic cleanup to avoid races and leaks.
     */
    private static void setupCleanup(
            long correlationId,
            CompletableFuture<Object> future,
            int maxInflightRequests,
            long clientHandle) {
        future.whenComplete(
                (result, error) -> {
                    // Atomic cleanup - no race conditions
                    activeFutures.remove(correlationId);

                    // Cancel the timeout task if it hasn't fired yet
                    // Using cancel(false) to avoid interrupting the scheduler thread
                    ScheduledFuture<?> timeoutTask = timeoutTasks.remove(correlationId);
                    if (timeoutTask != null) {
                        timeoutTask.cancel(false);
                    }

                    // Decrement per-client counter if applicable
                    if (maxInflightRequests > 0) {
                        decrementInflightCount(clientHandle);
                    }
                });
    }

    /** Decrement inflight count for client, removing the entry when it reaches zero. */
    private static void decrementInflightCount(long clientHandle) {
        clientInflightCounts.computeIfPresent(
                clientHandle,
                (key, counter) -> {
                    int remaining = counter.decrementAndGet();
                    // Clean up the entry when no more inflight requests
                    // to avoid leaking counters for inactive clients
                    return remaining <= 0 ? null : counter;
                });
    }

    /**
     * Complete callback with proper race condition handling. Returns false if already completed or
     * timed out.
     *
     * @param correlationId the correlation ID from register()
     * @param result the result to complete with
     * @return true if completed, false if already done
     */
    public static boolean completeCallback(long correlationId, Object result) {
        CompletableFuture<Object> future = activeFutures.get(correlationId);
        // complete() returns false if already completed
        // This prevents IllegalStateException from completing twice
        // Note: cleanup happens automatically in whenComplete()
        return future != null && future.complete(result);
    }

    /**
     * Complete with error using a structured error code from native layer. Codes map to glide-core
     * RequestErrorType: 0=Unspecified, 1=ExecAbort, 2=Timeout, 3=Disconnect.
     *
     * @param correlationId the correlation ID from register()
     * @param errorTypeCode error type code from native layer
     * @param errorMessage error message from native layer
     * @return true if completed, false if already done
     */
    public static boolean completeCallbackWithErrorCode(
            long correlationId, int errorTypeCode, String errorMessage) {
        CompletableFuture<Object> future = activeFutures.get(correlationId);
        if (future == null) {
            return false;
        }

        String msg =
                (errorMessage == null || errorMessage.trim().isEmpty())
                        ? "Unknown error from native code"
                        : errorMessage;

        RuntimeException ex;
        switch (errorTypeCode) {
            case 2:
                ex = new TimeoutException(msg);
                break;
            case 3:
                ex = new ClosingException(msg);
                break;
            case 1:
                ex = new ExecAbortException(msg);
                break;
            default:
                ex = new RequestException(msg);
                break;
        }

        return future.completeExceptionally(ex);
    }

    /** Get current pending operation count. */
    public static int getPendingCount() {
        return activeFutures.size();
    }

    /** Shutdown cleanup - cancel all pending operations during client shutdown. */
    public static void shutdown() {
        // Set shutdown flag first to prevent new registrations
        // This must happen before any clearing to avoid race conditions
        isShutdown.set(true);

        // Cancel timeout tasks without interrupting (they're just scheduled, not running)
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();

        // Cancel user futures with interrupt (may be blocked waiting)
        activeFutures.values().forEach(future -> future.cancel(true));
        activeFutures.clear();
        clientInflightCounts.clear();

        // Shutdown the timeout scheduler
        timeoutScheduler.shutdownNow();
    }

    /**
     * Fail all pending futures with a {@link ClosingException}. Called from the native layer when a
     * fatal infrastructure failure is detected (e.g., callback worker threads terminated or native
     * panic). This ensures no future is left dangling.
     *
     * @param errorMessage description of the failure cause
     */
    public static void failAllWithError(String errorMessage) {
        // Set shutdown flag first to prevent new registrations
        // This must happen before any clearing to avoid race conditions
        isShutdown.set(true);

        String msg =
                (errorMessage == null || errorMessage.isEmpty())
                        ? "Native callback infrastructure failed"
                        : errorMessage;
        activeFutures.forEach((id, future) -> future.completeExceptionally(new ClosingException(msg)));
        activeFutures.clear();

        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();
        clientInflightCounts.clear();
    }

    /** Clean up per-client tracking when a client is closed. */
    public static void cleanupClient(long clientHandle) {
        clientInflightCounts.remove(clientHandle);
    }

    /** Reset all internal state. Intended for test isolation and client shutdown cleanup. */
    public static void reset() {
        // Reset shutdown flag first to allow new registrations
        isShutdown.set(false);

        // Cancel timeout tasks without interrupting
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();
        activeFutures.clear();
        clientInflightCounts.clear();
        nextId.set(1);
    }

    /**
     * Returns the count of pending timeout tasks. Intended for testing to verify timeout tasks are
     * cancelled properly and don't accumulate.
     *
     * @return number of active timeout tasks
     */
    public static int getPendingTimeoutCount() {
        return timeoutTasks.size();
    }

    /**
     * Returns the count of active futures. Intended for testing to verify futures are cleaned up
     * properly.
     *
     * @return number of active futures
     */
    public static int getActiveFutureCount() {
        return activeFutures.size();
    }

    /**
     * Returns whether the registry is in shutdown state. Intended for testing and diagnostics.
     *
     * @return true if shutdown() or failAllWithError() has been called
     */
    public static boolean isShutdown() {
        return isShutdown.get();
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
