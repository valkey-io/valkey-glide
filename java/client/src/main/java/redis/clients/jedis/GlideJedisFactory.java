/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.GlideClient;
import glide.api.logging.Logger;
import glide.api.models.configuration.GlideClientConfiguration;
import java.util.concurrent.ExecutionException;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Factory for creating and managing Jedis instances backed by GLIDE clients in a connection pool.
 * This factory implements the Apache Commons Pool PooledObjectFactory interface to provide proper
 * connection lifecycle management.
 */
public class GlideJedisFactory implements PooledObjectFactory<Jedis> {

    private final String host;
    private final int port;
    private final JedisClientConfig clientConfig;
    private JedisPool pool; // Pool reference set after factory creation

    /**
     * Create a new factory for Jedis connections.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     * @param clientConfig the client configuration
     */
    public GlideJedisFactory(String host, int port, JedisClientConfig clientConfig) {
        this.host = host;
        this.port = port;
        this.clientConfig = clientConfig;
        this.pool = null; // Will be set later by JedisPool
    }

    /**
     * Set the pool reference after factory creation. This is called by JedisPool after the factory is
     * created but before it's used.
     *
     * @param pool the JedisPool that owns this factory
     */
    public void setPool(JedisPool pool) {
        this.pool = pool;
    }

    @Override
    public PooledObject<Jedis> makeObject() throws Exception {
        try {
            // Map Jedis configuration to GLIDE configuration
            GlideClientConfiguration glideConfig =
                    ConfigurationMapper.mapToGlideConfig(host, port, clientConfig);

            // Create GLIDE client
            GlideClient glideClient = GlideClient.createClient(glideConfig).get();

            // Use the direct constructor following original Jedis pattern
            // This constructor:
            // - Takes GlideClient directly (no lazy initialization needed)
            // - Sets isPooled = true
            // - Sets lazyInitialized = true
            // - Registers the resource immediately
            Jedis jedis = new Jedis(glideClient, clientConfig);

            // Set the pool reference following original Jedis pattern
            if (pool != null) {
                jedis.setDataSource(pool);
            }

            return new DefaultPooledObject<>(jedis);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("Failed to create Jedis connection", e);
        }
    }

    @Override
    public void destroyObject(PooledObject<Jedis> pooledObject) throws Exception {
        Jedis jedis = pooledObject.getObject();
        if (jedis != null && !jedis.isClosed()) {
            try {
                jedis.close();
            } catch (Exception e) {
                // Log error but don't throw - we're in cleanup mode
                Logger.log(
                        Logger.Level.WARN,
                        "GlideJedisFactory",
                        "Error closing Jedis connection during destroy",
                        e);
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean validateObject(PooledObject<Jedis> pooledObject) {
        Jedis jedis = pooledObject.getObject();
        if (jedis == null || jedis.isClosed()) {
            return false;
        }

        try {
            // Use ping to validate connection
            String response = jedis.ping();
            return "PONG".equals(response);
        } catch (Exception e) {
            // Connection is not valid
            return false;
        }
    }

    @Override
    public void activateObject(PooledObject<Jedis> pooledObject) throws Exception {
        // Reset any state when borrowing from pool
        Jedis jedis = pooledObject.getObject();
        if (jedis != null) {
            jedis.resetForReuse();
        }
    }

    @Override
    public void passivateObject(PooledObject<Jedis> pooledObject) throws Exception {
        // Clean up state when returning to pool
        Jedis jedis = pooledObject.getObject();
        if (jedis != null) {
            // Reset any transaction or pipeline state
            jedis.resetForReuse();
        }
    }

    /**
     * Get the host this factory connects to.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Get the port this factory connects to.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the client configuration used by this factory.
     *
     * @return the client configuration
     */
    public JedisClientConfig getClientConfig() {
        return clientConfig;
    }

    /**
     * Get the pool reference.
     *
     * @return the pool reference, or null if not set
     */
    public JedisPool getPool() {
        return pool;
    }
}
