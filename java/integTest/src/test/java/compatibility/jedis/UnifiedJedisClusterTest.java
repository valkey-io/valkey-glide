/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;

import glide.TestConfiguration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    // Server configuration - dynamically resolved from CI environment
    private static final Set<HostAndPort> clusterNodes;

    // GLIDE UnifiedJedis compatibility layer instance for cluster
    private UnifiedJedis unifiedJedis;

    static {
        String[] clusterHosts = TestConfiguration.CLUSTER_HOSTS;

        // Fail if cluster nodes configuration is not found in system properties
        if (clusterHosts.length == 0 || clusterHosts[0].trim().isEmpty()) {
            throw new IllegalStateException(
                    "Cluster nodes configuration not found in system properties. "
                            + "Please set 'test.server.cluster' system property with cluster node addresses "
                            + "(e.g., -Dtest.server.cluster=localhost:7000,localhost:7001,localhost:7002)");
        }

        clusterNodes = new HashSet<>();

        for (String host : clusterHosts) {
            String[] hostPort = host.trim().split(":");
            if (hostPort.length == 2) {
                try {
                    clusterNodes.add(new HostAndPort(hostPort[0], Integer.parseInt(hostPort[1])));
                } catch (NumberFormatException e) {
                    throw new IllegalStateException(
                            "Invalid port number in cluster configuration: "
                                    + host
                                    + ". "
                                    + "Expected format: host:port (e.g., localhost:7000)",
                            e);
                }
            } else {
                throw new IllegalStateException(
                        "Invalid cluster host format: "
                                + host
                                + ". "
                                + "Expected format: host:port (e.g., localhost:7000)");
            }
        }

        // Ensure we have at least one valid cluster node
        if (clusterNodes.isEmpty()) {
            throw new IllegalStateException(
                    "No valid cluster nodes found in configuration. Please provide valid cluster node"
                            + " addresses in 'test.server.cluster' system property (e.g.,"
                            + " -Dtest.server.cluster=localhost:7000,localhost:7001,localhost:7002)");
        }
    }

    @BeforeEach
    void setup() {
        // Create GLIDE UnifiedJedis compatibility layer instance for cluster
        unifiedJedis = new UnifiedJedis(clusterNodes);
        assertNotNull(
                unifiedJedis, "GLIDE UnifiedJedis cluster instance should be created successfully");
    }

    @Test
    void basic_set_and_get() {
        String testKey = UUID.randomUUID().toString();
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
        testData.put(UUID.randomUUID().toString(), "cluster_value1");
        testData.put(UUID.randomUUID().toString(), "cluster_value2");
        testData.put(UUID.randomUUID().toString(), "cluster_value3");

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
    void connection_operations() {
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
    void delete_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();
        String testKey3 = UUID.randomUUID().toString();

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
    void connection_state() {
        // Test that connection is not closed initially
        assertFalse(
                unifiedJedis.isClosed(), "Connection should not be closed initially in cluster mode");

        // Test basic operations work in cluster mode
        String testKey = UUID.randomUUID().toString();
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
    void binary_operations() {
        String keyString = UUID.randomUUID().toString();
        byte[] testKey = keyString.getBytes();
        byte[] testValue = "binary_value".getBytes();

        // Note: UnifiedJedis currently only supports del for binary keys
        // Set using string method first
        unifiedJedis.set(keyString, "binary_value");

        // Test binary key deletion in cluster mode - use the same key that was set
        long delResult = unifiedJedis.del(testKey);
        assertEquals(1, delResult, "Binary DEL should return 1 for deleted key in cluster mode");

        // Verify key is deleted
        String getResult = unifiedJedis.get(keyString);
        assertNull(getResult, "Key should not exist after binary deletion in cluster mode");
    }

    @Test
    void multiple_binary_deletion() {
        // Set up test keys using string methods
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

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
    void large_value_operations() {
        String testKey = UUID.randomUUID().toString();
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
        String testKey = UUID.randomUUID().toString();
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
    void cluster_key_distribution() {
        // Test that keys are distributed across cluster nodes
        Map<String, String> distributedKeys = new HashMap<>();

        // Create keys with different hash slots to test distribution
        for (int i = 0; i < 10; i++) {
            String key = UUID.randomUUID().toString() + i;
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
}
