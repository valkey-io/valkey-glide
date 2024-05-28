/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.ClusterValue;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Scripting and Function" group for a cluster client.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=scripting">Scripting and Function
 *     Commands</a>
 */
public interface ScriptingAndFunctionsClusterCommands {

    /**
     * Loads a library to Redis unless a library with the same name exists. Use {@link
     * #functionLoadReplace(String)} to replace existing libraries.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * String response = client.functionLoad(code).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode);

    /**
     * Loads a library to Redis and overwrites a library with the same name if it exists.<br>
     * The command will be routed to all primary nodes.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * String response = client.functionLoadReplace(code).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoadReplace(String libraryCode);

    /**
     * Loads a library to Redis unless a library with the same name exists. Use {@link
     * #functionLoadReplace(String, Route)} to replace existing libraries.<br>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * Route route = new SlotKeyRoute("key", PRIMARY);
     * String response = client.functionLoad(code, route).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoad(String libraryCode, Route route);

    /**
     * Loads a library to Redis and overwrites a library with the same name if it exists.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-load/">redis.io</a> for details.
     * @param libraryCode The source code that implements the library.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return The library name that was loaded.
     * @example
     *     <pre>{@code
     * String code = "#!lua name=mylib \n redis.register_function('myfunc', function(keys, args) return args[1] end)";
     * String response = client.functionLoadReplace(code, ALL_NODES).get();
     * assert response.equals("mylib");
     * }</pre>
     */
    CompletableFuture<String> functionLoadReplace(String libraryCode, Route route);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList().get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList();

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @return Info about all libraries, their functions, and their code.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionListWithCode().get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionListWithCode();

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionList("myLib?_backup").get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern);

    /**
     * Returns information about the functions and libraries.<br>
     * The command will be routed to a random node.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @return Info about queried libraries, their functions, and their code.
     * @example
     *     <pre>{@code
     * Map<String, Object>[] response = client.functionListWithCode("GLIDE*").get();
     * for (Map<String, Object> libraryInfo : response) {
     *     System.out.printf("Server has library '%s' which runs on %s engine%n",
     *         libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionListWithCode(String libNamePattern);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about all libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList(ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @return Info about all libraries, their functions, and their code.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionListWithCode(ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionListWithCode(Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @return Info about queried libraries and their functions.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionList("myLib?_backup", ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionList(
            String libNamePattern, Route route);

    /**
     * Returns information about the functions and libraries.
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/function-list/">redis.io</a> for details.
     * @param route Specifies the routing configuration for the command. The client will route the
     *     command to the nodes defined by <code>route</code>.
     * @param libNamePattern A wildcard pattern for matching library names.
     * @return Info about queried libraries, their functions, and their code.
     * @example
     *     <pre>{@code
     * ClusterValue<Map<String, Object>[]> response = client.functionListWithCode("myLib?_backup", ALL_NODES).get();
     * for (String node : response.getMultiValue().keySet()) {
     *   for (Map<String, Object> libraryInfo : response.getMultiValue().get(node)) {
     *     System.out.printf("Node '%s' has library '%s' which runs on %s engine%n",
     *         node, libraryInfo.get("library_name"), libraryInfo.get("engine"));
     *     Map<String, Object>[] functions = (Map<String, Object>[]) libraryInfo.get("functions");
     *     for (Map<String, Object> function : functions) {
     *         Set<String> flags = (Set<String>) function.get("flags");
     *         System.out.printf("Library has function '%s' with flags '%s' described as %s%n",
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<ClusterValue<Map<String, Object>[]>> functionListWithCode(
            String libNamePattern, Route route);
}
