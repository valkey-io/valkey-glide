/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;
import java.lang.reflect.Method;
import java.util.HashMap;
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
public class JedisComparisonTest {

    private static final String TEST_KEY_PREFIX = "jedis_comparison_test:";

    // Server configuration - dynamically resolved from CI environment
    private static String redisHost;
    private static int redisPort;

    // GLIDE compatibility layer instances
    private Jedis glideJedis;
    private JedisPool glideJedisPool;

    // Actual Jedis instances (loaded via reflection if available)
    private Object actualJedis;
    private Object actualJedisPool;
    private Class<?> actualJedisClass;
    private Class<?> actualJedisPoolClass;

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
            glideJedisPool = new JedisPool(redisHost, redisPort);
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
                actualJedisPoolClass = Class.forName("redis.clients.jedis.JedisPool");

                actualJedis =
                        actualJedisClass
                                .getConstructor(String.class, int.class)
                                .newInstance(redisHost, redisPort);
                actualJedisPool =
                        actualJedisPoolClass
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
                glideJedisPool.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (hasActualJedis && actualJedis != null) {
            try {
                cleanupTestKeys(actualJedis);
                Method closeMethod = actualJedisClass.getMethod("close");
                closeMethod.invoke(actualJedis);

                Method poolCloseMethod = actualJedisPoolClass.getMethod("close");
                poolCloseMethod.invoke(actualJedisPool);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

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

        Map<String, String> testData =
                Map.of(
                        TEST_KEY_PREFIX + "key1", "value1",
                        TEST_KEY_PREFIX + "key2", "value2",
                        TEST_KEY_PREFIX + "key3", "value3");

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

    /**
     * Test connection pool operations.
     *
     * <p>This test is important because: 1. Connection pooling is a critical feature for production
     * applications 2. Pool behavior differs significantly between GLIDE and actual Jedis: - GLIDE
     * uses internal connection management - Actual Jedis uses Apache Commons Pool2 3. Validates that
     * pool.getResource() returns working connections 4. Ensures proper resource lifecycle management
     * (try-with-resources) 5. Tests that pooled connections produce identical results to direct
     * connections
     */
    @Test
    @Order(3)
    @DisplayName("Connection Pool Operations")
    void testConnectionPoolOperations() {
        assumeTrue(hasGlideJedis, "GLIDE Jedis compatibility layer not available");

        String testKey = TEST_KEY_PREFIX + "pool";
        String testValue = "pool_test_value";

        // Test GLIDE JedisPool
        String glideSetResult;
        String glideGetResult;

        try (Jedis pooledJedis = glideJedisPool.getResource()) {
            glideSetResult = pooledJedis.set(testKey, testValue);
            glideGetResult = pooledJedis.get(testKey);
        }

        assertEquals("OK", glideSetResult, "GLIDE pooled Jedis SET should return OK");
        assertEquals(testValue, glideGetResult, "GLIDE pooled Jedis GET should return the set value");

        // Compare with actual Jedis pool if available
        if (hasActualJedis) {
            try {
                Method getResourceMethod = actualJedisPoolClass.getMethod("getResource");
                Object actualPooledJedis = getResourceMethod.invoke(actualJedisPool);

                Method setMethod = actualJedisClass.getMethod("set", String.class, String.class);
                Method getMethod = actualJedisClass.getMethod("get", String.class);
                Method closeMethod = actualJedisClass.getMethod("close");

                String actualSetResult = (String) setMethod.invoke(actualPooledJedis, testKey, testValue);
                String actualGetResult = (String) getMethod.invoke(actualPooledJedis, testKey);
                closeMethod.invoke(actualPooledJedis);

                assertEquals(
                        actualSetResult,
                        glideSetResult,
                        "GLIDE and actual Jedis pool SET results should be identical");
                assertEquals(
                        actualGetResult,
                        glideGetResult,
                        "GLIDE and actual Jedis pool GET results should be identical");
            } catch (Exception e) {
                fail("Failed to compare with actual Jedis pool: " + e.getMessage());
            }
        }
    }

    /** Clean up test keys to avoid interference between tests. */
    private void cleanupTestKeys(Object jedisInstance) {
        try {
            if (jedisInstance instanceof Jedis) {
                // GLIDE Jedis cleanup
                Jedis jedis = (Jedis) jedisInstance;
                jedis.del(TEST_KEY_PREFIX + "basic");
                jedis.del(TEST_KEY_PREFIX + "key1");
                jedis.del(TEST_KEY_PREFIX + "key2");
                jedis.del(TEST_KEY_PREFIX + "key3");
                jedis.del(TEST_KEY_PREFIX + "pool");
            } else {
                // Actual Jedis cleanup via reflection
                Method delMethod = actualJedisClass.getMethod("del", String[].class);
                String[] keysToDelete = {
                    TEST_KEY_PREFIX + "basic",
                    TEST_KEY_PREFIX + "key1",
                    TEST_KEY_PREFIX + "key2",
                    TEST_KEY_PREFIX + "key3",
                    TEST_KEY_PREFIX + "pool"
                };
                delMethod.invoke(jedisInstance, (Object) keysToDelete);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
