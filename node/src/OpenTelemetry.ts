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

import { InitOpenTelemetry, OpenTelemetryConfig } from "glide-rs";
import { Logger } from "./Logger";

export class OpenTelemetry {
    private static _instance: OpenTelemetry | null = null;
    private static openTelemetryConfig: OpenTelemetryConfig | null = null;

    /**
     * Example usage:
     * ```typescript
     * import { OpenTelemetry } from "@valkey/glide";
     *
     * OpenTelemetry.init({
     *   traces: {
     *     endpoint: "http://localhost:4318/v1/traces",
     *     samplePercentage: 10, // Optional, defaults to 1
     *   },
     *   metrics: {
     *     endpoint: "http://localhost:4318/v1/metrics",
     *   },
     *   flushIntervalMs: 5000, // Optional, defaults to 5000
     * });
     * ```
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
            "OpenTelemetry already initialized",
        );
    }

    private static internalInit(openTelemetryConfig: OpenTelemetryConfig) {
        this.openTelemetryConfig = openTelemetryConfig;
        InitOpenTelemetry(openTelemetryConfig);
        this._instance = new OpenTelemetry();
    }
}
