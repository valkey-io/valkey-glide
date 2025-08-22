/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import redis.clients.jedis.UnifiedJedis;

/**
 * UnifiedJedis standalone compatibility test that validates GLIDE UnifiedJedis functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected UnifiedJedis API
 * and behavior for core Redis operations in standalone mode.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UnifiedJedisTest {

    private static final String TEST_KEY_PREFIX = "unified_jedis_test:";

    // Server configuration - dynamically resolved from CI environment
    private static final String redisHost;
    private static final int redisPort;

    // GLIDE UnifiedJedis compatibility layer instance
    private UnifiedJedis unifiedJedis;

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
        // Create GLIDE UnifiedJedis compatibility layer instance
        unifiedJedis = new UnifiedJedis(redisHost, redisPort);
        assertNotNull(unifiedJedis, "GLIDE UnifiedJedis instance should be created successfully");
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (unifiedJedis != null) {
            cleanupTestKeys(unifiedJedis);
            unifiedJedis.close();
        }
    }

    @Test
    @Order(1)
    void testBasicSetAndGet() {
        String testKey = TEST_KEY_PREFIX + "basic";
        String testValue = "unified_test_value_123";

        // Test GLIDE UnifiedJedis compatibility layer
        String setResult = unifiedJedis.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should return OK");

        String getResult = unifiedJedis.get(testKey);
        assertEquals(testValue, getResult, "GET should return the set value");
    }

    @Test
    @Order(2)
    void testMultipleOperations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "unified_key1", "unified_value1");
        testData.put(TEST_KEY_PREFIX + "unified_key2", "unified_value2");
        testData.put(TEST_KEY_PREFIX + "unified_key3", "unified_value3");

        // Test multiple SET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = unifiedJedis.set(entry.getKey(), entry.getValue());
            assertEquals("OK", result, "SET should return OK for " + entry.getKey());
        }

        // Test multiple GET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = unifiedJedis.get(entry.getKey());
            assertEquals(
                    entry.getValue(), result, "GET should return correct value for " + entry.getKey());
        }
    }

    @Test
    @Order(3)
    void testConnectionOperations() {
        // Test PING
        String pingResult = unifiedJedis.ping();
        assertEquals("PONG", pingResult, "PING should return PONG");

        // Test PING with message
        String message = "unified_test_message";
        String pingWithMessage = unifiedJedis.ping(message);
        assertEquals(message, pingWithMessage, "PING with message should return the message");
    }

    @Test
    @Order(4)
    void testDeleteOperations() {
        String testKey1 = TEST_KEY_PREFIX + "del1";
        String testKey2 = TEST_KEY_PREFIX + "del2";
        String testKey3 = TEST_KEY_PREFIX + "del3";

        // Set some keys
        unifiedJedis.set(testKey1, "value1");
        unifiedJedis.set(testKey2, "value2");
        unifiedJedis.set(testKey3, "value3");

        // Test single key deletion
        long delResult = unifiedJedis.del(testKey1);
        assertEquals(1, delResult, "DEL should return 1 for deleted key");

        // Verify key is deleted
        String getResult = unifiedJedis.get(testKey1);
        assertNull(getResult, "Key should not exist after deletion");

        // Test multiple key deletion
        long multiDelResult = unifiedJedis.del(testKey2, testKey3);
        assertEquals(2, multiDelResult, "DEL should return 2 for two deleted keys");

        // Verify keys are deleted
        assertNull(unifiedJedis.get(testKey2), "Key2 should not exist after deletion");
        assertNull(unifiedJedis.get(testKey3), "Key3 should not exist after deletion");
    }

    @Test
    @Order(5)
    void testConnectionState() {
        // Test that connection is not closed initially
        assertFalse(unifiedJedis.isClosed(), "Connection should not be closed initially");

        // Test basic operations work
        String testKey = TEST_KEY_PREFIX + "connection_test";
        String testValue = "connection_value";

        unifiedJedis.set(testKey, testValue);
        String result = unifiedJedis.get(testKey);
        assertEquals(testValue, result, "Operations should work on active connection");

        // Connection should still be active
        assertFalse(unifiedJedis.isClosed(), "Connection should still be active after operations");
    }

    @Test
    @Order(6)
    void testBinaryOperations() {
        byte[] testKey = (TEST_KEY_PREFIX + "binary").getBytes();
        byte[] testValue = "binary_value".getBytes();

        // Note: UnifiedJedis currently only supports del for binary keys
        // Set using string method first
        String stringKey = TEST_KEY_PREFIX + "binary";
        unifiedJedis.set(stringKey, "binary_value");

        // Test binary key deletion
        long delResult = unifiedJedis.del(testKey);
        assertEquals(1, delResult, "Binary DEL should return 1 for deleted key");

        // Verify key is deleted
        String getResult = unifiedJedis.get(stringKey);
        assertNull(getResult, "Key should not exist after binary deletion");
    }

    @Test
    @Order(7)
    void testMultipleBinaryDeletion() {
        // Set up test keys using string methods
        String key1 = TEST_KEY_PREFIX + "binary1";
        String key2 = TEST_KEY_PREFIX + "binary2";
        String key3 = TEST_KEY_PREFIX + "binary3";

        unifiedJedis.set(key1, "value1");
        unifiedJedis.set(key2, "value2");
        unifiedJedis.set(key3, "value3");

        // Test multiple binary key deletion
        byte[][] binaryKeys = {key1.getBytes(), key2.getBytes(), key3.getBytes()};

        long delResult = unifiedJedis.del(binaryKeys);
        assertEquals(3, delResult, "Binary DEL should return 3 for three deleted keys");

        // Verify keys are deleted
        assertNull(unifiedJedis.get(key1), "Key1 should not exist after deletion");
        assertNull(unifiedJedis.get(key2), "Key2 should not exist after deletion");
        assertNull(unifiedJedis.get(key3), "Key3 should not exist after deletion");
    }

    @Test
    @Order(8)
    void testLargeValueOperations() {
        String testKey = TEST_KEY_PREFIX + "large_value";
        StringBuilder largeValue = new StringBuilder();

        // Create a large value (10KB)
        for (int i = 0; i < 1000; i++) {
            largeValue.append("0123456789");
        }
        String expectedValue = largeValue.toString();

        // Test setting and getting large value
        String setResult = unifiedJedis.set(testKey, expectedValue);
        assertEquals("OK", setResult, "SET should work with large values");

        String getResult = unifiedJedis.get(testKey);
        assertEquals(expectedValue, getResult, "GET should return complete large value");
        assertEquals(10000, getResult.length(), "Large value should have correct length");
    }

    private void cleanupTestKeys(UnifiedJedis unifiedJedis) {
        // Delete all test keys using the available del methods
        String[] keysToDelete = {
            TEST_KEY_PREFIX + "basic",
            TEST_KEY_PREFIX + "unified_key1",
            TEST_KEY_PREFIX + "unified_key2",
            TEST_KEY_PREFIX + "unified_key3",
            TEST_KEY_PREFIX + "del1",
            TEST_KEY_PREFIX + "del2",
            TEST_KEY_PREFIX + "del3",
            TEST_KEY_PREFIX + "connection_test",
            TEST_KEY_PREFIX + "binary",
            TEST_KEY_PREFIX + "binary1",
            TEST_KEY_PREFIX + "binary2",
            TEST_KEY_PREFIX + "binary3",
            TEST_KEY_PREFIX + "large_value"
        };
        unifiedJedis.del(keysToDelete);
    }
}
