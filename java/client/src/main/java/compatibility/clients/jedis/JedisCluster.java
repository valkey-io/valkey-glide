/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration;
import java.io.Closeable;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * JedisCluster compatibility wrapper for Valkey GLIDE cluster client. This class provides a
 * Jedis-like cluster API while using Valkey GLIDE underneath.
 */
public final class JedisCluster implements Closeable {

    private final GlideClusterClient glideClusterClient;
    private final JedisClientConfig config;
    private final String resourceId;
    private volatile boolean closed = false;

    /**
     * Create a new JedisCluster instance with a single node entry point.
     *
     * @param node the cluster node to connect to
     */
    public JedisCluster(HostAndPort node) {
        this(Set.of(node), DefaultJedisClientConfig.builder().build());
    }

    /**
     * Create a new JedisCluster instance with multiple node entry points.
     *
     * @param nodes the cluster nodes to connect to
     */
    public JedisCluster(Set<HostAndPort> nodes) {
        this(nodes, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Create a new JedisCluster instance with configuration.
     *
     * @param nodes the cluster nodes to connect to
     * @param config the client configuration
     */
    public JedisCluster(Set<HostAndPort> nodes, JedisClientConfig config) {
        this.config = config;

        // Validate configuration
        ClusterConfigurationMapper.validateClusterConfiguration(config);

        // Map Jedis cluster config to GLIDE cluster config
        GlideClusterClientConfiguration glideConfig =
                ClusterConfigurationMapper.mapToGlideClusterConfig(nodes, config);

        try {
            this.glideClusterClient = GlideClusterClient.createClient(glideConfig).get();
            this.resourceId = ResourceLifecycleManager.getInstance().registerResource(this);
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisConnectionException("Failed to create GLIDE cluster client", e);
        }
    }

    /**
     * Create JedisCluster with timeout.
     *
     * @param nodes the cluster nodes
     * @param timeout connection timeout in milliseconds
     */
    public JedisCluster(Set<HostAndPort> nodes, int timeout) {
        this(nodes, DefaultJedisClientConfig.builder().socketTimeoutMillis(timeout).build());
    }

    /**
     * Create JedisCluster with authentication.
     *
     * @param nodes the cluster nodes
     * @param user the username
     * @param password the password
     */
    public JedisCluster(Set<HostAndPort> nodes, String user, String password) {
        this(nodes, DefaultJedisClientConfig.builder().user(user).password(password).build());
    }

    // ========== Basic Redis Commands ==========

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
            return glideClusterClient.set(key, value).get();
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
            return glideClusterClient.get(key).get();
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
            return glideClusterClient.ping().get();
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
            return glideClusterClient.ping(message).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("PING operation failed", e);
        }
    }

    // ========== Cluster-Specific Commands ==========

    /**
     * Execute a command on a specific node by address.
     *
     * @param host the node host
     * @param port the node port
     * @param command the command to execute
     * @param args the command arguments
     * @return the command result
     */
    public Object executeOnNode(String host, int port, String command, String... args) {
        checkNotClosed();
        try {
            RequestRoutingConfiguration.ByAddressRoute route =
                    new RequestRoutingConfiguration.ByAddressRoute(host, port);

            String[] fullArgs = new String[args.length + 1];
            fullArgs[0] = command;
            System.arraycopy(args, 0, fullArgs, 1, args.length);

            ClusterValue<Object> result = glideClusterClient.customCommand(fullArgs, route).get();
            return result.getSingleValue();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("Command execution failed", e);
        }
    }

    /**
     * Execute a command on all primary nodes.
     *
     * @param command the command to execute
     * @param args the command arguments
     * @return map of node addresses to results
     */
    public Object executeOnAllPrimaries(String command, String... args) {
        checkNotClosed();
        try {
            RequestRoutingConfiguration.SimpleMultiNodeRoute route =
                    RequestRoutingConfiguration.SimpleMultiNodeRoute.ALL_PRIMARIES;

            String[] fullArgs = new String[args.length + 1];
            fullArgs[0] = command;
            System.arraycopy(args, 0, fullArgs, 1, args.length);

            ClusterValue<Object> result = glideClusterClient.customCommand(fullArgs, route).get();
            return result.getMultiValue();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("Command execution failed", e);
        }
    }

    /**
     * Get cluster information.
     *
     * @return cluster info string
     */
    public String clusterInfo() {
        checkNotClosed();
        try {
            ClusterValue<String> result = glideClusterClient.info().get();
            // Return info from any node (typically contains cluster info)
            return result.getSingleValue();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("CLUSTER INFO failed", e);
        }
    }

    /**
     * Get cluster nodes information.
     *
     * @return cluster nodes string
     */
    public String clusterNodes() {
        checkNotClosed();
        try {
            // Execute CLUSTER NODES on a random node
            ClusterValue<Object> result =
                    glideClusterClient
                            .customCommand(
                                    new String[] {"CLUSTER", "NODES"},
                                    RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM)
                            .get();
            return (String) result.getSingleValue();
        } catch (InterruptedException | ExecutionException e) {
            throw new JedisException("CLUSTER NODES failed", e);
        }
    }

    // ========== Connection Management ==========

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

    /** Close the cluster client. */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        ResourceLifecycleManager.getInstance().unregisterResource(resourceId);

        try {
            glideClusterClient.close();
        } catch (Exception e) {
            throw new JedisException("Failed to close GLIDE cluster client", e);
        }
    }

    /**
     * Get the underlying GLIDE cluster client for advanced operations.
     *
     * @return the GLIDE cluster client
     */
    protected GlideClusterClient getGlideClusterClient() {
        return glideClusterClient;
    }

    /** Check if the connection is not closed and throw exception if it is. */
    private void checkNotClosed() {
        if (closed) {
            throw new JedisException("Cluster connection is closed");
        }
    }
}
