/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import glide.internal.protocol.Command;
import glide.internal.protocol.CommandInterface;
import glide.internal.protocol.BinaryCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import static glide.api.models.commands.RequestType.*;
import glide.api.models.commands.scan.HScanOptions;
import glide.api.models.commands.scan.SScanOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.ExpireOptions;
import glide.api.models.commands.SortOptions;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.RangeOptions;
import glide.api.models.commands.bitmap.BitwiseOperation;

/**
 * Base class for all batch operations.
 * This is a simple data structure for collecting commands to be executed as a batch.
 * Command implementations are located in the client classes (BaseClient, GlideClient, etc.).
 */
public abstract class BaseBatch<T extends BaseBatch<T>> {
    /** List of commands to execute as a batch (may include BinaryCommand) */
    protected final List<CommandInterface> commands;

    /** Whether this batch should be executed atomically (transaction) */
    protected final boolean isAtomic;

    /** Whether results should preserve binary output (GlideString) rather than decode to Java String */
    protected boolean binaryOutput = false;

    /**
     * Creates a new BaseBatch instance.
     *
     * @param isAtomic Whether the batch should be executed atomically
     */
    protected BaseBatch(boolean isAtomic) {
        this.isAtomic = isAtomic;
        this.commands = new ArrayList<>();
    }

    /**
     * Add a command to the batch.
     *
     * @param command The command to add
     * @return This batch instance for method chaining
     */
    public T addCommand(Command command) { // backward compat for existing code
        commands.add(command);
        return getThis();
    }

    /** Add any command implementing CommandInterface (e.g. BinaryCommand). */
    public T addCommand(CommandInterface command) {
        commands.add(command);
        return getThis();
    }

    /**
     * Add a command to the batch by type and arguments.
     *
     * @param commandType The command type
     * @param args The command arguments
     * @return This batch instance for method chaining
     */
    public T addCommand(String commandType, String... args) {
        commands.add(new Command(commandType, args));
        return getThis();
    }

    /**
     * Returns this batch instance with the correct type.
     * This method must be implemented by subclasses to avoid unchecked cast warnings.
     *
     * @return This batch instance
     */
    protected abstract T getThis();

    /**
     * GET all commands in this batch.
     *
     * @return List of commands
     */
    public List<CommandInterface> getCommands() {
        return new ArrayList<>(commands);
    }

    /**
     * Check if this batch is atomic (transaction).
     *
     * @return true if atomic, false otherwise
     */
    public boolean isAtomic() {
        return isAtomic;
    }

    /**
     * GET the number of commands in this batch.
     *
     * @return Number of commands
     */
    public int size() {
        return commands.size();
    }

    /**
     * Check if this batch is empty.
     *
     * @return true if empty, false otherwise
     */
    public boolean isEmpty() {
        return commands.isEmpty();
    }

    // Common batch methods that are used by both Batch and ClusterBatch

    /**
     * Sets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @return This batch instance for method chaining.
     */

    public T set(String key, String value) {
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

    public T set(GlideString key, GlideString value) {
        // Preserve raw bytes when either side is not UTF-8 convertible. Using toString() on a
        // non-convertible GlideString produces a diagnostic placeholder which corrupts the
        // original binary payload (e.g. DUMP/RESTORE round trips inside a batch). For binary
        // safety we emit a BinaryCommand mixing text and raw bytes as needed.
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

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */

    public T get(String key) {
        return addCommand(GET, key);
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */

    public T get(GlideString key) {
        // Preserve binary data for non-UTF8 keys
        if (key != null && !key.canConvertToString()) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(GET);
            cmd.addArgument(key.getBytes());
            return addCommand(cmd);
        }
        return addCommand(GET, key.toString());
    }

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */

    public T ping() {
        return addCommand(Ping);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */

    public T ping(String message) {
        return addCommand(Ping, message);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */

    public T ping(GlideString message) {
        return addCommand(Ping, message.toString());
    }

    /**
     * Flushes all the databases.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */

    public T flushall() {
        return addCommand(FlushAll);
    }

    /**
     * Flushes all the databases.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flush mode (SYNC or ASYNC).
     * @return This batch instance for method chaining.
     */

    public T flushall(glide.api.models.commands.FlushMode mode) {
        return addCommand(FlushAll, mode.toString());
    }

    /**
     * Prepends one or more values to the beginning of a list.
     *
     * @see <a href="https://valkey.io/commands/lpush/">valkey.io</a> for details.
     * @param key The key of the list.
     * @param elements The elements to prepend.
     * @return This batch instance for method chaining.
     */

    public T lpush(String key, String... elements) {
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

    public T lpush(GlideString key, GlideString... elements) {
        // Check if any data is binary
        boolean hasBinary = (key != null && !key.canConvertToString());
        for (GlideString elem : elements) {
            if (elem != null && !elem.canConvertToString()) {
                hasBinary = true;
                break;
            }
        }
        
        if (hasBinary) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(LPush);
            cmd.addArgument(key.getBytes());
            for (GlideString elem : elements) {
                cmd.addArgument(elem.getBytes());
            }
            return addCommand(cmd);
        }
        
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return addCommand(LPush, args);
    }

    /**
     * Add one or more members to a set.
     *
     * @see <a href="https://valkey.io/commands/sadd/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to add.
     * @return This batch instance for method chaining.
     */

    public T sadd(String key, String... members) {
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

    public T sadd(GlideString key, GlideString... members) {
        // Check if any data is binary
        boolean hasBinary = (key != null && !key.canConvertToString());
        for (GlideString member : members) {
            if (member != null && !member.canConvertToString()) {
                hasBinary = true;
                break;
            }
        }
        
        if (hasBinary) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(SAdd);
            cmd.addArgument(key.getBytes());
            for (GlideString member : members) {
                cmd.addArgument(member.getBytes());
            }
            return addCommand(cmd);
        }
        
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(SAdd, args);
    }

    /**
     * Set one or more hash fields to values.
     *
     * @see <a href="https://valkey.io/commands/hset/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValuePairs The field-value pairs to set.
     * @return This batch instance for method chaining.
     */

    public T hset(String key, Map<String, String> fieldValuePairs) {
        String[] args = new String[fieldValuePairs.size() * 2 + 1];
        args[0] = key;
        int index = 1;
        for (Map.Entry<String, String> entry : fieldValuePairs.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(HSet, args);
    }

    /**
     * Set one or more hash fields to values.
     *
     * @see <a href="https://valkey.io/commands/hset/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValuePairs The field-value pairs to set.
     * @return This batch instance for method chaining.
     */

    public T hset(GlideString key, Map<String, String> fieldValuePairs) {
        String[] args = new String[fieldValuePairs.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (Map.Entry<String, String> entry : fieldValuePairs.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(HSet, args);
    }

    /**
     * Add one or more members to a sorted set, or update the score if the member already exists.
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A map of members to their scores.
     * @return This batch instance for method chaining.
     */

    public T zadd(String key, Map<String, Double> membersScoresMap) {
        String[] args = new String[membersScoresMap.size() * 2 + 1];
        args[0] = key;
        int index = 1;
        for (Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args[index++] = entry.getValue().toString();
            args[index++] = entry.getKey();
        }
        return addCommand(ZAdd, args);
    }

    /**
     * Add one or more members to a sorted set, or update the score if the member already exists.
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A map of members to their scores.
     * @return This batch instance for method chaining.
     */

    public T zadd(GlideString key, Map<String, Double> membersScoresMap) {
        String[] args = new String[membersScoresMap.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (Map.Entry<String, Double> entry : membersScoresMap.entrySet()) {
            args[index++] = entry.getValue().toString();
            args[index++] = entry.getKey();
        }
        return addCommand(ZAdd, args);
    }

    /**
     * Adds one or more elements to the HyperLogLog stored at key.
     *
     * @see <a href="https://valkey.io/commands/pfadd/">valkey.io</a> for details.
     * @param key The key of the HyperLogLog.
     * @param elements The elements to add.
     * @return This batch instance for method chaining.
     */

    public T pfadd(String key, String... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(PfAdd, args);
    }

    /**
     * Adds one or more elements to the HyperLogLog stored at key.
     *
     * @see <a href="https://valkey.io/commands/pfadd/">valkey.io</a> for details.
     * @param key The key of the HyperLogLog.
     * @param elements The elements to add.
     * @return This batch instance for method chaining.
     */

    public T pfadd(GlideString key, GlideString... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return addCommand(PfAdd, args);
    }

    /**
     * Execute a custom command with the given arguments.
     *
     * @param args The command arguments.
     * @return This batch instance for method chaining.
     */

    public T customCommand(String... args) {
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

    public T exists(String... keys) {
        return addCommand(Exists, keys);
    }

    /**
     * Returns if the given keys exist in the database.
     *
     * @see <a href="https://valkey.io/commands/exists/">valkey.io</a> for details.
     * @param keys The keys to check.
     * @return This batch instance for method chaining.
     */

    public T exists(GlideString... keys) {
        // Check if any key is binary
        boolean hasBinary = false;
        for (GlideString key : keys) {
            if (key != null && !key.canConvertToString()) {
                hasBinary = true;
                break;
            }
        }
        
        if (hasBinary) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(Exists);
            for (GlideString key : keys) {
                cmd.addArgument(key.getBytes());
            }
            return addCommand(cmd);
        }
        
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

    public T persist(String key) {
        return addCommand(Persist, key);
    }

    /**
     * Remove the existing timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/persist/">valkey.io</a> for details.
     * @param key The key to remove timeout from.
     * @return This batch instance for method chaining.
     */

    public T persist(GlideString key) {
        return addCommand(Persist, key.toString());
    }

    /**
     * Returns the string representation of the type of the value stored at key.
     *
     * @see <a href="https://valkey.io/commands/type/">valkey.io</a> for details.
     * @param key The key to check the type of.
     * @return This batch instance for method chaining.
     */

    public T type(String key) {
        return addCommand(Type, key);
    }

    /**
     * Returns the string representation of the type of the value stored at key.
     *
     * @see <a href="https://valkey.io/commands/type/">valkey.io</a> for details.
     * @param key The key to check the type of.
     * @return This batch instance for method chaining.
     */

    public T type(GlideString key) {
        return addCommand(Type, key.toString());
    }

    /**
     * Returns the internal encoding of the Valkey object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for
     *      details.
     * @param key The key to check the encoding of.
     * @return This batch instance for method chaining.
     */

    public T objectEncoding(String key) {
        return addCommand(ObjectEncoding, key);
    }

    /**
     * Returns the internal encoding of the Valkey object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for
     *      details.
     * @param key The key to check the encoding of.
     * @return This batch instance for method chaining.
     */

    public T objectEncoding(GlideString key) {
        return addCommand(ObjectEncoding, key.toString());
    }

    /**
     * Alters the last access time of the given keys.
     *
     * @see <a href="https://valkey.io/commands/touch/">valkey.io</a> for details.
     * @param keys The keys to touch.
     * @return This batch instance for method chaining.
     */

    public T touch(String... keys) {
        return addCommand(Touch, keys);
    }

    /**
     * Alters the last access time of the given keys.
     *
     * @see <a href="https://valkey.io/commands/touch/">valkey.io</a> for details.
     * @param keys The keys to touch.
     * @return This batch instance for method chaining.
     */

    public T touch(GlideString... keys) {
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

    public T rename(String key, String newkey) {
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

    public T rename(GlideString key, GlideString newkey) {
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

    public T renamenx(String key, String newkey) {
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

    public T renamenx(GlideString key, GlideString newkey) {
        return addCommand(RenameNX, key.toString(), newkey.toString());
    }

    /**
     * Unlinks (deletes) the key(s) in a non-blocking way.
     *
     * @see <a href="https://valkey.io/commands/unlink/">valkey.io</a> for details.
     * @param keys The keys to unlink.
     * @return This batch instance for method chaining.
     */

    public T unlink(String... keys) {
        return addCommand(UNLINK, keys);
    }

    /**
     * Unlinks (deletes) the key(s) in a non-blocking way.
     *
     * @see <a href="https://valkey.io/commands/unlink/">valkey.io</a> for details.
     * @param keys The keys to unlink.
     * @return This batch instance for method chaining.
     */

    public T unlink(GlideString... keys) {
        // Check if any key is binary
        boolean hasBinary = false;
        for (GlideString key : keys) {
            if (key != null && !key.canConvertToString()) {
                hasBinary = true;
                break;
            }
        }
        
        if (hasBinary) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(UNLINK);
            for (GlideString key : keys) {
                cmd.addArgument(key.getBytes());
            }
            return addCommand(cmd);
        }
        
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(UNLINK, stringKeys);
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */

    public T del(String... keys) {
        return addCommand(Del, keys);
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */

    public T del(GlideString... keys) {
        // Check if any key is binary
        boolean hasBinary = false;
        for (GlideString key : keys) {
            if (key != null && !key.canConvertToString()) {
                hasBinary = true;
                break;
            }
        }
        
        if (hasBinary) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(Del);
            for (GlideString key : keys) {
                cmd.addArgument(key.getBytes());
            }
            return addCommand(cmd);
        }
        
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(Del, stringKeys);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */

    public T sort(String key) {
        return addCommand(Sort, key);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */

    public T sort(GlideString key) {
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

    public T sortStore(String key, String destination) {
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

    public T sortStore(GlideString key, GlideString destination) {
        return addCommand(Sort, key.toString(), "STORE", destination.toString());
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     * Read-only version of SORT.
     *
     * @see <a href="https://valkey.io/commands/sort_ro/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    public T sortReadOnly(String key) {
        return addCommand(SortReadOnly, key);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     * Read-only version of SORT.
     *
     * @see <a href="https://valkey.io/commands/sort_ro/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    public T sortReadOnly(GlideString key) {
        return addCommand(SortReadOnly, key.toString());
    }

    /**
     * Sorts the elements in the list, set or sorted set at key with options.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key     The key to sort.
     * @param options The sort options.
     * @return This batch instance for method chaining.
     */
    public T sort(String key, SortOptions options) {
        String[] args = options.toArgs();
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = key;
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        return addCommand(Sort, fullArgs);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key with options.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key     The key to sort.
     * @param options The sort options.
     * @return This batch instance for method chaining.
     */
    public T sort(GlideString key, SortOptions options) {
        String[] args = options.toArgs();
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = key.toString();
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        return addCommand(Sort, fullArgs);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key with options.
     * Read-only version of SORT.
     *
     * @see <a href="https://valkey.io/commands/sort_ro/">valkey.io</a> for details.
     * @param key     The key to sort.
     * @param options The sort options.
     * @return This batch instance for method chaining.
     */
    public T sortReadOnly(String key, SortOptions options) {
        String[] args = options.toArgs();
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = key;
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        return addCommand(SortReadOnly, fullArgs);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key with options.
     * Read-only version of SORT.
     *
     * @see <a href="https://valkey.io/commands/sort_ro/">valkey.io</a> for details.
     * @param key     The key to sort.
     * @param options The sort options.
     * @return This batch instance for method chaining.
     */
    public T sortReadOnly(GlideString key, SortOptions options) {
        String[] args = options.toArgs();
        String[] fullArgs = new String[args.length + 1];
        fullArgs[0] = key.toString();
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        return addCommand(SortReadOnly, fullArgs);
    }

    /**
     * Set a timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set timeout on.
     * @param seconds The timeout in seconds.
     * @return This batch instance for method chaining.
     */

    public T expire(String key, long seconds) {
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

    public T expire(GlideString key, long seconds) {
        return addCommand(EXPIRE, key.toString(), String.valueOf(seconds));
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp.
     *
     * @see <a href="https://valkey.io/commands/expireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp when the key should expire.
     * @return This batch instance for method chaining.
     */

    public T expireAt(String key, long timestamp) {
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

    public T expireAt(GlideString key, long timestamp) {
        return addCommand(EXPIREAT, key.toString(), String.valueOf(timestamp));
    }

    /**
     * Set an expiration time on a key in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param milliseconds The expiration time in milliseconds.
     * @return This batch instance for method chaining.
     */

    public T pexpire(String key, long milliseconds) {
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

    public T pexpire(GlideString key, long milliseconds) {
        return addCommand(PEXPIRE, key.toString(), String.valueOf(milliseconds));
    }

    /**
     * Set an expiration time on a key in milliseconds with conditional options.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">valkey.io</a> for details.
     * @param key           The key to set expiration on.
     * @param milliseconds  The expiration time in milliseconds.
     * @param expireOptions The expiration conditions.
     * @return This batch instance for method chaining.
     */
    public T pexpire(String key, long milliseconds, ExpireOptions expireOptions) {
        String[] args = expireOptions.toArgs();
        String[] fullArgs = new String[args.length + 2];
        fullArgs[0] = key;
        fullArgs[1] = String.valueOf(milliseconds);
        System.arraycopy(args, 0, fullArgs, 2, args.length);
        return addCommand(PEXPIRE, fullArgs);
    }

    /**
     * Set an expiration time on a key in milliseconds with conditional options.
     *
     * @see <a href="https://valkey.io/commands/pexpire/">valkey.io</a> for details.
     * @param key           The key to set expiration on.
     * @param milliseconds  The expiration time in milliseconds.
     * @param expireOptions The expiration conditions.
     * @return This batch instance for method chaining.
     */
    public T pexpire(GlideString key, long milliseconds, ExpireOptions expireOptions) {
        String[] args = expireOptions.toArgs();
        String[] fullArgs = new String[args.length + 2];
        fullArgs[0] = key.toString();
        fullArgs[1] = String.valueOf(milliseconds);
        System.arraycopy(args, 0, fullArgs, 2, args.length);
        return addCommand(PEXPIRE, fullArgs);
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp in milliseconds when the key should expire.
     * @return This batch instance for method chaining.
     */

    public T pexpireAt(String key, long timestamp) {
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

    public T pexpireAt(GlideString key, long timestamp) {
        return addCommand(PEXPIREAT, key.toString(), String.valueOf(timestamp));
    }

    /**
     * Set a timeout on key in milliseconds. After the timeout has expired, the key
     * will
     * automatically be deleted.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">valkey.io</a> for
     *      details.
     * @param key           The key to set timeout on.
     * @param timestamp     The timeout in milliseconds.
     * @param expireOptions The expire options.
     * @return This batch instance for method chaining.
     */

    public T pexpireAt(String key, long timestamp, ExpireOptions expireOptions) {
        String[] args = expireOptions.toArgs();
        String[] fullArgs = new String[args.length + 2];
        fullArgs[0] = key;
        fullArgs[1] = String.valueOf(timestamp);
        System.arraycopy(args, 0, fullArgs, 2, args.length);
        return addCommand(PEXPIREAT, fullArgs);
    }

    /**
     * Set a timeout on key in milliseconds. After the timeout has expired, the key
     * will
     * automatically be deleted.
     *
     * @see <a href="https://valkey.io/commands/pexpireat/">valkey.io</a> for
     *      details.
     * @param key           The key to set timeout on.
     * @param timestamp     The timeout in milliseconds.
     * @param expireOptions The expire options.
     * @return This batch instance for method chaining.
     */

    public T pexpireAt(GlideString key, long timestamp, ExpireOptions expireOptions) {
        String[] args = expireOptions.toArgs();
        String[] fullArgs = new String[args.length + 2];
        fullArgs[0] = key.toString();
        fullArgs[1] = String.valueOf(timestamp);
        System.arraycopy(args, 0, fullArgs, 2, args.length);
        return addCommand(PEXPIREAT, fullArgs);
    }

    /**
     * GET the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */

    public T ttl(String key) {
        return addCommand(TTL, key);
    }

    /**
     * GET the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */

    public T ttl(GlideString key) {
        return addCommand(TTL, key.toString());
    }

    /**
     * GET the remaining time to live of a key that has a timeout in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public T pttl(String key) {
        return addCommand(PTTL, key);
    }

    /**
     * GET the remaining time to live of a key that has a timeout in milliseconds.
     *
     * @see <a href="https://valkey.io/commands/pttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public T pttl(GlideString key) {
        return addCommand(PTTL, key.toString());
    }

    /**
     * Returns the absolute Unix timestamp in seconds at which the given key will
     * expire.
     *
     * @see <a href="https://valkey.io/commands/expiretime/">valkey.io</a> for
     *      details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public T expiretime(String key) {
        return addCommand(ExpireTime, key);
    }

    /**
     * Returns the absolute Unix timestamp in seconds at which the given key will
     * expire.
     *
     * @see <a href="https://valkey.io/commands/expiretime/">valkey.io</a> for
     *      details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public T expiretime(GlideString key) {
        return addCommand(ExpireTime, key.toString());
    }

    /**
     * Returns the absolute Unix timestamp in milliseconds at which the given key
     * will expire.
     *
     * @see <a href="https://valkey.io/commands/pexpiretime/">valkey.io</a> for
     *      details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public T pexpiretime(String key) {
        return addCommand(PExpireTime, key);
    }

    /**
     * Returns the absolute Unix timestamp in milliseconds at which the given key
     * will expire.
     *
     * @see <a href="https://valkey.io/commands/pexpiretime/">valkey.io</a> for
     *      details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    public T pexpiretime(GlideString key) {
        return addCommand(PExpireTime, key.toString());
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

    public T lrange(String key, long start, long end) {
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

    public T lrange(GlideString key, long start, long end) {
        return addCommand(LRange, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    /**
     * Copies the value stored at the source key to the destination key.
     *
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The source key.
     * @param destination The destination key.
     * @param replace If true, remove the destination key if it exists before copying.
     * @return This batch instance for method chaining.
     */

    public T copy(String source, String destination, boolean replace) {
        if (replace) {
            return addCommand(Copy, source, destination, "REPLACE");
        } else {
            return addCommand(Copy, source, destination);
        }
    }

    /**
     * Copies the value stored at the source key to the destination key.
     *
     * @see <a href="https://valkey.io/commands/copy/">valkey.io</a> for details.
     * @param source The source key.
     * @param destination The destination key.
     * @param replace If true, remove the destination key if it exists before copying.
     * @return This batch instance for method chaining.
     */

    public T copy(GlideString source, GlideString destination, boolean replace) {
        if (replace) {
            return addCommand(Copy, source.toString(), destination.toString(), "REPLACE");
        } else {
            return addCommand(Copy, source.toString(), destination.toString());
        }
    }


    /**
     * Deletes all function libraries.
     *
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @param mode The flush mode.
     * @return This batch instance for method chaining.
     */

    public T functionFlush(glide.api.models.commands.FlushMode mode) {
        return addCommand(FunctionFlush, mode.toString());
    }

    /**
     * Deletes all function libraries.
     *
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */

    public T functionFlush() {
        return addCommand(FunctionFlush);
    }

    /**
     * Returns a random key from the keyspace.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */

    public T randomKey() {
        return addCommand(RandomKey);
    }

    /**
     * Finds the longest common subsequence between strings stored at key1 and key2.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */

    public T lcs(String key1, String key2) {
        return addCommand(LCS, key1, key2);
    }

    /**
     * Finds the longest common subsequence between strings stored at key1 and key2.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */

    public T lcs(GlideString key1, GlideString key2) {
        return addCommand(LCS, key1.toString(), key2.toString());
    }

    /**
     * Gets the value of a key and optionally sets its expiration.
     *
     * @see <a href="https://valkey.io/commands/getex/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */

    public T getex(String key) {
        return addCommand(GETEX, key);
    }

    /**
     * Gets the value of a key and optionally sets its expiration.
     *
     * @see <a href="https://valkey.io/commands/getex/">valkey.io</a> for details.
     * @param key The key to get.
     * @param options Options for the getex command.
     * @return This batch instance for method chaining.
     */

    public T getex(String key, glide.api.models.commands.GetExOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key);
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return addCommand(GETEX, args.toArray(new String[0]));
    }

    /**
     * Gets the value of a hash field.
     *
     * @see <a href="https://valkey.io/commands/hget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to get.
     * @return This batch instance for method chaining.
     */

    public T hget(String key, String field) {
        return addCommand(HGet, key, field);
    }

    /**
     * Gets the value of a hash field.
     *
     * @see <a href="https://valkey.io/commands/hget/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param field The field to get.
     * @return This batch instance for method chaining.
     */

    public T hget(GlideString key, GlideString field) {
        // Preserve binary data for non-UTF8 keys or fields
        if ((key != null && !key.canConvertToString()) || (field != null && !field.canConvertToString())) {
            glide.internal.protocol.BinaryCommand cmd = new glide.internal.protocol.BinaryCommand(HGet);
            cmd.addArgument(key.getBytes());
            cmd.addArgument(field.getBytes());
            return addCommand(cmd);
        }
        return addCommand(HGet, key.toString(), field.toString());
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/llen/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */

    public T llen(String key) {
        return addCommand(LLen, key);
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/llen/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */

    public T llen(GlideString key) {
        return addCommand(LLen, key.toString());
    }

    /**
     * Echoes the given string.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to echo.
     * @return This batch instance for method chaining.
     */

    public T echo(String message) {
        return addCommand(Echo, message);
    }

    /**
     * Echoes the given string.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to echo.
     * @return This batch instance for method chaining.
     */

    public T echo(GlideString message) {
        return addCommand(Echo, message.toString());
    }

    /**
     * Returns the cardinality of the set stored at key.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */

    public T scard(String key) {
        return addCommand(SCard, key);
    }

    /**
     * Returns the cardinality of the set stored at key.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */

    public T scard(GlideString key) {
        return addCommand(SCard, key.toString());
    }

    /**
     * Removes the specified members from the set stored at key.
     *
     * @see <a href="https://valkey.io/commands/srem/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */

    public T srem(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(SRem, args);
    }

    /**
     * Removes the specified members from the set stored at key.
     *
     * @see <a href="https://valkey.io/commands/srem/">valkey.io</a> for details.
     * @param key The key of the set.
     * @param members The members to remove.
     * @return This batch instance for method chaining.
     */

    public T srem(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(SRem, args);
    }

    /**
     * Check if a member exists in a set.
     *
     * @see <a href="https://valkey.io/commands/sismember/">valkey.io</a> for
     *      details.
     * @param key    The set key
     * @param member The member to check
     * @return This batch instance for method chaining
     */
    public T sismember(String key, String member) {
        return addCommand(SIsMember, key, member);
    }

    /**
     * Check if a member exists in a set (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/sismember/">valkey.io</a> for
     *      details.
     * @param key    The set key
     * @param member The member to check
     * @return This batch instance for method chaining
     */
    public T sismember(GlideString key, GlideString member) {
        return addCommand(SIsMember, key.toString(), member.toString());
    }

    /**
     * Returns the rank of a member in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zrank/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member to find the rank of.
     * @return This batch instance for method chaining.
     */

    public T zrank(String key, String member) {
        return addCommand(ZRank, key, member);
    }

    /**
     * Returns the rank of a member in a sorted set.
     *
     * @see <a href="https://valkey.io/commands/zrank/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param member The member to find the rank of.
     * @return This batch instance for method chaining.
     */

    public T zrank(GlideString key, GlideString member) {
        return addCommand(ZRank, key.toString(), member.toString());
    }

    /**
     * Returns the number of bits set to 1 in the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key of the string.
     * @return This batch instance for method chaining.
     */

    public T bitcount(String key) {
        return addCommand(BitCount, key);
    }

    /**
     * Returns the number of bits set to 1 in the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key of the string.
     * @return This batch instance for method chaining.
     */

    public T bitcount(GlideString key) {
        return addCommand(BitCount, key.toString());
    }

    /**
     * Returns the number of bits set to 1 in the string value stored at key within the given range.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key of the string.
     * @param start The start index.
     * @param end The end index.
     * @param indexType The index type (BIT or BYTE).
     * @return This batch instance for method chaining.
     */

    public T bitcount(String key, int start, int end, glide.api.models.commands.bitmap.BitmapIndexType indexType) {
        return addCommand(BitCount, key, String.valueOf(start), String.valueOf(end), indexType.toString());
    }

    /**
     * Returns the number of bits set to 1 in the string value stored at key within
     * the given range.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for
     *      details.
     * @param key   The key of the string.
     * @param start The start index.
     * @param end   The end index.
     * @return This batch instance for method chaining.
     */

    public T bitcount(String key, long start, long end) {
        return addCommand(BitCount, key, String.valueOf(start), String.valueOf(end));
    }

    /**
     * Returns the cardinality of the intersection of the sets stored at the specified keys.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */

    public T sintercard(String... keys) {
        // Correct syntax: SINTERCARD numkeys key [key ...]
        if (keys == null) keys = new String[0];
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        return addCommand(SInterCard, args);
    }

    /**
     * Returns the cardinality of the intersection of the sets stored at the specified keys.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */

    public T sintercard(GlideString... keys) {
        if (keys == null) keys = new GlideString[0];
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return addCommand(SInterCard, args);
    }

    /**
     * Returns the cardinality of the intersection of the sets stored at the
     * specified keys with a limit.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for
     *      details.
     * @param keys  The keys of the sets.
     * @param limit The maximum number of intersecting elements to count.
     * @return This batch instance for method chaining.
     */
    public T sintercard(String[] keys, int limit) {
        if (keys == null) keys = new String[0];
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return addCommand(SInterCard, args);
    }

    /**
     * Returns the cardinality of the intersection of the sets stored at the
     * specified keys with a limit.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for
     *      details.
     * @param keys  The keys of the sets.
     * @param limit The maximum number of intersecting elements to count.
     * @return This batch instance for method chaining.
     */
    public T sintercard(String[] keys, long limit) {
        if (keys == null) keys = new String[0];
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return addCommand(SInterCard, args);
    }

    /**
     * Returns the cardinality of the intersection of the sets stored at the
     * specified keys with a limit.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for
     *      details.
     * @param keys  The keys of the sets.
     * @param limit The maximum number of intersecting elements to count.
     * @return This batch instance for method chaining.
     */
    public T sintercard(GlideString[] keys, long limit) {
        if (keys == null) keys = new GlideString[0];
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return addCommand(SInterCard, args);
    }

    /**
     * Returns the cardinality of the HyperLogLog stored at key.
     *
     * @see <a href="https://valkey.io/commands/pfcount/">valkey.io</a> for details.
     * @param keys The keys of the HyperLogLogs.
     * @return This batch instance for method chaining.
     */

    public T pfcount(String... keys) {
        return addCommand(PfCount, keys);
    }

    /**
     * Returns the cardinality of the HyperLogLog stored at key.
     *
     * @see <a href="https://valkey.io/commands/pfcount/">valkey.io</a> for details.
     * @param keys The keys of the HyperLogLogs.
     * @return This batch instance for method chaining.
     */

    public T pfcount(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(PfCount, stringKeys);
    }

    public T hscan(String key, String cursor) {
        return addCommand(HScan, key, cursor);
    }

    public T lpushx(String key, String[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(LPushX, args);
    }

    public T sdiffstore(String destination, String[] keys) {
        // Protocol: SDIFFSTORE destination key [key ...]
        // Previous implementation incorrectly inserted a synthetic numkeys argument
        // (mirroring SINTERCARD/ZINTERCARD style) which caused the first real key to be
        // treated as a literal string key (e.g. "2") and produced empty diffs.
        String[] args = new String[keys.length + 1];
        args[0] = destination;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return addCommand(SDiffStore, args);
    }

    public T zrangestore(String destination, String source, RangeOptions.RangeQuery rangeQuery) {
        return addCommand(ZRangeStore, RangeOptions.createZRangeStoreArgs(destination, source, rangeQuery, false));
    }

    public T zinterstore(String destination, glide.api.models.commands.WeightAggregateOptions.KeyArray keys) {
        String[] args = glide.utils.ArrayTransformUtils.concatenateArrays(new String[]{destination}, keys.toArgs());
        return addCommand(ZInterStore, args);
    }

    

    public T bitfieldReadOnly(String key, glide.api.models.commands.bitmap.BitFieldOptions.BitFieldReadOnlySubCommands[] subcommands) {
        String[] args = glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldArgs(subcommands);
        String[] full = glide.utils.ArrayTransformUtils.concatenateArrays(new String[]{key}, args);
        return addCommand(BitFieldReadOnly, full);
    }

    /**
     * Sets the configuration parameter to the given value.
     *
     * @see <a href="https://valkey.io/commands/config-set/">valkey.io</a> for details.
     * @param configParams A map of configuration parameters to their values.
     * @return This batch instance for method chaining.
     */

    public T configSet(Map<String, String> configParams) {
        String[] args = new String[configParams.size() * 2];
        int index = 0;
        for (Map.Entry<String, String> entry : configParams.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(ConfigSet, args);
    }

    /**
     * Adds an entry to the specified stream.
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param values Field-value pairs to be added to the entry.
     * @param options Options for the xadd command.
     * @return This batch instance for method chaining.
     */

    public T xadd(String key, Map<String, String> values, glide.api.models.commands.stream.StreamAddOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        if (options != null) {
            for (String a : options.toArgs()) {
                args.add(a);
            }
        } else {
            args.add("*");
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            args.add(entry.getKey());
            args.add(entry.getValue());
        }
        return addCommand(XAdd, args.toArray(new String[0]));
    }

    /**
     * Adds an entry to the specified stream with field-value pairs as a 2D array.
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key     The key of the stream.
     * @param values  Field-value pairs as a 2D array to be added to the entry.
     * @param options Options for the xadd command.
     * @return This batch instance for method chaining.
     */
    public T xadd(String key, String[][] values, glide.api.models.commands.stream.StreamAddOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        if (options != null) {
            for (String a : options.toArgs()) {
                args.add(a);
            }
        } else {
            args.add("*");
        }
        for (String[] pair : values) {
            args.add(pair[0]);
            args.add(pair[1]);
        }
        return addCommand(XAdd, args.toArray(new String[0]));
    }

    /**
     * Acknowledges the successful processing of pending messages.
     *
     * @see <a href="https://valkey.io/commands/xack/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupName The name of the consumer group.
     * @param ids The IDs of the messages to acknowledge.
     * @return This batch instance for method chaining.
     */

    public T xack(String key, String groupName, String... ids) {
        String[] args = new String[ids.length + 2];
        args[0] = key;
        args[1] = groupName;
        System.arraycopy(ids, 0, args, 2, ids.length);
        return addCommand(XAck, args);
    }

    /**
     * Read from streams using XREAD with simple map of key->id.
     */
    public T xread(java.util.Map<String, String> keysAndIds) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("STREAMS");
        for (String k : keysAndIds.keySet()) args.add(k);
        for (String v : keysAndIds.values()) args.add(v);
        return addCommand("XREAD", args.toArray(new String[0]));
    }

    /**
     * XLEN key
     */
    public T xlen(String key) {
        return addCommand("XLEN", key);
    }

    /**
     * XTRIM key <options>
     */
    public T xtrim(String key, glide.api.models.commands.stream.StreamTrimOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        if (options != null) {
            for (String a : options.toArgs()) args.add(a);
        }
        return addCommand("XTRIM", args.toArray(new String[0]));
    }

    /**
     * XRANGE key start end [COUNT n]
     */
    public T xrange(String key, String start, String end) {
        return addCommand("XRANGE", key, start, end);
    }

    public T xrange(String key, String start, String end, long count) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        args.add(start);
        args.add(end);
        if (count > 0) {
            args.add("COUNT");
            args.add(String.valueOf(count));
        }
        return addCommand("XRANGE", args.toArray(new String[0]));
    }

    /**
     * XREVRANGE key end start [COUNT n]
     */
    public T xrevrange(String key, String end, String start) {
        return addCommand("XREVRANGE", key, end, start);
    }

    public T xrevrange(String key, String end, String start, long count) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        args.add(end);
        args.add(start);
        if (count > 0) {
            args.add("COUNT");
            args.add(String.valueOf(count));
        }
        return addCommand("XREVRANGE", args.toArray(new String[0]));
    }

    /**
     * XDEL key id...
     */
    public T xdel(String key, String[] ids) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        java.util.Collections.addAll(args, ids);
        return addCommand("XDEL", args.toArray(new String[0]));
    }

    /**
     * XGROUP CREATE key group id [options]
     */
    public T xgroupCreate(String key, String groupName, String id) {
        return addCommand("XGROUP", "CREATE", key, groupName, id);
    }
    public T xgroupDestroy(String key, String groupName) {
        return addCommand("XGROUP", "DESTROY", key, groupName);
    }

    public T xgroupCreate(String key, String groupName, String id, glide.api.models.commands.stream.StreamGroupOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("CREATE"); args.add(key); args.add(groupName); args.add(id);
        if (options != null) for (String s : options.toArgs()) args.add(s);
        return addCommand("XGROUP", args.toArray(new String[0]));
    }

    public T xgroupSetId(String key, String groupName, String id, int entriesRead) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("SETID"); args.add(key); args.add(groupName); args.add(id); args.add("ENTRIESREAD"); args.add(String.valueOf(entriesRead));
        return addCommand("XGROUP", args.toArray(new String[0]));
    }
    public T xgroupSetId(String key, String groupName, String id) {
        return addCommand("XGROUP", "SETID", key, groupName, id);
    }

    /**
     * XREADGROUP GROUP <group> <consumer> STREAMS ...
     */
    public T xreadgroup(java.util.Map<String, String> keysAndIds, String group, String consumer) {
        // Valkey syntax: XREADGROUP GROUP <group> <consumer> [COUNT n] [BLOCK ms] [NOACK] STREAMS key [key ...] id [id ...]
        // Previous implementation appended keys via keySet() and then ids via values(). While HashMap
        // iteration order keeps keys/values aligned, using values() separately is brittle and we also
        // duplicated the STREAMS token when options were supplied (see overload below) which caused the
        // server to mis-parse and raise "Unbalanced 'xreadgroup' list of streams". We normalize here:
        // 1. Build arguments manually for no-options variant ensuring single STREAMS token.
        // 2. Collect keys and ids using the entrySet order so their relative pairing is preserved.
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("GROUP");
        args.add(group);
        args.add(consumer);
        args.add("STREAMS");
        java.util.List<String> keys = new java.util.ArrayList<>(keysAndIds.size());
        java.util.List<String> ids = new java.util.ArrayList<>(keysAndIds.size());
        for (java.util.Map.Entry<String, String> e : keysAndIds.entrySet()) {
            keys.add(e.getKey());
            ids.add(e.getValue());
        }
        args.addAll(keys);
        args.addAll(ids);
        return addCommand("XREADGROUP", args.toArray(new String[0]));
    }

    public T xreadgroup(java.util.Map<String, String> keysAndIds, String group, String consumer, glide.api.models.commands.stream.StreamReadGroupOptions options) {
        if (options == null) {
            return xreadgroup(keysAndIds, group, consumer);
        }
        // Use the dedicated options helper which already assembles: GROUP group consumer [COUNT/BLOCK/NOACK] STREAMS keys ids
        String[] built = options.toArgs(group, consumer, keysAndIds);
        return addCommand("XREADGROUP", built);
    }

    /**
     * Transfers ownership of pending stream entries.
     *
     * @see <a href="https://valkey.io/commands/xautoclaim/">valkey.io</a> for details.
     * @param key The key of the stream.
     * @param groupName The name of the consumer group.
     * @param consumerName The name of the consumer.
     * @param minIdleTime The minimum idle time for messages to be claimed.
     * @param start The starting ID for the range of messages to claim.
     * @return This batch instance for method chaining.
     */

    public T xautoclaim(String key, String groupName, String consumerName, long minIdleTime, String start) {
        return addCommand(XAutoClaim, key, groupName, consumerName, String.valueOf(minIdleTime), start);
    }

    /**
     * XAUTCLAIM with JUSTID
     */
    public T xautoclaimJustId(String key, String groupName, String consumerName, long minIdleTime, String start) {
        return addCommand("XAUTOCLAIM", key, groupName, consumerName, String.valueOf(minIdleTime), start, "JUSTID");
    }

    /**
     * XPENDING key group start end count
     */
    public T xpending(String key, String groupName, String start, String end, long count) {
        return addCommand("XPENDING", key, groupName, start, end, String.valueOf(count));
    }
    public T xpending(String key, String groupName) {
        return addCommand("XPENDING", key, groupName);
    }

    public T xpending(String key, String groupName, glide.api.models.commands.stream.StreamRange.InfRangeBound start, glide.api.models.commands.stream.StreamRange.InfRangeBound end, long count) {
        return addCommand("XPENDING", key, groupName, start.getValkeyApi(), end.getValkeyApi(), String.valueOf(count));
    }

    public T xclaim(String key, String group, String consumer, long minIdleTimeMs, String[] ids) {
        String[] args = new String[4 + ids.length];
        args[0] = key; args[1] = group; args[2] = consumer; args[3] = String.valueOf(minIdleTimeMs);
        System.arraycopy(ids, 0, args, 4, ids.length);
        return addCommand("XCLAIM", args);
    }

    public T xclaim(String key, String group, String consumer, long minIdleTimeMs, String[] ids, glide.api.models.commands.stream.StreamClaimOptions options) {
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add(key); list.add(group); list.add(consumer); list.add(String.valueOf(minIdleTimeMs));
        for (String id : ids) list.add(id);
        if (options != null) for (String s : options.toArgs()) list.add(s);
        return addCommand("XCLAIM", list.toArray(new String[0]));
    }

    public T xclaimJustId(String key, String group, String consumer, long minIdleTimeMs, String[] ids) {
        String[] base = new String[4 + ids.length];
        base[0] = key; base[1] = group; base[2] = consumer; base[3] = String.valueOf(minIdleTimeMs);
        System.arraycopy(ids, 0, base, 4, ids.length);
        String[] full = glide.utils.ArrayTransformUtils.concatenateArrays(base, new String[]{glide.api.models.commands.stream.StreamClaimOptions.JUST_ID_VALKEY_API});
        return addCommand("XCLAIM", full);
    }
    public T xclaimJustId(String key, String group, String consumer, long minIdleTimeMs, String[] ids, glide.api.models.commands.stream.StreamClaimOptions options) {
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add(key); list.add(group); list.add(consumer); list.add(String.valueOf(minIdleTimeMs));
        for (String id : ids) list.add(id);
        if (options != null) for (String s : options.toArgs()) list.add(s);
        list.add(glide.api.models.commands.stream.StreamClaimOptions.JUST_ID_VALKEY_API);
        return addCommand("XCLAIM", list.toArray(new String[0]));
    }

    public T xgroupDelConsumer(String key, String group, String consumer) {
        return addCommand("XGROUP", "DELCONSUMER", key, group, consumer);
    }

    /**
     * Pops one or more elements from the first non-empty sorted set.
     *
     * @see <a href="https://valkey.io/commands/bzpopmin/">valkey.io</a> for details.
     * @param keys The keys of the sorted sets.
     * @param timeout The timeout in seconds.
     * @return This batch instance for method chaining.
     */

    public T bzpopmin(String[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[keys.length] = String.valueOf(timeout);
        return addCommand(BZPopMin, args);
    }
    public T bzpopmax(String[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[keys.length] = String.valueOf(timeout);
        return addCommand(BZPopMax, args);
    }

    /**
     * Gets the value of a key and deletes it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */

    public T getdel(String key) {
        return addCommand(GETDEL, key);
    }

    /**
     * Gets the value of a key and deletes it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */

    public T getdel(GlideString key) {
        return addCommand(GETDEL, key.toString());
    }

    /**
     * Gets the length of the longest common subsequence between strings stored at key1 and key2.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */

    public T lcsLen(String key1, String key2) {
        return addCommand(LCS, key1, key2, "LEN");
    }

    /**
     * Gets the length of the longest common subsequence between strings stored at key1 and key2.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */

    public T lcsLen(GlideString key1, GlideString key2) {
        return addCommand(LCS, key1.toString(), key2.toString(), "LEN");
    }

    /**
     * Returns the number of fields in the hash stored at key.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */

    public T hlen(String key) {
        return addCommand(HLen, key);
    }

    /**
     * Returns the number of fields in the hash stored at key.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */

    public T hlen(GlideString key) {
        return addCommand(HLen, key.toString());
    }

    /**
     * Set a timeout on a key with additional options.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set timeout on.
     * @param seconds The timeout in seconds.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */

    public T expire(String key, int seconds, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return addCommand(EXPIRE, key, String.valueOf(seconds));
        }
        String[] optionArgs = options.toArgs();
        String[] fullArgs = new String[2 + optionArgs.length];
        fullArgs[0] = key;
        fullArgs[1] = String.valueOf(seconds);
        System.arraycopy(optionArgs, 0, fullArgs, 2, optionArgs.length);
        return addCommand(EXPIRE, fullArgs);
    }

    /**
     * Set a timeout on a key with additional options.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set timeout on.
     * @param seconds The timeout in seconds.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */

    public T expire(GlideString key, int seconds, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return addCommand(EXPIRE, key.toString(), String.valueOf(seconds));
        }
        String[] optionArgs = options.toArgs();
        String[] fullArgs = new String[2 + optionArgs.length];
        fullArgs[0] = key.toString();
        fullArgs[1] = String.valueOf(seconds);
        System.arraycopy(optionArgs, 0, fullArgs, 2, optionArgs.length);
        return addCommand(EXPIRE, fullArgs);
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp with additional options.
     *
     * @see <a href="https://valkey.io/commands/expireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp when the key should expire.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */

    public T expireAt(String key, int timestamp, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return addCommand(EXPIREAT, key, String.valueOf(timestamp));
        }
        String[] optionArgs = options.toArgs();
        String[] fullArgs = new String[2 + optionArgs.length];
        fullArgs[0] = key;
        fullArgs[1] = String.valueOf(timestamp);
        System.arraycopy(optionArgs, 0, fullArgs, 2, optionArgs.length);
        return addCommand(EXPIREAT, fullArgs);
    }

    /**
     * Set an expiration time on a key at a specific Unix timestamp with additional options.
     *
     * @see <a href="https://valkey.io/commands/expireat/">valkey.io</a> for details.
     * @param key The key to set expiration on.
     * @param timestamp The Unix timestamp when the key should expire.
     * @param options The expire options.
     * @return This batch instance for method chaining.
     */

    public T expireAt(GlideString key, int timestamp, glide.api.models.commands.ExpireOptions options) {
        if (options == null) {
            return addCommand(EXPIREAT, key.toString(), String.valueOf(timestamp));
        }
        String[] optionArgs = options.toArgs();
        String[] fullArgs = new String[2 + optionArgs.length];
        fullArgs[0] = key.toString();
        fullArgs[1] = String.valueOf(timestamp);
        System.arraycopy(optionArgs, 0, fullArgs, 2, optionArgs.length);
        return addCommand(EXPIREAT, fullArgs);
    }

    /**
     * Sets the value of a key with additional options.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @param options The set options.
     * @return This batch instance for method chaining.
     */

    public T set(String key, String value, glide.api.models.commands.SetOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(value);
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return addCommand(SET, args.toArray(new String[0]));
    }

    /**
     * Sets the value of a key with additional options.
     *
     * @see <a href="https://valkey.io/commands/set/">valkey.io</a> for details.
     * @param key The key to set.
     * @param value The value to set.
     * @param options The set options.
     * @return This batch instance for method chaining.
     */

    public T set(GlideString key, GlideString value, glide.api.models.commands.SetOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(value.toString());
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return addCommand(SET, args.toArray(new String[0]));
    }

    /**
     * Gets the longest common subsequence indices between strings stored at key1 and key2.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */

    public T lcsIdx(String key1, String key2) {
        return addCommand(LCS, key1, key2, "IDX");
    }

    /**
     * Gets the longest common subsequence indices between strings stored at key1 and key2.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */

    public T lcsIdx(GlideString key1, GlideString key2) {
        return addCommand(LCS, key1.toString(), key2.toString(), "IDX");
    }

    /**
     * Gets the longest common subsequence indices between strings stored at key1
     * and key2 with minimum match length.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1        The first key.
     * @param key2        The second key.
     * @param minMatchLen The minimum match length.
     * @return This batch instance for method chaining.
     */
    public T lcsIdx(String key1, String key2, long minMatchLen) {
        return addCommand(LCS, key1, key2, "IDX", "MINMATCHLEN", String.valueOf(minMatchLen));
    }

    /**
     * Gets the longest common subsequence indices between strings stored at key1
     * and key2 with minimum match length.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1        The first key.
     * @param key2        The second key.
     * @param minMatchLen The minimum match length.
     * @return This batch instance for method chaining.
     */
    public T lcsIdx(GlideString key1, GlideString key2, long minMatchLen) {
        return addCommand(LCS, key1.toString(), key2.toString(), "IDX", "MINMATCHLEN", String.valueOf(minMatchLen));
    }

    /**
     * Returns the longest common subsequence indices and lengths between strings.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */
    public T lcsIdxWithMatchLen(String key1, String key2) {
        return addCommand(LCS, key1, key2, "IDX", "WITHMATCHLEN");
    }

    /**
     * Returns the longest common subsequence indices and lengths between strings.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1 The first key.
     * @param key2 The second key.
     * @return This batch instance for method chaining.
     */
    public T lcsIdxWithMatchLen(GlideString key1, GlideString key2) {
        return addCommand(LCS, key1.toString(), key2.toString(), "IDX", "WITHMATCHLEN");
    }

    /**
     * Returns the longest common subsequence indices and lengths between strings.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1        The first key.
     * @param key2        The second key.
     * @param minMatchLen The minimum match length.
     * @return This batch instance for method chaining.
     */
    public T lcsIdxWithMatchLen(String key1, String key2, long minMatchLen) {
        return addCommand(LCS, key1, key2, "IDX", "WITHMATCHLEN", "MINMATCHLEN", String.valueOf(minMatchLen));
    }

    /**
     * Returns the longest common subsequence indices and lengths between strings.
     *
     * @see <a href="https://valkey.io/commands/lcs/">valkey.io</a> for details.
     * @param key1        The first key.
     * @param key2        The second key.
     * @param minMatchLen The minimum match length.
     * @return This batch instance for method chaining.
     */
    public T lcsIdxWithMatchLen(GlideString key1, GlideString key2, long minMatchLen) {
        return addCommand(LCS, key1.toString(), key2.toString(), "IDX", "WITHMATCHLEN", "MINMATCHLEN",
                String.valueOf(minMatchLen));
    }

    /**
     * Call a Lua function.
     *
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name to call
     * @param keys The keys to pass to the function
     * @param args The arguments to pass to the function
     * @return This batch instance for method chaining
     */

    public T fcall(String function, String[] keys, String[] args) {
        // Legacy logic: FCALL <function> <numkeys> key [key ...] arg [arg ...]
        if (keys == null) keys = new String[0];
        if (args == null) args = new String[0];
        // Defensive validation mirroring server constraints to fail fast client-side in batch building.
        // numkeys == keys.length. Server errors previously observed: "numkeys should be greater than 0" and
        // "Number of keys can't be greater than number of args". The second appears when numkeys > remaining arg count.
        // We only trigger that scenario if keys length > args length when keys are intended to also be first arguments.
        // In our invocation pattern, keys and args are distinct; still we guard obvious invalid states.
        if (keys.length == 0 && args.length > 0) {
            // Calling FCALL with zero keys and some args is legal per Redis spec. However server error observed came
            // from a mis-parsed layout elsewhere; retain allowance but no validation error here.
        }
        if (keys.length > 0 && args.length == 0) {
            // This is valid (functions may only require keys). No action.
        }
        String[] commandArgs = new String[2 + keys.length + args.length];
        int idx = 0;
        commandArgs[idx++] = function;
        commandArgs[idx++] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, commandArgs, idx, keys.length);
        idx += keys.length;
        System.arraycopy(args, 0, commandArgs, idx, args.length);
        return addCommand(FCall, commandArgs);
    }

    /**
     * Call a Lua function (read-only).
     *
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name to call
     * @param keys The keys to pass to the function
     * @param args The arguments to pass to the function
     * @return This batch instance for method chaining
     */

    public T fcallReadOnly(String function, String[] keys, String[] args) {
        // Legacy logic: FCALL_RO <function> <numkeys> key [key ...] arg [arg ...]
        if (keys == null) keys = new String[0];
        if (args == null) args = new String[0];
        if (keys.length == 0 && args.length > 0) {
            // Allow; see note above.
        }
        String[] commandArgs = new String[2 + keys.length + args.length];
        int idx = 0;
        commandArgs[idx++] = function;
        commandArgs[idx++] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, commandArgs, idx, keys.length);
        idx += keys.length;
        System.arraycopy(args, 0, commandArgs, idx, args.length);
        return addCommand(FCallReadOnly, commandArgs);
    }

    /**
     * GET string length.
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */

    public T strlen(String key) {
        return addCommand(Strlen, key);
    }

    /**
     * GET string length (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */

    public T strlen(GlideString key) {
        return addCommand(Strlen, key.toString());
    }

    /**
     * Append string value.
     *
     * @see <a href="https://valkey.io/commands/append/">valkey.io</a> for details.
     * @param key The key
     * @param value The value to append
     * @return This batch instance for method chaining
     */

    public T append(String key, String value) {
        return addCommand(Append, key, value);
    }

    /**
     * Append string value (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/append/">valkey.io</a> for details.
     * @param key The key
     * @param value The value to append
     * @return This batch instance for method chaining
     */

    public T append(GlideString key, GlideString value) {
        return addCommand(Append, key.toString(), value.toString());
    }

    /**
     * Set multiple keys.
     *
     * @see <a href="https://valkey.io/commands/mset/">valkey.io</a> for details.
     * @param keyValueMap The key-value pairs to set
     * @return This batch instance for method chaining
     */

    public T mset(Map<String, String> keyValueMap) {
        String[] args = new String[keyValueMap.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : keyValueMap.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return addCommand(MSet, args);
    }

    /**
     * Set multiple keys (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/mset/">valkey.io</a> for details.
     * @param keyValueMap The key-value pairs to set
     * @return This batch instance for method chaining
     */

    public T msetGlideString(Map<GlideString, GlideString> keyValueMap) {
        String[] args = new String[keyValueMap.size() * 2];
        int i = 0;
        for (Map.Entry<GlideString, GlideString> entry : keyValueMap.entrySet()) {
            args[i++] = entry.getKey().toString();
            args[i++] = entry.getValue().toString();
        }
        return addCommand(MSet, args);
    }

    /**
     * GET multiple keys.
     *
     * @see <a href="https://valkey.io/commands/mget/">valkey.io</a> for details.
     * @param keys The keys to get
     * @return This batch instance for method chaining
     */

    public T mget(String[] keys) {
        return addCommand(MGet, keys);
    }

    /**
     * GET multiple keys (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/mget/">valkey.io</a> for details.
     * @param keys The keys to get
     * @return This batch instance for method chaining
     */

    public T mget(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(MGet, stringKeys);
    }

    /**
     * Increment integer value.
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */

    public T incr(String key) {
        return addCommand(Incr, key);
    }

    /**
     * Increment integer value (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */

    public T incr(GlideString key) {
        return addCommand(Incr, key.toString());
    }

    /**
     * Increment integer value by amount.
     *
     * @see <a href="https://valkey.io/commands/incrby/">valkey.io</a> for details.
     * @param key The key
     * @param amount The amount to increment
     * @return This batch instance for method chaining
     */

    public T incrBy(String key, long amount) {
        return addCommand(IncrBy, key, String.valueOf(amount));
    }

    /**
     * Increment integer value by amount (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/incrby/">valkey.io</a> for details.
     * @param key The key
     * @param amount The amount to increment
     * @return This batch instance for method chaining
     */

    public T incrBy(GlideString key, long amount) {
        return addCommand(IncrBy, key.toString(), String.valueOf(amount));
    }

    /**
     * Decrement integer value.
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */

    public T decr(String key) {
        return addCommand(Decr, key);
    }

    /**
     * Decrement integer value (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */

    public T decr(GlideString key) {
        return addCommand(Decr, key.toString());
    }

    /**
     * Decrement integer value by amount.
     *
     * @see <a href="https://valkey.io/commands/decrby/">valkey.io</a> for details.
     * @param key The key
     * @param amount The amount to decrement
     * @return This batch instance for method chaining
     */

    public T decrBy(String key, long amount) {
        return addCommand(DecrBy, key, String.valueOf(amount));
    }

    /**
     * Decrement integer value by amount (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/decrby/">valkey.io</a> for details.
     * @param key The key
     * @param amount The amount to decrement
     * @return This batch instance for method chaining
     */

    public T decrBy(GlideString key, long amount) {
        return addCommand(DecrBy, key.toString(), String.valueOf(amount));
    }

    /**
     * Increment float value by amount.
     *
     * @see <a href="https://valkey.io/commands/incrbyfloat/">valkey.io</a> for details.
     * @param key The key
     * @param amount The amount to increment
     * @return This batch instance for method chaining
     */
    public T incrByFloat(String key, double amount) {
        return addCommand(IncrByFloat, key, String.valueOf(amount));
    }

    /**
     * Increment float value by amount (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/incrbyfloat/">valkey.io</a> for details.
     * @param key The key
     * @param amount The amount to increment
     * @return This batch instance for method chaining
     */
    public T incrByFloat(GlideString key, double amount) {
        return addCommand(IncrByFloat, key.toString(), String.valueOf(amount));
    }

    /**
     * Check if hash field exists.
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
     * @param key The hash key
     * @param field The field to check
     * @return This batch instance for method chaining
     */
    public T hexists(String key, String field) {
        return addCommand(HExists, key, field);
    }

    /**
     * Check if hash field exists (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hexists/">valkey.io</a> for details.
     * @param key The hash key
     * @param field The field to check
     * @return This batch instance for method chaining
     */
    public T hexists(GlideString key, GlideString field) {
        return addCommand(HExists, key.toString(), field.toString());
    }

    /**
     * GET list element at index.
     *
     * @see <a href="https://valkey.io/commands/lindex/">valkey.io</a> for details.
     * @param key The list key
     * @param index The index
     * @return This batch instance for method chaining
     */
    public T lindex(String key, int index) {
        return addCommand(LIndex, key, String.valueOf(index));
    }

    /**
     * GET list element at index (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/lindex/">valkey.io</a> for details.
     * @param key The list key
     * @param index The index
     * @return This batch instance for method chaining
     */
    public T lindex(GlideString key, int index) {
        return addCommand(LIndex, key.toString(), String.valueOf(index));
    }

    /**
     * Scan set members.
     *
     * @see <a href="https://valkey.io/commands/sscan/">valkey.io</a> for details.
     * @param key The set key
     * @param cursor The cursor
     * @return This batch instance for method chaining
     */
    public T sscan(String key, String cursor) {
        return addCommand(SScan, key, cursor);
    }

    /**
     * Scan set members (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/sscan/">valkey.io</a> for details.
     * @param key The set key
     * @param cursor The cursor
     * @return This batch instance for method chaining
     */
    public T sscan(GlideString key, GlideString cursor) {
        return addCommand(SScan, key.toString(), cursor.toString());
    }

    /**
     * SCAN over members of a set with options.
     *
     * @see <a href="https://valkey.io/commands/sscan/">valkey.io</a> for details.
     * @param key     The set key
     * @param cursor  The cursor
     * @param options The scan options
     * @return This batch instance for method chaining
     */
    public T sscan(String key, String cursor, SScanOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(cursor);
        args.addAll(java.util.Arrays.asList(options.toArgs()));
        return addCommand(SScan, args.toArray(new String[0]));
    }

    /**
     * SCAN over members of a set with options (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/sscan/">valkey.io</a> for details.
     * @param key     The set key
     * @param cursor  The cursor
     * @param options The scan options
     * @return This batch instance for method chaining
     */
    public T sscan(GlideString key, GlideString cursor, SScanOptions options) {
        return sscan(key.toString(), cursor.toString(), options);
    }

    /**
     * GET reverse rank of sorted set member.
     *
     * @see <a href="https://valkey.io/commands/zrevrank/">valkey.io</a> for details.
     * @param key The sorted set key
     * @param member The member
     * @return This batch instance for method chaining
     */
    public T zrevrank(String key, String member) {
        return addCommand(ZRevRank, key, member);
    }

    /**
     * GET reverse rank of sorted set member (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/zrevrank/">valkey.io</a> for details.
     * @param key The sorted set key
     * @param member The member
     * @return This batch instance for method chaining
     */
    public T zrevrank(GlideString key, GlideString member) {
        return addCommand(ZRevRank, key.toString(), member.toString());
    }

    /**
     * GET configuration parameters.
     *
     * @see <a href="https://valkey.io/commands/config-get/">valkey.io</a> for details.
     * @param parameters The parameters to get
     * @return This batch instance for method chaining
     */
    public T configGet(String[] parameters) {
        return addCommand(ConfigGet, parameters);
    }

    /**
     * Merge multiple HyperLogLog values.
     *
     * @see <a href="https://valkey.io/commands/pfmerge/">valkey.io</a> for details.
     * @param destkey The destination key
     * @param sourcekeys The source keys
     * @return This batch instance for method chaining
     */
    public T pfmerge(String destkey, String[] sourcekeys) {
        String[] args = new String[sourcekeys.length + 1];
        args[0] = destkey;
        System.arraycopy(sourcekeys, 0, args, 1, sourcekeys.length);
        return addCommand(PfMerge, args);
    }

    /**
     * Merge multiple HyperLogLog values (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/pfmerge/">valkey.io</a> for details.
     * @param destkey The destination key
     * @param sourcekeys The source keys
     * @return This batch instance for method chaining
     */
    public T pfmerge(GlideString destkey, GlideString[] sourcekeys) {
        String[] args = new String[sourcekeys.length + 1];
        args[0] = destkey.toString();
        for (int i = 0; i < sourcekeys.length; i++) {
            args[i + 1] = sourcekeys[i].toString();
        }
        return addCommand(PfMerge, args);
    }

    /**
     * PUBLISH message to channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param channel The channel
     * @param message The message
     * @return This batch instance for method chaining
     */
    public T publish(String channel, String message) {
        return addCommand(PUBLISH, channel, message);
    }

    /**
     * PUBLISH message to channel (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param channel The channel
     * @param message The message
     * @return This batch instance for method chaining
     */
    public T publish(GlideString channel, GlideString message) {
        return addCommand(PUBLISH, channel.toString(), message.toString());
    }

    /**
     * Set the string value of a key at a given offset.
     *
     * @see <a href="https://valkey.io/commands/setrange/">valkey.io</a> for details.
     * @param key The key
     * @param offset The offset
     * @param value The value to set
     * @return This batch instance for method chaining
     */
    public T setrange(String key, int offset, String value) {
        return addCommand(SetRange, key, String.valueOf(offset), value);
    }

    /**
     * Set the string value of a key at a given offset (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/setrange/">valkey.io</a> for details.
     * @param key The key
     * @param offset The offset
     * @param value The value to set
     * @return This batch instance for method chaining
     */
    public T setrange(GlideString key, int offset, GlideString value) {
        return addCommand(SetRange, key.toString(), String.valueOf(offset), value.toString());
    }

    /**
     * Add a stream entry.
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key The stream key
     * @param values The field-value pairs
     * @return This batch instance for method chaining
     */
    public T xadd(String key, java.util.Map<String, String> values) {
        String[] args = new String[values.size() * 2 + 2];
        args[0] = key;
        args[1] = "*"; // Auto-generate ID
        int i = 2;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return addCommand(XAdd, args);
    }

    /**
     * Add a stream entry with field-value pairs as a 2D array.
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key    The stream key
     * @param values The field-value pairs as a 2D array
     * @return This batch instance for method chaining
     */
    public T xadd(String key, String[][] values) {
        String[] args = new String[values.length * 2 + 2];
        args[0] = key;
        args[1] = "*"; // Auto-generate ID
        int i = 2;
        for (String[] pair : values) {
            args[i++] = pair[0];
            args[i++] = pair[1];
        }
        return addCommand(XAdd, args);
    }

    /**
     * Add a stream entry (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key The stream key
     * @param values The field-value pairs
     * @return This batch instance for method chaining
     */
    public T xadd(GlideString key, java.util.Map<String, String> values) {
        String[] args = new String[values.size() * 2 + 2];
        args[0] = key.toString();
        args[1] = "*"; // Auto-generate ID
        int i = 2;
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return addCommand(XAdd, args);
    }

    /**
     * GET a substring of the string value.
     *
     * @see <a href="https://valkey.io/commands/getrange/">valkey.io</a> for details.
     * @param key The key
     * @param start Start index
     * @param end End index
     * @return This batch instance for method chaining
     */
    public T getrange(String key, int start, int end) {
        return addCommand(GetRange, key, String.valueOf(start), String.valueOf(end));
    }

    /**
     * GET a substring of the string value (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/getrange/">valkey.io</a> for details.
     * @param key The key
     * @param start Start index
     * @param end End index
     * @return This batch instance for method chaining
     */
    public T getrange(GlideString key, int start, int end) {
        return addCommand(GetRange, key.toString(), String.valueOf(start), String.valueOf(end));
    }

    /**
     * Set hash field only if it does not exist.
     *
     * @see <a href="https://valkey.io/commands/hsetnx/">valkey.io</a> for details.
     * @param key The hash key
     * @param field The field
     * @param value The value
     * @return This batch instance for method chaining
     */
    public T hsetnx(String key, String field, String value) {
        return addCommand(HSetNX, key, field, value);
    }

    /**
     * Set hash field only if it does not exist (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hsetnx/">valkey.io</a> for details.
     * @param key The hash key
     * @param field The field
     * @param value The value
     * @return This batch instance for method chaining
     */
    public T hsetnx(GlideString key, GlideString field, GlideString value) {
        return addCommand(HSetNX, key.toString(), field.toString(), value.toString());
    }

    /**
     * Remove elements from a list.
     *
     * @see <a href="https://valkey.io/commands/lrem/">valkey.io</a> for details.
     * @param key The list key
     * @param count The count
     * @param element The element to remove
     * @return This batch instance for method chaining
     */
    public T lrem(String key, int count, String element) {
        return addCommand(LRem, key, String.valueOf(count), element);
    }

    /**
     * Remove elements from a list (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/lrem/">valkey.io</a> for details.
     * @param key The list key
     * @param count The count
     * @param element The element to remove
     * @return This batch instance for method chaining
     */
    public T lrem(GlideString key, int count, GlideString element) {
        return addCommand(LRem, key.toString(), String.valueOf(count), element.toString());
    }

    /**
     * Scan hash fields and values.
     *
     * @see <a href="https://valkey.io/commands/hscan/">valkey.io</a> for details.
     * @param key The hash key
     * @param cursor The cursor
     * @param options The scan options
     * @return This batch instance for method chaining
     */
    public T hscan(String key, String cursor, HScanOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        args.add(cursor);
        if (options != null) {
            args.addAll(java.util.Arrays.asList(options.toArgs()));
        }
        return addCommand(HScan, args.toArray(new String[0]));
    }

    /**
     * Scan hash fields and values (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hscan/">valkey.io</a> for details.
     * @param key The hash key
     * @param cursor The cursor
     * @param options The scan options
     * @return This batch instance for method chaining
     */
    public T hscan(GlideString key, GlideString cursor, HScanOptions options) {
        return hscan(key.toString(), cursor.toString(), options);
    }

    /**
     * Pop elements from multiple lists.
     *
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys The list keys
     * @param direction The direction
     * @return This batch instance for method chaining
     */
    public T lmpop(String[] keys, ListDirection direction) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[args.length - 1] = direction.toString();
        return addCommand(LMPop, args);
    }

    /**
     * Pop elements from multiple lists (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys The list keys
     * @param direction The direction
     * @return This batch instance for method chaining
     */
    public T lmpop(GlideString[] keys, ListDirection direction) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return lmpop(stringKeys, direction);
    }

    /**
     * Pop elements from multiple lists with count.
     *
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys      The list keys
     * @param direction The direction
     * @param count     The count of elements to pop
     * @return This batch instance for method chaining
     */
    public T lmpop(String[] keys, ListDirection direction, long count) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[args.length - 3] = direction.toString();
        args[args.length - 2] = "COUNT";
        args[args.length - 1] = String.valueOf(count);
        return addCommand(LMPop, args);
    }

    /**
     * Pop elements from multiple lists with count (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/lmpop/">valkey.io</a> for details.
     * @param keys      The list keys
     * @param direction The direction
     * @param count     The count of elements to pop
     * @return This batch instance for method chaining
     */
    public T lmpop(GlideString[] keys, ListDirection direction, long count) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return lmpop(stringKeys, direction, count);
    }

    /**
     * Move element from one list to another.
     *
     * @see <a href="https://valkey.io/commands/lmove/">valkey.io</a> for details.
     * @param source The source list
     * @param destination The destination list
     * @param from The direction to pop from source
     * @param to The direction to push to destination
     * @return This batch instance for method chaining
     */
    public T lmove(String source, String destination, ListDirection from, ListDirection to) {
        return addCommand(LMove, source, destination, from.toString(), to.toString());
    }

    /**
     * Move element from one list to another (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/lmove/">valkey.io</a> for details.
     * @param source The source list
     * @param destination The destination list
     * @param from The direction to pop from source
     * @param to The direction to push to destination
     * @return This batch instance for method chaining
     */
    public T lmove(GlideString source, GlideString destination, ListDirection from, ListDirection to) {
        return lmove(source.toString(), destination.toString(), from, to);
    }

    /**
     * Set multiple keys only if none exist.
     *
     * @see <a href="https://valkey.io/commands/msetnx/">valkey.io</a> for details.
     * @param keyValueMap The key-value pairs
     * @return This batch instance for method chaining
     */
    public T msetnx(java.util.Map<String, String> keyValueMap) {
        String[] args = new String[keyValueMap.size() * 2];
        int i = 0;
        for (java.util.Map.Entry<String, String> entry : keyValueMap.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return addCommand(MSetNX, args);
    }

    /**
     * GET multiple hash field values.
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields
     * @return This batch instance for method chaining
     */
    public T hmget(String key, String[] fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return addCommand(HMGet, args);
    }

    public T hstrlen(String key, String field) { return addCommand(HStrlen, key, field); }

    /**
     * GET multiple hash field values (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hmget/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields
     * @return This batch instance for method chaining
     */
    public T hmget(GlideString key, GlideString[] fields) {
        String[] stringFields = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            stringFields[i] = fields[i].toString();
        }
        return hmget(key.toString(), stringFields);
    }

    public T hkeys(String key) { return addCommand(HKeys, key); }

    /**
     * Trim a list to a range.
     *
     * @see <a href="https://valkey.io/commands/ltrim/">valkey.io</a> for details.
     * @param key The list key
     * @param start Start index
     * @param stop Stop index
     * @return This batch instance for method chaining
     */
    public T ltrim(String key, int start, int stop) {
        return addCommand(LTrim, key, String.valueOf(start), String.valueOf(stop));
    }

    /**
     * Trim a list to a range (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/ltrim/">valkey.io</a> for details.
     * @param key The list key
     * @param start Start index
     * @param stop Stop index
     * @return This batch instance for method chaining
     */
    public T ltrim(GlideString key, int start, int stop) {
        return ltrim(key.toString(), start, stop);
    }

    /**
     * Reset server statistics.
     *
     * @see <a href="https://valkey.io/commands/config-resetstat/">valkey.io</a> for details.
     * @return This batch instance for method chaining
     */
    public T configResetStat() {
        return addCommand(ConfigResetStat);
    }

    /**
     * Blocking move element between lists.
     *
     * @see <a href="https://valkey.io/commands/blmove/">valkey.io</a> for details.
     * @param source The source list
     * @param destination The destination list
     * @param from Direction to pop from source
     * @param to Direction to push to destination
     * @param timeout Timeout in seconds
     * @return This batch instance for method chaining
     */
    public T blmove(String source, String destination, ListDirection from, ListDirection to, double timeout) {
        return addCommand(BLMove, source, destination, from.toString(), to.toString(), String.valueOf(timeout));
    }

    /**
     * Blocking move element between lists (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/blmove/">valkey.io</a> for details.
     * @param source The source list
     * @param destination The destination list
     * @param from Direction to pop from source
     * @param to Direction to push to destination
     * @param timeout Timeout in seconds
     * @return This batch instance for method chaining
     */
    public T blmove(GlideString source, GlideString destination, ListDirection from, ListDirection to, double timeout) {
        return blmove(source.toString(), destination.toString(), from, to, timeout);
    }

    /**
     * Add to sorted set with increment.
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The sorted set key
     * @param member The member
     * @param increment The increment value
     * @return This batch instance for method chaining
     */
    public T zaddIncr(String key, String member, int increment) {
        return addCommand(ZAdd, key, "INCR", String.valueOf(increment), member);
    }

    /**
     * Add to sorted set with increment (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The sorted set key
     * @param member The member
     * @param increment The increment value
     * @return This batch instance for method chaining
     */
    public T zaddIncr(GlideString key, GlideString member, int increment) {
        return zaddIncr(key.toString(), member.toString(), increment);
    }

    /**
     * Get all fields and values from a hash.
     *
     * @see <a href="https://valkey.io/commands/hgetall/">valkey.io</a> for details.
     * @param key The hash key
     * @return This batch instance for method chaining
     */
    public T hgetall(String key) {
        return addCommand(HGetAll, key);
    }

    /**
     * Get all fields and values from a hash (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hgetall/">valkey.io</a> for details.
     * @param key The hash key
     * @return This batch instance for method chaining
     */
    public T hgetall(GlideString key) {
        return addCommand(HGetAll, key.toString());
    }

    /**
     * Delete one or more fields from a hash.
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
     * @param key    The hash key
     * @param fields The fields to delete
     * @return This batch instance for method chaining
     */
    public T hdel(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return addCommand(HDel, args);
    }

    /**
     * Delete one or more fields from a hash (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hdel/">valkey.io</a> for details.
     * @param key    The hash key
     * @param fields The fields to delete
     * @return This batch instance for method chaining
     */
    public T hdel(GlideString key, GlideString... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < fields.length; i++) {
            args[i + 1] = fields[i].toString();
        }
        return addCommand(HDel, args);
    }

    /**
     * Remove and return first element from a list.
     *
     * @see <a href="https://valkey.io/commands/lpop/">valkey.io</a> for details.
     * @param key The list key
     * @return This batch instance for method chaining
     */
    public T lpop(String key) {
        return addCommand(LPop, key);
    }

    public T lset(String key, long index, String element) {
        return addCommand(LSet, key, String.valueOf(index), element);
    }

    public T blpop(String[] keys, double timeoutSeconds) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[args.length - 1] = String.valueOf(timeoutSeconds);
        return addCommand(BLPop, args);
    }

    public T brpop(String[] keys, double timeoutSeconds) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[args.length - 1] = String.valueOf(timeoutSeconds);
        return addCommand(BRPop, args);
    }

    /**
     * Remove and return first element from a list (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/lpop/">valkey.io</a> for details.
     * @param key The list key
     * @return This batch instance for method chaining
     */
    public T lpop(GlideString key) {
        return addCommand(LPop, key.toString());
    }

    public T rpop(String key) { return addCommand(RPop, key); }

    public T rpop(glide.api.models.GlideString key) { return addCommand(RPop, key.toString()); }

    public T rpopCount(String key, int count) { return addCommand(RPop, key, String.valueOf(count)); }

    public T rpushx(String key, String[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(RPushX, args);
    }

    /**
     * Remove and return multiple elements from the beginning of a list.
     *
     * @see <a href="https://valkey.io/commands/lpop/">valkey.io</a> for details.
     * @param key   The list key
     * @param count The number of elements to pop
     * @return This batch instance for method chaining
     */
    public T lpopCount(String key, long count) {
        return addCommand(LPop, key, String.valueOf(count));
    }

    /**
     * Remove and return multiple elements from the beginning of a list.
     *
     * @see <a href="https://valkey.io/commands/lpop/">valkey.io</a> for details.
     * @param key   The list key
     * @param count The number of elements to pop
     * @return This batch instance for method chaining
     */
    public T lpopCount(String key, int count) {
        return lpopCount(key, (long) count);
    }

    /**
     * Remove and return multiple elements from the beginning of a list (GlideString
     * variant).
     *
     * @see <a href="https://valkey.io/commands/lpop/">valkey.io</a> for details.
     * @param key   The list key
     * @param count The number of elements to pop
     * @return This batch instance for method chaining
     */
    public T lpopCount(GlideString key, long count) {
        return addCommand(LPop, key.toString(), String.valueOf(count));
    }

    /**
     * Block and pop from multiple lists with timeout.
     *
     * @see <a href="https://valkey.io/commands/blmpop/">valkey.io</a> for details.
     * @param keys      The list keys
     * @param direction The direction to pop from
     * @param timeout   The timeout in seconds
     * @return This batch instance for method chaining
     */
    public T blmpop(String[] keys, ListDirection direction, double timeout) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        args[keys.length + 2] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        return addCommand(BLMPop, args);
    }

    /**
     * Block and pop from multiple lists with timeout (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/blmpop/">valkey.io</a> for details.
     * @param keys      The list keys
     * @param direction The direction to pop from
     * @param timeout   The timeout in seconds
     * @return This batch instance for method chaining
     */
    public T blmpop(GlideString[] keys, ListDirection direction, double timeout) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return blmpop(stringKeys, direction, timeout);
    }

    /**
     * Block and pop from multiple lists with count and timeout.
     *
     * @see <a href="https://valkey.io/commands/blmpop/">valkey.io</a> for details.
     * @param keys      The list keys
     * @param direction The direction to pop from
     * @param count     The maximum number of elements to pop
     * @param timeout   The timeout in seconds
     * @return This batch instance for method chaining
     */
    public T blmpop(String[] keys, ListDirection direction, long count, double timeout) {
        String[] args = new String[keys.length + 5];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        args[keys.length + 2] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        args[keys.length + 3] = "COUNT";
        args[keys.length + 4] = String.valueOf(count);
        return addCommand(BLMPop, args);
    }

    /**
     * Store difference of sorted sets.
     *
     * @see <a href="https://valkey.io/commands/zdiffstore/">valkey.io</a> for details.
     * @param destination The destination key
     * @param keys The sorted set keys
     * @return This batch instance for method chaining
     */
    public T zdiffstore(String destination, String[] keys) {
        String[] args = new String[keys.length + 2];
        args[0] = destination;
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        return addCommand(ZDiffStore, args);
    }

    /**
     * Store difference of sorted sets (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/zdiffstore/">valkey.io</a> for details.
     * @param destination The destination key
     * @param keys The sorted set keys
     * @return This batch instance for method chaining
     */
    public T zdiffstore(GlideString destination, GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return zdiffstore(destination.toString(), stringKeys);
    }

    // ==================== Additional chain methods for API parity
    // ====================
    public T hvals(String key) {
        return addCommand(HVals, key);
    }

    public T rpush(String key, String[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(RPush, args);
    }

    public T smembers(String key) {
        return addCommand(SMembers, key);
    }

    public T zincrby(String key, double increment, String member) {
        return addCommand(ZIncrBy, key, String.valueOf(increment), member);
    }

    public T zscan(String key, int cursor, glide.api.models.commands.scan.ZScanOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        args.add(String.valueOf(cursor));
        if (options != null)
            for (String s : options.toArgs())
                args.add(s);
        return addCommand(ZScan, args.toArray(new String[0]));
    }
    public T zscan(String key, String cursor) {
        return addCommand(ZScan, key, cursor);
    }
    public T zscan(String key, String cursor, glide.api.models.commands.scan.ZScanOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        args.add(cursor);
        if (options != null) for (String s : options.toArgs()) args.add(s);
        return addCommand(ZScan, args.toArray(new String[0]));
    }

    public T zdiff(String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        return addCommand(ZDiff, args);
    }

    public T zmpop(String[] keys, glide.api.models.commands.ScoreFilter filter) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[args.length - 1] = filter == glide.api.models.commands.ScoreFilter.MAX ? "MAX" : "MIN";
        return addCommand(ZMPop, args);
    }
    public T zmpop(String[] keys, glide.api.models.commands.ScoreFilter filter, int count) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = filter == glide.api.models.commands.ScoreFilter.MAX ? "MAX" : "MIN";
        args[keys.length + 2] = "COUNT";
        args[keys.length + 3] = String.valueOf(count);
        return addCommand(ZMPop, args);
    }

    public T lolwut(int version) {
        return addCommand(Lolwut, "VERSION", String.valueOf(version));
    }

    public T xread(java.util.Map<String, String> streams, glide.api.models.commands.stream.StreamReadOptions options) {
        if (options == null)
            return xread(streams);
        return addCommand("XREAD", options.toArgs(streams));
    }


    public T geoadd(String key, java.util.Map<String, glide.api.models.commands.geospatial.GeospatialData> members) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        for (java.util.Map.Entry<String, glide.api.models.commands.geospatial.GeospatialData> e : members.entrySet()) {
            args.add(String.valueOf(e.getValue().getLongitude()));
            args.add(String.valueOf(e.getValue().getLatitude()));
            args.add(e.getKey());
        }
        return addCommand(GeoAdd, args.toArray(new String[0]));
    }

    public T geosearch(String key,
    glide.api.models.commands.geospatial.GeoSearchOrigin.SearchOrigin origin,
    glide.api.models.commands.geospatial.GeoSearchShape shape,
    glide.api.models.commands.geospatial.GeoSearchResultOptions resultOptions) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        // origin and shape are provided directly
        for (String s : origin.toArgs()) args.add(s);
        for (String s : shape.toArgs()) args.add(s);
        if (resultOptions != null)
            for (String s : resultOptions.toArgs())
                args.add(s);
        return addCommand(GeoSearch, args.toArray(new String[0]));
    }

    public T geosearch(String key,
                       glide.api.models.commands.geospatial.GeoSearchOrigin.CoordOrigin origin,
                       glide.api.models.commands.geospatial.GeoSearchShape shape,
                       glide.api.models.commands.geospatial.GeoSearchOptions options,
                       glide.api.models.commands.geospatial.GeoSearchResultOptions resultOptions) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        if (origin != null) for (String s : origin.toArgs()) args.add(s);
        if (shape != null) for (String s : shape.toArgs()) args.add(s);
        if (options != null) for (String s : options.toArgs()) args.add(s);
        if (resultOptions != null) for (String s : resultOptions.toArgs()) args.add(s);
        return addCommand(GeoSearch, args.toArray(new String[0]));
    }

    public T functionList(boolean withCode) { return withCode ? addCommand(FunctionList, "WITHCODE") : addCommand(FunctionList); }
    public T functionList(String library, boolean withCode) {
        java.util.List<String> args = new java.util.ArrayList<>();
        // Use the correct argument name "LIBRARYNAME" (server expects this token)
        if (library != null && !library.isEmpty()) { args.add("LIBRARYNAME"); args.add(library); }
        if (withCode) { args.add("WITHCODE"); }
        return args.isEmpty() ? addCommand(FunctionList) : addCommand(FunctionList, args.toArray(new String[0]));
    }
    public T functionLoad(String code, boolean replace) { return replace ? addCommand(FunctionLoad, "REPLACE", code) : addCommand(FunctionLoad, code); }

    public T setbit(String key, int offset, int value) { return addCommand("SETBIT", key, String.valueOf(offset), String.valueOf(value)); }
    public T getbit(String key, int offset) { return addCommand("GETBIT", key, String.valueOf(offset)); }

    public T bitpos(String key, int bit, int start, int end,
            glide.api.models.commands.bitmap.BitmapIndexType indexType) {
        return addCommand(BitPos, key, String.valueOf(bit), String.valueOf(start), String.valueOf(end),
                indexType == glide.api.models.commands.bitmap.BitmapIndexType.BIT ? "BIT" : "BYTE");
    }

    public T bitcount(String key, int start) {
        return addCommand(BitCount, key, String.valueOf(start));
    }

    public T zrankWithScore(String key, String member) { return addCommand(ZRank, key, member, "WITHSCORE"); }
    public T zrevrankWithScore(String key, String member) { return addCommand(ZRevRank, key, member, "WITHSCORE"); }
    public T zrem(String key, String[] members) { String[] args = new String[members.length+1]; args[0]=key; System.arraycopy(members,0,args,1,members.length); return addCommand(ZRem, args);} 
    public T smismember(String key, String[] members) { String[] args = new String[members.length+1]; args[0]=key; System.arraycopy(members,0,args,1,members.length); return addCommand(SMIsMember, args);} 
    public T lpos(String key, String element) { return addCommand(LPos, key, element); }
    public T lpos(String key, String element, glide.api.models.commands.LPosOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key); args.add(element);
        if (options != null) for (String s : options.toArgs()) args.add(s);
        return addCommand(LPos, args.toArray(new String[0]));
    }

    public T linsert(String key, glide.api.models.commands.LInsertOptions.InsertPosition position, String pivot, String element) {
        String pos = position == glide.api.models.commands.LInsertOptions.InsertPosition.BEFORE ? "BEFORE" : "AFTER";
        return addCommand(LInsert, key, pos, pivot, element);
    }
    public T hrandfield(String key) { return addCommand(HRandField, key); }
    public T hrandfieldWithCount(String key, int count) { return addCommand(HRandField, key, String.valueOf(count)); }
    public T zdiffWithScores(String[] keys) { String[] args = new String[keys.length+2]; args[0]=String.valueOf(keys.length); System.arraycopy(keys,0,args,1,keys.length); args[args.length-1] = "WITHSCORES"; return addCommand(ZDiff, args);} 
    
    public T hrandfieldWithCountWithValues(String key, int count) { return addCommand(HRandField, key, String.valueOf(count), "WITHVALUES"); }
    
    public T lposCount(String key, String element, long count) { return addCommand(LPos, key, element, LPosOptions.COUNT_VALKEY_API, String.valueOf(count)); }
    
    public T sinter(String[] keys) { String[] args = new String[keys.length]; System.arraycopy(keys,0,args,0,keys.length); return addCommand(SInter, args);} 
    
    public T sinterstore(String destination, String[] keys) {
        // Protocol: SINTERSTORE destination key [key ...]
        String[] args = new String[keys.length + 1];
        args[0] = destination;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return addCommand(SInterStore, args);
    }

    public T sunionstore(String destination, String[] keys) {
        // Protocol: SUNIONSTORE destination key [key ...]
        String[] args = new String[keys.length + 1];
        args[0] = destination;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return addCommand(SUnionStore, args);
    }
    
    public T zcard(String key) { return addCommand(ZCard, key); }

    public T zscore(String key, String member) { return addCommand(ZScore, key, member); }
    public T zpopmin(String key) { return addCommand(ZPopMin, key); }
    public T srandmember(String key) { return addCommand(SRandMember, key); }
    public T srandmember(String key, int count) { return addCommand(SRandMember, key, String.valueOf(count)); }
    public T zpopmax(String key) { return addCommand(ZPopMax, key); }
    public T spop(String key) { return addCommand(SPop, key); }
    public T spopCount(String key, int count) { return addCommand(SPop, key, String.valueOf(count)); }
    public T zrandmember(String key) { return addCommand(ZRandMember, key); }
    public T zrandmemberWithCount(String key, int count) { return addCommand(ZRandMember, key, String.valueOf(count)); }
    public T zrandmemberWithCountWithScores(String key, int count) { return addCommand(ZRandMember, key, String.valueOf(count), "WITHSCORES"); }

    public T zinter(glide.api.models.commands.WeightAggregateOptions.KeyArray keys) {
        return addCommand(ZInter, keys.toArgs());
    }

    public T zrange(String key, RangeOptions.RangeQuery rangeQuery) {
        return addCommand(ZRange, RangeOptions.createZRangeArgs(key, rangeQuery, false, false));
    }

    public T zremrangebyrank(String key, int start, int stop) { return addCommand(ZRemRangeByRank, key, String.valueOf(start), String.valueOf(stop)); }
    public T zremrangebylex(String key, glide.api.models.commands.RangeOptions.LexBoundary min, glide.api.models.commands.RangeOptions.InfLexBound max) {
        return addCommand(ZRemRangeByLex, key, min.toArgs(), max.toArgs());
    }

    public T zlexcount(String key, glide.api.models.commands.RangeOptions.LexBoundary min, glide.api.models.commands.RangeOptions.InfLexBound max) {
        return addCommand(ZLexCount, key, min.toArgs(), max.toArgs());
    }
    public T zremrangebyscore(String key, glide.api.models.commands.RangeOptions.ScoreBoundary min, glide.api.models.commands.RangeOptions.InfScoreBound max) {
        return addCommand(ZRemRangeByScore, key, min.toArgs(), max.toArgs());
    }

    public T zcount(String key, glide.api.models.commands.RangeOptions.ScoreBoundary min, glide.api.models.commands.RangeOptions.InfScoreBound max) {
        return addCommand(ZCount, key, min.toArgs(), max.toArgs());
    }

    public T zrangeWithScores(String key, RangeOptions.RangeQuery rangeQuery) {
        return addCommand(ZRange, RangeOptions.createZRangeArgs(key, rangeQuery, false, true));
    }
    
    public T zunionstore(String destination, glide.api.models.commands.WeightAggregateOptions.KeysOrWeightedKeys keysOrWeighted) {
        String[] keysArgs = keysOrWeighted.toArgs();
        String[] args = new String[1 + keysArgs.length];
        args[0] = destination;
        System.arraycopy(keysArgs, 0, args, 1, keysArgs.length);
        return addCommand(ZUnionStore, args);
    }
    
    public T bzmpop(String[] keys, glide.api.models.commands.ScoreFilter filter, double timeoutSeconds) {
        // Redis/Valkey syntax: BZMPOP <timeout> <numkeys> key [key ...] <MIN|MAX> [COUNT count]
        // Original implementation allocated an array of size keys.length + 3, leaving the last slot null
        // (timeout, numkeys, keys..., <uninitialized null>) before concatenating the MIN/MAX token. This
        // produced a null argument during serialization (observed as argIndex=3 for single-key variant).
        // We fix by allocating exactly 2 + keys.length slots (timeout, numkeys, keys...).
        if (keys == null) keys = new String[0];
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(timeoutSeconds);
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        String which = filter == glide.api.models.commands.ScoreFilter.MAX ? "MAX" : "MIN";
        return addCommand(BZMPop, glide.utils.ArrayTransformUtils.concatenateArrays(args, new String[]{which}));
    }
    
    public T dbsize() { return addCommand(DBSize); }

    public T geohash(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(GeoHash, args);
    }
    
    public T geopos(String key, String[] members) { String[] args = new String[members.length+1]; args[0]=key; System.arraycopy(members,0,args,1,members.length); return addCommand(GeoPos, args);} 
    
    public T geosearch(String key, glide.api.models.commands.geospatial.GeoSearchOrigin.CoordOrigin origin,
                       glide.api.models.commands.geospatial.GeoSearchShape shape) {
        return geosearch(key, origin, shape, null);
    }
    
    public T bitpos(String key, int bit) { return addCommand(BitPos, key, String.valueOf(bit)); }
    
    public T pubsubNumSub(String[] channels) { String[] args = new String[channels.length]; System.arraycopy(channels,0,args,0,channels.length); return addCommand(PubSubNumSub, args);} 

    public T smove(String source, String destination, String member) {
        return addCommand(SMove, source, destination, member);
    }
    
    public T copy(String source, String destination, int db, boolean replace) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(source); args.add(destination); args.add("DB"); args.add(String.valueOf(db));
        if (replace) args.add("REPLACE");
        return addCommand(Copy, args.toArray(new String[0]));
    }

    // Historical overload semantics: a 2-arg form (function, values) treats the
    // second parameter as ARGS (not KEYS) with numkeys=0. Tests rely on this
    // legacy behavior, so we preserve it here. To specify keys explicitly use
    // the 3-arg form fcall(function, keys, args).
    public T fcall(String function, String[] argsOnly) {
        if (argsOnly == null) argsOnly = new String[0];
        return fcall(function, new String[0], argsOnly);
    }

    public T fcallReadOnly(String function, String[] argsOnly) {
        if (argsOnly == null) argsOnly = new String[0];
        return fcallReadOnly(function, new String[0], argsOnly);
    }

    public T xrange(String key, glide.api.models.commands.stream.StreamRange start, glide.api.models.commands.stream.StreamRange end) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        for (String s : glide.api.models.commands.stream.StreamRange.toArgs(start, end)) args.add(s);
        return addCommand("XRANGE", args.toArray(new String[0]));
    }
    public T xrange(String key, glide.api.models.commands.stream.StreamRange start, glide.api.models.commands.stream.StreamRange end, long count) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        for (String s : glide.api.models.commands.stream.StreamRange.toArgs(start, end, count)) args.add(s);
        return addCommand("XRANGE", args.toArray(new String[0]));
    }
    public T xrevrange(String key, glide.api.models.commands.stream.StreamRange end, glide.api.models.commands.stream.StreamRange start) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        for (String s : glide.api.models.commands.stream.StreamRange.toArgs(end, start)) args.add(s);
        return addCommand("XREVRANGE", args.toArray(new String[0]));
    }
    public T xrevrange(String key, glide.api.models.commands.stream.StreamRange end, glide.api.models.commands.stream.StreamRange start, long count) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        for (String s : glide.api.models.commands.stream.StreamRange.toArgs(end, start)) args.add(s);
        args.add("COUNT"); args.add(String.valueOf(count));
        return addCommand("XREVRANGE", args.toArray(new String[0]));
    }


    public T sortStore(String key, String destination, glide.api.models.commands.SortOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        if (options != null) for (String s : options.toArgs()) args.add(s);
        args.add("STORE"); args.add(destination);
        return addCommand(Sort, args.toArray(new String[0]));
    }

    public T wait(int numreplicas, long timeoutMillis) { return addCommand(Wait, String.valueOf(numreplicas), String.valueOf(timeoutMillis)); }
    // Alias matching test usage (long,long)
    public T wait(long numreplicas, long timeoutMillis) { return wait((int) numreplicas, timeoutMillis); }

    public T xinfoStream(String key) { return addCommand("XINFO", "STREAM", key); }
    public T xinfoStreamFull(String key) { return addCommand("XINFO", "STREAM", key, "FULL"); }

    public T pubsubChannels() { return addCommand(PubSubChannels); }
    public T pubsubNumPat() { return addCommand(PubSubNumPat); }
    public T flushdb() { return addCommand(FlushDB); }
    public T flushdb(glide.api.models.commands.FlushMode mode) { return addCommand(FlushDB, mode.toString()); }
    public T pubsubChannels(String pattern) { return addCommand(PubSubChannels, pattern); }

    public T info() { return addCommand(Info); }

    public T info(glide.api.models.commands.InfoOptions.Section[] sections) {
        String[] args = new String[sections.length];
        for (int i = 0; i < sections.length; i++)
            args[i] = sections[i].name().toLowerCase();
        return addCommand(Info, args);
    }

    public T select(int index) {
        return addCommand(Select, String.valueOf(index));
    }

    public T move(String key, long db) {
        return addCommand(Move, key, String.valueOf(db));
    }

    public T lastsave() {
        return addCommand(LastSave);
    }

    public T objectFreq(String key) {
        return addCommand(ObjectFreq, key);
    }

    public T objectIdletime(String key) {
        return addCommand(ObjectIdleTime, key);
    }

    public T objectRefcount(String key) {
        return addCommand(ObjectRefCount, key);
    }

    public T withBinaryOutput() {
        this.binaryOutput = true;
        return getThis();
    }

    /** Internal accessor used by exec path to know if batch expects binary (GlideString) responses */
    public boolean isBinaryOutput() { return binaryOutput; }

    public T dump(String key) { return addCommand(Dump, key); }
    public T dump(glide.api.models.GlideString key) { return addCommand(Dump, key.toString()); }

    /**
     * Restores a key using the provided serialized value, previously obtained using DUMP.
     *
     * **Note**: This method is not supported in batch operations when the payload contains
     * binary data that cannot be represented as UTF-8. For binary payloads, use individual
     * commands via client.restore() instead.
     *
     * @see <a href="https://valkey.io/commands/restore/">valkey.io</a> for details.
     * @param key The key to restore.
     * @param ttl The time-to-live for the restored key in milliseconds.
     * @param payload The serialized value to restore (must be UTF-8 compatible for batch operations).
     * @return This batch instance for method chaining.
     * @throws IllegalArgumentException if payload contains binary data incompatible with UTF-8.
     */
    public T restore(GlideString key, int ttl, byte[] payload) {
        BinaryCommand cmd = new BinaryCommand(Restore)
            .addArgument(key.toString())
            .addArgument(String.valueOf(ttl))
            .addArgument(payload);
        return addCommand(cmd);
    }

    public T functionDump() { return addCommand(FunctionDump); }

    /**
     * Restores function libraries from the provided serialized payload.
     *
     * **Note**: This method is not supported in batch operations when the payload contains
     * binary data that cannot be represented as UTF-8. For binary payloads, use individual
     * commands via client.functionRestore() instead.
     *
     * @see <a href="https://valkey.io/commands/function-restore/">valkey.io</a> for details.
     * @param payload The serialized function data (must be UTF-8 compatible for batch operations).
     * @param policy The restoration policy.
     * @return This batch instance for method chaining.
     * @throws IllegalArgumentException if payload contains binary data incompatible with UTF-8.
     */
    public T functionRestore(byte[] payload, glide.api.models.commands.function.FunctionRestorePolicy policy) {
        // Valkey syntax: FUNCTION RESTORE <payload> [APPEND|FLUSH|REPLACE]
        // Ensure payload precedes policy so server parses correctly.
        BinaryCommand cmd = new BinaryCommand(FunctionRestore)
            .addArgument(payload)
            .addArgument(policy.toString());
        return addCommand(cmd);
    }

    public T scan(String cursor) {
        return addCommand(Scan, cursor);
    }

    public T scan(String cursor, glide.api.models.commands.scan.ScanOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(cursor);
        if (options != null)
            for (String s : options.toArgs())
                args.add(s);
        return addCommand(Scan, args.toArray(new String[0]));
    }

    public T scan(glide.api.models.GlideString cursor) {
        return addCommand(Scan, cursor.toString());
    }

    public T scan(glide.api.models.GlideString cursor, glide.api.models.commands.scan.ScanOptions options) {
        return scan(cursor.toString(), options);
    }

    // Additional overloads and commands for API parity with tests
    public T hincrBy(String key, String field, long amount) {
        return addCommand(HIncrBy, key, field, String.valueOf(amount));
    }

    public T hincrBy(String key, String field, int amount) {
        return hincrBy(key, field, (long) amount);
    }

    public T hincrBy(glide.api.models.GlideString key, glide.api.models.GlideString field, long amount) {
        return addCommand(HIncrBy, key.toString(), field.toString(), String.valueOf(amount));
    }

    public T hincrByFloat(String key, String field, double amount) {
        return addCommand(HIncrByFloat, key, field, String.valueOf(amount));
    }

    public T hincrByFloat(glide.api.models.GlideString key, glide.api.models.GlideString field, double amount) {
        return addCommand(HIncrByFloat, key.toString(), field.toString(), String.valueOf(amount));
    }

    public T lposCount(String key, String element, long count, LPosOptions options) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        args.add(element);
        args.add(LPosOptions.COUNT_VALKEY_API);
        args.add(String.valueOf(count));
        if (options != null) for (String s : options.toArgs()) args.add(s);
        return addCommand(LPos, args.toArray(new String[0]));
    }

    public T zmscore(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(ZMScore, args);
    }

    public T sunion(String[] keys) {
        String[] args = new String[keys.length];
        System.arraycopy(keys, 0, args, 0, keys.length);
        return addCommand(SUnion, args);
    }

    public T sdiff(String[] keys) {
        String[] args = new String[keys.length];
        System.arraycopy(keys, 0, args, 0, keys.length);
        return addCommand(SDiff, args);
    }

    public T zunion(glide.api.models.commands.WeightAggregateOptions.KeyArray keys) {
        return addCommand(ZUnion, keys.toArgs());
    }

    public T zunionWithScores(glide.api.models.commands.WeightAggregateOptions.KeyArray keys) {
        String[] args = glide.utils.ArrayTransformUtils.concatenateArrays(keys.toArgs(), new String[]{"WITHSCORES"});
        return addCommand(ZUnion, args);
    }

    public T zinterWithScores(glide.api.models.commands.WeightAggregateOptions.KeyArray keys) {
        String[] args = glide.utils.ArrayTransformUtils.concatenateArrays(keys.toArgs(), new String[]{"WITHSCORES"});
        return addCommand(ZInter, args);
    }
    public T zinterWithScores(glide.api.models.commands.WeightAggregateOptions.KeyArray keys, glide.api.models.commands.WeightAggregateOptions.Aggregate aggregate) {
        String[] args = glide.utils.ArrayTransformUtils.concatenateArrays(keys.toArgs(), new String[]{"WITHSCORES", "AGGREGATE", aggregate.name()});
        return addCommand(ZInter, args);
    }

    public T zunionWithScores(glide.api.models.commands.WeightAggregateOptions.KeyArray keys, glide.api.models.commands.WeightAggregateOptions.Aggregate aggregate) {
        String[] args = glide.utils.ArrayTransformUtils.concatenateArrays(keys.toArgs(), new String[]{"WITHSCORES", "AGGREGATE", aggregate.name()});
        return addCommand(ZUnion, args);
    }

    public T zintercard(String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        return addCommand(ZInterCard, args);
    }

    public T zintercard(String[] keys, int limit) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[args.length - 2] = "LIMIT";
        args[args.length - 1] = String.valueOf(limit);
        return addCommand(ZInterCard, args);
    }

    public T bzmpop(String[] keys, glide.api.models.commands.ScoreFilter filter, double timeoutSeconds, int count) {
        // See note above: allocate only needed slots to avoid trailing null.
        if (keys == null) keys = new String[0];
        String[] base = new String[keys.length + 2];
        base[0] = String.valueOf(timeoutSeconds);
        base[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, base, 2, keys.length);
        String which = filter == glide.api.models.commands.ScoreFilter.MAX ? "MAX" : "MIN";
        String[] withWhich = glide.utils.ArrayTransformUtils.concatenateArrays(base, new String[]{which});
        String[] full = glide.utils.ArrayTransformUtils.concatenateArrays(withWhich, new String[]{"COUNT", String.valueOf(count)});
        return addCommand(BZMPop, full);
    }

    public T xinfoConsumers(String key, String group) {
        return addCommand("XINFO", new String[]{"CONSUMERS", key, group});
    }
    public T xinfoGroups(String key) {
        return addCommand("XINFO", new String[]{"GROUPS", key});
    }

    public T xgroupCreateConsumer(String key, String group, String consumer) {
        return addCommand("XGROUP", new String[]{"CREATECONSUMER", key, group, consumer});
    }

    public T geodist(String key, String member1, String member2) {
        return addCommand(GeoDist, key, member1, member2);
    }

    public T geodist(String key, String member1, String member2, glide.api.models.commands.geospatial.GeoUnit unit) {
        // Use the Valkey API unit token (m|km|mi|ft) instead of enum constant name (METERS, etc.).
        // Previous implementation sent the enum name leading to server error: "unsupported unit provided".
        return addCommand(GeoDist, key, member1, member2, unit.getValkeyAPI());
    }

    public T geosearch(String key,
                       glide.api.models.commands.geospatial.GeoSearchOrigin.MemberOrigin origin,
                       glide.api.models.commands.geospatial.GeoSearchShape shape,
                       glide.api.models.commands.geospatial.GeoSearchOptions options,
                       glide.api.models.commands.geospatial.GeoSearchResultOptions resultOptions) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(key);
        if (origin != null) for (String s : origin.toArgs()) args.add(s);
        if (shape != null) for (String s : shape.toArgs()) args.add(s);
        if (options != null) for (String s : options.toArgs()) args.add(s);
        if (resultOptions != null) for (String s : resultOptions.toArgs()) args.add(s);
        return addCommand(GeoSearch, args.toArray(new String[0]));
    }

    public T functionStats() { return addCommand(FunctionStats); }
    public T functionDelete(String library) { return addCommand(FunctionDelete, library); }

    public T geosearchstore(String destination, String key,
                             glide.api.models.commands.geospatial.GeoSearchOrigin.MemberOrigin origin,
                             glide.api.models.commands.geospatial.GeoSearchShape shape,
                             glide.api.models.commands.geospatial.GeoSearchResultOptions resultOptions) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(destination); args.add(key);
        if (origin != null) for (String s : origin.toArgs()) args.add(s);
        if (shape != null) for (String s : shape.toArgs()) args.add(s);
        if (resultOptions != null) for (String s : resultOptions.toArgs()) args.add(s);
        return addCommand(GeoSearchStore, args.toArray(new String[0]));
    }

    public T geosearchstore(String destination, String key,
                             glide.api.models.commands.geospatial.GeoSearchOrigin.CoordOrigin origin,
                             glide.api.models.commands.geospatial.GeoSearchShape shape,
                             glide.api.models.commands.geospatial.GeoSearchStoreOptions storeOptions) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(destination); args.add(key);
        if (origin != null) for (String s : origin.toArgs()) args.add(s);
        if (shape != null) for (String s : shape.toArgs()) args.add(s);
        if (storeOptions != null) for (String s : storeOptions.toArgs()) args.add(s);
        return addCommand(GeoSearchStore, args.toArray(new String[0]));
    }
    public T geosearchstore(String destination, String key,
                             glide.api.models.commands.geospatial.GeoSearchOrigin.CoordOrigin origin,
                             glide.api.models.commands.geospatial.GeoSearchShape shape,
                             glide.api.models.commands.geospatial.GeoSearchStoreOptions storeOptions,
                             glide.api.models.commands.geospatial.GeoSearchResultOptions resultOptions) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(destination); args.add(key);
        if (origin != null) for (String s : origin.toArgs()) args.add(s);
        if (shape != null) for (String s : shape.toArgs()) args.add(s);
        if (storeOptions != null) for (String s : storeOptions.toArgs()) args.add(s);
        if (resultOptions != null) for (String s : resultOptions.toArgs()) args.add(s);
        return addCommand(GeoSearchStore, args.toArray(new String[0]));
    }

    public T bitpos(String key, int bit, int start) {
        return addCommand(BitPos, key, String.valueOf(bit), String.valueOf(start));
    }

    public T bitpos(String key, int bit, int start, int end) {
        return addCommand(BitPos, key, String.valueOf(bit), String.valueOf(start), String.valueOf(end));
    }

    public T bitfield(String key, glide.api.models.commands.bitmap.BitFieldOptions.BitFieldSubCommands[] subcommands) {
        String[] args = glide.api.models.commands.bitmap.BitFieldOptions.createBitFieldArgs(subcommands);
        String[] full = glide.utils.ArrayTransformUtils.concatenateArrays(new String[]{key}, args);
        return addCommand(BitField, full);
    }

    public T bitop(BitwiseOperation operation, String destKey, String[] keys) {
        String op = operation.name();
        String[] args = new String[keys.length + 2];
        args[0] = op; args[1] = destKey;
        System.arraycopy(keys, 0, args, 2, keys.length);
        return addCommand(BitOp, args);
    }

    /**
     * Utility method to build hash field command arguments with FIELDS keyword.
     *
     * @param key The hash key
     * @param fields The hash fields
     * @return Command arguments in format: [key, "FIELDS", field_count, ...fields]
     */
    private String[] buildHashFieldsArgs(String key, String[] fields) {
        String[] args = new String[fields.length + 3];
        args[0] = key;
        args[1] = "FIELDS";
        args[2] = String.valueOf(fields.length);
        System.arraycopy(fields, 0, args, 3, fields.length);
        return args;
    }

    /**
     * Returns the remaining time to live in milliseconds of hash fields.
     *
     * @see <a href="https://valkey.io/commands/hpttl/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields to get TTL for
     * @return This batch instance for method chaining
     */
    public T hpttl(String key, String[] fields) {
        return addCommand(HPTtl, buildHashFieldsArgs(key, fields));
    }

    /**
     * Returns the remaining time to live in milliseconds of hash fields (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hpttl/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields to get TTL for
     * @return This batch instance for method chaining
     */
    public T hpttl(GlideString key, GlideString[] fields) {
        String[] stringFields = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            stringFields[i] = fields[i].toString();
        }
        return hpttl(key.toString(), stringFields);
    }

    /**
     * Returns the absolute Unix timestamp in seconds at which hash fields will expire.
     *
     * @see <a href="https://valkey.io/commands/hexpiretime/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields to get expiration time for
     * @return This batch instance for method chaining
     */
    public T hexpiretime(String key, String[] fields) {
        return addCommand(HExpireTime, buildHashFieldsArgs(key, fields));
    }

    /**
     * Returns the absolute Unix timestamp in seconds at which hash fields will expire (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hexpiretime/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields to get expiration time for
     * @return This batch instance for method chaining
     */
    public T hexpiretime(GlideString key, GlideString[] fields) {
        String[] stringFields = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            stringFields[i] = fields[i].toString();
        }
        return hexpiretime(key.toString(), stringFields);
    }

    /**
     * Returns the absolute Unix timestamp in milliseconds at which hash fields will expire.
     *
     * @see <a href="https://valkey.io/commands/hpexpiretime/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields to get expiration time for
     * @return This batch instance for method chaining
     */
    public T hpexpiretime(String key, String[] fields) {
        return addCommand(HPExpireTime, buildHashFieldsArgs(key, fields));
    }

    /**
     * Returns the absolute Unix timestamp in milliseconds at which hash fields will expire (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/hpexpiretime/">valkey.io</a> for details.
     * @param key The hash key
     * @param fields The fields to get expiration time for
     * @return This batch instance for method chaining
     */
    public T hpexpiretime(GlideString key, GlideString[] fields) {
        String[] stringFields = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            stringFields[i] = fields[i].toString();
        }
        return hpexpiretime(key.toString(), stringFields);
    }
}
