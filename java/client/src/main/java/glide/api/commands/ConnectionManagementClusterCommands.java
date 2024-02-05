/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.configuration.RequestRoutingConfiguration;
import java.util.concurrent.CompletableFuture;

/**
 * Connection Management Commands interface.
 *
 * @see: <a href="https://redis.io/commands/?group=connection">Connection Management Commands</a>
 */
public interface ConnectionManagementClusterCommands {

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @return Response from Redis containing a <code>String</code>.
     */
    CompletableFuture<String> ping(RequestRoutingConfiguration.Route route);

    /**
     * Ping the Redis server.
     *
     * @see <a href="https://redis.io/commands/ping/">redis.io</a> for details.
     * @param msg The ping argument that will be returned.
     * @return Response from Redis containing a <code>String</code>.
     */
    CompletableFuture<String> ping(String msg, RequestRoutingConfiguration.Route route);
}
