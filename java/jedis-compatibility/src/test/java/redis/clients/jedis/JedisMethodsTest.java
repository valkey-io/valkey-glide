/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Map;
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
    public void testServerManagementMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test info() methods
        Method infoMethod = jedisClass.getMethod("info");
        assertEquals(String.class, infoMethod.getReturnType());

        Method infoSectionMethod = jedisClass.getMethod("info", String.class);
        assertEquals(String.class, infoSectionMethod.getReturnType());

        // Test configGet() method
        Method configGetMethod = jedisClass.getMethod("configGet", String.class);
        assertEquals(Map.class, configGetMethod.getReturnType());

        // Test configSet() methods
        Method configSetSingleMethod = jedisClass.getMethod("configSet", String.class, String.class);
        assertEquals(String.class, configSetSingleMethod.getReturnType());

        Method configSetMapMethod = jedisClass.getMethod("configSet", Map.class);
        assertEquals(String.class, configSetMapMethod.getReturnType());

        // Test configRewrite() method
        Method configRewriteMethod = jedisClass.getMethod("configRewrite");
        assertEquals(String.class, configRewriteMethod.getReturnType());

        // Test configResetStat() method
        Method configResetStatMethod = jedisClass.getMethod("configResetStat");
        assertEquals(String.class, configResetStatMethod.getReturnType());

        // Test dbsize() method
        Method dbsizeMethod = jedisClass.getMethod("dbsize");
        assertEquals(long.class, dbsizeMethod.getReturnType());

        // Test flushDB() method
        Method flushDBMethod = jedisClass.getMethod("flushDB");
        assertEquals(String.class, flushDBMethod.getReturnType());

        // Test flushAll() method
        Method flushAllMethod = jedisClass.getMethod("flushAll");
        assertEquals(String.class, flushAllMethod.getReturnType());

        // Test time() method
        Method timeMethod = jedisClass.getMethod("time");
        assertEquals(String[].class, timeMethod.getReturnType());

        // Test lastsave() method
        Method lastsaveMethod = jedisClass.getMethod("lastsave");
        assertEquals(long.class, lastsaveMethod.getReturnType());

        // Test lolwut() methods
        Method lolwutMethod = jedisClass.getMethod("lolwut");
        assertEquals(String.class, lolwutMethod.getReturnType());

        Method lolwutParamsMethod = jedisClass.getMethod("lolwut", int[].class);
        assertEquals(String.class, lolwutParamsMethod.getReturnType());
    }

    @Test
    public void testServerManagementMethodsExist() {
        // Test that we can get method references without creating instances
        assertDoesNotThrow(
                () -> {
                    Class<Jedis> jedisClass = Jedis.class;

                    // Verify the server management methods exist
                    assertNotNull(jedisClass.getMethod("info"));
                    assertNotNull(jedisClass.getMethod("info", String.class));
                    assertNotNull(jedisClass.getMethod("configGet", String.class));
                    assertNotNull(jedisClass.getMethod("configSet", String.class, String.class));
                    assertNotNull(jedisClass.getMethod("configSet", Map.class));
                    assertNotNull(jedisClass.getMethod("configRewrite"));
                    assertNotNull(jedisClass.getMethod("configResetStat"));
                    assertNotNull(jedisClass.getMethod("dbsize"));
                    assertNotNull(jedisClass.getMethod("flushDB"));
                    assertNotNull(jedisClass.getMethod("flushAll"));
                    assertNotNull(jedisClass.getMethod("time"));
                    assertNotNull(jedisClass.getMethod("lastsave"));
                    assertNotNull(jedisClass.getMethod("lolwut"));
                    assertNotNull(jedisClass.getMethod("lolwut", int[].class));
                });
    }
}
