/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.ScriptArgOptions;
import glide.api.models.commands.ScriptArgOptionsGlideString;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.configuration.ReadFrom;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Scripting and Function" group for a cluster client.
 *
 * @see <a href="https://valkey.io/commands/?group=scripting">Scripting and Function Commands</a>
 */
public interface ScriptingAndFunctionsClusterCommands {

    /**
     * Loads a library to Valkey.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-load/">valkey.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * String response = client.functionLoad(code, true).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode, boolean replace);

    /**
     * Loads a library to Valkey.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-load/">valkey.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * GlideString code = gs("#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)");
     * GlideString response = client.functionLoad(code, true).get();
     * assert response.equals(gs("mylib"));
     * }</pre>
     */
    CompletableFuture<GlideString> functionLoad(GlideString libraryCode, boolean replace);

    /**
     * Loads a library to Valkey.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-load/">valkey.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.functionLoad(code, true, route).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode, boolean replace, Route route);

    /**
     * Loads a library to Valkey.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-load/">valkey.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param replace Whether the given library should overwrite a library with the same name if it
     *     already exists.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * GlideString code = gs("#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)");
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * GlideString response = client.functionLoad(code, true, route).get();
     * assert response.equals(gs("mylib"));
     * }</pre>
     */
    CompletableFuture<GlideString> functionLoad(
            GlideString libraryCode, boolean replace, Route route);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList(true).get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(boolean withCode);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<GlideString, Object>[] response = client.functionList(true).get();
     * for (Map<GlideString, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get(gs("library_name")), libraryInfo.get(gs("engine")));
     *     Map<GlideString, Object>[] functions = (Map<GlideString, Object>[]) libraryInfo.get(gs("functions"));
     *     for (Map<GlideString, Object> function : functions) {
     *         Set<GlideString> flags = (Set<GlideString>) function.get(gs("flags"));
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get(gs("name")), gs(String.join(", ", flags)), function.get(gs("description")));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get(gs("library_code")));
     * }
     * }</pre>
     */
    CompletableFuture<Map<GlideString, Object>[]> functionListBinary(boolean withCode);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList("myLib?_backup", true).get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern, boolean withCode);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<GlideString, Object>[] response = client.functionList(gs("myLib?_backup"), true).get();
     * for (Map<GlideString, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get(gs("library_name")), libraryInfo.get(gs("engine")));
     *     Map<GlideString, Object>[] functions = (Map<GlideString, Object>[]) libraryInfo.get(gs("functions"));
     *     for (Map<GlideString, Object> function : functions) {
     *         Set<GlideString> flags = (Set<GlideString>) function.get(gs("flags"));
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get(gs("name")), gs(String.join(", ", flags)), function.get(gs("description")));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get(gs("library_code")));
     * }
     * }</pre>
     */
    CompletableFuture<Map<GlideString, Object>[]> functionListBinary(
            GlideString libNamePattern, boolean withCode);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList(true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            boolean withCode, Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<GlideString, Object>[]> response = client.functionList(true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<GlideString, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get(gs("library_name")), libraryInfo.get(gs("engine")));
     *     Map<GlideString, Object>[] functions = (Map<GlideString, Object>[]) libraryInfo.get(gs("functions"));
     *     for (Map<GlideString, Object> function : functions) {
     *         Set<GlideString> flags = (Set<GlideString>) function.get(gs("flags"));
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get(gs("name")), gs(String.join(", ", flags)), function.get(gs("description")));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get(gs("library_code")));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<GlideString, Object>[]>> functionListBinary(
            boolean withCode, Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList("myLib?_backup", true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String.join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            String libNamePattern, boolean withCode, Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @param withCode Specifies whether to request the library code from the server or not.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList(gs("myLib?_backup"), true, ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<GlideString, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get(gs("library_name")), libraryInfo.get(gs("engine")));
     *     Map<GlideString, Object>[] functions = (Map<GlideString, Object>[]) libraryInfo.get(gs("functions"));
     *     for (Map<GlideString, Object> function : functions) {
     *         Set<GlideString> flags = (Set<GlideString>) function.get(gs("flags"));
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get(gs("name")), gs(String.join(", ", flags)), function.get(gs("description")));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get(gs("library_code")));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<GlideString, Object>[]>> functionListBinary(
            GlideString libNamePattern, boolean withCode, Route route);

    /**
     * Deletes all function libraries.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush();

    /**
     * Deletes all function libraries.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush(SYNC).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush(FlushMode mode);

    /**
     * Deletes all function libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush(RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush(Route route);

    /**
     * Deletes all function libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-flush/">valkey.io</a> for details.
     * @param mode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionFlush(SYNC, RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionFlush(FlushMode mode, Route route);

    /**
     * Deletes a library and all its functions.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-delete/">valkey.io</a> for details.
     * @param libName The library name to delete.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete("myLib").get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(String libName);

    /**
     * Deletes a library and all its functions.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-delete/">valkey.io</a> for details.
     * @param libName The library name to delete.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete(gs("myLib")).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(GlideString libName);

    /**
     * Deletes a library and all its functions.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-delete/">valkey.io</a> for details.
     * @param libName The library name to delete.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete("myLib", RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(String libName, Route route);

    /**
     * Deletes a library and all its functions.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-delete/">valkey.io</a> for details.
     * @param libName The library name to delete.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionDelete(gs("myLib"), RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionDelete(GlideString libName, Route route);

    /**
     * Returns the serialized payload of all loaded libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-dump/">valkey.io</a> for details.
     * @return The serialized payload of all loaded libraries.
     * @example
     *     <pre>{@code
     * byte[] data = client.functionDump().get();
     * // data can be used to restore loaded functions on any Valkey instance
     * }</pre>
     */
    CompletableFuture<byte[]> functionDump();

    /**
     * Returns the serialized payload of all loaded libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-dump/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The serialized payload of all loaded libraries.
     * @example
     *     <pre>{@code
     * byte[] data = client.functionDump(RANDOM).get().getSingleValue();
     * // data can be used to restore loaded functions on any Valkey instance
     * }</pre>
     */
    CompletableFuture<ClusterValue<byte[]>> functionDump(Route route);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump()}.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-restore/">valkey.io</a> for details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(byte[] payload);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump()}.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-restore/">valkey.io</a> for details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @param policy A policy for handling existing libraries.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data, FLUSH).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(byte[] payload, FunctionRestorePolicy policy);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump(Route)}.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-restore/">valkey.io</a> for details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data, ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(byte[] payload, Route route);

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump(Route)}.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-restore/">valkey.io</a> for details.
     * @param payload The serialized data from {@link #functionDump()}.
     * @param policy A policy for handling existing libraries.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.functionRestore(data, FLUSH, ALL_PRIMARIES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionRestore(
            byte[] payload, FunctionRestorePolicy policy, Route route);

    /**
     * Invokes a previously loaded function.<br>
     * The command will be routed to a primary random node.<br>
     * To route to a replica please refer to {@link #fcallReadOnly(String)}.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * Object response = client.fcall("Deep_Thought").get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(String function);

    /**
     * Invokes a previously loaded function.<br>
     * The command will be routed to a primary random node.<br>
     * To route to a replica please refer to {@link #fcallReadOnly(String)}.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * Object response = client.fcall(gs("Deep_Thought")).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(GlideString function);

    /**
     * Invokes a previously loaded function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> response = client.fcall("Deep_Thought", ALL_NODES).get();
     * for (Object nodeResponse : response.getMultiValue().values()) {
     *   assert nodeResponse == 42L;
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcall(String function, Route route);

    /**
     * Invokes a previously loaded function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> response = client.fcall(gs("Deep_Thought"), ALL_NODES).get();
     * for (Object nodeResponse : response.getMultiValue().values()) {
     *   assert nodeResponse == 42L;
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcall(GlideString function, Route route);

    /**
     * Invokes a previously loaded function.<br>
     * The command will be routed to a random primary node.<br>
     * To route to a replica please refer to {@link #fcallReadOnly(String, String[])}.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * Object response = client.fcall("Deep_Thought", args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(String function, String[] arguments);

    /**
     * Invokes a previously loaded function.<br>
     * The command will be routed to a random primary node.<br>
     * To route to a replica please refer to {@link #fcallReadOnly(String, String[])}.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * GlideString[] args = new GlideString[] { gs("Answer"), gs("to"), gs("the"), gs("Ultimate"), gs("Question"), gs("of"), gs("Life,"), gs("the"), gs("Universe,"), gs("and"), gs("Everything")};
     * Object response = client.fcall(gs("Deep_Thought"), args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(GlideString function, GlideString[] arguments);

    /**
     * Invokes a previously loaded function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * ClusterValue<Object> response = client.fcall("Deep_Thought", args, RANDOM).get();
     * assert response.getSingleValue() == 42L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcall(String function, String[] arguments, Route route);

    /**
     * Invokes a previously loaded function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * GlideString[] args = new GlideString[] { gs("Answer"), gs("to"), gs("the"), gs("Ultimate"), gs("Question"), gs("of"), gs("Life,"), gs("the"), gs("Universe,"), gs("and"), gs("Everything")};
     * ClusterValue<Object> response = client.fcall(gs("Deep_Thought"), args, RANDOM).get();
     * assert response.getSingleValue() == 42L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcall(
            GlideString function, GlideString[] arguments, Route route);

    /**
     * Invokes a previously loaded read-only function.<br>
     * The command is routed to a random node depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * Object response = client.fcallReadOnly("Deep_Thought").get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(String function);

    /**
     * Invokes a previously loaded read-only function.<br>
     * The command is routed to a random node depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * Object response = client.fcallReadOnly(gs("Deep_Thought")).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(GlideString function);

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> response = client.fcallReadOnly("Deep_Thought", ALL_NODES).get();
     * for (Object nodeResponse : response.getMultiValue().values()) {
     *   assert nodeResponse == 42L;
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcallReadOnly(String function, Route route);

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * ClusterValue<Object> response = client.fcallReadOnly(gs("Deep_Thought"), ALL_NODES).get();
     * for (Object nodeResponse : response.getMultiValue().values()) {
     *   assert nodeResponse == 42L;
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcallReadOnly(GlideString function, Route route);

    /**
     * Invokes a previously loaded function.<br>
     * The command is routed to a random node depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * Object response = client.fcallReadOnly("Deep_Thought", args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(String function, String[] arguments);

    /**
     * Invokes a previously loaded function.<br>
     * The command is routed to a random node depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * GlideString[] args = new GlideString[] { gs("Answer"), gs("to"), gs("the"), gs("Ultimate"), gs("Question"), gs("of"), gs("Life,"), gs("the"), gs("Universe,"), gs("and"), gs("Everything")};
     * Object response = client.fcallReadOnly(gs("Deep_Thought"), args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(GlideString function, GlideString[] arguments);

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything" };
     * ClusterValue<Object> response = client.fcallReadOnly("Deep_Thought", args, RANDOM).get();
     * assert response.getSingleValue() == 42L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            String function, String[] arguments, Route route);

    /**
     * Invokes a previously loaded read-only function.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The invoked function's return value wrapped by a {@link ClusterValue}.
     * @example
     *     <pre>{@code
     * GlideString[] args = new GlideString[] { gs("Answer"), gs("to"), gs("the"), gs("Ultimate"), gs("Question"), gs("of"), gs("Life,"), gs("the"), gs("Universe,"), gs("and"), gs("Everything")};
     * ClusterValue<Object> response = client.fcallReadOnly(gs("Deep_Thought"), args, RANDOM).get();
     * assert response.getSingleValue() == 42L;
     * }</pre>
     */
    CompletableFuture<ClusterValue<Object>> fcallReadOnly(
            GlideString function, GlideString[] arguments, Route route);

    /**
     * Kills a function that is currently executing.<br>
     * <code>FUNCTION KILL</code> terminates read-only functions only.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-kill/">valkey.io</a> for details.
     * @return <code>OK</code> if function is terminated. Otherwise, throws an error.
     * @example
     *     <pre>{@code
     * String response = client.functionKill().get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionKill();

    /**
     * Kills a function that is currently executing.<br>
     * <code>FUNCTION KILL</code> terminates read-only functions only.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-kill/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return <code>OK</code> if function is terminated. Otherwise, throws an error.
     * @example
     *     <pre>{@code
     * String response = client.functionKill(RANDOM).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> functionKill(Route route);

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-stats/">valkey.io</a> for details.
     * @return A <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<String, Map<String, Map<String, Object>>> response = client.functionStats().get().getMultiValue();
     * for (String node : response.keySet()) {
     *   Map<String, Object> runningScriptInfo = response.get(node).get("running_script");
     *   if (runningScriptInfo != null) {
     *     String[] commandLine = (String[]) runningScriptInfo.get("command");
     *     System.out.printf("Node '%s' is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *         node, runningScriptInfo.get("name"), String.join(" ", commandLine), (long) runningScriptInfo.get("duration_ms"));
     *   }
     *   Map<String, Object> enginesInfo = response.get(node).get("engines");
     *   for (String engineName : enginesInfo.keySet()) {
     *     Map<String, Long> engine = (Map<String, Long>) enginesInfo.get(engineName);
     *     System.out.printf("Node '%s' supports engine '%s', which has %d libraries and %d functions in total%n",
     *         node, engineName, engine.get("libraries_count"), engine.get("functions_count"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats();

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.<br>
     * The command will be routed to all nodes.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-stats/">valkey.io</a> for details.
     * @return A <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<String, Map<GlideString, Map<GlideString, Object>>> response = client.functionStatsBinary().get().getMultiValue();
     * for (GlideString node : response.keySet()) {
     *   Map<GlideString, Object> runningScriptInfo = response.get(node).get(gs("running_script"));
     *   if (runningScriptInfo != null) {
     *     GlideString[] commandLine = (GlideString[]) runningScriptInfo.get(gs("command"));
     *     System.out.printf("Node '%s' is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *         node, runningScriptInfo.get(gs("name")), String.join(" ", Arrays.toString(commandLine)), (long) runningScriptInfo.get(gs("duration_ms")));
     *   }
     *   Map<GlideString, Object> enginesInfo = response.get(node).get(gs("engines"));
     *   for (String engineName : enginesInfo.keySet()) {
     *     Map<GlideString, Long> engine = (Map<GlideString, Long>) enginesInfo.get(engineName);
     *     System.out.printf("Node '%s' supports engine '%s', which has %d libraries and %d functions in total%n",
     *         node, engineName, engine.get(gs("libraries_count")), engine.get(gs("functions_count")));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<GlideString, Map<GlideString, Object>>>> functionStatsBinary();

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-stats/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<String, Map<String, Object>> response = client.functionStats(RANDOM).get().getSingleValue();
     * Map<String, Object> runningScriptInfo = response.get("running_script");
     * if (runningScriptInfo != null) {
     *   String[] commandLine = (String[]) runningScriptInfo.get("command");
     *   System.out.printf("Node is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *       runningScriptInfo.get("name"), String.join(" ", commandLine), (long)runningScriptInfo.get("duration_ms"));
     * }
     * Map<String, Object> enginesInfo = response.get("engines");
     * for (String engineName : enginesInfo.keySet()) {
     *   Map<String, Long> engine = (Map<String, Long>) enginesInfo.get(engineName);
     *   System.out.printf("Node supports engine '%s', which has %d libraries and %d functions in total%n",
     *       engineName, engine.get("libraries_count"), engine.get("functions_count"));
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Map<String, Object>>>> functionStats(Route route);

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-stats/">valkey.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<GlideString, Map<GlideString, Object>> response = client.functionStats(RANDOM).get().getSingleValue();
     * Map<GlideString, Object> runningScriptInfo = response.get(gs("running_script"));
     * if (runningScriptInfo != null) {
     *   GlideString[] commandLine = (GlideString[]) runningScriptInfo.get(gs("command"));
     *   System.out.printf("Node is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *       runningScriptInfo.get(gs("name")), String.join(" ", Arrays.toString(commandLine)), (long)runningScriptInfo.get(gs("duration_ms")));
     * }
     * Map<GlideString, Object> enginesInfo = response.get(gs("engines"));
     * for (GlideString engineName : enginesInfo.keySet()) {
     *   Map<GlideString, Long> engine = (Map<GlideString, Long>) enginesInfo.get(engineName);
     *   System.out.printf("Node supports engine '%s', which has %d libraries and %d functions in total%n",
     *       engineName, engine.get(gs("libraries_count")), engine.get(gs("functions_count")));
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<GlideString, Map<GlideString, Object>>>> functionStatsBinary(
            Route route);

    /**
     * Invokes a Lua script.<br>
     * This method simplifies the process of invoking scripts on the server by using an object that
     * represents a Lua script. The script loading and execution will all be handled internally. If
     * the script has not already been loaded, it will be loaded automatically using the <code>
     * SCRIPT LOAD</code> command. After that, it will be invoked using the <code>EVALSHA </code>
     * command.
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return 'Hello'", false)) {
     *     String result = (String) client.invokeScript(luaScript).get();
     *     assert result.equals("Hello");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script, Route route);

    /**
     * Invokes a Lua script with its keys and arguments.<br>
     * This method simplifies the process of invoking scripts on the server by using an object that
     * represents a Lua script. The script loading, argument preparation, and execution will all be
     * handled internally. If the script has not already been loaded, it will be loaded automatically
     * using the <code>SCRIPT LOAD</code> command. After that, it will be invoked using the <code>
     * EVALSHA</code> command.
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param options The script option that contains the non-key arguments for the script.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return { ARGV[1] }", false)) {
     *     ScriptArgOptions scriptArgOptions = ScriptArgOptions.builder().arg("abc").build();
     *     Object[] result = (Object[]) client.invokeScript(luaScript, scriptOptions, ALL_PRIMARIES).get();
     *     assert result[0].equals("abc");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script, ScriptArgOptions options, Route route);

    /**
     * Invokes a Lua script with its keys and arguments.<br>
     * This method simplifies the process of invoking scripts on the server by using an object that
     * represents a Lua script. The script loading, argument preparation, and execution will all be
     * handled internally. If the script has not already been loaded, it will be loaded automatically
     * using the <code>SCRIPT LOAD</code> command. After that, it will be invoked using the <code>
     * EVALSHA</code> command.
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param options The script option that contains the non-key arguments for the script.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script(gs("return { ARGV[1] }", true))) {
     *     ScriptArgOptionsGlideString options = ScriptArgOptions.builder().arg(gs("abc")).build();
     *     Object[] result = (Object[]) client.invokeScript(luaScript, options, ALL_PRIMARIES).get();
     *     assert result[0].equals(gs("abc"));
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(
            Script script, ScriptArgOptionsGlideString options, Route route);

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/script-exists">SCRIPT EXISTS</a> for details.
     * @param sha1s SHA1 digests of Lua scripts to be checked.
     * @return An array of <code>boolean</code> values indicating the existence of each script.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return { KEYS[1], ARGV[1] }", true)) {
     *     client.invokeScript(luaScript).get();
     *     Boolean[] result = client.scriptExists(new String[]{luaScript.getHash()});
     *     assert result[0].equals(true);
     * }
     * }</pre>
     */
    CompletableFuture<Boolean[]> scriptExists(String[] sha1s);

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.<br>
     * The command will be routed to all primary nodes.
     *
     * @see <a href="https://valkey.io/commands/script-exists">SCRIPT EXISTS</a> for details.
     * @param sha1s SHA1 digests of Lua scripts to be checked.
     * @return An array of <code>boolean</code> values indicating the existence of each script.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script(gs("return { KEYS[1], ARGV[1] }", true))) {
     *     client.invokeScript(luaScript).get();
     *     Boolean[] result = client.scriptExists(new String[]{luaScript.getHash()});
     *     assert result[0].equals(true);
     * }
     * }</pre>
     */
    CompletableFuture<Boolean[]> scriptExists(GlideString[] sha1s);

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.
     *
     * @see <a href="https://valkey.io/commands/script-exists">SCRIPT EXISTS</a> for details.
     * @param sha1s SHA1 digests of Lua scripts to be checked.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return An array of <code>boolean</code> values indicating the existence of each script.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return { KEYS[1], ARGV[1] }", true)) {
     *     client.invokeScript(luaScript).get();
     *     Boolean[] result = client.scriptExists(new String[]{luaScript.getHash()});
     *     assert result[0].equals(true);
     * }
     * }</pre>
     */
    CompletableFuture<Boolean[]> scriptExists(String[] sha1s, Route route);

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.
     *
     * @see <a href="https://valkey.io/commands/script-exists">SCRIPT EXISTS</a> for details.
     * @param sha1s SHA1 digests of Lua scripts to be checked.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return An array of <code>boolean</code> values indicating the existence of each script.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script(gs("return { KEYS[1], ARGV[1] }", true))) {
     *     client.invokeScript(luaScript).get();
     *     Boolean[] result = client.scriptExists(new String[]{luaScript.getHash()});
     *     assert result[0].equals(true);
     * }
     * }</pre>
     */
    CompletableFuture<Boolean[]> scriptExists(GlideString[] sha1s, Route route);

    /**
     * Flushes the Lua scripts cache.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/script-flush">SCRIPT FLUSH</a> for details.
     * @return A simple "OK" response.
     * @example
     *     <pre>{@code
     * String result = client.scriptFlush();
     * assert "OK".equals(result);
     * }</pre>
     */
    CompletableFuture<String> scriptFlush();

    /**
     * Flushes the Lua scripts cache.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/script-flush">SCRIPT FLUSH</a> for details.
     * @param flushMode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @return A simple "OK" response.
     * @example
     *     <pre>{@code
     * String result = client.scriptFlush(ASYNC);
     * assert "OK".equals(result);
     * }</pre>
     */
    CompletableFuture<String> scriptFlush(FlushMode flushMode);

    /**
     * Flushes the Lua scripts cache.
     *
     * @see <a href="https://valkey.io/commands/script-flush">SCRIPT flush</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A simple "OK" response.
     * @example
     *     <pre>{@code
     * String result = client.scriptFlush(ALL_PRIMARIES);
     * assert "OK".equals(result);
     * }</pre>
     */
    CompletableFuture<String> scriptFlush(Route route);

    /**
     * Flushes the Lua scripts cache.
     *
     * @see <a href="https://valkey.io/commands/script-flush">SCRIPT flush</a> for details.
     * @param flushMode The flushing mode, could be either {@link FlushMode#SYNC} or {@link
     *     FlushMode#ASYNC}.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A simple "OK" response.
     * @example
     *     <pre>{@code
     * String result = client.scriptFlush(ASYNC, ALL_PRIMARIES);
     * assert "OK".equals(result);
     * }</pre>
     */
    CompletableFuture<String> scriptFlush(FlushMode flushMode, Route route);

    /**
     * Kill the currently executing Lua script, assuming no write operation was yet performed by the
     * script.<br>
     * The command will be routed to all nodes.
     *
     * @see <a href="https://valkey.io/commands/script-kill">SCRIPT KILL</a> for details.
     * @return A simple "OK" response.
     * @example
     *     <pre>{@code
     * String result = client.scriptKill();
     * assert "OK".equals(result);
     * }</pre>
     */
    CompletableFuture<String> scriptKill();

    /**
     * Kills the currently executing Lua script, assuming no write operation was yet performed by the
     * script.
     *
     * @see <a href="https://valkey.io/commands/script-kill">SCRIPT KILL</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return A simple "OK" response.
     * @example
     *     <pre>{@code
     * String result = client.scriptKill(RANDOM);
     * assert "OK".equals(result);
     * }</pre>
     */
    CompletableFuture<String> scriptKill(Route route);
}
