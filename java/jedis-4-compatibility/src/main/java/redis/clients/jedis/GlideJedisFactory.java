/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;

/**
 * Jedis 4.x factory: creates {@link Jedis} instances with the Jedis 4.x compatibility layer (RESP2
 * enforcement).
 */
public class GlideJedisFactory extends AbstractGlideJedisFactory<Jedis> {

    public GlideJedisFactory(String host, int port, JedisClientConfig clientConfig) {
        super(host, port, clientConfig);
    }

    @Override
    protected boolean isJedis5CompatibilityLayer() {
        return false;
    }

    @Override
    protected Jedis createJedis(GlideClient glideClient, JedisClientConfig clientConfig) {
        return new Jedis(glideClient, clientConfig);
    }
}
