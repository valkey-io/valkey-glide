/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/**
 * Native resolver for OpenTelemetry functionality.
 * This class provides JNI bindings to the Rust OpenTelemetry implementation.
 */
public class OpenTelemetryResolver {

    /**
     * Creates a new OpenTelemetry span with the given name.
     *
     * @param name The name of the span to create
     * @return A pointer to the created span
     */
    public static native long createOtelSpan(String name);

    /**
     * Drops an OpenTelemetry span, releasing its resources.
     *
     * @param spanPtr The pointer to the span to drop
     */
    public static native void dropOtelSpan(long spanPtr);
}
