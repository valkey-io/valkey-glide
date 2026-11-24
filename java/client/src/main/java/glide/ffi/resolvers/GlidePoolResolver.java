/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/** Native method declarations for the client-instance pool. */
public class GlidePoolResolver {

    static {
        NativeUtils.loadGlideLib();
    }

    public static native long glidePoolCreate(
            int maxSize,
            int minIdle,
            long idleTimeoutMs,
            long requestTimeoutMs,
            long abandonTimeoutMs,
            byte[] connectionRequestBytes);

    public static native long glidePoolTryAcquire(long poolId);

    /** The pooled client id a current lease borrows, or -1 once that borrow has ended. */
    public static native long glidePoolLeaseClient(long poolId, long leaseId);

    public static native int glidePoolRelease(long poolId, long leaseId);

    public static native int glidePoolDestroy(long poolId);

    public static native int[] glidePoolMetrics(long poolId);
}
