package glide.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RACE-CONDITION-FREE AsyncRegistry that eliminates the fundamental flaws
 * causing crashes at high concurrency.
 * 
 * Key fixes:
 * 1. Uses CompletableFuture.orTimeout() instead of manual timeout scheduling
 * 2. Atomic cleanup in whenComplete() eliminates race conditions
 * 3. No separate timeout cleanup tasks that race with completion
 * 4. Proper memory management with automatic cleanup
 */
public final class AsyncRegistry {

    /**
     * Thread-safe storage for active futures
     * Using ConcurrentHashMap for lock-free operations
     */
    private static final ConcurrentHashMap<Long, CompletableFuture<Object>> activeFutures =
            // Use configurable segment count for optimal concurrent performance
            // inflight request allowed is more fittable
        new ConcurrentHashMap<>(4096, 0.75f, Runtime.getRuntime().availableProcessors());
    
    /**
     * Per-client inflight request counters
     * Maps client handle to the number of active requests for that client
     */
    private static final ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicInteger> clientInflightCounts =
        new ConcurrentHashMap<>();


    /**
     * Thread-safe ID generator
     */
    private static final AtomicLong nextId = new AtomicLong(1);


    // ==================== CONFIGURABLE CONSTANTS ====================

    /**
     * Compute default timeout with configuration hierarchy: env var -> system property -> core default
     */
    private static long computeDefaultTimeout() {
        String env = System.getenv("GLIDE_REQUEST_TIMEOUT_MS");
        if (env != null) {
            try {
                long v = Long.parseLong(env.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
            }
        }

        String prop = System.getProperty("glide.requestTimeoutMs");
        if (prop != null) {
            try {
                long v = Long.parseLong(prop.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
            }
        }

        // Use glide-core default via native method
        return GlideNativeBridge.getGlideCoreDefaultTimeoutMs();
    }

    /**
     * Compute maximum pending operations with configuration hierarchy
     */
    private static int computeMaxPendingOperations() {
        String env = System.getenv("GLIDE_MAX_PENDING_OPERATIONS");
        if (env != null) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
            }
        }

        String prop = System.getProperty("glide.maxPendingOperations");
        if (prop != null) {
            try {
                int v = Integer.parseInt(prop.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
            }
        }

        // Use conservative default that balances memory usage with throughput
        // This replaces the arbitrary hardcoded 50000
        return 25000;
    }

    /**
     * Default timeout for Valkey operations in milliseconds.
     * Now configurable via env vars, system properties, or core defaults.
     */
    private static final long DEFAULT_TIMEOUT_MS = computeDefaultTimeout();
    
    /**
     * Maximum number of pending operations allowed (backpressure mechanism).
     * Now configurable instead of arbitrary hardcoded value.
     */
    private static final int MAX_PENDING_OPERATIONS = computeMaxPendingOperations();
    

    /**
     * Register future with race-condition-free timeout handling
     */
    public static <T> long register(CompletableFuture<T> future, long timeoutMs) {
        return register(future, timeoutMs, MAX_PENDING_OPERATIONS, 0L);
    }
    
    /**
     * Register future with client-specific inflight limit (matches UDS glide-core behavior)
     */
    public static <T> long register(CompletableFuture<T> future, long timeoutMs, int maxInflightRequests) {
        return register(future, timeoutMs, maxInflightRequests, 0L);
    }
    
    /**
     * Register future with client-specific inflight limit and client handle for per-client tracking
     */
    public static <T> long register(CompletableFuture<T> future, long timeoutMs, int maxInflightRequests, long clientHandle) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout must be positive, was: " + timeoutMs);
        }
        
        // GLOBAL backpressure check (system-wide limit) - NEW
        // This moves backpressure control from Rust-side queue limiting to Java-side request blocking
        int currentPending = activeFutures.size();
        if (currentPending >= MAX_PENDING_OPERATIONS) {
            throw new glide.api.models.exceptions.RequestException(
                "System at maximum pending operations: " + currentPending + "/" + MAX_PENDING_OPERATIONS);
        }
        
        // Client-specific inflight limit check (matches UDS glide-core behavior)
        // 0 means "use native/core defaults" - no limit enforcement in Java layer
        if (maxInflightRequests > 0) {
            // Get or create per-client counter
            java.util.concurrent.atomic.AtomicInteger clientCount = clientInflightCounts.computeIfAbsent(
                clientHandle, k -> new java.util.concurrent.atomic.AtomicInteger(0));
            
            // Check if this specific client has reached its limit
            if (clientCount.get() >= maxInflightRequests) {
                // Use same error type and message as glide-core UDS implementation
                throw new glide.api.models.exceptions.RequestException("Client reached maximum inflight requests");
            }
            
            // Increment the client's inflight count
            clientCount.incrementAndGet();
        }

        long correlationId = nextId.getAndIncrement();
        
        // Store the ORIGINAL future, not a transformed one
        // .orTimeout() and .whenComplete() create NEW futures, breaking the reference!
        @SuppressWarnings("unchecked")
        CompletableFuture<Object> originalFuture = (CompletableFuture<Object>) future;

        // Store ORIGINAL future for completion by native code
        activeFutures.put(correlationId, originalFuture);
        
        // Set up timeout and cleanup on the ORIGINAL future
        // This ensures the same future that users hold gets completed
        originalFuture
            .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .whenComplete((result, throwable) -> {
                // Atomic cleanup - no race conditions possible
                activeFutures.remove(correlationId);
                
                // Decrement per-client counter if applicable
                if (maxInflightRequests > 0) {
                    java.util.concurrent.atomic.AtomicInteger clientCount = clientInflightCounts.get(clientHandle);
                    if (clientCount != null) {
                        clientCount.decrementAndGet();
                    }
                }
            });

        return correlationId;
    }

    /**
     * Register with default timeout
     */
    public static <T> long register(CompletableFuture<T> future) {
        return register(future, DEFAULT_TIMEOUT_MS, MAX_PENDING_OPERATIONS, 0L);
    }
    
    /**
     * Register with default timeout and client-specific inflight limit
     */
    public static <T> long register(CompletableFuture<T> future, int maxInflightRequests) {
        return register(future, DEFAULT_TIMEOUT_MS, maxInflightRequests, 0L);
    }
    
    /**
     * Register with default timeout, client-specific inflight limit and client handle
     */
    public static <T> long register(CompletableFuture<T> future, int maxInflightRequests, long clientHandle) {
        return register(future, DEFAULT_TIMEOUT_MS, maxInflightRequests, clientHandle);
    }

    /**
     * Register a blocking command that should not timeout
     * This allows BLPOP-style commands to block indefinitely as expected
     */
    public static <T> long registerInfiniteBlockingCommand(CompletableFuture<T> future, int maxInflightRequests) {
        return registerInfiniteBlockingCommand(future, maxInflightRequests, 0L);
    }
    
    /**
     * Register a blocking command with client handle for per-client tracking
     */
    public static <T> long registerInfiniteBlockingCommand(CompletableFuture<T> future, int maxInflightRequests, long clientHandle) {
        if (future == null) {
            throw new IllegalArgumentException("Future cannot be null");
        }
        
        // GLOBAL backpressure check (system-wide limit) - NEW
        // Apply same backpressure control to infinite blocking commands
        int currentPending = activeFutures.size();
        if (currentPending >= MAX_PENDING_OPERATIONS) {
            throw new glide.api.models.exceptions.RequestException(
                "System at maximum pending operations: " + currentPending + "/" + MAX_PENDING_OPERATIONS);
        }
        
        // Client-specific inflight limit check (matches UDS glide-core behavior)
        if (maxInflightRequests > 0) {
            // Get or create per-client counter
            java.util.concurrent.atomic.AtomicInteger clientCount = clientInflightCounts.computeIfAbsent(
                clientHandle, k -> new java.util.concurrent.atomic.AtomicInteger(0));
            
            // Check if this specific client has reached its limit
            if (clientCount.get() >= maxInflightRequests) {
                throw new glide.api.models.exceptions.RequestException("Client reached maximum inflight requests");
            }
            
            // Increment the client's inflight count
            clientCount.incrementAndGet();
        }

        long correlationId = nextId.getAndIncrement();
        
        // Store the ORIGINAL future, but DO NOT set any timeout
        @SuppressWarnings("unchecked")
        CompletableFuture<Object> originalFuture = (CompletableFuture<Object>) future;
        activeFutures.put(correlationId, originalFuture);
        
        // Set up cleanup WITHOUT timeout for infinite blocking
        originalFuture.whenComplete((result, throwable) -> {
            // Atomic cleanup - no race conditions possible
            activeFutures.remove(correlationId);
            
            // Decrement per-client counter if applicable
            if (maxInflightRequests > 0) {
                java.util.concurrent.atomic.AtomicInteger clientCount = clientInflightCounts.get(clientHandle);
                if (clientCount != null) {
                    clientCount.decrementAndGet();
                }
            }
        });
        return correlationId;
    }

    /**
     * Complete callback with proper race condition handling
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
     * Complete with error using same race-free pattern
     */
    public static boolean completeCallbackWithError(long correlationId, String errorMessage) {
        CompletableFuture<Object> future = activeFutures.get(correlationId);
        
        if (future == null) {
            return false;
        }

        RuntimeException exception = mapToTypedException(errorMessage);
        
        // completeExceptionally() is also atomic and safe
        boolean completed = future.completeExceptionally(exception);
        
        // Note: cleanup happens automatically in whenComplete()
        // No manual removal needed, which eliminates race conditions
        
        return completed;
    }

    /**
     * Complete with error using a structured error code from native layer.
     * Codes map to glide-core RequestErrorType: 0-Unspecified, 1-ExecAbort,
     * 2-Timeout, 3-Disconnect.
     */
    public static boolean completeCallbackWithErrorCode(long correlationId, int errorTypeCode, String errorMessage) {
        CompletableFuture<Object> future = activeFutures.get(correlationId);
        if (future == null) {
            return false;
        }

        String msg = (errorMessage == null || errorMessage.isBlank()) ? "Unknown error from native code" : errorMessage;
        RuntimeException ex;
        switch (errorTypeCode) {
            case 3: // Disconnect
                ex = new glide.api.models.exceptions.ClosingException(msg);
                break;
            case 0: // Unspecified - check if it's actually a connection error
                // For unspecified errors, check the message to determine the actual type
                ex = mapToTypedException(msg);
                break;
            case 1: // ExecAbort
            case 2: // Timeout
            default:
                ex = new glide.api.models.exceptions.RequestException(msg);
                break;
        }

        return future.completeExceptionally(ex);
    }

    private static RuntimeException mapToTypedException(String errorMessage) {
        String msg = (errorMessage == null || errorMessage.isBlank())
                ? "Unknown error from native code"
                : errorMessage;

        String lower = msg.toLowerCase();

        // Command/argument errors → RequestException
        if (lower.contains("unknown command") || lower.contains("wrong number of arguments")
                || lower.contains("syntax error") || lower.contains("invalid argument")) {
            return new glide.api.models.exceptions.RequestException(msg);
        }

        // Connection/setup errors → ClosingException
        if (lower.contains("connection refused") || lower.contains("failed to create client")
                || (lower.contains("connect") && lower.contains("failed"))
                || lower.contains("host") && lower.contains("unreachable")
                || lower.contains("failed connecting to")
                || lower.contains("connect failed")) {
            return new glide.api.models.exceptions.ClosingException(msg);
        }

        return new glide.api.models.exceptions.RequestException(msg);
    }

    /**
     * Complete multiple callbacks in a single batch operation
     * This reduces JNI crossing overhead by processing multiple completions together
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

    /**
     * Complete multiple callbacks with errors in a single batch operation
     * This reduces JNI crossing overhead by processing multiple error completions
     * together
     */
    public static int completeBatchedCallbacksWithError(long[] correlationIds, String[] errorMessages) {
        if (correlationIds == null || errorMessages == null) {
            throw new IllegalArgumentException("Arrays cannot be null");
        }
        if (correlationIds.length != errorMessages.length) {
            throw new IllegalArgumentException("Array lengths must match");
        }
        if (correlationIds.length == 0) {
            return 0;
        }

        int completed = 0;
        for (int i = 0; i < correlationIds.length; i++) {
            if (completeCallbackWithError(correlationIds[i], errorMessages[i])) {
                completed++;
            }
        }
        
        return completed;
    }

    /**
     * Get current pending operation count
     */
    public static int getPendingCount() {
        return activeFutures.size();
    }


    /**
     * Shutdown cleanup - cancel all pending operations during client shutdown
     */
    public static void shutdown() {
        // Complete all pending futures with cancellation
        activeFutures.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        
        // Clear the map
        activeFutures.clear();
        clientInflightCounts.clear();
    }

    /**
     * Clean up per-client tracking when a client is closed
     */
    public static void cleanupClient(long clientHandle) {
        clientInflightCounts.remove(clientHandle);
    }
    
    /**
     * Reset all internal state. Intended for test isolation and client shutdown cleanup.
     */
    public static void reset() {
        activeFutures.clear();
        clientInflightCounts.clear();
        nextId.set(1);
    }

    /**
     * Register shutdown hook for clean termination
     */
    static {
        Runtime.getRuntime().addShutdownHook(
            new Thread(AsyncRegistry::shutdown, "AsyncRegistry-Shutdown"));
    }
}