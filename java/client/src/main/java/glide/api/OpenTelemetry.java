/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 *
 * ⚠️ OpenTelemetry can only be initialized once per process. Calling `OpenTelemetry.init()` more than once will be ignored.
 * If you need to change configuration, restart the process with new settings.
 *
 * ### OpenTelemetry
 *
 * - **openTelemetryConfig**: Use this object to configure OpenTelemetry exporters and options.
 *   - **traces**: (optional) Configure trace exporting.
 *     - **endpoint**: The collector endpoint for traces. Supported protocols:
 *       - `http://` or `https://` for HTTP/HTTPS
 *       - `grpc://` for gRPC
 *       - `file://` for local file export (see below)
 *     - **samplePercentage**: (optional) The percentage of requests to sample and create a span for, used to measure command duration. Must be between 0 and 100. Defaults to 1 if not specified.
 *       Note: There is a tradeoff between sampling percentage and performance. Higher sampling percentages will provide more detailed telemetry data but will impact performance.
 *       It is recommended to keep this number low (1-5%) in production environments unless you have specific needs for higher sampling rates.
 *   - **metrics**: (optional) Configure metrics exporting.
 *     - **endpoint**: The collector endpoint for metrics. Same protocol rules as above.
 *   - **flushIntervalMs**: (optional) Interval in milliseconds for flushing data to the collector. Must be a positive integer. Defaults to 5000ms if not specified.
 *
 * #### File Exporter Details
 * - For `file://` endpoints:
 *   - The path must start with `file://` (e.g., `file:///tmp/otel` or `file:///tmp/otel/traces.json`).
 *   - If the path is a directory or lacks a file extension, data is written to `signals.json` in that directory.
 *   - If the path includes a filename with an extension, that file is used as-is.
 *   - The parent directory must already exist; otherwise, initialization will fail with an InvalidInput error.
 *   - If the target file exists, new data is appended (not overwritten).
 *
 * #### Validation Rules
 * - `flushIntervalMs` must be a positive integer.
 * - `samplePercentage` must be between 0 and 100.
 * - File exporter paths must start with `file://` and have an existing parent directory.
 * - Invalid configuration will throw an error synchronously when calling `OpenTelemetry.init()`.
 */

package glide.api;

import glide.api.models.exceptions.ConfigurationException;
import glide.ffi.resolvers.OpenTelemetryResolver;
import glide.api.logging.Logger;

import java.util.Random;

/**
 * OpenTelemetry integration for Valkey GLIDE.
 */
public class OpenTelemetry {
    private static OpenTelemetry instance = null;
    private static OpenTelemetryConfig openTelemetryConfig = null;
    private static final Random random = new Random();

    /**
     * Configuration for OpenTelemetry integration.
     */
    public static class OpenTelemetryConfig {
        private TracesConfig traces;
        private MetricsConfig metrics;
        private Long flushIntervalMs;

        /**
         * Creates a new OpenTelemetryConfig builder.
         * @return A new OpenTelemetryConfig builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for OpenTelemetryConfig.
         */
        public static class Builder {
            private TracesConfig traces;
            private MetricsConfig metrics;
            private Long flushIntervalMs = 5000L; // Default value

            /**
             * Sets the traces configuration.
             * @param traces The traces configuration
             * @return This builder
             */
            public Builder traces(TracesConfig traces) {
                this.traces = traces;
                return this;
            }

            /**
             * Sets the metrics configuration.
             * @param metrics The metrics configuration
             * @return This builder
             */
            public Builder metrics(MetricsConfig metrics) {
                this.metrics = metrics;
                return this;
            }

            /**
             * Sets the flush interval in milliseconds.
             * @param flushIntervalMs The flush interval in milliseconds
             * @return This builder
             */
            public Builder flushIntervalMs(Long flushIntervalMs) {
                this.flushIntervalMs = flushIntervalMs;
                return this;
            }

            /**
             * Builds the OpenTelemetryConfig.
             * @return The built OpenTelemetryConfig
             */
            public OpenTelemetryConfig build() {
                OpenTelemetryConfig config = new OpenTelemetryConfig();
                config.traces = this.traces;
                config.metrics = this.metrics;
                config.flushIntervalMs = this.flushIntervalMs;
                return config;
            }
        }

        /**
         * Gets the traces configuration.
         * @return The traces configuration
         */
        public TracesConfig getTraces() {
            return traces;
        }

        /**
         * Gets the metrics configuration.
         * @return The metrics configuration
         */
        public MetricsConfig getMetrics() {
            return metrics;
        }

        /**
         * Gets the flush interval in milliseconds.
         * @return The flush interval in milliseconds
         */
        public Long getFlushIntervalMs() {
            return flushIntervalMs;
        }
    }

    /**
     * Configuration for OpenTelemetry traces.
     */
    public static class TracesConfig {
        private String endpoint;
        private Integer samplePercentage;

        /**
         * Creates a new TracesConfig builder.
         * @return A new TracesConfig builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for TracesConfig.
         */
        public static class Builder {
            private String endpoint;
            private Integer samplePercentage = 1; // Default value

            /**
             * Sets the endpoint for traces.
             * @param endpoint The endpoint for traces
             * @return This builder
             */
            public Builder endpoint(String endpoint) {
                this.endpoint = endpoint;
                return this;
            }

            /**
             * Sets the sample percentage for traces.
             * @param samplePercentage The sample percentage for traces
             * @return This builder
             */
            public Builder samplePercentage(Integer samplePercentage) {
                this.samplePercentage = samplePercentage;
                return this;
            }

            /**
             * Builds the TracesConfig.
             * @return The built TracesConfig
             */
            public TracesConfig build() {
                TracesConfig config = new TracesConfig();
                config.endpoint = this.endpoint;
                config.samplePercentage = this.samplePercentage;
                return config;
            }
        }

        /**
         * Gets the endpoint for traces.
         * @return The endpoint for traces
         */
        public String getEndpoint() {
            return endpoint;
        }

        /**
         * Gets the sample percentage for traces.
         * @return The sample percentage for traces
         */
        public Integer getSamplePercentage() {
            return samplePercentage;
        }

        /**
         * Sets the sample percentage for traces.
         * @param samplePercentage The sample percentage for traces
         */
        public void setSamplePercentage(Integer samplePercentage) {
            this.samplePercentage = samplePercentage;
        }
    }

    /**
     * Configuration for OpenTelemetry metrics.
     */
    public static class MetricsConfig {
        private String endpoint;

        /**
         * Creates a new MetricsConfig builder.
         * @return A new MetricsConfig builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for MetricsConfig.
         */
        public static class Builder {
            private String endpoint;

            /**
             * Sets the endpoint for metrics.
             * @param endpoint The endpoint for metrics
             * @return This builder
             */
            public Builder endpoint(String endpoint) {
                this.endpoint = endpoint;
                return this;
            }

            /**
             * Builds the MetricsConfig.
             * @return The built MetricsConfig
             */
            public MetricsConfig build() {
                MetricsConfig config = new MetricsConfig();
                config.endpoint = this.endpoint;
                return config;
            }
        }

        /**
         * Gets the endpoint for metrics.
         * @return The endpoint for metrics
         */
        public String getEndpoint() {
            return endpoint;
        }
    }

    /**
     * Example usage:
     * ```java
     * import glide.api.OpenTelemetry;
     *
     * OpenTelemetry.init(
     *     OpenTelemetry.OpenTelemetryConfig.builder()
     *         .traces(
     *             OpenTelemetry.TracesConfig.builder()
     *                 .endpoint("http://localhost:4318/v1/traces")
     *                 .samplePercentage(10) // Optional, defaults to 1
     *                 .build()
     *         )
     *         .metrics(
     *             OpenTelemetry.MetricsConfig.builder()
     *                 .endpoint("http://localhost:4318/v1/metrics")
     *                 .build()
     *         )
     *         .flushIntervalMs(5000L) // Optional, defaults to 5000
     *         .build()
     * );
     * ```
     *
     * Initialize the OpenTelemetry instance
     * @param config The OpenTelemetry configuration
     */
    public static void init(OpenTelemetryConfig config) {
        if (instance == null) {
            internalInit(config);
            Logger.log(
                Logger.Level.INFO,
                "GlideOpenTelemetry",
                "OpenTelemetry initialized"
            );
            return;
        }

        Logger.log(
            Logger.Level.WARN,
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized"
        );
    }

    private static void internalInit(OpenTelemetryConfig config) {
        openTelemetryConfig = config;

        String tracesEndpoint = null;
        int tracesSamplePercentage = -1;
        if (config.getTraces() != null) {
            tracesEndpoint = config.getTraces().getEndpoint();
            if (config.getTraces().getSamplePercentage() != null) {
                tracesSamplePercentage = config.getTraces().getSamplePercentage();
            }
        }

        String metricsEndpoint = null;
        if (config.getMetrics() != null) {
            metricsEndpoint = config.getMetrics().getEndpoint();
        }

        long flushIntervalMs = config.getFlushIntervalMs() != null ?
            config.getFlushIntervalMs() : 5000L;

        OpenTelemetryResolver.initOpenTelemetry(
            tracesEndpoint,
            tracesSamplePercentage,
            metricsEndpoint,
            flushIntervalMs
        );

        instance = new OpenTelemetry();
    }

    /**
     * Check if the OpenTelemetry instance is initialized
     * @return True if the OpenTelemetry instance is initialized, false otherwise
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    /**
     * Get the sample percentage for traces
     * @return The sample percentage for traces only if OpenTelemetry is initialized and the traces config is set, otherwise null.
     */
    public static Integer getSamplePercentage() {
        if (openTelemetryConfig != null && openTelemetryConfig.getTraces() != null) {
            return openTelemetryConfig.getTraces().getSamplePercentage();
        }
        return null;
    }

    /**
     * Determines if the current request should be sampled for OpenTelemetry tracing.
     * Uses the configured sample percentage to randomly decide whether to create a span for this request.
     * @return true if the request should be sampled, false otherwise
     */
    public static boolean shouldSample() {
        Integer percentage = getSamplePercentage();
        return isInitialized() &&
               percentage != null &&
               random.nextDouble() * 100 < percentage;
    }

    /**
     * Set the percentage of requests to be sampled and traced. Must be a value between 0 and 100.
     * This setting only affects traces, not metrics.
     * @param percentage The sample percentage 0-100
     * @throws ConfigurationException if OpenTelemetry is not initialized or traces config is not set
     * @remarks
     * This method can be called at runtime to change the sampling percentage without reinitializing OpenTelemetry.
     */
    public static void setSamplePercentage(int percentage) {
        if (openTelemetryConfig == null || openTelemetryConfig.getTraces() == null) {
            throw new ConfigurationException("OpenTelemetry config traces not initialized");
        }

        openTelemetryConfig.getTraces().setSamplePercentage(percentage);
    }

    /**
     * Creates a new OpenTelemetry span with the given name.
     * @param name The name of the span
     * @return A pointer to the span
     */
    public static long createSpan(String name) {
        return OpenTelemetryResolver.createLeakedOtelSpan(name);
    }

    /**
     * Drops an OpenTelemetry span.
     * @param spanPtr The pointer to the span
     */
    public static void dropSpan(long spanPtr) {
        OpenTelemetryResolver.dropOtelSpan(spanPtr);
    }
}
