/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * Configuration options for OpenTelemetry.
 *
 * <p>This class provides configuration options for OpenTelemetry integration, including:
 *
 * <ul>
 *   <li>Traces collector endpoint: The endpoint to send trace data to
 *   <li>Metrics collector endpoint: The endpoint to send metrics data to
 *   <li>Flush interval: How often to flush data to the collectors (in milliseconds)
 * </ul>
 *
 * <p>The collector endpoints support multiple protocols:
 *
 * <ul>
 *   <li>HTTP: Use {@code http://} prefix (e.g., {@code http://localhost:4318})
 *   <li>HTTPS: Use {@code https://} prefix (e.g., {@code https://collector.example.com:4318})
 *   <li>gRPC: Use {@code grpc://} prefix (e.g., {@code grpc://localhost:4317})
 *   <li>File: Use {@code file://} prefix followed by the full path (e.g., {@code file:///path/to/})
 *       to write the signals data to a file.
 * </ul>
 */
@Getter
@SuperBuilder
public class OpenTelemetryConfig {
    /** The endpoint to send trace data to. */
    private final String tracesCollectorEndPoint;

    /** The endpoint to send metrics data to. */
    private final String metricsCollectorEndPoint;

    /**
     * How often to flush data to the collectors (in milliseconds). If not specified, defaults to
     * 5000ms (5 seconds).
     */
    private final Integer flushIntervalMs;
}
