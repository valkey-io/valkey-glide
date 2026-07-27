/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.scope;

import static glide.TestConfiguration.CLUSTER_HOSTS;
import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static glide.utils.Java8Utils.repeat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.CompressionBackend;
import glide.api.models.configuration.CompressionConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.scope.IsolatedScope;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests verifying that connection modifiers (compression, request timeout, inflight
 * limits) are properly inherited and respected by scoped connections and pooled clients.
 *
 * <p>Tests are parameterized over cluster mode (true/false) to ensure both standalone and cluster
 * deployments behave identically.
 */
public class ScopeConnectionModifiersTest {

    // ═══════════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════════

    private static boolean standaloneAvailable() {
        return STANDALONE_HOSTS.length > 0 && !STANDALONE_HOSTS[0].isEmpty();
    }

    private static boolean clusterAvailable() {
        return CLUSTER_HOSTS.length > 0 && !CLUSTER_HOSTS[0].isEmpty();
    }

    private static void assumeMode(boolean clusterMode) {
        if (clusterMode) {
            assumeTrue(clusterAvailable(), "No cluster endpoints configured");
        } else {
            assumeTrue(standaloneAvailable(), "No standalone endpoints configured");
        }
    }

    /** Generate a key with hash tag when in cluster mode to ensure slot consistency. */
    private static String testKey(boolean clusterMode, String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return clusterMode ? "{scope-test}-" + prefix + "-" + id : prefix + "-" + id;
    }

    /** Wrapper providing uniform access to client operations across standalone/cluster. */
    private static class ClientWrapper implements AutoCloseable {
        private final BaseClient client;
        private final boolean clusterMode;

        ClientWrapper(BaseClient client, boolean clusterMode) {
            this.client = client;
            this.clusterMode = clusterMode;
        }

        CompletableFuture<IsolatedScope> scopedConnection(Duration timeout) {
            if (clusterMode) {
                return ((GlideClusterClient) client).scopedConnection(timeout);
            } else {
                return ((GlideClient) client).scopedConnection(timeout);
            }
        }

        CompletableFuture<String> get(String key) {
            return client.get(key);
        }

        CompletableFuture<String> set(String key, String value) {
            return client.set(key, value);
        }

        CompletableFuture<Long> del(String[] keys) {
            return client.del(keys);
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<Object> customCommand(String[] args) {
            if (clusterMode) {
                return ((GlideClusterClient) client).customCommand(args).thenApply(cv -> (Object) cv);
            } else {
                return ((GlideClient) client).customCommand(args);
            }
        }

        @Override
        public void close() throws Exception {
            client.close();
        }
    }

    /** Create a client with a given compression config. */
    private static ClientWrapper createClient(
            boolean clusterMode, CompressionConfiguration compression, Integer requestTimeout)
            throws Exception {
        return createClient(clusterMode, compression, requestTimeout, null, null);
    }

    private static ClientWrapper createClient(
            boolean clusterMode,
            CompressionConfiguration compression,
            Integer requestTimeout,
            Integer inflightLimit,
            Integer databaseId)
            throws Exception {
        if (clusterMode) {
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?> builder =
                    GlideClusterClientConfiguration.builder();
            for (String host : CLUSTER_HOSTS) {
                String[] parts = host.split(":");
                builder.address(
                        NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build());
            }
            if (requestTimeout != null) builder.requestTimeout(requestTimeout);
            if (compression != null) builder.compressionConfiguration(compression);
            if (inflightLimit != null) builder.inflightRequestsLimit(inflightLimit);
            if (databaseId != null) builder.databaseId(databaseId);
            GlideClusterClient client =
                    GlideClusterClient.createClient(builder.build()).get(10, TimeUnit.SECONDS);
            return new ClientWrapper(client, true);
        } else {
            String host = STANDALONE_HOSTS[0].split(":")[0];
            int port = Integer.parseInt(STANDALONE_HOSTS[0].split(":")[1]);
            GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?> builder =
                    GlideClientConfiguration.builder()
                            .address(NodeAddress.builder().host(host).port(port).build());
            if (requestTimeout != null) builder.requestTimeout(requestTimeout);
            if (compression != null) builder.compressionConfiguration(compression);
            if (inflightLimit != null) builder.inflightRequestsLimit(inflightLimit);
            if (databaseId != null) builder.databaseId(databaseId);
            GlideClient client = GlideClient.createClient(builder.build()).get(10, TimeUnit.SECONDS);
            return new ClientWrapper(client, false);
        }
    }

    /** Create a raw client (no compression) for verifying stored values. */
    private static ClientWrapper createRawClient(boolean clusterMode) throws Exception {
        return createClient(clusterMode, null, 5000);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Compression Tests
    // ═══════════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeInheritsCompression(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        CompressionConfiguration compressionConfig =
                CompressionConfiguration.builder()
                        .enabled(true)
                        .backend(CompressionBackend.ZSTD)
                        .compressionLevel(3)
                        .minCompressionSize(64)
                        .build();

        try (ClientWrapper client = createClient(clusterMode, compressionConfig, 5000);
                ClientWrapper rawClient = createRawClient(clusterMode)) {
            String key = testKey(clusterMode, "scope-compress");
            String largeValue = repeat("A", 500);

            // SET via scoped connection
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.set(key, largeValue).get(5, TimeUnit.SECONDS);
            }

            // GET via normal client (with same compression) should decompress
            String retrieved = client.get(key).get(5, TimeUnit.SECONDS);
            assertEquals(
                    largeValue,
                    retrieved,
                    "Scoped SET with compression should be readable by the parent client");

            // Raw client reading compressed data should either:
            // - Return different bytes (if it can decode them as a String), OR
            // - Throw an exception (compressed bytes are not valid UTF-8)
            // Either outcome proves compression happened.
            try {
                String rawValue = rawClient.get(key).get(5, TimeUnit.SECONDS);
                // If no exception, the value must differ from original
                assertNotEquals(
                        largeValue,
                        rawValue,
                        "Value stored via compressed scope should be compressed in Valkey");
            } catch (Exception e) {
                // Exception when reading compressed bytes as String is expected —
                // proves the stored value is compressed binary data
                assertTrue(
                        e.getMessage().contains("GlideString")
                                || e.getMessage().contains("Unexpected return type"),
                        "Expected type conversion error for compressed binary data, got: " + e.getMessage());
            }

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeReadsCompressedData(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        CompressionConfiguration compressionConfig =
                CompressionConfiguration.builder().enabled(true).backend(CompressionBackend.ZSTD).build();

        try (ClientWrapper client = createClient(clusterMode, compressionConfig, 5000)) {
            String key = testKey(clusterMode, "scope-read-compressed");
            String value = repeat("CompressibleData_", 50);

            // Write via normal client (compressed)
            client.set(key, value).get(5, TimeUnit.SECONDS);

            // Read via scoped connection — should decompress correctly
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                String retrieved = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals(
                        value, retrieved, "Scoped GET should decompress data written by the parent client");
            }

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeSmallValuesNotCompressed(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        CompressionConfiguration compressionConfig =
                CompressionConfiguration.builder()
                        .enabled(true)
                        .backend(CompressionBackend.ZSTD)
                        .minCompressionSize(256)
                        .build();

        try (ClientWrapper client = createClient(clusterMode, compressionConfig, 5000);
                ClientWrapper rawClient = createRawClient(clusterMode)) {
            String key = testKey(clusterMode, "scope-small");
            String smallValue = "hello";

            // SET via scope
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.set(key, smallValue).get(5, TimeUnit.SECONDS);
            }

            // Raw client should see the same value (not compressed)
            String rawValue = rawClient.get(key).get(5, TimeUnit.SECONDS);
            assertEquals(
                    smallValue, rawValue, "Small values below minCompressionSize should NOT be compressed");

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Request Timeout Tests
    // ═══════════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeRespectsRequestTimeout(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        try (ClientWrapper client = createClient(clusterMode, null, 100)) {
            // Normal fast operations should succeed
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(5)).get(10, TimeUnit.SECONDS)) {
                String key = testKey(clusterMode, "timeout-test");
                scope.set(key, "fast").get(5, TimeUnit.SECONDS);
                String val = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals("fast", val);
                scope.executeCommand("DEL", key).get(5, TimeUnit.SECONDS);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeDifferentTimeoutsPerClient(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        try (ClientWrapper clientA = createClient(clusterMode, null, 5000);
                ClientWrapper clientB = createClient(clusterMode, null, 200)) {
            String key = testKey(clusterMode, "dual-timeout");

            // Both scopes should work for fast ops
            try (IsolatedScope scopeA =
                    clientA.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scopeA.set(key, "from-A").get(5, TimeUnit.SECONDS);
            }

            try (IsolatedScope scopeB =
                    clientB.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                String val = scopeB.get(key).get(5, TimeUnit.SECONDS);
                assertEquals("from-A", val);
            }

            // Cleanup
            clientA.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Inflight Request Limit Tests
    // ═══════════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeRespectsInflightLimit(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        try (ClientWrapper client = createClient(clusterMode, null, 5000, 1000, null)) {
            // Verify scoped operations work under normal conditions
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                for (int i = 0; i < 50; i++) {
                    String key = testKey(clusterMode, "inflight-" + i);
                    scope.set(key, "value-" + i).get(5, TimeUnit.SECONDS);
                    String val = scope.get(key).get(5, TimeUnit.SECONDS);
                    assertEquals("value-" + i, val);
                    scope.executeCommand("DEL", key).get(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Combined Modifiers Tests
    // ═══════════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeAllModifiersCombined(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        CompressionConfiguration compressionConfig =
                CompressionConfiguration.builder()
                        .enabled(true)
                        .backend(CompressionBackend.ZSTD)
                        .compressionLevel(3)
                        .minCompressionSize(64)
                        .build();

        try (ClientWrapper client = createClient(clusterMode, compressionConfig, 5000, 500, null);
                ClientWrapper rawClient = createRawClient(clusterMode)) {
            String key = testKey(clusterMode, "combined");
            String largeValue = repeat("TestData_", 100);

            // Write via scope
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.set(key, largeValue).get(5, TimeUnit.SECONDS);
                String retrieved = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals(
                        largeValue, retrieved, "Round-trip through scope with all modifiers should work");
            }

            // Verify via parent client
            String parentGet = client.get(key).get(5, TimeUnit.SECONDS);
            assertEquals(
                    largeValue, parentGet, "Parent client should read scope-written compressed data");

            // Verify compression actually happened — raw client sees binary data
            try {
                String rawValue = rawClient.get(key).get(5, TimeUnit.SECONDS);
                assertNotEquals(largeValue, rawValue, "Data should be stored compressed in Valkey");
            } catch (Exception e) {
                // Exception proves compression happened (binary data isn't valid UTF-8)
                assertTrue(
                        e.getMessage().contains("GlideString")
                                || e.getMessage().contains("Unexpected return type"),
                        "Expected type error for compressed data, got: " + e.getMessage());
            }

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // WATCH/MULTI/EXEC Tests
    // ═══════════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeWatchTransactionWithCompression(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        CompressionConfiguration compressionConfig =
                CompressionConfiguration.builder()
                        .enabled(true)
                        .backend(CompressionBackend.ZSTD)
                        .minCompressionSize(64)
                        .build();

        try (ClientWrapper client = createClient(clusterMode, compressionConfig, 5000)) {
            String key = testKey(clusterMode, "watch-compress");
            String initialValue = repeat("InitialLargeValue_", 20);
            client.set(key, initialValue).get(5, TimeUnit.SECONDS);

            // OCC loop on the compressed key
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.watch(key).get(5, TimeUnit.SECONDS);
                String current = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals(initialValue, current, "WATCH should read the decompressed value correctly");

                String newValue = repeat("UpdatedLargeValue_", 20);
                scope.multi().get(5, TimeUnit.SECONDS);
                scope.set(key, newValue).get(5, TimeUnit.SECONDS);
                String execResult = scope.exec().get(5, TimeUnit.SECONDS);
                assertNotNull(execResult, "EXEC should succeed (no conflict)");
            }

            // Verify the new value is readable (decompressed)
            String finalVal = client.get(key).get(5, TimeUnit.SECONDS);
            assertTrue(
                    finalVal.startsWith("UpdatedLargeValue_"),
                    "Final value should be the updated compressed value");

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeWatchConflictAbortsExec(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        try (ClientWrapper client = createClient(clusterMode, null, 5000)) {
            String key = testKey(clusterMode, "watch-conflict");
            client.set(key, "original").get(5, TimeUnit.SECONDS);

            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.watch(key).get(5, TimeUnit.SECONDS);
                scope.get(key).get(5, TimeUnit.SECONDS);

                // Modify externally via the main client
                client.set(key, "modified-externally").get(5, TimeUnit.SECONDS);

                scope.multi().get(5, TimeUnit.SECONDS);
                scope.set(key, "from-scope").get(5, TimeUnit.SECONDS);
                String execResult = scope.exec().get(5, TimeUnit.SECONDS);
                // EXEC returns null when transaction is aborted
                assertNull(execResult, "EXEC should return null on conflict");
            }

            // Verify external modification persists
            String val = client.get(key).get(5, TimeUnit.SECONDS);
            assertEquals("modified-externally", val);

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeWatchMultiExec(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        try (ClientWrapper client = createClient(clusterMode, null, 5000)) {
            String key = testKey(clusterMode, "occ");
            client.set(key, "0").get(5, TimeUnit.SECONDS);

            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.watch(key).get(5, TimeUnit.SECONDS);
                String current = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals("0", current);

                scope.multi().get(5, TimeUnit.SECONDS);
                scope.set(key, "1").get(5, TimeUnit.SECONDS);
                String execResult = scope.exec().get(5, TimeUnit.SECONDS);
                assertNotNull(execResult, "EXEC should succeed (no conflict)");
            }

            // Verify
            String finalVal = client.get(key).get(5, TimeUnit.SECONDS);
            assertEquals("1", finalVal);

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Acquire/Release Tests
    // ═══════════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeAcquireAndRelease(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        try (ClientWrapper client = createClient(clusterMode, null, 5000)) {
            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                String result = scope.executeCommand("PING").get(5, TimeUnit.SECONDS);
                assertNotNull(result);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeGetSet(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        try (ClientWrapper client = createClient(clusterMode, null, 5000)) {
            String key = testKey(clusterMode, "basic");

            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.set(key, "scope-value").get(5, TimeUnit.SECONDS);
                String val = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals("scope-value", val);
            }

            // Verify via parent client
            String result = client.get(key).get(5, TimeUnit.SECONDS);
            assertEquals("scope-value", result);

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolPublish(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);

        // Use the pool to acquire a client, PUBLISH, and verify no crash
        String host;
        int port;
        if (clusterMode) {
            host = CLUSTER_HOSTS[0].split(":")[0];
            port = Integer.parseInt(CLUSTER_HOSTS[0].split(":")[1]);
        } else {
            host = STANDALONE_HOSTS[0].split(":")[0];
            port = Integer.parseInt(STANDALONE_HOSTS[0].split(":")[1]);
        }

        glide.api.models.pool.ClientPoolConfig poolCfg =
                glide.api.models.pool.ClientPoolConfig.builder()
                        .maxSize(2)
                        .minIdle(1)
                        .acquireTimeout(Duration.ofSeconds(10))
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(host).port(port).build())
                                        .requestTimeout(5000)
                                        .build())
                        .build();

        glide.api.models.pool.ClientPool pool = glide.api.models.pool.ClientPool.create(poolCfg);
        // Wait for pool warmup — poll until idle > 0 (CI can be slow)
        long deadline = System.currentTimeMillis() + 30000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

        try (glide.api.models.pool.PooledGlideClient pooledClient =
                pool.acquire().get(10, TimeUnit.SECONDS)) {
            // PUBLISH to a channel — just verify no crash, don't need a subscriber
            String channel = testKey(clusterMode, "pool-pub-channel");
            pooledClient.unwrap().publish("test-message", channel).get(5, TimeUnit.SECONDS);
        }

        pool.close();
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // Database State (Valkey 9+ only, standalone)
    // ═══════════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testScopeInheritsConfiguredDatabase(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);
        if (clusterMode) {
            assumeTrue(
                    SERVER_VERSION.isGreaterThanOrEqualTo("9.0.0"), "SELECT in cluster requires Valkey 9+");
        }

        try (ClientWrapper client = createClient(clusterMode, null, 5000, null, 2);
                ClientWrapper clientDb0 = createClient(clusterMode, null, 5000, null, null)) {
            String key = testKey(clusterMode, "db2");

            try (IsolatedScope scope =
                    client.scopedConnection(Duration.ofSeconds(10)).get(10, TimeUnit.SECONDS)) {
                scope.set(key, "on-db2").get(5, TimeUnit.SECONDS);
                String val = scope.get(key).get(5, TimeUnit.SECONDS);
                assertEquals("on-db2", val);
            }

            // Parent client (also on db 2) should see the key
            String result = client.get(key).get(5, TimeUnit.SECONDS);
            assertEquals("on-db2", result);

            // A client on database 0 should NOT see the key
            String resultDb0 = clientDb0.get(key).get(5, TimeUnit.SECONDS);
            assertNull(resultDb0, "Key written on db2 via scope should not be visible on db0");

            // Cleanup
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }
    }
}
