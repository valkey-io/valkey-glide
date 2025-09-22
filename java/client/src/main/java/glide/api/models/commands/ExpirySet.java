/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.commands.ExpirySet.ExpiryType.KEEP_EXISTING;
import static glide.api.models.commands.ExpirySet.ExpiryType.MILLISECONDS;
import static glide.api.models.commands.ExpirySet.ExpiryType.PERSIST;
import static glide.api.models.commands.ExpirySet.ExpiryType.SECONDS;
import static glide.api.models.commands.ExpirySet.ExpiryType.UNIX_MILLISECONDS;
import static glide.api.models.commands.ExpirySet.ExpiryType.UNIX_SECONDS;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * Configuration of field expiration lifetime for commands that support standard expiry options.
 *
 * <p>This class provides various ways to set expiration times for hash fields, including:
 *
 * <ul>
 *   <li>Relative expiration in seconds or milliseconds
 *   <li>Absolute expiration using Unix timestamps
 *   <li>Keeping existing expiration times
 *   <li>Persisting fields (removing expiration)
 * </ul>
 *
 * <p>Different commands support different subsets of these options. Use {@link
 * #validateForCommand(String)} to ensure compatibility with specific commands.
 *
 * @example
 *     <pre>{@code
 * // Set expiration to 60 seconds from now
 * ExpirySet expiry = ExpirySet.Seconds(60L);
 *
 * // Set expiration to 5000 milliseconds from now
 * ExpirySet expiry = ExpirySet.Milliseconds(5000L);
 *
 * // Set expiration to specific Unix timestamp (seconds)
 * ExpirySet expiry = ExpirySet.UnixSeconds(1640995200L);
 *
 * // Keep existing expiration time (KEEPTTL)
 * ExpirySet expiry = ExpirySet.KeepExisting();
 *
 * // Remove expiration (PERSIST) - only supported by HGETEX
 * ExpirySet expiry = ExpirySet.Persist();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hsetex/">HSETEX Command Documentation</a>
 * @see <a href="https://valkey.io/commands/hgetex/">HGETEX Command Documentation</a>
 */
public final class ExpirySet {

    /** Expiry type for the time to live */
    private final ExpiryType type;

    /**
     * The amount of time to live before the field expires. Ignored when {@link
     * ExpiryType#KEEP_EXISTING} or {@link ExpiryType#PERSIST} type is set.
     */
    private final Long count;

    private ExpirySet(ExpiryType type) {
        this.type = type;
        this.count = null;
    }

    private ExpirySet(ExpiryType type, Long count) {
        this.type = type;
        this.count = count;
    }

    /**
     * Retain the time to live associated with the field. Equivalent to <code>KEEPTTL</code> in the
     * Valkey API.
     *
     * <p>This option preserves any existing expiration time on the hash fields. If a field has no
     * expiration, it will remain without expiration after the operation.
     *
     * <p><strong>Note:</strong> This option is not supported by the HGETEX command.
     *
     * @return ExpirySet configured to keep existing expiration
     * @example
     *     <pre>{@code
     * // Keep existing expiration times when setting new field values
     * HSetExOptions options = HSetExOptions.builder()
     *     .expiry(ExpirySet.KeepExisting())
     *     .build();
     * }</pre>
     */
    public static ExpirySet KeepExisting() {
        return new ExpirySet(KEEP_EXISTING);
    }

    /**
     * Set the specified expire time, in seconds. Equivalent to <code>EX</code> in the Valkey API.
     *
     * <p>The expiration time is relative to the current time when the command is executed.
     *
     * @param seconds time to expire, in seconds (must be positive)
     * @return ExpirySet configured with seconds-based expiration
     * @throws IllegalArgumentException if seconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Set fields to expire in 60 seconds
     * HSetExOptions options = HSetExOptions.builder()
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     * }</pre>
     */
    public static ExpirySet Seconds(Long seconds) {
        if (seconds == null || seconds <= 0) {
            throw new IllegalArgumentException("Seconds must be positive");
        }
        return new ExpirySet(SECONDS, seconds);
    }

    /**
     * Set the specified expire time, in milliseconds. Equivalent to <code>PX</code> in the Valkey
     * API.
     *
     * <p>The expiration time is relative to the current time when the command is executed.
     *
     * @param milliseconds time to expire, in milliseconds (must be positive)
     * @return ExpirySet configured with milliseconds-based expiration
     * @throws IllegalArgumentException if milliseconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Set fields to expire in 5000 milliseconds (5 seconds)
     * HSetExOptions options = HSetExOptions.builder()
     *     .expiry(ExpirySet.Milliseconds(5000L))
     *     .build();
     * }</pre>
     */
    public static ExpirySet Milliseconds(Long milliseconds) {
        if (milliseconds == null || milliseconds <= 0) {
            throw new IllegalArgumentException("Milliseconds must be positive");
        }
        return new ExpirySet(MILLISECONDS, milliseconds);
    }

    /**
     * Set the specified Unix time at which the field will expire, in seconds. Equivalent to <code>
     * EXAT</code> in the Valkey API.
     *
     * <p>The expiration time is an absolute Unix timestamp in seconds since epoch (January 1, 1970).
     *
     * @param unixSeconds <code>UNIX TIME</code> to expire, in seconds (must be positive)
     * @return ExpirySet configured with Unix seconds timestamp
     * @throws IllegalArgumentException if unixSeconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Set fields to expire at Unix timestamp 1640995200 (January 1, 2022)
     * HSetExOptions options = HSetExOptions.builder()
     *     .expiry(ExpirySet.UnixSeconds(1640995200L))
     *     .build();
     * }</pre>
     */
    public static ExpirySet UnixSeconds(Long unixSeconds) {
        if (unixSeconds == null || unixSeconds <= 0) {
            throw new IllegalArgumentException("Unix seconds must be positive");
        }
        return new ExpirySet(UNIX_SECONDS, unixSeconds);
    }

    /**
     * Set the specified Unix time at which the field will expire, in milliseconds. Equivalent to
     * <code>PXAT</code> in the Valkey API.
     *
     * <p>The expiration time is an absolute Unix timestamp in milliseconds since epoch (January 1,
     * 1970).
     *
     * @param unixMilliseconds <code>UNIX TIME</code> to expire, in milliseconds (must be positive)
     * @return ExpirySet configured with Unix milliseconds timestamp
     * @throws IllegalArgumentException if unixMilliseconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Set fields to expire at Unix timestamp 1640995200000 (January 1, 2022)
     * HSetExOptions options = HSetExOptions.builder()
     *     .expiry(ExpirySet.UnixMilliseconds(1640995200000L))
     *     .build();
     * }</pre>
     */
    public static ExpirySet UnixMilliseconds(Long unixMilliseconds) {
        if (unixMilliseconds == null || unixMilliseconds <= 0) {
            throw new IllegalArgumentException("Unix milliseconds must be positive");
        }
        return new ExpirySet(UNIX_MILLISECONDS, unixMilliseconds);
    }

    /**
     * Remove the time to live associated with the field. Equivalent to <code>PERSIST</code> in the
     * Valkey API.
     *
     * <p>This option removes any existing expiration from the hash fields, making them persistent
     * (never expire) until explicitly set again.
     *
     * <p><strong>Note:</strong> This option is only supported by the HGETEX command.
     *
     * @return ExpirySet configured to persist fields (remove expiration)
     * @example
     *     <pre>{@code
     * // Remove expiration from fields (only works with HGETEX)
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Persist())
     *     .build();
     * }</pre>
     */
    public static ExpirySet Persist() {
        return new ExpirySet(PERSIST);
    }

    /**
     * Validates that this ExpirySet is compatible with the given command.
     *
     * <p>Different hash field expiration commands support different subsets of expiry options:
     *
     * <ul>
     *   <li>HSETEX: Supports all options including KEEPTTL, but not PERSIST
     *   <li>HGETEX: Supports all options including PERSIST, but not KEEPTTL
     *   <li>Other commands: May have their own restrictions
     * </ul>
     *
     * @param commandName the name of the command to validate against (e.g., "HSETEX", "HGETEX")
     * @throws IllegalArgumentException if this ExpirySet is not compatible with the command
     * @example
     *     <pre>{@code
     * ExpirySet expiry = ExpirySet.Persist();
     * expiry.validateForCommand("HGETEX"); // OK
     * expiry.validateForCommand("HSETEX"); // Throws IllegalArgumentException
     * }</pre>
     */
    public void validateForCommand(String commandName) {
        if (commandName == null) {
            throw new IllegalArgumentException("Command name cannot be null");
        }

        if (type == PERSIST && !"HGETEX".equals(commandName)) {
            throw new IllegalArgumentException(
                    "PERSIST option is only supported by HGETEX command, not " + commandName);
        }

        if (type == KEEP_EXISTING && "HGETEX".equals(commandName)) {
            throw new IllegalArgumentException(
                    "KEEPTTL option is not supported by HGETEX command, use PERSIST instead");
        }
    }

    /**
     * Converts ExpirySet into a String[] to add to command arguments.
     *
     * <p>This method generates the appropriate command arguments based on the expiry type and value.
     * For time-based expiry types, it includes both the type argument and the time value. For KEEPTTL
     * and PERSIST, it includes only the type argument.
     *
     * @return String[] containing the command arguments for this expiry configuration
     * @throws AssertionError if a time-based expiry type has no count value set
     * @example
     *     <pre>{@code
     * ExpirySet.Seconds(60L).toArgs();        // Returns ["EX", "60"]
     * ExpirySet.KeepExisting().toArgs();       // Returns ["KEEPTTL"]
     * ExpirySet.Persist().toArgs();            // Returns ["PERSIST"]
     * }</pre>
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

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * <p>Two ExpirySet instances are considered equal if they have the same expiry type and count
     * value.
     *
     * @param obj the reference object with which to compare
     * @return true if this object is the same as the obj argument; false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ExpirySet expirySet = (ExpirySet) obj;
        return type == expirySet.type && java.util.Objects.equals(count, expirySet.count);
    }

    /**
     * Returns a hash code value for the object.
     *
     * <p>The hash code is computed based on the expiry type and count value.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(type, count);
    }

    /**
     * Returns a string representation of the object.
     *
     * <p>The string representation includes the class name and the values of the expiry type and
     * count in a readable format.
     *
     * @return a string representation of the object
     * @example
     *     <pre>{@code
     * ExpirySet expiry = ExpirySet.Seconds(60L);
     * System.out.println(expiry.toString());
     * // Output: ExpirySet{type=SECONDS, count=60}
     * }</pre>
     */
    @Override
    public String toString() {
        return "ExpirySet{" + "type=" + type + ", count=" + count + '}';
    }

    /** Types of field expiration configuration. */
    @RequiredArgsConstructor
    protected enum ExpiryType {
        /** Keep existing expiration time (KEEPTTL) */
        KEEP_EXISTING("KEEPTTL"),
        /** Remove expiration, making field persistent (PERSIST) */
        PERSIST("PERSIST"),
        /** Set expiration in seconds from now (EX) */
        SECONDS("EX"),
        /** Set expiration in milliseconds from now (PX) */
        MILLISECONDS("PX"),
        /** Set expiration at Unix timestamp in seconds (EXAT) */
        UNIX_SECONDS("EXAT"),
        /** Set expiration at Unix timestamp in milliseconds (PXAT) */
        UNIX_MILLISECONDS("PXAT");

        /** The Valkey API string representation of this expiry type. */
        private final String valkeyApi;
    }
}
