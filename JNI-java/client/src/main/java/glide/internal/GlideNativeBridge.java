package glide.internal;

import glide.api.logging.Logger;
import java.nio.ByteBuffer;

/**
 * JNI bridge for the native Valkey client.
 * Methods expose a handle-based API for safe cross-language operations.
 */
public class GlideNativeBridge {
    
    // Load native library
    static {
        try {
            System.loadLibrary("valkey_glide");
        } catch (UnsatisfiedLinkError e) {
            // Fallback to java.library.path for diagnostics
            String libPath = System.getProperty("java.library.path");
            Logger.error("GlideNativeBridge", "Failed to load native library 'valkey_glide': " + e.getMessage()
                    + "; java.library.path=" + libPath);
            throw e;
        }
    }
    
    /**
     * Create a new native client instance
     */
    public static native long createClient(
    String[] addresses,
    int databaseId,
    String username,
    String password,
    boolean useTls,
            boolean insecureTls,
                    boolean clusterMode,
            int requestTimeoutMs,
    int connectionTimeoutMs,
    int maxInflightRequests,
    int nativeDirectMemoryMB,
        String protocol,
        byte[][] subExact,
        byte[][] subPattern,
            byte[][] subSharded,
            String readFrom,
            String clientAZ,
            boolean lazyConnect,
            String clientName,
            int reconnectNumRetries,
            int reconnectExponentBase,
            int reconnectFactor,
            int reconnectJitterPercent
    );
    
    /**
     * Execute command asynchronously
     */
    public static native void executeCommandAsync(long clientPtr, byte[] requestBytes, long callbackId);
    
    /**
     * Execute binary command with mixed String/byte[] arguments asynchronously
     */
    public static native void executeBinaryCommandAsync(long clientPtr, byte[] requestBytes, long callbackId);
    
    /**
     * Execute batch (pipeline/transaction) asynchronously
     */
    public static native void executeBatchAsync(long clientPtr, byte[] batchRequestBytes, long callbackId);

    /**
     * Execute binary-safe PUBLISH/SPUBLISH asynchronously
     */
    public static native void executePublishBinaryAsync(long clientPtr, boolean sharded, byte[] channel, byte[] message, long callbackId);

    /**
     * Update the connection password with optional immediate authentication.
     */
    public static native void updateConnectionPassword(long clientPtr, String password, boolean immediateAuth,
            long callbackId);

    /**
     * Check if the native client is connected
     */
    public static native boolean isConnected(long clientPtr);
    
    /**
     * Get client information from native layer
     */
    public static native String getClientInfo(long clientPtr);
    
    /**
     * Close and release a native client
     */
    public static native void closeClient(long clientPtr);
    
    /**
     * Execute cluster scan command asynchronously
     */
    public static native void executeClusterScanAsync(long clientPtr, byte[] requestBytes, long callbackId);
    
    /**
     * Execute script asynchronously using glide-core's invoke_script
     */
    public static native void executeScriptAsync(
        long clientPtr,
        long callbackId, 
        String hash,
        String[] keys,
        String[] args,
        boolean hasRoute,
        int routeType,
        String routeParam
    );
    
    /**
     * Retrieve large data by pointer ID
     */
    public static native Object retrieveLargeData(long pointerId);
    
    /**
     * Retrieve large data as DirectByteBuffer (zero-copy)
     */
    public static native java.nio.ByteBuffer retrieveLargeDataAsByteBuffer(long pointerId);
    
    /**
     * Delete a large data pointer
     */
    public static native long deleteLargeDataPointer(long pointerId);

    /**
     * Release a reference held by a DirectByteBuffer on a large-data pointer.
     * Safe to call multiple times; no-op if already released.
     */
    public static native void releaseLargeDataRef(long pointerId);

    /**
     * Get large data handler statistics
     */
    public static native byte[] getLargeDataStatistics();
    
    /**
     * Set the garbage collection timeout for large data pointers
     * @param timeoutSeconds Timeout in seconds (must be positive)
     */
    public static native void setLargeDataGcTimeout(long timeoutSeconds);
    
    /**
     * Configure native large-data watermarks (in MB)
     * 
     * @param softMB Soft watermark in MB (0 to auto-set at 75% of hard)
     * @param hardMB Hard watermark in MB (must be > 0)
     */
    public static native void setLargeDataWatermarks(long softMB, long hardMB);

    /**
     * Force garbage collection of expired large data pointers
     * @return Number of pointers cleaned up
     */
    public static native long forceLargeDataGc();
    
    /**
     * Preferred unified runtime stats JSON.
     */
    public static native String getRuntimeStats();
    
    /**
     * Get glide-core default timeout in milliseconds
     */
    public static native long getGlideCoreDefaultTimeoutMs();
    
    /**
     * Get glide-core default maximum inflight requests limit
     */
    public static native int getGlideCoreDefaultMaxInflightRequests();

    /**
     * Enable/disable native stats collection and reporting (default: disabled).
     * This is primarily for benchmarks/tests and is not required for normal use.
     */
    public static native void setNativeStatsEnabled(boolean enabled);
}