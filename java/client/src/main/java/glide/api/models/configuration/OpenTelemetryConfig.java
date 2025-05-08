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
 *   <li>File: Use {@code file://} prefix followed by a full path to export the signals to a local file.
 *       <ul>
 *         <li>The {@code file://} endpoint supports both directory paths and explicit file paths:
 *           <ul>
 *             <li>If the path is a directory or lacks a file extension (e.g., {@code file:///tmp/otel}), 
 *                 it will default to writing to a file named spans.json in that directory 
 *                 (e.g., {@code /tmp/otel/spans.json}).
 *             <li>If the path includes a filename with an extension (e.g., {@code file:///tmp/otel/traces.json}), 
 *                 the specified file will be used as-is.
 *             <li>The parent directory must already exist. If it does not, the client will fail to initialize 
 *                 with an InvalidInput error.
 *             <li>If the target file already exists, new data will be appended to it (the file will not be overwritten).
 *           </ul>
 *       </ul>
 * </ul>
 */
@Getter
@SuperBuilder
public class OpenTelemetryConfig {
     /** The client collector address to export the traces measurements. */
    private final String tracesCollectorEndPoint;

    /** The client collector address to export the metrics. */
    private final String metricsCollectorEndPoint;

    /**
     * The duration in milliseconds the data will exported to the collector.  
     * If not specified, 5000 will be used.
     */
    private final Integer flushIntervalMs;
}
