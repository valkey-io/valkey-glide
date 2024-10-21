/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import static glide.api.models.GlideString.gs;

import glide.api.commands.servermodules.Json;
import glide.api.models.GlideString;
import lombok.Builder;
import lombok.NonNull;

/** Additional parameters for {@link Json#arrpop} command. */
@Builder
public class JsonArrPopOptionsBinary {

    /** The path within the JSON document. */
    @NonNull private final GlideString path;

    /**
     * The index of the element to pop. If not specified, will pop the last element.<br>
     * Out of boundary indexes are rounded to their respective array boundaries.
     */
    private final Integer index;

    /** Convert to module API. */
    public GlideString[] toArgs() {
        return index == null
                ? new GlideString[] {path}
                : new GlideString[] {path, gs(Integer.toString(index))};
    }
}
