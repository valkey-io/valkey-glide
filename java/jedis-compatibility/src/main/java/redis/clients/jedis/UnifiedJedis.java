/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

/** Jedis 5.x-shaped unified client; behavior is implemented in {@link AbstractUnifiedJedis}. */
public class UnifiedJedis extends AbstractUnifiedJedis {

    public UnifiedJedis() {
        super();
    }

    public UnifiedJedis(String host, int port) {
        super(host, port);
    }

    public UnifiedJedis(HostAndPort hostAndPort) {
        super(hostAndPort);
    }

    public UnifiedJedis(String url) {
        super(url);
    }

    public UnifiedJedis(URI uri) {
        super(uri);
    }

    public UnifiedJedis(URI uri, JedisClientConfig config) {
        super(uri, config);
    }

    public UnifiedJedis(HostAndPort hostAndPort, JedisClientConfig clientConfig) {
        super(hostAndPort, clientConfig);
    }

    public UnifiedJedis(String host, int port, JedisClientConfig clientConfig) {
        super(host, port, clientConfig);
    }

    public UnifiedJedis(String host, int port, int timeout, String password) {
        super(host, port, timeout, password);
    }

    public UnifiedJedis(String host, int port, int timeout) {
        super(host, port, timeout);
    }

    public UnifiedJedis(String host, int port, int timeout, String password, int database) {
        super(host, port, timeout, password, database);
    }

    public UnifiedJedis(
            String host, int port, int timeout, String password, int database, String clientName) {
        super(host, port, timeout, password, database, clientName);
    }

    public UnifiedJedis(Set<HostAndPort> jedisClusterNodes) {
        super(jedisClusterNodes);
    }

    public UnifiedJedis(Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig) {
        super(jedisClusterNodes, clientConfig);
    }

    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig, int maxAttempts) {
        super(jedisClusterNodes, clientConfig, maxAttempts);
    }

    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes,
            JedisClientConfig clientConfig,
            int maxAttempts,
            Duration maxTotalRetriesDuration) {
        super(jedisClusterNodes, clientConfig, maxAttempts, maxTotalRetriesDuration);
    }

    public UnifiedJedis(ConnectionProvider provider) {
        super(provider);
    }

    public UnifiedJedis(
            ConnectionProvider provider, int maxAttempts, Duration maxTotalRetriesDuration) {
        super(provider, maxAttempts, maxTotalRetriesDuration);
    }

    protected UnifiedJedis(GlideClient glideClient, JedisClientConfig jedisConfig) {
        super(glideClient, jedisConfig);
    }

    protected UnifiedJedis(GlideClusterClient glideClusterClient, JedisClientConfig jedisConfig) {
        super(glideClusterClient, jedisConfig);
    }

    @Override
    protected boolean isJedis5CompatibilityLayer() {
        return true;
    }
}
