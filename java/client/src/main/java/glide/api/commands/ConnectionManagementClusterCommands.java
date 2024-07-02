/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Connection Management" group for a cluster client.
 *
 * @see <a href="https://redis.io/commands/?group=connection">Connection Management Commands</a>
 */
public interface ConnectionManagementClusterCommands {

    /**
     * Pings the Redis server.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return <code>String</code> with <code>"PONG"</code>.
     * @example
     *     <pre>{@code
     * String payload = clusterClient.ping().get();
     * assert payload.equals("PONG");
     * }</pre>
     */
    CompletableFuture<String> ping();

    /**
     * Pings the Redis server.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
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
     * Pings the Redis server.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
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
     * Pings the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
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
     * Pings the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
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
     * Pings the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/client-id/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/client-id/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/client-getname/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/client-getname/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/echo/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/echo/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/echo/">redis.io</a> for details.
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
     * @see <a href="https://redis.io/commands/echo/">redis.io</a> for details.
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
}
