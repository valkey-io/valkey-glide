/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Transactions Commands" group for cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=transactions">Transactions Commands</a>
 */
public interface TransactionsBaseClusterCommands {
    /**
     * Flushes all the previously watched keys for a transaction. Executing a transaction will
     * automatically flush all previously watched keys.
     *
     * @see <a href="https://redis.io/docs/latest/commands/unwatch/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The string <code>OK</code>.
     * @example
     *     <pre>{@code
     * client.watch(new String[] {"sampleKey"});
     * client.unwatch(ALL_PRIMARIES); // Flushes "sampleKey" from watched keys for all primary nodes.
     * }</pre>
     */
    CompletableFuture<String> unwatch(Route route);
}
