/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.*;

import glide.api.GlideClusterClient;
import glide.api.OpenTelemetry;
import glide.api.OpenTelemetry.OpenTelemetryConfig;
import glide.api.models.ClusterBatch;
import glide.api.models.configuration.ProtocolVersion;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(30) // seconds
public class OpenTelemetryTests {

    private static final String VALID_ENDPOINT_TRACES =
            System.getProperty("java.io.tmpdir") + System.getProperty("file.separator") + "spans.json";
    private static final String VALID_FILE_ENDPOINT_TRACES = "file://" + VALID_ENDPOINT_TRACES;
    private static final String VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics";
    private static GlideClusterClient client;
    private static final int DELAY_500 = 500;
    private static final int DELAY_5000 = 5000;
    private static final int DELAY_1000 = 1000;

    /**
     * Wait for spans to be exported with retry logic.
     *
     * @param spanFilePath Path to the span file
     * @param expectedSpanCount Expected number of spans
     * @param spanName Name of the span to look for
     * @param maxWaitMs Maximum time to wait in milliseconds
     * @return SpanFileData if successful
     * @throws Exception if timeout or other error
     */
    private static SpanFileData waitForSpansWithRetry(
            String spanFilePath, int expectedSpanCount, String spanName, long maxWaitMs)
            throws Exception {

        long startTime = System.currentTimeMillis();
        long pollIntervalMs = 500; // Check every 500ms
        File spanFile = new File(spanFilePath);

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (!spanFile.exists() || spanFile.length() == 0) {
                Thread.sleep(pollIntervalMs);
                continue;
            }

            try {
                SpanFileData spanData = readAndParseSpanFile(spanFilePath);
                long matchingSpans =
                        spanData.spanNames.stream().filter(name -> name.equals(spanName)).count();

                if (matchingSpans >= expectedSpanCount) {
                    return spanData;
                }

                Thread.sleep(pollIntervalMs);
            } catch (Exception e) {
                // File might be partially written, wait and retry
                Thread.sleep(pollIntervalMs);
            }
        }

        // Timeout - try to provide diagnostic info
        String diagnosticInfo =
                "Timeout waiting for "
                        + expectedSpanCount
                        + " '"
                        + spanName
                        + "' spans after "
                        + maxWaitMs
                        + "ms";
        if (spanFile.exists() && spanFile.length() > 0) {
            try {
                SpanFileData spanData = readAndParseSpanFile(spanFilePath);
                long found = spanData.spanNames.stream().filter(n -> n.equals(spanName)).count();
                diagnosticInfo += ". Found " + found + " spans. All spans: " + spanData.spanNames;
            } catch (Exception ignored) {
            }
        }
        throw new Exception(diagnosticInfo);
    }

    /**
     * Reads and parses a span file, extracting span data and names.
     *
     * @param path - The path to the span file
     * @return An object containing the raw span data, array of spans, and array of span names
     * @throws Exception if the file cannot be read or parsed
     */
    private static SpanFileData readAndParseSpanFile(String path) throws Exception {
        String spanData = "";
        List<String> spans = new ArrayList<>();
        List<String> spanNames = new ArrayList<>();
        spanData = new String(Files.readAllBytes(Paths.get(path)));

        spans = spanData.lines().filter(line -> !line.trim().isEmpty()).collect(Collectors.toList());

        // Check that we have spans
        if (spans.isEmpty()) {
            throw new Exception("No spans found in the span file");
        }

        // Parse and extract span names
        spanNames =
                spans.stream()
                        .map(
                                line -> {
                                    try {
                                        // Simple extraction of the "name" field from JSON
                                        int nameIndex = line.indexOf("\"name\":");
                                        if (nameIndex != -1) {
                                            int startQuote = line.indexOf("\"", nameIndex + 7);
                                            int endQuote = line.indexOf("\"", startQuote + 1);
                                            if (startQuote != -1 && endQuote != -1) {
                                                return line.substring(startQuote + 1, endQuote);
                                            }
                                        }
                                        return null;
                                    } catch (Exception e) {
                                        return null;
                                    }
                                })
                        .filter(name -> name != null)
                        .collect(Collectors.toList());

        return new SpanFileData(spanData, spans, spanNames);
    }

    private static class SpanFileData {
        final String spanData;
        final List<String> spans;
        final List<String> spanNames;

        SpanFileData(String spanData, List<String> spans, List<String> spanNames) {
            this.spanData = spanData;
            this.spans = spans;
            this.spanNames = spanNames;
        }
    }

    public static Stream<Arguments> getClientsProtocolVersion() {
        return Stream.of(Arguments.of(ProtocolVersion.RESP2), Arguments.of(ProtocolVersion.RESP3));
    }

    @BeforeAll
    @SneakyThrows
    public static void setup() {
        // Initialize OpenTelemetry with valid configuration
        OpenTelemetryConfig openTelemetryConfig =
                OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint(VALID_FILE_ENDPOINT_TRACES)
                                        .samplePercentage(100)
                                        .build())
                        .metrics(OpenTelemetry.MetricsConfig.builder().endpoint(VALID_ENDPOINT_METRICS).build())
                        .flushIntervalMs(100L)
                        .build();

        OpenTelemetry.init(openTelemetryConfig);
    }

    // Test that spans are not exported before initializing OpenTelemetry
    @Test
    @SneakyThrows
    public void testSpanNotExportedBeforeInitOtel() {
        // Clean up any existing span file
        teardownOtelTest();

        // Create a client and execute a command
        GlideClusterClient tempClient =
                GlideClusterClient.createClient(commonClusterClientConfig().requestTimeout(10000).build())
                        .get();

        tempClient.get("testSpanNotExportedBeforeInitOtel").get();

        // Check that spans were not exported to the file before initializing OpenTelemetry
        assertFalse(new File(VALID_ENDPOINT_TRACES).exists());

        tempClient.close();
    }

    @AfterEach
    @SneakyThrows
    public void tearDown() {
        teardownOtelTest();
        if (client != null) {
            client.close();
            client = null;
        }
    }

    @AfterAll
    @SneakyThrows
    public static void cleanUp() {
        teardownOtelTest();
    }

    private static void teardownOtelTest() {
        // Clean up OpenTelemetry files
        File tracesFile = new File(VALID_ENDPOINT_TRACES);
        if (tracesFile.exists()) {
            tracesFile.delete();
        }

        File metricsFile = new File(VALID_ENDPOINT_METRICS);
        if (metricsFile.exists()) {
            metricsFile.delete();
        }
    }

    @ParameterizedTest
    @MethodSource("getClientsProtocolVersion")
    @SneakyThrows
    public void testSpanMemoryLeak(ProtocolVersion protocol) {
        // Force garbage collection if available
        System.gc();

        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        client =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();

        // Execute a series of commands sequentially
        for (int i = 0; i < 100; i++) {
            String key = "test_key_" + i;
            client.set(key, "value_" + i).get();
            client.get(key).get();
        }

        // Force GC and check memory
        System.gc();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Allow 10% growth
        assertTrue(
                endMemory < startMemory * 1.1,
                "Memory usage increased too much: " + startMemory + " -> " + endMemory);
    }

    @ParameterizedTest
    @MethodSource("getClientsProtocolVersion")
    @SneakyThrows
    public void testPercentageRequestsConfig(ProtocolVersion protocol) {
        client =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();

        // Set sample percentage to 0%
        OpenTelemetry.setSamplePercentage(0);
        assertEquals(0, OpenTelemetry.getSamplePercentage());

        // Wait for spans to be flushed and remove the file
        Thread.sleep(DELAY_500);
        teardownOtelTest();

        // Execute commands with 0% sampling
        for (int i = 0; i < 100; i++) {
            client.set("testPercentageRequestsConfig", "value").get();
        }

        Thread.sleep(DELAY_500);
        // Check that no spans were exported due to 0% sampling
        assertFalse(new File(VALID_ENDPOINT_TRACES).exists());
    }

    @ParameterizedTest
    @MethodSource("getClientsProtocolVersion")
    @SneakyThrows
    public void testPercentageRequestsConfig100Percent(ProtocolVersion protocol) {
        client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().requestTimeout(20000).protocol(protocol).build())
                        .get();

        // Set sample percentage to 100%
        OpenTelemetry.setSamplePercentage(100);
        assertEquals(100, OpenTelemetry.getSamplePercentage());

        // Test that setting negative value throws an exception
        assertThrows(Exception.class, () -> OpenTelemetry.setSamplePercentage(-100));

        // Execute a series of commands
        for (int i = 0; i < 10; i++) {
            String key = "testPercentageRequestsConfig_" + i;
            client.get(key).get();
        }

        // Wait for spans with retry logic (up to 10 seconds)
        SpanFileData spanData =
                waitForSpansWithRetry(
                        VALID_ENDPOINT_TRACES,
                        10, // Expected 10 "Get" spans
                        "Get", // Span name to look for
                        10000 // Max wait 10 seconds
                        );

        assertTrue(spanData.spanNames.contains("Get"));
        // Check that spans were exported exactly 10 times
        assertEquals(10, spanData.spanNames.stream().filter(name -> name.equals("Get")).count());
    }

    @ParameterizedTest
    @MethodSource("getClientsProtocolVersion")
    @SneakyThrows
    public void testOtelGlobalConfigNotReinitialize(ProtocolVersion protocol) {
        // Try to reinitialize with invalid config
        OpenTelemetryConfig openTelemetryConfig =
                OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint("wrong.endpoint")
                                        .samplePercentage(1)
                                        .build())
                        .build();

        // This should not throw an error because init can only be called once per process
        OpenTelemetry.init(openTelemetryConfig);

        client =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();

        client.set("testOtelGlobalConfig", "value").get();

        Thread.sleep(DELAY_500);

        // Read the span file and check span name
        SpanFileData spanData = readAndParseSpanFile(VALID_ENDPOINT_TRACES);

        assertTrue(spanData.spanNames.contains("Set"));
    }

    @ParameterizedTest
    @MethodSource("getClientsProtocolVersion")
    @SneakyThrows
    public void testSpanTransactionMemoryLeak(ProtocolVersion protocol) {
        // Force garbage collection if available
        System.gc();

        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        client =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();

        ClusterBatch batch = new ClusterBatch(true);

        batch.set("test_key", "foo");
        batch.objectRefcount("test_key");

        Object[] response = client.exec(batch, true).get();
        assertNotNull(response);
        assertEquals(2, response.length);
        assertEquals("OK", response[0]);
        assertTrue((Long) response[1] >= 1);

        // Force GC and check memory
        System.gc();

        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Allow 10% growth
        assertTrue(
                endMemory < startMemory * 1.1,
                "Memory usage increased too much: " + startMemory + " -> " + endMemory);
    }

    @ParameterizedTest
    @MethodSource("getClientsProtocolVersion")
    @SneakyThrows
    public void testNumberOfClientsWithSameConfig(ProtocolVersion protocol) {
        GlideClusterClient client1 =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();

        GlideClusterClient client2 =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();

        client1.set("test_key", "value").get();
        client2.get("test_key").get();

        // Wait for spans to be flushed to file
        Thread.sleep(DELAY_5000);

        // Read and check span names from the file
        SpanFileData spanData = readAndParseSpanFile(VALID_ENDPOINT_TRACES);

        // Check for expected span names
        assertTrue(spanData.spanNames.contains("Get"));
        assertTrue(spanData.spanNames.contains("Set"));

        client1.close();
        client2.close();
    }

    @ParameterizedTest
    @MethodSource("getClientsProtocolVersion")
    @SneakyThrows
    public void testSpanBatchFile(ProtocolVersion protocol) {
        // Force garbage collection if available
        System.gc();

        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        client =
                GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get();

        ClusterBatch batch = new ClusterBatch(true);

        batch.set("test_key", "foo");
        batch.objectRefcount("test_key");
        Object[] response = client.exec(batch, true).get();
        Thread.sleep(DELAY_1000);
        assertNotNull(response);
        assertEquals(2, response.length);
        assertEquals("OK", response[0]);
        assertTrue((Long) response[1] >= 1);

        // Wait for spans to be flushed to file
        Thread.sleep(DELAY_5000);

        // Read and check span names from the file
        SpanFileData spanData = readAndParseSpanFile(VALID_ENDPOINT_TRACES);

        // Check for expected span names
        assertTrue(spanData.spanNames.contains("Batch"));
        assertTrue(spanData.spanNames.contains("send_batch"));

        // Force GC and check memory
        System.gc();

        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Allow 10% growth
        assertTrue(
                endMemory < startMemory * 1.1,
                "Memory usage increased too much: " + startMemory + " -> " + endMemory);
    }

    @Test
    @SneakyThrows
    public void testAutomaticSpanLifecycle() {
        // Force garbage collection if available
        System.gc();

        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().protocol(ProtocolVersion.RESP3).build())
                        .get();

        // Execute multiple commands - each should automatically create and clean up its span
        client.set("test_key1", "value1").get();
        client.get("test_key1").get();
        client.set("test_key2", "value2").get();
        client.get("test_key2").get();

        // Force GC again to clean up
        System.gc();

        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Allow small fluctuations
        assertTrue(
                endMemory < startMemory * 1.1,
                "Memory usage increased too much: " + startMemory + " -> " + endMemory);
    }

    @Test
    @SneakyThrows
    public void testConcurrentCommandsSpanLifecycle() {
        // Force garbage collection if available
        System.gc();

        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        client =
                GlideClusterClient.createClient(
                                commonClusterClientConfig().protocol(ProtocolVersion.RESP3).build())
                        .get();

        // Execute multiple concurrent commands
        List<java.util.concurrent.CompletableFuture<?>> commands =
                List.of(
                        client.set("test_key1", "value1"),
                        client.get("test_key1"),
                        client.set("test_key2", "value2"),
                        client.get("test_key2"),
                        client.set("test_key3", "value3"),
                        client.get("test_key3"));

        // Wait for all commands to complete
        for (java.util.concurrent.CompletableFuture<?> command : commands) {
            command.get();
        }

        // Force GC again to clean up
        System.gc();

        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        // Allow small fluctuations
        assertTrue(
                endMemory < startMemory * 1.1,
                "Memory usage increased too much: " + startMemory + " -> " + endMemory);
    }
}
