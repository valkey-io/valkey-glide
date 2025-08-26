/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import redis.clients.jedis.JedisPooled;

/**
 * JedisPooled compatibility test that validates GLIDE JedisPooled functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected JedisPooled API and
 * behavior. JedisPooled extends UnifiedJedis and provides pooled connection semantics.
 */
public class JedisPooledTest {

    private static final String TEST_KEY_PREFIX = "jedis_pooled_test:";

    // Server configuration - dynamically resolved from CI environment
    private static final String redisHost;
    private static final int redisPort;

    // GLIDE JedisPooled compatibility layer instance
    private JedisPooled jedisPooled;

    static {
        String standaloneHosts = System.getProperty("test.server.standalone");

        if (standaloneHosts != null && !standaloneHosts.trim().isEmpty()) {
            String firstHost = standaloneHosts.split(",")[0].trim();
            String[] hostPort = firstHost.split(":");

            if (hostPort.length == 2) {
                redisHost = hostPort[0];
                redisPort = Integer.parseInt(hostPort[1]);
            } else {
                redisHost = "localhost";
                redisPort = 6379;
            }
        } else {
            redisHost = "localhost";
            redisPort = 6379;
        }
    }

    @BeforeEach
    void setup() {
        // Create GLIDE JedisPooled compatibility layer instance
        jedisPooled = new JedisPooled(redisHost, redisPort);
        assertNotNull(jedisPooled, "GLIDE JedisPooled instance should be created successfully");
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (jedisPooled != null) {
            cleanupTestKeys(jedisPooled);
            jedisPooled.close();
        }
    }

    @Test
    void basic_set_and_get() {
        String testKey = TEST_KEY_PREFIX + "basic";
        String testValue = "pooled_test_value_123";

        // Test GLIDE JedisPooled compatibility layer
        String setResult = jedisPooled.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should return OK");

        String getResult = jedisPooled.get(testKey);
        assertEquals(testValue, getResult, "GET should return the set value");
    }

    @Test
    void multiple_operations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "pooled_key1", "pooled_value1");
        testData.put(TEST_KEY_PREFIX + "pooled_key2", "pooled_value2");
        testData.put(TEST_KEY_PREFIX + "pooled_key3", "pooled_value3");

        // Test multiple SET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedisPooled.set(entry.getKey(), entry.getValue());
            assertEquals("OK", result, "SET should return OK for " + entry.getKey());
        }

        // Test multiple GET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedisPooled.get(entry.getKey());
            assertEquals(
                    entry.getValue(), result, "GET should return correct value for " + entry.getKey());
        }
    }

    @Test
    void testConnectionOperations() {
        // Test PING
        String pingResult = jedisPooled.ping();
        assertEquals("PONG", pingResult, "PING should return PONG");

        // Test PING with message
        String message = "pooled_test_message";
        String pingWithMessage = jedisPooled.ping(message);
        assertEquals(message, pingWithMessage, "PING with message should return the message");
    }

    @Test
    void testDeleteOperations() {
        String testKey1 = TEST_KEY_PREFIX + "del1";
        String testKey2 = TEST_KEY_PREFIX + "del2";
        String testKey3 = TEST_KEY_PREFIX + "del3";

        // Set some keys
        jedisPooled.set(testKey1, "value1");
        jedisPooled.set(testKey2, "value2");
        jedisPooled.set(testKey3, "value3");

        // Test single key deletion
        long delResult = jedisPooled.del(testKey1);
        assertEquals(1, delResult, "DEL should return 1 for deleted key");

        // Verify key is deleted
        String getResult = jedisPooled.get(testKey1);
        assertNull(getResult, "Key should not exist after deletion");

        // Test multiple key deletion
        long multiDelResult = jedisPooled.del(testKey2, testKey3);
        assertEquals(2, multiDelResult, "DEL should return 2 for two deleted keys");

        // Verify keys are deleted
        assertNull(jedisPooled.get(testKey2), "Key2 should not exist after deletion");
        assertNull(jedisPooled.get(testKey3), "Key3 should not exist after deletion");
    }

    @Test
    void testPooledConnectionBehavior() {
        // Test that JedisPooled maintains connection state properly
        String testKey = TEST_KEY_PREFIX + "pooled_behavior";

        // Set initial value
        jedisPooled.set(testKey, "initial_value");

        // Verify initial value
        String initialValue = jedisPooled.get(testKey);
        assertEquals("initial_value", initialValue, "Initial value should be set correctly");

        // Perform multiple operations to test connection reuse and value updates
        for (int i = 0; i < 10; i++) {
            // Update value
            String newValue = "value_" + i;
            jedisPooled.set(testKey, newValue);

            // Verify update immediately
            String retrievedValue = jedisPooled.get(testKey);
            assertEquals(newValue, retrievedValue, "Updated value should be correct for iteration " + i);

            // Test that the value persists across multiple gets (connection reuse)
            for (int j = 0; j < 3; j++) {
                String persistentValue = jedisPooled.get(testKey);
                assertEquals(
                        newValue,
                        persistentValue,
                        "Value should be consistent across multiple gets in iteration " + i);
            }
        }

        // Final verification - should have the last value
        String finalValue = jedisPooled.get(testKey);
        assertEquals("value_9", finalValue, "Final value should be the last updated value");
    }

    @Test
    void testConnectionState() {
        // Test that connection is not closed initially
        assertFalse(jedisPooled.isClosed(), "Connection should not be closed initially");

        // Test basic operations work
        String testKey = TEST_KEY_PREFIX + "connection_test";
        String testValue = "connection_value";

        jedisPooled.set(testKey, testValue);
        String result = jedisPooled.get(testKey);
        assertEquals(testValue, result, "Operations should work on active connection");

        // Connection should still be active
        assertFalse(jedisPooled.isClosed(), "Connection should still be active after operations");
    }

    @Test
    void testBinaryOperations() {
        byte[] testKey = (TEST_KEY_PREFIX + "binary").getBytes();

        // Set using string method first
        String stringKey = TEST_KEY_PREFIX + "binary";
        jedisPooled.set(stringKey, "binary_value");

        // Test binary key deletion
        long delResult = jedisPooled.del(testKey);
        assertEquals(1, delResult, "Binary DEL should return 1 for deleted key");

        // Verify key is deleted
        String getResult = jedisPooled.get(stringKey);
        assertNull(getResult, "Key should not exist after binary deletion");
    }

    @Test
    void testConcurrentOperations() throws InterruptedException {
        final int threadCount = 5;
        final String testKeyPrefix = TEST_KEY_PREFIX + "concurrent_";
        Thread[] threads = new Thread[threadCount];
        final boolean[] results = new boolean[threadCount];

        // Create threads that use JedisPooled concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] =
                    new Thread(
                            () -> {
                                try {
                                    String key = testKeyPrefix + threadIndex;
                                    String value = "thread_" + threadIndex + "_value";

                                    String setResult = jedisPooled.set(key, value);
                                    String getResult = jedisPooled.get(key);

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

    @Test
    void testLargeValueOperations() {
        String testKey = TEST_KEY_PREFIX + "large_value";
        StringBuilder largeValue = new StringBuilder();

        // Create a large value (5KB)
        for (int i = 0; i < 500; i++) {
            largeValue.append("0123456789");
        }
        String expectedValue = largeValue.toString();

        // Test setting and getting large value
        String setResult = jedisPooled.set(testKey, expectedValue);
        assertEquals("OK", setResult, "SET should work with large values");

        String getResult = jedisPooled.get(testKey);
        assertEquals(expectedValue, getResult, "GET should return complete large value");
        assertEquals(5000, getResult.length(), "Large value should have correct length");
    }

    private void cleanupTestKeys(JedisPooled jedisPooled) {
        // Delete all test keys
        String[] keysToDelete = {
            TEST_KEY_PREFIX + "basic",
            TEST_KEY_PREFIX + "pooled_key1",
            TEST_KEY_PREFIX + "pooled_key2",
            TEST_KEY_PREFIX + "pooled_key3",
            TEST_KEY_PREFIX + "del1",
            TEST_KEY_PREFIX + "del2",
            TEST_KEY_PREFIX + "del3",
            TEST_KEY_PREFIX + "pooled_behavior",
            TEST_KEY_PREFIX + "connection_test",
            TEST_KEY_PREFIX + "binary",
            TEST_KEY_PREFIX + "large_value",
            TEST_KEY_PREFIX + "concurrent_0",
            TEST_KEY_PREFIX + "concurrent_1",
            TEST_KEY_PREFIX + "concurrent_2",
            TEST_KEY_PREFIX + "concurrent_3",
            TEST_KEY_PREFIX + "concurrent_4"
        };
        jedisPooled.del(keysToDelete);
    }
}
