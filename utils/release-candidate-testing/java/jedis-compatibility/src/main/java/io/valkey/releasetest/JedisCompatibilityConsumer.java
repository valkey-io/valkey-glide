/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.valkey.releasetest;

import redis.clients.jedis.JedisPooled;

public final class JedisCompatibilityConsumer {
    private JedisCompatibilityConsumer() {}

    public static JedisPooled createClient(String host, int port) {
        return new JedisPooled(host, port);
    }

    public static String set(JedisPooled client, String key, String value) {
        return client.set(key, value);
    }
}
