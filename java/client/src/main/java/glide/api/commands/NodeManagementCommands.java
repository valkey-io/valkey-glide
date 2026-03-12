/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for managing cluster node membership and replication.
 *
 * @see <a href="https://valkey.io/commands/?group=cluster">Cluster Commands</a>
 */
public interface NodeManagementCommands {

    /**
     * Adds a new node to the cluster. This command is used to connect a new node to the cluster by
     * specifying the IP address and port of the node to add.<br>
     * The command will be routed to a random node in the cluster.
     *
     * @see <a href="https://valkey.io/commands/cluster-meet/">valkey.io</a> for details.
     * @param host The IP address or hostname of the node to add to the cluster.
     * @param port The port number of the node to add to the cluster.
     * @return <code>OK</code> if the node was successfully added to the cluster.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterMeet("192.168.1.100", 6379).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterMeet(String host, long port);

    /**
     * Adds a new node to the cluster. This command is used to connect a new node to the cluster by
     * specifying the IP address and port of the node to add.
     *
     * @see <a href="https://valkey.io/commands/cluster-meet/">valkey.io</a> for details.
     * @param host The IP address or hostname of the node to add to the cluster.
     * @param port The port number of the node to add to the cluster.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> if the node was successfully added to the cluster. When specifying a
     *     <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String>}</code> with each address as the key and its corresponding
     *     result.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = clusterClient.clusterMeet("192.168.1.100", 6379, ALL_PRIMARIES).get();
     * // Command sent to all primary nodes, expecting MultiValue result.
     * for (Map.Entry<String, String> entry : result.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]: " + entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterMeet(String host, long port, Route route);

    /**
     * Removes a node from the cluster. The node is specified by its ID. This command must be sent to
     * a node that is not the one being removed.<br>
     * The command will be routed to a random node in the cluster.
     *
     * <p><b>Warning:</b> This is a permanent operation. Once a node is forgotten, it must be re-added
     * to the cluster using {@link #clusterMeet(String, long)} if you wish to include it again.
     *
     * @see <a href="https://valkey.io/commands/cluster-forget/">valkey.io</a> for details.
     * @param nodeId The ID of the node to remove from the cluster. The node ID is a 40-character
     *     hexadecimal string.
     * @return <code>OK</code> if the node was successfully removed from the cluster.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterForget("1234567890abcdef1234567890abcdef12345678").get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterForget(String nodeId);

    /**
     * Configures the current node to replicate data from a primary node identified by <code>nodeId
     * </code>. This command reconfigures a node to become a replica of the specified primary.<br>
     * The command must be sent to the node that should become the replica.
     *
     * @see <a href="https://valkey.io/commands/cluster-replicate/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node to replicate. The node ID is a 40-character
     *     hexadecimal string.
     * @return <code>OK</code> if the node was successfully configured as a replica.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterReplicate("1234567890abcdef1234567890abcdef12345678").get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterReplicate(String nodeId);

    /**
     * Returns a list of replicas (slaves) for the specified primary node. Each line in the output
     * represents a replica and contains information similar to the <code>CLUSTER NODES</code> format.
     * <br>
     * The command will be routed to a random node in the cluster.
     *
     * @see <a href="https://valkey.io/commands/cluster-replicas/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node to get replicas for. The node ID is a 40-character
     *     hexadecimal string.
     * @return An array of strings, where each string contains information about a replica node.
     * @example
     *     <pre>{@code
     * String[] replicas = clusterClient.clusterReplicas("1234567890abcdef1234567890abcdef12345678").get();
     * for (String replica : replicas) {
     *     System.out.println("Replica: " + replica);
     * }
     * }</pre>
     */
    CompletableFuture<String[]> clusterReplicas(String nodeId);

    /**
     * Returns a list of replicas (slaves) for the specified primary node. Each line in the output
     * represents a replica and contains information similar to the <code>CLUSTER NODES</code> format.
     *
     * @see <a href="https://valkey.io/commands/cluster-replicas/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node to get replicas for. The node ID is a 40-character
     *     hexadecimal string.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return An array of strings, where each string contains information about a replica node. When
     *     specifying a <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String[]>}</code> with each address as the key and its corresponding
     *     result.
     * @example
     *     <pre>{@code
     * ClusterValue<String[]> replicas = clusterClient.clusterReplicas(
     *     "1234567890abcdef1234567890abcdef12345678", ALL_PRIMARIES).get();
     * // Command sent to all primary nodes, expecting MultiValue result.
     * for (Map.Entry<String, String[]> entry : replicas.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]:");
     *     for (String replica : entry.getValue()) {
     *         System.out.println("  Replica: " + replica);
     *     }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String[]>> clusterReplicas(String nodeId, Route route);

    /**
     * Returns the number of failure reports for the specified node. Failure reports are the way nodes
     * in the cluster signal that another node might be in a failure state.<br>
     * The command will be routed to a random node in the cluster.
     *
     * @see <a href="https://valkey.io/commands/cluster-count-failure-reports/">valkey.io</a> for
     *     details.
     * @param nodeId The ID of the node to get failure report count for. The node ID is a 40-character
     *     hexadecimal string.
     * @return The number of active failure reports for the specified node.
     * @example
     *     <pre>{@code
     * Long failureCount = clusterClient.clusterCountFailureReports(
     *     "1234567890abcdef1234567890abcdef12345678").get();
     * System.out.println("Failure reports: " + failureCount);
     * }</pre>
     */
    CompletableFuture<Long> clusterCountFailureReports(String nodeId);

    /**
     * Returns the number of failure reports for the specified node. Failure reports are the way nodes
     * in the cluster signal that another node might be in a failure state.
     *
     * @see <a href="https://valkey.io/commands/cluster-count-failure-reports/">valkey.io</a> for
     *     details.
     * @param nodeId The ID of the node to get failure report count for. The node ID is a 40-character
     *     hexadecimal string.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The number of active failure reports for the specified node. When specifying a <code>
     *     route</code> other than a single node, it returns a <code>Map{@literal <String, Long>}
     *     </code> with each address as the key and its corresponding result.
     * @example
     *     <pre>{@code
     * ClusterValue<Long> failureReports = clusterClient.clusterCountFailureReports(
     *     "1234567890abcdef1234567890abcdef12345678", ALL_NODES).get();
     * // Command sent to all nodes, expecting MultiValue result.
     * for (Map.Entry<String, Long> entry : failureReports.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]: " + entry.getValue() + " failure reports");
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Long>> clusterCountFailureReports(String nodeId, Route route);
}
