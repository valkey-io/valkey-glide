/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.SERVER_VERSION;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Jedis compatibility test that validates GLIDE's Jedis compatibility layer functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected Jedis API and
 * behavior for core Redis operations.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisTest {

    private static final String TEST_KEY_PREFIX = "jedis_test:";

    // Server configuration - dynamically resolved from CI environment
    private static String redisHost;
    private static int redisPort;

    // GLIDE compatibility layer instance
    private Jedis jedis;

    @BeforeAll
    static void setupClass() {
        resolveServerAddress();
    }

    @BeforeEach
    void setup() {
        // Create GLIDE Jedis compatibility layer instance
        try {
            jedis = new Jedis(redisHost, redisPort);
            assertNotNull(jedis, "GLIDE Jedis instance should be created successfully");
        } catch (Exception e) {
            fail("Failed to create GLIDE Jedis instance: " + e.getMessage());
        }
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (jedis != null) {
            try {
                cleanupTestKeys(jedis);
                jedis.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @Test
    @Order(1)
    void testBasicSetAndGet() {
        String testKey = TEST_KEY_PREFIX + "basic";
        String testValue = "test_value_123";

        // Test GLIDE Jedis compatibility layer
        String setResult = jedis.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should return OK");

        String getResult = jedis.get(testKey);
        assertEquals(testValue, getResult, "GET should return the set value");
    }

    @Test
    @Order(2)
    void testMultipleOperations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "key1", "value1");
        testData.put(TEST_KEY_PREFIX + "key2", "value2");
        testData.put(TEST_KEY_PREFIX + "key3", "value3");

        // Test multiple SET operations
        Map<String, String> setResults = new HashMap<>();
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedis.set(entry.getKey(), entry.getValue());
            setResults.put(entry.getKey(), result);
            assertEquals("OK", result, "SET should return OK for " + entry.getKey());
        }

        // Test multiple GET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedis.get(entry.getKey());
            assertEquals(
                    entry.getValue(), result, "GET should return correct value for " + entry.getKey());
        }
    }

    @Test
    @Order(3)
    void testGetEx() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"),
                "GETEX command requires Redis 6.2.0 or higher");

        String testKey = TEST_KEY_PREFIX + "getex";
        String testValue = "getex_value";

        // Set initial value
        jedis.set(testKey, testValue);

        // Test GETEX with expiration
        GetExParams params = new GetExParams().ex(10); // 10 seconds
        String result = jedis.getEx(testKey, params);
        assertEquals(testValue, result, "GETEX should return the current value");

        // Verify TTL was set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 10, "TTL should be set correctly");
    }

    @Test
    @Order(4)
    void testBitOperations() {
        String testKey = TEST_KEY_PREFIX + "bitops";

        // Set a bit
        boolean result = jedis.setbit(testKey, 7, true);
        assertFalse(result, "Initial bit should be false");

        // Get the bit
        boolean bitValue = jedis.getbit(testKey, 7);
        assertTrue(bitValue, "Bit should be set to true");

        // Test bitcount
        long count = jedis.bitcount(testKey);
        assertEquals(1, count, "Should count 1 set bit");
    }

    @Test
    @Order(5)
    void testBitPos() {
        String testKey = TEST_KEY_PREFIX + "bitpos";

        // Set some bits
        jedis.setbit(testKey, 0, false);
        jedis.setbit(testKey, 1, true);
        jedis.setbit(testKey, 2, false);
        jedis.setbit(testKey, 3, true);

        // Test bitpos for first set bit
        long pos = jedis.bitpos(testKey, true);
        assertEquals(1, pos, "First set bit should be at position 1");

        // Test bitpos with parameters
        BitPosParams params = new BitPosParams(0, 1);
        pos = jedis.bitpos(testKey, true, params);
        assertEquals(1, pos, "First set bit in range should be at position 1");
    }

    @Test
    @Order(6)
    void testScanOperations() {
        // Set up test data
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            testData.put(TEST_KEY_PREFIX + "scan_" + i, "value_" + i);
        }

        // Set all test data
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            jedis.set(entry.getKey(), entry.getValue());
        }

        // Test SCAN
        ScanParams scanParams = new ScanParams().match(TEST_KEY_PREFIX + "scan_*").count(5);
        ScanResult<String> scanResult = jedis.scan("0", scanParams);

        assertNotNull(scanResult, "SCAN result should not be null");
        assertNotNull(scanResult.getResult(), "SCAN result list should not be null");
        assertTrue(scanResult.getResult().size() > 0, "SCAN should return some keys");
    }

    @Test
    @Order(7)
    void testPfmerge() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("2.8.9"),
                "HyperLogLog commands require Redis 2.8.9 or higher");

        String sourceKey1 = TEST_KEY_PREFIX + "pfmerge_source1";
        String sourceKey2 = TEST_KEY_PREFIX + "pfmerge_source2";
        String destKey = TEST_KEY_PREFIX + "pfmerge_dest";

        // Add elements to source HyperLogLogs
        jedis.pfadd(sourceKey1, "element1", "element2", "element3");
        jedis.pfadd(sourceKey2, "element3", "element4", "element5");

        // Test PFMERGE
        String result = jedis.pfmerge(destKey, sourceKey1, sourceKey2);
        assertEquals("OK", result, "PFMERGE should return OK");

        // Verify merged count
        long count = jedis.pfcount(destKey);
        assertEquals(5, count, "Merged HyperLogLog should have 5 unique elements");
    }

    @Test
    @Order(8)
    void testRandomKey() {
        // Set up some test keys
        jedis.set(TEST_KEY_PREFIX + "random1", "value1");
        jedis.set(TEST_KEY_PREFIX + "random2", "value2");
        jedis.set(TEST_KEY_PREFIX + "random3", "value3");

        // Test RANDOMKEY
        String randomKey = jedis.randomKey();
        assertNotNull(randomKey, "RANDOMKEY should return a key");
        assertTrue(randomKey.length() > 0, "Random key should not be empty");
    }

    @Test
    @Order(9)
    void testConnectionOperations() {
        // Test PING
        String pingResult = jedis.ping();
        assertEquals("PONG", pingResult, "PING should return PONG");

        // Test PING with message
        String message = "test_message";
        String pingWithMessage = jedis.ping(message);
        assertEquals(message, pingWithMessage, "PING with message should return the message");
    }

    @Test
    @Order(10)
    void testKeyOperations() {
        String testKey = TEST_KEY_PREFIX + "keyops";
        String testValue = "test_value";

        // Set a key
        jedis.set(testKey, testValue);

        // Test EXISTS
        boolean exists = jedis.exists(testKey);
        assertTrue(exists, "Key should exist");

        // Test TTL (should be -1 for no expiration)
        long ttl = jedis.ttl(testKey);
        assertEquals(-1, ttl, "Key should have no expiration");

        // Test EXPIRE
        long expireResult = jedis.expire(testKey, 60);
        assertEquals(1, expireResult, "EXPIRE should return 1 for success");

        // Verify TTL is now set
        ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");

        // Test DEL
        long delResult = jedis.del(testKey);
        assertEquals(1, delResult, "DEL should return 1 for deleted key");

        // Verify key is deleted
        exists = jedis.exists(testKey);
        assertFalse(exists, "Key should not exist after deletion");
    }

    // Helper methods
    private static void resolveServerAddress() {
        String host = System.getProperty("redis.host");
        String port = System.getProperty("redis.port");

        redisHost = (host != null) ? host : "localhost";
        redisPort = (port != null) ? Integer.parseInt(port) : 6379;
    }

    private void cleanupTestKeys(Jedis jedis) {
        try {
            // Delete all test keys
            String[] keysToDelete = {
                // Basic operation keys
                TEST_KEY_PREFIX + "basic",
                TEST_KEY_PREFIX + "key1",
                TEST_KEY_PREFIX + "key2",
                TEST_KEY_PREFIX + "key3",
                TEST_KEY_PREFIX + "getex",
                TEST_KEY_PREFIX + "bitops",
                TEST_KEY_PREFIX + "bitpos",
                TEST_KEY_PREFIX + "keyops",
                TEST_KEY_PREFIX + "random1",
                TEST_KEY_PREFIX + "random2",
                TEST_KEY_PREFIX + "random3",
                // Scan keys
                TEST_KEY_PREFIX + "scan_0",
                TEST_KEY_PREFIX + "scan_1",
                TEST_KEY_PREFIX + "scan_2",
                TEST_KEY_PREFIX + "scan_3",
                TEST_KEY_PREFIX + "scan_4",
                TEST_KEY_PREFIX + "scan_5",
                TEST_KEY_PREFIX + "scan_6",
                TEST_KEY_PREFIX + "scan_7",
                TEST_KEY_PREFIX + "scan_8",
                TEST_KEY_PREFIX + "scan_9",
                // HyperLogLog keys
                TEST_KEY_PREFIX + "pfmerge_source1",
                TEST_KEY_PREFIX + "pfmerge_source2",
                TEST_KEY_PREFIX + "pfmerge_dest"
            };

            jedis.del(keysToDelete);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
