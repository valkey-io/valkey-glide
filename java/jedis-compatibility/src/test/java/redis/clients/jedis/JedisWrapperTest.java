/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Jedis wrapper functionality. Tests API contracts and constructor signatures
 * without establishing actual connections.
 */
public class JedisWrapperTest {

    @Test
    public void testJedisConstructorSignatures() throws NoSuchMethodException {
        // Test that Jedis constructor signatures exist
        Class<Jedis> jedisClass = Jedis.class;

        assertNotNull(jedisClass.getConstructor(String.class, int.class));
        assertNotNull(jedisClass.getConstructor(String.class, int.class, JedisClientConfig.class));
    }

    @Test
    public void testJedisConfigurationSignatures() throws NoSuchMethodException {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(5000)
                        .connectionTimeoutMillis(3000)
                        .clientName("test-client")
                        .database(1)
                        .build();

        // Test configuration properties
        assertEquals(5000, config.getSocketTimeoutMillis());
        assertEquals(3000, config.getConnectionTimeoutMillis());
        assertEquals("test-client", config.getClientName());
        assertEquals(1, config.getDatabase());

        // Test that constructor with config exists
        Class<Jedis> jedisClass = Jedis.class;
        assertNotNull(jedisClass.getConstructor(String.class, int.class, JedisClientConfig.class));
    }

    @Test
    public void testJedisPoolConstructorSignatures() throws NoSuchMethodException {
        Class<JedisPool> poolClass = JedisPool.class;

        assertNotNull(poolClass.getConstructor(String.class, int.class));
        assertNotNull(
                poolClass.getConstructor(
                        GenericObjectPoolConfig.class, String.class, int.class, JedisClientConfig.class));
    }

    @Test
    public void testJedisPoolConfigurationSignatures() throws NoSuchMethodException {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(2000)
                        .connectionTimeoutMillis(1000)
                        .build();

        assertEquals(2000, config.getSocketTimeoutMillis());
        assertEquals(1000, config.getConnectionTimeoutMillis());

        // Test that pool constructor with config exists
        Class<JedisPool> poolClass = JedisPool.class;
        assertNotNull(
                poolClass.getConstructor(
                        GenericObjectPoolConfig.class, String.class, int.class, JedisClientConfig.class));
    }

    @Test
    public void testJedisMethodSignatures() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        // Test that key methods exist
        assertNotNull(jedisClass.getMethod("isClosed"));
        assertNotNull(jedisClass.getMethod("getConfig"));
        assertNotNull(jedisClass.getMethod("close"));
        assertNotNull(jedisClass.getMethod("set", String.class, String.class));
        assertNotNull(jedisClass.getMethod("get", String.class));
        assertNotNull(jedisClass.getMethod("del", String.class));
        assertNotNull(jedisClass.getMethod("del", String[].class));
        assertNotNull(jedisClass.getMethod("keys", String.class));
    }

    @Test
    public void testJedisPoolMethodSignatures() throws NoSuchMethodException {
        Class<JedisPool> poolClass = JedisPool.class;

        // Test that pool methods exist (methods inherited from Pool base class)
        assertNotNull(poolClass.getMethod("getResource"));
        assertNotNull(poolClass.getMethod("close"));
        assertNotNull(poolClass.getMethod("getMaxTotal"));
        assertNotNull(poolClass.getMethod("getMaxBorrowWaitTimeMillis"));
        assertNotNull(poolClass.getMethod("getNumActive"));
        assertNotNull(poolClass.getMethod("getNumIdle"));
    }

    @Test
    public void testResourceManagementInterfaces() {
        // Test that classes implement expected interfaces
        assertTrue(java.io.Closeable.class.isAssignableFrom(Jedis.class));
        assertTrue(java.io.Closeable.class.isAssignableFrom(JedisPool.class));
    }

    @Test
    public void testConfigurationBuilderPattern() {
        // Test that configuration builder works
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(1000)
                        .connectionTimeoutMillis(500)
                        .clientName("test")
                        .database(2)
                        .ssl(true)
                        .build();

        assertEquals(1000, config.getSocketTimeoutMillis());
        assertEquals(500, config.getConnectionTimeoutMillis());
        assertEquals("test", config.getClientName());
        assertEquals(2, config.getDatabase());
        assertTrue(config.isSsl());
    }
}
