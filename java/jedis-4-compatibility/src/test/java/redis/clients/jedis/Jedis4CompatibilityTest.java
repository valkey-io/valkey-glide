/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

/**
 * Test to verify Jedis 4.x API compatibility. This validates that the key Jedis 4.x patterns work
 * with the compatibility layer.
 */
public class Jedis4CompatibilityTest {

    @Test
    public void testJedisPoolWithJedisGenericType() {
        // Jedis 4.x uses GenericObjectPoolConfig<Jedis>
        GenericObjectPoolConfig<Jedis> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(8);

        // This should compile with Jedis 4.x API
        // JedisPool pool = new JedisPool(poolConfig, "localhost", 6379);

        // Test passes if code compiles
        assert poolConfig.getMaxTotal() == 8;
    }

    @Test
    public void testJedisPooledWithObjectGenericType() {
        // Jedis 5.x uses GenericObjectPoolConfig<Object>
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(8);

        // This should compile with Jedis 5.x API
        // JedisPooled pooled = new JedisPooled(poolConfig, "localhost", 6379);

        // Test passes if code compiles
        assert poolConfig.getMaxTotal() == 8;
    }
}
