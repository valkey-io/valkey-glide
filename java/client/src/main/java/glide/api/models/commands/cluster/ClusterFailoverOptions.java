/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.cluster;

import glide.api.GlideClusterClient;

/**
 * Defines failover options for the <code>CLUSTER FAILOVER</code> command implemented by {@link
 * GlideClusterClient#clusterFailover(ClusterFailoverOptions)}.
 *
 * @see <a href="https://valkey.io/commands/cluster-failover/">cluster-failover</a> at valkey.io
 */
public enum ClusterFailoverOptions {
    /**
     * Forces a replica to perform a failover without coordination with the primary. This can result
     * in data loss but is useful when the primary is not reachable.
     */
    FORCE,
    /**
     * Forces a replica to become primary without any cluster consensus. This is the most aggressive
     * form of failover and should be used with extreme caution as it may lead to data loss and split
     * brain scenarios.
     */
    TAKEOVER;

    // Pre-allocated argument arrays to reduce allocations
    private static final String[] FORCE_ARGS = new String[] {"FORCE"};
    private static final String[] TAKEOVER_ARGS = new String[] {"TAKEOVER"};

    /**
     * Returns the command arguments for this failover option. Uses pre-allocated arrays to reduce GC
     * pressure.
     *
     * @return Array containing the failover option as a string argument
     */
    public String[] toArgs() {
        return this == FORCE ? FORCE_ARGS : TAKEOVER_ARGS;
    }
}
