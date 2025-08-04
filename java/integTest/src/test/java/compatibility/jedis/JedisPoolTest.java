/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * JedisPool compatibility test that validates GLIDE JedisPool functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected JedisPool API and
 * behavior for connection pooling.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisPoolTest {

    private static final String TEST_KEY_PREFIX = "jedis_pool_test:";

    // Server configuration - dynamically resolved from CI environment
    private static final String redisHost;
    private static final int redisPort;

    // GLIDE compatibility layer pool
    private JedisPool jedisPool;

    static {
        String standaloneHosts = System.getProperty("test.server.standalone");

        if (standaloneHosts != null && !standaloneHosts.trim().isEmpty()) {
            String firstHost = standaloneHosts.split(",")[0].trim();
            String[] hostPort = firstHost.split(":");

            if (hostPort.length == 2) {
                redisHost = hostPort[0];
                redisPort = Integer.parseInt(hostPort[1]);
            } else {
                // Fallback to localhost
                redisHost = "localhost";
                redisPort = 6379;
            }
        } else {
            // Fallback to localhost
            redisHost = "localhost";
            redisPort = 6379;
        }
    }

    @BeforeEach
    void setup() {
        jedisPool = new JedisPool(redisHost, redisPort);
        assertNotNull(jedisPool, "GLIDE JedisPool instance should be created successfully");
    }

    @AfterEach
    void cleanup() {
        // Cleanup and close pool
        if (jedisPool != null) {
            // Clean up test keys before closing pool
            try (Jedis jedis = jedisPool.getResource()) {
                cleanupTestKeys(jedis);
            }
            jedisPool.close();
        }
    }

    @Test
    @Order(1)
    void testPoolBasicOperations() {
        // Test getting resource from pool
        try (Jedis jedis = jedisPool.getResource()) {
            assertNotNull(jedis, "Should be able to get Jedis resource from pool");

            // Test basic operations through pooled connection
            String testKey = TEST_KEY_PREFIX + "pool_basic";
            String testValue = "pool_test_value";

            String setResult = jedis.set(testKey, testValue);
            assertEquals("OK", setResult, "SET through pool should return OK");

            String getResult = jedis.get(testKey);
            assertEquals(testValue, getResult, "GET through pool should return correct value");
        }
    }

    @Test
    @Order(2)
    void testMultipleConnections() {
        // Test multiple connections from pool
        String testKey1 = TEST_KEY_PREFIX + "multi_conn1";
        String testKey2 = TEST_KEY_PREFIX + "multi_conn2";
        String testValue1 = "value1";
        String testValue2 = "value2";

        // First connection
        try (Jedis jedis1 = jedisPool.getResource()) {
            assertNotNull(jedis1, "First connection should be available");
            String result1 = jedis1.set(testKey1, testValue1);
            assertEquals("OK", result1, "First connection SET should work");
        }

        // Second connection
        try (Jedis jedis2 = jedisPool.getResource()) {
            assertNotNull(jedis2, "Second connection should be available");
            String result2 = jedis2.set(testKey2, testValue2);
            assertEquals("OK", result2, "Second connection SET should work");

            // Verify first key is still accessible
            String getValue1 = jedis2.get(testKey1);
            assertEquals(testValue1, getValue1, "Should be able to access data from previous connection");
        }
    }

    @Test
    @Order(3)
    void testPoolResourceManagement() {
        // Test that resources are properly managed
        Jedis jedis1 = jedisPool.getResource();
        assertNotNull(jedis1, "Should get first resource");

        Jedis jedis2 = jedisPool.getResource();
        assertNotNull(jedis2, "Should get second resource");

        // Test basic operations on both
        String result1 = jedis1.ping();
        assertEquals("PONG", result1, "First connection should work");

        String result2 = jedis2.ping();
        assertEquals("PONG", result2, "Second connection should work");

        // Close resources
        jedis1.close();
        jedis2.close();

        // Should be able to get new resources after closing
        try (Jedis jedis3 = jedisPool.getResource()) {
            assertNotNull(jedis3, "Should get new resource after closing previous ones");
            String result3 = jedis3.ping();
            assertEquals("PONG", result3, "New connection should work");
        }
    }

    @Test
    @Order(4)
    void testPoolConnectionReuse() {
        String testKey = TEST_KEY_PREFIX + "reuse_test";

        // Use connection and close it
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(testKey, "initial_value");
        }

        // Get another connection and verify data persistence
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(testKey);
            assertEquals("initial_value", value, "Data should persist across connection reuse");

            // Update value
            jedis.set(testKey, "updated_value");
        }

        // Verify update persisted
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(testKey);
            assertEquals("updated_value", value, "Updated data should persist");
        }
    }

    @Test
    @Order(5)
    void testPoolStatus() {
        // Test pool is active
        assertFalse(jedisPool.isClosed(), "Pool should not be closed initially");

        // Use some connections
        try (Jedis jedis1 = jedisPool.getResource();
                Jedis jedis2 = jedisPool.getResource()) {

            assertNotNull(jedis1, "First connection should be available");
            assertNotNull(jedis2, "Second connection should be available");

            // Test both connections work
            assertEquals("PONG", jedis1.ping(), "First connection should work");
            assertEquals("PONG", jedis2.ping(), "Second connection should work");
        }

        // Pool should still be active after connections are returned
        assertFalse(jedisPool.isClosed(), "Pool should still be active after returning connections");
    }

    @Test
    @Order(6)
    void testPoolConcurrentAccess() throws InterruptedException {
        final int threadCount = 5;
        final String testKeyPrefix = TEST_KEY_PREFIX + "concurrent_";
        Thread[] threads = new Thread[threadCount];
        final boolean[] results = new boolean[threadCount];

        // Create threads that use the pool concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] =
                    new Thread(
                            () -> {
                                try (Jedis jedis = jedisPool.getResource()) {
                                    String key = testKeyPrefix + threadIndex;
                                    String value = "thread_" + threadIndex + "_value";

                                    String setResult = jedis.set(key, value);
                                    String getResult = jedis.get(key);

                                    results[threadIndex] = "OK".equals(setResult) && value.equals(getResult);
                                } catch (Exception e) {
                                    results[threadIndex] = false;
                                }
                            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }

        // Verify all threads succeeded
        for (int i = 0; i < threadCount; i++) {
            assertTrue(results[i], "Thread " + i + " should have succeeded");
        }
    }

    private void cleanupTestKeys(Jedis jedis) {
        // Delete all test keys
        String[] keysToDelete = {
            TEST_KEY_PREFIX + "pool_basic",
            TEST_KEY_PREFIX + "multi_conn1",
            TEST_KEY_PREFIX + "multi_conn2",
            TEST_KEY_PREFIX + "reuse_test",
            TEST_KEY_PREFIX + "concurrent_0",
            TEST_KEY_PREFIX + "concurrent_1",
            TEST_KEY_PREFIX + "concurrent_2",
            TEST_KEY_PREFIX + "concurrent_3",
            TEST_KEY_PREFIX + "concurrent_4"
        };

        jedis.del(keysToDelete);
    }
}
