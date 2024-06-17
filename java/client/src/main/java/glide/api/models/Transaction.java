/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static glide.api.commands.GenericBaseCommands.REPLACE_REDIS_API;
import static glide.api.commands.GenericCommands.DB_REDIS_API;
import static redis_request.RedisRequestOuterClass.RequestType.Copy;
import static redis_request.RedisRequestOuterClass.RequestType.Move;
import static redis_request.RedisRequestOuterClass.RequestType.Select;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.apache.commons.lang3.ArrayUtils;
import redis_request.RedisRequestOuterClass.Command.ArgsArray;

/**
 * Extends BaseTransaction class for Redis standalone commands. Transactions allow the execution of
 * a group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client <code>exec</code>
 * command, in the order they were given. Each element in the array represents a command given to
 * the <code>Transaction</code>. The response for each command depends on the executed Redis
 * command. Specific response types are documented alongside each method.
 *
 * @example
 *     <pre>{@code
 * Transaction transaction = new Transaction()
 *     .set("key", "value")
 *     .get("key");
 * Object[] result = client.exec(transaction).get();
 * // result contains: OK and "value"
 * assert result[0].equals("OK");
 * assert result[1].equals("value");
 * }</pre>
 */
@AllArgsConstructor
public class Transaction extends BaseTransaction<Transaction> {
    @Override
    protected Transaction getThis() {
        return this;
    }

    /**
     * Changes the currently selected Redis database.
     *
     * @see <a href="https://redis.io/commands/select/">redis.io</a> for details.
     * @param index The index of the database to select.
     * @return Command Response - A simple <code>OK</code> response.
     */
    public Transaction select(long index) {
        ArgsArray commandArgs = buildArgs(Long.toString(index));

        protobufTransaction.addCommands(buildCommand(Select, commandArgs));
        return this;
    }

    /**
     * Move <code>key</code> from the currently selected database to the database specified by <code>
     * dbIndex</code>.
     *
     * @see <a href="https://redis.io/commands/move/">redis.io</a> for more details.
     * @param key The key to move.
     * @param dbIndex The index of the database to move <code>key</code> to.
     * @return Command Response - <code>true</code> if <code>key</code> was moved, or <code>false
     *     </code> if the <code>key</code> already exists in the destination database or does not
     *     exist in the source database.
     */
    public Transaction move(String key, long dbIndex) {
        ArgsArray commandArgs = buildArgs(key, Long.toString(dbIndex));
        protobufTransaction.addCommands(buildCommand(Move, commandArgs));
        return this;
    }

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Redis 6.2.0 and above.
     * @see <a href="https://redis.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @return Command Response - <code>true</code> if <code>source</code> was copied, <code>false
     *     </code> if <code>source</code> was not copied.
     */
    public Transaction copy(@NonNull String source, @NonNull String destination, long destinationDB) {
        return copy(source, destination, destinationDB, false);
    }

    /**
     * Copies the value stored at the <code>source</code> to the <code>destination</code> key on
     * <code>destinationDB</code>. When <code>replace</code> is true, removes the <code>destination
     * </code> key first if it already exists, otherwise performs no action.
     *
     * @since Redis 6.2.0 and above.
     * @see <a href="https://redis.io/commands/copy/">redis.io</a> for details.
     * @param source The key to the source value.
     * @param destination The key where the value should be copied to.
     * @param destinationDB The alternative logical database index for the destination key.
     * @param replace If the destination key should be removed before copying the value to it.
     * @return Command Response - <code>true</code> if <code>source</code> was copied, <code>false
     *     </code> if <code>source</code> was not copied.
     */
    public Transaction copy(
            @NonNull String source, @NonNull String destination, long destinationDB, boolean replace) {
        String[] args = new String[] {source, destination, DB_REDIS_API, Long.toString(destinationDB)};
        if (replace) {
            args = ArrayUtils.add(args, REPLACE_REDIS_API);
        }
        ArgsArray commandArgs = buildArgs(args);
        protobufTransaction.addCommands(buildCommand(Copy, commandArgs));
        return this;
    }
}
