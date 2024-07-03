/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Pub/Sub" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/publish/">Pub/Sub Commands</a>
 */
public interface PubSubClusterCommands extends PubSubBaseCommands {

    /**
     * Publishes message on pubsub channel.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://valkey.io/commands/spublish/">valkey.io</a> for details.
     * @param channel The channel to publish the message on.
     * @param message The message to publish.
     * @param sharded Indicates that this should be run in sharded mode.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish("announcements", "The cat said 'meow'!", true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(String channel, String message, boolean sharded);
}
