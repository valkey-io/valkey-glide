package glide.ffi.resolvers;

/**
 * Resolver class for OpenTelemetry operations in Valkey GLIDE.
 * This class provides native methods to interact with OpenTelemetry functionality
 * implemented in the Rust core library.
 */
public class OpenTelemetryResolver {
    
    /**
     * Initializes OpenTelemetry with the provided configuration.
     *
     * @param tracesEndpoint The endpoint for traces exporter (can be null if not used)
     * @param tracesSamplePercentage The percentage of requests to sample (0 for default)
     * @param metricsEndpoint The endpoint for metrics exporter (can be null if not used)
     * @param flushIntervalMs The interval in milliseconds between consecutive exports (0 for default)
     * @return 0 on success, error code otherwise:
     *         1 - Missing configuration (both traces and metrics are null)
     *         2 - Invalid traces endpoint
     *         3 - Invalid metrics endpoint
     *         4 - Runtime initialization failure
     *         5 - OpenTelemetry initialization failure
     */
    public static native int initOpenTelemetry(
            String tracesEndpoint,
            int tracesSamplePercentage,
            String metricsEndpoint,
            long flushIntervalMs);
    
    /**
     * Creates a new OpenTelemetry span with the given name.
     *
     * @param spanName The name of the span to create
     * @return A pointer to the created span, or 0 if creation failed
     */
    public static native long createSpan(String spanName);
    
    /**
     * Drops an OpenTelemetry span, releasing its resources.
     *
     * @param spanPtr The pointer to the span to drop
     */
    public static native void dropSpan(long spanPtr);
}
