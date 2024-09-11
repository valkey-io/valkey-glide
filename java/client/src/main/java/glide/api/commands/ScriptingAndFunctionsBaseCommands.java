/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.GlideString;
import glide.api.models.configuration.ReadFrom;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Scripting and Function" group for standalone and
 * cluster clients.
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
     * @return The original source code of the script, if present in the cache.
     * @throws RequestException if the script is not found in the cache.
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
     * @return The original source code of the script, if present in the cache.
     * @throws RequestException if the script is not found in the cache.
     * @example
     *     <pre>{@code
     * String scriptSource = client.scriptShow(gs(luaScript.getHash())).get();
     * assert scriptSource.equals(gs("return { KEYS[1], ARGV[1] }"));
     * }</pre>
     */
    CompletableFuture<GlideString> scriptShow(GlideString sha1);
}
