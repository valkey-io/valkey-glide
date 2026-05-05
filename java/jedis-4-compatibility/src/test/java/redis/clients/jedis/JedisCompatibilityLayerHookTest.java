/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/** Verifies Jedis 4.x-shaped compatibility hooks on the thin {@link Jedis} facade. */
class JedisCompatibilityLayerHookTest {

    static final class Probe extends Jedis {
        boolean jedis5Layer() {
            return isJedis5CompatibilityLayer();
        }
    }

    @Test
    void jedis4ModuleDoesNotReportJedis5CompatibilityLayer() {
        assertFalse(new Probe().jedis5Layer());
    }
}
