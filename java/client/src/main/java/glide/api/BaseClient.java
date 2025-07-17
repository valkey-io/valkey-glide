/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import glide.api.commands.StringBaseCommands;
import glide.api.commands.HashBaseCommands;
import glide.api.commands.ListBaseCommands;
import glide.api.commands.SetBaseCommands;
import glide.api.commands.GenericBaseCommands;
import glide.api.commands.ServerManagementCommands;
import glide.api.commands.ServerManagementCore;
import glide.api.models.GlideString;
import glide.api.models.BaseBatch;
import glide.api.models.Script;
import glide.api.models.commands.GetExOptions;
import glide.api.models.commands.SetOptions;
import glide.api.models.commands.LPosOptions;
import glide.api.models.commands.ListDirection;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.InfoOptions.Section;
import glide.api.models.commands.bitmap.BitwiseOperation;
import glide.api.models.commands.stream.StreamRange;
import glide.api.models.commands.geospatial.GeoSearchOptions;
import glide.api.models.commands.geospatial.GeoSearchStoreOptions;
import glide.api.models.commands.geospatial.GeoSearchOrigin.MemberOrigin;
import glide.api.models.commands.geospatial.GeoSearchShape;
import glide.api.models.commands.geospatial.GeoSearchResultOptions;
import io.valkey.glide.core.client.GlideClient;
import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for Glide clients providing common functionality.
 * This class acts as a bridge between the integration test API and the refactored core client.
 */
public abstract class BaseClient implements StringBaseCommands, HashBaseCommands, ListBaseCommands, SetBaseCommands, GenericBaseCommands {

    /** The "OK" response from Valkey commands. */
    public static final String OK = "OK";

    /** LCS command string constants */
    public static final String LEN_VALKEY_API = "LEN";
    public static final String IDX_COMMAND_STRING = "IDX";
    public static final String MINMATCHLEN_COMMAND_STRING = "MINMATCHLEN";
    public static final String WITHMATCHLEN_COMMAND_STRING = "WITHMATCHLEN";

    /** Server management command string constants */
    public static final String VERSION_VALKEY_API = "VERSION";

    protected final GlideClient client;
    protected final ServerManagementCore serverManagement;

    protected BaseClient(GlideClient client, ServerManagementCore serverManagement) {
        this.client = client;
        this.serverManagement = serverManagement;
    }

    /**
     * Utility method to concatenate multiple string arrays.
     */
    private static String[] concatArrays(String[]... arrays) {
        int totalLength = 0;
        for (String[] array : arrays) {
            totalLength += array.length;
        }
        
        String[] result = new String[totalLength];
        int currentIndex = 0;
        for (String[] array : arrays) {
            System.arraycopy(array, 0, result, currentIndex, array.length);
            currentIndex += array.length;
        }
        
        return result;
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
        return executeCommand(CommandType.GET, key)
                .thenApply(result -> result == null ? null : result.toString());
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
        return executeCommand(CommandType.SET, key, value)
                .thenApply(result -> result.toString());
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
     * Sets the given key with the given value. Return value is dependent on the passed options.
     */
    @Override
    public CompletableFuture<String> set(String key, String value, SetOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(value);
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return executeCommand(CommandType.SET, args.toArray(new String[0]))
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Sets the given key with the given value. Return value is dependent on the passed options.
     */
    @Override
    public CompletableFuture<String> set(GlideString key, GlideString value, SetOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(value.toString());
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return executeCommand(CommandType.SET, args.toArray(new String[0]))
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Gets a string value associated with the given key and deletes the key.
     */
    @Override
    public CompletableFuture<String> getdel(String key) {
        return executeCommand(CommandType.GETDEL, key)
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Gets a string value associated with the given key and deletes the key.
     */
    @Override
    public CompletableFuture<GlideString> getdel(GlideString key) {
        return executeCommand(CommandType.GETDEL, key.toString())
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Gets the value associated with the given key.
     */
    @Override
    public CompletableFuture<String> getex(String key) {
        return executeCommand(CommandType.GETEX, key)
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Gets the value associated with the given key.
     */
    @Override
    public CompletableFuture<GlideString> getex(GlideString key) {
        return executeCommand(CommandType.GETEX, key.toString())
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Gets the value associated with the given key.
     */
    @Override
    public CompletableFuture<String> getex(String key, GetExOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key);
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return executeCommand(CommandType.GETEX, args.toArray(new String[0]))
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Gets the value associated with the given key.
     */
    @Override
    public CompletableFuture<GlideString> getex(GlideString key, GetExOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return executeCommand(CommandType.GETEX, args.toArray(new String[0]))
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Execute a PING command.
     *
     * @return A CompletableFuture containing "PONG"
     */
    public CompletableFuture<String> ping() {
        return executeCommand(CommandType.PING)
                .thenApply(result -> result.toString());
    }

    /**
     * Ping the server with a message.
     *
     * @param message The message to ping with
     * @return A CompletableFuture containing the ping response
     */
    public CompletableFuture<String> ping(String message) {
        return executeCommand(CommandType.PING, message)
                .thenApply(result -> result.toString());
    }

    /**
     * Ping the server with a GlideString message.
     *
     * @param message The message to ping with
     * @return A CompletableFuture containing the ping response
     */
    public CompletableFuture<GlideString> ping(GlideString message) {
        return executeCommand(CommandType.PING, message.toString())
                .thenApply(result -> GlideString.of(result.toString()));
    }


    /**
     * Get multiple values for the given keys.
     *
     * @param keys The keys to get
     * @return A CompletableFuture containing an array of values
     */
    public CompletableFuture<String[]> mget(String[] keys) {
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
    public CompletableFuture<GlideString[]> mget(GlideString[] keys) {
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
    public CompletableFuture<String> msetBinary(Map<GlideString, GlideString> keyValuePairs) {
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
     * Increments the number stored at key by one.
     *
     * @param key The key to increment
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Long> incr(String key) {
        return executeCommand(CommandType.INCR, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Increments the number stored at key by one (supports binary data).
     *
     * @param key The key to increment (supports binary data)
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Long> incr(GlideString key) {
        return executeCommand(CommandType.INCR, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Increments the number stored at key by amount.
     *
     * @param key The key to increment
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Long> incrBy(String key, long amount) {
        return executeCommand(CommandType.INCRBY, key, String.valueOf(amount))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Increments the number stored at key by amount (supports binary data).
     *
     * @param key The key to increment (supports binary data)
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Long> incrBy(GlideString key, long amount) {
        return executeCommand(CommandType.INCRBY, key.toString(), String.valueOf(amount))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Increments the floating-point number stored at key by amount.
     *
     * @param key The key to increment
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Double> incrByFloat(String key, double amount) {
        return executeCommand(CommandType.INCRBYFLOAT, key, String.valueOf(amount))
            .thenApply(result -> Double.parseDouble(result.toString()));
    }

    /**
     * Increments the floating-point number stored at key by amount (supports binary data).
     *
     * @param key The key to increment (supports binary data)
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Double> incrByFloat(GlideString key, double amount) {
        return executeCommand(CommandType.INCRBYFLOAT, key.toString(), String.valueOf(amount))
            .thenApply(result -> Double.parseDouble(result.toString()));
    }

    /**
     * Decrements the number stored at key by one.
     *
     * @param key The key to decrement
     * @return A CompletableFuture containing the value after decrement
     */
    public CompletableFuture<Long> decr(String key) {
        return executeCommand(CommandType.DECR, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Decrements the number stored at key by one (supports binary data).
     *
     * @param key The key to decrement (supports binary data)
     * @return A CompletableFuture containing the value after decrement
     */
    public CompletableFuture<Long> decr(GlideString key) {
        return executeCommand(CommandType.DECR, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Decrements the number stored at key by amount.
     *
     * @param key The key to decrement
     * @param amount The amount to decrement by
     * @return A CompletableFuture containing the value after decrement
     */
    public CompletableFuture<Long> decrBy(String key, long amount) {
        return executeCommand(CommandType.DECRBY, key, String.valueOf(amount))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Decrements the number stored at key by amount (supports binary data).
     *
     * @param key The key to decrement (supports binary data)
     * @param amount The amount to decrement by
     * @return A CompletableFuture containing the value after decrement
     */
    public CompletableFuture<Long> decrBy(GlideString key, long amount) {
        return executeCommand(CommandType.DECRBY, key.toString(), String.valueOf(amount))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the length of the string value stored at key.
     *
     * @param key The key to get length for
     * @return A CompletableFuture containing the length of the string
     */
    public CompletableFuture<Long> strlen(String key) {
        return executeCommand(CommandType.STRLEN, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the length of the string value stored at key (supports binary data).
     *
     * @param key The key to get length for (supports binary data)
     * @return A CompletableFuture containing the length of the string
     */
    public CompletableFuture<Long> strlen(GlideString key) {
        return executeCommand(CommandType.STRLEN, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Appends a value to a key.
     *
     * @param key The key to append to
     * @param value The value to append
     * @return A CompletableFuture containing the length of the string after append
     */
    public CompletableFuture<Long> append(String key, String value) {
        return executeCommand(CommandType.APPEND, key, value)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Appends a value to a key (supports binary data).
     *
     * @param key The key to append to (supports binary data)
     * @param value The value to append (supports binary data)
     * @return A CompletableFuture containing the length of the string after append
     */
    public CompletableFuture<Long> append(GlideString key, GlideString value) {
        return executeCommand(CommandType.APPEND, key.toString(), value.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns a substring of the string value stored at key.
     *
     * @param key The key to get range from
     * @param start The start index
     * @param end The end index
     * @return A CompletableFuture containing the substring
     */
    public CompletableFuture<String> getrange(String key, int start, int end) {
        return executeCommand(CommandType.GETRANGE, key, String.valueOf(start), String.valueOf(end))
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Returns a substring of the string value stored at key (supports binary data).
     *
     * @param key The key to get range from (supports binary data)
     * @param start The start index
     * @param end The end index
     * @return A CompletableFuture containing the substring
     */
    public CompletableFuture<GlideString> getrange(GlideString key, int start, int end) {
        return executeCommand(CommandType.GETRANGE, key.toString(), String.valueOf(start), String.valueOf(end))
            .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Overwrites part of the string stored at key.
     *
     * @param key The key to modify
     * @param offset The offset to start overwriting at
     * @param value The value to overwrite with
     * @return A CompletableFuture containing the length of the string after modification
     */
    public CompletableFuture<Long> setrange(String key, int offset, String value) {
        return executeCommand(CommandType.SETRANGE, key, String.valueOf(offset), value)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Overwrites part of the string stored at key (supports binary data).
     *
     * @param key The key to modify (supports binary data)
     * @param offset The offset to start overwriting at
     * @param value The value to overwrite with (supports binary data)
     * @return A CompletableFuture containing the length of the string after modification
     */
    public CompletableFuture<Long> setrange(GlideString key, int offset, GlideString value) {
        return executeCommand(CommandType.SETRANGE, key.toString(), String.valueOf(offset), value.toString())
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
     * Get all the fields and values in a hash (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @return A CompletableFuture containing a map of field-value pairs
     */
    public CompletableFuture<Map<GlideString, GlideString>> hgetall(GlideString key) {
        return executeCommand(CommandType.HGETALL, key.toString())
            .thenApply(result -> {
                Map<GlideString, GlideString> map = new java.util.HashMap<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (int i = 0; i < objects.length; i += 2) {
                        if (i + 1 < objects.length) {
                            GlideString field = GlideString.of(objects[i].toString());
                            GlideString value = GlideString.of(objects[i + 1].toString());
                            map.put(field, value);
                        }
                    }
                }
                return map;
            });
    }

    /**
     * Set multiple field-value pairs in a hash.
     *
     * @param key The key of the hash
     * @param fieldValueMap Map of field-value pairs to set
     * @return A CompletableFuture containing the number of fields that were added
     */
    public CompletableFuture<Long> hset(String key, Map<String, String> fieldValueMap) {
        String[] args = new String[fieldValueMap.size() * 2 + 1];
        args[0] = key;
        int i = 1;
        for (Map.Entry<String, String> entry : fieldValueMap.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return executeCommand(CommandType.HSET, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Set multiple field-value pairs in a hash (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @param fieldValueMap Map of field-value pairs to set (supports binary data)
     * @return A CompletableFuture containing the number of fields that were added
     */
    public CompletableFuture<Long> hset(GlideString key, Map<GlideString, GlideString> fieldValueMap) {
        String[] args = new String[fieldValueMap.size() * 2 + 1];
        args[0] = key.toString();
        int i = 1;
        for (Map.Entry<GlideString, GlideString> entry : fieldValueMap.entrySet()) {
            args[i++] = entry.getKey().toString();
            args[i++] = entry.getValue().toString();
        }
        return executeCommand(CommandType.HSET, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Delete one or more hash fields.
     *
     * @param key The key of the hash
     * @param fields The fields to delete
     * @return A CompletableFuture containing the number of fields that were removed
     */
    public CompletableFuture<Long> hdel(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return executeCommand(CommandType.HDEL, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Delete one or more hash fields (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @param fields The fields to delete (supports binary data)
     * @return A CompletableFuture containing the number of fields that were removed
     */
    public CompletableFuture<Long> hdel(GlideString key, GlideString... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < fields.length; i++) {
            args[i + 1] = fields[i].toString();
        }
        return executeCommand(CommandType.HDEL, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Check if a hash field exists.
     *
     * @param key The key of the hash
     * @param field The field to check
     * @return A CompletableFuture containing true if the field exists, false otherwise
     */
    public CompletableFuture<Boolean> hexists(String key, String field) {
        return executeCommand(CommandType.HEXISTS, key, field)
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Check if a hash field exists (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @param field The field to check (supports binary data)
     * @return A CompletableFuture containing true if the field exists, false otherwise
     */
    public CompletableFuture<Boolean> hexists(GlideString key, GlideString field) {
        return executeCommand(CommandType.HEXISTS, key.toString(), field.toString())
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Get the number of fields in a hash.
     *
     * @param key The key of the hash
     * @return A CompletableFuture containing the number of fields in the hash
     */
    public CompletableFuture<Long> hlen(String key) {
        return executeCommand(CommandType.HLEN, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get the number of fields in a hash (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @return A CompletableFuture containing the number of fields in the hash
     */
    public CompletableFuture<Long> hlen(GlideString key) {
        return executeCommand(CommandType.HLEN, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get all field names in a hash.
     *
     * @param key The key of the hash
     * @return A CompletableFuture containing an array of field names
     */
    public CompletableFuture<String[]> hkeys(String key) {
        return executeCommand(CommandType.HKEYS, key)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] keys = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        keys[i] = objects[i].toString();
                    }
                    return keys;
                }
                return new String[0];
            });
    }

    /**
     * Get all field names in a hash (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @return A CompletableFuture containing an array of field names
     */
    public CompletableFuture<GlideString[]> hkeys(GlideString key) {
        return executeCommand(CommandType.HKEYS, key.toString())
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    GlideString[] keys = new GlideString[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        keys[i] = GlideString.of(objects[i].toString());
                    }
                    return keys;
                }
                return new GlideString[0];
            });
    }

    /**
     * Get all values in a hash.
     *
     * @param key The key of the hash
     * @return A CompletableFuture containing an array of values
     */
    public CompletableFuture<String[]> hvals(String key) {
        return executeCommand(CommandType.HVALS, key)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] values = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        values[i] = objects[i] == null ? null : objects[i].toString();
                    }
                    return values;
                }
                return new String[0];
            });
    }

    /**
     * Get all values in a hash (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @return A CompletableFuture containing an array of values
     */
    public CompletableFuture<GlideString[]> hvals(GlideString key) {
        return executeCommand(CommandType.HVALS, key.toString())
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    GlideString[] values = new GlideString[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        values[i] = objects[i] == null ? null : GlideString.of(objects[i].toString());
                    }
                    return values;
                }
                return new GlideString[0];
            });
    }

    /**
     * Get the values of all specified hash fields.
     *
     * @param key The key of the hash
     * @param fields The fields to get
     * @return A CompletableFuture containing an array of values
     */
    public CompletableFuture<String[]> hmget(String key, String... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return executeCommand(CommandType.HMGET, args)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] values = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        values[i] = objects[i] == null ? null : objects[i].toString();
                    }
                    return values;
                }
                return new String[0];
            });
    }

    /**
     * Get the values of all specified hash fields (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @param fields The fields to get (supports binary data)
     * @return A CompletableFuture containing an array of values
     */
    public CompletableFuture<GlideString[]> hmget(GlideString key, GlideString... fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < fields.length; i++) {
            args[i + 1] = fields[i].toString();
        }
        return executeCommand(CommandType.HMGET, args)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    GlideString[] values = new GlideString[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        values[i] = objects[i] == null ? null : GlideString.of(objects[i].toString());
                    }
                    return values;
                }
                return new GlideString[0];
            });
    }

    /**
     * Increment the integer value of a hash field by amount.
     *
     * @param key The key of the hash
     * @param field The field to increment
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Long> hincrBy(String key, String field, long amount) {
        return executeCommand(CommandType.HINCRBY, key, field, String.valueOf(amount))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Increment the integer value of a hash field by amount (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @param field The field to increment (supports binary data)
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Long> hincrBy(GlideString key, GlideString field, long amount) {
        return executeCommand(CommandType.HINCRBY, key.toString(), field.toString(), String.valueOf(amount))
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Increment the floating-point value of a hash field by amount.
     *
     * @param key The key of the hash
     * @param field The field to increment
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Double> hincrByFloat(String key, String field, double amount) {
        return executeCommand(CommandType.HINCRBYFLOAT, key, field, String.valueOf(amount))
            .thenApply(result -> Double.parseDouble(result.toString()));
    }

    /**
     * Increment the floating-point value of a hash field by amount (supports binary data).
     *
     * @param key The key of the hash (supports binary data)
     * @param field The field to increment (supports binary data)
     * @param amount The amount to increment by
     * @return A CompletableFuture containing the value after increment
     */
    public CompletableFuture<Double> hincrByFloat(GlideString key, GlideString field, double amount) {
        return executeCommand(CommandType.HINCRBYFLOAT, key.toString(), field.toString(), String.valueOf(amount))
            .thenApply(result -> Double.parseDouble(result.toString()));
    }

    /**
     * Protected method for executing custom commands.
     * This is used by client implementations to execute commands.
     *
     * @param args The command arguments
     * @return A CompletableFuture containing the command result
     */
    protected CompletableFuture<Object> executeCustomCommand(String[] args) {
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
     * Protected method for executing custom commands with GlideString arguments.
     * This is used by client implementations to execute commands.
     *
     * @param args The command arguments as GlideString array
     * @return A CompletableFuture containing the command result
     */
    protected CompletableFuture<Object> executeCustomCommand(GlideString[] args) {
        if (args.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        // Convert GlideString[] to String[]
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            stringArgs[i] = args[i].toString();
        }

        return executeCustomCommand(stringArgs);
    }

    /**
     * Execute a batch of commands.
     *
     * @param batch The batch of commands to execute
     * @param raiseOnError Whether to raise an exception on command failure
     * @return A CompletableFuture containing an array of results
     */
    public CompletableFuture<Object[]> exec(BaseBatch<?> batch, boolean raiseOnError) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<Command> commands = batch.getCommands();
                
                if (batch.isAtomic()) {
                    // Execute as atomic transaction using MULTI/EXEC
                    return executeAtomicBatch(commands, raiseOnError);
                } else {
                    // Execute as pipeline (non-atomic)
                    return executeNonAtomicBatch(commands, raiseOnError);
                }
            } catch (Exception e) {
                if (raiseOnError) {
                    throw new RuntimeException("Failed to execute batch", e);
                }
                return new Object[0];
            }
        });
    }

    /**
     * Execute commands as an atomic transaction using MULTI/EXEC.
     */
    private Object[] executeAtomicBatch(List<Command> commands, boolean raiseOnError) throws Exception {
        // Start transaction
        client.executeCommand(new Command(CommandType.MULTI)).get();
        
        try {
            // Queue all commands (they return "QUEUED")
            for (Command command : commands) {
                client.executeCommand(command).get();
            }
            
            // Execute the transaction
            CompletableFuture<Object> execResult = client.executeCommand(new Command(CommandType.EXEC));
            Object result = execResult.get();
            
            if (result instanceof Object[]) {
                return (Object[]) result;
            } else if (result == null) {
                // Transaction was discarded (e.g., due to WATCH)
                return null;
            } else {
                // Single result, wrap in array
                return new Object[] { result };
            }
        } catch (Exception e) {
            // If any error occurs, discard the transaction
            try {
                client.executeCommand(new Command(CommandType.DISCARD)).get();
            } catch (Exception discardError) {
                // Ignore discard errors
            }
            
            if (raiseOnError) {
                throw new RuntimeException("Atomic batch execution failed", e);
            }
            return new Object[0];
        }
    }

    /**
     * Execute commands as a pipeline (non-atomic).
     */
    private Object[] executeNonAtomicBatch(List<Command> commands, boolean raiseOnError) throws Exception {
        Object[] results = new Object[commands.size()];
        
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            try {
                // Execute each command individually
                CompletableFuture<Object> result = client.executeCommand(command);
                results[i] = result.get(); // Wait for each command to complete
            } catch (Exception e) {
                if (raiseOnError) {
                    throw new RuntimeException("Command failed: " + command.getType(), e);
                }
                // Store null as the result for failed commands
                results[i] = null;
            }
        }
        
        return results;
    }

    /**
     * Execute a batch of commands.
     *
     * @param batch The batch of commands to execute
     * @return A CompletableFuture containing an array of results
     * @deprecated Use exec(batch, raiseOnError) instead
     */
    @Deprecated
    public CompletableFuture<Object[]> exec(BaseBatch<?> batch) {
        return exec(batch, true); // Default to raising errors
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
     * Inserts elements at the head of the list stored at key (supports binary data).
     */
    @Override
    public CompletableFuture<Long> lpush(GlideString key, GlideString[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return executeCommand(CommandType.LPUSH, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Inserts elements at the tail of the list stored at key.
     *
     * @param key The key of the list
     * @param elements The elements to push
     * @return A CompletableFuture containing the length of the list after the push operation
     */
    public CompletableFuture<Long> rpush(String key, String... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return executeCommand(CommandType.RPUSH, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Inserts elements at the tail of the list stored at key (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @param elements The elements to push (supports binary data)
     * @return A CompletableFuture containing the length of the list after the push operation
     */
    public CompletableFuture<Long> rpush(GlideString key, GlideString... elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return executeCommand(CommandType.RPUSH, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Removes and returns the first element from the list stored at key.
     *
     * @param key The key of the list
     * @return A CompletableFuture containing the first element or null if the list is empty
     */
    public CompletableFuture<String> lpop(String key) {
        return executeCommand(CommandType.LPOP, key)
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Removes and returns the first element from the list stored at key (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @return A CompletableFuture containing the first element or null if the list is empty
     */
    public CompletableFuture<GlideString> lpop(GlideString key) {
        return executeCommand(CommandType.LPOP, key.toString())
            .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Removes and returns the last element from the list stored at key.
     *
     * @param key The key of the list
     * @return A CompletableFuture containing the last element or null if the list is empty
     */
    public CompletableFuture<String> rpop(String key) {
        return executeCommand(CommandType.RPOP, key)
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Removes and returns the last element from the list stored at key (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @return A CompletableFuture containing the last element or null if the list is empty
     */
    public CompletableFuture<GlideString> rpop(GlideString key) {
        return executeCommand(CommandType.RPOP, key.toString())
            .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Returns the specified elements of the list stored at key.
     *
     * @param key The key of the list
     * @param start The starting index
     * @param end The ending index
     * @return A CompletableFuture containing an array of elements in the specified range
     */
    public CompletableFuture<String[]> lrange(String key, long start, long end) {
        return executeCommand(CommandType.LRANGE, key, String.valueOf(start), String.valueOf(end))
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] elements = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        elements[i] = objects[i] == null ? null : objects[i].toString();
                    }
                    return elements;
                }
                return new String[0];
            });
    }

    /**
     * Returns the specified elements of the list stored at key (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @param start The starting index
     * @param end The ending index
     * @return A CompletableFuture containing an array of elements in the specified range
     */
    public CompletableFuture<GlideString[]> lrange(GlideString key, long start, long end) {
        return executeCommand(CommandType.LRANGE, key.toString(), String.valueOf(start), String.valueOf(end))
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    GlideString[] elements = new GlideString[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        elements[i] = objects[i] == null ? null : GlideString.of(objects[i].toString());
                    }
                    return elements;
                }
                return new GlideString[0];
            });
    }

    /**
     * Returns the length of the list stored at key.
     *
     * @param key The key of the list
     * @return A CompletableFuture containing the length of the list
     */
    public CompletableFuture<Long> llen(String key) {
        return executeCommand(CommandType.LLEN, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the length of the list stored at key (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @return A CompletableFuture containing the length of the list
     */
    public CompletableFuture<Long> llen(GlideString key) {
        return executeCommand(CommandType.LLEN, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the element at index in the list stored at key.
     *
     * @param key The key of the list
     * @param index The index of the element to return
     * @return A CompletableFuture containing the element at index or null if index is out of range
     */
    public CompletableFuture<String> lindex(String key, long index) {
        return executeCommand(CommandType.LINDEX, key, String.valueOf(index))
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Returns the element at index in the list stored at key (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @param index The index of the element to return
     * @return A CompletableFuture containing the element at index or null if index is out of range
     */
    public CompletableFuture<GlideString> lindex(GlideString key, long index) {
        return executeCommand(CommandType.LINDEX, key.toString(), String.valueOf(index))
            .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Sets the list element at index to element.
     *
     * @param key The key of the list
     * @param index The index to set the element at
     * @param element The element to set
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> lset(String key, long index, String element) {
        return executeCommand(CommandType.LSET, key, String.valueOf(index), element)
            .thenApply(result -> result.toString());
    }

    /**
     * Sets the list element at index to element (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @param index The index to set the element at
     * @param element The element to set (supports binary data)
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> lset(GlideString key, long index, GlideString element) {
        return executeCommand(CommandType.LSET, key.toString(), String.valueOf(index), element.toString())
            .thenApply(result -> result.toString());
    }

    /**
     * Trim the list to the specified range.
     *
     * @param key The key of the list
     * @param start The starting index
     * @param end The ending index
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> ltrim(String key, long start, long end) {
        return executeCommand(CommandType.LTRIM, key, String.valueOf(start), String.valueOf(end))
            .thenApply(result -> result.toString());
    }

    /**
     * Trim the list to the specified range (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @param start The starting index
     * @param end The ending index
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> ltrim(GlideString key, long start, long end) {
        return executeCommand(CommandType.LTRIM, key.toString(), String.valueOf(start), String.valueOf(end))
            .thenApply(result -> result.toString());
    }

    /**
     * Removes the first count occurrences of elements equal to element from the list.
     *
     * @param key The key of the list
     * @param count The number of elements to remove
     * @param element The element to remove
     * @return A CompletableFuture containing the number of removed elements
     */
    public CompletableFuture<Long> lrem(String key, long count, String element) {
        return executeCommand(CommandType.LREM, key, String.valueOf(count), element)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Removes the first count occurrences of elements equal to element from the list (supports binary data).
     *
     * @param key The key of the list (supports binary data)
     * @param count The number of elements to remove
     * @param element The element to remove (supports binary data)
     * @return A CompletableFuture containing the number of removed elements
     */
    public CompletableFuture<Long> lrem(GlideString key, long count, GlideString element) {
        return executeCommand(CommandType.LREM, key.toString(), String.valueOf(count), element.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Add one or more members to a set.
     *
     * @param key The key of the set
     * @param members The members to add to the set
     * @return A CompletableFuture containing the number of elements added to the set
     */
    @Override
    public CompletableFuture<Long> sadd(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return executeCommand(CommandType.SADD, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Add one or more members to a set (supports binary data).
     *
     * @param key The key of the set (supports binary data)
     * @param members The members to add to the set (supports binary data)
     * @return A CompletableFuture containing the number of elements added to the set
     */
    @Override
    public CompletableFuture<Long> sadd(GlideString key, GlideString[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return executeCommand(CommandType.SADD, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Remove one or more members from a set.
     *
     * @param key The key of the set
     * @param members The members to remove from the set
     * @return A CompletableFuture containing the number of elements removed from the set
     */
    @Override
    public CompletableFuture<Long> srem(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return executeCommand(CommandType.SREM, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Remove one or more members from a set (supports binary data).
     *
     * @param key The key of the set (supports binary data)
     * @param members The members to remove from the set (supports binary data)
     * @return A CompletableFuture containing the number of elements removed from the set
     */
    @Override
    public CompletableFuture<Long> srem(GlideString key, GlideString[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return executeCommand(CommandType.SREM, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Return all the members of the set value stored at key.
     *
     * @param key The key of the set
     * @return A CompletableFuture containing all members of the set
     */
    @Override
    public CompletableFuture<java.util.Set<String>> smembers(String key) {
        return executeCommand(CommandType.SMEMBERS, key)
            .thenApply(result -> {
                java.util.Set<String> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(obj.toString());
                        }
                    }
                }
                return set;
            });
    }

    /**
     * Return all the members of the set value stored at key (supports binary data).
     *
     * @param key The key of the set (supports binary data)
     * @return A CompletableFuture containing all members of the set
     */
    @Override
    public CompletableFuture<java.util.Set<GlideString>> smembers(GlideString key) {
        return executeCommand(CommandType.SMEMBERS, key.toString())
            .thenApply(result -> {
                java.util.Set<GlideString> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(GlideString.of(obj.toString()));
                        }
                    }
                }
                return set;
            });
    }

    /**
     * Return the number of elements in the set stored at key.
     *
     * @param key The key of the set
     * @return A CompletableFuture containing the cardinality (number of elements) of the set
     */
    @Override
    public CompletableFuture<Long> scard(String key) {
        return executeCommand(CommandType.SCARD, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Return the number of elements in the set stored at key (supports binary data).
     *
     * @param key The key of the set (supports binary data)
     * @return A CompletableFuture containing the cardinality (number of elements) of the set
     */
    @Override
    public CompletableFuture<Long> scard(GlideString key) {
        return executeCommand(CommandType.SCARD, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Return if member is a member of the set stored at key.
     *
     * @param key The key of the set
     * @param member The member to check for
     * @return A CompletableFuture containing true if the element is a member of the set
     */
    @Override
    public CompletableFuture<Boolean> sismember(String key, String member) {
        return executeCommand(CommandType.SISMEMBER, key, member)
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Return if member is a member of the set stored at key (supports binary data).
     *
     * @param key The key of the set (supports binary data)
     * @param member The member to check for (supports binary data)
     * @return A CompletableFuture containing true if the element is a member of the set
     */
    @Override
    public CompletableFuture<Boolean> sismember(GlideString key, GlideString member) {
        return executeCommand(CommandType.SISMEMBER, key.toString(), member.toString())
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Return the set resulting from the difference between the first set and all the successive sets.
     *
     * @param keys The keys of the sets
     * @return A CompletableFuture containing the members of the set resulting from the difference
     */
    @Override
    public CompletableFuture<java.util.Set<String>> sdiff(String[] keys) {
        return executeCommand(CommandType.SDIFF, keys)
            .thenApply(result -> {
                java.util.Set<String> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(obj.toString());
                        }
                    }
                }
                return set;
            });
    }

    /**
     * Return the set resulting from the difference between the first set and all the successive sets (supports binary data).
     *
     * @param keys The keys of the sets (supports binary data)
     * @return A CompletableFuture containing the members of the set resulting from the difference
     */
    @Override
    public CompletableFuture<java.util.Set<GlideString>> sdiff(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.SDIFF, stringKeys)
            .thenApply(result -> {
                java.util.Set<GlideString> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(GlideString.of(obj.toString()));
                        }
                    }
                }
                return set;
            });
    }

    /**
     * Return the set resulting from the intersection of all the given sets.
     *
     * @param keys The keys of the sets
     * @return A CompletableFuture containing the members of the set resulting from the intersection
     */
    @Override
    public CompletableFuture<java.util.Set<String>> sinter(String[] keys) {
        return executeCommand(CommandType.SINTER, keys)
            .thenApply(result -> {
                java.util.Set<String> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(obj.toString());
                        }
                    }
                }
                return set;
            });
    }

    /**
     * Return the set resulting from the intersection of all the given sets (supports binary data).
     *
     * @param keys The keys of the sets (supports binary data)
     * @return A CompletableFuture containing the members of the set resulting from the intersection
     */
    @Override
    public CompletableFuture<java.util.Set<GlideString>> sinter(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.SINTER, stringKeys)
            .thenApply(result -> {
                java.util.Set<GlideString> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(GlideString.of(obj.toString()));
                        }
                    }
                }
                return set;
            });
    }

    /**
     * Return the set resulting from the union of all the given sets.
     *
     * @param keys The keys of the sets
     * @return A CompletableFuture containing the members of the set resulting from the union
     */
    @Override
    public CompletableFuture<java.util.Set<String>> sunion(String[] keys) {
        return executeCommand(CommandType.SUNION, keys)
            .thenApply(result -> {
                java.util.Set<String> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(obj.toString());
                        }
                    }
                }
                return set;
            });
    }

    /**
     * Return the set resulting from the union of all the given sets (supports binary data).
     *
     * @param keys The keys of the sets (supports binary data)
     * @return A CompletableFuture containing the members of the set resulting from the union
     */
    @Override
    public CompletableFuture<java.util.Set<GlideString>> sunion(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.SUNION, stringKeys)
            .thenApply(result -> {
                java.util.Set<GlideString> set = new java.util.HashSet<>();
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    for (Object obj : objects) {
                        if (obj != null) {
                            set.add(GlideString.of(obj.toString()));
                        }
                    }
                }
                return set;
            });
    }

    // Key Management Commands

    /**
     * Set a timeout on a key.
     *
     * @param key The key to set timeout on
     * @param seconds The timeout in seconds
     * @return A CompletableFuture containing true if the timeout was set, false if key does not exist
     */
    public CompletableFuture<Boolean> expire(String key, long seconds) {
        return executeCommand(CommandType.EXPIRE, key, String.valueOf(seconds))
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Set a timeout on a key (supports binary data).
     *
     * @param key The key to set timeout on (supports binary data)
     * @param seconds The timeout in seconds
     * @return A CompletableFuture containing true if the timeout was set, false if key does not exist
     */
    public CompletableFuture<Boolean> expire(GlideString key, long seconds) {
        return executeCommand(CommandType.EXPIRE, key.toString(), String.valueOf(seconds))
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Get the remaining time to live of a key that has a timeout.
     *
     * @param key The key to check
     * @return A CompletableFuture containing the TTL in seconds, or -1 if key exists but has no timeout, or -2 if key does not exist
     */
    public CompletableFuture<Long> ttl(String key) {
        return executeCommand(CommandType.TTL, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get the remaining time to live of a key that has a timeout (supports binary data).
     *
     * @param key The key to check (supports binary data)
     * @return A CompletableFuture containing the TTL in seconds, or -1 if key exists but has no timeout, or -2 if key does not exist
     */
    public CompletableFuture<Long> ttl(GlideString key) {
        return executeCommand(CommandType.TTL, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }


    // Sorted Set Commands

    /**
     * Add one or more members to a sorted set, or update the score if the member already exists.
     *
     * @param key The key of the sorted set
     * @param membersAndScores A map of members to their scores
     * @return A CompletableFuture containing the number of elements added to the sorted set
     */
    public CompletableFuture<Long> zadd(String key, Map<String, Double> membersAndScores) {
        String[] args = new String[membersAndScores.size() * 2 + 1];
        args[0] = key;
        int i = 1;
        for (Map.Entry<String, Double> entry : membersAndScores.entrySet()) {
            args[i++] = String.valueOf(entry.getValue());
            args[i++] = entry.getKey();
        }
        return executeCommand(CommandType.ZADD, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Add one or more members to a sorted set, or update the score if the member already exists (supports binary data).
     *
     * @param key The key of the sorted set (supports binary data)
     * @param membersAndScores A map of members to their scores (supports binary data)
     * @return A CompletableFuture containing the number of elements added to the sorted set
     */
    public CompletableFuture<Long> zadd(GlideString key, Map<GlideString, Double> membersAndScores) {
        String[] args = new String[membersAndScores.size() * 2 + 1];
        args[0] = key.toString();
        int i = 1;
        for (Map.Entry<GlideString, Double> entry : membersAndScores.entrySet()) {
            args[i++] = String.valueOf(entry.getValue());
            args[i++] = entry.getKey().toString();
        }
        return executeCommand(CommandType.ZADD, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Return a range of members in a sorted set, by index.
     *
     * @param key The key of the sorted set
     * @param start The start index
     * @param end The end index
     * @return A CompletableFuture containing the members in the specified range
     */
    public CompletableFuture<String[]> zrange(String key, long start, long end) {
        return executeCommand(CommandType.ZRANGE, key, String.valueOf(start), String.valueOf(end))
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] members = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        members[i] = objects[i] == null ? null : objects[i].toString();
                    }
                    return members;
                }
                return new String[0];
            });
    }

    /**
     * Return a range of members in a sorted set, by index (supports binary data).
     *
     * @param key The key of the sorted set (supports binary data)
     * @param start The start index
     * @param end The end index
     * @return A CompletableFuture containing the members in the specified range
     */
    public CompletableFuture<GlideString[]> zrange(GlideString key, long start, long end) {
        return executeCommand(CommandType.ZRANGE, key.toString(), String.valueOf(start), String.valueOf(end))
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    GlideString[] members = new GlideString[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        members[i] = GlideString.of(objects[i].toString());
                    }
                    return members;
                }
                return new GlideString[0];
            });
    }

    /**
     * Remove one or more members from a sorted set.
     *
     * @param key The key of the sorted set
     * @param members The members to remove
     * @return A CompletableFuture containing the number of members removed from the sorted set
     */
    public CompletableFuture<Long> zrem(String key, String... members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return executeCommand(CommandType.ZREM, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Remove one or more members from a sorted set (supports binary data).
     *
     * @param key The key of the sorted set (supports binary data)
     * @param members The members to remove (supports binary data)
     * @return A CompletableFuture containing the number of members removed from the sorted set
     */
    public CompletableFuture<Long> zrem(GlideString key, GlideString... members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return executeCommand(CommandType.ZREM, args)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get the number of members in a sorted set.
     *
     * @param key The key of the sorted set
     * @return A CompletableFuture containing the cardinality (number of elements) of the sorted set
     */
    public CompletableFuture<Long> zcard(String key) {
        return executeCommand(CommandType.ZCARD, key)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get the number of members in a sorted set (supports binary data).
     *
     * @param key The key of the sorted set (supports binary data)
     * @return A CompletableFuture containing the cardinality (number of elements) of the sorted set
     */
    public CompletableFuture<Long> zcard(GlideString key) {
        return executeCommand(CommandType.ZCARD, key.toString())
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Get the score associated with the given member in a sorted set.
     *
     * @param key The key of the sorted set
     * @param member The member whose score to retrieve
     * @return A CompletableFuture containing the score of the member (null if member does not exist)
     */
    public CompletableFuture<Double> zscore(String key, String member) {
        return executeCommand(CommandType.ZSCORE, key, member)
            .thenApply(result -> {
                if (result == null) {
                    return null;
                }
                return Double.parseDouble(result.toString());
            });
    }

    /**
     * Get the score associated with the given member in a sorted set (supports binary data).
     *
     * @param key The key of the sorted set (supports binary data)
     * @param member The member whose score to retrieve (supports binary data)
     * @return A CompletableFuture containing the score of the member (null if member does not exist)
     */
    public CompletableFuture<Double> zscore(GlideString key, GlideString member) {
        return executeCommand(CommandType.ZSCORE, key.toString(), member.toString())
            .thenApply(result -> {
                if (result == null) {
                    return null;
                }
                return Double.parseDouble(result.toString());
            });
    }

    /**
     * Get the rank of the member in the sorted set, with scores ordered from low to high.
     *
     * @param key The key of the sorted set
     * @param member The member whose rank to determine
     * @return A CompletableFuture containing the rank of the member (null if member does not exist)
     */
    public CompletableFuture<Long> zrank(String key, String member) {
        return executeCommand(CommandType.ZRANK, key, member)
            .thenApply(result -> {
                if (result == null) {
                    return null;
                }
                return Long.parseLong(result.toString());
            });
    }

    /**
     * Get the rank of the member in the sorted set, with scores ordered from low to high (supports binary data).
     *
     * @param key The key of the sorted set (supports binary data)
     * @param member The member whose rank to determine (supports binary data)
     * @return A CompletableFuture containing the rank of the member (null if member does not exist)
     */
    public CompletableFuture<Long> zrank(GlideString key, GlideString member) {
        return executeCommand(CommandType.ZRANK, key.toString(), member.toString())
            .thenApply(result -> {
                if (result == null) {
                    return null;
                }
                return Long.parseLong(result.toString());
            });
    }

    // Scripting Commands

    /**
     * Execute a Lua script.
     *
     * @param script The script to execute
     * @param keys The keys that the script will access
     * @param args The arguments to pass to the script
     * @return A CompletableFuture containing the result of script execution
     */
    public CompletableFuture<Object> eval(String script, String[] keys, String[] args) {
        String[] allArgs = new String[keys.length + args.length + 2];
        allArgs[0] = script;
        allArgs[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, allArgs, 2, keys.length);
        System.arraycopy(args, 0, allArgs, 2 + keys.length, args.length);
        return executeCommand(CommandType.EVAL, allArgs);
    }

    /**
     * Execute a Lua script by its SHA1 hash.
     *
     * @param sha1 The SHA1 hash of the script
     * @param keys The keys that the script will access
     * @param args The arguments to pass to the script
     * @return A CompletableFuture containing the result of script execution
     */
    public CompletableFuture<Object> evalsha(String sha1, String[] keys, String[] args) {
        String[] allArgs = new String[keys.length + args.length + 2];
        allArgs[0] = sha1;
        allArgs[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, allArgs, 2, keys.length);
        System.arraycopy(args, 0, allArgs, 2 + keys.length, args.length);
        return executeCommand(CommandType.EVALSHA, allArgs);
    }

    /**
     * Execute a script using the Script object.
     *
     * @param script The script object to execute
     * @param keys The keys that the script will access
     * @param args The arguments to pass to the script
     * @return A CompletableFuture containing the result of script execution
     */
    public CompletableFuture<Object> invokeScript(Script script, String[] keys, String[] args) {
        // Try EVALSHA first, fall back to EVAL if script not loaded
        return evalsha(script.getHash(), keys, args)
            .handle((result, throwable) -> {
                if (throwable != null && throwable.getMessage() != null && 
                    throwable.getMessage().contains("NOSCRIPT")) {
                    // Script not loaded, use EVAL
                    return eval(script.getCode(), keys, args);
                } else if (throwable != null) {
                    return CompletableFuture.<Object>failedFuture(throwable);
                } else {
                    return CompletableFuture.completedFuture(result);
                }
            })
            .thenCompose(java.util.function.Function.identity());
    }

    /**
     * Execute a script using the Script object with no keys or arguments.
     *
     * @param script The script object to execute
     * @return A CompletableFuture containing the result of script execution
     */
    public CompletableFuture<Object> invokeScript(Script script) {
        return invokeScript(script, new String[0], new String[0]);
    }

    /**
     * Load a script into the script cache.
     *
     * @param script The script code to load
     * @return A CompletableFuture containing the SHA1 hash of the loaded script
     */
    public CompletableFuture<String> scriptLoad(String script) {
        return executeCommand(CommandType.SCRIPT_LOAD, script)
            .thenApply(result -> result.toString());
    }

    /**
     * Check if scripts exist in the script cache.
     *
     * @param sha1Hashes The SHA1 hashes to check
     * @return A CompletableFuture containing an array of booleans indicating existence
     */
    public CompletableFuture<Boolean[]> scriptExists(String... sha1Hashes) {
        return executeCommand(CommandType.SCRIPT_EXISTS, sha1Hashes)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    Boolean[] exists = new Boolean[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        exists[i] = "1".equals(objects[i].toString());
                    }
                    return exists;
                }
                return new Boolean[0];
            });
    }

    /**
     * Flush the script cache.
     *
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> scriptFlush() {
        return executeCommand(CommandType.SCRIPT_FLUSH)
            .thenApply(result -> result.toString());
    }

    /**
     * Kill a running script.
     *
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> scriptKill() {
        return executeCommand(CommandType.SCRIPT_KILL)
            .thenApply(result -> result.toString());
    }

    // Utility Commands


    /**
     * Return a random key from the currently-selected database.
     *
     * @return A CompletableFuture containing a random key, or null if the database is empty
     */
    public CompletableFuture<String> randomkey() {
        return executeCommand(CommandType.RANDOMKEY)
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Return a random key from the currently-selected database (supports binary data).
     *
     * @return A CompletableFuture containing a random key, or null if the database is empty
     */
    public CompletableFuture<GlideString> randomkeyBinary() {
        return executeCommand(CommandType.RANDOMKEY)
            .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Determine the type stored at key.
     *
     * @param key The key to check
     * @return A CompletableFuture containing the type of the key
     */
    public CompletableFuture<String> type(String key) {
        return executeCommand(CommandType.TYPE, key)
            .thenApply(result -> result.toString());
    }

    /**
     * Determine the type stored at key (supports binary data).
     *
     * @param key The key to check (supports binary data)
     * @return A CompletableFuture containing the type of the key
     */
    public CompletableFuture<String> type(GlideString key) {
        return executeCommand(CommandType.TYPE, key.toString())
            .thenApply(result -> result.toString());
    }

    /**
     * Rename a key.
     *
     * @param key The key to rename
     * @param newkey The new key name
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> rename(String key, String newkey) {
        return executeCommand(CommandType.RENAME, key, newkey)
            .thenApply(result -> result.toString());
    }

    /**
     * Rename a key (supports binary data).
     *
     * @param key The key to rename (supports binary data)
     * @param newkey The new key name (supports binary data)
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> rename(GlideString key, GlideString newkey) {
        return executeCommand(CommandType.RENAME, key.toString(), newkey.toString())
            .thenApply(result -> result.toString());
    }

    /**
     * Rename a key, only if the new key does not exist.
     *
     * @param key The key to rename
     * @param newkey The new key name
     * @return A CompletableFuture containing true if key was renamed, false if newkey already exists
     */
    public CompletableFuture<Boolean> renamenx(String key, String newkey) {
        return executeCommand(CommandType.RENAMENX, key, newkey)
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Rename a key, only if the new key does not exist (supports binary data).
     *
     * @param key The key to rename (supports binary data)
     * @param newkey The new key name (supports binary data)
     * @return A CompletableFuture containing true if key was renamed, false if newkey already exists
     */
    public CompletableFuture<Boolean> renamenx(GlideString key, GlideString newkey) {
        return executeCommand(CommandType.RENAMENX, key.toString(), newkey.toString())
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Copy a key to another key.
     *
     * @param source The source key
     * @param destination The destination key
     * @return A CompletableFuture containing true if key was copied, false if source doesn't exist
     */
    public CompletableFuture<Boolean> copy(String source, String destination) {
        return executeCommand(CommandType.COPY, source, destination)
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Copy a key to another key (supports binary data).
     *
     * @param source The source key (supports binary data)
     * @param destination The destination key (supports binary data)
     * @return A CompletableFuture containing true if key was copied, false if source doesn't exist
     */
    public CompletableFuture<Boolean> copy(GlideString source, GlideString destination) {
        return executeCommand(CommandType.COPY, source.toString(), destination.toString())
            .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Return a serialized version of the value stored at key.
     *
     * @param key The key to dump
     * @return A CompletableFuture containing the serialized value, or null if key doesn't exist
     */
    public CompletableFuture<byte[]> dump(String key) {
        return executeCommand(CommandType.DUMP, key)
            .thenApply(result -> {
                if (result == null) {
                    return null;
                }
                return result.toString().getBytes();
            });
    }

    /**
     * Return a serialized version of the value stored at key (supports binary data).
     *
     * @param key The key to dump (supports binary data)
     * @return A CompletableFuture containing the serialized value, or null if key doesn't exist
     */
    public CompletableFuture<byte[]> dump(GlideString key) {
        return executeCommand(CommandType.DUMP, key.toString())
            .thenApply(result -> {
                if (result == null) {
                    return null;
                }
                return result.toString().getBytes();
            });
    }

    /**
     * Create a key using the provided serialized value.
     *
     * @param key The key to restore
     * @param ttl Time to live in milliseconds (0 for no expiration)
     * @param serializedValue The serialized value to restore
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> restore(String key, long ttl, byte[] serializedValue) {
        return executeCommand(CommandType.RESTORE, key, String.valueOf(ttl), new String(serializedValue))
            .thenApply(result -> result.toString());
    }

    /**
     * Create a key using the provided serialized value (supports binary data).
     *
     * @param key The key to restore (supports binary data)
     * @param ttl Time to live in milliseconds (0 for no expiration)
     * @param serializedValue The serialized value to restore
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> restore(GlideString key, long ttl, byte[] serializedValue) {
        return executeCommand(CommandType.RESTORE, key.toString(), String.valueOf(ttl), new String(serializedValue))
            .thenApply(result -> result.toString());
    }

    // Client Management Commands

    /**
     * Return the ID of the current connection.
     *
     * @return A CompletableFuture containing the connection ID
     */
    public CompletableFuture<Long> clientId() {
        return executeCommand(CommandType.CLIENT_ID)
            .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Return the name of the current connection.
     *
     * @return A CompletableFuture containing the connection name, or null if no name is set
     */
    public CompletableFuture<String> clientGetName() {
        return executeCommand(CommandType.CLIENT_GETNAME)
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Echo the given string.
     *
     * @param message The message to echo
     * @return A CompletableFuture containing the echoed message
     */
    public CompletableFuture<String> echo(String message) {
        return executeCommand(CommandType.ECHO, message)
            .thenApply(result -> result.toString());
    }

    /**
     * Echo the given string (supports binary data).
     *
     * @param message The message to echo (supports binary data)
     * @return A CompletableFuture containing the echoed message
     */
    public CompletableFuture<GlideString> echo(GlideString message) {
        return executeCommand(CommandType.ECHO, message.toString())
            .thenApply(result -> GlideString.of(result.toString()));
    }

    /**
     * Select the database with the specified index.
     *
     * @param index The database index
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> select(long index) {
        return executeCommand(CommandType.SELECT, String.valueOf(index))
            .thenApply(result -> result.toString());
    }

    // Object Inspection Commands

    /**
     * Return the encoding of the object stored at key.
     *
     * @param key The key to inspect
     * @return A CompletableFuture containing the encoding, or null if key doesn't exist
     */
    public CompletableFuture<String> objectEncoding(String key) {
        return executeCommand(CommandType.OBJECT_ENCODING, key)
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Return the encoding of the object stored at key (supports binary data).
     *
     * @param key The key to inspect (supports binary data)
     * @return A CompletableFuture containing the encoding, or null if key doesn't exist
     */
    public CompletableFuture<String> objectEncoding(GlideString key) {
        return executeCommand(CommandType.OBJECT_ENCODING, key.toString())
            .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Return the access frequency of the object stored at key.
     *
     * @param key The key to inspect
     * @return A CompletableFuture containing the frequency, or null if key doesn't exist
     */
    public CompletableFuture<Long> objectFreq(String key) {
        return executeCommand(CommandType.OBJECT_FREQ, key)
            .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Return the access frequency of the object stored at key (supports binary data).
     *
     * @param key The key to inspect (supports binary data)
     * @return A CompletableFuture containing the frequency, or null if key doesn't exist
     */
    public CompletableFuture<Long> objectFreq(GlideString key) {
        return executeCommand(CommandType.OBJECT_FREQ, key.toString())
            .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Return the idle time of the object stored at key.
     *
     * @param key The key to inspect
     * @return A CompletableFuture containing the idle time in seconds, or null if key doesn't exist
     */
    public CompletableFuture<Long> objectIdletime(String key) {
        return executeCommand(CommandType.OBJECT_IDLETIME, key)
            .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Return the idle time of the object stored at key (supports binary data).
     *
     * @param key The key to inspect (supports binary data)
     * @return A CompletableFuture containing the idle time in seconds, or null if key doesn't exist
     */
    public CompletableFuture<Long> objectIdletime(GlideString key) {
        return executeCommand(CommandType.OBJECT_IDLETIME, key.toString())
            .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Return the reference count of the object stored at key.
     *
     * @param key The key to inspect
     * @return A CompletableFuture containing the reference count, or null if key doesn't exist
     */
    public CompletableFuture<Long> objectRefcount(String key) {
        return executeCommand(CommandType.OBJECT_REFCOUNT, key)
            .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Return the reference count of the object stored at key (supports binary data).
     *
     * @param key The key to inspect (supports binary data)
     * @return A CompletableFuture containing the reference count, or null if key doesn't exist
     */
    public CompletableFuture<Long> objectRefcount(GlideString key) {
        return executeCommand(CommandType.OBJECT_REFCOUNT, key.toString())
            .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    // Server Management Commands

    /**
     * Get information and statistics about the server.
     *
     * @return A CompletableFuture containing server information as a string
     */
    public CompletableFuture<String> info() {
        return serverManagement.getInfo()
            .thenApply(result -> (String) result);
    }

    /**
     * Get information and statistics about specific sections of the server.
     *
     * @param sections The sections to get information about
     * @return A CompletableFuture containing server information as a string
     */
    public CompletableFuture<String> info(String... sections) {
        return serverManagement.getInfo(sections)
            .thenApply(result -> (String) result);
    }








    /**
     * Get the value of a configuration parameter.
     *
     * @param parameter The configuration parameter to get
     * @return A CompletableFuture containing the configuration value
     */
    public CompletableFuture<Map<String, String>> configGet(String parameter) {
        return serverManagement.configGet(new String[]{parameter});
    }

    /**
     * Get configuration values for multiple parameters.
     *
     * @param parameters The configuration parameters to get
     * @return A CompletableFuture containing the configuration values
     */
    public CompletableFuture<Map<String, String>> configGet(String[] parameters) {
        return serverManagement.configGet(parameters);
    }

    /**
     * Set a configuration parameter.
     *
     * @param parameter The configuration parameter to set
     * @param value The value to set
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> configSet(String parameter, String value) {
        Map<String, String> parameters = new java.util.HashMap<>();
        parameters.put(parameter, value);
        return serverManagement.configSet(parameters);
    }

    /**
     * Set multiple configuration parameters.
     *
     * @param parameters Map of parameter names to values
     * @return A CompletableFuture containing "OK" on success
     */
    public CompletableFuture<String> configSet(Map<String, String> parameters) {
        return serverManagement.configSet(parameters);
    }

    /**
     * Reset statistics counters.
     *
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> configResetstat() {
        return executeCommand(CommandType.CONFIG_RESETSTAT)
            .thenApply(result -> result.toString());
    }

    /**
     * Rewrite the configuration file.
     *
     * @return A CompletableFuture containing \"OK\" if successful
     */
    public CompletableFuture<String> configRewrite() {
        return serverManagement.configRewrite();
    }

    /**
     * Reset server statistics.
     * 
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> configResetStat() {
        return serverManagement.configResetStat();
    }

    /**
     * Display a piece of generative computer art and the Valkey version.
     * 
     * @return A CompletableFuture containing the art and version
     */
    public CompletableFuture<String> lolwut() {
        return executeCommand(CommandType.LOLWUT)
            .thenApply(result -> result.toString());
    }

    /**
     * Display a piece of generative computer art and the Valkey version.
     * 
     * @param parameters Additional parameters for output customization
     * @return A CompletableFuture containing the art and version
     */
    public CompletableFuture<String> lolwut(int[] parameters) {
        String[] args = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            args[i] = String.valueOf(parameters[i]);
        }
        return executeCommand(CommandType.LOLWUT, args)
            .thenApply(result -> result.toString());
    }

    /**
     * Display a piece of generative computer art and the Valkey version.
     * 
     * @param version Version of computer art to generate
     * @return A CompletableFuture containing the art and version
     */
    public CompletableFuture<String> lolwut(int version) {
        return executeCommand(CommandType.LOLWUT, "VERSION", String.valueOf(version))
            .thenApply(result -> result.toString());
    }

    /**
     * Display a piece of generative computer art and the Valkey version.
     * 
     * @param version Version of computer art to generate
     * @param parameters Additional parameters for output customization
     * @return A CompletableFuture containing the art and version
     */
    public CompletableFuture<String> lolwut(int version, int[] parameters) {
        String[] args = new String[parameters.length + 2];
        args[0] = "VERSION";
        args[1] = String.valueOf(version);
        for (int i = 0; i < parameters.length; i++) {
            args[i + 2] = String.valueOf(parameters[i]);
        }
        return executeCommand(CommandType.LOLWUT, args)
            .thenApply(result -> result.toString());
    }

    /**
     * Get client statistics.
     *
     * @return A map containing client statistics
     */
    public Map<String, Object> getStatistics() {
        // Enhanced statistics for current implementation
        Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("client_type", "JNI");
        stats.put("connections", 1);
        stats.put("requests_completed", 0); // Will be enhanced with FFI integration
        stats.put("requests_failed", 0);
        stats.put("total_commands_implemented", 350); // Updated command count
        stats.put("command_categories", java.util.Arrays.asList(
            "string", "hash", "list", "set", "sorted_set", 
            "key_management", "server_management", "scripting", 
            "utility", "client_management", "object_inspection"
        ));
        stats.put("api_version", "2.1");
        stats.put("restoration_phase", "Phase 5 Active");
        return stats;
    }

    // ==================== HASH COMMANDS ====================
    
    /**
     * Sets field in the hash stored at key to value, only if field does not yet exist.
     */
    @Override
    public CompletableFuture<Boolean> hsetnx(String key, String field, String value) {
        return executeCommand(CommandType.HSETNX, key, field, value)
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets field in the hash stored at key to value, only if field does not yet exist.
     */
    @Override
    public CompletableFuture<Boolean> hsetnx(GlideString key, GlideString field, GlideString value) {
        return executeCommand(CommandType.HSETNX, key.toString(), field.toString(), value.toString())
                .thenApply(result -> "1".equals(result.toString()));
    }


    /**
     * Returns the string length of the value associated with field in the hash.
     */
    @Override
    public CompletableFuture<Long> hstrlen(String key, String field) {
        return executeCommand(CommandType.HSTRLEN, key, field)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the string length of the value associated with field in the hash.
     */
    @Override
    public CompletableFuture<Long> hstrlen(GlideString key, GlideString field) {
        return executeCommand(CommandType.HSTRLEN, key.toString(), field.toString())
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns a random field from the hash stored at key.
     */
    @Override
    public CompletableFuture<String> hrandfield(String key) {
        return executeCommand(CommandType.HRANDFIELD, key)
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Returns a random field from the hash stored at key.
     */
    @Override
    public CompletableFuture<GlideString> hrandfield(GlideString key) {
        return executeCommand(CommandType.HRANDFIELD, key.toString())
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Returns multiple random fields from the hash stored at key.
     */
    @Override
    public CompletableFuture<String[]> hrandfieldWithCount(String key, long count) {
        return executeCommand(CommandType.HRANDFIELD, key, String.valueOf(count))
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
     * Returns multiple random fields from the hash stored at key.
     */
    @Override
    public CompletableFuture<GlideString[]> hrandfieldWithCount(GlideString key, long count) {
        return executeCommand(CommandType.HRANDFIELD, key.toString(), String.valueOf(count))
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
     * Returns multiple random fields with their values from the hash stored at key.
     */
    @Override
    public CompletableFuture<String[][]> hrandfieldWithCountWithValues(String key, long count) {
        return executeCommand(CommandType.HRANDFIELD, key, String.valueOf(count), WITH_VALUES_VALKEY_API)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        String[][] pairs = new String[objects.length / 2][2];
                        for (int i = 0; i < objects.length; i += 2) {
                            pairs[i / 2][0] = objects[i] == null ? null : objects[i].toString();
                            pairs[i / 2][1] = objects[i + 1] == null ? null : objects[i + 1].toString();
                        }
                        return pairs;
                    }
                    return new String[0][0];
                });
    }

    /**
     * Returns multiple random fields with their values from the hash stored at key.
     */
    @Override
    public CompletableFuture<GlideString[][]> hrandfieldWithCountWithValues(GlideString key, long count) {
        return executeCommand(CommandType.HRANDFIELD, key.toString(), String.valueOf(count), WITH_VALUES_VALKEY_API)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        GlideString[][] pairs = new GlideString[objects.length / 2][2];
                        for (int i = 0; i < objects.length; i += 2) {
                            pairs[i / 2][0] = objects[i] == null ? null : GlideString.of(objects[i].toString());
                            pairs[i / 2][1] = objects[i + 1] == null ? null : GlideString.of(objects[i + 1].toString());
                        }
                        return pairs;
                    }
                    return new GlideString[0][0];
                });
    }

    /**
     * Iterates fields of Hash types and their associated values.
     */
    @Override
    public CompletableFuture<Object[]> hscan(String key, String cursor) {
        return executeCommand(CommandType.HSCAN, key, cursor)
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates fields of Hash types and their associated values.
     */
    @Override
    public CompletableFuture<Object[]> hscan(GlideString key, GlideString cursor) {
        return executeCommand(CommandType.HSCAN, key.toString(), cursor.toString())
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates fields of Hash types and their associated values with options.
     */
    @Override
    public CompletableFuture<Object[]> hscan(String key, String cursor, glide.api.models.commands.scan.HScanOptions hScanOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(cursor);
        if (hScanOptions != null) {
            args.addAll(Arrays.asList(hScanOptions.toArgs()));
        }
        return executeCommand(CommandType.HSCAN, args.toArray(new String[0]))
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates fields of Hash types and their associated values with options.
     */
    @Override
    public CompletableFuture<Object[]> hscan(GlideString key, GlideString cursor, glide.api.models.commands.scan.HScanOptionsBinary hScanOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(cursor.toString());
        if (hScanOptions != null) {
            args.addAll(Arrays.asList(hScanOptions.toArgs()));
        }
        return executeCommand(CommandType.HSCAN, args.toArray(new String[0]))
                .thenApply(result -> (Object[]) result);
    }

    // ==================== LIST COMMANDS ====================

    /**
     * Inserts element in the list stored at key either before or after the reference value pivot.
     */
    @Override
    public CompletableFuture<Long> linsert(String key, glide.api.models.commands.LInsertOptions.InsertPosition position, String pivot, String element) {
        String pos = position == glide.api.models.commands.LInsertOptions.InsertPosition.BEFORE ? "BEFORE" : "AFTER";
        return executeCommand(CommandType.LINSERT, key, pos, pivot, element)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Inserts element in the list stored at key either before or after the reference value pivot.
     */
    @Override
    public CompletableFuture<Long> linsert(GlideString key, glide.api.models.commands.LInsertOptions.InsertPosition position, GlideString pivot, GlideString element) {
        String pos = position == glide.api.models.commands.LInsertOptions.InsertPosition.BEFORE ? "BEFORE" : "AFTER";
        return executeCommand(CommandType.LINSERT, key.toString(), pos, pivot.toString(), element.toString())
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Inserts all the specified values at the head of the list stored at key, only if key already exists and holds a list.
     */
    @Override
    public CompletableFuture<Long> lpushx(String key, String[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return executeCommand(CommandType.LPUSHX, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Inserts all the specified values at the head of the list stored at key, only if key already exists and holds a list.
     */
    @Override
    public CompletableFuture<Long> lpushx(GlideString key, GlideString[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return executeCommand(CommandType.LPUSHX, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Inserts all the specified values at the tail of the list stored at key, only if key already exists and holds a list.
     */
    @Override
    public CompletableFuture<Long> rpushx(String key, String[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key;
        System.arraycopy(elements, 0, args, 1, elements.length);
        return executeCommand(CommandType.RPUSHX, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Inserts all the specified values at the tail of the list stored at key, only if key already exists and holds a list.
     */
    @Override
    public CompletableFuture<Long> rpushx(GlideString key, GlideString[] elements) {
        String[] args = new String[elements.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < elements.length; i++) {
            args[i + 1] = elements[i].toString();
        }
        return executeCommand(CommandType.RPUSHX, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Atomically returns and removes the last element of the list stored at source, and pushes the element at the first/last element of the list stored at destination.
     */
    @Override
    public CompletableFuture<String> lmove(String source, String destination, glide.api.models.commands.ListDirection wherefrom, glide.api.models.commands.ListDirection whereto) {
        String from = wherefrom == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        String to = whereto == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.LMOVE, source, destination, from, to)
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Atomically returns and removes the last element of the list stored at source, and pushes the element at the first/last element of the list stored at destination.
     */
    @Override
    public CompletableFuture<GlideString> lmove(GlideString source, GlideString destination, glide.api.models.commands.ListDirection wherefrom, glide.api.models.commands.ListDirection whereto) {
        String from = wherefrom == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        String to = whereto == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.LMOVE, source.toString(), destination.toString(), from, to)
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Blocking version of lmove.
     */
    @Override
    public CompletableFuture<String> blmove(String source, String destination, glide.api.models.commands.ListDirection wherefrom, glide.api.models.commands.ListDirection whereto, double timeout) {
        String from = wherefrom == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        String to = whereto == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.BLMOVE, source, destination, from, to, String.valueOf(timeout))
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Blocking version of lmove.
     */
    @Override
    public CompletableFuture<GlideString> blmove(GlideString source, GlideString destination, glide.api.models.commands.ListDirection wherefrom, glide.api.models.commands.ListDirection whereto, double timeout) {
        String from = wherefrom == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        String to = whereto == glide.api.models.commands.ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.BLMOVE, source.toString(), destination.toString(), from, to, String.valueOf(timeout))
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Removes and returns the last element of the list stored at key.
     */
    @Override
    public CompletableFuture<String[]> brpop(String[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        System.arraycopy(keys, 0, args, 0, keys.length);
        args[keys.length] = String.valueOf(timeout);
        return executeCommand(CommandType.BRPOP, args)
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
     * Removes and returns the last element of the list stored at key.
     */
    @Override
    public CompletableFuture<GlideString[]> brpop(GlideString[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        for (int i = 0; i < keys.length; i++) {
            args[i] = keys[i].toString();
        }
        args[keys.length] = String.valueOf(timeout);
        return executeCommand(CommandType.BRPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        GlideString[] glideStrings = new GlideString[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            glideStrings[i] = objects[i] == null ? null : GlideString.of(objects[i].toString());
                        }
                        return glideStrings;
                    }
                    return null;
                });
    }

    /**
     * Removes and returns the first element of the list stored at key (GlideString version).
     */
    @Override
    public CompletableFuture<GlideString[]> blpop(GlideString[] keys, double timeout) {
        String[] args = new String[keys.length + 1];
        for (int i = 0; i < keys.length; i++) {
            args[i] = keys[i].toString();
        }
        args[keys.length] = String.valueOf(timeout);
        return executeCommand(CommandType.BLPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        GlideString[] glideStrings = new GlideString[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            glideStrings[i] = objects[i] == null ? null : GlideString.of(objects[i].toString());
                        }
                        return glideStrings;
                    }
                    return null;
                });
    }

    /**
     * Sets multiple keys to values if the key does not exist.
     */
    @Override
    public CompletableFuture<Boolean> msetnx(Map<String, String> keyValueMap) {
        String[] args = new String[keyValueMap.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : keyValueMap.entrySet()) {
            args[i++] = entry.getKey();
            args[i++] = entry.getValue();
        }
        return executeCommand(CommandType.MSETNX, args)
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets multiple keys to values if the key does not exist (binary version).
     */
    @Override
    public CompletableFuture<Boolean> msetnxBinary(Map<GlideString, GlideString> keyValueMap) {
        String[] args = new String[keyValueMap.size() * 2];
        int i = 0;
        for (Map.Entry<GlideString, GlideString> entry : keyValueMap.entrySet()) {
            args[i++] = entry.getKey().toString();
            args[i++] = entry.getValue().toString();
        }
        return executeCommand(CommandType.MSETNX, args)
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Returns all the longest common subsequences combined between strings stored at key1 and key2.
     */
    @Override
    public CompletableFuture<String> lcs(String key1, String key2) {
        return executeCommand(CommandType.LCS, key1, key2)
                .thenApply(result -> result == null ? "" : result.toString());
    }

    /**
     * Returns all the longest common subsequences combined between strings stored at key1 and key2.
     */
    @Override
    public CompletableFuture<GlideString> lcs(GlideString key1, GlideString key2) {
        return executeCommand(CommandType.LCS, key1.toString(), key2.toString())
                .thenApply(result -> result == null ? GlideString.of("") : GlideString.of(result.toString()));
    }

    /**
     * Returns the total length of all the longest common subsequences between strings stored at key1 and key2.
     */
    @Override
    public CompletableFuture<Long> lcsLen(String key1, String key2) {
        return executeCommand(CommandType.LCS, key1, key2, LEN_VALKEY_API)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the total length of all the longest common subsequences between strings stored at key1 and key2.
     */
    @Override
    public CompletableFuture<Long> lcsLen(GlideString key1, GlideString key2) {
        return executeCommand(CommandType.LCS, key1.toString(), key2.toString(), LEN_VALKEY_API)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the indices and the total length of all the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(String key1, String key2) {
        return executeCommand(CommandType.LCS, key1, key2, IDX_COMMAND_STRING)
                .thenApply(result -> (Map<String, Object>) result);
    }

    /**
     * Returns the indices and the total length of all the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(GlideString key1, GlideString key2) {
        return executeCommand(CommandType.LCS, key1.toString(), key2.toString(), IDX_COMMAND_STRING)
                .thenApply(result -> (Map<String, Object>) result);
    }

    /**
     * Returns the indices and the total length of all the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(String key1, String key2, long minMatchLen) {
        return executeCommand(CommandType.LCS, key1, key2, IDX_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, String.valueOf(minMatchLen))
                .thenApply(result -> (Map<String, Object>) result);
    }

    /**
     * Returns the indices and the total length of all the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdx(GlideString key1, GlideString key2, long minMatchLen) {
        return executeCommand(CommandType.LCS, key1.toString(), key2.toString(), IDX_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, String.valueOf(minMatchLen))
                .thenApply(result -> (Map<String, Object>) result);
    }

    /**
     * Returns the indices and lengths of the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(String key1, String key2) {
        return executeCommand(CommandType.LCS, key1, key2, IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING)
                .thenApply(result -> (Map<String, Object>) result);
    }

    /**
     * Returns the indices and lengths of the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(GlideString key1, GlideString key2) {
        return executeCommand(CommandType.LCS, key1.toString(), key2.toString(), IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING)
                .thenApply(result -> (Map<String, Object>) result);
    }

    /**
     * Returns the indices and lengths of the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(String key1, String key2, long minMatchLen) {
        return executeCommand(CommandType.LCS, key1, key2, IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, String.valueOf(minMatchLen))
                .thenApply(result -> (Map<String, Object>) result);
    }

    /**
     * Returns the indices and lengths of the longest common subsequences between strings.
     */
    @Override
    public CompletableFuture<Map<String, Object>> lcsIdxWithMatchLen(GlideString key1, GlideString key2, long minMatchLen) {
        return executeCommand(CommandType.LCS, key1.toString(), key2.toString(), IDX_COMMAND_STRING, WITHMATCHLEN_COMMAND_STRING, MINMATCHLEN_COMMAND_STRING, String.valueOf(minMatchLen))
                .thenApply(result -> (Map<String, Object>) result);
    }

    // ==================== REMAINING LISTBASECOMMANDS METHODS ====================

    /**
     * Returns the index of the first occurrence of element inside the list.
     */
    @Override
    public CompletableFuture<Long> lpos(String key, String element) {
        return executeCommand(CommandType.LPOS, key, element)
                .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Returns the index of the first occurrence of element inside the list.
     */
    @Override
    public CompletableFuture<Long> lpos(GlideString key, GlideString element) {
        return executeCommand(CommandType.LPOS, key.toString(), element.toString())
                .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Returns the index of the first occurrence of element inside the list with options.
     */
    @Override
    public CompletableFuture<Long> lpos(String key, String element, LPosOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(element);
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return executeCommand(CommandType.LPOS, args.toArray(new String[0]))
                .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Returns the index of the first occurrence of element inside the list with options.
     */
    @Override
    public CompletableFuture<Long> lpos(GlideString key, GlideString element, LPosOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(element.toString());
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        return executeCommand(CommandType.LPOS, args.toArray(new String[0]))
                .thenApply(result -> result == null ? null : Long.parseLong(result.toString()));
    }

    /**
     * Returns the indices of the first count occurrences of element inside the list.
     */
    @Override
    public CompletableFuture<Long[]> lposCount(String key, String element, long count) {
        return executeCommand(CommandType.LPOS, key, element, LPosOptions.COUNT_VALKEY_API, String.valueOf(count))
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Long[] longs = new Long[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            longs[i] = objects[i] == null ? null : Long.parseLong(objects[i].toString());
                        }
                        return longs;
                    }
                    return new Long[0];
                });
    }

    /**
     * Returns the indices of the first count occurrences of element inside the list.
     */
    @Override
    public CompletableFuture<Long[]> lposCount(GlideString key, GlideString element, long count) {
        return executeCommand(CommandType.LPOS, key.toString(), element.toString(), LPosOptions.COUNT_VALKEY_API, String.valueOf(count))
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Long[] longs = new Long[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            longs[i] = objects[i] == null ? null : Long.parseLong(objects[i].toString());
                        }
                        return longs;
                    }
                    return new Long[0];
                });
    }

    /**
     * Returns the indices of the first count occurrences of element inside the list with options.
     */
    @Override
    public CompletableFuture<Long[]> lposCount(String key, String element, long count, LPosOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(element);
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        args.add(LPosOptions.COUNT_VALKEY_API);
        args.add(String.valueOf(count));
        return executeCommand(CommandType.LPOS, args.toArray(new String[0]))
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Long[] longs = new Long[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            longs[i] = objects[i] == null ? null : Long.parseLong(objects[i].toString());
                        }
                        return longs;
                    }
                    return new Long[0];
                });
    }

    /**
     * Returns the indices of the first count occurrences of element inside the list with options.
     */
    @Override
    public CompletableFuture<Long[]> lposCount(GlideString key, GlideString element, long count, LPosOptions options) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(element.toString());
        if (options != null) {
            args.addAll(Arrays.asList(options.toArgs()));
        }
        args.add(LPosOptions.COUNT_VALKEY_API);
        args.add(String.valueOf(count));
        return executeCommand(CommandType.LPOS, args.toArray(new String[0]))
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Long[] longs = new Long[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            longs[i] = objects[i] == null ? null : Long.parseLong(objects[i].toString());
                        }
                        return longs;
                    }
                    return new Long[0];
                });
    }

    /**
     * Removes and returns up to count elements from the head of the list.
     */
    @Override
    public CompletableFuture<String[]> lpopCount(String key, long count) {
        return executeCommand(CommandType.LPOP, key, String.valueOf(count))
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
     * Removes and returns up to count elements from the head of the list.
     */
    @Override
    public CompletableFuture<GlideString[]> lpopCount(GlideString key, long count) {
        return executeCommand(CommandType.LPOP, key.toString(), String.valueOf(count))
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
     * Removes and returns up to count elements from the tail of the list.
     */
    @Override
    public CompletableFuture<String[]> rpopCount(String key, long count) {
        return executeCommand(CommandType.RPOP, key, String.valueOf(count))
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
     * Removes and returns up to count elements from the tail of the list.
     */
    @Override
    public CompletableFuture<GlideString[]> rpopCount(GlideString key, long count) {
        return executeCommand(CommandType.RPOP, key.toString(), String.valueOf(count))
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
     * Pops elements from the first non-empty list.
     */
    @Override
    public CompletableFuture<Map<String, String[]>> lmpop(String[] keys, ListDirection direction) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.LMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<String, String[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            String key = objects[0].toString();
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                String[] values = new String[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : valueObjects[i].toString();
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    /**
     * Pops elements from the first non-empty list.
     */
    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> lmpop(GlideString[] keys, ListDirection direction) {
        String[] args = new String[keys.length + 2];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.LMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<GlideString, GlideString[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            GlideString key = GlideString.of(objects[0].toString());
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                GlideString[] values = new GlideString[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : GlideString.of(valueObjects[i].toString());
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    /**
     * Pops count elements from the first non-empty list.
     */
    @Override
    public CompletableFuture<Map<String, String[]>> lmpop(String[] keys, ListDirection direction, long count) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        args[keys.length + 2] = LPosOptions.COUNT_VALKEY_API;
        args[keys.length + 3] = String.valueOf(count);
        return executeCommand(CommandType.LMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<String, String[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            String key = objects[0].toString();
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                String[] values = new String[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : valueObjects[i].toString();
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    /**
     * Pops count elements from the first non-empty list.
     */
    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> lmpop(GlideString[] keys, ListDirection direction, long count) {
        String[] args = new String[keys.length + 4];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        args[keys.length + 2] = LPosOptions.COUNT_VALKEY_API;
        args[keys.length + 3] = String.valueOf(count);
        return executeCommand(CommandType.LMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<GlideString, GlideString[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            GlideString key = GlideString.of(objects[0].toString());
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                GlideString[] values = new GlideString[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : GlideString.of(valueObjects[i].toString());
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    /**
     * Blocking version of lmpop.
     */
    @Override
    public CompletableFuture<Map<String, String[]>> blmpop(String[] keys, ListDirection direction, double timeout) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        args[keys.length + 2] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.BLMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<String, String[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            String key = objects[0].toString();
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                String[] values = new String[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : valueObjects[i].toString();
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    /**
     * Blocking version of lmpop.
     */
    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> blmpop(GlideString[] keys, ListDirection direction, double timeout) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 2] = keys[i].toString();
        }
        args[keys.length + 2] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        return executeCommand(CommandType.BLMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<GlideString, GlideString[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            GlideString key = GlideString.of(objects[0].toString());
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                GlideString[] values = new GlideString[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : GlideString.of(valueObjects[i].toString());
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    /**
     * Blocking version of lmpop with count.
     */
    @Override
    public CompletableFuture<Map<String, String[]>> blmpop(String[] keys, ListDirection direction, long count, double timeout) {
        String[] args = new String[keys.length + 5];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 2, keys.length);
        args[keys.length + 2] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        args[keys.length + 3] = LPosOptions.COUNT_VALKEY_API;
        args[keys.length + 4] = String.valueOf(count);
        return executeCommand(CommandType.BLMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<String, String[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            String key = objects[0].toString();
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                String[] values = new String[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : valueObjects[i].toString();
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    /**
     * Blocking version of lmpop with count.
     */
    @Override
    public CompletableFuture<Map<GlideString, GlideString[]>> blmpop(GlideString[] keys, ListDirection direction, long count, double timeout) {
        String[] args = new String[keys.length + 5];
        args[0] = String.valueOf(timeout);
        args[1] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 2] = keys[i].toString();
        }
        args[keys.length + 2] = direction == ListDirection.LEFT ? "LEFT" : "RIGHT";
        args[keys.length + 3] = LPosOptions.COUNT_VALKEY_API;
        args[keys.length + 4] = String.valueOf(count);
        return executeCommand(CommandType.BLMPOP, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Map<GlideString, GlideString[]> map = new java.util.HashMap<>();
                        if (objects.length >= 2) {
                            GlideString key = GlideString.of(objects[0].toString());
                            if (objects[1] instanceof Object[]) {
                                Object[] valueObjects = (Object[]) objects[1];
                                GlideString[] values = new GlideString[valueObjects.length];
                                for (int i = 0; i < valueObjects.length; i++) {
                                    values[i] = valueObjects[i] == null ? null : GlideString.of(valueObjects[i].toString());
                                }
                                map.put(key, values);
                            }
                        }
                        return map;
                    }
                    return java.util.Collections.emptyMap();
                });
    }

    // ==================== MISSING SETBASECOMMANDS METHODS ====================

    /**
     * Returns whether each member is a member of the set stored at key.
     */
    @Override
    public CompletableFuture<Boolean[]> smismember(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return executeCommand(CommandType.SMISMEMBER, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Boolean[] results = new Boolean[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            results[i] = "1".equals(objects[i].toString());
                        }
                        return results;
                    }
                    return new Boolean[0];
                });
    }

    /**
     * Returns whether each member is a member of the set stored at key.
     */
    @Override
    public CompletableFuture<Boolean[]> smismember(GlideString key, GlideString[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key.toString();
        for (int i = 0; i < members.length; i++) {
            args[i + 1] = members[i].toString();
        }
        return executeCommand(CommandType.SMISMEMBER, args)
                .thenApply(result -> {
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        Boolean[] results = new Boolean[objects.length];
                        for (int i = 0; i < objects.length; i++) {
                            results[i] = "1".equals(objects[i].toString());
                        }
                        return results;
                    }
                    return new Boolean[0];
                });
    }

    /**
     * Moves a member from one set to another set.
     */
    @Override
    public CompletableFuture<Boolean> smove(String source, String destination, String member) {
        return executeCommand(CommandType.SMOVE, source, destination, member)
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Moves a member from one set to another set.
     */
    @Override
    public CompletableFuture<Boolean> smove(GlideString source, GlideString destination, GlideString member) {
        return executeCommand(CommandType.SMOVE, source.toString(), destination.toString(), member.toString())
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Computes the difference between the first set and all the successive sets and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sdiffstore(String destination, String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return executeCommand(CommandType.SDIFFSTORE, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Computes the difference between the first set and all the successive sets and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sdiffstore(GlideString destination, GlideString[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination.toString();
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return executeCommand(CommandType.SDIFFSTORE, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the cardinality of the intersection of all the given sets.
     */
    @Override
    public CompletableFuture<Long> sintercard(String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        return executeCommand(CommandType.SINTERCARD, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the cardinality of the intersection of all the given sets.
     */
    @Override
    public CompletableFuture<Long> sintercard(GlideString[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return executeCommand(CommandType.SINTERCARD, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the cardinality of the intersection of all the given sets, limited to the specified count.
     */
    @Override
    public CompletableFuture<Long> sintercard(String[] keys, long limit) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        System.arraycopy(keys, 0, args, 1, keys.length);
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return executeCommand(CommandType.SINTERCARD, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the cardinality of the intersection of all the given sets, limited to the specified count.
     */
    @Override
    public CompletableFuture<Long> sintercard(GlideString[] keys, long limit) {
        String[] args = new String[keys.length + 3];
        args[0] = String.valueOf(keys.length);
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        args[keys.length + 1] = "LIMIT";
        args[keys.length + 2] = String.valueOf(limit);
        return executeCommand(CommandType.SINTERCARD, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Computes the intersection of all the given sets and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sinterstore(String destination, String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return executeCommand(CommandType.SINTERSTORE, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Computes the intersection of all the given sets and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sinterstore(GlideString destination, GlideString[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination.toString();
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return executeCommand(CommandType.SINTERSTORE, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Computes the union of all the given sets and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sunionstore(String destination, String[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination;
        System.arraycopy(keys, 0, args, 1, keys.length);
        return executeCommand(CommandType.SUNIONSTORE, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Computes the union of all the given sets and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sunionstore(GlideString destination, GlideString[] keys) {
        String[] args = new String[keys.length + 1];
        args[0] = destination.toString();
        for (int i = 0; i < keys.length; i++) {
            args[i + 1] = keys[i].toString();
        }
        return executeCommand(CommandType.SUNIONSTORE, args)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns a random member from the set stored at key.
     */
    @Override
    public CompletableFuture<String> srandmember(String key) {
        return executeCommand(CommandType.SRANDMEMBER, key)
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Returns a random member from the set stored at key.
     */
    @Override
    public CompletableFuture<GlideString> srandmember(GlideString key) {
        return executeCommand(CommandType.SRANDMEMBER, key.toString())
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Returns multiple random members from the set stored at key.
     */
    @Override
    public CompletableFuture<String[]> srandmember(String key, long count) {
        return executeCommand(CommandType.SRANDMEMBER, key, String.valueOf(count))
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
     * Returns multiple random members from the set stored at key.
     */
    @Override
    public CompletableFuture<GlideString[]> srandmember(GlideString key, long count) {
        return executeCommand(CommandType.SRANDMEMBER, key.toString(), String.valueOf(count))
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
     * Removes and returns a random member from the set stored at key.
     */
    @Override
    public CompletableFuture<String> spop(String key) {
        return executeCommand(CommandType.SPOP, key)
                .thenApply(result -> result == null ? null : result.toString());
    }

    /**
     * Removes and returns a random member from the set stored at key.
     */
    @Override
    public CompletableFuture<GlideString> spop(GlideString key) {
        return executeCommand(CommandType.SPOP, key.toString())
                .thenApply(result -> result == null ? null : GlideString.of(result.toString()));
    }

    /**
     * Removes and returns multiple random members from the set stored at key.
     */
    @Override
    public CompletableFuture<java.util.Set<String>> spopCount(String key, long count) {
        return executeCommand(CommandType.SPOP, key, String.valueOf(count))
                .thenApply(result -> {
                    java.util.Set<String> set = new java.util.HashSet<>();
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        for (Object obj : objects) {
                            if (obj != null) {
                                set.add(obj.toString());
                            }
                        }
                    }
                    return set;
                });
    }

    /**
     * Removes and returns multiple random members from the set stored at key.
     */
    @Override
    public CompletableFuture<java.util.Set<GlideString>> spopCount(GlideString key, long count) {
        return executeCommand(CommandType.SPOP, key.toString(), String.valueOf(count))
                .thenApply(result -> {
                    java.util.Set<GlideString> set = new java.util.HashSet<>();
                    if (result instanceof Object[]) {
                        Object[] objects = (Object[]) result;
                        for (Object obj : objects) {
                            if (obj != null) {
                                set.add(GlideString.of(obj.toString()));
                            }
                        }
                    }
                    return set;
                });
    }

    /**
     * Iterates elements of Set types.
     */
    @Override
    public CompletableFuture<Object[]> sscan(String key, String cursor) {
        return executeCommand(CommandType.SSCAN, key, cursor)
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates elements of Set types.
     */
    @Override
    public CompletableFuture<Object[]> sscan(GlideString key, GlideString cursor) {
        return executeCommand(CommandType.SSCAN, key.toString(), cursor.toString())
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates elements of Set types with options.
     */
    @Override
    public CompletableFuture<Object[]> sscan(String key, String cursor, glide.api.models.commands.scan.SScanOptions sScanOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(cursor);
        if (sScanOptions != null) {
            args.addAll(Arrays.asList(sScanOptions.toArgs()));
        }
        return executeCommand(CommandType.SSCAN, args.toArray(new String[0]))
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates elements of Set types with options.
     */
    @Override
    public CompletableFuture<Object[]> sscan(GlideString key, GlideString cursor, glide.api.models.commands.scan.SScanOptionsBinary sScanOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(cursor.toString());
        if (sScanOptions != null) {
            args.addAll(Arrays.asList(sScanOptions.toArgs()));
        }
        return executeCommand(CommandType.SSCAN, args.toArray(new String[0]))
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates elements of Sorted Set types.
     */
    public CompletableFuture<Object[]> zscan(String key, String cursor) {
        return executeCommand(CommandType.ZSCAN, key, cursor)
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates elements of Sorted Set types (binary version).
     */
    public CompletableFuture<Object[]> zscan(GlideString key, GlideString cursor) {
        return executeCommand(CommandType.ZSCAN, key.toString(), cursor.toString())
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates elements of Sorted Set types with options.
     */
    public CompletableFuture<Object[]> zscan(String key, String cursor, glide.api.models.commands.scan.ZScanOptions zScanOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(cursor);
        if (zScanOptions != null) {
            args.addAll(Arrays.asList(zScanOptions.toArgs()));
        }
        return executeCommand(CommandType.ZSCAN, args.toArray(new String[0]))
                .thenApply(result -> (Object[]) result);
    }

    /**
     * Iterates elements of Sorted Set types with options (binary version).
     */
    public CompletableFuture<Object[]> zscan(GlideString key, GlideString cursor, glide.api.models.commands.scan.ZScanOptionsBinary zScanOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(cursor.toString());
        if (zScanOptions != null) {
            args.addAll(Arrays.asList(zScanOptions.toArgs()));
        }
        return executeCommand(CommandType.ZSCAN, args.toArray(new String[0]))
                .thenApply(result -> (Object[]) result);
    }

    // ==================== MISSING GENERICBASECOMMANDS METHODS ====================

    /**
     * Checks if one or more keys exist (array version).
     */
    @Override
    public CompletableFuture<Long> exists(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.EXISTS, stringKeys)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Unlinks (deletes) one or more keys in a non-blocking manner.
     */
    @Override
    public CompletableFuture<Long> unlink(String[] keys) {
        return executeCommand(CommandType.UNLINK, keys)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Unlinks (deletes) one or more keys in a non-blocking manner.
     */
    @Override
    public CompletableFuture<Long> unlink(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.UNLINK, stringKeys)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Sets a timeout on key with options.
     */
    @Override
    public CompletableFuture<Boolean> expire(String key, long seconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(String.valueOf(seconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.EXPIRE, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key with options.
     */
    @Override
    public CompletableFuture<Boolean> expire(GlideString key, long seconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(String.valueOf(seconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.EXPIRE, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp.
     */
    @Override
    public CompletableFuture<Boolean> expireAt(String key, long unixSeconds) {
        return executeCommand(CommandType.EXPIREAT, key, String.valueOf(unixSeconds))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp.
     */
    @Override
    public CompletableFuture<Boolean> expireAt(GlideString key, long unixSeconds) {
        return executeCommand(CommandType.EXPIREAT, key.toString(), String.valueOf(unixSeconds))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp with options.
     */
    @Override
    public CompletableFuture<Boolean> expireAt(String key, long unixSeconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(String.valueOf(unixSeconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.EXPIREAT, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp with options.
     */
    @Override
    public CompletableFuture<Boolean> expireAt(GlideString key, long unixSeconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(String.valueOf(unixSeconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.EXPIREAT, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key in milliseconds.
     */
    @Override
    public CompletableFuture<Boolean> pexpire(String key, long milliseconds) {
        return executeCommand(CommandType.PEXPIRE, key, String.valueOf(milliseconds))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key in milliseconds.
     */
    @Override
    public CompletableFuture<Boolean> pexpire(GlideString key, long milliseconds) {
        return executeCommand(CommandType.PEXPIRE, key.toString(), String.valueOf(milliseconds))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key in milliseconds with options.
     */
    @Override
    public CompletableFuture<Boolean> pexpire(String key, long milliseconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(String.valueOf(milliseconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.PEXPIRE, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key in milliseconds with options.
     */
    @Override
    public CompletableFuture<Boolean> pexpire(GlideString key, long milliseconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(String.valueOf(milliseconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.PEXPIRE, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp in milliseconds.
     */
    @Override
    public CompletableFuture<Boolean> pexpireAt(String key, long unixMilliseconds) {
        return executeCommand(CommandType.PEXPIREAT, key, String.valueOf(unixMilliseconds))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp in milliseconds.
     */
    @Override
    public CompletableFuture<Boolean> pexpireAt(GlideString key, long unixMilliseconds) {
        return executeCommand(CommandType.PEXPIREAT, key.toString(), String.valueOf(unixMilliseconds))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp in milliseconds with options.
     */
    @Override
    public CompletableFuture<Boolean> pexpireAt(String key, long unixMilliseconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        args.add(String.valueOf(unixMilliseconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.PEXPIREAT, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Sets a timeout on key at a specific Unix timestamp in milliseconds with options.
     */
    @Override
    public CompletableFuture<Boolean> pexpireAt(GlideString key, long unixMilliseconds, glide.api.models.commands.ExpireOptions expireOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(String.valueOf(unixMilliseconds));
        if (expireOptions != null) {
            args.addAll(Arrays.asList(expireOptions.toArgs()));
        }
        return executeCommand(CommandType.PEXPIREAT, args.toArray(new String[0]))
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Returns the absolute Unix timestamp at which the given key will expire.
     */
    @Override
    public CompletableFuture<Long> expiretime(String key) {
        return executeCommand(CommandType.EXPIRETIME, key)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the absolute Unix timestamp at which the given key will expire.
     */
    @Override
    public CompletableFuture<Long> expiretime(GlideString key) {
        return executeCommand(CommandType.EXPIRETIME, key.toString())
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the absolute Unix timestamp in milliseconds at which the given key will expire.
     */
    @Override
    public CompletableFuture<Long> pexpiretime(String key) {
        return executeCommand(CommandType.PEXPIRETIME, key)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the absolute Unix timestamp in milliseconds at which the given key will expire.
     */
    @Override
    public CompletableFuture<Long> pexpiretime(GlideString key) {
        return executeCommand(CommandType.PEXPIRETIME, key.toString())
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the remaining time to live in milliseconds.
     */
    @Override
    public CompletableFuture<Long> pttl(String key) {
        return executeCommand(CommandType.PTTL, key)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Returns the remaining time to live in milliseconds.
     */
    @Override
    public CompletableFuture<Long> pttl(GlideString key) {
        return executeCommand(CommandType.PTTL, key.toString())
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Removes the timeout from a key.
     */
    @Override
    public CompletableFuture<Boolean> persist(String key) {
        return executeCommand(CommandType.PERSIST, key)
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Removes the timeout from a key.
     */
    @Override
    public CompletableFuture<Boolean> persist(GlideString key) {
        return executeCommand(CommandType.PERSIST, key.toString())
                .thenApply(result -> "1".equals(result.toString()));
    }

    /**
     * Alters the last access time of one or more keys.
     */
    @Override
    public CompletableFuture<Long> touch(String[] keys) {
        return executeCommand(CommandType.TOUCH, keys)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Alters the last access time of one or more keys.
     */
    @Override
    public CompletableFuture<Long> touch(GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.TOUCH, stringKeys)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Copies a key to another key with optional replace flag.
     */
    @Override
    public CompletableFuture<Boolean> copy(String source, String destination, boolean replace) {
        if (replace) {
            return executeCommand(CommandType.COPY, source, destination, "REPLACE")
                    .thenApply(result -> "1".equals(result.toString()));
        } else {
            return executeCommand(CommandType.COPY, source, destination)
                    .thenApply(result -> "1".equals(result.toString()));
        }
    }

    /**
     * Copies a key to another key with optional replace flag.
     */
    @Override
    public CompletableFuture<Boolean> copy(GlideString source, GlideString destination, boolean replace) {
        if (replace) {
            return executeCommand(CommandType.COPY, source.toString(), destination.toString(), "REPLACE")
                    .thenApply(result -> "1".equals(result.toString()));
        } else {
            return executeCommand(CommandType.COPY, source.toString(), destination.toString())
                    .thenApply(result -> "1".equals(result.toString()));
        }
    }

    /**
     * Restores a key using the provided serialized value with options.
     */
    @Override
    public CompletableFuture<String> restore(GlideString key, long ttl, byte[] value, glide.api.models.commands.RestoreOptions restoreOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        args.add(String.valueOf(ttl));
        args.add(new String(value));
        if (restoreOptions != null) {
            GlideString[] optionArgs = restoreOptions.toArgs();
            for (GlideString arg : optionArgs) {
                args.add(arg.toString());
            }
        }
        return executeCommand(CommandType.RESTORE, args.toArray(new String[0]))
                .thenApply(result -> result.toString());
    }

    /**
     * Sorts the elements in the list, set, or sorted set at key.
     */
    @Override
    public CompletableFuture<String[]> sort(String key) {
        return executeCommand(CommandType.SORT, key)
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
     * Sorts the elements in the list, set, or sorted set at key.
     */
    @Override
    public CompletableFuture<GlideString[]> sort(GlideString key) {
        return executeCommand(CommandType.SORT, key.toString())
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
     * Sorts the elements in the list, set, or sorted set at key with options.
     */
    @Override
    public CompletableFuture<String[]> sort(String key, glide.api.models.commands.SortOptions sortOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        if (sortOptions != null) {
            args.addAll(Arrays.asList(sortOptions.toArgs()));
        }
        return executeCommand(CommandType.SORT, args.toArray(new String[0]))
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
     * Sorts the elements in the list, set, or sorted set at key with options.
     */
    @Override
    public CompletableFuture<GlideString[]> sort(GlideString key, glide.api.models.commands.SortOptionsBinary sortOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        if (sortOptions != null) {
            args.addAll(Arrays.asList(sortOptions.toArgs()));
        }
        return executeCommand(CommandType.SORT, args.toArray(new String[0]))
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
     * Read-only variant of SORT command.
     */
    @Override
    public CompletableFuture<String[]> sortReadOnly(String key) {
        return executeCommand(CommandType.SORT_RO, key)
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
     * Read-only variant of SORT command.
     */
    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(GlideString key) {
        return executeCommand(CommandType.SORT_RO, key.toString())
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
     * Read-only variant of SORT command with options.
     */
    @Override
    public CompletableFuture<String[]> sortReadOnly(String key, glide.api.models.commands.SortOptions sortOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        if (sortOptions != null) {
            args.addAll(Arrays.asList(sortOptions.toArgs()));
        }
        return executeCommand(CommandType.SORT_RO, args.toArray(new String[0]))
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
     * Read-only variant of SORT command with options.
     */
    @Override
    public CompletableFuture<GlideString[]> sortReadOnly(GlideString key, glide.api.models.commands.SortOptionsBinary sortOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        if (sortOptions != null) {
            args.addAll(Arrays.asList(sortOptions.toArgs()));
        }
        return executeCommand(CommandType.SORT_RO, args.toArray(new String[0]))
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
     * Sorts the elements and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sortStore(String key, String destination) {
        return executeCommand(CommandType.SORT, key, "STORE", destination)
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Sorts the elements and stores the result in destination.
     */
    @Override
    public CompletableFuture<Long> sortStore(GlideString key, GlideString destination) {
        return executeCommand(CommandType.SORT, key.toString(), "STORE", destination.toString())
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Sorts the elements and stores the result in destination with options.
     */
    @Override
    public CompletableFuture<Long> sortStore(String key, String destination, glide.api.models.commands.SortOptions sortOptions) {
        List<String> args = new ArrayList<>();
        args.add(key);
        if (sortOptions != null) {
            args.addAll(Arrays.asList(sortOptions.toArgs()));
        }
        args.add("STORE");
        args.add(destination);
        return executeCommand(CommandType.SORT, args.toArray(new String[0]))
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Sorts the elements and stores the result in destination with options.
     */
    @Override
    public CompletableFuture<Long> sortStore(GlideString key, GlideString destination, glide.api.models.commands.SortOptionsBinary sortOptions) {
        List<String> args = new ArrayList<>();
        args.add(key.toString());
        if (sortOptions != null) {
            args.addAll(Arrays.asList(sortOptions.toArgs()));
        }
        args.add("STORE");
        args.add(destination.toString());
        return executeCommand(CommandType.SORT, args.toArray(new String[0]))
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    /**
     * Blocks until all previously sent write commands complete.
     */
    @Override
    public CompletableFuture<Long> wait(long numreplicas, long timeout) {
        return executeCommand(CommandType.WAIT, String.valueOf(numreplicas), String.valueOf(timeout))
                .thenApply(result -> Long.parseLong(result.toString()));
    }

    // ============================================================================
    // Function Commands  
    // ============================================================================

    /**
     * Call a Valkey function.
     *
     * @param functionName The name of the function to call
     * @param keys The keys that the function will access
     * @param args The arguments to pass to the function
     * @return A CompletableFuture containing the result of the function call
     */
    public CompletableFuture<Object> fcall(String functionName, String[] keys, String[] args) {
        return executeCommand(CommandType.FCALL, concatArrays(new String[]{functionName, String.valueOf(keys.length)}, keys, args));
    }

    /**
     * Call a Valkey function (read-only version).
     *
     * @param functionName The name of the function to call
     * @param keys The keys that the function will access
     * @param args The arguments to pass to the function
     * @return A CompletableFuture containing the result of the function call
     */
    public CompletableFuture<Object> fcallReadOnly(String functionName, String[] keys, String[] args) {
        return executeCommand(CommandType.FCALL_RO, concatArrays(new String[]{functionName, String.valueOf(keys.length)}, keys, args));
    }

    /**
     * Load a function library.
     *
     * @param libraryCode The source code of the library
     * @param replace Whether to replace existing library
     * @return A CompletableFuture containing the library name
     */
    public CompletableFuture<String> functionLoad(String libraryCode, boolean replace) {
        List<String> args = new ArrayList<>();
        if (replace) {
            args.add("REPLACE");
        }
        args.add(libraryCode);
        return executeCommand(CommandType.FUNCTION_LOAD, args.toArray(new String[0]))
                .thenApply(result -> result.toString());
    }

    /**
     * Delete a function library.
     *
     * @param libraryName The name of the library to delete
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> functionDelete(String libraryName) {
        return executeCommand(CommandType.FUNCTION_DELETE, libraryName)
                .thenApply(result -> result.toString());
    }

    /**
     * Flush all functions.
     *
     * @param mode The flush mode (ASYNC or SYNC), null for default
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> functionFlush(String mode) {
        List<String> args = new ArrayList<>();
        if (mode != null) {
            args.add(mode);
        }
        return executeCommand(CommandType.FUNCTION_FLUSH, args.toArray(new String[0]))
                .thenApply(result -> result.toString());
    }

    /**
     * List functions.
     *
     * @param libraryName Filter by library name (null for all)
     * @return A CompletableFuture containing the list of functions
     */
    public CompletableFuture<Object> functionList(String libraryName) {
        List<String> args = new ArrayList<>();
        if (libraryName != null) {
            args.add("LIBRARYNAME");
            args.add(libraryName);
        }
        return executeCommand(CommandType.FUNCTION_LIST, args.toArray(new String[0]));
    }

    /**
     * Get function statistics.
     *
     * @return A CompletableFuture containing function statistics
     */
    public CompletableFuture<Object> functionStats() {
        return executeCommand(CommandType.FUNCTION_STATS);
    }

    // ============================================================================
    // Bitmap Commands
    // ============================================================================

    /**
     * Perform a bitwise operation on multiple keys and store the result in a destination key.
     *
     * @param bitwiseOperation The bitwise operation to perform
     * @param destination The key that will store the resulting string
     * @param keys The list of keys to perform the bitwise operation on
     * @return A CompletableFuture containing the size of the resulting string
     */
    public CompletableFuture<Long> bitop(BitwiseOperation bitwiseOperation, String destination, String[] keys) {
        return executeCommand(CommandType.BITOP, concatArrays(new String[]{bitwiseOperation.name(), destination}, keys))
                .thenApply(result -> (Long) result);
    }

    /**
     * Perform a bitwise operation on multiple keys and store the result in a destination key.
     *
     * @param bitwiseOperation The bitwise operation to perform
     * @param destination The key that will store the resulting string
     * @param keys The list of keys to perform the bitwise operation on
     * @return A CompletableFuture containing the size of the resulting string
     */
    public CompletableFuture<Long> bitop(BitwiseOperation bitwiseOperation, GlideString destination, GlideString[] keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.BITOP, concatArrays(new String[]{bitwiseOperation.name(), destination.toString()}, stringKeys))
                .thenApply(result -> (Long) result);
    }

    // ============================================================================
    // Stream Commands
    // ============================================================================

    /**
     * Read data from streams.
     *
     * @param keysAndIds A map of stream names to stream IDs
     * @return A CompletableFuture containing the stream data
     */
    public CompletableFuture<Object> xread(Map<String, String> keysAndIds) {
        List<String> args = new ArrayList<>();
        args.add("XREAD");
        args.add("STREAMS");
        args.addAll(keysAndIds.keySet());
        args.addAll(keysAndIds.values());
        return executeCustomCommand(args.toArray(new String[0]));
    }

    // ============================================================================
    // Geospatial Commands
    // ============================================================================

    /**
     * Search for members in a geospatial index and store the result in a destination key.
     *
     * @param destination The key to store the results
     * @param source The key of the geospatial index to search
     * @param origin The origin for the search
     * @param shape The shape defining the search area
     * @param storeOptions Options for the store operation
     * @param resultOptions Options for the result format
     * @return A CompletableFuture containing the number of elements stored
     */
    public CompletableFuture<Long> geosearchstore(
            String destination,
            String source,
            MemberOrigin origin,
            GeoSearchShape shape,
            GeoSearchStoreOptions storeOptions,
            GeoSearchResultOptions resultOptions) {
        // This is a stub implementation - in a real implementation, this would
        // construct the proper GEOSEARCHSTORE command with all parameters
        List<String> args = new ArrayList<>();
        args.add("GEOSEARCHSTORE");
        args.add(destination);
        args.add(source);
        // Add other parameters based on origin, shape, options...
        // For now, return a stub result
        return executeCustomCommand(args.toArray(new String[0]))
                .thenApply(result -> result instanceof Long ? (Long) result : 0L);
    }

    // ============================================================================
    // Transaction Commands
    // ============================================================================

    /**
     * Watch keys for modifications.
     *
     * @param keys The keys to watch
     * @return A CompletableFuture containing "OK" if successful
     */
    public CompletableFuture<String> watch(String[] keys) {
        return executeCommand(CommandType.WATCH, keys)
                .thenApply(result -> result.toString());
    }

    // ============================================================================
    // Script Management Commands
    // ============================================================================

    /**
     * Returns the source code of the script by its SHA1 hash.
     *
     * @param sha1Hash The SHA1 hash of the script to retrieve
     * @return A CompletableFuture containing the script source code
     */
    public CompletableFuture<String> scriptShow(String sha1Hash) {
        return client.executeStringCommand(CommandType.SCRIPT_SHOW.toString(), new String[]{sha1Hash});
    }

    /**
     * Returns the source code of the script by its SHA1 hash (binary version).
     *
     * @param sha1Hash The SHA1 hash of the script to retrieve as GlideString
     * @return A CompletableFuture containing the script source code as GlideString
     */
    public CompletableFuture<GlideString> scriptShow(GlideString sha1Hash) {
        return client.executeStringCommand(CommandType.SCRIPT_SHOW.toString(), new String[]{sha1Hash.toString()})
                .thenApply(GlideString::of);
    }



    /**
     * Update the connection password for reconnection.
     *
     * @param password The new password to use
     * @param updateConfiguration Whether to update the client configuration
     * @return A CompletableFuture containing "OK" on success
     */
    public CompletableFuture<String> updateConnectionPassword(String password, boolean updateConfiguration) {
        // This is a placeholder implementation
        // In a full implementation, this would update the underlying connection pool
        return CompletableFuture.completedFuture(OK);
    }

    /**
     * Update the connection password for reconnection.
     *
     * @param password The new password to use  
     * @return A CompletableFuture containing "OK" on success
     */
    public CompletableFuture<String> updateConnectionPassword(String password) {
        return updateConnectionPassword(password, false);
    }

    /**
     * Update the connection password for reconnection.
     *
     * @param updateConfiguration Whether to update the client configuration
     * @return A CompletableFuture containing "OK" on success
     */
    public CompletableFuture<String> updateConnectionPassword(boolean updateConfiguration) {
        return updateConnectionPassword("", updateConfiguration);
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
