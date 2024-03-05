/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.concurrent.CompletableFuture;

/**
 * Connection Management Commands interface for cluster client.
 *
 * @see <a href="https://redis.io/commands/?group=connection">Connection Management Commands</a>
 */
public interface ConnectionManagementClusterCommands {

    /**
     * Ping the Redis server. The command will be routed to all primaries.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return <code>String</code> with <code>"PONG"</code>.
     */
    CompletableFuture<String> ping();

    /**
     * Ping the Redis server. The command will be routed to all primaries.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param message The server will respond with a copy of the message.
     * @return <code>String</code> with a copy of the argument <code>message</code>.
     */
    CompletableFuture<String> ping(String message);

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
     * @return <code>String</code> with <code>"PONG"</code>.
     */
    CompletableFuture<String> ping(Route route);

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param message The ping argument that will be returned.
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
     * @return <code>String</code> with a copy of the argument <code>message</code>.
     */
    CompletableFuture<String> ping(String message, Route route);

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
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
     * @return A {@link ClusterValue} which holds a single value if single node route is used or a
     *     dictionary where each address is the key and its corresponding node response is the value.
     *     The value is the id of the client on that node.
     * @example
     *     <pre>{@code
     * long id = client.clientId(new SlotIdRoute(...)).get().getSingleValue();
     * assert id > 0;
     *
     * Map<String, Long> idPerNode = client.clientId(ALL_NODES).get().getMultiValue();
     * assert idPerNode.get("<node 1 address>") > 0;
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
     * @param route Routing configuration for the command. Client will route the command to the nodes
     *     defined.
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
     * assert clientNamePerNode.get("<node 1 address>") != null
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clientGetName(Route route);
}
