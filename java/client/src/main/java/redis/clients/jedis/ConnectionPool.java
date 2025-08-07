/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

/**
 * ConnectionPool compatibility stub for Valkey GLIDE wrapper. This class exists only for
 * compilation compatibility.
 *
 * <p>NOTE: Connection pooling is not used in GLIDE compatibility layer. GLIDE handles connection
 * management internally.
 */
public abstract class ConnectionPool implements AutoCloseable {

    protected boolean closed = false;

    /** Stub method for compilation compatibility. */
    public abstract Connection getResource();

    /** Stub method for compilation compatibility. */
    public abstract void returnResource(Connection connection);

    /** Stub method for compilation compatibility. */
    public abstract void returnBrokenResource(Connection connection);

    @Override
    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed;
    }
}
