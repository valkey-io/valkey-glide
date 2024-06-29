/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Pub/Sub" group for standalone and cluster clients.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=pubsub">Pub/Sub Commands</a>
 */
public interface PubSubBaseCommands {

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://redis.io/docs/latest/commands/publish/">redis.io</a> for details.
     * @param channel The Channel to publish the message on.
     * @param message The message to publish.
     * @return The number of clients that received the message.
     * @example
     *     <pre>{@code
     * Long receivers = client.publish("announcements", "The cat said 'meow'!").get();
     * assert receivers > 0L;
     * }</pre>
     */
    CompletableFuture<Long> publish(String channel, String message);
}
