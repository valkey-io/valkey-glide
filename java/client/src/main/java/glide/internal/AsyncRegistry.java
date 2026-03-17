/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.logging.Logger;
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

    /** Rate-limit interval for timeout/disconnect log messages (in nanoseconds) */
    private static final long LOG_RATE_LIMIT_NS = 5_000_000_000L; // 5 seconds

    /** Last log timestamp for timeout errors */
    private static final AtomicLong lastTimeoutLogNs = new AtomicLong(0);

    /** Last log timestamp for disconnect errors */
    private static final AtomicLong lastDisconnectLogNs = new AtomicLong(0);

    /** Suppressed timeout log count since last emitted log */
    private static final AtomicLong suppressedTimeoutLogs = new AtomicLong(0);

    /** Suppressed disconnect log count since last emitted log */
    private static final AtomicLong suppressedDisconnectLogs = new AtomicLong(0);

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

    /** Thread-safe mapping from correlation ID to the registered entry. */
    private static final ConcurrentHashMap<Long, Entry> activeFutures =
            new ConcurrentHashMap<>(estimateInitialCapacity());

    /** Per-client inflight request counters. Maps client handle to active request count. */
    private static final ConcurrentHashMap<Long, AtomicInteger> clientInflightCounts =
            new ConcurrentHashMap<>();

    /** Thread-safe ID generator for correlation IDs. */
    private static final AtomicLong nextId = new AtomicLong(1);

    /** Shutdown flag to prevent new registrations after shutdown/failAllWithError. */
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

    /**
     * Complete callback with proper race condition handling.
     *
     * @param correlationId the correlation ID from register()
     * @param result the result to complete with
     * @return true if completed, false if already done
     */
    public static boolean completeCallback(long correlationId, Object result) {
        Entry entry = activeFutures.remove(correlationId);
        if (entry == null) {
            Logger.log(Logger.Level.WARN, "AsyncRegistry", "No entry for correlationId " + correlationId);
            return false;
        }
        boolean completed = entry.future.complete(result);
        releaseInflight(entry);
        return completed;
    }

    /**
     * Complete with error using a structured error code from native layer. Codes map to glide-core
     * RequestErrorType: 0=Unspecified, 1=ExecAbort, 2=Timeout, 3=Disconnect. Releases inflight even
     * if future was externally cancelled.
     *
     * @param correlationId the correlation ID from register()
     * @param errorTypeCode error type code from native layer
     * @param errorMessage error message from native layer
     * @return true if completed, false if already done
     */
    public static boolean completeCallbackWithErrorCode(
            long correlationId, int errorTypeCode, String errorMessage) {
        Entry entry = activeFutures.remove(correlationId);
        if (entry == null) {
            Logger.log(Logger.Level.WARN, "AsyncRegistry", "No entry for correlationId " + correlationId);
            return false;
        }

        String msg =
                (errorMessage == null || errorMessage.trim().isEmpty())
                        ? "Unknown error from native code"
                        : errorMessage;

        // Log timeout and disconnect errors (rate-limited)
        if (errorTypeCode == 2 || errorTypeCode == 3) {
            boolean isTimeout = errorTypeCode == 2;
            AtomicLong lastLogRef = isTimeout ? lastTimeoutLogNs : lastDisconnectLogNs;
            AtomicLong suppressedRef = isTimeout ? suppressedTimeoutLogs : suppressedDisconnectLogs;
            String errorTypeName = isTimeout ? "Timeout" : "Disconnect";

            long now = System.nanoTime();
            long lastLog = lastLogRef.get();
            if (now - lastLog >= LOG_RATE_LIMIT_NS && lastLogRef.compareAndSet(lastLog, now)) {
                long suppressed = suppressedRef.getAndSet(0);
                String suffix = suppressed > 0 ? " (suppressed " + suppressed + " similar)" : "";
                Logger.log(Logger.Level.WARN, "AsyncRegistry", errorTypeName + ": " + msg + suffix);
            } else {
                suppressedRef.incrementAndGet();
            }
        }

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

        boolean completed = entry.future.completeExceptionally(ex);
        releaseInflight(entry);
        return completed;
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
                (errorMessage == null || errorMessage.trim().isEmpty())
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
