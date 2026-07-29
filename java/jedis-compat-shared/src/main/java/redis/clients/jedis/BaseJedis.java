/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/**
 * Base Jedis compatibility facade shared between Jedis 4.x and 5.x compatibility layers. All
 * behavior is implemented in {@link AbstractGlideJedis}.
 *
 * <p>This abstract class contains all common constructors. Parameter wrapper methods that accept
 * concrete param types (SetParams, ScanParams, etc.) remain in version-specific subclasses since
 * those param classes only exist in the version-specific modules.
 */
public abstract class BaseJedis extends AbstractGlideJedis {

    protected BaseJedis(boolean jedis5) {
        this(jedis5, "localhost", 6379);
    }

    protected BaseJedis(boolean jedis5, String host, int port) {
        this(jedis5, host, port, DefaultJedisClientConfig.builder().build());
    }

    protected BaseJedis(boolean jedis5, String host, int port, boolean useSsl) {
        this(jedis5, host, port, DefaultJedisClientConfig.builder().ssl(useSsl).build());
    }

    protected BaseJedis(boolean jedis5, String host, int port, JedisClientConfig config) {
        super(jedis5, host, port, config);
    }

    protected BaseJedis(
            boolean jedis5,
            String host,
            int port,
            boolean ssl,
            SSLSocketFactory sslSocketFactory,
            SSLParameters sslParameters,
            HostnameVerifier hostnameVerifier) {
        super(jedis5, host, port, ssl, sslSocketFactory, sslParameters, hostnameVerifier);
    }

    protected BaseJedis(boolean jedis5, String host, int port, int timeout) {
        this(
                jedis5,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build());
    }

    protected BaseJedis(boolean jedis5, HostAndPort hostAndPort, JedisClientConfig config) {
        this(jedis5, hostAndPort.getHost(), hostAndPort.getPort(), config);
    }

    protected BaseJedis(boolean jedis5, GlideClient glideClient, JedisClientConfig config) {
        super(jedis5, glideClient, config);
    }

    protected BaseJedis(boolean jedis5, Connection connection) {
        this(jedis5, connection.getHost(), connection.getPort());
    }
}
