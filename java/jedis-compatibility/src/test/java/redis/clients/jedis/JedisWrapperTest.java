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
        assertNotNull(jedisClass.getMethod("publish", String.class, String.class));
        assertNotNull(jedisClass.getMethod("publish", byte[].class, byte[].class));
        assertNotNull(jedisClass.getMethod("pubsubChannels"));
        assertNotNull(jedisClass.getMethod("pubsubChannels", String.class));
        assertNotNull(jedisClass.getMethod("pubsubChannels", byte[].class));
        assertNotNull(jedisClass.getMethod("pubsubNumPat"));
        assertNotNull(jedisClass.getMethod("pubsubNumSub", String[].class));
        assertNotNull(jedisClass.getMethod("pubsubNumSub", byte[][].class));

        // Set command methods
        assertNotNull(jedisClass.getMethod("sadd", String.class, String[].class));
        assertNotNull(jedisClass.getMethod("sadd", byte[].class, byte[][].class));
        assertNotNull(jedisClass.getMethod("srem", String.class, String[].class));
        assertNotNull(jedisClass.getMethod("srem", byte[].class, byte[][].class));
        assertNotNull(jedisClass.getMethod("smembers", String.class));
        assertNotNull(jedisClass.getMethod("smembers", byte[].class));
        assertNotNull(jedisClass.getMethod("scard", String.class));
        assertNotNull(jedisClass.getMethod("scard", byte[].class));
        assertNotNull(jedisClass.getMethod("sismember", String.class, String.class));
        assertNotNull(jedisClass.getMethod("sismember", byte[].class, byte[].class));
        assertNotNull(jedisClass.getMethod("smismember", String.class, String[].class));
        assertNotNull(jedisClass.getMethod("smismember", byte[].class, byte[][].class));
        assertNotNull(jedisClass.getMethod("spop", String.class));
        assertNotNull(jedisClass.getMethod("spop", byte[].class));
        assertNotNull(jedisClass.getMethod("spop", String.class, long.class));
        assertNotNull(jedisClass.getMethod("spop", byte[].class, long.class));
        assertNotNull(jedisClass.getMethod("srandmember", String.class));
        assertNotNull(jedisClass.getMethod("srandmember", byte[].class));
        assertNotNull(jedisClass.getMethod("srandmember", String.class, int.class));
        assertNotNull(jedisClass.getMethod("srandmember", byte[].class, int.class));
        assertNotNull(jedisClass.getMethod("smove", String.class, String.class, String.class));
        assertNotNull(jedisClass.getMethod("smove", byte[].class, byte[].class, byte[].class));
        assertNotNull(jedisClass.getMethod("sinter", String[].class));
        assertNotNull(jedisClass.getMethod("sinter", byte[][].class));
        assertNotNull(jedisClass.getMethod("sintercard", String[].class));
        assertNotNull(jedisClass.getMethod("sintercard", long.class, String[].class));
        assertNotNull(jedisClass.getMethod("sintercard", byte[][].class));
        assertNotNull(jedisClass.getMethod("sintercard", long.class, byte[][].class));
        assertNotNull(jedisClass.getMethod("sinterstore", String.class, String[].class));
        assertNotNull(jedisClass.getMethod("sinterstore", byte[].class, byte[][].class));
        assertNotNull(jedisClass.getMethod("sunion", String[].class));
        assertNotNull(jedisClass.getMethod("sunion", byte[][].class));
        assertNotNull(jedisClass.getMethod("sunionstore", String.class, String[].class));
        assertNotNull(jedisClass.getMethod("sunionstore", byte[].class, byte[][].class));
        assertNotNull(jedisClass.getMethod("sdiff", String[].class));
        assertNotNull(jedisClass.getMethod("sdiff", byte[][].class));
        assertNotNull(jedisClass.getMethod("sdiffstore", String.class, String[].class));
        assertNotNull(jedisClass.getMethod("sdiffstore", byte[].class, byte[][].class));
        assertNotNull(jedisClass.getMethod("sscan", String.class, String.class));
        assertNotNull(
                jedisClass.getMethod(
                        "sscan", String.class, String.class, redis.clients.jedis.params.ScanParams.class));
        assertNotNull(jedisClass.getMethod("sscan", byte[].class, byte[].class));
        assertNotNull(
                jedisClass.getMethod(
                        "sscan", byte[].class, byte[].class, redis.clients.jedis.params.ScanParams.class));
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
