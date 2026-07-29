/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifies Jedis 5.x-shaped compatibility hooks on the thin {@link Jedis} facade. */
class JedisCompatibilityLayerHookTest {

    static final class Probe extends Jedis {
        boolean jedis5Layer() {
            return isJedis5CompatibilityLayer();
        }
    }

    @Test
    void jedis5ModuleReportsJedis5CompatibilityLayer() {
        assertTrue(new Probe().jedis5Layer());
    }
}
