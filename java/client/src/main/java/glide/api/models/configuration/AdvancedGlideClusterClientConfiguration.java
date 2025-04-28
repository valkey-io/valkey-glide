/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClusterClient;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Represents advanced configuration settings for a Standalone {@link GlideClusterClient} used in
 * {@link GlideClusterClientConfiguration}.
 *
 * @example
 *     <pre>{@code
 * // Basic configuration with connection timeout
 * AdvancedGlideClusterClientConfiguration config = AdvancedGlideClusterClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .build();
 *
 * // Configuration with OpenTelemetry enabled
 * AdvancedGlideClusterClientConfiguration configWithOtel = AdvancedGlideClusterClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .openTelemetryConfig(OpenTelemetryConfig.builder()
 *         .tracesCollectorEndPoint("https://collector.example.com:4318/v1/traces")
 *         .metricsCollectorEndPoint("https://collector.example.com:4318/v1/metrics")
 *         .flushIntervalMs(5000)
 *         .build())
 *     .build();
 *
 * // Configuration with OpenTelemetry using gRPC
 * AdvancedGlideClusterClientConfiguration configWithGrpcOtel = AdvancedGlideClusterClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .openTelemetryConfig(OpenTelemetryConfig.builder()
 *         .tracesCollectorEndPoint("grpc://localhost:4317")
 *         .metricsCollectorEndPoint("grpc://localhost:4317")
 *         .flushIntervalMs(2000)
 *         .build())
 *     .build();
 * }</pre>
 */
@Getter
@SuperBuilder
@ToString
public class AdvancedGlideClusterClientConfiguration extends AdvancedBaseClientConfiguration {}
