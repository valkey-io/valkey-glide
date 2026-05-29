/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.Objects;
import redis.clients.jedis.util.Pool;

/**
 * ConnectionPool compatibility stub for Valkey GLIDE wrapper.
 *
 * @deprecated ConnectionPool is not supported in the GLIDE compatibility layer. Use JedisPool for
 *     connection pooling instead. See <a
 *     href="https://github.com/valkey-io/valkey-glide/blob/main/java/MIGRATION.md">Migration
 *     guide</a> for more details.
 */
@Deprecated
public class ConnectionPool extends Pool<Connection> {

    /**
     * When non-null, this instance is a lightweight per-node view used by {@link
     * ClusterConnectionProvider#getClusterNodes()}; it is not backed by Apache Commons Pool.
     */
    private HostAndPort clusterNodeStub;

    public ConnectionPool() {
        throw new UnsupportedOperationException(
                "ConnectionPool is not supported in GLIDE compatibility layer. GLIDE uses a different"
                        + " connection management architecture. Please use JedisPool for connection pooling"
                        + " instead. See migration guide:"
                        + " https://github.com/valkey-io/valkey-glide/blob/main/java/MIGRATION.md");
    }

    /**
     * Package-private: per-node {@link Connection} factory for cluster topology maps. Each {@link
     * #getResource()} returns a new {@link Connection} for the given node (close is a no-op on {@link
     * Connection}).
     */
    ConnectionPool(HostAndPort clusterNodeStub) {
        this.clusterNodeStub = Objects.requireNonNull(clusterNodeStub, "clusterNodeStub");
    }

    private boolean isClusterNodeStub() {
        return clusterNodeStub != null;
    }

    @Override
    public Connection getResource() {
        if (isClusterNodeStub()) {
            return new Connection(clusterNodeStub);
        }
        throw new UnsupportedOperationException(
                "ConnectionPool is not supported in GLIDE compatibility layer. Use JedisPool instead.");
    }

    @Override
    public void returnResource(Connection resource) {
        if (isClusterNodeStub()) {
            closeQuietly(resource);
            return;
        }
        super.returnResource(resource);
    }

    @Override
    public void returnBrokenResource(Connection resource) {
        if (isClusterNodeStub()) {
            closeQuietly(resource);
            return;
        }
        super.returnBrokenResource(resource);
    }

    @Override
    protected void returnResourceObject(Connection resource) {
        if (isClusterNodeStub()) {
            closeQuietly(resource);
            return;
        }
        super.returnResourceObject(resource);
    }

    @Override
    protected void invalidateObject(Connection resource) {
        if (isClusterNodeStub()) {
            closeQuietly(resource);
            return;
        }
        super.invalidateObject(resource);
    }

    @Override
    public boolean isClosed() {
        if (isClusterNodeStub()) {
            return false;
        }
        return super.isClosed();
    }

    @Override
    public void addObjects(int count) {
        if (isClusterNodeStub()) {
            return;
        }
        super.addObjects(count);
    }

    @Override
    public void close() {
        if (isClusterNodeStub()) {
            return;
        }
        super.close();
    }

    private static void closeQuietly(Connection resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception ignored) {
            // Connection.close() is a no-op in the compatibility layer
        }
    }
}
