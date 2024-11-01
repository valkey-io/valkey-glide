/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import static glide.api.models.GlideString.gs;

import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;

/** Additional parameters for {@link Json#arrindex} command. */
public final class JsonArrIndexOptionsBinary {

    /** The start index, inclusive. Default to <code>0</code> if not provided. */
    private Long start;

    /** The end index, exclusive. Default to <code>0</code> if not provided. */
    private Long end;

    public JsonArrIndexOptionsBinary(Long start) {
        this.start = start;
    }

    public JsonArrIndexOptionsBinary(Long start, Long end) {
        this.start = start;
        this.end = end;
    }

    /**
     * Converts JsonGetOptions into a GlideString[].
     *
     * @return GlideString[]
     */
    public GlideString[] toArgs() {
        List<GlideString> args = new ArrayList<>();

        if (start != null) {
            args.add(gs(start.toString()));

            if (end != null) {
                args.add(gs(end.toString()));
            }
        }
        return args.toArray(new GlideString[0]);
    }
}
