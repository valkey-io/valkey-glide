/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Pub/Sub" group for standalone and cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=pubsub">Pub/Sub Commands</a>
 */
public interface PubSubBaseCommands {

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish("The cat said 'meow'!", "announcements").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(String message, String channel);

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish(gs("The cat said 'meow'!"), gs("announcements")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(GlideString message, GlideString channel);
}
