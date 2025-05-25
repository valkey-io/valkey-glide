/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static org.junit.jupiter.api.Assertions.*;

import glide.api.models.ClusterBatch;
import glide.api.models.ProtocolVersion;
import glide.api.models.exceptions.ConfigurationException;
import glide.utils.TestUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenTelemetryTest {
    private static final int TIMEOUT = 50000;
    private static final String VALID_ENDPOINT_TRACES = "/tmp/spans.json";
    private static final String VALID_FILE_ENDPOINT_TRACES = "file://" + VALID_ENDPOINT_TRACES;
    private static final String VALID_ENDPOINT_METRICS = "https://valid-endpoint/v1/metrics";
    private static TestUtils.ValkeyCluster cluster;
    private static GlideClusterClient client;

    @BeforeAll
    static void setUp() throws Exception {
        // Connect to cluster or create a new one
        cluster = TestUtils.ValkeyCluster.createCluster(true, 3, 1);

        // Test wrong OpenTelemetry configs before initializing
        testWrongOpenTelemetryConfig();
        testSpanNotExportedBeforeInitOtel();

        // Initialize OpenTelemetry with valid config
        OpenTelemetry.init(
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint(VALID_FILE_ENDPOINT_TRACES)
                                        .samplePercentage(100)
                                        .build())
                        .metrics(OpenTelemetry.MetricsConfig.builder().endpoint(VALID_ENDPOINT_METRICS).build())
                        .flushIntervalMs(100L)
                        .build());
    }

    private static void testWrongOpenTelemetryConfig() {
        // Wrong traces endpoint
        assertThrows(
                ConfigurationException.class,
                () -> {
                    OpenTelemetry.init(
                            OpenTelemetry.OpenTelemetryConfig.builder()
                                    .traces(OpenTelemetry.TracesConfig.builder().endpoint("wrong.endpoint").build())
                                    .build());
                });

        // Wrong metrics endpoint
        assertThrows(
                ConfigurationException.class,
                () -> {
                    OpenTelemetry.init(
                            OpenTelemetry.OpenTelemetryConfig.builder()
                                    .metrics(OpenTelemetry.MetricsConfig.builder().endpoint("wrong.endpoint").build())
                                    .build());
                });

        // Negative flush interval
        assertThrows(
                ConfigurationException.class,
                () -> {
                    OpenTelemetry.init(
                            OpenTelemetry.OpenTelemetryConfig.builder()
                                    .traces(
                                            OpenTelemetry.TracesConfig.builder()
                                                    .endpoint(VALID_FILE_ENDPOINT_TRACES)
                                                    .samplePercentage(1)
                                                    .build())
                                    .flushIntervalMs(-400L)
                                    .build());
                });

        // Negative sample percentage
        assertThrows(
                ConfigurationException.class,
                () -> {
                    OpenTelemetry.init(
                            OpenTelemetry.OpenTelemetryConfig.builder()
                                    .traces(
                                            OpenTelemetry.TracesConfig.builder()
                                                    .endpoint(VALID_FILE_ENDPOINT_TRACES)
                                                    .samplePercentage(-400)
                                                    .build())
                                    .build());
                });

        // Wrong traces file path
        assertThrows(
                ConfigurationException.class,
                () -> {
                    OpenTelemetry.init(
                            OpenTelemetry.OpenTelemetryConfig.builder()
                                    .traces(
                                            OpenTelemetry.TracesConfig.builder()
                                                    .endpoint("file:invalid-path/v1/traces.json")
                                                    .build())
                                    .build());
                });

        // No traces or metrics provided
        assertThrows(
                ConfigurationException.class,
                () -> {
                    OpenTelemetry.init(OpenTelemetry.OpenTelemetryConfig.builder().build());
                });
    }

    private static void testSpanNotExportedBeforeInitOtel() throws Exception {
        teardownOtelTest();

        GlideClusterClient testClient =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(ProtocolVersion.RESP3)
                        .build();

        testClient.get("testSpanNotExportedBeforeInitOtel");

        // Check that spans are not exported to file before initializing OpenTelemetry
        assertFalse(new File(VALID_ENDPOINT_TRACES).exists());

        testClient.close();
    }

    @AfterEach
    void tearDown() throws Exception {
        teardownOtelTest();
        if (client != null) {
            client.close();
        }
    }

    @AfterAll
    static void cleanUp() throws Exception {
        if (cluster != null) {
            cluster.close();
        }
    }

    private static void teardownOtelTest() {
        // Clean up OpenTelemetry files
        new File(VALID_ENDPOINT_TRACES).delete();
        new File(VALID_ENDPOINT_METRICS).delete();
    }

    private static SpanData readAndParseSpanFile(String path) throws IOException {
        String spanData = Files.readString(Path.of(path));
        List<String> spans =
                spanData.lines().filter(line -> !line.trim().isEmpty()).collect(Collectors.toList());

        if (spans.isEmpty()) {
            throw new IOException("No spans found in the span file");
        }

        List<String> spanNames = new ArrayList<>();
        for (String line : spans) {
            try {
                SpanJson span = TestUtils.OBJECT_MAPPER.readValue(line, SpanJson.class);
                spanNames.add(span.name);
            } catch (Exception ignored) {
            }
        }

        return new SpanData(spanData, spans, spanNames);
    }

    private static class SpanData {
        final String spanData;
        final List<String> spans;
        final List<String> spanNames;

        SpanData(String spanData, List<String> spans, List<String> spanNames) {
            this.spanData = spanData;
            this.spans = spans;
            this.spanNames = spanNames;
        }
    }

    private static class SpanJson {
        public String name;
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @Order(1)
    void testSpanMemoryLeak(ProtocolVersion protocol) throws Exception {
        System.gc();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        client =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(protocol)
                        .build();

        // Execute a series of commands sequentially
        for (int i = 0; i < 100; i++) {
            String key = "test_key_" + i;
            client.set(key, "value_" + i);
            client.get(key);
        }

        System.gc();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        assertTrue(endMemory < startMemory * 1.1); // Allow 10% growth
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @Order(2)
    void testPercentageRequestsConfig(ProtocolVersion protocol) throws Exception {
        client =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(protocol)
                        .build();

        OpenTelemetry.setSamplePercentage(0);
        assertEquals(0, OpenTelemetry.getSamplePercentage());

        Thread.sleep(500); // Wait for spans to be flushed
        teardownOtelTest();

        for (int i = 0; i < 100; i++) {
            client.set("GlideClusterClient_test_percentage_requests_config", "value");
        }

        Thread.sleep(500);
        assertFalse(new File(VALID_ENDPOINT_TRACES).exists());

        OpenTelemetry.setSamplePercentage(100);

        // Execute a series of commands sequentially
        for (int i = 0; i < 10; i++) {
            String key = "GlideClusterClient_test_percentage_requests_config_" + i;
            client.get(key);
        }

        Thread.sleep(5000); // Wait for spans to be flushed

        SpanData spanData = readAndParseSpanFile(VALID_ENDPOINT_TRACES);
        assertTrue(spanData.spanNames.contains("Get"));
        assertEquals(10, spanData.spanNames.stream().filter(name -> name.equals("Get")).count());
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @Order(3)
    void testOtelGlobalConfigNotReinitialize(ProtocolVersion protocol) throws Exception {
        OpenTelemetry.init(
                OpenTelemetry.OpenTelemetryConfig.builder()
                        .traces(
                                OpenTelemetry.TracesConfig.builder()
                                        .endpoint("wrong.endpoint")
                                        .samplePercentage(1)
                                        .build())
                        .build());

        client =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(protocol)
                        .build();

        client.set("GlideClusterClient_test_otel_global_config", "value");

        Thread.sleep(500);

        SpanData spanData = readAndParseSpanFile(VALID_ENDPOINT_TRACES);
        assertTrue(spanData.spanNames.contains("Set"));
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @Order(4)
    void testSpanTransactionMemoryLeak(ProtocolVersion protocol) throws Exception {
        System.gc();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        client =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(protocol)
                        .build();

        ClusterBatch batch = new ClusterBatch(true);
        batch.set("test_key", "foo");
        batch.objectRefcount("test_key");

        List<Object> response = client.exec(batch, true);
        assertNotNull(response);
        assertEquals(2, response.size());
        assertEquals("OK", response.get(0));
        assertTrue((Long) response.get(1) >= 1);

        System.gc();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        assertTrue(endMemory < startMemory * 1.1); // Allow 10% growth
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @Order(5)
    void testNumberOfClientsWithSameConfig(ProtocolVersion protocol) throws Exception {
        GlideClusterClient client1 =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(protocol)
                        .build();

        GlideClusterClient client2 =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(protocol)
                        .build();

        client1.set("test_key", "value");
        client2.get("test_key");

        Thread.sleep(5000); // Wait for spans to be flushed

        SpanData spanData = readAndParseSpanFile(VALID_ENDPOINT_TRACES);
        assertTrue(spanData.spanNames.contains("Get"));
        assertTrue(spanData.spanNames.contains("Set"));

        client1.close();
        client2.close();
    }

    @ParameterizedTest
    @EnumSource(ProtocolVersion.class)
    @Order(6)
    void testSpanBatchFile(ProtocolVersion protocol) throws Exception {
        System.gc();
        long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        client =
                GlideClusterClient.builder()
                        .addresses(cluster.getAddresses())
                        .protocolVersion(protocol)
                        .build();

        ClusterBatch batch = new ClusterBatch(true);
        batch.set("test_key", "foo");
        batch.objectRefcount("test_key");

        List<Object> response = client.exec(batch, true);
        assertNotNull(response);
        assertEquals(2, response.size());
        assertEquals("OK", response.get(0));
        assertTrue((Long) response.get(1) >= 1);

        Thread.sleep(5000); // Wait for spans to be flushed

        SpanData spanData = readAndParseSpanFile(VALID_ENDPOINT_TRACES);
        assertTrue(spanData.spanNames.contains("Batch"));
        assertTrue(spanData.spanNames.contains("send_batch"));

        System.gc();
        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        assertTrue(endMemory < startMemory * 1.1); // Allow 10% growth
    }
}
