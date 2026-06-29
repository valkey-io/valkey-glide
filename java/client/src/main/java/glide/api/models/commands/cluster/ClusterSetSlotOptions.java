/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.cluster;

import glide.api.GlideClusterClient;
import java.util.Objects;
import lombok.Getter;

/**
 * Defines slot assignment options for the <code>CLUSTER SETSLOT</code> command implemented by
 * {@link GlideClusterClient#clusterSetSlot(long, ClusterSetSlotOptions)}.
 *
 * @see <a href="https://valkey.io/commands/cluster-setslot/">cluster-setslot</a> at valkey.io
 */
@Getter
public class ClusterSetSlotOptions {

    /** The subcommand to execute for the slot. */
    private final SlotAction action;

    /**
     * The node ID (required for IMPORTING, MIGRATING, and NODE actions; null when action is STABLE).
     */
    private final String nodeId;

    private ClusterSetSlotOptions(SlotAction action, String nodeId) {
        this.action = action;
        this.nodeId = nodeId;
    }

    /**
     * Creates options to mark a slot as importing from the specified node.
     *
     * @param nodeId The ID of the node to import the slot from.
     * @return A ClusterSetSlotOptions instance configured for importing.
     */
    public static ClusterSetSlotOptions importing(String nodeId) {
        return new ClusterSetSlotOptions(
                SlotAction.IMPORTING, Objects.requireNonNull(nodeId, "nodeId"));
    }

    /**
     * Creates options to mark a slot as migrating to the specified node.
     *
     * @param nodeId The ID of the node to migrate the slot to.
     * @return A ClusterSetSlotOptions instance configured for migrating.
     */
    public static ClusterSetSlotOptions migrating(String nodeId) {
        return new ClusterSetSlotOptions(
                SlotAction.MIGRATING, Objects.requireNonNull(nodeId, "nodeId"));
    }

    /**
     * Creates options to clear the importing or migrating state of a slot and mark it as stable.
     *
     * @return A ClusterSetSlotOptions instance configured for stable state.
     */
    public static ClusterSetSlotOptions stable() {
        return new ClusterSetSlotOptions(SlotAction.STABLE, null);
    }

    /**
     * Creates options to assign a slot to the specified node.
     *
     * @param nodeId The ID of the node to assign the slot to.
     * @return A ClusterSetSlotOptions instance configured for node assignment.
     */
    public static ClusterSetSlotOptions node(String nodeId) {
        return new ClusterSetSlotOptions(SlotAction.NODE, Objects.requireNonNull(nodeId, "nodeId"));
    }

    /**
     * Converts the options to command arguments.
     *
     * @return Array of command arguments
     */
    public String[] toArgs() {
        if (action == SlotAction.STABLE) {
            return new String[] {"STABLE"};
        } else {
            return new String[] {action.toString(), nodeId};
        }
    }

    /** Defines the action to perform on the slot. */
    public enum SlotAction {
        /** Mark the slot as being imported from another node. */
        IMPORTING,
        /** Mark the slot as being migrated to another node. */
        MIGRATING,
        /** Clear the importing/migrating state and mark the slot as stable. */
        STABLE,
        /** Bind the slot to a specific node. */
        NODE
    }
}
