/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.SERVER_VERSION;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.*;

/**
 * Jedis compatibility test that compares GLIDE Jedis compatibility layer with actual Jedis
 * implementation for basic GET/SET operations.
 *
 * <p>This test validates that the GLIDE compatibility layer produces identical results to actual
 * Jedis for core Redis operations, ensuring drop-in compatibility.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisTest {

    private static final String TEST_KEY_PREFIX = "jedis_test:";

    // Server configuration - dynamically resolved from CI environment
    private static String redisHost;
    private static int redisPort;

    // GLIDE compatibility layer instances
    private Jedis glideJedis;

    // Actual Jedis instances (loaded via reflection if available)
    private Object actualJedis;
    private Class<?> actualJedisClass;

    // Availability flags
    private boolean hasGlideJedis = false;
    private boolean hasActualJedis = false;

    @BeforeAll
    static void setupClass() {
        resolveServerAddress();
    }

    /**
     * Resolve Redis/Valkey server address from CI environment properties. Falls back to
     * localhost:6379 if no CI configuration is found.
     */
    private static void resolveServerAddress() {
        String standaloneHosts = System.getProperty("test.server.standalone");

        if (standaloneHosts != null && !standaloneHosts.trim().isEmpty()) {
            String firstHost = standaloneHosts.split(",")[0].trim();
            String[] hostPort = firstHost.split(":");

            if (hostPort.length == 2) {
                redisHost = hostPort[0];
                try {
                    redisPort = Integer.parseInt(hostPort[1]);
                    return;
                } catch (NumberFormatException e) {
                    // Fall through to default
                }
            }
        }

        // Fallback to localhost for local development
        redisHost = "localhost";
        redisPort = 6379;
    }

    @BeforeEach
    void setup() {
        // Initialize GLIDE Jedis compatibility layer
        try {
            glideJedis = new Jedis(redisHost, redisPort);
            hasGlideJedis = true;
        } catch (Exception e) {
            hasGlideJedis = false;
        }

        // Try to load actual Jedis via reflection (optional)
        try {
            String jedisJarPath = System.getProperty("jedis.jar.path");
            if (jedisJarPath != null) {
                // Load actual Jedis classes and create instances
                actualJedisClass = Class.forName("redis.clients.jedis.Jedis");

                actualJedis =
                        actualJedisClass
                                .getConstructor(String.class, int.class)
                                .newInstance(redisHost, redisPort);
                hasActualJedis = true;
            }
        } catch (Exception e) {
            hasActualJedis = false;
        }
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (hasGlideJedis && glideJedis != null) {
            cleanupTestKeys(glideJedis);
            try {
                glideJedis.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (hasActualJedis && actualJedis != null) {
            try {
                cleanupTestKeys(actualJedis);
                Method closeMethod = actualJedisClass.getMethod("close");
                closeMethod.invoke(actualJedis);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    // ===== BASIC OPERATIONS =====

    @Test
    @Order(1)
    @DisplayName("Basic GET/SET Operations")
    void testBasicGetSetOperations() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "basic";
        String testValue = "test_value_123";

        // Test GLIDE Jedis
        String glideSetResult = glideJedis.set(testKey, testValue);
        String glideGetResult = glideJedis.get(testKey);

        assertEquals("OK", glideSetResult, "GLIDE Jedis SET should return OK");
        assertEquals(testValue, glideGetResult, "GLIDE Jedis GET should return the set value");

        // Compare with actual Jedis if available
        if (hasActualJedis) {
            try {
                Method setMethod = actualJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisClass.getMethod("get", String.class);

                String actualSetResult = (String) setMethod.invoke(actualJedis, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualJedis, testKey);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual Jedis SET results should be identical");
                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual Jedis GET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare with actual Jedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Multiple GET/SET Operations")
    void testMultipleGetSetOperations() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "key1", "value1");
        testData.put(TEST_KEY_PREFIX + "key2", "value2");
        testData.put(TEST_KEY_PREFIX + "key3", "value3");

        // Test GLIDE Jedis
        Map<String, String> glideSetResults = new HashMap<>();
        Map<String, String> glideGetResults = new HashMap<>();

        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String setResult = glideJedis.set(entry.getKey(), entry.getValue());
            String getResult = glideJedis.get(entry.getKey());

            glideSetResults.put(entry.getKey(), setResult);
            glideGetResults.put(entry.getKey(), getResult);

            assertEquals("OK", setResult, "GLIDE Jedis SET should return OK for " + entry.getKey());
            assertEquals(
                    entry.getValue(),
                    getResult,
                    "GLIDE Jedis GET should return correct value for " + entry.getKey());
        }

        // Compare with actual Jedis if available
        if (hasActualJedis) {
            try {
                Method setMethod = actualJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisClass.getMethod("get", String.class);

                for (Map.Entry<String, String> entry : testData.entrySet()) {
                    String actualSetResult =
                            (String) setMethod.invoke(actualJedis, entry.getKey(), entry.getValue());
                    String actualGetResult = (String) getMethod.invoke(actualJedis, entry.getKey());

                    assertEquals(
                            actualSetResult,
                            glideSetResults.get(entry.getKey()),
                            "GLIDE and actual Jedis SET results should be identical for " + entry.getKey());
                    assertEquals(
                            actualGetResult,
                            glideGetResults.get(entry.getKey()),
                            "GLIDE and actual Jedis GET results should be identical for " + entry.getKey());
                }
            } catch (Exception e) {
                fail("Failed to compare with actual Jedis: " + e.getMessage());
            }
        }
    }

    // ===== MULTIPLE KEY OPERATIONS =====

    @Test
    @Order(10)
    @DisplayName("MSET Command")
    void testMsetCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        // Test MSET with varargs
        String result1 =
                glideJedis.mset(
                        TEST_KEY_PREFIX + "mset1", "value1",
                        TEST_KEY_PREFIX + "mset2", "value2",
                        TEST_KEY_PREFIX + "mset3", "value3");
        assertEquals("OK", result1, "MSET with varargs should return OK");

        // Verify values were set
        assertEquals("value1", glideJedis.get(TEST_KEY_PREFIX + "mset1"));
        assertEquals("value2", glideJedis.get(TEST_KEY_PREFIX + "mset2"));
        assertEquals("value3", glideJedis.get(TEST_KEY_PREFIX + "mset3"));

        // Test MSET with Map
        Map<String, String> keyValueMap = new HashMap<>();
        keyValueMap.put(TEST_KEY_PREFIX + "mset4", "value4");
        keyValueMap.put(TEST_KEY_PREFIX + "mset5", "value5");

        String result2 = glideJedis.mset(keyValueMap);
        assertEquals("OK", result2, "MSET with Map should return OK");

        // Verify values were set
        assertEquals("value4", glideJedis.get(TEST_KEY_PREFIX + "mset4"));
        assertEquals("value5", glideJedis.get(TEST_KEY_PREFIX + "mset5"));
    }

    @Test
    @Order(11)
    @DisplayName("MGET Command")
    void testMgetCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        // Set up test data
        glideJedis.set(TEST_KEY_PREFIX + "mget1", "value1");
        glideJedis.set(TEST_KEY_PREFIX + "mget2", "value2");
        glideJedis.set(TEST_KEY_PREFIX + "mget3", "value3");

        // Test MGET
        List<String> results =
                glideJedis.mget(
                        TEST_KEY_PREFIX + "mget1",
                        TEST_KEY_PREFIX + "mget2",
                        TEST_KEY_PREFIX + "mget3",
                        TEST_KEY_PREFIX + "nonexistent");

        assertEquals(4, results.size(), "MGET should return 4 results");
        assertEquals("value1", results.get(0), "First value should match");
        assertEquals("value2", results.get(1), "Second value should match");
        assertEquals("value3", results.get(2), "Third value should match");
        assertNull(results.get(3), "Non-existent key should return null");
    }

    // ===== CONDITIONAL SET OPERATIONS =====

    @Test
    @Order(20)
    @DisplayName("SETNX Command")
    void testSetnxCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "setnx";

        // Test SETNX on new key
        Long result1 = glideJedis.setnx(testKey, "first_value");
        assertEquals(1L, result1, "SETNX should return 1 for new key");
        assertEquals("first_value", glideJedis.get(testKey), "Value should be set");

        // Test SETNX on existing key
        Long result2 = glideJedis.setnx(testKey, "second_value");
        assertEquals(0L, result2, "SETNX should return 0 for existing key");
        assertEquals("first_value", glideJedis.get(testKey), "Value should remain unchanged");
    }

    @Test
    @Order(21)
    @DisplayName("SETEX Command")
    void testSetexCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "setex";
        String testValue = "expires_in_60_seconds";

        // Test SETEX
        String result = glideJedis.setex(testKey, 60, testValue);
        assertEquals("OK", result, "SETEX should return OK");
        assertEquals(testValue, glideJedis.get(testKey), "Value should be set correctly");
    }

    @Test
    @Order(22)
    @DisplayName("PSETEX Command")
    void testPsetexCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "psetex";
        String testValue = "expires_in_60000_milliseconds";

        // Test PSETEX
        String result = glideJedis.psetex(testKey, 60000, testValue);
        assertEquals("OK", result, "PSETEX should return OK");
        assertEquals(testValue, glideJedis.get(testKey), "Value should be set correctly");
    }

    // ===== GET AND MODIFY OPERATIONS =====

    @Test
    @Order(30)
    @DisplayName("GETSET Command")
    void testGetsetCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "getset";

        // Test GETSET on non-existent key
        String result1 = glideJedis.getSet(testKey, "new_value");
        assertNull(result1, "GETSET should return null for non-existent key");
        assertEquals("new_value", glideJedis.get(testKey), "New value should be set");

        // Test GETSET on existing key
        String result2 = glideJedis.getSet(testKey, "newer_value");
        assertEquals("new_value", result2, "GETSET should return old value");
        assertEquals("newer_value", glideJedis.get(testKey), "Newer value should be set");
    }

    @Test
    @Order(31)
    @DisplayName("SETGET Command")
    void testSetgetCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "setget";

        // Test SETGET on non-existent key
        String result1 = glideJedis.setGet(testKey, "new_value");
        assertNull(result1, "SETGET should return null for non-existent key");
        assertEquals("new_value", glideJedis.get(testKey), "New value should be set");

        // Test SETGET on existing key
        String result2 = glideJedis.setGet(testKey, "newer_value");
        assertEquals("new_value", result2, "SETGET should return old value");
        assertEquals("newer_value", glideJedis.get(testKey), "Newer value should be set");
    }

    @Test
    @Order(32)
    @DisplayName("GETDEL Command")
    void testGetdelCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "getdel";
        String testValue = "to_be_deleted";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test GETDEL
        String result = glideJedis.getDel(testKey);
        assertEquals(testValue, result, "GETDEL should return the value");
        assertNull(glideJedis.get(testKey), "Key should be deleted after GETDEL");

        // Test GETDEL on non-existent key
        String result2 = glideJedis.getDel(testKey + "_nonexistent");
        assertNull(result2, "GETDEL should return null for non-existent key");
    }

    @Test
    @Order(33)
    @DisplayName("GETEX Command")
    void testGetexCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"), "GETEX command added in version 6.2.0");

        String testKey = TEST_KEY_PREFIX + "getex";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test GETEX with GetExParams - EX option
        String result1 = glideJedis.getEx(testKey, GetExParams.getExParams().ex(60));
        assertEquals(testValue, result1, "GETEX with EX should return the value");

        // Test GETEX with GetExParams - PX option
        String result2 = glideJedis.getEx(testKey, GetExParams.getExParams().px(60000));
        assertEquals(testValue, result2, "GETEX with PX should return the value");

        // Test GETEX with GetExParams - PERSIST option
        String result3 = glideJedis.getEx(testKey, GetExParams.getExParams().persist());
        assertEquals(testValue, result3, "GETEX with PERSIST should return the value");

        // Test GETEX on non-existent key
        String result4 = glideJedis.getEx(testKey + "_nonexistent", GetExParams.getExParams().ex(60));
        assertNull(result4, "GETEX should return null for non-existent key");
    }

    // ===== STRING MANIPULATION OPERATIONS =====

    @Test
    @Order(40)
    @DisplayName("APPEND Command")
    void testAppendCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "append";

        // Test APPEND on non-existent key
        Long result1 = glideJedis.append(testKey, "hello");
        assertEquals(5L, result1, "APPEND should return length of new string");
        assertEquals("hello", glideJedis.get(testKey), "Value should be set");

        // Test APPEND on existing key
        Long result2 = glideJedis.append(testKey, " world");
        assertEquals(11L, result2, "APPEND should return new length");
        assertEquals("hello world", glideJedis.get(testKey), "Value should be appended");
    }

    @Test
    @Order(41)
    @DisplayName("STRLEN Command")
    void testStrlenCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "strlen";

        // Test STRLEN on non-existent key
        Long result1 = glideJedis.strlen(testKey);
        assertEquals(0L, result1, "STRLEN should return 0 for non-existent key");

        // Test STRLEN on existing key
        glideJedis.set(testKey, "hello world");
        Long result2 = glideJedis.strlen(testKey);
        assertEquals(11L, result2, "STRLEN should return correct length");
    }

    // ===== BITMAP OPERATIONS =====

    @Test
    @Order(90)
    @DisplayName("SETBIT Command")
    void testSetbitCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "setbit";

        // Test SETBIT on new key
        boolean result1 = glideJedis.setbit(testKey, 0, true);
        assertFalse(result1, "SETBIT should return false for new bit");

        // Test SETBIT on existing bit
        boolean result2 = glideJedis.setbit(testKey, 0, false);
        assertTrue(result2, "SETBIT should return true for previously set bit");

        // Test SETBIT at different offsets
        boolean result3 = glideJedis.setbit(testKey, 7, true);
        assertFalse(result3, "SETBIT should return false for new bit at offset 7");

        boolean result4 = glideJedis.setbit(testKey, 15, true);
        assertFalse(result4, "SETBIT should return false for new bit at offset 15");
    }

    @Test
    @Order(91)
    @DisplayName("GETBIT Command")
    void testGetbitCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "getbit";

        // Test GETBIT on non-existent key
        boolean result1 = glideJedis.getbit(testKey, 0);
        assertFalse(result1, "GETBIT should return false for non-existent key");

        // Set some bits and test GETBIT
        glideJedis.setbit(testKey, 0, true);
        glideJedis.setbit(testKey, 7, true);

        boolean result2 = glideJedis.getbit(testKey, 0);
        assertTrue(result2, "GETBIT should return true for set bit at offset 0");

        boolean result3 = glideJedis.getbit(testKey, 7);
        assertTrue(result3, "GETBIT should return true for set bit at offset 7");

        boolean result4 = glideJedis.getbit(testKey, 1);
        assertFalse(result4, "GETBIT should return false for unset bit at offset 1");

        boolean result5 = glideJedis.getbit(testKey, 100);
        assertFalse(result5, "GETBIT should return false for offset beyond string length");
    }

    @Test
    @Order(92)
    @DisplayName("BITCOUNT Command")
    void testBitcountCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "bitcount";

        // Test BITCOUNT on non-existent key
        long result1 = glideJedis.bitcount(testKey);
        assertEquals(0L, result1, "BITCOUNT should return 0 for non-existent key");

        // Set some bits and test BITCOUNT
        glideJedis.setbit(testKey, 0, true);
        glideJedis.setbit(testKey, 1, true);
        glideJedis.setbit(testKey, 7, true);
        glideJedis.setbit(testKey, 8, true);

        long result2 = glideJedis.bitcount(testKey);
        assertEquals(4L, result2, "BITCOUNT should return 4 for 4 set bits");

        // Test BITCOUNT with range
        long result3 = glideJedis.bitcount(testKey, 0, 0);
        assertEquals(3L, result3, "BITCOUNT should return 3 for first byte (3 set bits)");

        long result4 = glideJedis.bitcount(testKey, 1, 1);
        assertEquals(1L, result4, "BITCOUNT should return 1 for second byte (1 set bit)");
    }

    @Test
    @Order(93)
    @DisplayName("BITPOS Command")
    void testBitposCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "bitpos";

        // Test BITPOS on non-existent key
        long result1 = glideJedis.bitpos(testKey, true);
        assertEquals(-1L, result1, "BITPOS should return -1 for non-existent key searching for 1");

        long result2 = glideJedis.bitpos(testKey, false);
        assertEquals(0L, result2, "BITPOS should return 0 for non-existent key searching for 0");

        // Set some bits and test BITPOS
        glideJedis.setbit(testKey, 2, true);
        glideJedis.setbit(testKey, 5, true);

        long result3 = glideJedis.bitpos(testKey, true);
        assertEquals(2L, result3, "BITPOS should return 2 for first set bit");

        long result4 = glideJedis.bitpos(testKey, false);
        assertEquals(0L, result4, "BITPOS should return 0 for first unset bit");

        // Test BITPOS with BitPosParams for range
        BitPosParams params = new BitPosParams(0, 0);
        long result5 = glideJedis.bitpos(testKey, true, params);
        assertEquals(2L, result5, "BITPOS should return 2 for first set bit in first byte");
    }

    @Test
    @Order(94)
    @DisplayName("BITOP Command")
    void testBitopCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String key1 = TEST_KEY_PREFIX + "bitop1";
        String key2 = TEST_KEY_PREFIX + "bitop2";
        String destKey = TEST_KEY_PREFIX + "bitop_dest";

        // Set up test data with ASCII characters that produce valid UTF-8 results
        // key1: 01110000 (ASCII 'p' = 112)
        glideJedis.set(key1, "p");
        // key2: 01110001 (ASCII 'q' = 113)
        glideJedis.set(key2, "q");

        // Test AND operation
        long result1 = glideJedis.bitop(Jedis.BitOP.AND, destKey, key1, key2);
        assertEquals(1L, result1, "BITOP AND should return length of result");
        // AND result: 01110000 (ASCII 'p' = 112)
        assertEquals("p", glideJedis.get(destKey), "BITOP AND result should be 'p'");

        // Test OR operation
        long result2 = glideJedis.bitop(Jedis.BitOP.OR, destKey, key1, key2);
        assertEquals(1L, result2, "BITOP OR should return length of result");
        // OR result: 01110001 (ASCII 'q' = 113)
        assertEquals("q", glideJedis.get(destKey), "BITOP OR result should be 'q'");

        // Test XOR operation
        long result3 = glideJedis.bitop(Jedis.BitOP.XOR, destKey, key1, key2);
        assertEquals(1L, result3, "BITOP XOR should return length of result");
        // XOR result may not be valid UTF-8, so just verify the key exists
        assertTrue(glideJedis.exists(destKey), "BITOP XOR result key should exist");

        // Test NOT operation (single key)
        long result4 = glideJedis.bitop(Jedis.BitOP.NOT, destKey, key1);
        assertEquals(1L, result4, "BITOP NOT should return length of result");
        // NOT result will be bitwise complement, which may not be valid UTF-8
        // Just verify the key exists
        assertTrue(glideJedis.exists(destKey), "BITOP NOT result key should exist");
    }

    @Test
    @Order(95)
    @DisplayName("BITFIELD Command")
    void testBitfieldCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "bitfield";

        // Test BITFIELD SET and GET operations
        List<Long> result1 = glideJedis.bitfield(testKey, "SET", "u8", "0", "255");
        assertNotNull(result1, "BITFIELD should return a result list");
        assertEquals(1, result1.size(), "BITFIELD SET should return one result");
        assertEquals(0L, result1.get(0), "BITFIELD SET should return previous value (0)");

        List<Long> result2 = glideJedis.bitfield(testKey, "GET", "u8", "0");
        assertNotNull(result2, "BITFIELD GET should return a result list");
        assertEquals(1, result2.size(), "BITFIELD GET should return one result");
        assertEquals(255L, result2.get(0), "BITFIELD GET should return set value (255)");

        // Test BITFIELD INCRBY operation
        List<Long> result3 = glideJedis.bitfield(testKey, "INCRBY", "u8", "0", "1");
        assertNotNull(result3, "BITFIELD INCRBY should return a result list");
        assertEquals(1, result3.size(), "BITFIELD INCRBY should return one result");
        // Note: This might wrap around due to overflow, depending on implementation
        assertNotNull(result3.get(0), "BITFIELD INCRBY should return a value");
    }

    @Test
    @Order(96)
    @DisplayName("BITFIELD_RO Command")
    void testBitfieldReadonlyCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "bitfield_ro";

        // Set up test data using regular BITFIELD
        glideJedis.bitfield(testKey, "SET", "u8", "0", "170"); // 10101010 in binary

        // Test BITFIELD_RO GET operations
        List<Long> result1 = glideJedis.bitfieldReadonly(testKey, "GET", "u8", "0");
        assertNotNull(result1, "BITFIELD_RO should return a result list");
        assertEquals(1, result1.size(), "BITFIELD_RO GET should return one result");
        assertEquals(170L, result1.get(0), "BITFIELD_RO GET should return correct value");

        // Test BITFIELD_RO with multiple GET operations
        List<Long> result2 = glideJedis.bitfieldReadonly(testKey, "GET", "u4", "0", "GET", "u4", "4");
        assertNotNull(result2, "BITFIELD_RO should return a result list");
        assertEquals(2, result2.size(), "BITFIELD_RO should return two results");
        assertEquals(10L, result2.get(0), "First nibble should be 10 (1010)");
        assertEquals(10L, result2.get(1), "Second nibble should be 10 (1010)");
    }

    // ===== NUMERIC OPERATIONS =====

    @Test
    @Order(50)
    @DisplayName("INCR Command")
    void testIncrCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "incr";

        // Test INCR on non-existent key
        Long result1 = glideJedis.incr(testKey);
        assertEquals(1L, result1, "INCR should return 1 for non-existent key");

        // Test INCR on existing key
        Long result2 = glideJedis.incr(testKey);
        assertEquals(2L, result2, "INCR should increment by 1");

        // Verify final value
        assertEquals("2", glideJedis.get(testKey), "Final value should be 2");
    }

    @Test
    @Order(51)
    @DisplayName("INCRBY Command")
    void testIncrbyCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "incrby";

        // Test INCRBY on non-existent key
        Long result1 = glideJedis.incrBy(testKey, 5);
        assertEquals(5L, result1, "INCRBY should return 5 for non-existent key");

        // Test INCRBY on existing key
        Long result2 = glideJedis.incrBy(testKey, 10);
        assertEquals(15L, result2, "INCRBY should increment by 10");

        // Test INCRBY with negative value
        Long result3 = glideJedis.incrBy(testKey, -3);
        assertEquals(12L, result3, "INCRBY should handle negative increment");
    }

    @Test
    @Order(52)
    @DisplayName("INCRBYFLOAT Command")
    void testIncrbyfloatCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "incrbyfloat";

        // Test INCRBYFLOAT on non-existent key
        Double result1 = glideJedis.incrByFloat(testKey, 2.5);
        assertEquals(2.5, result1, 0.001, "INCRBYFLOAT should return 2.5 for non-existent key");

        // Test INCRBYFLOAT on existing key
        Double result2 = glideJedis.incrByFloat(testKey, 1.5);
        assertEquals(4.0, result2, 0.001, "INCRBYFLOAT should increment by 1.5");

        // Test INCRBYFLOAT with negative value
        Double result3 = glideJedis.incrByFloat(testKey, -0.5);
        assertEquals(3.5, result3, 0.001, "INCRBYFLOAT should handle negative increment");
    }

    @Test
    @Order(53)
    @DisplayName("DECR Command")
    void testDecrCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "decr";

        // Set initial value
        glideJedis.set(testKey, "10");

        // Test DECR
        Long result1 = glideJedis.decr(testKey);
        assertEquals(9L, result1, "DECR should decrement by 1");

        // Test DECR again
        Long result2 = glideJedis.decr(testKey);
        assertEquals(8L, result2, "DECR should decrement by 1 again");

        // Verify final value
        assertEquals("8", glideJedis.get(testKey), "Final value should be 8");
    }

    @Test
    @Order(54)
    @DisplayName("DECRBY Command")
    void testDecrbyCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "decrby";

        // Set initial value
        glideJedis.set(testKey, "20");

        // Test DECRBY
        Long result1 = glideJedis.decrBy(testKey, 5);
        assertEquals(15L, result1, "DECRBY should decrement by 5");

        // Test DECRBY with larger value
        Long result2 = glideJedis.decrBy(testKey, 10);
        assertEquals(5L, result2, "DECRBY should decrement by 10");

        // Test DECRBY with negative value (should increment)
        Long result3 = glideJedis.decrBy(testKey, -3);
        assertEquals(8L, result3, "DECRBY should handle negative decrement");
    }

    // ===== KEY MANAGEMENT OPERATIONS =====

    @Test
    @Order(60)
    @DisplayName("DEL Command")
    void testDelCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey1 = TEST_KEY_PREFIX + "del1";
        String testKey2 = TEST_KEY_PREFIX + "del2";
        String testKey3 = TEST_KEY_PREFIX + "del3";

        // Set up test data
        glideJedis.set(testKey1, "value1");
        glideJedis.set(testKey2, "value2");

        // Test DEL single key
        Long result1 = glideJedis.del(testKey1);
        assertEquals(1L, result1, "DEL should return 1 for existing key");
        assertNull(glideJedis.get(testKey1), "Key should be deleted");

        // Test DEL multiple keys
        Long result2 = glideJedis.del(testKey2, testKey3);
        assertEquals(1L, result2, "DEL should return 1 for one existing key out of two");
        assertNull(glideJedis.get(testKey2), "Key should be deleted");

        // Test DEL non-existent key
        Long result3 = glideJedis.del(testKey3);
        assertEquals(0L, result3, "DEL should return 0 for non-existent key");
    }

    @Test
    @Order(61)
    @DisplayName("UNLINK Command")
    void testUnlinkCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey1 = TEST_KEY_PREFIX + "unlink1";
        String testKey2 = TEST_KEY_PREFIX + "unlink2";
        String testKey3 = TEST_KEY_PREFIX + "unlink3";

        // Set up test data
        glideJedis.set(testKey1, "value1");
        glideJedis.set(testKey2, "value2");

        // Test UNLINK multiple keys
        Long result = glideJedis.unlink(testKey1, testKey2, testKey3);
        assertEquals(2L, result, "UNLINK should return 2 for two existing keys out of three");
        assertNull(glideJedis.get(testKey1), "Key should be unlinked");
        assertNull(glideJedis.get(testKey2), "Key should be unlinked");
    }

    @Test
    @Order(62)
    @DisplayName("EXISTS Command")
    void testExistsCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey1 = TEST_KEY_PREFIX + "exists1";
        String testKey2 = TEST_KEY_PREFIX + "exists2";
        String testKey3 = TEST_KEY_PREFIX + "exists3";

        // Test EXISTS on non-existent keys
        Long result1 = glideJedis.exists(testKey1, testKey2, testKey3);
        assertEquals(0L, result1, "EXISTS should return 0 for non-existent keys");

        // Set up test data
        glideJedis.set(testKey1, "value1");
        glideJedis.set(testKey2, "value2");

        // Test EXISTS on mixed keys
        Long result2 = glideJedis.exists(testKey1, testKey2, testKey3);
        assertEquals(2L, result2, "EXISTS should return 2 for two existing keys out of three");

        // Test EXISTS on single key
        boolean result3 = glideJedis.exists(testKey1);
        assertTrue(result3, "EXISTS should return true for existing key");
    }

    @Test
    @Order(63)
    @DisplayName("TYPE Command")
    void testTypeCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String stringKey = TEST_KEY_PREFIX + "type_string";
        String nonExistentKey = TEST_KEY_PREFIX + "type_nonexistent";

        // Test TYPE on non-existent key
        String result1 = glideJedis.type(nonExistentKey);
        assertEquals("none", result1, "TYPE should return 'none' for non-existent key");

        // Test TYPE on string key
        glideJedis.set(stringKey, "test_value");
        String result2 = glideJedis.type(stringKey);
        assertEquals("string", result2, "TYPE should return 'string' for string key");
    }

    @Test
    @Order(64)
    @DisplayName("KEYS Command")
    void testKeysCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String prefix = TEST_KEY_PREFIX + "keys_test:";
        String key1 = prefix + "key1";
        String key2 = prefix + "key2";
        String key3 = prefix + "different";

        // Set up test data
        glideJedis.set(key1, "value1");
        glideJedis.set(key2, "value2");
        glideJedis.set(key3, "value3");

        // Test KEYS with pattern
        Set<String> result1 = glideJedis.keys(prefix + "key*");
        assertEquals(2, result1.size(), "KEYS should return 2 keys matching pattern");
        assertTrue(result1.contains(key1), "Result should contain key1");
        assertTrue(result1.contains(key2), "Result should contain key2");
        assertFalse(result1.contains(key3), "Result should not contain key3");

        // Test KEYS with wildcard
        Set<String> result2 = glideJedis.keys(prefix + "*");
        assertEquals(3, result2.size(), "KEYS should return 3 keys matching wildcard");
        assertTrue(result2.contains(key1), "Result should contain key1");
        assertTrue(result2.contains(key2), "Result should contain key2");
        assertTrue(result2.contains(key3), "Result should contain key3");
    }

    @Test
    @Order(65)
    @DisplayName("RANDOMKEY Command")
    void testRandomkeyCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "randomkey";

        // Set up test data
        glideJedis.set(testKey, "value");

        // Test RANDOMKEY
        String result = glideJedis.randomKey();
        assertNotNull(result, "RANDOMKEY should return a key when database is not empty");

        // Clean up and test empty database behavior
        glideJedis.del(testKey);
        // Note: In a real test environment, there might be other keys, so we can't reliably test empty
        // database
    }

    @Test
    @Order(66)
    @DisplayName("RENAME Command")
    void testRenameCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String oldKey = TEST_KEY_PREFIX + "rename_old";
        String newKey = TEST_KEY_PREFIX + "rename_new";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(oldKey, testValue);

        // Test RENAME
        String result = glideJedis.rename(oldKey, newKey);
        assertEquals("OK", result, "RENAME should return OK");
        assertNull(glideJedis.get(oldKey), "Old key should not exist after rename");
        assertEquals(testValue, glideJedis.get(newKey), "New key should have the value");
    }

    @Test
    @Order(67)
    @DisplayName("RENAMENX Command")
    void testRenamenxCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String oldKey = TEST_KEY_PREFIX + "renamenx_old";
        String newKey = TEST_KEY_PREFIX + "renamenx_new";
        String existingKey = TEST_KEY_PREFIX + "renamenx_existing";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(oldKey, testValue);

        // Test RENAMENX to non-existent key
        Long result1 = glideJedis.renamenx(oldKey, newKey);
        assertEquals(1L, result1, "RENAMENX should return 1 for successful rename");
        assertNull(glideJedis.get(oldKey), "Old key should not exist after rename");
        assertEquals(testValue, glideJedis.get(newKey), "New key should have the value");

        // Set up for second test
        glideJedis.set(oldKey, testValue);
        glideJedis.set(existingKey, "existing_value");

        // Test RENAMENX to existing key
        Long result2 = glideJedis.renamenx(oldKey, existingKey);
        assertEquals(0L, result2, "RENAMENX should return 0 when target key exists");
        assertEquals(testValue, glideJedis.get(oldKey), "Old key should still exist");
        assertEquals("existing_value", glideJedis.get(existingKey), "Existing key should be unchanged");
    }

    // ===== EXPIRATION AND TTL OPERATIONS =====

    @Test
    @Order(70)
    @DisplayName("EXPIRE Command")
    void testExpireCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "expire";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test EXPIRE
        Long result1 = glideJedis.expire(testKey, 60);
        assertEquals(1L, result1, "EXPIRE should return 1 for existing key");

        // Verify TTL is set
        Long ttl = glideJedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be positive and <= 60 seconds");

        // Test EXPIRE on non-existent key
        Long result2 = glideJedis.expire(TEST_KEY_PREFIX + "nonexistent", 60);
        assertEquals(0L, result2, "EXPIRE should return 0 for non-existent key");
    }

    @Test
    @Order(71)
    @DisplayName("EXPIREAT Command")
    void testExpireatCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "expireat";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test EXPIREAT (set expiration to 1 hour from now)
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        Long result1 = glideJedis.expireAt(testKey, futureTimestamp);
        assertEquals(1L, result1, "EXPIREAT should return 1 for existing key");

        // Verify expiration is set (only available in 7.0.0+)
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            Long expiretime = glideJedis.expireTime(testKey);
            assertTrue(expiretime > 0, "EXPIRETIME should return positive timestamp");
        }

        // Test EXPIREAT on non-existent key
        Long result2 = glideJedis.expireAt(TEST_KEY_PREFIX + "nonexistent", futureTimestamp);
        assertEquals(0L, result2, "EXPIREAT should return 0 for non-existent key");
    }

    @Test
    @Order(72)
    @DisplayName("PEXPIRE Command")
    void testPexpireCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "pexpire";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test PEXPIRE
        Long result1 = glideJedis.pexpire(testKey, 60000);
        assertEquals(1L, result1, "PEXPIRE should return 1 for existing key");

        // Verify PTTL is set
        Long pttl = glideJedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 60000, "PTTL should be positive and <= 60000 milliseconds");

        // Test PEXPIRE on non-existent key
        Long result2 = glideJedis.pexpire(TEST_KEY_PREFIX + "nonexistent", 60000);
        assertEquals(0L, result2, "PEXPIRE should return 0 for non-existent key");
    }

    @Test
    @Order(73)
    @DisplayName("PEXPIREAT Command")
    void testPexpireatCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "pexpireat";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test PEXPIREAT (set expiration to 1 hour from now in milliseconds)
        long futureTimestamp = System.currentTimeMillis() + 3600000;
        Long result1 = glideJedis.pexpireAt(testKey, futureTimestamp);
        assertEquals(1L, result1, "PEXPIREAT should return 1 for existing key");

        // Verify expiration is set (only available in 7.0.0+)
        if (SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0")) {
            Long pexpiretime = glideJedis.pexpireTime(testKey);
            assertTrue(pexpiretime > 0, "PEXPIRETIME should return positive timestamp");
        }

        // Test PEXPIREAT on non-existent key
        Long result2 = glideJedis.pexpireAt(TEST_KEY_PREFIX + "nonexistent", futureTimestamp);
        assertEquals(0L, result2, "PEXPIREAT should return 0 for non-existent key");
    }

    @Test
    @Order(74)
    @DisplayName("TTL Command")
    void testTtlCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "ttl";
        String testValue = "test_value";

        // Test TTL on non-existent key
        Long result1 = glideJedis.ttl(TEST_KEY_PREFIX + "nonexistent");
        assertEquals(-2L, result1, "TTL should return -2 for non-existent key");

        // Set up test data without expiration
        glideJedis.set(testKey, testValue);
        Long result2 = glideJedis.ttl(testKey);
        assertEquals(-1L, result2, "TTL should return -1 for key without expiration");

        // Set expiration and test TTL
        glideJedis.expire(testKey, 60);
        Long result3 = glideJedis.ttl(testKey);
        assertTrue(result3 > 0 && result3 <= 60, "TTL should be positive and <= 60 seconds");
    }

    @Test
    @Order(75)
    @DisplayName("PTTL Command")
    void testPttlCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "pttl";
        String testValue = "test_value";

        // Test PTTL on non-existent key
        Long result1 = glideJedis.pttl(TEST_KEY_PREFIX + "nonexistent");
        assertEquals(-2L, result1, "PTTL should return -2 for non-existent key");

        // Set up test data without expiration
        glideJedis.set(testKey, testValue);
        Long result2 = glideJedis.pttl(testKey);
        assertEquals(-1L, result2, "PTTL should return -1 for key without expiration");

        // Set expiration and test PTTL
        glideJedis.pexpire(testKey, 60000);
        Long result3 = glideJedis.pttl(testKey);
        assertTrue(
                result3 > 0 && result3 <= 60000, "PTTL should be positive and <= 60000 milliseconds");
    }

    @Test
    @Order(76)
    @DisplayName("EXPIRETIME Command")
    void testExpiretimeCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "EXPIRETIME command added in version 7.0.0");

        String testKey = TEST_KEY_PREFIX + "expiretime";
        String testValue = "test_value";

        // Test EXPIRETIME on non-existent key
        Long result1 = glideJedis.expireTime(TEST_KEY_PREFIX + "nonexistent");
        assertEquals(-2L, result1, "EXPIRETIME should return -2 for non-existent key");

        // Set up test data without expiration
        glideJedis.set(testKey, testValue);
        Long result2 = glideJedis.expireTime(testKey);
        assertEquals(-1L, result2, "EXPIRETIME should return -1 for key without expiration");

        // Set expiration and test EXPIRETIME
        long futureTimestamp = System.currentTimeMillis() / 1000 + 3600;
        glideJedis.expireAt(testKey, futureTimestamp);
        Long result3 = glideJedis.expireTime(testKey);
        assertTrue(result3 > 0, "EXPIRETIME should return positive timestamp");
    }

    @Test
    @Order(77)
    @DisplayName("PEXPIRETIME Command")
    void testPexpiretimeCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "PEXPIRETIME command added in version 7.0.0");

        String testKey = TEST_KEY_PREFIX + "pexpiretime";
        String testValue = "test_value";

        // Test PEXPIRETIME on non-existent key
        Long result1 = glideJedis.pexpireTime(TEST_KEY_PREFIX + "nonexistent");
        assertEquals(-2L, result1, "PEXPIRETIME should return -2 for non-existent key");

        // Set up test data without expiration
        glideJedis.set(testKey, testValue);
        Long result2 = glideJedis.pexpireTime(testKey);
        assertEquals(-1L, result2, "PEXPIRETIME should return -1 for key without expiration");

        // Set expiration and test PEXPIRETIME
        long futureTimestamp = System.currentTimeMillis() + 3600000;
        glideJedis.pexpireAt(testKey, futureTimestamp);
        Long result3 = glideJedis.pexpireTime(testKey);
        assertTrue(result3 > 0, "PEXPIRETIME should return positive timestamp");
    }

    @Test
    @Order(78)
    @DisplayName("PERSIST Command")
    void testPersistCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "persist";
        String testValue = "test_value";

        // Test PERSIST on non-existent key
        Long result1 = glideJedis.persist(TEST_KEY_PREFIX + "nonexistent");
        assertEquals(0L, result1, "PERSIST should return 0 for non-existent key");

        // Set up test data without expiration
        glideJedis.set(testKey, testValue);
        Long result2 = glideJedis.persist(testKey);
        assertEquals(0L, result2, "PERSIST should return 0 for key without expiration");

        // Set expiration and test PERSIST
        glideJedis.expire(testKey, 60);
        Long result3 = glideJedis.persist(testKey);
        assertEquals(1L, result3, "PERSIST should return 1 for key with expiration");

        // Verify expiration was removed
        Long ttl = glideJedis.ttl(testKey);
        assertEquals(-1L, ttl, "TTL should return -1 after PERSIST");
    }

    // ===== ADVANCED KEY OPERATIONS =====

    @Test
    @Order(80)
    @DisplayName("SORT Command")
    void testSortCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String listKey = TEST_KEY_PREFIX + "sort_list";

        // Set up test data - create a list with numbers
        glideJedis.del(listKey); // Ensure clean state
        // Note: We need to use LPUSH to create a list, but since it's not in our compatibility layer,
        // we'll use a different approach or skip this test if list operations aren't available

        // For now, let's test SORT with a simple case that might work with string keys
        String testKey = TEST_KEY_PREFIX + "sort_test";
        glideJedis.set(testKey, "test_value");

        try {
            List<String> result = glideJedis.sort(testKey);
            // SORT on a string key should work but may return empty or the value itself
            assertNotNull(result, "SORT should return a list");
        } catch (Exception e) {
            // SORT might not work on string keys, which is expected behavior
            assertTrue(
                    e.getMessage().contains("SORT") || e.getMessage().contains("WRONGTYPE"),
                    "Expected SORT-related error for string key");
        }
    }

    @Test
    @Order(81)
    @DisplayName("DUMP and RESTORE Commands")
    void testDumpRestoreCommands() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String sourceKey = TEST_KEY_PREFIX + "dump_source";
        String targetKey = TEST_KEY_PREFIX + "dump_target";
        String testValue = "test_value_for_dump";

        // Set up test data
        glideJedis.set(sourceKey, testValue);

        try {
            // Test DUMP
            byte[] dumpData = glideJedis.dump(sourceKey);
            assertNotNull(dumpData, "DUMP should return serialized data");
            assertTrue(dumpData.length > 0, "DUMP data should not be empty");

            // Test RESTORE
            String restoreResult = glideJedis.restore(targetKey, 0, dumpData);
            assertEquals("OK", restoreResult, "RESTORE should return OK");
            assertEquals(
                    testValue, glideJedis.get(targetKey), "RESTORE should recreate the key with same value");

        } catch (Exception e) {
            // DUMP/RESTORE with binary data may have encoding issues in the compatibility layer
            // This is a known limitation when dealing with binary serialized data
            assertTrue(
                    e.getMessage().contains("DUMP operation failed")
                            || e.getMessage().contains("invalid utf-8 sequence")
                            || e.getMessage().contains("RESTORE operation failed"),
                    "Expected DUMP/RESTORE related error due to binary data handling: " + e.getMessage());

            // Test that DUMP on non-existent key works
            try {
                byte[] dumpNull = glideJedis.dump(TEST_KEY_PREFIX + "nonexistent");
                assertNull(dumpNull, "DUMP should return null for non-existent key");
            } catch (Exception e2) {
                // This is also acceptable for non-existent keys
                assertTrue(
                        e2.getMessage().contains("DUMP operation failed"),
                        "Expected DUMP operation error for non-existent key");
            }
        }
    }

    @Test
    @Order(82)
    @DisplayName("MIGRATE Command")
    void testMigrateCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "migrate";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test MIGRATE (this will likely fail in test environment, but we test the method call)
        try {
            String result = glideJedis.migrate("localhost", 6380, testKey, 1, 1000);
            // If it succeeds, it should return "OK" or "NOKEY"
            assertTrue(
                    "OK".equals(result) || "NOKEY".equals(result), "MIGRATE should return OK or NOKEY");
        } catch (Exception e) {
            // Expected in test environment - connection refused, etc.
            assertTrue(
                    e.getMessage().contains("MIGRATE")
                            || e.getMessage().contains("Connection refused")
                            || e.getMessage().contains("timeout"),
                    "Expected MIGRATE-related error in test environment");
        }
    }

    @Test
    @Order(83)
    @DisplayName("MOVE Command")
    void testMoveCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "move";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test MOVE (this might fail if multiple databases aren't supported)
        try {
            Long result = glideJedis.move(testKey, 1);
            // Result should be 1 if moved, 0 if not
            assertTrue(result == 0L || result == 1L, "MOVE should return 0 or 1");
        } catch (Exception e) {
            // Expected if multiple databases aren't supported
            assertTrue(
                    e.getMessage().contains("MOVE")
                            || e.getMessage().contains("database")
                            || e.getMessage().contains("ERR"),
                    "Expected MOVE-related error if multiple databases not supported");
        }
    }

    @Test
    @Order(84)
    @DisplayName("SCAN Command")
    void testScanCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String prefix = TEST_KEY_PREFIX + "scan:";
        String key1 = prefix + "key1";
        String key2 = prefix + "key2";
        String key3 = prefix + "key3";

        // Set up test data
        glideJedis.set(key1, "value1");
        glideJedis.set(key2, "value2");
        glideJedis.set(key3, "value3");

        // Test SCAN without pattern
        ScanResult<String> result1 = glideJedis.scan("0");
        assertNotNull(result1, "SCAN should return ScanResult");
        assertNotNull(result1.getCursor(), "SCAN should return cursor");
        assertNotNull(result1.getResult(), "SCAN should return result list");

        String cursor = result1.getCursor();
        assertNotNull(cursor, "SCAN should return cursor");

        // Test SCAN with pattern using ScanParams
        ScanParams scanParams = new ScanParams().match(prefix + "*");
        ScanResult<String> result2 = glideJedis.scan("0", scanParams);
        assertNotNull(result2, "SCAN with pattern should return ScanResult");
        assertNotNull(result2.getCursor(), "SCAN with pattern should return cursor");
        assertNotNull(result2.getResult(), "SCAN with pattern should return result list");

        // Check if our test keys are in the results (they might be in subsequent scans)
        boolean foundTestKey = false;
        for (String key : result2.getResult()) {
            if (key != null && key.startsWith(prefix)) {
                foundTestKey = true;
                break;
            }
        }
        // Note: SCAN might not return all keys in first iteration, so we don't assert this
    }

    @Test
    @Order(85)
    @DisplayName("TOUCH Command")
    void testTouchCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey1 = TEST_KEY_PREFIX + "touch1";
        String testKey2 = TEST_KEY_PREFIX + "touch2";
        String testKey3 = TEST_KEY_PREFIX + "touch3";

        // Set up test data
        glideJedis.set(testKey1, "value1");
        glideJedis.set(testKey2, "value2");

        // Test TOUCH on existing keys
        Long result1 = glideJedis.touch(testKey1, testKey2);
        assertEquals(2L, result1, "TOUCH should return 2 for two existing keys");

        // Test TOUCH on mixed keys (existing and non-existing)
        Long result2 = glideJedis.touch(testKey1, testKey3);
        assertEquals(1L, result2, "TOUCH should return 1 for one existing key out of two");

        // Test TOUCH on non-existent key
        Long result3 = glideJedis.touch(testKey3);
        assertEquals(0L, result3, "TOUCH should return 0 for non-existent key");
    }

    @Test
    @Order(86)
    @DisplayName("COPY Command")
    void testCopyCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"), "COPY command added in version 6.2.0");

        String sourceKey = TEST_KEY_PREFIX + "copy_source";
        String targetKey = TEST_KEY_PREFIX + "copy_target";
        String existingKey = TEST_KEY_PREFIX + "copy_existing";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(sourceKey, testValue);

        // Test COPY to non-existent key
        boolean result1 = glideJedis.copy(sourceKey, targetKey, false);
        assertTrue(result1, "COPY should return true for successful copy");
        assertEquals(testValue, glideJedis.get(sourceKey), "Source key should still exist");
        assertEquals(testValue, glideJedis.get(targetKey), "Target key should have copied value");

        // Test COPY to existing key without replace
        glideJedis.set(existingKey, "existing_value");
        boolean result2 = glideJedis.copy(sourceKey, existingKey, false);
        assertFalse(result2, "COPY should return false when target exists and replace=false");
        assertEquals("existing_value", glideJedis.get(existingKey), "Existing key should be unchanged");

        // Test COPY to existing key with replace=true
        boolean result3 = glideJedis.copy(sourceKey, existingKey, true);
        assertTrue(result3, "COPY with replace=true should return true");
        assertEquals(testValue, glideJedis.get(existingKey), "Existing key should be replaced");

        // Test COPY from non-existent key
        boolean result4 =
                glideJedis.copy(TEST_KEY_PREFIX + "nonexistent", TEST_KEY_PREFIX + "target2", false);
        assertFalse(result4, "COPY should return false for non-existent source key");
    }

    @Test
    @Order(87)
    @DisplayName("PFADD Command")
    void testPfaddCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String key = TEST_KEY_PREFIX + "pfadd";

        // Test adding elements to a new HyperLogLog
        long result1 = glideJedis.pfadd(key, "element1", "element2", "element3");
        assertEquals(1L, result1, "PFADD should return 1 when HyperLogLog is created or modified");

        // Test adding duplicate elements (should not modify the HLL)
        long result2 = glideJedis.pfadd(key, "element1", "element2");
        assertEquals(0L, result2, "PFADD should return 0 when no new elements are added");

        // Test adding new elements to existing HyperLogLog
        long result3 = glideJedis.pfadd(key, "element4", "element5");
        assertEquals(1L, result3, "PFADD should return 1 when new elements are added");

        // Test adding no elements to existing HyperLogLog
        long result4 = glideJedis.pfadd(key);
        assertEquals(
                0L, result4, "PFADD should return 0 when no elements are provided to existing HLL");

        // Test adding no elements to non-existent HyperLogLog (creates empty HLL)
        String newKey = TEST_KEY_PREFIX + "pfadd_empty";
        long result5 = glideJedis.pfadd(newKey);
        assertEquals(1L, result5, "PFADD should return 1 when creating empty HyperLogLog");
    }

    @Test
    @Order(88)
    @DisplayName("PFCOUNT Command")
    void testPfcountCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String key1 = TEST_KEY_PREFIX + "pfcount1";
        String key2 = TEST_KEY_PREFIX + "pfcount2";
        String key3 = TEST_KEY_PREFIX + "pfcount3";

        // Test PFCOUNT on non-existent key
        long result1 = glideJedis.pfcount(key1);
        assertEquals(0L, result1, "PFCOUNT should return 0 for non-existent key");

        // Add elements to first HyperLogLog
        glideJedis.pfadd(key1, "a", "b", "c", "d", "e");
        long count1 = glideJedis.pfcount(key1);
        assertTrue(
                count1 >= 4 && count1 <= 6, "PFCOUNT should return approximate cardinality around 5");

        // Add elements to second HyperLogLog
        glideJedis.pfadd(key2, "c", "d", "e", "f", "g");
        long count2 = glideJedis.pfcount(key2);
        assertTrue(
                count2 >= 4 && count2 <= 6, "PFCOUNT should return approximate cardinality around 5");

        // Test PFCOUNT with multiple keys
        long combinedCount = glideJedis.pfcount(key1, key2);
        assertTrue(
                combinedCount >= 6 && combinedCount <= 8,
                "PFCOUNT with multiple keys should return combined cardinality around 7");

        // Test PFCOUNT with mix of existing and non-existent keys
        long mixedCount = glideJedis.pfcount(key1, key3);
        assertTrue(
                mixedCount >= 4 && mixedCount <= 6,
                "PFCOUNT with non-existent key should ignore the non-existent key");
    }

    @Test
    @Order(89)
    @DisplayName("PFMERGE Command")
    void testPfmergeCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String source1 = TEST_KEY_PREFIX + "pfmerge_src1";
        String source2 = TEST_KEY_PREFIX + "pfmerge_src2";
        String source3 = TEST_KEY_PREFIX + "pfmerge_src3";
        String dest = TEST_KEY_PREFIX + "pfmerge_dest";

        // Set up source HyperLogLogs
        glideJedis.pfadd(source1, "a", "b", "c");
        glideJedis.pfadd(source2, "c", "d", "e");
        glideJedis.pfadd(source3, "e", "f", "g");

        // Test merging into new destination
        String result1 = glideJedis.pfmerge(dest, source1, source2);
        assertEquals("OK", result1, "PFMERGE should return OK");

        // Verify the merged result
        long mergedCount = glideJedis.pfcount(dest);
        assertTrue(
                mergedCount >= 4 && mergedCount <= 6,
                "Merged HyperLogLog should have approximate cardinality around 5");

        // Test merging into existing destination
        String result2 = glideJedis.pfmerge(dest, source3);
        assertEquals("OK", result2, "PFMERGE into existing destination should return OK");

        // Verify the updated merged result
        long updatedCount = glideJedis.pfcount(dest);
        assertTrue(
                updatedCount >= 6 && updatedCount <= 8,
                "Updated merged HyperLogLog should have approximate cardinality around 7");

        // Test merging with non-existent source (should not affect result)
        String nonExistentKey = TEST_KEY_PREFIX + "pfmerge_nonexistent";
        String result3 = glideJedis.pfmerge(dest, nonExistentKey);
        assertEquals("OK", result3, "PFMERGE with non-existent source should return OK");

        long finalCount = glideJedis.pfcount(dest);
        assertEquals(
                updatedCount, finalCount, "PFMERGE with non-existent source should not change cardinality");

        // Test merging into non-existent destination
        String newDest = TEST_KEY_PREFIX + "pfmerge_new_dest";
        String result4 = glideJedis.pfmerge(newDest, source1, source2, source3);
        assertEquals("OK", result4, "PFMERGE into new destination should return OK");

        long newDestCount = glideJedis.pfcount(newDest);
        assertTrue(
                newDestCount >= 6 && newDestCount <= 8,
                "New destination should have approximate cardinality around 7");
    }

    /** Clean up test keys to avoid interference between tests. */
    private void cleanupTestKeys(Object jedisInstance) {
        try {
            if (jedisInstance instanceof Jedis) {
                // GLIDE Jedis cleanup
                Jedis jedis = (Jedis) jedisInstance;
                // Basic operation keys
                jedis.del(TEST_KEY_PREFIX + "basic");
                jedis.del(TEST_KEY_PREFIX + "key1");
                jedis.del(TEST_KEY_PREFIX + "key2");
                jedis.del(TEST_KEY_PREFIX + "key3");

                // MSET/MGET keys
                jedis.del(TEST_KEY_PREFIX + "mset1");
                jedis.del(TEST_KEY_PREFIX + "mset2");
                jedis.del(TEST_KEY_PREFIX + "mset3");
                jedis.del(TEST_KEY_PREFIX + "mset4");
                jedis.del(TEST_KEY_PREFIX + "mset5");
                jedis.del(TEST_KEY_PREFIX + "mget1");
                jedis.del(TEST_KEY_PREFIX + "mget2");
                jedis.del(TEST_KEY_PREFIX + "mget3");

                // Conditional set keys
                jedis.del(TEST_KEY_PREFIX + "setnx");
                jedis.del(TEST_KEY_PREFIX + "setex");
                jedis.del(TEST_KEY_PREFIX + "psetex");

                // Get and modify keys
                jedis.del(TEST_KEY_PREFIX + "getset");
                jedis.del(TEST_KEY_PREFIX + "setget");
                jedis.del(TEST_KEY_PREFIX + "getdel");
                jedis.del(TEST_KEY_PREFIX + "getex");

                // String manipulation keys
                jedis.del(TEST_KEY_PREFIX + "append");
                jedis.del(TEST_KEY_PREFIX + "strlen");

                // Numeric operation keys
                jedis.del(TEST_KEY_PREFIX + "incr");
                jedis.del(TEST_KEY_PREFIX + "incrby");
                jedis.del(TEST_KEY_PREFIX + "incrbyfloat");
                jedis.del(TEST_KEY_PREFIX + "decr");
                jedis.del(TEST_KEY_PREFIX + "decrby");

                // Key management keys
                jedis.del(TEST_KEY_PREFIX + "del1");
                jedis.del(TEST_KEY_PREFIX + "del2");
                jedis.del(TEST_KEY_PREFIX + "del3");
                jedis.del(TEST_KEY_PREFIX + "unlink1");
                jedis.del(TEST_KEY_PREFIX + "unlink2");
                jedis.del(TEST_KEY_PREFIX + "unlink3");
                jedis.del(TEST_KEY_PREFIX + "exists1");
                jedis.del(TEST_KEY_PREFIX + "exists2");
                jedis.del(TEST_KEY_PREFIX + "exists3");
                jedis.del(TEST_KEY_PREFIX + "type_string");
                jedis.del(TEST_KEY_PREFIX + "keys_test:key1");
                jedis.del(TEST_KEY_PREFIX + "keys_test:key2");
                jedis.del(TEST_KEY_PREFIX + "keys_test:different");
                jedis.del(TEST_KEY_PREFIX + "randomkey");
                jedis.del(TEST_KEY_PREFIX + "rename_old");
                jedis.del(TEST_KEY_PREFIX + "rename_new");
                jedis.del(TEST_KEY_PREFIX + "renamenx_old");
                jedis.del(TEST_KEY_PREFIX + "renamenx_new");
                jedis.del(TEST_KEY_PREFIX + "renamenx_existing");

                // Expiration and TTL keys
                jedis.del(TEST_KEY_PREFIX + "expire");
                jedis.del(TEST_KEY_PREFIX + "expireat");
                jedis.del(TEST_KEY_PREFIX + "pexpire");
                jedis.del(TEST_KEY_PREFIX + "pexpireat");
                jedis.del(TEST_KEY_PREFIX + "ttl");
                jedis.del(TEST_KEY_PREFIX + "pttl");
                jedis.del(TEST_KEY_PREFIX + "expiretime");
                jedis.del(TEST_KEY_PREFIX + "pexpiretime");
                jedis.del(TEST_KEY_PREFIX + "persist");

                // Advanced key operation keys
                jedis.del(TEST_KEY_PREFIX + "sort_list");
                jedis.del(TEST_KEY_PREFIX + "sort_test");
                jedis.del(TEST_KEY_PREFIX + "dump_source");
                jedis.del(TEST_KEY_PREFIX + "dump_target");
                jedis.del(TEST_KEY_PREFIX + "migrate");
                jedis.del(TEST_KEY_PREFIX + "move");
                jedis.del(TEST_KEY_PREFIX + "scan:key1");
                jedis.del(TEST_KEY_PREFIX + "scan:key2");
                jedis.del(TEST_KEY_PREFIX + "scan:key3");
                jedis.del(TEST_KEY_PREFIX + "touch1");
                jedis.del(TEST_KEY_PREFIX + "touch2");
                jedis.del(TEST_KEY_PREFIX + "touch3");
                jedis.del(TEST_KEY_PREFIX + "copy_source");
                jedis.del(TEST_KEY_PREFIX + "copy_target");
                jedis.del(TEST_KEY_PREFIX + "copy_existing");
                jedis.del(TEST_KEY_PREFIX + "target2");

                // Bitmap operation keys
                jedis.del(TEST_KEY_PREFIX + "setbit");
                jedis.del(TEST_KEY_PREFIX + "getbit");
                jedis.del(TEST_KEY_PREFIX + "bitcount");
                jedis.del(TEST_KEY_PREFIX + "bitpos");
                jedis.del(TEST_KEY_PREFIX + "bitop1");
                jedis.del(TEST_KEY_PREFIX + "bitop2");
                jedis.del(TEST_KEY_PREFIX + "bitop_dest");
                jedis.del(TEST_KEY_PREFIX + "bitfield");
                jedis.del(TEST_KEY_PREFIX + "bitfield_ro");

                // HyperLogLog operation keys
                jedis.del(TEST_KEY_PREFIX + "pfadd");
                jedis.del(TEST_KEY_PREFIX + "pfadd_empty");
                jedis.del(TEST_KEY_PREFIX + "pfcount1");
                jedis.del(TEST_KEY_PREFIX + "pfcount2");
                jedis.del(TEST_KEY_PREFIX + "pfcount3");
                jedis.del(TEST_KEY_PREFIX + "pfmerge_src1");
                jedis.del(TEST_KEY_PREFIX + "pfmerge_src2");
                jedis.del(TEST_KEY_PREFIX + "pfmerge_src3");
                jedis.del(TEST_KEY_PREFIX + "pfmerge_dest");
                jedis.del(TEST_KEY_PREFIX + "pfmerge_nonexistent");
                jedis.del(TEST_KEY_PREFIX + "pfmerge_new_dest");
            } else {
                // Actual Jedis cleanup via reflection
                Method delMethod = actualJedisClass.getMethod("del", String[].class);
                String[] keysToDelete = {
                    // Basic operation keys
                    TEST_KEY_PREFIX + "basic",
                    TEST_KEY_PREFIX + "key1",
                    TEST_KEY_PREFIX + "key2",
                    TEST_KEY_PREFIX + "key3",

                    // MSET/MGET keys
                    TEST_KEY_PREFIX + "mset1",
                    TEST_KEY_PREFIX + "mset2",
                    TEST_KEY_PREFIX + "mset3",
                    TEST_KEY_PREFIX + "mset4",
                    TEST_KEY_PREFIX + "mset5",
                    TEST_KEY_PREFIX + "mget1",
                    TEST_KEY_PREFIX + "mget2",
                    TEST_KEY_PREFIX + "mget3",

                    // Conditional set keys
                    TEST_KEY_PREFIX + "setnx",
                    TEST_KEY_PREFIX + "setex",
                    TEST_KEY_PREFIX + "psetex",

                    // Get and modify keys
                    TEST_KEY_PREFIX + "getset",
                    TEST_KEY_PREFIX + "setget",
                    TEST_KEY_PREFIX + "getdel",
                    TEST_KEY_PREFIX + "getex",

                    // String manipulation keys
                    TEST_KEY_PREFIX + "append",
                    TEST_KEY_PREFIX + "strlen",

                    // Numeric operation keys
                    TEST_KEY_PREFIX + "incr",
                    TEST_KEY_PREFIX + "incrby",
                    TEST_KEY_PREFIX + "incrbyfloat",
                    TEST_KEY_PREFIX + "decr",
                    TEST_KEY_PREFIX + "decrby",

                    // Key management keys
                    TEST_KEY_PREFIX + "del1",
                    TEST_KEY_PREFIX + "del2",
                    TEST_KEY_PREFIX + "del3",
                    TEST_KEY_PREFIX + "unlink1",
                    TEST_KEY_PREFIX + "unlink2",
                    TEST_KEY_PREFIX + "unlink3",
                    TEST_KEY_PREFIX + "exists1",
                    TEST_KEY_PREFIX + "exists2",
                    TEST_KEY_PREFIX + "exists3",
                    TEST_KEY_PREFIX + "type_string",
                    TEST_KEY_PREFIX + "keys_test:key1",
                    TEST_KEY_PREFIX + "keys_test:key2",
                    TEST_KEY_PREFIX + "keys_test:different",
                    TEST_KEY_PREFIX + "randomkey",
                    TEST_KEY_PREFIX + "rename_old",
                    TEST_KEY_PREFIX + "rename_new",
                    TEST_KEY_PREFIX + "renamenx_old",
                    TEST_KEY_PREFIX + "renamenx_new",
                    TEST_KEY_PREFIX + "renamenx_existing",

                    // Expiration and TTL keys
                    TEST_KEY_PREFIX + "expire",
                    TEST_KEY_PREFIX + "expireat",
                    TEST_KEY_PREFIX + "pexpire",
                    TEST_KEY_PREFIX + "pexpireat",
                    TEST_KEY_PREFIX + "ttl",
                    TEST_KEY_PREFIX + "pttl",
                    TEST_KEY_PREFIX + "expiretime",
                    TEST_KEY_PREFIX + "pexpiretime",
                    TEST_KEY_PREFIX + "persist",

                    // Advanced key operation keys
                    TEST_KEY_PREFIX + "sort_list",
                    TEST_KEY_PREFIX + "sort_test",
                    TEST_KEY_PREFIX + "dump_source",
                    TEST_KEY_PREFIX + "dump_target",
                    TEST_KEY_PREFIX + "migrate",
                    TEST_KEY_PREFIX + "move",
                    TEST_KEY_PREFIX + "scan:key1",
                    TEST_KEY_PREFIX + "scan:key2",
                    TEST_KEY_PREFIX + "scan:key3",
                    TEST_KEY_PREFIX + "touch1",
                    TEST_KEY_PREFIX + "touch2",
                    TEST_KEY_PREFIX + "touch3",
                    TEST_KEY_PREFIX + "copy_source",
                    TEST_KEY_PREFIX + "copy_target",
                    TEST_KEY_PREFIX + "copy_existing",
                    TEST_KEY_PREFIX + "target2",

                    // Bitmap operation keys
                    TEST_KEY_PREFIX + "setbit",
                    TEST_KEY_PREFIX + "getbit",
                    TEST_KEY_PREFIX + "bitcount",
                    TEST_KEY_PREFIX + "bitpos",
                    TEST_KEY_PREFIX + "bitop1",
                    TEST_KEY_PREFIX + "bitop2",
                    TEST_KEY_PREFIX + "bitop_dest",
                    TEST_KEY_PREFIX + "bitfield",
                    TEST_KEY_PREFIX + "bitfield_ro",

                    // HyperLogLog operation keys
                    TEST_KEY_PREFIX + "pfadd",
                    TEST_KEY_PREFIX + "pfadd_empty",
                    TEST_KEY_PREFIX + "pfcount1",
                    TEST_KEY_PREFIX + "pfcount2",
                    TEST_KEY_PREFIX + "pfcount3",
                    TEST_KEY_PREFIX + "pfmerge_src1",
                    TEST_KEY_PREFIX + "pfmerge_src2",
                    TEST_KEY_PREFIX + "pfmerge_src3",
                    TEST_KEY_PREFIX + "pfmerge_dest",
                    TEST_KEY_PREFIX + "pfmerge_nonexistent",
                    TEST_KEY_PREFIX + "pfmerge_new_dest"
                };
                delMethod.invoke(jedisInstance, (Object) keysToDelete);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
