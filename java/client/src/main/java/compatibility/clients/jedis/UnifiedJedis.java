/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.GlideClient;
import glide.api.models.configuration.GlideClientConfiguration;
import java.io.Closeable;
import java.util.concurrent.ExecutionException;

/**
 * UnifiedJedis compatibility wrapper for Valkey GLIDE client. This class provides the base
 * unified API that other Jedis clients extend from, while using Valkey GLIDE underneath.
 */
public class UnifiedJedis implements Closeable {

    protected final GlideClient glideClient;
    protected final JedisClientConfig config;
    protected final String resourceId;
    protected volatile boolean closed = false;

    /**
     * Create UnifiedJedis with GLIDE client configuration.
     *
     * @param glideConfig the GLIDE client configuration
     * @param jedisConfig the Jedis client configuration
     */
    protected UnifiedJedis(GlideClientConfiguration glideConfig, JedisClientConfig jedisConfig) {
        this.config = jedisConfig;
        
        try {
            this.glideClient = GlideClient.createClient(glideConfig).get();
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE client", e);
        }
    }

    /**
     * Create UnifiedJedis with existing GLIDE client (for internal use).
     *
     * @param glideClient the existing GLIDE client
     * @param jedisConfig the Jedis client configuration
     */
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

    /**
     * Close the connection.
     */
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
}
