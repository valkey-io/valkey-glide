package glide.ffi.resolvers;

import glide.api.logging.Logger;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolver class for OpenTelemetry operations in Valkey GLIDE. This class provides native methods
 * to interact with OpenTelemetry functionality implemented in the Rust core library.
 */
public class OpenTelemetryResolver {

    static {
        try {
            System.loadLibrary("valkey_glide");
        } catch (UnsatisfiedLinkError e) {
            Logger.error("OpenTelemetryResolver", "Failed to load native library: " + e.getMessage());
            // Propagate linkage error as-is per policy
            throw e;
        }
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
    private static native int initOpenTelemetryNative(
            String tracesEndpoint,
            int tracesSamplePercentage,
            String metricsEndpoint,
            long flushIntervalMs);

    /**
     * Initialize OpenTelemetry and throw if configuration is invalid.
     */
    public static void initOpenTelemetry(
            String tracesEndpoint,
            int tracesSamplePercentage,
            String metricsEndpoint,
            long flushIntervalMs) {
        // Pre-validate to produce user-friendly error messages expected by tests
        validateConfig(tracesEndpoint, tracesSamplePercentage, metricsEndpoint, flushIntervalMs);
        int code = initOpenTelemetryNative(tracesEndpoint, tracesSamplePercentage, metricsEndpoint, flushIntervalMs);
        if (code != 0) {
            String reason;
            switch (code) {
                case 1:
                    reason = "Missing configuration";
                    break;
                case 2:
                    reason = "Invalid traces endpoint";
                    break;
                case 3:
                    reason = "Invalid metrics endpoint";
                    break;
                case 4:
                    reason = "Runtime initialization failure";
                    break;
                default:
                    reason = "OpenTelemetry initialization failure";
            }
            throw new IllegalArgumentException(
                    "OpenTelemetry initialization failed: " + reason + " (code=" + code + ")");
        }
    }

    private static void validateConfig(
            String tracesEndpoint,
            int tracesSamplePercentage,
            String metricsEndpoint,
            long flushIntervalMs) {
        if (flushIntervalMs <= 0) {
            throw new IllegalArgumentException("flushIntervalMs must be a positive integer");
        }
        if (tracesEndpoint == null && metricsEndpoint == null) {
            // Handled earlier in OpenTelemetry.init, but keep for safety
            throw new IllegalArgumentException("At least one of traces or metrics must be provided");
        }
        if (tracesSamplePercentage < 0) {
            throw new IllegalArgumentException(
                    "InvalidInput: traces_sample_percentage must be a positive integer (got: " + tracesSamplePercentage + ")");
        }
        if (tracesEndpoint != null) {
            validateEndpoint(tracesEndpoint);
        }
        if (metricsEndpoint != null) {
            validateEndpoint(metricsEndpoint);
        }
    }

    private static void validateEndpoint(String endpoint) {
        if (endpoint.startsWith("file://")) {
            String pathStr = endpoint.substring("file://".length());
            Path path = Path.of(pathStr);
            Path parent = path.getParent();
            if (parent != null && (!Files.exists(parent) || !Files.isDirectory(parent))) {
                throw new IllegalArgumentException(
                        "The directory does not exist or is not a directory: " + parent.toString());
            }
            return;
        }
        if (endpoint.startsWith("file:")) {
            throw new IllegalArgumentException("File path must start with 'file://'");
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://") || endpoint.startsWith("grpc://")) {
            return;
        }
        throw new IllegalArgumentException("Parse error");
    }

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
     * Drops an OpenTelemetry span that was created with {@link #createLeakedOtelSpan(String)},
     * releasing its resources.
     *
     * @param spanPtr The pointer to the span to drop
     */
    public static native void dropOtelSpan(long spanPtr);
}