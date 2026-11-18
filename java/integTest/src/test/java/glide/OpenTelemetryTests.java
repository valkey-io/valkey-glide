/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.OpenTelemetry;
import glide.api.OpenTelemetry.GlideSpanContext;
import glide.api.OpenTelemetry.OpenTelemetryConfig;
import glide.api.models.Batch;
import glide.api.models.ClusterBatch;
import glide.api.models.GlideString;
import glide.api.models.configuration.ProtocolVersion;
import glide.ffi.resolvers.OpenTelemetryResolver;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30) // seconds
public class OpenTelemetryTests {

    private static String VALID_ENDPOINT_TRACES;
    private static final ThreadLocal<GlideSpanContext> ACTIVE_PARENT = new ThreadLocal<>();
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

        spans =
                Arrays.stream(spanData.split("\n"))
                        .filter(line -> !line.trim().isEmpty())
                        .collect(Collectors.toList());

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
        VALID_ENDPOINT_TRACES =
                Files.createTempDirectory("glide-java-otel-").resolve("spans.json").toString();
        // Initialize OpenTelemetry with valid configuration
        OpenTelemetryConfig openTelemetryConfig =
                OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint("file://" + VALID_ENDPOINT_TRACES)
                                        .samplePercentage(100)
                                        .build())
                        .metrics(OpenTelemetry.MetricsConfig.builder().endpoint(VALID_ENDPOINT_METRICS).build())
                        .flushIntervalMs(100L)
                        .parentSpanContextProvider(ACTIVE_PARENT::get)
                        .build();

        OpenTelemetry.init(openTelemetryConfig);
        GlideSpanContext parent = newParent();
        ACTIVE_PARENT.set(parent);
        assertSame(parent, OpenTelemetry.getParentSpanContext());
        assertSame(parent, openTelemetryConfig.getParentSpanContextProvider().get());
        ACTIVE_PARENT.remove();
    }

    @BeforeEach
    void resetTelemetryOptions() {
        OpenTelemetry.setSamplePercentage(100);
        OpenTelemetry.setParentSpanContextProvider(ACTIVE_PARENT::get);
        ACTIVE_PARENT.remove();
    }

    @Test
    void replacingAndClearingProviderDoesNotRetainInitializationCallback() throws Exception {
        java.lang.reflect.Field configField =
                OpenTelemetry.class.getDeclaredField("openTelemetryConfig");
        configField.setAccessible(true);
        OpenTelemetryConfig retained = (OpenTelemetryConfig) configField.get(null);
        assertNull(retained.getParentSpanContextProvider());
        GlideSpanContext first = newParent();
        OpenTelemetry.setParentSpanContextProvider(() -> first);
        assertSame(first, OpenTelemetry.getParentSpanContext());
        GlideSpanContext second = newParent();
        OpenTelemetry.setParentSpanContextProvider(() -> second);
        assertSame(second, OpenTelemetry.getParentSpanContext());
        OpenTelemetry.setParentSpanContextProvider(null);
        assertNull(OpenTelemetry.getParentSpanContext());
        assertNull(retained.getParentSpanContextProvider());
        assertEquals(100, OpenTelemetry.getSamplePercentage());
    }

    @AfterEach
    @SneakyThrows
    public void tearDown() {
        ACTIVE_PARENT.remove();
        OpenTelemetry.setParentSpanContextProvider(null);
        OpenTelemetry.setSamplePercentage(100);
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
    }

    private static GlideSpanContext newParent() {
        return new GlideSpanContext(
                UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                1,
                "vendor=value");
    }

    private static BaseClient tracingClient(boolean cluster, ProtocolVersion protocol)
            throws Exception {
        return cluster
                ? GlideClusterClient.createClient(commonClusterClientConfig().protocol(protocol).build())
                        .get()
                : GlideClient.createClient(commonClientConfig().protocol(protocol).build()).get();
    }

    public static Stream<Arguments> parentContextClients() {
        return Stream.of(
                Arguments.of(false, ProtocolVersion.RESP2),
                Arguments.of(false, ProtocolVersion.RESP3),
                Arguments.of(true, ProtocolVersion.RESP2),
                Arguments.of(true, ProtocolVersion.RESP3));
    }

    /** Read only complete JSON lines: the exporter can still be appending the last line. */
    private static List<JsonObject> jsonSpans() throws Exception {
        List<JsonObject> spans = new ArrayList<>();
        if (!Files.exists(Paths.get(VALID_ENDPOINT_TRACES))) return spans;
        String data =
                new String(
                        Files.readAllBytes(Paths.get(VALID_ENDPOINT_TRACES)),
                        java.nio.charset.StandardCharsets.UTF_8);
        int lastNewline = data.lastIndexOf('\n');
        if (lastNewline < 0) return spans;
        for (String line : data.substring(0, lastNewline).split("\n")) {
            if (!line.trim().isEmpty()) spans.add(JsonParser.parseString(line).getAsJsonObject());
        }
        return spans;
    }

    private static List<JsonObject> waitForTrace(String traceId, String... names) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        List<JsonObject> spans = new ArrayList<>();
        while (System.nanoTime() < deadline) {
            spans =
                    jsonSpans().stream()
                            .filter(span -> traceId.equals(span.get("trace_id").getAsString()))
                            .collect(Collectors.toList());
            List<String> found =
                    spans.stream().map(span -> span.get("name").getAsString()).collect(Collectors.toList());
            if (found.containsAll(Arrays.asList(names))) return spans;
            Thread.sleep(50);
        }
        fail("Missing spans " + Arrays.toString(names) + " for trace " + traceId + "; found " + spans);
        return spans;
    }

    private static void assertParent(JsonObject span, GlideSpanContext parent) {
        assertEquals(parent.getTraceId(), span.get("trace_id").getAsString());
        assertEquals(parent.getSpanId(), span.get("parent_span_id").getAsString());
        assertNotEquals(parent.getSpanId(), span.get("span_id").getAsString());
        assertNotEquals("0000000000000000", span.get("span_id").getAsString());
        assertEquals("Client", span.get("kind").getAsString());
    }

    private static void assertStandalone(JsonObject span, GlideSpanContext rejectedParent) {
        assertEquals("0000000000000000", span.get("parent_span_id").getAsString());
        String traceId = span.get("trace_id").getAsString();
        String spanId = span.get("span_id").getAsString();
        assertTrue(traceId.matches("[0-9a-f]{32}"));
        assertTrue(spanId.matches("[0-9a-f]{16}"));
        assertNotEquals("00000000000000000000000000000000", traceId);
        assertNotEquals("0000000000000000", spanId);
        assertNotEquals(rejectedParent.getTraceId(), traceId);
        assertNotEquals(rejectedParent.getSpanId(), spanId);
        assertEquals("Client", span.get("kind").getAsString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vendor=value, other=ok", " \tvendor=value \t,\t other=ok\t "})
    void tracestateWhitespacePreservesApplicationParent(String traceState) throws Exception {
        GlideSpanContext ids = newParent();
        GlideSpanContext parent =
                new GlideSpanContext(ids.getTraceId(), ids.getSpanId(), 1, traceState);
        try (BaseClient testClient = tracingClient(false, ProtocolVersion.RESP3)) {
            ACTIVE_PARENT.set(parent);
            testClient.get("tracestate-whitespace-" + parent.getTraceId()).get();
            ACTIVE_PARENT.remove();
            List<JsonObject> spans = waitForTrace(parent.getTraceId(), "Get");
            JsonObject command =
                    spans.stream()
                            .filter(span -> "Get".equals(span.get("name").getAsString()))
                            .findFirst()
                            .get();
            assertParent(command, parent);
        }
    }

    @ParameterizedTest
    @MethodSource("parentContextClients")
    void commandSpansInheritApplicationContext(boolean cluster, ProtocolVersion protocol)
            throws Exception {
        GlideSpanContext parent = newParent();
        String key = "parent-context-" + parent.getTraceId();
        try (BaseClient testClient = tracingClient(cluster, protocol)) {
            ACTIVE_PARENT.set(parent);
            CompletableFuture<String> pending = testClient.set(key, "value");
            // Capture happens at submission, not when the response arrives.
            ACTIVE_PARENT.remove();
            assertEquals("OK", pending.get());
            ACTIVE_PARENT.set(parent);
            assertEquals(GlideString.of("value"), testClient.get(GlideString.of(key)).get());
            assertNull(testClient.blpop(new String[] {key + "-missing"}, 0.01).get());
            if (cluster) {
                assertEquals(
                        "PONG",
                        ((GlideClusterClient) testClient)
                                .customCommand(new String[] {"PING"})
                                .get()
                                .getSingleValue());
            } else {
                assertEquals("PONG", ((GlideClient) testClient).customCommand(new String[] {"PING"}).get());
            }
            ACTIVE_PARENT.remove();
            List<JsonObject> spans =
                    waitForTrace(parent.getTraceId(), "Set", "Get", "BLPop", "CustomCommand");
            List<String> ids = new ArrayList<>();
            for (JsonObject span : spans) {
                if (Arrays.asList("Set", "Get", "BLPop", "CustomCommand")
                        .contains(span.get("name").getAsString())) {
                    assertParent(span, parent);
                    assertFalse(ids.contains(span.get("span_id").getAsString()));
                    ids.add(span.get("span_id").getAsString());
                }
            }
            assertEquals(4, ids.size());
        }
    }

    @ParameterizedTest
    @MethodSource("parentContextClients")
    void batchAndTransactionSpansInheritExecutionContext(boolean cluster, ProtocolVersion protocol)
            throws Exception {
        try (BaseClient testClient = tracingClient(cluster, protocol)) {
            for (boolean atomic : new boolean[] {false, true}) {
                // Build outside the application context: only exec should capture it.
                Batch batch = new Batch(atomic).ping();
                ClusterBatch clusterBatch = new ClusterBatch(atomic).ping();
                GlideSpanContext parent = newParent();
                ACTIVE_PARENT.set(parent);
                Object[] result =
                        cluster
                                ? ((GlideClusterClient) testClient).exec(clusterBatch, true).get()
                                : ((GlideClient) testClient).exec(batch, true).get();
                ACTIVE_PARENT.remove();
                assertArrayEquals(new Object[] {"PONG"}, result);
                List<JsonObject> spans = waitForTrace(parent.getTraceId(), "Batch", "send_batch");
                JsonObject root =
                        spans.stream()
                                .filter(span -> "Batch".equals(span.get("name").getAsString()))
                                .findFirst()
                                .get();
                assertParent(root, parent);
                JsonObject send =
                        spans.stream()
                                .filter(span -> "send_batch".equals(span.get("name").getAsString()))
                                .findFirst()
                                .get();
                assertEquals(root.get("span_id").getAsString(), send.get("parent_span_id").getAsString());
                assertNotEquals(root.get("span_id"), send.get("span_id"));
            }
        }
    }

    @Test
    void concurrentProvidersCaptureTheirOwnContext() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (BaseClient testClient = tracingClient(false, ProtocolVersion.RESP3)) {
            GlideSpanContext first = newParent();
            GlideSpanContext second = newParent();
            CountDownLatch capturing = new CountDownLatch(2);
            OpenTelemetry.setParentSpanContextProvider(
                    () -> {
                        capturing.countDown();
                        try {
                            assertTrue(capturing.await(5, TimeUnit.SECONDS), "Both providers must overlap");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("Interrupted while synchronizing providers", e);
                        }
                        return ACTIVE_PARENT.get();
                    });
            CompletableFuture<?>[] tasks =
                    Stream.of(first, second)
                            .map(
                                    parent ->
                                            CompletableFuture.runAsync(
                                                    () -> {
                                                        ACTIVE_PARENT.set(parent);
                                                        try {
                                                            testClient
                                                                    .get("context-" + parent.getTraceId())
                                                                    .get(5, TimeUnit.SECONDS);
                                                        } catch (Exception e) {
                                                            throw new AssertionError("Concurrent command failed", e);
                                                        } finally {
                                                            ACTIVE_PARENT.remove();
                                                        }
                                                    },
                                                    executor))
                            .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(tasks).get(10, TimeUnit.SECONDS);
            for (GlideSpanContext parent : Arrays.asList(first, second)) {
                for (JsonObject span : waitForTrace(parent.getTraceId(), "Get")) {
                    if ("Get".equals(span.get("name").getAsString())) assertParent(span, parent);
                }
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Context workers did not stop");
        }
    }

    @Test
    void zeroSamplingDoesNotInvokeProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenTelemetry.setParentSpanContextProvider(
                () -> {
                    calls.incrementAndGet();
                    return newParent();
                });
        OpenTelemetry.setSamplePercentage(0);
        try (BaseClient testClient = tracingClient(false, ProtocolVersion.RESP3)) {
            testClient.get("not-sampled").get();
            testClient.blpop(new String[] {"not-sampled-missing"}, 0.01).get();
            ((GlideClient) testClient).exec(new Batch(false).ping(), true).get();
            assertEquals(0, calls.get());
        }
    }

    @Test
    void unsampledParentIsNotReplacedWithStandaloneSpan() throws Exception {
        GlideSpanContext parent = newParent();
        GlideSpanContext unsampled =
                new GlideSpanContext(parent.getTraceId(), parent.getSpanId(), 0, null);
        AtomicInteger captures = new AtomicInteger();
        OpenTelemetry.setParentSpanContextProvider(
                () -> {
                    captures.incrementAndGet();
                    return ACTIVE_PARENT.get();
                });
        try (BaseClient testClient = tracingClient(false, ProtocolVersion.RESP3)) {
            GlideSpanContext baseline = newParent();
            ACTIVE_PARENT.set(baseline);
            testClient.get("sampling-baseline").get();
            waitForTrace(baseline.getTraceId(), "Get");
            List<String> existing =
                    jsonSpans().stream()
                            .map(span -> span.get("span_id").getAsString())
                            .collect(Collectors.toList());
            captures.set(0);
            ACTIVE_PARENT.set(unsampled);
            testClient.get("unsampled-parent").get();
            assertEquals(1, captures.get());
            // A later sampled operation serves as an exporter barrier for the completed request.
            GlideSpanContext sampled = newParent();
            ACTIVE_PARENT.set(sampled);
            testClient.get("sampled-parent").get();
            waitForTrace(sampled.getTraceId(), "Get");
            List<JsonObject> exported =
                    jsonSpans().stream()
                            .filter(span -> "Get".equals(span.get("name").getAsString()))
                            .filter(span -> !existing.contains(span.get("span_id").getAsString()))
                            .collect(Collectors.toList());
            assertEquals(1, exported.size(), "An unsampled parent must not produce a standalone span");
            assertParent(exported.get(0), sampled);
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "throwing",
                "null",
                "traceId",
                "spanId",
                "zeroTraceId",
                "zeroSpanId",
                "nullTraceId",
                "nullSpanId",
                "negativeFlags",
                "oversizedFlags",
                "traceState"
            })
    void invalidAndThrowingProvidersExportStandaloneSpans(String invalidField) throws Exception {
        try (GlideClient testClient = GlideClient.createClient(commonClientConfig().build()).get()) {
            // Drain earlier completed commands before taking the baseline. The file can contain
            // spans from earlier tests because the exporter outlives each test invocation.
            GlideSpanContext baseline = newParent();
            ACTIVE_PARENT.set(baseline);
            testClient.get("fallback-baseline-" + baseline.getTraceId()).get();
            waitForTrace(baseline.getTraceId(), "Get");
            List<String> existing =
                    jsonSpans().stream()
                            .map(span -> span.get("span_id").getAsString())
                            .collect(Collectors.toList());
            ACTIVE_PARENT.remove();

            GlideSpanContext parent = newParent();
            Supplier<GlideSpanContext> provider;
            switch (invalidField) {
                case "throwing":
                    provider =
                            () -> {
                                throw new IllegalStateException("test");
                            };
                    break;
                case "null":
                    provider = () -> null;
                    break;
                case "traceId":
                    provider = () -> new GlideSpanContext("bad", parent.getSpanId(), 1, null);
                    break;
                case "spanId":
                    provider = () -> new GlideSpanContext(parent.getTraceId(), "bad", 1, null);
                    break;
                case "zeroTraceId":
                    provider =
                            () ->
                                    new GlideSpanContext(
                                            "00000000000000000000000000000000", parent.getSpanId(), 1, null);
                    break;
                case "zeroSpanId":
                    provider = () -> new GlideSpanContext(parent.getTraceId(), "0000000000000000", 1, null);
                    break;
                case "nullTraceId":
                    provider = () -> new GlideSpanContext(null, parent.getSpanId(), 1, null);
                    break;
                case "nullSpanId":
                    provider = () -> new GlideSpanContext(parent.getTraceId(), null, 1, null);
                    break;
                case "negativeFlags":
                    provider = () -> new GlideSpanContext(parent.getTraceId(), parent.getSpanId(), -1, null);
                    break;
                case "oversizedFlags":
                    provider = () -> new GlideSpanContext(parent.getTraceId(), parent.getSpanId(), 256, null);
                    break;
                case "traceState":
                    provider =
                            () -> new GlideSpanContext(parent.getTraceId(), parent.getSpanId(), 1, "invalid");
                    break;
                default:
                    throw new AssertionError(invalidField);
            }
            AtomicInteger captures = new AtomicInteger();
            OpenTelemetry.setParentSpanContextProvider(
                    () -> {
                        captures.incrementAndGet();
                        return provider.get();
                    });
            String value = "fallback-" + invalidField + "-" + UUID.randomUUID();
            assertEquals(value, testClient.customCommand(new String[] {"ECHO", value}).get());
            assertEquals(1, captures.get());

            // Native spans end before futures complete. Seeing this later sampled span exported
            // therefore establishes that the fallback span has also passed through the exporter.
            GlideSpanContext sentinel = newParent();
            OpenTelemetry.setParentSpanContextProvider(ACTIVE_PARENT::get);
            ACTIVE_PARENT.set(sentinel);
            testClient.get("fallback-sentinel-" + sentinel.getTraceId()).get();
            waitForTrace(sentinel.getTraceId(), "Get");
            ACTIVE_PARENT.remove();
            List<JsonObject> exported =
                    jsonSpans().stream()
                            .filter(span -> "CustomCommand".equals(span.get("name").getAsString()))
                            .filter(span -> !existing.contains(span.get("span_id").getAsString()))
                            .collect(Collectors.toList());
            assertEquals(1, exported.size(), "Expected exactly one fallback span for " + invalidField);
            JsonObject fallback = exported.get(0);
            assertStandalone(fallback, parent);
            assertNotEquals(baseline.getTraceId(), fallback.get("trace_id").getAsString());
            assertNotEquals(sentinel.getTraceId(), fallback.get("trace_id").getAsString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"traceId", "spanId", "flags", "traceState", "zeroTraceId", "nullSpanId"})
    void nativeInvalidContextExportsStandaloneSpan(String invalidField) throws Exception {
        GlideSpanContext parent = newParent();
        String name = "Fallback-" + UUID.randomUUID();
        String traceId = parent.getTraceId();
        String spanId = parent.getSpanId();
        int flags = 1;
        String state = null;
        switch (invalidField) {
            case "traceId":
                traceId = "bad";
                break;
            case "zeroTraceId":
                traceId = "00000000000000000000000000000000";
                break;
            case "spanId":
                spanId = "bad";
                break;
            case "nullSpanId":
                spanId = null;
                break;
            case "flags":
                flags = 256;
                break;
            case "traceState":
                state = "missing-equals";
                break;
            default:
                throw new AssertionError(invalidField);
        }
        long pointer =
                OpenTelemetryResolver.createLeakedOtelSpanWithTraceContext(
                        name, traceId, spanId, flags, state);
        assertNotEquals(0, pointer);
        OpenTelemetryResolver.dropOtelSpan(pointer);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            for (JsonObject span : jsonSpans()) {
                if (name.equals(span.get("name").getAsString())) {
                    assertStandalone(span, parent);
                    return;
                }
            }
            Thread.sleep(50);
        }
        fail("No standalone span exported for " + invalidField);
    }
}
