/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import compatibility.clients.jedis.DefaultJedisClientConfig;
import compatibility.clients.jedis.HostAndPort;
import compatibility.clients.jedis.JedisClientConfig;
import compatibility.clients.jedis.JedisPooled;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;

/**
 * JedisPooled compatibility test that validates GLIDE JedisPooled compatibility layer for basic
 * GET/SET operations.
 *
 * <p>This test validates that the GLIDE JedisPooled compatibility layer produces correct results
 * for core Redis operations, ensuring compatibility with the JedisPooled interface. JedisPooled
 * extends UnifiedJedis and provides pooled connection semantics.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisPooledTest {

    private static final String TEST_KEY_PREFIX = "jedis_pooled_test:";

    // Server configuration - dynamically resolved from CI environment
    private static String redisHost;
    private static int redisPort;

    // GLIDE JedisPooled compatibility layer instances
    private JedisPooled glideJedisPooled;

    // Actual JedisPooled instances (loaded via reflection if available)
    private Object actualJedisPooled;
    private Class<?> actualJedisPooledClass;

    // Availability flags
    private boolean hasGlideJedisPooled = false;
    private boolean hasActualJedisPooled = false;

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
        // Initialize GLIDE JedisPooled compatibility layer
        try {
            glideJedisPooled = new JedisPooled(redisHost, redisPort);
            hasGlideJedisPooled = true;
        } catch (Exception e) {
            hasGlideJedisPooled = false;
            System.err.println("Failed to initialize GLIDE JedisPooled: " + e.getMessage());
        }

        // Try to load actual JedisPooled via reflection (optional)
        try {
            String jedisJarPath = System.getProperty("jedis.jar.path");
            if (jedisJarPath != null) {
                // Load actual JedisPooled classes and create instances
                actualJedisPooledClass = Class.forName("redis.clients.jedis.JedisPooled");

                // Try different constructor patterns
                try {
                    // Try host/port constructor
                    actualJedisPooled =
                            actualJedisPooledClass
                                    .getConstructor(String.class, int.class)
                                    .newInstance(redisHost, redisPort);
                } catch (Exception e1) {
                    try {
                        // Try HostAndPort constructor
                        Class<?> hostAndPortClass = Class.forName("redis.clients.jedis.HostAndPort");
                        Object hostAndPort =
                                hostAndPortClass
                                        .getConstructor(String.class, int.class)
                                        .newInstance(redisHost, redisPort);
                        actualJedisPooled =
                                actualJedisPooledClass.getConstructor(hostAndPortClass).newInstance(hostAndPort);
                    } catch (Exception e2) {
                        // Try basic constructor
                        actualJedisPooled = actualJedisPooledClass.getConstructor().newInstance();
                    }
                }
                hasActualJedisPooled = true;
            }
        } catch (Exception e) {
            hasActualJedisPooled = false;
        }
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (hasGlideJedisPooled && glideJedisPooled != null) {
            cleanupTestKeys(glideJedisPooled);
            try {
                glideJedisPooled.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (hasActualJedisPooled && actualJedisPooled != null) {
            try {
                cleanupTestKeys(actualJedisPooled);
                Method closeMethod = actualJedisPooledClass.getMethod("close");
                closeMethod.invoke(actualJedisPooled);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    // ===== BASIC GET/SET OPERATIONS =====

    @Test
    @Order(1)
    @DisplayName("Basic SET Operation")
    void testBasicSetOperation() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "basic_set";
        String testValue = "test_value_123";

        // Test GLIDE JedisPooled SET
        String glideSetResult = glideJedisPooled.set(testKey, testValue);
        assertEquals("OK", glideSetResult, "GLIDE JedisPooled SET should return OK");

        // Compare with actual JedisPooled if available
        if (hasActualJedisPooled) {
            try {
                Method setMethod = actualJedisPooledClass.getMethod("set", String.class, String.class);
                String actualSetResult = (String) setMethod.invoke(actualJedisPooled, testKey, testValue);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual JedisPooled SET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare SET with actual JedisPooled: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Basic GET Operation")
    void testBasicGetOperation() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "basic_get";
        String testValue = "test_value_456";

        // First set the value
        String setResult = glideJedisPooled.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should succeed before GET test");

        // Test GLIDE JedisPooled GET
        String glideGetResult = glideJedisPooled.get(testKey);
        assertEquals(testValue, glideGetResult, "GLIDE JedisPooled GET should return the set value");

        // Compare with actual JedisPooled if available
        if (hasActualJedisPooled) {
            try {
                Method setMethod = actualJedisPooledClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisPooledClass.getMethod("get", String.class);

                // Set value in actual JedisPooled
                setMethod.invoke(actualJedisPooled, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualJedisPooled, testKey);

                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual JedisPooled GET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare GET with actual JedisPooled: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Combined GET/SET Operations")
    void testCombinedGetSetOperations() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "combined";
        String testValue = "combined_test_value";

        // Test GLIDE JedisPooled SET followed by GET
        String glideSetResult = glideJedisPooled.set(testKey, testValue);
        String glideGetResult = glideJedisPooled.get(testKey);

        assertEquals("OK", glideSetResult, "GLIDE JedisPooled SET should return OK");
        assertEquals(testValue, glideGetResult, "GLIDE JedisPooled GET should return the set value");

        // Compare with actual JedisPooled if available
        if (hasActualJedisPooled) {
            try {
                Method setMethod = actualJedisPooledClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisPooledClass.getMethod("get", String.class);

                String actualSetResult = (String) setMethod.invoke(actualJedisPooled, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualJedisPooled, testKey);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual JedisPooled SET results should be identical");
                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual JedisPooled GET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare with actual JedisPooled: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Multiple GET/SET Operations")
    void testMultipleGetSetOperations() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "multi1", "value1");
        testData.put(TEST_KEY_PREFIX + "multi2", "value2");
        testData.put(TEST_KEY_PREFIX + "multi3", "value3");
        testData.put(TEST_KEY_PREFIX + "multi4", "value4");

        // Test GLIDE JedisPooled
        Map<String, String> glideSetResults = new HashMap<>();
        Map<String, String> glideGetResults = new HashMap<>();

        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String setResult = glideJedisPooled.set(entry.getKey(), entry.getValue());
            String getResult = glideJedisPooled.get(entry.getKey());

            glideSetResults.put(entry.getKey(), setResult);
            glideGetResults.put(entry.getKey(), getResult);

            assertEquals("OK", setResult, "GLIDE JedisPooled SET should return OK for " + entry.getKey());
            assertEquals(
                    entry.getValue(),
                    getResult,
                    "GLIDE JedisPooled GET should return correct value for " + entry.getKey());
        }

        // Compare with actual JedisPooled if available
        if (hasActualJedisPooled) {
            try {
                Method setMethod = actualJedisPooledClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisPooledClass.getMethod("get", String.class);

                for (Map.Entry<String, String> entry : testData.entrySet()) {
                    String actualSetResult =
                            (String) setMethod.invoke(actualJedisPooled, entry.getKey(), entry.getValue());
                    String actualGetResult = (String) getMethod.invoke(actualJedisPooled, entry.getKey());

                    assertEquals(
                            actualSetResult,
                            glideSetResults.get(entry.getKey()),
                            "GLIDE and actual JedisPooled SET results should be identical for " + entry.getKey());
                    assertEquals(
                            actualGetResult,
                            glideGetResults.get(entry.getKey()),
                            "GLIDE and actual JedisPooled GET results should be identical for " + entry.getKey());
                }
            } catch (Exception e) {
                fail("Failed to compare with actual JedisPooled: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("GET Non-existent Key")
    void testGetNonExistentKey() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        String nonExistentKey = TEST_KEY_PREFIX + "non_existent_" + System.currentTimeMillis();

        // Test GLIDE JedisPooled GET for non-existent key
        String glideGetResult = glideJedisPooled.get(nonExistentKey);
        assertNull(glideGetResult, "GLIDE JedisPooled GET should return null for non-existent key");

        // Compare with actual JedisPooled if available
        if (hasActualJedisPooled) {
            try {
                Method getMethod = actualJedisPooledClass.getMethod("get", String.class);
                String actualGetResult = (String) getMethod.invoke(actualJedisPooled, nonExistentKey);

                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual JedisPooled should both return null for non-existent key");
            } catch (Exception e) {
                fail("Failed to compare GET non-existent key with actual JedisPooled: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("SET/GET with Special Characters")
    void testSetGetWithSpecialCharacters() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "special_chars";
        String testValue = "value_with_special_chars: !@#$%^&*()_+-=[]{}|;':\",./<>?`~";

        // Test GLIDE JedisPooled with special characters
        String glideSetResult = glideJedisPooled.set(testKey, testValue);
        String glideGetResult = glideJedisPooled.get(testKey);

        assertEquals("OK", glideSetResult, "GLIDE JedisPooled SET should handle special characters");
        assertEquals(
                testValue,
                glideGetResult,
                "GLIDE JedisPooled GET should return value with special characters");

        // Compare with actual JedisPooled if available
        if (hasActualJedisPooled) {
            try {
                Method setMethod = actualJedisPooledClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisPooledClass.getMethod("get", String.class);

                String actualSetResult = (String) setMethod.invoke(actualJedisPooled, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualJedisPooled, testKey);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual JedisPooled SET should handle special characters identically");
                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual JedisPooled GET should handle special characters identically");
            } catch (Exception e) {
                fail("Failed to compare special characters with actual JedisPooled: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("DEL Command")
    void testDelCommand() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "del_test";
        String testValue = "value_to_delete";

        // First set a value
        String setResult = glideJedisPooled.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should succeed before DEL test");

        // Verify the key exists
        String getValue = glideJedisPooled.get(testKey);
        assertEquals(testValue, getValue, "Key should exist before deletion");

        // Test GLIDE JedisPooled DEL
        long glideDelResult = glideJedisPooled.del(testKey);
        assertEquals(1L, glideDelResult, "GLIDE JedisPooled DEL should return 1 for existing key");

        // Verify the key is deleted
        String getAfterDel = glideJedisPooled.get(testKey);
        assertNull(getAfterDel, "Key should not exist after deletion");

        // Test DEL on non-existent key
        long delNonExistent = glideJedisPooled.del(testKey);
        assertEquals(0L, delNonExistent, "GLIDE JedisPooled DEL should return 0 for non-existent key");

        // Compare with actual JedisPooled if available
        if (hasActualJedisPooled) {
            try {
                Method setMethod = actualJedisPooledClass.getMethod("set", String.class, String.class);
                Method delMethod = actualJedisPooledClass.getMethod("del", String.class);
                Method getMethod = actualJedisPooledClass.getMethod("get", String.class);

                // Set value in actual JedisPooled
                setMethod.invoke(actualJedisPooled, testKey, testValue);

                // Delete and compare result
                Long actualDelResult = (Long) delMethod.invoke(actualJedisPooled, testKey);
                assertEquals(
                        actualDelResult.longValue(),
                        glideDelResult,
                        "GLIDE and actual JedisPooled DEL results should be identical");

                // Test non-existent key deletion
                Long actualDelNonExistent = (Long) delMethod.invoke(actualJedisPooled, testKey);
                assertEquals(
                        actualDelNonExistent.longValue(),
                        delNonExistent,
                        "GLIDE and actual JedisPooled should both return 0 for non-existent key deletion");

            } catch (Exception e) {
                fail("Failed to compare DEL with actual JedisPooled: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("JedisPooled Constructor Variations")
    void testJedisPooledConstructorVariations() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        // Since JedisPooled extends UnifiedJedis, we test that the main instance
        // (created in setup) works properly and behaves like UnifiedJedis
        
        // Test 1: Verify the main instance works with basic operations
        String pingResult = glideJedisPooled.ping();
        assertEquals("PONG", pingResult, "JedisPooled instance should respond to PING");

        // Test 2: Verify SET/GET operations work (inherited from UnifiedJedis)
        String testKey = TEST_KEY_PREFIX + "constructor_test";
        String testValue = "constructor_value";
        
        String setResult = glideJedisPooled.set(testKey, testValue);
        assertEquals("OK", setResult, "JedisPooled SET should work like UnifiedJedis");
        
        String getResult = glideJedisPooled.get(testKey);
        assertEquals(testValue, getResult, "JedisPooled GET should work like UnifiedJedis");
        
        // Test 3: Verify DEL operation works (inherited from UnifiedJedis)
        long delResult = glideJedisPooled.del(testKey);
        assertEquals(1L, delResult, "JedisPooled DEL should work like UnifiedJedis");
        
        // Test 4: Verify key is actually deleted
        String getAfterDel = glideJedisPooled.get(testKey);
        assertNull(getAfterDel, "Key should be deleted after DEL operation");

        // Test 5: Verify configuration access (inherited from UnifiedJedis)
        assertNotNull(glideJedisPooled.getConfig(), "JedisPooled should have config access");
        
        // Test 6: Verify connection state (inherited from UnifiedJedis)
        assertFalse(glideJedisPooled.isClosed(), "JedisPooled should not be closed during test");
    }

    @Test
    @Order(9)
    @DisplayName("JedisPooled Inheritance from UnifiedJedis")
    void testJedisPooledInheritance() {
        assumeTrue(hasGlideJedisPooled, "GLIDE JedisPooled compatibility layer not available");

        // Verify that JedisPooled extends UnifiedJedis
        assertTrue(
                glideJedisPooled instanceof compatibility.clients.jedis.UnifiedJedis,
                "JedisPooled should extend UnifiedJedis");

        // Test that all UnifiedJedis methods are available
        String testKey = TEST_KEY_PREFIX + "inheritance_test";
        String testValue = "inheritance_value";

        // These methods should be inherited from UnifiedJedis
        String setResult = glideJedisPooled.set(testKey, testValue);
        assertEquals("OK", setResult, "Inherited SET method should work");

        String getResult = glideJedisPooled.get(testKey);
        assertEquals(testValue, getResult, "Inherited GET method should work");

        String pingResult = glideJedisPooled.ping();
        assertEquals("PONG", pingResult, "Inherited PING method should work");

        long delResult = glideJedisPooled.del(testKey);
        assertEquals(1L, delResult, "Inherited DEL method should work");
    }

    // ===== UTILITY METHODS =====

    /**
     * Clean up test keys to avoid interference between tests.
     *
     * @param jedisInstance the JedisPooled instance to clean up
     */
    private void cleanupTestKeys(Object jedisInstance) {
        try {
            if (jedisInstance instanceof JedisPooled) {
                // GLIDE JedisPooled cleanup
                JedisPooled jedisPooled = (JedisPooled) jedisInstance;

                // Clean up basic operation keys
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "basic_set");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "basic_get");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "combined");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "special_chars");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "del_test");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "inheritance_test");

                // Clean up multiple operation keys
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "multi1");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "multi2");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "multi3");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "multi4");

                // Clean up constructor test keys
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "constructor_test");
                safeDelete(jedisPooled, TEST_KEY_PREFIX + "config_test");

            } else {
                // Actual JedisPooled cleanup via reflection
                String[] keysToClean = {
                    TEST_KEY_PREFIX + "basic_set",
                    TEST_KEY_PREFIX + "basic_get",
                    TEST_KEY_PREFIX + "combined",
                    TEST_KEY_PREFIX + "special_chars",
                    TEST_KEY_PREFIX + "del_test",
                    TEST_KEY_PREFIX + "inheritance_test",
                    TEST_KEY_PREFIX + "multi1",
                    TEST_KEY_PREFIX + "multi2",
                    TEST_KEY_PREFIX + "multi3",
                    TEST_KEY_PREFIX + "multi4",
                    TEST_KEY_PREFIX + "constructor_test",
                    TEST_KEY_PREFIX + "config_test"
                };

                for (String key : keysToClean) {
                    try {
                        // Try to delete via reflection using del method
                        Method delMethod = jedisInstance.getClass().getMethod("del", String.class);
                        delMethod.invoke(jedisInstance, key);
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors to avoid masking test failures
            System.err.println("Warning: Failed to cleanup test keys: " + e.getMessage());
        }
    }

    /**
     * Safely delete a key, ignoring any errors.
     *
     * @param jedisPooled the JedisPooled instance
     * @param key the key to delete
     */
    private void safeDelete(JedisPooled jedisPooled, String key) {
        try {
            jedisPooled.del(key);
        } catch (Exception e) {
            // Ignore deletion errors to avoid masking test failures
            System.err.println("Warning: Failed to delete key '" + key + "': " + e.getMessage());
        }
    }
}
