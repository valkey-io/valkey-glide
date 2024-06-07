package glide.api.models.commands;

import glide.api.commands.SortedSetBaseCommands;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional arguments to {@link ListBaseCommands#lpos()},
 *
 * @see <a href="https://redis.io/commands/lpos/">redis.io</a>
 *//**
// * Optional arguments to {@link ListBaseCommands#lpos(String, Map, LPosOptions)},
// * {@link ListBaseCommands#lpos(String, LPosOptions)} and {@link
// * ListBaseCommands#zaddIncr(String, LPosOptions)}
 *
 * @see <a href="https://redis.io/commands/lpos/">redis.io</a>
 */
@Builder
public final class LPosOptions {

    private Long rank;
    private Long maxLength;
    private final String COUNT_REDIS_API = "COUNT";

    /**
     * Converts LPosOptions into a String[].
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();
        if (rank != null) {
            optionArgs.add("RANK" + Long.toString(rank));
        }

        if (maxLength != null) {
            optionArgs.add("MAXLEN" + Long.toString(maxLength));
        }

        return optionArgs.toArray(new String[0]);
    }

}
