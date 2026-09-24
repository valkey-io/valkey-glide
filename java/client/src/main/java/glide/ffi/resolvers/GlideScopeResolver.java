/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/** Native method declarations for isolated execution. */
public class GlideScopeResolver {
    static {
        NativeUtils.loadGlideLib();
    }

    public static native long glideScopeTryAcquire(
            long clientId, byte[] connectionRequestBytes, int routingSlot, long attemptToken);

    /**
     * Allocate a unique scope-acquire attempt token. Call once per {@code acquire()} and pass it on
     * every retry poll of {@link #glideScopeTryAcquire}, so the core dedupes a single acquire's
     * retries to one in-flight creation without serializing distinct concurrent borrowers.
     */
    public static native long glideScopeNextAttemptToken();

    public static native int glideScopeRelease(long scopeId, long clientId);

    public static native int glideScopeExecute(long scopeId, byte[] commandBytes, long callbackId);
}
