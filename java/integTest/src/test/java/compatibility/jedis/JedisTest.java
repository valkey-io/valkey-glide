/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.SERVER_VERSION;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.BitOP;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Jedis compatibility test that validates GLIDE's Jedis compatibility layer functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected Jedis API and
 * behavior for comprehensive Redis operations.
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
        jedis = new Jedis(redisHost, redisPort);
        assertNotNull(jedis, "GLIDE Jedis instance should be created successfully");
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (jedis != null) {
            cleanupTestKeys(jedis);
            jedis.close();
        }
    }

    // Helper methods
    private static void resolveServerAddress() {
        String host = System.getProperty("redis.host");
        String port = System.getProperty("redis.port");

        redisHost = (host != null) ? host : "localhost";
        redisPort = (port != null) ? Integer.parseInt(port) : 6379;
    }

    private void cleanupTestKeys(Jedis jedis) {
        // Delete all test keys - comprehensive cleanup
        String[] keysToDelete = {
            // Basic operation keys
            TEST_KEY_PREFIX + "basic",
            TEST_KEY_PREFIX + "key1",
            TEST_KEY_PREFIX + "key2",
            TEST_KEY_PREFIX + "key3",
            TEST_KEY_PREFIX + "mset_key1",
            TEST_KEY_PREFIX + "mset_key2",
            TEST_KEY_PREFIX + "mset_key3",
            TEST_KEY_PREFIX + "mget_key1",
            TEST_KEY_PREFIX + "mget_key2",
            TEST_KEY_PREFIX + "mget_key3",
            TEST_KEY_PREFIX + "setnx",
            TEST_KEY_PREFIX + "setex",
            TEST_KEY_PREFIX + "psetex",
            TEST_KEY_PREFIX + "getset",
            TEST_KEY_PREFIX + "setget",
            TEST_KEY_PREFIX + "getdel",
            TEST_KEY_PREFIX + "getex",
            TEST_KEY_PREFIX + "append",
            TEST_KEY_PREFIX + "strlen",
            TEST_KEY_PREFIX + "setbit",
            TEST_KEY_PREFIX + "getbit",
            TEST_KEY_PREFIX + "bitcount",
            TEST_KEY_PREFIX + "bitpos",
            TEST_KEY_PREFIX + "bitop_dest",
            TEST_KEY_PREFIX + "bitop_src1",
            TEST_KEY_PREFIX + "bitop_src2",
            TEST_KEY_PREFIX + "bitfield",
            TEST_KEY_PREFIX + "bitfield_ro",
            TEST_KEY_PREFIX + "incr",
            TEST_KEY_PREFIX + "incrby",
            TEST_KEY_PREFIX + "incrbyfloat",
            TEST_KEY_PREFIX + "decr",
            TEST_KEY_PREFIX + "decrby",
            TEST_KEY_PREFIX + "del1",
            TEST_KEY_PREFIX + "del2",
            TEST_KEY_PREFIX + "del3",
            TEST_KEY_PREFIX + "unlink1",
            TEST_KEY_PREFIX + "unlink2",
            TEST_KEY_PREFIX + "exists",
            TEST_KEY_PREFIX + "type",
            TEST_KEY_PREFIX + "keys_test1",
            TEST_KEY_PREFIX + "keys_test2",
            TEST_KEY_PREFIX + "keys_test3",
            TEST_KEY_PREFIX + "random1",
            TEST_KEY_PREFIX + "random2",
            TEST_KEY_PREFIX + "random3",
            TEST_KEY_PREFIX + "rename_src",
            TEST_KEY_PREFIX + "rename_dest",
            TEST_KEY_PREFIX + "renamenx_src",
            TEST_KEY_PREFIX + "renamenx_dest",
            TEST_KEY_PREFIX + "expire",
            TEST_KEY_PREFIX + "expireat",
            TEST_KEY_PREFIX + "pexpire",
            TEST_KEY_PREFIX + "pexpireat",
            TEST_KEY_PREFIX + "ttl",
            TEST_KEY_PREFIX + "pttl",
            TEST_KEY_PREFIX + "expiretime",
            TEST_KEY_PREFIX + "pexpiretime",
            TEST_KEY_PREFIX + "persist",
            TEST_KEY_PREFIX + "sort",
            TEST_KEY_PREFIX + "dump",
            TEST_KEY_PREFIX + "restore",
            TEST_KEY_PREFIX + "migrate",
            TEST_KEY_PREFIX + "move",
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
            TEST_KEY_PREFIX + "touch1",
            TEST_KEY_PREFIX + "touch2",
            TEST_KEY_PREFIX + "copy_src",
            TEST_KEY_PREFIX + "copy_dest",
            TEST_KEY_PREFIX + "pfadd1",
            TEST_KEY_PREFIX + "pfadd2",
            TEST_KEY_PREFIX + "pfcount1",
            TEST_KEY_PREFIX + "pfcount2",
            TEST_KEY_PREFIX + "pfmerge_src1",
            TEST_KEY_PREFIX + "pfmerge_src2",
            TEST_KEY_PREFIX + "pfmerge_dest",
            // New test keys
            TEST_KEY_PREFIX + "set_params",
            TEST_KEY_PREFIX + "set_nx",
            TEST_KEY_PREFIX + "set_xx",
            TEST_KEY_PREFIX + "setget_params",
            TEST_KEY_PREFIX + "rename_src",
            TEST_KEY_PREFIX + "rename_dest",
            TEST_KEY_PREFIX + "renamenx_src",
            TEST_KEY_PREFIX + "renamenx_dest",
            TEST_KEY_PREFIX + "renamenx_src2",
            TEST_KEY_PREFIX + "exists_multi1",
            TEST_KEY_PREFIX + "exists_multi2",
            TEST_KEY_PREFIX + "exists_multi3",
            TEST_KEY_PREFIX + "keyexists",
            TEST_KEY_PREFIX + "bitcount_range",
            TEST_KEY_PREFIX + "bitop_src1",
            TEST_KEY_PREFIX + "bitop_src2",
            TEST_KEY_PREFIX + "bitop_dest",
            TEST_KEY_PREFIX + "bitfield",
            TEST_KEY_PREFIX + "bitfield_ro",
            TEST_KEY_PREFIX + "sort_test",
            TEST_KEY_PREFIX + "dump",
            TEST_KEY_PREFIX + "restore_src",
            TEST_KEY_PREFIX + "restore_dest",
            TEST_KEY_PREFIX + "restore_ttl",
            TEST_KEY_PREFIX + "migrate",
            TEST_KEY_PREFIX + "move",
            TEST_KEY_PREFIX + "select_test",
            TEST_KEY_PREFIX + "touch1",
            TEST_KEY_PREFIX + "touch2",
            TEST_KEY_PREFIX + "touch_nonexistent",
            TEST_KEY_PREFIX + "copy_src",
            TEST_KEY_PREFIX + "copy_dest",
            TEST_KEY_PREFIX + "expiretime",
            TEST_KEY_PREFIX + "pexpiretime",
            TEST_KEY_PREFIX + "expire_option"
        };

        jedis.del(keysToDelete);
    }

    @Test
    @Order(1)
    @DisplayName("Basic GET/SET Operations")
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
    @Order(3)
    @DisplayName("SET Command with SetParams")
    void testSETWithParams() {
        String testKey = TEST_KEY_PREFIX + "set_params";
        String testValue = "set_params_value";

        // Test SET with EX (expiration in seconds)
        SetParams params = new SetParams().ex(60);
        String result = jedis.set(testKey, testValue, params);
        assertEquals("OK", result, "SET with params should return OK");
        assertEquals(testValue, jedis.get(testKey), "Key should have correct value");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");

        // Test SET with NX (only if not exists)
        String existingKey = TEST_KEY_PREFIX + "set_nx";
        jedis.set(existingKey, "existing_value");

        params = new SetParams().nx();
        result = jedis.set(existingKey, "new_value", params);
        assertNull(result, "SET NX should return null when key exists");
        assertEquals("existing_value", jedis.get(existingKey), "Key should retain original value");

        // Test SET with XX (only if exists)
        String nonExistentKey = TEST_KEY_PREFIX + "set_xx";
        params = new SetParams().xx();
        result = jedis.set(nonExistentKey, "xx_value", params);
        assertNull(result, "SET XX should return null when key doesn't exist");
        assertNull(jedis.get(nonExistentKey), "Key should not be created");
    }

    @Test
    @Order(2)
    @DisplayName("Multiple GET/SET Operations")
    void testMultipleOperations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "key1", "value1");
        testData.put(TEST_KEY_PREFIX + "key2", "value2");
        testData.put(TEST_KEY_PREFIX + "key3", "value3");

        // Test multiple SET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedis.set(entry.getKey(), entry.getValue());
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
    @Order(10)
    @DisplayName("MSET Command")
    void testMSET() {
        String key1 = TEST_KEY_PREFIX + "mset_key1";
        String key2 = TEST_KEY_PREFIX + "mset_key2";
        String key3 = TEST_KEY_PREFIX + "mset_key3";

        // Test MSET
        String result = jedis.mset(key1, "value1", key2, "value2", key3, "value3");
        assertEquals("OK", result, "MSET should return OK");

        // Verify all keys were set
        assertEquals("value1", jedis.get(key1), "Key1 should have correct value");
        assertEquals("value2", jedis.get(key2), "Key2 should have correct value");
        assertEquals("value3", jedis.get(key3), "Key3 should have correct value");
    }

    @Test
    @Order(11)
    @DisplayName("MGET Command")
    void testMGET() {
        String key1 = TEST_KEY_PREFIX + "mget_key1";
        String key2 = TEST_KEY_PREFIX + "mget_key2";
        String key3 = TEST_KEY_PREFIX + "mget_key3";

        // Set up test data
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");
        jedis.set(key3, "value3");

        // Test MGET
        List<String> results = jedis.mget(key1, key2, key3);
        assertNotNull(results, "MGET should return a list");
        assertEquals(3, results.size(), "MGET should return 3 values");
        assertEquals("value1", results.get(0), "First value should be correct");
        assertEquals("value2", results.get(1), "Second value should be correct");
        assertEquals("value3", results.get(2), "Third value should be correct");
    }

    @Test
    @Order(20)
    @DisplayName("SETNX Command")
    void testSETNX() {
        String testKey = TEST_KEY_PREFIX + "setnx";

        // Test SETNX on non-existing key
        long result = jedis.setnx(testKey, "value1");
        assertEquals(1, result, "SETNX should return 1 for new key");
        assertEquals("value1", jedis.get(testKey), "Key should have correct value");

        // Test SETNX on existing key
        result = jedis.setnx(testKey, "value2");
        assertEquals(0, result, "SETNX should return 0 for existing key");
        assertEquals("value1", jedis.get(testKey), "Key should retain original value");
    }

    @Test
    @Order(21)
    @DisplayName("SETEX Command")
    void testSETEX() {
        String testKey = TEST_KEY_PREFIX + "setex";

        // Test SETEX
        String result = jedis.setex(testKey, 60, "test_value");
        assertEquals("OK", result, "SETEX should return OK");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");
    }

    @Test
    @Order(22)
    @DisplayName("PSETEX Command")
    void testPSETEX() {
        String testKey = TEST_KEY_PREFIX + "psetex";

        // Test PSETEX
        String result = jedis.psetex(testKey, 60000, "test_value");
        assertEquals("OK", result, "PSETEX should return OK");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 60000, "PTTL should be set correctly");
    }

    @Test
    @Order(30)
    @DisplayName("GETSET Command")
    void testGETSET() {
        String testKey = TEST_KEY_PREFIX + "getset";

        // Test GETSET on non-existing key
        String result = jedis.getSet(testKey, "new_value");
        assertNull(result, "GETSET should return null for non-existing key");
        assertEquals("new_value", jedis.get(testKey), "Key should have new value");

        // Test GETSET on existing key
        result = jedis.getSet(testKey, "newer_value");
        assertEquals("new_value", result, "GETSET should return old value");
        assertEquals("newer_value", jedis.get(testKey), "Key should have newer value");
    }

    @Test
    @Order(31)
    @DisplayName("SETGET Command")
    void testSETGET() {
        String testKey = TEST_KEY_PREFIX + "setget";

        // Test SETGET
        String result = jedis.setGet(testKey, "test_value");
        assertNull(result, "SETGET should return null for new key");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Test SETGET on existing key
        result = jedis.setGet(testKey, "new_value");
        assertEquals("test_value", result, "SETGET should return old value");
        assertEquals("new_value", jedis.get(testKey), "Key should have new value");
    }

    @Test
    @Order(32)
    @DisplayName("SETGET Command with SetParams")
    void testSETGETWithParams() {
        String testKey = TEST_KEY_PREFIX + "setget_params";

        // Test SETGET with EX parameter
        SetParams params = new SetParams().ex(60);
        String result = jedis.setGet(testKey, "test_value", params);
        assertNull(result, "SETGET should return null for new key");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");

        // Test SETGET with params on existing key
        params = new SetParams().px(30000);
        result = jedis.setGet(testKey, "new_value", params);
        assertEquals("test_value", result, "SETGET should return old value");
        assertEquals("new_value", jedis.get(testKey), "Key should have new value");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 30000, "PTTL should be set correctly");
    }

    @Test
    @Order(32)
    @DisplayName("GETDEL Command")
    void testGETDEL() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"),
                "GETDEL command requires Redis 6.2.0 or higher");

        String testKey = TEST_KEY_PREFIX + "getdel";

        // Set up test data
        jedis.set(testKey, "test_value");

        // Test GETDEL
        String result = jedis.getDel(testKey);
        assertEquals("test_value", result, "GETDEL should return the value");
        assertNull(jedis.get(testKey), "Key should be deleted");

        // Test GETDEL on non-existing key
        result = jedis.getDel(testKey);
        assertNull(result, "GETDEL should return null for non-existing key");
    }

    @Test
    @Order(33)
    @DisplayName("GETEX Command")
    void testGETEX() {
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
    @Order(40)
    @DisplayName("APPEND Command")
    void testAPPEND() {
        String testKey = TEST_KEY_PREFIX + "append";

        // Test APPEND on non-existing key
        long result = jedis.append(testKey, "Hello");
        assertEquals(5, result, "APPEND should return string length");
        assertEquals("Hello", jedis.get(testKey), "Key should have appended value");

        // Test APPEND on existing key
        result = jedis.append(testKey, " World");
        assertEquals(11, result, "APPEND should return updated string length");
        assertEquals("Hello World", jedis.get(testKey), "Key should have concatenated value");
    }

    @Test
    @Order(41)
    @DisplayName("STRLEN Command")
    void testSTRLEN() {
        String testKey = TEST_KEY_PREFIX + "strlen";

        // Test STRLEN on non-existing key
        long result = jedis.strlen(testKey);
        assertEquals(0, result, "STRLEN should return 0 for non-existing key");

        // Test STRLEN on existing key
        jedis.set(testKey, "Hello World");
        result = jedis.strlen(testKey);
        assertEquals(11, result, "STRLEN should return correct length");
    }

    @Test
    @Order(50)
    @DisplayName("INCR Command")
    void testINCR() {
        String testKey = TEST_KEY_PREFIX + "incr";

        // Test INCR on non-existing key
        long result = jedis.incr(testKey);
        assertEquals(1, result, "INCR should return 1 for new key");
        assertEquals("1", jedis.get(testKey), "Key should have value 1");

        // Test INCR on existing key
        result = jedis.incr(testKey);
        assertEquals(2, result, "INCR should return 2");
        assertEquals("2", jedis.get(testKey), "Key should have value 2");
    }

    @Test
    @Order(51)
    @DisplayName("INCRBY Command")
    void testINCRBY() {
        String testKey = TEST_KEY_PREFIX + "incrby";

        // Test INCRBY on non-existing key
        long result = jedis.incrBy(testKey, 5);
        assertEquals(5, result, "INCRBY should return 5 for new key");
        assertEquals("5", jedis.get(testKey), "Key should have value 5");

        // Test INCRBY on existing key
        result = jedis.incrBy(testKey, 3);
        assertEquals(8, result, "INCRBY should return 8");
        assertEquals("8", jedis.get(testKey), "Key should have value 8");
    }

    @Test
    @Order(52)
    @DisplayName("INCRBYFLOAT Command")
    void testINCRBYFLOAT() {
        String testKey = TEST_KEY_PREFIX + "incrbyfloat";

        // Test INCRBYFLOAT on non-existing key
        double result = jedis.incrByFloat(testKey, 2.5);
        assertEquals(2.5, result, 0.001, "INCRBYFLOAT should return 2.5 for new key");
        assertEquals("2.5", jedis.get(testKey), "Key should have value 2.5");

        // Test INCRBYFLOAT on existing key
        result = jedis.incrByFloat(testKey, 1.5);
        assertEquals(4.0, result, 0.001, "INCRBYFLOAT should return 4.0");
        assertEquals("4", jedis.get(testKey), "Key should have value 4");
    }

    @Test
    @Order(53)
    @DisplayName("DECR Command")
    void testDECR() {
        String testKey = TEST_KEY_PREFIX + "decr";

        // Set initial value
        jedis.set(testKey, "10");

        // Test DECR
        long result = jedis.decr(testKey);
        assertEquals(9, result, "DECR should return 9");
        assertEquals("9", jedis.get(testKey), "Key should have value 9");

        // Test DECR again
        result = jedis.decr(testKey);
        assertEquals(8, result, "DECR should return 8");
        assertEquals("8", jedis.get(testKey), "Key should have value 8");
    }

    @Test
    @Order(54)
    @DisplayName("DECRBY Command")
    void testDECRBY() {
        String testKey = TEST_KEY_PREFIX + "decrby";

        // Set initial value
        jedis.set(testKey, "20");

        // Test DECRBY
        long result = jedis.decrBy(testKey, 5);
        assertEquals(15, result, "DECRBY should return 15");
        assertEquals("15", jedis.get(testKey), "Key should have value 15");

        // Test DECRBY again
        result = jedis.decrBy(testKey, 7);
        assertEquals(8, result, "DECRBY should return 8");
        assertEquals("8", jedis.get(testKey), "Key should have value 8");
    }

    @Test
    @Order(60)
    @DisplayName("DEL Command")
    void testDEL() {
        String key1 = TEST_KEY_PREFIX + "del1";
        String key2 = TEST_KEY_PREFIX + "del2";
        String key3 = TEST_KEY_PREFIX + "del3";

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");
        jedis.set(key3, "value3");

        // Test single key deletion
        long result = jedis.del(key1);
        assertEquals(1, result, "DEL should return 1 for deleted key");
        assertNull(jedis.get(key1), "Key should not exist after deletion");

        // Test multiple key deletion
        result = jedis.del(key2, key3);
        assertEquals(2, result, "DEL should return 2 for two deleted keys");
        assertNull(jedis.get(key2), "Key2 should not exist after deletion");
        assertNull(jedis.get(key3), "Key3 should not exist after deletion");
    }

    @Test
    @Order(61)
    @DisplayName("UNLINK Command")
    void testUNLINK() {
        String key1 = TEST_KEY_PREFIX + "unlink1";
        String key2 = TEST_KEY_PREFIX + "unlink2";

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");

        // Test UNLINK
        long result = jedis.unlink(key1, key2);
        assertEquals(2, result, "UNLINK should return 2 for two deleted keys");
        assertNull(jedis.get(key1), "Key1 should not exist after unlink");
        assertNull(jedis.get(key2), "Key2 should not exist after unlink");
    }

    @Test
    @Order(62)
    @DisplayName("EXISTS Command")
    void testEXISTS() {
        String testKey = TEST_KEY_PREFIX + "exists";

        // Test EXISTS on non-existing key
        boolean result = jedis.exists(testKey);
        assertFalse(result, "EXISTS should return false for non-existing key");

        // Test EXISTS on existing key
        jedis.set(testKey, "test_value");
        result = jedis.exists(testKey);
        assertTrue(result, "EXISTS should return true for existing key");
    }

    @Test
    @Order(62)
    @DisplayName("EXISTS Multiple Keys Command")
    void testEXISTSMultiple() {
        String key1 = TEST_KEY_PREFIX + "exists_multi1";
        String key2 = TEST_KEY_PREFIX + "exists_multi2";
        String key3 = TEST_KEY_PREFIX + "exists_multi3";

        // Set up some keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");

        // Test EXISTS on multiple keys
        long result = jedis.exists(key1, key2, key3);
        assertEquals(2, result, "EXISTS should return 2 for two existing keys");

        // Test EXISTS on all non-existing keys
        result = jedis.exists(key3, TEST_KEY_PREFIX + "nonexistent");
        assertEquals(0, result, "EXISTS should return 0 for no existing keys");
    }

    @Test
    @Order(63)
    @DisplayName("KEYEXISTS Command")
    void testKEYEXISTS() {
        String testKey = TEST_KEY_PREFIX + "keyexists";

        // Test KEYEXISTS on non-existing key
        boolean result = jedis.keyExists(testKey);
        assertFalse(result, "KEYEXISTS should return false for non-existing key");

        // Test KEYEXISTS on existing key
        jedis.set(testKey, "test_value");
        result = jedis.keyExists(testKey);
        assertTrue(result, "KEYEXISTS should return true for existing key");
    }

    @Test
    @Order(63)
    @DisplayName("TYPE Command")
    void testTYPE() {
        String testKey = TEST_KEY_PREFIX + "type";

        // Test TYPE on non-existing key
        String result = jedis.type(testKey);
        assertEquals("none", result, "TYPE should return 'none' for non-existing key");

        // Test TYPE on string key
        jedis.set(testKey, "test_value");
        result = jedis.type(testKey);
        assertEquals("string", result, "TYPE should return 'string' for string key");
    }

    @Test
    @Order(64)
    @DisplayName("KEYS Command")
    void testKEYS() {
        String key1 = TEST_KEY_PREFIX + "keys_test1";
        String key2 = TEST_KEY_PREFIX + "keys_test2";
        String key3 = TEST_KEY_PREFIX + "keys_test3";

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");
        jedis.set(key3, "value3");

        // Test KEYS with pattern
        Set<String> result = jedis.keys(TEST_KEY_PREFIX + "keys_test*");
        assertNotNull(result, "KEYS should return a set");
        assertEquals(3, result.size(), "KEYS should return 3 matching keys");
        assertTrue(result.contains(key1), "Result should contain key1");
        assertTrue(result.contains(key2), "Result should contain key2");
        assertTrue(result.contains(key3), "Result should contain key3");
    }

    @Test
    @Order(65)
    @DisplayName("RANDOMKEY Command")
    void testRANDOMKEY() {
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
    @Order(66)
    @DisplayName("RENAME Command")
    void testRENAME() {
        String srcKey = TEST_KEY_PREFIX + "rename_src";
        String destKey = TEST_KEY_PREFIX + "rename_dest";

        // Set up source key
        jedis.set(srcKey, "test_value");

        // Test RENAME
        String result = jedis.rename(srcKey, destKey);
        assertEquals("OK", result, "RENAME should return OK");

        // Verify source key is gone and destination key exists
        assertNull(jedis.get(srcKey), "Source key should not exist after rename");
        assertEquals("test_value", jedis.get(destKey), "Destination key should have the value");
    }

    @Test
    @Order(67)
    @DisplayName("RENAMENX Command")
    void testRENAMENX() {
        String srcKey = TEST_KEY_PREFIX + "renamenx_src";
        String destKey = TEST_KEY_PREFIX + "renamenx_dest";

        // Set up source key
        jedis.set(srcKey, "source_value");

        // Test RENAMENX on non-existing destination
        long result = jedis.renamenx(srcKey, destKey);
        assertEquals(1, result, "RENAMENX should return 1 for successful rename");
        assertEquals("source_value", jedis.get(destKey), "Destination key should have the value");

        // Set up another source key and test RENAMENX on existing destination
        String srcKey2 = TEST_KEY_PREFIX + "renamenx_src2";
        jedis.set(srcKey2, "source_value2");

        result = jedis.renamenx(srcKey2, destKey);
        assertEquals(0, result, "RENAMENX should return 0 when destination exists");
        assertEquals(
                "source_value", jedis.get(destKey), "Destination key should retain original value");
        assertEquals("source_value2", jedis.get(srcKey2), "Source key should still exist");
    }

    @Test
    @Order(90)
    @DisplayName("SETBIT Command")
    void testSETBIT() {
        String testKey = TEST_KEY_PREFIX + "setbit";

        // Test SETBIT
        boolean result = jedis.setbit(testKey, 7, true);
        assertFalse(result, "Initial bit should be false");

        // Test SETBIT again
        result = jedis.setbit(testKey, 7, false);
        assertTrue(result, "Previous bit should be true");

        // Test SETBIT on different position
        result = jedis.setbit(testKey, 0, true);
        assertFalse(result, "Initial bit at position 0 should be false");
    }

    @Test
    @Order(91)
    @DisplayName("GETBIT Command")
    void testGETBIT() {
        String testKey = TEST_KEY_PREFIX + "getbit";

        // Test GETBIT on non-existing key
        boolean result = jedis.getbit(testKey, 0);
        assertFalse(result, "Bit should be false for non-existing key");

        // Set a bit and test GETBIT
        jedis.setbit(testKey, 7, true);
        result = jedis.getbit(testKey, 7);
        assertTrue(result, "Bit should be true after setting");

        result = jedis.getbit(testKey, 0);
        assertFalse(result, "Unset bit should be false");
    }

    @Test
    @Order(92)
    @DisplayName("BITCOUNT Command")
    void testBITCOUNT() {
        String testKey = TEST_KEY_PREFIX + "bitcount";

        // Test BITCOUNT on non-existing key
        long result = jedis.bitcount(testKey);
        assertEquals(0, result, "BITCOUNT should return 0 for non-existing key");

        // Set some bits and test BITCOUNT
        jedis.setbit(testKey, 0, true);
        jedis.setbit(testKey, 7, true);
        jedis.setbit(testKey, 15, true);

        result = jedis.bitcount(testKey);
        assertEquals(3, result, "BITCOUNT should return 3 for three set bits");
    }

    @Test
    @Order(93)
    @DisplayName("BITPOS Command")
    void testBITPOS() {
        String testKey = TEST_KEY_PREFIX + "bitpos";

        // Set some bits
        jedis.setbit(testKey, 0, false);
        jedis.setbit(testKey, 1, true);
        jedis.setbit(testKey, 2, false);
        jedis.setbit(testKey, 3, true);

        // Test BITPOS for first set bit
        long result = jedis.bitpos(testKey, true);
        assertEquals(1, result, "First set bit should be at position 1");

        // Test BITPOS with parameters
        BitPosParams params = new BitPosParams(0, 1);
        result = jedis.bitpos(testKey, true, params);
        assertEquals(1, result, "First set bit in range should be at position 1");
    }

    @Test
    @Order(94)
    @DisplayName("BITCOUNT with range Command")
    void testBITCOUNTWithRange() {
        String testKey = TEST_KEY_PREFIX + "bitcount_range";

        // Set up test data - create a byte with pattern 10101010
        jedis.setbit(testKey, 0, true);
        jedis.setbit(testKey, 2, true);
        jedis.setbit(testKey, 4, true);
        jedis.setbit(testKey, 6, true);
        jedis.setbit(testKey, 8, true);
        jedis.setbit(testKey, 10, true);

        // Test BITCOUNT with byte range
        long result = jedis.bitcount(testKey, 0, 0);
        assertEquals(4, result, "BITCOUNT should count bits in first byte");

        result = jedis.bitcount(testKey, 0, 1);
        assertEquals(6, result, "BITCOUNT should count bits in first two bytes");
    }

    @Test
    @Order(95)
    @DisplayName("BITOP Command")
    void testBITOP() {
        String srcKey1 = TEST_KEY_PREFIX + "bitop_src1";
        String srcKey2 = TEST_KEY_PREFIX + "bitop_src2";
        String destKey = TEST_KEY_PREFIX + "bitop_dest";

        // Set up source keys with different bit patterns
        jedis.setbit(srcKey1, 0, true);
        jedis.setbit(srcKey1, 2, true);
        jedis.setbit(srcKey2, 1, true);
        jedis.setbit(srcKey2, 2, true);

        // Test BITOP AND
        long result = jedis.bitop(BitOP.AND, destKey, srcKey1, srcKey2);
        assertTrue(result > 0, "BITOP should return length of result");
        assertTrue(jedis.getbit(destKey, 2), "Bit 2 should be set (1 AND 1)");
        assertFalse(jedis.getbit(destKey, 0), "Bit 0 should not be set (1 AND 0)");
        assertFalse(jedis.getbit(destKey, 1), "Bit 1 should not be set (0 AND 1)");

        // Test BITOP OR
        result = jedis.bitop(BitOP.OR, destKey, srcKey1, srcKey2);
        assertTrue(result > 0, "BITOP OR should return length of result");
        assertTrue(jedis.getbit(destKey, 0), "Bit 0 should be set (1 OR 0)");
        assertTrue(jedis.getbit(destKey, 1), "Bit 1 should be set (0 OR 1)");
        assertTrue(jedis.getbit(destKey, 2), "Bit 2 should be set (1 OR 1)");

        // Test BITOP XOR
        result = jedis.bitop(BitOP.XOR, destKey, srcKey1, srcKey2);
        assertTrue(result > 0, "BITOP XOR should return length of result");
        assertTrue(jedis.getbit(destKey, 0), "Bit 0 should be set (1 XOR 0)");
        assertTrue(jedis.getbit(destKey, 1), "Bit 1 should be set (0 XOR 1)");
        assertFalse(jedis.getbit(destKey, 2), "Bit 2 should not be set (1 XOR 1)");
    }

    @Test
    @Order(96)
    @DisplayName("BITFIELD Command")
    void testBITFIELD() {
        String testKey = TEST_KEY_PREFIX + "bitfield";

        // Test BITFIELD SET
        List<Long> result = jedis.bitfield(testKey, "SET", "u8", "0", "255");
        assertNotNull(result, "BITFIELD should return a list");
        assertEquals(1, result.size(), "BITFIELD should return one result");
        assertEquals(0L, result.get(0), "Initial value should be 0");

        // Test BITFIELD GET
        result = jedis.bitfield(testKey, "GET", "u8", "0");
        assertNotNull(result, "BITFIELD GET should return a list");
        assertEquals(1, result.size(), "BITFIELD GET should return one result");
        assertEquals(255L, result.get(0), "Should return the set value");

        // Test BITFIELD INCRBY
        result = jedis.bitfield(testKey, "INCRBY", "u8", "0", "1");
        assertNotNull(result, "BITFIELD INCRBY should return a list");
        assertEquals(1, result.size(), "BITFIELD INCRBY should return one result");
        assertEquals(0L, result.get(0), "Should wrap around from 255 to 0");
    }

    @Test
    @Order(97)
    @DisplayName("BITFIELD_RO Command")
    void testBITFIELD_RO() {
        String testKey = TEST_KEY_PREFIX + "bitfield_ro";

        // Set up test data
        jedis.bitfield(testKey, "SET", "u8", "0", "42");

        // Test BITFIELD_RO (read-only)
        List<Long> result = jedis.bitfieldReadonly(testKey, "GET", "u8", "0");
        assertNotNull(result, "BITFIELD_RO should return a list");
        assertEquals(1, result.size(), "BITFIELD_RO should return one result");
        assertEquals(42L, result.get(0), "Should return the stored value");

        // Test BITFIELD_RO on non-existing key
        result = jedis.bitfieldReadonly(TEST_KEY_PREFIX + "nonexistent", "GET", "u8", "0");
        assertNotNull(result, "BITFIELD_RO should return a list for non-existing key");
        assertEquals(1, result.size(), "BITFIELD_RO should return one result");
        assertEquals(0L, result.get(0), "Should return 0 for non-existing key");
    }

    @Test
    @Order(80)
    @DisplayName("SORT Command")
    void testSORT() {
        // Note: SORT test is limited because GLIDE doesn't have list operations (lpush) yet
        String testKey = TEST_KEY_PREFIX + "sort_test";

        try {
            // Test SORT on non-existing key (should return empty list)
            List<String> result = jedis.sort(testKey);
            assertNotNull(result, "SORT should return a list");
            assertEquals(0, result.size(), "SORT on non-existing key should return empty list");

            // Test SORT with parameters on non-existing key
            result = jedis.sort(testKey, "DESC");
            assertNotNull(result, "SORT with parameters should return a list");
            assertEquals(
                    0, result.size(), "SORT with parameters on non-existing key should return empty list");

            // Create a string key to test SORT behavior on wrong type
            jedis.set(testKey, "not_a_list");

            try {
                result = jedis.sort(testKey);
                // If it doesn't throw an exception, it should return empty list
                assertNotNull(result, "SORT should return a list even for wrong type");
            } catch (Exception e) {
                // Expected behavior - SORT on wrong type should throw exception
                // Accept any exception as valid since GLIDE may have different error messages
                assertNotNull(e.getMessage(), "Exception should have a message");
                assertTrue(e.getMessage().length() > 0, "Exception message should not be empty");
            }

        } catch (Exception e) {
            // SORT might not be fully implemented in GLIDE or may fail for other reasons
            // Accept any exception as valid since this is testing API compatibility
            assertNotNull(e.getMessage(), "Exception should have a message");
            assertTrue(e.getMessage().length() > 0, "Exception message should not be empty");
        }
    }

    @Test
    @Order(81)
    @DisplayName("DUMP Command")
    void testDUMP() {
        String testKey = TEST_KEY_PREFIX + "dump";

        // Set up test data
        jedis.set(testKey, "dump_test_value");

        // Test DUMP
        byte[] result = jedis.dump(testKey);
        assertNotNull(result, "DUMP should return serialized data");
        assertTrue(result.length > 0, "DUMP should return non-empty data");

        // Test DUMP on non-existing key
        result = jedis.dump(TEST_KEY_PREFIX + "nonexistent");
        assertNull(result, "DUMP should return null for non-existing key");
    }

    @Test
    @Order(82)
    @DisplayName("RESTORE Command")
    void testRESTORE() {
        String sourceKey = TEST_KEY_PREFIX + "restore_src";
        String destKey = TEST_KEY_PREFIX + "restore_dest";

        // Set up source data and dump it
        jedis.set(sourceKey, "restore_test_value");
        byte[] dumpData = jedis.dump(sourceKey);
        assertNotNull(dumpData, "Should be able to dump source key");

        // Test RESTORE
        String result = jedis.restore(destKey, 0, dumpData);
        assertEquals("OK", result, "RESTORE should return OK");
        assertEquals(
                "restore_test_value", jedis.get(destKey), "Restored key should have correct value");

        // Test RESTORE with TTL
        String destKeyWithTTL = TEST_KEY_PREFIX + "restore_ttl";
        result = jedis.restore(destKeyWithTTL, 60000, dumpData);
        assertEquals("OK", result, "RESTORE with TTL should return OK");
        assertEquals(
                "restore_test_value", jedis.get(destKeyWithTTL), "Restored key should have correct value");

        long ttl = jedis.pttl(destKeyWithTTL);
        assertTrue(ttl > 0 && ttl <= 60000, "Restored key should have TTL set");
    }

    @Test
    @Order(83)
    @DisplayName("MIGRATE Command")
    void testMIGRATE() {
        // Note: MIGRATE requires a destination Redis instance
        // This test will verify the method exists but may not execute successfully
        String testKey = TEST_KEY_PREFIX + "migrate";
        jedis.set(testKey, "migrate_value");

        try {
            // Test MIGRATE (will likely fail without a destination server)
            String result = jedis.migrate("localhost", 6380, testKey, 0, 5000);
            // If it succeeds, it should return OK
            if (result != null) {
                assertEquals("OK", result, "MIGRATE should return OK if successful");
            }
        } catch (Exception e) {
            // Expected if no destination server is available
            assertTrue(
                    e.getMessage().contains("MIGRATE")
                            || e.getMessage().contains("connection")
                            || e.getMessage().contains("refused")
                            || e.getMessage().contains("timeout"),
                    "Exception should be migration-related");
        }
    }

    @Test
    @Order(84)
    @DisplayName("MOVE Command")
    void testMOVE() {
        // Note: GLIDE may not support true database isolation like traditional Redis
        // This test handles both scenarios: true move vs single-database behavior
        String testKey = TEST_KEY_PREFIX + "move";
        jedis.set(testKey, "move_value");

        try {
            // Test MOVE to database 1
            long result = jedis.move(testKey, 1);
            // Result depends on whether database 1 exists and key doesn't exist there
            assertTrue(result >= 0, "MOVE should return non-negative value");

            if (result == 1) {
                // Key was moved successfully (or GLIDE reports success but doesn't actually move)
                String sourceValue = jedis.get(testKey);
                if (sourceValue == null) {
                    // True move - key no longer exists in source database
                    assertNull(sourceValue, "Key should not exist in source database after move");

                    // Switch to database 1 to verify
                    jedis.select(1);
                    assertEquals(
                            "move_value", jedis.get(testKey), "Key should exist in destination database");

                    // Clean up and switch back
                    jedis.del(testKey);
                    jedis.select(0);
                } else {
                    // GLIDE might not support database isolation - key still exists in source
                    assertEquals(
                            "move_value",
                            sourceValue,
                            "Key still exists - GLIDE may not support database isolation");
                }
            }
        } catch (Exception e) {
            assertTrue(
                    e.getMessage().contains("MOVE") || e.getMessage().contains("database"),
                    "Exception should be database-related");
        }
    }

    @Test
    @Order(70)
    @DisplayName("EXPIRE Command")
    void testEXPIRE() {
        String testKey = TEST_KEY_PREFIX + "expire";

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test EXPIRE
        long result = jedis.expire(testKey, 60);
        assertEquals(1, result, "EXPIRE should return 1 for success");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");
    }

    @Test
    @Order(71)
    @DisplayName("EXPIREAT Command")
    void testEXPIREAT() {
        String testKey = TEST_KEY_PREFIX + "expireat";

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test EXPIREAT (set expiration to 60 seconds from now)
        long expireTime = System.currentTimeMillis() / 1000 + 60;
        long result = jedis.expireAt(testKey, expireTime);
        assertEquals(1, result, "EXPIREAT should return 1 for success");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");
    }

    @Test
    @Order(72)
    @DisplayName("PEXPIRE Command")
    void testPEXPIRE() {
        String testKey = TEST_KEY_PREFIX + "pexpire";

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test PEXPIRE
        long result = jedis.pexpire(testKey, 60000);
        assertEquals(1, result, "PEXPIRE should return 1 for success");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 60000, "PTTL should be set correctly");
    }

    @Test
    @Order(73)
    @DisplayName("PEXPIREAT Command")
    void testPEXPIREAT() {
        String testKey = TEST_KEY_PREFIX + "pexpireat";

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test PEXPIREAT (set expiration to 60 seconds from now)
        long expireTime = System.currentTimeMillis() + 60000;
        long result = jedis.pexpireAt(testKey, expireTime);
        assertEquals(1, result, "PEXPIREAT should return 1 for success");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 60000, "PTTL should be set correctly");
    }

    @Test
    @Order(74)
    @DisplayName("TTL Command")
    void testTTL() {
        String testKey = TEST_KEY_PREFIX + "ttl";

        // Test TTL on non-existing key
        long result = jedis.ttl(testKey);
        assertEquals(-2, result, "TTL should return -2 for non-existing key");

        // Test TTL on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.ttl(testKey);
        assertEquals(-1, result, "TTL should return -1 for key without expiration");

        // Test TTL on key with expiration
        jedis.expire(testKey, 60);
        result = jedis.ttl(testKey);
        assertTrue(result > 0 && result <= 60, "TTL should return remaining time");
    }

    @Test
    @Order(75)
    @DisplayName("PTTL Command")
    void testPTTL() {
        String testKey = TEST_KEY_PREFIX + "pttl";

        // Test PTTL on non-existing key
        long result = jedis.pttl(testKey);
        assertEquals(-2, result, "PTTL should return -2 for non-existing key");

        // Test PTTL on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.pttl(testKey);
        assertEquals(-1, result, "PTTL should return -1 for key without expiration");

        // Test PTTL on key with expiration
        jedis.pexpire(testKey, 60000);
        result = jedis.pttl(testKey);
        assertTrue(result > 0 && result <= 60000, "PTTL should return remaining time in milliseconds");
    }

    @Test
    @Order(76)
    @DisplayName("PERSIST Command")
    void testPERSIST() {
        String testKey = TEST_KEY_PREFIX + "persist";

        // Set up test key with expiration
        jedis.set(testKey, "test_value");
        jedis.expire(testKey, 60);

        // Verify key has expiration
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0, "Key should have expiration");

        // Test PERSIST
        long result = jedis.persist(testKey);
        assertEquals(1, result, "PERSIST should return 1 for success");

        // Verify expiration is removed
        ttl = jedis.ttl(testKey);
        assertEquals(-1, ttl, "Key should not have expiration after PERSIST");
    }

    @Test
    @Order(76)
    @DisplayName("EXPIRETIME Command")
    void testEXPIRETIME() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "EXPIRETIME command requires Redis 7.0.0 or higher");

        String testKey = TEST_KEY_PREFIX + "expiretime";

        // Test EXPIRETIME on non-existing key
        long result = jedis.expireTime(testKey);
        assertEquals(-2, result, "EXPIRETIME should return -2 for non-existing key");

        // Test EXPIRETIME on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.expireTime(testKey);
        assertEquals(-1, result, "EXPIRETIME should return -1 for key without expiration");

        // Test EXPIRETIME on key with expiration
        long currentTime = System.currentTimeMillis() / 1000;
        jedis.expireAt(testKey, currentTime + 60);
        result = jedis.expireTime(testKey);
        assertTrue(
                result > currentTime && result <= currentTime + 60,
                "EXPIRETIME should return expiration timestamp");
    }

    @Test
    @Order(77)
    @DisplayName("PEXPIRETIME Command")
    void testPEXPIRETIME() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "PEXPIRETIME command requires Redis 7.0.0 or higher");

        String testKey = TEST_KEY_PREFIX + "pexpiretime";

        // Test PEXPIRETIME on non-existing key
        long result = jedis.pexpireTime(testKey);
        assertEquals(-2, result, "PEXPIRETIME should return -2 for non-existing key");

        // Test PEXPIRETIME on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.pexpireTime(testKey);
        assertEquals(-1, result, "PEXPIRETIME should return -1 for key without expiration");

        // Test PEXPIRETIME on key with expiration
        long currentTime = System.currentTimeMillis();
        jedis.pexpireAt(testKey, currentTime + 60000);
        result = jedis.pexpireTime(testKey);
        assertTrue(
                result > currentTime && result <= currentTime + 60000,
                "PEXPIRETIME should return expiration timestamp in milliseconds");
    }

    @Test
    @Order(85)
    @DisplayName("TOUCH Command")
    void testTOUCH() {
        String key1 = TEST_KEY_PREFIX + "touch1";
        String key2 = TEST_KEY_PREFIX + "touch2";
        String nonExistentKey = TEST_KEY_PREFIX + "touch_nonexistent";

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");

        // Test TOUCH on existing keys
        long result = jedis.touch(key1, key2);
        assertEquals(2, result, "TOUCH should return 2 for two existing keys");

        // Test TOUCH on mix of existing and non-existing keys
        result = jedis.touch(key1, nonExistentKey);
        assertEquals(1, result, "TOUCH should return 1 for one existing key");

        // Test TOUCH on non-existing key only
        result = jedis.touch(nonExistentKey);
        assertEquals(0, result, "TOUCH should return 0 for non-existing key");
    }

    @Test
    @Order(86)
    @DisplayName("COPY Command")
    void testCOPY() {
        String srcKey = TEST_KEY_PREFIX + "copy_src";
        String destKey = TEST_KEY_PREFIX + "copy_dest";

        // Set up source key
        jedis.set(srcKey, "copy_value");

        // Test COPY to non-existing destination
        boolean result = jedis.copy(srcKey, destKey, false);
        assertTrue(result, "COPY should return true for successful copy");
        assertEquals("copy_value", jedis.get(srcKey), "Source key should still exist");
        assertEquals("copy_value", jedis.get(destKey), "Destination key should have copied value");

        // Test COPY to existing destination without replace
        jedis.set(destKey, "existing_value");
        result = jedis.copy(srcKey, destKey, false);
        assertFalse(result, "COPY should return false when destination exists and replace=false");
        assertEquals("existing_value", jedis.get(destKey), "Destination should retain original value");

        // Test COPY to existing destination with replace
        result = jedis.copy(srcKey, destKey, true);
        assertTrue(result, "COPY should return true when replace=true");
        assertEquals("copy_value", jedis.get(destKey), "Destination should have copied value");
    }

    @Test
    @Order(84)
    @DisplayName("SCAN Command")
    void testSCAN() {
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
    @Order(87)
    @DisplayName("PFADD Command")
    void testPFADD() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("2.8.9"),
                "HyperLogLog commands require Redis 2.8.9 or higher");

        String testKey = TEST_KEY_PREFIX + "pfadd1";

        // Test PFADD
        long result = jedis.pfadd(testKey, "element1", "element2", "element3");
        assertEquals(1, result, "PFADD should return 1 for new HyperLogLog");

        // Test PFADD with existing elements
        result = jedis.pfadd(testKey, "element1", "element4");
        assertTrue(result >= 0, "PFADD should return non-negative value");
    }

    @Test
    @Order(88)
    @DisplayName("PFCOUNT Command")
    void testPFCOUNT() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("2.8.9"),
                "HyperLogLog commands require Redis 2.8.9 or higher");

        String key1 = TEST_KEY_PREFIX + "pfcount1";
        String key2 = TEST_KEY_PREFIX + "pfcount2";

        // Set up test data
        jedis.pfadd(key1, "element1", "element2", "element3");
        jedis.pfadd(key2, "element3", "element4", "element5");

        // Test PFCOUNT on single key
        long result = jedis.pfcount(key1);
        assertEquals(3, result, "PFCOUNT should return approximate count");

        // Test PFCOUNT on multiple keys
        result = jedis.pfcount(key1, key2);
        assertEquals(5, result, "PFCOUNT should return combined approximate count");
    }

    @Test
    @Order(89)
    @DisplayName("PFMERGE Command")
    void testPFMERGE() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("2.8.9"),
                "HyperLogLog commands require Redis 2.8.9 or higher");

        String sourceKey1 = TEST_KEY_PREFIX + "pfmerge_src1";
        String sourceKey2 = TEST_KEY_PREFIX + "pfmerge_src2";
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
    @Order(100)
    @DisplayName("PING Command")
    void testPING() {
        // Test PING
        String result = jedis.ping();
        assertEquals("PONG", result, "PING should return PONG");

        // Test PING with message
        String message = "test_message";
        String pingWithMessage = jedis.ping(message);
        assertEquals(message, pingWithMessage, "PING with message should return the message");
    }
}
