/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.ffi.resolvers;

/**
 * Resolver class for OpenTelemetry operations in Valkey GLIDE. This class provides native methods
 * to interact with OpenTelemetry functionality implemented in the Rust core library.
 */
public class OpenTelemetryResolver {

    static {
        NativeUtils.loadGlideLib();
    }

    /**
     * Initializes OpenTelemetry with the provided configuration.
     *
     * @param tracesEndpoint The endpoint for traces exporter (can be null if not used)
     * @param tracesSamplePercentage The percentage of requests to sample (0 for default)
     * @param metricsEndpoint The endpoint for metrics exporter (can be null if not used)
     * @param flushIntervalMs The interval in milliseconds between consecutive exports (0 for default)
     * @return 0 on success, error code otherwise: 1 - Missing configuration (both traces and metrics
     *     are null) 2 - Invalid traces endpoint 3 - Invalid metrics endpoint 4 - Runtime
     *     initialization failure 5 - OpenTelemetry initialization failure
     */
    public static native int initOpenTelemetry(
            String tracesEndpoint,
            int tracesSamplePercentage,
            String metricsEndpoint,
            long flushIntervalMs);

    /**
     * Creates a new OpenTelemetry span with the given name that will not be automatically dropped by
     * the Rust core. The caller is responsible for dropping this span using {@link
     * #dropOtelSpan(long)}.
     *
     * @param spanName The name of the span to create
     * @return A pointer to the created span, or 0 if creation failed
     */
    public static native long createLeakedOtelSpan(String spanName);

    /**
     * Creates a span parented to an application's serialized context. Invalid context falls back to a
     * standalone span with a warning. Ownership is the same as {@link #createLeakedOtelSpan(String)}.
     *
     * @param spanName The command or batch span name
     * @param traceId Nonzero 32-character lowercase hexadecimal trace ID
     * @param spanId Nonzero 16-character lowercase hexadecimal parent span ID
     * @param traceFlags Unsigned trace flags (0–255)
     * @param traceState Optional serialized W3C tracestate; null or empty means absent
     * @return A pointer to the created span, or 0 if creation failed
     */
    public static long createLeakedOtelSpanWithTraceContext(
            String spanName, String traceId, String spanId, int traceFlags, String traceState) {
        return createLeakedOtelSpanWithTraceContextNative(
                spanName, traceId, spanId, traceFlags, traceState);
    }

    private static native long createLeakedOtelSpanWithTraceContextNative(
            String spanName, String traceId, String spanId, int traceFlags, String traceState);

    /**
     * Drops an OpenTelemetry span that was created with {@link #createLeakedOtelSpan(String)},
     * releasing its resources.
     *
     * @param spanPtr The pointer to the span to drop
     */
    public static native void dropOtelSpan(long spanPtr);
}
