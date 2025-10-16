/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.logging.Logger;
import glide.ffi.resolvers.NativeUtils;

/**
 * Native bridge for the Valkey client. Methods expose a handle-based API for safe cross-language
 * operations.
 */
public class GlideNativeBridge {

    // Load native library
    static {
        try {
            NativeUtils.loadGlideLib();
        } catch (Exception e) {
            Logger.log(
                    Logger.Level.ERROR,
                    "GlideNativeBridge",
                    "Failed to load native library: " + e.getMessage());
            throw new RuntimeException("Failed to load native library", e);
        }
    }

    /** Create a new native client instance */
    public static native long createClient(byte[] connectionRequestBytes);

    /** Execute command asynchronously */
    public static native void executeCommandAsync(
            long clientPtr, byte[] requestBytes, long callbackId);

    /** Execute binary command with mixed String/byte[] arguments asynchronously */
    public static native void executeBinaryCommandAsync(
            long clientPtr, byte[] requestBytes, long callbackId);

    /** Execute batch (pipeline/transaction) asynchronously */
    public static native void executeBatchAsync(
            long clientPtr, byte[] batchRequestBytes, boolean expectUtf8Response, long callbackId);

    /** Update the connection password with optional immediate authentication. */
    public static native void updateConnectionPassword(
            long clientPtr, String password, boolean immediateAuth, long callbackId);

    /** Check if the native client is connected */
    public static native boolean isConnected(long clientPtr);

    /** Get client information from native layer */
    public static native String getClientInfo(long clientPtr);

    /** Close and release a native client */
    public static native void closeClient(long clientPtr);

    /** Execute script asynchronously using glide-core's invoke_script */
    public static native void executeScriptAsync(
            long clientPtr,
            long callbackId,
            String hash,
            byte[][] keys,
            byte[][] args,
            boolean hasRoute,
            int routeType,
            String routeParam,
            boolean expectUtf8Response);

    /** Get glide-core default timeout in milliseconds */
    public static native long getGlideCoreDefaultTimeoutMs();

    /** Get glide-core default maximum inflight requests limit */
    public static native int getGlideCoreDefaultMaxInflightRequests();

    /** Execute cluster scan command asynchronously */
    public static native void executeClusterScanAsync(
            long clientPtr,
            String cursorId,
            String matchPattern,
            long count,
            String objectType,
            boolean expectUtf8Response,
            long callbackId);

    /** Enable client-side caching with tracking */
    public static native boolean enableClientTracking(
            long clientPtr,
            boolean enabled,
            int maxSize,
            long ttlSeconds,
            int trackingMode);

    /** Disable client-side caching */
    public static native boolean disableClientTracking(long clientPtr);

    /** Get value with client-side caching */
    public static native void getWithCacheAsync(
            long clientPtr,
            String key,
            long callbackId);

    /** Handle invalidation messages from server */
    public static native void handleInvalidation(long clientPtr, String[] keys);

    /** Clear entire cache */
    public static native void clearCache(long clientPtr);
}
