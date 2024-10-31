/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/** Additional parameters for {@link Json#arrindex} command. */
@Builder
public final class JsonIndexOptions {

    /** The start index, inclusive. Default to <code>0</code> if not provided. */
    private long start;

    /** The end index, exclusive. Default to <code>0</code> if not provided. */
    private long end;

    /**
     * Converts JsonGetOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> args = new ArrayList<>();

        if (Long.valueOf(start) != null) {
            args.add(Long.valueOf(start).toString());
        }

        if (Long.valueOf(end) != null) {
            args.add(Long.valueOf(end).toString());
        }

        return args.toArray(new String[0]);
    }
}
