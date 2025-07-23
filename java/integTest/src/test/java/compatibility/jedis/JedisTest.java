/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import compatibility.clients.jedis.Jedis;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Resolve Redis/Valkey server address from CI environment properties.
     * Falls back to localhost:6379 if no CI configuration is found.
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
                
                actualJedis = actualJedisClass.getConstructor(String.class, int.class)
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

                assertEquals(actualSetResult, glideSetResult, 
                    "GLIDE and actual Jedis SET results should be identical");
                assertEquals(actualGetResult, glideGetResult, 
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
            assertEquals(entry.getValue(), getResult, "GLIDE Jedis GET should return correct value for " + entry.getKey());
        }

        // Compare with actual Jedis if available
        if (hasActualJedis) {
            try {
                Method setMethod = actualJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisClass.getMethod("get", String.class);

                for (Map.Entry<String, String> entry : testData.entrySet()) {
                    String actualSetResult = (String) setMethod.invoke(actualJedis, entry.getKey(), entry.getValue());
                    String actualGetResult = (String) getMethod.invoke(actualJedis, entry.getKey());

                    assertEquals(actualSetResult, glideSetResults.get(entry.getKey()),
                        "GLIDE and actual Jedis SET results should be identical for " + entry.getKey());
                    assertEquals(actualGetResult, glideGetResults.get(entry.getKey()),
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
        String result1 = glideJedis.mset(
            TEST_KEY_PREFIX + "mset1", "value1",
            TEST_KEY_PREFIX + "mset2", "value2",
            TEST_KEY_PREFIX + "mset3", "value3"
        );
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
        List<String> results = glideJedis.mget(
            TEST_KEY_PREFIX + "mget1",
            TEST_KEY_PREFIX + "mget2",
            TEST_KEY_PREFIX + "mget3",
            TEST_KEY_PREFIX + "nonexistent"
        );

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
        String result1 = glideJedis.getset(testKey, "new_value");
        assertNull(result1, "GETSET should return null for non-existent key");
        assertEquals("new_value", glideJedis.get(testKey), "New value should be set");

        // Test GETSET on existing key
        String result2 = glideJedis.getset(testKey, "newer_value");
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
        String result1 = glideJedis.setget(testKey, "new_value");
        assertNull(result1, "SETGET should return null for non-existent key");
        assertEquals("new_value", glideJedis.get(testKey), "New value should be set");

        // Test SETGET on existing key
        String result2 = glideJedis.setget(testKey, "newer_value");
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
        String result = glideJedis.getdel(testKey);
        assertEquals(testValue, result, "GETDEL should return the value");
        assertNull(glideJedis.get(testKey), "Key should be deleted after GETDEL");

        // Test GETDEL on non-existent key
        String result2 = glideJedis.getdel(testKey + "_nonexistent");
        assertNull(result2, "GETDEL should return null for non-existent key");
    }

    @Test
    @Order(33)
    @DisplayName("GETEX Command")
    void testGetexCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "getex";
        String testValue = "test_value";

        // Set up test data
        glideJedis.set(testKey, testValue);

        // Test GETEX without options
        String result1 = glideJedis.getex(testKey);
        assertEquals(testValue, result1, "GETEX should return the value");

        // Test GETEX with EX option
        String result2 = glideJedis.getex(testKey, "EX", "60");
        assertEquals(testValue, result2, "GETEX with EX should return the value");

        // Test GETEX on non-existent key
        String result3 = glideJedis.getex(testKey + "_nonexistent");
        assertNull(result3, "GETEX should return null for non-existent key");
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
        Long result1 = glideJedis.incrby(testKey, 5);
        assertEquals(5L, result1, "INCRBY should return 5 for non-existent key");

        // Test INCRBY on existing key
        Long result2 = glideJedis.incrby(testKey, 10);
        assertEquals(15L, result2, "INCRBY should increment by 10");

        // Test INCRBY with negative value
        Long result3 = glideJedis.incrby(testKey, -3);
        assertEquals(12L, result3, "INCRBY should handle negative increment");
    }

    @Test
    @Order(52)
    @DisplayName("INCRBYFLOAT Command")
    void testIncrbyfloatCommand() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "incrbyfloat";

        // Test INCRBYFLOAT on non-existent key
        Double result1 = glideJedis.incrbyfloat(testKey, 2.5);
        assertEquals(2.5, result1, 0.001, "INCRBYFLOAT should return 2.5 for non-existent key");

        // Test INCRBYFLOAT on existing key
        Double result2 = glideJedis.incrbyfloat(testKey, 1.5);
        assertEquals(4.0, result2, 0.001, "INCRBYFLOAT should increment by 1.5");

        // Test INCRBYFLOAT with negative value
        Double result3 = glideJedis.incrbyfloat(testKey, -0.5);
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
        Long result1 = glideJedis.decrby(testKey, 5);
        assertEquals(15L, result1, "DECRBY should decrement by 5");

        // Test DECRBY with larger value
        Long result2 = glideJedis.decrby(testKey, 10);
        assertEquals(5L, result2, "DECRBY should decrement by 10");

        // Test DECRBY with negative value (should increment)
        Long result3 = glideJedis.decrby(testKey, -3);
        assertEquals(8L, result3, "DECRBY should handle negative decrement");
    }

    /**
     * Clean up test keys to avoid interference between tests.
     */
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
                    TEST_KEY_PREFIX + "decrby"
                };
                delMethod.invoke(jedisInstance, (Object) keysToDelete);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
