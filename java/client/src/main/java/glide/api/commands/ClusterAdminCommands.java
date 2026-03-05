/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for cluster administration including configuration persistence and key
 * inspection during slot migration.
 *
 * @see <a href="https://valkey.io/commands/?group=cluster">Cluster Commands</a>
 */
public interface ClusterAdminCommands {

    /**
     * Saves the cluster configuration to disk. This forces the node to persist its current cluster
     * configuration (including node mappings, slot assignments, etc.) to the cluster configuration
     * file on disk.<br>
     * The command will be routed to a random node in the cluster.
     *
     * @see <a href="https://valkey.io/commands/cluster-saveconfig/">valkey.io</a> for details.
     * @return <code>OK</code> if the configuration was successfully saved.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterSaveConfig().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterSaveConfig();

    /**
     * Saves the cluster configuration to disk. This forces the node(s) to persist their current
     * cluster configuration (including node mappings, slot assignments, etc.) to the cluster
     * configuration file on disk.
     *
     * @see <a href="https://valkey.io/commands/cluster-saveconfig/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> if the configuration was successfully saved. When specifying a <code>
     *     route</code> other than a single node, it returns a <code>Map{@literal <String, String>}
     *     </code> with each address as the key and its corresponding result.
     * @example
     *     <pre>{@code
     * ClusterValue<String> result = clusterClient.clusterSaveConfig(ALL_PRIMARIES).get();
     * // Command sent to all primary nodes, expecting MultiValue result.
     * for (Map.Entry<String, String> entry : result.getMultiValue().entrySet()) {
     *     System.out.println("Node [" + entry.getKey() + "]: " + entry.getValue());
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clusterSaveConfig(Route route);

    /**
     * Returns an array of keys stored in the specified hash slot. This command is useful for
     * inspecting the contents of a slot, particularly during slot migration or resharding operations.
     * <br>
     * The command will be routed to the node that owns the specified slot.
     *
     * @see <a href="https://valkey.io/commands/cluster-getkeysinslot/">valkey.io</a> for details.
     * @param slot The hash slot number to query (0-16383).
     * @param count The maximum number of keys to return from the slot.
     * @return An array of keys stored in the specified slot. The array may contain fewer keys than
     *     <code>count</code> if the slot has fewer keys.
     * @example
     *     <pre>{@code
     * // Get up to 10 keys from slot 1234
     * String[] keys = clusterClient.clusterGetKeysInSlot(1234, 10).get();
     * for (String key : keys) {
     *     System.out.println("Key in slot 1234: " + key);
     * }
     * }</pre>
     */
    CompletableFuture<String[]> clusterGetKeysInSlot(long slot, long count);
}
