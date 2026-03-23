/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static glide.TestUtilities.commonClientConfig;
import static glide.TestUtilities.commonClusterClientConfig;
import static glide.api.BaseClient.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.CompressionBackend;
import glide.api.models.configuration.CompressionConfiguration;
import glide.api.models.exceptions.ConfigurationError;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(35)
public class CompressionTests {

    // --- Data generation helpers ---

    private static String generateCompressibleText(int sizeBytes) {
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 10; i++) pattern.append('A');
        for (int i = 0; i < 10; i++) pattern.append('B');
        for (int i = 0; i < 10; i++) pattern.append('C');
        String p = pattern.toString();
        int repeats = (sizeBytes / p.length()) + 1;
        StringBuilder sb = new StringBuilder(p.length() * repeats);
        for (int i = 0; i < repeats; i++) sb.append(p);
        return sb.substring(0, sizeBytes);
    }

    private static String generateBase64Data(int sizeBytes) {
        byte[] raw = new byte[sizeBytes / 2];
        new Random().nextBytes(raw);
        String encoded = Base64.getEncoder().encodeToString(raw);
        return encoded.length() > sizeBytes ? encoded.substring(0, sizeBytes) : encoded;
    }

    private static String randomKey(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // --- Client creation helpers ---

    @SneakyThrows
    private static GlideClient compressionClient() {
        return GlideClient.createClient(
                        commonClientConfig()
                                .compressionConfiguration(CompressionConfiguration.builder().enabled(true).build())
                                .build())
                .get();
    }

    @SneakyThrows
    private static GlideClusterClient compressionClusterClient() {
        return GlideClusterClient.createClient(
                        commonClusterClientConfig()
                                .compressionConfiguration(CompressionConfiguration.builder().enabled(true).build())
                                .build())
                .get();
    }

    @SneakyThrows
    private static GlideClient compressionClientWithBackend(CompressionBackend backend) {
        return GlideClient.createClient(
                        commonClientConfig()
                                .compressionConfiguration(
                                        CompressionConfiguration.builder().enabled(true).backend(backend).build())
                                .build())
                .get();
    }

    // --- Provider methods ---

    static Stream<Arguments> getCompressionClients() {
        return Stream.of(Arguments.of(compressionClient()), Arguments.of(compressionClusterClient()));
    }

    // ============================================================================
    // Basic Compression Tests
    // ============================================================================

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_basic_set_get(BaseClient client) {
        try {
            int[] dataSizes = {512, 1024, 10240};

            for (int size : dataSizes) {
                String key = randomKey("test_compression_" + size);
                String value = generateCompressibleText(size);

                Map<String, String> initialStats = client.getStatistics();
                long initialCompressed = Long.parseLong(initialStats.get("total_values_compressed"));

                assertEquals(OK, client.set(key, value).get());
                assertEquals(value, client.get(key).get());

                Map<String, String> stats = client.getStatistics();
                long afterCompressed = Long.parseLong(stats.get("total_values_compressed"));
                assertTrue(
                        afterCompressed > initialCompressed,
                        "Compression should be applied for " + size + "B value");

                client.del(new String[] {key}).get();
            }
        } finally {
            client.close();
        }
    }

    // ============================================================================
    // Min Size Threshold Tests
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_min_size_threshold() {
        try (GlideClient client = compressionClient()) {
            Map<String, String> initialStats = client.getStatistics();
            long initialSkipped = Long.parseLong(initialStats.get("compression_skipped_count"));
            long initialCompressed = Long.parseLong(initialStats.get("total_values_compressed"));

            // Values below default threshold (64 bytes) should be skipped
            for (int size : new int[] {32, 48, 63}) {
                String key = randomKey("below_threshold_" + size);
                String value = generateCompressibleText(size);

                assertEquals(OK, client.set(key, value).get());
                assertEquals(value, client.get(key).get());

                Map<String, String> stats = client.getStatistics();
                long skipped = Long.parseLong(stats.get("compression_skipped_count"));
                assertTrue(skipped > initialSkipped, "Size " + size + ": should be skipped");
                assertEquals(
                        initialCompressed,
                        Long.parseLong(stats.get("total_values_compressed")),
                        "Size " + size + ": should not be compressed");

                initialSkipped = skipped;
                client.del(new String[] {key}).get();
            }

            // Values at/above threshold should be compressed
            for (int size : new int[] {64, 128, 256}) {
                String key = randomKey("above_threshold_" + size);
                String value = generateCompressibleText(size);

                assertEquals(OK, client.set(key, value).get());
                assertEquals(value, client.get(key).get());

                Map<String, String> stats = client.getStatistics();
                long compressed = Long.parseLong(stats.get("total_values_compressed"));
                assertTrue(compressed > initialCompressed, "Size " + size + ": should be compressed");

                initialCompressed = compressed;
                client.del(new String[] {key}).get();
            }
        }
    }

    // ============================================================================
    // Disabled By Default Test
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_disabled_by_default() {
        try (GlideClient client = GlideClient.createClient(commonClientConfig().build()).get()) {
            Map<String, String> initialStats = client.getStatistics();
            long initialCompressed = Long.parseLong(initialStats.get("total_values_compressed"));
            long initialSkipped = Long.parseLong(initialStats.get("compression_skipped_count"));

            for (int size : new int[] {64, 1024, 10240}) {
                String key = randomKey("no_compression_" + size);
                String value = generateCompressibleText(size);

                assertEquals(OK, client.set(key, value).get());
                assertEquals(value, client.get(key).get());

                Map<String, String> stats = client.getStatistics();
                assertEquals(
                        initialCompressed,
                        Long.parseLong(stats.get("total_values_compressed")),
                        "No compression when disabled. Size: " + size);
                assertEquals(
                        initialSkipped,
                        Long.parseLong(stats.get("compression_skipped_count")),
                        "Compression not attempted when disabled. Size: " + size);

                client.del(new String[] {key}).get();
            }
        }
    }

    // ============================================================================
    // Backend Tests
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_zstd_backend() {
        try (GlideClient client = compressionClientWithBackend(CompressionBackend.ZSTD)) {
            String key = randomKey("zstd_test");
            String value = generateCompressibleText(1024);

            Map<String, String> initialStats = client.getStatistics();
            long initialCompressed = Long.parseLong(initialStats.get("total_values_compressed"));

            assertEquals(OK, client.set(key, value).get());
            assertEquals(value, client.get(key).get());

            Map<String, String> stats = client.getStatistics();
            assertTrue(
                    Long.parseLong(stats.get("total_values_compressed")) > initialCompressed,
                    "ZSTD compression should be applied");

            client.del(new String[] {key}).get();
        }
    }

    @SneakyThrows
    @Test
    public void compression_lz4_backend() {
        try (GlideClient client = compressionClientWithBackend(CompressionBackend.LZ4)) {
            String key = randomKey("lz4_test");
            String value = generateCompressibleText(1024);

            Map<String, String> initialStats = client.getStatistics();
            long initialCompressed = Long.parseLong(initialStats.get("total_values_compressed"));

            assertEquals(OK, client.set(key, value).get());
            assertEquals(value, client.get(key).get());

            Map<String, String> stats = client.getStatistics();
            assertTrue(
                    Long.parseLong(stats.get("total_values_compressed")) > initialCompressed,
                    "LZ4 compression should be applied");

            client.del(new String[] {key}).get();
        }
    }

    // ============================================================================
    // Data Type Tests
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_data_types() {
        try (GlideClient client = compressionClient()) {
            // Compressible text
            verifyCompressionApplied(client, "compressible", generateCompressibleText(1024));
            // Base64 data
            verifyCompressionApplied(client, "base64", generateBase64Data(1024));
        }
    }

    @SneakyThrows
    private void verifyCompressionApplied(BaseClient client, String prefix, String value) {
        String key = randomKey(prefix);
        Map<String, String> initialStats = client.getStatistics();
        long initialCompressed = Long.parseLong(initialStats.get("total_values_compressed"));

        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());

        Map<String, String> stats = client.getStatistics();
        assertTrue(
                Long.parseLong(stats.get("total_values_compressed")) > initialCompressed,
                "Compression should be applied for " + prefix);

        client.del(new String[] {key}).get();
    }

    // ============================================================================
    // Cross-Client Compatibility Tests
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_cross_client_read() {
        // Compressed client writes, non-compressed client reads uncompressed data
        try (GlideClient compressedClient = compressionClient();
                GlideClient plainClient = GlideClient.createClient(commonClientConfig().build()).get()) {

            String key = randomKey("cross_client");
            String value = generateCompressibleText(1024);

            // Write with plain client
            assertEquals(OK, plainClient.set(key, value).get());
            // Read with compressed client (should handle uncompressed data)
            assertEquals(value, compressedClient.get(key).get());

            plainClient.del(new String[] {key}).get();
        }
    }

    // ============================================================================
    // Statistics Tests
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_statistics() {
        try (GlideClient client = compressionClient()) {
            Map<String, String> statsBefore = client.getStatistics();
            long originalBytesBefore = Long.parseLong(statsBefore.get("total_original_bytes"));
            long compressedBytesBefore = Long.parseLong(statsBefore.get("total_bytes_compressed"));

            String key = randomKey("stats_test");
            String value = generateCompressibleText(1024);

            assertEquals(OK, client.set(key, value).get());

            Map<String, String> statsAfter = client.getStatistics();
            long originalBytes =
                    Long.parseLong(statsAfter.get("total_original_bytes")) - originalBytesBefore;
            long compressedBytes =
                    Long.parseLong(statsAfter.get("total_bytes_compressed")) - compressedBytesBefore;

            assertTrue(compressedBytes > 0, "Should have compressed bytes");
            assertTrue(compressedBytes < originalBytes, "Compressed should be < original");

            client.del(new String[] {key}).get();
        }
    }

    // ============================================================================
    // Configuration Validation Tests
    // ============================================================================

    @Test
    public void compression_config_invalid_min_size() {
        assertThrows(
                ConfigurationError.class,
                () -> CompressionConfiguration.builder().minCompressionSize(3).build());
    }

    @Test
    public void compression_config_defaults() {
        CompressionConfiguration config = CompressionConfiguration.builder().build();
        assertTrue(config.isEnabled());
        assertEquals(CompressionBackend.ZSTD, config.getBackend());
        assertEquals(64, config.getMinCompressionSize());
    }

    @Test
    public void compression_config_custom_values() {
        CompressionConfiguration config =
                CompressionConfiguration.builder()
                        .enabled(true)
                        .backend(CompressionBackend.LZ4)
                        .compressionLevel(5)
                        .minCompressionSize(128)
                        .build();
        assertTrue(config.isEnabled());
        assertEquals(CompressionBackend.LZ4, config.getBackend());
        assertEquals(5, (int) config.getCompressionLevel());
        assertEquals(128, config.getMinCompressionSize());
    }
}
