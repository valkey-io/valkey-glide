/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.CLUSTER_HOSTS;
import static glide.TestConfiguration.SERVER_VERSION;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import redis.clients.jedis.ConnectionPool;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.args.BitCountOption;
import redis.clients.jedis.args.ExpiryOption;
import redis.clients.jedis.params.BitPosParams;

/**
 * JedisCluster compatibility test that validates the new JedisCluster extends UnifiedJedis
 * properly.
 */
public class JedisClusterTest {

    private static final Set<HostAndPort> clusterNodes;
    private JedisCluster jedisCluster;

    static {
        String[] clusterHosts = CLUSTER_HOSTS;

        if (clusterHosts.length == 0 || clusterHosts[0].trim().isEmpty()) {
            throw new IllegalStateException("Cluster nodes configuration not found");
        }

        clusterNodes = new HashSet<>();
        for (String host : clusterHosts) {
            String[] hostPort = host.trim().split(":");
            if (hostPort.length == 2) {
                try {
                    clusterNodes.add(new HostAndPort(hostPort[0], Integer.parseInt(hostPort[1])));
                } catch (NumberFormatException e) {
                    throw new IllegalStateException(
                            "Invalid port number in cluster configuration: " + host, e);
                }
            }
        }
    }

    @BeforeEach
    void setup() {
        jedisCluster = new JedisCluster(clusterNodes);
        assertNotNull(jedisCluster, "JedisCluster instance should be created successfully");
    }

    @AfterEach
    void tearDown() {
        if (jedisCluster != null) {
            jedisCluster.close();
        }
    }

    @Test
    void basic_set_and_get() {
        String testKey = UUID.randomUUID().toString();
        String testValue = "jedis_cluster_test_value";

        String setResult = jedisCluster.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should return OK in cluster mode");

        String getResult = jedisCluster.get(testKey);
        assertEquals(testValue, getResult, "GET should return the set value in cluster mode");
    }

    @Test
    void single_node_constructor() {
        HostAndPort firstNode = clusterNodes.iterator().next();

        try (JedisCluster cluster = new JedisCluster(firstNode)) {
            String testKey = UUID.randomUUID().toString();
            String testValue = "single_node_test_value";

            cluster.set(testKey, testValue);
            String result = cluster.get(testKey);

            assertEquals(testValue, result, "JedisCluster should work with single node constructor");

            // Test getClusterNodes works with single node constructor
            Map<String, ConnectionPool> nodes = cluster.getClusterNodes();
            assertNotNull(nodes, "getClusterNodes should work with single node constructor");
            assertEquals(1, nodes.size(), "Single node constructor should return one node");
        }
    }

    @Test
    void multiple_operations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(UUID.randomUUID().toString(), "cluster_value1");
        testData.put(UUID.randomUUID().toString(), "cluster_value2");
        testData.put(UUID.randomUUID().toString(), "cluster_value3");

        // Test multiple SET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedisCluster.set(entry.getKey(), entry.getValue());
            assertEquals("OK", result, "SET should return OK for " + entry.getKey());
        }

        // Test multiple GET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedisCluster.get(entry.getKey());
            assertEquals(
                    entry.getValue(), result, "GET should return correct value for " + entry.getKey());
        }
    }

    @Test
    void delete_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();
        String testKey3 = UUID.randomUUID().toString();

        // Set up test data
        jedisCluster.set(testKey1, "delete_test1");
        jedisCluster.set(testKey2, "delete_test2");
        jedisCluster.set(testKey3, "delete_test3");

        // Test single key deletion
        long delResult = jedisCluster.del(testKey1);
        assertEquals(1, delResult, "DEL should return 1 for single key deletion");

        // Verify key is deleted
        String getResult = jedisCluster.get(testKey1);
        assertNull(getResult, "GET should return null for deleted key");

        // Test multiple key deletion
        long multiDelResult = jedisCluster.del(testKey2, testKey3);
        assertEquals(2, multiDelResult, "DEL should return 2 for two key deletion");
    }

    @Test
    void string_operations() {
        String testKey = UUID.randomUUID().toString();

        jedisCluster.set(testKey, "hello");

        // Test APPEND
        long appendResult = jedisCluster.append(testKey, " world");
        assertEquals(11, appendResult, "APPEND should return new length");
        assertEquals("hello world", jedisCluster.get(testKey), "APPEND should concatenate strings");

        // Test GETRANGE
        String rangeResult = jedisCluster.getrange(testKey, 0, 4);
        assertEquals("hello", rangeResult, "GETRANGE should return substring");

        // Test SETRANGE
        long setrangeResult = jedisCluster.setrange(testKey, 6, "redis");
        assertEquals(11, setrangeResult, "SETRANGE should return string length");
        assertEquals("hello redis", jedisCluster.get(testKey), "SETRANGE should modify substring");

        // Test STRLEN
        long strlenResult = jedisCluster.strlen(testKey);
        assertEquals(11, strlenResult, "STRLEN should return string length");
    }

    @Test
    void numeric_operations() {
        String testKey = UUID.randomUUID().toString();

        jedisCluster.set(testKey, "10");

        // Test INCR
        long incrResult = jedisCluster.incr(testKey);
        assertEquals(11, incrResult, "INCR should increment by 1");

        // Test DECR
        long decrResult = jedisCluster.decr(testKey);
        assertEquals(10, decrResult, "DECR should decrement by 1");

        // Test INCRBY
        long incrbyResult = jedisCluster.incrBy(testKey, 5);
        assertEquals(15, incrbyResult, "INCRBY should increment by specified amount");

        // Test DECRBY
        long decrbyResult = jedisCluster.decrBy(testKey, 3);
        assertEquals(12, decrbyResult, "DECRBY should decrement by specified amount");
    }

    @Test
    void expiration_operations() {
        String testKey = UUID.randomUUID().toString();

        jedisCluster.set(testKey, "expiration_test");

        // Test EXPIRE
        long expireResult = jedisCluster.expire(testKey, 3600);
        assertEquals(1, expireResult, "EXPIRE should set expiration");

        // Test TTL
        long ttlResult = jedisCluster.ttl(testKey);
        assertTrue(ttlResult > 0 && ttlResult <= 3600, "TTL should return positive value");

        // Test PERSIST
        long persistResult = jedisCluster.persist(testKey);
        assertEquals(1, persistResult, "PERSIST should remove expiration");

        long ttlAfterPersist = jedisCluster.ttl(testKey);
        assertEquals(-1, ttlAfterPersist, "TTL should be -1 after PERSIST");
    }

    @Test
    void bit_operations() {
        String testKey = UUID.randomUUID().toString();

        jedisCluster.set(testKey, "A"); // ASCII 'A' = 01000001

        // Test GETBIT
        boolean bit0 = jedisCluster.getbit(testKey, 0);
        assertFalse(bit0, "First bit should be 0");

        boolean bit1 = jedisCluster.getbit(testKey, 1);
        assertTrue(bit1, "Second bit should be 1");

        // Test SETBIT
        boolean oldBit = jedisCluster.setbit(testKey, 0, true);
        assertFalse(oldBit, "SETBIT should return old bit value");

        // Test BITCOUNT
        long bitcountResult = jedisCluster.bitcount(testKey);
        assertTrue(bitcountResult > 0, "BITCOUNT should return positive count");
    }

    @Test
    void key_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();

        jedisCluster.set(testKey1, "test_value");

        // Test EXISTS
        boolean existsResult = jedisCluster.exists(testKey1);
        assertTrue(existsResult, "EXISTS should return true for existing key");

        boolean notExistsResult = jedisCluster.exists(testKey2);
        assertFalse(notExistsResult, "EXISTS should return false for non-existing key");

        // Test TYPE
        String typeResult = jedisCluster.type(testKey1);
        assertEquals("string", typeResult, "TYPE should return 'string' for string key");
    }

    @Test
    void binary_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();
        byte[] testValue = "binary_test_value".getBytes();

        jedisCluster.set(testKey, testValue);

        // Test binary GET
        byte[] getResult = jedisCluster.get(testKey);
        assertArrayEquals(testValue, getResult, "Binary GET should return original value");

        // Test binary EXISTS
        boolean existsResult = jedisCluster.exists(testKey);
        assertTrue(existsResult, "Binary EXISTS should return true for existing key");

        // Test binary APPEND
        byte[] appendData = " appended".getBytes();
        long appendResult = jedisCluster.append(testKey, appendData);
        assertTrue(appendResult > testValue.length, "Binary APPEND should increase length");
    }

    @Test
    void advanced_expiration_with_options() {
        String testKey = UUID.randomUUID().toString();

        jedisCluster.set(testKey, "expiry_option_test");

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            // Test EXPIRE with ExpiryOption.NX (only set if no expiry exists)
            long expireResult = jedisCluster.expire(testKey, 60, ExpiryOption.NX);
            assertEquals(1, expireResult, "EXPIRE with NX should set expiry on key without expiry");

            // Test EXPIRE with ExpiryOption.XX (only set if expiry exists)
            expireResult = jedisCluster.expire(testKey, 120, ExpiryOption.XX);
            assertEquals(1, expireResult, "EXPIRE with XX should update expiry on existing expiry");
        } else {
            // For Redis < 7.0.0, ExpiryOption is not supported
            try {
                jedisCluster.expire(testKey, 60, ExpiryOption.NX);
                fail("Should have thrown exception for unsupported ExpiryOption on Redis < 7.0.0");
            } catch (Exception e) {
                assertNotNull(e.getMessage(), "Should fail gracefully on Redis < 7.0.0");
            }
        }
    }

    @Test
    void advanced_bit_operations_with_params() {
        String testKey = UUID.randomUUID().toString();

        jedisCluster.set(testKey, "bit_test");

        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            // Test BITPOS with BitPosParams
            BitPosParams bitPosParams = new BitPosParams(0, 10);
            long bitposResult = jedisCluster.bitpos(testKey, true, bitPosParams);
            assertTrue(bitposResult >= -1, "BITPOS with BitPosParams should return valid position");

            // Test BITCOUNT with BitCountOption
            long bitcountResult = jedisCluster.bitcount(testKey, 0, 2, BitCountOption.BYTE);
            assertTrue(bitcountResult >= 0, "BITCOUNT with BYTE option should return count");
        } else {
            // For Redis < 7.0.0, these options are not supported
            try {
                BitPosParams bitPosParams = new BitPosParams(0, 10);
                jedisCluster.bitpos(testKey, true, bitPosParams);
                fail("Should have thrown exception for unsupported BitmapIndexType on Redis < 7.0.0");
            } catch (Exception e) {
                assertNotNull(e.getMessage(), "Should fail gracefully on Redis < 7.0.0");
            }
        }
    }

    @Test
    void cluster_nodes_support() {
        // Test that getClusterNodes() returns cluster node information
        Map<String, ConnectionPool> clusterNodes = jedisCluster.getClusterNodes();
        assertNotNull(clusterNodes, "getClusterNodes should return a map");
        assertFalse(clusterNodes.isEmpty(), "getClusterNodes should return configured nodes");

        // Verify that node keys are in HOST:PORT format
        for (String nodeKey : clusterNodes.keySet()) {
            assertTrue(nodeKey.contains(":"), "Node key should be in HOST:PORT format: " + nodeKey);
        }

        // Test that getConnectionFromSlot still throws UnsupportedOperationException
        assertThrows(
                UnsupportedOperationException.class,
                () -> jedisCluster.getConnectionFromSlot(0),
                "getConnectionFromSlot should throw UnsupportedOperationException");
    }
}
