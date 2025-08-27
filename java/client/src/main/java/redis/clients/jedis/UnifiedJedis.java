/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.GlideString;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.util.JedisURIHelper;

/**
 * Compatibility wrapper for Valkey GLIDE client. This class provides the unified API that works
 * with both standalone and cluster servers, while using Valkey GLIDE underneath.
 */
public class UnifiedJedis implements Closeable {

    protected final BaseClient baseClient;
    protected final GlideClient glideClient;
    protected final GlideClusterClient glideClusterClient;
    protected final JedisClientConfig config;
    protected final String resourceId;
    protected final boolean isClusterMode;
    protected volatile boolean closed = false;

    // ========== STANDALONE CONSTRUCTORS ==========

    /** Default constructor - connects to localhost:6379 */
    public UnifiedJedis() {
        this(new HostAndPort("localhost", 6379));
    }

    /** Constructor with host and port */
    public UnifiedJedis(String host, int port) {
        this(new HostAndPort(host, port));
    }

    /** Constructor with HostAndPort */
    public UnifiedJedis(HostAndPort hostAndPort) {
        this(hostAndPort, DefaultJedisClientConfig.builder().build());
    }

    /** Constructor with URL string */
    public UnifiedJedis(String url) {
        this(URI.create(url));
    }

    /** Constructor with URI */
    public UnifiedJedis(URI uri) {
        this(
                extractHostAndPort(uri),
                buildConfigFromURI(uri, DefaultJedisClientConfig.builder().build()));
    }

    /** Constructor with URI and config */
    public UnifiedJedis(URI uri, JedisClientConfig config) {
        this(extractHostAndPort(uri), buildConfigFromURI(uri, config));
    }

    /** Constructor with HostAndPort and config */
    public UnifiedJedis(HostAndPort hostAndPort, JedisClientConfig clientConfig) {
        this.config = clientConfig;
        this.isClusterMode = false;
        this.glideClusterClient = null;

        // Map Jedis config to GLIDE config for standalone
        GlideClientConfiguration glideConfig =
                ConfigurationMapper.mapToGlideConfig(
                        hostAndPort.getHost(), hostAndPort.getPort(), clientConfig);

        try {
            this.glideClient = GlideClient.createClient(glideConfig).get();
            this.baseClient = this.glideClient; // Set BaseClient reference
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE client", e);
        }
    }

    /** Constructor with host, port and config */
    public UnifiedJedis(String host, int port, JedisClientConfig clientConfig) {
        this(new HostAndPort(host, port), clientConfig);
    }

    /** Constructor with host, port, timeout and password */
    public UnifiedJedis(String host, int port, int timeout, String password) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).password(password).build());
    }

    /** Constructor with host, port and timeout */
    public UnifiedJedis(String host, int port, int timeout) {
        this(host, port, DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).build());
    }

    /** Constructor with host, port, timeout, password and database */
    public UnifiedJedis(String host, int port, int timeout, String password, int database) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .build());
    }

    /** Constructor with host, port, timeout, password, database and clientName */
    public UnifiedJedis(
            String host, int port, int timeout, String password, int database, String clientName) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .password(password)
                        .database(database)
                        .clientName(clientName)
                        .build());
    }

    // ========== CLUSTER CONSTRUCTORS ==========

    /** Constructor for cluster with Set of nodes */
    public UnifiedJedis(Set<HostAndPort> jedisClusterNodes) {
        this(jedisClusterNodes, DefaultJedisClientConfig.builder().build());
    }

    /** Constructor for cluster with Set of nodes and config */
    public UnifiedJedis(Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig) {
        this.config = clientConfig;
        this.isClusterMode = true;
        this.glideClient = null;

        // Map Jedis config to GLIDE cluster config
        GlideClusterClientConfiguration glideConfig =
                ClusterConfigurationMapper.mapToGlideClusterConfig(jedisClusterNodes, clientConfig);

        try {
            this.glideClusterClient = GlideClusterClient.createClient(glideConfig).get();
            this.baseClient = this.glideClusterClient; // Set BaseClient reference
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE cluster client", e);
        }
    }

    /**
     * Constructor for cluster with Set of nodes, config and max attempts Note: maxAttempts is for
     * Jedis compatibility but not used in GLIDE configuration
     */
    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig, int maxAttempts) {
        this(jedisClusterNodes, clientConfig);
    }

    /**
     * Constructor for cluster with Set of nodes, config, max attempts and max retry duration Note:
     * maxAttempts and maxTotalRetriesDuration are for Jedis compatibility but not used in GLIDE
     * configuration
     */
    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes,
            JedisClientConfig clientConfig,
            int maxAttempts,
            Duration maxTotalRetriesDuration) {
        this(jedisClusterNodes, clientConfig);
    }

    // ========== PROVIDER-BASED CONSTRUCTORS (for compatibility) ==========

    /** Constructor with ConnectionProvider (for compatibility) */
    public UnifiedJedis(ConnectionProvider provider) {
        // Extract connection info from provider and delegate to appropriate constructor
        if (provider instanceof ClusterConnectionProvider) {
            ClusterConnectionProvider clusterProvider = (ClusterConnectionProvider) provider;
            Set<HostAndPort> nodes = clusterProvider.getNodes();
            JedisClientConfig config = clusterProvider.getClientConfig();

            this.config = config;
            this.isClusterMode = true;
            this.glideClient = null;

            GlideClusterClientConfiguration glideConfig =
                    ClusterConfigurationMapper.mapToGlideClusterConfig(nodes, config);
            try {
                this.glideClusterClient = GlideClusterClient.createClient(glideConfig).get();
                this.baseClient = this.glideClusterClient; // Set BaseClient reference
                this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
            } catch (InterruptedException | ExecutionException e) {
                throw new JedisConnectionException("Failed to create GLIDE cluster client", e);
            }
        } else {
            // Assume standalone provider
            HostAndPort hostAndPort = provider.getConnection().getHostAndPort();
            JedisClientConfig config = provider.getClientConfig();

            this.config = config;
            this.isClusterMode = false;
            this.glideClusterClient = null;

            GlideClientConfiguration glideConfig =
                    ConfigurationMapper.mapToGlideConfig(
                            hostAndPort.getHost(), hostAndPort.getPort(), config);
            try {
                this.glideClient = GlideClient.createClient(glideConfig).get();
                this.baseClient = this.glideClient; // Set BaseClient reference
                this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
            } catch (InterruptedException | ExecutionException e) {
                throw new JedisConnectionException("Failed to create GLIDE client", e);
            }
        }
    }

    /** Constructor with ConnectionProvider and max attempts */
    public UnifiedJedis(
            ConnectionProvider provider, int maxAttempts, Duration maxTotalRetriesDuration) {
        this(provider); // Delegate to main provider constructor for now
    }

    // ========== PROTECTED CONSTRUCTORS ==========

    /** Protected constructor for internal use with standalone client */
    protected UnifiedJedis(GlideClient glideClient, JedisClientConfig jedisConfig) {
        this.glideClient = glideClient;
        this.glideClusterClient = null;
        this.baseClient = glideClient; // Set BaseClient reference
        this.config = jedisConfig;
        this.isClusterMode = false;
        this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
    }

    /** Protected constructor for internal use with cluster client */
    protected UnifiedJedis(GlideClusterClient glideClusterClient, JedisClientConfig jedisConfig) {
        this.glideClient = null;
        this.glideClusterClient = glideClusterClient;
        this.baseClient = glideClusterClient; // Set BaseClient reference
        this.config = jedisConfig;
        this.isClusterMode = true;
        this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
    }

    // ========== UTILITY METHODS ==========

    /** Extracts HostAndPort from URI */
    private static HostAndPort extractHostAndPort(URI uri) {
        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        int port = uri.getPort() != -1 ? uri.getPort() : JedisURIHelper.getDefaultPort(uri);
        return new HostAndPort(host, port);
    }

    /** Builds JedisClientConfig from URI */
    private static JedisClientConfig buildConfigFromURI(URI uri, JedisClientConfig baseConfig) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();

        // Copy existing config
        if (baseConfig != null) {
            builder
                    .socketTimeoutMillis(baseConfig.getSocketTimeoutMillis())
                    .connectionTimeoutMillis(baseConfig.getConnectionTimeoutMillis())
                    .blockingSocketTimeoutMillis(baseConfig.getBlockingSocketTimeoutMillis())
                    .user(baseConfig.getUser())
                    .password(baseConfig.getPassword())
                    .database(baseConfig.getDatabase())
                    .clientName(baseConfig.getClientName())
                    .ssl(baseConfig.isSsl());
        }

        // Override with URI parameters
        if (uri.getUserInfo() != null) {
            String[] userInfo = uri.getUserInfo().split(":");
            if (userInfo.length == 1) {
                builder.password(userInfo[0]);
            } else if (userInfo.length == 2) {
                builder.user(userInfo[0]).password(userInfo[1]);
            }
        }

        if ("rediss".equals(uri.getScheme())) {
            builder.ssl(true);
        }

        return builder.build();
    }

    /** Checks if the client is closed and throws exception if it is */
    protected void checkNotClosed() {
        if (closed) {
            throw new JedisException("UnifiedJedis has been closed");
        }
    }

    // ========== BASIC OPERATIONS ==========

    /** Set the string value of a key. */
    public String set(String key, String value) {
        checkNotClosed();
        try {
            return baseClient.set(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SET operation failed", e);
        }
    }

    /** Get the value of a key. */
    public String get(String key) {
        checkNotClosed();
        try {
            return baseClient.get(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GET operation failed", e);
        }
    }

    /** Delete one or more keys. */
    public Long del(String... keys) {
        checkNotClosed();
        try {
            return baseClient.del(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
    }

    /** Delete a key. */
    public Long del(String key) {
        return del(new String[] {key});
    }

    /** Delete keys (binary). */
    public Long del(byte[]... keys) {
        checkNotClosed();
        try {
            GlideString[] glideKeys = new GlideString[keys.length];
            for (int i = 0; i < keys.length; i++) {
                glideKeys[i] = GlideString.of(keys[i]);
            }
            return baseClient.del(glideKeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
    }

    /** Delete a key (binary). */
    public Long del(byte[] key) {
        return del(new byte[][] {key});
    }

    /** Test if the connection is alive. */
    public String ping() {
        checkNotClosed();
        try {
            if (isClusterMode) {
                return glideClusterClient.ping().get();
            } else {
                return glideClient.ping().get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PING operation failed", e);
        }
    }

    /** Test if the connection is alive with a message. */
    public String ping(String message) {
        checkNotClosed();
        try {
            if (isClusterMode) {
                return glideClusterClient.ping(message).get();
            } else {
                return glideClient.ping(message).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PING operation failed", e);
        }
    }

    /** Check if the connection is closed. */
    public boolean isClosed() {
        return closed;
    }

    /** Close the connection. */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                if (baseClient != null) {
                    baseClient.close();
                }
            } catch (ExecutionException e) {
                // Log the error but don't throw - close should be idempotent
                System.err.println("Error closing GLIDE client: " + e.getMessage());
            } finally {
                if (resourceId != null) {
                    ResourceLifecycleManager.getInstance().unregisterResource(resourceId);
                }
            }
        }
    }

    // ========== ADDITIONAL COMMON OPERATIONS (using baseClient) ==========

    /** Check if a key exists. */
    public Boolean exists(String key) {
        checkNotClosed();
        try {
            return baseClient.exists(new String[] {key}).get() > 0;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXISTS operation failed", e);
        }
    }

    /** Set a key's time to live in seconds. */
    public Boolean expire(String key, long seconds) {
        checkNotClosed();
        try {
            return baseClient.expire(key, seconds).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIRE operation failed", e);
        }
    }

    /** Get the remaining time to live of a key. */
    public Long ttl(String key) {
        checkNotClosed();
        try {
            return baseClient.ttl(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TTL operation failed", e);
        }
    }

    // ========== STANDALONE-SPECIFIC OPERATIONS ==========

    /** Select the database (standalone only). */
    public String select(int database) {
        checkNotClosed();
        if (isClusterMode) {
            throw new JedisException("SELECT is not supported in cluster mode");
        }
        try {
            return glideClient.select(database).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SELECT operation failed", e);
        }
    }
}
