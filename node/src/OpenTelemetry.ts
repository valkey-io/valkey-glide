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
 * Extended OpenTelemetry configuration that includes JS-side options
 * not representable in the native (NAPI) config.
 *
 * Pass this to {@link OpenTelemetry.init} instead of the plain `OpenTelemetryConfig`.
 */
export interface GlideOpenTelemetryConfig extends OpenTelemetryConfig {
    /**
     * Optional callback that returns the active parent span context for each command.
     *
     * When a {@link GlideSpanContext} is returned, the GLIDE command span will be created
     * as a child of that context, enabling end-to-end distributed tracing.
     *
     * The callback is invoked synchronously before each sampled command. Keep the
     * implementation lightweight — avoid I/O, async work, or expensive computation.
     *
     * @example
     * ```typescript
     * import { trace } from "@opentelemetry/api";
     *
     * OpenTelemetry.init({
     *     traces: { endpoint: "http://localhost:4318/v1/traces" },
     *     parentSpanContextProvider: () => {
     *         const span = trace.getActiveSpan();
     *         if (!span) return undefined;
     *         const ctx = span.spanContext();
     *         return {
     *             traceId: ctx.traceId,
     *             spanId: ctx.spanId,
     *             traceFlags: ctx.traceFlags,
     *             traceState: ctx.traceState?.toString(),
     *         };
     *     },
     * });
     * ```
     */
    parentSpanContextProvider?: () => GlideSpanContext | undefined;
}

/**
 * Represents the trace context of a remote span, used for parent span context propagation.
 *
 * When a user's application has an active OTel span (e.g., from an HTTP request handler),
 * this context allows GLIDE command spans to appear as children of that span in tracing UIs.
 */
export interface GlideSpanContext {
    /** The trace ID as a 32-character lowercase hex string. */
    traceId: string;
    /** The span ID as a 16-character lowercase hex string. */
    spanId: string;
    /** Trace flags (e.g., 1 for sampled). */
    traceFlags: number;
    /** Optional W3C trace state (e.g., "vendorname1=opaqueValue1,vendorname2=opaqueValue2"). */
    traceState?: string;
}

/**
 * ⚠️ OpenTelemetry can only be initialized once per process. Calling `OpenTelemetry.init()` more than once will be ignored.
 * If you need to change configuration, restart the process with new settings.
 * ### OpenTelemetry
 *
 * - **openTelemetryConfig**: Use {@link GlideOpenTelemetryConfig} to configure OpenTelemetry exporters and options.
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
 *   - **parentSpanContextProvider**: (optional) Callback returning the active parent span context. See {@link GlideOpenTelemetryConfig.parentSpanContextProvider}.
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
    private static spanContextFn: (() => GlideSpanContext | undefined) | null =
        null;
    private static readonly TRACE_ID_REGEX = /^[0-9a-f]{32}$/;
    private static readonly SPAN_ID_REGEX = /^[0-9a-f]{16}$/;

    /**
     * Singleton class for managing OpenTelemetry configuration and operations.
     * This class provides a centralized way to initialize OpenTelemetry and control
     * sampling behavior at runtime.
     *
     * Example usage:
     * ```typescript
     * import { OpenTelemetry, GlideOpenTelemetryConfig } from "@valkey/valkey-glide";
     * import { trace } from "@opentelemetry/api";
     *
     * const config: GlideOpenTelemetryConfig = {
     *     traces: {
     *         endpoint: "http://localhost:4318/v1/traces",
     *         samplePercentage: 10,
     *     },
     *     metrics: {
     *         endpoint: "http://localhost:4318/v1/metrics",
     *     },
     *     flushIntervalMs: 1000,
     *     parentSpanContextProvider: () => {
     *         const span = trace.getActiveSpan();
     *         if (!span) return undefined;
     *         const ctx = span.spanContext();
     *         return { traceId: ctx.traceId, spanId: ctx.spanId, traceFlags: ctx.traceFlags };
     *     },
     * };
     * OpenTelemetry.init(config);
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
    public static init(openTelemetryConfig: GlideOpenTelemetryConfig) {
        if (!this._instance) {
            const { parentSpanContextProvider, ...nativeConfig } =
                openTelemetryConfig;
            this.internalInit(nativeConfig, parentSpanContextProvider);
            Logger.log(
                "info",
                "GlideOpenTelemetry",
                "OpenTelemetry initialized with config: " +
                    JSON.stringify(nativeConfig) +
                    (parentSpanContextProvider
                        ? " (parentSpanContextProvider: set)"
                        : ""),
            );
            return;
        }

        Logger.log(
            "warn",
            "GlideOpenTelemetry",
            "OpenTelemetry already initialized - ignoring new configuration",
        );
    }

    private static internalInit(
        nativeConfig: OpenTelemetryConfig,
        parentSpanContextProvider?: () => GlideSpanContext | undefined,
    ) {
        this.openTelemetryConfig = nativeConfig;

        if (parentSpanContextProvider) {
            this.spanContextFn = parentSpanContextProvider;
        }

        InitOpenTelemetry(nativeConfig);
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

    /**
     * Register or replace the callback that returns the active parent span context.
     *
     * This allows changing the provider at runtime (e.g., switching tracing contexts
     * in a multi-tenant application). The initial provider can also be set via
     * {@link GlideOpenTelemetryConfig.parentSpanContextProvider} in `init()`.
     *
     * @param fn - A function returning a `GlideSpanContext` or `undefined`, or `null` to clear.
     *
     * @example
     * ```typescript
     * import { trace } from "@opentelemetry/api";
     *
     * OpenTelemetry.setParentSpanContextProvider(() => {
     *     const span = trace.getActiveSpan();
     *     if (!span) return undefined;
     *     const ctx = span.spanContext();
     *     return {
     *         traceId: ctx.traceId,
     *         spanId: ctx.spanId,
     *         traceFlags: ctx.traceFlags,
     *         traceState: ctx.traceState?.toString(),
     *     };
     * });
     * ```
     */
    public static setParentSpanContextProvider(
        fn: (() => GlideSpanContext | undefined) | null,
    ) {
        this.spanContextFn = fn;
    }

    /**
     * Retrieve the current parent span context by invoking the registered callback.
     *
     * @returns The `GlideSpanContext` from the registered callback, or `undefined` if no callback
     *   is set or the callback returns `undefined`.
     * @internal
     */
    public static getParentSpanContext(): GlideSpanContext | undefined {
        let ctx: GlideSpanContext | undefined;

        try {
            ctx = this.spanContextFn?.();
        } catch (e) {
            Logger.log(
                "warn",
                "GlideOpenTelemetry",
                `parentSpanContextProvider threw: ${e}. Falling back to standalone span.`,
            );
            return undefined;
        }

        if (ctx === undefined) {
            return undefined;
        }

        if (!this.TRACE_ID_REGEX.test(ctx.traceId)) {
            Logger.log(
                "warn",
                "GlideOpenTelemetry",
                `Invalid traceId "${ctx.traceId}" — expected 32 lowercase hex chars. Falling back to standalone span.`,
            );
            return undefined;
        }

        if (!this.SPAN_ID_REGEX.test(ctx.spanId)) {
            Logger.log(
                "warn",
                "GlideOpenTelemetry",
                `Invalid spanId "${ctx.spanId}" — expected 16 lowercase hex chars. Falling back to standalone span.`,
            );
            return undefined;
        }

        if (
            !Number.isInteger(ctx.traceFlags) ||
            ctx.traceFlags < 0 ||
            ctx.traceFlags > 255
        ) {
            Logger.log(
                "warn",
                "GlideOpenTelemetry",
                `Invalid traceFlags "${ctx.traceFlags}" — expected integer 0-255. Falling back to standalone span.`,
            );
            return undefined;
        }

        if (
            ctx.traceState !== undefined &&
            !OpenTelemetry.validTraceState(ctx.traceState)
        ) {
            Logger.log(
                "warn",
                "GlideOpenTelemetry",
                `Invalid traceState "${ctx.traceState}" — expected W3C tracestate format. Falling back to standalone span.`,
            );
            return undefined;
        }

        return ctx;
    }

    /**
     * Validate a W3C tracestate key.
     * See https://www.w3.org/TR/trace-context/#key
     * @internal
     */
    private static validTraceStateKey(key: string): boolean {
        if (key.length === 0 || key.length > 256) return false;

        const ALLOWED_SPECIAL = new Set([
            "_".charCodeAt(0),
            "-".charCodeAt(0),
            "*".charCodeAt(0),
            "/".charCodeAt(0),
        ]);

        let vendorStart: number | null = null;

        for (let i = 0; i < key.length; i++) {
            const c = key.charCodeAt(i);
            const isLower = c >= 0x61 && c <= 0x7a; // a-z
            const isDigit = c >= 0x30 && c <= 0x39; // 0-9
            const isAt = c === 0x40; // @

            if (!(isLower || isDigit || ALLOWED_SPECIAL.has(c) || isAt)) {
                return false;
            }

            if (i === 0 && !isLower && !isDigit) return false;

            if (isAt) {
                if (vendorStart !== null || i + 14 < key.length) return false;
                vendorStart = i;
            } else if (vendorStart !== null && i === vendorStart + 1) {
                if (!isLower && !isDigit) return false;
            }
        }

        return true;
    }

    /**
     * Validate a W3C tracestate value.
     * See https://www.w3.org/TR/trace-context/#value
     * @internal
     */
    private static validTraceStateValue(value: string): boolean {
        if (value.length > 256) return false;

        return !value.includes(",") && !value.includes("=");
    }

    /**
     * Validate a W3C tracestate string (comma-separated key=value pairs).
     * Mirrors the validation in opentelemetry-rust TraceState::from_str.
     * @internal
     */
    private static validTraceState(traceState: string): boolean {
        if (traceState === "") return true;

        const entries = traceState.split(",");

        for (const entry of entries) {
            const eqIndex = entry.indexOf("=");

            if (eqIndex === -1) return false;

            const key = entry.substring(0, eqIndex);
            const value = entry.substring(eqIndex + 1);

            if (
                !OpenTelemetry.validTraceStateKey(key) ||
                !OpenTelemetry.validTraceStateValue(value)
            ) {
                return false;
            }
        }

        return true;
    }
}
