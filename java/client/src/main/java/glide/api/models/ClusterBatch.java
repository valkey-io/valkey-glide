/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import io.valkey.glide.core.commands.CommandType;

/**
 * Batch implementation for cluster GlideClusterClient. Batches allow the execution of a
 * group of commands in a single step.
 *
 * <p>Batch Response: An array of command responses is returned by the client exec command,
 * in the order they were given. Each element in the array represents a command given to the
 * ClusterBatch. The response for each command depends on the executed command.
 *
 * <p><strong>isAtomic:</strong> Determines whether the batch is atomic or non-atomic. If {@code
 * true}, the batch will be executed as an atomic transaction. If {@code false}, the batch will be
 * executed as a non-atomic pipeline.
 *
 * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic Batches)</a>
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

    /**
     * Creates a new non-atomic ClusterBatch instance (pipeline).
     */
    public ClusterBatch() {
        super(false);
    }

    /**
     * Sets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch set(String key, String value) {
        return addCommand(CommandType.SET, key, value);
    }

    /**
     * Sets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch set(GlideString key, GlideString value) {
        return addCommand(CommandType.SET, key.toString(), value.toString());
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch get(String key) {
        return addCommand(CommandType.GET, key);
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch get(GlideString key) {
        return addCommand(CommandType.GET, key.toString());
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch del(String... keys) {
        return addCommand(CommandType.DEL, keys);
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch del(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.DEL, stringKeys);
    }

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch ping() {
        return addCommand(CommandType.PING);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch ping(String message) {
        return addCommand(CommandType.PING, message);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch ping(GlideString message) {
        return addCommand(CommandType.PING, message.toString());
    }
}