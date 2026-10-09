/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/** Native method declarations for isolated execution. */
public class GlideScopeResolver {
    static {
        NativeUtils.loadGlideLib();
    }

    /**
     * Acquire a scope, completing the future registered under {@code callbackId} with the scope id as
     * a {@link Long}, or exceptionally with the error the core classified. The core owns the wait, so
     * one call covers the whole acquire.
     *
     * @return 0 once queued; -2 if the connection request bytes could not be read, in which case the
     *     future is never completed by the native side
     */
    public static native int glideScopeAcquire(
            long clientId,
            byte[] connectionRequestBytes,
            int routingSlot,
            long timeoutMs,
            long callbackId);

    public static native int glideScopeRelease(long scopeId, long clientId);

    public static native int glideScopeExecute(long scopeId, byte[] commandBytes, long callbackId);
}
