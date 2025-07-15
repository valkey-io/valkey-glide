/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import glide.api.models.GlideString;
import glide.api.models.BaseBatch;
import io.valkey.glide.core.client.GlideClient;
import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for Glide clients providing common functionality.
 * This class acts as a bridge between the integration test API and the refactored core client.
 */
public abstract class BaseClient {

    /** The "OK" response from Redis/Valkey commands. */
    public static final String OK = "OK";

    protected final GlideClient client;

    protected BaseClient(GlideClient client) {
        this.client = client;
    }

    /**
     * Executes a command with the given type and arguments.
     *
     * @param commandType The type of command to execute
     * @param args        The arguments for the command
     * @return A CompletableFuture containing the result
     */
    protected CompletableFuture<Object> executeCommand(CommandType commandType, String... args) {
        Command command = new Command(commandType, args);
        return client.executeCommand(command);
    }

    /**
     * Get the value of a key.
     *
     * @param key The key to get
     * @return A CompletableFuture containing the value or null if key doesn't exist
     */
    public CompletableFuture<String> get(String key) {
        return client.get(key);
    }

    /**
     * Get the value of a key (supports binary data).
     *
     * @param key The key to get (supports binary data)
     * @return A CompletableFuture containing the value or null if key doesn't exist
     */
    public CompletableFuture<GlideString> get(GlideString key) {
        return executeCommand(CommandType.GET, key.toString())
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Set key to hold the string value.
     *
     * @param key   The key to set
     * @param value The value to set
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> set(String key, String value) {
        return client.set(key, value);
    }

    /**
     * Set key to hold the string value (supports binary data).
     *
     * @param key   The key to set (supports binary data)
     * @param value The value to set (supports binary data)
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> set(GlideString key, GlideString value) {
        return executeCommand(CommandType.SET, key.toString(), value.toString())
                .thenApply(result -> result.toString());
    }

    /**
     * Execute a PING command.
     *
     * @return A CompletableFuture containing "PONG"
     */
    public CompletableFuture<String> ping() {
        return client.ping();
    }

    /**
     * Get multiple values for the given keys.
     *
     * @param keys The keys to get
     * @return A CompletableFuture containing an array of values
     */
    public CompletableFuture<String[]> mget(String... keys) {
        return executeCommand(CommandType.MGET, keys)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] strings = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        strings[i] = objects[i] == null ? null : objects[i].toString();
                    }
                    return strings;
                }
                return new String[0];
            });
    }

    /**
     * Get multiple values for the given keys (supports binary data).
     *
     * @param keys The keys to get (supports binary data)
     * @return A CompletableFuture containing an array of values
     */
    public CompletableFuture<GlideString[]> mget(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.MGET, stringKeys)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        GlideString[] glideStrings = new GlideString[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            glideStrings[i] = objects[i] == null ? null : GlideString.of(objects[i].toString());
                        }
                        return glideStrings;
                    }
                    return new GlideString[0];
                });
    }

    /**
     * Set multiple keys to multiple values.
     *
     * @param keyValuePairs Map of key-value pairs to set
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> mset(Map<String, String> keyValuePairs) {
        String[] args = new String[keyValuePairs.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : keyValuePairs.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return executeCommand(CommandType.MSET, args)
            .thenApply(result -> result.toString());
    }

    /**
     * Set multiple keys to multiple values (supports binary data).
     *
     * @param keyValuePairs Map of key-value pairs to set (supports binary data)
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> mset(Map<GlideString, GlideString> keyValuePairs) {
        String[] args = new String[keyValuePairs.size() * 2];
        int i = 0;
        for (Map.Entry<GlideString, GlideString> entry : keyValuePairs.entrySet()) {
            args[i++] = entry.getKey().toString();
            args[i++] = entry.getValue().toString();
        }
        return executeCommand(CommandType.MSET, args)
                .thenApply(result -> result.toString());
    }

    /**
     * Delete one or more keys.
     *
     * @param keys The keys to delete
     * @return A CompletableFuture containing the number of keys that were removed
     */
    public CompletableFuture<Long> del(String... keys) {
        return executeCommand(CommandType.DEL, keys)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Delete one or more keys (supports binary data).
     *
     * @param keys The keys to delete (supports binary data)
     * @return A CompletableFuture containing the number of keys that were removed
     */
    public CompletableFuture<Long> del(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.DEL, stringKeys)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Check if one or more keys exist.
     *
     * @param keys The keys to check
     * @return A CompletableFuture containing the number of keys that exist
     */
    public CompletableFuture<Long> exists(String... keys) {
        return executeCommand(CommandType.EXISTS, keys)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Set the string value of a hash field.
     *
     * @param key   The key of the hash
     * @param field The field in the hash
     * @param value The value to set
     * @return A CompletableFuture containing the number of fields that were added
     */
    public CompletableFuture<Long> hset(String key, String field, String value) {
        return executeCommand(CommandType.HSET, key, field, value)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Set the string value of a hash field (supports binary data).
     *
     * @param key   The key of the hash (supports binary data)
     * @param field The field in the hash (supports binary data)
     * @param value The value to set (supports binary data)
     * @return A CompletableFuture containing the number of fields that were added
     */
    public CompletableFuture<Long> hset(GlideString key, GlideString field, GlideString value) {
        return executeCommand(CommandType.HSET, key.toString(), field.toString(), value.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get the value of a hash field.
     *
     * @param key   The key of the hash
     * @param field The field in the hash
     * @return A CompletableFuture containing the value or null if field doesn't exist
     */
    public CompletableFuture<String> hget(String key, String field) {
        return executeCommand(CommandType.HGET, key, field)
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Get the value of a hash field (supports binary data).
     *
     * @param key   The key of the hash (supports binary data)
     * @param field The field in the hash (supports binary data)
     * @return A CompletableFuture containing the value or null if field doesn't exist
     */
    public CompletableFuture<GlideString> hget(GlideString key, GlideString field) {
        return executeCommand(CommandType.HGET, key.toString(), field.toString())
            .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Get all the fields and values in a hash.
     *
     * @param key The key of the hash
     * @return A CompletableFuture containing a map of field-value pairs
     */
    public CompletableFuture<Map<String, String>> hgetall(String key) {
        return executeCommand(CommandType.HGETALL, key)
            .thenApply(result -> {
                Map<String, String> map = new java.util.HashMap<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (int i = 0; i < objects.length; i += 2) {
                        if (i + 1 < objects.length) {
                            String field = objects[i].toString();
                            String value = objects[i + 1].toString();
                            map.put(field, value);
                        }
                    }
                }
                return map;
            });
    }

    /**
     * Execute a custom command.
     *
     * @param args The command arguments
     * @return A CompletableFuture containing the command result
     */
    public CompletableFuture<Object> customCommand(String[] args) {
        if (args.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        // Try to map the command name to a CommandType
        try {
            CommandType commandType = CommandType.valueOf(args[0].toUpperCase());
            String[] commandArgs = new String[args.length - 1];
            System.arraycopy(args, 1, commandArgs, 0, args.length - 1);
            return executeCommand(commandType, commandArgs);
        } catch (IllegalArgumentException e) {
            // If command is not in enum, execute as raw command
            return executeCommand(CommandType.GET, args); // Fallback - this needs proper handling
        }
    }

    /**
     * Execute a custom command with GlideString arguments.
     *
     * @param args The command arguments as GlideString array
     * @return A CompletableFuture containing the command result
     */
    public CompletableFuture<Object> customCommand(GlideString[] args) {
        if (args.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        // Convert GlideString[] to String[]
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }

        return customCommand(stringArgs);
    }

    /**
     * Execute a batch of commands.
     *
     * @param batch The batch of commands to execute
     * @return A CompletableFuture containing an array of results
     */
    public CompletableFuture<Object[]> exec(BaseBatch<?> batch) {
        return client.exec(batch);
    }

    /**
     * Removes and returns the first element from the list stored at key.
     * Blocks until an element is available or timeout is reached.
     *
     * @param keys    The keys of the lists to pop from
     * @param timeout The timeout in seconds
     * @return A CompletableFuture containing an array with the key and the popped
     *         element
     */
    public CompletableFuture<String[]> blpop(String[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[keys.length] = String.valueOf(timeout);

        return executeCommand(CommandType.BLPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        String[] strings = new String[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            strings[i] = objects[i] == null ? null : objects[i].toString();
                        }
                        return strings;
                    }
                    return null;
                });
    }

    /**
     * Inserts elements at the head of the list stored at key.
     *
     * @param key      The key of the list
     * @param elements The elements to push
     * @return A CompletableFuture containing the length of the list after the push
     *         operation
     */
    public CompletableFuture<Long> lpush(String key, String[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);

        return executeCommand(CommandType.LPUSH, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get client statistics.
     *
     * @return A map containing client statistics
     */
    public Map<String, Object> getStatistics() {
        // Return basic statistics for now
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("connections", 1);
        stats.put("requests", 0);
        return stats;
    }

    /**
     * Close the client connection.
     */
    public void close() {
        if (client != null) {
            client.close();
        }
    }
}
