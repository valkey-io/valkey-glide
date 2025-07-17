package glide.ffi.resolvers;

/**
 * JNI resolver for OpenTelemetry operations.
 * Provides native bindings for telemetry initialization and configuration.
 */
public class OpenTelemetryResolver {

    /**
     * Initialize OpenTelemetry with the provided configuration.
     *
     * @param tracesEndpoint The endpoint for trace exports
     * @param tracesSamplePercentage The sampling percentage for traces (0-100)
     * @param metricsEndpoint The endpoint for metric exports
     * @param flushIntervalMs The flush interval in milliseconds
     */
    public static native void initOpenTelemetry(
            String tracesEndpoint,
            int tracesSamplePercentage, 
            String metricsEndpoint,
            long flushIntervalMs
    );

    /**
     * Set the sampling percentage for telemetry data.
     *
     * @param percentage The sampling percentage (0-100)
     */
    public static native void setSamplePercentage(int percentage);

    /**
     * Get the current sampling percentage.
     *
     * @return The current sampling percentage
     */
    public static native int getSamplePercentage();
}