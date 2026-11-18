/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import glide.api.logging.Logger;
import glide.api.models.exceptions.ConfigurationError;
import glide.ffi.resolvers.OpenTelemetryResolver;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * OpenTelemetry integration for Valkey GLIDE.
 *
 * <p>⚠️ OpenTelemetry can only be initialized once per process. Calling {@link
 * OpenTelemetry#init(OpenTelemetryConfig)} more than once will be ignored. If you need to change
 * configuration, restart the process with new settings.
 *
 * <p>
 *
 * <h4>OpenTelemetry </h4>
 *
 * <ul>
 *   <li><b>openTelemetryConfig</b>: Use this object to configure OpenTelemetry exporters and
 *       options.
 *       <ul>
 *         <li><b>traces</b>: (optional) Configure trace exporting.
 *             <ul>
 *               <li><b>endpoint</b>: The collector endpoint for traces. Supported protocols:
 *                   <ul>
 *                     <li><code>http://</code> or <code>https://</code> for HTTP/HTTPS
 *                     <li><code>grpc://</code> for gRPC
 *                     <li><code>file://</code> for local file export (see below)
 *                   </ul>
 *               <li><b>samplePercentage</b>: (optional) The percentage of requests to sample and
 *                   create a span for, used to measure command duration. Must be between 0 and 100.
 *                   Defaults to 1 if not specified. Note: There is a tradeoff between sampling
 *                   percentage and performance. Higher sampling percentages will provide more
 *                   detailed telemetry data but will impact performance. It is recommended to keep
 *                   this number low (1-5%) in production environments unless you have specific
 *                   needs for higher sampling rates.
 *             </ul>
 *         <li><b>metrics</b>: (optional) Configure metrics exporting.
 *             <ul>
 *               <li><b>endpoint</b>: The collector endpoint for metrics. Same protocol rules as
 *                   above.
 *             </ul>
 *         <li><b>flushIntervalMs</b>: (optional) Interval in milliseconds for flushing data to the
 *             collector. Must be a positive integer. Defaults to 5000ms if not specified.
 *         <li><b>parentSpanContextProvider</b>: (optional) Callback supplying application parent
 *             context for sampled command and batch spans. See {@link
 *             OpenTelemetryConfig.Builder#parentSpanContextProvider(Supplier)}.
 *       </ul>
 * </ul>
 *
 * #### File Exporter Details
 *
 * <ul>
 *   <li>For <code>file://</code> endpoints:
 *       <ul>
 *         <li>The path must start with <code>file://</code> (e.g., <code>file:///tmp/otel</code> or
 *             <code>file:///tmp/otel/traces.json</code>).
 *         <li>If the path is a directory or lacks a file extension, data is written to <code>
 *             signals.json</code> in that directory.
 *         <li>If the path includes a filename with an extension, that file is used as-is.
 *         <li>The parent directory must already exist; otherwise, initialization will fail with an
 *             InvalidInput error.
 *         <li>If the target file exists, new data is appended (not overwritten).
 *       </ul>
 * </ul>
 *
 * #### Validation Rules
 *
 * <ul>
 *   <li><code>flushIntervalMs</code> must be a positive integer.
 *   <li><code>samplePercentage</code> must be between 0 and 100.
 *   <li>File exporter paths must start with <code>file://</code> and have an existing parent
 *       directory.
 *   <li>Invalid configuration will throw an error synchronously when calling {@link
 *       OpenTelemetry#init(OpenTelemetryConfig)}
 * </ul>
 */
public class OpenTelemetry {
    private static OpenTelemetry openTelemetry = null;
    private static OpenTelemetryConfig openTelemetryConfig = null;
    private static final Random random = new Random();
    private static volatile ParentSpanContextProvider parentSpanContextProvider;
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID_PATTERN = Pattern.compile("[0-9a-f]{16}");

    private static final class ParentSpanContextProvider {
        private final Supplier<GlideSpanContext> supplier;
        private final AtomicBoolean warningLogged = new AtomicBoolean();

        private ParentSpanContextProvider(Supplier<GlideSpanContext> supplier) {
            this.supplier = supplier;
        }

        private void warnOnce(String message) {
            if (warningLogged.compareAndSet(false, true)) {
                Logger.log(Logger.Level.WARN, "GlideOpenTelemetry", message);
            }
        }
    }

    /**
     * Immutable parent span context, independent of the OpenTelemetry Java API. Invalid values are
     * ignored when submitting a command, with a warning and a fallback to a standalone span.
     */
    public static final class GlideSpanContext {
        private final String traceId;
        private final String spanId;
        private final int traceFlags;
        private final String traceState;

        /**
         * Creates a parent context. GLIDE never takes ownership of, or ends, the application span.
         *
         * @param traceId Nonzero trace ID as 32 lowercase hexadecimal characters
         * @param spanId Nonzero span ID as 16 lowercase hexadecimal characters
         * @param traceFlags Unsigned trace flags (0–255); bit 0 is the sampled flag
         * @param traceState Optional serialized W3C tracestate; null or empty means no tracestate
         */
        public GlideSpanContext(String traceId, String spanId, int traceFlags, String traceState) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.traceFlags = traceFlags;
            this.traceState = traceState;
        }

        /**
         * @return The parent trace ID
         */
        public String getTraceId() {
            return traceId;
        }

        /**
         * @return The parent span ID
         */
        public String getSpanId() {
            return spanId;
        }

        /**
         * @return The unsigned trace flags
         */
        public int getTraceFlags() {
            return traceFlags;
        }

        /**
         * @return The serialized tracestate, or null if absent
         */
        public String getTraceState() {
            return traceState;
        }
    }

    /** Configuration for OpenTelemetry integration. */
    public static class OpenTelemetryConfig {
        private TracesConfig traces;
        private MetricsConfig metrics;
        private Long flushIntervalMs;
        private Supplier<GlideSpanContext> parentSpanContextProvider;

        /**
         * Creates a new OpenTelemetryConfig builder.
         *
         * @return A new OpenTelemetryConfig builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /** Builder for OpenTelemetryConfig. */
        public static class Builder {
            private TracesConfig traces;
            private MetricsConfig metrics;
            private Long flushIntervalMs = 5000L; // Default value
            private Supplier<GlideSpanContext> parentSpanContextProvider;

            /**
             * Sets the callback used to parent sampled GLIDE command and batch spans. The callback stays
             * in Java; only its returned context fields cross JNI.
             *
             * <p>Propagation is opt-in. Applications supply their own OpenTelemetry Java API/SDK and
             * configure it separately from GLIDE's native exporter. With that SDK configured and an
             * existing GLIDE {@code client}, initialize GLIDE and submit commands inside an application
             * span's scope:
             *
             * <pre>{@code
             * OpenTelemetry.init(OpenTelemetry.OpenTelemetryConfig.builder()
             *     .traces(OpenTelemetry.TracesConfig.builder()
             *         .endpoint("http://localhost:4318/v1/traces")
             *         .samplePercentage(100)
             *         .build())
             *     .parentSpanContextProvider(() -> {
             *     io.opentelemetry.api.trace.SpanContext ctx =
             *         io.opentelemetry.api.trace.Span.current().getSpanContext();
             *     if (!ctx.isValid()) return null;
             *     String state = ctx.getTraceState().asMap().entrySet().stream()
             *         .map(e -> e.getKey() + "=" + e.getValue())
             *         .collect(java.util.stream.Collectors.joining(","));
             *     return new OpenTelemetry.GlideSpanContext(
             *         ctx.getTraceId(), ctx.getSpanId(),
             *         Byte.toUnsignedInt(ctx.getTraceFlags().asByte()), state);
             *     })
             *     .build());
             * io.opentelemetry.api.trace.Tracer tracer =
             *     io.opentelemetry.api.GlobalOpenTelemetry.getTracer("my-application");
             * io.opentelemetry.api.trace.Span parent = tracer.spanBuilder("request").startSpan();
             * try (io.opentelemetry.context.Scope scope = parent.makeCurrent()) {
             *     client.get("key").join();
             * } finally {
             *     parent.end();
             * }
             * }</pre>
             *
             * @param provider Callback returning the active parent, or null to disable propagation
             * @return This builder
             * @see OpenTelemetry#setParentSpanContextProvider(Supplier)
             */
            public Builder parentSpanContextProvider(Supplier<GlideSpanContext> provider) {
                this.parentSpanContextProvider = provider;
                return this;
            }

            /**
             * Sets the traces configuration.
             *
             * @param traces The traces configuration
             * @return This builder
             */
            public Builder traces(TracesConfig traces) {
                this.traces = traces != null ? traces.copy() : null;
                return this;
            }

            /**
             * Sets the metrics configuration.
             *
             * @param metrics The metrics configuration
             * @return This builder
             */
            public Builder metrics(MetricsConfig metrics) {
                this.metrics = metrics != null ? metrics.copy() : null;
                return this;
            }

            /**
             * Sets the flush interval in milliseconds.
             *
             * @param flushIntervalMs The flush interval in milliseconds
             * @return This builder
             */
            public Builder flushIntervalMs(Long flushIntervalMs) {
                this.flushIntervalMs = flushIntervalMs;
                return this;
            }

            /**
             * Builds the OpenTelemetryConfig.
             *
             * @return The built OpenTelemetryConfig
             */
            public OpenTelemetryConfig build() {
                OpenTelemetryConfig config = new OpenTelemetryConfig();
                config.traces = this.traces != null ? this.traces.copy() : null;
                config.metrics = this.metrics != null ? this.metrics.copy() : null;
                config.flushIntervalMs = this.flushIntervalMs;
                config.parentSpanContextProvider = this.parentSpanContextProvider;
                return config;
            }
        }

        /**
         * Gets the traces configuration.
         *
         * @return The traces configuration
         */
        public TracesConfig getTraces() {
            return traces == null ? null : traces.copy();
        }

        /**
         * Gets the metrics configuration.
         *
         * @return The metrics configuration
         */
        public MetricsConfig getMetrics() {
            return metrics == null ? null : metrics.copy();
        }

        /**
         * Gets the flush interval in milliseconds.
         *
         * @return The flush interval in milliseconds
         */
        public Long getFlushIntervalMs() {
            return flushIntervalMs;
        }

        /**
         * @return The configured parent context provider, or null if absent
         */
        public Supplier<GlideSpanContext> getParentSpanContextProvider() {
            return parentSpanContextProvider;
        }

        private void updateSamplePercentage(int samplePercentage) {
            if (traces == null) {
                throw new ConfigurationError("Traces configuration is not initialized");
            }
            TracesConfig updated = traces.copy();
            updated.setSamplePercentage(samplePercentage);
            traces = updated;
        }
    }

    /** Configuration for OpenTelemetry traces. */
    public static class TracesConfig {
        private String endpoint;
        private Integer samplePercentage;

        /**
         * Creates a new TracesConfig builder.
         *
         * @return A new TracesConfig builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /** Builder for TracesConfig. */
        public static class Builder {
            private String endpoint;
            private Integer samplePercentage = 1; // Default value

            /**
             * Sets the endpoint for traces.
             *
             * @param endpoint The endpoint for traces
             * @return This builder
             */
            public Builder endpoint(String endpoint) {
                this.endpoint = endpoint;
                return this;
            }

            /**
             * Sets the sample percentage for traces.
             *
             * @param samplePercentage The sample percentage for traces
             * @return This builder
             */
            public Builder samplePercentage(Integer samplePercentage) {
                this.samplePercentage = samplePercentage;
                return this;
            }

            /**
             * Builds the TracesConfig.
             *
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
         *
         * @return The endpoint for traces
         */
        public String getEndpoint() {
            return endpoint;
        }

        /**
         * Gets the sample percentage for traces.
         *
         * @return The sample percentage for traces
         */
        public Integer getSamplePercentage() {
            return samplePercentage;
        }

        /**
         * Sets the sample percentage for traces.
         *
         * @param samplePercentage The sample percentage for traces
         * @throws ConfigurationError if the sample percentage is not between 0 and 100
         */
        void setSamplePercentage(Integer samplePercentage) {
            if (samplePercentage < 0 || samplePercentage > 100) {
                throw new ConfigurationError("Sample percentage must be between 0 and 100");
            }
            this.samplePercentage = samplePercentage;
        }

        TracesConfig copy() {
            TracesConfig clone = new TracesConfig();
            clone.endpoint = this.endpoint;
            clone.samplePercentage = this.samplePercentage;
            return clone;
        }
    }

    /** Configuration for OpenTelemetry metrics. */
    public static class MetricsConfig {
        private String endpoint;

        /**
         * Creates a new MetricsConfig builder.
         *
         * @return A new MetricsConfig builder
         */
        public static Builder builder() {
            return new Builder();
        }

        /** Builder for MetricsConfig. */
        public static class Builder {
            private String endpoint;

            /**
             * Sets the endpoint for metrics.
             *
             * @param endpoint The endpoint for metrics
             * @return This builder
             */
            public Builder endpoint(String endpoint) {
                this.endpoint = endpoint;
                return this;
            }

            /**
             * Builds the MetricsConfig.
             *
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
         *
         * @return The endpoint for metrics
         */
        public String getEndpoint() {
            return endpoint;
        }

        MetricsConfig copy() {
            MetricsConfig clone = new MetricsConfig();
            clone.endpoint = this.endpoint;
            return clone;
        }
    }

    /**
     * Initializes the OpenTelemetry integration with the provided configuration. This method should
     * be called before any Valkey GLIDE client operations to enable OpenTelemetry tracing and metrics
     * collection. If OpenTelemetry is already initialized, this method will log a warning and take no
     * action.
     *
     * @param config The OpenTelemetry configuration containing settings for traces and metrics
     * @example
     *     <pre>{@code
     * import glide.api.OpenTelemetry;
     * OpenTelemetry.init(
     *      OpenTelemetry.OpenTelemetryConfig.builder()
     *         .traces(
     *             OpenTelemetry.TracesConfig.builder()
     *                 .endpoint("http://localhost:4318/v1/traces")
     *                 .samplePercentage(10) // Optional, defaults to 1. Can also be changed at runtime via setSamplePercentage().
     *                 .build()
     *          )
     *          .metrics(
     *             OpenTelemetry.MetricsConfig.builder()
     *                 .endpoint("http://localhost:4318/v1/metrics")
     *                 .build()
     *          )
     *         .flushIntervalMs(5000L) // Optional, defaults to 5000
     *         .build()
     * );
     * }</pre>
     */
    public static synchronized void init(OpenTelemetryConfig config) {
        if (openTelemetry == null) {
            internalInit(config);
            Logger.log(Logger.Level.INFO, "GlideOpenTelemetry", "OpenTelemetry initialized");
            return;
        }

        Logger.log(Logger.Level.WARN, "GlideOpenTelemetry", "OpenTelemetry already initialized");
    }

    private static void internalInit(OpenTelemetryConfig config) {
        String tracesEndpoint = null;
        int tracesSamplePercentage = -1;
        // TODO #7706: Remove once glide-telemetry rejects a config with no exporter.
        if (config.getTraces() == null && config.getMetrics() == null) {
            Logger.log(
                    Logger.Level.INFO, "GlideOpenTelemetry", "Error: Both traces and metrics are null");
            throw new ConfigurationError("At least one of traces or metrics must be provided");
        }
        TracesConfig tracesConfig = config.getTraces();
        if (tracesConfig != null) {
            tracesEndpoint = tracesConfig.getEndpoint();
            if (tracesConfig.getSamplePercentage() != null) {
                tracesSamplePercentage = tracesConfig.getSamplePercentage();
            }
        }

        String metricsEndpoint = null;
        MetricsConfig metricsConfig = config.getMetrics();
        if (metricsConfig != null) {
            metricsEndpoint = metricsConfig.getEndpoint();
        }

        long flushIntervalMs =
                config.getFlushIntervalMs() != null ? config.getFlushIntervalMs() : 5000L;

        int rc =
                OpenTelemetryResolver.initOpenTelemetry(
                        tracesEndpoint, tracesSamplePercentage, metricsEndpoint, flushIntervalMs);
        if (rc != 0) {
            String msg;
            switch (rc) {
                case 1:
                    msg = "Missing configuration";
                    break;
                case 2:
                case 3:
                    msg = "Parse error";
                    break;
                case 4:
                case 5:
                default:
                    msg = "OpenTelemetry initialization failure";
                    break;
            }
            throw new ConfigurationError(msg);
        }

        // Retain only exporter/sampling settings, not the initialization callback. Otherwise
        // replacing or clearing the active provider would keep the original callback alive.
        openTelemetryConfig =
                OpenTelemetryConfig.builder()
                        .traces(config.getTraces())
                        .metrics(config.getMetrics())
                        .flushIntervalMs(config.getFlushIntervalMs())
                        .build();
        setParentSpanContextProvider(config.getParentSpanContextProvider());
        openTelemetry = new OpenTelemetry();
    }

    /**
     * Registers, replaces, or clears the parent context provider without reinitializing telemetry.
     *
     * <p>The provider is process-global and applies to all GLIDE clients until replaced or cleared.
     * Register it through the initialization config or after {@link #init(OpenTelemetryConfig)}:
     * successful initialization replaces any previously registered provider with the configured one,
     * including null. Replacement or clearing affects subsequent captures; an already-captured
     * callback may still finish executing.
     *
     * <p>The provider runs synchronously on the submitting thread, once per sampled command or batch
     * (at batch execution, not construction). Keep it lightweight and thread-safe: avoid I/O or
     * expensive computation. Applications must propagate their own context across executor threads.
     * Returning null or throwing a RuntimeException falls back to a standalone span. Java provider
     * failures and invalid IDs/flags produce at most one warning per registration; repeated native
     * context-validation warnings are rate-limited.
     *
     * <p>The provider is not called when GLIDE's percentage sampling rejects an operation. A sampled
     * parent does not override that decision. A valid unsampled parent is preserved, allowing the
     * native OpenTelemetry sampler to honor its sampling flag.
     *
     * @param provider Callback returning the active parent context, or null to clear
     */
    public static void setParentSpanContextProvider(Supplier<GlideSpanContext> provider) {
        parentSpanContextProvider = provider == null ? null : new ParentSpanContextProvider(provider);
    }

    /**
     * Captures the current parent context for internal command submission. Call only after sampling.
     * Tracestate is parsed by the native OpenTelemetry implementation when creating the span.
     *
     * @return Context with validated IDs and flags, or null if absent, invalid, or the provider
     *     failed. Tracestate validation occurs during native span creation.
     */
    public static GlideSpanContext getParentSpanContext() {
        ParentSpanContextProvider provider = parentSpanContextProvider;
        if (provider == null) {
            return null;
        }
        GlideSpanContext context;
        try {
            context = provider.supplier.get();
        } catch (RuntimeException e) {
            provider.warnOnce("Parent span context provider failed; falling back to a standalone span");
            return null;
        }
        if (context == null) {
            return null;
        }
        if (context.traceId == null
                || !TRACE_ID_PATTERN.matcher(context.traceId).matches()
                || context.traceId.equals("00000000000000000000000000000000")
                || context.spanId == null
                || !SPAN_ID_PATTERN.matcher(context.spanId).matches()
                || context.spanId.equals("0000000000000000")
                || context.traceFlags < 0
                || context.traceFlags > 255) {
            provider.warnOnce("Invalid parent span context; falling back to a standalone span");
            return null;
        }
        return context;
    }

    /**
     * Check if the OpenTelemetry instance is initialized
     *
     * @return True if the OpenTelemetry instance is initialized, false otherwise
     */
    public static boolean isInitialized() {
        return openTelemetry != null;
    }

    /**
     * Get the sample percentage for traces
     *
     * @return The sample percentage for traces only if OpenTelemetry is initialized and the traces
     *     config is set, otherwise null.
     */
    public static Integer getSamplePercentage() {
        if (openTelemetryConfig != null && openTelemetryConfig.getTraces() != null) {
            return openTelemetryConfig.getTraces().getSamplePercentage();
        }
        return null;
    }

    /**
     * Determines if the current request should be sampled for OpenTelemetry tracing. Uses the
     * configured sample percentage to randomly decide whether to create a span for this request.
     *
     * @return true if the request should be sampled, false otherwise
     */
    public static boolean shouldSample() {
        Integer percentage = getSamplePercentage();
        return isInitialized() && percentage != null && random.nextDouble() * 100 < percentage;
    }

    /**
     * Set the percentage of requests to be sampled and traced. Must be a value between 0 and 100.
     * This setting only affects traces, not metrics.
     *
     * @param percentage The sample percentage 0-100
     * @throws ConfigurationError if OpenTelemetry is not initialized or traces config is not set
     * @remarks This method can be called at runtime to change the sampling percentage without
     *     reinitializing OpenTelemetry.
     */
    public static void setSamplePercentage(int percentage) {
        if (openTelemetryConfig == null || openTelemetryConfig.traces == null) {
            throw new ConfigurationError("OpenTelemetry config traces not initialized");
        }

        openTelemetryConfig.updateSamplePercentage(percentage);
    }
}
