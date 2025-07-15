/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.PubSubShardChannels;
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardNumSub;
import static command_request.CommandRequestOuterClass.RequestType.SPublish;
import static glide.utils.ArgsBuilder.checkTypeOrThrow;
import static glide.utils.ArgsBuilder.newArgsBuilder;

import glide.api.GlideClusterClient;
import lombok.NonNull;

/**
 * Batch implementation for cluster {@link GlideClusterClient}. Batches allow the execution of a
 * group of commands in a single step.
 *
 * <p>Batch Response: An <code>array</code> of command responses is returned by the client {@link
 * GlideClusterClient#exec} command, in the order they were given. Each element in the array
 * represents a command given to the {@link ClusterBatch}. The response for each command depends on
 * the executed command. Specific response types are documented alongside each method.
 *
 * <p><strong>isAtomic:</strong> Determines whether the batch is atomic or non-atomic. If {@code
 * true}, the batch will be executed as an atomic transaction. If {@code false}, the batch will be
 * executed as a non-atomic pipeline.
 *
 * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
 *     Batches)</a>
 * @see <a href="https://valkey.io/topics/pipelining">Valkey Pipelines (Non-Atomic Batches)</a>
 * @example
 *     <pre>{@code
 * // Example of Atomic Batch (Transaction) in a Cluster
 * ClusterBatch transaction = new ClusterBatch(true) // Atomic (Transactional)
 *     .set("key", "value")
 *     .get("key");
 * Object[] result = client.exec(transaction, false).get();
 * // result contains: OK and "value"
 * assert result[0].equals("OK");
 * assert result[1].equals("value");
 * }</pre>
 *
 * @example
 *     <pre>{@code
 * // Example of Non-Atomic Batch (Pipeline) in a Cluster
 * ClusterBatch pipeline = new ClusterBatch(false) // Non-Atomic (Pipeline)
 *     .set("key1", "value1")
 *     .set("key2", "value2")
 *     .get("key1")
 *     .get("key2");
 * Object[] result = client.exec(pipeline, false).get();
 * // result contains: OK, OK, "value1", "value2"
 * assert result[0].equals("OK");
 * assert result[1].equals("OK");
 * assert result[2].equals("value1");
 * assert result[3].equals("value2");
 * }</pre>
 */
public class ClusterBatch extends BaseBatch<ClusterBatch> {

    /**
     * Creates a new ClusterBatch instance.
     *
     * @param isAtomic Determines whether the batch is atomic or non-atomic. If {@code true}, the
     *     batch will be executed as an atomic transaction. If {@code false}, the batch will be
     *     executed as a non-atomic pipeline.
     */
    public ClusterBatch(boolean isAtomic) {
        super(isAtomic);
    }

    @Override
    protected ClusterBatch getThis() {
        return this;
    }

    /**
     * Publishes message on pubsub channel in sharded mode.
     *
     * @since Valkey 7.0 and above.
     * @implNote {@link ArgType} is limited to {@link String} or {@link GlideString}, any other type
     *     will throw {@link IllegalArgumentException}.
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @param sharded Indicates that this should be run in sharded mode. Setting <code>sharded</code>
     *     to <code>true</code> is only applicable with Valkey 7.0+.
     * @return Command response - The number of clients that received the message.
     */
    public <ArgType> ClusterBatch publish(
            @NonNull ArgType message, @NonNull ArgType channel, boolean sharded) {
        if (!sharded) {
            return super.publish(message, channel);
        }
        checkTypeOrThrow(channel);
        protobufBatch.addCommands(buildCommand(SPublish, newArgsBuilder().add(channel).add(message)));
        return getThis();
    }

    /**
     * Returns the number of subscribers (exclusive of clients subscribed to patterns) for the
     * specified shard channels. Note that it is valid to call this command without channels. In this
     * case, it will just return an empty map.
     *
     * @implNote {@link ArgType} is limited to {@link String} or {@link GlideString}, any other type
     *     will throw {@link IllegalArgumentException}.
     * @see <a href="https://valkey.io/commands/pubsub-shardnumsub/">valkey.io</a> for details.
     * @param channels The list of shard channels to query for the number of subscribers.
     * @return Command response - An <code>Map</code> where keys are the shard channel names and
     *     values are the number of subscribers.
     */
    public <ArgType> ClusterBatch pubsubShardNumSub(@NonNull ArgType[] channels) {
        checkTypeOrThrow(channels);
        protobufBatch.addCommands(buildCommand(PubSubShardNumSub, newArgsBuilder().add(channels)));
        return getThis();
    }

    /**
     * Lists the currently active shard channels.
     *
     * @see <a href="https://valkey.io/commands/pubsub-shardchannels/">valkey.io</a> for details.
     * @return Command response - An <code>Array</code> of all active shard channels.
     */
    public ClusterBatch pubsubShardChannels() {
        protobufBatch.addCommands(buildCommand(PubSubShardChannels));
        return getThis();
    }

    /**
     * Lists the currently active shard channels.
     *
     * @implNote {@link ArgType} is limited to {@link String} or {@link GlideString}, any other type *
     *     will throw {@link IllegalArgumentException}.
     * @see <a href="https://valkey.io/commands/pubsub-shardchannels/">valkey.io</a> for details.
     * @param pattern A glob-style pattern to match active shard channels.
     * @return Command response - An <code>Array</code> of all active shard channels.
     */
    public <ArgType> ClusterBatch pubsubShardChannels(@NonNull ArgType pattern) {
        checkTypeOrThrow(pattern);
        protobufBatch.addCommands(buildCommand(PubSubShardChannels, newArgsBuilder().add(pattern)));
        return getThis();
    }
}
