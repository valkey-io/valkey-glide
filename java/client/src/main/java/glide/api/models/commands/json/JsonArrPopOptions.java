/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.json;

import glide.api.commands.servermodules.Json;
import lombok.Builder;
import lombok.NonNull;

/** Additional parameters for {@link Json#arrpop} command. */
@Builder
public class JsonArrPopOptions {

    /** The path within the JSON document. */
    @NonNull private final String path;

    /**
     * The index of the element to pop. If not specified, will pop the last element.<br>
     * Out of boundary indexes are rounded to their respective array boundaries.
     */
    private final Integer index;

    /** Convert to module API. */
    public String[] toArgs() {
        return index == null ? new String[] {path} : new String[] {path, Integer.toString(index)};
    }
}
