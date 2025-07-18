/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/** Additional parameters for {@link Json#get} command. */
@Builder
public final class JsonGetOptions {
    /** ValKey API string to designate INDENT */
    public static final String INDENT_VALKEY_API = "INDENT";

    /** ValKey API string to designate NEWLINE */
    public static final String NEWLINE_VALKEY_API = "NEWLINE";

    /** ValKey API string to designate SPACE */
    public static final String SPACE_VALKEY_API = "SPACE";

    /** ValKey API string to designate SPACE */
    public static final String NOESCAPE_VALKEY_API = "NOESCAPE";

    /** Sets an indentation string for nested levels. */
    private String indent;

    /** Sets a string that's printed at the end of each line. */
    private String newline;

    /** Sets a string that's put between a key and a value. */
    private String space;

    /** Allowed to be present for legacy compatibility and has no other effect. */
    private boolean noescape;

    /**
     * Converts JsonGetOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> args = new ArrayList<>();
        if (indent != null) {
            args.add(INDENT_VALKEY_API);
            args.add(indent);
        }

        if (newline != null) {
            args.add(NEWLINE_VALKEY_API);
            args.add(newline);
        }

        if (space != null) {
            args.add(SPACE_VALKEY_API);
            args.add(space);
        }

        if (noescape) {
            args.add(NOESCAPE_VALKEY_API);
        }

        return args.toArray(new String[0]);
    }
}
