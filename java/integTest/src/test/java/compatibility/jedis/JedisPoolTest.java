/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.*;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.GlideJedisFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

/** Simplified JedisPool compatibility test that validates basic GLIDE JedisPool functionality. */
public class JedisPoolTest {

    // Server configuration - dynamically resolved from CI environment
    private static final String valkeyHost;
    private static final int valkeyPort;

    static {
        String[] standaloneHosts = STANDALONE_HOSTS;

        // Fail if standalone server configuration is not found in system properties
        if (standaloneHosts.length == 0 || standaloneHosts[0].trim().isEmpty()) {
            throw new IllegalStateException(
                    "Standalone server configuration not found in system properties. "
                            + "Please set 'test.server.standalone' system property with server address "
                            + "(e.g., -Dtest.server.standalone=localhost:6379)");
        }

        String firstHost = standaloneHosts[0].trim();
        String[] hostPort = firstHost.split(":");

        if (hostPort.length == 2) {
            try {
                valkeyHost = hostPort[0];
                valkeyPort = Integer.parseInt(hostPort[1]);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "Invalid port number in standalone server configuration: "
                                + firstHost
                                + ". "
                                + "Expected format: host:port (e.g., localhost:6379)",
                        e);
            }
        } else {
            throw new IllegalStateException(
                    "Invalid standalone server format: "
                            + firstHost
                            + ". "
                            + "Expected format: host:port (e.g., localhost:6379)");
        }
    }

    @Test
    void pool_creation() {
        // Test basic pool creation
        try (JedisPool pool = new JedisPool(valkeyHost, valkeyPort)) {
            assertNotNull(pool, "JedisPool should be created successfully");
            assertFalse(pool.isClosed(), "JedisPool should not be closed after creation");
        }
    }

    @Test
    void pool_basic_operations() {
        // Test basic pool operations
        try (JedisPool pool = new JedisPool(valkeyHost, valkeyPort)) {
            assertNotNull(pool, "Pool should be initialized");
            assertFalse(pool.isClosed(), "Pool should not be closed");

            // Test getting a resource
            try (Jedis jedis = pool.getResource()) {
                assertNotNull(jedis, "Should be able to get Jedis resource from pool");

                // Test basic Valkey operations
                String testKey = UUID.randomUUID().toString();
                String testValue = "test_value";

                String setResult = jedis.set(testKey, testValue);
                assertEquals("OK", setResult, "SET operation should succeed");

                String getValue = jedis.get(testKey);
                assertEquals(testValue, getValue, "GET should return the set value");

                // Cleanup
                jedis.del(testKey);
            }
        }
    }

    @Test
    void pool_with_timeout() {
        // Test pool with custom timeout using simple constructor
        try (JedisPool timeoutPool = new JedisPool(valkeyHost, valkeyPort, 5000)) {
            assertNotNull(timeoutPool, "Timeout pool should be created");

            try (Jedis jedis = timeoutPool.getResource()) {
                String testKey = UUID.randomUUID().toString();
                String testValue = "timeout_value";

                String setResult = jedis.set(testKey, testValue);
                assertEquals("OK", setResult, "SET with timeout pool should work");

                String getResult = jedis.get(testKey);
                assertEquals(testValue, getResult, "GET with timeout pool should work");

                // Cleanup
                jedis.del(testKey);
            }
        }
    }

    @Test
    void pool_with_authentication() {
        // Test pool with authentication using simple constructor
        String password = ""; // Empty password for test environment

        try (JedisPool authPool = new JedisPool(valkeyHost, valkeyPort, 2000, password)) {
            assertNotNull(authPool, "Authenticated pool should be created");

            try (Jedis jedis = authPool.getResource()) {
                String testKey = UUID.randomUUID().toString();
                String testValue = "auth_value";

                String setResult = jedis.set(testKey, testValue);
                assertEquals("OK", setResult, "SET with authenticated pool should work");

                String getResult = jedis.get(testKey);
                assertEquals(testValue, getResult, "GET with authenticated pool should work");

                // Cleanup
                jedis.del(testKey);
            }
        }
    }

    @Test
    void pool_factory_pattern() {
        // Test pool creation with factory using simple constructor
        GlideJedisFactory factory =
                new GlideJedisFactory(valkeyHost, valkeyPort, DefaultJedisClientConfig.builder().build());

        try (JedisPool factoryPool = new JedisPool(factory)) {
            assertNotNull(factoryPool, "Factory-based pool should be created");

            try (Jedis jedis = factoryPool.getResource()) {
                String testKey = UUID.randomUUID().toString();
                String testValue = "factory_value";

                String setResult = jedis.set(testKey, testValue);
                assertEquals("OK", setResult, "SET with factory pool should work");

                String getResult = jedis.get(testKey);
                assertEquals(testValue, getResult, "GET with factory pool should work");

                // Cleanup
                jedis.del(testKey);
            }
        }
    }

    @Test
    void pool_statistics() {
        // Test pool statistics and monitoring
        try (JedisPool pool = new JedisPool(valkeyHost, valkeyPort)) {
            assertNotNull(pool, "Pool should be initialized");

            // Get initial statistics
            int initialActive = pool.getNumActive();
            int initialIdle = pool.getNumIdle();
            int maxTotal = pool.getMaxTotal();

            assertTrue(initialActive >= 0, "Active connections should be non-negative");
            assertTrue(initialIdle >= 0, "Idle connections should be non-negative");
            assertTrue(maxTotal > 0, "Max total should be positive");

            // Test with active connection
            try (Jedis jedis = pool.getResource()) {
                // Active count might increase (depending on pool state)
                int activeWithConnection = pool.getNumActive();
                assertTrue(activeWithConnection >= initialActive, "Active count should not decrease");
            }

            // After returning connection, active should decrease
            int finalActive = pool.getNumActive();
            assertTrue(finalActive >= 0, "Final active connections should be non-negative");
        }
    }

    @Test
    void pool_broken_resource_is_invalidated_and_replaced() {
        try (JedisPool pool = new JedisPool(valkeyHost, valkeyPort)) {
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
                jedis.setBroken(true);
            }
            try (Jedis jedis2 = pool.getResource()) {
                assertEquals(
                        "PONG",
                        jedis2.ping(),
                        "verify: pool remains usable after broken connection is closed and invalidated");
            }
        }
    }

    @Test
    void pool_allows_multi_after_prior_borrow_abandoned_multi() {
        try (JedisPool pool = new JedisPool(valkeyHost, valkeyPort)) {
            try (Jedis j1 = pool.getResource()) {
                j1.multi();
                // exercise: return connection without exec/discard/Transaction.close (abandoned MULTI)
            }
            try (Jedis j2 = pool.getResource()) {
                Transaction t = j2.multi();
                assertNotNull(
                        t, "verify: multi() succeeds on reused connection after abandoned prior multi");
                String key = UUID.randomUUID().toString();
                t.set(key, "v");
                List<Object> results = t.exec();
                assertNotNull(results, "verify: exec completes after healthy transaction");
                j2.del(key);
            }
        }
    }

    @Test
    void pool_concurrent_borrow_and_return() throws InterruptedException {
        int threadCount = 8;
        int iterationsPerThread = 50;
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(4);
        try (JedisPool pool = new JedisPool(poolConfig, valkeyHost, valkeyPort)) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch done = new CountDownLatch(threadCount * iterationsPerThread);
            AtomicInteger failures = new AtomicInteger(0);
            for (int t = 0; t < threadCount; t++) {
                executor.submit(
                        () -> {
                            for (int i = 0; i < iterationsPerThread; i++) {
                                try (Jedis jedis = pool.getResource()) {
                                    jedis.ping();
                                } catch (Exception e) {
                                    failures.incrementAndGet();
                                } finally {
                                    done.countDown();
                                }
                            }
                        });
            }
            assertTrue(
                    done.await(120, TimeUnit.SECONDS),
                    "verify: all concurrent borrow/return cycles finished within deadline");
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
            assertEquals(0, failures.get(), "verify: no thread observed borrow/ping/close failures");
        }
    }

    @Test
    void pool_returnBrokenResource_directly_invalidates_borrowed_connection() {
        try (JedisPool pool = new JedisPool(valkeyHost, valkeyPort)) {
            Jedis jedis = pool.getResource();
            jedis.ping();
            pool.returnBrokenResource(jedis);
            try (Jedis jedis2 = pool.getResource()) {
                assertEquals(
                        "PONG", jedis2.ping(), "verify: pool usable after explicit returnBrokenResource");
            }
        }
    }
}
