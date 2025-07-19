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

    @Override
    protected ClusterBatch getThis() {
        return this;
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

    /**
     * Increments the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key to increment.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch incr(String key) {
        return addCommand(CommandType.INCR, key);
    }

    /**
     * Increments the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key to increment.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch incr(GlideString key) {
        return addCommand(CommandType.INCR, key.toString());
    }

    /**
     * Increments the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrby/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch incrBy(String key, long amount) {
        return addCommand(CommandType.INCRBY, key, String.valueOf(amount));
    }

    /**
     * Increments the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrby/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch incrBy(GlideString key, long amount) {
        return addCommand(CommandType.INCRBY, key.toString(), String.valueOf(amount));
    }

    /**
     * Increments the floating-point number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrbyfloat/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch incrByFloat(String key, double amount) {
        return addCommand(CommandType.INCRBYFLOAT, key, String.valueOf(amount));
    }

    /**
     * Increments the floating-point number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrbyfloat/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch incrByFloat(GlideString key, double amount) {
        return addCommand(CommandType.INCRBYFLOAT, key.toString(), String.valueOf(amount));
    }

    /**
     * Decrements the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch decr(String key) {
        return addCommand(CommandType.DECR, key);
    }

    /**
     * Decrements the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch decr(GlideString key) {
        return addCommand(CommandType.DECR, key.toString());
    }

    /**
     * Decrements the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/decrby/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @param amount The amount to decrement by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch decrBy(String key, long amount) {
        return addCommand(CommandType.DECRBY, key, String.valueOf(amount));
    }

    /**
     * Decrements the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/decrby/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @param amount The amount to decrement by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch decrBy(GlideString key, long amount) {
        return addCommand(CommandType.DECRBY, key.toString(), String.valueOf(amount));
    }

    /**
     * Returns the length of the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key to get length for.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch strlen(String key) {
        return addCommand(CommandType.STRLEN, key);
    }

    /**
     * Returns the length of the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key to get length for.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch strlen(GlideString key) {
        return addCommand(CommandType.STRLEN, key.toString());
    }

    /**
     * Appends a value to a key.
     *
     * @see <a href="https://valkey.io/commands/append/">valkey.io</a> for details.
     * @param key The key to append to.
     * @param value The value to append.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch append(String key, String value) {
        return addCommand(CommandType.APPEND, key, value);
    }

    /**
     * Appends a value to a key.
     *
     * @see <a href="https://valkey.io/commands/append/">valkey.io</a> for details.
     * @param key The key to append to.
     * @param value The value to append.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch append(GlideString key, GlideString value) {
        return addCommand(CommandType.APPEND, key.toString(), value.toString());
    }

    /**
     * Returns a substring of the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/getrange/">valkey.io</a> for details.
     * @param key The key to get range from.
     * @param start The start index.
     * @param end The end index.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch getrange(String key, int start, int end) {
        return addCommand(CommandType.GETRANGE, key, String.valueOf(start), String.valueOf(end));
    }

    /**
     * Returns a substring of the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/getrange/">valkey.io</a> for details.
     * @param key The key to get range from.
     * @param start The start index.
     * @param end The end index.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch getrange(GlideString key, int start, int end) {
        return addCommand(CommandType.GETRANGE, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    /**
     * Overwrites part of the string stored at key.
     *
     * @see <a href="https://valkey.io/commands/setrange/">valkey.io</a> for details.
     * @param key The key to modify.
     * @param offset The offset to start overwriting at.
     * @param value The value to overwrite with.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch setrange(String key, int offset, String value) {
        return addCommand(CommandType.SETRANGE, key, String.valueOf(offset), value);
    }

    /**
     * Overwrites part of the string stored at key.
     *
     * @see <a href="https://valkey.io/commands/setrange/">valkey.io</a> for details.
     * @param key The key to modify.
     * @param offset The offset to start overwriting at.
     * @param value The value to overwrite with.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch setrange(GlideString key, int offset, GlideString value) {
        return addCommand(CommandType.SETRANGE, key.toString(), String.valueOf(offset), value.toString());
    }

    /**
     * Delete one or more hash fields.
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to delete.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hdel(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return addCommand(CommandType.HDEL, args);
    }

    /**
     * Delete one or more hash fields.
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to delete.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hdel(GlideString key, GlideString... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < fields.length; i++) {
            args[i + 1] = fields[i].toString();
        }
        return addCommand(CommandType.HDEL, args);
    }

    /**
     * Check if a hash field exists.
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hexists(String key, String field) {
        return addCommand(CommandType.HEXISTS, key, field);
    }

    /**
     * Check if a hash field exists.
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hexists(GlideString key, GlideString field) {
        return addCommand(CommandType.HEXISTS, key.toString(), field.toString());
    }

    /**
     * Get the number of fields in a hash.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hlen(String key) {
        return addCommand(CommandType.HLEN, key);
    }

    /**
     * Get the number of fields in a hash.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hlen(GlideString key) {
        return addCommand(CommandType.HLEN, key.toString());
    }

    /**
     * Get all field names in a hash.
     *
     * @see <a href="https://valkey.io/commands/hkeys/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hkeys(String key) {
        return addCommand(CommandType.HKEYS, key);
    }

    /**
     * Get all field names in a hash.
     *
     * @see <a href="https://valkey.io/commands/hkeys/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hkeys(GlideString key) {
        return addCommand(CommandType.HKEYS, key.toString());
    }

    /**
     * Get all values in a hash.
     *
     * @see <a href="https://valkey.io/commands/hvals/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hvals(String key) {
        return addCommand(CommandType.HVALS, key);
    }

    /**
     * Get all values in a hash.
     *
     * @see <a href="https://valkey.io/commands/hvals/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hvals(GlideString key) {
        return addCommand(CommandType.HVALS, key.toString());
    }

    /**
     * Get the values of all specified hash fields.
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hmget(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return addCommand(CommandType.HMGET, args);
    }

    /**
     * Get the values of all specified hash fields.
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hmget(GlideString key, GlideString... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < fields.length; i++) {
            args[i + 1] = fields[i].toString();
        }
        return addCommand(CommandType.HMGET, args);
    }

    /**
     * Increment the integer value of a hash field by amount.
     *
     * @see <a href="https://valkey.io/commands/hincrby/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hincrBy(String key, String field, long amount) {
        return addCommand(CommandType.HINCRBY, key, field, String.valueOf(amount));
    }

    /**
     * Increment the integer value of a hash field by amount.
     *
     * @see <a href="https://valkey.io/commands/hincrby/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hincrBy(GlideString key, GlideString field, long amount) {
        return addCommand(CommandType.HINCRBY, key.toString(), field.toString(), String.valueOf(amount));
    }

    /**
     * Increment the floating-point value of a hash field by amount.
     *
     * @see <a href="https://valkey.io/commands/hincrbyfloat/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hincrByFloat(String key, String field, double amount) {
        return addCommand(CommandType.HINCRBYFLOAT, key, field, String.valueOf(amount));
    }

    /**
     * Increment the floating-point value of a hash field by amount.
     *
     * @see <a href="https://valkey.io/commands/hincrbyfloat/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch hincrByFloat(GlideString key, GlideString field, double amount) {
        return addCommand(CommandType.HINCRBYFLOAT, key.toString(), field.toString(), String.valueOf(amount));
    }

    // List Commands
    public ClusterBatch rpush(String key, String... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(CommandType.RPUSH, args);
    }

    public ClusterBatch rpush(GlideString key, GlideString... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return addCommand(CommandType.RPUSH, args);
    }

    public ClusterBatch lpop(String key) {
        return addCommand(CommandType.LPOP, key);
    }

    public ClusterBatch lpop(GlideString key) {
        return addCommand(CommandType.LPOP, key.toString());
    }

    public ClusterBatch rpop(String key) {
        return addCommand(CommandType.RPOP, key);
    }

    public ClusterBatch rpop(GlideString key) {
        return addCommand(CommandType.RPOP, key.toString());
    }

    public ClusterBatch lrange(String key, long start, long end) {
        return addCommand(CommandType.LRANGE, key, String.valueOf(start), String.valueOf(end));
    }

    public ClusterBatch lrange(GlideString key, long start, long end) {
        return addCommand(CommandType.LRANGE, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    public ClusterBatch llen(String key) {
        return addCommand(CommandType.LLEN, key);
    }

    public ClusterBatch llen(GlideString key) {
        return addCommand(CommandType.LLEN, key.toString());
    }

    public ClusterBatch lindex(String key, long index) {
        return addCommand(CommandType.LINDEX, key, String.valueOf(index));
    }

    public ClusterBatch lindex(GlideString key, long index) {
        return addCommand(CommandType.LINDEX, key.toString(), String.valueOf(index));
    }

    public ClusterBatch lset(String key, long index, String element) {
        return addCommand(CommandType.LSET, key, String.valueOf(index), element);
    }

    public ClusterBatch lset(GlideString key, long index, GlideString element) {
        return addCommand(CommandType.LSET, key.toString(), String.valueOf(index), element.toString());
    }

    public ClusterBatch ltrim(String key, long start, long end) {
        return addCommand(CommandType.LTRIM, key, String.valueOf(start), String.valueOf(end));
    }

    public ClusterBatch ltrim(GlideString key, long start, long end) {
        return addCommand(CommandType.LTRIM, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    public ClusterBatch lrem(String key, long count, String element) {
        return addCommand(CommandType.LREM, key, String.valueOf(count), element);
    }

    public ClusterBatch lrem(GlideString key, long count, GlideString element) {
        return addCommand(CommandType.LREM, key.toString(), String.valueOf(count), element.toString());
    }

    // Set Commands
    
    /**
     * Add one or more members to a set.
     *
     * @see <a href="https://valkey.io/commands/sadd/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to add.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sadd(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(CommandType.SADD, args);
    }

    /**
     * Add one or more members to a set.
     *
     * @see <a href="https://valkey.io/commands/sadd/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to add.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sadd(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(CommandType.SADD, args);
    }

    /**
     * Remove one or more members from a set.
     *
     * @see <a href="https://valkey.io/commands/srem/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch srem(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(CommandType.SREM, args);
    }

    /**
     * Remove one or more members from a set.
     *
     * @see <a href="https://valkey.io/commands/srem/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch srem(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(CommandType.SREM, args);
    }

    /**
     * Get all members of a set.
     *
     * @see <a href="https://valkey.io/commands/smembers/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch smembers(String key) {
        return addCommand(CommandType.SMEMBERS, key);
    }

    /**
     * Get all members of a set.
     *
     * @see <a href="https://valkey.io/commands/smembers/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch smembers(GlideString key) {
        return addCommand(CommandType.SMEMBERS, key.toString());
    }

    /**
     * Get the number of members in a set.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch scard(String key) {
        return addCommand(CommandType.SCARD, key);
    }

    /**
     * Get the number of members in a set.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch scard(GlideString key) {
        return addCommand(CommandType.SCARD, key.toString());
    }

    /**
     * Check if a member is in a set.
     *
     * @see <a href="https://valkey.io/commands/sismember/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sismember(String key, String member) {
        return addCommand(CommandType.SISMEMBER, key, member);
    }

    /**
     * Check if a member is in a set.
     *
     * @see <a href="https://valkey.io/commands/sismember/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sismember(GlideString key, GlideString member) {
        return addCommand(CommandType.SISMEMBER, key.toString(), member.toString());
    }

    /**
     * Return the difference of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sdiff/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sdiff(String... keys) {
        return addCommand(CommandType.SDIFF, keys);
    }

    /**
     * Return the difference of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sdiff/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sdiff(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.SDIFF, stringKeys);
    }

    /**
     * Return the intersection of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sinter/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sinter(String... keys) {
        return addCommand(CommandType.SINTER, keys);
    }

    /**
     * Return the intersection of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sinter/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sinter(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.SINTER, stringKeys);
    }

    /**
     * Return the union of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sunion/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sunion(String... keys) {
        return addCommand(CommandType.SUNION, keys);
    }

    /**
     * Return the union of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sunion/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch sunion(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.SUNION, stringKeys);
    }

    // Key Management Commands
    
    /**
     * Set a timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set timeout on.
     * @param seconds The timeout in seconds.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch expire(String key, long seconds) {
        return addCommand(CommandType.EXPIRE, key, String.valueOf(seconds));
    }

    /**
     * Set a timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set timeout on.
     * @param seconds The timeout in seconds.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch expire(GlideString key, long seconds) {
        return addCommand(CommandType.EXPIRE, key.toString(), String.valueOf(seconds));
    }

    /**
     * Get the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch ttl(String key) {
        return addCommand(CommandType.TTL, key);
    }

    /**
     * Get the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch ttl(GlideString key) {
        return addCommand(CommandType.TTL, key.toString());
    }

    // Sorted Set Commands
    
    /**
     * Add one or more members to a sorted set, or update the score if the member already exists.
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param scoreMembers Alternating scores and members (score1, member1, score2, member2, ...).
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zadd(String key, String... scoreMembers) {
        String[] args = new String[scoreMembers.length + 1];
        args[0] = key;
        System.arraycopy(scoreMembers, 0, args, 1, scoreMembers.length);
        return addCommand(CommandType.ZADD, args);
    }

    /**
     * Add one or more members to a sorted set, or update the score if the member already exists.
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param scoreMembers Alternating scores and members (score1, member1, score2, member2, ...).
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zadd(GlideString key, GlideString... scoreMembers) {
        String[] args = new String[scoreMembers.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < scoreMembers.length; i++) {
            args[i + 1] = scoreMembers[i].toString();
        }
        return addCommand(CommandType.ZADD, args);
    }

    /**
     * Return a range of members in a sorted set, by index.
     *
     * @see <a href="https://valkey.io/commands/zrange/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param start The start index.
     * @param end The end index.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zrange(String key, long start, long end) {
        return addCommand(CommandType.ZRANGE, key, String.valueOf(start), String.valueOf(end));
    }

    /**
     * Return a range of members in a sorted set, by index.
     *
     * @see <a href="https://valkey.io/commands/zrange/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param start The start index.
     * @param end The end index.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zrange(GlideString key, long start, long end) {
        return addCommand(CommandType.ZRANGE, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    /**
     * Remove one or more members from a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zrem/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zrem(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(CommandType.ZREM, args);
    }

    /**
     * Remove one or more members from a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zrem/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zrem(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(CommandType.ZREM, args);
    }

    /**
     * Get the number of members in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zcard/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zcard(String key) {
        return addCommand(CommandType.ZCARD, key);
    }

    /**
     * Get the number of members in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zcard/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zcard(GlideString key) {
        return addCommand(CommandType.ZCARD, key.toString());
    }

    /**
     * Get the score associated with the given member in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscore/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose score to retrieve.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zscore(String key, String member) {
        return addCommand(CommandType.ZSCORE, key, member);
    }

    /**
     * Get the score associated with the given member in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscore/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose score to retrieve.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zscore(GlideString key, GlideString member) {
        return addCommand(CommandType.ZSCORE, key.toString(), member.toString());
    }

    /**
     * Get the rank of the member in the sorted set, with scores ordered from low to high.
     *
     * @see <a href="https://valkey.io/commands/zrank/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose rank to determine.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zrank(String key, String member) {
        return addCommand(CommandType.ZRANK, key, member);
    }

    /**
     * Get the rank of the member in the sorted set, with scores ordered from low to high.
     *
     * @see <a href="https://valkey.io/commands/zrank/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose rank to determine.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch zrank(GlideString key, GlideString member) {
        return addCommand(CommandType.ZRANK, key.toString(), member.toString());
    }

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch publish(String message, String channel) {
        return addCommand(CommandType.PUBLISH, message, channel);
    }

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch publish(GlideString message, GlideString channel) {
        return addCommand(CommandType.PUBLISH, message.toString(), channel.toString());
    }

    /**
     * Publishes message on pubsub channel, with an option to use sharded publish.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for standard publish.
     * @see <a href="https://valkey.io/commands/spublish/">valkey.io</a> for sharded publish.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @param sharded If true, uses SPUBLISH (sharded publish), otherwise uses PUBLISH.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch publish(String message, String channel, boolean sharded) {
        CommandType commandType = sharded ? CommandType.SPUBLISH : CommandType.PUBLISH;
        return addCommand(commandType, message, channel);
    }

    /**
     * Publishes message on pubsub channel, with an option to use sharded publish.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for standard publish.
     * @see <a href="https://valkey.io/commands/spublish/">valkey.io</a> for sharded publish.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @param sharded If true, uses SPUBLISH (sharded publish), otherwise uses PUBLISH.
     * @return This batch instance for method chaining.
     */
    public ClusterBatch publish(GlideString message, GlideString channel, boolean sharded) {
        CommandType commandType = sharded ? CommandType.SPUBLISH : CommandType.PUBLISH;
        return addCommand(commandType, message.toString(), channel.toString());
    }
}