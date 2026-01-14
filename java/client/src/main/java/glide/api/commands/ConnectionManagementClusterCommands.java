/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
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
     * Authenticates the connection with a password.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = clusterClient.auth("myPassword").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(String password);

    /**
     * Authenticates the connection with a username and password.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = clusterClient.auth("myUser", "myPassword").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(String username, String password);

    /**
     * Authenticates the connection with a password.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = clusterClient.auth(gs("myPassword")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(GlideString password);

    /**
     * Authenticates the connection with a username and password.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = clusterClient.auth(gs("myUser"), gs("myPassword")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(GlideString username, GlideString password);

    /**
     * Returns information about the client connection.<br>
     * The command will be routed to a random node.
     *
     * @see <a href="https://valkey.io/commands/client-info/">valkey.io</a> for details.
     * @return A string containing information about the client connection.
     * @example
     *     <pre>{@code
     * String info = clusterClient.clientInfo().get();
     * assert info.contains("addr");
     * }</pre>
     */
    CompletableFuture<String> clientInfo();

    /**
     * Returns information about the client connection.
     *
     * @see <a href="https://valkey.io/commands/client-info/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A {@link ClusterValue} which holds a single value if single node route is used or a
     *     dictionary where each address is the key and its corresponding node response is the value.
     * @example
     *     <pre>{@code
     * String info = clusterClient.clientInfo(RANDOM).get().getSingleValue();
     * assert info.contains("addr");
     *
     * Map<String, String> infoPerNode = clusterClient.clientInfo(ALL_NODES).get().getMultiValue();
     * assert infoPerNode.get("node1.example.com:6379").contains("addr");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clientInfo(Route route);

    /**
     * Closes the connection of a specific client.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/client-kill/">valkey.io</a> for details.
     * @param ipPort The IP address and port of the client to close (<code>ip:port</code> format).
     * @return <code>OK</code> if the client connection was successfully closed.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientKillSimple("127.0.0.1:6379").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientKillSimple(String ipPort);

    /**
     * Closes the connection of a specific client.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/client-kill/">valkey.io</a> for details.
     * @param ipPort The IP address and port of the client to close (<code>ip:port</code> format).
     * @return <code>OK</code> if the client connection was successfully closed.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientKillSimple(gs("127.0.0.1:6379")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientKillSimple(GlideString ipPort);

    /**
     * Returns the list of all connected clients.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/client-list/">valkey.io</a> for details.
     * @return A string containing the list of clients.
     * @example
     *     <pre>{@code
     * String clientList = clusterClient.clientList().get();
     * assert clientList.contains("addr");
     * }</pre>
     */
    CompletableFuture<String> clientList();

    /**
     * Returns the list of all connected clients.
     *
     * @see <a href="https://valkey.io/commands/client-list/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A {@link ClusterValue} which holds a single value if single node route is used or a
     *     dictionary where each address is the key and its corresponding node response is the value.
     * @example
     *     <pre>{@code
     * String clientList = clusterClient.clientList(RANDOM).get().getSingleValue();
     * assert clientList.contains("addr");
     *
     * Map<String, String> clientListPerNode = clusterClient.clientList(ALL_NODES).get().getMultiValue();
     * assert clientListPerNode.get("node1.example.com:6379").contains("addr");
     * }</pre>
     */
    CompletableFuture<ClusterValue<String>> clientList(Route route);

    /**
     * Sets the client eviction mode for the current connection.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/client-no-evict/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, sets the client eviction off.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientNoEvict(true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientNoEvict(boolean enabled);

    /**
     * Sets the client eviction mode for the current connection.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/client-no-evict/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, sets the client eviction off.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientNoEvict(true, ALL_NODES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientNoEvict(boolean enabled, Route route);

    /**
     * Sets the client no-touch mode for the current connection.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/client-no-touch/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, sets the client no-touch on.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientNoTouch(true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientNoTouch(boolean enabled);

    /**
     * Sets the client no-touch mode for the current connection.
     *
     * @since Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/client-no-touch/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, sets the client no-touch on.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientNoTouch(true, ALL_NODES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientNoTouch(boolean enabled, Route route);

    /**
     * Suspends all client activity for the specified number of milliseconds.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/client-pause/">valkey.io</a> for details.
     * @param timeout The duration in milliseconds to pause client activity.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientPause(1000).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientPause(long timeout);

    /**
     * Suspends all client activity for the specified number of milliseconds.
     *
     * @see <a href="https://valkey.io/commands/client-pause/">valkey.io</a> for details.
     * @param timeout The duration in milliseconds to pause client activity.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientPause(1000, ALL_NODES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientPause(long timeout, Route route);

    /**
     * Resumes processing of clients that were paused by {@link #clientPause}.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.2 and above.
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
     * Resumes processing of clients that were paused by {@link #clientPause}.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/client-unpause/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientUnpause(ALL_NODES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientUnpause(Route route);

    /**
     * Assigns a name to the connection.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/client-setname/">valkey.io</a> for details.
     * @param connectionName The name to assign to the connection.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientSetName("myConnection").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetName(String connectionName);

    /**
     * Assigns a name to the connection.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/client-setname/">valkey.io</a> for details.
     * @param connectionName The name to assign to the connection.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientSetName(gs("myConnection")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetName(GlideString connectionName);

    /**
     * Unblocks a client blocked in a blocking command.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 5.0 and above.
     * @see <a href="https://valkey.io/commands/client-unblock/">valkey.io</a> for details.
     * @param clientId The ID of the client to unblock.
     * @return The number of clients that were unblocked (0 or 1).
     * @example
     *     <pre>{@code
     * Long numUnblocked = clusterClient.clientUnblock(12345L).get();
     * assert numUnblocked == 1L;
     * }</pre>
     */
    CompletableFuture<Long> clientUnblock(long clientId);

    /**
     * Unblocks a client blocked in a blocking command with an error.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 5.0 and above.
     * @see <a href="https://valkey.io/commands/client-unblock/">valkey.io</a> for details.
     * @param clientId The ID of the client to unblock.
     * @param withError If <code>true</code>, unblock with an error.
     * @return The number of clients that were unblocked (0 or 1).
     * @example
     *     <pre>{@code
     * Long numUnblocked = clusterClient.clientUnblock(12345L, true).get();
     * assert numUnblocked == 1L;
     * }</pre>
     */
    CompletableFuture<Long> clientUnblock(long clientId, boolean withError);

    /**
     * Unblocks a client blocked in a blocking command.
     *
     * @since Valkey 5.0 and above.
     * @see <a href="https://valkey.io/commands/client-unblock/">valkey.io</a> for details.
     * @param clientId The ID of the client to unblock.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The number of clients that were unblocked (0 or 1).
     * @example
     *     <pre>{@code
     * Long numUnblocked = clusterClient.clientUnblock(12345L, RANDOM).get();
     * assert numUnblocked == 1L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Long>> clientUnblock(long clientId, Route route);

    /**
     * Unblocks a client blocked in a blocking command with an error.
     *
     * @since Valkey 5.0 and above.
     * @see <a href="https://valkey.io/commands/client-unblock/">valkey.io</a> for details.
     * @param clientId The ID of the client to unblock.
     * @param withError If <code>true</code>, unblock with an error.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The number of clients that were unblocked (0 or 1).
     * @example
     *     <pre>{@code
     * Long numUnblocked = clusterClient.clientUnblock(12345L, true, RANDOM).get();
     * assert numUnblocked == 1L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Long>> clientUnblock(
            long clientId, boolean withError, Route route);

    /**
     * Returns the client ID to which tracking notifications are redirected.<br>
     * The command will be routed to a random node.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/client-getredir/">valkey.io</a> for details.
     * @return The client ID, or <code>-1</code> if tracking is not enabled.
     * @example
     *     <pre>{@code
     * Long clientId = clusterClient.clientGetRedir().get();
     * assert clientId >= -1L;
     * }</pre>
     */
    CompletableFuture<Long> clientGetRedir();

    /**
     * Returns the client ID to which tracking notifications are redirected.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/client-getredir/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A {@link ClusterValue} which holds a single value if single node route is used or a
     *     dictionary where each address is the key and its corresponding node response is the value.
     * @example
     *     <pre>{@code
     * Long clientId = clusterClient.clientGetRedir(RANDOM).get().getSingleValue();
     * assert clientId >= -1L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Long>> clientGetRedir(Route route);

    /**
     * Returns information about server assisted client side caching for the current connection.<br>
     * The command will be routed to a random node.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/client-trackinginfo/">valkey.io</a> for details.
     * @return An array with client tracking information.
     * @example
     *     <pre>{@code
     * Object[] trackingInfo = clusterClient.clientTrackingInfo().get();
     * assert trackingInfo != null;
     * }</pre>
     */
    CompletableFuture<Object[]> clientTrackingInfo();

    /**
     * Returns information about server assisted client side caching for the current connection.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/client-trackinginfo/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A {@link ClusterValue} which holds a single value if single node route is used or a
     *     dictionary where each address is the key and its corresponding node response is the value.
     * @example
     *     <pre>{@code
     * Object[] trackingInfo = clusterClient.clientTrackingInfo(RANDOM).get().getSingleValue();
     * assert trackingInfo != null;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object[]>> clientTrackingInfo(Route route);

    /**
     * Enables or disables tracking of keys in the next command.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/client-caching/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, enables caching. If <code>false</code>, disables it.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientCaching(true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientCaching(boolean enabled);

    /**
     * Enables or disables tracking of keys in the next command.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/client-caching/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, enables caching. If <code>false</code>, disables it.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientCaching(true, ALL_NODES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientCaching(boolean enabled, Route route);

    /**
     * Sets client information for the current connection.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/client-setinfo/">valkey.io</a> for details.
     * @param attribute The attribute to set. Valid attributes are "lib-name" and "lib-ver".
     * @param value The value to set for the attribute.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientSetInfo("lib-name", "glide-java").get();
     * assert response.equals("OK");
     * response = clusterClient.clientSetInfo("lib-ver", "1.0.0").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetInfo(String attribute, String value);

    /**
     * Sets client information for the current connection using binary strings.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/client-setinfo/">valkey.io</a> for details.
     * @param attribute The attribute to set. Valid attributes are "lib-name" and "lib-ver".
     * @param value The value to set for the attribute.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientSetInfo(gs("lib-name"), gs("glide-java")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetInfo(GlideString attribute, GlideString value);

    /**
     * Controls server replies to the client.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 3.2 and above.
     * @see <a href="https://valkey.io/commands/client-reply/">valkey.io</a> for details.
     * @param mode The reply mode: {@link glide.api.models.commands.ClientReplyMode#ON},
     *     {@link glide.api.models.commands.ClientReplyMode#OFF}, or
     *     {@link glide.api.models.commands.ClientReplyMode#SKIP}.
     * @return <code>OK</code> when mode is {@link glide.api.models.commands.ClientReplyMode#ON} or
     *     {@link glide.api.models.commands.ClientReplyMode#SKIP}. No reply for
     *     {@link glide.api.models.commands.ClientReplyMode#OFF} mode.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientReply(ClientReplyMode.ON).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientReply(glide.api.models.commands.ClientReplyMode mode);

    /**
     * Controls server replies to the client.
     *
     * @since Valkey 3.2 and above.
     * @see <a href="https://valkey.io/commands/client-reply/">valkey.io</a> for details.
     * @param mode The reply mode: {@link glide.api.models.commands.ClientReplyMode#ON},
     *     {@link glide.api.models.commands.ClientReplyMode#OFF}, or
     *     {@link glide.api.models.commands.ClientReplyMode#SKIP}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> when mode is {@link glide.api.models.commands.ClientReplyMode#ON} or
     *     {@link glide.api.models.commands.ClientReplyMode#SKIP}. No reply for
     *     {@link glide.api.models.commands.ClientReplyMode#OFF} mode.
     * @example
     *     <pre>{@code
     * String response = clusterClient.clientReply(ClientReplyMode.ON, ALL_NODES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientReply(glide.api.models.commands.ClientReplyMode mode, Route route);

    /**
     * Closes the client connection gracefully.<br>
     * The command will be routed to all nodes.
     *
     * @apiNote Deprecated in Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/quit/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.quit().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> quit();

    /**
     * Closes the client connection gracefully.
     *
     * @apiNote Deprecated in Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/quit/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.quit(ALL_NODES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> quit(Route route);

    /**
     * Resets the connection, clearing the connection state.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/reset/">valkey.io</a> for details.
     * @return <code>RESET</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.reset().get();
     * assert response.equals("RESET");
     * }</pre>
     */
    CompletableFuture<String> reset();

    /**
     * Resets the connection, clearing the connection state.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/reset/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>RESET</code>.
     * @example
     *     <pre>{@code
     * String response = clusterClient.reset(ALL_NODES).get();
     * assert response.equals("RESET");
     * }</pre>
     */
    CompletableFuture<String> reset(Route route);

    /**
     * Switches the protocol version used by the connection.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/hello/">valkey.io</a> for details.
     * @param protocolVersion The protocol version to use (2 or 3).
     * @return A map containing server information including protocol version, server version, and
     *     other connection details.
     * @example
     *     <pre>{@code
     * Map<String, Object> info = clusterClient.hello(3).get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(long protocolVersion);

    /**
     * Switches the protocol version and authenticates the connection.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/hello/">valkey.io</a> for details.
     * @param protocolVersion The protocol version to use (2 or 3).
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @return A map containing server information including protocol version, server version, and
     *     other connection details.
     * @example
     *     <pre>{@code
     * Map<String, Object> info = clusterClient.hello(3, "myUser", "myPassword").get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, String username, String password);

    /**
     * Switches the protocol version and authenticates the connection.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/hello/">valkey.io</a> for details.
     * @param protocolVersion The protocol version to use (2 or 3).
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @return A map containing server information including protocol version, server version, and
     *     other connection details.
     * @example
     *     <pre>{@code
     * Map<String, Object> info = clusterClient.hello(3, gs("myUser"), gs("myPassword")).get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, GlideString username, GlideString password);

    /**
     * Switches the protocol version, authenticates the connection, and sets a client name.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/hello/">valkey.io</a> for details.
     * @param protocolVersion The protocol version to use (2 or 3).
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @param clientName The client name to set for the connection.
     * @return A map containing server information including protocol version, server version, and
     *     other connection details.
     * @example
     *     <pre>{@code
     * Map<String, Object> info = clusterClient.hello(3, "myUser", "myPassword", "myClient").get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, String username, String password, String clientName);

    /**
     * Switches the protocol version, authenticates the connection, and sets a client name.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/hello/">valkey.io</a> for details.
     * @param protocolVersion The protocol version to use (2 or 3).
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @param clientName The client name to set for the connection.
     * @return A map containing server information including protocol version, server version, and
     *     other connection details.
     * @example
     *     <pre>{@code
     * Map<String, Object> info = clusterClient.hello(3, gs("myUser"), gs("myPassword"), gs("myClient")).get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, GlideString username, GlideString password, GlideString clientName);
}
