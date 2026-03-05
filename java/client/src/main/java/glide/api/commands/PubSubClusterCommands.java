/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Pub/Sub" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/publish/">Pub/Sub Commands</a>
 */
public interface PubSubClusterCommands {

    /**
     * Constant representing "unsubscribe from all sharded channels". Pass this to {@link
     * #sunsubscribe(Set)} or {@link #sunsubscribe(Set, int)} to unsubscribe from all sharded
     * channels.
     *
     * @example
     *     <pre>{@code
     * // Unsubscribe from all sharded channels
     * client.sunsubscribe(PubSubClusterCommands.ALL_SHARDED_CHANNELS).get();
     * }</pre>
     */
    Set<String> ALL_SHARDED_CHANNELS = Collections.emptySet();

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

    /**
     * Subscribes the client to the specified sharded channels.
     *
     * <p>This command updates the client's internal desired subscription state without waiting for
     * server confirmation. It returns immediately after updating the local state. The client will
     * attempt to subscribe asynchronously in the background.
     *
     * <p>Sharded pubsub (available in Valkey 7.0+) allows messages to be published to specific
     * cluster shards, reducing overhead compared to cluster-wide pubsub.
     *
     * <p>Note: Use {@code getSubscriptions()} to verify the actual server-side subscription state.
     *
     * @param channels A set of sharded channel names to subscribe to
     * @return A {@link CompletableFuture} that completes when the subscription request is processed
     * @example
     *     <pre>{@code
     * client.ssubscribeLazy(Set.of("shard-news", "shard-updates")).get();
     * }</pre>
     *
     * @see <a href="https://valkey.io/commands/ssubscribe/">valkey.io</a> for details
     */
    CompletableFuture<Void> ssubscribeLazy(Set<String> channels);

    /**
     * Subscribes the client to the specified sharded channels with a timeout.
     *
     * <p>This command updates the client's internal desired subscription state and waits for server
     * confirmation.
     *
     * @param channels A set of sharded channel names to subscribe to
     * @param timeoutMs Maximum time in milliseconds to wait for subscription confirmation. A value of
     *     0 blocks indefinitely until confirmation.
     * @return A {@link CompletableFuture} that completes when the subscription is confirmed or times
     *     out
     * @example
     *     <pre>{@code
     * client.ssubscribe(Set.of("shard-news", "shard-updates"), 5000).get();
     * }</pre>
     *
     * @see <a href="https://valkey.io/commands/ssubscribe/">valkey.io</a> for details
     */
    CompletableFuture<Void> ssubscribe(Set<String> channels, int timeoutMs);

    /**
     * Unsubscribes the client from all currently subscribed sharded channels.
     *
     * @return A {@link CompletableFuture} that completes when the unsubscription request is processed
     * @example
     *     <pre>{@code
     * client.sunsubscribe().get();
     * }</pre>
     *
     * @see <a href="https://valkey.io/commands/sunsubscribe/">valkey.io</a> for details
     */
    CompletableFuture<Void> sunsubscribe();

    /**
     * Unsubscribes the client from the specified sharded channels.
     *
     * @param channels A set of sharded channel names to unsubscribe from
     * @return A {@link CompletableFuture} that completes when the unsubscription request is processed
     * @example
     *     <pre>{@code
     * client.sunsubscribe(Set.of("shard-news", "shard-updates")).get();
     * }</pre>
     *
     * @see <a href="https://valkey.io/commands/sunsubscribe/">valkey.io</a> for details
     */
    CompletableFuture<Void> sunsubscribe(Set<String> channels);

    /**
     * Unsubscribes the client from the specified sharded channels with a timeout.
     *
     * <p>This command updates the client's internal desired subscription state and waits for server
     * confirmation.
     *
     * @param channels A set of sharded channel names to unsubscribe from
     * @param timeoutMs Maximum time in milliseconds to wait for unsubscription confirmation. A value
     *     of 0 blocks indefinitely until confirmation.
     * @return A {@link CompletableFuture} that completes when the unsubscription is confirmed or
     *     times out
     * @example
     *     <pre>{@code
     * client.sunsubscribe(Set.of("shard-news", "shard-updates"), 5000).get();
     * }</pre>
     *
     * @see <a href="https://valkey.io/commands/sunsubscribe/">valkey.io</a> for details
     */
    CompletableFuture<Void> sunsubscribe(Set<String> channels, int timeoutMs);

    /**
     * Unsubscribes the client from all currently subscribed sharded channels with a timeout.
     *
     * @param timeoutMs Maximum time in milliseconds to wait for unsubscription confirmation
     * @return A {@link CompletableFuture} that completes when the unsubscription is confirmed or
     *     times out
     * @example
     *     <pre>{@code
     * client.sunsubscribe(5000).get();
     * }</pre>
     *
     * @see <a href="https://valkey.io/commands/sunsubscribe/">valkey.io</a> for details
     */
    CompletableFuture<Void> sunsubscribe(int timeoutMs);
}
