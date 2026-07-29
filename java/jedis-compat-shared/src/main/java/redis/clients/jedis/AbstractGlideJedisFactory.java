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
import redis.clients.jedis.util.Pool;

/**
 * Abstract base factory for creating and managing {@link AbstractGlideJedis} instances backed by
 * GLIDE clients in a connection pool.
 *
 * <p>Concrete subclasses in each compatibility module ({@code jedis-4-compatibility} and {@code
 * jedis-compatibility}) implement {@link #createJedis} to instantiate the version-specific {@link
 * AbstractGlideJedis} subclass, and override {@link #isJedis5CompatibilityLayer()} to control the
 * protocol-enforcement flag passed to {@link ConfigurationMapper}.
 *
 * @param <J> the concrete Jedis type produced by this factory
 */
public abstract class AbstractGlideJedisFactory<J extends AbstractGlideJedis>
        implements PooledObjectFactory<J> {

    private final String host;
    private final int port;
    private final JedisClientConfig clientConfig;
    private volatile Pool<J> pool;

    protected AbstractGlideJedisFactory(String host, int port, JedisClientConfig clientConfig) {
        this.host = host;
        this.port = port;
        this.clientConfig = clientConfig;
        this.pool = null;
    }

    /** Returns {@code true} for the Jedis 5.x layer, {@code false} for the Jedis 4.x layer. */
    protected abstract boolean isJedis5CompatibilityLayer();

    /**
     * Instantiate the version-specific Jedis subclass wrapping the given GLIDE client.
     *
     * @param glideClient the connected GLIDE client
     * @param clientConfig the Jedis client configuration
     * @return a new Jedis instance
     */
    protected abstract J createJedis(GlideClient glideClient, JedisClientConfig clientConfig);

    /**
     * Set the pool reference. Called by the owning pool after factory creation and before {@code
     * initPool}.
     */
    public void setPool(Pool<J> pool) {
        this.pool = pool;
    }

    @Override
    public PooledObject<J> makeObject() throws Exception {
        try {
            GlideClientConfiguration glideConfig =
                    ConfigurationMapper.mapToGlideConfig(
                            host, port, clientConfig, isJedis5CompatibilityLayer());
            GlideClient glideClient = GlideClient.createClient(glideConfig).get();
            J jedis = createJedis(glideClient, clientConfig);
            if (pool != null) {
                jedis.setDataSource(pool);
            }
            return new DefaultPooledObject<>(jedis);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("Failed to create Jedis connection", e);
        }
    }

    @Override
    public void destroyObject(PooledObject<J> pooledObject) throws Exception {
        J jedis = pooledObject.getObject();
        if (jedis == null) {
            return;
        }
        jedis.detachFromPoolForDestroy();
        if (!jedis.isClosed()) {
            try {
                jedis.close();
            } catch (Exception e) {
                Logger.log(
                        Logger.Level.WARN,
                        "GlideJedisFactory",
                        "Error closing Jedis connection during destroy",
                        e);
            }
        }
    }

    @Override
    public boolean validateObject(PooledObject<J> pooledObject) {
        J jedis = pooledObject.getObject();
        if (jedis == null || jedis.isClosed() || jedis.isBroken()) {
            return false;
        }
        try {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void activateObject(PooledObject<J> pooledObject) throws Exception {
        J jedis = pooledObject.getObject();
        if (jedis != null) {
            jedis.resetForReuse();
        }
    }

    @Override
    public void passivateObject(PooledObject<J> pooledObject) throws Exception {
        J jedis = pooledObject.getObject();
        if (jedis != null) {
            jedis.resetForReuse();
        }
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public JedisClientConfig getClientConfig() {
        return clientConfig;
    }

    public Pool<J> getPool() {
        return pool;
    }
}
