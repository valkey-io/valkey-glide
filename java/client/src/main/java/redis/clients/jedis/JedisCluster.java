/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * JedisCluster compatibility wrapper that extends UnifiedJedis for cluster operations. This class
 * provides the traditional JedisCluster API while using the unified interface underneath.
 */
public class JedisCluster extends UnifiedJedis {

    public static final String INIT_NO_ERROR_PROPERTY = "jedis.cluster.initNoError";

    /** Default timeout in milliseconds. */
    public static final int DEFAULT_TIMEOUT = 2000;

    /** Default amount of attempts for executing a command */
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    /** Store cluster nodes for getClusterNodes() method */
    private final Set<HostAndPort> clusterNodes;

    /**
     * Creates a JedisCluster instance. The provided node is used to make the first contact with the
     * cluster.
     *
     * <p>Here, the default timeout of {@value redis.clients.jedis.JedisCluster#DEFAULT_TIMEOUT} ms is
     * being used with {@value redis.clients.jedis.JedisCluster#DEFAULT_MAX_ATTEMPTS} maximum
     * attempts.
     *
     * @param node Node to first connect to.
     */
    public JedisCluster(HostAndPort node) {
        this(Collections.singleton(node));
    }

    /**
     * Creates a JedisCluster instance. The provided node is used to make the first contact with the
     * cluster.
     *
     * <p>Here, the default timeout of {@value redis.clients.jedis.JedisCluster#DEFAULT_TIMEOUT} ms is
     * being used with {@value redis.clients.jedis.JedisCluster#DEFAULT_MAX_ATTEMPTS} maximum
     * attempts.
     *
     * @param node Node to first connect to.
     * @param timeout connection and socket timeout in milliseconds.
     */
    public JedisCluster(HostAndPort node, int timeout) {
        this(Collections.singleton(node), timeout);
    }

    /**
     * Creates a JedisCluster instance. The provided node is used to make the first contact with the
     * cluster.<br>
     * You can specify the timeout and the maximum attempts.
     *
     * @param node Node to first connect to.
     * @param timeout connection and socket timeout in milliseconds.
     * @param maxAttempts maximum attempts for executing a command.
     */
    public JedisCluster(HostAndPort node, int timeout, int maxAttempts) {
        this(Collections.singleton(node), timeout, maxAttempts);
    }

    public JedisCluster(HostAndPort node, final GenericObjectPoolConfig<Connection> poolConfig) {
        this(Collections.singleton(node), poolConfig);
    }

    public JedisCluster(
            HostAndPort node, int timeout, final GenericObjectPoolConfig<Connection> poolConfig) {
        this(Collections.singleton(node), timeout, poolConfig);
    }

    public JedisCluster(
            HostAndPort node,
            int timeout,
            int maxAttempts,
            final GenericObjectPoolConfig<Connection> poolConfig) {
        this(Collections.singleton(node), timeout, maxAttempts, poolConfig);
    }

    public JedisCluster(
            HostAndPort node,
            final JedisClientConfig clientConfig,
            int maxAttempts,
            final GenericObjectPoolConfig<Connection> poolConfig) {
        this(Collections.singleton(node), clientConfig, maxAttempts);
    }

    /**
     * Creates a JedisCluster with multiple entry points.
     *
     * <p>Here, the default timeout of {@value redis.clients.jedis.JedisCluster#DEFAULT_TIMEOUT} ms is
     * being used with {@value redis.clients.jedis.JedisCluster#DEFAULT_MAX_ATTEMPTS} maximum
     * attempts.
     *
     * @param nodes Nodes to connect to.
     */
    public JedisCluster(Set<HostAndPort> nodes) {
        this(nodes, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a JedisCluster with multiple entry points.
     *
     * <p>Here, the default timeout of {@value redis.clients.jedis.JedisCluster#DEFAULT_TIMEOUT} ms is
     * being used with {@value redis.clients.jedis.JedisCluster#DEFAULT_MAX_ATTEMPTS} maximum
     * attempts.
     *
     * @param nodes Nodes to connect to.
     * @param timeout connection and socket timeout in milliseconds.
     */
    public JedisCluster(Set<HostAndPort> nodes, int timeout) {
        super(nodes, DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).build());
        this.clusterNodes = new HashSet<>(nodes);
    }

    /**
     * Creates a JedisCluster with multiple entry points.<br>
     * You can specify the timeout and the maximum attempts.
     *
     * @param nodes Nodes to connect to.
     * @param timeout connection and socket timeout in milliseconds.
     * @param maxAttempts maximum attempts for executing a command.
     */
    public JedisCluster(Set<HostAndPort> nodes, int timeout, int maxAttempts) {
        super(
                nodes,
                DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).build(),
                maxAttempts);
        this.clusterNodes = new HashSet<>(nodes);
    }

    public JedisCluster(
            Set<HostAndPort> nodes, final GenericObjectPoolConfig<Connection> poolConfig) {
        super(nodes, DefaultJedisClientConfig.builder().build());
        this.clusterNodes = new HashSet<>(nodes);
    }

    public JedisCluster(
            Set<HostAndPort> nodes, int timeout, final GenericObjectPoolConfig<Connection> poolConfig) {
        super(nodes, DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).build());
        this.clusterNodes = new HashSet<>(nodes);
    }

    public JedisCluster(
            Set<HostAndPort> nodes,
            int timeout,
            int maxAttempts,
            final GenericObjectPoolConfig<Connection> poolConfig) {
        super(
                nodes,
                DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).build(),
                maxAttempts);
        this.clusterNodes = new HashSet<>(nodes);
    }

    public JedisCluster(Set<HostAndPort> nodes, final JedisClientConfig clientConfig) {
        super(nodes, clientConfig);
        this.clusterNodes = new HashSet<>(nodes);
    }

    public JedisCluster(
            Set<HostAndPort> nodes, final JedisClientConfig clientConfig, int maxAttempts) {
        super(nodes, clientConfig, maxAttempts);
        this.clusterNodes = new HashSet<>(nodes);
    }

    /**
     * Returns all nodes that were configured to connect to in key-value pairs ({@link Map}).<br>
     * Key is the HOST:PORT and the value is the connection pool.
     *
     * @return the map of all connections.
     */
    public Map<String, ConnectionPool> getClusterNodes() {
        Map<String, ConnectionPool> nodeMap = new HashMap<>();
        for (HostAndPort node : clusterNodes) {
            String nodeKey = node.getHost() + ":" + node.getPort();
            // Return empty ConnectionPool for compatibility - GLIDE manages connections internally
            nodeMap.put(nodeKey, null);
        }
        return nodeMap;
    }

    /**
     * Returns the connection for one of the 16,384 slots.
     *
     * @param slot the slot to retrieve the connection for.
     * @return connection of the provided slot.
     */
    public Connection getConnectionFromSlot(int slot) {
        throw new UnsupportedOperationException(
                "getConnectionFromSlot is not supported in GLIDE compatibility layer");
    }
}
