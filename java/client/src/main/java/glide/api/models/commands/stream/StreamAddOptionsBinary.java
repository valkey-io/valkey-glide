/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import static glide.api.models.GlideString.gs;

import glide.api.commands.StreamBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;

/**
 * Optional arguments to {@link StreamBaseCommands#xadd(GlideString, Map, StreamAddOptionsBinary)}
 *
 * @see <a href="https://valkey.io/commands/xadd/">valkey.io</a>
 */
@Builder
public final class StreamAddOptionsBinary {
    public static final GlideString NO_MAKE_STREAM_VALKEY_API_GLIDE_STRING = gs("NOMKSTREAM");
    public static final GlideString ID_WILDCARD_VALKEY_API_GLIDE_STRING = gs("*");

    /** If set, the new entry will be added with this <code>id</code>. */
    private final GlideString id;

    /**
     * If set to <code>false</code>, a new stream won't be created if no stream matches the given key.
     * <br>
     * Equivalent to <code>NOMKSTREAM</code> in the Valkey API.
     */
    private final Boolean makeStream;

    /** If set, the add operation will also trim the older entries in the stream. */
    private final StreamTrimOptions trim;

    /**
     * Converts options for Xadd into a GlideString[].
     *
     * @return GlideString[]
     */
    public GlideString[] toArgs() {
        List<GlideString> optionArgs = new ArrayList<>();

        if (makeStream != null && !makeStream) {
            optionArgs.add(NO_MAKE_STREAM_VALKEY_API_GLIDE_STRING);
        }

        if (trim != null) {
            optionArgs.addAll(
                    trim.getValkeyApi().stream().map(GlideString::gs).collect(Collectors.toList()));
        }

        if (id != null) {
            optionArgs.add(id);
        } else {
            optionArgs.add(ID_WILDCARD_VALKEY_API_GLIDE_STRING);
        }

        return optionArgs.toArray(new GlideString[0]);
    }
}
