/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import glide.api.models.GlideString;
import glide.api.models.BaseBatch;
import io.valkey.glide.core.client.GlideClient;
import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;
import java.util.List;
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
    public CompletableFuture<Long> sadd(String key, String... members) {
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
    public CompletableFuture<Long> sadd(GlideString key, GlideString... members) {
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
    public CompletableFuture<Long> srem(String key, String... members) {
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
    public CompletableFuture<Long> srem(GlideString key, GlideString... members) {
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
    public CompletableFuture<String[]> smembers(String key) {
        return executeCommand(CommandType.SMEMBERS, key)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] members = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        members[i] = objects[i].toString();
                    }
                    return members;
                }
                return new String[0];
            });
    }

    /**
     * Return all the members of the set value stored at key (supports binary data).
     *
     * @param key The key of the set (supports binary data)
     * @return A CompletableFuture containing all members of the set
     */
    public CompletableFuture<GlideString[]> smembers(GlideString key) {
        return executeCommand(CommandType.SMEMBERS, key.toString())
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
     * Return the number of elements in the set stored at key.
     *
     * @param key The key of the set
     * @return A CompletableFuture containing the cardinality (number of elements) of the set
     */
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
    public CompletableFuture<String[]> sdiff(String... keys) {
        return executeCommand(CommandType.SDIFF, keys)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] members = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        members[i] = objects[i].toString();
                    }
                    return members;
                }
                return new String[0];
            });
    }

    /**
     * Return the set resulting from the difference between the first set and all the successive sets (supports binary data).
     *
     * @param keys The keys of the sets (supports binary data)
     * @return A CompletableFuture containing the members of the set resulting from the difference
     */
    public CompletableFuture<GlideString[]> sdiff(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.SDIFF, stringKeys)
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
     * Return the set resulting from the intersection of all the given sets.
     *
     * @param keys The keys of the sets
     * @return A CompletableFuture containing the members of the set resulting from the intersection
     */
    public CompletableFuture<String[]> sinter(String... keys) {
        return executeCommand(CommandType.SINTER, keys)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] members = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        members[i] = objects[i].toString();
                    }
                    return members;
                }
                return new String[0];
            });
    }

    /**
     * Return the set resulting from the intersection of all the given sets (supports binary data).
     *
     * @param keys The keys of the sets (supports binary data)
     * @return A CompletableFuture containing the members of the set resulting from the intersection
     */
    public CompletableFuture<GlideString[]> sinter(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.SINTER, stringKeys)
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
     * Return the set resulting from the union of all the given sets.
     *
     * @param keys The keys of the sets
     * @return A CompletableFuture containing the members of the set resulting from the union
     */
    public CompletableFuture<String[]> sunion(String... keys) {
        return executeCommand(CommandType.SUNION, keys)
            .thenApply(result -> {
                if (result instanceof Object[]) {
                    Object[] objects = (Object[]) result;
                    String[] members = new String[objects.length];
                    for (int i = 0; i < objects.length; i++) {
                        members[i] = objects[i].toString();
                    }
                    return members;
                }
                return new String[0];
            });
    }

    /**
     * Return the set resulting from the union of all the given sets (supports binary data).
     *
     * @param keys The keys of the sets (supports binary data)
     * @return A CompletableFuture containing the members of the set resulting from the union
     */
    public CompletableFuture<GlideString[]> sunion(GlideString... keys) {
        String[] stringKeys = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            stringKeys[i] = keys[i].toString();
        }
        return executeCommand(CommandType.SUNION, stringKeys)
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
