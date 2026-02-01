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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for correlating native callbacks with Java futures.
 *
 * <p>Handles request tracking, per-client inflight limits, and optional Java-side timeouts.
 */
public final class AsyncRegistry {

    private static final ConcurrentHashMap<Long, CompletableFuture<Object>> activeFutures =
            new ConcurrentHashMap<>(estimateInitialCapacity());

    private static final ConcurrentHashMap<Long, ScheduledFuture<?>> timeoutTasks =
            new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Long, AtomicInteger> clientInflightCounts =
            new ConcurrentHashMap<>();

    private static final AtomicLong nextId = new AtomicLong(1);

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

    /** Register a future without Java-side timeout (delegates to Rust core). */
    public static <T> long register(
            CompletableFuture<T> future, int maxInflightRequests, long clientHandle) {
        return register(future, maxInflightRequests, clientHandle, 0);
    }

    /**
     * Register a future with optional Java-side timeout.
     *
     * @param future the future to register
     * @param maxInflightRequests per-client limit (0 = no Java-side limit)
     * @param clientHandle native client handle for tracking
     * @param timeoutMillis Java-side timeout (0 = use Rust default)
     * @return correlation ID for native callback
     */
    public static <T> long register(
            CompletableFuture<T> future, int maxInflightRequests, long clientHandle, long timeoutMillis) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }

        if (maxInflightRequests > 0) {
            enforceInflightLimit(clientHandle, maxInflightRequests);
        }

        long correlationId = nextId.getAndIncrement();

        @SuppressWarnings("unchecked")
        CompletableFuture<Object> typedFuture = (CompletableFuture<Object>) future;
        activeFutures.put(correlationId, typedFuture);

        if (timeoutMillis > 0) {
            scheduleTimeout(correlationId, typedFuture, timeoutMillis);
        }

        setupCleanup(correlationId, typedFuture, maxInflightRequests, clientHandle);

        return correlationId;
    }

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

    private static void setupCleanup(
            long correlationId,
            CompletableFuture<Object> future,
            int maxInflightRequests,
            long clientHandle) {
        future.whenComplete(
                (result, error) -> {
                    activeFutures.remove(correlationId);

                    ScheduledFuture<?> timeoutTask = timeoutTasks.remove(correlationId);
                    if (timeoutTask != null) {
                        timeoutTask.cancel(false);
                    }

                    if (maxInflightRequests > 0) {
                        decrementInflightCount(clientHandle);
                    }
                });
    }

    private static void decrementInflightCount(long clientHandle) {
        clientInflightCounts.computeIfPresent(
                clientHandle,
                (key, counter) -> {
                    int remaining = counter.decrementAndGet();
                    return remaining <= 0 ? null : counter;
                });
    }

    /** Complete a request with a successful result. Returns false if already completed. */
    public static boolean completeCallback(long correlationId, Object result) {
        CompletableFuture<Object> future = activeFutures.get(correlationId);
        return future != null && future.complete(result);
    }

    /**
     * Complete a request with an error. Error codes: 0=Unspecified, 1=ExecAbort, 2=Timeout,
     * 3=Disconnect.
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

    public static int getPendingCount() {
        return activeFutures.size();
    }

    public static void shutdown() {
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();

        activeFutures.values().forEach(future -> future.cancel(true));
        activeFutures.clear();
        clientInflightCounts.clear();
    }

    public static void cleanupClient(long clientHandle) {
        clientInflightCounts.remove(clientHandle);
    }

    /** Reset state for test isolation. */
    public static void reset() {
        timeoutTasks.values().forEach(task -> task.cancel(false));
        timeoutTasks.clear();
        activeFutures.clear();
        clientInflightCounts.clear();
        nextId.set(1);
    }

    public static void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }
    }
}
