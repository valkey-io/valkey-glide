/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;

/**
 * Simple comparison test that validates the GLIDE Jedis compatibility layer
 * by comparing its behavior with expected Jedis behavior patterns.
 * 
 * Since we can't easily load both GLIDE and actual Jedis in the same classpath,
 * this test focuses on validating that the GLIDE compatibility layer behaves
 * according to Jedis specifications and expected patterns.
 */
public class SimpleJedisCompatibilityTest {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String TEST_KEY_PREFIX = "glide_comparison:";
    
    private Jedis glideJedis;
    private JedisPool glideJedisPool;
    private int testCounter = 0;

    @BeforeEach
    public void setUp(TestInfo testInfo) {
        testCounter++;
        System.out.println("\n=== Running comparison test: " + testInfo.getDisplayName() + " ===");
        
        // Initialize GLIDE Jedis compatibility layer
        glideJedis = new Jedis(REDIS_HOST, REDIS_PORT);
        glideJedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);
        
        System.out.println("✓ GLIDE Jedis compatibility layer initialized");
    }

    @AfterEach
    public void tearDown() {
        if (glideJedis != null) {
            glideJedis.close();
        }
        if (glideJedisPool != null) {
            glideJedisPool.close();
        }
    }

    @Test
    public void testJedisApiCompatibility() {
        // Test that our GLIDE Jedis compatibility layer provides the same API
        // and behavior patterns as expected from standard Jedis
        
        String key = TEST_KEY_PREFIX + "api_test:" + testCounter;
        String value = "test_value_" + testCounter;

        // Test SET operation - should return "OK"
        String setResult = glideJedis.set(key, value);
        assertEquals("OK", setResult, "SET should return 'OK' like standard Jedis");
        
        // Test GET operation - should return the exact value
        String getResult = glideJedis.get(key);
        assertEquals(value, getResult, "GET should return exact value like standard Jedis");
        
        // Test GET non-existent key - should return null
        String nonExistentResult = glideJedis.get(TEST_KEY_PREFIX + "nonexistent:" + testCounter);
        assertNull(nonExistentResult, "GET non-existent key should return null like standard Jedis");
        
        System.out.println("✓ API compatibility validated");
    }

    @Test
    public void testJedisPingBehavior() {
        // Test PING behavior matches standard Jedis expectations
        
        // Basic PING should return "PONG"
        String pingResult = glideJedis.ping();
        assertEquals("PONG", pingResult, "PING should return 'PONG' like standard Jedis");
        
        // PING with message should echo the message
        String message = "hello_glide_" + testCounter;
        String pingMessageResult = glideJedis.ping(message);
        assertEquals(message, pingMessageResult, "PING with message should echo message like standard Jedis");
        
        System.out.println("✓ PING behavior compatibility validated");
    }

    @Test
    public void testJedisPoolBehavior() {
        // Test that JedisPool behaves like standard Jedis pool
        
        String key = TEST_KEY_PREFIX + "pool_test:" + testCounter;
        String value = "pool_value_" + testCounter;

        // Test try-with-resources pattern (standard Jedis pattern)
        try (Jedis pooledJedis = glideJedisPool.getResource()) {
            assertNotNull(pooledJedis, "Pool should provide non-null Jedis instance");
            
            String setResult = pooledJedis.set(key, value);
            assertEquals("OK", setResult, "Pooled Jedis SET should work like standard Jedis");
            
            String getResult = pooledJedis.get(key);
            assertEquals(value, getResult, "Pooled Jedis GET should work like standard Jedis");
        }
        // Resource should be automatically returned to pool here
        
        // Test that we can get another resource from the pool
        try (Jedis anotherPooledJedis = glideJedisPool.getResource()) {
            assertNotNull(anotherPooledJedis, "Pool should provide another non-null Jedis instance");
            
            // Should be able to access the same data
            String retrievedValue = anotherPooledJedis.get(key);
            assertEquals(value, retrievedValue, "Different pooled connections should access same data");
        }
        
        System.out.println("✓ JedisPool behavior compatibility validated");
    }

    @Test
    public void testJedisConnectionManagement() {
        // Test connection management behaves like standard Jedis
        
        // Test that we can create multiple connections
        Jedis jedis1 = new Jedis(REDIS_HOST, REDIS_PORT);
        Jedis jedis2 = new Jedis(REDIS_HOST, REDIS_PORT);
        
        try {
            String key1 = TEST_KEY_PREFIX + "conn1:" + testCounter;
            String key2 = TEST_KEY_PREFIX + "conn2:" + testCounter;
            String value1 = "value1_" + testCounter;
            String value2 = "value2_" + testCounter;
            
            // Both connections should work independently
            jedis1.set(key1, value1);
            jedis2.set(key2, value2);
            
            // Each connection should be able to read data set by the other
            assertEquals(value1, jedis1.get(key1));
            assertEquals(value2, jedis1.get(key2));
            assertEquals(value1, jedis2.get(key1));
            assertEquals(value2, jedis2.get(key2));
            
        } finally {
            jedis1.close();
            jedis2.close();
        }
        
        System.out.println("✓ Connection management compatibility validated");
    }

    @Test
    public void testJedisDataTypeHandling() {
        // Test that data types are handled like standard Jedis
        
        String stringKey = TEST_KEY_PREFIX + "string:" + testCounter;
        String stringValue = "simple_string_" + testCounter;
        
        // Test string operations
        glideJedis.set(stringKey, stringValue);
        assertEquals(stringValue, glideJedis.get(stringKey));
        
        // Test with special characters
        String specialKey = TEST_KEY_PREFIX + "special:" + testCounter;
        String specialValue = "value with spaces and symbols: !@#$%^&*()_+-={}[]|\\:;\"'<>?,./";
        
        glideJedis.set(specialKey, specialValue);
        assertEquals(specialValue, glideJedis.get(specialKey));
        
        // Test with empty string
        String emptyKey = TEST_KEY_PREFIX + "empty:" + testCounter;
        String emptyValue = "";
        
        glideJedis.set(emptyKey, emptyValue);
        assertEquals(emptyValue, glideJedis.get(emptyKey));
        
        System.out.println("✓ Data type handling compatibility validated");
    }

    @Test
    public void testJedisUsagePatterns() {
        // Test common Jedis usage patterns to ensure compatibility
        
        String sessionKey = TEST_KEY_PREFIX + "session:" + testCounter;
        String userId = "user_" + testCounter;
        String sessionData = "{\"userId\":\"" + userId + "\",\"loginTime\":\"" + System.currentTimeMillis() + "\"}";
        
        // Pattern 1: Simple key-value storage (like session management)
        glideJedis.set(sessionKey, sessionData);
        String retrievedSession = glideJedis.get(sessionKey);
        assertEquals(sessionData, retrievedSession);
        
        // Pattern 2: Configuration storage
        String configKey = TEST_KEY_PREFIX + "config:timeout:" + testCounter;
        String timeoutValue = "30";
        glideJedis.set(configKey, timeoutValue);
        String retrievedTimeout = glideJedis.get(configKey);
        assertEquals(timeoutValue, retrievedTimeout);
        
        // Pattern 3: Caching pattern with pool
        String cacheKey = TEST_KEY_PREFIX + "cache:user:" + userId;
        String userData = "{\"name\":\"John Doe\",\"email\":\"john@example.com\"}";
        
        try (Jedis pooledJedis = glideJedisPool.getResource()) {
            pooledJedis.set(cacheKey, userData);
        }
        
        // Retrieve from cache using different connection
        try (Jedis anotherPooledJedis = glideJedisPool.getResource()) {
            String cachedData = anotherPooledJedis.get(cacheKey);
            assertEquals(userData, cachedData);
        }
        
        System.out.println("✓ Common usage patterns compatibility validated");
    }

    @Test
    public void testBasicJedisUsagePattern() {
        // This test demonstrates the exact usage pattern an end user would follow
        // when migrating from Jedis to GLIDE with the compatibility layer
        
        // Direct connection usage (like original Jedis)
        Jedis directJedis = new Jedis("localhost", 6379);
        directJedis.set("user:1:name", "John Doe");
        String userName = directJedis.get("user:1:name");
        assertEquals("John Doe", userName);
        directJedis.close();
        
        // Pool usage (like original Jedis)
        JedisPool pool = new JedisPool("localhost", 6379);
        try (Jedis pooledJedis = pool.getResource()) {
            pooledJedis.set("user:2:name", "Jane Smith");
            String userName2 = pooledJedis.get("user:2:name");
            assertEquals("Jane Smith", userName2);
        }
        pool.close();
        
        System.out.println("✓ Basic usage patterns validated - ready for production migration!");
    }
}
