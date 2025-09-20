/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

/** Unit tests to verify Apache Commons Pool 2 integration works correctly. */
public class PoolImportTest {

    @Test
    public void testPoolConfigurationTypes() {
        // Test that we can create pool configuration
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        assertNotNull(poolConfig);

        // Test default values
        assertTrue(poolConfig.getMaxTotal() >= 0);
        assertTrue(poolConfig.getMaxIdle() >= 0);
        assertTrue(poolConfig.getMinIdle() >= 0);
    }

    @Test
    public void testPooledObjectFactoryInterface() {
        // Test that PooledObjectFactory interface is available and can be referenced
        Class<?> factoryClass = PooledObjectFactory.class;
        assertNotNull(factoryClass);
        assertTrue(factoryClass.isInterface());
    }

    @Test
    public void testJedisPooledWithPoolConfig() {
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);

        // Test that JedisPooled constructor signature exists
        assertDoesNotThrow(
                () -> {
                    Class<JedisPooled> pooledClass = JedisPooled.class;
                    pooledClass.getConstructor(GenericObjectPoolConfig.class);
                });
    }

    @Test
    public void testPoolConfigurationSettings() {
        GenericObjectPoolConfig<Object> poolConfig = new GenericObjectPoolConfig<>();

        // Test setting various pool parameters
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(2);
        poolConfig.setMaxWaitMillis(5000);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        assertEquals(20, poolConfig.getMaxTotal());
        assertEquals(10, poolConfig.getMaxIdle());
        assertEquals(2, poolConfig.getMinIdle());
        assertEquals(5000, poolConfig.getMaxWaitMillis());
        assertTrue(poolConfig.getTestOnBorrow());
        assertTrue(poolConfig.getTestOnReturn());
    }

    @Test
    public void testJedisPoolWithConfiguration() {
        JedisClientConfig clientConfig =
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(3000)
                        .connectionTimeoutMillis(2000)
                        .build();

        // Test that JedisPool constructor signature exists
        assertDoesNotThrow(
                () -> {
                    Class<JedisPool> poolClass = JedisPool.class;
                    poolClass.getConstructor(
                            GenericObjectPoolConfig.class, String.class, int.class, JedisClientConfig.class);
                });
    }
}
