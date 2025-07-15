/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.function;

/**
 * Option for <code>FUNCTION LOAD</code> command.
 *
 * @see <a href="https://valkey.io/commands/function-load/">valkey.io</a>
 */
public enum FunctionLoadOptions {
    /** Changes command behavior to overwrite the existing library with the new contents. */
    REPLACE
}
