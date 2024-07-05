/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.models.commands.SortBaseOptions.STORE_COMMAND_STRING;
import static redis_request.RedisRequestOuterClass.RequestType.SPublish;
import static redis_request.RedisRequestOuterClass.RequestType.Sort;
import static redis_request.RedisRequestOuterClass.RequestType.SortReadOnly;

import glide.api.RedisClusterClient;
import glide.api.models.commands.SortClusterOptions;
import lombok.NonNull;

/**
 * Transaction implementation for cluster {@link RedisClusterClient}. Transactions allow the
 * execution of a group of commands in a single step.
 *
 * <p>Transaction Response: An <code>array</code> of command responses is returned by the client
 * {@link RedisClusterClient#exec} command, in the order they were given. Each element in the array
 * represents a command given to the {@link ClusterTransaction}. The response for each command
 * depends on the executed Redis command. Specific response types are documented alongside each
 * method.
 *
 * @example
 *     <pre>{@code
 * ClusterTransaction transaction = new ClusterTransaction();
 *   .set("key", "value");
 *   .get("key");
 * Object[] result = client.exec(transaction).get();
 * // result contains: OK and "value"
 * }</pre>
 */
public class ClusterTransaction extends BaseTransaction<ClusterTransaction> {
    /**
     * Create a transaction for cluster client.
     *
     * @param binaryOutput Flag whether transaction commands may return binary data.<br>
     *     If set to <code>true</code>, all commands return {@link GlideString} instead of {@link
     *     String}.
     */
    public ClusterTransaction(boolean binaryOutput) {
        super(binaryOutput);
    }

    /**
     * Create a transaction for cluster client assuming {@link #binaryOutput} set to <code>false
     * </code>.
     */
    public ClusterTransaction() {
        this(false);
    }

    @Override
    protected ClusterTransaction getThis() {
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
     *     to <code>true</code> is only applicable with Redis 7.0+.
     * @return Command response - The number of clients that received the message.
     */
    public <ArgType> ClusterTransaction publish(
            @NonNull ArgType message, @NonNull ArgType channel, boolean sharded) {
        if (!sharded) {
            return super.publish(message, channel);
        }
        checkTypeOrThrow(channel);
        protobufTransaction.addCommands(
                buildCommand(SPublish, newArgsBuilder().add(channel).add(message)));
        return getThis();
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sort</code> command can be used to sort elements based on different criteria and
     * apply transformations on sorted elements.<br>
     * To store the result into a new key, see {@link #sortStore(ArgType, ArgType,
     * SortClusterOptions)}.
     *
     * @implNote {@link ArgType} is limited to {@link String} or {@link GlideString}, any other type
     *     will throw {@link IllegalArgumentException}.
     * @see <a href="https://valkey.io/commands/sort">valkey.io</a> for details.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public <ArgType> ClusterTransaction sort(
            @NonNull ArgType key, @NonNull SortClusterOptions sortClusterOptions) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(Sort, newArgsBuilder().add(key).add(sortClusterOptions.toArgs())));
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and returns the result.
     * <br>
     * The <code>sortReadOnly</code> command can be used to sort elements based on different criteria
     * and apply transformations on sorted elements.<br>
     *
     * @since Valkey 7.0 and above.
     * @implNote {@link ArgType} is limited to {@link String} or {@link GlideString}, any other type
     *     will throw {@link IllegalArgumentException}.
     * @see <a href="https://valkey.io/commands/sort_ro">valkey.io</a> for details.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return Command Response - An <code>Array</code> of sorted elements.
     */
    public <ArgType> ClusterTransaction sortReadOnly(
            @NonNull ArgType key, @NonNull SortClusterOptions sortClusterOptions) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(SortReadOnly, newArgsBuilder().add(key).add(sortClusterOptions.toArgs())));
        return this;
    }

    /**
     * Sorts the elements in the list, set, or sorted set at <code>key</code> and stores the result in
     * <code>destination</code>. The <code>sort</code> command can be used to sort elements based on
     * different criteria, apply transformations on sorted elements, and store the result in a new
     * key.<br>
     * To get the sort result without storing it into a key, see {@link #sort(ArgType,
     * SortClusterOptions)} or {@link #sortReadOnly(ArgType, SortClusterOptions)}.
     *
     * @implNote {@link ArgType} is limited to {@link String} or {@link GlideString}, any other type
     *     will throw {@link IllegalArgumentException}.
     * @see <a href="https://valkey.io/commands/sort">valkey.io</a> for details.
     * @param key The key of the list, set, or sorted set to be sorted.
     * @param destination The key where the sorted result will be stored.
     * @param sortClusterOptions The {@link SortClusterOptions}.
     * @return Command Response - The number of elements in the sorted key stored at <code>destination
     *     </code>.
     */
    public <ArgType> ClusterTransaction sortStore(
            @NonNull ArgType key,
            @NonNull ArgType destination,
            @NonNull SortClusterOptions sortClusterOptions) {
        checkTypeOrThrow(key);
        protobufTransaction.addCommands(
                buildCommand(
                        Sort,
                        newArgsBuilder()
                                .add(key)
                                .add(sortClusterOptions.toArgs())
                                .add(STORE_COMMAND_STRING)
                                .add(destination)));
        return this;
    }
}
