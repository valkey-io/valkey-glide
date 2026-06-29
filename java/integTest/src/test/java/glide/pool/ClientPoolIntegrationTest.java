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
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests for Feature 1: Client-Instance Pooling. Requires a Valkey server (uses test
 * infrastructure endpoints).
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolCreateAcquireRelease(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        long deadline = System.currentTimeMillis() + 30000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

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
        long deadline = System.currentTimeMillis() + 30000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

        glide.api.models.pool.PooledGlideClient c1 = pool.acquire().get(10, TimeUnit.SECONDS);
        long id1 = c1.getClientId();
        c1.close(); // returns to pool
        Thread.sleep(100);

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
        long deadline = System.currentTimeMillis() + 30000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

        assertTrue(pool.getIdleCount() >= 1);
        assertEquals(0, pool.getActiveCount());

        long clientId = pool.acquire().get(10, TimeUnit.SECONDS).getClientId();
        // After acquire: idle decreases, active increases.
        pool.release(clientId);

        // Poll until async release completes (DISCARD + SELECT reset)
        long releaseDeadline = System.currentTimeMillis() + 5000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < releaseDeadline) {
            Thread.sleep(50);
        }

        assertTrue(pool.getIdleCount() >= 1, "After release, idle should be >= 1");
        pool.close();
        System.out.println("testPoolMetrics PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolCloseRejectsAcquire(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        long deadline = System.currentTimeMillis() + 30000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

        pool.close();

        assertThrows(Exception.class, () -> pool.acquire().get(2, TimeUnit.SECONDS));
        System.out.println("testPoolCloseRejectsAcquire PASSED (cluster=" + clusterMode + ")");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPoolConcurrentAccess(boolean clusterMode) throws Exception {
        ClientPool pool = ClientPool.create(poolConfig(clusterMode));
        long deadline = System.currentTimeMillis() + 30000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

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
        long deadline = System.currentTimeMillis() + 30000;
        while (pool.getIdleCount() < 1 && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }

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
}
