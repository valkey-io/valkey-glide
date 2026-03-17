/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ExecAbortException;
import glide.api.models.exceptions.RequestException;
import glide.api.models.exceptions.TimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Async registry for correlating native callbacks with Java {@link CompletableFuture}s.
 *
 * <p>Entries are removed and inflight slots released only when the native callback arrives — never
 * on timeout. Timeouts are enforced by the Rust core ({@code response_timeout}) and delivered as
 * error callbacks. This keeps the inflight counter accurate to actual native-side work, providing
 * natural backpressure when the server is slow.
 */
public final class AsyncRegistry {

    private static final class Entry {
        final CompletableFuture<Object> future;
        final int maxInflightRequests;
        final long clientHandle;

        Entry(CompletableFuture<Object> future, int maxInflightRequests, long clientHandle) {
            this.future = future;
            this.maxInflightRequests = maxInflightRequests;
            this.clientHandle = clientHandle;
        }
    }

    private static final ConcurrentHashMap<Long, Entry> activeFutures =
            new ConcurrentHashMap<>(estimateInitialCapacity());

    private static final ConcurrentHashMap<Long, AtomicInteger> clientInflightCounts =
            new ConcurrentHashMap<>();

    private static final AtomicLong nextId = new AtomicLong(1);

    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);

    private static final Thread shutdownHook =
            new Thread(AsyncRegistry::shutdown, "AsyncRegistry-Shutdown");

    static {
        if (!"false".equalsIgnoreCase(System.getProperty("glide.autoShutdownHook", "true"))) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

    private static int estimateInitialCapacity() {
        for (String source :
                new String[] {
                    System.getenv("GLIDE_MAX_INFLIGHT_REQUESTS"),
                    System.getProperty("glide.maxInflightRequests")
                }) {
            if (source != null) {
                try {
                    int v = Integer.parseInt(source.trim());
                    if (v > 0) return Math.max(16, v * 2);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 2000;
    }

    // ---- Registration ----

    /**
     * Register a future for native callback correlation with inflight limit enforcement.
     *
     * @param future the future to complete when the native callback arrives
     * @param maxInflightRequests per-client limit (0 = no Java-side limit)
     * @param clientHandle native client handle for per-client tracking
     * @param timeoutMillis unused — timeout is enforced by the Rust core
     * @return correlation ID, or 0 if the registry is shutting down
     */
    public static <T> long register(
            CompletableFuture<T> future, int maxInflightRequests, long clientHandle, long timeoutMillis) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }

        if (isShutdown.get()) {
            future.completeExceptionally(
                    new ClosingException("Client is shutting down, cannot register new requests"));
            return 0L;
        }

        if (maxInflightRequests > 0) {
            enforceInflightLimit(clientHandle, maxInflightRequests);
        }

        long correlationId = nextId.getAndIncrement();

        @SuppressWarnings("unchecked")
        CompletableFuture<Object> originalFuture = (CompletableFuture<Object>) future;

        activeFutures.put(correlationId, new Entry(originalFuture, maxInflightRequests, clientHandle));

        if (isShutdown.get()) {
            Entry removed = activeFutures.remove(correlationId);
            if (removed != null) {
                releaseInflight(removed);
            }
            future.completeExceptionally(
                    new ClosingException("Client is shutting down, cannot register new requests"));
            return 0L;
        }

        return correlationId;
    }

    // ---- Completion (only path that removes entries and releases inflight) ----

    /** Native success callback. Releases inflight even if future was externally cancelled. */
    public static boolean completeCallback(long correlationId, Object result) {
        Entry entry = activeFutures.remove(correlationId);
        if (entry == null) {
            return false;
        }
        releaseInflight(entry);
        return entry.future.complete(result);
    }

    /**
     * Native error callback. Codes: 0=Unspecified, 1=ExecAbort, 2=Timeout, 3=Disconnect. Releases
     * inflight even if future was externally cancelled.
     */
    public static boolean completeCallbackWithErrorCode(
            long correlationId, int errorTypeCode, String errorMessage) {
        Entry entry = activeFutures.remove(correlationId);
        if (entry == null) {
            return false;
        }

        String msg =
                (errorMessage == null || errorMessage.isEmpty())
                        ? "Unknown error from native code"
                        : errorMessage;

        RuntimeException ex;
        switch (errorTypeCode) {
            case 1:
                ex = new ExecAbortException(msg);
                break;
            case 2:
                ex = new TimeoutException(msg);
                break;
            case 3:
                ex = new ClosingException(msg);
                break;
            default:
                ex = new RequestException(msg);
                break;
        }

        releaseInflight(entry);
        return entry.future.completeExceptionally(ex);
    }

    // ---- Inflight tracking ----

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

    private static void releaseInflight(Entry entry) {
        if (entry.maxInflightRequests > 0) {
            clientInflightCounts.computeIfPresent(
                    entry.clientHandle, (key, counter) -> counter.decrementAndGet() <= 0 ? null : counter);
        }
    }

    // ---- Lifecycle ----

    public static void shutdown() {
        isShutdown.set(true);
        activeFutures.values().forEach(entry -> entry.future.cancel(true));
        activeFutures.clear();
        clientInflightCounts.clear();
    }

    /**
     * Fail all pending futures with a {@link ClosingException}. Called from native on fatal failure.
     */
    public static void failAllWithError(String errorMessage) {
        isShutdown.set(true);
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

    /** Reset all state. For test isolation. */
    public static void reset() {
        isShutdown.set(false);
        activeFutures.clear();
        clientInflightCounts.clear();
        nextId.set(1);
    }

    // ---- Observability ----

    public static int getPendingCount() {
        return activeFutures.size();
    }

    /** Alias for {@link #getPendingCount()}. */
    public static int getActiveFutureCount() {
        return getPendingCount();
    }

    public static boolean isShutdown() {
        return isShutdown.get();
    }

    public static void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
        }
    }
}
