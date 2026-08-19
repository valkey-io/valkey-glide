/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.pool;

import static glide.TestConfiguration.CLUSTER_HOSTS;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.pool.ClientPool;
import glide.api.models.pool.ClientPoolConfig;
import glide.api.models.pool.PooledGlideClient;
import glide.api.models.scope.IsolatedScope;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Disabled;
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

    // Shared by the PoolAndScope cases.

    /** A cluster-mode pool that is ready to hand out clients. */
    private ClientPool clusterPool() throws InterruptedException {
        ClientPool pool = ClientPool.create(poolConfig(true));
        waitForPoolReady(pool, 1);
        return pool;
    }

    /** Open a scope on a pooled client, pinned to the slot of {@code routingKey}. */
    private static IsolatedScope scopeOn(PooledGlideClient client, String routingKey)
            throws Exception {
        return client
                .unwrap()
                .scopedConnection(Duration.ofSeconds(10), routingKey)
                .get(10, TimeUnit.SECONDS);
    }

    /**
     * A cluster key under a hash tag other than the one {@link #testKey} uses, so it lands in a
     * different slot.
     */
    private static String otherSlotKey(String prefix) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return "{pool-test-other}-" + prefix + "-" + id;
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
     * A scope opened on a pooled client commits a transaction on one slot, then refuses a key from
     * another slot.
     */
    @Test
    @Disabled(
            "Pooled clients cannot open scopes: https://github.com/valkey-io/valkey-glide/issues/6764")
    public void testPoolAndScopeCluster() throws Exception {
        assumeMode(true);
        ClientPool pool = clusterPool();
        String key = testKey(true, "pool-scope");
        String foreignKey = otherSlotKey("pool-scope");

        try (PooledGlideClient client = pool.acquire().get(10, TimeUnit.SECONDS)) {
            client.set(key, "10").get(5, TimeUnit.SECONDS);

            try (IsolatedScope scope = scopeOn(client, key)) {
                assertEquals("OK", scope.watch(key).get(5, TimeUnit.SECONDS));
                assertEquals("10", scope.get(key).get(5, TimeUnit.SECONDS));
                assertEquals("OK", scope.multi().get(5, TimeUnit.SECONDS));
                scope.set(key, "11").get(5, TimeUnit.SECONDS);
                String exec = scope.exec().get(5, TimeUnit.SECONDS);
                assertNotNull(exec, "EXEC should commit, nobody touched the key");

                // The scope is pinned to this key's slot, so another slot's key must be rejected.
                ExecutionException ex =
                        assertThrows(
                                ExecutionException.class, () -> scope.get(foreignKey).get(5, TimeUnit.SECONDS));
                assertTrue(
                        ex.getMessage().toLowerCase().contains("crossslot"),
                        "Expected a cross-slot error, got: " + ex.getMessage());
            } // scope released here, before the client goes back to the pool

            assertEquals("11", client.get(key).get(5, TimeUnit.SECONDS));
            client.del(new String[] {key}).get(5, TimeUnit.SECONDS);
        }

        pool.close();
        System.out.println("testPoolAndScopeCluster PASSED");
    }
}
