/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands.servermodules;

import static glide.api.models.GlideString.gs;

import glide.api.BaseClient;
import glide.api.GlideClient;
import glide.api.GlideClusterClient;
import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.commands.ConditionalChange;
import glide.api.models.commands.json.JsonGetOptions;
import glide.utils.ArgsBuilder;
import glide.utils.ArrayTransformUtils;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

public class GlideJson {

    public static final String JSON_PREFIX = "JSON.";
    public static final String JSON_SET = JSON_PREFIX + "SET";
    public static final String JSON_GET = JSON_PREFIX + "GET";

    private GlideJson() {}

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
     * @return A simple <code>"OK"</code> response if the value is successfully set. If value isn't
     *     set because of <code>set_condition</code>, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, "doc", , ".", "{'a': 1.0, 'b': 2}").get();
     * assert value.equals("OK");
     * }</pre>
     */
    public static CompletableFuture<String> set(
            @NonNull BaseClient client,
            @NonNull String key,
            @NonNull String path,
            @NonNull String value) {
        return executeCommand(client, new String[] {JSON_SET, key, path, value})
                .thenApply(r -> (String) r);
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
     * @return A simple <code>"OK"</code> response if the value is successfully set. If value isn't
     *     set because of <code>set_condition</code>, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, gs("doc"), , gs("."), gs("{'a': 1.0, 'b': 2}")).get();
     * assert value.equals("OK");
     * }</pre>
     */
    public static CompletableFuture<String> set(
            @NonNull BaseClient client,
            @NonNull GlideString key,
            @NonNull GlideString path,
            @NonNull GlideString value) {
        return executeCommand(client, new GlideString[] {gs(JSON_SET), key, path, value})
                .thenApply(r -> (String) r);
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
     *     set because of <code>set_condition</code>, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, "doc", , ".", "{'a': 1.0, 'b': 2}", ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get();
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
                        client, new String[] {JSON_SET, key, path, value, setCondition.getValkeyApi()})
                .thenApply(r -> (String) r);
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
     *     set because of <code>set_condition</code>, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, gs("doc"), , gs("."), gs("{'a': 1.0, 'b': 2}"), ConditionalChange.ONLY_IF_DOES_NOT_EXIST).get();
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
                        new GlideString[] {gs(JSON_SET), key, path, value, gs(setCondition.getValkeyApi())})
                .thenApply(r -> (String) r);
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @return For JSONPath (path starts with <code>$</code>): Returns a stringifies JSON list of
     *     bytes replies for every possible path, or a byte string representation of an empty array,
     *     if path doesn't exist. For legacy path (path doesn't start with <code>$</code>): Returns a
     *     byte string representation of the value in <code>path</code>. If <code>path</code> doesn't
     *     exist, an error is raised. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, "doc", "$").get();
     * assert value.equals("{'a': 1.0, 'b': 2}");
     * }</pre>
     */
    public static CompletableFuture<Object> get(BaseClient client, String key, String path) {
        return executeCommand(client, new String[] {JSON_GET, key, path});
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @return For JSONPath (path starts with <code>$</code>): Returns a stringifies JSON list of
     *     bytes replies for every possible path, or a byte string representation of an empty array,
     *     if path doesn't exist. For legacy path (path doesn't start with <code>$</code>): Returns a
     *     byte string representation of the value in <code>path</code>. If <code>path</code> doesn't
     *     exist, an error is raised. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * GlideString value = client.Json.set(client, gs("doc"), gs("$")).get();
     * assert value.equals(gs("{'a': 1.0, 'b': 2}"));
     * }</pre>
     */
    public static CompletableFuture<Object> get(
            BaseClient client, GlideString key, GlideString path) {
        return executeCommand(client, new GlideString[] {gs(JSON_GET), key, path});
    }

    /**
     * Retrieves the JSON value at the specified <code>paths</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @return Returns a stringifies JSON object in bytes, in which each path is a key, and it's
     *     corresponding value, is the value as if the path was executed in the command as a single
     *     path. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, "doc", new String[] {"$.a", "$.b"}).get();
     * assert value.equals("{\"$.a\": [1.0], \"$.b\": [2]}");
     * }</pre>
     */
    public static CompletableFuture<Object> get(BaseClient client, String key, String[] paths) {
        return executeCommand(
                client, ArrayTransformUtils.concatenateArrays(new String[] {JSON_GET, key}, paths));
    }

    /**
     * Retrieves the JSON value at the specified <code>paths</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @return Returns a stringifies JSON object in bytes, in which each path is a key, and it's
     *     corresponding value, is the value as if the path was executed in the command as a single
     *     path. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * GlideString value = client.Json.set(client, gs("doc"), new GlideString[] {gs("$.a"), gs("$.b")}).get();
     * assert value.equals(gs("{\"$.a\": [1.0], \"$.b\": [2]}"));
     * }</pre>
     */
    public static CompletableFuture<Object> get(
            BaseClient client, GlideString key, GlideString[] paths) {
        return executeCommand(
                client,
                ArrayTransformUtils.concatenateArrays(new GlideString[] {gs(JSON_GET), key}, paths));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return For JSONPath (path starts with <code>$</code>): Returns a stringifies JSON list of
     *     bytes replies for every possible path, or a byte string representation of an empty array,
     *     if path doesn't exist. For legacy path (path doesn't start with <code>$</code>): Returns a
     *     byte string representation of the value in <code>path</code>. If <code>path</code> doesn't
     *     exist, an error is raised. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, "doc", "$",
     *                  JsonGetOptions.builder()
     *                      .indent("  ")
     *                      .space(" ")
     *                      .newline("\n")
     *                      .build()
     *                  ).get();
     * assert value.equals("{\n \"a\": \n  1.0\n ,\n \"b\": \n  2\n }");
     * }</pre>
     */
    public static CompletableFuture<Object> get(
            BaseClient client, String key, String path, JsonGetOptions options) {
        return executeCommand(
                client,
                ArrayTransformUtils.concatenateArrays(
                        new String[] {JSON_GET, key}, options.toArgs(), new String[] {path}));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param path The path within the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return For JSONPath (path starts with <code>$</code>): Returns a stringifies JSON list of
     *     bytes replies for every possible path, or a byte string representation of an empty array,
     *     if path doesn't exist. For legacy path (path doesn't start with <code>$</code>): Returns a
     *     byte string representation of the value in <code>path</code>. If <code>path</code> doesn't
     *     exist, an error is raised. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * GlideString value = client.Json.set(client, gs("doc"), gs("$"),
     *                  JsonGetOptions.builder()
     *                      .indent("  ")
     *                      .space(" ")
     *                      .newline("\n")
     *                      .build()
     *                  ).get();
     * assert value.equals(gs("{\n \"a\": \n  1.0\n ,\n \"b\": \n  2\n }"));
     * }</pre>
     */
    public static CompletableFuture<Object> get(
            BaseClient client, GlideString key, GlideString path, JsonGetOptions options) {
        return executeCommand(
                client,
                new ArgsBuilder().add(gs(JSON_GET)).add(key).add(options.toArgs()).add(path).toArray());
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return Returns a stringifies JSON object in bytes, in which each path is a key, and it's
     *     corresponding value, is the value as if the path was executed in the command as a single
     *     path. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * String value = client.Json.set(client, "doc", new String[] {"$.a", "$.b"},
     *                  JsonGetOptions.builder()
     *                      .indent("  ")
     *                      .space(" ")
     *                      .newline("\n")
     *                      .build()
     *                  ).get();
     * assert value.equals("{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}");
     * }</pre>
     */
    public static CompletableFuture<Object> get(
            BaseClient client, String key, String[] paths, JsonGetOptions options) {
        return executeCommand(
                client,
                ArrayTransformUtils.concatenateArrays(
                        new String[] {JSON_GET, key}, options.toArgs(), paths));
    }

    /**
     * Retrieves the JSON value at the specified <code>path</code> stored at <code>key</code>.
     *
     * @param client The Valkey GLIDE client to execute the command.
     * @param key The <code>key</code> of the JSON document.
     * @param paths List of paths within the JSON document.
     * @param options Options for formatting the byte representation of the JSON data. See <code>
     *     JsonGetOptions</code>.
     * @return Returns a stringifies JSON object in bytes, in which each path is a key, and it's
     *     corresponding value, is the value as if the path was executed in the command as a single
     *     path. If <code>key</code> doesn't exist, returns null.
     * @example
     *     <pre>{@code
     * GlideString value = client.Json.set(client, gs("doc"), new GlideString[] {gs("$.a"), gs("$.b")},
     *                  JsonGetOptions.builder()
     *                      .indent("  ")
     *                      .space(" ")
     *                      .newline("\n")
     *                      .build()
     *                  ).get();
     * assert value.equals(gs("{\n \"$.a\": [\n  1.0\n ],\n \"$.b\": [\n  2\n ]\n}"));
     * }</pre>
     */
    public static CompletableFuture<Object> get(
            BaseClient client, GlideString key, GlideString[] paths, JsonGetOptions options) {
        return executeCommand(
                client,
                new ArgsBuilder().add(gs(JSON_GET)).add(key).add(options.toArgs()).add(paths).toArray());
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     */
    private static CompletableFuture<Object> executeCommand(BaseClient client, String[] args) {
        return executeCommand(client, args, false);
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     * @param returnsMap - true if command returns a map
     */
    private static CompletableFuture<Object> executeCommand(
            BaseClient client, String[] args, boolean returnsMap) {
        if (client instanceof GlideClient) {
            return ((GlideClient) client).customCommand(args);
        } else if (client instanceof GlideClusterClient) {
            return ((GlideClusterClient) client)
                    .customCommand(args)
                    .thenApply(returnsMap ? ClusterValue::getMultiValue : ClusterValue::getSingleValue);
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
    private static CompletableFuture<Object> executeCommand(BaseClient client, GlideString[] args) {
        return executeCommand(client, args, false);
    }

    /**
     * A wrapper for custom command API.
     *
     * @param client The client to execute the command.
     * @param args The command line.
     * @param returnsMap - true if command returns a map
     */
    private static CompletableFuture<Object> executeCommand(
            BaseClient client, GlideString[] args, boolean returnsMap) {
        if (client instanceof GlideClient) {
            return ((GlideClient) client).customCommand(args);
        } else if (client instanceof GlideClusterClient) {
            return ((GlideClusterClient) client)
                    .customCommand(args)
                    .thenApply(returnsMap ? ClusterValue::getMultiValue : ClusterValue::getSingleValue);
        }
        throw new IllegalArgumentException(
                "Unknown type of client, should be either `GlideClient` or `GlideClusterClient`");
    }
}
