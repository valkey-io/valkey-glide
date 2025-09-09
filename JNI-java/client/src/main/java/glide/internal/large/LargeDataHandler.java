package glide.internal.large;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.ref.Cleaner;
import glide.internal.GlideNativeBridge;

/**
 * **Large Data Handler - Java Interface for Deferred Conversion**
 * 
 * Provides efficient access to large Valkey values (>16KB) using a pointer
 * optimization that avoids expensive JNI data copying. Large values are stored
 * in native memory and accessed on-demand through pointer IDs.
 * 
 * The 16KB threshold is based on practical performance testing
 * against AWS ElastiCache clusters under production workloads.
 * 
 * ## Architecture
 * - Detects deferred responses by checking for "__deferred__" marker
 * - Retrieves data lazily using pointer IDs
 * - Supports both object and ByteBuffer access patterns
 * - Automatic cleanup of native pointers
 * 
 * ## Usage Example
 * ```java
 * Object result = client.get("large-key").get();
 * if (LargeDataHandler.isDeferredResponse(result)) {
 * // Option 1: Get as regular object (triggers conversion)
 * Object actualData = LargeDataHandler.retrieveData(result);
 * 
 * // Option 2: Get as ByteBuffer for zero-copy access
 * ByteBuffer buffer = LargeDataHandler.retrieveAsByteBuffer(result);
 * }
 * ```
 */
public class LargeDataHandler {
    static {
        // Initialize defaults in native layer
        try {
            Configuration.setGcTimeoutSeconds(Configuration.getGcTimeoutSeconds());
        } catch (Throwable ignored) {
        }
    }
    
    private static final String DEFERRED_MARKER = "__deferred__";
    private static final String POINTER_ID_KEY = "pointerId";
    private static final String SIZE_BYTES_KEY = "sizeBytes";
    private static final String DATA_TYPE_KEY = "dataType";
    
    // Cleaner for releasing native references when ByteBuffers are GC'd
    private static final Cleaner CLEANER = Cleaner.create();

    // Cache for resolved values to avoid repeated JNI calls
    private static final ConcurrentHashMap<Long, Object> resolvedCache = new ConcurrentHashMap<>();
    
    // Delegate to GlideClient native methods
    private static Object retrieveLargeDataNative(long pointerId) {
        return GlideNativeBridge.retrieveLargeData(pointerId);
    }
    
    private static ByteBuffer retrieveLargeDataAsByteBufferNative(long pointerId) {
        return GlideNativeBridge.retrieveLargeDataAsByteBuffer(pointerId);
    }
    
    private static long deleteLargeDataPointerNative(long pointerId) {
        return GlideNativeBridge.deleteLargeDataPointer(pointerId);
    }
    
    private static byte[] getLargeDataStatisticsNative() {
        return GlideNativeBridge.getLargeDataStatistics();
    }
    
    private static void setLargeDataGcTimeoutNative(long timeoutSeconds) {
        GlideNativeBridge.setLargeDataGcTimeout(timeoutSeconds);
    }

    private static void setLargeDataWatermarksNative(long softMB, long hardMB) {
        GlideNativeBridge.setLargeDataWatermarks(softMB, hardMB);
    }
    
    /**
     * Check if a response is a deferred large data response.
     * 
     * @param response The response object from Valkey
     * @return true if this is a deferred response requiring resolution
     */
    public static boolean isDeferredResponse(Object response) {
        if (response instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) response;
            return "true".equals(map.get(DEFERRED_MARKER));
        }
        return false;
    }
    
    /**
     * Retrieve the actual data for a deferred response.
     * This triggers the conversion from native memory to Java objects.
     * 
     * @param deferredResponse The deferred response containing pointer metadata
     * @return The actual Valkey value
     * @throws IllegalArgumentException if not a deferred response
     */
    public static Object retrieveData(Object deferredResponse) {
        if (!isDeferredResponse(deferredResponse)) {
            throw new IllegalArgumentException("Not a deferred response");
        }
        
        Map<?, ?> map = (Map<?, ?>) deferredResponse;
        Long pointerId = extractPointerId(map);
        
        // Check cache first
        Object cached = resolvedCache.get(pointerId);
        if (cached != null) {
            return cached;
        }
        
        // Retrieve from native memory
        Object result = retrieveLargeDataNative(pointerId);
        if (result != null) {
            // Cache only small results (<16KB) to avoid unbounded off-heap retention
            long sizeBytes = extractSizeBytes(map);
            if (sizeBytes > 0 && sizeBytes < 16 * 1024) {
                resolvedCache.put(pointerId, result);
            }
        }
        
        return result;
    }
    
    /**
     * Retrieve large binary data as a DirectByteBuffer for zero-copy access.
     * This is the most efficient way to access large binary values.
     * 
     * @param deferredResponse The deferred response containing pointer metadata
     * @return DirectByteBuffer pointing to native memory
     * @throws IllegalArgumentException if not a deferred response or not binary data
     */
    public static ByteBuffer retrieveAsByteBuffer(Object deferredResponse) {
        if (!isDeferredResponse(deferredResponse)) {
            throw new IllegalArgumentException("Not a deferred response");
        }
        
        Map<?, ?> map = (Map<?, ?>) deferredResponse;
        Long pointerId = extractPointerId(map);
        String dataType = (String) map.get(DATA_TYPE_KEY);
        
        if (!"BulkString".equals(dataType)) {
            throw new IllegalArgumentException(
                "ByteBuffer access only available for binary data, got: " + dataType
            );
        }
        
        ByteBuffer buffer = retrieveLargeDataAsByteBufferNative(pointerId);
        if (buffer != null) {
            // Register cleaner to release native reference when buffer is GC'd
            CLEANER.register(buffer, () -> {
                try {
                    GlideNativeBridge.releaseLargeDataRef(pointerId);
                } catch (Throwable ignored) {
                }
            });
        }
        return buffer;
    }
    
    /**
     * Manually delete a large data pointer to free native memory.
     * This is optional as automatic garbage collection will clean up
     * pointers after 5 minutes.
     * 
     * @param deferredResponse The deferred response to clean up
     * @return The number of bytes freed, or -1 if pointer not found
     */
    public static long deletePointer(Object deferredResponse) {
        if (!isDeferredResponse(deferredResponse)) {
            return -1;
        }
        
        Map<?, ?> map = (Map<?, ?>) deferredResponse;
        Long pointerId = extractPointerId(map);
        
        // Remove from cache
        resolvedCache.remove(pointerId);
        
        // Delete from native registry
        return deleteLargeDataPointerNative(pointerId);
    }
    
    /**
     * Get statistics about large data handler performance.
     * 
     * @return JSON string with statistics including deferred count,
     *         immediate count, bytes saved, and active pointers
     */
    public static String getStatistics() {
        byte[] statsBytes = getLargeDataStatisticsNative();
        return new String(statsBytes);
    }
    
    /**
     * Create a CompletableFuture that resolves deferred data automatically.
     * This provides a convenient async interface for handling large data.
     * 
     * @param future The original future that may contain deferred data
     * @return A future that automatically resolves deferred data
     */
    public static CompletableFuture<Object> withDeferredResolution(
            CompletableFuture<Object> future) {
        return future.thenApply(result -> {
            if (isDeferredResponse(result)) {
                return retrieveData(result);
            }
            return result;
        });
    }
    
    /**
     * Get metadata about a deferred response without retrieving the data.
     * 
     * @param deferredResponse The deferred response
     * @return Metadata containing pointerId, sizeBytes, and dataType
     */
    public static DeferredMetadata getMetadata(Object deferredResponse) {
        if (!isDeferredResponse(deferredResponse)) {
            throw new IllegalArgumentException("Not a deferred response");
        }
        
        Map<?, ?> map = (Map<?, ?>) deferredResponse;
        return new DeferredMetadata(
            extractPointerId(map),
            extractSizeBytes(map),
            (String) map.get(DATA_TYPE_KEY)
        );
    }
    
    /**
     * Clear the resolved value cache.
     * Useful for testing or when memory pressure is high.
     */
    public static void clearCache() {
        resolvedCache.clear();
    }
    
    /**
     * Force garbage collection of expired large data pointers.
     * This is useful for testing or when you need immediate cleanup.
     * 
     * @return The number of pointers that were cleaned up
     */
    public static long forceGarbageCollection() {
        long cleaned = GlideNativeBridge.forceLargeDataGc();
        return cleaned;
    }
    
    private static Long extractPointerId(Map<?, ?> map) {
        Object pointerId = map.get(POINTER_ID_KEY);
        if (pointerId instanceof Long) {
            return (Long) pointerId;
        }
        throw new IllegalStateException("Invalid or missing pointer ID");
    }
    
    private static long extractSizeBytes(Map<?, ?> map) {
        Object sizeBytes = map.get(SIZE_BYTES_KEY);
        if (sizeBytes instanceof Long) {
            return (Long) sizeBytes;
        }
        return -1;
    }
    
    /**
     * Metadata about a deferred large data value.
     */
    public static class DeferredMetadata {
        public final long pointerId;
        public final long sizeBytes;
        public final String dataType;
        
        public DeferredMetadata(long pointerId, long sizeBytes, String dataType) {
            this.pointerId = pointerId;
            this.sizeBytes = sizeBytes;
            this.dataType = dataType;
        }
        
        @Override
        public String toString() {
            return String.format(
                "DeferredMetadata[pointerId=%d, sizeBytes=%d, dataType=%s]",
                pointerId, sizeBytes, dataType
            );
        }
    }
    
    /**
     * Configuration for large data handling behavior.
     */
    public static class Configuration {
        private static int THRESHOLD_BYTES = 16 * 1024; // 16KB - empirically determined
        private static boolean AUTO_RESOLVE = false;
        private static int GC_TIMEOUT_SECONDS = 60; // Library default aligns with native; benchmarks can override
        
        public static void setThresholdBytes(int bytes) {
            THRESHOLD_BYTES = bytes;
        }
        
        public static int getThresholdBytes() {
            return THRESHOLD_BYTES;
        }
        
        public static void setAutoResolve(boolean autoResolve) {
            AUTO_RESOLVE = autoResolve;
        }
        
        public static boolean isAutoResolve() {
            return AUTO_RESOLVE;
        }
        
        public static void setGcTimeoutSeconds(int seconds) {
            if (seconds > 0) {
                GC_TIMEOUT_SECONDS = seconds;
                // Update the native Rust layer
                setLargeDataGcTimeoutNative(seconds);
            }
        }
        
        public static int getGcTimeoutSeconds() {
            return GC_TIMEOUT_SECONDS;
        }

        /**
         * Configure large-data watermarks.
         * 
         * @param softMB Soft watermark in MB (0 = auto 75% of hard)
         * @param hardMB Hard watermark in MB (>0)
         */
        public static void setWatermarksMB(long softMB, long hardMB) {
            if (hardMB > 0) {
                setLargeDataWatermarksNative(softMB, hardMB);
            }
        }
    }
}