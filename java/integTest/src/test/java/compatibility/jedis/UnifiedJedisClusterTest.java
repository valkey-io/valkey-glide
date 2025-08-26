/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;

import glide.TestConfiguration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;

/**
 * UnifiedJedis cluster compatibility test that validates GLIDE UnifiedJedis functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected UnifiedJedis API
 * and behavior for core Redis operations in cluster mode.
 */
public class UnifiedJedisClusterTest {

    private static final String TEST_KEY_PREFIX = "unified_jedis_cluster_test:";

    // Server configuration - dynamically resolved from CI environment
    private static final Set<HostAndPort> clusterNodes;

    // GLIDE UnifiedJedis compatibility layer instance for cluster
    private UnifiedJedis unifiedJedis;

    static {
        String[] clusterHosts = TestConfiguration.CLUSTER_HOSTS;

        if (clusterHosts.length > 0 && !clusterHosts[0].trim().isEmpty()) {
            clusterNodes = new HashSet<>();

            for (String host : clusterHosts) {
                String[] hostPort = host.trim().split(":");
                if (hostPort.length == 2) {
                    clusterNodes.add(new HostAndPort(hostPort[0], Integer.parseInt(hostPort[1])));
                }
            }

            // Fallback if no valid hosts found
            if (clusterNodes.isEmpty()) {
                clusterNodes.add(new HostAndPort("localhost", 7000));
                clusterNodes.add(new HostAndPort("localhost", 7001));
                clusterNodes.add(new HostAndPort("localhost", 7002));
            }
        } else {
            // Default cluster configuration
            clusterNodes = new HashSet<>();
            clusterNodes.add(new HostAndPort("localhost", 7000));
            clusterNodes.add(new HostAndPort("localhost", 7001));
            clusterNodes.add(new HostAndPort("localhost", 7002));
        }
    }

    @BeforeEach
    void setup() {
        // Create GLIDE UnifiedJedis compatibility layer instance for cluster
        unifiedJedis = new UnifiedJedis(clusterNodes);
        assertNotNull(
                unifiedJedis, "GLIDE UnifiedJedis cluster instance should be created successfully");
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (unifiedJedis != null) {
            cleanupTestKeys(unifiedJedis);
            unifiedJedis.close();
            unifiedJedis = null;
        }
    }

    @Test
    void basic_set_and_get() {
        String testKey = TEST_KEY_PREFIX + "basic";
        String testValue = "unified_cluster_test_value_123";

        // Test GLIDE UnifiedJedis compatibility layer in cluster mode
        String setResult = unifiedJedis.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should return OK in cluster mode");

        String getResult = unifiedJedis.get(testKey);
        assertEquals(testValue, getResult, "GET should return the set value in cluster mode");
    }

    @Test
    void multiple_operations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "cluster_key1", "cluster_value1");
        testData.put(TEST_KEY_PREFIX + "cluster_key2", "cluster_value2");
        testData.put(TEST_KEY_PREFIX + "cluster_key3", "cluster_value3");

        // Test multiple SET operations in cluster mode
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = unifiedJedis.set(entry.getKey(), entry.getValue());
            assertEquals("OK", result, "SET should return OK for " + entry.getKey() + " in cluster mode");
        }

        // Test multiple GET operations in cluster mode
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = unifiedJedis.get(entry.getKey());
            assertEquals(
                    entry.getValue(),
                    result,
                    "GET should return correct value for " + entry.getKey() + " in cluster mode");
        }
    }

    @Test
    void testConnectionOperations() {
        // Test PING in cluster mode
        String pingResult = unifiedJedis.ping();
        assertEquals("PONG", pingResult, "PING should return PONG in cluster mode");

        // Test PING with message in cluster mode
        String message = "unified_cluster_test_message";
        String pingWithMessage = unifiedJedis.ping(message);
        assertEquals(
                message, pingWithMessage, "PING with message should return the message in cluster mode");
    }

    @Test
    void testDeleteOperations() {
        String testKey1 = TEST_KEY_PREFIX + "del1";
        String testKey2 = TEST_KEY_PREFIX + "del2";
        String testKey3 = TEST_KEY_PREFIX + "del3";

        // Set some keys in cluster mode
        unifiedJedis.set(testKey1, "value1");
        unifiedJedis.set(testKey2, "value2");
        unifiedJedis.set(testKey3, "value3");

        // Test single key deletion in cluster mode
        long delResult = unifiedJedis.del(testKey1);
        assertEquals(1, delResult, "DEL should return 1 for deleted key in cluster mode");

        // Verify key is deleted
        String getResult = unifiedJedis.get(testKey1);
        assertNull(getResult, "Key should not exist after deletion in cluster mode");

        // Test multiple key deletion in cluster mode
        long multiDelResult = unifiedJedis.del(testKey2, testKey3);
        assertEquals(2, multiDelResult, "DEL should return 2 for two deleted keys in cluster mode");

        // Verify keys are deleted
        assertNull(unifiedJedis.get(testKey2), "Key2 should not exist after deletion in cluster mode");
        assertNull(unifiedJedis.get(testKey3), "Key3 should not exist after deletion in cluster mode");
    }

    @Test
    void testConnectionState() {
        // Test that connection is not closed initially
        assertFalse(
                unifiedJedis.isClosed(), "Connection should not be closed initially in cluster mode");

        // Test basic operations work in cluster mode
        String testKey = TEST_KEY_PREFIX + "connection_test";
        String testValue = "connection_value";

        unifiedJedis.set(testKey, testValue);
        String result = unifiedJedis.get(testKey);
        assertEquals(testValue, result, "Operations should work on active connection in cluster mode");

        // Connection should still be active
        assertFalse(
                unifiedJedis.isClosed(),
                "Connection should still be active after operations in cluster mode");
    }

    @Test
    void testBinaryOperations() {
        byte[] testKey = (TEST_KEY_PREFIX + "binary").getBytes();
        byte[] testValue = "binary_value".getBytes();

        // Note: UnifiedJedis currently only supports del for binary keys
        // Set using string method first
        String stringKey = TEST_KEY_PREFIX + "binary";
        unifiedJedis.set(stringKey, "binary_value");

        // Test binary key deletion in cluster mode
        long delResult = unifiedJedis.del(testKey);
        assertEquals(1, delResult, "Binary DEL should return 1 for deleted key in cluster mode");

        // Verify key is deleted
        String getResult = unifiedJedis.get(stringKey);
        assertNull(getResult, "Key should not exist after binary deletion in cluster mode");
    }

    @Test
    void testMultipleBinaryDeletion() {
        // Set up test keys using string methods
        String key1 = TEST_KEY_PREFIX + "binary1";
        String key2 = TEST_KEY_PREFIX + "binary2";
        String key3 = TEST_KEY_PREFIX + "binary3";

        unifiedJedis.set(key1, "value1");
        unifiedJedis.set(key2, "value2");
        unifiedJedis.set(key3, "value3");

        // Test multiple binary key deletion in cluster mode
        byte[][] binaryKeys = {key1.getBytes(), key2.getBytes(), key3.getBytes()};

        long delResult = unifiedJedis.del(binaryKeys);
        assertEquals(3, delResult, "Binary DEL should return 3 for three deleted keys in cluster mode");

        // Verify keys are deleted
        assertNull(unifiedJedis.get(key1), "Key1 should not exist after deletion in cluster mode");
        assertNull(unifiedJedis.get(key2), "Key2 should not exist after deletion in cluster mode");
        assertNull(unifiedJedis.get(key3), "Key3 should not exist after deletion in cluster mode");
    }

    @Test
    void testLargeValueOperations() {
        String testKey = TEST_KEY_PREFIX + "large_value";
        StringBuilder largeValue = new StringBuilder();

        // Create a large value (10KB)
        for (int i = 0; i < 1000; i++) {
            largeValue.append("0123456789");
        }
        String expectedValue = largeValue.toString();

        // Test setting and getting large value in cluster mode
        String setResult = unifiedJedis.set(testKey, expectedValue);
        assertEquals("OK", setResult, "SET should work with large values in cluster mode");

        String getResult = unifiedJedis.get(testKey);
        assertEquals(
                expectedValue, getResult, "GET should return complete large value in cluster mode");
        assertEquals(
                10000, getResult.length(), "Large value should have correct length in cluster mode");
    }

    @Test
    void cluster_specific_operations() {
        // Test operations that are specific to cluster mode
        String testKey = TEST_KEY_PREFIX + "cluster_specific";
        String testValue = "cluster_specific_value";

        // Set a key and verify it works across cluster
        unifiedJedis.set(testKey, testValue);
        String result = unifiedJedis.get(testKey);
        assertEquals(testValue, result, "Cluster operations should work correctly");

        // Test that the key can be deleted
        long delResult = unifiedJedis.del(testKey);
        assertEquals(1, delResult, "DEL should work in cluster mode");
    }

    @Test
    void testClusterKeyDistribution() {
        // Test that keys are distributed across cluster nodes
        Map<String, String> distributedKeys = new HashMap<>();

        // Create keys with different hash slots to test distribution
        for (int i = 0; i < 10; i++) {
            String key = TEST_KEY_PREFIX + "distributed_" + i;
            String value = "distributed_value_" + i;
            distributedKeys.put(key, value);
        }

        // Set all keys
        for (Map.Entry<String, String> entry : distributedKeys.entrySet()) {
            String result = unifiedJedis.set(entry.getKey(), entry.getValue());
            assertEquals("OK", result, "SET should work for distributed key: " + entry.getKey());
        }

        // Verify all keys can be retrieved
        for (Map.Entry<String, String> entry : distributedKeys.entrySet()) {
            String result = unifiedJedis.get(entry.getKey());
            assertEquals(
                    entry.getValue(), result, "GET should work for distributed key: " + entry.getKey());
        }

        // Clean up distributed keys
        String[] keysArray = distributedKeys.keySet().toArray(new String[0]);
        long delResult = unifiedJedis.del(keysArray);
        assertEquals(distributedKeys.size(), delResult, "All distributed keys should be deleted");
    }

    private void cleanupTestKeys(UnifiedJedis unifiedJedis) {
        // Delete all test keys using the available del methods
        String[] keysToDelete = {
            TEST_KEY_PREFIX + "basic",
            TEST_KEY_PREFIX + "cluster_key1",
            TEST_KEY_PREFIX + "cluster_key2",
            TEST_KEY_PREFIX + "cluster_key3",
            TEST_KEY_PREFIX + "del1",
            TEST_KEY_PREFIX + "del2",
            TEST_KEY_PREFIX + "del3",
            TEST_KEY_PREFIX + "connection_test",
            TEST_KEY_PREFIX + "binary",
            TEST_KEY_PREFIX + "binary1",
            TEST_KEY_PREFIX + "binary2",
            TEST_KEY_PREFIX + "binary3",
            TEST_KEY_PREFIX + "large_value",
            TEST_KEY_PREFIX + "cluster_specific"
        };

        try {
            unifiedJedis.del(keysToDelete);
        } catch (Exception e) {
            // Ignore cleanup errors
        }

        // Clean up any remaining distributed keys
        try {
            for (int i = 0; i < 10; i++) {
                String key = TEST_KEY_PREFIX + "distributed_" + i;
                unifiedJedis.del(key);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
