/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.resps.AccessControlUser;
import redis.clients.jedis.resps.FunctionStats;

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
}
