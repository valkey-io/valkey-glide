/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;
import java.util.ArrayList;
import java.util.List;

/** Additional parameters for {@link Json#arrindex} command. */
public final class JsonArrindexOptions {

    /** The start index, inclusive. Default to <code>0</code>. */
    private Long start;

    /** The end index, exclusive. */
    private Long end;

    /**
     * Search using a start index (is inclusive). Defaults to <code>0</code> if not provided. Indices
     * that exceed the array bounds are automatically adjusted to the nearest valid position.
     */
    public JsonArrindexOptions(Long start) {
        this.start = start;
    }

    /**
     * Search using a start index (is inclusive) and end index (is exclusive). If <code>start</code>
     * is greater than <code>end</code>, the command returns <code>-1</code> to indicate that the
     * value was not found. Indices that exceed the array bounds are automatically adjusted to the
     * nearest valid position.
     */
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
