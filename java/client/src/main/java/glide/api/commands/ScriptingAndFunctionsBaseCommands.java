/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.Script;
import glide.api.models.commands.ScriptDebugMode;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.ScriptOptionsGlideString;
import glide.api.models.configuration.ReadFrom;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands for the "Scripting and Function" group for standalone and cluster clients.
 *
 * @see <a href="https://valkey.io/commands/?group=scripting">Scripting and Function Commands</a>
 */
public interface ScriptingAndFunctionsBaseCommands {

    /**
     * Invokes a previously loaded function.<br>
     * This command is routed to primary nodes only.<br>
     * To route to a replica please refer to {@link #fcallReadOnly}.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random primary node.
     *     </ul>
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of keys accessed by the function. To ensure the correct
     *     execution of functions, both in standalone and clustered deployments, all names of keys
     *     that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"};
     * Object response = client.fcall("Deep_Thought", new String[0], args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(String function, String[] keys, String[] arguments);

    /**
     * Invokes a previously loaded function.<br>
     * This command is routed to primary nodes only.<br>
     * To route to a replica please refer to {@link #fcallReadOnly}.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random primary node.
     *     </ul>
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall/">valkey.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of keys accessed by the function. To ensure the correct
     *     execution of functions, both in standalone and clustered deployments, all names of keys
     *     that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * GlideString[] args = new GlideString[] { gs("Answer"), gs("to"), gs("the"), gs("Ultimate"), gs("Question"), gs("of"), gs("Life,"), gs("the"), gs("Universe,"), gs("and"), gs("Everything")};
     * Object response = client.fcall(gs("Deep_Thought"), new GlideString[0], args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(
            GlideString function, GlideString[] keys, GlideString[] arguments);

    /**
     * Invokes a previously loaded read-only function.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random node.
     *     </ul>
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of keys accessed by the function. To ensure the correct
     *     execution of functions, both in standalone and clustered deployments, all names of keys
     *     that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"};
     * Object response = client.fcallReadOnly("Deep_Thought", new String[0], args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(String function, String[] keys, String[] arguments);

    /**
     * Invokes a previously loaded read-only function.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random node.
     *     </ul>
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/fcall_ro/">valkey.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of keys accessed by the function. To ensure the correct
     *     execution of functions, both in standalone and clustered deployments, all names of keys
     *     that a function accesses must be explicitly provided as <code>keys</code>.
     * @param arguments An <code>array</code> of <code>function</code> arguments. <code>arguments
     *     </code> should not represent names of keys.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * GlideString[] args = new GlideString[] { gs("Answer"), gs("to"), gs("the"), gs("Ultimate"), gs("Question"), gs("of"), gs("Life,"), gs("the"), gs("Universe,"), gs("and"), gs("Everything")};
     * Object response = client.fcallReadOnly(gs("Deep_Thought"), new GlideString[0], args).get();
     * assert response == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcallReadOnly(
            GlideString function, GlideString[] keys, GlideString[] arguments);

    /**
     * Returns the original source code of a script in the script cache.
     *
     * @see <a href="https://valkey.io/commands/script-show">valkey.io</a> for details.
     * @since Valkey 8.0.0 and above.
     * @param sha1 The SHA1 digest of the script.
     * @return The original source code of the script, if present in the cache. If the script is not
     *     found in the cache, an error is thrown.
     * @example
     *     <pre>{@code
     * String scriptSource = client.scriptShow(luaScript.getHash()).get();
     * assert scriptSource.equals("return { KEYS[1], ARGV[1] }");
     * }</pre>
     */
    CompletableFuture<String> scriptShow(String sha1);

    /**
     * Returns the original source code of a script in the script cache.
     *
     * @see <a href="https://valkey.io/commands/script-show">valkey.io</a> for details.
     * @since Valkey 8.0.0 and above.
     * @param sha1 The SHA1 digest of the script.
     * @return The original source code of the script, if present in the cache. If the script is not
     *     found in the cache, an error is thrown.
     * @example
     *     <pre>{@code
     * String scriptSource = client.scriptShow(gs(luaScript.getHash())).get();
     * assert scriptSource.equals(gs("return { KEYS[1], ARGV[1] }"));
     * }</pre>
     */
    CompletableFuture<GlideString> scriptShow(GlideString sha1);

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
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return 'Hello'", false)) {
     *     String result = (String) client.invokeScript(luaScript).get();
     *     assert result.equals("Hello");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script);

    /**
     * Invokes a Lua script with its keys and arguments.<br>
     * This method simplifies the process of invoking scripts on the server by using an object that
     * represents a Lua script. The script loading, argument preparation, and execution will all be
     * handled internally. If the script has not already been loaded, it will be loaded automatically
     * using the <code>SCRIPT LOAD</code> command. After that, it will be invoked using the <code>
     * EVALSHA</code> command.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> in <code>options</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random primary node.
     *     </ul>
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param options The script option that contains keys and arguments for the script.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script("return { KEYS[1], ARGV[1] }", false)) {
     *     ScriptOptions scriptOptions = ScriptOptions.builder().key("foo").arg("bar").build();
     *     Object[] result = (Object[]) client.invokeScript(luaScript, scriptOptions).get();
     *     assert result[0].equals("foo");
     *     assert result[1].equals("bar");
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script, ScriptOptions options);

    /**
     * Invokes a Lua script with its keys and arguments.<br>
     * This method simplifies the process of invoking scripts on the server by using an object that
     * represents a Lua script. The script loading, argument preparation, and execution will all be
     * handled internally. If the script has not already been loaded, it will be loaded automatically
     * using the <code>SCRIPT LOAD</code> command. After that, it will be invoked using the <code>
     * EVALSHA</code> command.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> in <code>options</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random primary node.
     *     </ul>
     *
     * @see <a href="https://valkey.io/commands/script-load/">SCRIPT LOAD</a> and <a
     *     href="https://valkey.io/commands/evalsha/">EVALSHA</a> for details.
     * @param script The Lua script to execute.
     * @param options The script option that contains keys and arguments for the script.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * try(Script luaScript = new Script(gs("return { KEYS[1], ARGV[1] }", true))) {
     *     ScriptOptionsGlideString scriptOptions = ScriptOptionsGlideString.builder().key(gs("foo")).arg(gs("bar")).build();
     *     Object[] result = (Object[]) client.invokeScript(luaScript, scriptOptions).get();
     *     assert result[0].equals(gs("foo"));
     *     assert result[1].equals(gs("bar"));
     * }
     * }</pre>
     */
    CompletableFuture<Object> invokeScript(Script script, ScriptOptionsGlideString options);

    /**
     * Executes a read-only Lua script.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/eval_ro/">valkey.io</a> for details.
     * @param script The Lua script to execute.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * String script = "return 'Hello, World!'";
     * String result = (String) client.evalReadOnly(script).get();
     * assert result.equals("Hello, World!");
     * }</pre>
     */
    CompletableFuture<Object> evalReadOnly(String script);

    /**
     * Executes a read-only Lua script with keys and arguments.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/eval_ro/">valkey.io</a> for details.
     * @param script The Lua script to execute.
     * @param keys An array of keys accessed by the script.
     * @param args An array of script arguments.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * String script = "return {KEYS[1], ARGV[1]}";
     * Object[] result = (Object[]) client.evalReadOnly(script, new String[]{"key1"}, new String[]{"arg1"}).get();
     * assert result[0].equals("key1");
     * assert result[1].equals("arg1");
     * }</pre>
     */
    CompletableFuture<Object> evalReadOnly(String script, String[] keys, String[] args);

    /**
     * Executes a read-only Lua script.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/eval_ro/">valkey.io</a> for details.
     * @param script The Lua script to execute.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * GlideString script = gs("return 'Hello, World!'");
     * GlideString result = (GlideString) client.evalReadOnly(script).get();
     * assert result.equals(gs("Hello, World!"));
     * }</pre>
     */
    CompletableFuture<Object> evalReadOnly(GlideString script);

    /**
     * Executes a read-only Lua script with keys and arguments.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/eval_ro/">valkey.io</a> for details.
     * @param script The Lua script to execute.
     * @param keys An array of keys accessed by the script.
     * @param args An array of script arguments.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * GlideString script = gs("return {KEYS[1], ARGV[1]}");
     * Object[] result = (Object[]) client.evalReadOnly(script, new GlideString[]{gs("key1")}, new GlideString[]{gs("arg1")}).get();
     * assert result[0].equals(gs("key1"));
     * assert result[1].equals(gs("arg1"));
     * }</pre>
     */
    CompletableFuture<Object> evalReadOnly(
            GlideString script, GlideString[] keys, GlideString[] args);

    /**
     * Executes a read-only Lua script by its SHA1 digest.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/evalsha_ro/">valkey.io</a> for details.
     * @param sha1 The SHA1 digest of the script to execute.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * String sha1 = client.scriptLoad("return 'Hello from SHA!'").get();
     * String result = (String) client.evalshaReadOnly(sha1).get();
     * assert result.equals("Hello from SHA!");
     * }</pre>
     */
    CompletableFuture<Object> evalshaReadOnly(String sha1);

    /**
     * Executes a read-only Lua script by its SHA1 digest with keys and arguments.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/evalsha_ro/">valkey.io</a> for details.
     * @param sha1 The SHA1 digest of the script to execute.
     * @param keys An array of keys accessed by the script.
     * @param args An array of script arguments.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * String sha1 = client.scriptLoad("return {KEYS[1], ARGV[1]}").get();
     * Object[] result = (Object[]) client.evalshaReadOnly(sha1, new String[]{"key1"}, new String[]{"arg1"}).get();
     * assert result[0].equals("key1");
     * assert result[1].equals("arg1");
     * }</pre>
     */
    CompletableFuture<Object> evalshaReadOnly(String sha1, String[] keys, String[] args);

    /**
     * Executes a read-only Lua script by its SHA1 digest.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/evalsha_ro/">valkey.io</a> for details.
     * @param sha1 The SHA1 digest of the script to execute.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * GlideString sha1 = client.scriptLoad(gs("return 'Hello from SHA!'")).get();
     * GlideString result = (GlideString) client.evalshaReadOnly(sha1).get();
     * assert result.equals(gs("Hello from SHA!"));
     * }</pre>
     */
    CompletableFuture<Object> evalshaReadOnly(GlideString sha1);

    /**
     * Executes a read-only Lua script by its SHA1 digest with keys and arguments.<br>
     * This command is routed depending on the client's {@link ReadFrom} strategy.
     *
     * @apiNote When in cluster mode, all <code>keys</code> must map to the same hash slot.
     * @since Valkey 7.0 and above.
     * @see <a href="https://valkey.io/commands/evalsha_ro/">valkey.io</a> for details.
     * @param sha1 The SHA1 digest of the script to execute.
     * @param keys An array of keys accessed by the script.
     * @param args An array of script arguments.
     * @return A value that depends on the script that was executed.
     * @example
     *     <pre>{@code
     * GlideString sha1 = client.scriptLoad(gs("return {KEYS[1], ARGV[1]}")).get();
     * Object[] result = (Object[]) client.evalshaReadOnly(sha1, new GlideString[]{gs("key1")}, new GlideString[]{gs("arg1")}).get();
     * assert result[0].equals(gs("key1"));
     * assert result[1].equals(gs("arg1"));
     * }</pre>
     */
    CompletableFuture<Object> evalshaReadOnly(
            GlideString sha1, GlideString[] keys, GlideString[] args);

    /**
     * Sets the debugging mode for executed scripts.
     *
     * @since Redis 3.2 and above.
     * @see <a href="https://valkey.io/commands/script-debug/">valkey.io</a> for details.
     * @param mode The debugging mode to set.
     * @return <code>OK</code>.
     * @example
     *     <pre>{@code
     * String response = client.scriptDebug(ScriptDebugMode.YES).get();
     * assert response.equals("OK");
     * }</pre>
     */
    CompletableFuture<String> scriptDebug(ScriptDebugMode mode);
}
