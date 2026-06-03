/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import java.util.List;
import java.util.Map;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GeoSearchParam;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.HGetExParams;
import redis.clients.jedis.params.HSetExParams;
import redis.clients.jedis.params.LCSParams;
import redis.clients.jedis.params.LPosParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.params.XAddParams;
import redis.clients.jedis.params.XTrimParams;
import redis.clients.jedis.params.ZAddParams;
import redis.clients.jedis.params.ZIncrByParams;
import redis.clients.jedis.params.ZParams;
import redis.clients.jedis.params.ZRangeParams;
import redis.clients.jedis.resps.GeoRadiusResponse;
import redis.clients.jedis.resps.LCSMatchResult;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.resps.Tuple;

/**
 * Jedis 5.x-shaped compatibility facade; behavior is implemented in {@link AbstractGlideJedis}.
 *
 * <p>Shares the {@link JedisCommon} umbrella type with {@link UnifiedJedis}.
 */
public class Jedis extends BaseJedis {

    public Jedis() {
        super(true);
    }

    public Jedis(String host, int port) {
        super(true, host, port);
    }

    public Jedis(String host, int port, boolean useSsl) {
        super(true, host, port, useSsl);
    }

    public Jedis(String host, int port, JedisClientConfig config) {
        super(true, host, port, config);
    }

    public Jedis(
            String host,
            int port,
            boolean ssl,
            SSLSocketFactory sslSocketFactory,
            SSLParameters sslParameters,
            HostnameVerifier hostnameVerifier) {
        super(true, host, port, ssl, sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public Jedis(String host, int port, int timeout) {
        super(true, host, port, timeout);
    }

    public Jedis(HostAndPort hostAndPort, JedisClientConfig config) {
        super(true, hostAndPort, config);
    }

    public Jedis(GlideClient glideClient, JedisClientConfig config) {
        super(true, glideClient, config);
    }

    public Jedis(Connection connection) {
        super(true, connection);
    }

    // Stock Jedis parameter types for reflection / linkage; delegate to Abstract* implementations.
    public String set(final String key, final String value, final SetParams params) {
        return set(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public String set(final byte[] key, final byte[] value, final SetParams params) {
        return set(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public String setGet(final String key, final String value, final SetParams params) {
        return setGet(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public byte[] setGet(final byte[] key, final byte[] value, final SetParams params) {
        return setGet(key, value, (redis.clients.jedis.params.AbstractSetParams<?>) params);
    }

    public String getEx(final String key, final GetExParams params) {
        return getEx(key, (redis.clients.jedis.params.AbstractGetExParams<?>) params);
    }

    public byte[] getEx(final byte[] key, final GetExParams params) {
        return getEx(key, (redis.clients.jedis.params.AbstractGetExParams<?>) params);
    }

    public LCSMatchResult lcs(String keyA, String keyB, LCSParams params) {
        return lcs(keyA, keyB, (redis.clients.jedis.params.AbstractLCSParams<?>) params);
    }

    public LCSMatchResult lcs(byte[] keyA, byte[] keyB, LCSParams params) {
        return lcs(keyA, keyB, (redis.clients.jedis.params.AbstractLCSParams<?>) params);
    }

    public ScanResult<String> scan(final String cursor, final ScanParams params) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<byte[]> scan(final byte[] cursor, final ScanParams params) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<String> scan(final String cursor, final ScanParams params, final String type) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params, type);
    }

    public ScanResult<byte[]> scan(final byte[] cursor, final ScanParams params, final byte[] type) {
        return scan(cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params, type);
    }

    public long bitpos(final String key, final boolean value, final BitPosParams params) {
        return bitpos(key, value, (redis.clients.jedis.params.AbstractBitPosParams<?>) params);
    }

    public long bitpos(final byte[] key, final boolean value, final BitPosParams params) {
        return bitpos(key, value, (redis.clients.jedis.params.AbstractBitPosParams<?>) params);
    }

    public long hsetex(String key, HSetExParams params, String field, String value) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, field, value);
    }

    public long hsetex(String key, HSetExParams params, Map<String, String> hash) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, hash);
    }

    public List<String> hgetex(String key, HGetExParams params, String... fields) {
        return hgetex(key, (redis.clients.jedis.params.AbstractHGetExParams<?>) params, fields);
    }

    public long hsetex(byte[] key, HSetExParams params, byte[] field, byte[] value) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, field, value);
    }

    public long hsetex(byte[] key, HSetExParams params, Map<byte[], byte[]> hash) {
        return hsetex(key, (redis.clients.jedis.params.AbstractHSetExParams<?>) params, hash);
    }

    public List<byte[]> hgetex(byte[] key, HGetExParams params, byte[]... fields) {
        return hgetex(key, (redis.clients.jedis.params.AbstractHGetExParams<?>) params, fields);
    }

    public ScanResult<Map.Entry<String, String>> hscan(String key, String cursor, ScanParams params) {
        return hscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<Map.Entry<byte[], byte[]>> hscan(
            final byte[] key, final byte[] cursor, final ScanParams params) {
        return hscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<String> hscanNoValues(String key, String cursor, ScanParams params) {
        return hscanNoValues(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<byte[]> hscanNoValues(
            final byte[] key, final byte[] cursor, final ScanParams params) {
        return hscanNoValues(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public StreamEntryID xadd(String key, XAddParams params, Map<String, String> hash) {
        return xadd(key, (redis.clients.jedis.params.AbstractXAddParams<?>) params, hash);
    }

    public byte[] xadd(byte[] key, XAddParams params, Map<byte[], byte[]> hash) {
        return xadd(key, (redis.clients.jedis.params.AbstractXAddParams<?>) params, hash);
    }

    public long xtrim(String key, XTrimParams params) {
        return xtrim(key, (redis.clients.jedis.params.AbstractXTrimParams<?>) params);
    }

    public long xtrim(byte[] key, XTrimParams params) {
        return xtrim(key, (redis.clients.jedis.params.AbstractXTrimParams<?>) params);
    }

    public Long lpos(String key, String element, LPosParams params) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params);
    }

    public Long lpos(final byte[] key, final byte[] element, LPosParams params) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params);
    }

    public List<Long> lpos(String key, String element, LPosParams params, long count) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params, count);
    }

    public List<Long> lpos(final byte[] key, final byte[] element, LPosParams params, long count) {
        return lpos(key, element, (redis.clients.jedis.params.AbstractLPosParams<?>) params, count);
    }

    public long zadd(String key, double score, String member, ZAddParams params) {
        return zadd(key, score, member, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public long zadd(byte[] key, double score, byte[] member, ZAddParams params) {
        return zadd(key, score, member, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public long zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
        return zadd(key, scoreMembers, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public long zadd(final byte[] key, Map<byte[], Double> scoreMembers, ZAddParams params) {
        return zadd(key, scoreMembers, (redis.clients.jedis.params.AbstractZAddParams<?>) params);
    }

    public List<String> zrange(String key, ZRangeParams zRangeParams) {
        return zrange(key, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public List<byte[]> zrange(byte[] key, ZRangeParams zRangeParams) {
        return zrange(key, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public Double zincrby(String key, double increment, String member, ZIncrByParams params) {
        return zincrby(
                key, increment, member, (redis.clients.jedis.params.AbstractZIncrByParams<?>) params);
    }

    public Double zincrby(
            final byte[] key, double increment, final byte[] member, ZIncrByParams params) {
        return zincrby(
                key, increment, member, (redis.clients.jedis.params.AbstractZIncrByParams<?>) params);
    }

    public long zunionstore(String dstkey, ZParams params, String... sets) {
        return zunionstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public long zunionstore(final byte[] dstkey, ZParams params, final byte[]... sets) {
        return zunionstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public long zinterstore(String dstkey, ZParams params, String... sets) {
        return zinterstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public long zinterstore(final byte[] dstkey, ZParams params, final byte[]... sets) {
        return zinterstore(dstkey, (redis.clients.jedis.params.AbstractZParams<?>) params, sets);
    }

    public ScanResult<Tuple> zscan(String key, String cursor, ScanParams params) {
        return zscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<Tuple> zscan(byte[] key, byte[] cursor, ScanParams params) {
        return zscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public long zrangestore(String dest, String src, ZRangeParams zRangeParams) {
        return zrangestore(
                dest, src, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public long zrangestore(byte[] dest, byte[] src, ZRangeParams zRangeParams) {
        return zrangestore(
                dest, src, (redis.clients.jedis.params.AbstractZRangeParams<?>) zRangeParams);
    }

    public ScanResult<String> sscan(String key, String cursor, ScanParams params) {
        return sscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public ScanResult<byte[]> sscan(final byte[] key, final byte[] cursor, final ScanParams params) {
        return sscan(key, cursor, (redis.clients.jedis.params.AbstractScanParams<?>) params);
    }

    public List<String> sortReadonly(String key, SortingParams sortingParams) {
        return sortReadonly(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public List<byte[]> sortReadonly(final byte[] key, SortingParams sortingParams) {
        return sortReadonly(key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParams);
    }

    public long sort(String key, SortingParams sortingParameters, String dstkey) {
        return sort(
                key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParameters, dstkey);
    }

    public long sort(final byte[] key, SortingParams sortingParameters, final byte[] dstkey) {
        return sort(
                key, (redis.clients.jedis.params.AbstractSortingParams<?>) sortingParameters, dstkey);
    }

    public List<GeoRadiusResponse> geosearch(String key, GeoSearchParam params) {
        return geosearch(key, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public List<GeoRadiusResponse> geosearch(final byte[] key, GeoSearchParam params) {
        return geosearch(key, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStore(String dest, String src, GeoSearchParam params) {
        return geosearchStore(dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStore(final byte[] dest, final byte[] src, GeoSearchParam params) {
        return geosearchStore(dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStoreStoreDist(String dest, String src, GeoSearchParam params) {
        return geosearchStoreStoreDist(
                dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }

    public long geosearchStoreStoreDist(final byte[] dest, final byte[] src, GeoSearchParam params) {
        return geosearchStoreStoreDist(
                dest, src, (redis.clients.jedis.params.AbstractGeoSearchParam<?>) params);
    }
}
