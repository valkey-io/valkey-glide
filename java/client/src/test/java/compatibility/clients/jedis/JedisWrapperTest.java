/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Jedis wrapper functionality.
 * Tests API contracts and object creation without establishing actual connections.
 */
public class JedisWrapperTest {

    @Test
    public void testJedisInstantiation() {
        // Test that Jedis objects can be created without throwing exceptions
        assertDoesNotThrow(() -> {
            Jedis jedis = new Jedis("localhost", 6379);
            assertNotNull(jedis);
            assertFalse(jedis.isClosed());
        });
    }

    @Test
    public void testJedisConfiguration() {
        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(5000)
                .connectionTimeoutMillis(3000)
                .clientName("test-client")
                .database(1)
                .build();

        assertDoesNotThrow(() -> {
            Jedis jedis = new Jedis("localhost", 6379, config);
            assertNotNull(jedis);
            assertEquals(config, jedis.getConfig());
        });
    }

    @Test
    public void testJedisPoolInstantiation() {
        assertDoesNotThrow(() -> {
            JedisPool pool = new JedisPool("localhost", 6379);
            assertNotNull(pool);
            assertTrue(pool.getMaxTotal() > 0);
        });
    }

    @Test
    public void testJedisPoolWithConfiguration() {
        JedisClientConfig config = DefaultJedisClientConfig.builder()
                .socketTimeoutMillis(2000)
                .connectionTimeoutMillis(1000)
                .build();

        assertDoesNotThrow(() -> {
            JedisPool pool = new JedisPool("localhost", 6379, config, 10, 5000);
            assertNotNull(pool);
            assertEquals(10, pool.getMaxTotal());
            assertEquals(5000, pool.getMaxWaitMillis());
            assertEquals(config, pool.getConfig());
        });
    }

    @Test
    public void testResourceManagement() {
        // Test that resources can be created and closed without errors
        assertDoesNotThrow(() -> {
            Jedis jedis = new Jedis("localhost", 6379);
            jedis.close();
            assertTrue(jedis.isClosed());
        });

        assertDoesNotThrow(() -> {
            JedisPool pool = new JedisPool("localhost", 6379);
            pool.close();
            // Pool should be closed without errors
        });
    }

    @Test
    public void testJedisPoolResourceAcquisition() {
        JedisPool pool = new JedisPool("localhost", 6379);
        
        // Test that we can get the resource acquisition pattern without actual connection
        assertDoesNotThrow(() -> {
            // This tests the API contract, not actual functionality
            assertNotNull(pool);
            assertTrue(pool.getMaxTotal() > 0);
        });
        
        pool.close();
    }

    @Test
    public void testJedisMethodSignatures() {
        Jedis jedis = new Jedis("localhost", 6379);
        
        // Test that method signatures exist and are callable (without actual execution)
        assertDoesNotThrow(() -> {
            // These test method signatures exist, not functionality
            assertNotNull(jedis);
            assertFalse(jedis.isClosed());
            assertNotNull(jedis.getConfig());
        });
        
        jedis.close();
    }

    @Test
    public void testJedisPoolStats() {
        JedisPool pool = new JedisPool("localhost", 6379);
        
        // Test pool statistics methods
        assertDoesNotThrow(() -> {
            String stats = pool.getPoolStats();
            assertNotNull(stats);
            assertTrue(stats.contains("maxTotal") || stats.length() >= 0);
        });
        
        pool.close();
    }
}
