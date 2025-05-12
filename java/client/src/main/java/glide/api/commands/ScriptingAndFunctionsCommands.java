/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.commands.FlushMode;
import glide.api.models.commands.function.FunctionRestorePolicy;
import glide.api.models.configuration.ReadFrom;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Scripting and Function" group for a standalone client.
 *
 * @see <a href="https://valkey.io/commands/?group=scripting">Scripting and Function Commands</a>
 */
public interface ScriptingAndFunctionsCommands {

    /**
     * Loads a library to Valkey.
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
     * Loads a library to Valkey.
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
     * Returns information about the functions and libraries.
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
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(boolean withCode);

    /**
     * Returns information about the functions and libraries.
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
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<GlideString, Object>[]> functionListBinary(boolean withCode);

    /**
     * Returns information about the functions and libraries.
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
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Object>[]> functionList(String libNamePattern, boolean withCode);

    /**
     * Returns information about the functions and libraries.
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
     *             function.get("name"), String. join(", ", flags), function.get("description"));
     *     }
     *     System.out.printf("Library code:%n%s%n", libraryInfo.get("library_code"));
     * }
     * }</pre>
     */
    CompletableFuture<Map<GlideString, Object>[]> functionListBinary(
            GlideString libNamePattern, boolean withCode);

    /**
     * Deletes all function libraries.
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
     * Deletes all function libraries.
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
     * Deletes a library and all its functions.
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
     * Deletes a library and all its functions.
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
     * Returns the serialized payload of all loaded libraries.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-dump/">valkey.io</a> for details.
     * @return The serialized payload of all loaded libraries.
     * @example
     *     <pre>{@code
     * byte[] data = client.functionDump().get();
     * // now data could be saved to restore loaded functions on any Valkey instance
     * }</pre>
     */
    CompletableFuture<byte[]> functionDump();

    /**
     * Restores libraries from the serialized payload returned by {@link #functionDump()}.
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
     * Restores libraries from the serialized payload returned by {@link #functionDump()}..
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
     * Invokes a previously loaded function.<br>
     * This command is routed to primary nodes only.<br>
     * To route to a replica please refer to {@link #fcallReadOnly}.
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
     * This command is routed to primary nodes only.<br>
     * To route to a replica please refer to {@link #fcallReadOnly}.
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
     * Invokes a previously loaded read-only function.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
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
     * This command is routed depending on the client's {@link ReadFrom} strategy.
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
     * Kills a function that is currently executing.<br>
     * <code>FUNCTION KILL</code> terminates read-only functions only. <code>FUNCTION KILL</code> runs
     * on all nodes of the server, including primary and replicas.
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
     * Returns information about the function that's currently running and information about the
     * available execution engines.<br>
     * <code>FUNCTION STATS</code> runs on all nodes of the server, including primary and replicas.
     * The response includes a mapping from node address to the command response for that node.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-stats/">valkey.io</a> for details.
     * @return A <code>Map</code> from node address to the command response for that node, where the
     *     command contains a <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<String, Map<String, Map<String, Object>>> response = client.functionStats().get();
     * for (String node : response.keySet()) {
     *   Map<String, Object> runningScriptInfo = response.get(node).get("running_script");
     *   if (runningScriptInfo != null) {
     *     String[] commandLine = (String[]) runningScriptInfo.get("command");
     *     System.out.printf("Node '%s' is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *         node, runningScriptInfo.get("name"), String.join(" ", commandLine), (long)runningScriptInfo.get("duration_ms"));
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
    CompletableFuture<Map<String, Map<String, Map<String, Object>>>> functionStats();

    /**
     * Returns information about the function that's currently running and information about the
     * available execution engines.<br>
     * <code>FUNCTION STATS</code> runs on all nodes of the server, including primary and replicas.
     * The response includes a mapping from node address to the command response for that node.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/function-stats/">valkey.io</a> for details.
     * @return A <code>Map</code> from node address to the command response for that node, where the
     *     command contains a <code>Map</code> with two keys:
     *     <ul>
     *       <li><code>running_script</code> with information about the running script.
     *       <li><code>engines</code> with information about available engines and their stats.
     *     </ul>
     *     See example for more details.
     * @example
     *     <pre>{@code
     * Map<GlideString, Map<GlideString, Map<GlideString, Object>>> response = client.functionStats().get();
     * for (String node : response.keySet()) {
     *   Map<GlideString, Object> runningScriptInfo = response.get(gs(node)).get(gs("running_script"));
     *   if (runningScriptInfo != null) {
     *     GlideString[] commandLine = (GlideString[]) runningScriptInfo.get(gs("command"));
     *     System.out.printf("Node '%s' is currently running function '%s' with command line '%s', which has been running for %d ms%n",
     *         node, runningScriptInfo.get(gs("name")), String.join(" ", Arrays.toString(commandLine)), (long)runningScriptInfo.get(gs("duration_ms")));
     *   }
     *   Map<GlideString, Object> enginesInfo = response.get(gs(node)).get(gs("engines"));
     *   for (GlideString engineName : enginesInfo.keySet()) {
     *     Map<GlideString, Long> engine = (Map<GlideString, Long>) enginesInfo.get(gs(engineName));
     *     System.out.printf("Node '%s' supports engine '%s', which has %d libraries and %d functions in total%n",
     *         node, engineName, engine.get(gs("libraries_count")), engine.get(gs("functions_count")));
     *   }
     * }
     * }</pre>
     */
    CompletableFuture<Map<String, Map<GlideString, Map<GlideString, Object>>>> functionStatsBinary();

    /**
     * Checks existence of scripts in the script cache by their SHA1 digest.
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
     * Checks existence of scripts in the script cache by their SHA1 digest.
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
     * Flushes the Lua scripts cache.
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
     * Flushes the Lua scripts cache.
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
     * Kill the currently executing Lua script, assuming no write operation was yet performed by the
     * script.
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
}
