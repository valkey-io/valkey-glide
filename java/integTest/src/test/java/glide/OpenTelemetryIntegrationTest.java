/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.OpenTelemetryConfig;
import glide.api.models.configuration.ProtocolVersion;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Integration tests for OpenTelemetry functionality in the Valkey GLIDE Java client. These tests
 * require a running Redis server.
 */
public class OpenTelemetryIntegrationTest {

    private GlideClient client;
    private GlideClusterClient clusterClient;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        // Clean up any existing clients
        if (client != null) {
            client.close();
            client = null;
        }

        if (clusterClient != null) {
            clusterClient.close();
            clusterClient = null;
        }

        // Force garbage collection to ensure clean state
        System.gc();
    }

    @AfterEach
    @SneakyThrows
    void tearDown() {
        // Clean up clients after each test
        if (client != null) {
            client.close();
        }

        if (clusterClient != null) {
            clusterClient.close();
        }
    }

    /**
     * Creates a GlideClient with OpenTelemetry configuration.
     *
     * @param protocol The protocol version to use
     * @param tracesPath The path for traces collection
     * @param flushIntervalMs The flush interval in milliseconds
     * @return A configured GlideClient
     */
    @SneakyThrows
    private GlideClient createGlideClientWithOtel(
            ProtocolVersion protocol, String tracesPath, int flushIntervalMs) {
        return GlideClient.createClient(
                        commonClientConfig()
                                .protocol(protocol)
                                .requestTimeout(10000)
                                .advancedConfiguration(
                                        AdvancedGlideClientConfiguration.builder()
                                                .openTelemetryConfig(
                                                        OpenTelemetryConfig.builder()
                                                                .tracesCollectorEndPoint(tracesPath)
                                                                .flushIntervalMs(flushIntervalMs)
                                                                .build())
                                                .build())
                                .build())
                .get();
    }

    /**
     * Creates a GlideClusterClient with OpenTelemetry configuration.
     *
     * @param protocol The protocol version to use
     * @param tracesPath The path for traces collection
     * @param flushIntervalMs The flush interval in milliseconds
     * @return A configured GlideClusterClient
     */
    @SneakyThrows
    private GlideClusterClient createGlideClusterClientWithOtel(
            ProtocolVersion protocol, String tracesPath, int flushIntervalMs) {
        return GlideClusterClient.createClient(
                        commonClusterClientConfig()
                                .protocol(protocol)
                                .requestTimeout(10000)
                                .advancedConfiguration(
                                        AdvancedGlideClusterClientConfiguration.builder()
                                                .openTelemetryConfig(
                                                        OpenTelemetryConfig.builder()
                                                                .tracesCollectorEndPoint(tracesPath)
                                                                .flushIntervalMs(flushIntervalMs)
                                                                .build())
                                                .build())
                                .build())
                .get();
    }

    /**
     * Test that spans are properly created and exported to a file. This test verifies that the
     * OpenTelemetry integration works end-to-end by checking the content of the exported spans file.
     */
    @ParameterizedTest
    // TODO: revert to testing with both protocols after fixing the OTel global config issue (Java
    // equivalent of Node issue #3631)
    //    @EnumSource(ProtocolVersion.class)
    @EnumSource(
            value = ProtocolVersion.class,
            names = {"RESP3"})
    @Order(1)
    void testSpanExportToFile(ProtocolVersion protocol, @TempDir Path tempDir) throws Exception {
        // The commonClientConfig() method will handle connection details
        String tracesPath = "file://" + tempDir.toAbsolutePath() + "/";

        // Create client using the helper method
        client = createGlideClientWithOtel(protocol, tracesPath, 100);

        // Execute some commands to generate spans
        client.set("test_key", "test_value");
        client.get("test_key");
        client.del(new String[] {"test_key"});

        // Wait for spans to be flushed
        Thread.sleep(500);

        // Check if spans file was created
        File spansDir = tempDir.toFile();
        assertTrue(spansDir.exists(), "Spans directory should exist");

        File[] files = spansDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(files, "Files array should not be null");

        // Wait a bit longer if no files are found yet
        if (files.length == 0) {
            Thread.sleep(1000);
            files = spansDir.listFiles((dir, name) -> name.endsWith(".json"));
        }

        assertTrue(files != null && files.length > 0, "At least one span file should be created");

        // Read the content of the first spans file
        File spanFile = files[0];
        List<String> lines = Files.readAllLines(spanFile.toPath());
        assertFalse(lines.isEmpty(), "Spans file should not be empty");

        // Verify that the spans contain expected command names
        boolean hasSetCommand = false;
        boolean hasGetCommand = false;
        boolean hasDelCommand = false;

        for (String line : lines) {
            if (line.contains("\"name\":\"Set\"")) {
                hasSetCommand = true;
            }
            if (line.contains("\"name\":\"Get\"")) {
                hasGetCommand = true;
            }
            if (line.contains("\"name\":\"Del\"")) {
                hasDelCommand = true;
            }
        }

        assertTrue(
                hasSetCommand || hasGetCommand || hasDelCommand,
                "Spans should contain at least one of the executed commands");
    }

    // TODO: uncomment below tests after fixing the OTel global config issue which only allow the
    // first client to set the config (Java equivalent of Node issue #3631)
    //    /**
    //     * Test that transaction spans are properly created and exported.
    //     */
    //    @ParameterizedTest
    //    @EnumSource(ProtocolVersion.class)
    //    void testTransactionSpans(ProtocolVersion protocol, @TempDir Path tempDir) throws Exception
    // {
    //        // The commonClientConfig() method will handle connection details
    //        String tracesPath = "file://" + tempDir.toAbsolutePath() + "/";
    //
    //        // Create client using the helper method
    //        client = createGlideClientWithOtel(protocol, tracesPath, 100);
    //
    //        // Create and execute a transaction
    //        Transaction transaction = new Transaction();
    //        transaction.set("test_key", "test_value");
    //        transaction.get("test_key");
    //
    //        client.exec(transaction);
    //
    //        // Wait for spans to be flushed
    //        Thread.sleep(500);
    //
    //        // Check if spans file was created
    //        File spansDir = tempDir.toFile();
    //        File[] files = spansDir.listFiles((dir, name) -> name.endsWith(".json"));
    //
    //        // Wait a bit longer if no files are found yet
    //        if (files == null || files.length == 0) {
    //            Thread.sleep(1000);
    //            files = spansDir.listFiles((dir, name) -> name.endsWith(".json"));
    //        }
    //
    //        assertNotNull(files, "Files array should not be null");
    //        assertTrue(files.length > 0, "At least one span file should be created");
    //
    //        // Read all span files and check for transaction spans
    //        boolean hasBatchSpan = false;
    //
    //        for (File file : files) {
    //            List<String> lines = Files.readAllLines(file.toPath());
    //            for (String line : lines) {
    //                if (line.contains("\"name\":\"Batch\"") ||
    // line.contains("\"name\":\"send_batch\"")) {
    //                    hasBatchSpan = true;
    //                    break;
    //                }
    //            }
    //            if (hasBatchSpan) break;
    //        }
    //
    //        assertTrue(hasBatchSpan, "Should have at least one batch/transaction span");
    //    }
    //
    //    /**
    //     * Test that cluster client spans are properly created and exported.
    //     */
    //    @ParameterizedTest
    //    @EnumSource(ProtocolVersion.class)
    //    void testClusterClientSpans(ProtocolVersion protocol, @TempDir Path tempDir) throws
    // Exception {
    //        // The commonClusterClientConfig() method will handle connection details
    //        String tracesPath = "file://" + tempDir.toAbsolutePath() + "/";
    //
    //        // Create cluster client using the helper method
    //        clusterClient = createGlideClusterClientWithOtel(protocol, tracesPath, 100);
    //
    //        // Execute some commands to generate spans
    //        clusterClient.set("test_cluster_key", "test_value");
    //        clusterClient.get("test_cluster_key");
    //        clusterClient.del(new String[]{"test_cluster_key"});
    //
    //        // Wait for spans to be flushed
    //        Thread.sleep(500);
    //
    //        // Check if spans file was created
    //        File spansDir = tempDir.toFile();
    //        File[] files = spansDir.listFiles((dir, name) -> name.endsWith(".json"));
    //
    //        // Wait a bit longer if no files are found yet
    //        if (files == null || files.length == 0) {
    //            Thread.sleep(1000);
    //            files = spansDir.listFiles((dir, name) -> name.endsWith(".json"));
    //        }
    //
    //        assertNotNull(files, "Files array should not be null");
    //        assertTrue(files.length > 0, "At least one span file should be created");
    //    }
    //
    //    /**
    //     * Test that there are no memory leaks when executing many operations.
    //     */
    //    @ParameterizedTest
    //    @EnumSource(ProtocolVersion.class)
    //    void testNoMemoryLeaksWithManyOperations(ProtocolVersion protocol, @TempDir Path tempDir)
    // throws Exception {
    //        // The commonClientConfig() method will handle connection details
    //        String tracesPath = "file://" + tempDir.toAbsolutePath() + "/";
    //
    //        // Create client using the helper method
    //        client = createGlideClientWithOtel(protocol, tracesPath, 100);
    //
    //        // Force GC and record memory usage
    //        System.gc();
    //        long startMemory = Runtime.getRuntime().totalMemory() -
    // Runtime.getRuntime().freeMemory();
    //
    //        // Execute many commands
    //        for (int i = 0; i < 100; i++) {
    //            client.set("test_key_" + i, "test_value_" + i);
    //            client.get("test_key_" + i);
    //            client.del(new String[]{"test_key_" + i});
    //        }
    //
    //        // Force GC again and check memory usage
    //        System.gc();
    //        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    //
    //        // Allow for some memory growth but not excessive
    //        assertTrue(endMemory < startMemory * 1.5,
    //            "Memory usage should not grow excessively: start=" + startMemory + ", end=" +
    // endMemory);
    //    }
    //
    //    /**
    //     * Test that there are no memory leaks when executing concurrent operations.
    //     */
    //    @ParameterizedTest
    //    @EnumSource(ProtocolVersion.class)
    //    void testNoMemoryLeaksWithConcurrentOperations(ProtocolVersion protocol, @TempDir Path
    // tempDir) throws Exception {
    //        // The commonClientConfig() method will handle connection details
    //        String tracesPath = "file://" + tempDir.toAbsolutePath() + "/";
    //
    //        // Create client using the helper method
    //        client = createGlideClientWithOtel(protocol, tracesPath, 100);
    //
    //        // Force GC and record memory usage
    //        System.gc();
    //        long startMemory = Runtime.getRuntime().totalMemory() -
    // Runtime.getRuntime().freeMemory();
    //
    //        // Execute concurrent commands
    //        List<CompletableFuture<?>> futures = new ArrayList<>();
    //        for (int i = 0; i < 50; i++) {
    //            final int index = i;
    //            futures.add(CompletableFuture.runAsync(() -> {
    //                try {
    //                    client.set("concurrent_key_" + index, "value_" + index);
    //                    client.get("concurrent_key_" + index);
    //                    client.del(new String[]{"concurrent_key_" + index});
    //                } catch (Exception e) {
    //                    throw new RuntimeException(e);
    //                }
    //            }));
    //        }
    //
    //        // Wait for all operations to complete
    //        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    //            .get(30, TimeUnit.SECONDS);
    //
    //        // Force GC again and check memory usage
    //        System.gc();
    //        long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    //
    //        // Allow for some memory growth but not excessive
    //        assertTrue(endMemory < startMemory * 1.5,
    //            "Memory usage should not grow excessively: start=" + startMemory + ", end=" +
    // endMemory);
    //    }
}
