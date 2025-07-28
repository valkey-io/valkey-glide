/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.GlideString;
import glide.api.models.configuration.GlideClientConfiguration;
import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;

// import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * UnifiedJedis compatibility wrapper for Valkey GLIDE client. This class provides the base unified
 * API that other Jedis clients extend from, while using Valkey GLIDE underneath.
 */
public class UnifiedJedis implements Closeable {

    protected final GlideClient glideClient;
    protected final JedisClientConfig config;
    protected final String resourceId;
    protected volatile boolean closed = false;

    public UnifiedJedis() {
        this(new HostAndPort("localhost", 6379));
    }

    public UnifiedJedis(HostAndPort hostAndPort) {
        this(hostAndPort, DefaultJedisClientConfig.builder().build());
    }

    // Not in Jedis
    public UnifiedJedis(final String host, final int port) {
        this(new HostAndPort(host, port));
    }

    public UnifiedJedis(final String url) {
        this(URI.create(url));
    }

    public UnifiedJedis(final URI uri) {
        this(
                extractHostAndPort(uri),
                buildConfigFromURI(uri, DefaultJedisClientConfig.builder().build()));
    }

    public UnifiedJedis(final URI uri, JedisClientConfig config) {
        this(extractHostAndPort(uri), buildConfigFromURI(uri, config));
    }

    public UnifiedJedis(HostAndPort hostAndPort, JedisClientConfig clientConfig) {
        this.config = clientConfig;

        // Validate configuration
        ConfigurationMapper.validateConfiguration(clientConfig);

        // Map Jedis config to GLIDE config
        GlideClientConfiguration glideConfig =
                ConfigurationMapper.mapToGlideConfig(
                        hostAndPort.getHost(), hostAndPort.getPort(), clientConfig);

        try {
            this.glideClient = GlideClient.createClient(glideConfig).get();
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE client", e);
        }
    }

    //    // Experimental constructors (cache support - simplified for compatibility)
    //    public UnifiedJedis(HostAndPort hostAndPort, JedisClientConfig clientConfig, Object
    // cacheConfig) {
    //        this(hostAndPort, clientConfig); // Cache not supported in GLIDE compatibility layer
    //    }
    //
    //    public UnifiedJedis(HostAndPort hostAndPort, JedisClientConfig clientConfig, Object cache) {
    //        this(hostAndPort, clientConfig); // Cache not supported in GLIDE compatibility layer
    //    }
    //
    //    // Constructor for custom socket factory (simplified)
    //    public UnifiedJedis(Object socketFactory) {
    //        this(); // Default connection for compatibility
    //    }

    public UnifiedJedis(Object socketFactory, JedisClientConfig clientConfig) {
        this(new HostAndPort("localhost", 6379), clientConfig);
    }

    //    // Constructor for direct connection (simplified)
    //    public UnifiedJedis(Object connection) {
    //        this(); // Default connection for compatibility
    //    }

    // Deprecated cluster constructors (for compatibility)
    @Deprecated
    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig, int maxAttempts) {
        this(
                jedisClusterNodes,
                clientConfig,
                maxAttempts,
                Duration.ofMillis(maxAttempts * clientConfig.getSocketTimeoutMillis()));
    }

    @Deprecated
    public UnifiedJedis(
            Set<HostAndPort> jedisClusterNodes,
            JedisClientConfig clientConfig,
            int maxAttempts,
            Duration maxTotalRetriesDuration) {
        // For compatibility, use first node as connection point
        this(jedisClusterNodes.iterator().next(), clientConfig);
    }

    //    @Deprecated
    //    public UnifiedJedis(Set<HostAndPort> jedisClusterNodes, JedisClientConfig clientConfig,
    //        GenericObjectPoolConfig<Object> poolConfig, int maxAttempts, Duration
    // maxTotalRetriesDuration) {
    //        this(jedisClusterNodes.iterator().next(), clientConfig);
    //    }

    // Sharded constructors (deprecated - simplified for compatibility)
    @Deprecated
    public UnifiedJedis(Object provider) {
        this(); // Default connection
    }

    @Deprecated
    public UnifiedJedis(Object provider, Object tagPattern) {
        this(); // Default connection
    }

    // Retry constructor
    public UnifiedJedis(Object provider, int maxAttempts, Duration maxTotalRetriesDuration) {
        this(); // Default connection for compatibility
    }

    //    // Multi-cluster constructor (experimental - simplified)
    //    public UnifiedJedis(Object multiClusterProvider) {
    //        this(); // Default connection for compatibility
    //    }
    //
    //    // Command executor constructor
    //    public UnifiedJedis(Object executor) {
    //        this(); // Default connection for compatibility
    //    }

    // Protected constructors for internal use
    protected UnifiedJedis(GlideClient glideClient, JedisClientConfig jedisConfig) {
        this.glideClient = glideClient;
        this.config = jedisConfig;
        this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
    }

    /**
     * Set the string value of a key.
     *
     * @param key the key
     * @param value the value
     * @return "OK" if successful
     */
    public String set(String key, String value) {
        checkNotClosed();
        try {
            return glideClient.set(key, value).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("SET operation failed", e);
        }
    }

    /**
     * Get the value of a key.
     *
     * @param key the key
     * @return the value of the key, or null if the key does not exist
     */
    public String get(String key) {
        checkNotClosed();
        try {
            return glideClient.get(key).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("GET operation failed", e);
        }
    }

    /**
     * Delete a key.
     *
     * @param key the key to delete
     * @return the number of keys that were removed (0 or 1)
     */
    public long del(String key) {
        checkNotClosed();
        try {
            return glideClient.del(new String[] {key}).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
    }

    /**
     * Delete one or more keys.
     *
     * @param keys the keys to delete
     * @return the number of keys that were removed
     */
    public long del(String... keys) {
        checkNotClosed();
        try {
            return glideClient.del(keys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
    }

    /**
     * Delete a key (binary version).
     *
     * @param key the key to delete
     * @return the number of keys that were removed (0 or 1)
     */
    public long del(final byte[] key) {
        checkNotClosed();
        try {
            return glideClient.del(new GlideString[] {GlideString.of(key)}).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
    }

    /**
     * Delete one or more keys (binary version).
     *
     * @param keys the keys to delete
     * @return the number of keys that were removed
     */
    public long del(final byte[]... keys) {
        checkNotClosed();
        try {
            GlideString[] glideKeys = new GlideString[keys.length];
            for (int i = 0; i < keys.length; i++) {
                glideKeys[i] = GlideString.of(keys[i]);
            }
            return glideClient.del(glideKeys).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("DEL operation failed", e);
        }
    }

    /**
     * Test if the server is alive.
     *
     * @return "PONG"
     */
    public String ping() {
        checkNotClosed();
        try {
            return glideClient.ping().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PING operation failed", e);
        }
    }

    /**
     * Test if the server is alive with a custom message.
     *
     * @param message the message to echo back
     * @return the echoed message
     */
    public String ping(String message) {
        checkNotClosed();
        try {
            return glideClient.ping(message).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PING operation failed", e);
        }
    }

    /**
     * Check if the connection is closed.
     *
     * @return true if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Get the client configuration.
     *
     * @return the configuration
     */
    public JedisClientConfig getConfig() {
        return config;
    }

    /** Close the connection. */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        ResourceLifecycleManager.getInstance().unregisterResource(resourceId);

        try {
            glideClient.close();
        } catch (Exception e) {
            throw new JedisException("Failed to close GLIDE client", e);
        }
    }

    /**
     * Get the underlying GLIDE client for internal use.
     *
     * @return the GLIDE client
     */
    protected GlideClient getGlideClient() {
        return glideClient;
    }

    /** Check if the connection is not closed and throw exception if it is. */
    protected void checkNotClosed() {
        if (closed) {
            throw new JedisException("Connection is closed");
        }
    }

    // Helper methods for URI processing
    private static HostAndPort extractHostAndPort(URI uri) {
        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        int port = uri.getPort() != -1 ? uri.getPort() : 6379;
        return new HostAndPort(host, port);
    }

    private static JedisClientConfig buildConfigFromURI(URI uri, JedisClientConfig baseConfig) {
        DefaultJedisClientConfig.Builder builder =
                DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(baseConfig.getConnectionTimeoutMillis())
                        .socketTimeoutMillis(baseConfig.getSocketTimeoutMillis())
                        .blockingSocketTimeoutMillis(baseConfig.getBlockingSocketTimeoutMillis())
                        .clientName(baseConfig.getClientName())
                        .ssl(baseConfig.isSsl())
                        .sslSocketFactory(baseConfig.getSslSocketFactory())
                        .sslParameters(baseConfig.getSslParameters())
                        .hostnameVerifier(baseConfig.getHostnameVerifier());

        // Extract user info from URI
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            String[] parts = userInfo.split(":");
            if (parts.length == 2) {
                builder.user(parts[0]).password(parts[1]);
            } else if (parts.length == 1) {
                builder.password(parts[0]);
            }
        }

        // Extract database from path
        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            try {
                int database = Integer.parseInt(path.substring(1));
                builder.database(database);
            } catch (NumberFormatException e) {
                // Ignore invalid database number
            }
        }

        // Check for SSL scheme
        if ("rediss".equals(uri.getScheme())) {
            builder.ssl(true);
        }

        return builder.build();
    }
}
