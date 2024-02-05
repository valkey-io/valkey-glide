/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Server Management Commands interface.
 *
 * @see <a href="https://redis.io/commands/?group=server">Server Management Commands</a>
 */
public interface ServerManagementClusterCommands {

    /**
     * Get information and statistics about the Redis server. DEFAULT option is assumed
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details. {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @return Response from Redis cluster containing a <code>String</code>.
     */
    CompletableFuture<ClusterValue<String>> info();

    /**
     * Get information and statistics about the Redis server. DEFAULT option is assumed
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param route Routing configuration for the command
     * @return Response from Redis cluster containing a <code>String</code>.
     */
    CompletableFuture<ClusterValue<String>> info(Route route);

    /**
     * Get information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options - A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @return Response from Redis cluster containing a <code>String</code> with the requested
     *     Sections.
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions options);

    /**
     * Get information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options - A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @param route Routing configuration for the command
     * @return Response from Redis cluster containing a <code>String</code> with the requested
     *     Sections.
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions options, Route route);
}
