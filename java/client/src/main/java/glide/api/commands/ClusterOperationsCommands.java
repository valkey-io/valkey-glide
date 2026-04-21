/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.cluster.ClusterFailoverOptions;
import glide.api.models.commands.cluster.ClusterResetOptions;
import glide.api.models.commands.cluster.ClusterSetSlotOptions;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/**
 * Supports commands for cluster operational control including failover, slot management, and epoch
 * operations.
 *
 * @see <a href="https://valkey.io/commands/?group=cluster">Cluster Commands</a>
 */
public interface ClusterOperationsCommands {

    /**
     * Initiates a manual failover of the primary node to one of its replicas. This command must be
     * sent to a replica node. The replica will attempt to perform a failover, becoming the new
     * primary.<br>
     * The command must be sent to a replica node.
     *
     * @see <a href="https://valkey.io/commands/cluster-failover/">valkey.io</a> for details.
     * @return <code>OK</code> if the failover was successfully initiated.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterFailover().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterFailover();

    /**
     * Initiates a manual failover of the primary node to one of its replicas with specified options.
     * This command must be sent to a replica node. The replica will attempt to perform a failover,
     * becoming the new primary.
     *
     * @see <a href="https://valkey.io/commands/cluster-failover/">valkey.io</a> for details.
     * @param options The failover options specifying the type of failover to perform (FORCE or
     *     TAKEOVER).
     * @return <code>OK</code> if the failover was successfully initiated.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterFailover(ClusterFailoverOptions.FORCE).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterFailover(@NonNull ClusterFailoverOptions options);

    /**
     * Manages the assignment of hash slots to nodes in the cluster. This command is used during
     * cluster reconfiguration to migrate slots between nodes. The specific action depends on the
     * provided options (IMPORTING, MIGRATING, STABLE, or NODE).
     *
     * @see <a href="https://valkey.io/commands/cluster-setslot/">valkey.io</a> for details.
     * @param slot The hash slot number to manage (0-16383).
     * @param options The options specifying the action to perform on the slot.
     * @return <code>OK</code> if the slot assignment was successfully updated.
     * @example
     *     <pre>{@code
     * // Mark slot 1234 as migrating to node with given ID
     * String result = clusterClient.clusterSetSlot(
     *     1234, ClusterSetSlotOptions.migrating("1234567890abcdef1234567890abcdef12345678")).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterSetSlot(long slot, @NonNull ClusterSetSlotOptions options);

    /**
     * Forces a node to increment its configuration epoch. This is an advanced command that should be
     * used with caution. It is primarily used during cluster reconfiguration scenarios to force epoch
     * increments.
     *
     * @see <a href="https://valkey.io/commands/cluster-bumpepoch/">valkey.io</a> for details.
     * @return <code>BUMPED</code> if the epoch was successfully incremented, or <code>STILL</code> if
     *     the current configuration epoch is already the greatest in the cluster.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterBumpEpoch().get();
     * System.out.println("Epoch bump result: " + result);
     * }</pre>
     */
    CompletableFuture<String> clusterBumpEpoch();

    /**
     * Sets the configuration epoch for a node. This is an advanced command used during cluster
     * reconfiguration. The configuration epoch is a version number for the cluster configuration, and
     * this command allows explicitly setting it.
     *
     * <p><b>Warning:</b> Misuse of this command can lead to cluster inconsistencies. Use with caution
     * and only during controlled reconfiguration scenarios.
     *
     * @see <a href="https://valkey.io/commands/cluster-set-config-epoch/">valkey.io</a> for details.
     * @param configEpoch The configuration epoch value to set.
     * @return <code>OK</code> if the configuration epoch was successfully set.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterSetConfigEpoch(123).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterSetConfigEpoch(long configEpoch);

    /**
     * Clears the node's hash slot ownership information. This command removes all slots assigned to
     * the node, effectively clearing its slot assignment cache. This is typically used during cluster
     * reconfiguration or resharding.
     *
     * <p><b>Warning:</b> This is a disruptive operation that clears the node's slot assignments. The
     * node will no longer claim ownership of any slots until they are reassigned.
     *
     * @see <a href="https://valkey.io/commands/cluster-flushslots/">valkey.io</a> for details.
     * @return <code>OK</code> if the slots were successfully flushed.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterFlushSlots().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterFlushSlots();

    /**
     * Resets a cluster node, clearing its cluster configuration and state. By default, performs a
     * soft reset.<br>
     * The command must be sent to the node to reset.
     *
     * <p><b>Warning:</b> This is a destructive operation. A soft reset will clear cluster state but
     * preserve data. Use with caution.
     *
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @return <code>OK</code> if the node was successfully reset.
     * @example
     *     <pre>{@code
     * String result = clusterClient.clusterReset().get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterReset();

    /**
     * Resets a cluster node with specified options, clearing its cluster configuration and state.<br>
     * The command must be sent to the node to reset.
     *
     * <p><b>Warning:</b> This is a destructive operation. A soft reset clears cluster state but
     * preserves data, while a hard reset clears both cluster state and all data. Use with extreme
     * caution, especially with HARD option.
     *
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @param options The reset options specifying the type of reset (SOFT or HARD).
     * @return <code>OK</code> if the node was successfully reset.
     * @example
     *     <pre>{@code
     * // Perform a soft reset (preserves data)
     * String result = clusterClient.clusterReset(ClusterResetOptions.SOFT).get();
     * assert result.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clusterReset(@NonNull ClusterResetOptions options);
}
