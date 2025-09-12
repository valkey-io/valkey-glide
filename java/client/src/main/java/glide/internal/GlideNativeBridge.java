/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.logging.Logger;
import glide.ffi.resolvers.NativeUtils;

/**
 * JNI bridge for the native Valkey client. Methods expose a handle-based API for safe
 * cross-language operations.
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
            int maxInflightRequests);

    /** Execute command asynchronously */
    public static native void executeCommandAsync(
            long clientPtr, byte[] requestBytes, long callbackId);

    /** Execute binary command with mixed String/byte[] arguments asynchronously */
    public static native void executeBinaryCommandAsync(
            long clientPtr, byte[] requestBytes, long callbackId);

    /** Execute batch (pipeline/transaction) asynchronously */
    public static native void executeBatchAsync(
            long clientPtr, byte[] batchRequestBytes, long callbackId);

    /** Execute binary-safe PUBLISH/SPUBLISH asynchronously */
    public static native void executePublishBinaryAsync(
            long clientPtr, boolean sharded, byte[] channel, byte[] message, long callbackId);

    /** Update the connection password with optional immediate authentication. */
    public static native void updateConnectionPassword(
            long clientPtr, String password, boolean immediateAuth, long callbackId);

    /** Check if the native client is connected */
    public static native boolean isConnected(long clientPtr);

    /** Get client information from native layer */
    public static native String getClientInfo(long clientPtr);

    /** Close and release a native client */
    public static native void closeClient(long clientPtr);

    /** Execute cluster scan command asynchronously */
    public static native void executeClusterScanAsync(
            long clientPtr, byte[] requestBytes, long callbackId);

    /** Execute script asynchronously using glide-core's invoke_script */
    public static native void executeScriptAsync(
            long clientPtr,
            long callbackId,
            String hash,
            String[] keys,
            String[] args,
            boolean hasRoute,
            int routeType,
            String routeParam);

    /** Get glide-core default timeout in milliseconds */
    public static native long getGlideCoreDefaultTimeoutMs();

    /** Get glide-core default maximum inflight requests limit */
    public static native int getGlideCoreDefaultMaxInflightRequests();
}
