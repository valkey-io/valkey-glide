/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.pool;

import static glide.TestConfiguration.CLUSTER_HOSTS;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static glide.api.models.configuration.RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_NODES;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.pool.ClientPool;
import glide.api.models.pool.ClientPoolConfig;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for Client-Instance Pooling. Requires a Valkey server (uses test infrastructure
 * endpoints).
 *
 * <p>Tests are parameterized over cluster mode (true/false) to ensure both standalone and cluster
 * deployments behave identically.
 */
public class ClientPoolIntegrationTest {

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
        return clusterMode ? "{pool-test}-" + prefix + "-" + id : prefix + "-" + id;
    }

    private ClientPoolConfig poolConfig(boolean clusterMode) {
        assumeMode(clusterMode);
        if (clusterMode) {
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?> builder =
                    GlideClusterClientConfiguration.builder();
            for (String host : CLUSTER_HOSTS) {
                String[] parts = host.split(":");
                builder.address(
                        NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build());
            }
            builder.requestTimeout(5000);
            return ClientPoolConfig.builder()
                    .maxSize(3)
                    .minIdle(1)
                    .acquireTimeout(Duration.ofSeconds(10))
                    .clientConfig(builder.build())
                    .build();
        } else {
            String[] parts = STANDALONE_HOSTS[0].split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            return ClientPoolConfig.builder()
                    .maxSize(3)
                    .minIdle(1)
                    .acquireTimeout(Duration.ofSeconds(10))
                    .clientConfig(
                            GlideClientConfiguration.builder()
                                    .address(NodeAddress.builder().host(host).port(port).build())
                                    .requestTimeout(5000)
                                    .build())
                    .build();
        }
    }

    /** Poll until pool has at least minIdle clients ready. */
    private static void waitForPoolReady(ClientPool pool, int minIdle) throws InterruptedException {
        waitForPoolReady(pool, minIdle, 30000);
    }

    private static void waitForPoolReady(ClientPool pool, int minIdle, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (pool.getIdleCount() < minIdle && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
    }

    private ClientPoolConfig clientInfoPoolConfig(
            boolean clusterMode, String libName, String clientInfoTag, String clientName) {
        assumeMode(clusterMode);

        glide.api.models.configuration.BaseClientConfiguration clientConfig;
        if (clusterMode) {
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?> builder =
                    GlideClusterClientConfiguration.builder();
            for (String host : CLUSTER_HOSTS) {
                String[] parts = host.split(":");
                builder.address(
                        NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build());
            }
            builder.requestTimeout(5000).clientName(clientName);
            if (libName != null) {
                builder.libName(libName);
            }
            if (clientInfoTag != null) {
                builder.clientInfoTag(clientInfoTag);
            }
            clientConfig = builder.build();
        } else {
            String[] parts = STANDALONE_HOSTS[0].split(":");
            GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?> builder =
                    GlideClientConfiguration.builder()
                            .address(
                                    NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build())
                            .requestTimeout(5000)
                            .clientName(clientName);
            if (libName != null) {
                builder.libName(libName);
            }
            if (clientInfoTag != null) {
                builder.clientInfoTag(clientInfoTag);
            }
            clientConfig = builder.build();
        }

        return ClientPoolConfig.builder()
                .maxSize(1)
                .minIdle(1)
                .acquireTimeout(Duration.ofSeconds(10))
                .clientConfig(clientConfig)
                .build();
    }

    private void assertPooledClientLibName(
            boolean clusterMode, String libName, String clientInfoTag, String expectedLibName)
            throws Exception {
        String minVersion = "7.2.0";
        assumeTrue(
                glide.TestConfiguration.SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        String clientName = "pool-lib-name-" + UUID.randomUUID();
        try (ClientPool pool =
                ClientPool.create(clientInfoPoolConfig(clusterMode, libName, clientInfoTag, clientName))) {
            waitForPoolReady(pool, 1);
            assertTrue(pool.getIdleCount() >= 1, "Should have at least 1 idle client");

            try (glide.api.models.pool.PooledGlideClient client =
                    pool.acquire().get(10, TimeUnit.SECONDS)) {
                if (clusterMode) {
                    assertClusterPoolConnectionsLibName(clientName, expectedLibName);
                } else {
                    Object response =
                            client
                                    .unwrap()
                                    .customCommand(new String[] {"CLIENT", "INFO"})
                                    .get(5, TimeUnit.SECONDS);
                    assertInstanceOf(String.class, response);
                    String clientInfo = (String) response;
                    assertTrue(
                            hasClientInfoField(clientInfo, "lib-name", expectedLibName),
                            () ->
                                    "Expected pooled client lib-name="
                                            + expectedLibName
                                            + ", but CLIENT INFO returned: "
                                            + clientInfo);
                }
            }
        }
    }

    private void assertClusterPoolConnectionsLibName(String clientName, String expectedLibName)
            throws Exception {
        try (GlideClusterClient observer =
                GlideClusterClient.createClient(glide.TestUtilities.commonClusterClientConfig().build())
                        .get(10, TimeUnit.SECONDS)) {
            ClusterValue<Object> clientLists =
                    observer
                            .customCommand(new String[] {"CLIENT", "LIST"}, ALL_NODES)
                            .get(5, TimeUnit.SECONDS);
            assertTrue(clientLists.hasMultiData(), "Expected CLIENT LIST responses from all nodes");

            int totalPoolConnections = 0;
            for (Map.Entry<String, Object> node : clientLists.getMultiValue().entrySet()) {
                int nodePoolConnections = 0;
                for (String clientInfo : ((String) node.getValue()).split("\\R")) {
                    if (!hasClientInfoField(clientInfo, "name", clientName)) {
                        continue;
                    }
                    nodePoolConnections++;
                    assertTrue(
                            hasClientInfoField(clientInfo, "lib-name", expectedLibName),
                            () ->
                                    "Expected pooled connection on "
                                            + node.getKey()
                                            + " to report lib-name="
                                            + expectedLibName
                                            + ", but CLIENT LIST returned: "
                                            + clientInfo);
                }
                assertTrue(
                        nodePoolConnections > 0,
                        () -> "Expected a pooled connection on cluster node " + node.getKey());
                totalPoolConnections += nodePoolConnections;
            }
            assertTrue(totalPoolConnections > 0, "Expected at least one pooled cluster connection");
        }
    }

    private static boolean hasClientInfoField(
            String clientInfo, String fieldName, String expectedValue) {
        String expectedField = fieldName + "=" + expectedValue;
        for (String field : clientInfo.trim().split("\\s+")) {
            if (field.equals(expectedField)) {
                return true;
            }
        }
        return false;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPooledClientReportsDefaultLibraryName(boolean clusterMode) throws Exception {
        assertPooledClientLibName(clusterMode, null, null, "GlideJava");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPooledClientReportsConfiguredLibraryName(boolean clusterMode) throws Exception {
        assertPooledClientLibName(clusterMode, "custom-client", null, "custom-client");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPooledClientReportsClientInfoTag(boolean clusterMode) throws Exception {
        assertPooledClientLibName(clusterMode, null, "framework:1.2", "GlideJava(framework:1.2)");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPooledClientReportsCombinedLibraryMetadata(boolean clusterMode) throws Exception {
        assertPooledClientLibName(
                clusterMode, "custom-client", "framework:1.2", "custom-client(framework:1.2)");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolCreateAcquireRelease(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        waitForPoolReady(pool, 1);

        assertTrue(pool.getIdleCount() >= 1, "Should have at least 1 idle client");

        // acquire() returns PooledGlideClient — try-with-resources returns to pool
        try (glide.api.models.pool.PooledGlideClient client =
                pool.acquire().get(10, TimeUnit.SECONDS)) {
            assertNotNull(client);
            assertTrue(client.getClientId() > 0, "client_id should be positive");

            String key = testKey(clusterMode, "acquire-release");
            client.set(key, "hello").get(5, TimeUnit.SECONDS);
            assertEquals("hello", client.get(key).get(5, TimeUnit.SECONDS));
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        } // auto-released back to pool

        pool.close();
        System.out.println("testPoolCreateAcquireRelease PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolReuse(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        waitForPoolReady(pool, 1);

        glide.api.models.pool.PooledGlideClient c1 = pool.acquire().get(10, TimeUnit.SECONDS);
        long id1 = c1.getClientId();
        c1.close(); // returns to pool
        Thread.sleep(50);

        glide.api.models.pool.PooledGlideClient c2 = pool.acquire().get(10, TimeUnit.SECONDS);
        long id2 = c2.getClientId();
        assertEquals(id1, id2, "LIFO: same client_id returned after release");
        c2.close();

        pool.close();
        System.out.println("testPoolReuse PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolMetrics(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        waitForPoolReady(pool, 1);

        assertTrue(pool.getIdleCount() >= 1);
        assertEquals(0, pool.getActiveCount());

        long clientId = pool.acquire().get(10, TimeUnit.SECONDS).getClientId();
        // After acquire: idle decreases, active increases.
        pool.release(clientId);

        // Poll until async release completes (DISCARD + SELECT reset)
        waitForPoolReady(pool, 1, 5000);

        assertTrue(pool.getIdleCount() >= 1, "After release, idle should be >= 1");
        pool.close();
        System.out.println("testPoolMetrics PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolCloseRejectsAcquire(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        waitForPoolReady(pool, 1);

        pool.close();

        assertThrows(Exception.class, () -> pool.acquire().get(2, TimeUnit.SECONDS));
        System.out.println("testPoolCloseRejectsAcquire PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolConcurrentAccess(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        waitForPoolReady(pool, 1);

        int numThreads = 4;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(numThreads);
        java.util.concurrent.atomic.AtomicInteger successCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger errorCount =
                new java.util.concurrent.atomic.AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            final int threadIdx = t;
            new Thread(
                            () -> {
                                try (glide.api.models.pool.PooledGlideClient client =
                                        pool.acquire().get(15, TimeUnit.SECONDS)) {
                                    String key = testKey(clusterMode, "concurrent-" + threadIdx);
                                    client.set(key, "thread-" + threadIdx).get(5, TimeUnit.SECONDS);
                                    String val = client.get(key).get(5, TimeUnit.SECONDS);
                                    assertEquals("thread-" + threadIdx, val);
                                    client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
                                    successCount.incrementAndGet();
                                } catch (Exception e) {
                                    System.err.println("Thread " + threadIdx + " error: " + e);
                                    errorCount.incrementAndGet();
                                } finally {
                                    latch.countDown();
                                }
                            })
                    .start();
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All threads should finish");
        assertEquals(
                numThreads, successCount.get(), "All threads should succeed. Errors: " + errorCount.get());

        pool.close();
        System.out.println("testPoolConcurrentAccess PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolTimeoutOnExhaustion(boolean clusterMode) throws Exception {
        assumeMode(clusterMode);
        ClientPoolConfig exhaustConfig;
        if (clusterMode) {
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder<?, ?> builder =
                    GlideClusterClientConfiguration.builder();
            for (String host : CLUSTER_HOSTS) {
                String[] parts = host.split(":");
                builder.address(
                        NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build());
            }
            builder.requestTimeout(5000);
            exhaustConfig =
                    ClientPoolConfig.builder()
                            .maxSize(1)
                            .minIdle(1)
                            .acquireTimeout(Duration.ofSeconds(10))
                            .clientConfig(builder.build())
                            .build();
        } else {
            exhaustConfig =
                    ClientPoolConfig.builder()
                            .maxSize(1)
                            .minIdle(1)
                            .acquireTimeout(Duration.ofSeconds(10))
                            .clientConfig(
                                    GlideClientConfiguration.builder()
                                            .address(
                                                    NodeAddress.builder()
                                                            .host(STANDALONE_HOSTS[0].split(":")[0])
                                                            .port(Integer.parseInt(STANDALONE_HOSTS[0].split(":")[1]))
                                                            .build())
                                            .requestTimeout(5000)
                                            .build())
                            .build();
        }

        ClientPool pool = ClientPool.create(exhaustConfig);
        waitForPoolReady(pool, 1);

        // Acquire the only client
        glide.api.models.pool.PooledGlideClient held = pool.acquire().get(10, TimeUnit.SECONDS);

        // Second acquire should time out (short timeout)
        try {
            pool.acquire(Duration.ofMillis(500)).get(2, TimeUnit.SECONDS);
            fail("Should have thrown TimeoutException");
        } catch (java.util.concurrent.ExecutionException e) {
            assertTrue(
                    e.getCause() instanceof java.util.concurrent.TimeoutException
                            || e.getCause().getMessage().contains("exhausted"),
                    "Expected timeout, got: " + e.getCause());
        }

        held.close();
        pool.close();
        System.out.println("testPoolTimeoutOnExhaustion PASSED (cluster=" + clusterMode + ")");
    }

    @org.junit.jupiter.api.Test
    public void testPoolRejectsPubsubConfig() {
        // Standalone config with pubsub subscription should be rejected
        glide.api.models.configuration.StandaloneSubscriptionConfiguration subConfig =
                glide.api.models.configuration.StandaloneSubscriptionConfiguration.builder()
                        .subscription(
                                glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode
                                        .EXACT,
                                glide.api.models.GlideString.gs("test-channel"))
                        .build();

        assumeTrue(standaloneAvailable(), "No standalone endpoints configured");
        String[] parts = STANDALONE_HOSTS[0].split(":");

        GlideClientConfiguration clientConfig =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(parts[0]).port(Integer.parseInt(parts[1])).build())
                        .requestTimeout(5000)
                        .subscriptionConfiguration(subConfig)
                        .build();

        ClientPoolConfig poolCfg =
                ClientPoolConfig.builder().maxSize(2).minIdle(1).clientConfig(clientConfig).build();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> ClientPool.create(poolCfg));
        assertTrue(
                ex.getMessage().contains("pubsub"), "Error should mention pubsub: " + ex.getMessage());
        System.out.println("testPoolRejectsPubsubConfig PASSED");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolPublishConcurrent(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        waitForPoolReady(pool, 1);

        int numThreads = 4;
        int messagesPerThread = 5;
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int t = 0; t < numThreads; t++) {
            final int threadIdx = t;
            new Thread(
                            () -> {
                                try (glide.api.models.pool.PooledGlideClient client =
                                        pool.acquire().get(15, TimeUnit.SECONDS)) {
                                    String channel = testKey(clusterMode, "pub-chan-" + threadIdx);
                                    for (int m = 0; m < messagesPerThread; m++) {
                                        client
                                                .unwrap()
                                                .customCommand(new String[] {"PUBLISH", channel, "msg-" + m})
                                                .get(5, TimeUnit.SECONDS);
                                    }
                                    successCount.incrementAndGet();
                                } catch (Exception e) {
                                    System.err.println("Publish thread " + threadIdx + " error: " + e);
                                    errorCount.incrementAndGet();
                                } finally {
                                    latch.countDown();
                                }
                            })
                    .start();
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS), "All publish threads should finish");
        assertEquals(
                numThreads,
                successCount.get(),
                "All publish threads should succeed. Errors: " + errorCount.get());

        pool.close();
        System.out.println("testPoolPublishConcurrent PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolBlockingCmdIsolation(boolean clusterMode) throws Exception {
        // This test only applies to standalone mode
        assumeTrue(!clusterMode, "Blocking command isolation test is standalone-only");
        assumeMode(clusterMode);

        String[] parts = STANDALONE_HOSTS[0].split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        ClientPoolConfig blockingPoolConfig =
                ClientPoolConfig.builder()
                        .maxSize(2)
                        .minIdle(2)
                        .acquireTimeout(Duration.ofSeconds(10))
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(host).port(port).build())
                                        .requestTimeout(5000)
                                        .build())
                        .build();

        ClientPool pool = ClientPool.create(blockingPoolConfig);
        waitForPoolReady(pool, 2);
        assertTrue(pool.getIdleCount() >= 2, "Should have at least 2 idle clients");

        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch bothDone = new CountDownLatch(2);
        AtomicLong thread2DurationMs = new AtomicLong(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        String blockingKey = testKey(clusterMode, "blpop-isolation");

        // Thread 1: runs BLPOP with long timeout (will be unblocked by LPUSH from thread 2)
        new Thread(
                        () -> {
                            try (glide.api.models.pool.PooledGlideClient client =
                                    pool.acquire().get(10, TimeUnit.SECONDS)) {
                                bothReady.countDown();
                                bothReady.await(10, TimeUnit.SECONDS);
                                // BLPOP key — will be unblocked by LPUSH
                                client
                                        .unwrap()
                                        .customCommand(new String[] {"BLPOP", blockingKey, "30"})
                                        .get(35, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                System.err.println("Thread 1 (BLPOP) error: " + e);
                                errorCount.incrementAndGet();
                            } finally {
                                bothDone.countDown();
                            }
                        })
                .start();

        // Thread 2: runs SET+GET and verifies it completes quickly, then unblocks BLPOP
        new Thread(
                        () -> {
                            try (glide.api.models.pool.PooledGlideClient client =
                                    pool.acquire().get(10, TimeUnit.SECONDS)) {
                                bothReady.countDown();
                                bothReady.await(10, TimeUnit.SECONDS);
                                long start = System.currentTimeMillis();
                                String key = testKey(clusterMode, "isolation-check");
                                client.set(key, "fast").get(5, TimeUnit.SECONDS);
                                String val = client.get(key).get(5, TimeUnit.SECONDS);
                                assertEquals("fast", val);
                                client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
                                thread2DurationMs.set(System.currentTimeMillis() - start);
                                // Unblock the BLPOP by pushing to its key
                                client
                                        .unwrap()
                                        .customCommand(new String[] {"LPUSH", blockingKey, "unblock"})
                                        .get(5, TimeUnit.SECONDS);
                            } catch (Exception e) {
                                System.err.println("Thread 2 (SET+GET) error: " + e);
                                errorCount.incrementAndGet();
                            } finally {
                                bothDone.countDown();
                            }
                        })
                .start();

        assertTrue(bothDone.await(10, TimeUnit.SECONDS), "Both threads should finish");
        assertEquals(0, errorCount.get(), "No errors expected");
        assertTrue(
                thread2DurationMs.get() < 1000,
                "SET+GET should complete in <1000ms but took " + thread2DurationMs.get() + "ms");

        pool.close();
        System.out.println("testPoolBlockingCmdIsolation PASSED (cluster=" + clusterMode + ")");
    }

    /**
     * Regression test for #6971: the abandon monitor must NOT reclaim a borrowed client that is
     * executing a blocking command (BLPOP).
     *
     * <p>This test exercises the full JNI dispatch path: {@code executeCommandAsync} → {@code
     * pre_blocking_arc} → {@code fetch_add} → {@code spawn} → {@code UnmarkOnDrop}. It verifies the
     * actual fix works end-to-end with a real Valkey server.
     */
    @Test
    public void testAbandonMonitorDoesNotReclaimBlockingClient() throws Exception {
        assumeTrue(standaloneAvailable(), "No standalone endpoints configured");

        String[] parts = STANDALONE_HOSTS[0].split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        // Pool with a very short abandon timeout (500 ms) and enough room for contention.
        ClientPoolConfig cfg =
                ClientPoolConfig.builder()
                        .maxSize(6)
                        .minIdle(1)
                        .acquireTimeout(Duration.ofSeconds(15))
                        .abandonTimeout(Duration.ofMillis(500))
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .address(NodeAddress.builder().host(host).port(port).build())
                                        .requestTimeout(35000)
                                        .build())
                        .build();

        ClientPool pool = ClientPool.create(cfg);
        waitForPoolReady(pool, 1);

        String blpopKey = testKey(false, "abandon-monitor-blpop");
        String blpopValue = "sentinel-" + UUID.randomUUID().toString().substring(0, 8);

        int numContention = 4;
        CountDownLatch contentionReady = new CountDownLatch(numContention);
        AtomicInteger stopFlag = new AtomicInteger(0);
        AtomicInteger contentionErrors = new AtomicInteger(0);
        CountDownLatch contentionDone = new CountDownLatch(numContention);

        // Contention threads: tight acquire → SET → release loops to keep the pool busy.
        for (int t = 0; t < numContention; t++) {
            final int idx = t;
            new Thread(
                            () -> {
                                try {
                                    contentionReady.countDown();
                                    while (stopFlag.get() == 0) {
                                        try (glide.api.models.pool.PooledGlideClient c =
                                                pool.acquire().get(5, TimeUnit.SECONDS)) {
                                            c.set("contention-key-" + idx, "val").get(3, TimeUnit.SECONDS);
                                        }
                                    }
                                } catch (Exception e) {
                                    if (stopFlag.get() == 0) {
                                        System.err.println("Contention thread " + idx + " error: " + e);
                                        contentionErrors.incrementAndGet();
                                    }
                                } finally {
                                    contentionDone.countDown();
                                }
                            },
                            "contention-" + idx)
                    .start();
        }

        // Wait until all contention threads are cycling.
        assertTrue(contentionReady.await(10, TimeUnit.SECONDS), "Contention threads should start");

        // Acquire a client for BLPOP and dispatch it WITHOUT awaiting (fire-and-forget future).
        glide.api.models.pool.PooledGlideClient blpopClient = pool.acquire().get(10, TimeUnit.SECONDS);
        long blpopClientId = blpopClient.getClientId();
        java.util.concurrent.CompletableFuture<Object> blpopFuture =
                blpopClient.unwrap().customCommand(new String[] {"BLPOP", blpopKey, "30"});

        try {
            // Sleep for 3× the abandon window (1500 ms). The monitor runs every ~500 ms and
            // should see the BLPOP client as "blocking" and leave it alone.
            Thread.sleep(1500);

            // Stop contention threads and wait for all releases.
            stopFlag.set(1);
            assertTrue(contentionDone.await(10, TimeUnit.SECONDS), "Contention threads should stop");

            // Core assertion: the BLPOP client must still be in the pool's active set.
            int active = pool.getActiveCount();
            assertEquals(
                    1, active, "Pool should have exactly 1 active client (the BLPOP holder); got " + active);

            // Unblock the BLPOP by pushing the sentinel value.
            try (glide.api.models.pool.PooledGlideClient helper =
                    pool.acquire().get(10, TimeUnit.SECONDS)) {
                helper
                        .unwrap()
                        .customCommand(new String[] {"LPUSH", blpopKey, blpopValue})
                        .get(5, TimeUnit.SECONDS);
            }

            // Verify BLPOP returned the correct key and value.
            Object[] blpopResult = (Object[]) blpopFuture.get(10, TimeUnit.SECONDS);
            assertNotNull(blpopResult, "BLPOP should return a non-null result");
            assertEquals(2, blpopResult.length, "BLPOP result should have [key, value]");
            assertEquals(blpopKey, blpopResult[0].toString(), "BLPOP key mismatch");
            assertEquals(blpopValue, blpopResult[1].toString(), "BLPOP value mismatch");

            System.out.println("testAbandonMonitorDoesNotReclaimBlockingClient PASSED");
        } finally {
            // Always release the BLPOP client back to the pool.
            blpopClient.close();
            pool.close();
        }
    }

    @Test
    public void testPoolBadAddressAcquireFails() throws Exception {
        // Pool with unreachable address: create should fail (probe connectivity check)
        ClientPoolConfig badConfig =
                ClientPoolConfig.builder()
                        .maxSize(1)
                        .minIdle(1)
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .address(NodeAddress.builder().host("192.0.2.1").port(1).build())
                                        .requestTimeout(2000)
                                        .build())
                        .build();

        assertThrows(RuntimeException.class, () -> ClientPool.create(badConfig));
    }

    /**
     * A pool-borrowed client must enforce the configured inflight limit on the Java side, like a
     * directly-created client. {@code ClientPool.getClient} previously hard-coded {@code
     * maxInflight=0} into {@code fromPoolHandle}, disabling the Java-side (AsyncRegistry) limiter for
     * pooled clients regardless of {@code inflightRequestsLimit}.
     *
     * <p>The test exercises the batch path deliberately. On the command path the JNI pre-check also
     * rejects an over-limit request with the same message, so a command-based test cannot tell the
     * Java limiter apart from the core one. The batch path has no such native pre-check and {@code
     * send_pipeline}/{@code send_transaction} never reserve a core inflight slot, so the Java-side
     * limiter is the only thing bounding concurrent batches: with it off, an over-limit batch stays
     * pending; with it on, it is rejected. That makes this a genuine A-B of the fix.
     */
    @Test
    public void testPooledClientHonorsInflightRequestsLimit() throws Exception {
        assumeTrue(standaloneAvailable(), "No standalone endpoints configured");
        int inflightRequestsLimit = 5;
        String[] parts = STANDALONE_HOSTS[0].split(":");
        ClientPoolConfig config =
                ClientPoolConfig.builder()
                        .maxSize(2)
                        .minIdle(1)
                        .acquireTimeout(Duration.ofSeconds(10))
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .address(
                                                NodeAddress.builder()
                                                        .host(parts[0])
                                                        .port(Integer.parseInt(parts[1]))
                                                        .build())
                                        .requestTimeout(5000)
                                        .inflightRequestsLimit(inflightRequestsLimit)
                                        .build())
                        .build();

        ClientPool pool = ClientPool.create(config);
        try {
            waitForPoolReady(pool, 1);
            glide.api.models.pool.PooledGlideClient pooled = pool.acquire().get(10, TimeUnit.SECONDS);
            glide.api.GlideClient borrowed = pooled.unwrap();

            String keyName = testKey(false, "inflight-batch-nonexist");

            // Saturate the Java limiter with non-atomic batches each holding a blocking pop that never
            // completes. The batch path has no native inflight pre-check, so only the Java-side
            // limiter bounds these -- unlike the command path where the JNI pre-check would reject
            // regardless of this fix.
            java.util.List<java.util.concurrent.CompletableFuture<Object[]>> responses =
                    new java.util.ArrayList<>();
            for (int i = 0; i < inflightRequestsLimit + 1; i++) {
                glide.api.models.Batch batch = new glide.api.models.Batch(false);
                batch.blpop(new String[] {keyName}, 0);
                responses.add(borrowed.exec(batch, false));
            }

            for (int i = 0; i < inflightRequestsLimit; i++) {
                assertFalse(responses.get(i).isDone(), "Batch " + i + " should still be pending");
            }

            // The (limit + 1)-th batch must be rejected by the Java-side limiter. On the batch path
            // the core does not reserve a slot, so the exact AsyncRegistry message is reachable only
            // when the Java limiter is armed -- an exact-match assertion tells the layers apart.
            try {
                responses.get(inflightRequestsLimit).get(1, TimeUnit.SECONDS);
                fail("Expected the (limit + 1)-th batch to be rejected by the inflight limiter");
            } catch (java.util.concurrent.ExecutionException e) {
                assertInstanceOf(glide.api.models.exceptions.RequestException.class, e.getCause());
                assertEquals("Client reached maximum inflight requests", e.getCause().getMessage());
            }

            // Unblock the pending pops so the borrowed client releases cleanly.
            try (glide.api.GlideClient cleanup =
                    GlideClient.createClient(
                                    GlideClientConfiguration.builder()
                                            .address(
                                                    NodeAddress.builder()
                                                            .host(parts[0])
                                                            .port(Integer.parseInt(parts[1]))
                                                            .build())
                                            .requestTimeout(5000)
                                            .build())
                            .get()) {
                for (int i = 0; i < inflightRequestsLimit; i++) {
                    cleanup.lpush(keyName, new String[] {"val"}).get();
                }
            }
            pooled.close();
        } finally {
            pool.close();
        }
    }

    /**
     * A pool-borrowed client must time out commands per its own client config. {@code
     * ClientPool.getClient} previously passed the pool's own cleanup {@code requestTimeout} (the
     * {@link ClientPoolConfig} default of 5s) into {@code fromPoolHandle} instead of the client
     * config's {@code requestTimeout}. Unlike the inflight limiter (whose JNI pre-check rejects
     * regardless), this half is genuinely A-B distinguishable: with the pool's cleanup timeout short
     * (500ms) and the client config's timeout long (5s), a ~2s command completes post-fix but times
     * out pre-fix.
     */
    @Test
    public void testPooledClientHonorsClientConfigRequestTimeout() throws Exception {
        assumeTrue(standaloneAvailable(), "No standalone endpoints configured");
        String[] parts = STANDALONE_HOSTS[0].split(":");
        ClientPoolConfig config =
                ClientPoolConfig.builder()
                        .maxSize(2)
                        .minIdle(1)
                        .acquireTimeout(Duration.ofSeconds(10))
                        // Pool's own cleanup timeout, short. Pre-fix this leaked onto the borrowed
                        // client and would time out the command below.
                        .requestTimeout(Duration.ofMillis(500))
                        .clientConfig(
                                GlideClientConfiguration.builder()
                                        .address(
                                                NodeAddress.builder()
                                                        .host(parts[0])
                                                        .port(Integer.parseInt(parts[1]))
                                                        .build())
                                        // Client's configured timeout, long. Post-fix the borrowed
                                        // client uses this, so the command completes.
                                        .requestTimeout(5000)
                                        .build())
                        .build();

        ClientPool pool = ClientPool.create(config);
        try {
            waitForPoolReady(pool, 1);
            glide.api.models.pool.PooledGlideClient pooled = pool.acquire().get(10, TimeUnit.SECONDS);
            glide.api.GlideClient borrowed = pooled.unwrap();

            // DEBUG SLEEP blocks the connection server-side ~2s but is NOT a blocking command, so
            // (unlike BLPOP) it is subject to the client's request timeout. Pre-fix the borrowed
            // client inherited the pool's 500ms cleanup timeout and this fails with a TimeoutException;
            // post-fix it uses the client config's 5s timeout and completes.
            Object result =
                    borrowed.customCommand(new String[] {"DEBUG", "SLEEP", "2"}).get(10, TimeUnit.SECONDS);
            assertEquals("OK", result, "DEBUG SLEEP should complete under the client-config timeout");

            pooled.close();
        } finally {
            pool.close();
        }
    }
}
