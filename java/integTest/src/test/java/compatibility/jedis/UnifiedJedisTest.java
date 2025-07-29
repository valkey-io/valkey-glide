/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import redis.clients.jedis.UnifiedJedis;

/**
 * UnifiedJedis compatibility test that validates GLIDE UnifiedJedis compatibility layer for basic
 * GET/SET operations.
 *
 * <p>This test validates that the GLIDE UnifiedJedis compatibility layer produces correct results
 * for core Redis operations, ensuring compatibility with the UnifiedJedis interface.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UnifiedJedisTest {

    private static final String TEST_KEY_PREFIX = "unified_jedis_test:";

    // Server configuration - dynamically resolved from CI environment
    private static String redisHost;
    private static int redisPort;

    // GLIDE UnifiedJedis compatibility layer instances
    private UnifiedJedis glideUnifiedJedis;

    // Actual UnifiedJedis instances (loaded via reflection if available)
    private Object actualUnifiedJedis;
    private Class<?> actualUnifiedJedisClass;

    // Availability flags
    private boolean hasGlideUnifiedJedis = false;
    private boolean hasActualUnifiedJedis = false;

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
        // Initialize GLIDE UnifiedJedis compatibility layer
        try {
            glideUnifiedJedis = new UnifiedJedis(redisHost, redisPort);
            hasGlideUnifiedJedis = true;
        } catch (Exception e) {
            hasGlideUnifiedJedis = false;
            System.err.println("Failed to initialize GLIDE UnifiedJedis: " + e.getMessage());
        }

        // Try to load actual UnifiedJedis via reflection (optional)
        try {
            String jedisJarPath = System.getProperty("jedis.jar.path");
            if (jedisJarPath != null) {
                // Load actual UnifiedJedis classes and create instances
                actualUnifiedJedisClass = Class.forName("redis.clients.jedis.UnifiedJedis");

                // Try different constructor patterns
                try {
                    // Try HostAndPort constructor
                    Class<?> hostAndPortClass = Class.forName("redis.clients.jedis.HostAndPort");
                    Object hostAndPort =
                            hostAndPortClass
                                    .getConstructor(String.class, int.class)
                                    .newInstance(redisHost, redisPort);
                    actualUnifiedJedis =
                            actualUnifiedJedisClass.getConstructor(hostAndPortClass).newInstance(hostAndPort);
                } catch (Exception e1) {
                    // Try basic constructor
                    actualUnifiedJedis = actualUnifiedJedisClass.getConstructor().newInstance();
                }
                hasActualUnifiedJedis = true;
            }
        } catch (Exception e) {
            hasActualUnifiedJedis = false;
        }
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys
        if (hasGlideUnifiedJedis && glideUnifiedJedis != null) {
            cleanupTestKeys(glideUnifiedJedis);
            try {
                glideUnifiedJedis.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (hasActualUnifiedJedis && actualUnifiedJedis != null) {
            try {
                cleanupTestKeys(actualUnifiedJedis);
                Method closeMethod = actualUnifiedJedisClass.getMethod("close");
                closeMethod.invoke(actualUnifiedJedis);
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
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "basic_set";
        String testValue = "test_value_123";

        // Test GLIDE UnifiedJedis SET
        String glideSetResult = glideUnifiedJedis.set(testKey, testValue);
        assertEquals("OK", glideSetResult, "GLIDE UnifiedJedis SET should return OK");

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method setMethod = actualUnifiedJedisClass.getMethod("set", String.class, String.class);
                String actualSetResult = (String) setMethod.invoke(actualUnifiedJedis, testKey, testValue);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual UnifiedJedis SET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare SET with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Basic GET Operation")
    void testBasicGetOperation() {
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "basic_get";
        String testValue = "test_value_456";

        // First set the value
        String setResult = glideUnifiedJedis.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should succeed before GET test");

        // Test GLIDE UnifiedJedis GET
        String glideGetResult = glideUnifiedJedis.get(testKey);
        assertEquals(testValue, glideGetResult, "GLIDE UnifiedJedis GET should return the set value");

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method setMethod = actualUnifiedJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualUnifiedJedisClass.getMethod("get", String.class);

                // Set value in actual UnifiedJedis
                setMethod.invoke(actualUnifiedJedis, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualUnifiedJedis, testKey);

                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual UnifiedJedis GET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare GET with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Combined GET/SET Operations")
    void testCombinedGetSetOperations() {
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "combined";
        String testValue = "combined_test_value";

        // Test GLIDE UnifiedJedis SET followed by GET
        String glideSetResult = glideUnifiedJedis.set(testKey, testValue);
        String glideGetResult = glideUnifiedJedis.get(testKey);

        assertEquals("OK", glideSetResult, "GLIDE UnifiedJedis SET should return OK");
        assertEquals(testValue, glideGetResult, "GLIDE UnifiedJedis GET should return the set value");

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method setMethod = actualUnifiedJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualUnifiedJedisClass.getMethod("get", String.class);

                String actualSetResult = (String) setMethod.invoke(actualUnifiedJedis, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualUnifiedJedis, testKey);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual UnifiedJedis SET results should be identical");
                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual UnifiedJedis GET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Multiple GET/SET Operations")
    void testMultipleGetSetOperations() {
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        Map<String, String> testData = new HashMap<>();
        testData.put(TEST_KEY_PREFIX + "multi1", "value1");
        testData.put(TEST_KEY_PREFIX + "multi2", "value2");
        testData.put(TEST_KEY_PREFIX + "multi3", "value3");
        testData.put(TEST_KEY_PREFIX + "multi4", "value4");

        // Test GLIDE UnifiedJedis
        Map<String, String> glideSetResults = new HashMap<>();
        Map<String, String> glideGetResults = new HashMap<>();

        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String setResult = glideUnifiedJedis.set(entry.getKey(), entry.getValue());
            String getResult = glideUnifiedJedis.get(entry.getKey());

            glideSetResults.put(entry.getKey(), setResult);
            glideGetResults.put(entry.getKey(), getResult);

            assertEquals(
                    "OK", setResult, "GLIDE UnifiedJedis SET should return OK for " + entry.getKey());
            assertEquals(
                    entry.getValue(),
                    getResult,
                    "GLIDE UnifiedJedis GET should return correct value for " + entry.getKey());
        }

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method setMethod = actualUnifiedJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualUnifiedJedisClass.getMethod("get", String.class);

                for (Map.Entry<String, String> entry : testData.entrySet()) {
                    String actualSetResult =
                            (String) setMethod.invoke(actualUnifiedJedis, entry.getKey(), entry.getValue());
                    String actualGetResult = (String) getMethod.invoke(actualUnifiedJedis, entry.getKey());

                    assertEquals(
                            actualSetResult,
                            glideSetResults.get(entry.getKey()),
                            "GLIDE and actual UnifiedJedis SET results should be identical for "
                                    + entry.getKey());
                    assertEquals(
                            actualGetResult,
                            glideGetResults.get(entry.getKey()),
                            "GLIDE and actual UnifiedJedis GET results should be identical for "
                                    + entry.getKey());
                }
            } catch (Exception e) {
                fail("Failed to compare with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("GET Non-existent Key")
    void testGetNonExistentKey() {
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        String nonExistentKey = TEST_KEY_PREFIX + "non_existent_" + System.currentTimeMillis();

        // Test GLIDE UnifiedJedis GET for non-existent key
        String glideGetResult = glideUnifiedJedis.get(nonExistentKey);
        assertNull(glideGetResult, "GLIDE UnifiedJedis GET should return null for non-existent key");

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method getMethod = actualUnifiedJedisClass.getMethod("get", String.class);
                String actualGetResult = (String) getMethod.invoke(actualUnifiedJedis, nonExistentKey);

                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual UnifiedJedis should both return null for non-existent key");
            } catch (Exception e) {
                fail("Failed to compare GET non-existent key with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(6)
    @DisplayName("SET/GET with Special Characters")
    void testSetGetWithSpecialCharacters() {
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "special_chars";
        String testValue = "value_with_special_chars: !@#$%^&*()_+-=[]{}|;':\",./<>?`~";

        // Test GLIDE UnifiedJedis with special characters
        String glideSetResult = glideUnifiedJedis.set(testKey, testValue);
        String glideGetResult = glideUnifiedJedis.get(testKey);

        assertEquals("OK", glideSetResult, "GLIDE UnifiedJedis SET should handle special characters");
        assertEquals(
                testValue,
                glideGetResult,
                "GLIDE UnifiedJedis GET should return value with special characters");

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method setMethod = actualUnifiedJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualUnifiedJedisClass.getMethod("get", String.class);

                String actualSetResult = (String) setMethod.invoke(actualUnifiedJedis, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualUnifiedJedis, testKey);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual UnifiedJedis SET should handle special characters identically");
                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual UnifiedJedis GET should handle special characters identically");
            } catch (Exception e) {
                fail("Failed to compare special characters with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("DEL Command")
    void testDelCommand() {
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "del_test";
        String testValue = "value_to_delete";

        // First set a value
        String setResult = glideUnifiedJedis.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should succeed before DEL test");

        // Verify the key exists
        String getValue = glideUnifiedJedis.get(testKey);
        assertEquals(testValue, getValue, "Key should exist before deletion");

        // Test GLIDE UnifiedJedis DEL
        long glideDelResult = glideUnifiedJedis.del(testKey);
        assertEquals(1L, glideDelResult, "GLIDE UnifiedJedis DEL should return 1 for existing key");

        // Verify the key is deleted
        String getAfterDel = glideUnifiedJedis.get(testKey);
        assertNull(getAfterDel, "Key should not exist after deletion");

        // Test DEL on non-existent key
        long delNonExistent = glideUnifiedJedis.del(testKey);
        assertEquals(0L, delNonExistent, "GLIDE UnifiedJedis DEL should return 0 for non-existent key");

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method setMethod = actualUnifiedJedisClass.getMethod("set", String.class, String.class);
                Method delMethod = actualUnifiedJedisClass.getMethod("del", String.class);
                Method getMethod = actualUnifiedJedisClass.getMethod("get", String.class);

                // Set value in actual UnifiedJedis
                setMethod.invoke(actualUnifiedJedis, testKey, testValue);

                // Delete and compare result
                Long actualDelResult = (Long) delMethod.invoke(actualUnifiedJedis, testKey);
                assertEquals(
                        actualDelResult.longValue(),
                        glideDelResult,
                        "GLIDE and actual UnifiedJedis DEL results should be identical");

                // Test non-existent key deletion
                Long actualDelNonExistent = (Long) delMethod.invoke(actualUnifiedJedis, testKey);
                assertEquals(
                        actualDelNonExistent.longValue(),
                        delNonExistent,
                        "GLIDE and actual UnifiedJedis should both return 0 for non-existent key deletion");

            } catch (Exception e) {
                fail("Failed to compare DEL with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("DEL Multiple Keys")
    void testDelMultipleKeys() {
        assumeTrue(hasGlideUnifiedJedis, "GLIDE UnifiedJedis compatibility layer not available");

        String[] testKeys = {
            TEST_KEY_PREFIX + "del_multi1", TEST_KEY_PREFIX + "del_multi2", TEST_KEY_PREFIX + "del_multi3"
        };
        String testValue = "multi_delete_value";

        // Set multiple keys
        for (String key : testKeys) {
            String setResult = glideUnifiedJedis.set(key, testValue);
            assertEquals("OK", setResult, "SET should succeed for key: " + key);
        }

        // Test GLIDE UnifiedJedis DEL with multiple keys
        long glideDelResult = glideUnifiedJedis.del(testKeys);
        assertEquals(
                3L, glideDelResult, "GLIDE UnifiedJedis DEL should return 3 for three existing keys");

        // Verify all keys are deleted
        for (String key : testKeys) {
            String getResult = glideUnifiedJedis.get(key);
            assertNull(getResult, "Key should not exist after deletion: " + key);
        }

        // Test DEL on non-existent keys
        long delNonExistent = glideUnifiedJedis.del(testKeys);
        assertEquals(
                0L, delNonExistent, "GLIDE UnifiedJedis DEL should return 0 for non-existent keys");

        // Compare with actual UnifiedJedis if available
        if (hasActualUnifiedJedis) {
            try {
                Method setMethod = actualUnifiedJedisClass.getMethod("set", String.class, String.class);
                Method delMethod = actualUnifiedJedisClass.getMethod("del", String[].class);

                // Set values in actual UnifiedJedis
                for (String key : testKeys) {
                    setMethod.invoke(actualUnifiedJedis, key, testValue);
                }

                // Delete and compare result
                Long actualDelResult = (Long) delMethod.invoke(actualUnifiedJedis, (Object) testKeys);
                assertEquals(
                        actualDelResult.longValue(),
                        glideDelResult,
                        "GLIDE and actual UnifiedJedis DEL results should be identical for multiple keys");

            } catch (Exception e) {
                fail("Failed to compare multiple DEL with actual UnifiedJedis: " + e.getMessage());
            }
        }
    }

    // ===== UTILITY METHODS =====

    /**
     * Clean up test keys to avoid interference between tests.
     *
     * @param jedisInstance the UnifiedJedis instance to clean up
     */
    private void cleanupTestKeys(Object jedisInstance) {
        try {
            if (jedisInstance instanceof UnifiedJedis) {
                // GLIDE UnifiedJedis cleanup
                UnifiedJedis unifiedJedis = (UnifiedJedis) jedisInstance;

                // Clean up basic operation keys
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "basic_set");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "basic_get");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "combined");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "special_chars");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "del_test");

                // Clean up multiple operation keys
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "multi1");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "multi2");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "multi3");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "multi4");

                // Clean up del test keys
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "del_multi1");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "del_multi2");
                safeDelete(unifiedJedis, TEST_KEY_PREFIX + "del_multi3");

            } else {
                // Actual UnifiedJedis cleanup via reflection
                String[] keysToClean = {
                    TEST_KEY_PREFIX + "basic_set",
                    TEST_KEY_PREFIX + "basic_get",
                    TEST_KEY_PREFIX + "combined",
                    TEST_KEY_PREFIX + "special_chars",
                    TEST_KEY_PREFIX + "del_test",
                    TEST_KEY_PREFIX + "multi1",
                    TEST_KEY_PREFIX + "multi2",
                    TEST_KEY_PREFIX + "multi3",
                    TEST_KEY_PREFIX + "multi4",
                    TEST_KEY_PREFIX + "del_multi1",
                    TEST_KEY_PREFIX + "del_multi2",
                    TEST_KEY_PREFIX + "del_multi3"
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
     * @param unifiedJedis the UnifiedJedis instance
     * @param key the key to delete
     */
    private void safeDelete(UnifiedJedis unifiedJedis, String key) {
        try {
            unifiedJedis.del(key);
        } catch (Exception e) {
            // Ignore deletion errors to avoid masking test failures
            System.err.println("Warning: Failed to delete key '" + key + "': " + e.getMessage());
        }
    }
}
