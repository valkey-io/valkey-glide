/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands.stream;

import static glide.api.models.GlideString.gs;

import glide.api.commands.StreamBaseCommands;
import glide.api.models.GlideString;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/**
 * Arguments for {@link StreamBaseCommands#xpending} to specify additional filter items by idle time
 * and consumer.
 *
 * @see <a href="https://valkey.io/commands/xpending/">valkey.io</a>
 */
@Builder
public class StreamPendingOptionsBinary {

    /** Valkey api string to designate IDLE or minimum idle time */
    public static final GlideString IDLE_TIME_VALKEY_API_GLIDE_STRING = gs("IDLE");

    /** Filters pending entries by their idle time - in Milliseconds */
    private final Long minIdleTime; // Milliseconds

    /** Filters pending entries by consumer */
    private final GlideString consumer;

    /**
     * Convert StreamPendingOptions arguments to a string array
     *
     * @return arguments converted to an array to be consumed by Valkey
     */
    public GlideString[] toArgs(StreamRange start, StreamRange end, long count) {
        List<GlideString> optionArgs = new ArrayList<>();
        if (minIdleTime != null) {
            optionArgs.add(IDLE_TIME_VALKEY_API_GLIDE_STRING);
            optionArgs.add(gs(Long.toString(minIdleTime)));
        }

        optionArgs.add(gs(start.getValkeyApi()));
        optionArgs.add(gs(end.getValkeyApi()));
        optionArgs.add(gs(Long.toString(count)));

        if (consumer != null) {
            optionArgs.add(consumer);
        }

        return optionArgs.toArray(new GlideString[0]);
    }
}
