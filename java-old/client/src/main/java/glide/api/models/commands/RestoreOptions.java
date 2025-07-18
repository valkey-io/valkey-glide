/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.GlideString.gs;

import glide.api.commands.GenericBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

/**
 * Optional arguments to {@link GenericBaseCommands#restore(GlideString, long, byte[],
 * RestoreOptions)}.
 *
 * @see <a href="https://valkey.io/commands/restore/">valkey.io</a>
 * @apiNote <code>IDLETIME</code> and <code>FREQ</code> modifiers cannot be set at the same time.
 */
@Getter
@Builder
public final class RestoreOptions {
    /** <code>REPLACE</code> subcommand string to replace existing key */
    public static final String REPLACE_VALKEY_API = "REPLACE";

    /**
     * <code>ABSTTL</code> subcommand string to represent absolute timestamp (in milliseconds) for TTL
     */
    public static final String ABSTTL_VALKEY_API = "ABSTTL";

    /** <code>IDELTIME</code> subcommand string to set Object Idletime */
    public static final String IDLETIME_VALKEY_API = "IDLETIME";

    /** <code>FREQ</code> subcommand string to set Object Frequency */
    public static final String FREQ_VALKEY_API = "FREQ";

    /** When `true`, it represents <code>REPLACE</code> keyword has been used */
    @Builder.Default private boolean hasReplace = false;

    /** When `true`, it represents <code>ABSTTL</code> keyword has been used */
    @Builder.Default private boolean hasAbsttl = false;

    /** It represents the idletime of object */
    @Builder.Default private Long idletime = null;

    /** It represents the frequency of object */
    @Builder.Default private Long frequency = null;

    /**
     * Creates the argument to be used in {@link GenericBaseCommands#restore(GlideString, long,
     * byte[], RestoreOptions)}
     *
     * @return a <code>GlideString</code> array that holds the subcommands and their arguments.
     */
    public GlideString[] toArgs() {
        List<GlideString> resultList = new ArrayList<>();

        if (hasReplace) {
            resultList.add(gs(REPLACE_VALKEY_API));
        }

        if (hasAbsttl) {
            resultList.add(gs(ABSTTL_VALKEY_API));
        }

        if (idletime != null && frequency != null) {
            throw new IllegalArgumentException("IDLETIME and FREQ cannot be set at the same time.");
        }

        if (idletime != null) {
            resultList.add(gs(IDLETIME_VALKEY_API));
            resultList.add(gs(Long.toString(idletime)));
        }

        if (frequency != null) {
            resultList.add(gs(FREQ_VALKEY_API));
            resultList.add(gs(Long.toString(frequency)));
        }

        return resultList.toArray(new GlideString[0]);
    }

    /** Custom setter methods for replace and absttl */
    public static class RestoreOptionsBuilder {
        public RestoreOptionsBuilder replace() {
            return hasReplace(true);
        }

        public RestoreOptionsBuilder absttl() {
            return hasAbsttl(true);
        }
    }
}
