/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;
import java.util.ArrayList;
import java.util.List;

/** Additional parameters for {@link Json#arrindex} command. */
public final class JsonArrindexOptions {

    /** The start index, inclusive. Default to <code>0</code> if not provided. */
    private Long start;

    /** The end index, exclusive. Default to <code>0</code> if not provided. */
    private Long end;

    /** Constructs the JsonArrindexOptions object with only the start index */
    public JsonArrindexOptions(Long start) {
        this.start = start;
    }

    /** Constructs the JsonArrindexOptions object with the start and end index */
    public JsonArrindexOptions(Long start, Long end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Converts JsonArrindexOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> args = new ArrayList<>();

        if (start != null) {
            args.add(start.toString());

            if (end != null) {
                args.add(end.toString());
            }
        }

        return args.toArray(new String[0]);
    }
}
