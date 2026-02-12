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
