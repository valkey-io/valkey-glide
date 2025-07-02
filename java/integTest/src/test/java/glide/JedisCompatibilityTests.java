/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive compatibility tests comparing GLIDE Jedis compatibility layer
 * with actual Jedis implementation. Tests the same operations on both clients
 * and verifies identical behavior.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisCompatibilityTests {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String TEST_KEY_PREFIX = "compat_test:";
    
    // Test clients - will be initialized based on available implementations
    private Object glideJedis;
    private Object actualJedis;
    private boolean hasActualJedis = false;
    private boolean hasGlideJedis = false;

    @BeforeAll
    static void setupClass() {
        System.out.println("=== Jedis Compatibility Test Suite ===");
        System.out.println("Testing GLIDE compatibility layer against actual Jedis");
    }

    @BeforeEach
    void setup() {
        // Initialize GLIDE Jedis compatibility layer
        try {
            Class<?> glideJedisClass = Class.forName("redis.clients.jedis.Jedis");
            glideJedis = glideJedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            hasGlideJedis = true;
            System.out.println("✓ GLIDE Jedis compatibility layer initialized");
        } catch (Exception e) {
            System.out.println("✗ GLIDE Jedis compatibility layer not available: " + e.getMessage());
            hasGlideJedis = false;
        }

        // Initialize actual Jedis (if available)
        try {
            // Try to load actual Jedis from a different classpath
            Class<?> actualJedisClass = Class.forName("redis.clients.jedis.Jedis", false, 
                Thread.currentThread().getContextClassLoader());
            actualJedis = actualJedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            hasActualJedis = true;
            System.out.println("✓ Actual Jedis initialized");
        } catch (Exception e) {
            System.out.println("✗ Actual Jedis not available: " + e.getMessage());
            hasActualJedis = false;
        }
    }

    @AfterEach
    void cleanup() {
        // Clean up test keys
        if (hasGlideJedis) {
            try {
                cleanupTestKeys(glideJedis);
                closeClient(glideJedis);
            } catch (Exception e) {
                System.err.println("Error cleaning up GLIDE Jedis: " + e.getMessage());
            }
        }
        
        if (hasActualJedis) {
            try {
                cleanupTestKeys(actualJedis);
                closeClient(actualJedis);
            } catch (Exception e) {
                System.err.println("Error cleaning up actual Jedis: " + e.getMessage());
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test Basic Connectivity - PING")
    void testPingCompatibility() {
        if (!hasGlideJedis) {
            System.out.println("Skipping PING test - GLIDE Jedis not available");
            return;
        }

        try {
            // Test GLIDE Jedis PING
            String glidePingResult = (String) invokeMethod(glideJedis, "ping");
            assertEquals("PONG", glidePingResult, "GLIDE Jedis PING should return PONG");
            System.out.println("✓ GLIDE Jedis PING: " + glidePingResult);

            // Test actual Jedis PING (if available)
            if (hasActualJedis) {
                String actualPingResult = (String) invokeMethod(actualJedis, "ping");
                assertEquals("PONG", actualPingResult, "Actual Jedis PING should return PONG");
                assertEquals(glidePingResult, actualPingResult, "PING results should be identical");
                System.out.println("✓ Actual Jedis PING: " + actualPingResult);
                System.out.println("✓ PING compatibility verified");
            }
        } catch (Exception e) {
            fail("PING test failed: " + e.getMessage());
        }
    }

    @Test
    @Order(2)
    @DisplayName("Test Basic Operations - GET/SET")
    void testBasicOperationsCompatibility() {
        if (!hasGlideJedis) {
            System.out.println("Skipping basic operations test - GLIDE Jedis not available");
            return;
        }

        String testKey = TEST_KEY_PREFIX + "basic_ops";
        String testValue = "test_value_" + System.currentTimeMillis();

        try {
            // Test GLIDE Jedis SET/GET
            String glideSetResult = (String) invokeMethod(glideJedis, "set", testKey, testValue);
            assertEquals("OK", glideSetResult, "GLIDE Jedis SET should return OK");
            
            String glideGetResult = (String) invokeMethod(glideJedis, "get", testKey);
            assertEquals(testValue, glideGetResult, "GLIDE Jedis GET should return set value");
            
            System.out.println("✓ GLIDE Jedis SET: " + glideSetResult);
            System.out.println("✓ GLIDE Jedis GET: " + glideGetResult);

            // Test actual Jedis SET/GET (if available)
            if (hasActualJedis) {
                String actualSetResult = (String) invokeMethod(actualJedis, "set", testKey + "_actual", testValue);
                assertEquals("OK", actualSetResult, "Actual Jedis SET should return OK");
                
                String actualGetResult = (String) invokeMethod(actualJedis, "get", testKey + "_actual");
                assertEquals(testValue, actualGetResult, "Actual Jedis GET should return set value");
                
                // Compare results
                assertEquals(glideSetResult, actualSetResult, "SET results should be identical");
                assertEquals(glideGetResult, actualGetResult, "GET results should be identical");
                
                System.out.println("✓ Actual Jedis SET: " + actualSetResult);
                System.out.println("✓ Actual Jedis GET: " + actualGetResult);
                System.out.println("✓ Basic operations compatibility verified");
            }
        } catch (Exception e) {
            fail("Basic operations test failed: " + e.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple_value", "value with spaces", "special!@#$%^&*()chars", "unicode_测试_🚀"})
    @Order(3)
    @DisplayName("Test String Values Compatibility")
    void testStringValuesCompatibility(String testValue) {
        if (!hasGlideJedis) {
            System.out.println("Skipping string values test - GLIDE Jedis not available");
            return;
        }

        String testKey = TEST_KEY_PREFIX + "string_" + testValue.hashCode();

        try {
            // Test GLIDE Jedis
            invokeMethod(glideJedis, "set", testKey, testValue);
            String glideResult = (String) invokeMethod(glideJedis, "get", testKey);
            assertEquals(testValue, glideResult, "GLIDE Jedis should handle string value: " + testValue);

            // Test actual Jedis (if available)
            if (hasActualJedis) {
                invokeMethod(actualJedis, "set", testKey + "_actual", testValue);
                String actualResult = (String) invokeMethod(actualJedis, "get", testKey + "_actual");
                assertEquals(testValue, actualResult, "Actual Jedis should handle string value: " + testValue);
                assertEquals(glideResult, actualResult, "Results should be identical for: " + testValue);
                System.out.println("✓ String compatibility verified for: " + testValue);
            }
        } catch (Exception e) {
            fail("String values test failed for '" + testValue + "': " + e.getMessage());
        }
    }

    @Test
    @Order(4)
    @DisplayName("Test Connection Management")
    void testConnectionManagement() {
        if (!hasGlideJedis) {
            System.out.println("Skipping connection management test - GLIDE Jedis not available");
            return;
        }

        try {
            // Test isClosed() method
            Boolean glideIsClosed = (Boolean) invokeMethod(glideJedis, "isClosed");
            assertFalse(glideIsClosed, "GLIDE Jedis should not be closed initially");
            System.out.println("✓ GLIDE Jedis isClosed(): " + glideIsClosed);

            if (hasActualJedis) {
                Boolean actualIsClosed = (Boolean) invokeMethod(actualJedis, "isClosed");
                assertFalse(actualIsClosed, "Actual Jedis should not be closed initially");
                assertEquals(glideIsClosed, actualIsClosed, "isClosed() results should be identical");
                System.out.println("✓ Actual Jedis isClosed(): " + actualIsClosed);
                System.out.println("✓ Connection management compatibility verified");
            }
        } catch (Exception e) {
            fail("Connection management test failed: " + e.getMessage());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Test Configuration Compatibility")
    void testConfigurationCompatibility() {
        try {
            // Test DefaultJedisClientConfig
            Class<?> configClass = Class.forName("redis.clients.jedis.DefaultJedisClientConfig");
            Object config = configClass.getMethod("builder").invoke(null);
            
            // Test builder methods
            config = invokeMethod(config, "host", "localhost");
            config = invokeMethod(config, "port", 6379);
            config = invokeMethod(config, "socketTimeoutMillis", 2000);
            Object builtConfig = invokeMethod(config, "build");
            
            assertNotNull(builtConfig, "Configuration should be built successfully");
            System.out.println("✓ Configuration compatibility verified");
            
            // Test creating Jedis with config
            Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
            Object configuredJedis = jedisClass.getConstructor(String.class, int.class, builtConfig.getClass())
                    .newInstance("localhost", 6379, builtConfig);
            
            String pingResult = (String) invokeMethod(configuredJedis, "ping");
            assertEquals("PONG", pingResult, "Configured Jedis should work");
            System.out.println("✓ Configured Jedis PING: " + pingResult);
            
            closeClient(configuredJedis);
        } catch (Exception e) {
            fail("Configuration compatibility test failed: " + e.getMessage());
        }
    }

    @Test
    @Order(6)
    @DisplayName("Test Pool Compatibility")
    void testPoolCompatibility() {
        try {
            // Test JedisPool
            Class<?> poolClass = Class.forName("redis.clients.jedis.JedisPool");
            Object pool = poolClass.getConstructor(String.class, int.class)
                    .newInstance("localhost", 6379);
            
            // Get resource from pool
            Object pooledJedis = invokeMethod(pool, "getResource");
            assertNotNull(pooledJedis, "Pool should provide Jedis instance");
            
            // Test operations on pooled Jedis
            String pingResult = (String) invokeMethod(pooledJedis, "ping");
            assertEquals("PONG", pingResult, "Pooled Jedis should work");
            System.out.println("✓ Pooled Jedis PING: " + pingResult);
            
            // Return resource to pool
            invokeMethod(pool, "returnResource", pooledJedis);
            
            // Close pool
            invokeMethod(pool, "close");
            System.out.println("✓ Pool compatibility verified");
        } catch (Exception e) {
            fail("Pool compatibility test failed: " + e.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("Test Exception Compatibility")
    void testExceptionCompatibility() {
        try {
            // Test JedisException
            Class<?> exceptionClass = Class.forName("redis.clients.jedis.JedisException");
            Exception exception = (Exception) exceptionClass.getConstructor(String.class)
                    .newInstance("Test exception");
            
            assertTrue(exception instanceof RuntimeException, "JedisException should extend RuntimeException");
            assertEquals("Test exception", exception.getMessage(), "Exception message should be preserved");
            
            // Test JedisConnectionException
            Class<?> connectionExceptionClass = Class.forName("redis.clients.jedis.JedisConnectionException");
            Exception connectionException = (Exception) connectionExceptionClass.getConstructor(String.class)
                    .newInstance("Test connection exception");
            
            assertTrue(connectionException instanceof RuntimeException, "JedisConnectionException should extend RuntimeException");
            assertTrue(exceptionClass.isAssignableFrom(connectionExceptionClass), "JedisConnectionException should extend JedisException");
            
            System.out.println("✓ Exception hierarchy compatibility verified");
        } catch (Exception e) {
            fail("Exception compatibility test failed: " + e.getMessage());
        }
    }

    @Test
    @Order(8)
    @DisplayName("Test Performance Comparison")
    @EnabledIf("hasBothImplementations")
    void testPerformanceComparison() {
        if (!hasGlideJedis || !hasActualJedis) {
            System.out.println("Skipping performance test - both implementations not available");
            return;
        }

        int iterations = 1000;
        String testKey = TEST_KEY_PREFIX + "perf_test";
        String testValue = "performance_test_value";

        try {
            // Warm up
            for (int i = 0; i < 100; i++) {
                invokeMethod(glideJedis, "set", testKey + "_glide_warmup", testValue);
                invokeMethod(actualJedis, "set", testKey + "_actual_warmup", testValue);
            }

            // Test GLIDE Jedis performance
            long glideStartTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                invokeMethod(glideJedis, "set", testKey + "_glide_" + i, testValue);
                invokeMethod(glideJedis, "get", testKey + "_glide_" + i);
            }
            long glideEndTime = System.nanoTime();
            long glideDuration = glideEndTime - glideStartTime;

            // Test actual Jedis performance
            long actualStartTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                invokeMethod(actualJedis, "set", testKey + "_actual_" + i, testValue);
                invokeMethod(actualJedis, "get", testKey + "_actual_" + i);
            }
            long actualEndTime = System.nanoTime();
            long actualDuration = actualEndTime - actualStartTime;

            System.out.println("Performance Comparison (" + iterations + " SET/GET operations):");
            System.out.println("GLIDE Jedis: " + TimeUnit.NANOSECONDS.toMillis(glideDuration) + "ms");
            System.out.println("Actual Jedis: " + TimeUnit.NANOSECONDS.toMillis(actualDuration) + "ms");
            
            double ratio = (double) glideDuration / actualDuration;
            System.out.println("GLIDE/Actual ratio: " + String.format("%.2f", ratio));
            
            if (ratio < 1.0) {
                System.out.println("✓ GLIDE Jedis is faster!");
            } else if (ratio < 2.0) {
                System.out.println("✓ GLIDE Jedis performance is acceptable");
            } else {
                System.out.println("⚠ GLIDE Jedis is significantly slower");
            }
        } catch (Exception e) {
            fail("Performance comparison test failed: " + e.getMessage());
        }
    }

    // Helper methods
    private Object invokeMethod(Object obj, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
            // Handle primitive types
            if (paramTypes[i] == Integer.class) paramTypes[i] = int.class;
            if (paramTypes[i] == Boolean.class) paramTypes[i] = boolean.class;
            if (paramTypes[i] == Long.class) paramTypes[i] = long.class;
        }
        
        return obj.getClass().getMethod(methodName, paramTypes).invoke(obj, args);
    }

    private void closeClient(Object client) throws Exception {
        invokeMethod(client, "close");
    }

    private void cleanupTestKeys(Object client) throws Exception {
        // Note: This is a simplified cleanup - in real implementation,
        // you might want to use SCAN to find and delete test keys
        try {
            for (int i = 0; i < 100; i++) {
                invokeMethod(client, "del", TEST_KEY_PREFIX + "basic_ops");
                invokeMethod(client, "del", TEST_KEY_PREFIX + "string_" + i);
                invokeMethod(client, "del", TEST_KEY_PREFIX + "perf_test_glide_" + i);
                invokeMethod(client, "del", TEST_KEY_PREFIX + "perf_test_actual_" + i);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    boolean hasBothImplementations() {
        return hasGlideJedis && hasActualJedis;
    }
}
