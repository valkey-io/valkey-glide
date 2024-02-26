/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Connection Management Commands interface.
 *
 * @see: <a href="https://redis.io/commands/?group=connection">Connection Management Commands</a>
 */
public interface ConnectionManagementCommands {

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return <code>String</code> with <code>"PONG"</code>.
     */
    CompletableFuture<String> ping();

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param message The server will respond with a copy of the message.
     * @return <code>String</code> with a copy of the argument <code>message</code>.
     */
    CompletableFuture<String> ping(String message);
}
