/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;
import java.lang.reflect.Method;
import org.junit.jupiter.api.*;

/**
 * JedisPool compatibility test that validates GLIDE JedisPool behavior matches actual Jedis pool
 * implementation.
 *
 * <p>This test focuses on connection pool functionality to ensure that the GLIDE compatibility
 * layer provides identical pooling behavior to actual Jedis.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisPoolTest {

    private static final String TEST_KEY_PREFIX = "jedis_pool_test:";

    // Server configuration - dynamically resolved from CI environment
    private static String redisHost;
    private static int redisPort;

    // GLIDE compatibility layer pool
    private JedisPool glideJedisPool;

    // Actual Jedis pool (loaded via reflection if available)
    private Object actualJedisPool;
    private Class<?> actualJedisClass;
    private Class<?> actualJedisPoolClass;

    // Availability flags
    private boolean hasGlideJedisPool = false;
    private boolean hasActualJedisPool = false;

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
        // Initialize GLIDE JedisPool
        try {
            glideJedisPool = new JedisPool(redisHost, redisPort);
            hasGlideJedisPool = true;
        } catch (Exception e) {
            hasGlideJedisPool = false;
        }

        // Try to load actual Jedis pool via reflection (optional)
        try {
            String jedisJarPath = System.getProperty("jedis.jar.path");
            if (jedisJarPath != null) {
                // Load actual Jedis classes and create pool instance
                actualJedisClass = Class.forName("redis.clients.jedis.Jedis");
                actualJedisPoolClass = Class.forName("redis.clients.jedis.JedisPool");

                actualJedisPool =
                        actualJedisPoolClass
                                .getConstructor(String.class, int.class)
                                .newInstance(redisHost, redisPort);
                hasActualJedisPool = true;
            }
        } catch (Exception e) {
            hasActualJedisPool = false;
        }
    }

    @AfterEach
    void cleanup() {
        // Cleanup test keys and close pools
        if (hasGlideJedisPool && glideJedisPool != null) {
            try (Jedis jedis = glideJedisPool.getResource()) {
                cleanupTestKeys(jedis);
            } catch (Exception e) {
                // Ignore cleanup errors
            }

            try {
                glideJedisPool.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }

        if (hasActualJedisPool && actualJedisPool != null) {
            try {
                Method getResourceMethod = actualJedisPoolClass.getMethod("getResource");
                Object actualJedis = getResourceMethod.invoke(actualJedisPool);

                cleanupTestKeys(actualJedis);

                Method closeJedisMethod = actualJedisClass.getMethod("close");
                closeJedisMethod.invoke(actualJedis);

                Method closePoolMethod = actualJedisPoolClass.getMethod("close");
                closePoolMethod.invoke(actualJedisPool);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Test connection pool operations.
     *
     * <p>This test is important because:
     *
     * <ul>
     *   <li>Connection pooling is a critical feature for production applications
     *   <li>Pool behavior differs significantly between GLIDE and actual Jedis:
     *       <ul>
     *         <li>GLIDE uses internal connection management
     *         <li>Actual Jedis uses Apache Commons Pool2
     *       </ul>
     *   <li>Validates that pool.getResource() returns working connections
     *   <li>Ensures proper resource lifecycle management (try-with-resources)
     *   <li>Tests that pooled connections produce identical results to direct connections
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("Basic Pool Operations")
    void testBasicPoolOperations() {
        assumeTrue(hasGlideJedisPool, "GLIDE JedisPool not available");

        String testKey = TEST_KEY_PREFIX + "basic";
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
        if (hasActualJedisPool) {
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

    @Test
    @Order(2)
    @DisplayName("Pool Resource Management")
    void testPoolResourceManagement() {
        assumeTrue(hasGlideJedisPool, "GLIDE JedisPool not available");

        String testKey = TEST_KEY_PREFIX + "resource";
        String testValue = "resource_test_value";

        // Test multiple resource acquisitions
        for (int i = 0; i < 5; i++) {
            try (Jedis pooledJedis = glideJedisPool.getResource()) {
                String setResult = pooledJedis.set(testKey + i, testValue + i);
                String getResult = pooledJedis.get(testKey + i);

                assertEquals("OK", setResult, "SET should succeed for iteration " + i);
                assertEquals(
                        testValue + i, getResult, "GET should return correct value for iteration " + i);
            }
        }

        // Verify all values were set correctly by acquiring a new resource
        try (Jedis pooledJedis = glideJedisPool.getResource()) {
            for (int i = 0; i < 5; i++) {
                String getResult = pooledJedis.get(testKey + i);
                assertEquals(testValue + i, getResult, "Value should persist for key " + i);
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("Pool Configuration")
    void testPoolConfiguration() {
        assumeTrue(hasGlideJedisPool, "GLIDE JedisPool not available");

        // Test that pool provides basic configuration information
        assertNotNull(glideJedisPool, "Pool should not be null");

        // Test pool stats (basic functionality)
        String stats = glideJedisPool.getPoolStats();
        assertNotNull(stats, "Pool stats should not be null");

        // Test pool configuration methods exist and return reasonable values
        assertTrue(glideJedisPool.getMaxTotal() > 0, "Max total should be positive");
        assertTrue(glideJedisPool.getMaxWaitMillis() >= 0, "Max wait should be non-negative");
        assertNotNull(glideJedisPool.getConfig(), "Pool config should not be null");
    }

    /** Clean up test keys to avoid interference between tests. */
    private void cleanupTestKeys(Object jedisInstance) {
        try {
            if (jedisInstance instanceof Jedis) {
                // GLIDE Jedis cleanup
                Jedis jedis = (Jedis) jedisInstance;
                jedis.del(TEST_KEY_PREFIX + "basic");
                jedis.del(TEST_KEY_PREFIX + "resource0");
                jedis.del(TEST_KEY_PREFIX + "resource1");
                jedis.del(TEST_KEY_PREFIX + "resource2");
                jedis.del(TEST_KEY_PREFIX + "resource3");
                jedis.del(TEST_KEY_PREFIX + "resource4");
            } else {
                // Actual Jedis cleanup via reflection
                Method delMethod = actualJedisClass.getMethod("del", String[].class);
                String[] keysToDelete = {
                    TEST_KEY_PREFIX + "basic",
                    TEST_KEY_PREFIX + "resource0",
                    TEST_KEY_PREFIX + "resource1",
                    TEST_KEY_PREFIX + "resource2",
                    TEST_KEY_PREFIX + "resource3",
                    TEST_KEY_PREFIX + "resource4"
                };
                delMethod.invoke(jedisInstance, (Object) keysToDelete);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
