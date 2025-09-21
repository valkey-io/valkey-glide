/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;

// TODO add links to script flush
/**
 * Defines flushing mode for:
 *
 * <ul>
 *   <li><code>FLUSHALL</code> command implemented by {@link GlideClient#flushall(FlushMode)},
 *       {@link GlideClusterClient#flushall(FlushMode)}, and {@link
 *       GlideClusterClient#flushall(FlushMode, SingleNodeRoute)}.
 *   <li><code>FLUSHDB</code> command implemented by {@link GlideClient#flushdb(FlushMode)}, {@link
 *       GlideClusterClient#flushdb(FlushMode)}, and {@link GlideClusterClient#flushdb(FlushMode,
 *       SingleNodeRoute)}.
 *   <li><code>FUNCTION FLUSH</code> command implemented by {@link
 *       GlideClient#functionFlush(FlushMode)}, {@link GlideClusterClient#functionFlush(FlushMode)},
 *       and {@link GlideClusterClient#functionFlush(FlushMode, Route)}.
 *   <li><code>SCRIPT FLUSH</code> command implemented by {@link GlideClient#scriptFlush(FlushMode)}
 *       and {@link GlideClusterClient#scriptFlush(FlushMode, Route)}
 * </ul>
 *
 * @see <a href="https://valkey.io/commands/flushall/">flushall</a>, <a
 *     href="https://valkey.io/commands/flushdb/">flushdb</a>, and <a
 *     href="https://valkey.io/commands/function-flush/">function flush</a> at valkey.io
 */
public enum FlushMode {
    /**
     * Flushes synchronously.
     *
     * @since Valkey 6.2 and above.
     */
    SYNC,
    /** Flushes asynchronously. */
    ASYNC;

    // Pre-allocated argument arrays to reduce allocations
    private static final String[] SYNC_ARGS = new String[] {"SYNC"};
    private static final String[] ASYNC_ARGS = new String[] {"ASYNC"};

    /**
     * Returns the command arguments for this flush mode. Uses pre-allocated arrays to reduce GC
     * pressure.
     *
     * @return Array containing the flush mode as a string argument
     */
    public String[] toArgs() {
        return this == SYNC ? SYNC_ARGS : ASYNC_ARGS;
    }
}
