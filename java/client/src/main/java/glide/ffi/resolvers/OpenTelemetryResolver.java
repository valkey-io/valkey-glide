/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/**
 * Native resolver for OpenTelemetry functionality.
 */
public class OpenTelemetryResolver {
    static {
        // Load the native library
        NativeUtils.loadGlideLib();
    }

    /**
     * Creates a new OpenTelemetry span with the given name.
     *
     * @param name The name of the span
     * @return A pointer to the span as a long
     */
    public static native long createOtelSpan(String name);

    /**
     * Drops an OpenTelemetry span that was previously created.
     *
     * @param spanPtr The pointer to the span to drop
     */
    public static native void dropOtelSpan(long spanPtr);
}
