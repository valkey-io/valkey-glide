/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ScriptingAndFunctionsBaseCommands;

/**
 * Defines the debugging mode for executed scripts.
 *
 * @see ScriptingAndFunctionsBaseCommands#scriptDebug(ScriptDebugMode)
 */
public enum ScriptDebugMode {
    /**
     * Enable non-blocking asynchronous debugging mode. The server will fork a debugging session that
     * won't block the server.
     */
    YES,

    /**
     * Enable blocking synchronous debugging mode. The server will block and wait for commands from
     * the debugging client.
     */
    SYNC,

    /** Disable script debugging mode. */
    NO
}
