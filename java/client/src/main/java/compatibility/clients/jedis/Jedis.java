/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import java.io.Closeable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/**
 * Jedis compatibility wrapper for Valkey GLIDE client. This class provides a Jedis-like API while
 * using Valkey GLIDE underneath.
 */
public class Jedis implements Closeable {

    private static final Logger logger = Logger.getLogger(Jedis.class.getName());

    private final GlideClient glideClient;
    private final boolean isPooled;
    private final String resourceId;
    private final JedisClientConfig config;
    private JedisPool parentPool;
    private volatile boolean closed = false;

    /** Create a new Jedis instance with default localhost:6379 connection. */
    public Jedis() {
        this("localhost", 6379);
    }

    /**
     * Create a new Jedis instance with specified host and port.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     */
    public Jedis(String host, int port) {
        this(host, port, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Create a new Jedis instance with specified host, port and SSL configuration.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     * @param useSsl whether to use SSL/TLS
     */
    public Jedis(String host, int port, boolean useSsl) {
        this(host, port, DefaultJedisClientConfig.builder().ssl(useSsl).build());
    }

    /**
     * Create a new Jedis instance with full configuration.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     * @param config the jedis client configuration
     */
    public Jedis(String host, int port, JedisClientConfig config) {
        this.isPooled = false;
        this.parentPool = null;
        this.config = config;

        // Validate configuration
        ConfigurationMapper.validateConfiguration(config);

        // Map Jedis config to GLIDE config
        GlideClientConfiguration glideConfig = ConfigurationMapper.mapToGlideConfig(host, port, config);

        try {
            this.glideClient = GlideClient.createClient(glideConfig).get();
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE client", e);
        }
    }

    /**
     * Create Jedis with SSL configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param ssl whether to use SSL
     * @param sslSocketFactory SSL socket factory
     * @param sslParameters SSL parameters
     * @param hostnameVerifier hostname verifier
     */
    public Jedis(
            String host,
            int port,
            boolean ssl,
            SSLSocketFactory sslSocketFactory,
            SSLParameters sslParameters,
            HostnameVerifier hostnameVerifier) {
        this(
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
     * Create Jedis with timeout configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param timeout the timeout in milliseconds
     */
    public Jedis(String host, int port, int timeout) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build());
    }

    /**
     * Internal constructor for pooled connections.
     *
     * @param glideClient the underlying GLIDE client
     * @param pool the parent pool
     * @param config the client configuration
     */
    protected Jedis(GlideClient glideClient, JedisPool pool, JedisClientConfig config) {
        this.glideClient = glideClient;
        this.isPooled = true;
        this.parentPool = pool;
        this.config = config;
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
     * Select a database.
     *
     * @param index the database index
     * @return "OK" if successful
     */
    public String select(int index) {
        checkNotClosed();
        if (config.getDatabase() != index) {
            logger.warning("Database selection may behave differently in GLIDE compatibility mode");
        }
        // TO DO: GLIDE handles database selection differently. This is a placeholder implementation
        // In case of Glide, the databaseId is set in  GlideClientConfiguration. Will need to re call
        // the constructor for this to work.

        return "OK";
    }

    /**
     * Authenticate with the server.
     *
     * @param password the password
     * @return "OK" if successful
     */
    public String auth(String password) {
        checkNotClosed();
        // TO DO: GLIDE handles auth differently. This is a placeholder for runtime authentication.
        // In case of Glide, the auth is set in  ServerCredentials.
        // Will need to call the constructor again for this to work.

        return "OK";
    }

    /**
     * Authenticate with username and password.
     *
     * @param user the username
     * @param password the password
     * @return "OK" if successful
     */
    public String auth(String user, String password) {
        checkNotClosed();
        // TO DO: GLIDE handles auth differently. This is a placeholder for runtime authentication.
        // In case of Glide, the auth is set in  ServerCredentials.
        // Will need to call the constructor again for this to work.
        return "OK";
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

    /**
     * Close the connection. If this is a pooled connection, return it to the pool. Otherwise, close
     * the underlying GLIDE client.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        ResourceLifecycleManager.getInstance().unregisterResource(resourceId);

        if (isPooled && parentPool != null) {
            parentPool.returnResource(this);
        } else {
            try {
                glideClient.close();
            } catch (Exception e) {
                throw new JedisException("Failed to close GLIDE client", e);
            }
        }
    }

    /**
     * Get the underlying GLIDE client. This method is for internal use by the pool.
     *
     * @return the GLIDE client
     */
    protected GlideClient getGlideClient() {
        return glideClient;
    }

    /**
     * Check if this connection is from a pool.
     *
     * @return true if pooled, false otherwise
     */
    protected boolean isPooled() {
        return isPooled;
    }

    /** Reset the closed state for pooled connections. */
    protected void resetForReuse() {
        if (isPooled) {
            closed = false;
        }
    }

    /** Check if the connection is not closed and throw exception if it is. */
    private void checkNotClosed() {
        if (closed) {
            throw new JedisException("Connection is closed");
        }
    }
}
