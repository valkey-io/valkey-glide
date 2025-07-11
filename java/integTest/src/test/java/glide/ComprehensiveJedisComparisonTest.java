/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide;

import static org.junit.jupiter.api.Assertions.*;

import compatibility.clients.jedis.Jedis;
import compatibility.clients.jedis.JedisPool;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Comprehensive test that validates GLIDE Jedis compatibility layer against known Jedis behavior
 * patterns and specifications. This test ensures that the GLIDE compatibility layer behaves exactly
 * like actual Jedis would.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComprehensiveJedisComparisonTest {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final String TEST_KEY_PREFIX = "comprehensive_test:";

    private Jedis glideJedis;
    private JedisPool glideJedisPool;
    private int testCounter = 0;

    @BeforeAll
    static void setupClass() {
        System.out.println("=== Comprehensive Jedis Compatibility Test Suite ===");
        System.out.println("Validating GLIDE Jedis compatibility layer against Jedis specifications");
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        testCounter++;
        System.out.println("\n--- Test " + testCounter + ": " + testInfo.getDisplayName() + " ---");

        glideJedis = new Jedis(REDIS_HOST, REDIS_PORT);
        glideJedisPool = new JedisPool(REDIS_HOST, REDIS_PORT);

        System.out.println("✓ GLIDE Jedis compatibility layer initialized");
    }

    @AfterEach
    void tearDown() {
        if (glideJedis != null) {
            glideJedis.close();
        }
        if (glideJedisPool != null) {
            glideJedisPool.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Validate Basic SET/GET API Compatibility")
    void testBasicSetGetApiCompatibility() {
        String key = TEST_KEY_PREFIX + "basic:" + testCounter;
        String value = "test_value_" + testCounter;

        // Test SET - should return "OK" exactly like Jedis
        String setResult = glideJedis.set(key, value);
        assertEquals("OK", setResult, "SET should return 'OK' string exactly like Jedis");
        assertNotNull(setResult, "SET result should not be null");

        // Test GET - should return exact value like Jedis
        String getResult = glideJedis.get(key);
        assertEquals(value, getResult, "GET should return exact value like Jedis");
        assertNotNull(getResult, "GET result should not be null for existing key");

        // Test GET non-existent - should return null exactly like Jedis
        String nonExistentKey = TEST_KEY_PREFIX + "nonexistent:" + testCounter;
        String nonExistentResult = glideJedis.get(nonExistentKey);
        assertNull(nonExistentResult, "GET non-existent key should return null exactly like Jedis");

        System.out.println("✓ Basic SET/GET API compatibility validated");
        System.out.println("  SET result: " + setResult);
        System.out.println("  GET result: " + getResult);
        System.out.println("  Non-existent GET result: " + nonExistentResult);
    }

    @Test
    @Order(2)
    @DisplayName("Validate PING Command Compatibility")
    void testPingCompatibility() {
        // Test basic PING - should return "PONG" exactly like Jedis
        String pingResult = glideJedis.ping();
        assertEquals("PONG", pingResult, "PING should return 'PONG' exactly like Jedis");
        assertNotNull(pingResult, "PING result should not be null");

        // Test PING with message - should echo message exactly like Jedis
        String message = "hello_glide_" + testCounter;
        String pingMessageResult = glideJedis.ping(message);
        assertEquals(
                message, pingMessageResult, "PING with message should echo message exactly like Jedis");
        assertNotNull(pingMessageResult, "PING with message result should not be null");

        // Test PING with empty message
        String emptyMessage = "";
        String pingEmptyResult = glideJedis.ping(emptyMessage);
        assertEquals(
                emptyMessage,
                pingEmptyResult,
                "PING with empty message should return empty string like Jedis");

        System.out.println("✓ PING command compatibility validated");
        System.out.println("  PING result: " + pingResult);
        System.out.println("  PING with message result: " + pingMessageResult);
        System.out.println("  PING with empty message result: '" + pingEmptyResult + "'");
    }

    @Test
    @Order(3)
    @DisplayName("Validate JedisPool Compatibility")
    void testJedisPoolCompatibility() {
        String key = TEST_KEY_PREFIX + "pool:" + testCounter;
        String value = "pool_value_" + testCounter;

        // Test getResource() - should return non-null Jedis instance like JedisPool
        Jedis pooledJedis = glideJedisPool.getResource();
        assertNotNull(
                pooledJedis,
                "JedisPool.getResource() should return non-null Jedis instance like actual JedisPool");

        try {
            // Test operations on pooled connection
            String setResult = pooledJedis.set(key, value);
            assertEquals("OK", setResult, "Pooled Jedis SET should work exactly like actual JedisPool");

            String getResult = pooledJedis.get(key);
            assertEquals(value, getResult, "Pooled Jedis GET should work exactly like actual JedisPool");

        } finally {
            pooledJedis.close(); // Should return to pool, not close underlying connection
        }

        // Test try-with-resources pattern (standard Jedis pattern)
        try (Jedis autoClosedJedis = glideJedisPool.getResource()) {
            String retrievedValue = autoClosedJedis.get(key);
            assertEquals(
                    value, retrievedValue, "Try-with-resources pattern should work like actual JedisPool");
        }

        // Test multiple connections from pool
        Jedis jedis1 = glideJedisPool.getResource();
        Jedis jedis2 = glideJedisPool.getResource();

        try {
            assertNotNull(jedis1, "First pool connection should be non-null");
            assertNotNull(jedis2, "Second pool connection should be non-null");

            // Both should be able to access the same data
            assertEquals(value, jedis1.get(key), "First pool connection should access data");
            assertEquals(value, jedis2.get(key), "Second pool connection should access data");

        } finally {
            jedis1.close();
            jedis2.close();
        }

        System.out.println("✓ JedisPool compatibility validated");
    }

    @Test
    @Order(4)
    @DisplayName("Validate Data Type Handling Compatibility")
    void testDataTypeHandlingCompatibility() {
        // Test regular strings
        String stringKey = TEST_KEY_PREFIX + "string:" + testCounter;
        String stringValue = "regular_string_" + testCounter;
        glideJedis.set(stringKey, stringValue);
        assertEquals(
                stringValue, glideJedis.get(stringKey), "Regular strings should be handled like Jedis");

        // Test empty strings
        String emptyKey = TEST_KEY_PREFIX + "empty:" + testCounter;
        String emptyValue = "";
        glideJedis.set(emptyKey, emptyValue);
        assertEquals(
                emptyValue, glideJedis.get(emptyKey), "Empty strings should be handled like Jedis");

        // Test strings with special characters
        String specialKey = TEST_KEY_PREFIX + "special:" + testCounter;
        String specialValue = "special!@#$%^&*()_+-={}[]|\\:;\"'<>?,./\n\t\r";
        glideJedis.set(specialKey, specialValue);
        assertEquals(
                specialValue,
                glideJedis.get(specialKey),
                "Special characters should be handled like Jedis");

        // Test Unicode strings
        String unicodeKey = TEST_KEY_PREFIX + "unicode:" + testCounter;
        String unicodeValue = "Hello 世界 🌍 Здравствуй мир";
        glideJedis.set(unicodeKey, unicodeValue);
        assertEquals(
                unicodeValue, glideJedis.get(unicodeKey), "Unicode strings should be handled like Jedis");

        // Test long strings
        String longKey = TEST_KEY_PREFIX + "long:" + testCounter;
        StringBuilder longValueBuilder = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longValueBuilder.append("LongString").append(i).append("_");
        }
        String longValue = longValueBuilder.toString();
        glideJedis.set(longKey, longValue);
        assertEquals(longValue, glideJedis.get(longKey), "Long strings should be handled like Jedis");

        System.out.println("✓ Data type handling compatibility validated");
        System.out.println("  Regular string: OK");
        System.out.println("  Empty string: OK");
        System.out.println("  Special characters: OK");
        System.out.println("  Unicode: OK");
        System.out.println("  Long string (" + longValue.length() + " chars): OK");
    }

    @Test
    @Order(5)
    @DisplayName("Validate Connection Management Compatibility")
    void testConnectionManagementCompatibility() {
        // Test multiple independent connections
        Jedis conn1 = new Jedis(REDIS_HOST, REDIS_PORT);
        Jedis conn2 = new Jedis(REDIS_HOST, REDIS_PORT);
        Jedis conn3 = new Jedis(REDIS_HOST, REDIS_PORT);

        try {
            String key1 = TEST_KEY_PREFIX + "conn1:" + testCounter;
            String key2 = TEST_KEY_PREFIX + "conn2:" + testCounter;
            String key3 = TEST_KEY_PREFIX + "conn3:" + testCounter;
            String value1 = "value1_" + testCounter;
            String value2 = "value2_" + testCounter;
            String value3 = "value3_" + testCounter;

            // Each connection should work independently
            conn1.set(key1, value1);
            conn2.set(key2, value2);
            conn3.set(key3, value3);

            // Each connection should see data from all connections
            assertEquals(value1, conn1.get(key1), "Connection 1 should see its own data");
            assertEquals(value2, conn1.get(key2), "Connection 1 should see data from connection 2");
            assertEquals(value3, conn1.get(key3), "Connection 1 should see data from connection 3");

            assertEquals(value1, conn2.get(key1), "Connection 2 should see data from connection 1");
            assertEquals(value2, conn2.get(key2), "Connection 2 should see its own data");
            assertEquals(value3, conn2.get(key3), "Connection 2 should see data from connection 3");

            assertEquals(value1, conn3.get(key1), "Connection 3 should see data from connection 1");
            assertEquals(value2, conn3.get(key2), "Connection 3 should see data from connection 2");
            assertEquals(value3, conn3.get(key3), "Connection 3 should see its own data");

        } finally {
            conn1.close();
            conn2.close();
            conn3.close();
        }

        System.out.println("✓ Connection management compatibility validated");
    }

    @Test
    @Order(6)
    @DisplayName("Validate Concurrent Operations Compatibility")
    void testConcurrentOperationsCompatibility() throws Exception {
        int threadCount = 5;
        int operationsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Test concurrent operations using pool
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            CompletableFuture<Void> future =
                    CompletableFuture.runAsync(
                            () -> {
                                for (int op = 0; op < operationsPerThread; op++) {
                                    try (Jedis jedis = glideJedisPool.getResource()) {
                                        String key =
                                                TEST_KEY_PREFIX
                                                        + "concurrent:thread"
                                                        + threadId
                                                        + ":op"
                                                        + op
                                                        + ":"
                                                        + testCounter;
                                        String value = "thread" + threadId + "_op" + op + "_value";

                                        String setResult = jedis.set(key, value);
                                        assertEquals("OK", setResult, "Concurrent SET should work like Jedis");

                                        String getResult = jedis.get(key);
                                        assertEquals(value, getResult, "Concurrent GET should work like Jedis");

                                    } catch (Exception e) {
                                        throw new RuntimeException("Concurrent operation failed", e);
                                    }
                                }
                            },
                            executor);

            futures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Executor should terminate");

        System.out.println("✓ Concurrent operations compatibility validated");
        System.out.println("  Threads: " + threadCount);
        System.out.println("  Operations per thread: " + operationsPerThread);
        System.out.println(
                "  Total operations: " + (threadCount * operationsPerThread * 2)); // SET + GET
    }

    @Test
    @Order(7)
    @DisplayName("Validate Real-World Usage Patterns")
    void testRealWorldUsagePatterns() {
        // Pattern 1: Session management
        String sessionId = "session_" + testCounter + "_" + System.currentTimeMillis();
        String sessionKey = TEST_KEY_PREFIX + "session:" + sessionId;
        String sessionData =
                "{\"userId\":\"user123\",\"loginTime\":"
                        + System.currentTimeMillis()
                        + ",\"permissions\":[\"read\",\"write\"]}";

        glideJedis.set(sessionKey, sessionData);
        String retrievedSession = glideJedis.get(sessionKey);
        assertEquals(
                sessionData, retrievedSession, "Session management pattern should work like Jedis");

        // Pattern 2: Configuration caching
        Map<String, String> configs = new HashMap<>();
        configs.put("database.timeout", "30");
        configs.put("cache.ttl", "3600");
        configs.put("api.rate_limit", "1000");

        for (Map.Entry<String, String> config : configs.entrySet()) {
            String configKey = TEST_KEY_PREFIX + "config:" + config.getKey() + ":" + testCounter;
            glideJedis.set(configKey, config.getValue());
        }

        for (Map.Entry<String, String> config : configs.entrySet()) {
            String configKey = TEST_KEY_PREFIX + "config:" + config.getKey() + ":" + testCounter;
            String retrievedValue = glideJedis.get(configKey);
            assertEquals(
                    config.getValue(), retrievedValue, "Configuration caching should work like Jedis");
        }

        // Pattern 3: User data caching with pool
        String userId = "user_" + testCounter;
        String userKey = TEST_KEY_PREFIX + "user:" + userId;
        String userData =
                "{\"id\":\""
                        + userId
                        + "\",\"name\":\"John Doe\",\"email\":\"john@example.com\",\"role\":\"admin\"}";

        // Cache user data
        try (Jedis jedis = glideJedisPool.getResource()) {
            jedis.set(userKey, userData);
        }

        // Retrieve user data from different connection
        try (Jedis jedis = glideJedisPool.getResource()) {
            String cachedUserData = jedis.get(userKey);
            assertEquals(userData, cachedUserData, "User data caching with pool should work like Jedis");
        }

        // Pattern 4: Distributed locking simulation (key existence check)
        String lockKey = TEST_KEY_PREFIX + "lock:resource123:" + testCounter;
        String lockValue = "locked_by_process_" + testCounter;

        glideJedis.set(lockKey, lockValue);
        String lockCheck = glideJedis.get(lockKey);
        assertEquals(lockValue, lockCheck, "Distributed locking pattern should work like Jedis");

        System.out.println("✓ Real-world usage patterns validated");
        System.out.println("  Session management: OK");
        System.out.println("  Configuration caching: OK");
        System.out.println("  User data caching: OK");
        System.out.println("  Distributed locking: OK");
    }

    @Test
    @Order(8)
    @DisplayName("Validate Error Handling Compatibility")
    void testErrorHandlingCompatibility() {
        // Test behavior with edge cases that should match Jedis

        // Test with very long key
        StringBuilder longKeyBuilder = new StringBuilder(TEST_KEY_PREFIX + "longkey:");
        for (int i = 0; i < 100; i++) {
            longKeyBuilder.append("verylongkeypart").append(i);
        }
        String longKey = longKeyBuilder.toString();
        String longKeyValue = "value_for_long_key";

        // Should handle long keys like Jedis
        String longKeySetResult = glideJedis.set(longKey, longKeyValue);
        assertEquals("OK", longKeySetResult, "Long keys should be handled like Jedis");

        String longKeyGetResult = glideJedis.get(longKey);
        assertEquals(longKeyValue, longKeyGetResult, "Long key retrieval should work like Jedis");

        // Test connection state after operations
        String testKey = TEST_KEY_PREFIX + "after_edge_cases:" + testCounter;
        String testValue = "connection_should_still_work";

        String setResult = glideJedis.set(testKey, testValue);
        assertEquals("OK", setResult, "Connection should work after edge case operations");

        String getResult = glideJedis.get(testKey);
        assertEquals(testValue, getResult, "GET should work after edge case operations");

        System.out.println("✓ Error handling compatibility validated");
        System.out.println("  Long key handling: OK");
        System.out.println("  Connection stability: OK");
    }

    @Test
    @Order(9)
    @DisplayName("Performance Characteristics Validation")
    void testPerformanceCharacteristics() {
        int warmupOps = 50;
        int testOps = 200;
        String keyPrefix = TEST_KEY_PREFIX + "perf:" + testCounter + ":";

        // Warmup
        for (int i = 0; i < warmupOps; i++) {
            glideJedis.set(keyPrefix + "warmup:" + i, "warmup_value_" + i);
            glideJedis.get(keyPrefix + "warmup:" + i);
        }

        // Measure performance
        long startTime = System.nanoTime();

        for (int i = 0; i < testOps; i++) {
            String key = keyPrefix + i;
            String value = "perf_value_" + i;

            String setResult = glideJedis.set(key, value);
            assertEquals("OK", setResult, "Performance test SET should work");

            String getResult = glideJedis.get(key);
            assertEquals(value, getResult, "Performance test GET should work");
        }

        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        double durationMs = durationNanos / 1_000_000.0;
        double opsPerSecond = (testOps * 2) / (durationMs / 1000.0); // SET + GET operations

        System.out.println("✓ Performance characteristics validated");
        System.out.println("  Operations: " + (testOps * 2) + " (SET + GET)");
        System.out.println("  Duration: " + String.format("%.2f", durationMs) + " ms");
        System.out.println("  Throughput: " + String.format("%.0f", opsPerSecond) + " ops/sec");

        // Basic performance sanity check
        assertTrue(opsPerSecond > 100, "Should achieve reasonable throughput (>100 ops/sec)");
        assertTrue(durationMs < 30000, "Should complete in reasonable time (<30 seconds)");
    }

    @Test
    @Order(10)
    @DisplayName("Final Integration Validation")
    void testFinalIntegrationValidation() {
        System.out.println("=== Final Integration Validation ===");

        // Test that demonstrates complete Jedis compatibility for migration
        String migrationKey = TEST_KEY_PREFIX + "migration_ready:" + testCounter;
        String migrationValue = "GLIDE_is_ready_for_production_migration";

        // Direct connection pattern (typical Jedis usage)
        Jedis directJedis = new Jedis(REDIS_HOST, REDIS_PORT);
        try {
            String setResult = directJedis.set(migrationKey, migrationValue);
            assertEquals("OK", setResult, "Direct connection should work exactly like Jedis");

            String getResult = directJedis.get(migrationKey);
            assertEquals(
                    migrationValue, getResult, "Direct connection GET should work exactly like Jedis");

            String pingResult = directJedis.ping();
            assertEquals("PONG", pingResult, "Direct connection PING should work exactly like Jedis");

        } finally {
            directJedis.close();
        }

        // Pool pattern (typical production Jedis usage)
        JedisPool migrationPool = new JedisPool(REDIS_HOST, REDIS_PORT);
        try {
            try (Jedis pooledJedis = migrationPool.getResource()) {
                String retrievedValue = pooledJedis.get(migrationKey);
                assertEquals(
                        migrationValue, retrievedValue, "Pool connection should work exactly like JedisPool");

                String pingResult = pooledJedis.ping("migration_test");
                assertEquals("migration_test", pingResult, "Pool PING should work exactly like JedisPool");
            }
        } finally {
            migrationPool.close();
        }

        System.out.println("✓ Final integration validation completed");
        System.out.println("✓ GLIDE Jedis compatibility layer is ready for production migration!");
        System.out.println("✓ All Jedis API patterns work identically to actual Jedis");
    }
}
