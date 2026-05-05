/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.io.Closeable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Umbrella base for Valkey GLIDE Jedis compatibility facades.
 *
 * <p>Both the classic {@link Jedis} implementation path ({@link AbstractGlideJedis}) and the
 * unified standalone/cluster path ({@link AbstractUnifiedJedis}) extend this type so callers,
 * tests, or adapters can treat them under one supertype (e.g. {@code instance of JedisCommon})
 * while sharing layer-discrimination hooks.
 *
 * @see AbstractGlideJedis
 * @see AbstractUnifiedJedis
 */
public abstract class JedisCommon implements Closeable {

    /**
     * {@code true} when this instance follows Jedis 5.x-shaped compatibility semantics; {@code false}
     * for Jedis 4.x-shaped semantics (encoding, {@code SELECT}/{@code CLIENT} behavior, protocol
     * mapping, cluster scan paths, etc.).
     */
    protected abstract boolean isJedis5CompatibilityLayer();

    /**
     * Charset used for binary/string conversions where the Jedis 4.x and 5.x layers intentionally
     * differ (Jedis 5-shaped uses the platform default; Jedis 4-shaped uses UTF-8).
     */
    protected Charset jedisBinaryCharset() {
        return isJedis5CompatibilityLayer() ? Charset.defaultCharset() : StandardCharsets.UTF_8;
    }
}
