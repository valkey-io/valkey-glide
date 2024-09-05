/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.SortedSetBaseCommands;
import glide.api.models.GlideString;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link SortedSetBaseCommands#zscan(String, String, ZScanOptions)}.
 *
 * @see <a href="https://valkey.io/commands/zscan/">valkey.io</a>
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class ZScanOptions extends BaseScanOptions {

    /** Option string to include in the ZSCAN command when scores are not included. */
    public static final String NO_SCORES_API = "NOSCORES";

    /**
     * When set to true, the command will not include scores in the results. This option is available
     * from Valkey version 8.0.0 and above.
     */
    @Builder.Default protected boolean noScores = false;

    @Override
    public String[] toArgs() {
        return Arrays.stream(toGlideStringArgs()).map(GlideString::getString).toArray(String[]::new);
    }

    /**
     * Creates the arguments to be used in <code>ZSCAN</code> commands.
     *
     * @return a GlideString array that holds the options and their arguments.
     */
    @Override
    public GlideString[] toGlideStringArgs() {
        ArgsBuilder builder = new ArgsBuilder();

        // Add options from the superclass
        GlideString[] superArgs = super.toGlideStringArgs();
        for (GlideString arg : superArgs) {
            builder.add(arg);
        }

        // Add the noScores option if applicable
        if (noScores) {
            builder.add(NO_SCORES_API);
        }

        return builder.toArray();
    }
}
