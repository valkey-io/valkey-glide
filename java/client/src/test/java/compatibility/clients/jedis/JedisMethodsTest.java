/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Jedis method signatures and API contracts.
 * Tests that required methods exist with correct signatures without executing them.
 */
public class JedisMethodsTest {

    @Test
    public void testDelMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;
        
        // Test del(String key) method exists
        Method delSingleKey = jedisClass.getMethod("del", String.class);
        assertEquals(Long.class, delSingleKey.getReturnType());
        
        // Test del(String... keys) method exists
        Method delMultipleKeys = jedisClass.getMethod("del", String[].class);
        assertEquals(Long.class, delMultipleKeys.getReturnType());
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
    public void testJedisInstantiationForMethodTesting() {
        // Test that we can create Jedis instance for method signature testing
        assertDoesNotThrow(() -> {
            Jedis jedis = new Jedis("localhost", 6379);
            assertNotNull(jedis);
            
            // Verify the instance has the expected methods available
            Class<?> jedisClass = jedis.getClass();
            assertNotNull(jedisClass.getMethod("del", String.class));
            assertNotNull(jedisClass.getMethod("del", String[].class));
            assertNotNull(jedisClass.getMethod("keys", String.class));
            assertNotNull(jedisClass.getMethod("set", String.class, String.class));
            assertNotNull(jedisClass.getMethod("get", String.class));
            assertNotNull(jedisClass.getMethod("ping"));
            assertNotNull(jedisClass.getMethod("ping", String.class));
            
            jedis.close();
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
        
        assertDoesNotThrow(() -> {
            Jedis jedis = new Jedis("localhost", 6379);
            jedis.close(); // Should not throw
            assertTrue(jedis.isClosed());
        });
    }

    @Test
    public void testJedisStateManagement() {
        Jedis jedis = new Jedis("localhost", 6379);
        
        // Test initial state
        assertFalse(jedis.isClosed());
        assertNotNull(jedis.getConfig());
        
        // Test closed state
        jedis.close();
        assertTrue(jedis.isClosed());
    }

    @Test
    public void testJedisConfigurationAccess() {
        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(5000)
                .clientName("test-client")
                .build();
        
        Jedis jedis = new Jedis("localhost", 6379, config);
        
        assertEquals(config, jedis.getConfig());
        jedis.close();
    }
}
