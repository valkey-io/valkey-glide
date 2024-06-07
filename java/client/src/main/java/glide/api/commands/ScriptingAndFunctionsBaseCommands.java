/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Scripting and Function" group for standalone and
 * cluster clients.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=scripting">Scripting and Function
 *     Commands</a>
 */
public interface ScriptingAndFunctionsBaseCommands {

    /**
     * Invokes a previously loaded function.
     *
     * @apiNote When in cluster mode
     *     <ul>
     *       <li>all <code>keys</code> must map to the same hash slot.
     *       <li>if no <code>keys</code> are given, command will be routed to a random node.
     *     </ul>
     *
     * @since Redis 7.0 and above.
     * @see <a href="https://redis.io/docs/latest/commands/fcall/">redis.io</a> for details.
     * @param function The function name.
     * @param keys An <code>array</code> of keys accessed by the function.
     * @param arguments An <code>array</code> of <code>function</code> arguments.
     * @return The invoked function's return value.
     * @example
     *     <pre>{@code
     * String[] args = new String[] { "Answer", "to", "the", "Ultimate", "Question", "of", "Life,", "the", "Universe,", "and", "Everything"};
     * Object response = client.fcall("Deep_Thought", new String[0], args).get();
     * assert Object == 42L;
     * }</pre>
     */
    CompletableFuture<Object> fcall(String function, String[] keys, String[] arguments);
}
