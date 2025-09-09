/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.api.GlideClient;
import static glide.api.models.commands.RequestType.*;

/**
 * Batch implementation for standalone {@link GlideClient}. Batches allow the execution of a group
 * of commands in a single step.
 *
 * <p>Batch Response: An <code>array</code> of command responses is returned by the client {@link
 * GlideClient#exec} API, in the order they were given. Each element in the array represents a
 * command given to the {@link Batch}. The response for each command depends on the executed Valkey
 * command. Specific response types are documented alongside each method.
 *
 * <p><strong>isAtomic:</strong> Determines whether the batch is atomic or non-atomic. If {@code
 * true}, the batch will be executed as an atomic transaction. If {@code false}, the batch will be
 * executed as a non-atomic pipeline.
 *
 * @see <a href="https://valkey.io/docs/topics/transactions/">Valkey Transactions (Atomic
 *     Batches)</a>
 * @see <a href="https://valkey.io/topics/pipelining">Valkey Pipelines (Non-Atomic Batches)</a>
 * @apiNote Standalone Batches are executed on the primary node.
 * @example
 *     <pre>{@code
 * // Example of Atomic Batch (Transaction)
 * Batch transaction = new Batch(true) // Atomic (Transactional)
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
 * // Example of Non-Atomic Batch (Pipeline)
 * Batch pipeline = new Batch(false) // Non-Atomic (Pipeline)
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
public class Batch extends BaseBatch<Batch> {

    /**
     * Creates a new Batch instance.
     *
     * @param isAtomic Determines whether the batch is atomic or non-atomic. If {@code true}, the
     *     batch will be executed as an atomic transaction. If {@code false}, the batch will be
     *     executed as a non-atomic pipeline.
     */
    public Batch(boolean isAtomic) {
        super(isAtomic);
    }

    /**
     * Creates a new non-atomic Batch instance (pipeline).
     */
    public Batch() {
        super(false);
    }

    /**
     * Returns this batch instance with the correct type.
     *
     * @return This batch instance
     */
    @Override
    protected Batch getThis() {
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
    public Batch set(String key, String value) {
        return addCommand(SET, key, value);
    }


    /**
     * Sets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @return This batch instance for method chaining.
     */
    public Batch set(GlideString key, GlideString value) {
        boolean keyBinary = (key != null && !key.canConvertToString());
        boolean valueBinary = (value != null && !value.canConvertToString());
        if (keyBinary || valueBinary) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(SET);
            if (keyBinary) {
                cmd.addArgument(key.getBytes());
            } else {
                cmd.addArgument(key.toString());
            }
            if (valueBinary) {
                cmd.addArgument(value.getBytes());
            } else {
                cmd.addArgument(value.toString());
            }
            return addCommand(cmd);
        }
        return addCommand(SET, key.toString(), value.toString());
    }

    public Batch set(GlideString key, String value) {
        return addCommand(SET, key.toString(), value);
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    public Batch get(String key) {
        return addCommand(GET, key);
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    public Batch get(GlideString key) {
        return addCommand(GET, key.toString());
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */
    public Batch del(String... keys) {
        return addCommand(Del, keys);
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */
    public Batch del(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(Del, stringKeys);
    }

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    public Batch ping() {
        return addCommand(Ping);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */
    public Batch ping(String message) {
        return addCommand(Ping, message);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */
    public Batch ping(GlideString message) {
        return addCommand(Ping, message.toString());
    }

    /**
     * Increments the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key to increment.
     * @return This batch instance for method chaining.
     */
    public Batch incr(String key) {
        return addCommand(Incr, key);
    }

    /**
     * Increments the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key to increment.
     * @return This batch instance for method chaining.
     */
    public Batch incr(GlideString key) {
        return addCommand(Incr, key.toString());
    }

    /**
     * Increments the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrby/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public Batch incrBy(String key, long amount) {
        return addCommand(IncrBy, key, String.valueOf(amount));
    }

    /**
     * Increments the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrby/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public Batch incrBy(GlideString key, long amount) {
        return addCommand(IncrBy, key.toString(), String.valueOf(amount));
    }

    /**
     * Increments the floating-point number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrbyfloat/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public Batch incrByFloat(String key, double amount) {
        return addCommand(IncrByFloat, key, String.valueOf(amount));
    }

    /**
     * Increments the floating-point number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/incrbyfloat/">valkey.io</a> for details.
     * @param key The key to increment.
     * @param amount The amount to increment by.
     * @return This batch instance for method chaining.
     */
    public Batch incrByFloat(GlideString key, double amount) {
        return addCommand(IncrByFloat, key.toString(), String.valueOf(amount));
    }

    /**
     * Decrements the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @return This batch instance for method chaining.
     */
    public Batch decr(String key) {
        return addCommand(Decr, key);
    }

    /**
     * Decrements the number stored at key by one.
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @return This batch instance for method chaining.
     */
    public Batch decr(GlideString key) {
        return addCommand(Decr, key.toString());
    }

    /**
     * Decrements the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/decrby/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @param amount The amount to decrement by.
     * @return This batch instance for method chaining.
     */
    public Batch decrBy(String key, long amount) {
        return addCommand(DecrBy, key, String.valueOf(amount));
    }

    /**
     * Decrements the number stored at key by amount.
     *
     * @see <a href="https://valkey.io/commands/decrby/">valkey.io</a> for details.
     * @param key The key to decrement.
     * @param amount The amount to decrement by.
     * @return This batch instance for method chaining.
     */
    public Batch decrBy(GlideString key, long amount) {
        return addCommand(DecrBy, key.toString(), String.valueOf(amount));
    }

    /**
     * Returns the length of the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key to get length for.
     * @return This batch instance for method chaining.
     */
    public Batch strlen(String key) {
        return addCommand(Strlen, key);
    }

    /**
     * Returns the length of the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key to get length for.
     * @return This batch instance for method chaining.
     */
    public Batch strlen(GlideString key) {
        return addCommand(Strlen, key.toString());
    }

    /**
     * Appends a value to a key.
     *
     * @see <a href="https://valkey.io/commands/append/">valkey.io</a> for details.
     * @param key The key to append to.
     * @param value The value to append.
     * @return This batch instance for method chaining.
     */
    public Batch append(String key, String value) {
        return addCommand(Append, key, value);
    }

    /**
     * Appends a value to a key.
     *
     * @see <a href="https://valkey.io/commands/append/">valkey.io</a> for details.
     * @param key The key to append to.
     * @param value The value to append.
     * @return This batch instance for method chaining.
     */
    public Batch append(GlideString key, GlideString value) {
        return addCommand(Append, key.toString(), value.toString());
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
    public Batch getrange(String key, int start, int end) {
        return addCommand(GetRange, key, String.valueOf(start), String.valueOf(end));
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
    public Batch getrange(GlideString key, int start, int end) {
        return addCommand(GetRange, key.toString(), String.valueOf(start), String.valueOf(end));
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
    public Batch setrange(String key, int offset, String value) {
        return addCommand(SetRange, key, String.valueOf(offset), value);
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
    public Batch setrange(GlideString key, int offset, GlideString value) {
        return addCommand(SetRange, key.toString(), String.valueOf(offset), value.toString());
    }

    /**
     * Delete one or more hash fields.
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to delete.
     * @return This batch instance for method chaining.
     */
    public Batch hdel(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return addCommand(HDel, args);
    }

    /**
     * Delete one or more hash fields.
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to delete.
     * @return This batch instance for method chaining.
     */
    public Batch hdel(GlideString key, GlideString... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < fields.length; i++) {
            args[i + 1] = fields[i].toString();
        }
        return addCommand(HDel, args);
    }

    /**
     * Check if a hash field exists.
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check.
     * @return This batch instance for method chaining.
     */
    public Batch hexists(String key, String field) {
        return addCommand(HExists, key, field);
    }

    /**
     * Check if a hash field exists.
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to check.
     * @return This batch instance for method chaining.
     */
    public Batch hexists(GlideString key, GlideString field) {
        return addCommand(HExists, key.toString(), field.toString());
    }

    /**
     * GET the number of fields in a hash.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public Batch hlen(String key) {
        return addCommand(HLen, key);
    }

    /**
     * GET the number of fields in a hash.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public Batch hlen(GlideString key) {
        return addCommand(HLen, key.toString());
    }

    /**
     * GET all field names in a hash.
     *
     * @see <a href="https://valkey.io/commands/hkeys/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public Batch hkeys(String key) {
        return addCommand(HKeys, key);
    }

    /**
     * GET all field names in a hash.
     *
     * @see <a href="https://valkey.io/commands/hkeys/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public Batch hkeys(GlideString key) {
        return addCommand(HKeys, key.toString());
    }

    /**
     * GET all values in a hash.
     *
     * @see <a href="https://valkey.io/commands/hvals/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public Batch hvals(String key) {
        return addCommand(HVals, key);
    }

    /**
     * GET all values in a hash.
     *
     * @see <a href="https://valkey.io/commands/hvals/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    public Batch hvals(GlideString key) {
        return addCommand(HVals, key.toString());
    }

    /**
     * GET the values of all specified hash fields.
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get.
     * @return This batch instance for method chaining.
     */
    public Batch hmget(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return addCommand(HMGet, args);
    }

    /**
     * GET the values of all specified hash fields.
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fields The fields to get.
     * @return This batch instance for method chaining.
     */
    public Batch hmget(GlideString key, GlideString... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < fields.length; i++) {
            args[i + 1] = fields[i].toString();
        }
        return addCommand(HMGet, args);
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
    public Batch hincrBy(String key, String field, long amount) {
        return addCommand(HIncrBy, key, field, String.valueOf(amount));
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
    public Batch hincrBy(GlideString key, GlideString field, long amount) {
        return addCommand(HIncrBy, key.toString(), field.toString(), String.valueOf(amount));
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
    public Batch hincrByFloat(String key, String field, double amount) {
        return addCommand(HIncrByFloat, key, field, String.valueOf(amount));
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
    public Batch hincrByFloat(GlideString key, GlideString field, double amount) {
        return addCommand(HIncrByFloat, key.toString(), field.toString(), String.valueOf(amount));
    }

    /**
     * Inserts elements at the tail of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/rpush/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to push.
     * @return This batch instance for method chaining.
     */
    public Batch rpush(String key, String... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(RPush, args);
    }

    /**
     * Inserts elements at the tail of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/rpush/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to push.
     * @return This batch instance for method chaining.
     */
    public Batch rpush(GlideString key, GlideString... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return addCommand(RPush, args);
    }

    /**
     * Removes and returns the first element from the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/lpop/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    public Batch lpop(String key) {
        return addCommand(LPop, key);
    }

    /**
     * Removes and returns the first element from the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/lpop/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    public Batch lpop(GlideString key) {
        return addCommand(LPop, key.toString());
    }

    /**
     * Removes and returns the last element from the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/rpop/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    public Batch rpop(String key) {
        return addCommand(RPop, key);
    }

    /**
     * Removes and returns the last element from the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/rpop/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    public Batch rpop(GlideString key) {
        return addCommand(RPop, key.toString());
    }

    /**
     * Returns the specified elements of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/lrange/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param start The starting index.
     * @param end The ending index.
     * @return This batch instance for method chaining.
     */
    public Batch lrange(String key, long start, long end) {
        return addCommand(LRange, key, String.valueOf(start), String.valueOf(end));
    }

    /**
     * Returns the specified elements of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/lrange/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param start The starting index.
     * @param end The ending index.
     * @return This batch instance for method chaining.
     */
    public Batch lrange(GlideString key, long start, long end) {
        return addCommand(LRange, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/llen/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    public Batch llen(String key) {
        return addCommand(LLen, key);
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/llen/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    public Batch llen(GlideString key) {
        return addCommand(LLen, key.toString());
    }

    /**
     * Returns the element at index in the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/lindex/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element to return.
     * @return This batch instance for method chaining.
     */
    public Batch lindex(String key, long index) {
        return addCommand(LIndex, key, String.valueOf(index));
    }

    /**
     * Returns the element at index in the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/lindex/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param index The index of the element to return.
     * @return This batch instance for method chaining.
     */
    public Batch lindex(GlideString key, long index) {
        return addCommand(LIndex, key.toString(), String.valueOf(index));
    }

    /**
     * Sets the list element at index to element.
     *
     * @see <a href="https://valkey.io/commands/lset/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param index The index to set the element at.
     * @param element The element to set.
     * @return This batch instance for method chaining.
     */
    public Batch lset(String key, long index, String element) {
        return addCommand(LSet, key, String.valueOf(index), element);
    }

    /**
     * Sets the list element at index to element.
     *
     * @see <a href="https://valkey.io/commands/lset/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param index The index to set the element at.
     * @param element The element to set.
     * @return This batch instance for method chaining.
     */
    public Batch lset(GlideString key, long index, GlideString element) {
        return addCommand(LSet, key.toString(), String.valueOf(index), element.toString());
    }

    /**
     * Trim the list to the specified range.
     *
     * @see <a href="https://valkey.io/commands/ltrim/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param start The starting index.
     * @param end The ending index.
     * @return This batch instance for method chaining.
     */
    public Batch ltrim(String key, long start, long end) {
        return addCommand(LTrim, key, String.valueOf(start), String.valueOf(end));
    }

    /**
     * Trim the list to the specified range.
     *
     * @see <a href="https://valkey.io/commands/ltrim/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param start The starting index.
     * @param end The ending index.
     * @return This batch instance for method chaining.
     */
    public Batch ltrim(GlideString key, long start, long end) {
        return addCommand(LTrim, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    /**
     * Removes the first count occurrences of elements equal to element from the list.
     *
     * @see <a href="https://valkey.io/commands/lrem/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param count The number of elements to remove.
     * @param element The element to remove.
     * @return This batch instance for method chaining.
     */
    public Batch lrem(String key, long count, String element) {
        return addCommand(LRem, key, String.valueOf(count), element);
    }

    /**
     * Removes the first count occurrences of elements equal to element from the list.
     *
     * @see <a href="https://valkey.io/commands/lrem/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param count The number of elements to remove.
     * @param element The element to remove.
     * @return This batch instance for method chaining.
     */
    public Batch lrem(GlideString key, long count, GlideString element) {
        return addCommand(LRem, key.toString(), String.valueOf(count), element.toString());
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
    public Batch sadd(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(SAdd, args);
    }

    /**
     * Add one or more members to a set.
     *
     * @see <a href="https://valkey.io/commands/sadd/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to add.
     * @return This batch instance for method chaining.
     */
    public Batch sadd(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(SAdd, args);
    }

    public Batch sadd(GlideString key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(SAdd, args);
    }

    /**
     * Remove one or more members from a set.
     *
     * @see <a href="https://valkey.io/commands/srem/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public Batch srem(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(SRem, args);
    }

    /**
     * Remove one or more members from a set.
     *
     * @see <a href="https://valkey.io/commands/srem/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public Batch srem(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(SRem, args);
    }

    /**
     * GET all members of a set.
     *
     * @see <a href="https://valkey.io/commands/smembers/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public Batch smembers(String key) {
        return addCommand(SMembers, key);
    }

    /**
     * GET all members of a set.
     *
     * @see <a href="https://valkey.io/commands/smembers/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public Batch smembers(GlideString key) {
        return addCommand(SMembers, key.toString());
    }

    /**
     * GET the number of members in a set.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public Batch scard(String key) {
        return addCommand(SCard, key);
    }

    /**
     * GET the number of members in a set.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    public Batch scard(GlideString key) {
        return addCommand(SCard, key.toString());
    }

    /**
     * Check if a member is in a set.
     *
     * @see <a href="https://valkey.io/commands/sismember/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check.
     * @return This batch instance for method chaining.
     */
    public Batch sismember(String key, String member) {
        return addCommand(SIsMember, key, member);
    }

    /**
     * Check if a member is in a set.
     *
     * @see <a href="https://valkey.io/commands/sismember/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param member The member to check.
     * @return This batch instance for method chaining.
     */
    public Batch sismember(GlideString key, GlideString member) {
        return addCommand(SIsMember, key.toString(), member.toString());
    }

    /**
     * Return the difference of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sdiff/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public Batch sdiff(String... keys) {
        return addCommand(SDiff, keys);
    }

    /**
     * Return the difference of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sdiff/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public Batch sdiff(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(SDiff, stringKeys);
    }

    /**
     * Return the intersection of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sinter/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public Batch sinter(String... keys) {
        return addCommand(SInter, keys);
    }

    /**
     * Return the intersection of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sinter/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public Batch sinter(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(SInter, stringKeys);
    }

    /**
     * Return the union of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sunion/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public Batch sunion(String... keys) {
        return addCommand(SUnion, keys);
    }

    /**
     * Return the union of multiple sets.
     *
     * @see <a href="https://valkey.io/commands/sunion/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    public Batch sunion(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(SUnion, stringKeys);
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
    public Batch expire(String key, long seconds) {
        return addCommand(EXPIRE, key, String.valueOf(seconds));
    }

    /**
     * Set a timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set timeout on.
     * @param seconds The timeout in seconds.
     * @return This batch instance for method chaining.
     */
    public Batch expire(GlideString key, long seconds) {
        return addCommand(EXPIRE, key.toString(), String.valueOf(seconds));
    }

    /**
     * GET the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public Batch ttl(String key) {
        return addCommand(TTL, key);
    }

    /**
     * GET the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public Batch ttl(GlideString key) {
        return addCommand(TTL, key.toString());
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
    public Batch zadd(String key, String... scoreMembers) {
        String[] args = new String[scoreMembers.length + 1];
        args[0] = key;
        System.arraycopy(scoreMembers, 0, args, 1, scoreMembers.length);
        return addCommand(ZAdd, args);
    }

    /**
     * Add one or more members to a sorted set, or update the score if the member already exists.
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param scoreMembers Alternating scores and members (score1, member1, score2, member2, ...).
     * @return This batch instance for method chaining.
     */
    public Batch zadd(GlideString key, GlideString... scoreMembers) {
        String[] args = new String[scoreMembers.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < scoreMembers.length; i++) {
            args[i + 1] = scoreMembers[i].toString();
        }
        return addCommand(ZAdd, args);
    }

    public Batch zadd(GlideString key, java.util.Map<String, Double> membersScoresMap) {
        String[] args = new String[membersScoresMap.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (java.util.Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args[index++] = entry.getValue().toString();
            args[index++] = entry.getKey();
        }
        return addCommand(ZAdd, args);
    }

    public Batch zadd(String key, java.util.Map<String, Double> membersScoresMap) {
        String[] args = new String[membersScoresMap.size() * 2 + 1];
        args[0] = key;
        int index = 1;
        for (java.util.Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args[index++] = entry.getValue().toString();
            args[index++] = entry.getKey();
        }
        return addCommand(ZAdd, args);
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
    public Batch zrange(String key, long start, long end) {
        return addCommand(ZRange, key, String.valueOf(start), String.valueOf(end));
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
    public Batch zrange(GlideString key, long start, long end) {
        return addCommand(ZRange, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    /**
     * Remove one or more members from a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zrem/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public Batch zrem(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(ZRem, args);
    }

    /**
     * Remove one or more members from a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zrem/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */
    public Batch zrem(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(ZRem, args);
    }

    /**
     * GET the number of members in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zcard/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @return This batch instance for method chaining.
     */
    public Batch zcard(String key) {
        return addCommand(ZCard, key);
    }

    /**
     * GET the number of members in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zcard/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @return This batch instance for method chaining.
     */
    public Batch zcard(GlideString key) {
        return addCommand(ZCard, key.toString());
    }

    /**
     * GET the score associated with the given member in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscore/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose score to retrieve.
     * @return This batch instance for method chaining.
     */
    public Batch zscore(String key, String member) {
        return addCommand(ZScore, key, member);
    }

    /**
     * GET the score associated with the given member in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zscore/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose score to retrieve.
     * @return This batch instance for method chaining.
     */
    public Batch zscore(GlideString key, GlideString member) {
        return addCommand(ZScore, key.toString(), member.toString());
    }

    /**
     * GET the rank of the member in the sorted set, with scores ordered from low to high.
     *
     * @see <a href="https://valkey.io/commands/zrank/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose rank to determine.
     * @return This batch instance for method chaining.
     */
    public Batch zrank(String key, String member) {
        return addCommand(ZRank, key, member);
    }

    /**
     * GET the rank of the member in the sorted set, with scores ordered from low to high.
     *
     * @see <a href="https://valkey.io/commands/zrank/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member whose rank to determine.
     * @return This batch instance for method chaining.
     */
    public Batch zrank(GlideString key, GlideString member) {
        return addCommand(ZRank, key.toString(), member.toString());
    }

    /**
     * Execute a custom command with the given arguments.
     *
     * @param args The command arguments.
     * @return This batch instance for method chaining.
     */
    public Batch customCommand(String... args) {
        if (args == null || args.length == 0) {
            return getThis();
        }
        String primary = args[0];
        String[] remaining = java.util.Arrays.copyOfRange(args, 1, args.length);
        return addCommand(primary, remaining);
    }

    /**
     * Returns if the given keys exist in the database.
     *
     * @see <a href="https://valkey.io/commands/exists/">valkey.io</a> for details.
     * @param keys The keys to check.
     * @return This batch instance for method chaining.
     */
    public Batch exists(String... keys) {
        return addCommand(Exists, keys);
    }

    /**
     * Returns if the given keys exist in the database.
     *
     * @see <a href="https://valkey.io/commands/exists/">valkey.io</a> for details.
     * @param keys The keys to check.
     * @return This batch instance for method chaining.
     */
    public Batch exists(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(Exists, stringKeys);
    }

    /**
     * Remove the existing timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/persist/">valkey.io</a> for details.
     * @param key The key to remove timeout from.
     * @return This batch instance for method chaining.
     */
    public Batch persist(String key) {
        return addCommand(Persist, key);
    }

    /**
     * Remove the existing timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/persist/">valkey.io</a> for details.
     * @param key The key to remove timeout from.
     * @return This batch instance for method chaining.
     */
    public Batch persist(GlideString key) {
        return addCommand(Persist, key.toString());
    }

    /**
     * Returns the string representation of the type of the value stored at key.
     *
     * @see <a href="https://valkey.io/commands/type/">valkey.io</a> for details.
     * @param key The key to check the type of.
     * @return This batch instance for method chaining.
     */
    public Batch type(String key) {
        return addCommand(Type, key);
    }

    /**
     * Returns the string representation of the type of the value stored at key.
     *
     * @see <a href="https://valkey.io/commands/type/">valkey.io</a> for details.
     * @param key The key to check the type of.
     * @return This batch instance for method chaining.
     */
    public Batch type(GlideString key) {
        return addCommand(Type, key.toString());
    }

    /**
     * Returns the internal encoding of the Redis object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for details.
     * @param key The key to check the encoding of.
     * @return This batch instance for method chaining.
     */
    public Batch objectEncoding(String key) {
        return addCommand(ObjectEncoding, key);
    }

    /**
     * Returns the internal encoding of the Redis object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for details.
     * @param key The key to check the encoding of.
     * @return This batch instance for method chaining.
     */
    public Batch objectEncoding(GlideString key) {
        return addCommand(ObjectEncoding, key.toString());
    }

    /**
     * Alters the last access time of the given keys.
     *
     * @see <a href="https://valkey.io/commands/touch/">valkey.io</a> for details.
     * @param keys The keys to touch.
     * @return This batch instance for method chaining.
     */
    public Batch touch(String... keys) {
        return addCommand(Touch, keys);
    }

    /**
     * Alters the last access time of the given keys.
     *
     * @see <a href="https://valkey.io/commands/touch/">valkey.io</a> for details.
     * @param keys The keys to touch.
     * @return This batch instance for method chaining.
     */
    public Batch touch(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(Touch, stringKeys);
    }

    /**
     * Renames key to newkey.
     *
     * @see <a href="https://valkey.io/commands/rename/">valkey.io</a> for details.
     * @param key The key to rename.
     * @param newkey The new key name.
     * @return This batch instance for method chaining.
     */
    public Batch rename(String key, String newkey) {
        return addCommand(Rename, key, newkey);
    }

    /**
     * Renames key to newkey.
     *
     * @see <a href="https://valkey.io/commands/rename/">valkey.io</a> for details.
     * @param key The key to rename.
     * @param newkey The new key name.
     * @return This batch instance for method chaining.
     */
    public Batch rename(GlideString key, GlideString newkey) {
        return addCommand(Rename, key.toString(), newkey.toString());
    }

    /**
     * Renames key to newkey if newkey does not yet exist.
     *
     * @see <a href="https://valkey.io/commands/renamenx/">valkey.io</a> for details.
     * @param key The key to rename.
     * @param newkey The new key name.
     * @return This batch instance for method chaining.
     */
    public Batch renamenx(String key, String newkey) {
        return addCommand(RenameNX, key, newkey);
    }

    /**
     * Renames key to newkey if newkey does not yet exist.
     *
     * @see <a href="https://valkey.io/commands/renamenx/">valkey.io</a> for details.
     * @param key The key to rename.
     * @param newkey The new key name.
     * @return This batch instance for method chaining.
     */
    public Batch renamenx(GlideString key, GlideString newkey) {
        return addCommand(RenameNX, key.toString(), newkey.toString());
    }

    /**
     * Unlinks (deletes) the key(s) in a non-blocking way.
     *
     * @see <a href="https://valkey.io/commands/unlink/">valkey.io</a> for details.
     * @param keys The keys to unlink.
     * @return This batch instance for method chaining.
     */
    public Batch unlink(String... keys) {
        return addCommand(UNLINK, keys);
    }

    /**
     * Unlinks (deletes) the key(s) in a non-blocking way.
     *
     * @see <a href="https://valkey.io/commands/unlink/">valkey.io</a> for details.
     * @param keys The keys to unlink.
     * @return This batch instance for method chaining.
     */
    public Batch unlink(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(UNLINK, stringKeys);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    public Batch sort(String key) {
        return addCommand(Sort, key);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    public Batch sort(GlideString key) {
        return addCommand(Sort, key.toString());
    }

    /**
     * Sorts the elements in the list, set or sorted set at key and stores the result in destination.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @param destination The key to store the result in.
     * @return This batch instance for method chaining.
     */
    public Batch sortStore(String key, String destination) {
        return addCommand(Sort, key, "STORE", destination);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key and stores the result in destination.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @param destination The key to store the result in.
     * @return This batch instance for method chaining.
     */
    public Batch sortStore(GlideString key, GlideString destination) {
        return addCommand(Sort, key.toString(), "STORE", destination.toString());
    }

    /**
     * Sorts the elements in the list, set or sorted set at key (read-only).
     *
     * @see <a href="https://valkey.io/commands/sort_ro/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    public Batch sortReadOnly(String key) {
        return addCommand(SortReadOnly, key);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key (read-only).
     *
     * @see <a href="https://valkey.io/commands/sort_ro/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    public Batch sortReadOnly(GlideString key) {
        return addCommand(SortReadOnly, key.toString());
    }

    /**
     * Flushes all the databases.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    public Batch flushall() {
        return addCommand(FlushAll);
    }

    /**
     * Flushes all the databases.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flush mode (SYNC or ASYNC).
     * @return This batch instance for method chaining.
     */
    public Batch flushall(glide.api.models.commands.FlushMode mode) {
        return addCommand(FlushAll, mode.toString());
    }

    /**
     * Flushes the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    public Batch flushdb() {
        return addCommand(FlushDB);
    }

    /**
     * Flushes the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/flushdb/">valkey.io</a> for details.
     * @param mode The flush mode (SYNC or ASYNC).
     * @return This batch instance for method chaining.
     */
    public Batch flushdb(glide.api.models.commands.FlushMode mode) {
        return addCommand(FlushDB, mode.toString());
    }

    /**
     * Returns the number of keys in the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/dbsize/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    public Batch dbsize() {
        return addCommand(DBSize);
    }

    /**
     * Returns a random key from the currently selected database.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    public Batch randomKey() {
        return addCommand(RandomKey);
    }

    /**
     * Sets configuration parameters to the given values.
     *
     * @see <a href="https://valkey.io/commands/config-set/">valkey.io</a> for details.
     * @param parameters The configuration parameters to set.
     * @return This batch instance for method chaining.
     */
    public Batch configSet(java.util.Map<String, String> parameters) {
        String[] args = new String[parameters.size() * 2];
        int index = 0;
        for (java.util.Map.Entry<String, String> entry : parameters.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(ConfigSet, args);
    }

    /**
     * Gets the values of configuration parameters.
     *
     * @see <a href="https://valkey.io/commands/config-get/">valkey.io</a> for details.
     * @param parameters The configuration parameters to get.
     * @return This batch instance for method chaining.
     */
    public Batch configGet(String... parameters) {
        return addCommand(ConfigGet, parameters);
    }

    /**
     * Resets the statistics reported by the INFO command.
     *
     * @see <a href="https://valkey.io/commands/config-resetstat/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    public Batch configResetStat() {
        return addCommand(ConfigResetStat);
    }

    /**
     * Displays computer art and the Redis version.
     *
     * @see <a href="https://valkey.io/commands/lolwut/">valkey.io</a> for details.
     * @param version The version of the art to display.
     * @return This batch instance for method chaining.
     */
    public Batch lolwut(int version) {
        return addCommand(Lolwut, "VERSION", String.valueOf(version));
    }

    /**
     * GET the value of a key after deleting it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */
    public Batch getdel(String key) {
        return addCommand(GETDEL, key);
    }

    /**
     * GET the value of a key after deleting it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */
    public Batch getdel(GlideString key) {
        return addCommand(GETDEL, key.toString());
    }

    /**
     * Atomically sets multiple key-value pairs.
     *
     * @see <a href="https://valkey.io/commands/mset/">valkey.io</a> for details.
     * @param keyValuePairs The key-value pairs to set.
     * @return This batch instance for method chaining.
     */
    public Batch mset(java.util.Map<String, String> keyValuePairs) {
        String[] args = new String[keyValuePairs.size() * 2];
        int index = 0;
        for (java.util.Map.Entry<String, String> entry : keyValuePairs.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(MSet, args);
    }

    /**
     * Gets the values of multiple keys.
     *
     * @see <a href="https://valkey.io/commands/mget/">valkey.io</a> for details.
     * @param keys The keys to get.
     * @return This batch instance for method chaining.
     */
    public Batch mget(String... keys) {
        return addCommand(MGet, keys);
    }

    /**
     * Sets multiple key-value pairs only if none of the keys exist.
     *
     * @see <a href="https://valkey.io/commands/msetnx/">valkey.io</a> for details.
     * @param keyValuePairs The key-value pairs to set.
     * @return This batch instance for method chaining.
     */
    public Batch msetnx(java.util.Map<String, String> keyValuePairs) {
        String[] args = new String[keyValuePairs.size() * 2];
        int index = 0;
        for (java.util.Map.Entry<String, String> entry : keyValuePairs.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(MSetNX, args);
    }

    /**
     * Prepends one or more values to the beginning of a list.
     *
     * @see <a href="https://valkey.io/commands/lpush/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to prepend.
     * @return This batch instance for method chaining.
     */
    public Batch lpush(String key, String... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(LPush, args);
    }

    /**
     * Prepends one or more values to the beginning of a list.
     *
     * @see <a href="https://valkey.io/commands/lpush/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to prepend.
     * @return This batch instance for method chaining.
     */
    public Batch lpush(GlideString key, GlideString... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return addCommand(LPush, args);
    }

    public Batch lpush(GlideString key, String... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(LPush, args);
    }

    /**
     * Sets the value of a key with options.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @param options The set options.
     * @return This batch instance for method chaining.
     */
    public Batch set(String key, String value, glide.api.models.commands.SetOptions options) {
        if (options == null) {
            return set(key, value);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key;
        args[1] = value;
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(SET, args);
    }

    /**
     * Sets the value of a key with options.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @param options The set options.
     * @return This batch instance for method chaining.
     */
    public Batch set(GlideString key, GlideString value, glide.api.models.commands.SetOptions options) {
        if (options == null) {
            return set(key, value);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key.toString();
        args[1] = value.toString();
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(SET, args);
    }

    public Batch hset(GlideString key, java.util.Map<String, String> fieldValuePairs) {
        String[] args = new String[fieldValuePairs.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (java.util.Map.Entry<String, String> entry : fieldValuePairs.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(HSet, args);
    }

    public Batch hset(String key, java.util.Map<String, String> fieldValuePairs) {
        String[] args = new String[fieldValuePairs.size() * 2 + 1];
        args[0] = key;
        int index = 1;
        for (java.util.Map.Entry<String, String> entry : fieldValuePairs.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(HSet, args);
    }

    public Batch xadd(GlideString key, java.util.Map<String, String> values) {
        String[] args = new String[values.size() * 2 + 2];
        args[0] = key.toString();
        args[1] = "*";
        int i = 2;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return addCommand(XAdd, args);
    }

    public Batch xadd(String key, java.util.Map<String, String> values) {
        String[] args = new String[values.size() * 2 + 2];
        args[0] = key;
        args[1] = "*";
        int i = 2;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return addCommand(XAdd, args);
    }

    /**
     * GET the value of a key with options.
     *
     * @see <a href="https://valkey.io/commands/getex/">valkey.io</a> for details.
     * @param key The key to get.
     * @param options The get options.
     * @return This batch instance for method chaining.
     */
    public Batch getex(String key, glide.api.models.commands.GetExOptions options) {
        if (options == null) {
            return get(key);
        }
        String[] args = new String[options.toArgs().length + 1];
        args[0] = key;
        System.arraycopy(options.toArgs(), 0, args, 1, options.toArgs().length);
        return addCommand(GETEX, args);
    }

    /**
     * GET the value of a key with options.
     *
     * @see <a href="https://valkey.io/commands/getex/">valkey.io</a> for details.
     * @param key The key to get.
     * @param options The get options.
     * @return This batch instance for method chaining.
     */
    public Batch getex(GlideString key, glide.api.models.commands.GetExOptions options) {
        if (options == null) {
            return get(key);
        }
        String[] args = new String[options.toArgs().length + 1];
        args[0] = key.toString();
        System.arraycopy(options.toArgs(), 0, args, 1, options.toArgs().length);
        return addCommand(GETEX, args);
    }

    /**
     * GET the value of a key with options.
     *
     * @see <a href="https://valkey.io/commands/getex/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    public Batch getex(String key) {
        return addCommand(GETEX, key);
    }

    /**
     * GET the value of a key with options.
     *
     * @see <a href="https://valkey.io/commands/getex/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    public Batch getex(GlideString key) {
        return addCommand(GETEX, key.toString());
    }

    /**
     * Set an expiration time on a key.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param seconds The expiration time in seconds.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch expire(String key, long seconds, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return expire(key, seconds);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key;
        args[1] = String.valueOf(seconds);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(EXPIRE, args);
    }

    /**
     * Set an expiration time on a key.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param seconds The expiration time in seconds.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch expire(GlideString key, long seconds, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return expire(key, seconds);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key.toString();
        args[1] = String.valueOf(seconds);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(EXPIRE, args);
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp.
     *
     * @see <a href="https://valkey.io/commands/expireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp when the key should expire.
     * @return This batch instance for method chaining.
     */
    public Batch expireAt(String key, long timestamp) {
        return addCommand(EXPIREAT, key, String.valueOf(timestamp));
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp.
     *
     * @see <a href="https://valkey.io/commands/expireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp when the key should expire.
     * @return This batch instance for method chaining.
     */
    public Batch expireAt(GlideString key, long timestamp) {
        return addCommand(EXPIREAT, key.toString(), String.valueOf(timestamp));
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp with options.
     *
     * @see <a href="https://valkey.io/commands/expireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp when the key should expire.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch expireAt(String key, long timestamp, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return expireAt(key, timestamp);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key;
        args[1] = String.valueOf(timestamp);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(EXPIREAT, args);
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp with options.
     *
     * @see <a href="https://valkey.io/commands/expireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp when the key should expire.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch expireAt(GlideString key, long timestamp, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return expireAt(key, timestamp);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key.toString();
        args[1] = String.valueOf(timestamp);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(EXPIREAT, args);
    }

    /**
     * Set an expiration time on a key in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param milliseconds The expiration time in milliseconds.
     * @return This batch instance for method chaining.
     */
    public Batch pexpire(String key, long milliseconds) {
        return addCommand(PEXPIRE, key, String.valueOf(milliseconds));
    }

    /**
     * Set an expiration time on a key in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param milliseconds The expiration time in milliseconds.
     * @return This batch instance for method chaining.
     */
    public Batch pexpire(GlideString key, long milliseconds) {
        return addCommand(PEXPIRE, key.toString(), String.valueOf(milliseconds));
    }

    /**
     * Set an expiration time on a key in milliseconds with options.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param milliseconds The expiration time in milliseconds.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch pexpire(String key, long milliseconds, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return pexpire(key, milliseconds);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key;
        args[1] = String.valueOf(milliseconds);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(PEXPIRE, args);
    }

    /**
     * Set an expiration time on a key in milliseconds with options.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param milliseconds The expiration time in milliseconds.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch pexpire(GlideString key, long milliseconds, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return pexpire(key, milliseconds);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key.toString();
        args[1] = String.valueOf(milliseconds);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(PEXPIRE, args);
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp in milliseconds when the key should expire.
     * @return This batch instance for method chaining.
     */
    public Batch pexpireAt(String key, long timestamp) {
        return addCommand(PEXPIREAT, key, String.valueOf(timestamp));
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp in milliseconds when the key should expire.
     * @return This batch instance for method chaining.
     */
    public Batch pexpireAt(GlideString key, long timestamp) {
        return addCommand(PEXPIREAT, key.toString(), String.valueOf(timestamp));
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp in milliseconds with options.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp in milliseconds when the key should expire.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch pexpireAt(String key, long timestamp, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return pexpireAt(key, timestamp);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key;
        args[1] = String.valueOf(timestamp);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(PEXPIREAT, args);
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp in milliseconds with options.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp in milliseconds when the key should expire.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */
    public Batch pexpireAt(GlideString key, long timestamp, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return pexpireAt(key, timestamp);
        }
        String[] args = new String[options.toArgs().length + 2];
        args[0] = key.toString();
        args[1] = String.valueOf(timestamp);
        System.arraycopy(options.toArgs(), 0, args, 2, options.toArgs().length);
        return addCommand(PEXPIREAT, args);
    }

    /**
     * GET the expiration time of a key in Unix timestamp format.
     *
     * @see <a href="https://valkey.io/commands/expiretime/">valkey.io</a> for details.
     * @param key The key to get the expiration time of.
     * @return This batch instance for method chaining.
     */
    public Batch expiretime(String key) {
        return addCommand(ExpireTime, key);
    }

    /**
     * GET the expiration time of a key in Unix timestamp format.
     *
     * @see <a href="https://valkey.io/commands/expiretime/">valkey.io</a> for details.
     * @param key The key to get the expiration time of.
     * @return This batch instance for method chaining.
     */
    public Batch expiretime(GlideString key) {
        return addCommand(ExpireTime, key.toString());
    }

    /**
     * GET the expiration time of a key in Unix timestamp format in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpiretime/">valkey.io</a> for details.
     * @param key The key to get the expiration time of.
     * @return This batch instance for method chaining.
     */
    public Batch pexpiretime(String key) {
        return addCommand(PExpireTime, key);
    }

    /**
     * GET the expiration time of a key in Unix timestamp format in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpiretime/">valkey.io</a> for details.
     * @param key The key to get the expiration time of.
     * @return This batch instance for method chaining.
     */
    public Batch pexpiretime(GlideString key) {
        return addCommand(PExpireTime, key.toString());
    }

    /**
     * Copies the value from a source key to a destination key.
     *
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The source key.
     * @param destination The destination key.
     * @return This batch instance for method chaining.
     */
    public Batch copy(String source, String destination) {
        return addCommand(Copy, source, destination);
    }

    /**
     * Copies the value from a source key to a destination key.
     *
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The source key.
     * @param destination The destination key.
     * @return This batch instance for method chaining.
     */
    public Batch copy(GlideString source, GlideString destination) {
        return addCommand(Copy, source.toString(), destination.toString());
    }

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return This batch instance for method chaining.
     */
    public Batch publish(String message, String channel) {
        return addCommand(PUBLISH, channel, message);
    }

    /**
     * Publishes message on pubsub channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param message The message to publish.
     * @param channel The channel to publish the message on.
     * @return This batch instance for method chaining.
     */
    public Batch publish(GlideString message, GlideString channel) {
        return addCommand(PUBLISH, channel.toString(), message.toString());
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
    public Batch publish(String message, String channel, boolean sharded) {
        String commandType = sharded ? SPublish : PUBLISH;
        return addCommand(commandType, channel, message);
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
    public Batch publish(GlideString message, GlideString channel, boolean sharded) {
        String commandType = sharded ? SPublish : PUBLISH;
        return addCommand(commandType, channel.toString(), message.toString());
    }
}