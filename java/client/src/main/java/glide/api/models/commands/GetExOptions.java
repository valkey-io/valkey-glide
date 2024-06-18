/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.StringBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;

/**
 * Optional arguments to {@link StringBaseCommands#getex(String, GetExOptions)} command.
 *
 * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a>
 * */
@Builder
public class GetExOptions {

    /** The specified expire time, in seconds. */
    private Long seconds;

    /** The specified expire time, in milliseconds. */
    private Long milliseconds;

    /** The specified Unix time at which the key will expire, in seconds. */
    private Long unix_time_seconds;

    /** The specified Unix time at which the key will expire, in milliseconds. */
    private Long unix_time_milliseconds;


    /** Redis API keyword used to set the specified expire time, in seconds. */
    public static final String SECONDS_REDIS_API = "EX";

    /** Redis API keyword used to set the specified expire time, in milliseconds. */
    public static final String MSECONDS_REDIS_API = "PX";

    /** Redis API keyword used to set the specified expire time, in Unix time, in seconds. */
    public static final String UXT_REDIS_API = "EXAT";

    /** Redis API keyword used to set the specified expire time, in Unix time, in milliseconds. */
    public static final String UXTMSECONDS_REDIS_API = "PXAT";

    /** Redis API keyword used to remove the time associated with the key. */
    public static final String PERSIST_REDIS_API = "PERSIST";

    /**
     * Converts GetExOptions into a String[].
     *
     * @return String[]
     * */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();
        if (seconds != null) {
            optionArgs.add(SECONDS_REDIS_API);
            optionArgs.add(String.valueOf(seconds));
        }

        if (milliseconds != null) {
            optionArgs.add(MSECONDS_REDIS_API);
            optionArgs.add(String.valueOf(milliseconds));
        }

        if (unix_time_seconds != null) {
            optionArgs.add(UXT_REDIS_API);
            optionArgs.add(String.valueOf(unix_time_seconds));
        }

        if (unix_time_milliseconds != null) {
            optionArgs.add(UXTMSECONDS_REDIS_API);
            optionArgs.add(String.valueOf(unix_time_milliseconds));
        }

        return optionArgs.toArray(new String[0]);
    }
}
