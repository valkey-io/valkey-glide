/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.resps.AccessControlUser;
import redis.clients.jedis.resps.FunctionStats;
import redis.clients.jedis.util.KeyValue;

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
    public void testEvalMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test eval(String) exists
        Method evalSimple = jedisClass.getMethod("eval", String.class);
        assertEquals(Object.class, evalSimple.getReturnType());

        // Test eval(String, int, String...) exists
        Method evalWithKeys = jedisClass.getMethod("eval", String.class, int.class, String[].class);
        assertEquals(Object.class, evalWithKeys.getReturnType());

        // Test eval(String, List, List) exists
        Method evalWithLists = jedisClass.getMethod("eval", String.class, List.class, List.class);
        assertEquals(Object.class, evalWithLists.getReturnType());
    }

    @Test
    public void testEvalshaMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test evalsha(String) exists
        Method evalshaSimple = jedisClass.getMethod("evalsha", String.class);
        assertEquals(Object.class, evalshaSimple.getReturnType());

        // Test evalsha(String, int, String...) exists
        Method evalshaWithKeys =
                jedisClass.getMethod("evalsha", String.class, int.class, String[].class);
        assertEquals(Object.class, evalshaWithKeys.getReturnType());

        // Test evalsha(String, List, List) exists
        Method evalshaWithLists = jedisClass.getMethod("evalsha", String.class, List.class, List.class);
        assertEquals(Object.class, evalshaWithLists.getReturnType());
    }

    @Test
    public void testEvalReadonlyMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test evalReadonly(String, List, List) exists
        Method evalReadonly =
                jedisClass.getMethod("evalReadonly", String.class, List.class, List.class);
        assertEquals(Object.class, evalReadonly.getReturnType());

        // Test evalshaReadonly(String, List, List) exists
        Method evalshaReadonly =
                jedisClass.getMethod("evalshaReadonly", String.class, List.class, List.class);
        assertEquals(Object.class, evalshaReadonly.getReturnType());
    }

    @Test
    public void testScriptManagementMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test scriptLoad(String) exists
        Method scriptLoad = jedisClass.getMethod("scriptLoad", String.class);
        assertEquals(String.class, scriptLoad.getReturnType());

        // Test scriptExists(String...) exists
        Method scriptExists = jedisClass.getMethod("scriptExists", String[].class);
        assertEquals(List.class, scriptExists.getReturnType());

        // Test scriptFlush() exists
        Method scriptFlush = jedisClass.getMethod("scriptFlush");
        assertEquals(String.class, scriptFlush.getReturnType());

        // Test scriptFlush(FlushMode) exists
        Method scriptFlushWithMode =
                jedisClass.getMethod("scriptFlush", redis.clients.jedis.args.FlushMode.class);
        assertEquals(String.class, scriptFlushWithMode.getReturnType());

        // Test scriptKill() exists
        Method scriptKill = jedisClass.getMethod("scriptKill");
        assertEquals(String.class, scriptKill.getReturnType());
    }

    @Test
    public void testFcallMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test fcall(String, List, List) exists
        Method fcall = jedisClass.getMethod("fcall", String.class, List.class, List.class);
        assertEquals(Object.class, fcall.getReturnType());

        // Test fcallReadonly(String, List, List) exists
        Method fcallReadonly =
                jedisClass.getMethod("fcallReadonly", String.class, List.class, List.class);
        assertEquals(Object.class, fcallReadonly.getReturnType());
    }

    @Test
    public void testFunctionManagementMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test functionLoad(String) exists
        Method functionLoad = jedisClass.getMethod("functionLoad", String.class);
        assertEquals(String.class, functionLoad.getReturnType());

        // Test functionLoadReplace(String) exists
        Method functionLoadReplace = jedisClass.getMethod("functionLoadReplace", String.class);
        assertEquals(String.class, functionLoadReplace.getReturnType());

        // Test functionDelete(String) exists
        Method functionDelete = jedisClass.getMethod("functionDelete", String.class);
        assertEquals(String.class, functionDelete.getReturnType());

        // Test functionDump() exists
        Method functionDump = jedisClass.getMethod("functionDump");
        assertEquals(byte[].class, functionDump.getReturnType());

        // Test functionRestore(byte[]) exists
        Method functionRestore = jedisClass.getMethod("functionRestore", byte[].class);
        assertEquals(String.class, functionRestore.getReturnType());

        // Test functionRestore(byte[], FunctionRestorePolicy) exists
        Method functionRestoreWithPolicy =
                jedisClass.getMethod(
                        "functionRestore", byte[].class, redis.clients.jedis.args.FunctionRestorePolicy.class);
        assertEquals(String.class, functionRestoreWithPolicy.getReturnType());

        // Test functionFlush() exists
        Method functionFlush = jedisClass.getMethod("functionFlush");
        assertEquals(String.class, functionFlush.getReturnType());

        // Test functionFlush(FlushMode) exists
        Method functionFlushWithMode =
                jedisClass.getMethod("functionFlush", redis.clients.jedis.args.FlushMode.class);
        assertEquals(String.class, functionFlushWithMode.getReturnType());

        // Test functionKill() exists
        Method functionKill = jedisClass.getMethod("functionKill");
        assertEquals(String.class, functionKill.getReturnType());
    }

    @Test
    public void testFunctionListMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test functionList() exists
        Method functionList = jedisClass.getMethod("functionList");
        assertEquals(List.class, functionList.getReturnType());

        // Test functionList(String) exists
        Method functionListWithPattern = jedisClass.getMethod("functionList", String.class);
        assertEquals(List.class, functionListWithPattern.getReturnType());

        // Test functionListWithCode() exists
        Method functionListWithCode = jedisClass.getMethod("functionListWithCode");
        assertEquals(List.class, functionListWithCode.getReturnType());

        // Test functionListWithCode(String) exists
        Method functionListWithCodeAndPattern =
                jedisClass.getMethod("functionListWithCode", String.class);
        assertEquals(List.class, functionListWithCodeAndPattern.getReturnType());

        // Test functionStats() exists
        Method functionStats = jedisClass.getMethod("functionStats");
        assertEquals(FunctionStats.class, functionStats.getReturnType());
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
    public void testSortReadonlyMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test sortReadonly(String key) method exists
        Method sortReadonlyString = jedisClass.getMethod("sortReadonly", String.class);
        assertEquals(List.class, sortReadonlyString.getReturnType());

        // Test sortReadonly(byte[] key) method exists
        Method sortReadonlyBytes = jedisClass.getMethod("sortReadonly", byte[].class);
        assertEquals(List.class, sortReadonlyBytes.getReturnType());

        // Test sortReadonly(String key, SortingParams) method exists
        Method sortReadonlyStringParams =
                jedisClass.getMethod("sortReadonly", String.class, SortingParams.class);
        assertEquals(List.class, sortReadonlyStringParams.getReturnType());

        // Test sortReadonly(byte[] key, SortingParams) method exists
        Method sortReadonlyBytesParams =
                jedisClass.getMethod("sortReadonly", byte[].class, SortingParams.class);
        assertEquals(List.class, sortReadonlyBytesParams.getReturnType());
    }

    @Test
    public void testSortStoreMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test sort(String key, String dstkey) method exists
        Method sortStoreString = jedisClass.getMethod("sort", String.class, String.class);
        assertEquals(long.class, sortStoreString.getReturnType());

        // Test sort(byte[] key, byte[] dstkey) method exists
        Method sortStoreBytes = jedisClass.getMethod("sort", byte[].class, byte[].class);
        assertEquals(long.class, sortStoreBytes.getReturnType());

        // Test sort(String key, SortingParams, String dstkey) method exists
        Method sortStoreStringParams =
                jedisClass.getMethod("sort", String.class, SortingParams.class, String.class);
        assertEquals(long.class, sortStoreStringParams.getReturnType());

        // Test sort(byte[] key, SortingParams, byte[] dstkey) method exists
        Method sortStoreBytesParams =
                jedisClass.getMethod("sort", byte[].class, SortingParams.class, byte[].class);
        assertEquals(long.class, sortStoreBytesParams.getReturnType());
    }

    @Test
    public void testWaitMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test wait(long replicas, long timeout) method exists
        Method waitMethod = jedisClass.getMethod("wait", long.class, long.class);
        assertEquals(long.class, waitMethod.getReturnType());

        // Test waitAOF(long numlocal, long numreplicas, long timeout) method exists
        Method waitAOFMethod = jedisClass.getMethod("waitAOF", long.class, long.class, long.class);
        assertEquals(KeyValue.class, waitAOFMethod.getReturnType());
    }

    @Test
    public void testObjectMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test objectEncoding(String key) method exists
        Method objectEncodingString = jedisClass.getMethod("objectEncoding", String.class);
        assertEquals(String.class, objectEncodingString.getReturnType());

        // Test objectEncoding(byte[] key) method exists
        Method objectEncodingBytes = jedisClass.getMethod("objectEncoding", byte[].class);
        assertEquals(byte[].class, objectEncodingBytes.getReturnType());

        // Test objectFreq(String key) method exists
        Method objectFreqString = jedisClass.getMethod("objectFreq", String.class);
        assertEquals(Long.class, objectFreqString.getReturnType());

        // Test objectFreq(byte[] key) method exists
        Method objectFreqBytes = jedisClass.getMethod("objectFreq", byte[].class);
        assertEquals(Long.class, objectFreqBytes.getReturnType());

        // Test objectIdletime(String key) method exists
        Method objectIdletimeString = jedisClass.getMethod("objectIdletime", String.class);
        assertEquals(Long.class, objectIdletimeString.getReturnType());

        // Test objectIdletime(byte[] key) method exists
        Method objectIdletimeBytes = jedisClass.getMethod("objectIdletime", byte[].class);
        assertEquals(Long.class, objectIdletimeBytes.getReturnType());

        // Test objectRefcount(String key) method exists
        Method objectRefcountString = jedisClass.getMethod("objectRefcount", String.class);
        assertEquals(Long.class, objectRefcountString.getReturnType());

        // Test objectRefcount(byte[] key) method exists
        Method objectRefcountBytes = jedisClass.getMethod("objectRefcount", byte[].class);
        assertEquals(Long.class, objectRefcountBytes.getReturnType());
    }

    @Test
    public void testGeoMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test geoadd(String key, double longitude, double latitude, String member) method exists
        Method geoaddSingle =
                jedisClass.getMethod("geoadd", String.class, double.class, double.class, String.class);
        assertEquals(long.class, geoaddSingle.getReturnType());

        // Test geoadd(byte[] key, double longitude, double latitude, byte[] member) method exists
        Method geoaddSingleBytes =
                jedisClass.getMethod("geoadd", byte[].class, double.class, double.class, byte[].class);
        assertEquals(long.class, geoaddSingleBytes.getReturnType());

        // Test geoadd(String key, Map<String, GeoCoordinate>) method exists
        Method geoaddMap = jedisClass.getMethod("geoadd", String.class, Map.class);
        assertEquals(long.class, geoaddMap.getReturnType());

        // Test geoadd(byte[] key, Map<byte[], GeoCoordinate>) method exists
        Method geoaddMapBytes = jedisClass.getMethod("geoadd", byte[].class, Map.class);
        assertEquals(long.class, geoaddMapBytes.getReturnType());

        // Test geopos(String key, String... members) method exists
        Method geoposString = jedisClass.getMethod("geopos", String.class, String[].class);
        assertEquals(List.class, geoposString.getReturnType());

        // Test geopos(byte[] key, byte[]... members) method exists
        Method geoposBytes = jedisClass.getMethod("geopos", byte[].class, byte[][].class);
        assertEquals(List.class, geoposBytes.getReturnType());

        // Test geodist(String key, String member1, String member2) method exists
        Method geodistString =
                jedisClass.getMethod("geodist", String.class, String.class, String.class);
        assertEquals(Double.class, geodistString.getReturnType());

        // Test geodist(byte[] key, byte[] member1, byte[] member2) method exists
        Method geodistBytes = jedisClass.getMethod("geodist", byte[].class, byte[].class, byte[].class);
        assertEquals(Double.class, geodistBytes.getReturnType());

        // Test geodist with unit
        Method geodistStringUnit =
                jedisClass.getMethod(
                        "geodist",
                        String.class,
                        String.class,
                        String.class,
                        redis.clients.jedis.args.GeoUnit.class);
        assertEquals(Double.class, geodistStringUnit.getReturnType());

        // Test geohash(String key, String... members) method exists
        Method geohashString = jedisClass.getMethod("geohash", String.class, String[].class);
        assertEquals(List.class, geohashString.getReturnType());

        // Test geohash(byte[] key, byte[]... members) method exists
        Method geohashBytes = jedisClass.getMethod("geohash", byte[].class, byte[][].class);
        assertEquals(List.class, geohashBytes.getReturnType());

        // Test geosearch(String key, String member, double radius, GeoUnit unit) method exists
        Method geosearchString =
                jedisClass.getMethod(
                        "geosearch",
                        String.class,
                        String.class,
                        double.class,
                        redis.clients.jedis.args.GeoUnit.class);
        assertEquals(List.class, geosearchString.getReturnType());

        // Test geosearch(byte[] key, byte[] member, double radius, GeoUnit unit) method exists
        Method geosearchBytes =
                jedisClass.getMethod(
                        "geosearch",
                        byte[].class,
                        byte[].class,
                        double.class,
                        redis.clients.jedis.args.GeoUnit.class);
        assertEquals(List.class, geosearchBytes.getReturnType());

        // Test geosearchstore(String dest, String src, String member, double radius, GeoUnit unit)
        // method exists
        Method geosearchstoreString =
                jedisClass.getMethod(
                        "geosearchstore",
                        String.class,
                        String.class,
                        String.class,
                        double.class,
                        redis.clients.jedis.args.GeoUnit.class);
        assertEquals(long.class, geosearchstoreString.getReturnType());

        // Test geosearchstore(byte[] dest, byte[] src, byte[] member, double radius, GeoUnit unit)
        // method exists
        Method geosearchstoreBytes =
                jedisClass.getMethod(
                        "geosearchstore",
                        byte[].class,
                        byte[].class,
                        byte[].class,
                        double.class,
                        redis.clients.jedis.args.GeoUnit.class);
        assertEquals(long.class, geosearchstoreBytes.getReturnType());
    }
}
