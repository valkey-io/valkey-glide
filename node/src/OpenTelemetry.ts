/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import {
    ConfigurationError,
    InitOpenTelemetry,
    Logger,
    OpenTelemetryConfig,
} from ".";

/**
 * ⚠️ OpenTelemetry can only be initialized once per process. Calling `OpenTelemetry.init()` more than once will be ignored.
 * If you need to change configuration, restart the process with new settings.
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
export class OpenTelemetry {
    private static _instance: OpenTelemetry | null = null;
    private static openTelemetryConfig: OpenTelemetryConfig | null = null;

    /**
     * Singleton class for managing OpenTelemetry configuration and operations.
     * This class provides a centralized way to initialize OpenTelemetry and control
     * sampling behavior at runtime.
     *
     * Example usage:
     * ```typescript
     * import { OpenTelemetry, OpenTelemetryConfig, OpenTelemetryTracesConfig, OpenTelemetryMetricsConfig } from "@valkey/valkey-glide";
     *
     * let tracesConfig: OpenTelemetryTracesConfig = {
     *  endpoint: "http://localhost:4318/v1/traces",
     *  samplePercentage: 10, // Optional, defaults to 1. Can also be changed at runtime via setSamplePercentage().
     * };
     * let metricsConfig: OpenTelemetryMetricsConfig = {
     *  endpoint: "http://localhost:4318/v1/metrics",
     * };

     * let config : OpenTelemetryConfig = { 
     *  traces: tracesConfig,
     *  metrics: metricsConfig,
     *  flushIntervalMs: 1000, // Optional, defaults to 5000
     * };
     * OpenTelemetry.init(config);
     * 
     * ```
     *
     * @remarks
     *   OpenTelemetry can only be initialized once per process. Subsequent calls to
     *   init() will be ignored. This is by design, as OpenTelemetry is a global
     *   resource that should be configured once at application startup.
     *
     * Initialize the OpenTelemetry instance
     * @param openTelemetryConfig - The OpenTelemetry configuration
     */
    public static init(openTelemetryConfig: OpenTelemetryConfig) {
        if (!this._instance) {
            this.internalInit(openTelemetryConfig);
            Logger.log(
                "info",
                "GlideOpenTelemetry",
                "OpenTelemetry initialized with config: " +
                    JSON.stringify(openTelemetryConfig),
            );
            return;
        }

        Logger.log(
            "warn",
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized - ignoring new configuration",
        );
    }

    private static internalInit(openTelemetryConfig: OpenTelemetryConfig) {
        this.openTelemetryConfig = openTelemetryConfig;
        InitOpenTelemetry(openTelemetryConfig);
        this._instance = new OpenTelemetry();
    }

    /**
     * Check if the OpenTelemetry instance is initialized
     * @returns True if the OpenTelemetry instance is initialized, false otherwise
     */
    public static isInitialized() {
        return this._instance != null;
    }

    /**
     * Get the sample percentage for traces
     * @returns The sample percentage for traces only if OpenTelemetry is initialized and the traces config is set, otherwise undefined.
     */
    public static getSamplePercentage() {
        return this.openTelemetryConfig?.traces?.samplePercentage;
    }

    /**
     * Determines if the current request should be sampled for OpenTelemetry tracing.
     * Uses the configured sample percentage to randomly decide whether to create a span for this request.
     * @returns true if the request should be sampled, false otherwise
     */
    public static shouldSample(): boolean {
        const percentage = this.getSamplePercentage();
        return (
            this.isInitialized() &&
            percentage !== undefined &&
            Math.random() * 100 < percentage
        );
    }

    /**
     * Set the percentage of requests to be sampled and traced. Must be a value between 0 and 100.
     * This setting only affects traces, not metrics.
     * @param percentage - The sample percentage 0-100
     * @throws Error if OpenTelemetry is not initialized or traces config is not set
     * @remarks
     * This method can be called at runtime to change the sampling percentage without reinitializing OpenTelemetry.
     */
    public static setSamplePercentage(percentage: number) {
        if (!this.openTelemetryConfig || !this.openTelemetryConfig.traces) {
            throw new ConfigurationError(
                "OpenTelemetry config traces not initialized",
            );
        }

        if (percentage < 0 || percentage > 100) {
            throw new ConfigurationError(
                "Sample percentage must be between 0 and 100",
            );
        }

        this.openTelemetryConfig.traces.samplePercentage = percentage;
    }
}
