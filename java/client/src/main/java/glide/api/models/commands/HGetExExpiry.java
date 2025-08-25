/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import static glide.api.models.commands.HGetExExpiry.HGetExExpiryType.MILLISECONDS;
import static glide.api.models.commands.HGetExExpiry.HGetExExpiryType.PERSIST;
import static glide.api.models.commands.HGetExExpiry.HGetExExpiryType.SECONDS;
import static glide.api.models.commands.HGetExExpiry.HGetExExpiryType.UNIX_MILLISECONDS;
import static glide.api.models.commands.HGetExExpiry.HGetExExpiryType.UNIX_SECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

/**
 * Configuration of field expiration lifetime specifically for HGETEX command.
 *
 * <p>This class provides expiry options that are specifically tailored for the HGETEX command.
 * Unlike the general {@link ExpirySet} class, HGetExExpiry:
 *
 * <ul>
 *   <li>Supports the PERSIST option to remove expiration from fields
 *   <li>Does not support the KEEPTTL option (use PERSIST instead)
 *   <li>Provides the same relative and absolute time-based expiration options
 * </ul>
 *
 * <p>The HGETEX command retrieves hash field values and optionally sets their expiration time in a
 * single atomic operation.
 *
 * @example
 *     <pre>{@code
 * // Set expiration to 60 seconds from now
 * HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
 *
 * // Set expiration to 5000 milliseconds from now
 * HGetExExpiry expiry = HGetExExpiry.Milliseconds(5000L);
 *
 * // Set expiration to specific Unix timestamp (seconds)
 * HGetExExpiry expiry = HGetExExpiry.UnixSeconds(1640995200L);
 *
 * // Remove expiration (make fields persistent)
 * HGetExExpiry expiry = HGetExExpiry.Persist();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hgetex/">HGETEX Command Documentation</a>
 */
public final class HGetExExpiry {

    /** Expiry type for the time to live */
    private final HGetExExpiryType type;

    /**
     * The amount of time to live before the field expires. Ignored when {@link
     * HGetExExpiryType#PERSIST} type is set.
     */
    private final Long count;

    private HGetExExpiry(HGetExExpiryType type) {
        this.type = type;
        this.count = null;
    }

    private HGetExExpiry(HGetExExpiryType type, Long count) {
        this.type = type;
        this.count = count;
    }

    /**
     * Set the specified expire time, in seconds. Equivalent to <code>EX</code> in the Valkey API.
     *
     * <p>The expiration time is relative to the current time when the HGETEX command is executed.
     * After retrieving the field values, they will be set to expire after the specified number of
     * seconds.
     *
     * @param seconds time to expire, in seconds (must be positive)
     * @return HGetExExpiry configured with seconds-based expiration
     * @throws IllegalArgumentException if seconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Retrieve fields and set them to expire in 60 seconds
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Seconds(60L))
     *     .build();
     * String[] values = client.hgetex("myHash", new String[]{"field1", "field2"}, options).get();
     * }</pre>
     */
    public static HGetExExpiry Seconds(Long seconds) {
        if (seconds == null || seconds <= 0) {
            throw new IllegalArgumentException("Seconds must be positive");
        }
        return new HGetExExpiry(SECONDS, seconds);
    }

    /**
     * Set the specified expire time, in milliseconds. Equivalent to <code>PX</code> in the Valkey
     * API.
     *
     * <p>The expiration time is relative to the current time when the HGETEX command is executed.
     * After retrieving the field values, they will be set to expire after the specified number of
     * milliseconds.
     *
     * @param milliseconds time to expire, in milliseconds (must be positive)
     * @return HGetExExpiry configured with milliseconds-based expiration
     * @throws IllegalArgumentException if milliseconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Retrieve fields and set them to expire in 5000 milliseconds (5 seconds)
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Milliseconds(5000L))
     *     .build();
     * String[] values = client.hgetex("myHash", new String[]{"field1", "field2"}, options).get();
     * }</pre>
     */
    public static HGetExExpiry Milliseconds(Long milliseconds) {
        if (milliseconds == null || milliseconds <= 0) {
            throw new IllegalArgumentException("Milliseconds must be positive");
        }
        return new HGetExExpiry(MILLISECONDS, milliseconds);
    }

    /**
     * Set the specified Unix time at which the field will expire, in seconds. Equivalent to <code>
     * EXAT</code> in the Valkey API.
     *
     * <p>The expiration time is an absolute Unix timestamp in seconds since epoch (January 1, 1970).
     * After retrieving the field values, they will be set to expire at the specified timestamp.
     *
     * @param unixSeconds <code>UNIX TIME</code> to expire, in seconds (must be positive)
     * @return HGetExExpiry configured with Unix seconds timestamp
     * @throws IllegalArgumentException if unixSeconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Retrieve fields and set them to expire at Unix timestamp 1640995200 (January 1, 2022)
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.UnixSeconds(1640995200L))
     *     .build();
     * String[] values = client.hgetex("myHash", new String[]{"field1", "field2"}, options).get();
     * }</pre>
     */
    public static HGetExExpiry UnixSeconds(Long unixSeconds) {
        if (unixSeconds == null || unixSeconds <= 0) {
            throw new IllegalArgumentException("Unix seconds must be positive");
        }
        return new HGetExExpiry(UNIX_SECONDS, unixSeconds);
    }

    /**
     * Set the specified Unix time at which the field will expire, in milliseconds. Equivalent to
     * <code>PXAT</code> in the Valkey API.
     *
     * <p>The expiration time is an absolute Unix timestamp in milliseconds since epoch (January 1,
     * 1970). After retrieving the field values, they will be set to expire at the specified
     * timestamp.
     *
     * @param unixMilliseconds <code>UNIX TIME</code> to expire, in milliseconds (must be positive)
     * @return HGetExExpiry configured with Unix milliseconds timestamp
     * @throws IllegalArgumentException if unixMilliseconds is null or non-positive
     * @example
     *     <pre>{@code
     * // Retrieve fields and set them to expire at Unix timestamp 1640995200000 (January 1, 2022)
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.UnixMilliseconds(1640995200000L))
     *     .build();
     * String[] values = client.hgetex("myHash", new String[]{"field1", "field2"}, options).get();
     * }</pre>
     */
    public static HGetExExpiry UnixMilliseconds(Long unixMilliseconds) {
        if (unixMilliseconds == null || unixMilliseconds <= 0) {
            throw new IllegalArgumentException("Unix milliseconds must be positive");
        }
        return new HGetExExpiry(UNIX_MILLISECONDS, unixMilliseconds);
    }

    /**
     * Remove the time to live associated with the field. Equivalent to <code>PERSIST</code> in the
     * Valkey API.
     *
     * <p>This option removes any existing expiration from the hash fields after retrieving their
     * values, making them persistent (never expire) until explicitly set again. This is the HGETEX
     * equivalent of the KEEPTTL option in other commands.
     *
     * @return HGetExExpiry configured to persist fields (remove expiration)
     * @example
     *     <pre>{@code
     * // Retrieve field values and remove their expiration
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Persist())
     *     .build();
     * String[] values = client.hgetex("myHash", new String[]{"field1", "field2"}, options).get();
     * // Now field1 and field2 have no expiration time
     * }</pre>
     */
    public static HGetExExpiry Persist() {
        return new HGetExExpiry(PERSIST);
    }

    /**
     * Converts HGetExExpiry into a String[] to add to HGETEX command arguments.
     *
     * <p>This method generates the appropriate command arguments based on the expiry type and value.
     * For time-based expiry types, it includes both the type argument and the time value. For
     * PERSIST, it includes only the type argument.
     *
     * @return String[] containing the command arguments for this expiry configuration
     * @throws AssertionError if a time-based expiry type has no count value set
     * @example
     *     <pre>{@code
     * HGetExExpiry.Seconds(60L).toArgs();      // Returns ["EX", "60"]
     * HGetExExpiry.Persist().toArgs();         // Returns ["PERSIST"]
     * HGetExExpiry.UnixSeconds(1640995200L).toArgs(); // Returns ["EXAT", "1640995200"]
     * }</pre>
     */
    public String[] toArgs() {
        List<String> args = new ArrayList<>();
        args.add(type.valkeyApi);
        if (type != PERSIST) {
            assert count != null
                    : "HGETEX expiry command received expiry type " + type + ", but count was not set.";
            args.add(count.toString());
        }
        return args.toArray(new String[0]);
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * <p>Two HGetExExpiry instances are considered equal if they have the same expiry type and count
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
        HGetExExpiry that = (HGetExExpiry) obj;
        return type == that.type && Objects.equals(count, that.count);
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
        return Objects.hash(type, count);
    }

    /**
     * Returns a string representation of the object.
     *
     * <p>The string representation includes the class name and the values of all fields in a readable
     * format.
     *
     * @return a string representation of the object
     * @example
     *     <pre>{@code
     * HGetExExpiry expiry = HGetExExpiry.Seconds(60L);
     * System.out.println(expiry.toString());
     * // Output: HGetExExpiry{type=SECONDS, count=60}
     *
     * HGetExExpiry persist = HGetExExpiry.Persist();
     * System.out.println(persist.toString());
     * // Output: HGetExExpiry{type=PERSIST, count=null}
     * }</pre>
     */
    @Override
    public String toString() {
        return "HGetExExpiry{" + "type=" + type + ", count=" + count + '}';
    }

    /** Types of field expiration configuration specific to HGETEX command. */
    @RequiredArgsConstructor
    protected enum HGetExExpiryType {
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
