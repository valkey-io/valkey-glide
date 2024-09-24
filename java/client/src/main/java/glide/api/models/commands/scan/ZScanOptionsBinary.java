/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import static glide.api.models.GlideString.gs;

import glide.api.commands.SortedSetBaseCommands;
import glide.api.models.GlideString;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link SortedSetBaseCommands#zscan(GlideString, GlideString,
 * ZScanOptionsBinary)}.
 *
 * @see <a href="https://valkey.io/commands/zscan/">valkey.io</a>
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ZScanOptionsBinary extends BaseScanOptionsBinary {
    /** Option string to include in the ZSCAN command when scores are not included. */
    public static final GlideString NO_SCORES_API = gs("NOSCORES");

    /**
     * When set to true, the command will not include scores in the results. This option is available
     * from Valkey version 8.0.0 and above.
     */
    @Builder.Default protected boolean noScores = false;

    /**
     * Creates the arguments to be used in <code>ZSCAN</code> commands.
     *
     * @return a String array that holds the options and their arguments.
     */
    @Override
    public String[] toArgs() {
        ArgsBuilder builder = new ArgsBuilder();

        // Add options from the superclass
        String[] superArgs = super.toArgs();
        for (String arg : superArgs) {
            builder.add(arg);
        }

        // Add the noScores option if applicable
        if (noScores) {
            builder.add(NO_SCORES_API.toString());
        }

        return Arrays.stream(builder.toArray()).map(GlideString::getString).toArray(String[]::new);
    }
}
