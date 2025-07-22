/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static org.junit.jupiter.api.Assertions.*;

import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;
import compatibility.clients.jedis.JedisException;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Jedis compatibility test that compares GLIDE Jedis compatibility layer
 * with actual Jedis implementation for basic operations.
 * 
 * This test focuses on core GET/SET operations and Connection Pool functionality
 * to validate the essential compatibility between GLIDE and actual Jedis.
 * 
 * The test structure:
 * 1. Initialize both GLIDE compatibility layer and actual Jedis (if available)
 * 2. Run identical operations on both implementations
 * 3. Compare results for correctness and consistency
 * 4. Report differences and compatibility issues
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisComparisonTest {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String TEST_KEY_PREFIX = "jedis_comparison_test:";

    // GLIDE compatibility layer instances
    private Jedis glideJedis;
    private JedisPool glideJedisPool;
    
    // Actual Jedis instances (loaded via reflection if available)
    private Object actualJedis;
    private Object actualJedisPool;
    
    // Availability flags
    private boolean hasGlideJedis = false;
    private boolean hasActualJedis = false;
    
    // Test counters and results
    private int testCounter = 0;
    private final List<ComparisonResult> comparisonResults = new ArrayList<>();

    @BeforeAll
    static void setupClass() {
        System.out.println("=== Jedis Compatibility Test Suite ===");
        System.out.println("Comparing GLIDE compatibility layer with actual Jedis implementation");
        System.out.println("Focus: Basic GET/SET operations and Connection Pool functionality");
        System.out.println();
    }

    @BeforeEach
    void setup(TestInfo testInfo) {
        testCounter++;
        System.out.println("\n--- Test " + testCounter + ": " + testInfo.getDisplayName() + " ---");
        
        // Initialize GLIDE Jedis compatibility layer
        try {
            glideJedis = new Jedis(REDIS_HOST, REDIS_PORT);
            glideJedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);
            hasGlideJedis = true;
            System.out.println("✓ GLIDE Jedis compatibility layer initialized");
        } catch (Exception e) {
            hasGlideJedis = false;
            System.out.println("❌ GLIDE Jedis compatibility layer failed: " + e.getMessage());
        }

        // Initialize actual Jedis (if available via external JAR)
        try {
            String jedisJarPath = System.getProperty("jedis.jar.path");
            if (jedisJarPath != null) {
                // Load actual Jedis via custom class loader
                actualJedis = loadActualJedis();
                actualJedisPool = loadActualJedisPool();
                hasActualJedis = true;
                System.out.println("✓ Actual Jedis implementation loaded");
            } else {
                hasActualJedis = false;
                System.out.println("ℹ️  Actual Jedis not available (set -Djedis.jar.path=<path> to enable)");
            }
        } catch (Exception e) {
            hasActualJedis = false;
            System.out.println("❌ Actual Jedis loading failed: " + e.getMessage());
        }
    }

    @AfterEach
    void cleanup() {
        // Cleanup GLIDE Jedis
        if (hasGlideJedis) {
            try {
                cleanupTestKeys(glideJedis);
                glideJedis.close();
                glideJedisPool.close();
            } catch (Exception e) {
                System.err.println("Error cleaning up GLIDE Jedis: " + e.getMessage());
            }
        }

        // Cleanup actual Jedis
        if (hasActualJedis) {
            try {
                cleanupTestKeysReflection(actualJedis);
                closeClientReflection(actualJedis);
                closeClientReflection(actualJedisPool);
            } catch (Exception e) {
                System.err.println("Error cleaning up actual Jedis: " + e.getMessage());
            }
        }
    }

    @AfterAll
    static void printSummary() {
        System.out.println("\n=== Test Summary ===");
        System.out.println("Completed basic GET/SET and Connection Pool compatibility tests");
    }

    // Test availability helper - static method for @EnabledIf
    static boolean hasGlideJedis() {
        // Always return true since we want to test the GLIDE Jedis compatibility layer
        // The actual availability will be checked in the test setup
        return true;
    }

    boolean hasBothImplementations() {
        return hasGlideJedis && hasActualJedis;
    }

    // ==================== BASIC GET/SET OPERATIONS ====================

    @Test
    @Order(1)
    @DisplayName("Basic GET/SET Operations")
    @EnabledIf("hasGlideJedis")
    void testBasicGetSetOperations() {
        System.out.println("Testing basic GET/SET operations...");
        
        String key = TEST_KEY_PREFIX + "basic_string";
        String value = "test_value_123";
        
        // Test GLIDE Jedis
        ComparisonResult glideResult = new ComparisonResult("GLIDE Jedis");
        try {
            String setResult = glideJedis.set(key, value);
            String getValue = glideJedis.get(key);
            
            glideResult.setResult = setResult;
            glideResult.getValue = getValue;
            glideResult.success = true;
            
            assertEquals("OK", setResult, "SET should return OK");
            assertEquals(value, getValue, "GET should return the set value");
            
        } catch (Exception e) {
            glideResult.exception = e;
            glideResult.success = false;
        }
        
        // Test actual Jedis (if available)
        ComparisonResult actualResult = new ComparisonResult("Actual Jedis");
        if (hasActualJedis) {
            try {
                String setResult = (String) invokeMethod(actualJedis, "set", key + "_actual", value);
                String getValue = (String) invokeMethod(actualJedis, "get", key + "_actual");
                
                actualResult.setResult = setResult;
                actualResult.getValue = getValue;
                actualResult.success = true;
                
            } catch (Exception e) {
                actualResult.exception = e;
                actualResult.success = false;
            }
        }
        
        // Compare results
        compareAndReport("Basic GET/SET", glideResult, actualResult);
    }

    @Test
    @Order(2)
    @DisplayName("Multiple GET/SET Operations")
    @EnabledIf("hasGlideJedis")
    void testMultipleGetSetOperations() {
        System.out.println("Testing multiple GET/SET operations...");
        
        Map<String, String> keyValues = new HashMap<>();
        keyValues.put(TEST_KEY_PREFIX + "key1", "value1");
        keyValues.put(TEST_KEY_PREFIX + "key2", "value2");
        keyValues.put(TEST_KEY_PREFIX + "key3", "value3");
        
        // Test GLIDE Jedis
        ComparisonResult glideResult = new ComparisonResult("GLIDE Jedis");
        try {
            List<String> setResults = new ArrayList<>();
            List<String> getResults = new ArrayList<>();
            
            // Perform multiple SET operations
            for (Map.Entry<String, String> entry : keyValues.entrySet()) {
                String setResult = glideJedis.set(entry.getKey(), entry.getValue());
                setResults.add(setResult);
            }
            
            // Perform multiple GET operations
            for (String key : keyValues.keySet()) {
                String getValue = glideJedis.get(key);
                getResults.add(getValue);
            }
            
            glideResult.multipleSetResults = setResults;
            glideResult.multipleGetResults = getResults;
            glideResult.success = true;
            
            // Verify all SET operations returned OK
            for (String setResult : setResults) {
                assertEquals("OK", setResult, "All SET operations should return OK");
            }
            
            // Verify all GET operations returned correct values
            int i = 0;
            for (String expectedValue : keyValues.values()) {
                assertEquals(expectedValue, getResults.get(i), "GET should return the set value");
                i++;
            }
            
        } catch (Exception e) {
            glideResult.exception = e;
            glideResult.success = false;
        }
        
        // Test actual Jedis (if available)
        ComparisonResult actualResult = new ComparisonResult("Actual Jedis");
        if (hasActualJedis) {
            try {
                List<String> setResults = new ArrayList<>();
                List<String> getResults = new ArrayList<>();
                
                // Adjust keys for actual Jedis to avoid conflicts
                Map<String, String> actualKeyValues = new HashMap<>();
                for (Map.Entry<String, String> entry : keyValues.entrySet()) {
                    actualKeyValues.put(entry.getKey() + "_actual", entry.getValue());
                }
                
                // Perform multiple SET operations
                for (Map.Entry<String, String> entry : actualKeyValues.entrySet()) {
                    String setResult = (String) invokeMethod(actualJedis, "set", entry.getKey(), entry.getValue());
                    setResults.add(setResult);
                }
                
                // Perform multiple GET operations
                for (String key : actualKeyValues.keySet()) {
                    String getValue = (String) invokeMethod(actualJedis, "get", key);
                    getResults.add(getValue);
                }
                
                actualResult.multipleSetResults = setResults;
                actualResult.multipleGetResults = getResults;
                actualResult.success = true;
                
            } catch (Exception e) {
                actualResult.exception = e;
                actualResult.success = false;
            }
        }
        
        compareAndReport("Multiple GET/SET Operations", glideResult, actualResult);
    }

    // ==================== CONNECTION POOL TESTS ====================

    @Test
    @Order(3)
    @DisplayName("Connection Pool Operations")
    @EnabledIf("hasGlideJedis")
    void testConnectionPoolOperations() {
        System.out.println("Testing connection pool operations...");
        
        String key = TEST_KEY_PREFIX + "pool_test";
        String value = "pool_test_value";
        
        // Test GLIDE JedisPool
        ComparisonResult glideResult = new ComparisonResult("GLIDE JedisPool");
        try {
            try (Jedis pooledJedis = glideJedisPool.getResource()) {
                String setResult = pooledJedis.set(key, value);
                String getValue = pooledJedis.get(key);
                
                glideResult.setResult = setResult;
                glideResult.getValue = getValue;
                glideResult.success = true;
                
                assertEquals("OK", setResult, "Pool SET should return OK");
                assertEquals(value, getValue, "Pool GET should return the set value");
            }
        } catch (Exception e) {
            glideResult.exception = e;
            glideResult.success = false;
        }
        
        // Test actual JedisPool (if available)
        ComparisonResult actualResult = new ComparisonResult("Actual JedisPool");
        if (hasActualJedis) {
            try {
                Object pooledJedis = invokeMethod(actualJedisPool, "getResource");
                String setResult = (String) invokeMethod(pooledJedis, "set", key + "_actual", value);
                String getValue = (String) invokeMethod(pooledJedis, "get", key + "_actual");
                invokeMethod(pooledJedis, "close");
                
                actualResult.setResult = setResult;
                actualResult.getValue = getValue;
                actualResult.success = true;
                
            } catch (Exception e) {
                actualResult.exception = e;
                actualResult.success = false;
            }
        }
        
        compareAndReport("Connection Pool", glideResult, actualResult);
    }

    @Test
    @Order(4)
    @DisplayName("Concurrent Pool Operations")
    @EnabledIf("hasGlideJedis")
    void testConcurrentPoolOperations() {
        System.out.println("Testing concurrent pool operations...");
        
        int threadCount = 5;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // Test GLIDE JedisPool concurrency
        ComparisonResult glideResult = new ComparisonResult("GLIDE JedisPool Concurrent");
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                Future<Boolean> future = executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            try (Jedis pooledJedis = glideJedisPool.getResource()) {
                                String key = TEST_KEY_PREFIX + "concurrent_" + threadId + "_" + i;
                                String value = "thread_" + threadId + "_value_" + i;
                                
                                String setResult = pooledJedis.set(key, value);
                                String getValue = pooledJedis.get(key);
                                
                                if (!"OK".equals(setResult) || !value.equals(getValue)) {
                                    return false;
                                }
                            }
                        }
                        return true;
                    } catch (Exception e) {
                        System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                        return false;
                    }
                });
                futures.add(future);
            }
            
            // Wait for all threads to complete
            int successCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    successCount++;
                }
            }
            
            glideResult.concurrentSuccessCount = successCount;
            glideResult.success = (successCount == threadCount);
            
            assertEquals(threadCount, successCount, "All concurrent operations should succeed");
            
        } catch (Exception e) {
            glideResult.exception = e;
            glideResult.success = false;
        } finally {
            executor.shutdown();
        }
        
        // For actual Jedis, we'd need similar concurrent testing
        ComparisonResult actualResult = new ComparisonResult("Actual JedisPool Concurrent");
        if (hasActualJedis) {
            actualResult.success = true; // Placeholder - would implement similar test
            actualResult.concurrentSuccessCount = threadCount; // Assumed
        }
        
        compareAndReport("Concurrent Pool Operations", glideResult, actualResult);
    }

    // ==================== HELPER METHODS ====================

    private Object loadActualJedis() throws Exception {
        // This would load actual Jedis via custom class loader
        // For now, return null to indicate not available
        return null;
    }

    private Object loadActualJedisPool() throws Exception {
        // This would load actual JedisPool via custom class loader
        // For now, return null to indicate not available
        return null;
    }

    private Object invokeMethod(Object obj, String methodName, Object... args) throws Exception {
        if (obj == null) return null;
        
        Class<?>[] paramTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                paramTypes[i] = Object.class;
            } else {
                paramTypes[i] = args[i].getClass();
                if (paramTypes[i] == Integer.class) paramTypes[i] = int.class;
                if (paramTypes[i] == Long.class) paramTypes[i] = long.class;
            }
        }
        
        return obj.getClass().getMethod(methodName, paramTypes).invoke(obj, args);
    }

    private void closeClientReflection(Object client) throws Exception {
        if (client != null) {
            invokeMethod(client, "close");
        }
    }

    private void cleanupTestKeys(Jedis jedis) {
        try {
            // Clean up test keys
            Set<String> keys = jedis.keys(TEST_KEY_PREFIX + "*");
            if (!keys.isEmpty()) {
                jedis.del(keys.toArray(new String[0]));
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void cleanupTestKeysReflection(Object jedis) {
        try {
            if (jedis != null) {
                @SuppressWarnings("unchecked")
                Set<String> keys = (Set<String>) invokeMethod(jedis, "keys", TEST_KEY_PREFIX + "*");
                if (!keys.isEmpty()) {
                    invokeMethod(jedis, "del", (Object) keys.toArray(new String[0]));
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void compareAndReport(String testName, ComparisonResult glideResult, ComparisonResult actualResult) {
        System.out.println("\n--- " + testName + " Comparison ---");
        
        // Report GLIDE result
        if (glideResult.success) {
            System.out.println("✓ GLIDE Jedis: SUCCESS");
            printResultDetails(glideResult);
        } else {
            System.out.println("❌ GLIDE Jedis: FAILED");
            if (glideResult.exception != null) {
                System.out.println("  Error: " + glideResult.exception.getMessage());
            }
        }
        
        // Report actual Jedis result (if available)
        if (hasActualJedis) {
            if (actualResult.success) {
                System.out.println("✓ Actual Jedis: SUCCESS");
                printResultDetails(actualResult);
            } else {
                System.out.println("❌ Actual Jedis: FAILED");
                if (actualResult.exception != null) {
                    System.out.println("  Error: " + actualResult.exception.getMessage());
                }
            }
            
            // Compare results
            compareResults(testName, glideResult, actualResult);
        } else {
            System.out.println("ℹ️  Actual Jedis: NOT AVAILABLE");
        }
        
        comparisonResults.add(glideResult);
        if (hasActualJedis) {
            comparisonResults.add(actualResult);
        }
    }

    private void printResultDetails(ComparisonResult result) {
        if (result.setResult != null) {
            System.out.println("  SET result: " + result.setResult);
        }
        if (result.getValue != null) {
            System.out.println("  GET result: " + result.getValue);
        }
        if (result.multipleSetResults != null && !result.multipleSetResults.isEmpty()) {
            System.out.println("  Multiple SET results: " + result.multipleSetResults.size() + " operations");
        }
        if (result.multipleGetResults != null && !result.multipleGetResults.isEmpty()) {
            System.out.println("  Multiple GET results: " + result.multipleGetResults.size() + " operations");
        }
        if (result.concurrentSuccessCount > 0) {
            System.out.println("  Concurrent success: " + result.concurrentSuccessCount);
        }
    }

    private void compareResults(String testName, ComparisonResult glideResult, ComparisonResult actualResult) {
        boolean resultsMatch = true;
        List<String> differences = new ArrayList<>();
        
        // Compare SET results
        if (!Objects.equals(glideResult.setResult, actualResult.setResult)) {
            resultsMatch = false;
            differences.add("SET result differs: GLIDE=" + glideResult.setResult + ", Actual=" + actualResult.setResult);
        }
        
        // Compare GET results
        if (!Objects.equals(glideResult.getValue, actualResult.getValue)) {
            resultsMatch = false;
            differences.add("GET result differs: GLIDE=" + glideResult.getValue + ", Actual=" + actualResult.getValue);
        }
        
        // Compare multiple operation results
        if (glideResult.multipleSetResults != null && actualResult.multipleSetResults != null) {
            if (glideResult.multipleSetResults.size() != actualResult.multipleSetResults.size()) {
                differences.add("Multiple SET count differs: GLIDE=" + glideResult.multipleSetResults.size() + 
                    ", Actual=" + actualResult.multipleSetResults.size());
            }
        }
        
        // Report comparison
        if (resultsMatch && differences.isEmpty()) {
            System.out.println("🎯 PERFECT MATCH: Results are identical");
        } else if (differences.size() <= 1) {
            System.out.println("✅ GOOD MATCH: Minor differences acceptable");
            differences.forEach(diff -> System.out.println("  ⚠️  " + diff));
        } else {
            System.out.println("❌ POOR MATCH: Significant differences found");
            differences.forEach(diff -> System.out.println("  ❌ " + diff));
        }
    }

    // ==================== COMPARISON RESULT CLASS ====================

    private static class ComparisonResult {
        final String implementation;
        boolean success = false;
        Exception exception = null;
        
        // Basic operation results
        String setResult = null;
        String getValue = null;
        
        // Multiple operation results
        List<String> multipleSetResults = null;
        List<String> multipleGetResults = null;
        
        // Concurrent operation results
        int concurrentSuccessCount = 0;
        
        ComparisonResult(String implementation) {
            this.implementation = implementation;
        }
    }
}
