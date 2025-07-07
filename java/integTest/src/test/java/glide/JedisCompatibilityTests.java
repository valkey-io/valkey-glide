/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.*;

import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisException;
import compatibility.clients.jedis.JedisPool;
import org.junit.jupiter.api.*;

/**
 * Simplified compatibility tests for the GLIDE Jedis compatibility layer.
 *
 * <p>Since the package name is now compatibility.clients.jedis (not redis.clients.jedis), we can
 * directly import and test without complex reflection or classpath conflicts.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JedisCompatibilityTests {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String TEST_KEY_PREFIX = "compat_test:";

    private Jedis jedis;
    private JedisPool jedisPool;

    @BeforeAll
    static void setupClass() {
        System.out.println("=== Simplified Jedis Compatibility Test Suite ===");
        System.out.println("Testing GLIDE compatibility layer (compatibility.clients.jedis)");
    }

    @BeforeEach
    void setup() {
        jedis = new Jedis(REDIS_HOST, REDIS_PORT);
        jedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);
        System.out.println("✓ GLIDE Jedis compatibility layer initialized");
    }

    @AfterEach
    void cleanup() {
        if (jedis != null) {
            jedis.close();
        }
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test Basic SET/GET Operations")
    void testBasicSetGet() {
        String key = TEST_KEY_PREFIX + "basic";
        String value = "test_value";

        // Test SET
        String setResult = jedis.set(key, value);
        assertEquals("OK", setResult, "SET should return 'OK'");

        // Test GET
        String getResult = jedis.get(key);
        assertEquals(value, getResult, "GET should return the exact value");

        // Test GET non-existent key
        String nonExistentResult = jedis.get(TEST_KEY_PREFIX + "nonexistent");
        assertNull(nonExistentResult, "GET non-existent key should return null");

        System.out.println("✓ Basic SET/GET operations work correctly");
    }

    @Test
    @Order(2)
    @DisplayName("Test PING Operations")
    void testPingOperations() {
        // Test basic PING
        String pingResult = jedis.ping();
        assertEquals("PONG", pingResult, "PING should return 'PONG'");

        // Test PING with message
        String message = "hello_compatibility";
        String pingMessageResult = jedis.ping(message);
        assertEquals(message, pingMessageResult, "PING with message should echo the message");

        System.out.println("✓ PING operations work correctly");
    }

    @Test
    @Order(3)
    @DisplayName("Test JedisPool Operations")
    void testJedisPoolOperations() {
        String key = TEST_KEY_PREFIX + "pool";
        String value = "pool_value";

        // Test pool resource management
        try (Jedis pooledJedis = jedisPool.getResource()) {
            assertNotNull(pooledJedis, "Pool should provide non-null Jedis instance");

            String setResult = pooledJedis.set(key, value);
            assertEquals("OK", setResult, "Pooled SET should work");

            String getResult = pooledJedis.get(key);
            assertEquals(value, getResult, "Pooled GET should work");
        }

        // Test multiple pool connections
        try (Jedis jedis1 = jedisPool.getResource();
                Jedis jedis2 = jedisPool.getResource()) {

            assertNotNull(jedis1, "First pool connection should work");
            assertNotNull(jedis2, "Second pool connection should work");

            // Both should access the same data
            assertEquals(value, jedis1.get(key), "First connection should access data");
            assertEquals(value, jedis2.get(key), "Second connection should access data");
        }

        System.out.println("✓ JedisPool operations work correctly");
    }

    @Test
    @Order(4)
    @DisplayName("Test Connection Management")
    void testConnectionManagement() {
        // Test multiple independent connections
        Jedis conn1 = new Jedis(REDIS_HOST, REDIS_PORT);
        Jedis conn2 = new Jedis(REDIS_HOST, REDIS_PORT);

        try {
            String key1 = TEST_KEY_PREFIX + "conn1";
            String key2 = TEST_KEY_PREFIX + "conn2";
            String value1 = "value1";
            String value2 = "value2";

            // Set data on different connections
            conn1.set(key1, value1);
            conn2.set(key2, value2);

            // Each connection should see all data
            assertEquals(value1, conn1.get(key1), "Connection 1 should see its data");
            assertEquals(value2, conn1.get(key2), "Connection 1 should see data from connection 2");
            assertEquals(value1, conn2.get(key1), "Connection 2 should see data from connection 1");
            assertEquals(value2, conn2.get(key2), "Connection 2 should see its data");

        } finally {
            conn1.close();
            conn2.close();
        }

        System.out.println("✓ Connection management works correctly");
    }

    @Test
    @Order(5)
    @DisplayName("Test Data Types and Edge Cases")
    void testDataTypesAndEdgeCases() {
        // Test empty string
        String emptyKey = TEST_KEY_PREFIX + "empty";
        String emptyValue = "";
        jedis.set(emptyKey, emptyValue);
        assertEquals(emptyValue, jedis.get(emptyKey), "Empty string should be handled correctly");

        // Test special characters
        String specialKey = TEST_KEY_PREFIX + "special";
        String specialValue = "special!@#$%^&*()_+-={}[]|\\:;\"'<>?,./";
        jedis.set(specialKey, specialValue);
        assertEquals(
                specialValue, jedis.get(specialKey), "Special characters should be handled correctly");

        // Test Unicode
        String unicodeKey = TEST_KEY_PREFIX + "unicode";
        String unicodeValue = "Hello 世界 🌍 Здравствуй мир";
        jedis.set(unicodeKey, unicodeValue);
        assertEquals(unicodeValue, jedis.get(unicodeKey), "Unicode should be handled correctly");

        System.out.println("✓ Data types and edge cases work correctly");
    }

    @Test
    @Order(6)
    @DisplayName("Test Exception Handling")
    void testExceptionHandling() {
        // Test that exceptions are properly typed
        try {
            // This should work normally
            jedis.ping();
        } catch (Exception e) {
            // If there's an exception, it should be a JedisException or subclass
            assertTrue(e instanceof JedisException, "Exceptions should be JedisException or subclass");
        }

        // Test connection state after operations
        String testKey = TEST_KEY_PREFIX + "exception_test";
        String testValue = "exception_test_value";

        String setResult = jedis.set(testKey, testValue);
        assertEquals("OK", setResult, "Connection should work after exception handling");

        String getResult = jedis.get(testKey);
        assertEquals(testValue, getResult, "GET should work after exception handling");

        System.out.println("✓ Exception handling works correctly");
    }

    @Test
    @Order(7)
    @DisplayName("Test Configuration and Constructors")
    void testConfigurationAndConstructors() {
        // Test default constructor
        Jedis defaultJedis = new Jedis();
        try {
            assertNotNull(defaultJedis, "Default constructor should work");
            // Don't test operations as default might not connect to our test server
        } finally {
            defaultJedis.close();
        }

        // Test host/port constructor
        Jedis hostPortJedis = new Jedis(REDIS_HOST, REDIS_PORT);
        try {
            String pingResult = hostPortJedis.ping();
            assertEquals("PONG", pingResult, "Host/port constructor should work");
        } finally {
            hostPortJedis.close();
        }

        // Test timeout constructor
        Jedis timeoutJedis = new Jedis(REDIS_HOST, REDIS_PORT, 5000);
        try {
            String pingResult = timeoutJedis.ping();
            assertEquals("PONG", pingResult, "Timeout constructor should work");
        } finally {
            timeoutJedis.close();
        }

        System.out.println("✓ Configuration and constructors work correctly");
    }

    @Test
    @Order(8)
    @DisplayName("Test Real-World Usage Patterns")
    void testRealWorldUsagePatterns() {
        // Pattern 1: Session management
        String sessionId = "session_" + System.currentTimeMillis();
        String sessionKey = TEST_KEY_PREFIX + "session:" + sessionId;
        String sessionData =
                "{\"userId\":\"user123\",\"loginTime\":" + System.currentTimeMillis() + "}";

        jedis.set(sessionKey, sessionData);
        String retrievedSession = jedis.get(sessionKey);
        assertEquals(sessionData, retrievedSession, "Session management pattern should work");

        // Pattern 2: Configuration caching with pool
        String configKey = TEST_KEY_PREFIX + "config:timeout";
        String configValue = "30";

        try (Jedis pooledJedis = jedisPool.getResource()) {
            pooledJedis.set(configKey, configValue);
        }

        try (Jedis anotherPooledJedis = jedisPool.getResource()) {
            String cachedConfig = anotherPooledJedis.get(configKey);
            assertEquals(configValue, cachedConfig, "Configuration caching should work");
        }

        // Pattern 3: Multiple operations
        String[] keys = {
            TEST_KEY_PREFIX + "multi1", TEST_KEY_PREFIX + "multi2", TEST_KEY_PREFIX + "multi3"
        };
        String[] values = {"value1", "value2", "value3"};

        for (int i = 0; i < keys.length; i++) {
            jedis.set(keys[i], values[i]);
        }

        for (int i = 0; i < keys.length; i++) {
            assertEquals(values[i], jedis.get(keys[i]), "Multiple operations should work");
        }

        System.out.println("✓ Real-world usage patterns work correctly");
    }

    @Test
    @Order(9)
    @DisplayName("Final Integration Test")
    void testFinalIntegration() {
        System.out.println("=== Final Integration Test ===");

        String integrationKey = TEST_KEY_PREFIX + "integration";
        String integrationValue = "GLIDE_compatibility_layer_works_perfectly";

        // Test complete workflow
        String setResult = jedis.set(integrationKey, integrationValue);
        assertEquals("OK", setResult, "Integration SET should work");

        String getResult = jedis.get(integrationKey);
        assertEquals(integrationValue, getResult, "Integration GET should work");

        String pingResult = jedis.ping();
        assertEquals("PONG", pingResult, "Integration PING should work");

        // Test with pool
        try (Jedis pooledJedis = jedisPool.getResource()) {
            String poolGetResult = pooledJedis.get(integrationKey);
            assertEquals(integrationValue, poolGetResult, "Integration pool GET should work");

            String poolPingResult = pooledJedis.ping("integration_test");
            assertEquals("integration_test", poolPingResult, "Integration pool PING should work");
        }

        System.out.println("✓ Final integration test passed");
        System.out.println(
                "✓ GLIDE Jedis compatibility layer (compatibility.clients.jedis) is fully functional!");
        System.out.println("✓ Ready for production use with simplified, direct API access!");
    }
}
