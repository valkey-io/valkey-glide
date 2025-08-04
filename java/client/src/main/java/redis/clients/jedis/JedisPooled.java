/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.net.URI;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * JedisPooled compatibility wrapper for Valkey GLIDE client. This class provides a Jedis-like
 * pooled connection API while using Valkey GLIDE underneath. This is the recommended client for
 * standalone Redis/Valkey usage.
 *
 * <p>Note: Pool configurations are ignored since GLIDE handles connection pooling internally.
 */
public class JedisPooled extends UnifiedJedis {

    public JedisPooled() {
        this("localhost", 6379);
    }

    /**
     * WARNING: This constructor only accepts a uri string as {@code url}. To use a host string,
     * {@link #JedisPooled(java.lang.String, int)} can be used.
     *
     * @param url the connection URL
     */
    public JedisPooled(final String url) {
        super(url);
    }

    /**
     * WARNING: This constructor only accepts a uri string as {@code url}.
     *
     * @param url the connection URL
     * @param sslSocketFactory SSL socket factory
     * @param sslParameters SSL parameters
     * @param hostnameVerifier hostname verifier
     */
    public JedisPooled(
            final String url,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(URI.create(url), sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public JedisPooled(final String host, final int port) {
        this(new HostAndPort(host, port));
    }

    public JedisPooled(final HostAndPort hostAndPort) {
        super(hostAndPort);
    }

    public JedisPooled(final String host, final int port, final boolean ssl) {
        this(new HostAndPort(host, port), DefaultJedisClientConfig.builder().ssl(ssl).build());
    }

    public JedisPooled(
            final String host,
            final int port,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                new HostAndPort(host, port),
                DefaultJedisClientConfig.builder()
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    public JedisPooled(final String host, final int port, final String user, final String password) {
        this(
                new HostAndPort(host, port),
                DefaultJedisClientConfig.builder().user(user).password(password).build());
    }

    public JedisPooled(final HostAndPort hostAndPort, final JedisClientConfig clientConfig) {
        super(hostAndPort, clientConfig);
    }

    // Experimental constructors (cache support - simplified for compatibility)
    //    public JedisPooled(
    //            final HostAndPort hostAndPort, final JedisClientConfig clientConfig, Object
    // cacheConfig) {
    //        this(hostAndPort, clientConfig); // Cache not supported in GLIDE compatibility layer
    //    }
    //
    //    public JedisPooled(
    //            final HostAndPort hostAndPort, final JedisClientConfig clientConfig, Object
    // clientSideCache) {
    //        super(hostAndPort, clientConfig, clientSideCache);
    //    }

    // Pool-related constructors (simplified for GLIDE compatibility)
    // Note: In original Jedis, these use Connection type, but we use Object for simplicity
    public JedisPooled(PooledObjectFactory<Object> factory) {
        this(); // Use default connection since GLIDE handles pooling internally
    }

    public JedisPooled(final GenericObjectPoolConfig<Object> poolConfig) {
        this(poolConfig, "localhost", 6379);
    }

    /**
     * WARNING: This constructor only accepts a uri string as {@code url}.
     *
     * @param poolConfig pool configuration (ignored in GLIDE compatibility)
     * @param url the connection URL
     */
    public JedisPooled(final GenericObjectPoolConfig<Object> poolConfig, final String url) {
        this(poolConfig, URI.create(url));
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig, final String host, final int port) {
        this(poolConfig, host, port, 2000); // Default timeout
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            final int port,
            final boolean ssl) {
        this(poolConfig, host, port, 2000, ssl);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            final int port,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(poolConfig, host, port, 2000, ssl, sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            final int port,
            final String user,
            final String password) {
        this(poolConfig, host, port, 2000, user, password, 0);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            final int port,
            final int timeout) {
        this(poolConfig, host, port, timeout, (String) null);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            final int port,
            final int timeout,
            final boolean ssl) {
        this(poolConfig, host, port, timeout, null, ssl);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            final int port,
            final int timeout,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                host,
                port,
                timeout,
                null,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password) {
        this(poolConfig, host, port, timeout, password, 0);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final boolean ssl) {
        this(poolConfig, host, port, timeout, password, 0, ssl);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                host,
                port,
                timeout,
                password,
                0,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password) {
        this(poolConfig, host, port, timeout, user, password, 0);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password,
            final boolean ssl) {
        this(poolConfig, host, port, timeout, user, password, 0, ssl);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database) {
        this(poolConfig, host, port, timeout, password, database, null);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database,
            final boolean ssl) {
        this(poolConfig, host, port, timeout, password, database, null, ssl);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                poolConfig,
                host,
                port,
                timeout,
                password,
                database,
                null,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password,
            final int database) {
        this(poolConfig, host, port, timeout, user, password, database, null);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password,
            final int database,
            final boolean ssl) {
        this(poolConfig, host, port, timeout, user, password, database, null, ssl);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database,
            final String clientName) {
        this(poolConfig, host, port, timeout, timeout, password, database, clientName);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl) {
        this(poolConfig, host, port, timeout, timeout, password, database, clientName, ssl);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
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
                timeout,
                timeout,
                password,
                database,
                clientName,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password,
            final int database,
            final String clientName) {
        this(poolConfig, host, port, timeout, timeout, user, password, database, clientName);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            int timeout,
            final String user,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl) {
        this(poolConfig, host, port, timeout, timeout, user, password, database, clientName, ssl);
    }

    // Core constructor that all others delegate to
    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final String password,
            final int database,
            final String clientName) {
        this(
                poolConfig, host, port, connectionTimeout, soTimeout, null, password, database, clientName);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
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
                connectionTimeout,
                soTimeout,
                password,
                database,
                clientName,
                ssl,
                null,
                null,
                null);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
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
                connectionTimeout,
                soTimeout,
                null,
                password,
                database,
                clientName,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
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
                connectionTimeout,
                soTimeout,
                0,
                user,
                password,
                database,
                clientName);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
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
                connectionTimeout,
                soTimeout,
                user,
                password,
                database,
                clientName,
                ssl,
                null,
                null,
                null);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
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
                connectionTimeout,
                soTimeout,
                0,
                user,
                password,
                database,
                clientName,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final int infiniteSoTimeout,
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
                connectionTimeout,
                soTimeout,
                infiniteSoTimeout,
                null,
                password,
                database,
                clientName,
                ssl,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    // Ultimate constructor that all others delegate to
    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final int infiniteSoTimeout,
            final String user,
            final String password,
            final int database,
            final String clientName) {
        this(
                new HostAndPort(host, port),
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .blockingSocketTimeoutMillis(infiniteSoTimeout)
                        .user(user)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .build());
        // poolConfig is ignored since GLIDE handles pooling internally
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final String host,
            int port,
            final int connectionTimeout,
            final int soTimeout,
            final int infiniteSoTimeout,
            final String user,
            final String password,
            final int database,
            final String clientName,
            final boolean ssl,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                new HostAndPort(host, port),
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .blockingSocketTimeoutMillis(infiniteSoTimeout)
                        .user(user)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
        // poolConfig is ignored since GLIDE handles pooling internally
    }

    // URI-based constructors
    public JedisPooled(final URI uri) {
        super(uri);
    }

    public JedisPooled(
            final URI uri,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                new GenericObjectPoolConfig<Object>(),
                uri,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(final URI uri, final int timeout) {
        this(new GenericObjectPoolConfig<Object>(), uri, timeout);
    }

    public JedisPooled(
            final URI uri,
            final int timeout,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(
                new GenericObjectPoolConfig<Object>(),
                uri,
                timeout,
                sslSocketFactory,
                sslParameters,
                hostnameVerifier);
    }

    public JedisPooled(final GenericObjectPoolConfig<Object> poolConfig, final URI uri) {
        this(poolConfig, uri, 2000);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final URI uri,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(poolConfig, uri, 2000, sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig, final URI uri, final int timeout) {
        this(poolConfig, uri, timeout, timeout);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final URI uri,
            final int timeout,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        this(poolConfig, uri, timeout, timeout, sslSocketFactory, sslParameters, hostnameVerifier);
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final URI uri,
            final int connectionTimeout,
            final int soTimeout) {
        super(
                uri,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .build());
        // poolConfig is ignored since GLIDE handles pooling internally
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final URI uri,
            final int connectionTimeout,
            final int soTimeout,
            final SSLSocketFactory sslSocketFactory,
            final SSLParameters sslParameters,
            final HostnameVerifier hostnameVerifier) {
        super(
                uri,
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(connectionTimeout)
                        .socketTimeoutMillis(soTimeout)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
        // poolConfig is ignored since GLIDE handles pooling internally
    }

    // Additional constructors for compatibility
    public JedisPooled(
            final HostAndPort hostAndPort, final GenericObjectPoolConfig<Object> poolConfig) {
        this(hostAndPort, DefaultJedisClientConfig.builder().build());
        // poolConfig is ignored since GLIDE handles pooling internally
    }

    public JedisPooled(
            final GenericObjectPoolConfig<Object> poolConfig,
            final HostAndPort hostAndPort,
            final JedisClientConfig clientConfig) {
        this(hostAndPort, clientConfig);
        // poolConfig is ignored since GLIDE handles pooling internally
    }

    public JedisPooled(
            final HostAndPort hostAndPort,
            final JedisClientConfig clientConfig,
            final GenericObjectPoolConfig<Object> poolConfig) {
        this(hostAndPort, clientConfig);
        // poolConfig is ignored since GLIDE handles pooling internally
    }

    //    public JedisPooled(
    //            final HostAndPort hostAndPort,
    //            final JedisClientConfig clientConfig,
    //            Object cacheConfig,
    //            final GenericObjectPoolConfig<Object> poolConfig) {
    //        this(hostAndPort, clientConfig, cacheConfig);
    //        // poolConfig is ignored since GLIDE handles pooling internally
    //    }
    //
    //    public JedisPooled(
    //            final HostAndPort hostAndPort,
    //            final JedisClientConfig clientConfig,
    //            Object clientSideCache,
    //            final GenericObjectPoolConfig<Object> poolConfig) {
    //        this(hostAndPort, clientConfig, clientSideCache);
    //        // poolConfig is ignored since GLIDE handles pooling internally
    //    }

    // Factory-based constructors (simplified for GLIDE compatibility)
    //    public JedisPooled(
    //            final GenericObjectPoolConfig<Object> poolConfig, PooledObjectFactory<Object>
    // factory) {
    //        this(); // Use default connection since GLIDE handles pooling internally
    //    }
    //
    //    public JedisPooled(
    //            GenericObjectPoolConfig<Object> poolConfig, PooledObjectFactory<Object> factory) {
    //        this(); // Use default connection since GLIDE handles pooling internally
    //    }

    public JedisPooled(
            PooledObjectFactory<Object> factory, GenericObjectPoolConfig<Object> poolConfig) {
        this(); // Use default connection since GLIDE handles pooling internally
    }

    // Provider-based constructor (simplified for GLIDE compatibility)
    public JedisPooled(Object provider) {
        this(); // Use default connection since GLIDE handles pooling internally
    }
}
