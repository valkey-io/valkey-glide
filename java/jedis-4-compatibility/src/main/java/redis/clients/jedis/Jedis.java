/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/** Jedis 4.x-shaped compatibility facade; behavior is implemented in {@link AbstractGlideJedis}. */
public class Jedis extends AbstractGlideJedis {

    public Jedis() {
        super();
    }

    public Jedis(String host, int port) {
        super(host, port);
    }

    public Jedis(String host, int port, boolean useSsl) {
        super(host, port, useSsl);
    }

    public Jedis(String host, int port, JedisClientConfig config) {
        super(host, port, config);
    }

    public Jedis(
            String host,
            int port,
            boolean ssl,
            SSLSocketFactory sslSocketFactory,
            SSLParameters sslParameters,
            HostnameVerifier hostnameVerifier) {
        super(host, port, ssl, sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public Jedis(String host, int port, int timeout) {
        super(host, port, timeout);
    }

    public Jedis(HostAndPort hostAndPort, JedisClientConfig config) {
        super(hostAndPort, config);
    }

    public Jedis(GlideClient glideClient, JedisClientConfig config) {
        super(glideClient, config);
    }

    public Jedis(Connection connection) {
        super(connection);
    }

    @Override
    protected boolean isJedis5CompatibilityLayer() {
        return false;
    }
}
