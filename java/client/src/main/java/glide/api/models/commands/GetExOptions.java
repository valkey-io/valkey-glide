/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.commands.GetExOptions.ExpiryType.MILLISECONDS;
import static glide.api.models.commands.GetExOptions.ExpiryType.SECONDS;
import static glide.api.models.commands.GetExOptions.ExpiryType.UNIX_MILLISECONDS;
import static glide.api.models.commands.GetExOptions.ExpiryType.UNIX_SECONDS;

import glide.api.commands.StringBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * Optional arguments to {@link StringBaseCommands#getex(String, GetExOptions)} command.
 *
 * @see <a href="https://redis.io/docs/latest/commands/getex/">redis.io</a>
 */
@Builder
public class GetExOptions {

    private final Expiry expiry;

    /** Redis API keyword used to remove the time associated with the key. */
    public static final String PERSIST_REDIS_API = "PERSIST";

    public static final class Expiry {
        /** Expiry type for the time to live */
        private final ExpiryType type;

        /** The amount of time to live before the key expires. */
        private Long count;

        private Expiry(ExpiryType type) {
            this.type = type;
        }

        private Expiry(ExpiryType type, Long count) {
            this.type = type;
            this.count = count;
        }

        /**
         * Set the specified expire time, in seconds. Equivalent to <code>EX</code> in the Redis API.
         *
         * @param seconds time to expire, in seconds
         * @return Expiry
         */
        public static Expiry Seconds(Long seconds) {
            return new Expiry(SECONDS, seconds);
        }

        /**
         * Set the specified expire time, in milliseconds. Equivalent to <code>PX</code> in the Redis
         * API.
         *
         * @param milliseconds time to expire, in milliseconds
         * @return Expiry
         */
        public static Expiry Milliseconds(Long milliseconds) {
            return new Expiry(MILLISECONDS, milliseconds);
        }

        /**
         * Set the specified Unix time at which the key will expire, in seconds. Equivalent to <code>
         * EXAT</code> in the Redis API.
         *
         * @param unixSeconds <code>UNIX TIME</code> to expire, in seconds.
         * @return Expiry
         */
        public static Expiry UnixSeconds(Long unixSeconds) {
            return new Expiry(UNIX_SECONDS, unixSeconds);
        }

        /**
         * Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to
         * <code>PXAT</code> in the Redis API.
         *
         * @param unixMilliseconds <code>UNIX TIME</code> to expire, in milliseconds.
         * @return Expiry
         */
        public static Expiry UnixMilliseconds(Long unixMilliseconds) {
            return new Expiry(UNIX_MILLISECONDS, unixMilliseconds);
        }
    }

    /** Types of value expiration configuration. */
    @RequiredArgsConstructor
    protected enum ExpiryType {
        SECONDS("EX"),
        MILLISECONDS("PX"),
        UNIX_SECONDS("EXAT"),
        UNIX_MILLISECONDS("PXAT");

        private final String redisApi;
    }

    /**
     * Converts GetExOptions into a String[] to add to a {@link GetExOptions} arguments.
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();

        if (expiry != null) {
            optionArgs.add(expiry.type.redisApi);
            optionArgs.add(String.valueOf(expiry.count));
        }

        if (expiry == null) {
            optionArgs.add(PERSIST_REDIS_API);
        }

        return optionArgs.toArray(new String[0]);
    }
}
