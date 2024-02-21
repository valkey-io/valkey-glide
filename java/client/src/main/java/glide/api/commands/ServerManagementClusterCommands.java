/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Server Management Commands interface for cluster client.
 *
 * @see <a href="https://redis.io/commands/?group=server">Server Management Commands</a>
 */
public interface ServerManagementClusterCommands {

    /**
     * Get information and statistics about the Redis server using the {@link Section#DEFAULT} option.
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return Response from Redis cluster with a <code>Map{@literal <String, String>}</code> with
     *     each address as the key and its corresponding value is the information for the node.
     * @example
     *     <p><code>
     *     {@literal Map<String, String>} routedInfoResult = clusterClient.info().get().getMultiValue();
     *     </code>
     */
    CompletableFuture<ClusterValue<String>> info();

    /**
     * Get information and statistics about the Redis server. If no argument is provided, so the
     * {@link Section#DEFAULT} option is assumed.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
     * @return Response from Redis cluster with a <code>String</code> with the requested Sections.
     *     When specifying a <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String>}</code> with each address as the key and its corresponding
     *     value is the information for the node.
     */
    CompletableFuture<ClusterValue<String>> info(Route route);

    /**
     * Get information and statistics about the Redis server. The command will be routed to all
     * primary nodes.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @return Response from Redis cluster with a <code>Map{@literal <String, String>}</code> with
     *     each address as the key and its corresponding value is the information of the sections
     *     requested for the node.
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions options);

    /**
     * Get information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link InfoOptions.Section} values specifying which sections of
     *     information to retrieve. When no parameter is provided, the {@link
     *     InfoOptions.Section#DEFAULT} option is assumed.
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
     * @return Response from Redis cluster with a <code>String</code> with the requested sections.
     *     When specifying a <code>route</code> other than a single node, it returns a <code>
     *     Map{@literal <String, String>}</code> with each address as the key and its corresponding
     *     value is the information of the sections requested for the node.
     */
    CompletableFuture<ClusterValue<String>> info(InfoOptions options, Route route);

    /**
     * Rewrites the configuration file with the current configuration.<br>
     * The command will be routed automatically to all nodes.
     *
     * @see <a href="https://redis.io/commands/config-rewrite/">redis.io</a> for details.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     raised.
     * @example
     *     <pre>
     * String response = client.configRewrite().get();
     * assert response.equals("OK")
     * </pre>
     */
    CompletableFuture<String> configRewrite();

    /**
     * Rewrites the configuration file with the current configuration.
     *
     * @see <a href="https://redis.io/commands/config-rewrite/">redis.io</a> for details.
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
     * @return <code>OK</code> when the configuration was rewritten properly, otherwise an error is
     *     raised.
     * @example
     *     <pre>
     * String response = client.configRewrite(ALL_PRIMARIES).get();
     * assert response.equals("OK")
     * </pre>
     */
    CompletableFuture<String> configRewrite(Route route);

    /**
     * Resets the statistics reported by Redis using the <a
     * href="https://redis.io/commands/info/">INFO</a> and <a
     * href="https://redis.io/commands/latency-histogram/">LATENCY HISTOGRAM </a> commands.<br>
     * The command will be routed automatically to all nodes.
     *
     * @see <a href="https://redis.io/commands/config-resetstat/">redis.io</a> for details.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>
     * String response = client.configResetStat().get();
     * assert response.equals("OK")
     * </pre>
     */
    CompletableFuture<String> configResetStat();

    /**
     * Resets the statistics reported by Redis using the <a
     * href="https://redis.io/commands/info/">INFO</a> and <a
     * href="https://redis.io/commands/latency-histogram/">LATENCY HISTOGRAM </a> commands.
     *
     * @see <a href="https://redis.io/commands/config-resetstat/">redis.io</a> for details.
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
     * @return <code>OK</code> to confirm that the statistics were successfully reset.
     * @example
     *     <pre>
     * String response = client.configResetStat(ALL_PRIMARIES).get();
     * assert response.equals("OK")
     * </pre>
     */
    CompletableFuture<String> configResetStat(Route route);
}
