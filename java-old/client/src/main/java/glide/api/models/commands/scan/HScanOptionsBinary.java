/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.scan;

import static glide.api.models.GlideString.gs;

import glide.api.commands.HashBaseCommands;
import glide.api.models.GlideString;
import glide.utils.ArgsBuilder;
import java.util.Arrays;
import lombok.Builder;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link HashBaseCommands#hscan(GlideString, GlideString,
 * HScanOptionsBinary)}.
 *
 * @see <a href="https://valkey.io/commands/hscan/">valkey.io</a>
 */
@SuperBuilder
public class HScanOptionsBinary extends BaseScanOptionsBinary {
    /** Option string to include in the HSCAN command when values are not included. */
    public static final GlideString NO_VALUES_API = gs("NOVALUES");

    /**
     * When set to true, the command will not include values in the results. This option is available
     * from Valkey version 8.0.0 and above.
     */
    @Builder.Default protected boolean noValues = false;

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

        // Add the noValues option if applicable
        if (noValues) {
            builder.add(NO_VALUES_API.toString());
        }

        return Arrays.stream(builder.toArray()).map(GlideString::getString).toArray(String[]::new);
    }
}
