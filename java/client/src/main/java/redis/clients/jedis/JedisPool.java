/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import java.io.Closeable;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import redis.clients.jedis.exceptions.JedisException;

/**
 * JedisPool compatibility wrapper for Valkey GLIDE client. This class provides a Jedis-like
 * connection pool API while using Valkey GLIDE underneath.
 */
public class JedisPool implements Closeable {

    private final String host;
    private final int port;
    private final JedisClientConfig config;
    private final int maxConnections;
    private final long maxWaitMillis;
    private final BlockingQueue<Jedis> availableConnections;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final String poolId;

    /** Create a new JedisPool with default localhost:6379 connection. */
    public JedisPool() {
        this("localhost", 6379);
    }

    /**
     * Create a new JedisPool with specified host and port.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     */
    public JedisPool(String host, int port) {
        this(host, port, DefaultJedisClientConfig.builder().build(), 8, 2000);
    }

    /**
     * Create a new JedisPool with specified host, port and SSL configuration.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     * @param useSsl whether to use SSL/TLS
     */
    public JedisPool(String host, int port, boolean useSsl) {
        this(host, port, DefaultJedisClientConfig.builder().ssl(useSsl).build(), 8, 2000);
    }

    /**
     * Create a new JedisPool with SSL configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param ssl whether to use SSL
     * @param sslSocketFactory SSL socket factory
     * @param sslParameters SSL parameters
     * @param hostnameVerifier hostname verifier
     */
    public JedisPool(
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
                        .build(),
                8,
                2000);
    }

    /**
     * Create a new JedisPool with timeout configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param timeout the timeout in milliseconds
     */
    public JedisPool(String host, int port, int timeout) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build(),
                8,
                2000);
    }

    /**
     * Create a new JedisPool with full configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param config the client configuration
     * @param maxConnections maximum number of connections in the pool
     * @param maxWaitMillis maximum time to wait for a connection
     */
    public JedisPool(
            String host, int port, JedisClientConfig config, int maxConnections, long maxWaitMillis) {
        this.host = host;
        this.port = port;
        this.config = config;
        this.maxConnections = maxConnections;
        this.maxWaitMillis = maxWaitMillis;
        this.availableConnections = new LinkedBlockingQueue<>();
        this.poolId = ResourceLifecycleManager.getInstance().registerResource(this);

        // Validate configuration
        ConfigurationMapper.validateConfiguration(config);
    }

    /**
     * Get a Jedis connection from the pool.
     *
     * @return a Jedis connection
     * @throws JedisException if unable to get a connection
     */
    public Jedis getResource() {
        if (closed.get()) {
            throw new JedisException("Pool is closed");
        }

        // Try to get an existing connection from the pool
        Jedis jedis = availableConnections.poll();
        if (jedis != null && !jedis.isClosed()) {
            jedis.resetForReuse();
            activeConnections.incrementAndGet();
            return jedis;
        }

        // Create a new connection if pool is not at capacity
        if (totalConnections.get() < maxConnections) {
            jedis = createNewConnection();
            if (jedis != null) {
                totalConnections.incrementAndGet();
                activeConnections.incrementAndGet();
                return jedis;
            }
        }

        // Wait for an available connection
        try {
            jedis = availableConnections.poll(maxWaitMillis, TimeUnit.MILLISECONDS);
            if (jedis != null && !jedis.isClosed()) {
                jedis.resetForReuse();
                activeConnections.incrementAndGet();
                return jedis;
            } else {
                throw new JedisException("Could not get a resource from the pool");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JedisException("Interrupted while waiting for connection", e);
        }
    }

    /**
     * Return a connection to the pool. This method is called internally by Jedis.close() for pooled
     * connections.
     *
     * @param jedis the connection to return
     */
    protected void returnResource(Jedis jedis) {
        if (!closed.get() && jedis != null && !jedis.isClosed()) {
            activeConnections.decrementAndGet();
            // Check return value to handle queue full scenario
            if (!availableConnections.offer(jedis)) {
                // Queue is full, close the connection and decrement total count
                totalConnections.decrementAndGet();
                try {
                    jedis.getGlideClient().close();
                } catch (Exception e) {
                    // Log error but don't throw - we're in cleanup mode
                    System.err.println("Warning: Failed to close Jedis connection when pool queue full:");
                    e.printStackTrace();
                }
            }
        } else if (jedis != null) {
            // Connection is closed or pool is closed, decrement total count
            totalConnections.decrementAndGet();
            if (!jedis.isClosed()) {
                try {
                    jedis.getGlideClient().close();
                } catch (Exception e) {
                    // Log error but continue
                    System.err.println("Error closing returned connection:");
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Create a new Jedis connection.
     *
     * @return a new Jedis connection
     * @throws JedisException if unable to create connection
     */
    private Jedis createNewConnection() {
        try {
            GlideClientConfiguration glideConfig =
                    ConfigurationMapper.mapToGlideConfig(host, port, config);
            GlideClient glideClient = GlideClient.createClient(glideConfig).get();
            return new Jedis(glideClient, this, config);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("Failed to create new connection", e);
        }
    }

    /** Close the pool and all connections. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            ResourceLifecycleManager.getInstance().unregisterResource(poolId);

            // Close all available connections
            Jedis jedis;
            while ((jedis = availableConnections.poll()) != null) {
                try {
                    jedis.getGlideClient().close();
                } catch (Exception e) {
                    // Log error but continue closing other connections
                    System.err.println("Error closing connection:");
                    e.printStackTrace();
                }
            }

            // Reset counters
            totalConnections.set(0);
            activeConnections.set(0);
        }
    }

    /**
     * Check if the pool is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Get the number of active connections in the pool.
     *
     * @return number of active connections
     */
    public int getNumActive() {
        return activeConnections.get();
    }

    /**
     * Get the number of idle connections in the pool.
     *
     * @return number of idle connections
     */
    public int getNumIdle() {
        return availableConnections.size();
    }

    /**
     * Get the total number of connections created by this pool.
     *
     * @return total number of connections
     */
    public int getNumTotal() {
        return totalConnections.get();
    }

    /**
     * Get the maximum number of connections allowed in the pool.
     *
     * @return maximum connections
     */
    public int getMaxTotal() {
        return maxConnections;
    }

    /**
     * Get the maximum wait time for getting a connection.
     *
     * @return maximum wait time in milliseconds
     */
    public long getMaxWaitMillis() {
        return maxWaitMillis;
    }

    /**
     * Get the pool configuration.
     *
     * @return the client configuration
     */
    public JedisClientConfig getConfig() {
        return config;
    }

    /**
     * Get pool statistics as a formatted string.
     *
     * @return pool statistics
     */
    public String getPoolStats() {
        return String.format(
                "JedisPool[active=%d, idle=%d, total=%d, max=%d, closed=%s]",
                getNumActive(), getNumIdle(), getNumTotal(), getMaxTotal(), isClosed());
    }
}
