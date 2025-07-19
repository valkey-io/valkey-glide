/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import io.valkey.glide.core.commands.Command;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static glide.api.models.commands.RequestType.*;
import glide.api.models.commands.scan.HScanOptions;
import glide.api.models.commands.ListDirection;

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
        return addCommand(Set, key, value);
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
        return addCommand(Set, key.toString(), value.toString());
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    
    public T get(String key) {
        return addCommand(Get, key);
    }

    /**
     * Gets the value of a key.
     *
     * @see <a href="https://valkey.io/commands/get/">valkey.io</a> for details.
     * @param key The key to get.
     * @return This batch instance for method chaining.
     */
    
    public T get(GlideString key) {
        return addCommand(Get, key.toString());
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
    
    public T hset(GlideString key, Map<GlideString, GlideString> fieldValuePairs) {
        String[] args = new String[fieldValuePairs.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (Map.Entry<GlideString, GlideString> entry : fieldValuePairs.entrySet()) {
            args[index++] = entry.getKey().toString();
            args[index++] = entry.getValue().toString();
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
    
    public T zadd(GlideString key, Map<GlideString, Double> membersScoresMap) {
        String[] args = new String[membersScoresMap.size() * 2 + 1];
        args[0] = key.toString();
        int index = 1;
        for (Map.Entry<GlideString, Double> entry : membersScoresMap.entrySet()) {
            args[index++] = entry.getValue().toString();
            args[index++] = entry.getKey().toString();
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
        return addCommand(CustomCommand, args);
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
     * Returns the internal encoding of the Redis object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for details.
     * @param key The key to check the encoding of.
     * @return This batch instance for method chaining.
     */
    
    public T objectEncoding(String key) {
        return addCommand(ObjectEncoding, key);
    }

    /**
     * Returns the internal encoding of the Redis object stored at key.
     *
     * @see <a href="https://valkey.io/commands/object-encoding/">valkey.io</a> for details.
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
        return addCommand(Unlink, keys);
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
        return addCommand(Unlink, stringKeys);
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
     * Set a timeout on a key.
     *
     * @see <a href="https://valkey.io/commands/expire/">valkey.io</a> for details.
     * @param key The key to set timeout on.
     * @param seconds The timeout in seconds.
     * @return This batch instance for method chaining.
     */
    
    public T expire(String key, long seconds) {
        return addCommand(Expire, key, String.valueOf(seconds));
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
        return addCommand(Expire, key.toString(), String.valueOf(seconds));
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
        return addCommand(ExpireAt, key, String.valueOf(timestamp));
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
        return addCommand(ExpireAt, key.toString(), String.valueOf(timestamp));
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
        return addCommand(PExpire, key, String.valueOf(milliseconds));
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
        return addCommand(PExpire, key.toString(), String.valueOf(milliseconds));
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
        return addCommand(PExpireAt, key, String.valueOf(timestamp));
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
        return addCommand(PExpireAt, key.toString(), String.valueOf(timestamp));
    }

    /**
     * Get the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    
    public T ttl(String key) {
        return addCommand(TTL, key);
    }

    /**
     * Get the remaining time to live of a key that has a timeout.
     *
     * @see <a href="https://valkey.io/commands/ttl/">valkey.io</a> for details.
     * @param key The key to check.
     * @return This batch instance for method chaining.
     */
    
    public T ttl(GlideString key) {
        return addCommand(TTL, key.toString());
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
        return addCommand(GetEx, key);
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
        return addCommand(GetEx, key, options.toString());
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
     * Returns the cardinality of the intersection of the sets stored at the specified keys.
     *
     * @see <a href="https://valkey.io/commands/sintercard/">valkey.io</a> for details.
     * @param keys The keys of the sets.
     * @return This batch instance for method chaining.
     */
    
    public T sintercard(String... keys) {
        return addCommand(SInterCard, keys);
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
        return addCommand(SInterCard, stringKeys);
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
        String[] args = new String[values.size() * 2 + 2];
        args[0] = key;
        args[1] = options.toString();
        int index = 2;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            args[index++] = entry.getKey();
            args[index++] = entry.getValue();
        }
        return addCommand(XAdd, args);
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

    /**
     * Gets the value of a key and deletes it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */
    
    public T getdel(String key) {
        return addCommand(GetDel, key);
    }

    /**
     * Gets the value of a key and deletes it.
     *
     * @see <a href="https://valkey.io/commands/getdel/">valkey.io</a> for details.
     * @param key The key to get and delete.
     * @return This batch instance for method chaining.
     */
    
    public T getdel(GlideString key) {
        return addCommand(GetDel, key.toString());
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
        return addCommand(Expire, key, String.valueOf(seconds), options.toString());
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
        return addCommand(Expire, key.toString(), String.valueOf(seconds), options.toString());
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
        return addCommand(ExpireAt, key, String.valueOf(timestamp), options.toString());
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
        return addCommand(ExpireAt, key.toString(), String.valueOf(timestamp), options.toString());
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
        return addCommand(Set, key, value, options.toString());
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
        return addCommand(Set, key.toString(), value.toString(), options.toString());
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
        String[] commandArgs = new String[1 + keys.length + args.length];
        commandArgs[0] = function;
        System.arraycopy(keys, 0, commandArgs, 1, keys.length);
        System.arraycopy(args, 0, commandArgs, 1 + keys.length, args.length);
        return addCommand(FCallReadOnly, commandArgs);
    }

    /**
     * Get string length.
     *
     * @see <a href="https://valkey.io/commands/strlen/">valkey.io</a> for details.
     * @param key The key
     * @return This batch instance for method chaining
     */
    
    public T strlen(String key) {
        return addCommand(Strlen, key);
    }

    /**
     * Get string length (GlideString variant).
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
     * Get multiple keys.
     *
     * @see <a href="https://valkey.io/commands/mget/">valkey.io</a> for details.
     * @param keys The keys to get
     * @return This batch instance for method chaining
     */
    
    public T mget(String[] keys) {
        return addCommand(MGet, keys);
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
     * Get list element at index.
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
     * Get list element at index (GlideString variant).
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
     * Get reverse rank of sorted set member.
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
     * Get reverse rank of sorted set member (GlideString variant).
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
     * Get configuration parameters.
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
     * Publish message to channel.
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param channel The channel
     * @param message The message
     * @return This batch instance for method chaining
     */
    public T publish(String channel, String message) {
        return addCommand(Publish, channel, message);
    }

    /**
     * Publish message to channel (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/publish/">valkey.io</a> for details.
     * @param channel The channel
     * @param message The message
     * @return This batch instance for method chaining
     */
    public T publish(GlideString channel, GlideString message) {
        return addCommand(Publish, channel.toString(), message.toString());
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
     * Add a stream entry (GlideString variant).
     *
     * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a> for details.
     * @param key The stream key
     * @param values The field-value pairs
     * @return This batch instance for method chaining
     */
    public T xadd(GlideString key, java.util.Map<GlideString, GlideString> values) {
        String[] args = new String[values.size() * 2 + 2];
        args[0] = key.toString();
        args[1] = "*"; // Auto-generate ID
        int i = 2;
        for (java.util.Map.Entry<GlideString, GlideString> entry : values.entrySet()) {
            args[i++] = entry.getKey().toString();
            args[i++] = entry.getValue().toString();
        }
        return addCommand(XAdd, args);
    }

    /**
     * Get a substring of the string value.
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
     * Get a substring of the string value (GlideString variant).
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
     * Get multiple hash field values.
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

    /**
     * Get multiple hash field values (GlideString variant).
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
}
