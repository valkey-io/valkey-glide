/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;
import static glide.utils.ArrayTransformUtils.castArray;
import static glide.utils.ArrayTransformUtils.concatenateArrays;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.json.JsonArrindexOptions;
import glide.api.models.commands.json.JsonGetOptions;
import glide.api.models.commands.json.JsonGetOptionsBinary;
import glide.utils.ArgsBuilder;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/** Module for JSON commands. */
public class Json {

    private static final String JSON_PREFIX = "JSON.";
    private static final String JSON_SET = JSON_PREFIX + "SET";
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
     * String value = Json.set(client, "doc", ".", "{\"a\": 1.0, \"b\": 2}").get();
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
     * String value = Json.set(client, gs("doc"), gs("."), gs("{\"a\": 1.0, \"b\": 2}")).get();
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
     * String value = Json.set(client, "doc", ".", "{\"a\": 1.0, \"b\": 2}", ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get();
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
     * String value = Json.set(client, gs("doc"), gs("."), gs("{\"a\": 1.0, \"b\": 2}"), ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get();
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
     * assert value.equals("{\"a\": 1.0, \"b\": 2}");
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
     * assert value.equals(gs("{\"a\": 1.0, \"b\": 2}"));
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
     * @example
     *     <pre>{@code
     * String value = Json.get(client, "doc", new String[] {"$"}).get();
     * assert value.equals("{\"a\": 1.0, \"b\": 2}");
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
     * @example
     *     <pre>{@code
     * GlideString value = Json.get(client, gs("doc"), new GlideString[] {gs("$")}).get();
     * assert value.equals(gs("{\"a\": 1.0, \"b\": 2}"));
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
     * Retrieves the JSON values at the specified <code>path</code> stored at multiple <code>keys
     * </code>.
     *
     * @apiNote When in cluster mode, if keys in <code>keys</code> map to different hash slots, the
     *     command will be split across these slots and executed separately for each. This means the
     *     command is atomic only at the slot level. If one or more slot-specific requests fail, the
     *     entire call will return the first encountered error, even though some requests may have
     *     succeeded while others did not. If this behavior impacts your application logic, consider
     *     splitting the request into sub-requests per slot to ensure atomicity.
     * @param client The client to execute the command.
     * @param keys The keys of the JSON documents.
     * @param path The path within the JSON documents.
     * @return An array with requested values for each key.
     *     <ul>
     *       <li>For JSONPath (path starts with <code>$</code>): Returns a stringified JSON list
     *           replies for every possible path, or a string representation of an empty array, if
     *           path doesn't exist.
     *       <li>For legacy path (path doesn't start with <code>$</code>): Returns a string
     *           representation of the value in <code>path</code>. If <code>path</code> doesn't exist,
     *           the corresponding array element will be <code>null</code>.
     *     </ul>
     *     If a <code>key</code> doesn't exist, the corresponding array element will be <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc1", "$", "{\"a\": 1, \"b\": [\"one\", \"two\"]}").get();
     * Json.set(client, "doc2", "$", "{\"a\": 1, \"c\": false}").get();
     * var res = Json.mget(client, new String[] { "doc1", "doc2", "non_existing" }, "$.c").get();
     * assert Arrays.equals(res, new String[] { "[]", "[false]", null });
     * }</pre>
     */
    public static CompletableFuture<String[]> mget(
            @NonNull BaseClient client, @NonNull String[] keys, @NonNull String path) {
        return Json.<Object[]>executeCommand(
                        client, concatenateArrays(new String[] {JSON_MGET}, keys, new String[] {path}))
                .thenApply(res -> castArray(res, String.class));
    }

    /**
     * Retrieves the JSON values at the specified <code>path</code> stored at multiple <code>keys
     * </code>.
     *
     * @apiNote When in cluster mode, if keys in <code>keys</code> map to different hash slots, the
     *     command will be split across these slots and executed separately for each. This means the
     *     command is atomic only at the slot level. If one or more slot-specific requests fail, the
     *     entire call will return the first encountered error, even though some requests may have
     *     succeeded while others did not. If this behavior impacts your application logic, consider
     *     splitting the request into sub-requests per slot to ensure atomicity.
     * @param client The client to execute the command.
     * @param keys The keys of the JSON documents.
     * @param path The path within the JSON documents.
     * @return An array with requested values for each key.
     *     <ul>
     *       <li>For JSONPath (path starts with <code>$</code>): Returns a stringified JSON list
     *           replies for every possible path, or a string representation of an empty array, if
     *           path doesn't exist.
     *       <li>For legacy path (path doesn't start with <code>$</code>): Returns a string
     *           representation of the value in <code>path</code>. If <code>path</code> doesn't exist,
     *           the corresponding array element will be <code>null</code>.
     *     </ul>
     *     If a <code>key</code> doesn't exist, the corresponding array element will be <code>null
     *     </code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc1", "$", "{\"a\": 1, \"b\": [\"one\", \"two\"]}").get();
     * Json.set(client, "doc2", "$", "{\"a\": 1, \"c\": false}").get();
     * var res = Json.mget(client, new GlideString[] { gs("doc1"), gs("doc2"), gs("doc3") }, gs("$.c")).get();
     * assert Arrays.equals(res, new GlideString[] { gs("[]"), gs("[false]"), null });
     * }</pre>
     */
    public static CompletableFuture<GlideString[]> mget(
            @NonNull BaseClient client, @NonNull GlideString[] keys, @NonNull GlideString path) {
        return Json.<Object[]>executeCommand(
                        client,
                        concatenateArrays(new GlideString[] {gs(JSON_MGET)}, keys, new GlideString[] {path}))
                .thenApply(res -> castArray(res, GlideString.class));
    }

    /**
     * Appends one or more <code>values</code> to the JSON array at the specified <code>path</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the <code>path</code> within the JSON document where the <code>values
     *     </code> will be appended.
     * @param values The JSON values to be appended to the array.<br>
     *     JSON string values must be wrapped with quotes. For example, to append <code>"foo"</code>,
     *     pass <code>"\"foo\""</code>.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a list of integers for every possible path, indicating the new length of the
     *           array after appending <code>values</code>, or <code>null</code> for JSON values
     *           matching the path that are not an array. If <code>path</code> does not exist, an
     *           empty array will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the new length of the array after appending <code>values</code> to the array
     *           at <code>path</code>. If multiple paths are matched, returns the last updated array.
     *           If the JSON value at <code>path</code> is not a array or if <code>path</code> doesn't
     *           exist, an error is raised. If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1, \"b\": [\"one\", \"two\"]}").get();
     * var res = Json.arrappend(client, "doc", "$.b", new String[] {"\"three\""}).get();
     * assert Arrays.equals((Object[]) res, new int[] {3}); // New length of the array after appending
     * res = Json.arrappend(client, "doc", ".b", new String[] {"\"four\""}).get();
     * assert res.equals(4); // New length of the array after appending
     * }</pre>
     */
    public static CompletableFuture<Object> arrappend(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String path,
            @NonNull String[] values) {
        return executeCommand(
                client, concatenateArrays(new String[] {JSON_ARRAPPEND, key, path}, values));
    }

    /**
     * Appends one or more <code>values</code> to the JSON array at the specified <code>path</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the <code>path</code> within the JSON document where the <code>values
     *     </code> will be appended.
     * @param values The JSON values to be appended to the array.<br>
     *     JSON string values must be wrapped with quotes. For example, to append <code>"foo"</code>,
     *     pass <code>"\"foo\""</code>.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a list of integers for every possible path, indicating the new length of the
     *           new array after appending <code>values</code>, or <code>null</code> for JSON values
     *           matching the path that are not an array. If <code>path</code> does not exist, an
     *           empty array will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the length of the new array after appending <code>values</code> to the array
     *           at <code>path</code>. If multiple paths are matched, returns the last updated array.
     *           If the JSON value at <code>path</code> is not a array or if <code>path</code> doesn't
     *           exist, an error is raised. If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1, \"b\": [\"one\", \"two\"]}").get();
     * var res = Json.arrappend(client, gs("doc"), gs("$.b"), new GlideString[] {gs("\"three\"")}).get();
     * assert Arrays.equals((Object[]) res, new int[] {3}); // New length of the array after appending
     * res = Json.arrappend(client, gs("doc"), gs(".b"), new GlideString[] {gs("\"four\"")}).get();
     * assert res.equals(4); // New length of the array after appending
     * }</pre>
     */
    public static CompletableFuture<Object> arrappend(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            @NonNull GlideString[] values) {
        return executeCommand(
                client, new ArgsBuilder().add(gs(JSON_ARRAPPEND)).add(key).add(path).add(values).toArray());
    }

    /**
     * Inserts one or more values into the array at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>, before the given <code>index</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param index The array index before which values are inserted.
     * @param values The JSON values to be inserted into the array.<br>
     *     JSON string values must be wrapped with quotes. For example, to insert <code>"foo"</code>,
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
     * @param values The JSON values to be inserted into the array.<br>
     *     JSON string values must be wrapped with quotes. For example, to insert <code>"foo"</code>,
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
     * Searches for the first occurrence of a <code>scalar</code> JSON value in the arrays at the
     * path.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param scalar The scalar value to search for.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns an array with a
     *           list of integers for every possible path, indicating the index of the matching
     *           element. The value is <code>-1</code> if not found. If a value is not an array, its
     *           corresponding return value is <code>null</code>.
     *       <li>For legacy path (path doesn't start with <code>$</code>): Returns an integer
     *           representing the index of matching element, or <code>-1</code> if not found. If the
     *           value at the <code>path</code> is not an array, an error is raised.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, key, "$", "{\"a\": [\"value\", 3], \"b\": {\"a\": [3, [\"value\", false], 5]}}").get();
     * var result = Json.arrindex(client, key, "$..a", "3").get();
     * assert Arrays.equals((Object[]) result, new Object[] {1L, 0L});
     *
     * result = Json.arrindex(client, key, "$..a", "\"value\"").get();
     * assert Arrays.equals((Object[]) result, new Object[] {0L, -1L});
     * }</pre>
     */
    public static CompletableFuture<Object> arrindex(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String path,
            @NonNull String scalar) {
        return arrindex(client, gs(key), gs(path), gs(scalar));
    }

    /**
     * Searches for the first occurrence of a <code>scalar</code> JSON value in the arrays at the
     * path.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param scalar The scalar value to search for.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns an array with a
     *           list of integers for every possible path, indicating the index of the matching
     *           element. The value is <code>-1</code> if not found. If a value is not an array, its
     *           corresponding return value is <code>null</code>.
     *       <li>For legacy path (path doesn't start with <code>$</code>): Returns an integer
     *           representing the index of matching element, or <code>-1</code> if not found. If the
     *           value at the <code>path</code> is not an array, an error is raised.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, key, "$", "{\"a\": [\"value\", 3], \"b\": {\"a\": [3, [\"value\", false], 5]}}").get();
     * var result = Json.arrindex(client, gs(key), gs("$..a"), gs("3")).get();
     * assert Arrays.equals((Object[]) result, new Object[] {1L, 0L});
     *
     * // Searches for the first occurrence of null in the arrays
     * result = Json.arrindex(client, gs(key), gs("$..a"), gs("null")).get();
     * assert Arrays.equals((Object[]) result, new Object[] {-1L, -1L});
     * }</pre>
     */
    public static CompletableFuture<Object> arrindex(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            @NonNull GlideString scalar) {
        return executeCommand(client, new GlideString[] {gs(JSON_ARRINDEX), key, path, scalar});
    }

    /**
     * Searches for the first occurrence of a <code>scalar</code> JSON value in the arrays at the
     * path.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param scalar The scalar value to search for.
     * @param options The additional options for the command. See <code>JsonArrindexOptions</code>.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns an array with a
     *           list of integers for every possible path, indicating the index of the matching
     *           element. The value is <code>-1</code> if not found. If a value is not an array, its
     *           corresponding return value is <code>null</code>.
     *       <li>For legacy path (path doesn't start with <code>$</code>): Returns an integer
     *           representing the index of matching element, or <code>-1</code> if not found. If the
     *           value at the <code>path</code> is not an array, an error is raised.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, key, "$", "{\"a\": [\"value\", 3], \"b\": {\"a\": [3, [\"value\", false], 5]}}").get();
     * var result = Json.arrindex(client, key, ".a", "3", new JsonArrindexOptions(0L)).get();
     * assert Arrays.equals(1L, result);
     * }</pre>
     */
    public static CompletableFuture<Object> arrindex(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String path,
            @NonNull String scalar,
            @NonNull JsonArrindexOptions options) {

        return executeCommand(
                client,
                new ArgsBuilder()
                        .add(JSON_ARRINDEX)
                        .add(key)
                        .add(path)
                        .add(scalar)
                        .add(options.toArgs())
                        .toArray());
    }

    /**
     * Searches for the first occurrence of a <code>scalar</code> JSON value in the arrays at the
     * path.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param scalar The scalar value to search for.
     * @param options The additional options for the command. See <code>JsonArrindexOptions</code>.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns an array with a
     *           list of integers for every possible path, indicating the index of the matching
     *           element. The value is <code>-1</code> if not found. If a value is not an array, its
     *           corresponding return value is <code>null</code>..
     *       <li>For legacy path (path doesn't start with <code>$</code>): Returns an integer
     *           representing the index of matching element, or <code>-1</code> if not found. If the
     *           value at the <code>path</code> is not an array, an error is raised.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, key, "$", "{\"a\": [\"value\", 3], \"b\": {\"a\": [3, [\"value\", false], 5]}}").get();
     * var result = Json.arrindex(client, gs(key), gs(".a"), gs("3"), new JsonArrindexOptions(0L)).get();
     * assert Arrays.equals(1L, result);
     * }</pre>
     */
    public static CompletableFuture<Object> arrindex(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            @NonNull GlideString scalar,
            @NonNull JsonArrindexOptions options) {

        return executeCommand(
                client,
                new ArgsBuilder()
                        .add(JSON_ARRINDEX)
                        .add(key)
                        .add(path)
                        .add(scalar)
                        .add(options.toArgs())
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
     * Reports memory usage in bytes of a JSON object at the specified <code>path</code> within the
     * JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of numbers for every possible path,
     *           indicating the memory usage. If <code>path</code> does not exist, an empty array will
     *           be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the memory usage. If multiple paths are matched,
     *           returns the data of the first matching object. If <code>path</code> doesn't exist, an
     *           error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugMemory(client, "doc", "..").get();
     * assert res == 258L;
     * }</pre>
     */
    public static CompletableFuture<Object> debugMemory(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, concatenateArrays(JSON_DEBUG_MEMORY, new String[] {key, path}));
    }

    /**
     * Reports the number of fields at the specified <code>path</code> within the JSON document stored
     * at <code>key</code>.<br>
     * Each non-container JSON value counts as one field. Objects and arrays recursively count one
     * field for each of their containing JSON values. Each container value, except the root
     * container, counts as one additional field.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of numbers for every possible path,
     *           indicating the number of fields. If <code>path</code> does not exist, an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the number of fields. If multiple paths are matched,
     *           returns the data of the first matching object. If <code>path</code> doesn't exist, an
     *           error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugFields(client, "doc", "$[*]").get();
     * assert Arrays.equals((Object[]) res, new Object[] {1, 1, 1, 1, 1, 0, 0, 2, 3});
     * }</pre>
     */
    public static CompletableFuture<Object> debugFields(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, concatenateArrays(JSON_DEBUG_FIELDS, new String[] {key, path}));
    }

    /**
     * Reports memory usage in bytes of a JSON object at the specified <code>path</code> within the
     * JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of numbers for every possible path,
     *           indicating the memory usage. If <code>path</code> does not exist, an empty array will
     *           be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the memory usage. If multiple paths are matched,
     *           returns the data of the first matching object. If <code>path</code> doesn't exist, an
     *           error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugMemory(client, gs("doc"), gs("..")).get();
     * assert res == 258L;
     * }</pre>
     */
    public static CompletableFuture<Object> debugMemory(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(
                client, new ArgsBuilder().add(JSON_DEBUG_MEMORY).add(key).add(path).toArray());
    }

    /**
     * Reports the number of fields at the specified <code>path</code> within the JSON document stored
     * at <code>key</code>.<br>
     * Each non-container JSON value counts as one field. Objects and arrays recursively count one
     * field for each of their containing JSON values. Each container value, except the root
     * container, counts as one additional field.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of numbers for every possible path,
     *           indicating the number of fields. If <code>path</code> does not exist, an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the number of fields. If multiple paths are matched,
     *           returns the data of the first matching object. If <code>path</code> doesn't exist, an
     *           error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugFields(client, gs("doc"), gs("$[*]")).get();
     * assert Arrays.equals((Object[]) res, new Object[] {1, 1, 1, 1, 1, 0, 0, 2, 3});
     * }</pre>
     */
    public static CompletableFuture<Object> debugFields(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(
                client, new ArgsBuilder().add(JSON_DEBUG_FIELDS).add(key).add(path).toArray());
    }

    /**
     * Reports memory usage in bytes of a JSON object at the specified <code>path</code> within the
     * JSON document stored at <code>key</code>.<br>
     * Equivalent to {@link #debugMemory(BaseClient, String, String)} with <code>path</code> set to
     * <code>".."</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The total memory usage in bytes of the entire JSON document.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugMemory(client, "doc").get();
     * assert res == 258L;
     * }</pre>
     */
    public static CompletableFuture<Long> debugMemory(
            @NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, concatenateArrays(JSON_DEBUG_MEMORY, new String[] {key}));
    }

    /**
     * Reports the number of fields at the specified <code>path</code> within the JSON document stored
     * at <code>key</code>.<br>
     * Each non-container JSON value counts as one field. Objects and arrays recursively count one
     * field for each of their containing JSON values. Each container value, except the root
     * container, counts as one additional field.<br>
     * Equivalent to {@link #debugFields(BaseClient, String, String)} with <code>path</code> set to
     * <code>".."</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The total number of fields in the entire JSON document.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugFields(client, "doc").get();
     * assert res == 14L;
     * }</pre>
     */
    public static CompletableFuture<Long> debugFields(
            @NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, concatenateArrays(JSON_DEBUG_FIELDS, new String[] {key}));
    }

    /**
     * Reports memory usage in bytes of a JSON object at the specified <code>path</code> within the
     * JSON document stored at <code>key</code>.<br>
     * Equivalent to {@link #debugMemory(BaseClient, GlideString, GlideString)} with <code>path</code>
     * set to <code>gs("..")</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The total memory usage in bytes of the entire JSON document.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugMemory(client, gs("doc")).get();
     * assert res == 258L;
     * }</pre>
     */
    public static CompletableFuture<Long> debugMemory(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new ArgsBuilder().add(JSON_DEBUG_MEMORY).add(key).toArray());
    }

    /**
     * Reports the number of fields at the specified <code>path</code> within the JSON document stored
     * at <code>key</code>.<br>
     * Each non-container JSON value counts as one field. Objects and arrays recursively count one
     * field for each of their containing JSON values. Each container value, except the root
     * container, counts as one additional field.<br>
     * Equivalent to {@link #debugFields(BaseClient, GlideString, GlideString)} with <code>path</code>
     * set to <code>gs("..")</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The total number of fields in the entire JSON document.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2.3, \"foo\", true, null, {}, [], {\"a\":1, \"b\":2}, [1, 2, 3]]").get();
     * var res = Json.debugFields(client, gs("doc")).get();
     * assert res == 14L;
     * }</pre>
     */
    public static CompletableFuture<Long> debugFields(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new ArgsBuilder().add(JSON_DEBUG_FIELDS).add(key).toArray());
    }

    /**
     * Pops the last element from the array stored in the root of the JSON document stored at <code>
     * key</code>. Equivalent to {@link #arrpop(BaseClient, String, String)} with <code>
     * path</code> set to <code>"."</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return Returns a string representing the popped JSON value, or <code>null</code> if the array
     *     at document root is empty.<br>
     *     If the JSON value at document root is not an array or if <code>key</code> doesn't exist, an
     *     error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, true, {\"a\": 42, \"b\": 33}, \"tree\"]").get();
     * var res = Json.arrpop(client, "doc").get();
     * assert res.equals("\"tree\"");
     * res = Json.arrpop(client, "doc").get();
     * assert res.equals("{\"a\": 42, \"b\": 33}");
     * }</pre>
     */
    public static CompletableFuture<String> arrpop(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_ARRPOP, key});
    }

    /**
     * Pops the last element from the array located in the root of the JSON document stored at <code>
     * key</code>. Equivalent to {@link #arrpop(BaseClient, GlideString, GlideString)} with <code>
     * path</code> set to <code>gs(".")</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return Returns a string representing the popped JSON value, or <code>null</code> if the array
     *     at document root is empty.<br>
     *     If the JSON value at document root is not an array or if <code>key</code> doesn't exist, an
     *     error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, true, {\"a\": 42, \"b\": 33}, \"tree\"]").get();
     * var res = Json.arrpop(client, gs("doc")).get();
     * assert res.equals(gs("\"tree\""));
     * res = Json.arrpop(client, gs("doc")).get();
     * assert res.equals(gs("{\"a\": 42, \"b\": 33}"));
     * }</pre>
     */
    public static CompletableFuture<GlideString> arrpop(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_ARRPOP), key});
    }

    /**
     * Pops the last element from the array located at <code>path</code> in the JSON document stored
     * at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an array with a strings for every possible path, representing the popped JSON
     *           values, or <code>null</code> for JSON values matching the path that are not an array
     *           or an empty array.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a string representing the popped JSON value, or <code>null</code> if the
     *           array at <code>path</code> is empty. If multiple paths are matched, the value from
     *           the first matching array that is not empty is returned. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, true, {\"a\": 42, \"b\": 33}, \"tree\"]").get();
     * var res = Json.arrpop(client, "doc", "$").get();
     * assert Arrays.equals((Object[]) res, new Object[] { "\"tree\"" });
     * res = Json.arrpop(client, "doc", ".").get();
     * assert res.equals("{\"a\": 42, \"b\": 33}");
     * }</pre>
     */
    public static CompletableFuture<Object> arrpop(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_ARRPOP, key, path});
    }

    /**
     * Pops the last element from the array located at <code>path</code> in the JSON document stored
     * at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an array with a strings for every possible path, representing the popped JSON
     *           values, or <code>null</code> for JSON values matching the path that are not an array
     *           or an empty array.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a string representing the popped JSON value, or <code>null</code> if the
     *           array at <code>path</code> is empty. If multiple paths are matched, the value from
     *           the first matching array that is not empty is returned. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, true, {\"a\": 42, \"b\": 33}, \"tree\"]").get();
     * var res = Json.arrpop(client, gs("doc"), gs("$")).get();
     * assert Arrays.equals((Object[]) res, new Object[] { gs("\"tree\"") });
     * res = Json.arrpop(client, gs("doc"), gs(".")).get();
     * assert res.equals(gs("{\"a\": 42, \"b\": 33}"));
     * }</pre>
     */
    public static CompletableFuture<Object> arrpop(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_ARRPOP), key, path});
    }

    /**
     * Pops an element from the array located at <code>path</code> in the JSON document stored at
     * <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @param index The index of the element to pop. Out of boundary indexes are rounded to their
     *     respective array boundaries.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an array with a strings for every possible path, representing the popped JSON
     *           values, or <code>null</code> for JSON values matching the path that are not an array
     *           or an empty array.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a string representing the popped JSON value, or <code>null</code> if the
     *           array at <code>path</code> is empty. If multiple paths are matched, the value from
     *           the first matching array that is not empty is returned. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * String doc = "{\"a\": [1, 2, true], \"b\": {\"a\": [3, 4, [\"value\", 3, false], 5], \"c\": {\"a\": 42}}}";
     * Json.set(client, "doc", "$", doc).get();
     * var res = Json.arrpop(client, "doc", "$.a", 1).get();
     * assert res.equals("2"); // Pop second element from array at path `$.a`
     *
     * Json.set(client, "doc", "$", "[[], [\"a\"], [\"a\", \"b\", \"c\"]]").get();
     * res = Json.arrpop(client, "doc", ".", -1).get());
     * assert res.equals("[\"a\", \"b\", \"c\"]"); // Pop last elements at path `.`
     * }</pre>
     */
    public static CompletableFuture<Object> arrpop(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path, long index) {
        return executeCommand(client, new String[] {JSON_ARRPOP, key, path, Long.toString(index)});
    }

    /**
     * Pops an element from the array located at <code>path</code> in the JSON document stored at
     * <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @param index The index of the element to pop. Out of boundary indexes are rounded to their
     *     respective array boundaries.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an array with a strings for every possible path, representing the popped JSON
     *           values, or <code>null</code> for JSON values matching the path that are not an array
     *           or an empty array.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a string representing the popped JSON value, or <code>null</code> if the
     *           array at <code>path</code> is empty. If multiple paths are matched, the value from
     *           the first matching array that is not empty is returned. If <code>path</code> doesn't
     *           exist or the value at <code>path</code> is not an array, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * String doc = "{\"a\": [1, 2, true], \"b\": {\"a\": [3, 4, [\"value\", 3, false], 5], \"c\": {\"a\": 42}}}";
     * Json.set(client, "doc", "$", doc).get();
     * var res = Json.arrpop(client, gs("doc"), gs("$.a"), 1).get();
     * assert res.equals("2"); // Pop second element from array at path `$.a`
     *
     * Json.set(client, "doc", "$", "[[], [\"a\"], [\"a\", \"b\", \"c\"]]").get();
     * res = Json.arrpop(client, gs("doc"), gs("."), -1).get());
     * assert res.equals(gs("[\"a\", \"b\", \"c\"]")); // Pop last elements at path `.`
     * }</pre>
     */
    public static CompletableFuture<Object> arrpop(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path, long index) {
        return executeCommand(
                client, new GlideString[] {gs(JSON_ARRPOP), key, path, gs(Long.toString(index))});
    }

    /**
     * Trims an array at the specified <code>path</code> within the JSON document stored at <code>key
     * </code> so that it becomes a subarray [<code>start</code>, <code>end</code>], both inclusive.
     * <br>
     * If <code>start</code> < 0, it is treated as 0.<br>
     * If <code>end</code> >= size (size of the array), it is treated as size -1.<br>
     * If <code>start</code> >= size or <code>start</code> > <code>end</code>, the array is emptied
     * and 0 is return.<br>
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param start The index of the first element to keep, inclusive.
     * @param end The index of the last element to keep, inclusive.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of integers for every possible path,
     *           indicating the new length of the array, or <code>null</code> for JSON values matching
     *           the path that are not an array. If the array is empty, its corresponding return value
     *           is 0. If <code>path</code> doesn't exist, an empty array will be return. If an index
     *           argument is out of bounds, an error is raised.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the new length of the array. If the array is empty,
     *           its corresponding return value is 0. If multiple paths match, the length of the first
     *           trimmed array match is returned. If <code>path</code> doesn't exist, or the value at
     *           <code>path</code> is not an array, an error is raised. If an index argument is out of
     *           bounds, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{[], [\"a\"], [\"a\", \"b\"], [\"a\", \"b\", \"c\"]}").get();
     * var res = Json.arrtrim(client, "doc", "$[*]", 0, 1).get();
     * assert Arrays.equals((Object[]) res, new Object[] { 0, 1, 2, 2 }); // New lengths of arrays after trimming
     *
     * Json.set(client, "doc", "$", "{\"children\": [\"John\", \"Jack\", \"Tom\", \"Bob\", \"Mike\"]}").get();
     * res = Json.arrtrim(client, "doc", ".children", 0, 1).get();
     * assert res == 2; // new length after trimming
     * }</pre>
     */
    public static CompletableFuture<Object> arrtrim(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path, int start, int end) {
        return executeCommand(
                client,
                new String[] {JSON_ARRTRIM, key, path, Integer.toString(start), Integer.toString(end)});
    }

    /**
     * Trims an array at the specified <code>path</code> within the JSON document stored at <code>key
     * </code> so that it becomes a subarray [<code>start</code>, <code>end</code>], both inclusive.
     * <br>
     * If <code>start</code> < 0, it is treated as 0.<br>
     * If <code>end</code> >= size (size of the array), it is treated as size -1.<br>
     * If <code>start</code> >= size or <code>start</code> > <code>end</code>, the array is emptied
     * and 0 is return.<br>
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param start The index of the first element to keep, inclusive.
     * @param end The index of the last element to keep, inclusive.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of integers for every possible path,
     *           indicating the new length of the array, or <code>null</code> for JSON values matching
     *           the path that are not an array. If the array is empty, its corresponding return value
     *           is 0. If <code>path</code> doesn't exist, an empty array will be return. If an index
     *           argument is out of bounds, an error is raised.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an integer representing the new length of the array. If the array is empty,
     *           its corresponding return value is 0. If multiple paths match, the length of the first
     *           trimmed array match is returned. If <code>path</code> doesn't exist, or the value at
     *           <code>path</code> is not an array, an error is raised. If an index argument is out of
     *           bounds, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{[], [\"a\"], [\"a\", \"b\"], [\"a\", \"b\", \"c\"]}").get();
     * var res = Json.arrtrim(client, gs("doc"), gs("$[*]"), 0, 1).get();
     * assert Arrays.equals((Object[]) res, new Object[] { 0, 1, 2, 2 }); // New lengths of arrays after trimming
     *
     * Json.set(client, "doc", "$", "{\"children\": [\"John\", \"Jack\", \"Tom\", \"Bob\", \"Mike\"]}").get();
     * res = Json.arrtrim(client, gs("doc"), gs(".children"), 0, 1).get();
     * assert res == 2; // new length after trimming
     * }</pre>
     */
    public static CompletableFuture<Object> arrtrim(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            int start,
            int end) {
        return executeCommand(
                client,
                new ArgsBuilder()
                        .add(gs(JSON_ARRTRIM))
                        .add(key)
                        .add(path)
                        .add(Integer.toString(start))
                        .add(Integer.toString(end))
                        .toArray());
    }

    /**
     * Increments or decrements the JSON value(s) at the specified <code>path</code> by <code>number
     * </code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to increment or decrement by.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a string representation of an array of strings, indicating the new values
     *           after incrementing for each matched <code>path</code>.<br>
     *           If a value is not a number, its corresponding return value will be <code>null</code>.
     *           <br>
     *           If <code>path</code> doesn't exist, a byte string representation of an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a string representation of the resulting value after the increment or
     *           decrement.<br>
     *           If multiple paths match, the result of the last updated value is returned.<br>
     *           If the value at the <code>path</code> is not a number or <code>path</code> doesn't
     *           exist, an error is raised.
     *     </ul>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.numincrby(client, "doc", "$.d[*]", 10.0).get();
     * assert res.equals("[11,12,13]"); // Increment each element in `d` array by 10.
     *
     * res = Json.numincrby(client, "doc", ".c[1]", 10.0).get();
     * assert res.equals("12"); // Increment the second element in the `c` array by 10.
     * }</pre>
     */
    public static CompletableFuture<String> numincrby(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path, Number number) {
        return executeCommand(client, new String[] {JSON_NUMINCRBY, key, path, number.toString()});
    }

    /**
     * Increments or decrements the JSON value(s) at the specified <code>path</code> by <code>number
     * </code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to increment or decrement by.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a <code>GlideString</code> representation of an array of strings, indicating
     *           the new values after incrementing for each matched <code>path</code>.<br>
     *           If a value is not a number, its corresponding return value will be <code>null</code>.
     *           <br>
     *           If <code>path</code> doesn't exist, a byte string representation of an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a <code>GlideString</code> representation of the resulting value after the
     *           increment or decrement.<br>
     *           If multiple paths match, the result of the last updated value is returned.<br>
     *           If the value at the <code>path</code> is not a number or <code>path</code> doesn't
     *           exist, an error is raised.
     *     </ul>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.numincrby(client, gs("doc"), gs("$.d[*]"), 10.0).get();
     * assert res.equals(gs("[11,12,13]")); // Increment each element in `d` array by 10.
     *
     * res = Json.numincrby(client, gs("doc"), gs(".c[1]"), 10.0).get();
     * assert res.equals(gs("12")); // Increment the second element in the `c` array by 10.
     * }</pre>
     */
    public static CompletableFuture<GlideString> numincrby(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            Number number) {
        return executeCommand(
                client, new GlideString[] {gs(JSON_NUMINCRBY), key, path, gs(number.toString())});
    }

    /**
     * Multiplies the JSON value(s) at the specified <code>path</code> by <code>number</code> within
     * the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to multiply by.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a string representation of an array of strings, indicating the new values
     *           after multiplication for each matched <code>path</code>.<br>
     *           If a value is not a number, its corresponding return value will be <code>null</code>.
     *           <br>
     *           If <code>path</code> doesn't exist, a byte string representation of an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a string representation of the resulting value after multiplication.<br>
     *           If multiple paths match, the result of the last updated value is returned.<br>
     *           If the value at the <code>path</code> is not a number or <code>path</code> doesn't
     *           exist, an error is raised.
     *     </ul>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.nummultby(client, "doc", "$.d[*]", 2.0).get();
     * assert res.equals("[2,4,6]"); // Multiplies each element in the `d` array by 2.
     *
     * res = Json.nummultby(client, "doc", ".c[1]", 2.0).get();
     * assert res.equals("12"); // Multiplies the second element in the `c` array by 2.
     * }</pre>
     */
    public static CompletableFuture<String> nummultby(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path, Number number) {
        return executeCommand(client, new String[] {JSON_NUMMULTBY, key, path, number.toString()});
    }

    /**
     * Multiplies the JSON value(s) at the specified <code>path</code> by <code>number</code> within
     * the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @param number The number to multiply by.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a <code>GlideString</code> representation of an array of strings, indicating
     *           the new values after multiplication for each matched <code>path</code>.<br>
     *           If a value is not a number, its corresponding return value will be <code>null</code>.
     *           <br>
     *           If <code>path</code> doesn't exist, a byte string representation of an empty array
     *           will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns a <code>GlideString</code> representation of the resulting value after
     *           multiplication.<br>
     *           If multiple paths match, the result of the last updated value is returned.<br>
     *           If the value at the <code>path</code> is not a number or <code>path</code> doesn't
     *           exist, an error is raised.
     *     </ul>
     *     If <code>key</code> does not exist, an error is raised.<br>
     *     If the result is out of the range of 64-bit IEEE double, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"c\": [1, 2], \"d\": [1, 2, 3]}").get();
     * var res = Json.nummultby(client, gs("doc"), gs("$.d[*]"), 2.0).get();
     * assert res.equals(gs("[2,4,6]")); // Multiplies each element in the `d` array by 2.
     *
     * res = Json.nummultby(client, gs("doc"), gs(".c[1]"), 2.0).get();
     * assert res.equals(gs("12")); // Multiplies the second element in the `c` array by 2.
     * }</pre>
     */
    public static CompletableFuture<GlideString> nummultby(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            Number number) {
        return executeCommand(
                client, new GlideString[] {gs(JSON_NUMMULTBY), key, path, gs(number.toString())});
    }

    /**
     * Retrieves the number of key-value pairs in the object values at the specified <code>path</code>
     * within the JSON document stored at <code>key</code>.<br>
     * Equivalent to {@link #objlen(BaseClient, String, String)} with <code>path</code> set to <code>
     * "."</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The object length stored at the root of the document. If document root is not an
     *     object, an error is raised.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objlen(client, "doc").get();
     * assert res == 2; // the size of object matching the path `.`, which has 2 keys: 'a' and 'b'.
     * }</pre>
     */
    public static CompletableFuture<Long> objlen(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_OBJLEN, key});
    }

    /**
     * Retrieves the number of key-value pairs in the object values at the specified <code>path</code>
     * within the JSON document stored at <code>key</code>.<br>
     * Equivalent to {@link #objlen(BaseClient, GlideString, GlideString)} with <code>path</code> set
     * to <code>gs(".")</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The object length stored at the root of the document. If document root is not an
     *     object, an error is raised.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objlen(client, gs("doc"), gs(".")).get();
     * assert res == 2; // the size of object matching the path `.`, which has 2 keys: 'a' and 'b'.
     * }</pre>
     */
    public static CompletableFuture<Long> objlen(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_OBJLEN), key});
    }

    /**
     * Retrieves the number of key-value pairs in the object values at the specified <code>path</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of long integers for every possible
     *           path, indicating the number of key-value pairs for each matching object, or <code>
     *           null
     *           </code> for JSON values matching the path that are not an object. If <code>path
     *           </code> does not exist, an empty array will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the number of key-value pairs for the object value matching the path. If
     *           multiple paths are matched, returns the length of the first matching object. If
     *           <code>path</code> doesn't exist or the value at <code>path</code> is not an array, an
     *           error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objlen(client, "doc", ".").get(); // legacy path - command returns first value as `Long`
     * assert res == 2L; // the size of object matching the path `.`, which has 2 keys: 'a' and 'b'.
     *
     * res = Json.objlen(client, "doc", "$.b").get(); // JSONPath - command returns an array
     * assert Arrays.equals((Object[]) res, new Object[] { 3L }); // the length of the objects at path `$.b`
     * }</pre>
     */
    public static CompletableFuture<Object> objlen(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_OBJLEN, key, path});
    }

    /**
     * Retrieves the number of key-value pairs in the object values at the specified <code>path</code>
     * within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[]</code> with a list of long integers for every possible
     *           path, indicating the number of key-value pairs for each matching object, or <code>
     *           null
     *           </code> for JSON values matching the path that are not an object. If <code>path
     *           </code> does not exist, an empty array will be returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the number of key-value pairs for the object value matching the path. If
     *           multiple paths are matched, returns the length of the first matching object. If
     *           <code>path</code> doesn't exist or the value at <code>path</code> is not an array, an
     *           error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objlen(client, gs("doc"), gs(".")).get(); // legacy path - command returns first value as `Long`
     * assert res == 2L; // the size of object matching the path `.`, which has 2 keys: 'a' and 'b'.
     *
     * res = Json.objlen(client, gs("doc"), gs("$.b")).get(); // JSONPath - command returns an array
     * assert Arrays.equals((Object[]) res, new Object[] { 3L }); // the length of the objects at path `$.b`
     * }</pre>
     */
    public static CompletableFuture<Object> objlen(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_OBJLEN), key, path});
    }

    /**
     * Retrieves the key names in the object values at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>.<br>
     * Equivalent to {@link #objkeys(BaseClient, String, String)} with <code>path</code> set to <code>
     * "."</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The object length stored at the root of the document. If document root is not an
     *     object, an error is raised.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objkeys(client, "doc").get();
     * assert Arrays.equals((Object[]) res, new Object[] { "a", "b" }); // the keys of the object matching the path `.`, which has 2 keys: 'a' and 'b'.
     * }</pre>
     */
    public static CompletableFuture<Object[]> objkeys(
            @NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_OBJKEYS, key});
    }

    /**
     * Retrieves the key names in the object values at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>.<br>
     * Equivalent to {@link #objkeys(BaseClient, GlideString, GlideString)} with <code>path</code> set
     * to <code>gs(".")</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return The object length stored at the root of the document. If document root is not an
     *     object, an error is raised.<br>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objkeys(client, gs("doc"), gs(".")).get();
     * assert Arrays.equals((Object[]) res, new Object[] { gs("a"), gs("b") }); // the keys of the object matching the path `.`, which has 2 keys: 'a' and 'b'.
     * }</pre>
     */
    public static CompletableFuture<Object[]> objkeys(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_OBJKEYS), key});
    }

    /**
     * Retrieves the key names in the object values at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[][]</code> with each nested array containing key names for
     *           each matching object for every possible path, indicating the list of object keys for
     *           each matching object, or <code>null</code> for JSON values matching the path that are
     *           not an object. If <code>path</code> does not exist, an empty sub-array will be
     *           returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an array of object keys for the object value matching the path. If multiple
     *           paths are matched, returns the length of the first matching object. If <code>path
     *           </code> doesn't exist or the value at <code>path</code> is not an array, an error is
     *           raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objkeys(client, "doc", ".").get(); // legacy path - command returns array for first matched object
     * assert Arrays.equals((Object[]) res, new Object[] { "a", "b" }); // key names for the object matching the path `.` as it is the only match.
     *
     * res = Json.objkeys(client, "doc", "$.b").get(); // JSONPath - command returns an array for each matched object
     * assert Arrays.equals((Object[]) res, new Object[][] { { "a", "b", "c" } }); // key names as a nested list for objects matching the JSONPath `$.b`.
     * }</pre>
     */
    public static CompletableFuture<Object[]> objkeys(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_OBJKEYS, key, path});
    }

    /**
     * Retrieves the key names in the object values at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns an <code>Object[][]</code> with each nested array containing key names for
     *           each matching object for every possible path, indicating the list of object keys for
     *           each matching object, or <code>null</code> for JSON values matching the path that are
     *           not an object. If <code>path</code> does not exist, an empty sub-array will be
     *           returned.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns an array of object keys for the object value matching the path. If multiple
     *           paths are matched, returns the length of the first matching object. If <code>path
     *           </code> doesn't exist or the value at <code>path</code> is not an array, an error is
     *           raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1.0, \"b\": {\"a\": {\"x\": 1, \"y\": 2}, \"b\": 2.5, \"c\": true}}").get();
     * var res = Json.objkeys(client, gs("doc"), gs(".")).get(); // legacy path - command returns array for first matched object
     * assert Arrays.equals((Object[]) res, new Object[] { "a", "b" }); // key names for the object matching the path `.` as it is the only match.
     *
     * res = Json.objkeys(client, gs("doc"), gs("$.b")).get(); // JSONPath - command returns an array for each matched object
     * assert Arrays.equals((Object[]) res, new Object[][] { { "a", "b", "c" } }); // key names as a nested list for objects matching the JSONPath `$.b`.
     * }</pre>
     */
    public static CompletableFuture<Object[]> objkeys(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_OBJKEYS), key, path});
    }

    /**
     * Deletes the JSON document stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return The number of elements deleted. 0 if the key does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.del(client, "doc").get();
     * assert result == 1L;
     * }</pre>
     */
    public static CompletableFuture<Long> del(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_DEL, key});
    }

    /**
     * Deletes the JSON document stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return The number of elements deleted. 0 if the key does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.del(client, gs("doc")).get();
     * assert result == 1L;
     * }</pre>
     */
    public static CompletableFuture<Long> del(@NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_DEL), key});
    }

    /**
     * Deletes the JSON value at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be deleted.
     * @return The number of elements deleted. 0 if the key does not exist, or if the JSON path is
     *     invalid or does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.del(client, "doc", "$..a").get();
     * assert result == 2L;
     * }</pre>
     */
    public static CompletableFuture<Long> del(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_DEL, key, path});
    }

    /**
     * Deletes the JSON value at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be deleted.
     * @return The number of elements deleted. 0 if the key does not exist, or if the JSON path is
     *     invalid or does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.del(client, gs("doc"), gs("$..a")).get();
     * assert result == 2L;
     * }</pre>
     */
    public static CompletableFuture<Long> del(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_DEL), key, path});
    }

    /**
     * Deletes the JSON document stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return The number of elements deleted. 0 if the key does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.forget(client, "doc").get();
     * assert result == 1L;
     * }</pre>
     */
    public static CompletableFuture<Long> forget(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_FORGET, key});
    }

    /**
     * Deletes the JSON document stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @return The number of elements deleted. 0 if the key does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.forget(client, gs("doc")).get();
     * assert result == 1L;
     * }</pre>
     */
    public static CompletableFuture<Long> forget(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_FORGET), key});
    }

    /**
     * Deletes the JSON value at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be deleted.
     * @return The number of elements deleted. 0 if the key does not exist, or if the JSON path is
     *     invalid or does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.forget(client, "doc", "$..a").get();
     * assert result == 2L;
     * }</pre>
     */
    public static CompletableFuture<Long> forget(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_FORGET, key, path});
    }

    /**
     * Deletes the JSON value at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path Represents the path within the JSON document where the value will be deleted.
     * @return The number of elements deleted. 0 if the key does not exist, or if the JSON path is
     *     invalid or does not exist.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * Long result = Json.forget(client, gs("doc"), gs("$..a")).get();
     * assert result == 2L;
     * }</pre>
     */
    public static CompletableFuture<Long> forget(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_FORGET), key, path});
    }

    /**
     * Toggles a Boolean value stored at the root within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the toggled boolean value at the root of the document, or <code>null</code> for
     *     JSON values matching the root that are not boolean. If <code>key</code> doesn't exist,
     *     returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", true).get();
     * var res = Json.toggle(client, "doc").get();
     * assert res.equals(false);
     * res = Json.toggle(client, "doc").get();
     * assert res.equals(true);
     * }</pre>
     */
    public static CompletableFuture<Boolean> toggle(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_TOGGLE, key});
    }

    /**
     * Toggles a Boolean value stored at the root within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the toggled boolean value at the root of the document, or <code>null</code> for
     *     JSON values matching the root that are not boolean. If <code>key</code> doesn't exist,
     *     returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", true).get();
     * var res = Json.toggle(client, gs("doc")).get();
     * assert res.equals(false);
     * res = Json.toggle(client, gs("doc")).get();
     * assert res.equals(true);
     * }</pre>
     */
    public static CompletableFuture<Boolean> toggle(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new ArgsBuilder().add(gs(JSON_TOGGLE)).add(key).toArray());
    }

    /**
     * Toggles a Boolean value stored at the specified <code>path</code> within the JSON document
     * stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a <code>Boolean[]</code> with the toggled boolean value for every possible
     *           path, or <code>null</code> for JSON values matching the path that are not boolean.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the value of the toggled boolean in <code>path</code>. If <code>path</code>
     *           doesn't exist or the value at <code>path</code> isn't a boolean, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"bool\": true, \"nested\": {\"bool\": false, \"nested\": {\"bool\": 10}}}").get();
     * var res = Json.toggle(client, "doc", "$..bool").get();
     * assert Arrays.equals((Boolean[]) res, new Boolean[] {false, true, null});
     * res = Json.toggle(client, "doc", "bool").get();
     * assert res.equals(true);
     * var getResult = Json.get(client, "doc", "$").get();
     * assert getResult.equals("{\"bool\": true, \"nested\": {\"bool\": true, \"nested\": {\"bool\": 10}}}");
     * }</pre>
     */
    public static CompletableFuture<Object> toggle(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_TOGGLE, key, path});
    }

    /**
     * Toggles a Boolean value stored at the specified <code>path</code> within the JSON document
     * stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a <code>Boolean[]</code> with the toggled boolean value for every possible
     *           path, or <code>null</code> for JSON values matching the path that are not boolean.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the value of the toggled boolean in <code>path</code>. If <code>path</code>
     *           doesn't exist or the value at <code>path</code> isn't a boolean, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, returns <code>null</code>.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"bool\": true, \"nested\": {\"bool\": false, \"nested\": {\"bool\": 10}}}").get();
     * var res = Json.toggle(client, gs("doc"), gs("$..bool")).get();
     * assert Arrays.equals((Boolean[]) res, new Boolean[] {false, true, null});
     * res = Json.toggle(client, gs("doc"), gs("bool")).get();
     * assert res.equals(true);
     * var getResult = Json.get(client, "doc", "$").get();
     * assert getResult.equals("{\"bool\": true, \"nested\": {\"bool\": true, \"nested\": {\"bool\": 10}}}");
     * }</pre>
     */
    public static CompletableFuture<Object> toggle(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(
                client, new ArgsBuilder().add(gs(JSON_TOGGLE)).add(key).add(path).toArray());
    }

    /**
     * Appends the specified <code>value</code> to the string stored at the specified <code>path
     * </code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param value The value to append to the string. Must be wrapped with single quotes. For
     *     example, to append "foo", pass '"foo"'.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a list of integer replies for every possible path, indicating the length of
     *           the resulting string after appending <code>value</code>, or <code>null</code> for
     *           JSON values matching the path that are not string.<br>
     *           If <code>key</code> doesn't exist, an error is raised.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the length of the resulting string after appending <code>value</code> to the
     *           string at <code>path</code>.<br>
     *           If multiple paths match, the length of the last updated string is returned.<br>
     *           If the JSON value at <code>path</code> is not a string of if <code>path</code>
     *           doesn't exist, an error is raised.<br>
     *           If <code>key</code> doesn't exist, an error is raised.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\":\"foo\", \"nested\": {\"a\": \"hello\"}, \"nested2\": {\"a\": 31}}").get();
     * var res = Json.strappend(client, "doc", "baz", "$..a").get();
     * assert Arrays.equals((Object[]) res, new Object[] {6L, 8L, null}); // The new length of the string values at path '$..a' in the key stored at `doc` after the append operation.
     *
     * res = Json.strappend(client, "doc", '"foo"', "nested.a").get();
     * assert (Long) res == 11L; // The length of the string value after appending "foo" to the string at path 'nested.array' in the key stored at `doc`.
     *
     * var getResult = Json.get(client, "doc", "$").get();
     * assert getResult.equals("[{\"a\":\"foobaz\", \"nested\": {\"a\": \"hellobazfoo\"}, \"nested2\": {\"a\": 31}}]"); // The updated JSON value in the key stored at `doc`.
     * }</pre>
     */
    public static CompletableFuture<Object> strappend(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String value,
            @NonNull String path) {
        return executeCommand(
                client, new ArgsBuilder().add(JSON_STRAPPEND).add(key).add(path).add(value).toArray());
    }

    /**
     * Appends the specified <code>value</code> to the string stored at the specified <code>path
     * </code> within the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param value The value to append to the string. Must be wrapped with single quotes. For
     *     example, to append "foo", pass '"foo"'.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a list of integer replies for every possible path, indicating the length of
     *           the resulting string after appending <code>value</code>, or <code>null</code> for
     *           JSON values matching the path that are not string.<br>
     *           If <code>key</code> doesn't exist, an error is raised.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the length of the resulting string after appending <code>value</code> to the
     *           string at <code>path</code>.<br>
     *           If multiple paths match, the length of the last updated string is returned.<br>
     *           If the JSON value at <code>path</code> is not a string of if <code>path</code>
     *           doesn't exist, an error is raised.<br>
     *           If <code>key</code> doesn't exist, an error is raised.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\":\"foo\", \"nested\": {\"a\": \"hello\"}, \"nested2\": {\"a\": 31}}").get();
     * var res = Json.strappend(client, gs("doc"), gs("baz"), gs("$..a")).get();
     * assert Arrays.equals((Object[]) res, new Object[] {6L, 8L, null}); // The new length of the string values at path '$..a' in the key stored at `doc` after the append operation.
     *
     * res = Json.strappend(client, gs("doc"), gs("'\"foo\"'"), gs("nested.a")).get();
     * assert (Long) res == 11L; // The length of the string value after appending "foo" to the string at path 'nested.array' in the key stored at `doc`.
     *
     * var getResult = Json.get(client, gs("doc"), gs("$")).get();
     * assert getResult.equals("[{\"a\":\"foobaz\", \"nested\": {\"a\": \"hellobazfoo\"}, \"nested2\": {\"a\": 31}}]"); // The updated JSON value in the key stored at `doc`.
     * }</pre>
     */
    public static CompletableFuture<Object> strappend(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString value,
            @NonNull GlideString path) {
        return executeCommand(
                client, new ArgsBuilder().add(gs(JSON_STRAPPEND)).add(key).add(path).add(value).toArray());
    }

    /**
     * Appends the specified <code>value</code> to the string stored at the root within the JSON
     * document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param value The value to append to the string. Must be wrapped with single quotes. For
     *     example, to append "foo", pass '"foo"'.
     * @return Returns the length of the resulting string after appending <code>value</code> to the
     *     string at the root.<br>
     *     If the JSON value at root is not a string, an error is raised.<br>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "'\"foo\"'").get();
     * var res = Json.strappend(client, "doc", "'\"baz\"'").get();
     * assert res == 6L; // The length of the string value after appending "foo" to the string at root in the key stored at `doc`.
     *
     * var getResult = Json.get(client, "doc").get();
     * assert getResult.equals("\"foobaz\""); // The updated JSON value in the key stored at `doc`.
     * }</pre>
     */
    public static CompletableFuture<Long> strappend(
            @NonNull BaseClient client, @NonNull String key, @NonNull String value) {
        return executeCommand(
                client, new ArgsBuilder().add(JSON_STRAPPEND).add(key).add(value).toArray());
    }

    /**
     * Appends the specified <code>value</code> to the string stored at the root within the JSON
     * document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param value The value to append to the string. Must be wrapped with single quotes. For
     *     example, to append "foo", pass '"foo"'.
     * @return Returns the length of the resulting string after appending <code>value</code> to the
     *     string at the root.<br>
     *     If the JSON value at root is not a string, an error is raised.<br>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "'\"foo\"'").get();
     * var res = Json.strappend(client, gs("doc"), gs("'\"baz\"'")).get();
     * assert res == 6L; // The length of the string value after appending "foo" to the string at root in the key stored at `doc`.
     *
     * var getResult = Json.get(client, gs("$"), gs("doc")).get();
     * assert getResult.equals("\"foobaz\""); // The updated JSON value in the key stored at `doc`.
     * }</pre>
     */
    public static CompletableFuture<Long> strappend(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString value) {
        return executeCommand(
                client, new ArgsBuilder().add(gs(JSON_STRAPPEND)).add(key).add(value).toArray());
    }

    /**
     * Returns the length of the JSON string value stored at the specified <code>path</code> within
     * the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a list of integer replies for every possible path, indicating the length of
     *           the JSON string value, or <code>null</code> for JSON values matching the path that
     *           are not string.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the length of the JSON value at <code>path</code> or <code>null</code> if
     *           <code>key</code> doesn't exist.<br>
     *           If multiple paths match, the length of the first matched string is returned.<br>
     *           If the JSON value at <code>path</code> is not a string of if <code>path</code>
     *           doesn't exist, an error is raised. If <code>key</code> doesn't exist, <code>null
     *           </code> is returned.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\":\"foo\", \"nested\": {\"a\": \"hello\"}, \"nested2\": {\"a\": 31}}").get();
     * var res = Json.strlen(client, "doc", "$..a").get();
     * assert Arrays.equals((Object[]) res, new Object[] {3L, 5L, null}); // The length of the string values at path '$..a' in the key stored at `doc`.
     *
     * res = Json.strlen(client, "doc", "nested.a").get();
     * assert (Long) res == 5L; // The length of the JSON value at path 'nested.a' in the key stored at `doc`.
     *
     * res = Json.strlen(client, "doc", "$").get();
     * assert Arrays.equals((Object[]) res, new Object[] {null}); // Returns an array with null since the value at root path does in the JSON document stored at `doc` is not a string.
     *
     * res = Json.strlen(client, "non_existing_key", ".").get();
     * assert res == null; // `key` doesn't exist.
     * }</pre>
     */
    public static CompletableFuture<Object> strlen(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new ArgsBuilder().add(JSON_STRLEN).add(key).add(path).toArray());
    }

    /**
     * Returns the length of the JSON string value stored at the specified <code>path</code> within
     * the JSON document stored at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>):<br>
     *           Returns a list of integer replies for every possible path, indicating the length of
     *           the JSON string value, or <code>null</code> for JSON values matching the path that
     *           are not string.
     *       <li>For legacy path (<code>path</code> doesn't start with <code>$</code>):<br>
     *           Returns the length of the JSON value at <code>path</code> or <code>null</code> if
     *           <code>key</code> doesn't exist.<br>
     *           If multiple paths match, the length of the first matched string is returned.<br>
     *           If the JSON value at <code>path</code> is not a string of if <code>path</code>
     *           doesn't exist, an error is raised. If <code>key</code> doesn't exist, <code>null
     *           </code> is returned.
     *     </ul>
     *
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\":\"foo\", \"nested\": {\"a\": \"hello\"}, \"nested2\": {\"a\": 31}}").get();
     * var res = Json.strlen(client, gs("doc"), gs("$..a")).get();
     * assert Arrays.equals((Object[]) res, new Object[] {3L, 5L, null}); // The length of the string values at path '$..a' in the key stored at `doc`.
     *
     * res = Json.strlen(client, gs("doc"), gs("nested.a")).get();
     * assert (Long) res == 5L; // The length of the JSON value at path 'nested.a' in the key stored at `doc`.
     *
     * res = Json.strlen(client, gs("doc"), gs("$")).get();
     * assert Arrays.equals((Object[]) res, new Object[] {null}); // Returns an array with null since the value at root path does in the JSON document stored at `doc` is not a string.
     *
     * res = Json.strlen(client, gs("non_existing_key"), gs(".")).get();
     * assert res == null; // `key` doesn't exist.
     * }</pre>
     */
    public static CompletableFuture<Object> strlen(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(
                client, new ArgsBuilder().add(gs(JSON_STRLEN)).add(key).add(path).toArray());
    }

    /**
     * Returns the length of the JSON string value stored at the root within the JSON document stored
     * at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the length of the JSON value at the root.<br>
     *     If the JSON value is not a string, an error is raised.<br>
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "\"Hello\"").get();
     * var res = Json.strlen(client, "doc").get();
     * assert res == 5L; // The length of the JSON value at the root in the key stored at `doc`.
     *
     * res = Json.strlen(client, "non_existing_key").get();
     * assert res == null; // `key` doesn't exist.
     * }</pre>
     */
    public static CompletableFuture<Long> strlen(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new ArgsBuilder().add(JSON_STRLEN).add(key).toArray());
    }

    /**
     * Returns the length of the JSON string value stored at the root within the JSON document stored
     * at <code>key</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the length of the JSON value at the root.<br>
     *     If the JSON value is not a string, an error is raised.<br>
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "\"Hello\"").get();
     * var res = Json.strlen(client, gs("doc")).get();
     * assert res == 5L; // The length of the JSON value at the root in the key stored at `doc`.
     *
     * res = Json.strlen(client, gs("non_existing_key")).get();
     * assert res == null; // `key` doesn't exist.
     * }</pre>
     */
    public static CompletableFuture<Long> strlen(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new ArgsBuilder().add(gs(JSON_STRLEN)).add(key).toArray());
    }

    /**
     * Clears an array and an object at the root of the JSON document stored at <code>key</code>.<br>
     * Equivalent to {@link #clear(BaseClient, String, String)} with <code>path</code> set to <code>
     * "."</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return <code>1</code> if the document wasn't empty or <code>0</code> if it was.<br>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\":1, \"b\":2}").get();
     * long res = Json.clear(client, "doc").get();
     * assert res == 1;
     *
     * var doc = Json.get(client, "doc", "$").get();
     * assert doc.equals("[{}]");
     *
     * res = Json.clear(client, "doc").get();
     * assert res == 0; // the doc is already empty
     * }</pre>
     */
    public static CompletableFuture<Long> clear(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_CLEAR, key});
    }

    /**
     * Clears an array and an object at the root of the JSON document stored at <code>key</code>.<br>
     * Equivalent to {@link #clear(BaseClient, GlideString, GlideString)} with <code>path</code> set
     * to <code>"."</code>.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @return <code>1</code> if the document wasn't empty or <code>0</code> if it was.<br>
     *     If <code>key</code> doesn't exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\":1, \"b\":2}").get();
     * long res = Json.clear(client, gs("doc")).get();
     * assert res == 1;
     *
     * var doc = Json.get(client, "doc", "$").get();
     * assert doc.equals("[{}]");
     *
     * res = Json.clear(client, gs("doc")).get();
     * assert res == 0; // the doc is already empty
     * }</pre>
     */
    public static CompletableFuture<Long> clear(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_CLEAR), key});
    }

    /**
     * Clears arrays and objects at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>.<br>
     * Numeric values are set to <code>0</code>, boolean values are set to <code>false</code>, and
     * string values are converted to empty strings.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return The number of containers cleared.<br>
     *     If <code>path</code> doesn't exist, or the value at <code>path</code> is already cleared
     *     (e.g., an empty array, object, or string), 0 is returned. If <code>key</code> doesn't
     *     exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"obj\": {\"a\":1, \"b\":2}, \"arr\":[1, 2, 3], \"str\": \"foo\", \"bool\": true,
     *     \"int\": 42, \"float\": 3.14, \"nullVal\": null}").get();
     * long res = Json.clear(client, "doc", "$.*").get();
     * assert res == 6; // 6 values are cleared: "obj", "arr", "str", "bool", "int", and "float"; "nullVal" is not clearable.
     *
     * var doc = Json.get(client, "doc", "$").get();
     * assert doc.equals("[{\"obj\":{},\"arr\":[],\"str\":\"\",\"bool\":false,\"int\":0,\"float\":0.0,\"nullVal\":null}]");
     *
     * res = Json.clear(client, "doc", "$.*").get();
     * assert res == 0; // containers are already empty and nothing is cleared
     * }</pre>
     */
    public static CompletableFuture<Long> clear(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_CLEAR, key, path});
    }

    /**
     * Clears arrays and objects at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>.<br>
     * Numeric values are set to <code>0</code>, boolean values are set to <code>false</code>, and
     * string values are converted to empty strings.
     *
     * @param client The client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return The number of containers cleared.<br>
     *     If <code>path</code> doesn't exist, or the value at <code>path</code> is already cleared
     *     (e.g., an empty array, object, or string), 0 is returned. If <code>key</code> doesn't
     *     exist, an error is raised.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"obj\": {\"a\":1, \"b\":2}, \"arr\":[1, 2, 3], \"str\": \"foo\", \"bool\": true,
     *     \"int\": 42, \"float\": 3.14, \"nullVal\": null}").get();
     * long res = Json.clear(client, gs("doc"), gs("$.*")).get();
     * assert res == 6; // 6 values are cleared: "obj", "arr", "str", "bool", "int", and "float"; "nullVal" is not clearable.
     *
     * var doc = Json.get(client, "doc", "$").get();
     * assert doc.equals("[{\"obj\":{},\"arr\":[],\"str\":\"\",\"bool\":false,\"int\":0,\"float\":0.0,\"nullVal\":null}]");
     *
     * res = Json.clear(client, gs("doc"), gs("$.*")).get();
     * assert res == 0; // containers are already empty and nothing is cleared
     * }</pre>
     */
    public static CompletableFuture<Long> clear(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_CLEAR), key, path});
    }

    /**
     * Retrieves the JSON document stored at <code>key</code>. The returning result is in the Valkey or Redis OSS Serialization Protocol (RESP).
     * <ul>
     *     <li>JSON null is mapped to the RESP Null Bulk String.</li>
     *     <li>JSON Booleans are mapped to RESP Simple string.</li>
     *     <li>JSON integers are mapped to RESP Integers.</li>
     *     <li>JSON doubles are mapped to RESP Bulk Strings.</li>
     *     <li>JSON strings are mapped to RESP Bulk Strings.</li>
     *     <li>JSON arrays are represented as RESP arrays, where the first element is the simple string [, followed by the array's elements.</li>
     *     <li>JSON objects are represented as RESP object, where the first element is the simple string {, followed by key-value pairs, each of which is a RESP bulk string.</li>
     * </ul>
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the JSON document in its RESP form.
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": [1, 2, 3], \"b\": {\"b1\": 1}, \"c\": 42}");
     * Object actualResult = Json.resp(client, "doc").get();
     * Object[] expectedResult = new Object[] {
     *     "{",
     *     new Object[] {"a", new Object[] {"[", 1L, 2L, 3L}},
     *     new Object[] {"b", new Object[] {"{", new Object[] {"b1", 1L}}},
     *     new Object[] {"c", 42L}
     * };
     * assertInstanceOf(Object[].class, actualResult);
     * assertArrayEquals(expectedResult, (Object[]) actualResult);
     * }</pre>
     */
    public static CompletableFuture<Object> resp(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_RESP, key});
    }

    /**
     * Retrieves the JSON document stored at <code>key</code>. The returning result is in the Valkey or Redis OSS Serialization Protocol (RESP).
     * <ul>
     *     <li>JSON null is mapped to the RESP Null Bulk String.</li>
     *     <li>JSON Booleans are mapped to RESP Simple string.</li>
     *     <li>JSON integers are mapped to RESP Integers.</li>
     *     <li>JSON doubles are mapped to RESP Bulk Strings.</li>
     *     <li>JSON strings are mapped to RESP Bulk Strings.</li>
     *     <li>JSON arrays are represented as RESP arrays, where the first element is the simple string [, followed by the array's elements.</li>
     *     <li>JSON objects are represented as RESP object, where the first element is the simple string {, followed by key-value pairs, each of which is a RESP bulk string.</li>
     * </ul>
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the JSON document in its RESP form.
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": [1, 2, 3], \"b\": {\"b1\": 1}, \"c\": 42}");
     * Object actualResultBinary = Json.resp(client, gs("doc")).get();
     * Object[] expectedResultBinary = new Object[] {
     *     "{",
     *     new Object[] {gs("a"), new Object[] {gs("["), 1L, 2L, 3L}},
     *     new Object[] {gs("b"), new Object[] {gs("{"), new Object[] {gs("b1"), 1L}}},
     *     new Object[] {gs("c"), 42L}
     * };
     * assertInstanceOf(Object[].class, actualResultBinary);
     * assertArrayEquals(expectedResultBinary, (Object[]) actualResultBinary);
     * }</pre>
     */
    public static CompletableFuture<Object> resp(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_RESP), key});
    }

    /**
     * Retrieve the JSON value at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>. The returning result is in the Valkey or Redis OSS Serialization Protocol
     * (RESP).
     *
     * <ul>
     *   <li>JSON null is mapped to the RESP Null Bulk String.
     *   <li>JSON Booleans are mapped to RESP Simple string.
     *   <li>JSON integers are mapped to RESP Integers.
     *   <li>JSON doubles are mapped to RESP Bulk Strings.
     *   <li>JSON strings are mapped to RESP Bulk Strings.
     *   <li>JSON arrays are represented as RESP arrays, where the first element is the simple string
     *       [, followed by the array's elements.
     *   <li>JSON objects are represented as RESP object, where the first element is the simple string
     *       {, followed by key-value pairs, each of which is a RESP bulk string.
     * </ul>
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns a list of
     *           replies for every possible path, indicating the RESP form of the JSON value. If
     *           <code>path</code> doesn't exist, returns an empty list.
     *       <li>For legacy path (<code>path</code> doesn't starts with <code>$</code>): Returns a
     *           single reply for the JSON value at the specified path, in its RESP form. If multiple
     *           paths match, the value of the first JSON value match is returned. If <code>path
     *           </code> doesn't exist, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": [1, 2, 3], \"b\": {\"a\": [1, 2], \"c\": {\"a\": 42}}}");
     * Object actualResult = Json.resp(client, "doc", "$..a").get(); // JSONPath returns all possible paths
     * Object[] expectedResult = new Object[] {
     *                 new Object[] {"[", 1L, 2L, 3L},
     *                 new Object[] {"[", 1L, 2L},
     *                 42L};
     * assertArrayEquals(expectedResult, (Object[]) actualResult);
     * // legacy path only returns the first JSON value match
     * assertArrayEquals(new Object[] {"[", 1L, 2L, 3L}, (Object[]) Json.resp(client, key, "..a").get());
     * }</pre>
     */
    public static CompletableFuture<Object> resp(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {
        return executeCommand(client, new String[] {JSON_RESP, key, path});
    }

    /**
     * Retrieve the JSON value at the specified <code>path</code> within the JSON document stored at
     * <code>key</code>. The returning result is in the Valkey or Redis OSS Serialization Protocol
     * (RESP).
     *
     * <ul>
     *   <li>JSON null is mapped to the RESP Null Bulk String.
     *   <li>JSON Booleans are mapped to RESP Simple string.
     *   <li>JSON integers are mapped to RESP Integers.
     *   <li>JSON doubles are mapped to RESP Bulk Strings.
     *   <li>JSON strings are mapped to RESP Bulk Strings.
     *   <li>JSON arrays are represented as RESP arrays, where the first element is the simple string
     *       [, followed by the array's elements.
     *   <li>JSON objects are represented as RESP object, where the first element is the simple string
     *       {, followed by key-value pairs, each of which is a RESP bulk string.
     * </ul>
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @param path The path within the JSON document.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns a list of
     *           replies for every possible path, indicating the RESP form of the JSON value. If
     *           <code>path</code> doesn't exist, returns an empty list.
     *       <li>For legacy path (<code>path</code> doesn't starts with <code>$</code>): Returns a
     *           single reply for the JSON value at the specified path, in its RESP form. If multiple
     *           paths match, the value of the first JSON value match is returned. If <code>path
     *           </code> doesn't exist, an error is raised.
     *     </ul>
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", ".", "{\"a\": [1, 2, 3], \"b\": {\"a\": [1, 2], \"c\": {\"a\": 42}}}");
     * Object actualResult = Json.resp(client, gs("doc"), gs("$..a")).get(); // JSONPath returns all possible paths
     * Object[] expectedResult = new Object[] {
     *                 new Object[] {gs("["), 1L, 2L, 3L},
     *                 new Object[] {gs("["), 1L, 2L},
     *                 42L};
     * assertArrayEquals(expectedResult, (Object[]) actualResult);
     * // legacy path only returns the first JSON value match
     * assertArrayEquals(new Object[] {gs("["), 1L, 2L, 3L}, (Object[]) Json.resp(client, gs(key), gs("..a")).get());
     * }</pre>
     */
    public static CompletableFuture<Object> resp(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_RESP), key, path});
    }

    /**
     * Retrieves the type of the JSON value at the root of the JSON document stored at <code>key
     * </code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the type of the JSON value at root. If <code>key</code> doesn't exist, <code>
     *     null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, 3]");
     * assertEquals("array", Json.type(client, "doc").get());
     *
     * Json.set(client, "doc", "$", "{\"a\": 1}");
     * assertEquals("object", Json.type(client, "doc").get());
     *
     * assertNull(Json.type(client, "non_existing_key").get());
     * }</pre>
     */
    public static CompletableFuture<Object> type(@NonNull BaseClient client, @NonNull String key) {
        return executeCommand(client, new String[] {JSON_TYPE, key});
    }

    /**
     * Retrieves the type of the JSON value at the root of the JSON document stored at <code>key
     * </code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @return Returns the type of the JSON value at root. If <code>key</code> doesn't exist, <code>
     *     null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "[1, 2, 3]");
     * assertEquals(gs("array"), Json.type(client, gs("doc")).get());
     *
     * Json.set(client, "doc", "$", "{\"a\": 1}");
     * assertEquals(gs("object"), Json.type(client, gs("doc")).get());
     *
     * assertNull(Json.type(client, gs("non_existing_key")).get());
     * }</pre>
     */
    public static CompletableFuture<Object> type(
            @NonNull BaseClient client, @NonNull GlideString key) {
        return executeCommand(client, new GlideString[] {gs(JSON_TYPE), key});
    }

    /**
     * Retrieves the type of the JSON value at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @param path Represents the path within the JSON document where the type will be retrieved.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns a list of string
     *           replies for every possible path, indicating the type of the JSON value. If `path`
     *           doesn't exist, an empty array will be returned.
     *       <li>For legacy path (<code>path</code> doesn't starts with <code>$</code>): Returns the
     *           type of the JSON value at `path`. If multiple paths match, the type of the first JSON
     *           value match is returned. If `path` doesn't exist, <code>null</code> will be returned.
     *     </ul>
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * assertArrayEquals(new Object[]{"object"}, (Object[]) Json.type(client, key, "$.nested").get());
     * assertArrayEquals(new Object[]{"integer"}, (Object[]) Json.type(client, key, "$.nested.a").get());
     * assertArrayEquals(new Object[]{"integer", "object"}, (Object[]) Json.type(client, key, "$[*]").get());
     * }</pre>
     */
    public static CompletableFuture<Object> type(
            @NonNull BaseClient client, @NonNull String key, @NonNull String path) {

        return executeCommand(client, new String[] {JSON_TYPE, key, path});
    }

    /**
     * Retrieves the type of the JSON value at the specified <code>path</code> within the JSON
     * document stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The key of the JSON document.
     * @param path Represents the path within the JSON document where the type will be retrieved.
     * @return
     *     <ul>
     *       <li>For JSONPath (<code>path</code> starts with <code>$</code>): Returns a list of string
     *           replies for every possible path, indicating the type of the JSON value. If `path`
     *           doesn't exist, an empty array will be returned.
     *       <li>For legacy path (<code>path</code> doesn't starts with <code>$</code>): Returns the
     *           type of the JSON value at `path`. If multiple paths match, the type of the first JSON
     *           value match is returned. If `path` doesn't exist, <code>null</code> will be returned.
     *     </ul>
     *     If <code>key</code> doesn't exist, <code>null</code> is returned.
     * @example
     *     <pre>{@code
     * Json.set(client, "doc", "$", "{\"a\": 1, \"nested\": {\"a\": 2, \"b\": 3}}");
     * assertArrayEquals(new Object[]{gs("object")}, (Object[]) Json.type(client, gs(key), gs("$.nested")).get());
     * assertArrayEquals(new Object[]{gs("integer")}, (Object[]) Json.type(client, gs(key), gs("$.nested.a")).get());
     * assertArrayEquals(new Object[]{gs("integer"), gs("object")}, (Object[]) Json.type(client, gs(key), gs("$[*]")).get());
     * }</pre>
     */
    public static CompletableFuture<Object> type(
            @NonNull BaseClient client, @NonNull GlideString key, @NonNull GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_TYPE), key, path});
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
