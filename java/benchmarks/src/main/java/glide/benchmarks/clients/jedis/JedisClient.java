/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.jedis;

import glide.benchmarks.clients.SyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.Set;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.commands.JedisCommands;

/** A Jedis client with sync capabilities. See: https://github.com/redis/jedis */
public class JedisClient implements SyncClient {

    private JedisCommands jedis;

    @Override
    public void closeConnection() {
        // nothing to do
    }

    @Override
    public String getName() {
        return "Jedis";
    }

    @Override
    public void connectToRedis(ConnectionSettings connectionSettings) {
        if (connectionSettings.clusterMode) {
            jedis =
                    new JedisCluster(
                            Set.of(new HostAndPort(connectionSettings.host, connectionSettings.port)),
                            DefaultJedisClientConfig.builder().ssl(connectionSettings.useSsl).build());
        } else {
            try (JedisPool pool =
                    new JedisPool(
                            connectionSettings.host, connectionSettings.port, connectionSettings.useSsl)) {
                jedis = pool.getResource();
            }
        }
    }

    @Override
    public void set(String key, String value) {
        jedis.set(key, value);
    }

    @Override
    public String get(String key) {
        return jedis.get(key);
    }
}
