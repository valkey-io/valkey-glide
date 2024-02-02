/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.commands.StringCommands;
import glide.api.models.exceptions.RequestException;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import redis_request.RedisRequestOuterClass.Command;

/**
 * Optional arguments for {@link StringCommands#set(String, String, SetOptions)} command.
 *
 * @see <a href="https://redis.io/commands/set/">redis.io</a>
 */
@Builder
public final class SetOptions {

    /**
     * If <code>conditionalSet</code> is not set the value will be set regardless of prior value
     * existence. If value isn't set because of the condition, command will return <code>null</code>.
     */
    private final ConditionalSet conditionalSet;

    /**
     * Set command to return the old string stored at <code>key</code>, or <code>null</code> if <code>
     * key</code> did not exist. An error is returned and <code>SET</code> aborted if the value stored
     * at <code>key</code> is not a string. Equivalent to <code>GET</code> in the Redis API.
     */
    private final boolean returnOldValue;

    /** If not set, no expiry time will be set for the value. */
    private final TimeToLive expiry;

    /** Conditions which define whether new value should be set or not. */
    @RequiredArgsConstructor
    @Getter
    public enum ConditionalSet {
        /**
         * Only set the key if it does not already exist. Equivalent to <code>XX</code> in the Redis
         * API.
         */
        ONLY_IF_EXISTS("XX"),
        /** Only set the key if it already exists. Equivalent to <code>NX</code> in the Redis API. */
        ONLY_IF_DOES_NOT_EXIST("NX");

        private final String redisApi;
    }

    /** Configuration of value lifetime. */
    @Builder
    public static final class TimeToLive {
        /** Expiry type for the time to live */
        @NonNull private TimeToLiveType type;

        /**
         * The amount of time to live before the key expires. Ignored when {@link
         * TimeToLiveType#KEEP_EXISTING} type is set.
         */
        private Integer count;
    }

    /** Types of value expiration configuration. */
    @RequiredArgsConstructor
    @Getter
    public enum TimeToLiveType {
        /**
         * Retain the time to live associated with the key. Equivalent to <code>KEEPTTL</code> in the
         * Redis API.
         */
        KEEP_EXISTING("KEEPTTL"),
        /**
         * Set the specified expire time, in seconds. Equivalent to <code>EX</code> in the Redis API.
         */
        SECONDS("EX"),
        /**
         * Set the specified expire time, in milliseconds. Equivalent to <code>PX</code> in the Redis
         * API.
         */
        MILLISECONDS("PX"),
        /**
         * Set the specified Unix time at which the key will expire, in seconds. Equivalent to <code>
         * EXAT</code> in the Redis API.
         */
        UNIX_SECONDS("EXAT"),
        /**
         * Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to
         * <code>PXAT</code> in the Redis API.
         */
        UNIX_MILLISECONDS("PXAT");

        private final String redisApi;
    }

    /** String representation of {@link #returnOldValue} when set. */
    public static final String RETURN_OLD_VALUE = "GET";

    /**
     * Converts SetOptions into a String[] to add to a {@link Command} arguments.
     *
     * @return String[]
     */
    public String[] toArgs() {
        List<String> optionArgs = new ArrayList<>();
        if (conditionalSet != null) {
            optionArgs.add(conditionalSet.redisApi);
        }

        if (returnOldValue) {
            optionArgs.add(RETURN_OLD_VALUE);
        }

        if (expiry != null) {
            optionArgs.add(expiry.type.redisApi);
            if (expiry.type != TimeToLiveType.KEEP_EXISTING) {
                if (expiry.count == null) {
                    throw new RequestException(
                            "Set command received expiry type " + expiry.type + ", but count was not set.");
                }
                optionArgs.add(expiry.count.toString());
            }
        }

        return optionArgs.toArray(new String[0]);
    }
}
