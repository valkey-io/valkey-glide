/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import glide.api.GlideClient;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Represents advanced configuration settings for a Standalone {@link GlideClient} used in {@link
 * GlideClientConfiguration}.
 *
 * @example
 *     <pre>{@code
 * // Basic configuration with connection timeout
 * AdvancedGlideClientConfiguration config = AdvancedGlideClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .build();
 *
 * // Configuration with OpenTelemetry enabled
 * AdvancedGlideClientConfiguration configWithOtel = AdvancedGlideClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .openTelemetryConfig(OpenTelemetryConfig.builder()
 *         .tracesCollectorEndPoint("https://collector.example.com:4318/v1/traces")
 *         .metricsCollectorEndPoint("https://collector.example.com:4318/v1/metrics")
 *         .flushIntervalMs(5000)
 *         .build())
 *     .build();
 *
 * // Configuration with OpenTelemetry using file output
 * AdvancedGlideClientConfiguration configWithFileOtel = AdvancedGlideClientConfiguration.builder()
 *     .connectionTimeout(500)
 *     .openTelemetryConfig(OpenTelemetryConfig.builder()
 *         .tracesCollectorEndPoint("file:///tmp/traces/")
 *         .metricsCollectorEndPoint("file:///tmp/metrics/")
 *         .flushIntervalMs(1000)
 *         .build())
 *     .build();
 * }</pre>
 */
@Getter
@SuperBuilder
@ToString
public class AdvancedGlideClientConfiguration extends AdvancedBaseClientConfiguration {}
