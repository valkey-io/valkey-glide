/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.ProtocolVersion;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

/**
 * Tests to verify Jedis 4.x API compatibility at compile time. These tests ensure that the Jedis
 * 4.x-specific type signatures compile correctly.
 */
public class Jedis4ApiCompatibilityTest {

    @Test
    public void testJedisPoolWithJedisGenericType() {
        // Jedis 4.x uses GenericObjectPoolConfig<Jedis>
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMinIdle(1);

        // These constructors should compile with Jedis 4.x API
        assertDoesNotThrow(
                () -> {
                    // Note: We don't actually create the pool as it would require a running Redis instance
                    // This test is primarily for compile-time verification
                    assertTrue(poolConfig.getMaxTotal() == 8);
                    assertTrue(poolConfig.getMaxIdle() == 4);
                    assertTrue(poolConfig.getMinIdle() == 1);
                });
    }

    @Test
    public void testJedisPooledWithConnectionGenericType() {
        // Jedis 4.x uses GenericObjectPoolConfig<Connection> for JedisPooled
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(4);
        poolConfig.setMinIdle(1);

        // These should compile with Jedis 4.x API
        assertDoesNotThrow(
                () -> {
                    assertTrue(poolConfig.getMaxTotal() == 8);
                    assertTrue(poolConfig.getMaxIdle() == 4);
                    assertTrue(poolConfig.getMinIdle() == 1);
                });
    }

    @Test
    public void testJedisClientConfigProtocolAcceptedButForcedToResp2() {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(2000)
                        .socketTimeoutMillis(2000)
                        .user("testuser")
                        .password("testpass")
                        .database(0)
                        .clientName("test-client")
                        .ssl(false)
                        .protocol(RedisProtocol.RESP3)
                        .build();

        assertEquals(2000, config.getConnectionTimeoutMillis());
        assertEquals(2000, config.getSocketTimeoutMillis());
        assertEquals("testuser", config.getUser());
        assertEquals("testpass", config.getPassword());
        assertEquals(0, config.getDatabase());
        assertEquals("test-client", config.getClientName());
        assertFalse(config.isSsl());
        assertEquals(RedisProtocol.RESP3, config.getRedisProtocol());

        GlideClientConfiguration glideConfig =
                ConfigurationMapper.mapToGlideConfig("localhost", 6379, config, false);
        assertEquals(ProtocolVersion.RESP2, glideConfig.getProtocol());
    }

    @Test
    public void testDefaultJedisClientConfigBuilder() {
        // Builder exposes protocol() for shared API compatibility; Jedis 4 layer ignores it at mapping.
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();

        // These methods should exist
        builder.connectionTimeoutMillis(1000);
        builder.socketTimeoutMillis(2000);
        builder.blockingSocketTimeoutMillis(0);
        builder.user("user");
        builder.password("pass");
        builder.database(1);
        builder.clientName("client");
        builder.ssl(true);

        // Build the config
        DefaultJedisClientConfig config = builder.build();

        assertEquals(1000, config.getConnectionTimeoutMillis());
        assertEquals(2000, config.getSocketTimeoutMillis());
        assertEquals("user", config.getUser());
        assertEquals("pass", config.getPassword());
        assertEquals(1, config.getDatabase());
        assertEquals("client", config.getClientName());
        assertTrue(config.isSsl());
    }

    @Test
    public void testConnectionClass() {
        // Test that Connection class exists (Jedis 4.x compatibility)
        HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
        Connection connection = new Connection(hostAndPort);

        assertEquals("localhost", connection.getHost());
        assertEquals(6379, connection.getPort());
        assertEquals(hostAndPort, connection.getHostAndPort());

        // Connection should be closeable
        assertDoesNotThrow(() -> connection.close());
    }

    @Test
    public void testJedisPoolConstructorSignatures() {
        // Test that various JedisPool constructor signatures exist and compile
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();

        // These should all compile (Jedis 4.x signatures)
        assertDoesNotThrow(
                () -> {
                    // Basic constructors would work here if we had a running Redis instance
                    // For now, just verify the types are correct
                    JedisClientConfig config = DefaultJedisClientConfig.builder().build();
                    assertNotNull(config);
                    assertNotNull(poolConfig);
                });
    }

    @Test
    public void testJedisPooledConstructorSignatures() {
        // Test that JedisPooled constructor signatures exist and compile with Connection type
        GenericObjectPoolConfig<Connection> poolConfig = new GenericObjectPoolConfig<>();

        assertDoesNotThrow(
                () -> {
                    JedisClientConfig config = DefaultJedisClientConfig.builder().build();
                    assertNotNull(config);
                    assertNotNull(poolConfig);
                });
    }

    @Test
    public void testUnifiedJedisConstructors() {
        // UnifiedJedis should work without RedisProtocol
        assertDoesNotThrow(
                () -> {
                    JedisClientConfig config =
                            DefaultJedisClientConfig.builder()
                                    .connectionTimeoutMillis(2000)
                                    .socketTimeoutMillis(2000)
                                    .build();

                    assertNotNull(config);
                    // These constructors should exist and compile
                    // Actual instantiation would require a running Redis instance
                });
    }

    @Test
    public void testHostAndPort() {
        HostAndPort hp1 = new HostAndPort("localhost", 6379);
        assertEquals("localhost", hp1.getHost());
        assertEquals(6379, hp1.getPort());

        HostAndPort hp2 = new HostAndPort("redis.example.com", 7000);
        assertEquals("redis.example.com", hp2.getHost());
        assertEquals(7000, hp2.getPort());
    }

    @Test
    public void testProtocolDefaults() {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder().protocol(RedisProtocol.RESP3).build();
        assertNotNull(config);
        assertEquals(RedisProtocol.RESP3, config.getRedisProtocol());

        GlideClientConfiguration glideConfig =
                ConfigurationMapper.mapToGlideConfig("localhost", 6379, config, false);
        assertEquals(ProtocolVersion.RESP2, glideConfig.getProtocol());
    }

    @Test
    public void testPubSubMethodSignaturesMatchJedis4Style() throws NoSuchMethodException {
        Class<Jedis> jedisClass = Jedis.class;

        Method publishString = jedisClass.getMethod("publish", String.class, String.class);
        assertEquals(Long.class, publishString.getReturnType());

        Method publishBinary = jedisClass.getMethod("publish", byte[].class, byte[].class);
        assertEquals(Long.class, publishBinary.getReturnType());

        Method pubsubChannels = jedisClass.getMethod("pubsubChannels");
        assertEquals(List.class, pubsubChannels.getReturnType());

        Method pubsubChannelsPattern = jedisClass.getMethod("pubsubChannels", String.class);
        assertEquals(List.class, pubsubChannelsPattern.getReturnType());

        Method pubsubNumPat = jedisClass.getMethod("pubsubNumPat");
        assertEquals(Long.class, pubsubNumPat.getReturnType());

        Method pubsubNumSub = jedisClass.getMethod("pubsubNumSub", String[].class);
        assertEquals(Map.class, pubsubNumSub.getReturnType());
    }
}
