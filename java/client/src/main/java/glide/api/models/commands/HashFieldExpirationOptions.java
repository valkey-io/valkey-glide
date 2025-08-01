/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.commands.HashFieldExpirationOptions.ExpiryType.KEEP_EXISTING;
import static glide.api.models.commands.HashFieldExpirationOptions.ExpiryType.MILLISECONDS;
import static glide.api.models.commands.HashFieldExpirationOptions.ExpiryType.PERSIST;
import static glide.api.models.commands.HashFieldExpirationOptions.ExpiryType.SECONDS;
import static glide.api.models.commands.HashFieldExpirationOptions.ExpiryType.UNIX_MILLISECONDS;
import static glide.api.models.commands.HashFieldExpirationOptions.ExpiryType.UNIX_SECONDS;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Optional arguments for hash field expiration commands like HSETEX, HGETEX, HEXPIRE, etc.
 *
 * @see <a href="https://valkey.io/commands/hsetex/">valkey.io</a>
 */
@Builder
public final class HashFieldExpirationOptions {

    /** Conditional change option for hash operations (NX/XX). */
    private final ConditionalChange conditionalChange;

    /** Field-specific conditional change option for HSETEX (FNX/FXX). */
    private final FieldConditionalChange fieldConditionalChange;

    /** Expiration condition for HEXPIRE-like commands (NX/XX/GT/LT). */
    private final ExpirationCondition expirationCondition;

    /** Expiry configuration for the hash fields. */
    private final ExpirySet expiry;

    /**
     * Conditions which define whether hash operations should be performed based on hash existence.
     */
    @RequiredArgsConstructor
    @Getter
    public enum ConditionalChange {
        /**
         * Only perform operation if the hash already exists. Equivalent to <code>XX</code> in the
         * Valkey API.
         */
        ONLY_IF_EXISTS("XX"),
        /**
         * Only perform operation if the hash does not exist. Equivalent to <code>NX</code> in the
         * Valkey API.
         */
        ONLY_IF_DOES_NOT_EXIST("NX");

        private final String valkeyApi;
    }

    /** Field-specific conditions for HSETEX command. */
    @RequiredArgsConstructor
    @Getter
    public enum FieldConditionalChange {
        /**
         * Only set fields if all of them already exist. Equivalent to <code>FXX</code> in the Valkey
         * API.
         */
        ONLY_IF_ALL_EXIST("FXX"),
        /**
         * Only set fields if none of them already exist. Equivalent to <code>FNX</code> in the Valkey
         * API.
         */
        ONLY_IF_NONE_EXIST("FNX");

        private final String valkeyApi;
    }

    /** Expiration conditions for HEXPIRE-like commands. */
    @RequiredArgsConstructor
    @Getter
    public enum ExpirationCondition {
        /**
         * Only set expiration when field has no expiration. Equivalent to <code>NX</code> in the Valkey
         * API.
         */
        ONLY_IF_NO_EXPIRY("NX"),
        /**
         * Only set expiration when field has existing expiration. Equivalent to <code>XX</code> in the
         * Valkey API.
         */
        ONLY_IF_HAS_EXPIRY("XX"),
        /**
         * Only set expiration when new expiration is greater than current. Equivalent to <code>GT
         * </code> in the Valkey API.
         */
        ONLY_IF_GREATER_THAN_CURRENT("GT"),
        /**
         * Only set expiration when new expiration is less than current. Equivalent to <code>LT</code>
         * in the Valkey API.
         */
        ONLY_IF_LESS_THAN_CURRENT("LT");

        private final String valkeyApi;
    }

    /** Configuration of field expiration lifetime. */
    public static final class ExpirySet {

        /** Expiry type for the time to live */
        private final ExpiryType type;

        /**
         * The amount of time to live before the field expires. Ignored when {@link
         * ExpiryType#KEEP_EXISTING} type is set.
         */
        private Long count;

        private ExpirySet(ExpiryType type) {
            this.type = type;
        }

        private ExpirySet(ExpiryType type, Long count) {
            this.type = type;
            this.count = count;
        }

        /**
         * Retain the time to live associated with the field. Equivalent to <code>KEEPTTL</code> in the
         * Valkey API.
         */
        public static ExpirySet KeepExisting() {
            return new ExpirySet(KEEP_EXISTING);
        }

        /**
         * Set the specified expire time, in seconds. Equivalent to <code>EX</code> in the Valkey API.
         *
         * @param seconds time to expire, in seconds
         * @return ExpirySet
         */
        public static ExpirySet Seconds(Long seconds) {
            return new ExpirySet(SECONDS, seconds);
        }

        /**
         * Set the specified expire time, in milliseconds. Equivalent to <code>PX</code> in the Valkey
         * API.
         *
         * @param milliseconds time to expire, in milliseconds
         * @return ExpirySet
         */
        public static ExpirySet Milliseconds(Long milliseconds) {
            return new ExpirySet(MILLISECONDS, milliseconds);
        }

        /**
         * Set the specified Unix time at which the field will expire, in seconds. Equivalent to <code>
         * EXAT</code> in the Valkey API.
         *
         * @param unixSeconds <code>UNIX TIME</code> to expire, in seconds.
         * @return ExpirySet
         */
        public static ExpirySet UnixSeconds(Long unixSeconds) {
            return new ExpirySet(UNIX_SECONDS, unixSeconds);
        }

        /**
         * Set the specified Unix time at which the field will expire, in milliseconds. Equivalent to
         * <code>PXAT</code> in the Valkey API.
         *
         * @param unixMilliseconds <code>UNIX TIME</code> to expire, in milliseconds.
         * @return ExpirySet
         */
        public static ExpirySet UnixMilliseconds(Long unixMilliseconds) {
            return new ExpirySet(UNIX_MILLISECONDS, unixMilliseconds);
        }

        /**
         * Remove the time to live associated with the field. Equivalent to <code>PERSIST</code> in the
         * Valkey API. This option is only available for HGETEX command.
         */
        public static ExpirySet Persist() {
            return new ExpirySet(PERSIST);
        }

        /**
         * Converts ExpirySet into a String[] to add to command arguments.
         *
         * @return String[]
         */
        public String[] toArgs() {
            List<String> args = new ArrayList<>();
            args.add(type.valkeyApi);
            if (type != KEEP_EXISTING && type != PERSIST) {
                assert count != null
                        : "Hash field expiration command received expiry type "
                                + type
                                + ", but count was not set.";
                args.add(count.toString());
            }
            return args.toArray(new String[0]);
        }
    }

    /** Types of field expiration configuration. */
    @RequiredArgsConstructor
    protected enum ExpiryType {
        KEEP_EXISTING("KEEPTTL"),
        PERSIST("PERSIST"),
        SECONDS("EX"),
        MILLISECONDS("PX"),
        UNIX_SECONDS("EXAT"),
        UNIX_MILLISECONDS("PXAT");

        private final String valkeyApi;
    }

    /**
     * Validates that mutually exclusive options are not set together.
     *
     * @throws IllegalArgumentException if mutually exclusive options are set
     */
    private void validateOptions() {
        // Validate that conditionalChange and fieldConditionalChange are not both set to conflicting
        // values
        if (conditionalChange != null && fieldConditionalChange != null) {
            // Both NX-type options or both XX-type options would be redundant but not conflicting
            // The validation here is more about logical consistency
            if ((conditionalChange == ConditionalChange.ONLY_IF_DOES_NOT_EXIST
                            && fieldConditionalChange == FieldConditionalChange.ONLY_IF_ALL_EXIST)
                    || (conditionalChange == ConditionalChange.ONLY_IF_EXISTS
                            && fieldConditionalChange == FieldConditionalChange.ONLY_IF_NONE_EXIST)) {
                throw new IllegalArgumentException(
                        "Conflicting conditional options: "
                                + conditionalChange
                                + " and "
                                + fieldConditionalChange);
            }
        }

        // Validate expiry options - PERSIST is mutually exclusive with all other expiry types
        if (expiry != null && expiry.type == PERSIST) {
            // PERSIST should not be combined with any conditional or field conditional changes
            // for HGETEX as it's a simple operation to remove expiration
            if (conditionalChange != null
                    || fieldConditionalChange != null
                    || expirationCondition != null) {
                throw new IllegalArgumentException(
                        "PERSIST option cannot be combined with conditional options");
            }
        }
    }

    /**
     * Converts HashFieldExpirationOptions into a String[] to add to command arguments. This method
     * validates options before conversion.
     *
     * @return String[]
     * @throws IllegalArgumentException if mutually exclusive options are set
     */
    public String[] toArgs() {
        validateOptions();

        List<String> optionArgs = new ArrayList<>();

        if (conditionalChange != null) {
            optionArgs.add(conditionalChange.valkeyApi);
        }

        if (fieldConditionalChange != null) {
            optionArgs.add(fieldConditionalChange.valkeyApi);
        }

        if (expirationCondition != null) {
            optionArgs.add(expirationCondition.valkeyApi);
        }

        if (expiry != null) {
            String[] expiryArgs = expiry.toArgs();
            for (String arg : expiryArgs) {
                optionArgs.add(arg);
            }
        }

        return optionArgs.toArray(new String[0]);
    }

    /** Builder class for {@link HashFieldExpirationOptions}. */
    public static class HashFieldExpirationOptionsBuilder {
        /**
         * Sets the condition to {@link ConditionalChange#ONLY_IF_EXISTS} for the operation.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder conditionalSetOnlyIfExists() {
            this.conditionalChange = ConditionalChange.ONLY_IF_EXISTS;
            return this;
        }

        /**
         * Sets the condition to {@link ConditionalChange#ONLY_IF_DOES_NOT_EXIST} for the operation.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder conditionalSetOnlyIfNotExist() {
            this.conditionalChange = ConditionalChange.ONLY_IF_DOES_NOT_EXIST;
            return this;
        }

        /**
         * Sets the field condition to {@link FieldConditionalChange#ONLY_IF_ALL_EXIST}.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder fieldConditionalSetOnlyIfAllExist() {
            this.fieldConditionalChange = FieldConditionalChange.ONLY_IF_ALL_EXIST;
            return this;
        }

        /**
         * Sets the field condition to {@link FieldConditionalChange#ONLY_IF_NONE_EXIST}.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder fieldConditionalSetOnlyIfNoneExist() {
            this.fieldConditionalChange = FieldConditionalChange.ONLY_IF_NONE_EXIST;
            return this;
        }

        /**
         * Sets the expiration condition to {@link ExpirationCondition#ONLY_IF_NO_EXPIRY}.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder expirationConditionOnlyIfNoExpiry() {
            this.expirationCondition = ExpirationCondition.ONLY_IF_NO_EXPIRY;
            return this;
        }

        /**
         * Sets the expiration condition to {@link ExpirationCondition#ONLY_IF_HAS_EXPIRY}.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder expirationConditionOnlyIfHasExpiry() {
            this.expirationCondition = ExpirationCondition.ONLY_IF_HAS_EXPIRY;
            return this;
        }

        /**
         * Sets the expiration condition to {@link ExpirationCondition#ONLY_IF_GREATER_THAN_CURRENT}.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder expirationConditionOnlyIfGreaterThanCurrent() {
            this.expirationCondition = ExpirationCondition.ONLY_IF_GREATER_THAN_CURRENT;
            return this;
        }

        /**
         * Sets the expiration condition to {@link ExpirationCondition#ONLY_IF_LESS_THAN_CURRENT}.
         *
         * @return This builder instance
         */
        public HashFieldExpirationOptionsBuilder expirationConditionOnlyIfLessThanCurrent() {
            this.expirationCondition = ExpirationCondition.ONLY_IF_LESS_THAN_CURRENT;
            return this;
        }
    }
}
