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

    /** Creates a JedisPool with default configuration connecting to localhost:6379. */
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

    /**
     * Creates a JedisPool connecting to the specified host and port.
     *
     * @param host the Redis server host
     * @param port the Redis server port
     */
    public JedisPool(final String host, final int port) {
        this(new GenericObjectPoolConfig<Jedis>(), host, port);
    }

    /**
     * Creates a JedisPool connecting to the specified host and port with SSL configuration.
     *
     * @param host the Redis server host
     * @param port the Redis server port
     * @param ssl whether to use SSL connection
     */
    public JedisPool(final String host, final int port, final boolean ssl) {
        this(new GenericObjectPoolConfig<Jedis>(), host, port, ssl);
    }

    /**
     * Creates a JedisPool connecting to the specified host and port with SSL configuration and custom
     * SSL parameters.
     *
     * @param host the Redis server host
     * @param port the Redis server port
     * @param ssl whether to use SSL connection
     * @param sslSocketFactory SSL socket factory for creating SSL connections
     * @param sslParameters SSL parameters for the connection
     * @param hostnameVerifier hostname verifier for SSL connections
     */
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

    /**
     * Creates a JedisPool connecting to the specified host and port with user authentication.
     *
     * @param host the Redis server host
     * @param port the Redis server port
     * @param user the username for authentication
     * @param password the password for authentication
     */
    public JedisPool(final String host, int port, String user, final String password) {
        this(new GenericObjectPoolConfig<Jedis>(), host, port, user, password);
    }

    /**
     * Creates a JedisPool connecting to the specified host and port with timeout configuration.
     *
     * @param host the Redis server host
     * @param port the Redis server port
     * @param timeout the connection and socket timeout in milliseconds
     */
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

    /**
     * Creates a JedisPool connecting to the specified host and port with password authentication.
     *
     * @param host the Redis server host
     * @param port the Redis server port
     * @param password the password for authentication
     */
    public JedisPool(final String host, final int port, final String password) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                host,
                port,
                DefaultJedisClientConfig.builder().password(password).build());
    }

    /**
     * Creates a JedisPool connecting to the specified host and port with timeout and password
     * authentication.
     *
     * @param host the Redis server host
     * @param port the Redis server port
     * @param timeout the connection and socket timeout in milliseconds
     * @param password the password for authentication
     */
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

    /**
     * Creates a JedisPool with the specified host and port configuration and client configuration.
     *
     * @param hostAndPort the host and port configuration
     * @param clientConfig the client configuration
     */
    public JedisPool(final HostAndPort hostAndPort, final JedisClientConfig clientConfig) {
        this(
                new GenericObjectPoolConfig<Jedis>(),
                hostAndPort.getHost(),
                hostAndPort.getPort(),
                clientConfig);
    }

    /**
     * Creates a JedisPool with a custom pooled object factory.
     *
     * @param factory the pooled object factory for creating Jedis instances
     */
    public JedisPool(PooledObjectFactory<Jedis> factory) {
        this(new GenericObjectPoolConfig<Jedis>(), factory);
    }

    /**
     * Creates a JedisPool with custom pool configuration connecting to localhost:6379.
     *
     * @param poolConfig the pool configuration
     */
    public JedisPool(final GenericObjectPoolConfig<Jedis> poolConfig) {
        this(poolConfig, Protocol.DEFAULT_HOST, Protocol.DEFAULT_PORT);
    }

    /**
     * Creates a JedisPool connecting to the Redis server specified by the URI.
     *
     * @param uri the Redis server URI
     */
    public JedisPool(final URI uri) {
        this(new GenericObjectPoolConfig<Jedis>(), uri);
    }

    /**
     * Creates a JedisPool connecting to the Redis server specified by the URI with SSL configuration.
     *
     * @param uri the Redis server URI
     * @param sslSocketFactory SSL socket factory for creating SSL connections
     * @param sslParameters SSL parameters for the connection
     * @param hostnameVerifier hostname verifier for SSL connections
     */
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

    /**
     * Creates a JedisPool connecting to the Redis server specified by the URI with timeout
     * configuration.
     *
     * @param uri the Redis server URI
     * @param timeout the connection and socket timeout in milliseconds
     */
    public JedisPool(final URI uri, final int timeout) {
        this(new GenericObjectPoolConfig<Jedis>(), uri, timeout);
    }

    /**
     * Creates a JedisPool connecting to the Redis server specified by the URI with timeout and SSL
     * configuration.
     *
     * @param uri the Redis server URI
     * @param timeout the connection and socket timeout in milliseconds
     * @param sslSocketFactory SSL socket factory for creating SSL connections
     * @param sslParameters SSL parameters for the connection
     * @param hostnameVerifier hostname verifier for SSL connections
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the Redis server specified by
     * the URL. WARNING: This constructor only accepts a uri string as {@code url}. {@link
     * JedisURIHelper#isValid(java.net.URI)} can be used before this.
     *
     * <p>To use a host string, {@link
     * #JedisPool(org.apache.commons.pool2.impl.GenericObjectPoolConfig, java.lang.String, int)} can
     * be used with {@link Protocol#DEFAULT_PORT}.
     *
     * @param poolConfig the pool configuration
     * @param url the Redis server URL
     */
    public JedisPool(final GenericObjectPoolConfig<Jedis> poolConfig, final String url) {
        this(poolConfig, URI.create(url));
    }

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     */
    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig, final String host, final int port) {
        this(poolConfig, host, port, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with SSL.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param ssl whether to use SSL connection
     */
    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final String host,
            final int port,
            final boolean ssl) {
        this(poolConfig, host, port, DefaultJedisClientConfig.builder().ssl(ssl).build());
    }

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with SSL and custom SSL parameters.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param ssl whether to use SSL connection
     * @param sslSocketFactory SSL socket factory for creating SSL connections
     * @param sslParameters SSL parameters for the connection
     * @param hostnameVerifier hostname verifier for SSL connections
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with timeout.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param timeout the connection and socket timeout in milliseconds
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with timeout and password.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param timeout the connection and socket timeout in milliseconds
     * @param password the password for authentication
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with timeout, password, and database.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param timeout the connection and socket timeout in milliseconds
     * @param password the password for authentication
     * @param database the database number to select
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with timeout, password, database, and client name.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param timeout the connection and socket timeout in milliseconds
     * @param password the password for authentication
     * @param database the database number to select
     * @param clientName the client name to set for connections
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with separate connection and socket timeouts, user authentication, database, client name, and
     * SSL.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param connectionTimeout the connection timeout in milliseconds
     * @param soTimeout the socket timeout in milliseconds
     * @param user the username for authentication
     * @param password the password for authentication
     * @param database the database number to select
     * @param clientName the client name to set for connections
     * @param ssl whether to use SSL connection
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with user authentication.
     *
     * @param poolConfig the pool configuration
     * @param host the Redis server host
     * @param port the Redis server port
     * @param user the username for authentication
     * @param password the password for authentication
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the specified host and port
     * with client configuration. This is the main constructor that most other constructors delegate
     * to.
     *
     * @param poolConfig the pool configuration for managing the connection pool
     * @param host the Redis server host
     * @param port the Redis server port
     * @param clientConfig the client configuration including timeouts, authentication, SSL settings,
     *     etc.
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the Redis server specified by
     * the URI.
     *
     * @param poolConfig the pool configuration
     * @param uri the Redis server URI
     */
    public JedisPool(final GenericObjectPoolConfig<Jedis> poolConfig, final URI uri) {
        this(poolConfig, uri, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Creates a JedisPool with custom pool configuration connecting to the Redis server specified by
     * the URI with SSL configuration.
     *
     * @param poolConfig the pool configuration
     * @param uri the Redis server URI
     * @param sslSocketFactory SSL socket factory for creating SSL connections
     * @param sslParameters SSL parameters for the connection
     * @param hostnameVerifier hostname verifier for SSL connections
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the Redis server specified by
     * the URI with timeout.
     *
     * @param poolConfig the pool configuration
     * @param uri the Redis server URI
     * @param timeout the connection and socket timeout in milliseconds
     */
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

    /**
     * Creates a JedisPool with custom pool configuration connecting to the Redis server specified by
     * the URI with timeout and SSL configuration.
     *
     * @param poolConfig the pool configuration
     * @param uri the Redis server URI
     * @param timeout the connection and socket timeout in milliseconds
     * @param sslSocketFactory SSL socket factory for creating SSL connections
     * @param sslParameters SSL parameters for the connection
     * @param hostnameVerifier hostname verifier for SSL connections
     */
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

    /**
     * Private constructor for creating a JedisPool with URI and client configuration. This
     * constructor handles URI parsing and merges URI parameters with client configuration.
     *
     * @param poolConfig the pool configuration
     * @param uri the Redis server URI
     * @param clientConfig the client configuration
     */
    private JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig,
            final URI uri,
            final JedisClientConfig clientConfig) {
        // Create factory and set pool reference
        String host = uri.getHost();
        int port = uri.getPort() != -1 ? uri.getPort() : JedisURIHelper.getDefaultPort(uri);

        GlideJedisFactory factory =
                new GlideJedisFactory(host, port, mergeUriConfig(uri, clientConfig));
        initPool(poolConfig, factory);
        factory.setPool(this); // Set pool reference after initialization
    }

    /**
     * Creates a JedisPool with custom pool configuration and a custom pooled object factory.
     *
     * @param poolConfig the pool configuration
     * @param factory the pooled object factory for creating Jedis instances
     */
    public JedisPool(
            final GenericObjectPoolConfig<Jedis> poolConfig, PooledObjectFactory<Jedis> factory) {
        initPool(poolConfig, factory);

        // If it's a GlideJedisFactory, set the pool reference
        if (factory instanceof GlideJedisFactory) {
            ((GlideJedisFactory) factory).setPool(this);
        }
    }

    /**
     * Merge URI configuration with client configuration. URI parameters take precedence over client
     * configuration parameters.
     *
     * @param uri the Redis server URI containing connection parameters
     * @param clientConfig the base client configuration
     * @return merged client configuration with URI parameters applied
     */
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
