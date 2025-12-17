/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.OpenTelemetry;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30) // seconds
public class OpenTelemetryConfigTests {
    private static String VALID_ENDPOINT_TRACES;
    private static String VALID_FILE_ENDPOINT_TRACES;

    @BeforeAll
    @SneakyThrows
    static void setup() {
        // Use Java's system temporary directory API (cross-platform)
        Path tempDir = Files.createTempDirectory("otel-test");
        VALID_ENDPOINT_TRACES = tempDir.resolve("spans.json").toString();
        VALID_FILE_ENDPOINT_TRACES = "file://" + VALID_ENDPOINT_TRACES;
    }

    // Test wrong open telemetry configs
    @Test
    @SneakyThrows
    public void wrongOpenTelemetryConfig() {
        Exception exception;
        // Wrong traces endpoint
        OpenTelemetry.OpenTelemetryConfig wrongTracesConfig =
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .traces(OpenTelemetry.TracesConfig.builder().endpoint("wrong.endpoint").build())
                        .build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(wrongTracesConfig));
        assertTrue(exception.getMessage().contains("Parse error"));

        // Wrong metrics endpoint
        OpenTelemetry.OpenTelemetryConfig wrongMetricsConfig =
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .metrics(OpenTelemetry.MetricsConfig.builder().endpoint("wrong.endpoint").build())
                        .build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(wrongMetricsConfig));
        assertTrue(exception.getMessage().contains("Parse error"));

        // Negative flush interval
        OpenTelemetry.OpenTelemetryConfig negativeFlushConfig =
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint(VALID_FILE_ENDPOINT_TRACES)
                                        .samplePercentage(1)
                                        .build())
                        .flushIntervalMs(-400L)
                        .build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(negativeFlushConfig));
        assertTrue(exception.getMessage().contains("flushIntervalMs must be a positive integer"));

        // Negative sample percentage
        OpenTelemetry.OpenTelemetryConfig negativeSampleConfig =
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint(VALID_FILE_ENDPOINT_TRACES)
                                        .samplePercentage(-400)
                                        .build())
                        .build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(negativeSampleConfig));
        assertTrue(
                exception
                        .getMessage()
                        .contains(
                                "InvalidInput: traces_sample_percentage must be a positive integer (got: -400)"));

        // Wrong traces file path
        OpenTelemetry.OpenTelemetryConfig wrongTracesPathConfig =
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint("file:invalid-path/v1/traces.json")
                                        .build())
                        .build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(wrongTracesPathConfig));
        assertTrue(exception.getMessage().contains("File path must start with 'file://'"));

        // Wrong metrics file path
        OpenTelemetry.OpenTelemetryConfig wrongMetricsPathConfig =
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .metrics(
                                OpenTelemetry.MetricsConfig.builder()
                                        .endpoint("file:invalid-path/v1/metrics.json")
                                        .build())
                        .build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(wrongMetricsPathConfig));
        assertTrue(exception.getMessage().contains("File path must start with 'file://'"));

        // Wrong directory path
        OpenTelemetry.OpenTelemetryConfig wrongDirPathConfig =
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint("file:///no-exits-path/v1/traces.json")
                                        .build())
                        .build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(wrongDirPathConfig));
        assertTrue(
                exception.getMessage().contains("The directory does not exist or is not a directory"));

        // No traces or metrics provided
        OpenTelemetry.OpenTelemetryConfig emptyConfig =
                OpenTelemetry.OpenTelemetryConfig.builder().build();

        exception = assertThrows(Exception.class, () -> OpenTelemetry.init(emptyConfig));
        assertTrue(
                exception.getMessage().contains("At least one of traces or metrics must be provided"));
    }
}
