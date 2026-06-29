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
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardChannels;
import static command_request.CommandRequestOuterClass.RequestType.PubSubShardNumSub;
import static command_request.CommandRequestOuterClass.RequestType.ReadOnly;
import static command_request.CommandRequestOuterClass.RequestType.ReadWrite;
import static command_request.CommandRequestOuterClass.RequestType.SPublish;
import static glide.utils.ArgsBuilder.checkTypeOrThrow;
import static glide.utils.ArgsBuilder.newArgsBuilder;

import glide.api.GlideClusterClient;
import glide.api.models.commands.cluster.ClusterFailoverOptions;
import glide.api.models.commands.cluster.ClusterResetOptions;
import glide.api.models.commands.cluster.ClusterSetSlotOptions;
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

    /**
     * Adds a new node to the cluster.
     *
     * @see <a href="https://valkey.io/commands/cluster-meet/">valkey.io</a> for details.
     * @param host The IP address or hostname of the node to add.
     * @param port The port number of the node to add.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterMeet(@NonNull String host, long port) {
        protobufBatch.addCommands(buildCommand(ClusterMeet, newArgsBuilder().add(host).add(port)));
        return getThis();
    }

    /**
     * Gets information and statistics about the cluster state.
     *
     * @see <a href="https://valkey.io/commands/cluster-info/">valkey.io</a> for details.
     * @return Command response - A <code>String</code> containing cluster state information.
     */
    public ClusterBatch clusterInfo() {
        protobufBatch.addCommands(buildCommand(ClusterInfo));
        return getThis();
    }

    /**
     * Removes a node from the cluster.
     *
     * @see <a href="https://valkey.io/commands/cluster-forget/">valkey.io</a> for details.
     * @param nodeId The ID of the node to remove.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterForget(@NonNull String nodeId) {
        protobufBatch.addCommands(buildCommand(ClusterForget, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Gets a list of all nodes in the cluster and their attributes.
     *
     * @see <a href="https://valkey.io/commands/cluster-nodes/">valkey.io</a> for details.
     * @return Command response - A <code>String</code> containing node information.
     */
    public ClusterBatch clusterNodes() {
        protobufBatch.addCommands(buildCommand(ClusterNodes));
        return getThis();
    }

    /**
     * Configures the current node to replicate data from a primary node.
     *
     * @see <a href="https://valkey.io/commands/cluster-replicate/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node to replicate.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterReplicate(@NonNull String nodeId) {
        protobufBatch.addCommands(buildCommand(ClusterReplicate, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Returns details about the shards of the cluster.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-shards/">valkey.io</a> for details.
     * @return Command response - An <code>array</code> of shard information maps.
     */
    public ClusterBatch clusterShards() {
        protobufBatch.addCommands(buildCommand(ClusterShards));
        return getThis();
    }

    /**
     * Returns a list of replicas for the specified primary node.
     *
     * @see <a href="https://valkey.io/commands/cluster-replicas/">valkey.io</a> for details.
     * @param nodeId The ID of the primary node.
     * @return Command response - An array of replica node information strings.
     */
    public ClusterBatch clusterReplicas(@NonNull String nodeId) {
        protobufBatch.addCommands(buildCommand(ClusterReplicas, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Returns information about the TCP links to and from each node in the cluster.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-links/">valkey.io</a> for details.
     * @return Command response - An <code>array</code> of link information maps.
     */
    public ClusterBatch clusterLinks() {
        protobufBatch.addCommands(buildCommand(ClusterLinks));
        return getThis();
    }

    /**
     * Returns the number of failure reports for the specified node.
     *
     * @see <a href="https://valkey.io/commands/cluster-count-failure-reports/">valkey.io</a> for
     *     details.
     * @param nodeId The ID of the node.
     * @return Command response - The number of active failure reports.
     */
    public ClusterBatch clusterCountFailureReports(@NonNull String nodeId) {
        protobufBatch.addCommands(
                buildCommand(ClusterCountFailureReports, newArgsBuilder().add(nodeId)));
        return getThis();
    }

    /**
     * Returns the unique identifier (ID) of the current node.
     *
     * @see <a href="https://valkey.io/commands/cluster-myid/">valkey.io</a> for details.
     * @return Command response - A <code>String</code> containing the node ID.
     */
    public ClusterBatch clusterMyId() {
        protobufBatch.addCommands(buildCommand(ClusterMyId));
        return getThis();
    }

    /**
     * Initiates a manual failover of the primary node to one of its replicas.
     *
     * @see <a href="https://valkey.io/commands/cluster-failover/">valkey.io</a> for details.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterFailover() {
        protobufBatch.addCommands(buildCommand(ClusterFailover));
        return getThis();
    }

    /**
     * Initiates a manual failover with specified options.
     *
     * @see <a href="https://valkey.io/commands/cluster-failover/">valkey.io</a> for details.
     * @param options The failover options (FORCE or TAKEOVER).
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterFailover(@NonNull ClusterFailoverOptions options) {
        protobufBatch.addCommands(
                buildCommand(ClusterFailover, newArgsBuilder().add(options.toArgs())));
        return getThis();
    }

    /**
     * Manages the assignment of hash slots to nodes.
     *
     * @see <a href="https://valkey.io/commands/cluster-setslot/">valkey.io</a> for details.
     * @param slot The hash slot number (0-16383).
     * @param options The slot assignment options.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterSetSlot(long slot, @NonNull ClusterSetSlotOptions options) {
        protobufBatch.addCommands(
                buildCommand(ClusterSetslot, newArgsBuilder().add(slot).add(options.toArgs())));
        return getThis();
    }

    /**
     * Forces a node to increment its configuration epoch.
     *
     * @see <a href="https://valkey.io/commands/cluster-bumpepoch/">valkey.io</a> for details.
     * @return Command response - <code>BUMPED</code> or <code>STILL</code>.
     */
    public ClusterBatch clusterBumpEpoch() {
        protobufBatch.addCommands(buildCommand(ClusterBumpEpoch));
        return getThis();
    }

    /**
     * Sets the configuration epoch for a node.
     *
     * @see <a href="https://valkey.io/commands/cluster-set-config-epoch/">valkey.io</a> for details.
     * @param configEpoch The configuration epoch value to set.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterSetConfigEpoch(long configEpoch) {
        protobufBatch.addCommands(
                buildCommand(ClusterSetConfigEpoch, newArgsBuilder().add(configEpoch)));
        return getThis();
    }

    /**
     * Clears the node's hash slot ownership information.
     *
     * @see <a href="https://valkey.io/commands/cluster-flushslots/">valkey.io</a> for details.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterFlushSlots() {
        protobufBatch.addCommands(buildCommand(ClusterFlushSlots));
        return getThis();
    }

    /**
     * Resets a cluster node with default soft reset.
     *
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterReset() {
        protobufBatch.addCommands(buildCommand(ClusterReset));
        return getThis();
    }

    /**
     * Resets a cluster node with specified options.
     *
     * @see <a href="https://valkey.io/commands/cluster-reset/">valkey.io</a> for details.
     * @param options The reset options (SOFT or HARD).
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterReset(@NonNull ClusterResetOptions options) {
        protobufBatch.addCommands(buildCommand(ClusterReset, newArgsBuilder().add(options.toArgs())));
        return getThis();
    }

    /**
     * Enables read queries for a connection to a cluster replica node.
     *
     * @see <a href="https://valkey.io/commands/readonly/">valkey.io</a> for details.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch readonly() {
        protobufBatch.addCommands(buildCommand(ReadOnly));
        return getThis();
    }

    /**
     * Disables read queries for a connection to a cluster replica node.
     *
     * @see <a href="https://valkey.io/commands/readwrite/">valkey.io</a> for details.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch readwrite() {
        protobufBatch.addCommands(buildCommand(ReadWrite));
        return getThis();
    }

    /**
     * Allows commands to be executed during slot migration.
     *
     * @see <a href="https://valkey.io/commands/asking/">valkey.io</a> for details.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch asking() {
        protobufBatch.addCommands(buildCommand(Asking));
        return getThis();
    }

    /**
     * Saves the cluster configuration to disk.
     *
     * @see <a href="https://valkey.io/commands/cluster-saveconfig/">valkey.io</a> for details.
     * @return Command response - <code>OK</code> on success.
     */
    public ClusterBatch clusterSaveConfig() {
        protobufBatch.addCommands(buildCommand(ClusterSaveConfig));
        return getThis();
    }

    /**
     * Returns an array of keys stored in the specified hash slot.
     *
     * @see <a href="https://valkey.io/commands/cluster-getkeysinslot/">valkey.io</a> for details.
     * @param slot The hash slot number (0-16383).
     * @param count The maximum number of keys to return.
     * @return Command response - An array of keys in the slot.
     */
    public ClusterBatch clusterGetKeysInSlot(long slot, long count) {
        protobufBatch.addCommands(
                buildCommand(ClusterGetKeysInSlot, newArgsBuilder().add(slot).add(count)));
        return getThis();
    }

    /**
     * Returns the shard ID of the current node.
     *
     * @apiNote Valkey 7.2 and above.
     * @see <a href="https://valkey.io/commands/cluster-myshardid/">valkey.io</a> for details.
     * @return Command response - A <code>String</code> containing the shard ID.
     */
    public ClusterBatch clusterMyShardId() {
        protobufBatch.addCommands(buildCommand(ClusterMyShardId));
        return getThis();
    }

    /**
     * Returns the hash slot number for the given key.
     *
     * @see <a href="https://valkey.io/commands/cluster-keyslot/">valkey.io</a> for details.
     * @param key The key to get the hash slot for.
     * @return Command response - The hash slot number (0-16383) for the given key.
     */
    public <ArgType> ClusterBatch clusterKeySlot(@NonNull ArgType key) {
        checkTypeOrThrow(key);
        protobufBatch.addCommands(buildCommand(ClusterKeySlot, newArgsBuilder().add(key)));
        return getThis();
    }

    /**
     * Returns the number of keys in the specified hash slot.
     *
     * @see <a href="https://valkey.io/commands/cluster-countkeysinslot/">valkey.io</a> for details.
     * @param slot The hash slot number (0-16383) to count keys in.
     * @return Command response - The number of keys in the specified slot.
     */
    public ClusterBatch clusterCountKeysInSlot(long slot) {
        protobufBatch.addCommands(buildCommand(ClusterCountKeysInSlot, newArgsBuilder().add(slot)));
        return getThis();
    }

    /**
     * Assigns hash slots to the current node.
     *
     * @see <a href="https://valkey.io/commands/cluster-addslots/">valkey.io</a> for details.
     * @param slots An array of hash slot numbers (0-16383) to assign to the node.
     * @return Command response - <code>"OK"</code> if the slots were successfully assigned.
     */
    public ClusterBatch clusterAddSlots(long[] slots) {
        protobufBatch.addCommands(buildCommand(ClusterAddSlots, newArgsBuilder().add(slots)));
        return getThis();
    }

    /**
     * Assigns hash slot ranges to the current node.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-addslotsrange/">valkey.io</a> for details.
     * @param slotRanges A 2D array where each sub-array contains two elements: [start_slot, end_slot]
     *     representing an inclusive range of slots to assign.
     * @return Command response - <code>"OK"</code> if the slot ranges were successfully assigned.
     */
    public ClusterBatch clusterAddSlotsRange(long[][] slotRanges) {
        protobufBatch.addCommands(buildCommand(ClusterAddSlotsRange, newArgsBuilder().add(slotRanges)));
        return getThis();
    }

    /**
     * Removes hash slots from the current node.
     *
     * @see <a href="https://valkey.io/commands/cluster-delslots/">valkey.io</a> for details.
     * @param slots An array of hash slot numbers (0-16383) to remove from the node.
     * @return Command response - <code>"OK"</code> if the slots were successfully removed.
     */
    public ClusterBatch clusterDelSlots(long[] slots) {
        protobufBatch.addCommands(buildCommand(ClusterDelSlots, newArgsBuilder().add(slots)));
        return getThis();
    }

    /**
     * Removes hash slot ranges from the current node.
     *
     * @apiNote Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/cluster-delslotsrange/">valkey.io</a> for details.
     * @param slotRanges A 2D array where each sub-array contains two elements: [start_slot, end_slot]
     *     representing an inclusive range of slots to remove.
     * @return Command response - <code>"OK"</code> if the slot ranges were successfully removed.
     */
    public ClusterBatch clusterDelSlotsRange(long[][] slotRanges) {
        protobufBatch.addCommands(buildCommand(ClusterDelSlotsRange, newArgsBuilder().add(slotRanges)));
        return getThis();
    }
}
