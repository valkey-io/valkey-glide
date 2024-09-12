/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.Map;
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
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @param sharded Indicates that this should be run in sharded mode. Setting <code>sharded</code>
     *     to <code>true</code> is only applicable with Valkey 7.0+.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish("The cat said 'meow'!", "announcements", true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(String message, String channel, boolean sharded);

    /**
     * Publishes message on pubsub channel.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @param sharded Indicates that this should be run in sharded mode. Setting <code>sharded</code>
     *     to <code>true</code> is only applicable with Valkey 7.0+.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.publish(gs("The cat said 'meow'!"), gs("announcements"), true).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> publish(GlideString message, GlideString channel, boolean sharded);

    /**
     * Lists the currently active shard channels.
     *
     * @see <a href="https://valkey.io/commands/pubsub-shardchannels/">valkey.io</a> for details.
     * @return An <code>array</code> of all active shard channels.
     * @example
     *     <pre>{@code
     * String[] result = client.pubsubShardChannels().get();
     * assert Arrays.equals(result, new String[] { "channel1", "channel2" });
     * }</pre>
     */
    CompletableFuture<String[]> pubsubShardChannels();

    /**
     * Lists the currently active shard channels.
     *
     * @see <a href="https://valkey.io/commands/pubsub-shardchannels/">valkey.io</a> for details.
     * @return An <code>array</code> of all active shard channels.
     * @example
     *     <pre>{@code
     * GlideString[] result = client.pubsubShardChannelsBinary().get();
     * assert Arrays.equals(result, new GlideString[] { gs("channel1"), gs("channel2") });
     * }</pre>
     */
    CompletableFuture<GlideString[]> pubsubShardChannelsBinary();

    /**
     * Lists the currently active shard channels.
     *
     * @see <a href="https://valkey.io/commands/pubsub-shardchannels/">valkey.io</a> for details.
     * @param pattern A glob-style pattern to match active shard channels.
     * @return An <code>array</code> of currently active shard channels matching the given pattern.
     * @example
     *     <pre>{@code
     * String[] result = client.pubsubShardChannels("channel*").get();
     * assert Arrays.equals(result, new String[] { "channel1", "channel2" });
     * }</pre>
     */
    CompletableFuture<String[]> pubsubShardChannels(String pattern);

    /**
     * Lists the currently active shard channels.
     *
     * @see <a href="https://valkey.io/commands/pubsub-shardchannels/">valkey.io</a> for details.
     * @param pattern A glob-style pattern to match active shard channels.
     * @return An <code>array</code> of currently active shard channels matching the given pattern.
     * @example
     *     <pre>{@code
     * GlideString[] result = client.pubsubShardChannels(gs.("channel*")).get();
     * assert Arrays.equals(result, new GlideString[] { gs("channel1"), gs("channel2") });
     * }</pre>
     */
    CompletableFuture<GlideString[]> pubsubShardChannels(GlideString pattern);

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the
     * specified shard channels. Note that it is valid to call this command without channels. In this
     * case, it will just return an empty map.
     *
     * @see <a href="https://valkey.io/commands/pubsub-shardnumsub/">valkey.io</a> for details.
     * @param channels The list of shard channels to query for the number of subscribers.
     * @return An <code>Map</code> where keys are the shard channel names and values are the number of
     *     subscribers.
     * @example
     *     <pre>{@code
     * Map<String, Long> result = client.pubsubShardNumSub(new String[] {"channel1", "channel2"}).get();
     * assert result.equals(Map.of("channel1", 3L, "channel2", 5L));
     * }</pre>
     */
    CompletableFuture<Map<String, Long>> pubsubShardNumSub(String[] channels);

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the
     * specified shard channels. Note that it is valid to call this command without channels. In this
     * case, it will just return an empty map.
     *
     * @see <a href="https://valkey.io/commands/pubsub-shardnumsub/">valkey.io</a> for details.
     * @param channels The list of shard channels to query for the number of subscribers.
     * @return An <code>Map</code> where keys are the shard channel names and values are the number of
     *     subscribers.
     * @example
     *     <pre>{@code
     * Map<GlideString, Long> result = client.pubsubShardNumSub(new GlideString[] {gs("channel1"), gs("channel2")}).get();
     * assert result.equals(Map.of(gs("channel1"), 3L, gs("channel2"), 5L));
     * }</pre>
     */
    CompletableFuture<Map<GlideString, Long>> pubsubShardNumSub(GlideString[] channels);
}
