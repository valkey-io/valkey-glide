/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Connection Management" group for a standalone client.
 *
 * @see <a href="https://valkey.io/commands/?group=connection">Connection Management Commands</a>
 */
public interface ConnectionManagementCommands {

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @return <code>String</code> with <code>"PONG"</code>.
     * @example
     *     <pre>{@code
     * String payload = client.ping().get();
     * assert payload.equals("PONG");
     * }</pre>
     */
    CompletableFuture<String> ping();

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The server will respond with a copy of the message.
     * @return <code>String</code> with a copy of the argument <code>message</code>.
     * @example
     *     <pre>{@code
     * String payload = client.ping("GLIDE").get();
     * assert payload.equals("GLIDE");
     * }</pre>
     */
    CompletableFuture<String> ping(String message);

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The server will respond with a copy of the message.
     * @return <code>GlideString</code> with a copy of the argument <code>message</code>.
     * @example
     *     <pre>{@code
     * GlideString payload = client.ping(gs("GLIDE")).get();
     * assert payload.equals(gs("GLIDE"));
     * }</pre>
     */
    CompletableFuture<GlideString> ping(GlideString message);

    /**
     * Gets the current connection id.
     *
     * @see <a href="https://valkey.io/commands/client-id/">valkey.io</a> for details.
     * @return The id of the client.
     * @example
     *     <pre>{@code
     * Long id = client.clientId().get();
     * assert id > 0;
     * }</pre>
     */
    CompletableFuture<Long> clientId();

    /**
     * Gets the name of the current connection.
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
     * Echoes the provided <code>message</code> back.
     *
     * @see <a href="https://valkey.io/commands/echo/>valkey.io</a> for details.
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
     * Echoes the provided <code>message</code> back.
     *
     * @see <a href="https://valkey.io/commands/echo/>valkey.io</a> for details.
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
     * Changes the currently selected database.
     *
     * <p><b>WARNING:</b> This command is <b>NOT RECOMMENDED</b> for production use. Upon
     * reconnection, the client will revert to the database_id specified in the client configuration
     * (default: 0), NOT the database selected via this command.
     *
     * <p><b>RECOMMENDED APPROACH:</b> Use the database_id parameter in client configuration instead:
     *
     * <p><b>RECOMMENDED EXAMPLE:</b>
     *
     * <pre>{@code
     * GlideClient client = GlideClient.createClient(
     *     GlideClientConfiguration.builder()
     *         .address(NodeAddress.builder().host("localhost").port(6379).build())
     *         .databaseId(5)  // Recommended: persists across reconnections
     *         .build()
     * ).get();
     * }</pre>
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
     * Authenticates the connection with a password.
     *
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = client.auth("myPassword").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(String password);

    /**
     * Authenticates the connection with a username and password.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = client.auth("myUser", "myPassword").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(String username, String password);

    /**
     * Authenticates the connection with a password.
     *
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = client.auth(gs("myPassword")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(GlideString password);

    /**
     * Authenticates the connection with a username and password.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/auth/">valkey.io</a> for details.
     * @param username The username to authenticate with.
     * @param password The password to authenticate with.
     * @return <code>OK</code> if authentication was successful.
     * @example
     *     <pre>{@code
     * String response = client.auth(gs("myUser"), gs("myPassword")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> auth(GlideString username, GlideString password);

    /**
     * Returns information about the client connection.
     *
     * @see <a href="https://valkey.io/commands/client-info/">valkey.io</a> for details.
     * @return A string containing information about the client connection.
     * @example
     *     <pre>{@code
     * String info = client.clientInfo().get();
     * assert info.contains("addr");
     * }</pre>
     */
    CompletableFuture<String> clientInfo();

    /**
     * Closes the connection of all clients in the specified category.
     *
     * @see <a href="https://valkey.io/commands/client-kill/">valkey.io</a> for details.
     * @param ipPort The IP address and port of the client to close (<code>ip:port</code> format).
     * @return <code>OK</code> if the client connection was successfully closed.
     * @example
     *     <pre>{@code
     * String response = client.clientKillSimple("127.0.0.1:6379").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientKillSimple(String ipPort);

    /**
     * Closes the connection of all clients in the specified category.
     *
     * @see <a href="https://valkey.io/commands/client-kill/">valkey.io</a> for details.
     * @param ipPort The IP address and port of the client to close (<code>ip:port</code> format).
     * @return <code>OK</code> if the client connection was successfully closed.
     * @example
     *     <pre>{@code
     * String response = client.clientKillSimple(gs("127.0.0.1:6379")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientKillSimple(GlideString ipPort);

    /**
     * Returns the list of all connected clients.
     *
     * @see <a href="https://valkey.io/commands/client-list/">valkey.io</a> for details.
     * @return A string containing the list of clients.
     * @example
     *     <pre>{@code
     * String clientList = client.clientList().get();
     * assert clientList.contains("addr");
     * }</pre>
     */
    CompletableFuture<String> clientList();

    /**
     * Sets the client eviction mode for the current connection.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/client-no-evict/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, sets the client eviction off.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientNoEvict(true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientNoEvict(boolean enabled);

    /**
     * Sets the client no-touch mode for the current connection.
     *
     * @since Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/client-no-touch/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, sets the client no-touch on.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientNoTouch(true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientNoTouch(boolean enabled);

    /**
     * Suspends all client activity for the specified number of milliseconds.
     *
     * @see <a href="https://valkey.io/commands/client-pause/">valkey.io</a> for details.
     * @param timeout The duration in milliseconds to pause client activity.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientPause(1000).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientPause(long timeout);

    /**
     * Resumes processing of clients that were paused by {@link #clientPause}.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/client-unpause/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientUnpause().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientUnpause();

    /**
     * Assigns a name to the connection.
     *
     * @see <a href="https://valkey.io/commands/client-setname/">valkey.io</a> for details.
     * @param connectionName The name to assign to the connection.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientSetName("myConnection").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetName(String connectionName);

    /**
     * Assigns a name to the connection.
     *
     * @see <a href="https://valkey.io/commands/client-setname/">valkey.io</a> for details.
     * @param connectionName The name to assign to the connection.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientSetName(gs("myConnection")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetName(GlideString connectionName);

    /**
     * Unblocks a client blocked in a blocking command.
     *
     * @since Valkey 5.0 and above.
     * @see <a href="https://valkey.io/commands/client-unblock/">valkey.io</a> for details.
     * @param clientId The ID of the client to unblock.
     * @return The number of clients that were unblocked (0 or 1).
     * @example
     *     <pre>{@code
     * Long numUnblocked = client.clientUnblock(12345L).get();
     * assert numUnblocked == 1L;
     * }</pre>
     */
    CompletableFuture<Long> clientUnblock(long clientId);

    /**
     * Unblocks a client blocked in a blocking command with an error.
     *
     * @since Valkey 5.0 and above.
     * @see <a href="https://valkey.io/commands/client-unblock/">valkey.io</a> for details.
     * @param clientId The ID of the client to unblock.
     * @param withError If <code>true</code>, unblock with an error.
     * @return The number of clients that were unblocked (0 or 1).
     * @example
     *     <pre>{@code
     * Long numUnblocked = client.clientUnblock(12345L, true).get();
     * assert numUnblocked == 1L;
     * }</pre>
     */
    CompletableFuture<Long> clientUnblock(long clientId, boolean withError);

    /**
     * Returns the client ID to which tracking notifications are redirected.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/client-getredir/">valkey.io</a> for details.
     * @return The client ID, or <code>-1</code> if tracking is not enabled.
     * @example
     *     <pre>{@code
     * Long clientId = client.clientGetRedir().get();
     * assert clientId >= -1L;
     * }</pre>
     */
    CompletableFuture<Long> clientGetRedir();

    /**
     * Returns information about server assisted client side caching for the current connection.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/client-trackinginfo/">valkey.io</a> for details.
     * @return An array with client tracking information.
     * @example
     *     <pre>{@code
     * Object[] trackingInfo = client.clientTrackingInfo().get();
     * assert trackingInfo != null;
     * }</pre>
     */
    CompletableFuture<Object[]> clientTrackingInfo();

    /**
     * Enables or disables tracking of keys in the next command.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/client-caching/">valkey.io</a> for details.
     * @param enabled If <code>true</code>, enables caching. If <code>false</code>, disables it.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientCaching(true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientCaching(boolean enabled);

    /**
     * Sets client information for the current connection.
     *
     * @since Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/client-setinfo/">valkey.io</a> for details.
     * @param attribute The attribute to set. Valid attributes are "lib-name" and "lib-ver".
     * @param value The value to set for the attribute.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientSetInfo("lib-name", "glide-java").get();
     * assert response.equals("OK");
     * response = client.clientSetInfo("lib-ver", "1.0.0").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetInfo(String attribute, String value);

    /**
     * Sets client information for the current connection using binary strings.
     *
     * @since Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/client-setinfo/">valkey.io</a> for details.
     * @param attribute The attribute to set. Valid attributes are "lib-name" and "lib-ver".
     * @param value The value to set for the attribute.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.clientSetInfo(gs("lib-name"), gs("glide-java")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientSetInfo(GlideString attribute, GlideString value);

    /**
     * Controls server replies to the client.
     *
     * @since Valkey 3.2 and above.
     * @see <a href="https://valkey.io/commands/client-reply/">valkey.io</a> for details.
     * @param mode The reply mode: {@link glide.api.models.commands.ClientReplyMode#ON}, {@link
     *     glide.api.models.commands.ClientReplyMode#OFF}, or {@link
     *     glide.api.models.commands.ClientReplyMode#SKIP}.
     * @return <code>OK</code> when mode is {@link glide.api.models.commands.ClientReplyMode#ON} or
     *     {@link glide.api.models.commands.ClientReplyMode#SKIP}. No reply for {@link
     *     glide.api.models.commands.ClientReplyMode#OFF} mode.
     * @example
     *     <pre>{@code
     * String response = client.clientReply(ClientReplyMode.ON).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> clientReply(glide.api.models.commands.ClientReplyMode mode);

    /**
     * Closes the client connection gracefully.
     *
     * @apiNote Deprecated in Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/quit/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.quit().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> quit();

    /**
     * Resets the connection, clearing the connection state.
     *
     * @since Valkey 6.2 and above.
     * @see <a href="https://valkey.io/commands/reset/">valkey.io</a> for details.
     * @return <code>RESET</code>.
     * @example
     *     <pre>{@code
     * String response = client.reset().get();
     * assert response.equals("RESET");
     * }</pre>
     */
    CompletableFuture<String> reset();

    /**
     * Switches the protocol version used by the connection.
     *
     * @since Valkey 6.0 and above.
     * @see <a href="https://valkey.io/commands/hello/">valkey.io</a> for details.
     * @param protocolVersion The protocol version to use (2 or 3).
     * @return A map containing server information including protocol version, server version, and
     *     other connection details.
     * @example
     *     <pre>{@code
     * Map<String, Object> info = client.hello(3).get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(long protocolVersion);

    /**
     * Switches the protocol version and authenticates the connection.
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
     * Map<String, Object> info = client.hello(3, "myUser", "myPassword").get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, String username, String password);

    /**
     * Switches the protocol version and authenticates the connection.
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
     * Map<String, Object> info = client.hello(3, gs("myUser"), gs("myPassword")).get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, GlideString username, GlideString password);

    /**
     * Switches the protocol version, authenticates the connection, and sets a client name.
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
     * Map<String, Object> info = client.hello(3, "myUser", "myPassword", "myClient").get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, String username, String password, String clientName);

    /**
     * Switches the protocol version, authenticates the connection, and sets a client name.
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
     * Map<String, Object> info = client.hello(3, gs("myUser"), gs("myPassword"), gs("myClient")).get();
     * assert info.containsKey("proto");
     * }</pre>
     */
    CompletableFuture<java.util.Map<String, Object>> hello(
            long protocolVersion, GlideString username, GlideString password, GlideString clientName);
}
