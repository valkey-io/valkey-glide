/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.params.ScanParams;

/**
 * Unit tests for Jedis set commands (sadd, srem, smembers, scard, sismember, smismember, spop,
 * srandmember, smove, sinter, sintercard, sinterstore, sunion, sunionstore, sdiff, sdiffstore,
 * sscan).
 */
public class JedisSetCommandsTest {

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
    public void saddSremSmembersMethodsExist() throws NoSuchMethodException {
        Class<Jedis> c = Jedis.class;
        assertNotNull(c.getMethod("sadd", String.class, String[].class));
        assertNotNull(c.getMethod("sadd", byte[].class, byte[][].class));
        assertNotNull(c.getMethod("srem", String.class, String[].class));
        assertNotNull(c.getMethod("srem", byte[].class, byte[][].class));
        assertNotNull(c.getMethod("smembers", String.class));
        assertNotNull(c.getMethod("smembers", byte[].class));
    }

    @Test
    public void scardMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("scard", String.class));
        assertNotNull(Jedis.class.getMethod("scard", byte[].class));
        assertEquals(long.class, Jedis.class.getMethod("scard", String.class).getReturnType());
    }

    @Test
    public void sismemberMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sismember", String.class, String.class));
        assertNotNull(Jedis.class.getMethod("sismember", byte[].class, byte[].class));
        assertEquals(
                boolean.class,
                Jedis.class.getMethod("sismember", String.class, String.class).getReturnType());
    }

    @Test
    public void smismemberMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("smismember", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("smismember", byte[].class, byte[][].class));
        assertEquals(
                java.util.List.class,
                Jedis.class.getMethod("smismember", String.class, String[].class).getReturnType());
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
    }

    @Test
    public void sinterSunionSdiffMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sinter", String[].class));
        assertNotNull(Jedis.class.getMethod("sinter", byte[][].class));
        assertNotNull(Jedis.class.getMethod("sunion", String[].class));
        assertNotNull(Jedis.class.getMethod("sunion", byte[][].class));
        assertNotNull(Jedis.class.getMethod("sdiff", String[].class));
        assertNotNull(Jedis.class.getMethod("sdiff", byte[][].class));
    }

    @Test
    public void sintercardSinterstoreSunionstoreSdiffstoreMethodsExist()
            throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sintercard", String[].class));
        assertNotNull(Jedis.class.getMethod("sintercard", long.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sintercard", byte[][].class));
        assertNotNull(Jedis.class.getMethod("sintercard", long.class, byte[][].class));
        assertNotNull(Jedis.class.getMethod("sinterstore", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sinterstore", byte[].class, byte[][].class));
        assertNotNull(Jedis.class.getMethod("sunionstore", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sunionstore", byte[].class, byte[][].class));
        assertNotNull(Jedis.class.getMethod("sdiffstore", String.class, String[].class));
        assertNotNull(Jedis.class.getMethod("sdiffstore", byte[].class, byte[][].class));
    }

    @Test
    public void sscanMethodsExist() throws NoSuchMethodException {
        assertNotNull(Jedis.class.getMethod("sscan", String.class, String.class));
        assertNotNull(Jedis.class.getMethod("sscan", String.class, String.class, ScanParams.class));
        assertNotNull(Jedis.class.getMethod("sscan", byte[].class, byte[].class));
        assertNotNull(Jedis.class.getMethod("sscan", byte[].class, byte[].class, ScanParams.class));
    }
}
