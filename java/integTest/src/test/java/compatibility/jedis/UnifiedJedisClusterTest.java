/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.SERVER_VERSION;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import glide.TestConfiguration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.args.BitCountOption;
import redis.clients.jedis.args.ExpiryOption;
import redis.clients.jedis.params.BitPosParams;

/**
 * UnifiedJedis cluster compatibility test that validates GLIDE UnifiedJedis functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected UnifiedJedis API
 * and behavior for core Valkey operations in cluster mode.
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
    void register_client_name_and_version() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        String info = unifiedJedis.clientInfo();
        assertTrue(info.contains("lib-name=GlideJedisAdapter"));
        assertTrue(info.contains("lib-ver=unknown"));
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

    @Test
    void string_operations() {
        String testKey = UUID.randomUUID().toString();

        unifiedJedis.set(testKey, "hello");

        // Test APPEND
        long appendResult = unifiedJedis.append(testKey, " world");
        assertEquals(11, appendResult, "APPEND should return new length");
        assertEquals("hello world", unifiedJedis.get(testKey), "APPEND should concatenate strings");

        // Test GETRANGE
        String rangeResult = unifiedJedis.getrange(testKey, 0, 4);
        assertEquals("hello", rangeResult, "GETRANGE should return substring");

        // Test SETRANGE
        long setrangeResult = unifiedJedis.setrange(testKey, 6, "redis");
        assertEquals(11, setrangeResult, "SETRANGE should return string length");
        assertEquals("hello redis", unifiedJedis.get(testKey), "SETRANGE should modify substring");

        // Test STRLEN
        long strlenResult = unifiedJedis.strlen(testKey);
        assertEquals(11, strlenResult, "STRLEN should return string length");
    }

    @Test
    void numeric_operations() {
        String testKey = UUID.randomUUID().toString();

        unifiedJedis.set(testKey, "10");

        // Test INCR
        long incrResult = unifiedJedis.incr(testKey);
        assertEquals(11, incrResult, "INCR should increment by 1");

        // Test DECR
        long decrResult = unifiedJedis.decr(testKey);
        assertEquals(10, decrResult, "DECR should decrement by 1");

        // Test INCRBY
        long incrbyResult = unifiedJedis.incrBy(testKey, 5);
        assertEquals(15, incrbyResult, "INCRBY should increment by specified amount");

        // Test DECRBY
        long decrbyResult = unifiedJedis.decrBy(testKey, 3);
        assertEquals(12, decrbyResult, "DECRBY should decrement by specified amount");
    }

    @Test
    void expiration_operations() {
        String testKey = UUID.randomUUID().toString();

        unifiedJedis.set(testKey, "expiration_test");

        // Test EXPIRE
        long expireResult = unifiedJedis.expire(testKey, 3600);
        assertEquals(1, expireResult, "EXPIRE should set expiration");

        // Test TTL
        long ttlResult = unifiedJedis.ttl(testKey);
        assertTrue(ttlResult > 0 && ttlResult <= 3600, "TTL should return positive value");

        // Test PERSIST
        long persistResult = unifiedJedis.persist(testKey);
        assertEquals(1, persistResult, "PERSIST should remove expiration");

        long ttlAfterPersist = unifiedJedis.ttl(testKey);
        assertEquals(-1, ttlAfterPersist, "TTL should be -1 after PERSIST");
    }

    @Test
    void bit_operations() {
        String testKey = UUID.randomUUID().toString();

        unifiedJedis.set(testKey, "A"); // ASCII 'A' = 01000001

        // Test GETBIT
        boolean bit0 = unifiedJedis.getbit(testKey, 0);
        assertFalse(bit0, "First bit should be 0");

        boolean bit1 = unifiedJedis.getbit(testKey, 1);
        assertTrue(bit1, "Second bit should be 1");

        // Test SETBIT
        boolean oldBit = unifiedJedis.setbit(testKey, 0, true);
        assertFalse(oldBit, "SETBIT should return old bit value");

        // Test BITCOUNT
        long bitcountResult = unifiedJedis.bitcount(testKey);
        assertTrue(bitcountResult > 0, "BITCOUNT should return positive count");
    }

    @Test
    void key_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();

        unifiedJedis.set(testKey1, "test_value");

        // Test EXISTS
        boolean existsResult = unifiedJedis.exists(testKey1);
        assertTrue(existsResult, "EXISTS should return true for existing key");

        boolean notExistsResult = unifiedJedis.exists(testKey2);
        assertFalse(notExistsResult, "EXISTS should return false for non-existing key");

        // Test TYPE
        String typeResult = unifiedJedis.type(testKey1);
        assertEquals("string", typeResult, "TYPE should return 'string' for string key");
    }

    @Test
    void binary_key_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();
        byte[] testValue = "binary_test_value".getBytes();

        unifiedJedis.set(testKey, testValue);

        // Test binary GET
        byte[] getResult = unifiedJedis.get(testKey);
        assertArrayEquals(testValue, getResult, "Binary GET should return original value");

        // Test binary EXISTS
        boolean existsResult = unifiedJedis.exists(testKey);
        assertTrue(existsResult, "Binary EXISTS should return true for existing key");

        // Test binary APPEND
        byte[] appendData = " appended".getBytes();
        long appendResult = unifiedJedis.append(testKey, appendData);
        assertTrue(appendResult > testValue.length, "Binary APPEND should increase length");
    }

    @Test
    void binary_numeric_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();

        unifiedJedis.set(testKey, "100".getBytes());

        // Test binary INCR
        long incrResult = unifiedJedis.incr(testKey);
        assertEquals(101, incrResult, "Binary INCR should increment by 1");

        // Test binary DECR
        long decrResult = unifiedJedis.decr(testKey);
        assertEquals(100, decrResult, "Binary DECR should decrement by 1");

        // Test binary INCRBY
        long incrbyResult = unifiedJedis.incrBy(testKey, 10);
        assertEquals(110, incrbyResult, "Binary INCRBY should increment by specified amount");
    }

    @Test
    void binary_expiration_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();
        byte[] testValue = "binary_expiration_test".getBytes();

        unifiedJedis.set(testKey, testValue);

        // Test binary EXPIRE
        long expireResult = unifiedJedis.expire(testKey, 1800);
        assertEquals(1, expireResult, "Binary EXPIRE should set expiration");

        // Test binary TTL
        long ttlResult = unifiedJedis.ttl(testKey);
        assertTrue(ttlResult > 0 && ttlResult <= 1800, "Binary TTL should return positive value");

        // Test binary PERSIST
        long persistResult = unifiedJedis.persist(testKey);
        assertEquals(1, persistResult, "Binary PERSIST should remove expiration");
    }

    @Test
    void advanced_expiration_with_options() {
        String testKey = UUID.randomUUID().toString();

        unifiedJedis.set(testKey, "expiry_option_test");

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            // Test EXPIRE with ExpiryOption.NX (only set if no expiry exists)
            long expireResult = unifiedJedis.expire(testKey, 60, ExpiryOption.NX);
            assertEquals(1, expireResult, "EXPIRE with NX should set expiry on key without expiry");

            // Test EXPIRE with ExpiryOption.XX (only set if expiry exists)
            expireResult = unifiedJedis.expire(testKey, 120, ExpiryOption.XX);
            assertEquals(1, expireResult, "EXPIRE with XX should update expiry on existing expiry");
        } else {
            // For Valkey < 7.0.0, ExpiryOption is not supported
            try {
                unifiedJedis.expire(testKey, 60, ExpiryOption.NX);
                fail("Should have thrown exception for unsupported ExpiryOption on Valkey < 7.0.0");
            } catch (Exception e) {
                assertNotNull(e.getMessage(), "Should fail gracefully on Valkey < 7.0.0");
            }
        }
    }

    @Test
    void advanced_bit_operations_with_params() {
        String testKey = UUID.randomUUID().toString();

        unifiedJedis.set(testKey, "bit_test");

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            // Test BITPOS with BitPosParams
            BitPosParams bitPosParams = new BitPosParams(0, 10);
            long bitposResult = unifiedJedis.bitpos(testKey, true, bitPosParams);
            assertTrue(bitposResult >= -1, "BITPOS with BitPosParams should return valid position");

            // Test BITCOUNT with BitCountOption
            long bitcountResult = unifiedJedis.bitcount(testKey, 0, 2, BitCountOption.BYTE);
            assertTrue(bitcountResult >= 0, "BITCOUNT with BYTE option should return count");
        } else {
            // For Valkey < 7.0.0, these options are not supported
            try {
                BitPosParams bitPosParams = new BitPosParams(0, 10);
                unifiedJedis.bitpos(testKey, true, bitPosParams);
                fail("Should have thrown exception for unsupported BitmapIndexType on Valkey < 7.0.0");
            } catch (Exception e) {
                assertNotNull(e.getMessage(), "Should fail gracefully on Valkey < 7.0.0");
            }
        }
    }

    @Test
    void expiration_time_operations() {
        String testKey = UUID.randomUUID().toString();

        unifiedJedis.set(testKey, "expiration_time_test");
        unifiedJedis.expire(testKey, 3600);

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            // Test EXPIRETIME (Valkey 7.0+ only)
            long expireTimeResult = unifiedJedis.expireTime(testKey);
            assertTrue(expireTimeResult > 0, "EXPIRETIME should return positive timestamp");

            // Test PEXPIRETIME (Valkey 7.0+ only)
            long pexpireTimeResult = unifiedJedis.pexpireTime(testKey);
            assertTrue(pexpireTimeResult > 0, "PEXPIRETIME should return positive timestamp");
        } else {
            // For Valkey < 7.0.0, EXPIRETIME/PEXPIRETIME commands don't exist
            try {
                unifiedJedis.expireTime(testKey);
                fail("Should have thrown exception for unsupported EXPIRETIME on Valkey < 7.0.0");
            } catch (Exception e) {
                assertNotNull(e.getMessage(), "Should fail gracefully on Valkey < 7.0.0");
            }
        }
    }
}
