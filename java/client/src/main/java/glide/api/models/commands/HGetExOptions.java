/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.Objects;
import lombok.Builder;

/**
 * Optional arguments for the HGETEX command.
 *
 * <p>HGETEX retrieves hash field values and optionally sets their expiration time in a single
 * atomic operation. This class supports:
 *
 * <ul>
 *   <li>Standard expiry options (EX/PX/EXAT/PXAT) - set expiration times
 *   <li>PERSIST option - remove expiration from fields
 * </ul>
 *
 * <p>This class provides compile-time safety by only allowing parameter combinations that are valid
 * for the HGETEX command. It excludes options like KEEPTTL (use PERSIST instead), field conditional
 * changes (which are only for HSETEX), and expiration conditions (which are only for HEXPIRE-family
 * commands).
 *
 * <p>All instances of this class are immutable and thread-safe after construction.
 *
 * @example
 *     <pre>{@code
 * // Retrieve fields and set them to expire in 60 seconds
 * HGetExOptions options = HGetExOptions.builder()
 *     .expiry(HGetExExpiry.Seconds(60L))
 *     .build();
 *
 * String[] values = client.hgetex("myHash", new String[]{"field1", "field2"}, options).get();
 *
 * // Retrieve fields and remove their expiration (make persistent)
 * HGetExOptions options2 = HGetExOptions.builder()
 *     .expiry(HGetExExpiry.Persist())
 *     .build();
 *
 * // Retrieve fields and set expiration to specific Unix timestamp
 * HGetExOptions options3 = HGetExOptions.builder()
 *     .expiry(HGetExExpiry.UnixMilliseconds(1640995200000L))
 *     .build();
 *
 * // Retrieve fields without changing expiration (no options needed)
 * String[] values2 = client.hgetex("myHash", new String[]{"field1", "field2"}, null).get();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hgetex/">HGETEX Command Documentation</a>
 * @see HGetExExpiry
 */
@Builder
public final class HGetExOptions {

    /** Expiry configuration for the hash fields after retrieval. */
    private final HGetExExpiry expiry;

    /**
     * Private constructor used by the builder pattern.
     *
     * @param expiry the expiry configuration, may be null
     */
    private HGetExOptions(HGetExExpiry expiry) {
        this.expiry = expiry;
    }

    /**
     * Converts options into command arguments for the HGETEX command.
     *
     * <p>This method generates the appropriate command arguments based on the expiry configuration.
     * If no expiry is specified, it returns an empty array, allowing the HGETEX command to retrieve
     * field values without modifying their expiration.
     *
     * <p>The argument order follows the HGETEX command specification:
     *
     * <ol>
     *   <li>Expiry options (EX/PX/EXAT/PXAT/PERSIST) if specified
     * </ol>
     *
     * @return String[] containing the command arguments for these options, or empty array if no
     *     options
     * @example
     *     <pre>{@code
     * HGetExOptions options1 = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Seconds(60L))
     *     .build();
     * String[] args1 = options1.toArgs(); // Returns ["EX", "60"]
     *
     * HGetExOptions options2 = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Persist())
     *     .build();
     * String[] args2 = options2.toArgs(); // Returns ["PERSIST"]
     *
     * HGetExOptions options3 = HGetExOptions.builder().build();
     * String[] args3 = options3.toArgs(); // Returns []
     * }</pre>
     */
    public String[] toArgs() {
        if (expiry == null) {
            return new String[0];
        }
        return expiry.toArgs();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * <p>Two HGetExOptions instances are considered equal if they have the same expiry configuration.
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
        HGetExOptions that = (HGetExOptions) obj;
        return Objects.equals(expiry, that.expiry);
    }

    /**
     * Returns a hash code value for the object.
     *
     * <p>The hash code is computed based on the expiry configuration.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(expiry);
    }

    /**
     * Returns a string representation of the object.
     *
     * <p>The string representation includes the class name and the values of all non-null fields in a
     * readable format.
     *
     * @return a string representation of the object
     * @example
     *     <pre>{@code
     * HGetExOptions options = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Seconds(60L))
     *     .build();
     *
     * System.out.println(options.toString());
     * // Output: HGetExOptions{expiry=HGetExExpiry{type=SECONDS, count=60}}
     * }</pre>
     */
    @Override
    public String toString() {
        return "HGetExOptions{" + "expiry=" + expiry + '}';
    }

    /**
     * Builder class for creating HGetExOptions instances with a fluent API.
     *
     * <p>This builder provides a clean, readable API for setting expiry options and supports method
     * chaining. The builder ensures thread safety during the building process.
     *
     * @since Valkey 9.0.0
     */
    public static class HGetExOptionsBuilder {

        /** Expiry configuration being built. */
        private HGetExExpiry expiry;

        /**
         * Sets the expiry configuration for the hash fields after retrieval.
         *
         * <p>This method accepts any HGetExExpiry configuration, including time-based expiration
         * options and the PERSIST option to remove expiration.
         *
         * @param expiry the expiry configuration to set, may be null
         * @return this builder instance for method chaining
         * @example
         *     <pre>{@code
         * // Set expiration to 60 seconds from now
         * HGetExOptions options = HGetExOptions.builder()
         *     .expiry(HGetExExpiry.Seconds(60L))
         *     .build();
         *
         * // Remove expiration (make fields persistent)
         * HGetExOptions options2 = HGetExOptions.builder()
         *     .expiry(HGetExExpiry.Persist())
         *     .build();
         *
         * // Set expiration to specific Unix timestamp in milliseconds
         * HGetExOptions options3 = HGetExOptions.builder()
         *     .expiry(HGetExExpiry.UnixMilliseconds(1640995200000L))
         *     .build();
         * }</pre>
         *
         * @see HGetExExpiry
         */
        public HGetExOptionsBuilder expiry(HGetExExpiry expiry) {
            this.expiry = expiry;
            return this;
        }

        /**
         * Builds and returns a new HGetExOptions instance.
         *
         * <p>This method creates an immutable HGetExOptions instance with the configuration specified
         * in this builder. The resulting instance is thread-safe and can be reused across multiple
         * command invocations.
         *
         * <p>If no expiry is specified, the resulting options will not modify the expiration of the
         * retrieved fields.
         *
         * @return a new HGetExOptions instance with the specified configuration
         * @example
         *     <pre>{@code
         * HGetExOptions options = HGetExOptions.builder()
         *     .expiry(HGetExExpiry.Seconds(60L))
         *     .build();
         *
         * // The options instance is now immutable and thread-safe
         * CompletableFuture<String[]> result1 = client.hgetex("hash1", fields1, options);
         * CompletableFuture<String[]> result2 = client.hgetex("hash2", fields2, options);
         * }</pre>
         */
        public HGetExOptions build() {
            return new HGetExOptions(expiry);
        }
    }
}
