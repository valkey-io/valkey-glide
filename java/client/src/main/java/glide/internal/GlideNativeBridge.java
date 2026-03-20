/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.logging.Logger;
import glide.api.models.configuration.AddressResolver;
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

    /**
     * Create a new native client instance.
     *
     * <p>If an AddressResolver is provided, it will be stored as a global reference on the native
     * side to prevent garbage collection while the client is alive. The resolver will be called from
     * any thread when address resolution is needed.
     *
     * @param connectionRequestBytes Protobuf-encoded ConnectionRequest
     * @param addressResolver The address resolver callback, or null if not needed
     * @return Native client handle, or 0 on failure
     */
    public static native long createClient(
            byte[] connectionRequestBytes, AddressResolver addressResolver);

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

    /** Refresh the IAM authentication token. */
    public static native void refreshIamToken(long clientPtr, long callbackId);

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

    /** Get glide-core default connection timeout in milliseconds */
    public static native long getGlideCoreDefaultConnectionTimeoutMs();

    /** Get glide-core default request timeout in milliseconds */
    public static native long getGlideCoreDefaultRequestTimeoutMs();

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

    /** Mark a callback as timed out on the native side. */
    public static native void markTimedOut(long callbackId);
}
