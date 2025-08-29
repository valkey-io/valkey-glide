/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.Set;

/** Connection provider for Valkey Cluster. This is part of the Jedis compatibility layer. */
public class ClusterConnectionProvider implements ConnectionProvider {

    private final Set<HostAndPort> nodes;
    private final JedisClientConfig clientConfig;

    public ClusterConnectionProvider(Set<HostAndPort> nodes, JedisClientConfig clientConfig) {
        this.nodes = nodes;
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

    /** Get all configured cluster nodes */
    public Set<HostAndPort> getNodes() {
        return nodes;
    }

    @Override
    public void close() {
        // Implementation for closing cluster connections
        // No real connection to close since GLIDE manages them internally
    }
}
