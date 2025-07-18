/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for all batch operations.
 * This is a simple data structure for collecting commands to be executed as a batch.
 * Command implementations are located in the client classes (BaseClient, GlideClient, etc.).
 */
public abstract class BaseBatch<T extends BaseBatch<T>> {
    /** List of commands to execute as a batch */
    protected final List<Command> commands;
    
    /** Whether this batch should be executed atomically (transaction) */
    protected final boolean isAtomic;

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
    public T addCommand(Command command) {
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
    public T addCommand(CommandType commandType, String... args) {
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
     * Get all commands in this batch.
     *
     * @return List of commands
     */
    public List<Command> getCommands() {
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
     * Get the number of commands in this batch.
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
    
    public T set(GlideString key, GlideString value) {
        return addCommand(CommandType.SET, key.toString(), value.toString());
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    
    public T get(String key) {
        return addCommand(CommandType.GET, key);
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    
    public T get(GlideString key) {
        return addCommand(CommandType.GET, key.toString());
    }

    /**
     * Pings the server.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    
    public T ping() {
        return addCommand(CommandType.PING);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */
    
    public T ping(String message) {
        return addCommand(CommandType.PING, message);
    }

    /**
     * Pings the server with a message.
     *
     * @see <a href="https://valkey.io/commands/ping/">valkey.io</a> for details.
     * @param message The message to include in the ping.
     * @return This batch instance for method chaining.
     */
    
    public T ping(GlideString message) {
        return addCommand(CommandType.PING, message.toString());
    }

    /**
     * Flushes all the databases.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    
    public T flushall() {
        return addCommand(CommandType.FLUSHALL);
    }

    /**
     * Flushes all the databases.
     *
     * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> for details.
     * @param mode The flush mode (SYNC or ASYNC).
     * @return This batch instance for method chaining.
     */
    
    public T flushall(glide.api.models.commands.FlushMode mode) {
        return addCommand(CommandType.FLUSHALL, mode.toString());
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
        return addCommand(CommandType.LPUSH, args);
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
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return addCommand(CommandType.LPUSH, args);
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
    
    public T sadd(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return addCommand(CommandType.SADD, args);
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
        return addCommand(CommandType.HSET, args);
    }

    /**
     * Set one or more hash fields to values.
     *
     * @see <a href="https://valkey.io/commands/hset/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @param fieldValuePairs The field-value pairs to set.
     * @return This batch instance for method chaining.
     */
    
    public T hset(GlideString key, Map<GlideString, GlideString> fieldValuePairs) {
        String[] args = new String[fieldValuePairs.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (Map.Entry<GlideString, GlideString> entry : fieldValuePairs.entrySet()) {
            args[index++] = entry.getKey().toString();
            args[index++] = entry.getValue().toString();
        }
        return addCommand(CommandType.HSET, args);
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
        return addCommand(CommandType.ZADD, args);
    }

    /**
     * Add one or more members to a sorted set, or update the score if the member already exists.
     *
     * @see <a href="https://valkey.io/commands/zadd/">valkey.io</a> for details.
     * @param key The key of the sorted set.
     * @param membersScoresMap A map of members to their scores.
     * @return This batch instance for method chaining.
     */
    
    public T zadd(GlideString key, Map<GlideString, Double> membersScoresMap) {
        String[] args = new String[membersScoresMap.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (Map.Entry<GlideString, Double> entry : membersScoresMap.entrySet()) {
            args[index++] = entry.getValue().toString();
            args[index++] = entry.getKey().toString();
        }
        return addCommand(CommandType.ZADD, args);
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
        return addCommand(CommandType.PFADD, args);
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
        return addCommand(CommandType.PFADD, args);
    }

    /**
     * Execute a custom command with the given arguments.
     *
     * @param args The command arguments.
     * @return This batch instance for method chaining.
     */
    
    public T customCommand(String... args) {
        return addCommand(CommandType.CUSTOM_COMMAND, args);
    }

    /**
     * Returns if the given keys exist in the database.
     *
     * @see <a href="https://valkey.io/commands/exists/">valkey.io</a> for details.
     * @param keys The keys to check.
     * @return This batch instance for method chaining.
     */
    
    public T exists(String... keys) {
        return addCommand(CommandType.EXISTS, keys);
    }

    /**
     * Returns if the given keys exist in the database.
     *
     * @see <a href="https://valkey.io/commands/exists/">valkey.io</a> for details.
     * @param keys The keys to check.
     * @return This batch instance for method chaining.
     */
    
    public T exists(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.EXISTS, stringKeys);
    }

    /**
     * Remove the existing timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/persist/">valkey.io</a> for details.
     * @param key The key to remove timeout from.
     * @return This batch instance for method chaining.
     */
    
    public T persist(String key) {
        return addCommand(CommandType.PERSIST, key);
    }

    /**
     * Remove the existing timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/persist/">valkey.io</a> for details.
     * @param key The key to remove timeout from.
     * @return This batch instance for method chaining.
     */
    
    public T persist(GlideString key) {
        return addCommand(CommandType.PERSIST, key.toString());
    }

    /**
     * Returns the string representation of the type of the value stored at key.
     *
     * @see <a href="https://valkey.io/commands/type/">valkey.io</a> for details.
     * @param key The key to check the type of.
     * @return This batch instance for method chaining.
     */
    
    public T type(String key) {
        return addCommand(CommandType.TYPE, key);
    }

    /**
     * Returns the string representation of the type of the value stored at key.
     *
     * @see <a href="https://valkey.io/commands/type/">valkey.io</a> for details.
     * @param key The key to check the type of.
     * @return This batch instance for method chaining.
     */
    
    public T type(GlideString key) {
        return addCommand(CommandType.TYPE, key.toString());
    }

    /**
     * Returns the internal encoding of the Redis object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for details.
     * @param key The key to check the encoding of.
     * @return This batch instance for method chaining.
     */
    
    public T objectEncoding(String key) {
        return addCommand(CommandType.OBJECT_ENCODING, key);
    }

    /**
     * Returns the internal encoding of the Redis object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for details.
     * @param key The key to check the encoding of.
     * @return This batch instance for method chaining.
     */
    
    public T objectEncoding(GlideString key) {
        return addCommand(CommandType.OBJECT_ENCODING, key.toString());
    }

    /**
     * Alters the last access time of the given keys.
     *
     * @see <a href="https://valkey.io/commands/touch/">valkey.io</a> for details.
     * @param keys The keys to touch.
     * @return This batch instance for method chaining.
     */
    
    public T touch(String... keys) {
        return addCommand(CommandType.TOUCH, keys);
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
        return addCommand(CommandType.TOUCH, stringKeys);
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
        return addCommand(CommandType.RENAME, key, newkey);
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
        return addCommand(CommandType.RENAME, key.toString(), newkey.toString());
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
        return addCommand(CommandType.RENAMENX, key, newkey);
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
        return addCommand(CommandType.RENAMENX, key.toString(), newkey.toString());
    }

    /**
     * Unlinks (deletes) the key(s) in a non-blocking way.
     *
     * @see <a href="https://valkey.io/commands/unlink/">valkey.io</a> for details.
     * @param keys The keys to unlink.
     * @return This batch instance for method chaining.
     */
    
    public T unlink(String... keys) {
        return addCommand(CommandType.UNLINK, keys);
    }

    /**
     * Unlinks (deletes) the key(s) in a non-blocking way.
     *
     * @see <a href="https://valkey.io/commands/unlink/">valkey.io</a> for details.
     * @param keys The keys to unlink.
     * @return This batch instance for method chaining.
     */
    
    public T unlink(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.UNLINK, stringKeys);
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */
    
    public T del(String... keys) {
        return addCommand(CommandType.DEL, keys);
    }

    /**
     * Removes one or more keys from the database.
     *
     * @see <a href="https://valkey.io/commands/del/">valkey.io</a> for details.
     * @param keys The keys to delete.
     * @return This batch instance for method chaining.
     */
    
    public T del(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.DEL, stringKeys);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    
    public T sort(String key) {
        return addCommand(CommandType.SORT, key);
    }

    /**
     * Sorts the elements in the list, set or sorted set at key.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for details.
     * @param key The key to sort.
     * @return This batch instance for method chaining.
     */
    
    public T sort(GlideString key) {
        return addCommand(CommandType.SORT, key.toString());
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
        return addCommand(CommandType.SORT, key, "STORE", destination);
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
        return addCommand(CommandType.SORT, key.toString(), "STORE", destination.toString());
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
    
    public T expire(GlideString key, long seconds) {
        return addCommand(CommandType.EXPIRE, key.toString(), String.valueOf(seconds));
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
        return addCommand(CommandType.EXPIREAT, key, String.valueOf(timestamp));
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
        return addCommand(CommandType.EXPIREAT, key.toString(), String.valueOf(timestamp));
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
        return addCommand(CommandType.PEXPIRE, key, String.valueOf(milliseconds));
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
        return addCommand(CommandType.PEXPIRE, key.toString(), String.valueOf(milliseconds));
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
        return addCommand(CommandType.PEXPIREAT, key, String.valueOf(timestamp));
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
        return addCommand(CommandType.PEXPIREAT, key.toString(), String.valueOf(timestamp));
    }

    /**
     * Get the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    
    public T ttl(String key) {
        return addCommand(CommandType.TTL, key);
    }

    /**
     * Get the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    
    public T ttl(GlideString key) {
        return addCommand(CommandType.TTL, key.toString());
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
        return addCommand(CommandType.LRANGE, key, String.valueOf(start), String.valueOf(end));
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
        return addCommand(CommandType.LRANGE, key.toString(), String.valueOf(start), String.valueOf(end));
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
            return addCommand(CommandType.COPY, source, destination, "REPLACE");
        } else {
            return addCommand(CommandType.COPY, source, destination);
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
            return addCommand(CommandType.COPY, source.toString(), destination.toString(), "REPLACE");
        } else {
            return addCommand(CommandType.COPY, source.toString(), destination.toString());
        }
    }

    // TODO: Implement geoadd() and geosearch() methods
    // These methods require GeospatialData class and related classes to be implemented first

    /**
     * Deletes all function libraries.
     *
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @param mode The flush mode.
     * @return This batch instance for method chaining.
     */
    
    public T functionFlush(glide.api.models.commands.FlushMode mode) {
        return addCommand(CommandType.FUNCTION_FLUSH, mode.toString());
    }

    /**
     * Deletes all function libraries.
     *
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    
    public T functionFlush() {
        return addCommand(CommandType.FUNCTION_FLUSH);
    }

    /**
     * Returns a random key from the keyspace.
     *
     * @see <a href="https://valkey.io/commands/randomkey/">valkey.io</a> for details.
     * @return This batch instance for method chaining.
     */
    
    public T randomKey() {
        return addCommand(CommandType.RANDOMKEY);
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
        return addCommand(CommandType.LCS, key1, key2);
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
        return addCommand(CommandType.LCS, key1.toString(), key2.toString());
    }

    /**
     * Gets the value of a key and optionally sets its expiration.
     *
     * @see <a href="https://valkey.io/commands/getex/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    
    public T getex(String key) {
        return addCommand(CommandType.GETEX, key);
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
        return addCommand(CommandType.GETEX, key, options.toString());
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
        return addCommand(CommandType.HGET, key, field);
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
        return addCommand(CommandType.HGET, key.toString(), field.toString());
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/llen/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    
    public T llen(String key) {
        return addCommand(CommandType.LLEN, key);
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @see <a href="https://valkey.io/commands/llen/">valkey.io</a> for details.
     * @param key The key of the list.
     * @return This batch instance for method chaining.
     */
    
    public T llen(GlideString key) {
        return addCommand(CommandType.LLEN, key.toString());
    }

    /**
     * Echoes the given string.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to echo.
     * @return This batch instance for method chaining.
     */
    
    public T echo(String message) {
        return addCommand(CommandType.ECHO, message);
    }

    /**
     * Echoes the given string.
     *
     * @see <a href="https://valkey.io/commands/echo/">valkey.io</a> for details.
     * @param message The message to echo.
     * @return This batch instance for method chaining.
     */
    
    public T echo(GlideString message) {
        return addCommand(CommandType.ECHO, message.toString());
    }

    /**
     * Returns the cardinality of the set stored at key.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    
    public T scard(String key) {
        return addCommand(CommandType.SCARD, key);
    }

    /**
     * Returns the cardinality of the set stored at key.
     *
     * @see <a href="https://valkey.io/commands/scard/">valkey.io</a> for details.
     * @param key The key of the set.
     * @return This batch instance for method chaining.
     */
    
    public T scard(GlideString key) {
        return addCommand(CommandType.SCARD, key.toString());
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
        return addCommand(CommandType.SREM, args);
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
        return addCommand(CommandType.SREM, args);
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
        return addCommand(CommandType.ZRANK, key, member);
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
        return addCommand(CommandType.ZRANK, key.toString(), member.toString());
    }

    /**
     * Returns the number of bits set to 1 in the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key of the string.
     * @return This batch instance for method chaining.
     */
    
    public T bitcount(String key) {
        return addCommand(CommandType.BITCOUNT, key);
    }

    /**
     * Returns the number of bits set to 1 in the string value stored at key.
     *
     * @see <a href="https://valkey.io/commands/bitcount/">valkey.io</a> for details.
     * @param key The key of the string.
     * @return This batch instance for method chaining.
     */
    
    public T bitcount(GlideString key) {
        return addCommand(CommandType.BITCOUNT, key.toString());
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
        return addCommand(CommandType.BITCOUNT, key, String.valueOf(start), String.valueOf(end), indexType.toString());
    }

    /**
     * Returns the cardinality of the intersection of the sets stored at the specified keys.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    
    public T sintercard(String... keys) {
        return addCommand(CommandType.SINTERCARD, keys);
    }

    /**
     * Returns the cardinality of the intersection of the sets stored at the specified keys.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    
    public T sintercard(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return addCommand(CommandType.SINTERCARD, stringKeys);
    }

    /**
     * Returns the cardinality of the HyperLogLog stored at key.
     *
     * @see <a href="https://valkey.io/commands/pfcount/">valkey.io</a> for details.
     * @param keys The keys of the HyperLogLogs.
     * @return This batch instance for method chaining.
     */
    
    public T pfcount(String... keys) {
        return addCommand(CommandType.PFCOUNT, keys);
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
        return addCommand(CommandType.PFCOUNT, stringKeys);
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
        return addCommand(CommandType.CONFIG_SET, args);
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
        String[] args = new String[values.size() * 2 + 2];
        args[0] = key;
        args[1] = options.toString();
        int index = 2;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(CommandType.XADD, args);
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
        return addCommand(CommandType.XACK, args);
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
        return addCommand(CommandType.XAUTOCLAIM, key, groupName, consumerName, String.valueOf(minIdleTime), start);
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
        return addCommand(CommandType.BZPOPMIN, args);
    }

    /**
     * Gets the value of a key and deletes it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */
    
    public T getdel(String key) {
        return addCommand(CommandType.GETDEL, key);
    }

    /**
     * Gets the value of a key and deletes it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */
    
    public T getdel(GlideString key) {
        return addCommand(CommandType.GETDEL, key.toString());
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
        return addCommand(CommandType.LCS, key1, key2, "LEN");
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
        return addCommand(CommandType.LCS, key1.toString(), key2.toString(), "LEN");
    }

    /**
     * Returns the number of fields in the hash stored at key.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    
    public T hlen(String key) {
        return addCommand(CommandType.HLEN, key);
    }

    /**
     * Returns the number of fields in the hash stored at key.
     *
     * @see <a href="https://valkey.io/commands/hlen/">valkey.io</a> for details.
     * @param key The key of the hash.
     * @return This batch instance for method chaining.
     */
    
    public T hlen(GlideString key) {
        return addCommand(CommandType.HLEN, key.toString());
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
        return addCommand(CommandType.EXPIRE, key, String.valueOf(seconds), options.toString());
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
        return addCommand(CommandType.EXPIRE, key.toString(), String.valueOf(seconds), options.toString());
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
        return addCommand(CommandType.EXPIREAT, key, String.valueOf(timestamp), options.toString());
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
        return addCommand(CommandType.EXPIREAT, key.toString(), String.valueOf(timestamp), options.toString());
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
        return addCommand(CommandType.SET, key, value, options.toString());
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
        return addCommand(CommandType.SET, key.toString(), value.toString(), options.toString());
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
        return addCommand(CommandType.LCS, key1, key2, "IDX");
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
        return addCommand(CommandType.LCS, key1.toString(), key2.toString(), "IDX");
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
        String[] commandArgs = new String[1 + keys.length + args.length];
        commandArgs[0] = function;
        System.arraycopy(keys, 0, commandArgs, 1, keys.length);
        System.arraycopy(args, 0, commandArgs, 1 + keys.length, args.length);
        return addCommand(CommandType.FCALL, commandArgs);
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
        String[] commandArgs = new String[1 + keys.length + args.length];
        commandArgs[0] = function;
        System.arraycopy(keys, 0, commandArgs, 1, keys.length);
        System.arraycopy(args, 0, commandArgs, 1 + keys.length, args.length);
        return addCommand(CommandType.FCALL_RO, commandArgs);
    }

    /**
     * Get string length.
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */
    
    public T strlen(String key) {
        return addCommand(CommandType.STRLEN, key);
    }

    /**
     * Get string length (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */
    
    public T strlen(GlideString key) {
        return addCommand(CommandType.STRLEN, key.toString());
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
        return addCommand(CommandType.APPEND, key, value);
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
        return addCommand(CommandType.APPEND, key.toString(), value.toString());
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
        return addCommand(CommandType.MSET, args);
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
        return addCommand(CommandType.MSET, args);
    }

    /**
     * Get multiple keys.
     *
     * @see <a href="https://valkey.io/commands/mget/">valkey.io</a> for details.
     * @param keys The keys to get
     * @return This batch instance for method chaining
     */
    
    public T mget(String[] keys) {
        return addCommand(CommandType.MGET, keys);
    }

    /**
     * Get multiple keys (GlideString variant).
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
        return addCommand(CommandType.MGET, stringKeys);
    }

    /**
     * Increment integer value.
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */
    
    public T incr(String key) {
        return addCommand(CommandType.INCR, key);
    }

    /**
     * Increment integer value (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/incr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */
    
    public T incr(GlideString key) {
        return addCommand(CommandType.INCR, key.toString());
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
        return addCommand(CommandType.INCRBY, key, String.valueOf(amount));
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
        return addCommand(CommandType.INCRBY, key.toString(), String.valueOf(amount));
    }

    /**
     * Decrement integer value.
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */
    
    public T decr(String key) {
        return addCommand(CommandType.DECR, key);
    }

    /**
     * Decrement integer value (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/decr/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */
    
    public T decr(GlideString key) {
        return addCommand(CommandType.DECR, key.toString());
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
        return addCommand(CommandType.DECRBY, key, String.valueOf(amount));
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
        return addCommand(CommandType.DECRBY, key.toString(), String.valueOf(amount));
    }
}
