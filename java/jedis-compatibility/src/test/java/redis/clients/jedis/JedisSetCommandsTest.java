/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for Jedis set commands (sadd, srem, smembers). */
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
}
