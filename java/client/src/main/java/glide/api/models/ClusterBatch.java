/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static command_request.CommandRequestOuterClass.RequestType.Asking;
import static command_request.CommandRequestOuterClass.RequestType.ClusterAddSlots;
import static command_request.CommandRequestOuterClass.RequestType.ClusterAddSlotsRange;
import static command_request.CommandRequestOuterClass.RequestType.ClusterBumpEpoch;
import static command_request.CommandRequestOuterClass.RequestType.ClusterCountFailureReports;
import static command_request.CommandRequestOuterClass.RequestType.ClusterCountKeysInSlot;
import static command_request.CommandRequestOuterClass.RequestType.ClusterDelSlots;
import static command_request.CommandRequestOuterClass.RequestType.ClusterDelSlotsRange;
import static command_request.CommandRequestOuterClass.RequestType.ClusterFailover;
import static command_request.CommandRequestOuterClass.RequestType.ClusterFlushSlots;
import static command_request.CommandRequestOuterClass.RequestType.ClusterForget;
import static command_request.CommandRequestOuterClass.RequestType.ClusterGetKeysInSlot;
import static command_request.CommandRequestOuterClass.RequestType.ClusterInfo;
import static command_request.CommandRequestOuterClass.RequestType.ClusterKeySlot;
import static command_request.CommandRequestOuterClass.RequestType.ClusterLinks;
import static command_request.CommandRequestOuterClass.RequestType.ClusterMeet;
import static command_request.CommandRequestOuterClass.RequestType.ClusterMyId;
import static command_request.CommandRequestOuterClass.RequestType.ClusterMyShardId;
import static command_request.CommandRequestOuterClass.RequestType.ClusterNodes;
import static command_request.CommandRequestOuterClass.RequestType.ClusterReplicas;
import static command_request.CommandRequestOuterClass.RequestType.ClusterReplicate;
import static command_request.CommandRequestOuterClass.RequestType.ClusterReset;
import static command_request.CommandRequestOuterClass.RequestType.ClusterSaveConfig;
import static command_request.CommandRequestOuterClass.RequestType.ClusterSetConfigEpoch;
import static command_request.CommandRequestOuterClass.RequestType.ClusterSetslot;
import static command_request.CommandRequestOuterClass.RequestType.ClusterShards;
import static command_request.CommandRequestOuterClass.RequestType.ClusterSlots;
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardChannels;
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardNumSub;
import static command_request.CommandRequestOuterClass.RequestType.ReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.ReadWrite;
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

    // ==================== CLUSTER COMMANDS ====================

    /**
     * Returns information about the cluster. See {@link
     * glide.api.commands.ClusterCommands#clusterInfo()} for details.
     *
     * @return Command response - A {@link String} containing cluster state information.
     */
    public ClusterBatch clusterInfo() {
        protobufBatch.addCommands(buildCommand(ClusterInfo));
        return getThis();
    }

    /**
     * Returns the hash slot for a key. See {@link
     * glide.api.commands.ClusterCommands#clusterKeySlot(String)} for details.
     *
     * @param key The key to determine the hash slot for.
     * @return Command response - The hash slot number.
     */
    public <ArgType> ClusterBatch clusterKeySlot(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufBatch.addCommands(buildCommand(ClusterKeySlot, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns the node ID. See {@link glide.api.commands.ClusterCommands#clusterMyId()} for details.
     *
     * @return Command response - The node ID.
     */
    public ClusterBatch clusterMyId() {
        protobufBatch.addCommands(buildCommand(ClusterMyId));
        return getThis();
    }

    /**
     * Returns cluster nodes information. See {@link
     * glide.api.commands.ClusterCommands#clusterNodes()} for details.
     *
     * @return Command response - Node information.
     */
    public ClusterBatch clusterNodes() {
        protobufBatch.addCommands(buildCommand(ClusterNodes));
        return getThis();
    }

    /**
     * Returns slot mapping information. See {@link glide.api.commands.ClusterCommands#clusterSlots()}
     * for details.
     *
     * @return Command response - Slot mapping information.
     */
    public ClusterBatch clusterSlots() {
        protobufBatch.addCommands(buildCommand(ClusterSlots));
        return getThis();
    }

    /**
     * Returns shard information. See {@link glide.api.commands.ClusterCommands#clusterShards()} for
     * details.
     *
     * @return Command response - Shard information.
     */
    public ClusterBatch clusterShards() {
        protobufBatch.addCommands(buildCommand(ClusterShards));
        return getThis();
    }

    /**
     * Assigns slots to the node. See {@link
     * glide.api.commands.ClusterCommands#clusterAddSlots(long[])} for details.
     *
     * @param slots The slot numbers to assign.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterAddSlots(@NonNull long[] slots) {
        var args = newArgsBuilder();
        for (long slot : slots) {
            args.add(slot);
        }
        protobufBatch.addCommands(buildCommand(ClusterAddSlots, args));
        return getThis();
    }

    /**
     * Assigns slot ranges to the node. See {@link
     * glide.api.commands.ClusterCommands#clusterAddSlotsRange(long[][])} for details.
     *
     * @param slotRanges The slot range pairs [start, end] to assign.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterAddSlotsRange(@NonNull long[][] slotRanges) {
        var args = newArgsBuilder();
        for (long[] range : slotRanges) {
            for (long slot : range) {
                args.add(slot);
            }
        }
        protobufBatch.addCommands(buildCommand(ClusterAddSlotsRange, args));
        return getThis();
    }

    /**
     * Removes slots from the node. See {@link
     * glide.api.commands.ClusterCommands#clusterDelSlots(long[])} for details.
     *
     * @param slots The slot numbers to remove.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterDelSlots(@NonNull long[] slots) {
        var args = newArgsBuilder();
        for (long slot : slots) {
            args.add(slot);
        }
        protobufBatch.addCommands(buildCommand(ClusterDelSlots, args));
        return getThis();
    }

    /**
     * Removes slot ranges from the node. See {@link
     * glide.api.commands.ClusterCommands#clusterDelSlotsRange(long[][])} for details.
     *
     * @param slotRanges The slot range pairs [start, end] to remove.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterDelSlotsRange(@NonNull long[][] slotRanges) {
        var args = newArgsBuilder();
        for (long[] range : slotRanges) {
            for (long slot : range) {
                args.add(slot);
            }
        }
        protobufBatch.addCommands(buildCommand(ClusterDelSlotsRange, args));
        return getThis();
    }

    /**
     * Forces a failover. See {@link glide.api.commands.ClusterCommands#clusterFailover()} for
     * details.
     *
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterFailover() {
        protobufBatch.addCommands(buildCommand(ClusterFailover));
        return getThis();
    }

    /**
     * Removes a node from the cluster. See {@link
     * glide.api.commands.ClusterCommands#clusterForget(String)} for details.
     *
     * @param nodeId The ID of the node to remove.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterForget(@NonNull String nodeId) {
        protobufBatch.addCommands(buildCommand(ClusterForget, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Adds a node to the cluster. See {@link glide.api.commands.ClusterCommands#clusterMeet(String,
     * long)} for details.
     *
     * @param host The hostname of the node.
     * @param port The port of the node.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterMeet(@NonNull String host, long port) {
        protobufBatch.addCommands(buildCommand(ClusterMeet, newArgsBuilder().add(host).add(port)));
        return getThis();
    }

    /**
     * Changes replication source. See {@link
     * glide.api.commands.ClusterCommands#clusterReplicate(String)} for details.
     *
     * @param nodeId The ID of the primary node.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterReplicate(@NonNull String nodeId) {
        protobufBatch.addCommands(buildCommand(ClusterReplicate, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Resets the node. See {@link glide.api.commands.ClusterCommands#clusterReset()} for details.
     *
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterReset() {
        protobufBatch.addCommands(buildCommand(ClusterReset));
        return getThis();
    }

    /**
     * Resets the node with mode. See {@link
     * glide.api.commands.ClusterCommands#clusterReset(glide.api.commands.ClusterCommands.ClusterResetMode)}
     * for details.
     *
     * @param mode The reset mode.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterReset(
            @NonNull glide.api.commands.ClusterCommands.ClusterResetMode mode) {
        protobufBatch.addCommands(buildCommand(ClusterReset, newArgsBuilder().add(mode.name())));
        return getThis();
    }

    /**
     * Saves the cluster config. See {@link glide.api.commands.ClusterCommands#clusterSaveConfig()}
     * for details.
     *
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterSaveConfig() {
        protobufBatch.addCommands(buildCommand(ClusterSaveConfig));
        return getThis();
    }

    /**
     * Counts keys in a slot. See {@link
     * glide.api.commands.ClusterCommands#clusterCountKeysInSlot(long)} for details.
     *
     * @param slot The slot number.
     * @return Command response - The number of keys.
     */
    public ClusterBatch clusterCountKeysInSlot(long slot) {
        protobufBatch.addCommands(buildCommand(ClusterCountKeysInSlot, newArgsBuilder().add(slot)));
        return getThis();
    }

    /**
     * Gets keys from a slot. See {@link glide.api.commands.ClusterCommands#clusterGetKeysInSlot(long,
     * long)} for details.
     *
     * @param slot The slot number.
     * @param count The maximum number of keys to return.
     * @return Command response - An array of keys.
     */
    public ClusterBatch clusterGetKeysInSlot(long slot, long count) {
        protobufBatch.addCommands(
                buildCommand(ClusterGetKeysInSlot, newArgsBuilder().add(slot).add(count)));
        return getThis();
    }

    /**
     * Returns the shard ID. See {@link glide.api.commands.ClusterCommands#clusterMyShardId()} for
     * details.
     *
     * @return Command response - The shard ID.
     */
    public ClusterBatch clusterMyShardId() {
        protobufBatch.addCommands(buildCommand(ClusterMyShardId));
        return getThis();
    }

    /**
     * Returns replica information. See {@link
     * glide.api.commands.ClusterCommands#clusterReplicas(String)} for details.
     *
     * @param nodeId The primary node ID.
     * @return Command response - Replica information.
     */
    public ClusterBatch clusterReplicas(@NonNull String nodeId) {
        protobufBatch.addCommands(buildCommand(ClusterReplicas, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Returns cluster link information. See {@link glide.api.commands.ClusterCommands#clusterLinks()}
     * for details.
     *
     * @return Command response - Link information.
     */
    public ClusterBatch clusterLinks() {
        protobufBatch.addCommands(buildCommand(ClusterLinks));
        return getThis();
    }

    /**
     * Advances the cluster epoch. See {@link glide.api.commands.ClusterCommands#clusterBumpEpoch()}
     * for details.
     *
     * @return Command response - Result message.
     */
    public ClusterBatch clusterBumpEpoch() {
        protobufBatch.addCommands(buildCommand(ClusterBumpEpoch));
        return getThis();
    }

    /**
     * Binds a slot to a node. See {@link glide.api.commands.ClusterCommands#clusterSetSlot(long,
     * String, String)} for details.
     *
     * @param slot The slot number.
     * @param subcommand The subcommand (e.g., "IMPORTING", "MIGRATING", "STABLE", "NODE").
     * @param nodeId The node ID.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterSetSlot(
            long slot, @NonNull String subcommand, @NonNull String nodeId) {
        protobufBatch.addCommands(
                buildCommand(ClusterSetslot, newArgsBuilder().add(slot).add(subcommand).add(nodeId)));
        return getThis();
    }

    /**
     * Sets the config epoch. See {@link
     * glide.api.commands.ClusterCommands#clusterSetConfigEpoch(long)} for details.
     *
     * @param epoch The configuration epoch.
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterSetConfigEpoch(long epoch) {
        protobufBatch.addCommands(buildCommand(ClusterSetConfigEpoch, newArgsBuilder().add(epoch)));
        return getThis();
    }

    /**
     * Counts failure reports. See {@link
     * glide.api.commands.ClusterCommands#clusterCountFailureReports(String)} for details.
     *
     * @param nodeId The node ID.
     * @return Command response - The failure report count.
     */
    public ClusterBatch clusterCountFailureReports(@NonNull String nodeId) {
        protobufBatch.addCommands(
                buildCommand(ClusterCountFailureReports, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Flushes all slots. See {@link glide.api.commands.ClusterCommands#clusterFlushSlots()} for
     * details.
     *
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch clusterFlushSlots() {
        protobufBatch.addCommands(buildCommand(ClusterFlushSlots));
        return getThis();
    }

    /**
     * Enables read queries. See {@link glide.api.commands.ClusterCommands#readOnly()} for details.
     *
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch readOnly() {
        protobufBatch.addCommands(buildCommand(ReadOnly));
        return getThis();
    }

    /**
     * Disables read queries. See {@link glide.api.commands.ClusterCommands#readWrite()} for details.
     *
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch readWrite() {
        protobufBatch.addCommands(buildCommand(ReadWrite));
        return getThis();
    }

    /**
     * Enables redirection mode. See {@link glide.api.commands.ClusterCommands#asking()} for details.
     *
     * @return Command response - <code>OK</code>.
     */
    public ClusterBatch asking() {
        protobufBatch.addCommands(buildCommand(Asking));
        return getThis();
    }
}
