/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.GlideString.gs;

import glide.api.commands.GenericBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import lombok.Singular;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments to {@link GenericBaseCommands#sort(GlideString, SortOptionsBinary)}, {@link
 * GenericBaseCommands#sortReadOnly(GlideString, SortOptionsBinary)}, and {@link
 * GenericBaseCommands#sortStore(GlideString, String, SortOptionsBinary)}
 *
 * @apiNote In cluster mode, {@link #byPattern} and {@link #getPatterns} must map to the same hash
 *     slot as the key, and this is supported only since Valkey version 8.0.
 * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> and <a
 *     href="https://valkey.io/commands/sort/">valkey.io</a>
 */
@SuperBuilder
public class SortOptionsBinary extends SortBaseOptions {
    /**
     * <code>BY</code> subcommand string to include in the <code>SORT</code> and <code>SORT_RO</code>
     * commands. Supported in cluster mode since Valkey version 8.0 and above.
     */
    public static final GlideString BY_COMMAND_GLIDE_STRING = gs("BY");

    /**
     * <code>GET</code> subcommand string to include in the <code>SORT</code> and <code>SORT_RO</code>
     * commands. Supported in cluster mode since Valkey version 8.0 and above.
     */
    public static final GlideString GET_COMMAND_GLIDE_STRING = gs("GET");

    /**
     * A pattern to sort by external keys instead of by the elements stored at the key themselves. The
     * pattern should contain an asterisk (*) as a placeholder for the element values, where the value
     * from the key replaces the asterisk to create the key name. For example, if <code>key</code>
     * contains IDs of objects, <code>byPattern</code> can be used to sort these IDs based on an
     * attribute of the objects, like their weights or timestamps. Supported in cluster mode since
     * Valkey version 8.0 and above.
     */
    private final GlideString byPattern;

    /**
     * A pattern used to retrieve external keys' values, instead of the elements at <code>key</code>.
     * The pattern should contain an asterisk (*) as a placeholder for the element values, where the
     * value from <code>key</code> replaces the asterisk to create the <code>key</code> name. This
     * allows the sorted elements to be transformed based on the related keys values. For example, if
     * <code>key</code> contains IDs of users, <code>getPatterns</code> can be used to retrieve
     * specific attributes of these users, such as their names or email addresses. E.g., if <code>
     * getPatterns</code> is <code>name_*</code>, the command will return the values of the keys
     * <code>name_&lt;element&gt;</code> for each sorted element. Multiple <code>getPatterns</code>
     * arguments can be provided to retrieve multiple attributes. The special value <code>#</code> can
     * be used to include the actual element from <code>key</code> being sorted. If not provided, only
     * the sorted elements themselves are returned.<br>
     * Supported in cluster mode since Valkey version 8.0 and above.
     *
     * @see <a href="https://valkey.io/commands/sort/">valkey.io</a> for more information.
     */
    @Singular private final List<GlideString> getPatterns;

    /**
     * Creates the arguments to be used in <code>SORT</code> and <code>SORT_RO</code> commands.
     *
     * @return a String array that holds the sub commands and their arguments.
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>(List.of(super.toArgs()));

        if (byPattern != null) {
            optionArgs.addAll(List.of(BY_COMMAND_GLIDE_STRING.toString(), byPattern.toString()));
        }

        if (getPatterns != null) {
            getPatterns.stream()
                    .forEach(
                            getPattern ->
                                    optionArgs.addAll(
                                            List.of(GET_COMMAND_GLIDE_STRING.toString(), getPattern.toString())));
        }

        return optionArgs.toArray(new String[0]);
    }
}
