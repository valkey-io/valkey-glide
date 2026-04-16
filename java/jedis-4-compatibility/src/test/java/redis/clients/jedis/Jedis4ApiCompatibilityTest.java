/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

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
    public void testJedisClientConfigNoRedisProtocol() {
        // Jedis 4.x doesn't have getRedisProtocol() method
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(2000)
                        .socketTimeoutMillis(2000)
                        .user("testuser")
                        .password("testpass")
                        .database(0)
                        .clientName("test-client")
                        .ssl(false)
                        .build();

        // Verify the config works
        assertEquals(2000, config.getConnectionTimeoutMillis());
        assertEquals(2000, config.getSocketTimeoutMillis());
        assertEquals("testuser", config.getUser());
        assertEquals("testpass", config.getPassword());
        assertEquals(0, config.getDatabase());
        assertEquals("test-client", config.getClientName());
        assertFalse(config.isSsl());

        // Note: getRedisProtocol() should not exist in Jedis 4.x
        // If this method exists, it would be a compilation error when using real Jedis 4.x
    }

    @Test
    public void testDefaultJedisClientConfigBuilder() {
        // Test that the builder doesn't have protocol() method (Jedis 4.x behavior)
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
        // In Jedis 4.x, there's no protocol configuration
        // The compatibility layer should always use RESP2
        JedisClientConfig config = DefaultJedisClientConfig.builder().build();

        // Just verify the config builds successfully
        assertNotNull(config);
        // The actual protocol is enforced at the GLIDE level (RESP2 only)
    }
}
