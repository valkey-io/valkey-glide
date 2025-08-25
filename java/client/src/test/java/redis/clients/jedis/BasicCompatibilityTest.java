/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Unit tests for Jedis compatibility layer classes. These tests verify API contracts and basic
 * functionality without creating actual connections.
 */
public class BasicCompatibilityTest {

    @Test
    public void testJedisConstructors() {
        // Test that constructors exist and can be called without throwing compilation errors
        // We don't test actual connection since these are unit tests

        // Test constructor signatures exist
        assertDoesNotThrow(
                () -> {
                    Class<Jedis> jedisClass = Jedis.class;

                    // Verify constructors exist
                    jedisClass.getConstructor(); // Default constructor
                    jedisClass.getConstructor(String.class, int.class); // Host/port constructor
                    jedisClass.getConstructor(
                            String.class, int.class, boolean.class); // Host/port/ssl constructor
                    jedisClass.getConstructor(
                            String.class, int.class, int.class); // Host/port/timeout constructor
                });
    }

    @Test
    public void testJedisWithConfig() {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(5000)
                        .connectionTimeoutMillis(3000)
                        .database(1)
                        .build();

        // Test that constructor signature exists
        assertDoesNotThrow(
                () -> {
                    Class<Jedis> jedisClass = Jedis.class;
                    jedisClass.getConstructor(String.class, int.class, JedisClientConfig.class);
                });
    }

    @Test
    public void testJedisPoolConstructors() {
        // Test that constructor signatures exist
        assertDoesNotThrow(
                () -> {
                    Class<JedisPool> poolClass = JedisPool.class;
                    poolClass.getConstructor(String.class, int.class);
                });

        JedisClientConfig config = DefaultJedisClientConfig.builder().socketTimeoutMillis(2000).build();

        assertDoesNotThrow(
                () -> {
                    Class<JedisPool> poolClass = JedisPool.class;
                    // Test the proper constructor with GenericObjectPoolConfig
                    poolClass.getConstructor(
                            GenericObjectPoolConfig.class, String.class, int.class, JedisClientConfig.class);
                });
    }

    @Test
    public void testJedisPooledConstructors() {
        // Test that constructor signatures exist
        assertDoesNotThrow(
                () -> {
                    Class<JedisPooled> pooledClass = JedisPooled.class;
                    pooledClass.getConstructor(String.class, int.class);
                    pooledClass.getConstructor(); // Default constructor
                    pooledClass.getConstructor(String.class); // URL constructor
                    pooledClass.getConstructor(HostAndPort.class);
                });

        JedisClientConfig config = DefaultJedisClientConfig.builder().socketTimeoutMillis(2000).build();

        assertDoesNotThrow(
                () -> {
                    Class<JedisPooled> pooledClass = JedisPooled.class;
                    pooledClass.getConstructor(HostAndPort.class, JedisClientConfig.class);
                    pooledClass.getConstructor(GenericObjectPoolConfig.class);
                });
    }

    @Test
    public void testUnifiedJedisConstructors() {
        // Test that constructor signatures exist without creating actual connections
        assertDoesNotThrow(
                () -> {
                    Class<UnifiedJedis> unifiedJedisClass = UnifiedJedis.class;

                    // Verify constructors exist
                    unifiedJedisClass.getConstructor(); // Default constructor
                    unifiedJedisClass.getConstructor(String.class, int.class); // Host/port constructor
                    unifiedJedisClass.getConstructor(HostAndPort.class); // HostAndPort constructor
                    unifiedJedisClass.getConstructor(
                            HostAndPort.class, JedisClientConfig.class); // HostAndPort with config
                    unifiedJedisClass.getConstructor(String.class); // URL constructor
                });
    }

    @Test
    public void testJedisClientConfigBuilder() {
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(5000)
                        .connectionTimeoutMillis(3000)
                        .password("testpass")
                        .clientName("test-client")
                        .database(2)
                        .protocol(RedisProtocol.RESP2)
                        .ssl(true)
                        .build();

        assertEquals(5000, config.getSocketTimeoutMillis());
        assertEquals(3000, config.getConnectionTimeoutMillis());
        assertEquals("testpass", config.getPassword());
        assertEquals("test-client", config.getClientName());
        assertEquals(2, config.getDatabase());
        assertEquals(RedisProtocol.RESP2, config.getRedisProtocol());
        assertTrue(config.isSsl());
    }

    @Test
    public void testHostAndPortClass() {
        HostAndPort hostAndPort = new HostAndPort("localhost", 6379);
        assertEquals("localhost", hostAndPort.getHost());
        assertEquals(6379, hostAndPort.getPort());

        // Test parsing functionality
        HostAndPort parsed = HostAndPort.parseString("localhost:6379");
        assertEquals("localhost", parsed.getHost());
        assertEquals(6379, parsed.getPort());
    }

    @Test
    public void testRedisProtocolEnum() {
        assertNotNull(RedisProtocol.RESP2);
        assertNotNull(RedisProtocol.RESP3);

        // Test enum values
        RedisProtocol[] protocols = RedisProtocol.values();
        assertTrue(protocols.length >= 2);
    }

    @Test
    public void testJedisExceptionHierarchy() {
        JedisException exception = new JedisException("Test message");
        assertEquals("Test message", exception.getMessage());
        assertTrue(exception instanceof RuntimeException);

        JedisConnectionException connectionException =
                new JedisConnectionException("Connection failed");
        assertEquals("Connection failed", connectionException.getMessage());
        assertTrue(connectionException instanceof JedisException);
    }
}
