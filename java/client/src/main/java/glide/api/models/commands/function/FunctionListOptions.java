/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.function;

import glide.api.commands.ScriptingAndFunctionsClusterCommands;
import glide.api.commands.ScriptingAndFunctionsCommands;

/**
 * Option for {@link ScriptingAndFunctionsCommands#functionList} and {@link
 * ScriptingAndFunctionsClusterCommands#functionList} command.
 *
 * @see <a href="https://valkey.io/commands/function-list/">valkey.io</a>
 */
public class FunctionListOptions {
    /** Causes the server to include the libraries source implementation in the reply. */
    public static final String WITH_CODE_VALKEY_API = "WITHCODE";

    /** Valkey API keyword followed by library name pattern. */
    public static final String LIBRARY_NAME_VALKEY_API = "LIBRARYNAME";
}
