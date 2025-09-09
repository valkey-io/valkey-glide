/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.utils.ArgsBuilder.checkTypeOrThrow;
import static glide.utils.ArgsBuilder.newArgsBuilder;

import glide.api.models.BaseBatch;
import glide.api.models.Batch;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.json.JsonArrindexOptions;
import glide.api.models.commands.json.JsonGetOptions;
import glide.api.models.GlideString;
import java.util.Arrays;
import lombok.NonNull;

/**
 * Batch implementation for JSON module. Batches allow the execution of a group of commands in a
 * single step. See {@link Batch}.
 *
 * @example
 *     <pre>{@code
 * Batch batch = new Batch(true);
 * JsonBatch.set(batch, "doc", ".", "{\"a\": 1.0, \"b\": 2}");
 * JsonBatch.get(batch, "doc");
 * Object[] result = client.exec(batch, false).get();
 * assert result[0].equals("OK"); // result of JsonBatch.set()
 * assert result[1].equals("{\"a\": 1.0, \"b\": 2}"); // result of JsonBatch.get()
 * }</pre>
 */
public class JsonBatch {

    private static final String JSON_PREFIX = "JSON.";
    private static final String JSON_SET = JSON_PREFIX + "Set";
    private static final String JSON_GET = JSON_PREFIX + "GET";
    private static final String JSON_MGET = JSON_PREFIX + "MGET";
    private static final String JSON_NUMINCRBY = JSON_PREFIX + "NUMINCRBY";
    private static final String JSON_NUMMULTBY = JSON_PREFIX + "NUMMULTBY";
    private static final String JSON_ARRAPPEND = JSON_PREFIX + "ARRAPPEND";
    private static final String JSON_ARRINSERT = JSON_PREFIX + "ARRINSERT";
    private static final String JSON_ARRINDEX = JSON_PREFIX + "ARRINDEX";
    private static final String JSON_ARRLEN = JSON_PREFIX + "ARRLEN";
    private static final String[] JSON_DEBUG_MEMORY = new String[] {JSON_PREFIX + "DEBUG", "MEMORY"};
    private static final String[] JSON_DEBUG_FIELDS = new String[] {JSON_PREFIX + "DEBUG", "FIELDS"};
    private static final String JSON_ARRPOP = JSON_PREFIX + "ARRPOP";
    private static final String JSON_ARRTRIM = JSON_PREFIX + "ARRTRIM";
    private static final String JSON_OBJLEN = JSON_PREFIX + "OBJLEN";
    private static final String JSON_OBJKEYS = JSON_PREFIX + "OBJKEYS";
    private static final String JSON_DEL = JSON_PREFIX + "DEL";
    private static final String JSON_FORGET = JSON_PREFIX + "FORGET";
    private static final String JSON_TOGGLE = JSON_PREFIX + "TOGGLE";
    private static final String JSON_STRAPPEND = JSON_PREFIX + "STRAPPEND";
    private static final String JSON_STRLEN = JSON_PREFIX + "STRLEN";
    private static final String JSON_CLEAR = JSON_PREFIX + "CLEAR";
    private static final String JSON_RESP = JSON_PREFIX + "RESP";
    private static final String JSON_TYPE = JSON_PREFIX + "TYPE";

    private JsonBatch() {}

    /**
     * Sets the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param batch The batch to execute the command in.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be set. The key
     *     will be modified only if <code>value</code> is added as the last child in the specified
     *     <code>path</code>, or if the specified <code>path</code> acts as the parent of a new child
     *     being added.
     * @param value The value to set at the specific path, in JSON formatted string.
     * @return Command Response - A simple <code>"OK"</code> response if the value is successfully
     *     set.
     */
    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> set(
            @NonNull BaseBatch<T> batch,
            @NonNull ArgType key,
            @NonNull ArgType path,
            @NonNull ArgType value) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        checkTypeOrThrow(value);
        GlideString[] args = newArgsBuilder().add(JSON_SET).add(key).add(path).add(value).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    /**
     * Sets the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param batch The batch to execute the command in.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be set. The key
     *     will be modified only if <code>value</code> is added as the last child in the specified
     *     <code>path</code>, or if the specified <code>path</code> acts as the parent of a new child
     *     being added.
     * @param value The value to set at the specific path, in JSON formatted string.
     * @param setCondition Set the value only if the given condition is met (within the key or path).
     * @return Command Response - A simple <code>"OK"</code> response if the value is successfully
     *     set. If value isn't set because of <code>setCondition</code>, returns <code>null</code>.
     */
    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> set(
            @NonNull BaseBatch<T> batch,
            @NonNull ArgType key,
            @NonNull ArgType path,
            @NonNull ArgType value,
            @NonNull ConditionalChange setCondition) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        checkTypeOrThrow(value);
        GlideString[] args = newArgsBuilder()
                        .add(JSON_SET)
                        .add(key)
                        .add(path)
                        .add(value)
                        .add(setCondition.getValkeyApi())
                        .toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param batch The batch to execute the command in.
     * @param key The <code>key</code> of the JSON document.
     * @return Command Response - Returns a string representation of the JSON document. If <code>key
     *     </code> doesn't exist, returns <code>null</code>.
     */
    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> get(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_GET).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    /**
     * Retrieves the JSON value at the specified <code>paths</code> stored at <code>key</code>.
     *
     * @param batch The batch to execute the command in.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @return Command Response -
     *     <ul>
     *       <li>If one path is given:
     *           <ul>
     *             <li>For JSONPath (path starts with <code>$</code>): Returns a stringified JSON list
     *                 replies for every possible path, or a string representation of an empty array,
     *                 if path doesn't exist. If <code>key</code> doesn't exist, returns <code>null
     *                 </code>.
     *             <li>For legacy path (path doesn't start with <code>$</code>): Returns a string
     *                 representation of the value in <code>paths</code>. If <code>paths</code>
     *                 doesn't exist, an error is raised. If <code>key</code> doesn't exist, returns
     *                 <code>null</code>.
     *           </ul>
     *       <li>If multiple paths are given: Returns a stringified JSON, in which each path is a key,
     *           and it's corresponding value, is the value as if the path was executed in the command
     *           as a single path.
     *     </ul>
     *     In case of multiple paths, and <code>paths</code> are a mix of both JSONPath and legacy
     *     path, the command behaves as if all are JSONPath paths.
     */
    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> get(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType[] paths) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(paths);
        GlideString[] args = newArgsBuilder().add(JSON_GET).add(key).add(paths).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> get(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType[] paths, @NonNull JsonGetOptions options) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(paths);
        GlideString[] args = newArgsBuilder().add(JSON_GET).add(key).add(paths).add(options.toArgs()).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrappend(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, @NonNull ArgType[] jsonScalarValues) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        checkTypeOrThrow(jsonScalarValues);
        GlideString[] args = newArgsBuilder().add(JSON_ARRAPPEND).add(key).add(path).add(jsonScalarValues).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrindex(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, @NonNull ArgType jsonScalar) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        checkTypeOrThrow(jsonScalar);
        GlideString[] args = newArgsBuilder().add(JSON_ARRINDEX).add(key).add(path).add(jsonScalar).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrindex(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, @NonNull ArgType jsonScalar, @NonNull JsonArrindexOptions options) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        checkTypeOrThrow(jsonScalar);
        GlideString[] args = newArgsBuilder().add(JSON_ARRINDEX).add(key).add(path).add(jsonScalar).add(options.toArgs()).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrinsert(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, int index, @NonNull ArgType[] jsonScalarValues) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        checkTypeOrThrow(jsonScalarValues);
        GlideString[] args = newArgsBuilder().add(JSON_ARRINSERT).add(key).add(path).add(index).add(jsonScalarValues).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrlen(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_ARRLEN).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrpop(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, long index) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_ARRPOP).add(key).add(path).add(index).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrpop(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_ARRPOP).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrpop(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_ARRPOP).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrlen(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_ARRLEN).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> debugFields(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_DEBUG_FIELDS).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> debugFields(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_DEBUG_FIELDS).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> strlen(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_STRLEN).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> arrtrim(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, int start, int stop) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_ARRTRIM).add(key).add(path).add(start).add(stop).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> objlen(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_OBJLEN).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> objlen(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_OBJLEN).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> objkeys(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_OBJKEYS).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> objkeys(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_OBJKEYS).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> del(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_DEL).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> numincrby(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, double number) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_NUMINCRBY).add(key).add(path).add(number).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> nummultby(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path, double number) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_NUMMULTBY).add(key).add(path).add(number).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> strappend(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType jsonScalar, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(jsonScalar);
        GlideString[] args = newArgsBuilder().add(JSON_STRAPPEND).add(key).add(jsonScalar).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> strlen(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_STRLEN).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> toggle(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_TOGGLE).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> resp(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_RESP).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> debugMemory(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_DEBUG_MEMORY).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> debugMemory(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_DEBUG_MEMORY).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> clear(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_CLEAR).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> clear(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_CLEAR).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> strappend(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType jsonScalar) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(jsonScalar);
        GlideString[] args = newArgsBuilder().add(JSON_STRAPPEND).add(key).add(jsonScalar).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> type(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_TYPE).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> resp(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_RESP).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> toggle(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_TOGGLE).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> forget(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_FORGET).add(key).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> forget(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_FORGET).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> mget(
            @NonNull BaseBatch<T> batch, @NonNull ArgType[] keys, @NonNull ArgType path) {
        checkTypeOrThrow(keys);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_MGET).add(keys).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param batch The batch to execute the command in.
     * @param key The <code>key</code> of the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return Command Response - Returns a string representation of the JSON document. If <code>key
     *     </code> doesn't exist, returns <code>null</code>.
     */
    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> get(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull JsonGetOptions options) {
        checkTypeOrThrow(key);
        GlideString[] args = newArgsBuilder().add(JSON_GET).add(key).add(options.toArgs()).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    /**
     * Deletes the JSON value at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>.
     *
     * @param batch The batch to execute the command in.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be deleted.
     * @return Command Response - The number of elements deleted. 0 if the key does not exist, or if
     *     the JSON path is invalid or does not exist.
     */
    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> del(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_DEL).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }

    /**
     * Retrieves the type of the JSON value at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>.
     *
     * @param batch The batch to execute the command in.
     * @param key The key of the JSON document.
     * @param path Represents the path within the JSON document where the type will be retrieved.
     * @return Command Response -
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns a list of string
     *           replies for every possible path, indicating the type of the JSON value. If `path`
     *           doesn't exist, an empty array will be returned.
     *       <li>For legacy path (<code>path</code> doesn't starts with <code>$</code>): Returns the
     *           type of the JSON value at `path`. If multiple paths match, the type of the first JSON
     *           value match is returned. If `path` doesn't exist, <code>null</code> will be returned.
     *     </ul>
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     */
    public static <ArgType, T extends BaseBatch<T>> BaseBatch<T> type(
            @NonNull BaseBatch<T> batch, @NonNull ArgType key, @NonNull ArgType path) {
        checkTypeOrThrow(key);
        checkTypeOrThrow(path);
        GlideString[] args = newArgsBuilder().add(JSON_TYPE).add(key).add(path).toArray();
        return batch.customCommand(Arrays.stream(args).map(GlideString::getString).toArray(String[]::new));
    }
}