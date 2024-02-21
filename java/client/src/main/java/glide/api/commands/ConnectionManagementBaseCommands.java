/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Connection Management Commands interface for both standalone and cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=connection">Connection Management Commands</a>
 */
public interface ConnectionManagementBaseCommands {

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return Response from Redis containing a <code>String</code> with "PONG".
     */
    CompletableFuture<String> ping();

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param str The ping argument that will be returned.
     * @return Response from Redis containing a <code>String</code> with a copy of the argument <code>
     *     str</code>.
     */
    CompletableFuture<String> ping(String str);

    /**
     * Gets the current connection id.
     *
     * @see <a href="https://redis.io/commands/client-id/">redis.io</a> for details.
     * @return The id of the client.
     * @example
     *     <pre>
     * long id = client.clientId().get();
     * assert id > 0
     * </pre>
     */
    CompletableFuture<Long> clientId();

    /**
     * Gets the name of the current connection.
     *
     * @see <a href="https://redis.io/commands/client-getname/">redis.io</a> for details.
     * @return The name of the client connection as a string if a name is set, or <code>null</code> if
     *     no name is assigned.
     * @example
     *     <pre>
     * String clientName = client.clientGetName().get();
     * assert clientName != null
     * </pre>
     */
    CompletableFuture<String> clientGetName();
}
