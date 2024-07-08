/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import static glide.api.models.GlideString.gs;

import glide.api.commands.StreamBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.SuperBuilder;

/**
 * Optional arguments for {@link StreamBaseCommands#xreadgroup(Map, String, String,
 * StreamReadGroupOptions)} and {@link StreamBaseCommands#xreadgroup(Map, GlideString, GlideString,
 * StreamReadGroupOptions)}
 *
 * @see <a href="https://valkey.io/commands/xreadgroup/">valkey.io</a>
 */
@SuperBuilder
public final class StreamReadGroupOptions extends StreamReadOptions {

    public static final String READ_GROUP_VALKEY_API = "GROUP";
    public static final String READ_NOACK_VALKEY_API = "NOACK";

    /**
     * If set, messages are not added to the Pending Entries List (PEL). This is equivalent to
     * acknowledging the message when it is read.
     */
    private boolean noack;

    public abstract static class StreamReadGroupOptionsBuilder<
                    C extends StreamReadGroupOptions, B extends StreamReadGroupOptionsBuilder<C, B>>
            extends StreamReadOptions.StreamReadOptionsBuilder<C, B> {
        public B noack() {
            this.noack = true;
            return self();
        }
    }

    /**
     * Converts options and the key-to-id input for {@link StreamBaseCommands#xreadgroup(Map, ArgType,
     * ArgType, StreamReadGroupOptions)} into a GlideString[].
     *
     * @return GlideString[]
     */
    public <ArgType> GlideString[] toArgs(ArgType group, ArgType consumer) {
        List<GlideString> optionArgs = new ArrayList<>();
        optionArgs.add(GlideString.of(READ_GROUP_VALKEY_API));
        optionArgs.add(GlideString.of(group));
        optionArgs.add(GlideString.of(consumer));

        if (this.count != null) {
            optionArgs.add(GlideString.of(READ_COUNT_VALKEY_API));
            optionArgs.add(GlideString.of(count.toString()));
        }

        if (this.block != null) {
            optionArgs.add(GlideString.of(READ_BLOCK_VALKEY_API));
            optionArgs.add(GlideString.of(block.toString()));
        }

        if (this.noack) {
            optionArgs.add(GlideString.of(READ_NOACK_VALKEY_API));
        }

        optionArgs.add(GlideString.of(READ_STREAMS_VALKEY_API));
        return optionArgs.toArray(new GlideString[0]);
    }

    /**
     * Converts options and the key-to-id input for {@link StreamBaseCommands#xreadgroup(Map, String,
     * String, StreamReadGroupOptions)} into a String[].
     *
     * @return String[]
     */
    public String[] toArgs(String group, String consumer, Map<String, String> streams) {
        List<String> optionArgs = new ArrayList<>();
        optionArgs.add(READ_GROUP_VALKEY_API);
        optionArgs.add(group);
        optionArgs.add(consumer);

        if (this.count != null) {
            optionArgs.add(READ_COUNT_VALKEY_API);
            optionArgs.add(count.toString());
        }

        if (this.block != null) {
            optionArgs.add(READ_BLOCK_VALKEY_API);
            optionArgs.add(block.toString());
        }

        if (this.noack) {
            optionArgs.add(READ_NOACK_VALKEY_API);
        }

        optionArgs.add(READ_STREAMS_VALKEY_API);
        Set<Map.Entry<String, String>> entrySet = streams.entrySet();
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getValue).collect(Collectors.toList()));

        return optionArgs.toArray(new String[0]);
    }

    /**
     * Converts options and the key-to-id input for {@link StreamBaseCommands#xreadgroupBinary(Map,
     * GlideString, GlideString, StreamReadGroupOptions)} into a GlideString[].
     *
     * @return GlideString[]
     */
    public GlideString[] toArgsBinary(
            GlideString group, GlideString consumer, Map<GlideString, GlideString> streams) {
        List<GlideString> optionArgs = new ArrayList<>();
        optionArgs.add(gs(READ_GROUP_VALKEY_API));
        optionArgs.add(group);
        optionArgs.add(consumer);

        if (this.count != null) {
            optionArgs.add(gs(READ_COUNT_VALKEY_API));
            optionArgs.add(gs(count.toString()));
        }

        if (this.block != null) {
            optionArgs.add(gs(READ_BLOCK_VALKEY_API));
            optionArgs.add(gs(block.toString()));
        }

        if (this.noack) {
            optionArgs.add(gs(READ_NOACK_VALKEY_API));
        }

        optionArgs.add(gs(READ_STREAMS_VALKEY_API));
        Set<Map.Entry<GlideString, GlideString>> entrySet = streams.entrySet();
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getKey).collect(Collectors.toList()));
        optionArgs.addAll(entrySet.stream().map(Map.Entry::getValue).collect(Collectors.toList()));

        return optionArgs.toArray(new GlideString[0]);
    }
}
