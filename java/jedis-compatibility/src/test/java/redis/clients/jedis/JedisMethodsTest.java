/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.resps.AccessControlUser;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.resps.StreamInfo;
import redis.clients.jedis.resps.StreamPendingSummary;

/**
 * Unit tests for Jedis method signatures and API contracts. Tests that required methods exist with
 * correct signatures without executing them.
 */
public class JedisMethodsTest {

    @Test
    public void testDelMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test del(String key) method exists
        Method delSingleKey = jedisClass.getMethod("del", String.class);
        assertEquals(long.class, delSingleKey.getReturnType());

        // Test del(String... keys) method exists
        Method delMultipleKeys = jedisClass.getMethod("del", String[].class);
        assertEquals(long.class, delMultipleKeys.getReturnType());
    }

    @Test
    public void testKeysMethodSignature() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test keys(String pattern) method exists
        Method keysMethod = jedisClass.getMethod("keys", String.class);
        assertEquals(Set.class, keysMethod.getReturnType());
    }

    @Test
    public void testBasicMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test basic methods exist
        Method setMethod = jedisClass.getMethod("set", String.class, String.class);
        assertEquals(String.class, setMethod.getReturnType());

        Method getMethod = jedisClass.getMethod("get", String.class);
        assertEquals(String.class, getMethod.getReturnType());

        Method pingMethod = jedisClass.getMethod("ping");
        assertEquals(String.class, pingMethod.getReturnType());

        Method pingWithMessageMethod = jedisClass.getMethod("ping", String.class);
        assertEquals(String.class, pingWithMessageMethod.getReturnType());
    }

    @Test
    public void testJedisMethodsExist() {
        // Test that we can get method references without creating instances
        assertDoesNotThrow(
                () -> {
                    Class<Jedis> jedisClass = Jedis.class;

                    // Verify the methods exist
                    assertNotNull(jedisClass.getMethod("del", String.class));
                    assertNotNull(jedisClass.getMethod("del", String[].class));
                    assertNotNull(jedisClass.getMethod("keys", String.class));
                    assertNotNull(jedisClass.getMethod("set", String.class, String.class));
                    assertNotNull(jedisClass.getMethod("get", String.class));
                    assertNotNull(jedisClass.getMethod("ping"));
                    assertNotNull(jedisClass.getMethod("ping", String.class));
                });
    }

    @Test
    public void testMethodParameterTypes() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test del method parameter types
        Method delSingle = jedisClass.getMethod("del", String.class);
        Class<?>[] delSingleParams = delSingle.getParameterTypes();
        assertEquals(1, delSingleParams.length);
        assertEquals(String.class, delSingleParams[0]);

        Method delMultiple = jedisClass.getMethod("del", String[].class);
        Class<?>[] delMultipleParams = delMultiple.getParameterTypes();
        assertEquals(1, delMultipleParams.length);
        assertEquals(String[].class, delMultipleParams[0]);

        // Test keys method parameter types
        Method keys = jedisClass.getMethod("keys", String.class);
        Class<?>[] keysParams = keys.getParameterTypes();
        assertEquals(1, keysParams.length);
        assertEquals(String.class, keysParams[0]);
    }

    @Test
    public void testJedisCloseable() {
        // Test that Jedis implements Closeable
        assertTrue(java.io.Closeable.class.isAssignableFrom(Jedis.class));
    }

    @Test
    public void testJedisConstructorSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test that constructors exist
        assertNotNull(jedisClass.getConstructor());
        assertNotNull(jedisClass.getConstructor(String.class, int.class));
        assertNotNull(jedisClass.getConstructor(String.class, int.class, boolean.class));
        assertNotNull(jedisClass.getConstructor(String.class, int.class, int.class));
        assertNotNull(jedisClass.getConstructor(String.class, int.class, JedisClientConfig.class));
    }

    @Test
    public void testJedisStateManagementMethods() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test state management methods exist
        Method isClosedMethod = jedisClass.getMethod("isClosed");
        assertEquals(boolean.class, isClosedMethod.getReturnType());

        Method getConfigMethod = jedisClass.getMethod("getConfig");
        assertEquals(JedisClientConfig.class, getConfigMethod.getReturnType());

        Method closeMethod = jedisClass.getMethod("close");
        assertEquals(void.class, closeMethod.getReturnType());
    }

    @Test
    public void testAclMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        Method aclList = jedisClass.getMethod("aclList");
        assertEquals(List.class, aclList.getReturnType());

        Method aclGetUser = jedisClass.getMethod("aclGetUser", String.class);
        assertEquals(AccessControlUser.class, aclGetUser.getReturnType());

        Method aclSetUserNoRules = jedisClass.getMethod("aclSetUser", String.class);
        assertEquals(String.class, aclSetUserNoRules.getReturnType());

        Method aclSetUserWithRules = jedisClass.getMethod("aclSetUser", String.class, String[].class);
        assertEquals(String.class, aclSetUserWithRules.getReturnType());

        Method aclDelUser = jedisClass.getMethod("aclDelUser", String[].class);
        assertEquals(long.class, aclDelUser.getReturnType());

        Method aclCatNoArg = jedisClass.getMethod("aclCat");
        assertEquals(List.class, aclCatNoArg.getReturnType());

        Method aclCatCategory = jedisClass.getMethod("aclCat", String.class);
        assertEquals(List.class, aclCatCategory.getReturnType());

        Method aclGenPassNoArg = jedisClass.getMethod("aclGenPass");
        assertEquals(String.class, aclGenPassNoArg.getReturnType());

        Method aclGenPassBits = jedisClass.getMethod("aclGenPass", int.class);
        assertEquals(String.class, aclGenPassBits.getReturnType());

        Method aclLogNoArg = jedisClass.getMethod("aclLog");
        assertEquals(List.class, aclLogNoArg.getReturnType());

        Method aclLogCount = jedisClass.getMethod("aclLog", int.class);
        assertEquals(List.class, aclLogCount.getReturnType());

        Method aclLogReset = jedisClass.getMethod("aclLogReset");
        assertEquals(String.class, aclLogReset.getReturnType());

        Method aclWhoAmI = jedisClass.getMethod("aclWhoAmI");
        assertEquals(String.class, aclWhoAmI.getReturnType());

        Method aclUsers = jedisClass.getMethod("aclUsers");
        assertEquals(List.class, aclUsers.getReturnType());

        Method aclSave = jedisClass.getMethod("aclSave");
        assertEquals(String.class, aclSave.getReturnType());

        Method aclLoad = jedisClass.getMethod("aclLoad");
        assertEquals(String.class, aclLoad.getReturnType());

        Method aclDryRun =
                jedisClass.getMethod("aclDryRun", String.class, String.class, String[].class);
        assertEquals(String.class, aclDryRun.getReturnType());
    public void testStreamMethodSignaturesExist() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XADD
        assertNotNull(jedisClass.getMethod("xadd", String.class, Map.class));
        assertNotNull(jedisClass.getMethod("xadd", String.class, StreamEntryID.class, Map.class));
        assertNotNull(jedisClass.getMethod("xadd", String.class, Map.class, boolean.class));

        // XLEN, XDEL
        assertNotNull(jedisClass.getMethod("xlen", String.class));
        assertNotNull(jedisClass.getMethod("xdel", String.class, String[].class));
        assertNotNull(jedisClass.getMethod("xdel", String.class, StreamEntryID[].class));

        // XRANGE, XREVRANGE
        assertNotNull(jedisClass.getMethod("xrange", String.class, String.class, String.class));
        assertNotNull(
                jedisClass.getMethod("xrange", String.class, String.class, String.class, long.class));
        assertNotNull(jedisClass.getMethod("xrevrange", String.class, String.class, String.class));
        assertNotNull(
                jedisClass.getMethod("xrevrange", String.class, String.class, String.class, long.class));

        // XREAD
        assertNotNull(jedisClass.getMethod("xread", Map.class));
        assertNotNull(jedisClass.getMethod("xread", Long.class, Long.class, Map.class));

        // XTRIM
        assertNotNull(jedisClass.getMethod("xtrim", String.class, long.class));
        assertNotNull(jedisClass.getMethod("xtrim", String.class, long.class, boolean.class));
        assertNotNull(jedisClass.getMethod("xtrim", String.class, String.class));

        // XGROUP
        assertNotNull(jedisClass.getMethod("xgroupCreate", String.class, String.class, String.class));
        assertNotNull(
                jedisClass.getMethod(
                        "xgroupCreate", String.class, String.class, String.class, boolean.class));
        assertNotNull(jedisClass.getMethod("xgroupDestroy", String.class, String.class));
        assertNotNull(jedisClass.getMethod("xgroupSetId", String.class, String.class, String.class));
        assertNotNull(
                jedisClass.getMethod("xgroupCreateConsumer", String.class, String.class, String.class));
        assertNotNull(
                jedisClass.getMethod("xgroupDelConsumer", String.class, String.class, String.class));

        // XREADGROUP, XACK
        assertNotNull(jedisClass.getMethod("xreadgroup", String.class, String.class, Map.class));
        assertNotNull(jedisClass.getMethod("xack", String.class, String.class, String[].class));
        assertNotNull(jedisClass.getMethod("xack", String.class, String.class, StreamEntryID[].class));

        // XPENDING
        assertNotNull(jedisClass.getMethod("xpending", String.class, String.class));
        assertNotNull(
                jedisClass.getMethod(
                        "xpending",
                        String.class,
                        String.class,
                        glide.api.models.commands.stream.StreamRange.class,
                        glide.api.models.commands.stream.StreamRange.class,
                        long.class));
        assertNotNull(
                jedisClass.getMethod(
                        "xpending", String.class, String.class, String.class, String.class, long.class));

        // XCLAIM, XAUTOCLAIM
        assertNotNull(
                jedisClass.getMethod(
                        "xclaim", String.class, String.class, String.class, long.class, String[].class));
        assertNotNull(
                jedisClass.getMethod(
                        "xautoclaim", String.class, String.class, String.class, long.class, String.class));
        assertNotNull(
                jedisClass.getMethod(
                        "xautoclaim",
                        String.class,
                        String.class,
                        String.class,
                        long.class,
                        String.class,
                        long.class));

        // XINFO
        assertNotNull(jedisClass.getMethod("xinfoStream", String.class));
        assertNotNull(jedisClass.getMethod("xinfoStreamAsInfo", String.class));
        assertNotNull(jedisClass.getMethod("xinfoGroups", String.class));
        assertNotNull(jedisClass.getMethod("xinfoConsumers", String.class, String.class));
    }

    @Test
    public void testStreamMethodReturnTypes() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        Method xadd = jedisClass.getMethod("xadd", String.class, Map.class);
        assertEquals(StreamEntryID.class, xadd.getReturnType());

        Method xlen = jedisClass.getMethod("xlen", String.class);
        assertEquals(long.class, xlen.getReturnType());

        Method xrange = jedisClass.getMethod("xrange", String.class, String.class, String.class);
        assertEquals(List.class, xrange.getReturnType());

        Method xread = jedisClass.getMethod("xread", Map.class);
        assertEquals(Map.class, xread.getReturnType());

        Method xpendingSummary = jedisClass.getMethod("xpending", String.class, String.class);
        assertEquals(StreamPendingSummary.class, xpendingSummary.getReturnType());

        Method xinfoStream = jedisClass.getMethod("xinfoStream", String.class);
        assertEquals(Map.class, xinfoStream.getReturnType());

        Method xinfoStreamAsInfo = jedisClass.getMethod("xinfoStreamAsInfo", String.class);
        assertEquals(StreamInfo.class, xinfoStreamAsInfo.getReturnType());

        Method xinfoGroups = jedisClass.getMethod("xinfoGroups", String.class);
        assertEquals(List.class, xinfoGroups.getReturnType());

        Method xinfoConsumers = jedisClass.getMethod("xinfoConsumers", String.class, String.class);
        assertEquals(List.class, xinfoConsumers.getReturnType());
    }

    @Test
    public void testAclMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        Method aclList = jedisClass.getMethod("aclList");
        assertEquals(List.class, aclList.getReturnType());

        Method aclGetUser = jedisClass.getMethod("aclGetUser", String.class);
        assertEquals(AccessControlUser.class, aclGetUser.getReturnType());

        Method aclSetUserNoRules = jedisClass.getMethod("aclSetUser", String.class);
        assertEquals(String.class, aclSetUserNoRules.getReturnType());

        Method aclSetUserWithRules = jedisClass.getMethod("aclSetUser", String.class, String[].class);
        assertEquals(String.class, aclSetUserWithRules.getReturnType());

        Method aclDelUser = jedisClass.getMethod("aclDelUser", String[].class);
        assertEquals(long.class, aclDelUser.getReturnType());

        Method aclCatNoArg = jedisClass.getMethod("aclCat");
        assertEquals(List.class, aclCatNoArg.getReturnType());

        Method aclCatCategory = jedisClass.getMethod("aclCat", String.class);
        assertEquals(List.class, aclCatCategory.getReturnType());

        Method aclGenPassNoArg = jedisClass.getMethod("aclGenPass");
        assertEquals(String.class, aclGenPassNoArg.getReturnType());

        Method aclGenPassBits = jedisClass.getMethod("aclGenPass", int.class);
        assertEquals(String.class, aclGenPassBits.getReturnType());

        Method aclLogNoArg = jedisClass.getMethod("aclLog");
        assertEquals(List.class, aclLogNoArg.getReturnType());

        Method aclLogCount = jedisClass.getMethod("aclLog", int.class);
        assertEquals(List.class, aclLogCount.getReturnType());

        Method aclLogReset = jedisClass.getMethod("aclLogReset");
        assertEquals(String.class, aclLogReset.getReturnType());

        Method aclWhoAmI = jedisClass.getMethod("aclWhoAmI");
        assertEquals(String.class, aclWhoAmI.getReturnType());

        Method aclUsers = jedisClass.getMethod("aclUsers");
        assertEquals(List.class, aclUsers.getReturnType());

        Method aclSave = jedisClass.getMethod("aclSave");
        assertEquals(String.class, aclSave.getReturnType());

        Method aclLoad = jedisClass.getMethod("aclLoad");
        assertEquals(String.class, aclLoad.getReturnType());

        Method aclDryRun =
                jedisClass.getMethod("aclDryRun", String.class, String.class, String[].class);
        assertEquals(String.class, aclDryRun.getReturnType());
    }

    @Test
    public void testStreamBinaryMethodSignaturesExist() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XADD binary with XAddParams
        assertNotNull(
                jedisClass.getMethod(
                        "xadd",
                        byte[].class,
                        redis.clients.jedis.params.XAddParams.class,
                        Map.class));

        // XLEN binary
        assertNotNull(jedisClass.getMethod("xlen", byte[].class));

        // XDEL binary
        assertNotNull(jedisClass.getMethod("xdel", byte[].class, byte[][].class));

        // XRANGE binary
        assertNotNull(jedisClass.getMethod("xrange", byte[].class, byte[].class, byte[].class));
        assertNotNull(
                jedisClass.getMethod("xrange", byte[].class, byte[].class, byte[].class, int.class));

        // XREVRANGE binary
        assertNotNull(jedisClass.getMethod("xrevrange", byte[].class, byte[].class, byte[].class));
        assertNotNull(
                jedisClass.getMethod(
                        "xrevrange", byte[].class, byte[].class, byte[].class, int.class));

        // XTRIM binary
        assertNotNull(jedisClass.getMethod("xtrim", byte[].class, long.class));
        assertNotNull(jedisClass.getMethod("xtrim", byte[].class, long.class, boolean.class));
        assertNotNull(
                jedisClass.getMethod(
                        "xtrim", byte[].class, redis.clients.jedis.params.XTrimParams.class));
    }

    @Test
    public void testStreamBinaryMethodReturnTypes() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XADD binary returns byte[]
        Method xaddBinary =
                jedisClass.getMethod(
                        "xadd",
                        byte[].class,
                        redis.clients.jedis.params.XAddParams.class,
                        Map.class);
        assertEquals(byte[].class, xaddBinary.getReturnType());

        // XLEN binary returns long
        Method xlenBinary = jedisClass.getMethod("xlen", byte[].class);
        assertEquals(long.class, xlenBinary.getReturnType());

        // XDEL binary returns long
        Method xdelBinary = jedisClass.getMethod("xdel", byte[].class, byte[][].class);
        assertEquals(long.class, xdelBinary.getReturnType());

        // XRANGE binary returns List
        Method xrangeBinary = jedisClass.getMethod("xrange", byte[].class, byte[].class, byte[].class);
        assertEquals(List.class, xrangeBinary.getReturnType());

        // XREVRANGE binary returns List
        Method xrevrangeBinary =
                jedisClass.getMethod("xrevrange", byte[].class, byte[].class, byte[].class);
        assertEquals(List.class, xrevrangeBinary.getReturnType());

        // XTRIM binary returns long
        Method xtrimBinary = jedisClass.getMethod("xtrim", byte[].class, long.class);
        assertEquals(long.class, xtrimBinary.getReturnType());
    }

    @Test
    public void testXAddParamsMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XADD with XAddParams for String keys
        assertNotNull(
                jedisClass.getMethod(
                        "xadd", String.class, redis.clients.jedis.params.XAddParams.class, Map.class));

        Method xaddWithParams =
                jedisClass.getMethod(
                        "xadd", String.class, redis.clients.jedis.params.XAddParams.class, Map.class);
        assertEquals(StreamEntryID.class, xaddWithParams.getReturnType());
    }

    @Test
    public void testXTrimParamsMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XTRIM with XTrimParams for String keys
        assertNotNull(
                jedisClass.getMethod(
                        "xtrim", String.class, redis.clients.jedis.params.XTrimParams.class));

        Method xtrimWithParams =
                jedisClass.getMethod(
                        "xtrim", String.class, redis.clients.jedis.params.XTrimParams.class);
        assertEquals(long.class, xtrimWithParams.getReturnType());
    }

    @Test
    public void testXAddParamsClassExists() {
        // Test that XAddParams class exists and has expected methods
        assertDoesNotThrow(
                () -> {
                    Class<?> xAddParamsClass = redis.clients.jedis.params.XAddParams.class;
                    assertNotNull(xAddParamsClass);

                    // Check factory method exists
                    assertNotNull(xAddParamsClass.getMethod("xAddParams"));

                    // Check builder methods exist
                    assertNotNull(xAddParamsClass.getMethod("id", String.class));
                    assertNotNull(xAddParamsClass.getMethod("id", StreamEntryID.class));
                    assertNotNull(xAddParamsClass.getMethod("noMkStream"));
                    assertNotNull(xAddParamsClass.getMethod("maxLen", long.class));
                    assertNotNull(xAddParamsClass.getMethod("maxLenExact", long.class));
                    assertNotNull(xAddParamsClass.getMethod("minId", String.class));
                    assertNotNull(xAddParamsClass.getMethod("minId", StreamEntryID.class));
                    assertNotNull(xAddParamsClass.getMethod("minIdExact", String.class));
                    assertNotNull(xAddParamsClass.getMethod("minIdExact", StreamEntryID.class));
                    assertNotNull(xAddParamsClass.getMethod("limit", long.class));
                });
    }

    @Test
    public void testXTrimParamsClassExists() {
        // Test that XTrimParams class exists and has expected methods
        assertDoesNotThrow(
                () -> {
                    Class<?> xTrimParamsClass = redis.clients.jedis.params.XTrimParams.class;
                    assertNotNull(xTrimParamsClass);

                    // Check factory method exists
                    assertNotNull(xTrimParamsClass.getMethod("xTrimParams"));

                    // Check builder methods exist
                    assertNotNull(xTrimParamsClass.getMethod("maxLen", long.class));
                    assertNotNull(xTrimParamsClass.getMethod("maxLenExact", long.class));
                    assertNotNull(xTrimParamsClass.getMethod("minId", String.class));
                    assertNotNull(xTrimParamsClass.getMethod("minId", StreamEntryID.class));
                    assertNotNull(xTrimParamsClass.getMethod("minIdExact", String.class));
                    assertNotNull(xTrimParamsClass.getMethod("minIdExact", StreamEntryID.class));
                    assertNotNull(xTrimParamsClass.getMethod("limit", long.class));
                });
    }
}
