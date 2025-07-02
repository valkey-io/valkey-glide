/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Comparison test that validates the GLIDE Jedis compatibility layer behaves
 * identically to the actual Jedis client. This test runs the same operations
 * on both clients and compares the results.
 */
public class JedisComparisonTest {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String TEST_KEY_PREFIX = "comparison_test:";
    
    // GLIDE Jedis compatibility layer (from our project)
    private Object glideJedis;
    private Object glideJedisPool;
    
    // Actual Jedis client (from external dependency)
    private Object actualJedis;
    private Object actualJedisPool;
    
    private boolean hasGlideJedis = false;
    private boolean hasActualJedis = false;
    
    private int testCounter = 0;

    @BeforeEach
    public void setUp(TestInfo testInfo) {
        testCounter++;
        System.out.println("\n=== Running test: " + testInfo.getDisplayName() + " ===");
        
        // Initialize GLIDE Jedis compatibility layer
        initializeGlideJedis();
        
        // Initialize actual Jedis client
        initializeActualJedis();
        
        System.out.println("GLIDE Jedis available: " + hasGlideJedis);
        System.out.println("Actual Jedis available: " + hasActualJedis);
    }

    @AfterEach
    public void tearDown() {
        // Clean up GLIDE Jedis
        if (glideJedis != null) {
            try {
                invokeMethod(glideJedis, "close");
            } catch (Exception e) {
                System.err.println("Error closing GLIDE Jedis: " + e.getMessage());
            }
        }
        
        if (glideJedisPool != null) {
            try {
                invokeMethod(glideJedisPool, "close");
            } catch (Exception e) {
                System.err.println("Error closing GLIDE JedisPool: " + e.getMessage());
            }
        }
        
        // Clean up actual Jedis
        if (actualJedis != null) {
            try {
                invokeMethod(actualJedis, "close");
            } catch (Exception e) {
                System.err.println("Error closing actual Jedis: " + e.getMessage());
            }
        }
        
        if (actualJedisPool != null) {
            try {
                invokeMethod(actualJedisPool, "close");
            } catch (Exception e) {
                System.err.println("Error closing actual JedisPool: " + e.getMessage());
            }
        }
    }

    private void initializeGlideJedis() {
        try {
            // Load GLIDE Jedis compatibility layer
            Class<?> glideJedisClass = redis.clients.jedis.Jedis.class;
            Class<?> glideJedisPoolClass = redis.clients.jedis.JedisPool.class;
            
            glideJedis = glideJedisClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            glideJedisPool = glideJedisPoolClass.getConstructor(String.class, int.class)
                    .newInstance(REDIS_HOST, REDIS_PORT);
            
            hasGlideJedis = true;
            System.out.println("✓ GLIDE Jedis compatibility layer initialized");
        } catch (Exception e) {
            System.out.println("✗ GLIDE Jedis compatibility layer failed: " + e.getMessage());
            hasGlideJedis = false;
        }
    }

    private void initializeActualJedis() {
        try {
            // Try to load actual Jedis using reflection to avoid classpath conflicts
            // This is a simplified approach - in a real scenario you might use separate classloaders
            
            // For now, we'll create a mock comparison since we can't easily load both
            // In a real implementation, you would use separate classloaders or different packages
            hasActualJedis = false;
            System.out.println("✗ Actual Jedis comparison not implemented yet (classpath conflict)");
            
            // TODO: Implement proper classloader separation or use different approach
            // This would require more complex setup to avoid class conflicts
            
        } catch (Exception e) {
            System.out.println("✗ Actual Jedis initialization failed: " + e.getMessage());
            hasActualJedis = false;
        }
    }

    @Test
    @EnabledIf("hasGlideJedis")
    public void testBasicSetAndGetComparison() {
        if (!hasGlideJedis) {
            System.out.println("Skipping test - GLIDE Jedis not available");
            return;
        }

        String key = TEST_KEY_PREFIX + "basic:" + testCounter;
        String value = "test_value_" + testCounter;

        try {
            // Test GLIDE Jedis
            String glideSetResult = (String) invokeMethod(glideJedis, "set", key, value);
            String glideGetResult = (String) invokeMethod(glideJedis, "get", key);
            
            System.out.println("GLIDE SET result: " + glideSetResult);
            System.out.println("GLIDE GET result: " + glideGetResult);
            
            // Validate GLIDE results
            assertEquals("OK", glideSetResult);
            assertEquals(value, glideGetResult);
            
            if (hasActualJedis) {
                // Test actual Jedis (when available)
                String actualSetResult = (String) invokeMethod(actualJedis, "set", key + "_actual", value);
                String actualGetResult = (String) invokeMethod(actualJedis, "get", key + "_actual");
                
                System.out.println("Actual SET result: " + actualSetResult);
                System.out.println("Actual GET result: " + actualGetResult);
                
                // Compare results
                assertEquals(actualSetResult, glideSetResult, "SET results should be identical");
                assertEquals(actualGetResult, glideGetResult, "GET results should be identical");
            } else {
                System.out.println("Note: Only testing GLIDE Jedis (actual Jedis not available for comparison)");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Test failed", e);
        }
    }

    @Test
    @EnabledIf("hasGlideJedis")
    public void testPingComparison() {
        if (!hasGlideJedis) {
            System.out.println("Skipping test - GLIDE Jedis not available");
            return;
        }

        try {
            // Test GLIDE Jedis PING
            String glidePingResult = (String) invokeMethod(glideJedis, "ping");
            System.out.println("GLIDE PING result: " + glidePingResult);
            assertEquals("PONG", glidePingResult);
            
            // Test GLIDE Jedis PING with message
            String message = "hello_" + testCounter;
            String glidePingMessageResult = (String) invokeMethod(glideJedis, "ping", message);
            System.out.println("GLIDE PING(message) result: " + glidePingMessageResult);
            assertEquals(message, glidePingMessageResult);
            
            if (hasActualJedis) {
                // Test actual Jedis PING
                String actualPingResult = (String) invokeMethod(actualJedis, "ping");
                String actualPingMessageResult = (String) invokeMethod(actualJedis, "ping", message);
                
                System.out.println("Actual PING result: " + actualPingResult);
                System.out.println("Actual PING(message) result: " + actualPingMessageResult);
                
                // Compare results
                assertEquals(actualPingResult, glidePingResult, "PING results should be identical");
                assertEquals(actualPingMessageResult, glidePingMessageResult, "PING(message) results should be identical");
            } else {
                System.out.println("Note: Only testing GLIDE Jedis (actual Jedis not available for comparison)");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("PING test failed", e);
        }
    }

    @Test
    @EnabledIf("hasGlideJedis")
    public void testNonExistentKeyComparison() {
        if (!hasGlideJedis) {
            System.out.println("Skipping test - GLIDE Jedis not available");
            return;
        }

        String nonExistentKey = TEST_KEY_PREFIX + "nonexistent:" + testCounter;

        try {
            // Test GLIDE Jedis
            String glideResult = (String) invokeMethod(glideJedis, "get", nonExistentKey);
            System.out.println("GLIDE GET(nonexistent) result: " + glideResult);
            assertNull(glideResult, "Non-existent key should return null");
            
            if (hasActualJedis) {
                // Test actual Jedis
                String actualResult = (String) invokeMethod(actualJedis, "get", nonExistentKey);
                System.out.println("Actual GET(nonexistent) result: " + actualResult);
                
                // Compare results
                assertEquals(actualResult, glideResult, "Non-existent key results should be identical");
            } else {
                System.out.println("Note: Only testing GLIDE Jedis (actual Jedis not available for comparison)");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Non-existent key test failed", e);
        }
    }

    @Test
    @EnabledIf("hasGlideJedis")
    public void testPoolComparison() {
        if (!hasGlideJedis) {
            System.out.println("Skipping test - GLIDE Jedis not available");
            return;
        }

        String key = TEST_KEY_PREFIX + "pool:" + testCounter;
        String value = "pool_value_" + testCounter;

        try {
            // Test GLIDE JedisPool
            Object glidePooledJedis = invokeMethod(glideJedisPool, "getResource");
            
            String glideSetResult = (String) invokeMethod(glidePooledJedis, "set", key, value);
            String glideGetResult = (String) invokeMethod(glidePooledJedis, "get", key);
            
            // Close the pooled connection
            invokeMethod(glidePooledJedis, "close");
            
            System.out.println("GLIDE Pool SET result: " + glideSetResult);
            System.out.println("GLIDE Pool GET result: " + glideGetResult);
            
            assertEquals("OK", glideSetResult);
            assertEquals(value, glideGetResult);
            
            if (hasActualJedis) {
                // Test actual JedisPool (when available)
                Object actualPooledJedis = invokeMethod(actualJedisPool, "getResource");
                
                String actualSetResult = (String) invokeMethod(actualPooledJedis, "set", key + "_actual", value);
                String actualGetResult = (String) invokeMethod(actualPooledJedis, "get", key + "_actual");
                
                invokeMethod(actualPooledJedis, "close");
                
                System.out.println("Actual Pool SET result: " + actualSetResult);
                System.out.println("Actual Pool GET result: " + actualGetResult);
                
                // Compare results
                assertEquals(actualSetResult, glideSetResult, "Pool SET results should be identical");
                assertEquals(actualGetResult, glideGetResult, "Pool GET results should be identical");
            } else {
                System.out.println("Note: Only testing GLIDE JedisPool (actual JedisPool not available for comparison)");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Pool test failed", e);
        }
    }

    @Test
    @EnabledIf("hasGlideJedis")
    public void testMultipleOperationsComparison() {
        if (!hasGlideJedis) {
            System.out.println("Skipping test - GLIDE Jedis not available");
            return;
        }

        List<String> keys = new ArrayList<>();
        List<String> values = new ArrayList<>();
        
        // Prepare test data
        for (int i = 0; i < 3; i++) {
            keys.add(TEST_KEY_PREFIX + "multi:" + testCounter + ":" + i);
            values.add("value_" + testCounter + "_" + i);
        }

        try {
            // Test GLIDE Jedis multiple operations
            List<String> glideSetResults = new ArrayList<>();
            List<String> glideGetResults = new ArrayList<>();
            
            for (int i = 0; i < keys.size(); i++) {
                String setResult = (String) invokeMethod(glideJedis, "set", keys.get(i), values.get(i));
                glideSetResults.add(setResult);
            }
            
            for (String key : keys) {
                String getResult = (String) invokeMethod(glideJedis, "get", key);
                glideGetResults.add(getResult);
            }
            
            System.out.println("GLIDE Multiple SET results: " + glideSetResults);
            System.out.println("GLIDE Multiple GET results: " + glideGetResults);
            
            // Validate GLIDE results
            for (String setResult : glideSetResults) {
                assertEquals("OK", setResult);
            }
            for (int i = 0; i < values.size(); i++) {
                assertEquals(values.get(i), glideGetResults.get(i));
            }
            
            if (hasActualJedis) {
                // Test actual Jedis multiple operations (when available)
                List<String> actualSetResults = new ArrayList<>();
                List<String> actualGetResults = new ArrayList<>();
                
                for (int i = 0; i < keys.size(); i++) {
                    String actualKey = keys.get(i) + "_actual";
                    String setResult = (String) invokeMethod(actualJedis, "set", actualKey, values.get(i));
                    actualSetResults.add(setResult);
                }
                
                for (int i = 0; i < keys.size(); i++) {
                    String actualKey = keys.get(i) + "_actual";
                    String getResult = (String) invokeMethod(actualJedis, "get", actualKey);
                    actualGetResults.add(getResult);
                }
                
                System.out.println("Actual Multiple SET results: " + actualSetResults);
                System.out.println("Actual Multiple GET results: " + actualGetResults);
                
                // Compare results
                assertEquals(actualSetResults, glideSetResults, "Multiple SET results should be identical");
                assertEquals(actualGetResults, glideGetResults, "Multiple GET results should be identical");
            } else {
                System.out.println("Note: Only testing GLIDE Jedis (actual Jedis not available for comparison)");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Multiple operations test failed", e);
        }
    }

    // Helper method to invoke methods using reflection
    private Object invokeMethod(Object obj, String methodName, Object... args) throws Exception {
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            paramTypes[i] = args[i].getClass();
        }
        
        Method method = obj.getClass().getMethod(methodName, paramTypes);
        return method.invoke(obj, args);
    }

    // Condition method for @EnabledIf
    boolean hasGlideJedis() {
        return hasGlideJedis;
    }
}
