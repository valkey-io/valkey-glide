/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.commands.SetOptions.ExpiryType.KEEP_EXISTING;
import static glide.api.models.commands.SetOptions.ExpiryType.MILLISECONDS;
import static glide.api.models.commands.SetOptions.ExpiryType.SECONDS;
import static glide.api.models.commands.SetOptions.ExpiryType.UNIX_MILLISECONDS;
import static glide.api.models.commands.SetOptions.ExpiryType.UNIX_SECONDS;

import command_request.CommandRequestOuterClass.Command;
import glide.api.commands.StringBaseCommands;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Optional arguments for {@link StringBaseCommands#set(String, String, SetOptions)} command.
 *
 * @see <a href="https://valkey.io/commands/set/">valkey.io</a>
 */
@Builder
public final class SetOptions {

    /**
     * If <code>conditionalSet</code> is not set the value will be set regardless of prior value
     * existence. If value isn't set because of the condition, command will return <code>null</code>.
     */
    private final ConditionalSet conditionalSet;

    /** Value to compare when {@link ConditionalSet#ONLY_IF_EQUAL} is set. */
    private final String comparisonValue;

    /**
     * Set command to return the old string stored at <code>key</code>, or <code>null</code> if <code>
     * key</code> did not exist. An error is returned and <code>SET</code> aborted if the value stored
     * at <code>key</code> is not a string. Equivalent to <code>GET</code> in the Valkey API.
     */
    private final boolean returnOldValue;

    /** If not set, no expiry time will be set for the value. */
    private final Expiry expiry;

    /** Conditions which define whether new value should be set or not. */
    @RequiredArgsConstructor
    @Getter
    public enum ConditionalSet {
        /** Only set the key if it already exists. Equivalent to <code>XX</code> in the Valkey API. */
        ONLY_IF_EXISTS("XX"),
        /**
         * Only set the key if it does not already exist. Equivalent to <code>NX</code> in the Valkey
         * API.
         */
        ONLY_IF_DOES_NOT_EXIST("NX"),
        /**
         * Only set the key if the current value of key equals the {@link SetOptions#comparisonValue}.
         * Equivalent to <code>IFEQ comparison-value</code> in the Valkey API.
         */
        ONLY_IF_EQUAL("IFEQ");

        private final String valkeyApi;
    }

    /**
     * Builder class for {@link SetOptions}.
     *
     * <p>Provides methods to set conditions under which a value should be set.
     *
     * <p>Note: Calling any of these methods will override the existing values of {@code
     * conditionalSet} and {@code comparisonValue}, if they are already set.
     */
    public static class SetOptionsBuilder {
        /**
         * Sets the condition to {@link ConditionalSet#ONLY_IF_EXISTS} for setting the value.
         *
         * <p>This method overrides any previously set {@code conditionalSet} and {@code
         * comparisonValue}.
         *
         * @return This builder instance
         */
        public SetOptionsBuilder conditionalSetOnlyIfExists() {
            this.conditionalSet = ConditionalSet.ONLY_IF_EXISTS;
            this.comparisonValue = null;
            return this;
        }

        /**
         * Sets the condition to {@link ConditionalSet#ONLY_IF_DOES_NOT_EXIST} for setting the value.
         *
         * <p>This method overrides any previously set {@code conditionalSet} and {@code
         * comparisonValue}.
         *
         * @return This builder instance
         */
        public SetOptionsBuilder conditionalSetOnlyIfNotExist() {
            this.conditionalSet = ConditionalSet.ONLY_IF_DOES_NOT_EXIST;
            this.comparisonValue = null;
            return this;
        }

        /**
         * Sets the condition to {@link ConditionalSet#ONLY_IF_EQUAL} for setting the value. The key
         * will be set if the provided comparison value matches the existing value.
         *
         * <p>This method overrides any previously set {@code conditionalSet} and {@code
         * comparisonValue}.
         *
         * @since Valkey 8.1 and above.
         * @param value the value to compare
         * @return this builder instance
         */
        public SetOptionsBuilder conditionalSetOnlyIfEqualTo(@NonNull String value) {
            this.conditionalSet = ConditionalSet.ONLY_IF_EQUAL;
            this.comparisonValue = value;
            return this;
        }
    }

    /** Configuration of value lifetime. */
    public static final class Expiry {

        /** Expiry type for the time to live */
        private final ExpiryType type;

        /**
         * The amount of time to live before the key expires. Ignored when {@link
         * ExpiryType#KEEP_EXISTING} type is set.
         */
        private Long count;

        private Expiry(ExpiryType type) {
            this.type = type;
        }

        private Expiry(ExpiryType type, Long count) {
            this.type = type;
            this.count = count;
        }

        /**
         * Retain the time to live associated with the key. Equivalent to <code>KEEPTTL</code> in the
         * Valkey API.
         */
        public static Expiry KeepExisting() {
            return new Expiry(KEEP_EXISTING);
        }

        /**
         * Set the specified expire time, in seconds. Equivalent to <code>EX</code> in the Valkey API.
         *
         * @param seconds time to expire, in seconds
         * @return Expiry
         */
        public static Expiry Seconds(Long seconds) {
            return new Expiry(SECONDS, seconds);
        }

        /**
         * Set the specified expire time, in milliseconds. Equivalent to <code>PX</code> in the Valkey
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
         * EXAT</code> in the Valkey API.
         *
         * @param unixSeconds <code>UNIX TIME</code> to expire, in seconds.
         * @return Expiry
         */
        public static Expiry UnixSeconds(Long unixSeconds) {
            return new Expiry(UNIX_SECONDS, unixSeconds);
        }

        /**
         * Set the specified Unix time at which the key will expire, in milliseconds. Equivalent to
         * <code>PXAT</code> in the Valkey API.
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
        KEEP_EXISTING("KEEPTTL"),
        SECONDS("EX"),
        MILLISECONDS("PX"),
        UNIX_SECONDS("EXAT"),
        UNIX_MILLISECONDS("PXAT");

        private final String valkeyApi;
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
            optionArgs.add(conditionalSet.valkeyApi);
            if (conditionalSet == ConditionalSet.ONLY_IF_EQUAL) {
                optionArgs.add(comparisonValue);
            }
        }

        if (returnOldValue) {
            optionArgs.add(RETURN_OLD_VALUE);
        }

        if (expiry != null) {
            optionArgs.add(expiry.type.valkeyApi);
            if (expiry.type != KEEP_EXISTING) {
                assert expiry.count != null
                        : "Set command received expiry type " + expiry.type + ", but count was not set.";
                optionArgs.add(expiry.count.toString());
            }
        }

        return optionArgs.toArray(new String[0]);
    }
}
