/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;

/**
 * Jedis 5.x factory: creates {@link Jedis} instances with the Jedis 5.x compatibility layer (RESP2
 * default, RESP3 configurable).
 */
public class GlideJedisFactory extends AbstractGlideJedisFactory<Jedis> {

    public GlideJedisFactory(String host, int port, JedisClientConfig clientConfig) {
        super(host, port, clientConfig);
    }

    @Override
    protected boolean isJedis5CompatibilityLayer() {
        return true;
    }

    @Override
    protected Jedis createJedis(GlideClient glideClient, JedisClientConfig clientConfig) {
        return new Jedis(glideClient, clientConfig);
    }
}
