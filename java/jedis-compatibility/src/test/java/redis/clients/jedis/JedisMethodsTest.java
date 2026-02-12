/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

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
    public void testSortedSetMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test zadd methods
        Method zaddSingle = jedisClass.getMethod("zadd", String.class, double.class, String.class);
        assertEquals(long.class, zaddSingle.getReturnType());

        Method zaddMap = jedisClass.getMethod("zadd", String.class, java.util.Map.class);
        assertEquals(long.class, zaddMap.getReturnType());

        Method zaddBinary = jedisClass.getMethod("zadd", byte[].class, double.class, byte[].class);
        assertEquals(long.class, zaddBinary.getReturnType());

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
        assertEquals(java.util.Map.class, zrangeWithScores.getReturnType());

        Method zrangeWithScoresBinary =
                jedisClass.getMethod("zrangeWithScores", byte[].class, long.class, long.class);
        assertEquals(java.util.Map.class, zrangeWithScoresBinary.getReturnType());

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
        assertEquals(java.util.Map.class, zpopmin.getReturnType());

        Method zpopminBinary = jedisClass.getMethod("zpopmin", byte[].class, int.class);
        assertEquals(java.util.Map.class, zpopminBinary.getReturnType());

        // Test zpopmax methods
        Method zpopmax = jedisClass.getMethod("zpopmax", String.class, int.class);
        assertEquals(java.util.Map.class, zpopmax.getReturnType());

        Method zpopmaxBinary = jedisClass.getMethod("zpopmax", byte[].class, int.class);
        assertEquals(java.util.Map.class, zpopmaxBinary.getReturnType());

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
        assertEquals(Long.class, zrangestore.getReturnType());

        Method zrangestoreBinary =
                jedisClass.getMethod("zrangestore", byte[].class, byte[].class, long.class, long.class);
        assertEquals(Long.class, zrangestoreBinary.getReturnType());

        // Test zrankWithScore methods
        Method zrankWithScore = jedisClass.getMethod("zrankWithScore", String.class, String.class);
        assertEquals(java.util.List.class, zrankWithScore.getReturnType());

        Method zrankWithScoreBinary =
                jedisClass.getMethod("zrankWithScore", byte[].class, byte[].class);
        assertEquals(java.util.List.class, zrankWithScoreBinary.getReturnType());

        // Test zrevrankWithScore methods
        Method zrevrankWithScore =
                jedisClass.getMethod("zrevrankWithScore", String.class, String.class);
        assertEquals(java.util.List.class, zrevrankWithScore.getReturnType());

        Method zrevrankWithScoreBinary =
                jedisClass.getMethod("zrevrankWithScore", byte[].class, byte[].class);
        assertEquals(java.util.List.class, zrevrankWithScoreBinary.getReturnType());

        // Test zlexcount methods
        Method zlexcount = jedisClass.getMethod("zlexcount", String.class, String.class, String.class);
        assertEquals(Long.class, zlexcount.getReturnType());

        Method zlexcountBinary =
                jedisClass.getMethod("zlexcount", byte[].class, byte[].class, byte[].class);
        assertEquals(Long.class, zlexcountBinary.getReturnType());

        // Test bzpopmin methods
        Method bzpopmin = jedisClass.getMethod("bzpopmin", double.class, String[].class);
        assertEquals(java.util.List.class, bzpopmin.getReturnType());

        Method bzpopminBinary = jedisClass.getMethod("bzpopmin", double.class, byte[][].class);
        assertEquals(java.util.List.class, bzpopminBinary.getReturnType());

        // Test bzpopmax methods
        Method bzpopmax = jedisClass.getMethod("bzpopmax", double.class, String[].class);
        assertEquals(java.util.List.class, bzpopmax.getReturnType());

        Method bzpopmaxBinary = jedisClass.getMethod("bzpopmax", double.class, byte[][].class);
        assertEquals(java.util.List.class, bzpopmaxBinary.getReturnType());

        // Test zdiff methods
        Method zdiff = jedisClass.getMethod("zdiff", String[].class);
        assertEquals(java.util.List.class, zdiff.getReturnType());

        Method zdiffBinary = jedisClass.getMethod("zdiff", byte[][].class);
        assertEquals(java.util.List.class, zdiffBinary.getReturnType());

        // Test zdiffWithScores methods
        Method zdiffWithScores = jedisClass.getMethod("zdiffWithScores", String[].class);
        assertEquals(java.util.Map.class, zdiffWithScores.getReturnType());

        Method zdiffWithScoresBinary = jedisClass.getMethod("zdiffWithScores", byte[][].class);
        assertEquals(java.util.Map.class, zdiffWithScoresBinary.getReturnType());

        // Test zdiffstore methods
        Method zdiffstore = jedisClass.getMethod("zdiffstore", String.class, String[].class);
        assertEquals(Long.class, zdiffstore.getReturnType());

        Method zdiffstoreBinary = jedisClass.getMethod("zdiffstore", byte[].class, byte[][].class);
        assertEquals(Long.class, zdiffstoreBinary.getReturnType());

        // Test zunion methods
        Method zunion = jedisClass.getMethod("zunion", String[].class);
        assertEquals(java.util.List.class, zunion.getReturnType());

        Method zunionBinary = jedisClass.getMethod("zunion", byte[][].class);
        assertEquals(java.util.List.class, zunionBinary.getReturnType());

        // Test zunionWithScores methods
        Method zunionWithScores = jedisClass.getMethod("zunionWithScores", String[].class);
        assertEquals(java.util.Map.class, zunionWithScores.getReturnType());

        Method zunionWithScoresBinary = jedisClass.getMethod("zunionWithScores", byte[][].class);
        assertEquals(java.util.Map.class, zunionWithScoresBinary.getReturnType());

        // Test zinter methods
        Method zinter = jedisClass.getMethod("zinter", String[].class);
        assertEquals(java.util.List.class, zinter.getReturnType());

        Method zinterBinary = jedisClass.getMethod("zinter", byte[][].class);
        assertEquals(java.util.List.class, zinterBinary.getReturnType());

        // Test zinterWithScores methods
        Method zinterWithScores = jedisClass.getMethod("zinterWithScores", String[].class);
        assertEquals(java.util.Map.class, zinterWithScores.getReturnType());

        Method zinterWithScoresBinary = jedisClass.getMethod("zinterWithScores", byte[][].class);
        assertEquals(java.util.Map.class, zinterWithScoresBinary.getReturnType());

        // Test zintercard methods
        Method zintercard = jedisClass.getMethod("zintercard", String[].class);
        assertEquals(Long.class, zintercard.getReturnType());

        Method zintercardBinary = jedisClass.getMethod("zintercard", byte[][].class);
        assertEquals(Long.class, zintercardBinary.getReturnType());

        // Test zmpop methods
        Method zmpop = jedisClass.getMethod("zmpop", boolean.class, String[].class);
        assertEquals(java.util.Map.class, zmpop.getReturnType());

        Method zmpopBinary = jedisClass.getMethod("zmpop", boolean.class, byte[][].class);
        assertEquals(java.util.Map.class, zmpopBinary.getReturnType());

        // Test bzmpop methods
        Method bzmpop = jedisClass.getMethod("bzmpop", double.class, boolean.class, String[].class);
        assertEquals(java.util.Map.class, bzmpop.getReturnType());

        Method bzmpopBinary =
                jedisClass.getMethod("bzmpop", double.class, boolean.class, byte[][].class);
        assertEquals(java.util.Map.class, bzmpopBinary.getReturnType());

        // Test zremrangebylex methods
        Method zremrangebylex =
                jedisClass.getMethod("zremrangebylex", String.class, String.class, String.class);
        assertEquals(Long.class, zremrangebylex.getReturnType());

        Method zremrangebylexBinary =
                jedisClass.getMethod("zremrangebylex", byte[].class, byte[].class, byte[].class);
        assertEquals(Long.class, zremrangebylexBinary.getReturnType());

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
    }
}
