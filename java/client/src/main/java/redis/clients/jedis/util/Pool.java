/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis.util;

import static glide.api.logging.Logger.Level.WARN;

import glide.api.logging.Logger;
import java.io.Closeable;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Base pool class for Jedis connections. This class provides the foundation for connection pooling
 * using Apache Commons Pool, matching the original Jedis Pool architecture.
 *
 * @param <T> the type of objects managed by this pool
 */
public abstract class Pool<T> implements Closeable {

    protected GenericObjectPool<T> internalPool;

    /**
     * Initialize the pool with a factory.
     *
     * @param pooledObjectFactory the factory for creating pooled objects
     */
    public void initPool(PooledObjectFactory<T> pooledObjectFactory) {
        initPool(new GenericObjectPoolConfig<T>(), pooledObjectFactory);
    }

    /**
     * Initialize the pool with configuration and factory.
     *
     * @param poolConfig the pool configuration
     * @param pooledObjectFactory the factory for creating pooled objects
     */
    public void initPool(
            GenericObjectPoolConfig<T> poolConfig, PooledObjectFactory<T> pooledObjectFactory) {
        if (this.internalPool != null) {
            try {
                closeInternalPool();
            } catch (Exception e) {
                // Log cleanup errors but continue with pool initialization
                Logger.log(
                        WARN,
                        "Pool",
                        () -> "Failed to close existing internal pool during initialization: " + e.getMessage(),
                        e);
            }
        }
        this.internalPool = new GenericObjectPool<>(pooledObjectFactory, poolConfig);
    }

    /**
     * Get a resource from the pool.
     *
     * @return a resource from the pool
     * @throws JedisException if unable to get a resource
     */
    public T getResource() {
        try {
            return internalPool.borrowObject();
        } catch (Exception e) {
            throw new JedisException("Could not get a resource from the pool", e);
        }
    }

    /**
     * Return a resource to the pool.
     *
     * @param resource the resource to return
     */
    protected void returnResourceObject(T resource) {
        if (resource == null) {
            return;
        }
        try {
            internalPool.returnObject(resource);
        } catch (Exception e) {
            throw new JedisException("Could not return the resource to the pool", e);
        }
    }

    /**
     * Return a resource to the pool (public method following original Jedis pattern).
     *
     * @param resource the resource to return
     */
    public void returnResource(T resource) {
        returnResourceObject(resource);
    }

    /**
     * Invalidate a resource in the pool.
     *
     * @param resource the resource to invalidate
     */
    protected void invalidateObject(T resource) {
        if (resource == null) {
            return;
        }
        try {
            internalPool.invalidateObject(resource);
        } catch (Exception e) {
            throw new JedisException("Could not invalidate the resource", e);
        }
    }

    /**
     * Get the number of active resources in the pool.
     *
     * @return number of active resources
     */
    public int getNumActive() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getNumActive();
    }

    /**
     * Get the number of idle resources in the pool.
     *
     * @return number of idle resources
     */
    public int getNumIdle() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getNumIdle();
    }

    /**
     * Get the number of threads waiting for resources.
     *
     * @return number of waiting threads
     */
    public int getNumWaiters() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getNumWaiters();
    }

    /**
     * Get the mean time threads wait for resources.
     *
     * @return mean wait time in milliseconds
     */
    public long getMeanBorrowWaitTimeMillis() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getMeanBorrowWaitTimeMillis();
    }

    /**
     * Get the maximum time threads have waited for resources.
     *
     * @return maximum wait time in milliseconds
     */
    public long getMaxBorrowWaitTimeMillis() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getMaxBorrowWaitTimeMillis();
    }

    /**
     * Get the total number of resources created by this pool.
     *
     * @return total created count
     */
    public long getCreatedCount() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getCreatedCount();
    }

    /**
     * Get the total number of resources destroyed by this pool.
     *
     * @return total destroyed count
     */
    public long getDestroyedCount() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getDestroyedCount();
    }

    /**
     * Get the maximum total number of resources in the pool.
     *
     * @return maximum total resources
     */
    public int getMaxTotal() {
        if (poolInactive()) {
            return -1;
        }
        return this.internalPool.getMaxTotal();
    }

    /**
     * Check if the pool is closed.
     *
     * @return true if closed, false otherwise
     */
    public boolean isClosed() {
        return this.internalPool.isClosed();
    }

    /** Add objects to the pool to reach the minimum idle count. */
    public void addObjects(int count) {
        try {
            for (int i = 0; i < count; i++) {
                this.internalPool.addObject();
            }
        } catch (Exception e) {
            throw new JedisException("Error trying to add objects to pool", e);
        }
    }

    /** Close the internal pool. */
    protected void closeInternalPool() {
        try {
            this.internalPool.close();
        } catch (Exception e) {
            throw new JedisException("Could not destroy the pool", e);
        }
    }

    /**
     * Check if the pool is inactive (null or closed).
     *
     * @return true if inactive, false otherwise
     */
    private boolean poolInactive() {
        return this.internalPool == null || this.internalPool.isClosed();
    }

    @Override
    public void close() {
        closeInternalPool();
    }
}
