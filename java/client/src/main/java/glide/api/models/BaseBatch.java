/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models;

import io.valkey.glide.api.commands.Command;
import io.valkey.glide.api.commands.CommandType;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all batch operations.
 * Provides basic functionality for accumulating commands and executing them as a batch.
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
    @SuppressWarnings("unchecked")
    protected T addCommand(Command command) {
        commands.add(command);
        return (T) this;
    }

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

    // ==================== COMMON COMMANDS ====================

    /**
     * Get a value from a key.
     *
     * @param key The key to get
     * @return This batch instance for method chaining
     */
    public T get(String key) {
        return addCommand(Command.get(key));
    }

    /**
     * Get a value from a key (supports binary data).
     *
     * @param key The key to get (supports binary data)
     * @return This batch instance for method chaining
     */
    public T get(GlideString key) {
        return addCommand(Command.get(key));
    }

    /**
     * Set a key-value pair.
     *
     * @param key The key to set
     * @param value The value to set
     * @return This batch instance for method chaining
     */
    public T set(String key, String value) {
        return addCommand(Command.set(key, value));
    }

    /**
     * Set a key-value pair (supports binary data).
     *
     * @param key The key to set (supports binary data)
     * @param value The value to set (supports binary data)
     * @return This batch instance for method chaining
     */
    public T set(GlideString key, GlideString value) {
        return addCommand(Command.set(key, value));
    }

    /**
     * Get multiple keys.
     *
     * @param keys The keys to get
     * @return This batch instance for method chaining
     */
    public T mget(String... keys) {
        return addCommand(Command.mget(keys));
    }

    /**
     * Get multiple keys (supports binary data).
     *
     * @param keys The keys to get (supports binary data)
     * @return This batch instance for method chaining
     */
    public T mget(GlideString... keys) {
        return addCommand(new Command(CommandType.MGET, keys));
    }

    /**
     * Get a field from a hash.
     *
     * @param key The hash key
     * @param field The field to get
     * @return This batch instance for method chaining
     */
    public T hget(String key, String field) {
        return addCommand(Command.hget(key, field));
    }

    /**
     * Get a field from a hash (supports binary data).
     *
     * @param key The hash key (supports binary data)
     * @param field The field to get (supports binary data)
     * @return This batch instance for method chaining
     */
    public T hget(GlideString key, GlideString field) {
        return addCommand(Command.hget(key, field));
    }

    /**
     * Set a field in a hash.
     *
     * @param key The hash key
     * @param field The field to set
     * @param value The value to set
     * @return This batch instance for method chaining
     */
    public T hset(String key, String field, String value) {
        return addCommand(Command.hset(key, field, value));
    }

    /**
     * Set a field in a hash (supports binary data).
     *
     * @param key The hash key (supports binary data)
     * @param field The field to set (supports binary data)
     * @param value The value to set (supports binary data)
     * @return This batch instance for method chaining
     */
    public T hset(GlideString key, GlideString field, GlideString value) {
        return addCommand(Command.hset(key, field, value));
    }

    /**
     * Add members to a set.
     *
     * @param key The set key
     * @param members The members to add
     * @return This batch instance for method chaining
     */
    public T sadd(String key, String... members) {
        return addCommand(Command.sadd(key, members));
    }

    /**
     * Add members to a set (supports binary data).
     *
     * @param key The set key (supports binary data)
     * @param members The members to add (supports binary data)
     * @return This batch instance for method chaining
     */
    public T sadd(GlideString key, GlideString... members) {
        GlideString[] args = new GlideString[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return addCommand(new Command(CommandType.SADD, args));
    }

    /**
     * Add a member to a sorted set.
     *
     * @param key The sorted set key
     * @param score The score
     * @param member The member to add
     * @return This batch instance for method chaining
     */
    public T zadd(String key, double score, String member) {
        return addCommand(Command.zadd(key, score, member));
    }

    /**
     * Add a member to a sorted set (supports binary data).
     *
     * @param key The sorted set key (supports binary data)
     * @param score The score
     * @param member The member to add (supports binary data)
     * @return This batch instance for method chaining
     */
    public T zadd(GlideString key, double score, GlideString member) {
        return addCommand(new Command(CommandType.ZADD, key, GlideString.of(String.valueOf(score)), member));
    }

    /**
     * Push elements to the left of a list.
     *
     * @param key The list key
     * @param elements The elements to push
     * @return This batch instance for method chaining
     */
    public T lpush(String key, String... elements) {
        return addCommand(Command.lpush(key, elements));
    }

    /**
     * Push elements to the left of a list (supports binary data).
     *
     * @param key The list key (supports binary data)
     * @param elements The elements to push (supports binary data)
     * @return This batch instance for method chaining
     */
    public T lpush(GlideString key, GlideString... elements) {
        GlideString[] args = new GlideString[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return addCommand(new Command(CommandType.LPUSH, args));
    }

    /**
     * Ping the server.
     *
     * @return This batch instance for method chaining
     */
    public T ping() {
        return addCommand(Command.ping());
    }

    /**
     * Get server information.
     *
     * @param section Optional section to get info for
     * @return This batch instance for method chaining
     */
    public T info(String... section) {
        return addCommand(Command.info(section));
    }

    /**
     * Add a custom command to the batch.
     *
     * @param command The command name
     * @param args The command arguments
     * @return This batch instance for method chaining
     */
    public T customCommand(String command, String... args) {
        return addCommand(new Command(command, args));
    }

    /**
     * Add a custom command to the batch (supports binary data).
     *
     * @param command The command name
     * @param args The command arguments (supports binary data)
     * @return This batch instance for method chaining
     */
    public T customCommand(String command, GlideString... args) {
        return addCommand(new Command(command, args));
    }

    /**
     * Add a custom command to the batch.
     *
     * @param commandType The command type
     * @param args The command arguments
     * @return This batch instance for method chaining
     */
    public T customCommand(CommandType commandType, String... args) {
        return addCommand(new Command(commandType, args));
    }

    /**
     * Add a custom command to the batch (supports binary data).
     *
     * @param commandType The command type
     * @param args The command arguments (supports binary data)
     * @return This batch instance for method chaining
     */
    public T customCommand(CommandType commandType, GlideString... args) {
        return addCommand(new Command(commandType, args));
    }
}
