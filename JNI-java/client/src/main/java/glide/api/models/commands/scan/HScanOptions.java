/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import glide.api.commands.HashBaseCommands;
import glide.api.models.GlideString;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link HashBaseCommands#hscan(String, String, HScanOptions)}.
 *
 * @see <a href="https://valkey.io/commands/hscan/">valkey.io</a>
 */
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class HScanOptions extends BaseScanOptions {

    /** Option string to include in the HSCAN command when values are not included. */
    public static final String NO_VALUES_API = "NOVALUES";

    /**
     * When set to true, the command will not include values in the results. This option is available
     * from Valkey version 8.0.0 and above.
     */
    @Builder.Default protected boolean noValues = false;

    @Override
    public String[] toArgs() {
        return Arrays.stream(toGlideStringArgs()).map(GlideString::getString).toArray(String[]::new);
    }

    /**
     * Creates the arguments to be used in <code>HSCAN</code> commands.
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

        // Add the noValues option if applicable
        if (noValues) {
            builder.add(NO_VALUES_API);
        }

        return builder.toArray();
    }
}
