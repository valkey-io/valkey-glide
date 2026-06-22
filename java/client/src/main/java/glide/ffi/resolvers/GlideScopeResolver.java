/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/** Native method declarations for isolated execution (Feature 2 scopes). */
public class GlideScopeResolver {
    static { NativeUtils.loadGlideLib(); }

    public static native long glideScopeTryAcquire(long clientId, byte[] connectionRequestBytes);
    public static native int glideScopeRelease(long scopeId, long clientId);
    public static native int glideScopeExecute(long scopeId, byte[] commandBytes, long callbackId);
}
