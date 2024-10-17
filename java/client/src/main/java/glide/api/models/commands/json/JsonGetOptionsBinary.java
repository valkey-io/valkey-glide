/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import static glide.api.models.GlideString.gs;

import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/** GlideString version of additional parameters for {@link Json#get} command. */
@Builder
public final class JsonGetOptionsBinary {
    /** ValKey API string to designate INDENT */
    public static final GlideString INDENT_VALKEY_API = gs("INDENT");

    /** ValKey API string to designate NEWLINE */
    public static final GlideString NEWLINE_VALKEY_API = gs("NEWLINE");

    /** ValKey API string to designate SPACE */
    public static final GlideString SPACE_VALKEY_API = gs("SPACE");

    /** ValKey API string to designate SPACE */
    public static final GlideString NOESCAPE_VALKEY_API = gs("NOESCAPE");

    /** Sets an indentation string for nested levels. */
    private GlideString indent;

    /** Sets a string that's printed at the end of each line. */
    private GlideString newline;

    /** Sets a string that's put between a key and a value. */
    private GlideString space;

    /** Allowed to be present for legacy compatibility and has no other effect. */
    private boolean noescape;

    /**
     * Converts JsonGetOptions into a GlideString[].
     *
     * @return GlideString[]
     */
    public GlideString[] toArgs() {
        List<GlideString> args = new ArrayList<>();
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

        return args.toArray(new GlideString[0]);
    }
}
