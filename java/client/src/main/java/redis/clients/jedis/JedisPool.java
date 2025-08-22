/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.net.URI;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.util.JedisURIHelper;
import redis.clients.jedis.util.Pool;

/**
 * JedisPool compatibility wrapper for Valkey GLIDE client. This class provides a Jedis-like
 * connection pool API while using Valkey GLIDE underneath. It matches the original Jedis JedisPool
 * API for maximum compatibility.
 */
public class JedisPool extends Pool<Jedis> {

    public JedisPool() {
        this(Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    /**
     * WARNING: This constructor only accepts a uri string as {@code url}. {@link
     * JedisURIHelper#isValid(java.net.URI)} can be used before this.
     *
     * <p>To use a host string, {@link #JedisPool(java.lang.String, int)} can be used with {@link
     * Protocol#DEFAULT_PORT}.
     *
     * @param url the Redis URI
     */
    public JedisPool(final String url) {
        this(URI.create(url));
    }

    /**
     * WARNING: This constructor only accepts a uri string as {@code url}. {@link
     * JedisURIHelper#isValid(java.net.URI)} can be used before this.
     *
     * <p>To use a host string, {@link #JedisPool(java.lang.String, int, boolean,
     * javax.net.ssl.SSLSocketFactory, javax.net.ssl.SSLParameters, javax.net.ssl.HostnameVerifier)}
     * can be used with {@link Protocol#DEFAULT_PORT} and {@code ssl=true}.
     *
     * @param url the Redis URI
     * @param sslSocketFactory SSL socket factory
     * @param sslParameters SSL parameters
     * @param hostnameVerifier hostname verifier
     */
    public JedisPool(
            final String url,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(URI.create(url), sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public JedisPool(final String host, final int port) {
        this(new GenericObjectPoolConfig<Jedis>(), host, port);
    }

    public JedisPool(final String host, final int port, final boolean ssl) {
        this(new GenericObjectPoolConfig<Jedis>(), host, port, ssl);
    }

    public JedisPool(
            final String host,
            final int port,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                host,
                port,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPool(final String host, int port, String user, final String password) {
        this(new GenericObjectPoolConfig<Jedis>(), host, port, user, password);
    }

    public JedisPool(final String host, final int port, final int timeout) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build());
    }

    public JedisPool(final String host, final int port, final String password) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                host,
                port,
                DefaultJedisClientConfig.builder().password(password).build());
    }

    public JedisPool(final String host, final int port, final int timeout, final String password) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .password(password)
                        .build());
    }

    public JedisPool(final HostAndPort hostAndPort, final JedisClientConfig clientConfig) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                hostAndPort.getHost(),
                hostAndPort.getPort(),
                clientConfig);
    }

    public JedisPool(PooledObjectFactory<Jedis> factory) {
        this(new GenericObjectPoolConfig<Jedis>(), factory);
    }

    public JedisPool(final GenericObjectPoolConfig<Jedis> poolConfig) {
        this(poolConfig, Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    public JedisPool(final URI uri) {
        this(new GenericObjectPoolConfig<Jedis>(), uri);
    }

    public JedisPool(
            final URI uri,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                uri,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPool(final URI uri, final int timeout) {
        this(new GenericObjectPoolConfig<Jedis>(), uri, timeout);
    }

    public JedisPool(
            final URI uri,
            final int timeout,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                uri,
                timeout,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPool(final GenericObjectPoolConfig<Jedis> poolConfig, final String url) {
        this(poolConfig, URI.create(url));
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig, final String host, final int port) {
        this(poolConfig, host, port, DefaultJedisClientConfig.builder().build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            final int port,
            final boolean ssl) {
        this(poolConfig, host, port, DefaultJedisClientConfig.builder().ssl(ssl).build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            final int port,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig, final String host, int port, int timeout) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .password(password)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database,
            final String clientName) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password,
            final int database,
            final String clientName) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .user(user)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .user(user)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            final int port,
            final int connectionTimeout,
            final int soTimeout,
            final String password,
            final int database,
            final String clientName) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            final int port,
            final int connectionTimeout,
            final int soTimeout,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final String user,
            final String password,
            final int database,
            final String clientName) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .user(user)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final String user,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .user(user)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final String user,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .user(user)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            final String user,
            final String password) {
        this(
                poolConfig,
                host,
                port,
                DefaultJedisClientConfig.builder().user(user).password(password).build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            int port,
            final JedisClientConfig clientConfig) {
        // Create factory and set pool reference
        GlideJedisFactory factory = new GlideJedisFactory(host, port, clientConfig);
        initPool(poolConfig, factory);
        factory.setPool(this); // Set pool reference after initialization
    }

    public JedisPool(final GenericObjectPoolConfig<Jedis> poolConfig, final URI uri) {
        this(poolConfig, uri, DefaultJedisClientConfig.builder().build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final URI uri,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                uri,
                DefaultJedisClientConfig.builder()
                        .ssl(JedisURIHelper.isRedisSSLScheme(uri))
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig, final URI uri, final int timeout) {
        this(
                poolConfig,
                uri,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build());
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final URI uri,
            final int timeout,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                uri,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .ssl(JedisURIHelper.isRedisSSLScheme(uri))
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    private JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final URI uri,
            final JedisClientConfig clientConfig) {
        // Create factory and set pool reference
        GlideJedisFactory factory =
                new GlideJedisFactory(uri.getHost(), uri.getPort(), mergeUriConfig(uri, clientConfig));
        initPool(poolConfig, factory);
        factory.setPool(this); // Set pool reference after initialization
    }

    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig, PooledObjectFactory<Jedis> factory) {
        initPool(poolConfig, factory);

        // If it's a GlideJedisFactory, set the pool reference
        if (factory instanceof GlideJedisFactory) {
            ((GlideJedisFactory) factory).setPool(this);
        }
    }

    /** Merge URI configuration with client configuration. */
    private static JedisClientConfig mergeUriConfig(URI uri, JedisClientConfig clientConfig) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();

        // Copy existing config
        builder
                .socketTimeoutMillis(clientConfig.getSocketTimeoutMillis())
                .connectionTimeoutMillis(clientConfig.getConnectionTimeoutMillis())
                .blockingSocketTimeoutMillis(clientConfig.getBlockingSocketTimeoutMillis())
                .user(clientConfig.getUser())
                .password(clientConfig.getPassword())
                .database(clientConfig.getDatabase())
                .clientName(clientConfig.getClientName())
                .ssl(clientConfig.isSsl())
                .sslSocketFactory(clientConfig.getSslSocketFactory())
                .sslParameters(clientConfig.getSslParameters())
                .hostnameVerifier(clientConfig.getHostnameVerifier())
                .sslOptions(clientConfig.getSslOptions());

        // Override with URI values
        if (JedisURIHelper.isRedisSSLScheme(uri)) {
            builder.ssl(true);
        }

        if (uri.getUserInfo() != null) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            if (userInfo.length == 2) {
                builder.user(userInfo[0]).password(userInfo[1]);
            } else {
                builder.password(userInfo[0]);
            }
        }

        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            try {
                int database = Integer.parseInt(path.substring(1));
                builder.database(database);
            } catch (NumberFormatException e) {
                // Ignore invalid database number
            }
        }

        return builder.build();
    }

    @Override
    protected void returnResourceObject(Jedis resource) {
        if (resource != null) {
            resource.resetForReuse();
            super.returnResourceObject(resource);
        }
    }
}
