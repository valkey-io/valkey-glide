/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import static redis_request.RedisRequestOuterClass.RequestType.SPublish;

import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * Extends BaseTransaction class for cluster mode commands. Transactions allow the execution of a
 * group of commands in a single step.
 *
 * <p>Command Response: An array of command responses is returned by the client <code>exec</code>
 * command, in the order they were given. Each element in the array represents a command given to
 * the <code>Transaction</code>. The response for each command depends on the executed Redis
 * command. Specific response types are documented alongside each method.
 *
 * @example
 *     <pre>
 *  ClusterTransaction transaction = new ClusterTransaction();
 *    .set("key", "value");
 *    .get("key");
 *  ClusterValue[] result = client.exec(transaction, route).get();
 *  // result contains: OK and "value"
 *  </pre>
 */
@AllArgsConstructor
public class ClusterTransaction extends BaseTransaction<ClusterTransaction> {
    @Override
    protected ClusterTransaction getThis() {
        return this;
    }

    /**
     * Publishes message on pubsub channel in sharded mode.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/publish/">redis.io</a> for details.
     * @param channel The Channel to publish the message on.
     * @param message The message to publish.
     * @return Command response - The number of clients that received the message.
     */
    public ClusterTransaction spublish(@NonNull String channel, @NonNull String message) {
        protobufTransaction.addCommands(buildCommand(SPublish, buildArgs(channel, message)));
        return this;
    }
}
