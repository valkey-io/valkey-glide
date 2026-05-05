/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Ensures public facades extend the shared {@link JedisCommon} umbrella type. */
class JedisCommonHierarchyTest {

    @Test
    void jedisExtendsJedisCommon() {
        assertTrue(JedisCommon.class.isAssignableFrom(Jedis.class));
        assertTrue(AbstractGlideJedis.class.isAssignableFrom(Jedis.class));
    }

    @Test
    void unifiedJedisExtendsJedisCommon() {
        assertTrue(JedisCommon.class.isAssignableFrom(UnifiedJedis.class));
        assertTrue(AbstractUnifiedJedis.class.isAssignableFrom(UnifiedJedis.class));
    }
}
