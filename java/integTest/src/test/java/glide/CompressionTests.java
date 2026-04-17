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

    private static final String COMPRESSIBLE_PATTERN;

    static {
        StringBuilder sb = new StringBuilder(30);
        for (int i = 0; i < 10; i++) sb.append('A');
        for (int i = 0; i < 10; i++) sb.append('B');
        for (int i = 0; i < 10; i++) sb.append('C');
        COMPRESSIBLE_PATTERN = sb.toString();
    }

    private static String generateCompressibleText(int sizeBytes) {
        int repeats = (sizeBytes / COMPRESSIBLE_PATTERN.length()) + 1;
        StringBuilder sb = new StringBuilder(COMPRESSIBLE_PATTERN.length() * repeats);
        for (int i = 0; i < repeats; i++) sb.append(COMPRESSIBLE_PATTERN);
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

    // --- Stats helper ---

    private static long getStat(BaseClient client, String key) {
        return Long.parseLong(client.getStatistics().get(key));
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

    // --- Common assertion: set value, verify get, verify compression stat increased ---

    @SneakyThrows
    private void assertCompressedSetGet(BaseClient client, String prefix, String value) {
        String key = randomKey(prefix);
        long before = getStat(client, "total_values_compressed");

        assertEquals(OK, client.set(key, value).get());
        assertEquals(value, client.get(key).get());

        assertTrue(
                getStat(client, "total_values_compressed") > before,
                "Compression should be applied for " + prefix);

        client.del(new String[] {key}).get();
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
            for (int size : new int[] {512, 1024, 10240}) {
                assertCompressedSetGet(client, "test_compression_" + size, generateCompressibleText(size));
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
            long initialSkipped = getStat(client, "compression_skipped_count");
            long initialCompressed = getStat(client, "total_values_compressed");

            // Values below default threshold (64 bytes) should be skipped
            for (int size : new int[] {32, 48, 63}) {
                String key = randomKey("below_threshold_" + size);
                assertEquals(OK, client.set(key, generateCompressibleText(size)).get());

                assertTrue(
                        getStat(client, "compression_skipped_count") > initialSkipped,
                        "Size " + size + ": should be skipped");
                assertEquals(
                        initialCompressed,
                        getStat(client, "total_values_compressed"),
                        "Size " + size + ": should not be compressed");

                initialSkipped = getStat(client, "compression_skipped_count");
                client.del(new String[] {key}).get();
            }

            // Values at/above threshold should be compressed
            for (int size : new int[] {64, 128, 256}) {
                String key = randomKey("above_threshold_" + size);
                assertEquals(OK, client.set(key, generateCompressibleText(size)).get());

                assertTrue(
                        getStat(client, "total_values_compressed") > initialCompressed,
                        "Size " + size + ": should be compressed");

                initialCompressed = getStat(client, "total_values_compressed");
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
            long initialCompressed = getStat(client, "total_values_compressed");
            long initialSkipped = getStat(client, "compression_skipped_count");

            for (int size : new int[] {64, 1024, 10240}) {
                String key = randomKey("no_compression_" + size);
                assertEquals(OK, client.set(key, generateCompressibleText(size)).get());

                assertEquals(
                        initialCompressed,
                        getStat(client, "total_values_compressed"),
                        "No compression when disabled. Size: " + size);
                assertEquals(
                        initialSkipped,
                        getStat(client, "compression_skipped_count"),
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
            assertCompressedSetGet(client, "zstd_test", generateCompressibleText(1024));
        }
    }

    @SneakyThrows
    @Test
    public void compression_lz4_backend() {
        try (GlideClient client = compressionClientWithBackend(CompressionBackend.LZ4)) {
            assertCompressedSetGet(client, "lz4_test", generateCompressibleText(1024));
        }
    }

    // ============================================================================
    // Data Type Tests
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_data_types() {
        try (GlideClient client = compressionClient()) {
            assertCompressedSetGet(client, "compressible", generateCompressibleText(1024));
            assertCompressedSetGet(client, "base64", generateBase64Data(1024));
        }
    }

    // ============================================================================
    // Cross-Client Compatibility Tests
    // ============================================================================

    @SneakyThrows
    @Test
    public void compression_cross_client_read() {
        try (GlideClient compressedClient = compressionClient();
                GlideClient plainClient = GlideClient.createClient(commonClientConfig().build()).get()) {

            String key = randomKey("cross_client");
            String value = generateCompressibleText(1024);

            // Write with plain client, read with compressed client
            assertEquals(OK, plainClient.set(key, value).get());
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
            long originalBytesBefore = getStat(client, "total_original_bytes");
            long compressedBytesBefore = getStat(client, "total_bytes_compressed");

            String key = randomKey("stats_test");
            assertEquals(OK, client.set(key, generateCompressibleText(1024)).get());

            long originalBytes = getStat(client, "total_original_bytes") - originalBytesBefore;
            long compressedBytes = getStat(client, "total_bytes_compressed") - compressedBytesBefore;

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
