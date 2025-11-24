/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.SERVER_VERSION;
import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.args.BitOP;
import redis.clients.jedis.args.ExpiryOption;
import redis.clients.jedis.args.ListDirection;
import redis.clients.jedis.args.ListPosition;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.HGetExParams;
import redis.clients.jedis.params.HSetExParams;
import redis.clients.jedis.params.LPosParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.util.KeyValue;

/**
 * Jedis compatibility test that validates GLIDE's Jedis compatibility layer functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected Jedis API and
 * behavior for comprehensive server operations.
 */
public class JedisTest {

    // Server configuration - dynamically resolved from CI environment
    private static final String valkeyHost;
    private static final int valkeyPort;

    // GLIDE compatibility layer instance
    private Jedis jedis;

    static {
        String[] standaloneHosts = STANDALONE_HOSTS;

        // Fail if standalone server configuration is not found in system properties
        if (standaloneHosts.length == 0 || standaloneHosts[0].trim().isEmpty()) {
            throw new IllegalStateException(
                    "Standalone server configuration not found in system properties. "
                            + "Please set 'test.server.standalone' system property with server address "
                            + "(e.g., -Dtest.server.standalone=localhost:6379)");
        }

        String firstHost = standaloneHosts[0].trim();
        String[] hostPort = firstHost.split(":");

        if (hostPort.length == 2) {
            try {
                valkeyHost = hostPort[0];
                valkeyPort = Integer.parseInt(hostPort[1]);
            } catch (NumberFormatException e) {
                throw new IllegalStateException(
                        "Invalid port number in standalone server configuration: "
                                + firstHost
                                + ". "
                                + "Expected format: host:port (e.g., localhost:6379)",
                        e);
            }
        } else {
            throw new IllegalStateException(
                    "Invalid standalone server format: "
                            + firstHost
                            + ". "
                            + "Expected format: host:port (e.g., localhost:6379)");
        }
    }

    @BeforeEach
    void setup() {
        // Create GLIDE Jedis compatibility layer instance
        jedis = new Jedis(valkeyHost, valkeyPort);
        jedis.connect();
        assertNotNull(jedis, "GLIDE Jedis instance should be created successfully");
    }

    /**
     * Helper method to safely convert sendCommand result to Long. Handles both Number types and
     * String representations.
     */
    private Long assertLongResult(Object result, String message) {
        assertNotNull(result, message + " - result should not be null");
        if (result instanceof Number) {
            return ((Number) result).longValue();
        } else {
            try {
                return Long.parseLong(result.toString());
            } catch (NumberFormatException e) {
                fail(message + " - could not parse result as Long: " + result);
                return null; // Never reached
            }
        }
    }

    /** Helper method to safely convert sendCommand result to String. */
    private String assertStringResult(Object result, String message) {
        assertNotNull(result, message + " - result should not be null");
        return result.toString();
    }

    /** Helper method to validate array/list results from commands like MGET. */
    private void assertArrayContains(Object result, String[] expectedValues, String message) {
        assertNotNull(result, message + " - result should not be null");

        if (result instanceof Object[]) {
            Object[] array = (Object[]) result;
            assertEquals(expectedValues.length, array.length, message + " - array length should match");
            for (int i = 0; i < expectedValues.length; i++) {
                assertEquals(
                        expectedValues[i], array[i].toString(), message + " - element " + i + " should match");
            }
        } else if (result instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) result;
            assertEquals(expectedValues.length, list.size(), message + " - list size should match");
            for (int i = 0; i < expectedValues.length; i++) {
                assertEquals(
                        expectedValues[i],
                        list.get(i).toString(),
                        message + " - element " + i + " should match");
            }
        } else {
            // Fallback: check string representation contains all values
            String resultStr = result.toString();
            for (String expectedValue : expectedValues) {
                assertTrue(
                        resultStr.contains(expectedValue), message + " - should contain " + expectedValue);
            }
        }
    }

    @Test
    void jedis_constructor_with_glide_client() {
        // Test the protected constructor that takes GlideClient directly
        // This constructor is used internally by the pool factory
        try {
            // Create a GLIDE client configuration
            GlideClientConfiguration glideConfig =
                    GlideClientConfiguration.builder()
                            .address(NodeAddress.builder().host(valkeyHost).port(valkeyPort).build())
                            .requestTimeout(2000)
                            .build();

            // Create GLIDE client
            GlideClient glideClient = GlideClient.createClient(glideConfig).get();

            // Create Jedis instance using the protected constructor
            DefaultJedisClientConfig config = DefaultJedisClientConfig.builder().build();

            // Use reflection to access the protected constructor
            Constructor<Jedis> constructor =
                    Jedis.class.getDeclaredConstructor(GlideClient.class, JedisClientConfig.class);
            constructor.setAccessible(true);
            Jedis testJedis = constructor.newInstance(glideClient, config);

            assertNotNull(testJedis, "Jedis instance should be created successfully");
            assertFalse(testJedis.isClosed(), "Jedis instance should not be closed initially");

            // Test basic operations
            String testKey = UUID.randomUUID().toString();
            String testValue = "constructor_test_value";

            String setResult = testJedis.set(testKey, testValue);
            assertEquals("OK", setResult, "SET should work with constructor-created Jedis");

            String getResult = testJedis.get(testKey);
            assertEquals(testValue, getResult, "GET should work with constructor-created Jedis");

            // Clean up
            testJedis.del(testKey);
            testJedis.close(); // This should not return to pool since dataSource is null

        } catch (Exception e) {
            fail("Constructor test failed: " + e.getMessage(), e);
        }
    }

    @Test
    void register_client_name_and_version() {
        String minVersion = "7.2.0";
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo(minVersion),
                "Valkey version required >= " + minVersion);

        String info = (String) jedis.sendCommand(Protocol.Command.CLIENT, "INFO");
        assertTrue(info.contains("lib-name=GlideJedisAdapter"));
        assertTrue(info.contains("lib-ver=unknown"));
    }

    @Test
    void basic_get_set_operations() {
        String testKey = UUID.randomUUID().toString();
        String testValue = "test_value_123";

        // Test GLIDE Jedis compatibility layer
        String setResult = jedis.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should return OK");

        String getResult = jedis.get(testKey);
        assertEquals(testValue, getResult, "GET should return the set value");
    }

    @Test
    void set_command_with_set_params() {
        String testKey = UUID.randomUUID().toString();
        String testValue = "set_params_value";

        // Test SET with EX (expiration in seconds)
        SetParams params = new SetParams().ex(60);
        String result = jedis.set(testKey, testValue, params);
        assertEquals("OK", result, "SET with params should return OK");
        assertEquals(testValue, jedis.get(testKey), "Key should have correct value");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");

        // Test SET with NX (only if not exists)
        String existingKey = UUID.randomUUID().toString();
        jedis.set(existingKey, "existing_value");

        params = new SetParams().nx();
        result = jedis.set(existingKey, "new_value", params);
        assertNull(result, "SET NX should return null when key exists");
        assertEquals("existing_value", jedis.get(existingKey), "Key should retain original value");

        // Test SET with XX (only if exists)
        String nonExistentKey = UUID.randomUUID().toString();
        params = new SetParams().xx();
        result = jedis.set(nonExistentKey, "xx_value", params);
        assertNull(result, "SET XX should return null when key doesn't exist");
        assertNull(jedis.get(nonExistentKey), "Key should not be created");
    }

    @Test
    void multiple_get_set_operations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(UUID.randomUUID().toString(), "value1");
        testData.put(UUID.randomUUID().toString(), "value2");
        testData.put(UUID.randomUUID().toString(), "value3");

        // Test multiple SET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedis.set(entry.getKey(), entry.getValue());
            assertEquals("OK", result, "SET should return OK for " + entry.getKey());
        }

        // Test multiple GET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = jedis.get(entry.getKey());
            assertEquals(
                    entry.getValue(), result, "GET should return correct value for " + entry.getKey());
        }
    }

    @Test
    void mset_command() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        // Test MSET
        String result = jedis.mset(key1, "value1", key2, "value2", key3, "value3");
        assertEquals("OK", result, "MSET should return OK");

        // Verify all keys were set
        assertEquals("value1", jedis.get(key1), "Key1 should have correct value");
        assertEquals("value2", jedis.get(key2), "Key2 should have correct value");
        assertEquals("value3", jedis.get(key3), "Key3 should have correct value");
    }

    @Test
    void mget_command() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        // Set up test data
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");
        jedis.set(key3, "value3");

        // Test MGET
        List<String> results = jedis.mget(key1, key2, key3);
        assertNotNull(results, "MGET should return a list");
        assertEquals(3, results.size(), "MGET should return 3 values");
        assertEquals("value1", results.get(0), "First value should be correct");
        assertEquals("value2", results.get(1), "Second value should be correct");
        assertEquals("value3", results.get(2), "Third value should be correct");
    }

    @Test
    void setnx_command() {
        String testKey = UUID.randomUUID().toString();

        // Test SETNX on non-existing key
        long result = jedis.setnx(testKey, "value1");
        assertEquals(1, result, "SETNX should return 1 for new key");
        assertEquals("value1", jedis.get(testKey), "Key should have correct value");

        // Test SETNX on existing key
        result = jedis.setnx(testKey, "value2");
        assertEquals(0, result, "SETNX should return 0 for existing key");
        assertEquals("value1", jedis.get(testKey), "Key should retain original value");
    }

    @Test
    void setex_command() {
        String testKey = UUID.randomUUID().toString();

        // Test SETEX
        String result = jedis.setex(testKey, 60, "test_value");
        assertEquals("OK", result, "SETEX should return OK");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");
    }

    @Test
    void psetex_command() {
        String testKey = UUID.randomUUID().toString();

        // Test PSETEX
        String result = jedis.psetex(testKey, 60000, "test_value");
        assertEquals("OK", result, "PSETEX should return OK");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 60000, "PTTL should be set correctly");
    }

    @Test
    void getset_command() {
        String testKey = UUID.randomUUID().toString();

        // Test GETSET on non-existing key
        String result = jedis.getSet(testKey, "new_value");
        assertNull(result, "GETSET should return null for non-existing key");
        assertEquals("new_value", jedis.get(testKey), "Key should have new value");

        // Test GETSET on existing key
        result = jedis.getSet(testKey, "newer_value");
        assertEquals("new_value", result, "GETSET should return old value");
        assertEquals("newer_value", jedis.get(testKey), "Key should have newer value");
    }

    @Test
    void setget_command() {
        String testKey = UUID.randomUUID().toString();

        // Test SETGET
        String result = jedis.setGet(testKey, "test_value");
        assertNull(result, "SETGET should return null for new key");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Test SETGET on existing key
        result = jedis.setGet(testKey, "new_value");
        assertEquals("test_value", result, "SETGET should return old value");
        assertEquals("new_value", jedis.get(testKey), "Key should have new value");
    }

    @Test
    void setget_command_with_params() {
        String testKey = UUID.randomUUID().toString();

        // Test SETGET with EX parameter
        SetParams params = new SetParams().ex(60);
        String result = jedis.setGet(testKey, "test_value", params);
        assertNull(result, "SETGET should return null for new key");
        assertEquals("test_value", jedis.get(testKey), "Key should have correct value");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");

        // Test SETGET with params on existing key
        params = new SetParams().px(30000);
        result = jedis.setGet(testKey, "new_value", params);
        assertEquals("test_value", result, "SETGET should return old value");
        assertEquals("new_value", jedis.get(testKey), "Key should have new value");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 30000, "PTTL should be set correctly");
    }

    @Test
    void getdel_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"),
                "GETDEL command requires Valkey 6.2.0 or higher");

        String testKey = UUID.randomUUID().toString();

        // Set up test data
        jedis.set(testKey, "test_value");

        // Test GETDEL
        String result = jedis.getDel(testKey);
        assertEquals("test_value", result, "GETDEL should return the value");
        assertNull(jedis.get(testKey), "Key should be deleted");

        // Test GETDEL on non-existing key
        result = jedis.getDel(testKey);
        assertNull(result, "GETDEL should return null for non-existing key");
    }

    @Test
    void getex_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"),
                "GETEX command requires Valkey 6.2.0 or higher");

        String testKey = UUID.randomUUID().toString();
        String testValue = "getex_value";

        // Set initial value
        jedis.set(testKey, testValue);

        // Test GETEX with expiration
        GetExParams params = new GetExParams().ex(10); // 10 seconds
        String result = jedis.getEx(testKey, params);
        assertEquals(testValue, result, "GETEX should return the current value");

        // Verify TTL was set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 10, "TTL should be set correctly");
    }

    @Test
    void append_command() {
        String testKey = UUID.randomUUID().toString();

        // Test APPEND on non-existing key
        long result = jedis.append(testKey, "Hello");
        assertEquals(5, result, "APPEND should return string length");
        assertEquals("Hello", jedis.get(testKey), "Key should have appended value");

        // Test APPEND on existing key
        result = jedis.append(testKey, " World");
        assertEquals(11, result, "APPEND should return updated string length");
        assertEquals("Hello World", jedis.get(testKey), "Key should have concatenated value");
    }

    @Test
    void strlen_command() {
        String testKey = UUID.randomUUID().toString();

        // Test STRLEN on non-existing key
        long result = jedis.strlen(testKey);
        assertEquals(0, result, "STRLEN should return 0 for non-existing key");

        // Test STRLEN on existing key
        jedis.set(testKey, "Hello World");
        result = jedis.strlen(testKey);
        assertEquals(11, result, "STRLEN should return correct length");
    }

    @Test
    void incr_command() {
        String testKey = UUID.randomUUID().toString();

        // Test INCR on non-existing key
        long result = jedis.incr(testKey);
        assertEquals(1, result, "INCR should return 1 for new key");
        assertEquals("1", jedis.get(testKey), "Key should have value 1");

        // Test INCR on existing key
        result = jedis.incr(testKey);
        assertEquals(2, result, "INCR should return 2");
        assertEquals("2", jedis.get(testKey), "Key should have value 2");
    }

    @Test
    void incrby_command() {
        String testKey = UUID.randomUUID().toString();

        // Test INCRBY on non-existing key
        long result = jedis.incrBy(testKey, 5);
        assertEquals(5, result, "INCRBY should return 5 for new key");
        assertEquals("5", jedis.get(testKey), "Key should have value 5");

        // Test INCRBY on existing key
        result = jedis.incrBy(testKey, 3);
        assertEquals(8, result, "INCRBY should return 8");
        assertEquals("8", jedis.get(testKey), "Key should have value 8");
    }

    @Test
    void incrbyfloat_command() {
        String testKey = UUID.randomUUID().toString();

        // Test INCRBYFLOAT on non-existing key
        double result = jedis.incrByFloat(testKey, 2.5);
        assertEquals(2.5, result, 0.001, "INCRBYFLOAT should return 2.5 for new key");
        assertEquals("2.5", jedis.get(testKey), "Key should have value 2.5");

        // Test INCRBYFLOAT on existing key
        result = jedis.incrByFloat(testKey, 1.5);
        assertEquals(4.0, result, 0.001, "INCRBYFLOAT should return 4.0");
        assertEquals("4", jedis.get(testKey), "Key should have value 4");
    }

    @Test
    void decr_command() {
        String testKey = UUID.randomUUID().toString();

        // Set initial value
        jedis.set(testKey, "10");

        // Test DECR
        long result = jedis.decr(testKey);
        assertEquals(9, result, "DECR should return 9");
        assertEquals("9", jedis.get(testKey), "Key should have value 9");

        // Test DECR again
        result = jedis.decr(testKey);
        assertEquals(8, result, "DECR should return 8");
        assertEquals("8", jedis.get(testKey), "Key should have value 8");
    }

    @Test
    void decrby_command() {
        String testKey = UUID.randomUUID().toString();

        // Set initial value
        jedis.set(testKey, "20");

        // Test DECRBY
        long result = jedis.decrBy(testKey, 5);
        assertEquals(15, result, "DECRBY should return 15");
        assertEquals("15", jedis.get(testKey), "Key should have value 15");

        // Test DECRBY again
        result = jedis.decrBy(testKey, 7);
        assertEquals(8, result, "DECRBY should return 8");
        assertEquals("8", jedis.get(testKey), "Key should have value 8");
    }

    @Test
    void del_command() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");
        jedis.set(key3, "value3");

        // Test single key deletion
        long result = jedis.del(key1);
        assertEquals(1, result, "DEL should return 1 for deleted key");
        assertNull(jedis.get(key1), "Key should not exist after deletion");

        // Test multiple key deletion
        result = jedis.del(key2, key3);
        assertEquals(2, result, "DEL should return 2 for two deleted keys");
        assertNull(jedis.get(key2), "Key2 should not exist after deletion");
        assertNull(jedis.get(key3), "Key3 should not exist after deletion");
    }

    @Test
    void unlink_command() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");

        // Test UNLINK
        long result = jedis.unlink(key1, key2);
        assertEquals(2, result, "UNLINK should return 2 for two deleted keys");
        assertNull(jedis.get(key1), "Key1 should not exist after unlink");
        assertNull(jedis.get(key2), "Key2 should not exist after unlink");
    }

    @Test
    void exists_command() {
        String testKey = UUID.randomUUID().toString();

        // Test EXISTS on non-existing key
        boolean result = jedis.exists(testKey);
        assertFalse(result, "EXISTS should return false for non-existing key");

        // Test EXISTS on existing key
        jedis.set(testKey, "test_value");
        result = jedis.exists(testKey);
        assertTrue(result, "EXISTS should return true for existing key");
    }

    @Test
    void exists_multiple_command() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        // Set up some keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");

        // Test EXISTS on multiple keys
        long result = jedis.exists(key1, key2, key3);
        assertEquals(2, result, "EXISTS should return 2 for two existing keys");

        // Test EXISTS on all non-existing keys
        result = jedis.exists(key3, UUID.randomUUID().toString());
        assertEquals(0, result, "EXISTS should return 0 for no existing keys");
    }

    @Test
    void key_exists_command() {
        String testKey = UUID.randomUUID().toString();

        // Test KEYEXISTS on non-existing key
        boolean result = jedis.keyExists(testKey);
        assertFalse(result, "KEYEXISTS should return false for non-existing key");

        // Test KEYEXISTS on existing key
        jedis.set(testKey, "test_value");
        result = jedis.keyExists(testKey);
        assertTrue(result, "KEYEXISTS should return true for existing key");
    }

    @Test
    void type_command() {
        String testKey = UUID.randomUUID().toString();

        // Test TYPE on non-existing key
        String result = jedis.type(testKey);
        assertEquals("none", result, "TYPE should return 'none' for non-existing key");

        // Test TYPE on string key
        jedis.set(testKey, "test_value");
        result = jedis.type(testKey);
        assertEquals("string", result, "TYPE should return 'string' for string key");
    }

    @Test
    void keys_command() {
        String keyPrefix = "keys_test_" + UUID.randomUUID().toString().substring(0, 8) + "_";
        String key1 = keyPrefix + "1";
        String key2 = keyPrefix + "2";
        String key3 = keyPrefix + "3";

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");
        jedis.set(key3, "value3");

        // Test KEYS with pattern that matches the keys we just created
        Set<String> result = jedis.keys(keyPrefix + "*");
        assertNotNull(result, "KEYS should return a set");
        assertEquals(3, result.size(), "KEYS should return 3 matching keys");
        assertTrue(result.contains(key1), "Result should contain key1");
        assertTrue(result.contains(key2), "Result should contain key2");
        assertTrue(result.contains(key3), "Result should contain key3");
    }

    @Test
    void randomkey_command() {
        // Set up some test keys
        jedis.set(UUID.randomUUID().toString(), "value1");
        jedis.set(UUID.randomUUID().toString(), "value2");
        jedis.set(UUID.randomUUID().toString(), "value3");

        // Test RANDOMKEY
        String randomKey = jedis.randomKey();
        assertNotNull(randomKey, "RANDOMKEY should return a key");
        assertTrue(randomKey.length() > 0, "Random key should not be empty");
    }

    @Test
    void rename_command() {
        String srcKey = UUID.randomUUID().toString();
        String destKey = UUID.randomUUID().toString();

        // Set up source key
        jedis.set(srcKey, "test_value");

        // Test RENAME
        String result = jedis.rename(srcKey, destKey);
        assertEquals("OK", result, "RENAME should return OK");

        // Verify source key is gone and destination key exists
        assertNull(jedis.get(srcKey), "Source key should not exist after rename");
        assertEquals("test_value", jedis.get(destKey), "Destination key should have the value");
    }

    @Test
    void renamenx_command() {
        String srcKey = UUID.randomUUID().toString();
        String destKey = UUID.randomUUID().toString();

        // Set up source key
        jedis.set(srcKey, "source_value");

        // Test RENAMENX on non-existing destination
        long result = jedis.renamenx(srcKey, destKey);
        assertEquals(1, result, "RENAMENX should return 1 for successful rename");
        assertEquals("source_value", jedis.get(destKey), "Destination key should have the value");

        // Set up another source key and test RENAMENX on existing destination
        String srcKey2 = UUID.randomUUID().toString();
        jedis.set(srcKey2, "source_value2");

        result = jedis.renamenx(srcKey2, destKey);
        assertEquals(0, result, "RENAMENX should return 0 when destination exists");
        assertEquals(
                "source_value", jedis.get(destKey), "Destination key should retain original value");
        assertEquals("source_value2", jedis.get(srcKey2), "Source key should still exist");
    }

    @Test
    void setbit_command() {
        String testKey = UUID.randomUUID().toString();

        // Test SETBIT
        boolean result = jedis.setbit(testKey, 7, true);
        assertFalse(result, "Initial bit should be false");

        // Test SETBIT again
        result = jedis.setbit(testKey, 7, false);
        assertTrue(result, "Previous bit should be true");

        // Test SETBIT on different position
        result = jedis.setbit(testKey, 0, true);
        assertFalse(result, "Initial bit at position 0 should be false");
    }

    @Test
    void getbit_command() {
        String testKey = UUID.randomUUID().toString();

        // Test GETBIT on non-existing key
        boolean result = jedis.getbit(testKey, 0);
        assertFalse(result, "Bit should be false for non-existing key");

        // Set a bit and test GETBIT
        jedis.setbit(testKey, 7, true);
        result = jedis.getbit(testKey, 7);
        assertTrue(result, "Bit should be true after setting");

        result = jedis.getbit(testKey, 0);
        assertFalse(result, "Unset bit should be false");
    }

    @Test
    void bitcount_command() {
        String testKey = UUID.randomUUID().toString();

        // Test BITCOUNT on non-existing key
        long result = jedis.bitcount(testKey);
        assertEquals(0, result, "BITCOUNT should return 0 for non-existing key");

        // Set some bits and test BITCOUNT
        jedis.setbit(testKey, 0, true);
        jedis.setbit(testKey, 7, true);
        jedis.setbit(testKey, 15, true);

        result = jedis.bitcount(testKey);
        assertEquals(3, result, "BITCOUNT should return 3 for three set bits");
    }

    @Test
    void bitpos_command() {
        String testKey = UUID.randomUUID().toString();

        // Set some bits
        jedis.setbit(testKey, 0, false);
        jedis.setbit(testKey, 1, true);
        jedis.setbit(testKey, 2, false);
        jedis.setbit(testKey, 3, true);

        // Test BITPOS for first set bit
        long result = jedis.bitpos(testKey, true);
        assertEquals(1, result, "First set bit should be at position 1");

        // Test BITPOS with parameters
        BitPosParams params = new BitPosParams(0, 1);
        result = jedis.bitpos(testKey, true, params);
        assertEquals(1, result, "First set bit in range should be at position 1");
    }

    @Test
    void bitcount_with_range_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test data - create a byte with pattern 10101010
        jedis.setbit(testKey, 0, true);
        jedis.setbit(testKey, 2, true);
        jedis.setbit(testKey, 4, true);
        jedis.setbit(testKey, 6, true);
        jedis.setbit(testKey, 8, true);
        jedis.setbit(testKey, 10, true);

        // Test BITCOUNT with byte range
        long result = jedis.bitcount(testKey, 0, 0);
        assertEquals(4, result, "BITCOUNT should count bits in first byte");

        result = jedis.bitcount(testKey, 0, 1);
        assertEquals(6, result, "BITCOUNT should count bits in first two bytes");
    }

    @Test
    void bitop_command() {
        String srcKey1 = UUID.randomUUID().toString();
        String srcKey2 = UUID.randomUUID().toString();
        String destKey = UUID.randomUUID().toString();

        // Set up source keys with different bit patterns
        jedis.setbit(srcKey1, 0, true);
        jedis.setbit(srcKey1, 2, true);
        jedis.setbit(srcKey2, 1, true);
        jedis.setbit(srcKey2, 2, true);

        // Test BITOP AND
        long result = jedis.bitop(BitOP.AND, destKey, srcKey1, srcKey2);
        assertTrue(result > 0, "BITOP should return length of result");
        assertTrue(jedis.getbit(destKey, 2), "Bit 2 should be set (1 AND 1)");
        assertFalse(jedis.getbit(destKey, 0), "Bit 0 should not be set (1 AND 0)");
        assertFalse(jedis.getbit(destKey, 1), "Bit 1 should not be set (0 AND 1)");

        // Test BITOP OR
        result = jedis.bitop(BitOP.OR, destKey, srcKey1, srcKey2);
        assertTrue(result > 0, "BITOP OR should return length of result");
        assertTrue(jedis.getbit(destKey, 0), "Bit 0 should be set (1 OR 0)");
        assertTrue(jedis.getbit(destKey, 1), "Bit 1 should be set (0 OR 1)");
        assertTrue(jedis.getbit(destKey, 2), "Bit 2 should be set (1 OR 1)");

        // Test BITOP XOR
        result = jedis.bitop(BitOP.XOR, destKey, srcKey1, srcKey2);
        assertTrue(result > 0, "BITOP XOR should return length of result");
        assertTrue(jedis.getbit(destKey, 0), "Bit 0 should be set (1 XOR 0)");
        assertTrue(jedis.getbit(destKey, 1), "Bit 1 should be set (0 XOR 1)");
        assertFalse(jedis.getbit(destKey, 2), "Bit 2 should not be set (1 XOR 1)");
    }

    @Test
    void bitfield_command() {
        String testKey = UUID.randomUUID().toString();

        // Test BITFIELD SET
        List<Long> result = jedis.bitfield(testKey, "SET", "u8", "0", "255");
        assertNotNull(result, "BITFIELD should return a list");
        assertEquals(1, result.size(), "BITFIELD should return one result");
        assertEquals(0L, result.get(0), "Initial value should be 0");

        // Test BITFIELD GET
        result = jedis.bitfield(testKey, "GET", "u8", "0");
        assertNotNull(result, "BITFIELD GET should return a list");
        assertEquals(1, result.size(), "BITFIELD GET should return one result");
        assertEquals(255L, result.get(0), "Should return the set value");

        // Test BITFIELD INCRBY
        result = jedis.bitfield(testKey, "INCRBY", "u8", "0", "1");
        assertNotNull(result, "BITFIELD INCRBY should return a list");
        assertEquals(1, result.size(), "BITFIELD INCRBY should return one result");
        assertEquals(0L, result.get(0), "Should wrap around from 255 to 0");
    }

    @Test
    void bitfield_ro_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test data
        jedis.bitfield(testKey, "SET", "u8", "0", "42");

        // Test BITFIELD_RO (read-only)
        List<Long> result = jedis.bitfieldReadonly(testKey, "GET", "u8", "0");
        assertNotNull(result, "BITFIELD_RO should return a list");
        assertEquals(1, result.size(), "BITFIELD_RO should return one result");
        assertEquals(42L, result.get(0), "Should return the stored value");

        // Test BITFIELD_RO on non-existing key
        result = jedis.bitfieldReadonly(UUID.randomUUID().toString(), "GET", "u8", "0");
        assertNotNull(result, "BITFIELD_RO should return a list for non-existing key");
        assertEquals(1, result.size(), "BITFIELD_RO should return one result");
        assertEquals(0L, result.get(0), "Should return 0 for non-existing key");
    }

    @Test
    void sort_command() {
        // TO DO: Add integration test
    }

    @Test
    void dump_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test data
        jedis.set(testKey, "dump_test_value");

        // Test DUMP
        byte[] result = jedis.dump(testKey);
        assertNotNull(result, "DUMP should return serialized data");
        assertTrue(result.length > 0, "DUMP should return non-empty data");

        // Test DUMP on non-existing key
        result = jedis.dump(UUID.randomUUID().toString());
        assertNull(result, "DUMP should return null for non-existing key");
    }

    @Test
    void restore_command() {
        String sourceKey = UUID.randomUUID().toString();
        String destKey = UUID.randomUUID().toString();

        // Set up source data and dump it
        jedis.set(sourceKey, "restore_test_value");
        byte[] dumpData = jedis.dump(sourceKey);
        assertNotNull(dumpData, "Should be able to dump source key");

        // Test RESTORE
        String result = jedis.restore(destKey, 0, dumpData);
        assertEquals("OK", result, "RESTORE should return OK");
        assertEquals(
                "restore_test_value", jedis.get(destKey), "Restored key should have correct value");

        // Test RESTORE with TTL
        String destKeyWithTTL = UUID.randomUUID().toString();
        result = jedis.restore(destKeyWithTTL, 60000, dumpData);
        assertEquals("OK", result, "RESTORE with TTL should return OK");
        assertEquals(
                "restore_test_value", jedis.get(destKeyWithTTL), "Restored key should have correct value");

        long ttl = jedis.pttl(destKeyWithTTL);
        assertTrue(ttl > 0 && ttl <= 60000, "Restored key should have TTL set");
    }

    @Test
    void migrate_command() {
        // TO DO: Add integration test
    }

    @Test
    void move_command() {
        // TO DO: Add integration test
    }

    @Test
    void expire_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test EXPIRE
        long result = jedis.expire(testKey, 60);
        assertEquals(1, result, "EXPIRE should return 1 for success");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");
    }

    @Test
    void expireat_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test EXPIREAT (set expiration to 60 seconds from now)
        long expireTime = System.currentTimeMillis() / 1000 + 60;
        long result = jedis.expireAt(testKey, expireTime);
        assertEquals(1, result, "EXPIREAT should return 1 for success");

        // Verify TTL is set
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be set correctly");
    }

    @Test
    void pexpire_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test PEXPIRE
        long result = jedis.pexpire(testKey, 60000);
        assertEquals(1, result, "PEXPIRE should return 1 for success");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 60000, "PTTL should be set correctly");
    }

    @Test
    void pexpireat_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test key
        jedis.set(testKey, "test_value");

        // Test PEXPIREAT (set expiration to 60 seconds from now)
        long expireTime = System.currentTimeMillis() + 60000;
        long result = jedis.pexpireAt(testKey, expireTime);
        assertEquals(1, result, "PEXPIREAT should return 1 for success");

        // Verify PTTL is set
        long pttl = jedis.pttl(testKey);
        assertTrue(pttl > 0 && pttl <= 60000, "PTTL should be set correctly");
    }

    @Test
    void ttl_command() {
        String testKey = UUID.randomUUID().toString();

        // Test TTL on non-existing key
        long result = jedis.ttl(testKey);
        assertEquals(-2, result, "TTL should return -2 for non-existing key");

        // Test TTL on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.ttl(testKey);
        assertEquals(-1, result, "TTL should return -1 for key without expiration");

        // Test TTL on key with expiration
        jedis.expire(testKey, 60);
        result = jedis.ttl(testKey);
        assertTrue(result > 0 && result <= 60, "TTL should return remaining time");
    }

    @Test
    void pttl_command() {
        String testKey = UUID.randomUUID().toString();

        // Test PTTL on non-existing key
        long result = jedis.pttl(testKey);
        assertEquals(-2, result, "PTTL should return -2 for non-existing key");

        // Test PTTL on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.pttl(testKey);
        assertEquals(-1, result, "PTTL should return -1 for key without expiration");

        // Test PTTL on key with expiration
        jedis.pexpire(testKey, 60000);
        result = jedis.pttl(testKey);
        assertTrue(result > 0 && result <= 60000, "PTTL should return remaining time in milliseconds");
    }

    @Test
    void persist_command() {
        String testKey = UUID.randomUUID().toString();

        // Set up test key with expiration
        jedis.set(testKey, "test_value");
        jedis.expire(testKey, 60);

        // Verify key has expiration
        long ttl = jedis.ttl(testKey);
        assertTrue(ttl > 0, "Key should have expiration");

        // Test PERSIST
        long result = jedis.persist(testKey);
        assertEquals(1, result, "PERSIST should return 1 for success");

        // Verify expiration is removed
        ttl = jedis.ttl(testKey);
        assertEquals(-1, ttl, "Key should not have expiration after PERSIST");
    }

    @Test
    void expiretime_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "EXPIRETIME command requires Valkey 7.0.0 or higher");

        String testKey = UUID.randomUUID().toString();

        // Test EXPIRETIME on non-existing key
        long result = jedis.expireTime(testKey);
        assertEquals(-2, result, "EXPIRETIME should return -2 for non-existing key");

        // Test EXPIRETIME on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.expireTime(testKey);
        assertEquals(-1, result, "EXPIRETIME should return -1 for key without expiration");

        // Test EXPIRETIME on key with expiration
        long currentTime = System.currentTimeMillis() / 1000;
        jedis.expireAt(testKey, currentTime + 60);
        result = jedis.expireTime(testKey);
        assertTrue(
                result > currentTime && result <= currentTime + 60,
                "EXPIRETIME should return expiration timestamp");
    }

    @Test
    void pexpiretime_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"),
                "PEXPIRETIME command requires Valkey 7.0.0 or higher");

        String testKey = UUID.randomUUID().toString();

        // Test PEXPIRETIME on non-existing key
        long result = jedis.pexpireTime(testKey);
        assertEquals(-2, result, "PEXPIRETIME should return -2 for non-existing key");

        // Test PEXPIRETIME on key without expiration
        jedis.set(testKey, "test_value");
        result = jedis.pexpireTime(testKey);
        assertEquals(-1, result, "PEXPIRETIME should return -1 for key without expiration");

        // Test PEXPIRETIME on key with expiration
        long currentTime = System.currentTimeMillis();
        jedis.pexpireAt(testKey, currentTime + 60000);
        result = jedis.pexpireTime(testKey);
        assertTrue(
                result > currentTime && result <= currentTime + 60000,
                "PEXPIRETIME should return expiration timestamp in milliseconds");
    }

    @Test
    void touch_command() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String nonExistentKey = UUID.randomUUID().toString();

        // Set up test keys
        jedis.set(key1, "value1");
        jedis.set(key2, "value2");

        // Test TOUCH on existing keys
        long result = jedis.touch(key1, key2);
        assertEquals(2, result, "TOUCH should return 2 for two existing keys");

        // Test TOUCH on mix of existing and non-existing keys
        result = jedis.touch(key1, nonExistentKey);
        assertEquals(1, result, "TOUCH should return 1 for one existing key");

        // Test TOUCH on non-existing key only
        result = jedis.touch(nonExistentKey);
        assertEquals(0, result, "TOUCH should return 0 for non-existing key");
    }

    @Test
    void copy_command() {
        String srcKey = UUID.randomUUID().toString();
        String destKey = UUID.randomUUID().toString();

        // Set up source key
        jedis.set(srcKey, "copy_value");

        // Test COPY to non-existing destination
        boolean result = jedis.copy(srcKey, destKey, false);
        assertTrue(result, "COPY should return true for successful copy");
        assertEquals("copy_value", jedis.get(srcKey), "Source key should still exist");
        assertEquals("copy_value", jedis.get(destKey), "Destination key should have copied value");

        // Test COPY to existing destination without replace
        jedis.set(destKey, "existing_value");
        result = jedis.copy(srcKey, destKey, false);
        assertFalse(result, "COPY should return false when destination exists and replace=false");
        assertEquals("existing_value", jedis.get(destKey), "Destination should retain original value");

        // Test COPY to existing destination with replace
        result = jedis.copy(srcKey, destKey, true);
        assertTrue(result, "COPY should return true when replace=true");
        assertEquals("copy_value", jedis.get(destKey), "Destination should have copied value");
    }

    @Test
    void scan_command() {
        // Clean up any existing keys first to avoid interference
        String keyPrefix = "scan_test_" + UUID.randomUUID().toString().substring(0, 8) + "_";

        // Set up test data with a common prefix for scanning
        Map<String, String> testData = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            testData.put(keyPrefix + i, "value_" + i);
        }

        // Set all test data
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            jedis.set(entry.getKey(), entry.getValue());
        }

        // Verify keys were set
        for (String key : testData.keySet()) {
            assertTrue(jedis.exists(key), "Key should exist: " + key);
        }

        // Test SCAN with pattern that matches our keys - iterate through all results
        ScanParams scanParams = new ScanParams().match(keyPrefix + "*").count(100);
        Set<String> allScannedKeys = new HashSet<>();
        String cursor = "0";
        int maxIterations = 50; // Prevent infinite loops
        int iterations = 0;

        do {
            ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
            assertNotNull(scanResult, "SCAN result should not be null");
            assertNotNull(scanResult.getResult(), "SCAN result list should not be null");

            allScannedKeys.addAll(scanResult.getResult());
            cursor = scanResult.getCursor();
            iterations++;
        } while (!"0".equals(cursor) && iterations < maxIterations);

        // Verify we found our keys
        assertFalse(allScannedKeys.isEmpty(), "SCAN should return some keys");

        // Count how many of our keys were found
        int foundCount = 0;
        for (String key : allScannedKeys) {
            if (key.startsWith(keyPrefix)) {
                foundCount++;
            }
        }

        assertTrue(
                foundCount > 0,
                "SCAN should find at least some keys with our prefix. Found: "
                        + foundCount
                        + " out of "
                        + testData.size());

        // Clean up test keys
        for (String key : testData.keySet()) {
            jedis.del(key);
        }
    }

    @Test
    void pfadd_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("2.8.9"),
                "HyperLogLog commands require Valkey 2.8.9 or higher");

        String testKey = UUID.randomUUID().toString();

        // Test PFADD
        long result = jedis.pfadd(testKey, "element1", "element2", "element3");
        assertEquals(1, result, "PFADD should return 1 for new HyperLogLog");

        // Test PFADD with existing elements
        result = jedis.pfadd(testKey, "element1", "element4");
        assertTrue(result >= 0, "PFADD should return non-negative value");
    }

    @Test
    void pfcount_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("2.8.9"),
                "HyperLogLog commands require Valkey 2.8.9 or higher");

        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        // Set up test data
        jedis.pfadd(key1, "element1", "element2", "element3");
        jedis.pfadd(key2, "element3", "element4", "element5");

        // Test PFCOUNT on single key
        long result = jedis.pfcount(key1);
        assertEquals(3, result, "PFCOUNT should return approximate count");

        // Test PFCOUNT on multiple keys
        result = jedis.pfcount(key1, key2);
        assertEquals(5, result, "PFCOUNT should return combined approximate count");
    }

    @Test
    void pfmerge_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("2.8.9"),
                "HyperLogLog commands require Valkey 2.8.9 or higher");

        String sourceKey1 = UUID.randomUUID().toString();
        String sourceKey2 = UUID.randomUUID().toString();
        String destKey = UUID.randomUUID().toString();

        // Add elements to source HyperLogLogs
        jedis.pfadd(sourceKey1, "element1", "element2", "element3");
        jedis.pfadd(sourceKey2, "element3", "element4", "element5");

        // Test PFMERGE
        String result = jedis.pfmerge(destKey, sourceKey1, sourceKey2);
        assertEquals("OK", result, "PFMERGE should return OK");

        // Verify merged count
        long count = jedis.pfcount(destKey);
        assertEquals(5, count, "Merged HyperLogLog should have 5 unique elements");
    }

    @Test
    void ping_command() {
        // Test PING
        String result = jedis.ping();
        assertEquals("PONG", result, "PING should return PONG");

        // Test PING with message
        String message = "test_message";
        String pingWithMessage = jedis.ping(message);
        assertEquals(message, pingWithMessage, "PING with message should return the message");
    }

    @Test
    void send_command_basic() {
        String key = UUID.randomUUID().toString();
        String value = "test_value";

        // Test SET command via sendCommand with byte arrays
        Object setResult = jedis.sendCommand(Protocol.Command.SET, key.getBytes(), value.getBytes());
        assertEquals("OK", setResult.toString(), "SET via sendCommand should return OK");

        // Test GET command via sendCommand with byte arrays
        Object getResult = jedis.sendCommand(Protocol.Command.GET, key.getBytes());
        assertNotNull(getResult, "GET via sendCommand should return the value");
        // Note: GLIDE may return different types (String vs byte[]), so we convert to string for
        // comparison
        assertEquals(
                value, getResult.toString(), "GET via sendCommand should return the correct value");

        // Test PING command via sendCommand with no arguments
        Object pingResult = jedis.sendCommand(Protocol.Command.PING);
        assertEquals("PONG", pingResult.toString(), "PING via sendCommand should return PONG");
    }

    @Test
    void send_command_string_args() {
        String key = UUID.randomUUID().toString();
        String value = "string_value";

        // Test SET command via sendCommand with string arguments
        Object setResult = jedis.sendCommand(Protocol.Command.SET, key, value);
        assertEquals("OK", setResult.toString(), "SET via sendCommand with strings should return OK");

        // Test GET command via sendCommand with string arguments
        Object getResult = jedis.sendCommand(Protocol.Command.GET, key);
        assertEquals(
                value,
                getResult.toString(),
                "GET via sendCommand with strings should return the correct value");

        // Test EXISTS command via sendCommand with string arguments
        Object existsResult = jedis.sendCommand(Protocol.Command.EXISTS, key);
        assertEquals(
                1L,
                ((Number) existsResult).longValue(),
                "EXISTS via sendCommand should return 1 for existing key");
    }

    @Test
    void send_command_multiple_args() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();
        String value1 = "value1";
        String value2 = "value2";
        String value3 = "value3";

        // Set up test data using regular methods
        jedis.set(key1, value1);
        jedis.set(key2, value2);
        jedis.set(key3, value3);

        // Test MGET command via sendCommand
        Object mgetResult = jedis.sendCommand(Protocol.Command.MGET, key1, key2, key3);
        assertArrayContains(mgetResult, new String[] {value1, value2, value3}, "MGET via sendCommand");

        // Test DEL command via sendCommand with multiple keys
        Object delResult = jedis.sendCommand(Protocol.Command.DEL, key1, key2, key3);
        Long delCount = assertLongResult(delResult, "DEL via sendCommand");
        assertEquals(3L, delCount, "DEL via sendCommand should return 3 for three deleted keys");
    }

    @Test
    void send_command_numeric() {
        String key = UUID.randomUUID().toString();

        // Test INCR command via sendCommand
        Object incrResult = jedis.sendCommand(Protocol.Command.INCR, key);
        Long incrValue = assertLongResult(incrResult, "INCR via sendCommand");
        assertEquals(1L, incrValue, "INCR via sendCommand should return 1 for first increment");

        // Test INCRBY command via sendCommand
        Object incrbyResult = jedis.sendCommand(Protocol.Command.INCRBY, key, "5");
        Long incrbyValue = assertLongResult(incrbyResult, "INCRBY via sendCommand");
        assertEquals(6L, incrbyValue, "INCRBY via sendCommand should return 6 (1+5)");

        // Test DECR command via sendCommand
        Object decrResult = jedis.sendCommand(Protocol.Command.DECR, key);
        Long decrValue = assertLongResult(decrResult, "DECR via sendCommand");
        assertEquals(5L, decrValue, "DECR via sendCommand should return 5 (6-1)");
    }

    @Test
    void send_command_expiration() {
        String key = UUID.randomUUID().toString();
        String value = "expire_value";

        // Set up test data
        jedis.set(key, value);

        // Test EXPIRE command via sendCommand FIRST
        Object expireResult = jedis.sendCommand(Protocol.Command.EXPIRE, key, "60");
        // NOTE: GLIDE returns Boolean for EXPIRE, original Jedis returns Long
        // This difference needs to be fixed in the compatibility layer later
        if (expireResult instanceof Boolean) {
            assertTrue(
                    (Boolean) expireResult,
                    "EXPIRE via sendCommand should return true for successful expiration");
        } else {
            Long expireStatus = assertLongResult(expireResult, "EXPIRE via sendCommand");
            assertEquals(
                    1L, expireStatus, "EXPIRE via sendCommand should return 1 for successful expiration");
        }

        // Test TTL command via sendCommand AFTER setting expiration
        Object ttlResult = jedis.sendCommand(Protocol.Command.TTL, key);
        Long ttl = assertLongResult(ttlResult, "TTL via sendCommand");
        assertTrue(
                ttl > 0 && ttl <= 60,
                "TTL via sendCommand should return a value between 1 and 60, got: " + ttl);

        // Test PERSIST command via sendCommand
        Object persistResult = jedis.sendCommand(Protocol.Command.PERSIST, key);
        // NOTE: GLIDE returns Boolean for PERSIST, original Jedis returns Long
        // This difference needs to be fixed in the compatibility layer later
        if (persistResult instanceof Boolean) {
            assertTrue(
                    (Boolean) persistResult,
                    "PERSIST via sendCommand should return true for successful persist");
        } else {
            Long persistStatus = assertLongResult(persistResult, "PERSIST via sendCommand");
            assertEquals(
                    1L, persistStatus, "PERSIST via sendCommand should return 1 for successful persist");
        }

        // Verify TTL is now -1 (no expiration)
        Object ttlAfterPersist = jedis.sendCommand(Protocol.Command.TTL, key);
        Long ttlAfterPersistValue = assertLongResult(ttlAfterPersist, "TTL after PERSIST");
        assertEquals(-1L, ttlAfterPersistValue, "TTL should be -1 after PERSIST");
    }

    @Test
    void send_command_hash() {
        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";

        // Test HSET command via sendCommand
        Object hsetResult = jedis.sendCommand(Protocol.Command.HSET, key, field1, value1);
        assertEquals(
                1L,
                ((Number) hsetResult).longValue(),
                "HSET via sendCommand should return 1 for new field");

        // Test HGET command via sendCommand
        Object hgetResult = jedis.sendCommand(Protocol.Command.HGET, key, field1);
        assertEquals(
                value1,
                hgetResult.toString(),
                "HGET via sendCommand should return the correct field value");

        // Test HMSET command via sendCommand (note: HMSET is deprecated but still supported)
        Object hmsetResult = jedis.sendCommand(Protocol.Command.HMSET, key, field2, value2);
        assertEquals("OK", hmsetResult.toString(), "HMSET via sendCommand should return OK");

        // Test HGETALL command via sendCommand
        Object hgetallResult = jedis.sendCommand(Protocol.Command.HGETALL, key);
        assertNotNull(hgetallResult, "HGETALL via sendCommand should return all fields and values");

        // Original Jedis HGETALL returns Map<String, String>
        @SuppressWarnings("unchecked")
        Map<Object, Object> hgetallMap = (Map<Object, Object>) hgetallResult;
        assertEquals(2, hgetallMap.size(), "HGETALL should return 2 field-value pairs");

        // Convert keys and values to strings for comparison
        boolean foundField1 = false, foundField2 = false;
        for (Map.Entry<Object, Object> entry : hgetallMap.entrySet()) {
            String key_str = entry.getKey().toString();
            String value_str = entry.getValue().toString();

            if (field1.equals(key_str) && value1.equals(value_str)) {
                foundField1 = true;
            } else if (field2.equals(key_str) && value2.equals(value_str)) {
                foundField2 = true;
            }
        }

        assertTrue(foundField1, "HGETALL should contain field1 -> value1 mapping");
        assertTrue(foundField2, "HGETALL should contain field2 -> value2 mapping");
    }

    @Test
    void send_command_list() {
        String key = UUID.randomUUID().toString();
        String value1 = "item1";
        String value2 = "item2";
        String value3 = "item3";

        // Test LPUSH command via sendCommand
        Object lpushResult = jedis.sendCommand(Protocol.Command.LPUSH, key, value1, value2);
        Long lpushCount = assertLongResult(lpushResult, "LPUSH via sendCommand");
        assertEquals(2L, lpushCount, "LPUSH via sendCommand should return 2 for list length");

        // Test RPUSH command via sendCommand
        Object rpushResult = jedis.sendCommand(Protocol.Command.RPUSH, key, value3);
        Long rpushCount = assertLongResult(rpushResult, "RPUSH via sendCommand");
        assertEquals(3L, rpushCount, "RPUSH via sendCommand should return 3 for list length");

        // Test LLEN command via sendCommand
        Object llenResult = jedis.sendCommand(Protocol.Command.LLEN, key);
        Long llenCount = assertLongResult(llenResult, "LLEN via sendCommand");
        assertEquals(3L, llenCount, "LLEN via sendCommand should return 3 for list length");

        // Test LRANGE command via sendCommand
        // TODO: Fix compatibility layer to add response transformation to match original Jedis
        Object lrangeResult = jedis.sendCommand(Protocol.Command.LRANGE, key, "0", "-1");
        assertNotNull(lrangeResult, "LRANGE via sendCommand should return list elements");
        Object[] lrangeArray = (Object[]) lrangeResult;
        assertEquals(3, lrangeArray.length, "LRANGE should return 3 elements");

        // Convert to strings and check all values are present (order-independent)
        Set<String> resultSet = new HashSet<>();
        for (Object item : lrangeArray) {
            resultSet.add(item.toString());
        }
        assertTrue(resultSet.contains(value1), "LRANGE should contain value1");
        assertTrue(resultSet.contains(value2), "LRANGE should contain value2");
        assertTrue(resultSet.contains(value3), "LRANGE should contain value3");
    }

    @Test
    void send_command_set() {
        String key = UUID.randomUUID().toString();
        String member1 = "member1";
        String member2 = "member2";
        String member3 = "member3";

        // Test SADD command via sendCommand
        Object saddResult = jedis.sendCommand(Protocol.Command.SADD, key, member1, member2, member3);
        assertEquals(
                3L,
                ((Number) saddResult).longValue(),
                "SADD via sendCommand should return 3 for three new members");

        // Test SCARD command via sendCommand
        Object scardResult = jedis.sendCommand(Protocol.Command.SCARD, key);
        assertEquals(
                3L,
                ((Number) scardResult).longValue(),
                "SCARD via sendCommand should return 3 for set cardinality");

        // Test SISMEMBER command via sendCommand
        Object sismemberResult = jedis.sendCommand(Protocol.Command.SISMEMBER, key, member1);
        assertNotNull(sismemberResult, "SISMEMBER via sendCommand should return membership result");
        // SISMEMBER can return Boolean or Number, handle both
        if (sismemberResult instanceof Boolean) {
            assertTrue(
                    (Boolean) sismemberResult,
                    "SISMEMBER via sendCommand should return true for existing member");
        } else if (sismemberResult instanceof Number) {
            assertEquals(
                    1L,
                    ((Number) sismemberResult).longValue(),
                    "SISMEMBER via sendCommand should return 1 for existing member");
        } else {
            assertEquals(
                    "1",
                    sismemberResult.toString(),
                    "SISMEMBER via sendCommand should return 1 as string for existing member");
        }

        // Test SMEMBERS command via sendCommand
        Object smembersResult = jedis.sendCommand(Protocol.Command.SMEMBERS, key);
        assertNotNull(smembersResult, "SMEMBERS via sendCommand should return all set members");

        // Original Jedis SMEMBERS returns Set<String>, but sendCommand might return different
        // collection types
        // Convert to Set for validation
        Set<String> resultSet = new HashSet<>();
        if (smembersResult instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<Object> smembersSet = (Set<Object>) smembersResult;
            for (Object member : smembersSet) {
                resultSet.add(member.toString());
            }
        } else if (smembersResult instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> smembersList = (List<Object>) smembersResult;
            for (Object member : smembersList) {
                resultSet.add(member.toString());
            }
        } else if (smembersResult instanceof Object[]) {
            Object[] smembersArray = (Object[]) smembersResult;
            for (Object member : smembersArray) {
                resultSet.add(member.toString());
            }
        }

        assertEquals(3, resultSet.size(), "SMEMBERS should return 3 members");
        assertTrue(resultSet.contains(member1), "SMEMBERS result should contain member1");
        assertTrue(resultSet.contains(member2), "SMEMBERS result should contain member2");
        assertTrue(resultSet.contains(member3), "SMEMBERS result should contain member3");
    }

    @Test
    void send_command_binary_data() {
        String key = UUID.randomUUID().toString();
        byte[] binaryValue = {0x00, 0x01, 0x02, 0x03, (byte) 0xFF};

        // Test SET command via sendCommand with binary data
        Object setResult = jedis.sendCommand(Protocol.Command.SET, key.getBytes(), binaryValue);
        assertEquals(
                "OK", setResult.toString(), "SET via sendCommand with binary data should return OK");

        // Test GET command via sendCommand with binary data
        Object getResult = jedis.sendCommand(Protocol.Command.GET, key.getBytes());
        assertNotNull(getResult, "GET via sendCommand with binary data should return the binary value");

        // For binary data, we can't easily compare the exact bytes due to potential encoding
        // differences
        // in GLIDE's response processing, but we can verify that we got a non-null response
        // and that the key exists
        Object existsResult = jedis.sendCommand(Protocol.Command.EXISTS, key.getBytes());
        assertEquals(1L, ((Number) existsResult).longValue(), "Key with binary data should exist");
    }

    @Test
    void send_command_optional_args() {
        String key = UUID.randomUUID().toString();
        String value = "optional_value";

        // Test SET command with optional arguments (EX for expiration)
        Object setExResult = jedis.sendCommand(Protocol.Command.SET, key, value, "EX", "60");
        assertEquals(
                "OK", setExResult.toString(), "SET via sendCommand with EX option should return OK");

        // Verify the key was set with expiration
        Object ttlResult = jedis.sendCommand(Protocol.Command.TTL, key);
        long ttl = ((Number) ttlResult).longValue();
        assertTrue(ttl > 0 && ttl <= 60, "TTL should be between 1 and 60 seconds");

        // Verify the value was set correctly
        Object getResult = jedis.sendCommand(Protocol.Command.GET, key);
        assertEquals(value, getResult.toString(), "GET should return the correct value");

        // Test SET command with NX option (only if not exists)
        String key2 = UUID.randomUUID().toString();
        Object setNxResult = jedis.sendCommand(Protocol.Command.SET, key2, value, "NX");
        assertEquals(
                "OK",
                setNxResult.toString(),
                "SET via sendCommand with NX option should return OK for new key");

        // Test SET command with NX option on existing key (should return null)
        Object setNxExistingResult = jedis.sendCommand(Protocol.Command.SET, key2, "new_value", "NX");
        assertNull(setNxExistingResult, "SET with NX on existing key should return null");

        // Verify the original value wasn't changed
        Object getKey2Result = jedis.sendCommand(Protocol.Command.GET, key2);
        assertEquals(
                value, getKey2Result.toString(), "Original value should be unchanged after failed NX");
    }

    // Hash Commands Tests

    @Test
    void hset_and_hget_command() {
        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";

        // Test HSET - single field
        long result = jedis.hset(key, field1, value1);
        assertEquals(1, result, "HSET should return 1 for new field");

        // Test HGET
        String getValue = jedis.hget(key, field1);
        assertEquals(value1, getValue, "HGET should return correct value");

        // Test HSET - existing field
        result = jedis.hset(key, field1, "new_value");
        assertEquals(0, result, "HSET should return 0 for existing field");

        // Test HSET - multiple fields
        Map<String, String> hash = new HashMap<>();
        hash.put(field1, value1);
        hash.put(field2, value2);
        result = jedis.hset(key, hash);
        assertEquals(1, result, "HSET with map should return number of new fields");

        // Test HGET on non-existing field
        String nonExistentValue = jedis.hget(key, "nonexistent");
        assertNull(nonExistentValue, "HGET should return null for non-existing field");
    }

    @Test
    void hdel_command() {
        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String field3 = "field3";

        // Set up test data
        jedis.hset(key, field1, "value1");
        jedis.hset(key, field2, "value2");
        jedis.hset(key, field3, "value3");

        // Test HDEL - single field
        long result = jedis.hdel(key, field1);
        assertEquals(1, result, "HDEL should return 1 for existing field");

        // Verify field is deleted
        assertNull(jedis.hget(key, field1), "Deleted field should not exist");

        // Test HDEL - multiple fields
        result = jedis.hdel(key, field2, field3);
        assertEquals(2, result, "HDEL should return 2 for two existing fields");

        // Test HDEL - non-existing field
        result = jedis.hdel(key, "nonexistent");
        assertEquals(0, result, "HDEL should return 0 for non-existing field");
    }

    @Test
    void hexists_command() {
        String key = UUID.randomUUID().toString();
        String field = "testfield";

        // Test HEXISTS on non-existing hash
        boolean exists = jedis.hexists(key, field);
        assertFalse(exists, "HEXISTS should return false for non-existing hash");

        // Set up test data
        jedis.hset(key, field, "value");

        // Test HEXISTS on existing field
        exists = jedis.hexists(key, field);
        assertTrue(exists, "HEXISTS should return true for existing field");

        // Test HEXISTS on non-existing field
        exists = jedis.hexists(key, "nonexistent");
        assertFalse(exists, "HEXISTS should return false for non-existing field");
    }

    @Test
    void hlen_command() {
        String key = UUID.randomUUID().toString();

        // Test HLEN on non-existing hash
        long length = jedis.hlen(key);
        assertEquals(0, length, "HLEN should return 0 for non-existing hash");

        // Set up test data
        jedis.hset(key, "field1", "value1");
        jedis.hset(key, "field2", "value2");
        jedis.hset(key, "field3", "value3");

        // Test HLEN
        length = jedis.hlen(key);
        assertEquals(3, length, "HLEN should return correct number of fields");

        // Delete a field and test again
        jedis.hdel(key, "field1");
        length = jedis.hlen(key);
        assertEquals(2, length, "HLEN should return updated count after deletion");
    }

    @Test
    void hkeys_and_hvals_command() {
        String key = UUID.randomUUID().toString();
        Map<String, String> testData = new HashMap<>();
        testData.put("field1", "value1");
        testData.put("field2", "value2");
        testData.put("field3", "value3");

        // Set up test data
        jedis.hset(key, testData);

        // Test HKEYS
        Set<String> keys = jedis.hkeys(key);
        assertEquals(3, keys.size(), "HKEYS should return all field names");
        assertTrue(keys.containsAll(testData.keySet()), "HKEYS should contain all field names");

        // Test HVALS
        List<String> values = jedis.hvals(key);
        assertEquals(3, values.size(), "HVALS should return all values");
        assertTrue(values.containsAll(testData.values()), "HVALS should contain all values");
    }

    @Test
    void hgetall_command() {
        String key = UUID.randomUUID().toString();
        Map<String, String> testData = new HashMap<>();
        testData.put("field1", "value1");
        testData.put("field2", "value2");
        testData.put("field3", "value3");

        // Test HGETALL on non-existing hash
        Map<String, String> result = jedis.hgetAll(key);
        assertTrue(result.isEmpty(), "HGETALL should return empty map for non-existing hash");

        // Set up test data
        jedis.hset(key, testData);

        // Test HGETALL
        result = jedis.hgetAll(key);
        assertEquals(testData.size(), result.size(), "HGETALL should return all field-value pairs");
        assertEquals(testData, result, "HGETALL should return correct field-value pairs");
    }

    @Test
    void hmget_and_hmset_command() {
        String key = UUID.randomUUID().toString();
        Map<String, String> testData = new HashMap<>();
        testData.put("field1", "value1");
        testData.put("field2", "value2");
        testData.put("field3", "value3");

        // Test HMSET
        String result = jedis.hmset(key, testData);
        assertEquals("OK", result, "HMSET should return OK"); // HMSET typically returns "OK"

        // Test HMGET - existing fields
        List<String> values = jedis.hmget(key, "field1", "field2", "field3");
        assertEquals(3, values.size(), "HMGET should return values for all fields");
        assertEquals("value1", values.get(0), "HMGET should return correct value for field1");
        assertEquals("value2", values.get(1), "HMGET should return correct value for field2");
        assertEquals("value3", values.get(2), "HMGET should return correct value for field3");

        // Test HMGET - mix of existing and non-existing fields
        values = jedis.hmget(key, "field1", "nonexistent", "field2");
        assertEquals(3, values.size(), "HMGET should return list with same size as requested fields");
        assertEquals("value1", values.get(0), "HMGET should return correct value for existing field");
        assertNull(values.get(1), "HMGET should return null for non-existing field");
        assertEquals("value2", values.get(2), "HMGET should return correct value for existing field");
    }

    @Test
    void hsetnx_command() {
        String key = UUID.randomUUID().toString();
        String field = "testfield";
        String value1 = "value1";
        String value2 = "value2";

        // Test HSETNX on new field
        long result = jedis.hsetnx(key, field, value1);
        assertEquals(1, result, "HSETNX should return 1 for new field");
        assertEquals(value1, jedis.hget(key, field), "Field should have correct value");

        // Test HSETNX on existing field
        result = jedis.hsetnx(key, field, value2);
        assertEquals(0, result, "HSETNX should return 0 for existing field");
        assertEquals(value1, jedis.hget(key, field), "Field should retain original value");
    }

    @Test
    void hincrby_command() {
        String key = UUID.randomUUID().toString();
        String field = "counter";

        // Test HINCRBY on non-existing field
        long result = jedis.hincrBy(key, field, 5);
        assertEquals(5, result, "HINCRBY should return 5 for new field");
        assertEquals("5", jedis.hget(key, field), "Field should have value 5");

        // Test HINCRBY on existing field
        result = jedis.hincrBy(key, field, 3);
        assertEquals(8, result, "HINCRBY should return 8");
        assertEquals("8", jedis.hget(key, field), "Field should have value 8");

        // Test HINCRBY with negative value
        result = jedis.hincrBy(key, field, -2);
        assertEquals(6, result, "HINCRBY should return 6");
        assertEquals("6", jedis.hget(key, field), "Field should have value 6");
    }

    @Test
    void hincrbyfloat_command() {
        String key = UUID.randomUUID().toString();
        String field = "float_counter";

        // Test HINCRBYFLOAT on non-existing field
        double result = jedis.hincrByFloat(key, field, 2.5);
        assertEquals(2.5, result, 0.001, "HINCRBYFLOAT should return 2.5 for new field");
        assertEquals("2.5", jedis.hget(key, field), "Field should have value 2.5");

        // Test HINCRBYFLOAT on existing field
        result = jedis.hincrByFloat(key, field, 1.5);
        assertEquals(4.0, result, 0.001, "HINCRBYFLOAT should return 4.0");
        assertEquals("4", jedis.hget(key, field), "Field should have value 4");

        // Test HINCRBYFLOAT with negative value
        result = jedis.hincrByFloat(key, field, -0.5);
        assertEquals(3.5, result, 0.001, "HINCRBYFLOAT should return 3.5");
        assertEquals("3.5", jedis.hget(key, field), "Field should have value 3.5");
    }

    @Test
    void hstrlen_command() {
        String key = UUID.randomUUID().toString();
        String field = "testfield";
        String value = "Hello World";

        // Test HSTRLEN on non-existing field
        long length = jedis.hstrlen(key, field);
        assertEquals(0, length, "HSTRLEN should return 0 for non-existing field");

        // Set up test data
        jedis.hset(key, field, value);

        // Test HSTRLEN on existing field
        length = jedis.hstrlen(key, field);
        assertEquals(value.length(), length, "HSTRLEN should return correct string length");

        // Test HSTRLEN on empty field
        jedis.hset(key, "empty", "");
        length = jedis.hstrlen(key, "empty");
        assertEquals(0, length, "HSTRLEN should return 0 for empty field");
    }

    @Test
    void hrandfield_command() {
        String key = UUID.randomUUID().toString();
        Map<String, String> testData = new HashMap<>();
        testData.put("field1", "value1");
        testData.put("field2", "value2");
        testData.put("field3", "value3");
        testData.put("field4", "value4");
        testData.put("field5", "value5");

        // Set up test data
        jedis.hset(key, testData);

        // Test HRANDFIELD - single field
        String randomField = jedis.hrandfield(key);
        assertNotNull(randomField, "HRANDFIELD should return a field");
        assertTrue(testData.containsKey(randomField), "HRANDFIELD should return existing field");

        // Test HRANDFIELD - multiple fields
        List<String> randomFields = jedis.hrandfield(key, 3);
        assertEquals(3, randomFields.size(), "HRANDFIELD should return requested number of fields");
        for (String field : randomFields) {
            assertTrue(testData.containsKey(field), "All returned fields should exist in hash");
        }

        // Test HRANDFIELD with values
        List<Map.Entry<String, String>> randomFieldsWithValues = jedis.hrandfieldWithValues(key, 2);
        assertEquals(
                2,
                randomFieldsWithValues.size(),
                "HRANDFIELD with values should return requested number of pairs");
        for (Map.Entry<String, String> entry : randomFieldsWithValues) {
            assertTrue(testData.containsKey(entry.getKey()), "Field should exist in hash");
            assertEquals(testData.get(entry.getKey()), entry.getValue(), "Value should match");
        }

        // Test HRANDFIELD on non-existing hash
        String nonExistentField = jedis.hrandfield(UUID.randomUUID().toString());
        assertNull(nonExistentField, "HRANDFIELD should return null for non-existing hash");
    }

    @Test
    void hscan_command() {
        String key = UUID.randomUUID().toString();
        Map<String, String> testData = new HashMap<>();

        // Create test data with predictable pattern
        for (int i = 0; i < 20; i++) {
            testData.put("field_" + i, "value_" + i);
        }
        jedis.hset(key, testData);

        // Test HSCAN - basic scan
        ScanResult<Map.Entry<String, String>> scanResult = jedis.hscan(key, "0");
        assertNotNull(scanResult, "HSCAN should return scan result");
        assertNotNull(scanResult.getResult(), "HSCAN should return field-value pairs");
        assertTrue(scanResult.getResult().size() > 0, "HSCAN should return some field-value pairs");

        // Test HSCAN with ScanParams
        ScanParams params = new ScanParams();
        params.match("field_1*");
        params.count(5);

        scanResult = jedis.hscan(key, "0", params);
        assertNotNull(scanResult, "HSCAN with params should return scan result");

        // Verify all returned fields match the pattern
        for (Map.Entry<String, String> entry : scanResult.getResult()) {
            assertTrue(
                    entry.getKey().startsWith("field_1"),
                    "All returned fields should match pattern field_1*");
        }

        // Test HSCANNOVALS - scan without values
        ScanResult<String> scanNoValsResult = jedis.hscanNoValues(key, "0");
        assertNotNull(scanNoValsResult, "HSCANNOVALS should return scan result");
        assertNotNull(scanNoValsResult.getResult(), "HSCANNOVALS should return field names");
        assertTrue(
                scanNoValsResult.getResult().size() > 0, "HSCANNOVALS should return some field names");

        // Verify all returned items are field names
        for (String field : scanNoValsResult.getResult()) {
            assertTrue(testData.containsKey(field), "All returned fields should exist in hash");
        }
    }

    @Test
    void hsetex_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.9.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "HSETEX command requires Valkey 7.9.0+ (not available in Valkey 8.x)");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";

        // Test HSETEX with expiration - single field
        HSetExParams params = HSetExParams.hSetExParams().ex(60); // 60 seconds
        long result = jedis.hsetex(key, params, field1, value1);
        assertEquals(1, result, "HSETEX should return 1 for new field");
        assertEquals(value1, jedis.hget(key, field1), "Field should have correct value");

        // Test HSETEX with FNX condition (field not exists)
        params = HSetExParams.hSetExParams().fnx().ex(30);
        result = jedis.hsetex(key, params, field1, "new_value");
        assertEquals(0, result, "HSETEX with FNX should return 0 for existing field");
        assertEquals(value1, jedis.hget(key, field1), "Field should retain original value");

        // Test HSETEX with FXX condition (field exists)
        params = HSetExParams.hSetExParams().fxx().px(30000); // 30 seconds in milliseconds
        result = jedis.hsetex(key, params, field1, "updated_value");
        assertEquals(
                1, result, "HSETEX with FXX should return 1 when successfully updating existing field");
        assertEquals("updated_value", jedis.hget(key, field1), "Field should have updated value");

        // Test HSETEX with multiple fields
        Map<String, String> hash = new HashMap<>();
        hash.put(field2, value2);
        hash.put("field3", "value3");

        params = HSetExParams.hSetExParams().ex(120);
        result = jedis.hsetex(key, params, hash);
        assertTrue(result >= 1, "HSETEX with map should return number of new fields");
        assertEquals(value2, jedis.hget(key, field2), "Field2 should have correct value");
    }

    @Test
    void hgetex_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.9.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "HGETEX command requires Valkey 7.9.0+ (not available in Valkey 8.x)");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";

        // Set up test data
        jedis.hset(key, field1, value1);
        jedis.hset(key, field2, value2);

        // Test HGETEX with expiration
        HGetExParams params = HGetExParams.hGetExParams().ex(60); // 60 seconds
        List<String> result = jedis.hgetex(key, params, field1, field2);
        assertEquals(2, result.size(), "HGETEX should return values for all fields");
        assertEquals(value1, result.get(0), "HGETEX should return correct value for field1");
        assertEquals(value2, result.get(1), "HGETEX should return correct value for field2");

        // Test HGETEX with persist
        params = HGetExParams.hGetExParams().persist();
        result = jedis.hgetex(key, params, field1);
        assertEquals(1, result.size(), "HGETEX should return one value");
        assertEquals(value1, result.get(0), "HGETEX should return correct value");

        // Test HGETEX on non-existing field
        result = jedis.hgetex(key, params, "nonexistent");
        assertEquals(1, result.size(), "HGETEX should return list with one element");
        assertNull(result.get(0), "HGETEX should return null for non-existing field");
    }

    @Test
    void hgetdel_command() {
        assumeTrue(SERVER_VERSION.isGreaterThan("9.0.0"), "HGETDEL command requires Valkey 9.0.0+");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String field3 = "field3";
        String value1 = "value1";
        String value2 = "value2";
        String value3 = "value3";

        // Set up test data
        jedis.hset(key, field1, value1);
        jedis.hset(key, field2, value2);
        jedis.hset(key, field3, value3);

        // Test HGETDEL - single field
        List<String> result = jedis.hgetdel(key, field1);
        assertEquals(1, result.size(), "HGETDEL should return one value");
        assertEquals(value1, result.get(0), "HGETDEL should return correct value");
        assertNull(jedis.hget(key, field1), "Field should be deleted after HGETDEL");

        // Test HGETDEL - multiple fields
        result = jedis.hgetdel(key, field2, field3);
        assertEquals(2, result.size(), "HGETDEL should return values for all fields");
        assertEquals(value2, result.get(0), "HGETDEL should return correct value for field2");
        assertEquals(value3, result.get(1), "HGETDEL should return correct value for field3");
        assertNull(jedis.hget(key, field2), "Field2 should be deleted after HGETDEL");
        assertNull(jedis.hget(key, field3), "Field3 should be deleted after HGETDEL");

        // Test HGETDEL on non-existing field
        result = jedis.hgetdel(key, "nonexistent");
        assertEquals(1, result.size(), "HGETDEL should return list with one element");
        assertNull(result.get(0), "HGETDEL should return null for non-existing field");
    }

    @Test
    void hexpire_and_httl_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.4.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "Hash field expiration commands require Valkey 7.4.0+ (not available in Valkey 8.x)");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";

        // Set up test data
        jedis.hset(key, field1, value1);
        jedis.hset(key, field2, value2);

        // Test HEXPIRE - set expiration in seconds
        List<Long> result = jedis.hexpire(key, 60, field1, field2);
        assertEquals(2, result.size(), "HEXPIRE should return results for all fields");
        assertEquals(
                Long.valueOf(1), result.get(0), "HEXPIRE should return 1 for successful expiration");
        assertEquals(
                Long.valueOf(1), result.get(1), "HEXPIRE should return 1 for successful expiration");

        // Test HTTL - get TTL in seconds
        List<Long> ttlResult = jedis.httl(key, field1, field2);
        assertEquals(2, ttlResult.size(), "HTTL should return TTL for all fields");
        assertTrue(ttlResult.get(0) > 0 && ttlResult.get(0) <= 60, "TTL should be positive and <= 60");
        assertTrue(ttlResult.get(1) > 0 && ttlResult.get(1) <= 60, "TTL should be positive and <= 60");

        // Test HEXPIRE with condition
        ExpiryOption condition = ExpiryOption.GT; // Greater than current expiration
        result = jedis.hexpire(key, 120, condition, field1);
        assertEquals(1, result.size(), "HEXPIRE with condition should return one result");
        assertEquals(Long.valueOf(1), result.get(0), "HEXPIRE with GT condition should succeed");

        // Test HTTL on non-existing field
        ttlResult = jedis.httl(key, "nonexistent");
        assertEquals(1, ttlResult.size(), "HTTL should return one result");
        assertEquals(
                Long.valueOf(-2), ttlResult.get(0), "HTTL should return -2 for non-existing field");
    }

    @Test
    void hpexpire_and_hpttl_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.4.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "Hash field expiration commands require Valkey 7.4.0+ (not available in Valkey 8.x)");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";

        // Set up test data
        jedis.hset(key, field1, value1);
        jedis.hset(key, field2, value2);

        // Test HPEXPIRE - set expiration in milliseconds
        List<Long> result = jedis.hpexpire(key, 60000, field1, field2); // 60 seconds in milliseconds
        assertEquals(2, result.size(), "HPEXPIRE should return results for all fields");
        assertEquals(
                Long.valueOf(1), result.get(0), "HPEXPIRE should return 1 for successful expiration");
        assertEquals(
                Long.valueOf(1), result.get(1), "HPEXPIRE should return 1 for successful expiration");

        // Test HPTTL - get TTL in milliseconds
        List<Long> pttlResult = jedis.hpttl(key, field1, field2);
        assertEquals(2, pttlResult.size(), "HPTTL should return TTL for all fields");
        assertTrue(
                pttlResult.get(0) > 0 && pttlResult.get(0) <= 60000,
                "PTTL should be positive and <= 60000");
        assertTrue(
                pttlResult.get(1) > 0 && pttlResult.get(1) <= 60000,
                "PTTL should be positive and <= 60000");

        // Test HPEXPIRE with condition
        ExpiryOption condition = ExpiryOption.LT; // Less than current expiration
        result = jedis.hpexpire(key, 30000, condition, field1);
        assertEquals(1, result.size(), "HPEXPIRE with condition should return one result");
        assertEquals(Long.valueOf(1), result.get(0), "HPEXPIRE with LT condition should succeed");

        // Test HPTTL on non-existing field
        pttlResult = jedis.hpttl(key, "nonexistent");
        assertEquals(1, pttlResult.size(), "HPTTL should return one result");
        assertEquals(
                Long.valueOf(-2), pttlResult.get(0), "HPTTL should return -2 for non-existing field");
    }

    @Test
    void hexpireat_and_hexpiretime_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.4.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "Hash field expiration commands require Valkey 7.4.0+ (not available in Valkey 8.x)");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String value1 = "value1";

        // Set up test data
        jedis.hset(key, field1, value1);

        // Test HEXPIREAT - set expiration at Unix timestamp (seconds)
        long futureTimestamp = System.currentTimeMillis() / 1000 + 120; // 2 minutes from now
        List<Long> result = jedis.hexpireAt(key, futureTimestamp, field1);
        assertEquals(1, result.size(), "HEXPIREAT should return one result");
        assertEquals(
                Long.valueOf(1), result.get(0), "HEXPIREAT should return 1 for successful expiration");

        // Test HEXPIRETIME - get expiration time
        List<Long> expireTimeResult = jedis.hexpireTime(key, field1);
        assertEquals(1, expireTimeResult.size(), "HEXPIRETIME should return one result");
        assertEquals(
                futureTimestamp,
                expireTimeResult.get(0).longValue(),
                "HEXPIRETIME should return correct timestamp");

        // Test HEXPIREAT with condition
        ExpiryOption condition = ExpiryOption.XX; // Only if field has expiration
        long newTimestamp = futureTimestamp + 60; // 1 minute later
        result = jedis.hexpireAt(key, newTimestamp, condition, field1);
        assertEquals(1, result.size(), "HEXPIREAT with condition should return one result");
        assertEquals(Long.valueOf(1), result.get(0), "HEXPIREAT with XX condition should succeed");
    }

    @Test
    void hpexpireat_and_hpexpiretime_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.4.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "Hash field expiration commands require Valkey 7.4.0+ (not available in Valkey 8.x)");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String value1 = "value1";

        // Set up test data
        jedis.hset(key, field1, value1);

        // Test HPEXPIREAT - set expiration at Unix timestamp (milliseconds)
        long futureTimestamp = System.currentTimeMillis() + 120000; // 2 minutes from now
        List<Long> result = jedis.hpexpireAt(key, futureTimestamp, field1);
        assertEquals(1, result.size(), "HPEXPIREAT should return one result");
        assertEquals(
                Long.valueOf(1), result.get(0), "HPEXPIREAT should return 1 for successful expiration");

        // Test HPEXPIRETIME - get expiration time in milliseconds
        List<Long> pexpireTimeResult = jedis.hpexpireTime(key, field1);
        assertEquals(1, pexpireTimeResult.size(), "HPEXPIRETIME should return one result");
        assertEquals(
                futureTimestamp,
                pexpireTimeResult.get(0).longValue(),
                "HPEXPIRETIME should return correct timestamp");

        // Test HPEXPIREAT with condition
        ExpiryOption condition = ExpiryOption.NX; // Only if field has no expiration
        String field2 = "field2";
        jedis.hset(key, field2, "value2");

        result = jedis.hpexpireAt(key, futureTimestamp, condition, field2);
        assertEquals(1, result.size(), "HPEXPIREAT with condition should return one result");
        assertEquals(Long.valueOf(1), result.get(0), "HPEXPIREAT with NX condition should succeed");
    }

    @Test
    void hpersist_command() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.4.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "Hash field expiration commands require Valkey 7.4.0+ (not available in Valkey 8.x)");

        String key = UUID.randomUUID().toString();
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";

        // Set up test data with expiration
        jedis.hset(key, field1, value1);
        jedis.hset(key, field2, value2);
        jedis.hexpire(key, 60, field1, field2);

        // Verify fields have expiration
        List<Long> ttlResult = jedis.httl(key, field1, field2);
        assertTrue(ttlResult.get(0) > 0, "Field1 should have TTL");
        assertTrue(ttlResult.get(1) > 0, "Field2 should have TTL");

        // Test HPERSIST - remove expiration
        List<Long> result = jedis.hpersist(key, field1, field2);
        assertEquals(2, result.size(), "HPERSIST should return results for all fields");
        assertEquals(Long.valueOf(1), result.get(0), "HPERSIST should return 1 for successful persist");
        assertEquals(Long.valueOf(1), result.get(1), "HPERSIST should return 1 for successful persist");

        // Verify fields no longer have expiration
        ttlResult = jedis.httl(key, field1, field2);
        assertEquals(Long.valueOf(-1), ttlResult.get(0), "Field1 should have no expiration");
        assertEquals(Long.valueOf(-1), ttlResult.get(1), "Field2 should have no expiration");

        // Test HPERSIST on field without expiration
        result = jedis.hpersist(key, field1);
        assertEquals(1, result.size(), "HPERSIST should return one result");
        assertEquals(
                Long.valueOf(-1), result.get(0), "HPERSIST should return -1 for field without expiration");

        // Test HPERSIST on non-existing field
        result = jedis.hpersist(key, "nonexistent");
        assertEquals(1, result.size(), "HPERSIST should return one result");
        assertEquals(
                Long.valueOf(-2), result.get(0), "HPERSIST should return -2 for non-existing field");
    }

    @Test
    void hash_commands_binary() {
        byte[] key = (UUID.randomUUID().toString()).getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();

        // Test HSET and HGET - binary
        long result = jedis.hset(key, field1, value1);
        assertEquals(1, result, "Binary HSET should return 1 for new field");

        byte[] getValue = jedis.hget(key, field1);
        assertArrayEquals(value1, getValue, "Binary HGET should return correct value");

        // Test HSET with map - binary
        Map<byte[], byte[]> hash = new HashMap<>();
        hash.put(field1, value1);
        hash.put(field2, value2);
        result = jedis.hset(key, hash);
        assertEquals(1, result, "Binary HSET with map should return number of new fields");

        // Test HMGET - binary
        List<byte[]> values = jedis.hmget(key, field1, field2);
        assertEquals(2, values.size(), "Binary HMGET should return values for all fields");
        assertArrayEquals(value1, values.get(0), "Binary HMGET should return correct value for field1");
        assertArrayEquals(value2, values.get(1), "Binary HMGET should return correct value for field2");

        // Test HGETALL - binary
        Map<byte[], byte[]> allFields = jedis.hgetAll(key);
        assertEquals(2, allFields.size(), "Binary HGETALL should return all field-value pairs");

        // Check if field1 and field2 exist by comparing byte arrays content
        boolean foundField1 = false, foundField2 = false;
        for (Map.Entry<byte[], byte[]> entry : allFields.entrySet()) {
            if (Arrays.equals(entry.getKey(), field1)) {
                foundField1 = true;
                assertArrayEquals(
                        value1, entry.getValue(), "Binary HGETALL should have correct value for field1");
            } else if (Arrays.equals(entry.getKey(), field2)) {
                foundField2 = true;
                assertArrayEquals(
                        value2, entry.getValue(), "Binary HGETALL should have correct value for field2");
            }
        }
        assertTrue(foundField1, "Binary HGETALL should contain field1");
        assertTrue(foundField2, "Binary HGETALL should contain field2");

        // Test HKEYS and HVALS - binary
        Set<byte[]> keys = jedis.hkeys(key);
        assertEquals(2, keys.size(), "Binary HKEYS should return all field names");

        List<byte[]> vals = jedis.hvals(key);
        assertEquals(2, vals.size(), "Binary HVALS should return all values");

        // Test HEXISTS - binary
        boolean exists = jedis.hexists(key, field1);
        assertTrue(exists, "Binary HEXISTS should return true for existing field");

        // Test HDEL - binary
        result = jedis.hdel(key, field1);
        assertEquals(1, result, "Binary HDEL should return 1 for existing field");

        // Test HLEN - binary
        long length = jedis.hlen(key);
        assertEquals(1, length, "Binary HLEN should return correct count");
    }

    @Test
    void hash_commands_binary_with_expiration() {
        assumeTrue(
                SERVER_VERSION.isGreaterThanOrEqualTo("7.4.0")
                        && !SERVER_VERSION.toString().startsWith("8."),
                "Hash field expiration commands require Valkey 7.4.0+ (not available in Valkey 8.x)");

        byte[] key = (UUID.randomUUID().toString()).getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();

        // Set up test data
        jedis.hset(key, field1, value1);
        jedis.hset(key, field2, value2);

        // Test HEXPIRE - binary
        List<Long> result = jedis.hexpire(key, 60, field1, field2);
        assertEquals(2, result.size(), "Binary HEXPIRE should return results for all fields");
        assertEquals(
                Long.valueOf(1), result.get(0), "Binary HEXPIRE should return 1 for successful expiration");

        // Test HTTL - binary
        List<Long> ttlResult = jedis.httl(key, field1, field2);
        assertEquals(2, ttlResult.size(), "Binary HTTL should return TTL for all fields");
        assertTrue(ttlResult.get(0) > 0, "Binary TTL should be positive");

        // Test HPEXPIRE - binary
        result = jedis.hpexpire(key, 120000, field1); // 2 minutes in milliseconds
        assertEquals(1, result.size(), "Binary HPEXPIRE should return one result");
        assertEquals(
                Long.valueOf(1),
                result.get(0),
                "Binary HPEXPIRE should return 1 for successful expiration");

        // Test HPTTL - binary
        List<Long> pttlResult = jedis.hpttl(key, field1);
        assertEquals(1, pttlResult.size(), "Binary HPTTL should return one result");
        assertTrue(pttlResult.get(0) > 0, "Binary PTTL should be positive");

        // Test HPERSIST - binary
        result = jedis.hpersist(key, field1, field2);
        assertEquals(2, result.size(), "Binary HPERSIST should return results for all fields");
        assertEquals(
                Long.valueOf(1), result.get(0), "Binary HPERSIST should return 1 for successful persist");
    }

    @Test
    void hash_commands_binary_newer() {
        // Hash field expiration commands (HSETEX, HGETEX) are available in:
        // - Valkey 9.0.0+ (HSETEX, HGETEX only - HGETDEL not available)
        assumeTrue(
                SERVER_VERSION.isGreaterThan("9.0.0"),
                "Newer hash commands require Valkey 9.0.0 and HGETDEL requires Valkey greater than 9");

        byte[] key = (UUID.randomUUID().toString()).getBytes();
        byte[] field1 = "field1".getBytes();
        byte[] field2 = "field2".getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();

        // Test HSETEX - binary
        HSetExParams params = HSetExParams.hSetExParams().ex(60);
        long result = jedis.hsetex(key, params, field1, value1);
        assertEquals(1, result, "Binary HSETEX should return 1 for new field");

        // Test HSETEX with map - binary
        Map<byte[], byte[]> hash = new HashMap<>();
        hash.put(field2, value2);
        result = jedis.hsetex(key, params, hash);
        assertEquals(1, result, "Binary HSETEX with map should return number of new fields");

        // Test HGETEX - binary
        HGetExParams getParams = HGetExParams.hGetExParams().persist();
        List<byte[]> getResult = jedis.hgetex(key, getParams, field1, field2);
        assertEquals(2, getResult.size(), "Binary HGETEX should return values for all fields");
        assertArrayEquals(
                value1, getResult.get(0), "Binary HGETEX should return correct value for field1");
        assertArrayEquals(
                value2, getResult.get(1), "Binary HGETEX should return correct value for field2");

        // Test HGETDEL - binary
        List<byte[]> delResult = jedis.hgetdel(key, field1, field2);
        assertEquals(2, delResult.size(), "Binary HGETDEL should return values for all fields");
        assertArrayEquals(
                value1, delResult.get(0), "Binary HGETDEL should return correct value for field1");
        assertArrayEquals(
                value2, delResult.get(1), "Binary HGETDEL should return correct value for field2");

        // Verify fields are deleted
        assertNull(jedis.hget(key, field1), "Field1 should be deleted after binary HGETDEL");
        assertNull(jedis.hget(key, field2), "Field2 should be deleted after binary HGETDEL");
    }

    // ========== LIST COMMANDS TESTS ==========

    @Test
    void list_basic_operations() {
        String key = UUID.randomUUID().toString();

        // Test LPUSH - String version
        long result = jedis.lpush(key, "value1", "value2", "value3");
        assertEquals(3, result, "LPUSH should return list length");

        // Test RPUSH - String version
        result = jedis.rpush(key, "value4", "value5");
        assertEquals(5, result, "RPUSH should return list length");

        // Test LLEN
        long length = jedis.llen(key);
        assertEquals(5, length, "LLEN should return correct list length");

        // Test LPOP - single element
        String popped = jedis.lpop(key);
        assertEquals("value3", popped, "LPOP should return last pushed element");

        // Test RPOP - single element
        popped = jedis.rpop(key);
        assertEquals("value5", popped, "RPOP should return last pushed element");

        // Test LPOP - multiple elements
        List<String> poppedList = jedis.lpop(key, 2);
        assertEquals(2, poppedList.size(), "LPOP with count should return correct number of elements");
        assertEquals("value2", poppedList.get(0), "LPOP should return elements in correct order");
        assertEquals("value1", poppedList.get(1), "LPOP should return elements in correct order");

        // Test RPOP - multiple elements
        jedis.rpush(key, "a", "b", "c");
        poppedList = jedis.rpop(key, 2);
        assertEquals(2, poppedList.size(), "RPOP with count should return correct number of elements");
        assertEquals("c", poppedList.get(0), "RPOP should return elements in correct order");
        assertEquals("b", poppedList.get(1), "RPOP should return elements in correct order");
    }

    @Test
    void list_basic_operations_binary() {
        byte[] key = (UUID.randomUUID().toString()).getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();
        byte[] value3 = "value3".getBytes();

        // Test LPUSH - binary version
        long result = jedis.lpush(key, value1, value2, value3);
        assertEquals(3, result, "Binary LPUSH should return list length");

        // Test RPUSH - binary version
        byte[] value4 = "value4".getBytes();
        result = jedis.rpush(key, value4);
        assertEquals(4, result, "Binary RPUSH should return list length");

        // Test LLEN - binary
        long length = jedis.llen(key);
        assertEquals(4, length, "Binary LLEN should return correct list length");

        // Test LPOP - binary single element
        byte[] popped = jedis.lpop(key);
        assertArrayEquals(value3, popped, "Binary LPOP should return last pushed element");

        // Test RPOP - binary single element
        popped = jedis.rpop(key);
        assertArrayEquals(value4, popped, "Binary RPOP should return last pushed element");

        // Test LPOP - binary multiple elements
        List<byte[]> poppedList = jedis.lpop(key, 2);
        assertEquals(
                2, poppedList.size(), "Binary LPOP with count should return correct number of elements");
        assertArrayEquals(
                value2, poppedList.get(0), "Binary LPOP should return elements in correct order");
        assertArrayEquals(
                value1, poppedList.get(1), "Binary LPOP should return elements in correct order");
    }

    @Test
    void list_range_and_index_operations() {
        String key = UUID.randomUUID().toString();

        // Setup test data
        jedis.lpush(key, "item1", "item2", "item3", "item4", "item5");

        // Test LRANGE
        List<String> range = jedis.lrange(key, 0, 2);
        assertEquals(3, range.size(), "LRANGE should return correct number of elements");
        assertEquals("item5", range.get(0), "LRANGE should return elements in correct order");
        assertEquals("item4", range.get(1), "LRANGE should return elements in correct order");
        assertEquals("item3", range.get(2), "LRANGE should return elements in correct order");

        // Test LRANGE with negative indices
        range = jedis.lrange(key, -2, -1);
        assertEquals(2, range.size(), "LRANGE with negative indices should work");
        assertEquals("item2", range.get(0), "LRANGE should handle negative indices correctly");
        assertEquals("item1", range.get(1), "LRANGE should handle negative indices correctly");

        // Test LINDEX
        String element = jedis.lindex(key, 0);
        assertEquals("item5", element, "LINDEX should return correct element");

        element = jedis.lindex(key, -1);
        assertEquals("item1", element, "LINDEX should handle negative index");

        // Test LSET
        String result = jedis.lset(key, 2, "modified_item3");
        assertEquals("OK", result, "LSET should return OK");

        element = jedis.lindex(key, 2);
        assertEquals("modified_item3", element, "LSET should modify element correctly");

        // Test LTRIM
        result = jedis.ltrim(key, 1, 3);
        assertEquals("OK", result, "LTRIM should return OK");

        long length = jedis.llen(key);
        assertEquals(3, length, "LTRIM should reduce list length");

        range = jedis.lrange(key, 0, -1);
        assertEquals("item4", range.get(0), "LTRIM should preserve correct elements");
        assertEquals("modified_item3", range.get(1), "LTRIM should preserve correct elements");
        assertEquals("item2", range.get(2), "LTRIM should preserve correct elements");
    }

    @Test
    void list_range_and_index_operations_binary() {
        byte[] key = (UUID.randomUUID().toString()).getBytes();
        byte[] item1 = "item1".getBytes();
        byte[] item2 = "item2".getBytes();
        byte[] item3 = "item3".getBytes();

        // Setup test data
        jedis.lpush(key, item1, item2, item3);

        // Test LRANGE - binary
        List<byte[]> range = jedis.lrange(key, 0, -1);
        assertEquals(3, range.size(), "Binary LRANGE should return correct number of elements");
        assertArrayEquals(item3, range.get(0), "Binary LRANGE should return elements in correct order");
        assertArrayEquals(item2, range.get(1), "Binary LRANGE should return elements in correct order");
        assertArrayEquals(item1, range.get(2), "Binary LRANGE should return elements in correct order");

        // Test LINDEX - binary
        byte[] element = jedis.lindex(key, 1);
        assertArrayEquals(item2, element, "Binary LINDEX should return correct element");

        // Test LSET - binary
        byte[] newValue = "modified_item2".getBytes();
        String result = jedis.lset(key, 1, newValue);
        assertEquals("OK", result, "Binary LSET should return OK");

        element = jedis.lindex(key, 1);
        assertArrayEquals(newValue, element, "Binary LSET should modify element correctly");

        // Test LTRIM - binary
        result = jedis.ltrim(key, 0, 1);
        assertEquals("OK", result, "Binary LTRIM should return OK");

        long length = jedis.llen(key);
        assertEquals(2, length, "Binary LTRIM should reduce list length");
    }

    @Test
    void list_modification_operations() {
        String key = UUID.randomUUID().toString();
        String nonExistentKey = UUID.randomUUID().toString();

        // Setup test data with duplicates for LREM testing
        jedis.lpush(key, "a", "b", "a", "c", "a", "d");

        // Test LREM - remove all occurrences
        long removed = jedis.lrem(key, 0, "a");
        assertEquals(3, removed, "LREM should remove all occurrences when count is 0");

        List<String> remaining = jedis.lrange(key, 0, -1);
        assertEquals(3, remaining.size(), "List should have correct size after LREM");
        assertFalse(remaining.contains("a"), "List should not contain removed elements");

        // Test LREM - remove from head
        jedis.lpush(key, "x", "x", "y");
        removed = jedis.lrem(key, 2, "x");
        assertEquals(2, removed, "LREM should remove specified count from head");

        // Test LINSERT - before
        long insertResult = jedis.linsert(key, ListPosition.BEFORE, "y", "before_y");
        assertTrue(insertResult > 0, "LINSERT BEFORE should return positive length");

        String element = jedis.lindex(key, 0);
        assertEquals("before_y", element, "LINSERT BEFORE should insert in correct position");

        // Test LINSERT - after
        insertResult = jedis.linsert(key, ListPosition.AFTER, "y", "after_y");
        assertTrue(insertResult > 0, "LINSERT AFTER should return positive length");

        // Test LPUSHX - existing key
        long pushResult = jedis.lpushx(key, "lpushx_value");
        assertTrue(pushResult > 0, "LPUSHX on existing key should return positive length");

        element = jedis.lindex(key, 0);
        assertEquals("lpushx_value", element, "LPUSHX should add element to head");

        // Test LPUSHX - non-existent key
        pushResult = jedis.lpushx(nonExistentKey, "should_not_exist");
        assertEquals(0, pushResult, "LPUSHX on non-existent key should return 0");

        assertFalse(jedis.exists(nonExistentKey), "LPUSHX should not create non-existent key");

        // Test RPUSHX - existing key
        pushResult = jedis.rpushx(key, "rpushx_value");
        assertTrue(pushResult > 0, "RPUSHX on existing key should return positive length");

        element = jedis.lindex(key, -1);
        assertEquals("rpushx_value", element, "RPUSHX should add element to tail");

        // Test RPUSHX - non-existent key
        pushResult = jedis.rpushx(nonExistentKey, "should_not_exist");
        assertEquals(0, pushResult, "RPUSHX on non-existent key should return 0");
    }

    @Test
    void list_modification_operations_binary() {
        byte[] key = (UUID.randomUUID().toString()).getBytes();
        byte[] valueA = "a".getBytes();
        byte[] valueB = "b".getBytes();
        byte[] valueC = "c".getBytes();

        // Setup test data
        jedis.lpush(key, valueA, valueB, valueA, valueC);

        // Test LREM - binary
        long removed = jedis.lrem(key, 1, valueA);
        assertEquals(1, removed, "Binary LREM should remove one occurrence");

        // Test LINSERT - binary
        byte[] insertValue = "inserted".getBytes();
        long insertResult = jedis.linsert(key, ListPosition.BEFORE, valueB, insertValue);
        assertTrue(insertResult > 0, "Binary LINSERT should return positive length");

        // Test LPUSHX - binary
        byte[] lpushxValue = "lpushx".getBytes();
        long pushResult = jedis.lpushx(key, lpushxValue);
        assertTrue(pushResult > 0, "Binary LPUSHX should return positive length");

        byte[] element = jedis.lindex(key, 0);
        assertArrayEquals(lpushxValue, element, "Binary LPUSHX should add element correctly");

        // Test RPUSHX - binary
        byte[] rpushxValue = "rpushx".getBytes();
        pushResult = jedis.rpushx(key, rpushxValue);
        assertTrue(pushResult > 0, "Binary RPUSHX should return positive length");

        element = jedis.lindex(key, -1);
        assertArrayEquals(rpushxValue, element, "Binary RPUSHX should add element correctly");
    }

    @Test
    void list_blocking_operations() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        // Setup test data
        jedis.lpush(key1, "value1", "value2");
        jedis.lpush(key2, "value3", "value4");

        // Test BLPOP - int timeout, multiple keys
        List<String> result = jedis.blpop(1, key1, key2, key3);
        assertEquals(2, result.size(), "BLPOP should return key and value");
        assertEquals(key1, result.get(0), "BLPOP should return correct key");
        assertEquals("value2", result.get(1), "BLPOP should return correct value");

        // Test BLPOP - double timeout, multiple keys (KeyValue return)
        KeyValue<String, String> kvResult = jedis.blpop(1.0, key1, key2, key3);
        assertNotNull(kvResult, "BLPOP with double timeout should return KeyValue");
        assertEquals(key1, kvResult.getKey(), "BLPOP KeyValue should have correct key");
        assertEquals("value1", kvResult.getValue(), "BLPOP KeyValue should have correct value");

        // Test BLPOP - single key variants
        jedis.lpush(key1, "single_value");
        result = jedis.blpop(1, key1);
        assertEquals(2, result.size(), "BLPOP single key should return key and value");
        assertEquals("single_value", result.get(1), "BLPOP single key should return correct value");

        kvResult = jedis.blpop(1.0, key1);
        assertNull(kvResult, "BLPOP on empty key should return null");

        // Test BRPOP - int timeout, multiple keys
        result = jedis.brpop(1, key2, key3);
        assertEquals(2, result.size(), "BRPOP should return key and value");
        assertEquals(key2, result.get(0), "BRPOP should return correct key");
        assertEquals("value3", result.get(1), "BRPOP should return correct value");

        // Test BRPOP - double timeout, multiple keys (KeyValue return)
        kvResult = jedis.brpop(1.0, key2, key3);
        assertNotNull(kvResult, "BRPOP with double timeout should return KeyValue");
        assertEquals(key2, kvResult.getKey(), "BRPOP KeyValue should have correct key");
        assertEquals("value4", kvResult.getValue(), "BRPOP KeyValue should have correct value");

        // Test BRPOP - single key variants
        jedis.rpush(key2, "single_value_r");
        result = jedis.brpop(1, key2);
        assertEquals(2, result.size(), "BRPOP single key should return key and value");
        assertEquals("single_value_r", result.get(1), "BRPOP single key should return correct value");

        kvResult = jedis.brpop(1.0, key2);
        assertNull(kvResult, "BRPOP on empty key should return null");
    }

    @Test
    void list_blocking_operations_binary() {
        byte[] key1 = (UUID.randomUUID().toString()).getBytes();
        byte[] key2 = (UUID.randomUUID().toString()).getBytes();
        byte[] value1 = "value1".getBytes();
        byte[] value2 = "value2".getBytes();

        // Setup test data
        jedis.lpush(key1, value1, value2);

        // Test BLPOP - binary int timeout
        List<byte[]> result = jedis.blpop(1, key1, key2);
        assertEquals(2, result.size(), "Binary BLPOP should return key and value");
        assertArrayEquals(key1, result.get(0), "Binary BLPOP should return correct key");
        assertArrayEquals(value2, result.get(1), "Binary BLPOP should return correct value");

        // Test BLPOP - binary double timeout (KeyValue return)
        KeyValue<byte[], byte[]> kvResult = jedis.blpop(1.0, key1, key2);
        assertNotNull(kvResult, "Binary BLPOP with double timeout should return KeyValue");
        assertArrayEquals(key1, kvResult.getKey(), "Binary BLPOP KeyValue should have correct key");
        assertArrayEquals(
                value1, kvResult.getValue(), "Binary BLPOP KeyValue should have correct value");

        // Test BRPOP - binary
        jedis.rpush(key2, value1, value2);
        result = jedis.brpop(1, key2);
        assertEquals(2, result.size(), "Binary BRPOP should return key and value");
        assertArrayEquals(key2, result.get(0), "Binary BRPOP should return correct key");
        assertArrayEquals(value2, result.get(1), "Binary BRPOP should return correct value");

        kvResult = jedis.brpop(1.0, key2);
        assertNotNull(kvResult, "Binary BRPOP with double timeout should return KeyValue");
        assertArrayEquals(
                value1, kvResult.getValue(), "Binary BRPOP KeyValue should have correct value");
    }

    @Test
    void list_position_operations() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("6.0.6"), "LPOS requires Valkey 6.0.6+");

        String key = UUID.randomUUID().toString();

        // Setup test data with duplicates
        jedis.lpush(key, "a", "b", "c", "b", "d", "b", "e");

        // Test LPOS - basic usage
        Long position = jedis.lpos(key, "b");
        assertNotNull(position, "LPOS should find element");
        assertEquals(1L, position, "LPOS should return correct position");

        // Test LPOS - element not found
        position = jedis.lpos(key, "not_found");
        assertNull(position, "LPOS should return null for non-existent element");

        // Test LPOS with parameters - rank
        LPosParams params = LPosParams.lPosParams().rank(2);
        position = jedis.lpos(key, "b", params);
        assertNotNull(position, "LPOS with rank should find element");
        assertEquals(3L, position, "LPOS with rank should return correct position");

        // Test LPOS with parameters - maxlen
        params = LPosParams.lPosParams().maxlen(3);
        position = jedis.lpos(key, "d");
        assertNotNull(position, "LPOS should find element within range");

        // Test LPOS - multiple positions
        List<Long> positions = jedis.lpos(key, "b", LPosParams.lPosParams(), 3);
        assertEquals(3, positions.size(), "LPOS should return multiple positions");
        assertEquals(1L, positions.get(0), "LPOS should return positions in order");
        assertEquals(3L, positions.get(1), "LPOS should return positions in order");
        assertEquals(5L, positions.get(2), "LPOS should return positions in order");

        // Test LPOS - limit results
        positions = jedis.lpos(key, "b", LPosParams.lPosParams(), 2);
        assertEquals(2, positions.size(), "LPOS should limit results correctly");
    }

    @Test
    void list_position_operations_binary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("6.0.6"), "LPOS requires Valkey 6.0.6+");

        byte[] key = (UUID.randomUUID().toString()).getBytes();
        byte[] valueA = "a".getBytes();
        byte[] valueB = "b".getBytes();
        byte[] valueC = "c".getBytes();

        // Setup test data - lpush adds to the left, so order will be: valueB, valueC, valueB, valueA
        jedis.lpush(key, valueA, valueB, valueC, valueB);

        // Test LPOS - binary basic usage
        Long position = jedis.lpos(key, valueB);
        assertNotNull(position, "Binary LPOS should find element");
        assertEquals(0L, position, "Binary LPOS should return correct position");

        // Test LPOS - binary with parameters
        LPosParams params = LPosParams.lPosParams().rank(2);
        position = jedis.lpos(key, valueB, params);
        assertNotNull(position, "Binary LPOS with rank should find element");
        assertEquals(2L, position, "Binary LPOS with rank should return correct position");

        // Test LPOS - binary multiple positions
        List<Long> positions = jedis.lpos(key, valueB, LPosParams.lPosParams(), 2);
        assertEquals(2, positions.size(), "Binary LPOS should return multiple positions");
        assertEquals(0L, positions.get(0), "Binary LPOS should return positions in order");
        assertEquals(2L, positions.get(1), "Binary LPOS should return positions in order");
    }

    @Test
    void list_move_operations() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"), "LMOVE requires Valkey 6.2.0+");

        String srcKey = UUID.randomUUID().toString();
        String dstKey = UUID.randomUUID().toString();

        // Setup test data
        jedis.lpush(srcKey, "item1", "item2", "item3");
        jedis.lpush(dstKey, "existing1", "existing2");

        // Test LMOVE - LEFT to RIGHT
        String moved = jedis.lmove(srcKey, dstKey, ListDirection.LEFT, ListDirection.RIGHT);
        assertEquals("item3", moved, "LMOVE should return moved element");

        // Verify source list
        List<String> srcList = jedis.lrange(srcKey, 0, -1);
        assertEquals(2, srcList.size(), "Source list should have one less element");
        assertFalse(srcList.contains("item3"), "Moved element should not be in source");

        // Verify destination list
        List<String> dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals(3, dstList.size(), "Destination list should have one more element");
        assertEquals("item3", dstList.get(2), "Moved element should be at end of destination");

        // Test LMOVE - RIGHT to LEFT
        moved = jedis.lmove(srcKey, dstKey, ListDirection.RIGHT, ListDirection.LEFT);
        assertEquals("item1", moved, "LMOVE RIGHT to LEFT should return correct element");

        dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals("item1", dstList.get(0), "Moved element should be at head of destination");

        // Test BLMOVE - blocking move
        jedis.lpush(srcKey, "blocking_item");
        moved = jedis.blmove(srcKey, dstKey, ListDirection.LEFT, ListDirection.RIGHT, 1.0);
        assertEquals("blocking_item", moved, "BLMOVE should return moved element");

        // Test BLMOVE - timeout on empty list
        jedis.del(srcKey);
        moved = jedis.blmove(srcKey, dstKey, ListDirection.LEFT, ListDirection.RIGHT, 0.1);
        assertNull(moved, "BLMOVE should timeout and return null on empty list");
    }

    @Test
    void list_move_operations_binary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("6.2.0"), "LMOVE requires Valkey 6.2.0+");

        byte[] srcKey = (UUID.randomUUID().toString()).getBytes();
        byte[] dstKey = (UUID.randomUUID().toString()).getBytes();
        byte[] item1 = "item1".getBytes();
        byte[] item2 = "item2".getBytes();

        // Setup test data
        jedis.lpush(srcKey, item1, item2);

        // Test LMOVE - binary
        byte[] moved = jedis.lmove(srcKey, dstKey, ListDirection.LEFT, ListDirection.RIGHT);
        assertArrayEquals(item2, moved, "Binary LMOVE should return moved element");

        // Verify destination list
        List<byte[]> dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals(1, dstList.size(), "Binary destination list should have moved element");
        assertArrayEquals(item2, dstList.get(0), "Binary moved element should be correct");

        // Test BLMOVE - binary
        moved = jedis.blmove(srcKey, dstKey, ListDirection.LEFT, ListDirection.LEFT, 1.0);
        assertArrayEquals(item1, moved, "Binary BLMOVE should return moved element");

        dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals(2, dstList.size(), "Binary destination should have both elements");
        assertArrayEquals(item1, dstList.get(0), "Binary BLMOVE should place element at head");
    }

    @Test
    void list_multi_pop_operations() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "LMPOP requires Valkey 7.0.0+");

        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        // Setup test data
        jedis.lpush(key2, "item1", "item2", "item3");

        // Test LMPOP - LEFT direction, first non-empty key
        KeyValue<String, List<String>> result = jedis.lmpop(ListDirection.LEFT, key1, key2, key3);
        assertNotNull(result, "LMPOP should return result from first non-empty key");
        assertEquals(key2, result.getKey(), "LMPOP should return correct key");
        assertEquals(1, result.getValue().size(), "LMPOP should return one element by default");
        assertEquals("item3", result.getValue().get(0), "LMPOP should return correct element");

        // Test LMPOP - RIGHT direction with count
        result = jedis.lmpop(ListDirection.RIGHT, 2, key1, key2, key3);
        assertNotNull(result, "LMPOP with count should return result");
        assertEquals(key2, result.getKey(), "LMPOP should return correct key");
        assertEquals(2, result.getValue().size(), "LMPOP should return specified count");
        assertEquals("item1", result.getValue().get(0), "LMPOP RIGHT should return from tail");
        assertEquals("item2", result.getValue().get(1), "LMPOP RIGHT should return in order");

        // Test LMPOP - no elements available
        result = jedis.lmpop(ListDirection.LEFT, key1, key2, key3);
        assertNull(result, "LMPOP should return null when no elements available");

        // Test BLMPOP - blocking multi-pop
        jedis.lpush(key1, "blocking1", "blocking2", "blocking3");
        result = jedis.blmpop(1.0, ListDirection.LEFT, key1, key2);
        assertNotNull(result, "BLMPOP should return result");
        assertEquals(key1, result.getKey(), "BLMPOP should return correct key");
        assertEquals(1, result.getValue().size(), "BLMPOP should return one element by default");
        assertEquals("blocking3", result.getValue().get(0), "BLMPOP should return correct element");

        // Test BLMPOP - with count
        result = jedis.blmpop(1.0, ListDirection.LEFT, 2, key1, key2);
        assertNotNull(result, "BLMPOP with count should return result");
        assertEquals(2, result.getValue().size(), "BLMPOP should return specified count");
        assertEquals("blocking2", result.getValue().get(0), "BLMPOP should return elements in order");
        assertEquals("blocking1", result.getValue().get(1), "BLMPOP should return elements in order");

        // Test BLMPOP - timeout
        result = jedis.blmpop(0.1, ListDirection.LEFT, key1, key2, key3);
        assertNull(result, "BLMPOP should timeout and return null");
    }

    @Test
    void list_multi_pop_operations_binary() {
        assumeTrue(SERVER_VERSION.isGreaterThanOrEqualTo("7.0.0"), "LMPOP requires Valkey 7.0.0+");

        byte[] key1 = (UUID.randomUUID().toString()).getBytes();
        byte[] key2 = (UUID.randomUUID().toString()).getBytes();
        byte[] item1 = "item1".getBytes();
        byte[] item2 = "item2".getBytes();
        byte[] item3 = "item3".getBytes();

        // Setup test data
        jedis.lpush(key1, item1, item2, item3);

        // Test LMPOP - binary
        KeyValue<byte[], List<byte[]>> result = jedis.lmpop(ListDirection.LEFT, key1, key2);
        assertNotNull(result, "Binary LMPOP should return result");
        assertArrayEquals(key1, result.getKey(), "Binary LMPOP should return correct key");
        assertEquals(1, result.getValue().size(), "Binary LMPOP should return one element");
        assertArrayEquals(
                item3, result.getValue().get(0), "Binary LMPOP should return correct element");

        // Test LMPOP - binary with count
        result = jedis.lmpop(ListDirection.RIGHT, 2, key1, key2);
        assertNotNull(result, "Binary LMPOP with count should return result");
        assertEquals(2, result.getValue().size(), "Binary LMPOP should return specified count");
        assertArrayEquals(item1, result.getValue().get(0), "Binary LMPOP should return from tail");
        assertArrayEquals(item2, result.getValue().get(1), "Binary LMPOP should return in order");

        // Test BLMPOP - binary
        jedis.lpush(key2, item1, item2);
        result = jedis.blmpop(1.0, ListDirection.LEFT, key1, key2);
        assertNotNull(result, "Binary BLMPOP should return result");
        assertArrayEquals(key2, result.getKey(), "Binary BLMPOP should return correct key");
        assertArrayEquals(
                item2, result.getValue().get(0), "Binary BLMPOP should return correct element");

        // Test BLMPOP - binary with count
        result = jedis.blmpop(1.0, ListDirection.LEFT, 2, key1, key2);
        assertNotNull(result, "Binary BLMPOP with count should return result");
        assertEquals(1, result.getValue().size(), "Binary BLMPOP should return available elements");
        assertArrayEquals(
                item1, result.getValue().get(0), "Binary BLMPOP should return remaining element");
    }

    @Test
    void list_deprecated_operations() {
        String srcKey = UUID.randomUUID().toString();
        String dstKey = UUID.randomUUID().toString();

        // Setup test data
        jedis.lpush(srcKey, "item1", "item2", "item3");
        jedis.lpush(dstKey, "existing1");

        // Test RPOPLPUSH
        String moved = jedis.rpoplpush(srcKey, dstKey);
        assertEquals("item1", moved, "RPOPLPUSH should return moved element");

        // Verify source list
        List<String> srcList = jedis.lrange(srcKey, 0, -1);
        assertEquals(2, srcList.size(), "Source list should have one less element after RPOPLPUSH");
        assertFalse(srcList.contains("item1"), "Moved element should not be in source");

        // Verify destination list
        List<String> dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals(2, dstList.size(), "Destination list should have moved element");
        assertEquals("item1", dstList.get(0), "RPOPLPUSH should add element to head of destination");

        // Test BRPOPLPUSH
        jedis.lpush(srcKey, "blocking_item");
        moved = jedis.brpoplpush(srcKey, dstKey, 1);
        assertEquals("item2", moved, "BRPOPLPUSH should return moved element");

        dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals("item2", dstList.get(0), "BRPOPLPUSH should add element to head of destination");

        // Test BRPOPLPUSH - timeout
        jedis.del(srcKey);
        moved = jedis.brpoplpush(srcKey, dstKey, 1);
        assertNull(moved, "BRPOPLPUSH should timeout and return null on empty list");
    }

    @Test
    void list_deprecated_operations_binary() {
        byte[] srcKey = (UUID.randomUUID().toString()).getBytes();
        byte[] dstKey = (UUID.randomUUID().toString()).getBytes();
        byte[] item1 = "item1".getBytes();
        byte[] item2 = "item2".getBytes();

        // Setup test data
        jedis.lpush(srcKey, item1, item2);

        // Test RPOPLPUSH - binary
        byte[] moved = jedis.rpoplpush(srcKey, dstKey);
        assertArrayEquals(item1, moved, "Binary RPOPLPUSH should return moved element");

        // Verify destination list
        List<byte[]> dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals(1, dstList.size(), "Binary destination list should have moved element");
        assertArrayEquals(item1, dstList.get(0), "Binary RPOPLPUSH should move element correctly");

        // Test BRPOPLPUSH - binary
        moved = jedis.brpoplpush(srcKey, dstKey, 1);
        assertArrayEquals(item2, moved, "Binary BRPOPLPUSH should return moved element");

        dstList = jedis.lrange(dstKey, 0, -1);
        assertEquals(2, dstList.size(), "Binary destination should have both elements");
        assertArrayEquals(item2, dstList.get(0), "Binary BRPOPLPUSH should add to head");
        assertArrayEquals(item1, dstList.get(1), "Binary BRPOPLPUSH should preserve order");
    }

    @Test
    void list_edge_cases() {
        String key = UUID.randomUUID().toString();
        String nonListKey = UUID.randomUUID().toString();

        // Setup non-list key
        jedis.set(nonListKey, "string_value");

        // Test operations on empty list
        assertNull(jedis.lpop(key), "LPOP on non-existent key should return null");
        assertNull(jedis.rpop(key), "RPOP on non-existent key should return null");
        assertEquals(0, jedis.llen(key), "LLEN on non-existent key should return 0");

        List<String> emptyRange = jedis.lrange(key, 0, -1);
        assertTrue(emptyRange.isEmpty(), "LRANGE on non-existent key should return empty list");

        // Test LINDEX on non-existent key
        assertNull(jedis.lindex(key, 0), "LINDEX on non-existent key should return null");

        // Test operations with large indices
        jedis.lpush(key, "item1", "item2");
        assertNull(jedis.lindex(key, 100), "LINDEX with large index should return null");
        assertNull(jedis.lindex(key, -100), "LINDEX with large negative index should return null");

        // Test LRANGE with invalid ranges
        List<String> invalidRange = jedis.lrange(key, 10, 20);
        assertTrue(invalidRange.isEmpty(), "LRANGE with invalid range should return empty list");

        // Test LREM on non-existent element
        long removed = jedis.lrem(key, 1, "non_existent");
        assertEquals(0, removed, "LREM on non-existent element should return 0");

        // Test LINSERT with non-existent pivot
        long insertResult = jedis.linsert(key, ListPosition.BEFORE, "non_existent", "new_value");
        assertEquals(-1, insertResult, "LINSERT with non-existent pivot should return -1");

        // Test LPUSHX and RPUSHX on non-existent key
        assertEquals(
                0, jedis.lpushx("non_existent_key", "value"), "LPUSHX on non-existent key should return 0");
        assertEquals(
                0, jedis.rpushx("non_existent_key", "value"), "RPUSHX on non-existent key should return 0");
    }
}
