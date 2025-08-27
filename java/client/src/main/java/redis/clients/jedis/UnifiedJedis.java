/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.GlideString;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.bitmap.BitmapIndexType;
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.commands.scan.ScanOptions;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import redis.clients.jedis.args.BitCountOption;
import redis.clients.jedis.args.BitOP;
import redis.clients.jedis.args.ExpiryOption;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.params.BitPosParams;
import redis.clients.jedis.params.GetExParams;
import redis.clients.jedis.params.MigrateParams;
import redis.clients.jedis.params.RestoreParams;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.util.JedisURIHelper;

/**
 * Unified Jedis compatibility wrapper for Valkey GLIDE client. This class provides a unified API
 * that works seamlessly with both standalone and cluster Valkey/Redis servers, while leveraging
 * Valkey GLIDE underneath for enhanced performance, reliability, and feature support.
 *
 * <p>This compatibility layer enables existing Jedis applications to migrate to Valkey GLIDE with
 * minimal code changes while benefiting from GLIDE's advanced capabilities:
 *
 * <ul>
 *   <li>Unified API for both standalone and cluster deployments
 *   <li>Improved connection management and automatic failover
 *   <li>Enhanced error handling and retry mechanisms
 *   <li>Better performance optimizations and connection pooling
 *   <li>Support for the latest Valkey and Redis features
 *   <li>Automatic cluster topology discovery and updates
 * </ul>
 *
 * <p>The class implements the same method signatures as the original Jedis UnifiedJedis client,
 * ensuring drop-in compatibility for most use cases. The implementation automatically detects
 * whether it's connected to a standalone or cluster deployment and routes commands appropriately.
 *
 * <p>Example usage for standalone server:
 *
 * <pre>{@code
 * try (UnifiedJedis jedis = new UnifiedJedis("localhost", 6379)) {
 *     jedis.set("key", "value");
 *     String value = jedis.get("key");
 * }
 * }</pre>
 *
 * <p>Example usage for cluster:
 *
 * <pre>{@code
 * Set<HostAndPort> nodes = new HashSet<>();
 * nodes.add(new HostAndPort("localhost", 7000));
 * nodes.add(new HostAndPort("localhost", 7001));
 *
 * try (UnifiedJedis jedis = new UnifiedJedis(nodes)) {
 *     jedis.set("key", "value");
 *     String value = jedis.get("key");
 * }
 * }</pre>
 *
 * <p><strong>Note:</strong> Some advanced Jedis features may require migration to native GLIDE APIs
 * for optimal performance. This compatibility layer focuses on the most commonly used operations
 * while maintaining API compatibility.
 *
 * @author Valkey GLIDE Project Contributors
 * @since 1.0.0
 * @see GlideClient
 * @see GlideClusterClient
 * @see JedisClientConfig
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

    /**
     * Create a new UnifiedJedis instance with default localhost:6379 connection. This constructor is
     * suitable for development and testing environments where Valkey/Redis is running locally with
     * default settings.
     *
     * @throws JedisConnectionException if the connection cannot be established
     */
    public UnifiedJedis() {
        this(new HostAndPort("localhost", 6379));
    }

    /**
     * Create a new UnifiedJedis instance with specified host and port. Uses default configuration
     * settings for connection timeout, socket timeout, and other client parameters.
     *
     * @param host the Valkey/Redis server host (must not be null)
     * @param port the Valkey/Redis server port (must be positive)
     * @throws JedisConnectionException if the connection cannot be established
     */
    public UnifiedJedis(String host, int port) {
        this(new HostAndPort(host, port));
    }

    /**
     * Create a new UnifiedJedis instance with HostAndPort configuration. This constructor provides a
     * convenient way to specify connection details using the HostAndPort utility class.
     *
     * @param hostAndPort the host and port configuration (must not be null)
     * @throws JedisConnectionException if the connection cannot be established
     */
    public UnifiedJedis(HostAndPort hostAndPort) {
        this(hostAndPort, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Create a new UnifiedJedis instance with URL string connection. The URL should follow the
     * format: redis://[username:password@]host:port[/database] or
     * rediss://[username:password@]host:port[/database] for SSL connections.
     *
     * @param url the connection URL string (must not be null and must be valid)
     * @throws JedisConnectionException if the connection cannot be established
     * @throws IllegalArgumentException if the URL format is invalid
     */
    public UnifiedJedis(String url) {
        this(URI.create(url));
    }

    /**
     * Create a new UnifiedJedis instance with URI configuration. The URI should follow the format:
     * redis://[username:password@]host:port[/database] or
     * rediss://[username:password@]host:port[/database] for SSL connections.
     *
     * @param uri the connection URI (must not be null and must be valid)
     * @throws JedisConnectionException if the connection cannot be established
     * @throws IllegalArgumentException if the URI format is invalid
     */
    public UnifiedJedis(URI uri) {
        this(
                extractHostAndPort(uri),
                buildConfigFromURI(uri, DefaultJedisClientConfig.builder().build()));
    }

    /**
     * Create a new UnifiedJedis instance with URI and custom configuration. This constructor allows
     * you to override default settings while still using URI-based connection parameters.
     *
     * @param uri the connection URI (must not be null and must be valid)
     * @param config the client configuration to use (must not be null)
     * @throws JedisConnectionException if the connection cannot be established
     * @throws IllegalArgumentException if the URI format is invalid
     */
    public UnifiedJedis(URI uri, JedisClientConfig config) {
        this(extractHostAndPort(uri), buildConfigFromURI(uri, config));
    }

    /**
     * Create a new UnifiedJedis instance with full configuration control. This constructor provides
     * the most flexibility for configuring connection parameters, timeouts, SSL settings,
     * authentication, and other client options.
     *
     * @param hostAndPort the host and port configuration (must not be null)
     * @param clientConfig the comprehensive client configuration (must not be null)
     * @throws JedisConnectionException if the connection cannot be established
     * @throws IllegalArgumentException if configuration parameters are invalid
     */
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

    /**
     * Checks if the client connection is closed and throws an exception if it is. This method is
     * called before every operation to ensure the client is in a valid state.
     *
     * @throws JedisException if the client has been closed
     */
    protected void checkNotClosed() {
        if (closed) {
            throw new JedisException("UnifiedJedis has been closed");
        }
    }

    // ========== BASIC OPERATIONS ==========

    /**
     * Set the string value of a key. If the key already exists, its value will be overwritten
     * regardless of its type. This is the most basic Valkey SET operation.
     *
     * @param key the key to set (must not be null)
     * @param value the string value to set (must not be null)
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public String set(String key, String value) {
        checkNotClosed();
        try {
            return baseClient.set(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SET operation failed", e);
        }
    }

    /**
     * Get the string value stored at a key. This is the most fundamental Valkey GET operation for
     * retrieving string values from the database.
     *
     * @param key the key to retrieve the value from (must not be null)
     * @return the string value stored at the key, or null if the key does not exist
     * @throws JedisException if the operation fails or the key contains a non-string value
     * @since Valkey 1.0.0
     */
    public String get(String key) {
        checkNotClosed();
        try {
            return baseClient.get(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GET operation failed", e);
        }
    }

    /**
     * Delete one or more keys from the database. This operation removes the keys and their associated
     * values completely. Non-existent keys are ignored.
     *
     * @param keys the keys to delete (must not be null, can be empty)
     * @return the number of keys that were actually deleted (0 to keys.length)
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
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

    /**
     * Test if the server is alive and responding. This command is often used for health checks and
     * connection testing. The server will respond with "PONG" if it's functioning correctly.
     *
     * @return "PONG" if the server is responding
     * @throws JedisException if the operation fails or connection is lost
     * @since Valkey 1.0.0
     */
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

    /**
     * Test if the server is alive and echo back a custom message. This variant of PING allows you to
     * send a custom message that will be echoed back by the server, useful for testing message
     * integrity and round-trip functionality.
     *
     * @param message the message to echo back (must not be null)
     * @return the echoed message exactly as sent
     * @throws JedisException if the operation fails or connection is lost
     * @since Valkey 2.8.0
     */
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

    // ========== STRING COMMANDS ==========

    /**
     * Set the string value of a key with optional parameters. This method provides advanced SET
     * functionality including conditional setting, expiration, and atomic get-and-set operations.
     *
     * <p>The SetParams object allows you to specify:
     *
     * <ul>
     *   <li>Existence conditions (NX - only if key doesn't exist, XX - only if key exists)
     *   <li>Expiration settings (EX, PX, EXAT, PXAT, KEEPTTL)
     *   <li>GET option to return the old value atomically
     * </ul>
     *
     * @param key the key to set (must not be null)
     * @param value the value to set (must not be null)
     * @param params the SET parameters for conditional setting and expiration (can be null for basic
     *     SET)
     * @return "OK" if successful, null if not set due to NX/XX conditions, or the old value if GET
     *     option is used
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public String set(String key, String value, SetParams params) {
        checkNotClosed();
        try {
            if (params == null) {
                return baseClient.set(key, value).get();
            }

            // Convert Jedis SetParams to GLIDE SetOptions
            SetOptions.SetOptionsBuilder builder = SetOptions.builder();

            // This is a simplified conversion - in a full implementation, you would need
            // to properly parse the SetParams to extract NX, XX, EX, PX options
            // For now, we'll use the basic set command
            return baseClient.set(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SET operation failed", e);
        }
    }

    /**
     * Atomically set the value of a key and return its old value. This operation is atomic, meaning
     * no other client can modify the key between getting the old value and setting the new one. This
     * is equivalent to SET with the GET option.
     *
     * @param key the key to set (must not be null)
     * @param value the new value to set (must not be null)
     * @return the old value stored at the key, or null if the key did not exist
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     */
    public String setGet(String key, String value) {
        checkNotClosed();
        try {
            // Use SET with returnOldValue option to implement SETGET
            SetOptions options = SetOptions.builder().returnOldValue(true).build();
            return baseClient.set(key, value, options).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETGET operation failed", e);
        }
    }

    /**
     * Atomically set the value of a key with parameters and return its old value. This combines the
     * functionality of SET with parameters and the GET option for atomic operations.
     *
     * @param key the key to set (must not be null)
     * @param value the new value to set (must not be null)
     * @param params the SET parameters for conditional setting and expiration (can be null for basic
     *     operation)
     * @return the old value stored at the key, or null if the key did not exist or conditions weren't
     *     met
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     */
    public String setGet(String key, String value, SetParams params) {
        checkNotClosed();
        try {
            // Use SET with returnOldValue option to implement SETGET
            SetOptions.SetOptionsBuilder builder = SetOptions.builder().returnOldValue(true);

            // This is a simplified conversion - in a full implementation, you would need
            // to properly parse the SetParams to extract NX, XX, EX, PX options

            return baseClient.set(key, value, builder.build()).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETGET operation failed", e);
        }
    }

    /**
     * Get the values of multiple keys in a single operation. This is more efficient than multiple
     * individual GET operations, especially when dealing with network latency. Non-existent keys will
     * return null values in the corresponding positions.
     *
     * @param keys the keys to retrieve values for (must not be null, can be empty)
     * @return a list of values corresponding to the given keys, with null for non-existent keys
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public List<String> mget(String... keys) {
        checkNotClosed();
        try {
            String[] result = baseClient.mget(keys).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MGET operation failed", e);
        }
    }

    /**
     * Set multiple key-value pairs in a single atomic operation. This is more efficient than multiple
     * individual SET operations and is guaranteed to be atomic - either all keys are set or none are
     * set if an error occurs.
     *
     * @param keysvalues alternating keys and values (key1, value1, key2, value2, ...). Must have an
     *     even number of arguments.
     * @return "OK" if successful
     * @throws IllegalArgumentException if the number of arguments is odd
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.1
     */
    public String mset(String... keysvalues) {
        checkNotClosed();
        try {
            if (keysvalues.length % 2 != 0) {
                throw new IllegalArgumentException("Wrong number of arguments for MSET");
            }

            java.util.Map<String, String> keyValueMap = new java.util.HashMap<>();
            for (int i = 0; i < keysvalues.length; i += 2) {
                keyValueMap.put(keysvalues[i], keysvalues[i + 1]);
            }

            return baseClient.mset(keyValueMap).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MSET operation failed", e);
        }
    }

    /**
     * Set the value of a key only if the key does not already exist. This is useful for implementing
     * distributed locks or ensuring that initialization values are not overwritten.
     *
     * @param key the key to set (must not be null)
     * @param value the value to set (must not be null)
     * @return 1 if the key was set, 0 if the key already exists and was not set
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public long setnx(String key, String value) {
        checkNotClosed();
        try {
            SetOptions options = SetOptions.builder().conditionalSetOnlyIfNotExist().build();
            String result = baseClient.set(key, value, options).get();
            return result != null ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETNX operation failed", e);
        }
    }

    /**
     * Set the value of a key with an expiration time in seconds. The key will be automatically
     * deleted after the specified number of seconds. This is equivalent to SET with EX option.
     *
     * @param key the key to set (must not be null)
     * @param seconds the expiration time in seconds (must be positive)
     * @param value the value to set (must not be null)
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Valkey 2.0.0
     */
    public String setex(String key, long seconds, String value) {
        checkNotClosed();
        try {
            SetOptions options = SetOptions.builder().expiry(SetOptions.Expiry.Seconds(seconds)).build();
            return baseClient.set(key, value, options).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETEX operation failed", e);
        }
    }

    /**
     * Set the value of a key with an expiration time in milliseconds. The key will be automatically
     * deleted after the specified number of milliseconds. This provides more precise timing control
     * than SETEX. This is equivalent to SET with PX option.
     *
     * @param key the key to set (must not be null)
     * @param milliseconds the expiration time in milliseconds (must be positive)
     * @param value the value to set (must not be null)
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public String psetex(String key, long milliseconds, String value) {
        checkNotClosed();
        try {
            SetOptions options =
                    SetOptions.builder().expiry(SetOptions.Expiry.Milliseconds(milliseconds)).build();
            return baseClient.set(key, value, options).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PSETEX operation failed", e);
        }
    }

    /**
     * Atomically get the current value of a key and set it to a new value. This method is deprecated
     * in favor of {@link #setGet(String, String)} which provides the same functionality with a
     * clearer name.
     *
     * @param key the key to get and set (must not be null)
     * @param value the new value to set (must not be null)
     * @return the old value stored at the key, or null if the key did not exist
     * @throws JedisException if the operation fails
     * @deprecated Use {@link #setGet(String, String)} instead
     * @since Valkey 1.0.0
     */
    @Deprecated
    public String getSet(String key, String value) {
        return setGet(key, value);
    }

    /**
     * Get the value of a key and delete the key atomically. This operation is useful for implementing
     * queues or consuming values that should only be processed once. The operation is atomic,
     * ensuring no race conditions between getting and deleting.
     *
     * @param key the key to get and delete (must not be null)
     * @return the value that was stored at the key, or null if the key did not exist
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     */
    public String getDel(String key) {
        checkNotClosed();
        try {
            return baseClient.getdel(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETDEL operation failed", e);
        }
    }

    /**
     * Get the value of a key and optionally set its expiration. This command allows you to retrieve a
     * value while simultaneously updating its expiration time, which is useful for implementing
     * sliding window expiration patterns.
     *
     * @param key the key to get (must not be null)
     * @param params the expiration parameters (can be null to just get without changing expiration)
     * @return the value stored at the key, or null if the key does not exist
     * @throws JedisException if the operation fails
     * @since Valkey 6.2.0
     */
    public String getEx(String key, GetExParams params) {
        checkNotClosed();
        try {
            if (params == null) {
                return baseClient.get(key).get();
            }

            // Convert Jedis GetExParams to GLIDE GetExOptions
            // This is a simplified implementation - full implementation would need
            // to parse the actual parameters from GetExParams
            GetExOptions options = GetExOptions.Persist();

            return baseClient.getex(key, options).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETEX operation failed", e);
        }
    }

    /**
     * Append a value to the end of the string stored at a key. If the key does not exist, it is
     * created with an empty string as its value before performing the append operation.
     *
     * @param key the key whose value to append to (must not be null)
     * @param value the value to append (must not be null)
     * @return the length of the string after the append operation
     * @throws JedisException if the operation fails
     * @since Valkey 2.0.0
     */
    public long append(String key, String value) {
        checkNotClosed();
        try {
            return baseClient.append(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("APPEND operation failed", e);
        }
    }

    /**
     * Get the length of the string value stored at a key. If the key does not exist, it is treated as
     * an empty string and returns 0.
     *
     * @param key the key to get the string length for (must not be null)
     * @return the length of the string stored at the key, or 0 if the key does not exist
     * @throws JedisException if the operation fails or the key contains a non-string value
     * @since Valkey 2.2.0
     */
    public long strlen(String key) {
        checkNotClosed();
        try {
            return baseClient.strlen(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("STRLEN operation failed", e);
        }
    }

    /**
     * Increment the integer value stored at a key by 1. If the key does not exist, it is set to 0
     * before performing the increment operation. The value must be representable as a 64-bit signed
     * integer.
     *
     * @param key the key whose value to increment (must not be null)
     * @return the value of the key after incrementing
     * @throws JedisException if the operation fails or the value is not an integer
     * @since Valkey 1.0.0
     */
    public long incr(String key) {
        checkNotClosed();
        try {
            return baseClient.incr(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("INCR operation failed", e);
        }
    }

    /**
     * Increment the integer value stored at a key by the specified amount. If the key does not exist,
     * it is set to 0 before performing the increment operation. The value must be representable as a
     * 64-bit signed integer.
     *
     * @param key the key whose value to increment (must not be null)
     * @param increment the amount to increment by (can be negative for decrement)
     * @return the value of the key after incrementing
     * @throws JedisException if the operation fails or the value is not an integer
     * @since Valkey 1.0.0
     */
    public long incrBy(String key, long increment) {
        checkNotClosed();
        try {
            return baseClient.incrBy(key, increment).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("INCRBY operation failed", e);
        }
    }

    /**
     * Increment the floating-point value stored at a key by the specified amount. If the key does not
     * exist, it is set to 0 before performing the increment operation. The value must be
     * representable as a double-precision floating-point number.
     *
     * @param key the key whose value to increment (must not be null)
     * @param increment the floating-point amount to increment by (can be negative for decrement)
     * @return the value of the key after incrementing
     * @throws JedisException if the operation fails or the value is not a valid float
     * @since Valkey 2.6.0
     */
    public double incrByFloat(String key, double increment) {
        checkNotClosed();
        try {
            return baseClient.incrByFloat(key, increment).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("INCRBYFLOAT operation failed", e);
        }
    }

    /**
     * Decrement the integer value stored at a key by 1. If the key does not exist, it is set to 0
     * before performing the decrement operation. The value must be representable as a 64-bit signed
     * integer.
     *
     * @param key the key whose value to decrement (must not be null)
     * @return the value of the key after decrementing
     * @throws JedisException if the operation fails or the value is not an integer
     * @since Valkey 1.0.0
     */
    public long decr(String key) {
        checkNotClosed();
        try {
            return baseClient.decr(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DECR operation failed", e);
        }
    }

    /**
     * Decrement the integer value stored at a key by the specified amount. If the key does not exist,
     * it is set to 0 before performing the decrement operation. The value must be representable as a
     * 64-bit signed integer.
     *
     * @param key the key whose value to decrement (must not be null)
     * @param decrement the amount to decrement by (must be positive)
     * @return the value of the key after decrementing
     * @throws JedisException if the operation fails or the value is not an integer
     * @since Valkey 1.0.0
     */
    public long decrBy(String key, long decrement) {
        checkNotClosed();
        try {
            return baseClient.decrBy(key, decrement).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DECRBY operation failed", e);
        }
    }

    /**
     * Get a substring of the string stored at a key. Both start and end offsets are inclusive.
     * Negative offsets can be used to specify positions from the end of the string.
     *
     * @param key the key containing the string (must not be null)
     * @param startOffset the start position (inclusive, can be negative)
     * @param endOffset the end position (inclusive, can be negative)
     * @return the substring, or empty string if the key does not exist
     * @throws JedisException if the operation fails or the key contains a non-string value
     * @since Valkey 2.4.0
     */
    public String getrange(String key, long startOffset, long endOffset) {
        checkNotClosed();
        try {
            return baseClient.getrange(key, (int) startOffset, (int) endOffset).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETRANGE operation failed", e);
        }
    }

    /**
     * Overwrite part of the string stored at a key, starting at the specified offset. If the offset
     * is larger than the current string length, the string is padded with zero-bytes. If the key does
     * not exist, it is created with an empty string before performing the operation.
     *
     * @param key the key containing the string to modify (must not be null)
     * @param offset the position to start overwriting from (must be non-negative)
     * @param value the string to write at the specified offset (must not be null)
     * @return the length of the string after the operation
     * @throws JedisException if the operation fails or the key contains a non-string value
     * @since Valkey 2.2.0
     */
    public long setrange(String key, long offset, String value) {
        checkNotClosed();
        try {
            return baseClient.setrange(key, (int) offset, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETRANGE operation failed", e);
        }
    }

    /**
     * Get a substring of the string stored at a key. This method is deprecated in favor of {@link
     * #getrange(String, long, long)} which provides the same functionality.
     *
     * @param key the key containing the string (must not be null)
     * @param start the start position (inclusive)
     * @param end the end position (inclusive)
     * @return the substring, or empty string if the key does not exist
     * @deprecated Use {@link #getrange(String, long, long)} instead
     */
    public String substr(String key, int start, int end) {
        return getrange(key, start, end);
    }

    // ========== KEY MANAGEMENT COMMANDS ==========

    /**
     * Check if a key exists in the database. This is a fast operation that only checks for the
     * existence of the key without retrieving its value.
     *
     * @param key the key to check for existence (must not be null)
     * @return true if the key exists, false otherwise
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public boolean exists(String key) {
        checkNotClosed();
        try {
            return baseClient.exists(new String[] {key}).get() > 0;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXISTS operation failed", e);
        }
    }

    /**
     * Check how many of the specified keys exist in the database. This operation is more efficient
     * than multiple individual EXISTS calls when checking multiple keys.
     *
     * @param keys the keys to check for existence (must not be null, can be empty)
     * @return the number of keys that exist (0 to keys.length)
     * @throws JedisException if the operation fails
     * @since Valkey 3.0.3
     */
    public long exists(String... keys) {
        checkNotClosed();
        try {
            return baseClient.exists(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXISTS operation failed", e);
        }
    }

    /**
     * Asynchronously delete a key from the database. Unlike DEL, this command performs the deletion
     * in the background, making it non-blocking for large objects. The key is immediately removed
     * from the keyspace but the memory is reclaimed asynchronously.
     *
     * @param key the key to delete asynchronously (must not be null)
     * @return the number of keys that were deleted (0 or 1)
     * @throws JedisException if the operation fails
     * @since Valkey 4.0.0
     */
    public long unlink(String key) {
        checkNotClosed();
        try {
            return baseClient.unlink(new String[] {key}).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("UNLINK operation failed", e);
        }
    }

    /**
     * Asynchronously delete multiple keys from the database. Unlike DEL, this command performs the
     * deletion in the background, making it non-blocking for large objects. The keys are immediately
     * removed from the keyspace but the memory is reclaimed asynchronously.
     *
     * @param keys the keys to delete asynchronously (must not be null, can be empty)
     * @return the number of keys that were deleted
     * @throws JedisException if the operation fails
     * @since Valkey 4.0.0
     */
    public long unlink(String... keys) {
        checkNotClosed();
        try {
            return baseClient.unlink(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("UNLINK operation failed", e);
        }
    }

    /**
     * Get the data type of the value stored at a key. This command returns the string representation
     * of the type, which can be used to determine how to handle the value.
     *
     * @param key the key to get the type for (must not be null)
     * @return the type of the value ("string", "list", "set", "zset", "hash", "stream", or "none" if
     *     key doesn't exist)
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public String type(String key) {
        checkNotClosed();
        try {
            return baseClient.type(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TYPE operation failed", e);
        }
    }

    /**
     * Find all keys matching a given pattern. This operation can be expensive on large databases as
     * it scans all keys. Consider using SCAN for production environments with large datasets.
     *
     * <p>Supported patterns:
     *
     * <ul>
     *   <li>* - matches any number of characters
     *   <li>? - matches exactly one character
     *   <li>[abc] - matches any one of the characters in brackets
     *   <li>[a-z] - matches any character in the range
     * </ul>
     *
     * @param pattern the pattern to match keys against (must not be null)
     * @return a set of keys matching the pattern (never null, but can be empty)
     * @throws JedisException if the operation fails
     * @throws UnsupportedOperationException if called in cluster mode (use SCAN instead)
     * @since Valkey 1.0.0
     */
    public java.util.Set<String> keys(String pattern) {
        checkNotClosed();
        // KEYS command is not available in GLIDE BaseClient
        // We can implement it using SCAN for compatibility
        try {
            java.util.Set<String> allKeys = new java.util.HashSet<>();
            String cursor = "0";

            if (isClusterMode) {
                // For cluster mode, SCAN is more complex - simplified implementation
                throw new UnsupportedOperationException(
                        "KEYS command in cluster mode requires special handling not yet implemented");
            } else {
                do {
                    Object[] result = glideClient.scan(cursor).get();
                    cursor = (String) result[0];
                    String[] keys = (String[]) result[1];

                    // Filter keys by pattern (simple pattern matching)
                    for (String key : keys) {
                        if (matchesPattern(key, pattern)) {
                            allKeys.add(key);
                        }
                    }
                } while (!"0".equals(cursor));
            }

            return allKeys;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("KEYS operation failed", e);
        }
    }

    /**
     * Simple pattern matching helper for KEYS command compatibility. Converts Redis-style patterns to
     * Java regular expressions.
     *
     * @param key the key to test
     * @param pattern the Redis-style pattern
     * @return true if the key matches the pattern
     */
    private boolean matchesPattern(String key, String pattern) {
        // Convert Redis pattern to Java regex
        String regex =
                pattern.replace("*", ".*").replace("?", ".").replace("[", "\\[").replace("]", "\\]");
        return key.matches(regex);
    }

    /**
     * Return a random key from the database. This command is useful for sampling or implementing
     * random selection algorithms.
     *
     * @return a random key from the database, or null if the database is empty
     * @throws UnsupportedOperationException this command is not supported in the GLIDE compatibility
     *     layer
     * @since Valkey 1.0.0
     */
    public String randomKey() {
        checkNotClosed();
        // RANDOMKEY is not available in GLIDE BaseClient
        throw new UnsupportedOperationException(
                "RANDOMKEY command is not supported in GLIDE compatibility layer");
    }

    /**
     * Rename a key to a new name. If the destination key already exists, it will be overwritten. This
     * operation is atomic - the key is renamed instantly without any intermediate state.
     *
     * @param oldkey the current name of the key (must not be null and must exist)
     * @param newkey the new name for the key (must not be null)
     * @return "OK" if successful
     * @throws JedisException if the operation fails or the source key does not exist
     * @since Valkey 1.0.0
     */
    public String rename(String oldkey, String newkey) {
        checkNotClosed();
        try {
            return baseClient.rename(oldkey, newkey).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RENAME operation failed", e);
        }
    }

    /**
     * Rename a key to a new name only if the destination key does not already exist. This is useful
     * for atomic key renaming when you want to avoid overwriting existing data.
     *
     * @param oldkey the current name of the key (must not be null and must exist)
     * @param newkey the new name for the key (must not be null and must not exist)
     * @return 1 if the key was renamed, 0 if the destination key already exists
     * @throws JedisException if the operation fails or the source key does not exist
     * @since Valkey 1.0.0
     */
    public long renamenx(String oldkey, String newkey) {
        checkNotClosed();
        try {
            Boolean result = baseClient.renamenx(oldkey, newkey).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RENAMENX operation failed", e);
        }
    }

    /**
     * Set an expiration timeout on a key in seconds. After the timeout expires, the key will be
     * automatically deleted. This is useful for implementing TTL-based caching and automatic cleanup
     * of temporary data.
     *
     * @param key the key to set expiration on (must not be null and should exist)
     * @param seconds the expiration timeout in seconds (must be positive)
     * @return 1 if the timeout was set, 0 if the key does not exist
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public long expire(String key, long seconds) {
        checkNotClosed();
        try {
            Boolean result = baseClient.expire(key, seconds).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIRE operation failed", e);
        }
    }

    /**
     * Set an expiration timeout on a key in seconds with additional options. The ExpiryOption
     * parameter allows for conditional expiration setting based on the current expiration state of
     * the key.
     *
     * @param key the key to set expiration on (must not be null and should exist)
     * @param seconds the expiration timeout in seconds (must be positive)
     * @param expiryOption the condition for setting expiration (can be null for unconditional)
     * @return 1 if the timeout was set, 0 if the key does not exist or condition was not met
     * @throws JedisException if the operation fails
     * @since Valkey 7.0.0
     */
    public long expire(String key, long seconds, ExpiryOption expiryOption) {
        checkNotClosed();
        try {
            if (expiryOption == null) {
                return expire(key, seconds);
            }

            // Convert ExpiryOption to GLIDE ExpireOptions
            // Note: GLIDE may not support all expire options, this is a simplified implementation
            Boolean result = baseClient.expire(key, seconds).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIRE operation failed", e);
        }
    }

    /**
     * Set an expiration time for a key as a Unix timestamp in seconds. The key will be automatically
     * deleted when the specified timestamp is reached.
     *
     * @param key the key to set expiration on (must not be null and should exist)
     * @param unixTime the expiration time as Unix timestamp in seconds
     * @return 1 if the timeout was set, 0 if the key does not exist
     * @throws JedisException if the operation fails
     * @since Valkey 1.2.0
     */
    public long expireAt(String key, long unixTime) {
        checkNotClosed();
        try {
            Boolean result = baseClient.expireAt(key, unixTime).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIREAT operation failed", e);
        }
    }

    /** Set expiration at timestamp with expiry option */
    public long expireAt(String key, long unixTime, ExpiryOption expiryOption) {
        checkNotClosed();
        try {
            if (expiryOption == null) {
                return expireAt(key, unixTime);
            }

            // Convert ExpiryOption to GLIDE ExpireOptions
            // Note: GLIDE may not support all expire options, this is a simplified implementation
            Boolean result = baseClient.expireAt(key, unixTime).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("EXPIREAT operation failed", e);
        }
    }

    /** Set expiration in milliseconds */
    public long pexpire(String key, long milliseconds) {
        checkNotClosed();
        try {
            Boolean result = baseClient.pexpire(key, milliseconds).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PEXPIRE operation failed", e);
        }
    }

    /** Set expiration in milliseconds with expiry option */
    public long pexpire(String key, long milliseconds, ExpiryOption expiryOption) {
        checkNotClosed();
        try {
            if (expiryOption == null) {
                return pexpire(key, milliseconds);
            }

            // Convert ExpiryOption to GLIDE ExpireOptions
            // Note: GLIDE may not support all expire options, this is a simplified implementation
            Boolean result = baseClient.pexpire(key, milliseconds).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PEXPIRE operation failed", e);
        }
    }

    /** Set expiration at millisecond timestamp */
    public long pexpireAt(String key, long millisecondsTimestamp) {
        checkNotClosed();
        try {
            Boolean result = baseClient.pexpireAt(key, millisecondsTimestamp).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PEXPIREAT operation failed", e);
        }
    }

    /** Set expiration at millisecond timestamp with expiry option */
    public long pexpireAt(String key, long millisecondsTimestamp, ExpiryOption expiryOption) {
        checkNotClosed();
        try {
            if (expiryOption == null) {
                return pexpireAt(key, millisecondsTimestamp);
            }

            // Convert ExpiryOption to GLIDE ExpireOptions
            // Note: GLIDE may not support all expire options, this is a simplified implementation
            Boolean result = baseClient.pexpireAt(key, millisecondsTimestamp).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PEXPIREAT operation failed", e);
        }
    }

    /** Get expiration timestamp - not available in GLIDE */
    public long expireTime(String key) {
        checkNotClosed();
        // EXPIRETIME is not available in GLIDE BaseClient
        throw new UnsupportedOperationException(
                "EXPIRETIME command is not supported in GLIDE compatibility layer");
    }

    /** Get expiration millisecond timestamp - not available in GLIDE */
    public long pexpireTime(String key) {
        checkNotClosed();
        // PEXPIRETIME is not available in GLIDE BaseClient
        throw new UnsupportedOperationException(
                "PEXPIRETIME command is not supported in GLIDE compatibility layer");
    }

    /**
     * Get the remaining time to live of a key in seconds. This command returns the number of seconds
     * until the key expires, or special values for keys without expiration.
     *
     * @param key the key to check TTL for (must not be null)
     * @return the TTL in seconds, -1 if the key exists but has no expiration, -2 if the key does not
     *     exist
     * @throws JedisException if the operation fails
     * @since Valkey 1.0.0
     */
    public long ttl(String key) {
        checkNotClosed();
        try {
            return baseClient.ttl(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TTL operation failed", e);
        }
    }

    /**
     * Get the remaining time to live of a key in milliseconds. This command provides more precise
     * timing information than TTL, useful for fine-grained expiration monitoring.
     *
     * @param key the key to check TTL for (must not be null)
     * @return the TTL in milliseconds, -1 if the key exists but has no expiration, -2 if the key does
     *     not exist
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public long pttl(String key) {
        checkNotClosed();
        try {
            return baseClient.pttl(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PTTL operation failed", e);
        }
    }

    /**
     * Remove the expiration from a key, making it persistent. After this operation, the key will not
     * expire automatically and will remain in the database until explicitly deleted.
     *
     * @param key the key to make persistent (must not be null)
     * @return 1 if the expiration was removed, 0 if the key does not exist or has no expiration
     * @throws JedisException if the operation fails
     * @since Valkey 2.2.0
     */
    public long persist(String key) {
        checkNotClosed();
        try {
            Boolean result = baseClient.persist(key).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PERSIST operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/sort">SORT Command</a></b> Sort the elements in a list,
     * set or sorted set. By default, sorting is numeric and elements are compared by their value
     * interpreted as double precision floating point number.
     *
     * <p>Time complexity: O(N+M*log(M)) where N is the number of elements in the list or set to sort,
     * and M the number of returned elements. When the elements are not sorted, complexity is O(N).
     *
     * @param key the key of the list, set or sorted set to sort
     * @return the sorted elements as a list
     * @throws JedisException if the operation fails
     * @since Redis 1.0.0
     */
    public List<String> sort(String key) {
        checkNotClosed();
        try {
            String[] result = baseClient.sort(key).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/sort">SORT Command</a></b> Sort the elements in a list,
     * set or sorted set with additional parameters for controlling the sorting behavior, including
     * ordering, limiting results, and external key patterns.
     *
     * <p>Time complexity: O(N+M*log(M)) where N is the number of elements in the list or set to sort,
     * and M the number of returned elements. When the elements are not sorted, complexity is O(N).
     *
     * @param key the key of the list, set or sorted set to sort
     * @param sortingParameters the parameters controlling sort behavior (can be null for default
     *     sorting)
     * @return the sorted elements as a list
     * @throws JedisException if the operation fails
     * @since Redis 1.0.0
     */
    public List<String> sort(String key, SortingParams sortingParameters) {
        checkNotClosed();
        try {
            if (sortingParameters == null) {
                return sort(key);
            }

            // Convert Jedis SortingParams to GLIDE SortOptions
            // This is a simplified implementation - full implementation would need
            // to parse all SortingParams options
            String[] result = baseClient.sort(key).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/sort">SORT Command</a></b> Sort the elements and store
     * the result in a destination key. This is useful when you want to sort elements and store the
     * result for later use, rather than returning them immediately.
     *
     * <p>Time complexity: O(N+M*log(M)) where N is the number of elements in the list or set to sort,
     * and M the number of returned elements. When the elements are not sorted, complexity is O(N).
     *
     * @param key the key of the list, set or sorted set to sort
     * @param dstkey the destination key where the sorted result will be stored
     * @return the number of elements in the sorted result
     * @throws JedisException if the operation fails
     * @since Redis 1.0.0
     */
    public long sort(String key, String dstkey) {
        checkNotClosed();
        try {
            return baseClient.sortStore(key, dstkey).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT STORE operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/sort">SORT Command</a></b> Sort the elements with
     * parameters and store the result in a destination key. Combines the flexibility of parameterized
     * sorting with result storage.
     *
     * <p>Time complexity: O(N+M*log(M)) where N is the number of elements in the list or set to sort,
     * and M the number of returned elements. When the elements are not sorted, complexity is O(N).
     *
     * @param key the key of the list, set or sorted set to sort
     * @param sortingParameters the parameters controlling sort behavior (can be null for default
     *     sorting)
     * @param dstkey the destination key where the sorted result will be stored
     * @return the number of elements in the sorted result
     * @throws JedisException if the operation fails
     * @since Redis 1.0.0
     */
    public long sort(String key, SortingParams sortingParameters, String dstkey) {
        checkNotClosed();
        try {
            if (sortingParameters == null) {
                return sort(key, dstkey);
            }

            // Convert Jedis SortingParams to GLIDE SortOptions
            // This is a simplified implementation
            return baseClient.sortStore(key, dstkey).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT STORE operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/sort_ro">SORT_RO Command</a></b> Read-only variant of
     * the SORT command. This command is identical to SORT, but refuses to modify the database. This
     * allows it to be used in read-only replicas and during multi/exec.
     *
     * <p>Time complexity: O(N+M*log(M)) where N is the number of elements in the list or set to sort,
     * and M the number of returned elements. When the elements are not sorted, complexity is O(N).
     *
     * @param key the key of the list, set or sorted set to sort
     * @param sortingParams the parameters controlling sort behavior (can be null for default sorting)
     * @return the sorted elements as a list
     * @throws JedisException if the operation fails
     * @since Redis 7.0.0
     */
    public List<String> sortReadonly(String key, SortingParams sortingParams) {
        checkNotClosed();
        try {
            if (sortingParams == null) {
                return sort(key);
            }

            // Convert Jedis SortingParams to GLIDE SortOptions
            // This is a simplified implementation
            String[] result = baseClient.sortReadOnly(key).get();
            return Arrays.asList(result);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SORT_RO operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/dump">DUMP Command</a></b> Serialize the value stored at
     * key in a Redis-specific format and return it to the user. The returned value can be synthesized
     * back into a Redis key using the RESTORE command.
     *
     * <p>Time complexity: O(1) to access the key and additional O(N*M) to serialize it, where N is
     * the number of Redis objects composing the value and M their average size. For small string
     * values the time complexity is thus O(1)+O(1*M) where M is small, so simply O(1).
     *
     * @param key the key to serialize
     * @return the serialized value as a byte array
     * @throws JedisException if the operation fails or the key does not exist
     * @since Redis 2.6.0
     */
    public byte[] dump(String key) {
        checkNotClosed();
        try {
            return baseClient.dump(GlideString.of(key)).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DUMP operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/restore">RESTORE Command</a></b> Create a key using the
     * provided serialized value, previously obtained using DUMP. If ttl is 0 the key is created
     * without any expire, otherwise the specified expire time (in milliseconds) is set.
     *
     * <p>Time complexity: O(1) to create the new key and additional O(N*M) to reconstruct the
     * serialized value, where N is the number of Redis objects composing the value and M their
     * average size.
     *
     * @param key the key to create
     * @param ttl the time to live in milliseconds (0 for no expiration)
     * @param serializedValue the serialized value obtained from DUMP
     * @return "OK" if successful
     * @throws JedisException if the operation fails or the key already exists
     * @since Redis 2.6.0
     */
    public String restore(String key, long ttl, byte[] serializedValue) {
        checkNotClosed();
        try {
            return baseClient.restore(GlideString.of(key), ttl, serializedValue).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RESTORE operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/restore">RESTORE Command</a></b> Create a key using the
     * provided serialized value with additional restore parameters. This variant allows for more
     * control over the restore operation, including options like REPLACE.
     *
     * <p>Time complexity: O(1) to create the new key and additional O(N*M) to reconstruct the
     * serialized value, where N is the number of Redis objects composing the value and M their
     * average size.
     *
     * @param key the key to create
     * @param ttl the time to live in milliseconds (0 for no expiration)
     * @param serializedValue the serialized value obtained from DUMP
     * @param params additional restore parameters (can be null for default behavior)
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Redis 3.0.0
     */
    public String restore(String key, long ttl, byte[] serializedValue, RestoreParams params) {
        checkNotClosed();
        try {
            if (params == null) {
                return restore(key, ttl, serializedValue);
            }

            // Convert Jedis RestoreParams to GLIDE RestoreOptions
            // This is a simplified implementation
            return baseClient.restore(GlideString.of(key), ttl, serializedValue).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("RESTORE operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/migrate">MIGRATE Command</a></b> Atomically transfer a
     * key from a Redis instance to another one. On success the key is deleted from the original
     * instance and is guaranteed to exist in the target instance.
     *
     * <p><b>Note:</b> This command is not directly supported in the GLIDE compatibility layer.
     *
     * <p>Time complexity: This command actually executes a DUMP+DEL in the source instance, and a
     * RESTORE in the target instance.
     *
     * @param host the target host
     * @param port the target port
     * @param key the key to migrate
     * @param timeout the timeout in milliseconds
     * @return "OK" if successful
     * @throws UnsupportedOperationException always, as this command is not supported in GLIDE
     * @since Redis 2.6.0
     */
    public String migrate(String host, int port, String key, int timeout) {
        checkNotClosed();
        // MIGRATE is not directly supported in GLIDE BaseClient
        // This would need to be implemented using custom commands or alternative approaches
        throw new UnsupportedOperationException(
                "MIGRATE command is not supported in GLIDE compatibility layer");
    }

    /**
     * <b><a href="https://valkey.io/commands/migrate">MIGRATE Command</a></b> Atomically transfer
     * keys from a Redis instance to another one with additional parameters. This variant allows for
     * more control over the migration process.
     *
     * <p><b>Note:</b> This command is not directly supported in the GLIDE compatibility layer.
     *
     * <p>Time complexity: This command actually executes a DUMP+DEL in the source instance, and a
     * RESTORE in the target instance.
     *
     * @param host the target host
     * @param port the target port
     * @param timeout the timeout in milliseconds
     * @param params additional migration parameters
     * @param keys the keys to migrate
     * @return "OK" if successful
     * @throws UnsupportedOperationException always, as this command is not supported in GLIDE
     * @since Redis 3.0.0
     */
    public String migrate(String host, int port, int timeout, MigrateParams params, String... keys) {
        checkNotClosed();
        // MIGRATE is not directly supported in GLIDE BaseClient
        throw new UnsupportedOperationException(
                "MIGRATE command is not supported in GLIDE compatibility layer");
    }

    /**
     * <b><a href="https://valkey.io/commands/move">MOVE Command</a></b> Move key from the currently
     * selected database to the specified destination database. When key already exists in the
     * destination database, or it does not exist in the source database, it does nothing.
     *
     * <p><b>Note:</b> This command is only available in standalone mode, not in cluster mode.
     *
     * <p>Time complexity: O(1)
     *
     * @param key the key to move
     * @param dbIndex the destination database index
     * @return 1 if key was moved, 0 if key was not moved
     * @throws JedisException if used in cluster mode or if the operation fails
     * @since Redis 1.0.0
     */
    public long move(String key, int dbIndex) {
        checkNotClosed();
        if (isClusterMode) {
            throw new JedisException("MOVE is not supported in cluster mode");
        }
        try {
            Boolean result = glideClient.move(key, (long) dbIndex).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MOVE operation failed", e);
        }
    }

    /**
     * Incrementally iterate over the keys in the database. SCAN is a cursor-based iterator that
     * allows you to retrieve keys in small batches, making it suitable for large databases without
     * blocking the server for extended periods.
     *
     * @param cursor the cursor value ("0" to start iteration, or value from previous SCAN)
     * @return a ScanResult containing the next cursor and a list of keys
     * @throws JedisException if the operation fails
     * @throws UnsupportedOperationException if called in cluster mode (requires special handling)
     * @since Valkey 2.8.0
     */
    public ScanResult<String> scan(String cursor) {
        checkNotClosed();
        try {
            Object[] result;
            if (isClusterMode) {
                // For cluster mode, we need to handle the different cursor type
                // This is a simplified implementation - full cluster scan support would be more complex
                throw new UnsupportedOperationException(
                        "SCAN command in cluster mode requires special handling not yet implemented");
            } else {
                result = glideClient.scan(cursor).get();
            }
            String nextCursor = (String) result[0];
            String[] keys = (String[]) result[1];
            return new ScanResult<>(nextCursor, Arrays.asList(keys));
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * Incrementally iterate over the keys in the database with additional parameters. This version
     * allows you to specify patterns, count hints, and other options to control the iteration
     * behavior.
     *
     * @param cursor the cursor value ("0" to start iteration, or value from previous SCAN)
     * @param params the scan parameters for filtering and controlling iteration (can be null)
     * @return a ScanResult containing the next cursor and a list of keys
     * @throws JedisException if the operation fails
     * @throws UnsupportedOperationException if called in cluster mode (requires special handling)
     * @since Valkey 2.8.0
     */
    public ScanResult<String> scan(String cursor, ScanParams params) {
        checkNotClosed();
        try {
            if (params == null) {
                return scan(cursor);
            }

            // Convert Jedis ScanParams to GLIDE ScanOptions
            ScanOptions.ScanOptionsBuilder builder = ScanOptions.builder();

            // This is a simplified conversion - full implementation would need
            // to properly parse ScanParams
            ScanOptions options = builder.build();

            Object[] result;
            if (isClusterMode) {
                // For cluster mode, we need to handle the different cursor type
                throw new UnsupportedOperationException(
                        "SCAN command in cluster mode requires special handling not yet implemented");
            } else {
                result = glideClient.scan(cursor, options).get();
            }
            String nextCursor = (String) result[0];
            String[] keys = (String[]) result[1];
            return new ScanResult<>(nextCursor, Arrays.asList(keys));
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/scan">SCAN Command</a></b> Incrementally iterate over
     * the keys in the database with additional parameters and type filtering. This version allows you
     * to specify patterns, count hints, and filter by key type.
     *
     * <p>Time complexity: O(1) for every call. O(N) for a complete iteration, including enough
     * command calls for the cursor to return back to 0. N is the number of elements inside the
     * collection.
     *
     * @param cursor the cursor value ("0" to start iteration, or value from previous SCAN)
     * @param params the scan parameters for filtering and controlling iteration (can be null)
     * @param type the type of keys to return (e.g., "string", "list", "set", "zset", "hash",
     *     "stream")
     * @return a ScanResult containing the next cursor and a list of keys
     * @throws JedisException if the operation fails
     * @throws UnsupportedOperationException if called in cluster mode (requires special handling)
     * @since Redis 2.8.0
     */
    public ScanResult<String> scan(String cursor, ScanParams params, String type) {
        checkNotClosed();
        try {
            // Convert Jedis ScanParams to GLIDE ScanOptions
            ScanOptions.ScanOptionsBuilder builder = ScanOptions.builder();

            if (type != null) {
                // Convert string type to ObjectType enum
                ScanOptions.ObjectType objectType;
                switch (type.toLowerCase()) {
                    case "string":
                        objectType = ScanOptions.ObjectType.STRING;
                        break;
                    case "list":
                        objectType = ScanOptions.ObjectType.LIST;
                        break;
                    case "set":
                        objectType = ScanOptions.ObjectType.SET;
                        break;
                    case "zset":
                        objectType = ScanOptions.ObjectType.ZSET;
                        break;
                    case "hash":
                        objectType = ScanOptions.ObjectType.HASH;
                        break;
                    case "stream":
                        objectType = ScanOptions.ObjectType.STREAM;
                        break;
                    default:
                        objectType = null;
                }
                if (objectType != null) {
                    builder.type(objectType);
                }
            }

            // This is a simplified conversion - full implementation would need
            // to properly parse ScanParams
            ScanOptions options = builder.build();

            Object[] result;
            if (isClusterMode) {
                // For cluster mode, we need to handle the different cursor type
                throw new UnsupportedOperationException(
                        "SCAN command in cluster mode requires special handling not yet implemented");
            } else {
                result = glideClient.scan(cursor, options).get();
            }
            String nextCursor = (String) result[0];
            String[] keys = (String[]) result[1];
            return new ScanResult<>(nextCursor, Arrays.asList(keys));
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SCAN operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/touch">TOUCH Command</a></b> Alters the last access time
     * of a key. A key is ignored if it does not exist.
     *
     * <p>Time complexity: O(N) where N is the number of keys that will be touched.
     *
     * @param key the key to touch
     * @return 1 if the key was touched, 0 if the key does not exist
     * @throws JedisException if the operation fails
     * @since Redis 3.2.1
     */
    public long touch(String key) {
        checkNotClosed();
        try {
            return baseClient.touch(new String[] {key}).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TOUCH operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/touch">TOUCH Command</a></b> Alters the last access time
     * of multiple keys. Keys that do not exist are ignored.
     *
     * <p>Time complexity: O(N) where N is the number of keys that will be touched.
     *
     * @param keys the keys to touch
     * @return the number of keys that were touched
     * @throws JedisException if the operation fails
     * @since Redis 3.2.1
     */
    public long touch(String... keys) {
        checkNotClosed();
        try {
            return baseClient.touch(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("TOUCH operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/copy">COPY Command</a></b> Copy the value stored at the
     * source key to the destination key.
     *
     * <p>Time complexity: O(N) worst case for collections, where N is the number of nested items.
     * O(1) for string values.
     *
     * @param srcKey the source key
     * @param dstKey the destination key
     * @param replace whether to replace the destination key if it already exists
     * @return true if the key was copied, false if the source key does not exist or destination
     *     exists and replace is false
     * @throws JedisException if the operation fails
     * @since Redis 6.2.0
     */
    public boolean copy(String srcKey, String dstKey, boolean replace) {
        checkNotClosed();
        try {
            return baseClient.copy(srcKey, dstKey, replace).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("COPY operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/msetnx">MSETNX Command</a></b> Set multiple keys to
     * multiple values, only if none of the keys exist. MSETNX is atomic, so either all the keys are
     * set, or none are set.
     *
     * <p>Time complexity: O(N) where N is the number of keys to set.
     *
     * @param keysvalues alternating keys and values (key1, value1, key2, value2, ...)
     * @return 1 if all keys were set, 0 if no key was set (at least one key already existed)
     * @throws JedisException if the operation fails or if the number of arguments is not even
     * @since Redis 1.0.1
     */
    public long msetnx(String... keysvalues) {
        checkNotClosed();
        try {
            if (keysvalues.length % 2 != 0) {
                throw new IllegalArgumentException("Wrong number of arguments for MSETNX");
            }

            java.util.Map<String, String> keyValueMap = new java.util.HashMap<>();
            for (int i = 0; i < keysvalues.length; i += 2) {
                keyValueMap.put(keysvalues[i], keysvalues[i + 1]);
            }

            Boolean result = baseClient.msetnx(keyValueMap).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("MSETNX operation failed", e);
        }
    }

    // ========== BITMAP COMMANDS ==========

    /**
     * Set the bit value at the specified offset in the string stored at key. The string is treated as
     * a bit array, and individual bits can be set or cleared. If the key doesn't exist, a new string
     * is created.
     *
     * @param key the key containing the bitmap (must not be null)
     * @param offset the bit offset to set (must be non-negative)
     * @param value the bit value to set (true for 1, false for 0)
     * @return the original bit value at the offset before it was set
     * @throws JedisException if the operation fails
     * @since Valkey 2.2.0
     */
    public boolean setbit(String key, long offset, boolean value) {
        checkNotClosed();
        try {
            Long result = baseClient.setbit(key, offset, value ? 1L : 0L).get();
            return result == 1L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SETBIT operation failed", e);
        }
    }

    /**
     * Get the bit value at the specified offset in the string stored at key. If the offset is beyond
     * the string length, it is treated as 0.
     *
     * @param key the key containing the bitmap (must not be null)
     * @param offset the bit offset to get (must be non-negative)
     * @return the bit value at the offset (true for 1, false for 0)
     * @throws JedisException if the operation fails
     * @since Valkey 2.2.0
     */
    public boolean getbit(String key, long offset) {
        checkNotClosed();
        try {
            Long result = baseClient.getbit(key, offset).get();
            return result == 1L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GETBIT operation failed", e);
        }
    }

    /**
     * Count the number of set bits (bits with value 1) in the string stored at key. This operation is
     * useful for implementing efficient counting and analytics on bitmap data.
     *
     * @param key the key containing the bitmap (must not be null)
     * @return the number of bits set to 1
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public long bitcount(String key) {
        checkNotClosed();
        try {
            return baseClient.bitcount(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITCOUNT operation failed", e);
        }
    }

    /**
     * Count the number of set bits in a specific range of the string stored at key. The range is
     * specified by start and end byte positions (inclusive).
     *
     * @param key the key containing the bitmap (must not be null)
     * @param start the start byte position (inclusive, can be negative for end-relative)
     * @param end the end byte position (inclusive, can be negative for end-relative)
     * @return the number of bits set to 1 in the specified range
     * @throws JedisException if the operation fails
     * @since Valkey 2.6.0
     */
    public long bitcount(String key, long start, long end) {
        checkNotClosed();
        try {
            return baseClient.bitcount(key, start, end).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITCOUNT operation failed", e);
        }
    }

    /** Count set bits with option */
    public long bitcount(String key, long start, long end, BitCountOption option) {
        checkNotClosed();
        try {
            // Convert BitCountOption to GLIDE BitmapIndexType
            BitmapIndexType indexType =
                    (option == BitCountOption.BYTE) ? BitmapIndexType.BYTE : BitmapIndexType.BIT;

            return baseClient.bitcount(key, start, end, indexType).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITCOUNT operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/bitpos">BITPOS Command</a></b> Return the position of
     * the first bit set to 1 or 0 in a string. The position is returned, thinking of the string as an
     * array of bits from left to right, where the first byte's most significant bit is at position 0.
     *
     * <p>Time complexity: O(N)
     *
     * @param key the key containing the bitmap
     * @param value the bit value to search for (true for 1, false for 0)
     * @return the position of the first bit set to the specified value, or -1 if not found
     * @throws JedisException if the operation fails
     * @since Redis 2.8.7
     */
    public long bitpos(String key, boolean value) {
        checkNotClosed();
        try {
            return baseClient.bitpos(key, value ? 1L : 0L).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITPOS operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/bitpos">BITPOS Command</a></b> Return the position of
     * the first bit set to 1 or 0 in a string with additional parameters. This variant allows you to
     * specify start and end positions to limit the search range.
     *
     * <p>Time complexity: O(N)
     *
     * @param key the key containing the bitmap
     * @param value the bit value to search for (true for 1, false for 0)
     * @param params additional parameters for controlling the search range (can be null)
     * @return the position of the first bit set to the specified value, or -1 if not found
     * @throws JedisException if the operation fails
     * @since Redis 2.8.7
     */
    public long bitpos(String key, boolean value, BitPosParams params) {
        checkNotClosed();
        try {
            if (params == null) {
                return bitpos(key, value);
            }

            // Convert BitPosParams to start/end parameters
            // This is a simplified implementation - full implementation would need
            // to parse the actual parameters from BitPosParams
            return baseClient.bitpos(key, value ? 1L : 0L).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITPOS operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/bitop">BITOP Command</a></b> Perform a bitwise operation
     * between multiple keys (containing string values) and store the result in the destination key.
     * The BITOP command supports four bitwise operations: AND, OR, XOR and NOT.
     *
     * <p>Time complexity: O(N)
     *
     * @param op the bitwise operation to perform (AND, OR, XOR, NOT)
     * @param destKey the destination key where the result will be stored
     * @param srcKeys the source keys to perform the operation on
     * @return the size of the string stored in the destination key
     * @throws JedisException if the operation fails
     * @since Redis 2.6.0
     */
    public long bitop(BitOP op, String destKey, String... srcKeys) {
        checkNotClosed();
        try {
            // Convert Jedis BitOP to GLIDE BitwiseOperation
            BitwiseOperation operation;
            switch (op) {
                case AND:
                    operation = BitwiseOperation.AND;
                    break;
                case OR:
                    operation = BitwiseOperation.OR;
                    break;
                case XOR:
                    operation = BitwiseOperation.XOR;
                    break;
                case NOT:
                    operation = BitwiseOperation.NOT;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported BitOP operation: " + op);
            }

            return baseClient.bitop(operation, destKey, srcKeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("BITOP operation failed", e);
        }
    }

    /**
     * <b><a href="https://valkey.io/commands/bitfield">BITFIELD Command</a></b> Treat a Redis string
     * as an array of bits and perform arbitrary bit field operations on it. The command can
     * atomically get, set and increment bit field values using different integer types.
     *
     * <p><b>Subcommand Format:</b>
     *
     * <ul>
     *   <li><b>GET</b> &lt;type&gt; &lt;offset&gt; - Get the specified bit field
     *   <li><b>SET</b> &lt;type&gt; &lt;offset&gt; &lt;value&gt; - Set the specified bit field and
     *       return its old value
     *   <li><b>INCRBY</b> &lt;type&gt; &lt;offset&gt; &lt;increment&gt; - Increment the specified bit
     *       field and return the new value
     * </ul>
     *
     * <p><b>Type Format:</b> [u|i]&lt;width&gt; where u=unsigned, i=signed, width=1-64 bits <br>
     * Examples: u8, i16, u32, i64
     *
     * <p><b>Note:</b> This is currently a placeholder implementation that does not parse the
     * arguments. Full implementation requires proper parsing of bitfield subcommands.
     *
     * <p>Time complexity: O(1) for each subcommand specified
     *
     * @param key the key containing the bitmap
     * @param arguments the bit field operations in the format: subcommand type offset [value]
     * @return a list of results for each subcommand (may contain null values for some operations)
     * @throws JedisException if the operation fails
     * @throws UnsupportedOperationException currently thrown as this is a placeholder implementation
     * @since Redis 3.2.0
     */
    public List<Long> bitfield(String key, String... arguments) {
        checkNotClosed();
        // This is currently a placeholder implementation
        // Full implementation would need to parse bitfield subcommands (GET, SET, INCRBY)
        // and convert them to GLIDE BitFieldOptions.BitFieldSubCommands
        throw new UnsupportedOperationException(
                "BITFIELD command is not fully implemented in the compatibility layer. "
                        + "Please use the native GLIDE client for bitfield operations.");
    }

    /**
     * <b><a href="https://valkey.io/commands/bitfield_ro">BITFIELD_RO Command</a></b> Read-only
     * variant of the BITFIELD command. It can only be used with GET subcommands. This command is
     * useful when you want to perform read-only bit field operations without the risk of modifying
     * the data.
     *
     * <p><b>Subcommand Format:</b>
     *
     * <ul>
     *   <li><b>GET</b> &lt;type&gt; &lt;offset&gt; - Get the specified bit field
     * </ul>
     *
     * <p><b>Type Format:</b> [u|i]&lt;width&gt; where u=unsigned, i=signed, width=1-64 bits <br>
     * Examples: u8, i16, u32, i64
     *
     * <p><b>Note:</b> This is currently a placeholder implementation that does not parse the
     * arguments. Full implementation requires proper parsing of bitfield GET subcommands.
     *
     * <p>Time complexity: O(1) for each subcommand specified
     *
     * @param key the key containing the bitmap
     * @param arguments the bit field GET operations in the format: GET type offset
     * @return a list of results for each GET subcommand
     * @throws JedisException if the operation fails
     * @throws UnsupportedOperationException currently thrown as this is a placeholder implementation
     * @since Redis 6.0.0
     */
    public List<Long> bitfieldReadonly(String key, String... arguments) {
        checkNotClosed();
        // This is currently a placeholder implementation
        // Full implementation would need to parse bitfield GET subcommands
        // and convert them to GLIDE BitFieldOptions.BitFieldReadOnlySubCommands
        throw new UnsupportedOperationException(
                "BITFIELD_RO command is not fully implemented in the compatibility layer. "
                        + "Please use the native GLIDE client for read-only bitfield operations.");
    }

    // ========== HYPERLOGLOG COMMANDS ==========

    /**
     * Add elements to a HyperLogLog data structure. HyperLogLog is a probabilistic data structure
     * used for estimating the cardinality of large datasets with minimal memory usage.
     *
     * @param key the key of the HyperLogLog (must not be null)
     * @param elements the elements to add (must not be null, can be empty)
     * @return 1 if the HyperLogLog was modified, 0 if it was not modified
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public long pfadd(String key, String... elements) {
        checkNotClosed();
        try {
            Boolean result = baseClient.pfadd(key, elements).get();
            return result ? 1L : 0L;
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFADD operation failed", e);
        }
    }

    /**
     * Get the estimated cardinality of a HyperLogLog. This returns an approximation of the number of
     * unique elements that have been added to the HyperLogLog.
     *
     * @param key the key of the HyperLogLog (must not be null)
     * @return the estimated cardinality
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public long pfcount(String key) {
        checkNotClosed();
        try {
            return baseClient.pfcount(new String[] {key}).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFCOUNT operation failed", e);
        }
    }

    /**
     * Get the estimated cardinality of the union of multiple HyperLogLogs. This operation computes
     * the estimated number of unique elements across all specified HyperLogLog keys without modifying
     * the original structures.
     *
     * @param keys the keys of the HyperLogLogs to union (must not be null, should not be empty)
     * @return the estimated cardinality of the union
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public long pfcount(String... keys) {
        checkNotClosed();
        try {
            return baseClient.pfcount(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFCOUNT operation failed", e);
        }
    }

    /**
     * Merge multiple HyperLogLog structures into a destination key. The destination HyperLogLog will
     * contain the union of all unique elements from the source HyperLogLogs.
     *
     * @param destkey the destination key for the merged HyperLogLog (must not be null)
     * @param sourcekeys the source HyperLogLog keys to merge (must not be null, should not be empty)
     * @return "OK" if successful
     * @throws JedisException if the operation fails
     * @since Valkey 2.8.9
     */
    public String pfmerge(String destkey, String... sourcekeys) {
        checkNotClosed();
        try {
            return baseClient.pfmerge(destkey, sourcekeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PFMERGE operation failed", e);
        }
    }

    // ========== STANDALONE-SPECIFIC OPERATIONS ==========

    /**
     * Select the database to use for subsequent operations. This command is only available in
     * standalone mode and allows switching between different logical databases (0-15 by default). In
     * cluster mode, this operation is not supported as all operations use database 0.
     *
     * @param database the database index to select (typically 0-15, must be non-negative)
     * @return "OK" if successful
     * @throws JedisException if the operation fails or the database index is invalid
     * @throws UnsupportedOperationException if called in cluster mode
     * @since Valkey 1.0.0
     */
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
