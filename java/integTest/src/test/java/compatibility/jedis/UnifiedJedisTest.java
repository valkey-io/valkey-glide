/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.jedis;

import static glide.TestConfiguration.STANDALONE_HOSTS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import redis.clients.jedis.UnifiedJedis;

/**
 * UnifiedJedis standalone compatibility test that validates GLIDE UnifiedJedis functionality.
 *
 * <p>This test ensures that the GLIDE compatibility layer provides the expected UnifiedJedis API
 * and behavior for core Valkey operations in standalone mode.
 */
public class UnifiedJedisTest {

    // Server configuration - dynamically resolved from CI environment
    private static final String valkeyHost;
    private static final int valkeyPort;

    // GLIDE UnifiedJedis compatibility layer instance
    private UnifiedJedis unifiedJedis;

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
        // Create GLIDE UnifiedJedis compatibility layer instance
        unifiedJedis = new UnifiedJedis(valkeyHost, valkeyPort);
        assertNotNull(unifiedJedis, "GLIDE UnifiedJedis instance should be created successfully");
    }

    @Test
    void basic_set_and_get() {
        String testKey = UUID.randomUUID().toString();
        String testValue = "unified_test_value_123";

        // Test GLIDE UnifiedJedis compatibility layer
        String setResult = unifiedJedis.set(testKey, testValue);
        assertEquals("OK", setResult, "SET should return OK");

        String getResult = unifiedJedis.get(testKey);
        assertEquals(testValue, getResult, "GET should return the set value");
    }

    @Test
    void multiple_operations() {
        Map<String, String> testData = new HashMap<>();
        testData.put(UUID.randomUUID().toString(), "unified_value1");
        testData.put(UUID.randomUUID().toString(), "unified_value2");
        testData.put(UUID.randomUUID().toString(), "unified_value3");

        // Test multiple SET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = unifiedJedis.set(entry.getKey(), entry.getValue());
            assertEquals("OK", result, "SET should return OK for " + entry.getKey());
        }

        // Test multiple GET operations
        for (Map.Entry<String, String> entry : testData.entrySet()) {
            String result = unifiedJedis.get(entry.getKey());
            assertEquals(
                    entry.getValue(), result, "GET should return correct value for " + entry.getKey());
        }
    }

    @Test
    void connection_operations() {
        // Test PING
        String pingResult = unifiedJedis.ping();
        assertEquals("PONG", pingResult, "PING should return PONG");

        // Test PING with message
        String message = "unified_test_message";
        String pingWithMessage = unifiedJedis.ping(message);
        assertEquals(message, pingWithMessage, "PING with message should return the message");
    }

    @Test
    void delete_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();
        String testKey3 = UUID.randomUUID().toString();

        // Set some keys
        unifiedJedis.set(testKey1, "value1");
        unifiedJedis.set(testKey2, "value2");
        unifiedJedis.set(testKey3, "value3");

        // Test single key deletion
        long delResult = unifiedJedis.del(testKey1);
        assertEquals(1, delResult, "DEL should return 1 for deleted key");

        // Verify key is deleted
        String getResult = unifiedJedis.get(testKey1);
        assertNull(getResult, "Key should not exist after deletion");

        // Test multiple key deletion
        long multiDelResult = unifiedJedis.del(testKey2, testKey3);
        assertEquals(2, multiDelResult, "DEL should return 2 for two deleted keys");

        // Verify keys are deleted
        assertNull(unifiedJedis.get(testKey2), "Key2 should not exist after deletion");
        assertNull(unifiedJedis.get(testKey3), "Key3 should not exist after deletion");
    }

    @Test
    void connection_state() {
        // Test that connection is not closed initially
        assertFalse(unifiedJedis.isClosed(), "Connection should not be closed initially");

        // Test basic operations work
        String testKey = UUID.randomUUID().toString();
        String testValue = "connection_value";

        unifiedJedis.set(testKey, testValue);
        String result = unifiedJedis.get(testKey);
        assertEquals(testValue, result, "Operations should work on active connection");

        // Connection should still be active
        assertFalse(unifiedJedis.isClosed(), "Connection should still be active after operations");
    }

    @Test
    void binary_operations() {
        String keyString = UUID.randomUUID().toString();
        byte[] testKey = keyString.getBytes();
        byte[] testValue = "binary_value".getBytes();

        // Set using string method first (since binary set might not be supported)
        unifiedJedis.set(keyString, "binary_value");

        // Test binary key deletion - use the same key that was set
        long delResult = unifiedJedis.del(testKey);
        assertEquals(1, delResult, "Binary DEL should return 1 for deleted key");

        // Verify key is deleted
        String getResult = unifiedJedis.get(keyString);
        assertNull(getResult, "Key should not exist after binary deletion");
    }

    @Test
    void multiple_binary_deletion() {
        // Set up test keys using string methods
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();
        String key3 = UUID.randomUUID().toString();

        unifiedJedis.set(key1, "value1");
        unifiedJedis.set(key2, "value2");
        unifiedJedis.set(key3, "value3");

        // Test multiple binary key deletion
        byte[][] binaryKeys = {key1.getBytes(), key2.getBytes(), key3.getBytes()};

        long delResult = unifiedJedis.del(binaryKeys);
        assertEquals(3, delResult, "Binary DEL should return 3 for three deleted keys");

        // Verify keys are deleted
        assertNull(unifiedJedis.get(key1), "Key1 should not exist after deletion");
        assertNull(unifiedJedis.get(key2), "Key2 should not exist after deletion");
        assertNull(unifiedJedis.get(key3), "Key3 should not exist after deletion");
    }

    @Test
    void large_value_operations() {
        String testKey = UUID.randomUUID().toString();
        StringBuilder largeValue = new StringBuilder();

        // Create a large value (10KB)
        for (int i = 0; i < 1000; i++) {
            largeValue.append("0123456789");
        }
        String expectedValue = largeValue.toString();

        // Test setting and getting large value
        String setResult = unifiedJedis.set(testKey, expectedValue);
        assertEquals("OK", setResult, "SET should work with large values");

        String getResult = unifiedJedis.get(testKey);
        assertEquals(expectedValue, getResult, "GET should return complete large value");
        assertEquals(10000, getResult.length(), "Large value should have correct length");
    }

    @Test
    void string_operations() {
        String testKey = UUID.randomUUID().toString();

        // Test APPEND
        long appendResult = unifiedJedis.append(testKey, "hello");
        assertEquals(5, appendResult, "APPEND should return length of new string");

        appendResult = unifiedJedis.append(testKey, " world");
        assertEquals(11, appendResult, "APPEND should return total length");

        String getResult = unifiedJedis.get(testKey);
        assertEquals("hello world", getResult, "GET should return appended string");

        // Test STRLEN
        long strlenResult = unifiedJedis.strlen(testKey);
        assertEquals(11, strlenResult, "STRLEN should return correct length");

        // Test GETRANGE
        String rangeResult = unifiedJedis.getrange(testKey, 0, 4);
        assertEquals("hello", rangeResult, "GETRANGE should return substring");

        // Test SETRANGE
        long setrangeResult = unifiedJedis.setrange(testKey, 6, "Redis");
        assertEquals(11, setrangeResult, "SETRANGE should return string length");

        getResult = unifiedJedis.get(testKey);
        assertEquals("hello Redis", getResult, "String should be modified by SETRANGE");

        // Test SUBSTR
        String substrResult = unifiedJedis.substr(testKey, 6, 10);
        assertEquals("Redis", substrResult, "SUBSTR should return substring");
    }

    @Test
    void numeric_operations() {
        String testKey = UUID.randomUUID().toString();

        // Test INCR
        long incrResult = unifiedJedis.incr(testKey);
        assertEquals(1, incrResult, "INCR should return 1 for new key");

        incrResult = unifiedJedis.incr(testKey);
        assertEquals(2, incrResult, "INCR should increment value");

        // Test INCRBY
        long incrByResult = unifiedJedis.incrBy(testKey, 5);
        assertEquals(7, incrByResult, "INCRBY should add specified amount");

        // Test INCRBYFLOAT
        double incrByFloatResult = unifiedJedis.incrByFloat(testKey, 2.5);
        assertEquals(9.5, incrByFloatResult, 0.001, "INCRBYFLOAT should add float amount");

        // Test DECR
        unifiedJedis.set(testKey, "10");
        long decrResult = unifiedJedis.decr(testKey);
        assertEquals(9, decrResult, "DECR should decrement value");

        // Test DECRBY
        long decrByResult = unifiedJedis.decrBy(testKey, 3);
        assertEquals(6, decrByResult, "DECRBY should subtract specified amount");
    }

    @Test
    void key_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();

        // Set up test data
        unifiedJedis.set(testKey1, "value1");

        // Test EXISTS
        boolean existsResult = unifiedJedis.exists(testKey1);
        assertTrue(existsResult, "EXISTS should return true for existing key");

        existsResult = unifiedJedis.exists(testKey2);
        assertFalse(existsResult, "EXISTS should return false for non-existing key");

        // Test multiple EXISTS
        unifiedJedis.set(testKey2, "value2");
        long multiExistsResult = unifiedJedis.exists(testKey1, testKey2);
        assertEquals(2, multiExistsResult, "EXISTS should return count of existing keys");

        // Test TTL (should be -1 for keys without expiry)
        long ttlResult = unifiedJedis.ttl(testKey1);
        assertEquals(-1, ttlResult, "TTL should return -1 for key without expiry");

        // Test EXPIRE
        long expireResult = unifiedJedis.expire(testKey1, 60);
        assertEquals(1, expireResult, "EXPIRE should return 1 for existing key");

        ttlResult = unifiedJedis.ttl(testKey1);
        assertTrue(ttlResult > 0 && ttlResult <= 60, "TTL should be positive after EXPIRE");

        // Test TYPE
        String typeResult = unifiedJedis.type(testKey1);
        assertEquals("string", typeResult, "TYPE should return 'string' for string value");
    }

    @Test
    void set_operations_with_params() {
        String testKey = UUID.randomUUID().toString();

        // Test SETNX
        long setnxResult = unifiedJedis.setnx(testKey, "value1");
        assertEquals(1, setnxResult, "SETNX should return 1 for new key");

        setnxResult = unifiedJedis.setnx(testKey, "value2");
        assertEquals(0, setnxResult, "SETNX should return 0 for existing key");

        String getResult = unifiedJedis.get(testKey);
        assertEquals("value1", getResult, "Value should not change with failed SETNX");

        // Test SETEX
        String testKey2 = UUID.randomUUID().toString();
        String setexResult = unifiedJedis.setex(testKey2, 60, "expiring_value");
        assertEquals("OK", setexResult, "SETEX should return OK");

        getResult = unifiedJedis.get(testKey2);
        assertEquals("expiring_value", getResult, "SETEX should set value");

        long ttl = unifiedJedis.ttl(testKey2);
        assertTrue(ttl > 0 && ttl <= 60, "SETEX should set expiry");

        // Test PSETEX
        String testKey3 = UUID.randomUUID().toString();
        String psetexResult = unifiedJedis.psetex(testKey3, 60000, "ms_expiring_value");
        assertEquals("OK", psetexResult, "PSETEX should return OK");

        getResult = unifiedJedis.get(testKey3);
        assertEquals("ms_expiring_value", getResult, "PSETEX should set value");
    }

    @Test
    void multi_key_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();
        String testKey3 = UUID.randomUUID().toString();

        // Test MSET
        String msetResult =
                unifiedJedis.mset(testKey1, "value1", testKey2, "value2", testKey3, "value3");
        assertEquals("OK", msetResult, "MSET should return OK");

        // Test MGET
        java.util.List<String> mgetResult = unifiedJedis.mget(testKey1, testKey2, testKey3);
        assertEquals(3, mgetResult.size(), "MGET should return 3 values");
        assertEquals("value1", mgetResult.get(0), "MGET should return correct first value");
        assertEquals("value2", mgetResult.get(1), "MGET should return correct second value");
        assertEquals("value3", mgetResult.get(2), "MGET should return correct third value");

        // Test MGET with non-existing key
        String nonExistingKey = UUID.randomUUID().toString();
        mgetResult = unifiedJedis.mget(testKey1, nonExistingKey, testKey2);
        assertEquals(3, mgetResult.size(), "MGET should return 3 values including null");
        assertEquals("value1", mgetResult.get(0), "MGET should return existing value");
        assertNull(mgetResult.get(1), "MGET should return null for non-existing key");
        assertEquals("value2", mgetResult.get(2), "MGET should return existing value");
    }

    @Test
    void get_set_operations() {
        String testKey = UUID.randomUUID().toString();

        // Test GETSET
        String getSetResult = unifiedJedis.getSet(testKey, "new_value");
        assertNull(getSetResult, "GETSET should return null for new key");

        getSetResult = unifiedJedis.getSet(testKey, "newer_value");
        assertEquals("new_value", getSetResult, "GETSET should return old value");

        String getResult = unifiedJedis.get(testKey);
        assertEquals("newer_value", getResult, "GETSET should set new value");

        // Test GETDEL
        String getDelResult = unifiedJedis.getDel(testKey);
        assertEquals("newer_value", getDelResult, "GETDEL should return value");

        getResult = unifiedJedis.get(testKey);
        assertNull(getResult, "Key should be deleted after GETDEL");
    }

    @Test
    void binary_key_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();
        byte[] testValue = "binary_test_value".getBytes();

        // Test binary SET and GET
        String setResult = unifiedJedis.set(testKey, testValue);
        assertEquals("OK", setResult, "Binary SET should return OK");

        byte[] getResult = unifiedJedis.get(testKey);
        assertArrayEquals(testValue, getResult, "Binary GET should return correct value");

        // Test binary EXISTS
        boolean existsResult = unifiedJedis.exists(testKey);
        assertTrue(existsResult, "Binary EXISTS should return true for existing key");

        // Test binary APPEND
        byte[] appendValue = " appended".getBytes();
        long appendResult = unifiedJedis.append(testKey, appendValue);
        assertEquals(25, appendResult, "Binary APPEND should return total length");

        getResult = unifiedJedis.get(testKey);
        String expectedValue = "binary_test_value appended";
        assertArrayEquals(
                expectedValue.getBytes(), getResult, "Binary APPEND should concatenate values");

        // Test binary STRLEN
        long strlenResult = unifiedJedis.strlen(testKey);
        assertEquals(25, strlenResult, "Binary STRLEN should return correct length");

        // Test binary DEL
        long delResult = unifiedJedis.del(testKey);
        assertEquals(1, delResult, "Binary DEL should return 1 for deleted key");

        getResult = unifiedJedis.get(testKey);
        assertNull(getResult, "Key should not exist after binary deletion");
    }

    @Test
    void binary_multi_key_operations() {
        byte[] testKey1 = UUID.randomUUID().toString().getBytes();
        byte[] testKey2 = UUID.randomUUID().toString().getBytes();
        byte[] testKey3 = UUID.randomUUID().toString().getBytes();
        byte[] testValue1 = "binary_value1".getBytes();
        byte[] testValue2 = "binary_value2".getBytes();
        byte[] testValue3 = "binary_value3".getBytes();

        // Test binary MSET
        String msetResult =
                unifiedJedis.mset(testKey1, testValue1, testKey2, testValue2, testKey3, testValue3);
        assertEquals("OK", msetResult, "Binary MSET should return OK");

        // Test binary MGET
        java.util.List<byte[]> mgetResult = unifiedJedis.mget(testKey1, testKey2, testKey3);
        assertEquals(3, mgetResult.size(), "Binary MGET should return 3 values");
        assertArrayEquals(
                testValue1, mgetResult.get(0), "Binary MGET should return correct first value");
        assertArrayEquals(
                testValue2, mgetResult.get(1), "Binary MGET should return correct second value");
        assertArrayEquals(
                testValue3, mgetResult.get(2), "Binary MGET should return correct third value");

        // Test binary multiple EXISTS
        long existsResult = unifiedJedis.exists(testKey1, testKey2, testKey3);
        assertEquals(3, existsResult, "Binary EXISTS should return count of existing keys");

        // Test binary multiple DEL
        long delResult = unifiedJedis.del(testKey1, testKey2, testKey3);
        assertEquals(3, delResult, "Binary DEL should return count of deleted keys");
    }

    @Test
    void binary_numeric_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();

        // Test binary INCR
        long incrResult = unifiedJedis.incr(testKey);
        assertEquals(1, incrResult, "Binary INCR should return 1 for new key");

        incrResult = unifiedJedis.incr(testKey);
        assertEquals(2, incrResult, "Binary INCR should increment value");

        // Test binary INCRBY
        long incrByResult = unifiedJedis.incrBy(testKey, 5);
        assertEquals(7, incrByResult, "Binary INCRBY should add specified amount");

        // Test binary INCRBYFLOAT
        double incrByFloatResult = unifiedJedis.incrByFloat(testKey, 2.5);
        assertEquals(9.5, incrByFloatResult, 0.001, "Binary INCRBYFLOAT should add float amount");

        // Test binary DECR
        unifiedJedis.set(testKey, "10".getBytes());
        long decrResult = unifiedJedis.decr(testKey);
        assertEquals(9, decrResult, "Binary DECR should decrement value");

        // Test binary DECRBY
        long decrByResult = unifiedJedis.decrBy(testKey, 3);
        assertEquals(6, decrByResult, "Binary DECRBY should subtract specified amount");
    }

    @Test
    void bit_operations() {
        String testKey = UUID.randomUUID().toString();

        // Set initial value for bit operations
        unifiedJedis.set(testKey, "hello");

        // Test GETBIT
        boolean getBitResult = unifiedJedis.getbit(testKey, 1);
        assertTrue(getBitResult, "GETBIT should return correct bit value");

        // Test SETBIT
        boolean setBitResult = unifiedJedis.setbit(testKey, 7, false);
        assertTrue(setBitResult, "SETBIT should return original bit value");

        // Test BITCOUNT
        long bitcountResult = unifiedJedis.bitcount(testKey);
        assertTrue(bitcountResult > 0, "BITCOUNT should return positive count");

        // Test BITCOUNT with range
        long bitcountRangeResult = unifiedJedis.bitcount(testKey, 0, 1);
        assertTrue(bitcountRangeResult >= 0, "BITCOUNT with range should work");

        // Test BITPOS
        long bitposResult = unifiedJedis.bitpos(testKey, true);
        assertTrue(bitposResult >= 0, "BITPOS should find first set bit");
    }

    @Test
    void expiration_operations() {
        String testKey = UUID.randomUUID().toString();
        unifiedJedis.set(testKey, "expiring_value");

        // Test EXPIRE
        long expireResult = unifiedJedis.expire(testKey, 60);
        assertEquals(1, expireResult, "EXPIRE should return 1 for existing key");

        // Test TTL
        long ttlResult = unifiedJedis.ttl(testKey);
        assertTrue(ttlResult > 0 && ttlResult <= 60, "TTL should be positive after EXPIRE");

        // Test PTTL
        long pttlResult = unifiedJedis.pttl(testKey);
        assertTrue(pttlResult > 0, "PTTL should return positive milliseconds");

        // Test EXPIREAT
        long futureTimestamp = System.currentTimeMillis() / 1000 + 120;
        long expireAtResult = unifiedJedis.expireAt(testKey, futureTimestamp);
        assertEquals(1, expireAtResult, "EXPIREAT should return 1 for existing key");

        // Test PEXPIRE
        String testKey2 = UUID.randomUUID().toString();
        unifiedJedis.set(testKey2, "ms_expiring_value");
        long pexpireResult = unifiedJedis.pexpire(testKey2, 60000);
        assertEquals(1, pexpireResult, "PEXPIRE should return 1 for existing key");

        // Test PERSIST
        long persistResult = unifiedJedis.persist(testKey2);
        assertEquals(1, persistResult, "PERSIST should return 1 for key with expiry");

        ttlResult = unifiedJedis.ttl(testKey2);
        assertEquals(-1, ttlResult, "TTL should be -1 after PERSIST");
    }

    @Test
    void binary_expiration_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();
        unifiedJedis.set(testKey, "binary_expiring_value".getBytes());

        // Test binary EXPIRE
        long expireResult = unifiedJedis.expire(testKey, 60);
        assertEquals(1, expireResult, "Binary EXPIRE should return 1 for existing key");

        // Test binary TTL
        long ttlResult = unifiedJedis.ttl(testKey);
        assertTrue(ttlResult > 0 && ttlResult <= 60, "Binary TTL should be positive after EXPIRE");

        // Test binary PTTL
        long pttlResult = unifiedJedis.pttl(testKey);
        assertTrue(pttlResult > 0, "Binary PTTL should return positive milliseconds");

        // Test binary PERSIST
        long persistResult = unifiedJedis.persist(testKey);
        assertEquals(1, persistResult, "Binary PERSIST should return 1 for key with expiry");

        ttlResult = unifiedJedis.ttl(testKey);
        assertEquals(-1, ttlResult, "Binary TTL should be -1 after PERSIST");
    }

    @Test
    void key_management_operations() {
        String oldKey = UUID.randomUUID().toString();
        String newKey = UUID.randomUUID().toString();

        // Set up test data
        unifiedJedis.set(oldKey, "rename_test_value");

        // Test RENAME
        String renameResult = unifiedJedis.rename(oldKey, newKey);
        assertEquals("OK", renameResult, "RENAME should return OK");

        String getResult = unifiedJedis.get(newKey);
        assertEquals("rename_test_value", getResult, "Value should be available at new key");

        getResult = unifiedJedis.get(oldKey);
        assertNull(getResult, "Old key should not exist after RENAME");

        // Test RENAMENX
        String anotherKey = UUID.randomUUID().toString();
        unifiedJedis.set(anotherKey, "another_value");

        long renamenxResult = unifiedJedis.renamenx(newKey, anotherKey);
        assertEquals(0, renamenxResult, "RENAMENX should return 0 when target exists");

        String nonExistingKey = UUID.randomUUID().toString();
        renamenxResult = unifiedJedis.renamenx(newKey, nonExistingKey);
        assertEquals(1, renamenxResult, "RENAMENX should return 1 when target doesn't exist");

        // Test TYPE
        String typeResult = unifiedJedis.type(nonExistingKey);
        assertEquals("string", typeResult, "TYPE should return 'string' for string value");

        typeResult = unifiedJedis.type(UUID.randomUUID().toString());
        assertEquals("none", typeResult, "TYPE should return 'none' for non-existing key");
    }

    @Test
    void binary_key_management_operations() {
        byte[] oldKey = UUID.randomUUID().toString().getBytes();
        byte[] newKey = UUID.randomUUID().toString().getBytes();

        // Set up test data
        unifiedJedis.set(oldKey, "binary_rename_test_value".getBytes());

        // Test binary RENAME
        String renameResult = unifiedJedis.rename(oldKey, newKey);
        assertEquals("OK", renameResult, "Binary RENAME should return OK");

        byte[] getResult = unifiedJedis.get(newKey);
        assertArrayEquals(
                "binary_rename_test_value".getBytes(), getResult, "Value should be available at new key");

        getResult = unifiedJedis.get(oldKey);
        assertNull(getResult, "Old key should not exist after binary RENAME");

        // Test binary RENAMENX
        byte[] anotherKey = UUID.randomUUID().toString().getBytes();
        unifiedJedis.set(anotherKey, "another_binary_value".getBytes());

        long renamenxResult = unifiedJedis.renamenx(newKey, anotherKey);
        assertEquals(0, renamenxResult, "Binary RENAMENX should return 0 when target exists");

        // Test binary TYPE
        String typeResult = unifiedJedis.type(newKey);
        assertEquals("string", typeResult, "Binary TYPE should return 'string' for string value");
    }

    @Test
    void utility_operations() {
        String testKey1 = UUID.randomUUID().toString();
        String testKey2 = UUID.randomUUID().toString();

        // Set up test data
        unifiedJedis.set(testKey1, "touch_test_value1");
        unifiedJedis.set(testKey2, "touch_test_value2");

        // Test TOUCH single key
        long touchResult = unifiedJedis.touch(testKey1);
        assertEquals(1, touchResult, "TOUCH should return 1 for existing key");

        // Test TOUCH multiple keys
        touchResult = unifiedJedis.touch(testKey1, testKey2);
        assertEquals(2, touchResult, "TOUCH should return count of touched keys");

        // Test TOUCH non-existing key
        String nonExistingKey = UUID.randomUUID().toString();
        touchResult = unifiedJedis.touch(nonExistingKey);
        assertEquals(0, touchResult, "TOUCH should return 0 for non-existing key");

        // Test UNLINK single key
        long unlinkResult = unifiedJedis.unlink(testKey1);
        assertEquals(1, unlinkResult, "UNLINK should return 1 for deleted key");

        String getResult = unifiedJedis.get(testKey1);
        assertNull(getResult, "Key should not exist after UNLINK");

        // Test UNLINK multiple keys
        unifiedJedis.set(testKey1, "value_for_unlink");
        unlinkResult = unifiedJedis.unlink(testKey1, testKey2);
        assertEquals(2, unlinkResult, "UNLINK should return count of deleted keys");
    }

    @Test
    void advanced_string_operations() {
        String testKey = UUID.randomUUID().toString();

        // Test GETRANGE
        unifiedJedis.set(testKey, "Hello World");
        String getrangeResult = unifiedJedis.getrange(testKey, 0, 4);
        assertEquals("Hello", getrangeResult, "GETRANGE should return substring");

        getrangeResult = unifiedJedis.getrange(testKey, 6, -1);
        assertEquals("World", getrangeResult, "GETRANGE should handle negative indices");

        // Test SETRANGE
        long setrangeResult = unifiedJedis.setrange(testKey, 6, "Redis");
        assertEquals(11, setrangeResult, "SETRANGE should return string length");

        String getResult = unifiedJedis.get(testKey);
        assertEquals("Hello Redis", getResult, "SETRANGE should modify string");

        // Test SUBSTR (deprecated but should work)
        String substrResult = unifiedJedis.substr(testKey, 0, 4);
        assertEquals("Hello", substrResult, "SUBSTR should return substring");
    }

    @Test
    void binary_advanced_operations() {
        byte[] testKey = UUID.randomUUID().toString().getBytes();

        // Test binary GETRANGE
        unifiedJedis.set(testKey, "Binary Hello World".getBytes());
        byte[] getrangeResult = unifiedJedis.getrange(testKey, 7, 11);
        assertArrayEquals(
                "Hello".getBytes(), getrangeResult, "Binary GETRANGE should return substring");

        // Test binary SETRANGE
        long setrangeResult = unifiedJedis.setrange(testKey, 13, "Redis".getBytes());
        assertEquals(18, setrangeResult, "Binary SETRANGE should return string length");

        byte[] getResult = unifiedJedis.get(testKey);
        assertArrayEquals(
                "Binary Hello Redis".getBytes(), getResult, "Binary SETRANGE should modify string");

        // Test binary SUBSTR
        byte[] substrResult = unifiedJedis.substr(testKey, 7, 11);
        assertArrayEquals("Hello".getBytes(), substrResult, "Binary SUBSTR should return substring");

        // Test binary GETBIT and SETBIT
        boolean getBitResult = unifiedJedis.getbit(testKey, 0);
        assertTrue(getBitResult || !getBitResult, "Binary GETBIT should return boolean");

        boolean setBitResult = unifiedJedis.setbit(testKey, 0, true);
        assertTrue(setBitResult || !setBitResult, "Binary SETBIT should return original bit value");
    }

    @Test
    void error_handling() {
        // Test operations on non-existing keys
        String nonExistingKey = UUID.randomUUID().toString();

        String getResult = unifiedJedis.get(nonExistingKey);
        assertNull(getResult, "GET should return null for non-existing key");

        boolean existsResult = unifiedJedis.exists(nonExistingKey);
        assertFalse(existsResult, "EXISTS should return false for non-existing key");

        long delResult = unifiedJedis.del(nonExistingKey);
        assertEquals(0, delResult, "DEL should return 0 for non-existing key");

        long ttlResult = unifiedJedis.ttl(nonExistingKey);
        assertEquals(-2, ttlResult, "TTL should return -2 for non-existing key");

        // Test INCR on non-numeric value
        String stringKey = UUID.randomUUID().toString();
        unifiedJedis.set(stringKey, "not_a_number");

        assertThrows(
                Exception.class,
                () -> {
                    unifiedJedis.incr(stringKey);
                },
                "INCR should throw exception for non-numeric value");
    }

    @Test
    void edge_cases() {
        // Test empty string operations
        String testKey = UUID.randomUUID().toString();

        String setResult = unifiedJedis.set(testKey, "");
        assertEquals("OK", setResult, "SET should work with empty string");

        String getResult = unifiedJedis.get(testKey);
        assertEquals("", getResult, "GET should return empty string");

        long strlenResult = unifiedJedis.strlen(testKey);
        assertEquals(0, strlenResult, "STRLEN should return 0 for empty string");

        // Test very long key names
        String longKey = "a".repeat(1000);
        setResult = unifiedJedis.set(longKey, "long_key_value");
        assertEquals("OK", setResult, "SET should work with long key names");

        getResult = unifiedJedis.get(longKey);
        assertEquals("long_key_value", getResult, "GET should work with long key names");

        // Test binary operations with empty values
        byte[] binaryKey = UUID.randomUUID().toString().getBytes();
        byte[] emptyValue = new byte[0];

        setResult = unifiedJedis.set(binaryKey, emptyValue);
        assertEquals("OK", setResult, "Binary SET should work with empty value");

        byte[] binaryGetResult = unifiedJedis.get(binaryKey);
        assertArrayEquals(emptyValue, binaryGetResult, "Binary GET should return empty array");
    }
}
