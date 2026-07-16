/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import redis.clients.jedis.exceptions.JedisException;

/** Connection provider for Valkey Cluster. This is part of the Jedis compatibility layer. */
public class ClusterConnectionProvider implements ClusterNodesConnectionProvider {

    private final Set<HostAndPort> nodes;
    private final JedisClientConfig clientConfig;

    public ClusterConnectionProvider(Set<HostAndPort> nodes, JedisClientConfig clientConfig) {
        this.nodes = Objects.requireNonNull(nodes, "nodes");
        if (this.nodes.isEmpty()) {
            throw new JedisException("Cluster nodes must not be empty");
        }
        this.clientConfig = clientConfig;
    }

    @Override
    public Connection getConnection() {
        // Return connection to first node for compatibility
        HostAndPort firstNode = nodes.iterator().next();
        return new Connection(firstNode);
    }

    @Override
    public JedisClientConfig getClientConfig() {
        return clientConfig;
    }

    /** Get all configured cluster nodes (defensive copy). */
    public Set<HostAndPort> getNodes() {
        return Collections.unmodifiableSet(new HashSet<>(nodes));
    }

    /**
     * Returns a map of {@code host:port} to a lightweight per-node {@link ConnectionPool} view. Each
     * pool's {@link ConnectionPool#getResource()} returns a new {@link Connection} for that node;
     * GLIDE still owns real connections.
     */
    public Map<String, ConnectionPool> getClusterNodes() {
        Map<String, ConnectionPool> map = new LinkedHashMap<>();
        for (HostAndPort n : nodes) {
            map.put(n.getHost() + ":" + n.getPort(), new ConnectionPool(n));
        }
        return Collections.unmodifiableMap(map);
    }

    @Override
    public void close() {
        // Implementation for closing cluster connections
        // No real connection to close since GLIDE manages them internally
    }
}
