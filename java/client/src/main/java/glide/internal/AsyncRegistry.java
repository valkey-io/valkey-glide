/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async registry for correlating native callbacks with Java {@link CompletableFuture}s.
 *
 * <p>Timeouts use a periodic sweep over {@code activeFutures}. Each entry stores the future and
 * its deadline, avoiding a separate tracking map and minimizing per-request overhead.
 */
public final class AsyncRegistry {

    /** Holds a future and its timeout deadline. */
    private static final class Entry {
        final CompletableFuture<Object> future;
        final long deadlineMs; // System.currentTimeMillis() deadline, or Long.MAX_VALUE if no timeout

        Entry(CompletableFuture<Object> future, long deadlineMs) {
            this.future = future;
            this.deadlineMs = deadlineMs;
        }
    }

    private static final ConcurrentHashMap<Long, Entry> activeFutures =
            new ConcurrentHashMap<>(estimateInitialCapacity());

    private static final ConcurrentHashMap<Long, AtomicInteger> clientInflightCounts =
            new ConcurrentHashMap<>();

    private static final AtomicLong nextId = new AtomicLong(1);

    private static final ScheduledThreadPoolExecutor timeoutScheduler;

    static {
        timeoutScheduler =
                new ScheduledThreadPoolExecutor(
                        1,
                        r -> {
                            Thread t = new Thread(r, "GlideTimeoutScheduler");
                            t.setDaemon(true);
                            return t;
                        });
    }

    private static volatile boolean sweepStarted = false;
    private static final Object sweepInitLock = new Object();

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
        return 2000;
    }

    /** Register a future with optional timeout, inflight limit, and client tracking. */
    public static <T> long register(
            CompletableFuture<T> future,
            int maxInflightRequests,
            long clientHandle,
            long timeoutMillis) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }

        if (maxInflightRequests > 0) {
            enforceInflightLimit(clientHandle, maxInflightRequests);
        }

        long correlationId = nextId.getAndIncrement();

        @SuppressWarnings("unchecked")
        CompletableFuture<Object> originalFuture = (CompletableFuture<Object>) future;

        long deadline =
                timeoutMillis > 0
                        ? System.currentTimeMillis() + timeoutMillis
                        : Long.MAX_VALUE;

        activeFutures.put(correlationId, new Entry(originalFuture, deadline));

        if (timeoutMillis > 0) {
            ensureSweepStarted();
        }

        originalFuture.whenComplete(
                (result, error) -> {
                    activeFutures.remove(correlationId);

                    if (maxInflightRequests > 0) {
                        decrementInflightCount(clientHandle);
                    }
                });

        return correlationId;
    }

    private static void ensureSweepStarted() {
        if (sweepStarted) return;
        synchronized (sweepInitLock) {
            if (sweepStarted) return;
            sweepStarted = true;
            timeoutScheduler.scheduleAtFixedRate(
                    AsyncRegistry::sweepTimedOut, 2, 2, TimeUnit.MILLISECONDS);
        }
    }

    private static void sweepTimedOut() {
        long now = System.currentTimeMillis();
        activeFutures.forEach(
                (id, entry) -> {
                    if (entry.deadlineMs <= now) {
                        if (entry.future.completeExceptionally(
                                new TimeoutException("Request timed out"))) {
                            GlideNativeBridge.markTimedOut(id);
                        }
                    }
                });
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

    private static void decrementInflightCount(long clientHandle) {
        clientInflightCounts.computeIfPresent(
                clientHandle,
                (key, counter) -> {
                    int remaining = counter.decrementAndGet();
                    return remaining <= 0 ? null : counter;
                });
    }

    public static boolean completeCallback(long correlationId, Object result) {
        Entry entry = activeFutures.get(correlationId);
        return entry != null && entry.future.complete(result);
    }

    /**
     * Complete with error. Codes: 0=Unspecified, 1=ExecAbort, 2=Timeout, 3=Disconnect.
     */
    public static boolean completeCallbackWithErrorCode(
            long correlationId, int errorTypeCode, String errorMessage) {
        Entry entry = activeFutures.get(correlationId);
        if (entry == null) {
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

        return entry.future.completeExceptionally(ex);
    }

    public static int getPendingCount() {
        return activeFutures.size();
    }

    public static void shutdown() {
        activeFutures.values().forEach(e -> e.future.cancel(true));
        activeFutures.clear();
        clientInflightCounts.clear();
        timeoutScheduler.shutdownNow();
    }

    public static void failAllWithError(String errorMessage) {
        String msg =
                (errorMessage == null || errorMessage.isEmpty())
                        ? "Native callback infrastructure failed"
                        : errorMessage;
        activeFutures.forEach(
                (id, entry) -> entry.future.completeExceptionally(new ClosingException(msg)));
        activeFutures.clear();
        clientInflightCounts.clear();
    }

    public static void cleanupClient(long clientHandle) {
        clientInflightCounts.remove(clientHandle);
    }

    public static void reset() {
        activeFutures.clear();
        clientInflightCounts.clear();
        sweepStarted = false;
        nextId.set(1);
    }

    public static int getPendingTimeoutCount() {
        return activeFutures.size();
    }

    public static int getActiveFutureCount() {
        return activeFutures.size();
    }

    public static void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }
    }
}
