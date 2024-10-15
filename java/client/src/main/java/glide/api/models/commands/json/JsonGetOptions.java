/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.GlideJson;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Additional parameters for {@link GlideJson#get} command. */
@Getter
@Builder
public final class JsonGetOptions {
    /** ValKey API string to designate INDENT */
    public static final String INDENT_VALKEY_API = "INDENT";

    /** ValKey API string to designate NEWLINE */
    public static final String NEWLINE_VALKEY_API = "NEWLINE";

    /** ValKey API string to designate SPACE */
    public static final String SPACE_VALKEY_API = "SPACE";

    /** Sets an indentation string for nested levels. Defaults to null. */
    @Builder.Default private String indent = null;

    /** Sets a string that's printed at the end of each line. Defaults to null. */
    @Builder.Default private String newline = null;

    /** Sets a string that's put between a key and a value. Defaults to null. */
    @Builder.Default private String space = null;

    /**
     * Converts JsonGetOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> resultList = new ArrayList<>();
        if (indent != null) {
            resultList.add(INDENT_VALKEY_API);
            resultList.add(indent);
        }

        if (newline != null) {
            resultList.add(NEWLINE_VALKEY_API);
            resultList.add(newline);
        }

        if (space != null) {
            resultList.add(SPACE_VALKEY_API);
            resultList.add(space);
        }

        return resultList.toArray(new String[0]);
    }
}
