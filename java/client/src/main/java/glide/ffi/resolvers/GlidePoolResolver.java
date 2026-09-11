/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

import glide.api.models.configuration.GlideCredentialProvider;

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
            byte[] connectionRequestBytes,
            GlideCredentialProvider credentialProvider);

    public static native long glidePoolTryAcquire(long poolId);

    public static native int glidePoolRelease(long poolId, long clientId);

    public static native int glidePoolDestroy(long poolId);

    public static native int[] glidePoolMetrics(long poolId);
}
