/**
 * Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0
 */

import { InitOpenTelemetry, OpenTelemetryConfig } from "glide-rs";

export class OpenTelemetry {
    private static _instance: OpenTelemetry | null = null;
    private static openTelemetryConfig: OpenTelemetryConfig | null = null;

    private constructor() {}

    /**
     * Initialize the OpenTelemetry instance
     * @param openTelemetryConfig - The OpenTelemetry configuration
     */
    public static init(openTelemetryConfig: OpenTelemetryConfig) {
        if (!this._instance) {
            this.openTelemetryConfig = openTelemetryConfig;
            InitOpenTelemetry(
                openTelemetryConfig
            );
            this._instance = new OpenTelemetry();
        }
    }

    /**
     * Update the OpenTelemetry configuration. 
     * if the traces configs setting to null or undefined and they already exits, the config will remain the same and will not change, if the attempt to disable the traces please call to disableTraces method.
     * @param openTelemetryConfig - The OpenTelemetry configuration
     */
    public static updateOpenTelemetryConfig(openTelemetryConfig: OpenTelemetryConfig) {
        this.init(openTelemetryConfig);
    }

    /**
     * Disable the traces
     */
    public static disableTraces() {
        if (this.openTelemetryConfig) {
            this.openTelemetryConfig.traces = undefined;
        }
    }

    /**
     * Disable the metrics
     */
    public static disableMetrics() {
        if (this.openTelemetryConfig) {
            this.openTelemetryConfig.metrics = undefined;
        }
    }
}
