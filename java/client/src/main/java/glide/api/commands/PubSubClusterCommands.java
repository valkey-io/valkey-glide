/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Pub/Sub" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/publish/">Pub/Sub Commands</a>
 */
public interface PubSubClusterCommands {

    /**
     * Publishes message on pubsub channel.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @param sharded Indicates that this should be run in sharded mode. Setting <code>sharded</code>
     *     to <code>true</code> is only applicable with Redis 7.0+.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish("The cat said 'meow'!", "announcements", true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(String message, String channel, boolean sharded);
}
