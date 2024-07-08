/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import static glide.api.models.GlideString.gs;

import glide.api.commands.StreamBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link StreamBaseCommands#xread(Map, StreamReadOptions)}
 *
 * @see <a href="https://valkey.io/commands/xread/">valkey.io</a>
 */
@SuperBuilder
public class StreamReadOptions {

    public static final String READ_COUNT_VALKEY_API = "COUNT";
    public static final String READ_BLOCK_VALKEY_API = "BLOCK";
    public static final String READ_STREAMS_VALKEY_API = "STREAMS";

    /**
     * If set, the request will be blocked for the set amount of milliseconds or until the server has
     * the required number of entries. Equivalent to <code>BLOCK</code> in the Valkey API.
     */
    protected Long block;

    /**
     * The maximal number of elements requested. Equivalent to <code>COUNT</code> in the Valkey API.
     */
    protected Long count;

    /**
     * Converts options and the key-to-id input for {@link StreamBaseCommands#xread(Map,
     * StreamReadOptions)} into a String[].
     *
     * @return String[]
     */
    public String[] toArgs(Map<String, String> streams) {
        List<String> optionArgs = new ArrayList<>();

        if (this.count != null) {
            optionArgs.add(READ_COUNT_VALKEY_API);
            optionArgs.add(count.toString());
        }

        if (this.block != null) {
            optionArgs.add(READ_BLOCK_VALKEY_API);
            optionArgs.add(block.toString());
        }

        optionArgs.add(READ_STREAMS_VALKEY_API);
        Set<Map.Entry<String, String>> entrySet = streams.entrySet();
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getValue).collect(Collectors.toList()));

        return optionArgs.toArray(new String[0]);
    }

    /**
     * Converts options and the key-to-id input for {@link StreamBaseCommands#xread(Map,
     * StreamReadOptions)} into a GlideString[].
     *
     * @return GlideString[]
     */
    public GlideString[] toArgsBinary(Map<GlideString, GlideString> streams) {
        List<GlideString> optionArgs = new ArrayList<>();

        if (this.count != null) {
            optionArgs.add(gs(READ_COUNT_VALKEY_API));
            optionArgs.add(gs(count.toString()));
        }

        if (this.block != null) {
            optionArgs.add(gs(READ_BLOCK_VALKEY_API));
            optionArgs.add(gs(block.toString()));
        }

        optionArgs.add(gs(READ_STREAMS_VALKEY_API));
        Set<Map.Entry<GlideString, GlideString>> entrySet = streams.entrySet();
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getValue).collect(Collectors.toList()));

        return optionArgs.toArray(new GlideString[0]);
    }

    /**
     * Converts options into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        Map<String, String> emptyMap = new HashMap<>();
        return toArgs(emptyMap);
    }

    /**
     * Converts options into a GlideString[].
     *
     * @return GlideString[]
     */
    public GlideString[] toArgsBinary() {
        Map<GlideString, GlideString> emptyMap = new HashMap<>();
        return toArgsBinary(emptyMap);
    }
}
