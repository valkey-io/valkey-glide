/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.ClientPauseMode;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Connection Management" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/?group=connection">Connection Management Commands</a>
 */
public interface ConnectionManagementClusterCommands {

    /**
     * Pings the server.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @return <code>String</code> with <code>"PONG"</code>.
     * @example
     *     <pre>{@code
     * String payload = clusterClient.ping().get();
     * assert payload.equals("PONG");
     * }</pre>
     */
    CompletableFuture<String> ping();

    /**
     * Pings the server.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The server will respond with a copy of the message.
     * @return <code>String</code> with a copy of the argument <code>message</code>.
     * @example
     *     <pre>{@code
     * String payload = clusterClient.ping("GLIDE").get();
     * assert payload.equals("GLIDE");
     * }</pre>
     */
    CompletableFuture<String> ping(String message);

    /**
     * Pings the server.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The server will respond with a copy of the message.
     * @return <code>GlideString</code> with a copy of the argument <code>message</code>.
     * @example
     *     <pre>{@code
     * GlideString payload = clusterClient.ping(gs("GLIDE")).get();
     * assert payload.equals(gs("GLIDE"));
     * }</pre>
     */
    CompletableFuture<GlideString> ping(GlideString message);

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>String</code> with <code>"PONG"</code>.
     * @example
     *     <pre>{@code
     * String payload = clusterClient.ping(ALL_NODES).get();
     * assert payload.equals("PONG");
     * }</pre>
     */
    CompletableFuture<String> ping(Route route);

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The ping argument that will be returned.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>String</code> with a copy of the argument <code>message</code>.
     * @example
     *     <pre>{@code
     * String payload = clusterClient.ping("GLIDE", RANDOM).get();
     * assert payload.equals("GLIDE");
     * }</pre>
     */
    CompletableFuture<String> ping(String message, Route route);

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The ping argument that will be returned.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>GlideString</code> with a copy of the argument <code>message</code>.
     * @example
     *     <pre>{@code
     * GlideString payload = clusterClient.ping(gs("GLIDE"), RANDOM).get();
     * assert payload.equals(gs("GLIDE"));
     * }</pre>
     */
    CompletableFuture<GlideString> ping(GlideString message, Route route);

    /**
     * Gets the current connection id.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/client-id/">valkey.io</a> for details.
     * @return The id of the client.
     * @example
     *     <pre>{@code
     * long id = client.clientId().get();
     * assert id > 0
     * }</pre>
     */
    CompletableFuture<Long> clientId();

    /**
     * Gets the current connection id.
     *
     * @see <a href="https://valkey.io/commands/client-id/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A {@link ClusterValue} which holds a single value if single node route is used or a
     *     dictionary where each address is the key and its corresponding node response is the value.
     *     The value is the id of the client on that node.
     * @example
     *     <pre>{@code
     * long id = client.clientId(new SlotIdRoute(...)).get().getSingleValue();
     * assert id > 0;
     *
     * Map<String, Long> idPerNode = client.clientId(ALL_NODES).get().getMultiValue();
     * assert idPerNode.get("node1.example.com:6379") > 0;
     * </pre>
     */
    CompletableFuture<ClusterValue<Long>> clientId(Route route);

    /**
     * Gets the name of the current connection.<br>
     * The command will be routed a random node.
     *
     * @see <a href="https://valkey.io/commands/client-getname/">valkey.io</a> for details.
     * @return The name of the client connection as a string if a name is set, or <code>null</code> if
     *     no name is assigned.
     * @example
     *     <pre>{@code
     * String clientName = client.clientGetName().get();
     * assert clientName != null;
     * }</pre>
     */
    CompletableFuture<String> clientGetName();

    /**
     * Gets the name of the current connection.
     *
     * @see <a href="https://valkey.io/commands/client-getname/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A {@link ClusterValue} which holds a single value if single node route is used or a
     *     dictionary where each address is the key and its corresponding node response is the value.
     *     The value is the name of the client connection as a string if a name is set, or null if no
     *     name is assigned.
     * @example
     *     <pre>{@code
     * String clientName = client.clientGetName(new SlotIdRoute(...)).get().getSingleValue();
     * assert clientName != null;
     *
     * Map<String, String> clientNamePerNode = client.clientGetName(ALL_NODES).get().getMultiValue();
     * assert clientNamePerNode.get("node1.example.com:6379") != null;
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clientGetName(Route route);

    /**
     * Echoes the provided <code>message</code> back.<br>
     * The command will be routed a random node.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to be echoed back.
     * @return The provided <code>message</code>.
     * @example
     *     <pre>{@code
     * String payload = client.echo("GLIDE").get();
     * assert payload.equals("GLIDE");
     * }</pre>
     */
    CompletableFuture<String> echo(String message);

    /**
     * Echoes the provided <code>message</code> back.<br>
     * The command will be routed a random node.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to be echoed back.
     * @return The provided <code>message</code>.
     * @example
     *     <pre>{@code
     * GlideString payload = client.echo(gs("GLIDE")).get();
     * assert payload.equals(gs("GLIDE"));
     * }</pre>
     */
    CompletableFuture<GlideString> echo(GlideString message);

    /**
     * Echoes the provided <code>message</code> back.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to be echoed back.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The provided <code>message</code>.
     * @example
     *     <pre>{@code
     * // Command sent to a single random node via RANDOM route, expecting a SingleValue result.
     * String message = client.echo("GLIDE", RANDOM).get().getSingleValue();
     * assert message.equals("GLIDE");
     *
     * // Command sent to all nodes via ALL_NODES route, expecting a MultiValue result.
     * Map<String, String> msgForAllNodes = client.echo("GLIDE", ALL_NODES).get().getMultiValue();
     * for(var msgPerNode : msgForAllNodes.entrySet()) {
     *     assert msgPerNode.equals("GLIDE");
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> echo(String message, Route route);

    /**
     * Echoes the provided <code>message</code> back.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to be echoed back.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The provided <code>message</code>.
     * @example
     *     <pre>{@code
     * // Command sent to a single random node via RANDOM route, expecting a SingleValue result.
     * GlideString message = client.echo(gs("GLIDE"), RANDOM).get().getSingleValue();
     * assert message.equals(gs("GLIDE"));
     *
     * // Command sent to all nodes via ALL_NODES route, expecting a MultiValue result.
     * Map<String, GlideString> msgForAllNodes = client.echo(gs("GLIDE"), ALL_NODES).get().getMultiValue();
     * for(var msgPerNode : msgForAllNodes.entrySet()) {
     *     assert msgPerNode.equals(gs("GLIDE"));
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<GlideString>> echo(GlideString message, Route route);

    /**
     * Changes the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/select/">valkey.io</a> for details.
     * @param index The index of the database to select.
     * @return A simple <code>OK</code> response.
     * @example
     *     <pre>{@code
     * String response = regularClient.select(0).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> select(long index);

    /**
     * Suspends all clients for the specified timeout.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/client-pause/">valkey.io</a> for details.
     * @param timeout The time in milliseconds to pause clients.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientPause(1000).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientPause(long timeout);

    /**
     * Suspends all clients for the specified timeout.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/client-pause/">valkey.io</a> for details.
     * @param timeout The time in milliseconds to pause clients.
     * @param mode The pause mode to use.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientPause(1000, ClientPauseMode.WRITE).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientPause(long timeout, ClientPauseMode mode);

    /**
     * Suspends all clients for the specified timeout.<br>
     * The command will be routed to the nodes defined by <code>route</code>.
     *
     * @see <a href="https://valkey.io/commands/client-pause/">valkey.io</a> for details.
     * @param timeout The time in milliseconds to pause clients.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientPause(1000, ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientPause(long timeout, Route route);

    /**
     * Suspends all clients for the specified timeout.<br>
     * The command will be routed to the nodes defined by <code>route</code>.
     *
     * @see <a href="https://valkey.io/commands/client-pause/">valkey.io</a> for details.
     * @param timeout The time in milliseconds to pause clients.
     * @param mode The pause mode to use.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientPause(1000, ClientPauseMode.WRITE, ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientPause(long timeout, ClientPauseMode mode, Route route);

    /**
     * Resumes processing commands on all clients.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/client-unpause/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientUnpause().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientUnpause();

    /**
     * Resumes processing commands on all clients.<br>
     * The command will be routed to the nodes defined by <code>route</code>.
     *
     * @see <a href="https://valkey.io/commands/client-unpause/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientUnpause(ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientUnpause(Route route);

    /**
     * Resets the connection state.
     *
     * @see <a href="https://valkey.io/commands/reset/">valkey.io</a> for details.
     * @return <code>String</code> with <code>"RESET"</code>.
     * @example
     *     <pre>{@code
     * String payload = client.reset().get();
     * assert payload.equals("RESET");
     * }</pre>
     */
    CompletableFuture<String> reset();

    // TODO #6144: Move to a shared ConnectionManagementBaseCommands interface once created

    /**
     * Returns information about the current client connection's use of the server assisted client
     * side caching feature.
     *
     * <p>Routes to a random node by default. To specify routing, use {@link
     * #clientTrackingInfo(Route)}.
     *
     * @see <a href="https://valkey.io/commands/client-trackinginfo/">valkey.io</a> for details.
     * @return A {@link Map} with the client's tracking state. The map contains:
     *     <ul>
     *       <li>{@code flags}: a {@link java.util.Set} of tracking flags. See <a
     *           href="https://valkey.io/commands/client-trackinginfo/">valkey.io</a> for the full
     *           list.
     *       <li>{@code redirect}: a {@link Long} with the client ID receiving invalidation messages,
     *           or {@code -1} if not redirecting
     *       <li>{@code prefixes}: an {@code Object[]} of key prefixes monitored for invalidation
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * // Tracking off (default):
     * Map<String, Object> info = client.clientTrackingInfo().get();
     * Set<String> flags = (Set<String>) info.get("flags");     // e.g. {"off"}
     * Long redirect = (Long) info.get("redirect");              // e.g. -1L
     * Object[] prefixes = (Object[]) info.get("prefixes");     // e.g. []
     * // Tracking on with prefix:
     * // {"flags": {"on", "noloop"}, "redirect": -1L, "prefixes": ["key:"]}
     * for (Object prefix : (Object[]) info.get("prefixes")) {
     *     System.out.println((String) prefix);
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>> clientTrackingInfo();

    /**
     * Returns information about the current client connection's use of the server assisted client
     * side caching feature.
     *
     * @see <a href="https://valkey.io/commands/client-trackinginfo/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command.
     * @return A {@link glide.api.models.ClusterValue} containing the tracking state map per the
     *     routing.
     *     <ul>
     *       <li>For a {@link
     *           glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute}: a single
     *           {@link Map} where:
     *           <ul>
     *             <li>{@code flags}: a {@link java.util.Set} of tracking flags. See <a
     *                 href="https://valkey.io/commands/client-trackinginfo/">valkey.io</a> for the
     *                 full list.
     *             <li>{@code redirect}: a {@link Long} with the client ID receiving invalidation
     *                 messages, or {@code -1} if not redirecting
     *             <li>{@code prefixes}: an {@code Object[]} of key prefixes monitored for
     *                 invalidation
     *           </ul>
     *       <li>For a multi-node route: a {@link Map} of node address to tracking state map.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * // Single node:
     * Map<String, Object> info = client.clientTrackingInfo(RANDOM).get().getSingleValue();
     * Set<String> flags = (Set<String>) info.get("flags");   // e.g. {"off"}
     * Long redirect = (Long) info.get("redirect");            // e.g. -1
     * Object[] prefixes = (Object[]) info.get("prefixes");   // e.g. []
     * for (Object prefix : (Object[]) info.get("prefixes")) {
     *     System.out.println((String) prefix);
     * }
     *
     * // Multi-node:
     * Map<String, Map<String, Object>> allNodes = client.clientTrackingInfo(ALL_NODES).get().getMultiValue();
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>>> clientTrackingInfo(Route route);
}
