/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.ScriptingAndFunctionsBaseCommands;
import glide.api.models.Script;
import java.util.List;
import lombok.Getter;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link ScriptingAndFunctionsBaseCommands#invokeScript(Script,
 * ScriptOptions)} command.
 *
 * @see <a href="https://valkey.io/commands/evalsha/">valkey.io</a>
 */
@SuperBuilder
public final class ScriptOptions extends ScriptArgOptions {

    /** The keys that are used in the script. */
    @Singular @Getter private final List<String> keys;
}
