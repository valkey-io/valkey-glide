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
import glide.api.models.commands.GetExOptions;
import glide.api.models.configuration.CompressionBackend;
import glide.api.models.configuration.CompressionConfiguration;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.RequestException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
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

    // ============================================================================
    // Supported Commands Tests
    // ============================================================================

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_mset_mget(BaseClient client) {
        try {
            String key1 = randomKey("{mset_test}_1");
            String key2 = randomKey("{mset_test}_2");
            String key3 = randomKey("{mset_test}_3");
            String value = generateCompressibleText(1024);

            long before = getStat(client, "total_values_compressed");

            // MSET should compress values
            Map<String, String> keyValueMap = new LinkedHashMap<>();
            keyValueMap.put(key1, value);
            keyValueMap.put(key2, value);
            keyValueMap.put(key3, value);
            assertEquals(OK, client.mset(keyValueMap).get());

            assertTrue(
                    getStat(client, "total_values_compressed") >= before + 3,
                    "MSET should compress all values");

            // MGET should decompress values
            String[] retrieved = client.mget(new String[] {key1, key2, key3}).get();
            assertEquals(3, retrieved.length);
            assertEquals(value, retrieved[0]);
            assertEquals(value, retrieved[1]);
            assertEquals(value, retrieved[2]);

            client.del(new String[] {key1, key2, key3}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_msetnx(BaseClient client) {
        try {
            String key1 = randomKey("{msetnx_test}_1");
            String key2 = randomKey("{msetnx_test}_2");
            String value = generateCompressibleText(1024);

            long before = getStat(client, "total_values_compressed");

            // MSETNX should compress values
            Map<String, String> keyValueMap = new LinkedHashMap<>();
            keyValueMap.put(key1, value);
            keyValueMap.put(key2, value);
            assertTrue(client.msetnx(keyValueMap).get(), "MSETNX should succeed for new keys");

            assertTrue(
                    getStat(client, "total_values_compressed") >= before + 2,
                    "MSETNX should compress all values");

            // Verify values can be retrieved and decompressed
            assertEquals(value, client.get(key1).get());
            assertEquals(value, client.get(key2).get());

            client.del(new String[] {key1, key2}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_getex(BaseClient client) {
        try {
            String key = randomKey("getex_test");
            String value = generateCompressibleText(1024);

            // Set value (should be compressed)
            long compressBefore = getStat(client, "total_values_compressed");
            assertEquals(OK, client.set(key, value).get());
            assertTrue(
                    getStat(client, "total_values_compressed") > compressBefore, "SET should compress value");

            // GETEX should decompress value
            long decompressBefore = getStat(client, "total_values_decompressed");
            String retrieved = client.getex(key, GetExOptions.Seconds(10L)).get();
            assertEquals(value, retrieved);
            assertTrue(
                    getStat(client, "total_values_decompressed") > decompressBefore,
                    "GETEX should decompress value");

            // Verify TTL was set
            long ttl = client.ttl(key).get();
            assertTrue(ttl > 0 && ttl <= 10, "TTL should be set");

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_getdel(BaseClient client) {
        try {
            String key = randomKey("getdel_test");
            String value = generateCompressibleText(1024);

            // Set value (should be compressed)
            long compressBefore = getStat(client, "total_values_compressed");
            assertEquals(OK, client.set(key, value).get());
            assertTrue(
                    getStat(client, "total_values_compressed") > compressBefore, "SET should compress value");

            // GETDEL should decompress value and delete key
            long decompressBefore = getStat(client, "total_values_decompressed");
            String retrieved = client.getdel(key).get();
            assertEquals(value, retrieved);
            assertTrue(
                    getStat(client, "total_values_decompressed") > decompressBefore,
                    "GETDEL should decompress value");

            // Verify key was deleted
            assertEquals(null, client.get(key).get());
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void compression_setex_via_custom_command() {
        try (GlideClient client = compressionClient()) {
            String key = randomKey("setex_test");
            String value = generateCompressibleText(1024);

            long before = getStat(client, "total_values_compressed");

            // SETEX via custom command should compress value
            assertEquals(OK, client.customCommand(new String[] {"SETEX", key, "10", value}).get());

            assertTrue(
                    getStat(client, "total_values_compressed") > before, "SETEX should compress value");

            // Verify value can be retrieved and decompressed
            assertEquals(value, client.get(key).get());

            // Verify TTL was set
            long ttl = client.ttl(key).get();
            assertTrue(ttl > 0 && ttl <= 10, "TTL should be set");

            client.del(new String[] {key}).get();
        }
    }

    @SneakyThrows
    @Test
    public void compression_psetex_via_custom_command() {
        try (GlideClient client = compressionClient()) {
            String key = randomKey("psetex_test");
            String value = generateCompressibleText(1024);

            long before = getStat(client, "total_values_compressed");

            // PSETEX via custom command should compress value
            assertEquals(OK, client.customCommand(new String[] {"PSETEX", key, "10000", value}).get());

            assertTrue(
                    getStat(client, "total_values_compressed") > before, "PSETEX should compress value");

            // Verify value can be retrieved and decompressed
            assertEquals(value, client.get(key).get());

            client.del(new String[] {key}).get();
        }
    }

    @SneakyThrows
    @Test
    public void compression_setnx_via_custom_command() {
        try (GlideClient client = compressionClient()) {
            String key = randomKey("setnx_test");
            String value = generateCompressibleText(1024);

            // Ensure key doesn't exist
            client.del(new String[] {key}).get();

            long before = getStat(client, "total_values_compressed");

            // SETNX via custom command should compress value
            assertEquals(1L, client.customCommand(new String[] {"SETNX", key, value}).get());

            assertTrue(
                    getStat(client, "total_values_compressed") > before, "SETNX should compress value");

            // Verify value can be retrieved and decompressed
            assertEquals(value, client.get(key).get());

            client.del(new String[] {key}).get();
        }
    }

    // ============================================================================
    // Incompatible Commands Tests
    // ============================================================================

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_append_incompatible(BaseClient client) {
        try {
            String key = randomKey("append_test");
            assertEquals(OK, client.set(key, "initial_value").get());

            // APPEND should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.append(key, "_appended").get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_getrange_incompatible(BaseClient client) {
        try {
            String key = randomKey("getrange_test");
            assertEquals(OK, client.set(key, generateCompressibleText(1024)).get());

            // GETRANGE should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.getrange(key, 0, 10).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_setrange_incompatible(BaseClient client) {
        try {
            String key = randomKey("setrange_test");
            assertEquals(OK, client.set(key, generateCompressibleText(1024)).get());

            // SETRANGE should fail with compression enabled
            ExecutionException ex =
                    assertThrows(
                            ExecutionException.class, () -> client.setrange(key, 5, "replacement").get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_strlen_incompatible(BaseClient client) {
        try {
            String key = randomKey("strlen_test");
            assertEquals(OK, client.set(key, generateCompressibleText(1024)).get());

            // STRLEN should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.strlen(key).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_incr_incompatible(BaseClient client) {
        try {
            String key = randomKey("incr_test");
            assertEquals(OK, client.set(key, "100").get());

            // INCR should fail with compression enabled
            ExecutionException ex = assertThrows(ExecutionException.class, () -> client.incr(key).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_incrby_incompatible(BaseClient client) {
        try {
            String key = randomKey("incrby_test");
            assertEquals(OK, client.set(key, "100").get());

            // INCRBY should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.incrBy(key, 10).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_incrbyfloat_incompatible(BaseClient client) {
        try {
            String key = randomKey("incrbyfloat_test");
            assertEquals(OK, client.set(key, "100.5").get());

            // INCRBYFLOAT should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.incrByFloat(key, 0.5).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_decr_incompatible(BaseClient client) {
        try {
            String key = randomKey("decr_test");
            assertEquals(OK, client.set(key, "100").get());

            // DECR should fail with compression enabled
            ExecutionException ex = assertThrows(ExecutionException.class, () -> client.decr(key).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_decrby_incompatible(BaseClient client) {
        try {
            String key = randomKey("decrby_test");
            assertEquals(OK, client.set(key, "100").get());

            // DECRBY should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.decrBy(key, 10).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_getbit_incompatible(BaseClient client) {
        try {
            String key = randomKey("getbit_test");
            assertEquals(OK, client.set(key, "test_value").get());

            // GETBIT should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.getbit(key, 0).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_setbit_incompatible(BaseClient client) {
        try {
            String key = randomKey("setbit_test");
            assertEquals(OK, client.set(key, "test_value").get());

            // SETBIT should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.setbit(key, 0, 1).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_bitcount_incompatible(BaseClient client) {
        try {
            String key = randomKey("bitcount_test");
            assertEquals(OK, client.set(key, "test_value").get());

            // BITCOUNT should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.bitcount(key).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @ParameterizedTest
    @MethodSource("getCompressionClients")
    public void compression_bitpos_incompatible(BaseClient client) {
        try {
            String key = randomKey("bitpos_test");
            assertEquals(OK, client.set(key, "test_value").get());

            // BITPOS should fail with compression enabled
            ExecutionException ex =
                    assertThrows(ExecutionException.class, () -> client.bitpos(key, 1).get());
            assertTrue(ex.getCause() instanceof RequestException, "Should throw RequestException");
            String errorMsg = ex.getCause().getMessage().toLowerCase();
            assertTrue(
                    errorMsg.contains("incompatible") || errorMsg.contains("compression"),
                    "Error should mention incompatibility: " + errorMsg);

            client.del(new String[] {key}).get();
        } finally {
            client.close();
        }
    }

    @SneakyThrows
    @Test
    public void compression_incompatible_commands_work_without_compression() {
        try (GlideClient client = GlideClient.createClient(commonClientConfig().build()).get()) {
            String key = randomKey("no_compression_test");

            // Set initial value
            assertEquals(OK, client.set(key, "100").get());

            // All these commands should work without compression
            // INCR
            assertEquals(101L, client.incr(key).get());

            // INCRBY
            assertEquals(111L, client.incrBy(key, 10).get());

            // DECR
            assertEquals(110L, client.decr(key).get());

            // DECRBY
            assertEquals(100L, client.decrBy(key, 10).get());

            // STRLEN
            assertEquals(OK, client.set(key, "hello").get());
            assertEquals(5L, client.strlen(key).get());

            // APPEND
            assertEquals(11L, client.append(key, " world").get());

            // GETRANGE
            assertEquals("hello", client.getrange(key, 0, 4).get());

            // SETRANGE
            assertEquals(11L, client.setrange(key, 6, "WORLD").get());

            // GETBIT
            assertEquals(OK, client.set(key, "\u0000").get());
            assertEquals(0L, client.getbit(key, 0).get());

            // SETBIT
            assertEquals(0L, client.setbit(key, 0, 1).get());

            // BITCOUNT
            assertTrue(client.bitcount(key).get() >= 0);

            client.del(new String[] {key}).get();
        }
    }
}
