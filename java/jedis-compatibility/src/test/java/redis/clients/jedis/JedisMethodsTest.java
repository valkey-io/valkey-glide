/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.args.FlushMode;
import redis.clients.jedis.args.FunctionRestorePolicy;
import redis.clients.jedis.params.LCSParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XTrimParams;
import redis.clients.jedis.resps.AccessControlUser;
import redis.clients.jedis.resps.FunctionStats;
import redis.clients.jedis.resps.LCSMatchResult;
import redis.clients.jedis.resps.StreamInfo;
import redis.clients.jedis.resps.StreamPendingSummary;
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
        Method scriptFlushWithMode = jedisClass.getMethod("scriptFlush", FlushMode.class);
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
                jedisClass.getMethod("functionRestore", byte[].class, FunctionRestorePolicy.class);
        assertEquals(String.class, functionRestoreWithPolicy.getReturnType());

        // Test functionFlush() exists
        Method functionFlush = jedisClass.getMethod("functionFlush");
        assertEquals(String.class, functionFlush.getReturnType());

        // Test functionFlush(FlushMode) exists
        Method functionFlushWithMode = jedisClass.getMethod("functionFlush", FlushMode.class);
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

    @Test
    public void testHashCommandMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test hrandfieldWithCount alias methods
        Method hrandfieldWithCountString =
                jedisClass.getMethod("hrandfieldWithCount", String.class, long.class);
        assertEquals(List.class, hrandfieldWithCountString.getReturnType());

        Method hrandfieldWithCountBinary =
                jedisClass.getMethod("hrandfieldWithCount", byte[].class, long.class);
        assertEquals(List.class, hrandfieldWithCountBinary.getReturnType());
    }

    @Test
    public void testStringCommandMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test msetnx methods
        Method msetnxString = jedisClass.getMethod("msetnx", String[].class);
        assertEquals(long.class, msetnxString.getReturnType());

        Method msetnxBinary = jedisClass.getMethod("msetnx", byte[][].class);
        assertEquals(long.class, msetnxBinary.getReturnType());

        // Test setrange methods
        Method setrangeString =
                jedisClass.getMethod("setrange", String.class, long.class, String.class);
        assertEquals(long.class, setrangeString.getReturnType());

        Method setrangeBinary =
                jedisClass.getMethod("setrange", byte[].class, long.class, byte[].class);
        assertEquals(long.class, setrangeBinary.getReturnType());

        // Test getrange methods
        Method getrangeString = jedisClass.getMethod("getrange", String.class, long.class, long.class);
        assertEquals(String.class, getrangeString.getReturnType());

        Method getrangeBinary = jedisClass.getMethod("getrange", byte[].class, long.class, long.class);
        assertEquals(byte[].class, getrangeBinary.getReturnType());

        // Test lcs methods (takes LCSParams and returns LCSMatchResult)
        Method lcsString = jedisClass.getMethod("lcs", String.class, String.class, LCSParams.class);
        assertEquals(LCSMatchResult.class, lcsString.getReturnType());

        Method lcsBinary = jedisClass.getMethod("lcs", byte[].class, byte[].class, LCSParams.class);
        assertEquals(LCSMatchResult.class, lcsBinary.getReturnType());
    }

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
    public void testStreamBinaryMethodSignaturesExist() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XADD binary with XAddParams
        assertNotNull(jedisClass.getMethod("xadd", byte[].class, XAddParams.class, Map.class));

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
                jedisClass.getMethod("xrevrange", byte[].class, byte[].class, byte[].class, int.class));

        // XTRIM binary
        assertNotNull(jedisClass.getMethod("xtrim", byte[].class, long.class));
        assertNotNull(jedisClass.getMethod("xtrim", byte[].class, long.class, boolean.class));
        assertNotNull(jedisClass.getMethod("xtrim", byte[].class, XTrimParams.class));
    }

    @Test
    public void testStreamBinaryMethodReturnTypes() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XADD binary returns byte[]
        Method xaddBinary = jedisClass.getMethod("xadd", byte[].class, XAddParams.class, Map.class);
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
        assertNotNull(jedisClass.getMethod("xadd", String.class, XAddParams.class, Map.class));

        Method xaddWithParams = jedisClass.getMethod("xadd", String.class, XAddParams.class, Map.class);
        assertEquals(StreamEntryID.class, xaddWithParams.getReturnType());
    }

    @Test
    public void testXTrimParamsMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // XTRIM with XTrimParams for String keys
        assertNotNull(jedisClass.getMethod("xtrim", String.class, XTrimParams.class));

        Method xtrimWithParams = jedisClass.getMethod("xtrim", String.class, XTrimParams.class);
        assertEquals(long.class, xtrimWithParams.getReturnType());
    }

    @Test
    public void testXAddParamsClassExists() {
        // Test that XAddParams class exists and has expected methods
        assertDoesNotThrow(
                () -> {
                    Class<?> xAddParamsClass = XAddParams.class;
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
                    Class<?> xTrimParamsClass = XTrimParams.class;
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

    @Test
    public void testSortedSetMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test zadd methods
        Method zaddSingle = jedisClass.getMethod("zadd", String.class, double.class, String.class);
        assertEquals(long.class, zaddSingle.getReturnType());

        Method zaddMap = jedisClass.getMethod("zadd", String.class, java.util.Map.class);
        assertEquals(long.class, zaddMap.getReturnType());

        Method zaddBinary = jedisClass.getMethod("zadd", byte[].class, double.class, byte[].class);
        assertEquals(long.class, zaddBinary.getReturnType());

        // Test zadd with ZAddParams
        Method zaddWithParams =
                jedisClass.getMethod(
                        "zadd",
                        String.class,
                        double.class,
                        String.class,
                        redis.clients.jedis.params.ZAddParams.class);
        assertEquals(long.class, zaddWithParams.getReturnType());

        Method zaddWithParamsBinary =
                jedisClass.getMethod(
                        "zadd",
                        byte[].class,
                        double.class,
                        byte[].class,
                        redis.clients.jedis.params.ZAddParams.class);
        assertEquals(long.class, zaddWithParamsBinary.getReturnType());

        // Test zaddIncr methods
        Method zaddIncr = jedisClass.getMethod("zaddIncr", String.class, double.class, String.class);
        assertEquals(double.class, zaddIncr.getReturnType());

        Method zaddIncrBinary =
                jedisClass.getMethod("zaddIncr", byte[].class, double.class, byte[].class);
        assertEquals(double.class, zaddIncrBinary.getReturnType());

        // Test zrem methods
        Method zrem = jedisClass.getMethod("zrem", String.class, String[].class);
        assertEquals(long.class, zrem.getReturnType());

        Method zremBinary = jedisClass.getMethod("zrem", byte[].class, byte[][].class);
        assertEquals(long.class, zremBinary.getReturnType());

        // Test zcard methods
        Method zcard = jedisClass.getMethod("zcard", String.class);
        assertEquals(long.class, zcard.getReturnType());

        Method zcardBinary = jedisClass.getMethod("zcard", byte[].class);
        assertEquals(long.class, zcardBinary.getReturnType());

        // Test zscore methods
        Method zscore = jedisClass.getMethod("zscore", String.class, String.class);
        assertEquals(Double.class, zscore.getReturnType());

        Method zscoreBinary = jedisClass.getMethod("zscore", byte[].class, byte[].class);
        assertEquals(Double.class, zscoreBinary.getReturnType());

        // Test zmscore methods
        Method zmscore = jedisClass.getMethod("zmscore", String.class, String[].class);
        assertEquals(java.util.List.class, zmscore.getReturnType());

        Method zmscoreBinary = jedisClass.getMethod("zmscore", byte[].class, byte[][].class);
        assertEquals(java.util.List.class, zmscoreBinary.getReturnType());

        // Test zrange methods
        Method zrange = jedisClass.getMethod("zrange", String.class, long.class, long.class);
        assertEquals(java.util.List.class, zrange.getReturnType());

        Method zrangeBinary = jedisClass.getMethod("zrange", byte[].class, long.class, long.class);
        assertEquals(java.util.List.class, zrangeBinary.getReturnType());

        // Test zrangeWithScores methods
        Method zrangeWithScores =
                jedisClass.getMethod("zrangeWithScores", String.class, long.class, long.class);
        assertEquals(java.util.List.class, zrangeWithScores.getReturnType());

        Method zrangeWithScoresBinary =
                jedisClass.getMethod("zrangeWithScores", byte[].class, long.class, long.class);
        assertEquals(java.util.List.class, zrangeWithScoresBinary.getReturnType());

        // Test zrank methods
        Method zrank = jedisClass.getMethod("zrank", String.class, String.class);
        assertEquals(Long.class, zrank.getReturnType());

        Method zrankBinary = jedisClass.getMethod("zrank", byte[].class, byte[].class);
        assertEquals(Long.class, zrankBinary.getReturnType());

        // Test zrevrank methods
        Method zrevrank = jedisClass.getMethod("zrevrank", String.class, String.class);
        assertEquals(Long.class, zrevrank.getReturnType());

        Method zrevrankBinary = jedisClass.getMethod("zrevrank", byte[].class, byte[].class);
        assertEquals(Long.class, zrevrankBinary.getReturnType());

        // Test zcount methods
        Method zcount = jedisClass.getMethod("zcount", String.class, double.class, double.class);
        assertEquals(long.class, zcount.getReturnType());

        Method zcountBinary = jedisClass.getMethod("zcount", byte[].class, double.class, double.class);
        assertEquals(long.class, zcountBinary.getReturnType());

        // Test zincrby methods
        Method zincrby = jedisClass.getMethod("zincrby", String.class, double.class, String.class);
        assertEquals(double.class, zincrby.getReturnType());

        Method zincrbyBinary =
                jedisClass.getMethod("zincrby", byte[].class, double.class, byte[].class);
        assertEquals(double.class, zincrbyBinary.getReturnType());

        // Test zpopmin methods
        Method zpopmin = jedisClass.getMethod("zpopmin", String.class, int.class);
        assertEquals(java.util.List.class, zpopmin.getReturnType());

        Method zpopminBinary = jedisClass.getMethod("zpopmin", byte[].class, int.class);
        assertEquals(java.util.List.class, zpopminBinary.getReturnType());

        // Test zpopmax methods
        Method zpopmax = jedisClass.getMethod("zpopmax", String.class, int.class);
        assertEquals(java.util.List.class, zpopmax.getReturnType());

        Method zpopmaxBinary = jedisClass.getMethod("zpopmax", byte[].class, int.class);
        assertEquals(java.util.List.class, zpopmaxBinary.getReturnType());

        // Test zunionstore methods
        Method zunionstore = jedisClass.getMethod("zunionstore", String.class, String[].class);
        assertEquals(long.class, zunionstore.getReturnType());

        Method zunionstoreBinary = jedisClass.getMethod("zunionstore", byte[].class, byte[][].class);
        assertEquals(long.class, zunionstoreBinary.getReturnType());

        // Test zinterstore methods
        Method zinterstore = jedisClass.getMethod("zinterstore", String.class, String[].class);
        assertEquals(long.class, zinterstore.getReturnType());

        Method zinterstoreBinary = jedisClass.getMethod("zinterstore", byte[].class, byte[][].class);
        assertEquals(long.class, zinterstoreBinary.getReturnType());

        // Test zremrangebyrank methods
        Method zremrangebyrank =
                jedisClass.getMethod("zremrangebyrank", String.class, long.class, long.class);
        assertEquals(long.class, zremrangebyrank.getReturnType());

        Method zremrangebyrankBinary =
                jedisClass.getMethod("zremrangebyrank", byte[].class, long.class, long.class);
        assertEquals(long.class, zremrangebyrankBinary.getReturnType());

        // Test zremrangebyscore methods
        Method zremrangebyscore =
                jedisClass.getMethod("zremrangebyscore", String.class, double.class, double.class);
        assertEquals(long.class, zremrangebyscore.getReturnType());

        Method zremrangebyscoreBinary =
                jedisClass.getMethod("zremrangebyscore", byte[].class, double.class, double.class);
        assertEquals(long.class, zremrangebyscoreBinary.getReturnType());

        // Test zscan methods
        Method zscan = jedisClass.getMethod("zscan", String.class, String.class);
        assertEquals(redis.clients.jedis.resps.ScanResult.class, zscan.getReturnType());

        Method zscanBinary = jedisClass.getMethod("zscan", byte[].class, byte[].class);
        assertEquals(redis.clients.jedis.resps.ScanResult.class, zscanBinary.getReturnType());

        // Test zrangestore methods
        Method zrangestore =
                jedisClass.getMethod("zrangestore", String.class, String.class, long.class, long.class);
        assertEquals(long.class, zrangestore.getReturnType());

        Method zrangestoreBinary =
                jedisClass.getMethod("zrangestore", byte[].class, byte[].class, long.class, long.class);
        assertEquals(long.class, zrangestoreBinary.getReturnType());

        // Test zrankWithScore methods
        Method zrankWithScore = jedisClass.getMethod("zrankWithScore", String.class, String.class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, zrankWithScore.getReturnType());

        Method zrankWithScoreBinary =
                jedisClass.getMethod("zrankWithScore", byte[].class, byte[].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, zrankWithScoreBinary.getReturnType());

        // Test zrevrankWithScore methods
        Method zrevrankWithScore =
                jedisClass.getMethod("zrevrankWithScore", String.class, String.class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, zrevrankWithScore.getReturnType());

        Method zrevrankWithScoreBinary =
                jedisClass.getMethod("zrevrankWithScore", byte[].class, byte[].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, zrevrankWithScoreBinary.getReturnType());

        // Test zlexcount methods
        Method zlexcount = jedisClass.getMethod("zlexcount", String.class, String.class, String.class);
        assertEquals(long.class, zlexcount.getReturnType());

        Method zlexcountBinary =
                jedisClass.getMethod("zlexcount", byte[].class, byte[].class, byte[].class);
        assertEquals(long.class, zlexcountBinary.getReturnType());

        // Test bzpopmin methods
        Method bzpopmin = jedisClass.getMethod("bzpopmin", double.class, String[].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, bzpopmin.getReturnType());

        Method bzpopminBinary = jedisClass.getMethod("bzpopmin", double.class, byte[][].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, bzpopminBinary.getReturnType());

        // Test bzpopmax methods
        Method bzpopmax = jedisClass.getMethod("bzpopmax", double.class, String[].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, bzpopmax.getReturnType());

        Method bzpopmaxBinary = jedisClass.getMethod("bzpopmax", double.class, byte[][].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, bzpopmaxBinary.getReturnType());

        // Test zdiff methods
        Method zdiff = jedisClass.getMethod("zdiff", String[].class);
        assertEquals(java.util.List.class, zdiff.getReturnType());

        Method zdiffBinary = jedisClass.getMethod("zdiff", byte[][].class);
        assertEquals(java.util.List.class, zdiffBinary.getReturnType());

        // Test zdiffWithScores methods
        Method zdiffWithScores = jedisClass.getMethod("zdiffWithScores", String[].class);
        assertEquals(java.util.List.class, zdiffWithScores.getReturnType());

        Method zdiffWithScoresBinary = jedisClass.getMethod("zdiffWithScores", byte[][].class);
        assertEquals(java.util.List.class, zdiffWithScoresBinary.getReturnType());

        // Test zdiffstore methods
        Method zdiffstore = jedisClass.getMethod("zdiffstore", String.class, String[].class);
        assertEquals(long.class, zdiffstore.getReturnType());

        Method zdiffstoreBinary = jedisClass.getMethod("zdiffstore", byte[].class, byte[][].class);
        assertEquals(long.class, zdiffstoreBinary.getReturnType());

        // Test zunion methods
        Method zunion = jedisClass.getMethod("zunion", String[].class);
        assertEquals(java.util.List.class, zunion.getReturnType());

        Method zunionBinary = jedisClass.getMethod("zunion", byte[][].class);
        assertEquals(java.util.List.class, zunionBinary.getReturnType());

        // Test zunionWithScores methods
        Method zunionWithScores = jedisClass.getMethod("zunionWithScores", String[].class);
        assertEquals(java.util.List.class, zunionWithScores.getReturnType());

        Method zunionWithScoresBinary = jedisClass.getMethod("zunionWithScores", byte[][].class);
        assertEquals(java.util.List.class, zunionWithScoresBinary.getReturnType());

        // Test zinter methods
        Method zinter = jedisClass.getMethod("zinter", String[].class);
        assertEquals(java.util.List.class, zinter.getReturnType());

        Method zinterBinary = jedisClass.getMethod("zinter", byte[][].class);
        assertEquals(java.util.List.class, zinterBinary.getReturnType());

        // Test zinterWithScores methods
        Method zinterWithScores = jedisClass.getMethod("zinterWithScores", String[].class);
        assertEquals(java.util.List.class, zinterWithScores.getReturnType());

        Method zinterWithScoresBinary = jedisClass.getMethod("zinterWithScores", byte[][].class);
        assertEquals(java.util.List.class, zinterWithScoresBinary.getReturnType());

        // Test zintercard methods
        Method zintercard = jedisClass.getMethod("zintercard", String[].class);
        assertEquals(long.class, zintercard.getReturnType());

        Method zintercardBinary = jedisClass.getMethod("zintercard", byte[][].class);
        assertEquals(long.class, zintercardBinary.getReturnType());

        // Test zmpop methods (binary only, uses SortedSetOption)
        Method zmpop =
                jedisClass.getMethod(
                        "zmpop", redis.clients.jedis.args.SortedSetOption.class, byte[][].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, zmpop.getReturnType());

        // Test bzmpop methods (binary only, uses SortedSetOption)
        Method bzmpop =
                jedisClass.getMethod(
                        "bzmpop", double.class, redis.clients.jedis.args.SortedSetOption.class, byte[][].class);
        assertEquals(redis.clients.jedis.resps.KeyValue.class, bzmpop.getReturnType());

        // Test zremrangebylex methods
        Method zremrangebylex =
                jedisClass.getMethod("zremrangebylex", String.class, String.class, String.class);
        assertEquals(long.class, zremrangebylex.getReturnType());

        Method zremrangebylexBinary =
                jedisClass.getMethod("zremrangebylex", byte[].class, byte[].class, byte[].class);
        assertEquals(long.class, zremrangebylexBinary.getReturnType());

        // Test zrandmember methods
        Method zrandmember = jedisClass.getMethod("zrandmember", String.class);
        assertEquals(String.class, zrandmember.getReturnType());

        Method zrandmemberBinary = jedisClass.getMethod("zrandmember", byte[].class);
        assertEquals(byte[].class, zrandmemberBinary.getReturnType());

        // Test zrandmemberWithCount methods
        Method zrandmemberWithCount =
                jedisClass.getMethod("zrandmemberWithCount", String.class, long.class);
        assertEquals(java.util.List.class, zrandmemberWithCount.getReturnType());

        Method zrandmemberWithCountBinary =
                jedisClass.getMethod("zrandmemberWithCount", byte[].class, long.class);
        assertEquals(java.util.List.class, zrandmemberWithCountBinary.getReturnType());

        // Test zrandmemberWithCountWithScores methods
        Method zrandmemberWithCountWithScores =
                jedisClass.getMethod("zrandmemberWithCountWithScores", String.class, long.class);
        assertEquals(java.util.List.class, zrandmemberWithCountWithScores.getReturnType());

        Method zrandmemberWithCountWithScoresBinary =
                jedisClass.getMethod("zrandmemberWithCountWithScores", byte[].class, long.class);
        assertEquals(java.util.List.class, zrandmemberWithCountWithScoresBinary.getReturnType());

        // Test zrevrange methods
        Method zrevrange = jedisClass.getMethod("zrevrange", String.class, long.class, long.class);
        assertEquals(java.util.List.class, zrevrange.getReturnType());

        Method zrevrangeBinary =
                jedisClass.getMethod("zrevrange", byte[].class, long.class, long.class);
        assertEquals(java.util.List.class, zrevrangeBinary.getReturnType());

        // Test zscan with ScanParams
        Method zscanWithParams =
                jedisClass.getMethod(
                        "zscan", String.class, String.class, redis.clients.jedis.params.ScanParams.class);
        assertEquals(redis.clients.jedis.resps.ScanResult.class, zscanWithParams.getReturnType());

        Method zscanWithParamsBinary =
                jedisClass.getMethod(
                        "zscan", byte[].class, byte[].class, redis.clients.jedis.params.ScanParams.class);
        assertEquals(redis.clients.jedis.resps.ScanResult.class, zscanWithParamsBinary.getReturnType());

        // Test zrange with ZRangeParams
        Method zrangeWithParams =
                jedisClass.getMethod("zrange", String.class, redis.clients.jedis.params.ZRangeParams.class);
        assertEquals(java.util.List.class, zrangeWithParams.getReturnType());

        Method zrangeWithParamsBinary =
                jedisClass.getMethod("zrange", byte[].class, redis.clients.jedis.params.ZRangeParams.class);
        assertEquals(java.util.List.class, zrangeWithParamsBinary.getReturnType());

        // Test zrangestore with ZRangeParams
        Method zrangestoreWithParams =
                jedisClass.getMethod(
                        "zrangestore",
                        String.class,
                        String.class,
                        redis.clients.jedis.params.ZRangeParams.class);
        assertEquals(long.class, zrangestoreWithParams.getReturnType());

        Method zrangestoreWithParamsBinary =
                jedisClass.getMethod(
                        "zrangestore",
                        byte[].class,
                        byte[].class,
                        redis.clients.jedis.params.ZRangeParams.class);
        assertEquals(long.class, zrangestoreWithParamsBinary.getReturnType());
    }

    @Test
    public void testSetMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        Method saddString = jedisClass.getMethod("sadd", String.class, String[].class);
        assertEquals(long.class, saddString.getReturnType());

        Method saddBinary = jedisClass.getMethod("sadd", byte[].class, byte[][].class);
        assertEquals(long.class, saddBinary.getReturnType());

        Method sremString = jedisClass.getMethod("srem", String.class, String[].class);
        assertEquals(long.class, sremString.getReturnType());

        Method sremBinary = jedisClass.getMethod("srem", byte[].class, byte[][].class);
        assertEquals(long.class, sremBinary.getReturnType());

        Method smembersString = jedisClass.getMethod("smembers", String.class);
        assertEquals(Set.class, smembersString.getReturnType());

        Method smembersBinary = jedisClass.getMethod("smembers", byte[].class);
        assertEquals(Set.class, smembersBinary.getReturnType());
    }

    @Test
    public void saddStringSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("sadd", String.class, String[].class);
        assertEquals(long.class, m.getReturnType());
        assertEquals(2, m.getParameterCount());
        assertEquals(String.class, m.getParameterTypes()[0]);
        assertEquals(String[].class, m.getParameterTypes()[1]);
    }

    @Test
    public void saddBinarySignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("sadd", byte[].class, byte[][].class);
        assertEquals(long.class, m.getReturnType());
        assertEquals(2, m.getParameterCount());
        assertEquals(byte[].class, m.getParameterTypes()[0]);
        assertEquals(byte[][].class, m.getParameterTypes()[1]);
    }

    @Test
    public void sremStringSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("srem", String.class, String[].class);
        assertEquals(long.class, m.getReturnType());
        assertEquals(2, m.getParameterCount());
        assertEquals(String.class, m.getParameterTypes()[0]);
        assertEquals(String[].class, m.getParameterTypes()[1]);
    }

    @Test
    public void sremBinarySignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("srem", byte[].class, byte[][].class);
        assertEquals(long.class, m.getReturnType());
        assertEquals(2, m.getParameterCount());
        assertEquals(byte[].class, m.getParameterTypes()[0]);
        assertEquals(byte[][].class, m.getParameterTypes()[1]);
    }

    @Test
    public void smembersStringSignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("smembers", String.class);
        assertEquals(Set.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        assertEquals(String.class, m.getParameterTypes()[0]);
    }

    @Test
    public void smembersBinarySignatureAndReturnType() throws NoSuchMethodException {
        Method m = Jedis.class.getMethod("smembers", byte[].class);
        assertEquals(Set.class, m.getReturnType());
        assertEquals(1, m.getParameterCount());
        assertEquals(byte[].class, m.getParameterTypes()[0]);
    }

    @Test
    public void scardMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("scard", String.class));
        assertNotNull(Jedis.class.getMethod("scard", byte[].class));
        assertEquals(long.class, Jedis.class.getMethod("scard", String.class).getReturnType());
        assertEquals(long.class, Jedis.class.getMethod("scard", byte[].class).getReturnType());
    }

    @Test
    public void sismemberMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sismember", String.class, String.class));
        assertNotNull(Jedis.class.getMethod("sismember", byte[].class, byte[].class));
        assertEquals(
                boolean.class,
                Jedis.class.getMethod("sismember", String.class, String.class).getReturnType());
        assertEquals(
                boolean.class,
                Jedis.class.getMethod("sismember", byte[].class, byte[].class).getReturnType());
    }

    @Test
    public void smismemberMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("smismember", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("smismember", byte[].class, byte[][].class));
        assertEquals(
                java.util.List.class,
                Jedis.class.getMethod("smismember", String.class, String[].class).getReturnType());
        assertEquals(
                java.util.List.class,
                Jedis.class.getMethod("smismember", byte[].class, byte[][].class).getReturnType());
    }

    @Test
    public void spopMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("spop", String.class));
        assertNotNull(Jedis.class.getMethod("spop", byte[].class));
        assertNotNull(Jedis.class.getMethod("spop", String.class, long.class));
        assertNotNull(Jedis.class.getMethod("spop", byte[].class, long.class));
    }

    @Test
    public void srandmemberMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("srandmember", String.class));
        assertNotNull(Jedis.class.getMethod("srandmember", byte[].class));
        assertNotNull(Jedis.class.getMethod("srandmember", String.class, int.class));
        assertNotNull(Jedis.class.getMethod("srandmember", byte[].class, int.class));
    }

    @Test
    public void smoveMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("smove", String.class, String.class, String.class));
        assertNotNull(Jedis.class.getMethod("smove", byte[].class, byte[].class, byte[].class));
        assertEquals(
                long.class,
                Jedis.class.getMethod("smove", String.class, String.class, String.class).getReturnType());
        assertEquals(
                long.class,
                Jedis.class.getMethod("smove", byte[].class, byte[].class, byte[].class).getReturnType());
    }

    @Test
    public void sinterMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sinter", String[].class));
        assertNotNull(Jedis.class.getMethod("sinter", byte[][].class));
        assertEquals(Set.class, Jedis.class.getMethod("sinter", String[].class).getReturnType());
        assertEquals(Set.class, Jedis.class.getMethod("sinter", byte[][].class).getReturnType());
    }

    @Test
    public void sunionMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sunion", String[].class));
        assertNotNull(Jedis.class.getMethod("sunion", byte[][].class));
        assertEquals(Set.class, Jedis.class.getMethod("sunion", String[].class).getReturnType());
        assertEquals(Set.class, Jedis.class.getMethod("sunion", byte[][].class).getReturnType());
    }

    @Test
    public void sdiffMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sdiff", String[].class));
        assertNotNull(Jedis.class.getMethod("sdiff", byte[][].class));
        assertEquals(Set.class, Jedis.class.getMethod("sdiff", String[].class).getReturnType());
        assertEquals(Set.class, Jedis.class.getMethod("sdiff", byte[][].class).getReturnType());
    }

    @Test
    public void sintercardMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sintercard", String[].class));
        assertNotNull(Jedis.class.getMethod("sintercard", long.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sintercard", byte[][].class));
        assertNotNull(Jedis.class.getMethod("sintercard", long.class, byte[][].class));
        assertEquals(long.class, Jedis.class.getMethod("sintercard", String[].class).getReturnType());
        assertEquals(
                long.class,
                Jedis.class.getMethod("sintercard", long.class, String[].class).getReturnType());
        assertEquals(long.class, Jedis.class.getMethod("sintercard", byte[][].class).getReturnType());
        assertEquals(
                long.class,
                Jedis.class.getMethod("sintercard", long.class, byte[][].class).getReturnType());
    }

    @Test
    public void sinterstoreMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sinterstore", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sinterstore", byte[].class, byte[][].class));
        assertEquals(
                long.class,
                Jedis.class.getMethod("sinterstore", String.class, String[].class).getReturnType());
        assertEquals(
                long.class,
                Jedis.class.getMethod("sinterstore", byte[].class, byte[][].class).getReturnType());
    }

    @Test
    public void sunionstoreMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sunionstore", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sunionstore", byte[].class, byte[][].class));
        assertEquals(
                long.class,
                Jedis.class.getMethod("sunionstore", String.class, String[].class).getReturnType());
        assertEquals(
                long.class,
                Jedis.class.getMethod("sunionstore", byte[].class, byte[][].class).getReturnType());
    }

    @Test
    public void sdiffstoreMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sdiffstore", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sdiffstore", byte[].class, byte[][].class));
        assertEquals(
                long.class,
                Jedis.class.getMethod("sdiffstore", String.class, String[].class).getReturnType());
        assertEquals(
                long.class,
                Jedis.class.getMethod("sdiffstore", byte[].class, byte[][].class).getReturnType());
    }

    @Test
    public void sscanMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sscan", String.class, String.class));
        assertNotNull(
                Jedis.class.getMethod(
                        "sscan", String.class, String.class, redis.clients.jedis.params.ScanParams.class));
        assertNotNull(Jedis.class.getMethod("sscan", byte[].class, byte[].class));
        assertNotNull(
                Jedis.class.getMethod(
                        "sscan", byte[].class, byte[].class, redis.clients.jedis.params.ScanParams.class));
    }
}
