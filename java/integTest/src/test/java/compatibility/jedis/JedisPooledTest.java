/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import redis.clients.jedis.JedisPooled;

/**
 * JedisPooled compatibility test that validates GLIDE JedisPooled functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected JedisPooled API and
 * behavior. JedisPooled extends UnifiedJedis and provides pooled connection semantics.
 */
public class JedisPooledTest {

    // Server configuration - dynamically resolved from CI environment
    private static final String valkeyHost;
    private static final int valkeyPort;

    // GLIDE JedisPooled compatibility layer instance
    private JedisPooled jedisPooled;

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

    @BeforeEach
    void setup() {
        // Create GLIDE JedisPooled compatibility layer instance
        jedisPooled = new JedisPooled(valkeyHost, valkeyPort);
        assertNotNull(jedisPooled, "GLIDE JedisPooled instance should be created successfully");
    }

    @Test
    void basic_set_and_get() {
        String testKey = UUID.randomUUID().toString();
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
        testData.put(UUID.randomUUID().toString(), "pooled_value1");
        testData.put(UUID.randomUUID().toString(), "pooled_value2");
        testData.put(UUID.randomUUID().toString(), "pooled_value3");

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
    void connection_operations() {
        // Test PING
        String pingResult = jedisPooled.ping();
        assertEquals("PONG", pingResult, "PING should return PONG");

        // Test PING with message
        String message = "pooled_test_message";
        String pingWithMessage = jedisPooled.ping(message);
        assertEquals(message, pingWithMessage, "PING with message should return the message");
    }

    @Test
    void delete_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();
        String testKey3 = UUID.randomUUID().toString();

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
    void pooled_connection_behavior() {
        // Test that JedisPooled maintains connection state properly
        String testKey = UUID.randomUUID().toString();

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
    void connection_state() {
        // Test that connection is not closed initially
        assertFalse(jedisPooled.isClosed(), "Connection should not be closed initially");

        // Test basic operations work
        String testKey = UUID.randomUUID().toString();
        String testValue = "connection_value";

        jedisPooled.set(testKey, testValue);
        String result = jedisPooled.get(testKey);
        assertEquals(testValue, result, "Operations should work on active connection");

        // Connection should still be active
        assertFalse(jedisPooled.isClosed(), "Connection should still be active after operations");
    }

    @Test
    void binary_operations() {
        String keyString = UUID.randomUUID().toString();
        byte[] testKey = keyString.getBytes();

        // Set using string method first
        jedisPooled.set(keyString, "binary_value");

        // Test binary key deletion - use the same key that was set
        long delResult = jedisPooled.del(testKey);
        assertEquals(1, delResult, "Binary DEL should return 1 for deleted key");

        // Verify key is deleted
        String getResult = jedisPooled.get(keyString);
        assertNull(getResult, "Key should not exist after binary deletion");
    }

    @Test
    void concurrent_operations() throws InterruptedException {
        final int threadCount = 5;
        final String testKeyPrefix = UUID.randomUUID().toString();
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
    void large_value_operations() {
        String testKey = UUID.randomUUID().toString();
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
}
