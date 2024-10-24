/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.json.JsonGetOptions;
import glide.api.models.commands.json.JsonGetOptionsBinary;
import glide.utils.ArgsBuilder;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/** Module for JSON commands. */
public class Json {

    private static final String JSON_PREFIX = "JSON.";
    public static final String JSON_SET = JSON_PREFIX + "SET";
    public static final String JSON_GET = JSON_PREFIX + "GET";
    private static final String JSON_ARRINSERT = JSON_PREFIX + "ARRINSERT";
    private static final String JSON_ARRLEN = JSON_PREFIX + "ARRLEN";
    private static final String JSON_NUMINCRBY = JSON_PREFIX + "NUMINCRBY";
    private static final String JSON_NUMMULTBY = JSON_PREFIX + "NUMMULTBY";

    private Json() {}

    /**
     * Sets the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be set. The key
     *     will be modified only if <code>value</code> is added as the last child in the specified
     *     <code>path</code>, or if the specified <code>path</code> acts as the parent of a new child
     *     being added.
     * @param value The value to set at the specific path, in JSON formatted string.
     * @return A simple <code>"OK"</code> response if the value is successfully set.
     * @example
     *     <pre>{@code
     * String value = Json.set(client, "doc", ".", "{'a': 1.0, 'b': 2}").get();
     * assert value.equals("OK");
     * }</pre>
     */
    public static CompletableFuture<String> set(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String path,
            @NonNull String value) {
        return executeCommand(client, new String[] {JSON_SET, key, path, value});
    }

    /**
     * Sets the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be set. The key
     *     will be modified only if <code>value</code> is added as the last child in the specified
     *     <code>path</code>, or if the specified <code>path</code> acts as the parent of a new child
     *     being added.
     * @param value The value to set at the specific path, in JSON formatted GlideString.
     * @return A simple <code>"OK"</code> response if the value is successfully set.
     * @example
     *     <pre>{@code
     * String value = Json.set(client, gs("doc"), gs("."), gs("{'a': 1.0, 'b': 2}")).get();
     * assert value.equals("OK");
     * }</pre>
     */
    public static CompletableFuture<String> set(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            @NonNull GlideString value) {
        return executeCommand(client, new GlideString[] {gs(JSON_SET), key, path, value});
    }

    /**
     * Sets the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be set. The key
     *     will be modified only if <code>value</code> is added as the last child in the specified
     *     <code>path</code>, or if the specified <code>path</code> acts as the parent of a new child
     *     being added.
     * @param value The value to set at the specific path, in JSON formatted string.
     * @param setCondition Set the value only if the given condition is met (within the key or path).
     * @return A simple <code>"OK"</code> response if the value is successfully set. If value isn't
     *     set because of <code>setCondition</code>, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * String value = Json.set(client, "doc", ".", "{'a': 1.0, 'b': 2}", ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get();
     * assert value.equals("OK");
     * }</pre>
     */
    public static CompletableFuture<String> set(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String path,
            @NonNull String value,
            @NonNull ConditionalChange setCondition) {
        return executeCommand(
                client, new String[] {JSON_SET, key, path, value, setCondition.getValkeyApi()});
    }

    /**
     * Sets the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be set. The key
     *     will be modified only if <code>value</code> is added as the last child in the specified
     *     <code>path</code>, or if the specified <code>path</code> acts as the parent of a new child
     *     being added.
     * @param value The value to set at the specific path, in JSON formatted GlideString.
     * @param setCondition Set the value only if the given condition is met (within the key or path).
     * @return A simple <code>"OK"</code> response if the value is successfully set. If value isn't
     *     set because of <code>setCondition</code>, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * String value = Json.set(client, gs("doc"), gs("."), gs("{'a': 1.0, 'b': 2}"), ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get();
     * assert value.equals("OK");
     * }</pre>
     */
    public static CompletableFuture<String> set(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            @NonNull GlideString value,
            @NonNull ConditionalChange setCondition) {
        return executeCommand(
                client,
                new GlideString[] {gs(JSON_SET), key, path, value, gs(setCondition.getValkeyApi())});
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return Returns a string representation of the JSON document. If <code>key</code> doesn't
     *     exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * String value = Json.get(client, "doc").get();
     * assert value.equals("{'a': 1.0, 'b': 2}");
     * }</pre>
     */
    public static CompletableFuture<String> get(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_GET, key});
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return Returns a string representation of the JSON document. If <code>key</code> doesn't
     *     exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * GlideString value = Json.get(client, gs("doc")).get();
     * assert value.equals(gs("{'a': 1.0, 'b': 2}"));
     * }</pre>
     */
    public static CompletableFuture<GlideString> get(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_GET), key});
    }

    /**
     * Retrieves the JSON value at the specified <code>paths</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @return
     *     <ul>
     *       <li>If one path is given:
     *           <ul>
     *             <li>For JSONPath (path starts with <code>$</code>): Returns a stringified JSON list
     *                 replies for every possible path, or a string representation of an empty array,
     *                 if path doesn't exist. If <code>key</code> doesn't exist, returns None.
     *             <li>For legacy path (path doesn't start with <code>$</code>): Returns a string
     *                 representation of the value in <code>paths</code>. If <code>paths</code>
     *                 doesn't exist, an error is raised. If <code>key</code> doesn't exist, returns
     *                 None.
     *           </ul>
     *       <li>If multiple paths are given: Returns a stringified JSON, in which each path is a key,
     *           and it's corresponding value, is the value as if the path was executed in the command
     *           as a single path.
     *     </ul>
     *     In case of multiple paths, and <code>paths</code> are a mix of both JSONPath and legacy
     *     path, the command behaves as if all are JSONPath paths.
     * @example
     *     <pre>{@code
     * String value = Json.get(client, "doc", new String[] {"$"}).get();
     * assert value.equals("{'a': 1.0, 'b': 2}");
     * String value = Json.get(client, "doc", new String[] {"$.a", "$.b"}).get();
     * assert value.equals("{\"$.a\": [1.0], \"$.b\": [2]}");
     * }</pre>
     */
    public static CompletableFuture<String> get(
            @NonNull BaseClient client, @NonNull String key, @NonNull String[] paths) {
        return executeCommand(client, concatenateArrays(new String[] {JSON_GET, key}, paths));
    }

    /**
     * Retrieves the JSON value at the specified <code>paths</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @return
     *     <ul>
     *       <li>If one path is given:
     *           <ul>
     *             <li>For JSONPath (path starts with <code>$</code>): Returns a stringified JSON list
     *                 replies for every possible path, or a string representation of an empty array,
     *                 if path doesn't exist. If <code>key</code> doesn't exist, returns None.
     *             <li>For legacy path (path doesn't start with <code>$</code>): Returns a string
     *                 representation of the value in <code>paths</code>. If <code>paths</code>
     *                 doesn't exist, an error is raised. If <code>key</code> doesn't exist, returns
     *                 None.
     *           </ul>
     *       <li>If multiple paths are given: Returns a stringified JSON, in which each path is a key,
     *           and it's corresponding value, is the value as if the path was executed in the command
     *           as a single path.
     *     </ul>
     *     In case of multiple paths, and <code>paths</code> are a mix of both JSONPath and legacy
     *     path, the command behaves as if all are JSONPath paths.
     * @example
     *     <pre>{@code
     * GlideString value = Json.get(client, gs("doc"), new GlideString[] {gs("$")}).get();
     * assert value.equals(gs("{'a': 1.0, 'b': 2}"));
     * GlideString value = Json.get(client, gs("doc"), new GlideString[] {gs("$.a"), gs("$.b")}).get();
     * assert value.equals(gs("{\"$.a\": [1.0], \"$.b\": [2]}"));
     * }</pre>
     */
    public static CompletableFuture<GlideString> get(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString[] paths) {
        return executeCommand(client, concatenateArrays(new GlideString[] {gs(JSON_GET), key}, paths));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return Returns a string representation of the JSON document. If <code>key</code> doesn't
     *     exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * JsonGetOptions options = JsonGetOptions.builder()
     *                              .indent("  ")
     *                              .space(" ")
     *                              .newline("\n")
     *                              .build();
     * String value = Json.get(client, "doc", "$", options).get();
     * assert value.equals("{\n \"a\": \n  1.0\n ,\n \"b\": \n  2\n }");
     * }</pre>
     */
    public static CompletableFuture<String> get(
            @NonNull BaseClient client, @NonNull String key, @NonNull JsonGetOptions options) {
        return executeCommand(
                client, concatenateArrays(new String[] {JSON_GET, key}, options.toArgs()));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return Returns a string representation of the JSON document. If <code>key</code> doesn't
     *     exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * JsonGetOptions options = JsonGetOptions.builder()
     *                              .indent("  ")
     *                              .space(" ")
     *                              .newline("\n")
     *                              .build();
     * GlideString value = Json.get(client, gs("doc"), gs("$"), options).get();
     * assert value.equals(gs("{\n \"a\": \n  1.0\n ,\n \"b\": \n  2\n }"));
     * }</pre>
     */
    public static CompletableFuture<GlideString> get(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull JsonGetOptionsBinary options) {
        return executeCommand(
                client, new ArgsBuilder().add(gs(JSON_GET)).add(key).add(options.toArgs()).toArray());
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return
     *     <ul>
     *       <li>If one path is given:
     *           <ul>
     *             <li>For JSONPath (path starts with <code>$</code>): Returns a stringified JSON list
     *                 replies for every possible path, or a string representation of an empty array,
     *                 if path doesn't exist. If <code>key</code> doesn't exist, returns None.
     *             <li>For legacy path (path doesn't start with <code>$</code>): Returns a string
     *                 representation of the value in <code>paths</code>. If <code>paths</code>
     *                 doesn't exist, an error is raised. If <code>key</code> doesn't exist, returns
     *                 None.
     *           </ul>
     *       <li>If multiple paths are given: Returns a stringified JSON, in which each path is a key,
     *           and it's corresponding value, is the value as if the path was executed in the command
     *           as a single path.
     *     </ul>
     *     In case of multiple paths, and <code>paths</code> are a mix of both JSONPath and legacy
     *     path, the command behaves as if all are JSONPath paths.
     * @example
     *     <pre>{@code
     * JsonGetOptions options = JsonGetOptions.builder()
     *                              .indent("  ")
     *                              .space(" ")
     *                              .newline("\n")
     *                              .build();
     * String value = Json.get(client, "doc", new String[] {"$.a", "$.b"}, options).get();
     * assert value.equals("{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}");
     * }</pre>
     */
    public static CompletableFuture<String> get(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String[] paths,
            @NonNull JsonGetOptions options) {
        return executeCommand(
                client, concatenateArrays(new String[] {JSON_GET, key}, options.toArgs(), paths));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return
     *     <ul>
     *       <li>If one path is given:
     *           <ul>
     *             <li>For JSONPath (path starts with <code>$</code>): Returns a stringified JSON list
     *                 replies for every possible path, or a string representation of an empty array,
     *                 if path doesn't exist. If <code>key</code> doesn't exist, returns None.
     *             <li>For legacy path (path doesn't start with <code>$</code>): Returns a string
     *                 representation of the value in <code>paths</code>. If <code>paths</code>
     *                 doesn't exist, an error is raised. If <code>key</code> doesn't exist, returns
     *                 None.
     *           </ul>
     *       <li>If multiple paths are given: Returns a stringified JSON, in which each path is a key,
     *           and it's corresponding value, is the value as if the path was executed in the command
     *           as a single path.
     *     </ul>
     *     In case of multiple paths, and <code>paths</code> are a mix of both JSONPath and legacy
     *     path, the command behaves as if all are JSONPath paths.
     * @example
     *     <pre>{@code
     * JsonGetOptions options = JsonGetOptions.builder()
     *                              .indent("  ")
     *                              .space(" ")
     *                              .newline("\n")
     *                              .build();
     * GlideString value = Json.get(client, gs("doc"), new GlideString[] {gs("$.a"), gs("$.b")}, options).get();
     * assert value.equals(gs("{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}"));
     * }</pre>
     */
    public static CompletableFuture<GlideString> get(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString[] paths,
            @NonNull JsonGetOptionsBinary options) {
        return executeCommand(
                client,
                new ArgsBuilder().add(gs(JSON_GET)).add(key).add(options.toArgs()).add(paths).toArray());
    }

    /**
     * Inserts one or more values into the array at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>, before the given <code>index</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param index The array index before which values are inserted.
     * @param values The JSON values to be inserted into the array, in JSON formatted bytes or str.
     *     JSON string values must be wrapped with quotes. For example, to append <code>"foo"</code>,
     *     pass <code>"\"foo\""</code>.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of integers for every possible path,
     *           indicating the new length of the array, or <code>null</code> for JSON values matching
     *           the path that are not an array. If <code>path</code> does not exist, an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the new length of the array. If multiple paths are
     *           matched, returns the length of the first modified array. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If the index is out of bounds or <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[[], [\"a\"], [\"a\", \"b\"]]").get();
     * var newValues = new String[] { "\"c\"", "{\"key\": \"value\"}", "true", "null", "[\"bar\"]" };
     * var res = Json.arrinsert(client, "doc", "$[*]", 0, newValues).get();
     * assert Arrays.equals((Object[]) res, new int[] { 5, 6, 7 }); // New lengths of arrays after insertion
     * var doc = Json.get(client, "doc").get();
     * assert doc.equals("[[\"c\", {\"key\": \"value\"}, true, null, [\"bar\"]], [\"c\", {\"key\": \"value\"}, "
     *     + "true, null, [\"bar\"], \"a\"], [\"c\", {\"key\": \"value\"}, true, null, [\"bar\"], \"a\", \"b\"]]");
     *
     * Json.set(client, "doc", "$", "[[], [\"a\"], [\"a\", \"b\"]]").get();
     * res = Json.arrinsert(client, "doc", ".", 0, new String[] { "\"c\"" }).get();
     * assert res == 4 // New length of the root array after insertion
     * doc = Json.get(client, "doc").get();
     * assert doc.equals("[\"c\", [], [\"a\"], [\"a\", \"b\"]]");
     * }</pre>
     */
    public static CompletableFuture<Object> arrinsert(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String path,
            int index,
            @NonNull String[] values) {
        return executeCommand(
                client,
                concatenateArrays(
                        new String[] {JSON_ARRINSERT, key, path, Integer.toString(index)}, values));
    }

    /**
     * Inserts one or more values into the array at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>, before the given <code>index</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param index The array index before which values are inserted.
     * @param values The JSON values to be inserted into the array, in JSON formatted bytes or str.
     *     JSON string values must be wrapped with quotes. For example, to append <code>"foo"</code>,
     *     pass <code>"\"foo\""</code>.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of integers for every possible path,
     *           indicating the new length of the array, or <code>null</code> for JSON values matching
     *           the path that are not an array. If <code>path</code> does not exist, an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the new length of the array. If multiple paths are
     *           matched, returns the length of the first modified array. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If the index is out of bounds or <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[[], [\"a\"], [\"a\", \"b\"]]").get();
     * var newValues = new GlideString[] { gs("\"c\""), gs("{\"key\": \"value\"}"), gs("true"), gs("null"), gs("[\"bar\"]") };
     * var res = Json.arrinsert(client, gs("doc"), gs("$[*]"), 0, newValues).get();
     * assert Arrays.equals((Object[]) res, new int[] { 5, 6, 7 }); // New lengths of arrays after insertion
     * var doc = Json.get(client, "doc").get();
     * assert doc.equals("[[\"c\", {\"key\": \"value\"}, true, null, [\"bar\"]], [\"c\", {\"key\": \"value\"}, "
     *     + "true, null, [\"bar\"], \"a\"], [\"c\", {\"key\": \"value\"}, true, null, [\"bar\"], \"a\", \"b\"]]");
     *
     * Json.set(client, "doc", "$", "[[], [\"a\"], [\"a\", \"b\"]]").get();
     * res = Json.arrinsert(client, gs("doc"), gs("."), 0, new GlideString[] { gs("\"c\"") }).get();
     * assert res == 4 // New length of the root array after insertion
     * doc = Json.get(client, "doc").get();
     * assert doc.equals("[\"c\", [], [\"a\"], [\"a\", \"b\"]]");
     * }</pre>
     */
    public static CompletableFuture<Object> arrinsert(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            int index,
            @NonNull GlideString[] values) {
        return executeCommand(
                client,
                new ArgsBuilder()
                        .add(gs(JSON_ARRINSERT))
                        .add(key)
                        .add(path)
                        .add(Integer.toString(index))
                        .add(values)
                        .toArray());
    }

    /**
     * Retrieves the length of the array at the specified <code>path</code> within the JSON document
     * stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of integers for every possible path,
     *           indicating the length of the array, or <code>null</code> for JSON values matching the
     *           path that are not an array. If <code>path</code> does not exist, an empty array will
     *           be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the length of the array. If multiple paths are
     *           matched, returns the length of the first matching array. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [1, 2, 3], \"b\": {\"a\": [1, 2], \"c\": {\"a\": 42}}}").get();
     * var res = Json.arrlen(client, "doc", "$").get();
     * assert Arrays.equals((Object[]) res, new Object[] { null }); // No array at the root path.
     * res = Json.arrlen(client, "doc", "$.a").get();
     * assert Arrays.equals((Object[]) res, new Object[] { 3 }); // Retrieves the length of the array at path $.a.
     * res = Json.arrlen(client, "doc", "$..a").get();
     * assert Arrays.equals((Object[]) res, new Object[] { 3, 2, null }); // Retrieves lengths of arrays found at all levels of the path `..a`.
     * res = Json.arrlen(client, "doc", "..a").get();
     * assert res == 3; // Legacy path retrieves the first array match at path `..a`.
     * }</pre>
     */
    public static CompletableFuture<Object> arrlen(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_ARRLEN, key, path});
    }

    /**
     * Retrieves the length of the array at the specified <code>path</code> within the JSON document
     * stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of integers for every possible path,
     *           indicating the length of the array, or <code>null</code> for JSON values matching the
     *           path that are not an array. If <code>path</code> does not exist, an empty array will
     *           be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the length of the array. If multiple paths are
     *           matched, returns the length of the first matching array. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [1, 2, 3], \"b\": {\"a\": [1, 2], \"c\": {\"a\": 42}}}").get();
     * var res = Json.arrlen(client, gs("doc"), gs("$")).get();
     * assert Arrays.equals((Object[]) res, new Object[] { null }); // No array at the root path.
     * res = Json.arrlen(client, gs("doc"), gs("$.a")).get();
     * assert Arrays.equals((Object[]) res, new Object[] { 3 }); // Retrieves the length of the array at path $.a.
     * res = Json.arrlen(client, gs("doc"), gs("$..a")).get();
     * assert Arrays.equals((Object[]) res, new Object[] { 3, 2, null }); // Retrieves lengths of arrays found at all levels of the path `..a`.
     * res = Json.arrlen(client, gs("doc"), gs("..a")).get();
     * assert res == 3; // Legacy path retrieves the first array match at path `..a`.
     * }</pre>
     */
    public static CompletableFuture<Object> arrlen(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_ARRLEN), key, path});
    }

    /**
     * Retrieves the length of the array at the root of the JSON document stored at <code>key</code>.
     * <br>
     * Equivalent to {@link #arrlen(BaseClient, String, String)} with <code>path</code> set to <code>
     * "."</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The array length stored at the root of the document. If document root is not an array,
     *     an error is raised.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, true, null, \"tree\"]").get();
     * var res = Json.arrlen(client, "doc").get();
     * assert res == 5;
     * }</pre>
     */
    public static CompletableFuture<Long> arrlen(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_ARRLEN, key});
    }

    /**
     * Retrieves the length of the array at the root of the JSON document stored at <code>key</code>.
     * Equivalent to {@link #arrlen(BaseClient, GlideString, GlideString)} with <code>path</code> set
     * to <code>gs(".")</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The array length stored at the root of the document. If document root is not an array,
     *     an error is raised.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, true, null, \"tree\"]").get();
     * var res = Json.arrlen(client, gs("doc")).get();
     * assert res == 5;
     * }</pre>
     */
    public static CompletableFuture<Long> arrlen(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_ARRLEN), key});
    }

    /**
     * Increments or decrements the JSON value(s) at the specified <code>path</code> by <code>number</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to increment or decrement by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after incrementing for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after the increment or decrement.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     * </ul>
     * If <code>key</code> does not exist, an error is raised.<br>
     * If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.numincrby(client, "doc", "$.d[*]", 10.0).get();
     * assert res.equals("[11,12,13]"); // Increment each element in `d` array by 10.
     * res = Json.numincrby(client, "doc", ".c[1]", 10.0).get();
     * assert res.equals("12"); // Increment the second element in the `c` array by 10.
     * }</pre>
     */
    public static CompletableFuture<String> numincrby(
        @NonNull BaseClient client,
        @NonNull String key,
        @NonNull String path,
        double number) {
        return executeCommand(
            client,
            new String[] {JSON_NUMINCRBY, key, path, Double.toString(number)}
        );
    }

    /**
     * Increments or decrements the JSON value(s) at the specified <code>path</code> by <code>number</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to increment or decrement by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after incrementing for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after the increment or decrement.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     * </ul>
     * If <code>key</code> does not exist, an error is raised.<br>
     * If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.numincrby(client, gs("doc"), gs("$.d[*]"), 10.0).get();
     * assert res.equals(gs("[11,12,13]")); // Increment each element in `d` array by 10.
     * res = Json.numincrby(client, gs("doc"), gs(".c[1]"), 10.0).get();
     * assert res.equals(gs("12")); // Increment the second element in the `c` array by 10.
     * }</pre>
     */
    public static CompletableFuture<GlideString> numincrby(
        @NonNull BaseClient client,
        @NonNull GlideString key,
        @NonNull GlideString path,
        double number) {
        return executeCommand(
            client,
            new GlideString[] {gs(JSON_NUMINCRBY), key, path, gs(Double.toString(number))}
        );
    }

    /**
     * Increments or decrements the JSON value(s) at the specified <code>path</code> by <code>number</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to increment or decrement by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after incrementing for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after the increment or decrement.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     * </ul>
     * If <code>key</code> does not exist, an error is raised.<br>
     * If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.numincrby(client, "doc", "$.d[*]", 10).get();
     * assert res.equals("[11,12,13]"); // Increment each element in `d` array by 10.
     * res = Json.numincrby(client, "doc", ".c[1]", 10).get();
     * assert res.equals("12"); // Increment the second element in the `c` array by 10.
     * }</pre>
     */
    public static CompletableFuture<Object> numincrby(
        @NonNull BaseClient client,
        @NonNull String key,
        @NonNull String path,
        long number) {
        return executeCommand(
            client,
            new String[] {JSON_NUMINCRBY, key, path, Long.toString(number)}
        );
    }

    /**
     * Increments or decrements the JSON value(s) at the specified <code>path</code> by <code>number</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to increment or decrement by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after incrementing for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after the increment or decrement.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     * </ul>
     * If <code>key</code> does not exist, an error is raised.<br>
     * If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.numincrby(client, gs("doc"), gs("$.d[*]"), 10).get();
     * assert res.equals(gs("[11,12,13]")); // Increment each element in `d` array by 10.
     * res = Json.numincrby(client, gs("doc"), gs(".c[1]"), 10).get();
     * assert res.equals(gs("12")); // Increment the second element in the `c` array by 10.
     * }</pre>
     */
    public static CompletableFuture<GlideString> numincrby(
        @NonNull BaseClient client,
        @NonNull GlideString key,
        @NonNull GlideString path,
        long number) {
        return executeCommand(
            client,
            new GlideString[] {gs(JSON_NUMINCRBY), key, path, gs(Long.toString(number))}
        );
    }

    /**
     * Multiplies the JSON value(s) at the specified <code>path</code> by <code>number</code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to multiply by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after multiplication for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after multiplication.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * </ul>
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.nummultby(client, "doc", "$.d[*]", 2.0).get();
     * assert res.equals("[2,4,6]"); // Multiplies each element in the `d` array by 2.
     * res = Json.numincrby(client, "doc", ".c[1]", 2.0).get();
     * assert res.equals("12"); // Multiplies the second element in the `c` array by 2.
     * }</pre>
     */
    public static CompletableFuture<String> nummultby(
        @NonNull BaseClient client,
        @NonNull String key,
        @NonNull String path,
        double number) {
        return executeCommand(
            client,
            new String[] {JSON_NUMMULTBY, key, path, Double.toString(number)}
        );
    }

    /**
     * Multiplies the JSON value(s) at the specified <code>path</code> by <code>number</code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to multiply by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after multiplication for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after multiplication.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * </ul>
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.nummultby(client, gs("doc"), gs("$.d[*]"), 2.0).get();
     * assert res.equals(gs("[2,4,6]")); // Multiplies each element in the `d` array by 2.
     * res = Json.numincrby(client, gs("doc"), gs(".c[1]"), 2.0).get();
     * assert res.equals(gs("12")); // Multiplies the second element in the `c` array by 2.
     * }</pre>
     */
    public static CompletableFuture<GlideString> nummultby(
        @NonNull BaseClient client,
        @NonNull GlideString key,
        @NonNull GlideString path,
        double number) {
        return executeCommand(
            client,
            new GlideString[] {gs(JSON_NUMMULTBY), key, path, gs(Double.toString(number))}
        );
    }

    /**
     * Multiplies the JSON value(s) at the specified <code>path</code> by <code>number</code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to multiply by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after multiplication for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after multiplication.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * </ul>
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.nummultby(client, "doc", "$.d[*]", 2).get();
     * assert res.equals("[2,4,6]"); // Multiplies each element in the `d` array by 2.
     * res = Json.numincrby(client, "doc", ".c[1]", 2).get();
     * assert res.equals("12"); // Multiplies the second element in the `c` array by 2.
     * }</pre>
     */
    public static CompletableFuture<String> nummultby(
        @NonNull BaseClient client,
        @NonNull String key,
        @NonNull String path,
        long number) {
        return executeCommand(
            client,
            new String[] {JSON_NUMMULTBY, key, path, Long.toString(number)}
        );
    }

    /**
     * Multiplies the JSON value(s) at the specified <code>path</code> by <code>number</code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to multiply by.
     * @return <ul>
     *     <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *         Returns a bytes string representation of an array of bulk strings, indicating the new values after multiplication for each matched <code>path</code>.<br>
     *         If a value is not a number, its corresponding return value will be <code>null</code>.<br>
     *         If <code>path</code> doesn't exist, a byte string representation of an empty array will be returned.
     *     </li>
     *     <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *         Returns a bytes string representation of the resulting value after multiplication.<br>
     *         If multiple paths match, the result of the last updated value is returned.<br>
     *         If the value at the <code>path</code> is not a number or <code>path</code> doesn't exist, an error is raised.
     *     </li>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * </ul>
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": [], \"b\": [1], \"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.nummultby(client, gs("doc"), gs("$.d[*]"), 2).get();
     * assert res.equals(gs("[2,4,6]")); // Multiplies each element in the `d` array by 2.
     * res = Json.numincrby(client, gs("doc"), gs(".c[1]"), 2).get();
     * assert res.equals(gs("12")); // Multiplies the second element in the `c` array by 2.
     * }</pre>
     */
    public static CompletableFuture<GlideString> nummultby(
        @NonNull BaseClient client,
        @NonNull GlideString key,
        @NonNull GlideString path,
        long number) {
        return executeCommand(
            client,
            new GlideString[] {gs(JSON_NUMMULTBY), key, path, gs(Long.toString(number))}
        );
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     */
    private static <T> CompletableFuture<T> executeCommand(BaseClient client, String[] args) {
        return executeCommand(client, args, false);
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     * @param returnsMap - true if command returns a map
     */
    @SuppressWarnings({"unchecked", "SameParameterValue"})
    private static <T> CompletableFuture<T> executeCommand(
            BaseClient client, String[] args, boolean returnsMap) {
        if (client instanceof GlideClient) {
            return ((GlideClient) client).customCommand(args).thenApply(r -> (T) r);
        } else if (client instanceof GlideClusterClient) {
            return ((GlideClusterClient) client)
                    .customCommand(args)
                    .thenApply(returnsMap ? ClusterValue::getMultiValue : ClusterValue::getSingleValue)
                    .thenApply(r -> (T) r);
        }
        throw new IllegalArgumentException(
                "Unknown type of client, should be either `GlideClient` or `GlideClusterClient`");
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     */
    private static <T> CompletableFuture<T> executeCommand(BaseClient client, GlideString[] args) {
        return executeCommand(client, args, false);
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     * @param returnsMap - true if command returns a map
     */
    @SuppressWarnings({"unchecked", "SameParameterValue"})
    private static <T> CompletableFuture<T> executeCommand(
            BaseClient client, GlideString[] args, boolean returnsMap) {
        if (client instanceof GlideClient) {
            return ((GlideClient) client).customCommand(args).thenApply(r -> (T) r);
        } else if (client instanceof GlideClusterClient) {
            return ((GlideClusterClient) client)
                    .customCommand(args)
                    .thenApply(returnsMap ? ClusterValue::getMultiValue : ClusterValue::getSingleValue)
                    .thenApply(r -> (T) r);
        }
        throw new IllegalArgumentException(
                "Unknown type of client, should be either `GlideClient` or `GlideClusterClient`");
    }
}
