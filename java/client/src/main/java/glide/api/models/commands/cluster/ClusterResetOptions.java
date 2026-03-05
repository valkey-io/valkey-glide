/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.cluster;

import glide.api.GlideClusterClient;

/**
 * Defines reset options for the <code>CLUSTER RESET</code> command implemented by {@link
 * GlideClusterClient#clusterReset(ClusterResetOptions)}.
 *
 * @see <a href="https://valkey.io/commands/cluster-reset/">cluster-reset</a> at valkey.io
 */
public enum ClusterResetOptions {
    /**
     * Performs a soft reset, clearing cluster configuration but preserving all data. The node will
     * forget its cluster membership but retain its datasets.
     */
    SOFT,
    /**
     * Performs a hard reset, clearing both cluster configuration and all data. This completely resets
     * the node as if it was freshly started. <b>Warning:</b> This will result in complete data loss.
     */
    HARD;

    // Pre-allocated argument arrays to reduce allocations
    private static final String[] SOFT_ARGS = new String[] {"SOFT"};
    private static final String[] HARD_ARGS = new String[] {"HARD"};

    /**
     * Returns the command arguments for this reset option. Uses pre-allocated arrays to reduce GC
     * pressure.
     *
     * @return Array containing the reset option as a string argument
     */
    public String[] toArgs() {
        return this == SOFT ? SOFT_ARGS : HARD_ARGS;
    }
}
