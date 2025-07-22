/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Jedis compatibility layer classes.
 * These tests verify API contracts and basic functionality without creating actual connections.
 */
public class BasicCompatibilityTest {

    @Test
    public void testJedisConstructors() {
        // Test that constructors exist and can be called without throwing compilation errors
        // We don't test actual connection since these are unit tests
        
        // Test constructor signatures exist
        assertDoesNotThrow(() -> {
            Class<Jedis> jedisClass = Jedis.class;
            
            // Verify constructors exist
            jedisClass.getConstructor(); // Default constructor
            jedisClass.getConstructor(String.class, int.class); // Host/port constructor
            jedisClass.getConstructor(String.class, int.class, boolean.class); // Host/port/ssl constructor
            jedisClass.getConstructor(String.class, int.class, int.class); // Host/port/timeout constructor
        });
    }

    @Test
    public void testJedisWithConfig() {
        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(5000)
                .connectionTimeoutMillis(3000)
                .database(1)
                .build();

        assertDoesNotThrow(() -> {
            new Jedis("localhost", 6379, config);
        });
    }

    @Test
    public void testJedisPoolConstructors() {
        assertDoesNotThrow(() -> {
            new JedisPool("localhost", 6379);
        });

        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(2000)
                .build();

        assertDoesNotThrow(() -> {
            new JedisPool("localhost", 6379, config, 10, 5000);
        });
    }

    @Test
    public void testJedisPooledConstructors() {
        assertDoesNotThrow(() -> {
            new JedisPooled("localhost", 6379);
        });

        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(2000)
                .build();

        assertDoesNotThrow(() -> {
            new JedisPooled(config);
        });
    }

    @Test
    public void testUnifiedJedisConstructors() {
        assertDoesNotThrow(() -> {
            new UnifiedJedis("localhost", 6379);
        });

        assertDoesNotThrow(() -> {
            new UnifiedJedis(new HostAndPort("localhost", 6379));
        });

        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(2000)
                .build();

        assertDoesNotThrow(() -> {
            new UnifiedJedis(new HostAndPort("localhost", 6379), config);
        });
    }

    @Test
    public void testJedisClientConfigBuilder() {
        JedisClientConfig config = DefaultJedisClientConfig.builder()
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

        JedisConnectionException connectionException = new JedisConnectionException("Connection failed");
        assertEquals("Connection failed", connectionException.getMessage());
        assertTrue(connectionException instanceof JedisException);
    }
}
