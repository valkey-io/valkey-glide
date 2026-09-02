/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.MigrateParams;
import redis.clients.jedis.params.RestoreParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Jedis 4.x-shaped unified client; behavior is implemented in {@link AbstractUnifiedJedis}.
 *
 * <p>Shares the {@link JedisCommon} umbrella type with {@link Jedis}.
 */
public class UnifiedJedis extends BaseUnifiedJedis {

    public UnifiedJedis() {
        super(false);
    }

    public UnifiedJedis(String host, int port) {
        super(false, host, port);
    }

    public UnifiedJedis(HostAndPort hostAndPort) {
        super(false, hostAndPort);
    }

    public UnifiedJedis(String url) {
        super(false, url);
    }

    public UnifiedJedis(URI uri) {
        super(false, uri);
    }

    public UnifiedJedis(URI uri, JedisClientConfig config) {
        super(false, uri, config);
    }

    public UnifiedJedis(HostAndPort hostAndPort, JedisClientConfig clientConfig) {
        super(false, hostAndPort, clientConfig);
    }

    public UnifiedJedis(String host, int port, JedisClientConfig clientConfig) {
        super(false, host, port, clientConfig);
    }

    public UnifiedJedis(String host, int port, int timeout, String password) {
        super(false, host, port, timeout, password);
    }

    public UnifiedJedis(String host, int port, int timeout) {
        super(false, host, port, timeout);
    }

    public UnifiedJedis(String host, int port, int timeout, String password, int database) {
        super(false, host, port, timeout, password, database);
    }

    public UnifiedJedis(
            String host, int port, int timeout, String password, int database, String clientName) {
        super(false, host, port, timeout, password, database, clientName);
    }

    public UnifiedJedis(Set<HostAndPort> jedisClusterNodes) {
        super(false, jedisClusterNodes);
    }

    public UnifiedJedis(Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig) {
        super(false, jedisClusterNodes, clientConfig);
    }

    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig, int maxAttempts) {
        super(false, jedisClusterNodes, clientConfig, maxAttempts);
    }

    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes,
            JedisClientConfig clientConfig,
            int maxAttempts,
            Duration maxTotalRetriesDuration) {
        super(false, jedisClusterNodes, clientConfig, maxAttempts, maxTotalRetriesDuration);
    }

    public UnifiedJedis(ConnectionProvider provider) {
        super(false, provider);
    }

    public UnifiedJedis(
            ConnectionProvider provider, int maxAttempts, Duration maxTotalRetriesDuration) {
        super(false, provider, maxAttempts, maxTotalRetriesDuration);
    }

    protected UnifiedJedis(GlideClient glideClient, JedisClientConfig jedisConfig) {
        super(false, glideClient, jedisConfig);
    }

    protected UnifiedJedis(GlideClusterClient glideClusterClient, JedisClientConfig jedisConfig) {
        super(false, glideClusterClient, jedisConfig);
    }

    // Stock Jedis parameter types for reflection / linkage; delegate to Abstract* implementations.
    public String set(String key, String value, SetParams params) {
        return set(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public String setGet(String key, String value, SetParams params) {
        return setGet(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public String getEx(String key, GetExParams params) {
        return getEx(key, (redis.clients.jedis.params.AbstractGetExParams<?>) params);
    }

    public List<String> sort(String key, SortingParams sortingParams) {
        return sort(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public long sort(String key, SortingParams sortingParams, String dstkey) {
        return sort(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams, dstkey);
    }

    public List<String> sortReadonly(String key, SortingParams sortingParams) {
        return sortReadonly(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public String restore(String key, long ttl, byte[] serializedValue, RestoreParams params) {
        return restore(
                key, ttl, serializedValue, (redis.clients.jedis.params.AbstractRestoreParams<?>) params);
    }

    public String migrate(String host, int port, int timeout, MigrateParams params, String... keys) {
        return migrate(
                host, port, timeout, (redis.clients.jedis.params.AbstractMigrateParams<?>) params, keys);
    }

    public ScanResult<String> scan(String cursor, ScanParams params) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<String> scan(String cursor, ScanParams params, String type) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params, type);
    }

    public long bitpos(String key, boolean value, BitPosParams params) {
        return bitpos(key, value, (redis.clients.jedis.params.AbstractBitPosParams<?>) params);
    }

    public long bitpos(byte[] key, boolean value, BitPosParams params) {
        return bitpos(key, value, (redis.clients.jedis.params.AbstractBitPosParams<?>) params);
    }

    public String restore(byte[] key, long ttl, byte[] serializedValue, RestoreParams params) {
        return restore(
                key, ttl, serializedValue, (redis.clients.jedis.params.AbstractRestoreParams<?>) params);
    }

    public String set(byte[] key, byte[] value, SetParams params) {
        return set(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public byte[] setGet(byte[] key, byte[] value, SetParams params) {
        return setGet(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public List<byte[]> sort(byte[] key, SortingParams sortingParams) {
        return sort(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public long sort(byte[] key, SortingParams sortingParams, byte[] dstkey) {
        return sort(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams, dstkey);
    }

    public List<byte[]> sortReadonly(byte[] key, SortingParams sortingParams) {
        return sortReadonly(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public byte[] getEx(byte[] key, GetExParams params) {
        return getEx(key, (redis.clients.jedis.params.AbstractGetExParams<?>) params);
    }

    public String migrate(String host, int port, int timeout, MigrateParams params, byte[]... keys) {
        return migrate(
                host, port, timeout, (redis.clients.jedis.params.AbstractMigrateParams<?>) params, keys);
    }

    public ScanResult<byte[]> scan(byte[] cursor, ScanParams params) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<byte[]> scan(byte[] cursor, ScanParams params, byte[] type) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params, type);
    }
}
