/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

/**
 * Optional arguments for the HSETEX command.
 *
 * <p>HSETEX sets hash fields with expiration times and supports:
 *
 * <ul>
 *   <li>Field conditional changes (FNX/FXX) - control when fields are set based on existence
 *   <li>Standard expiry options (EX/PX/EXAT/PXAT/KEEPTTL) - set expiration times
 * </ul>
 *
 * <p>This class provides compile-time safety by only allowing parameter combinations that are valid
 * for the HSETEX command. It excludes options like PERSIST (which is only for HGETEX) and
 * expiration conditions (which are only for HEXPIRE-family commands).
 *
 * <p>All instances of this class are immutable and thread-safe after construction.
 *
 * @example
 *     <pre>{@code
 * // Set fields only if none exist, with 60 second expiration
 * HSetExOptions options = HSetExOptions.builder()
 *     .onlyIfNoneExist()
 *     .expiry(ExpirySet.Seconds(60L))
 *     .build();
 *
 * Map<String, String> fieldValueMap = Map.of("field1", "value1", "field2", "value2");
 * Long result = client.hsetex("myHash", fieldValueMap, options).get();
 *
 * // Set fields only if all exist, keeping existing expiration
 * HSetExOptions options2 = HSetExOptions.builder()
 *     .onlyIfAllExist()
 *     .expiry(ExpirySet.KeepExisting())
 *     .build();
 *
 * // Set fields with Unix timestamp expiration
 * HSetExOptions options3 = HSetExOptions.builder()
 *     .expiry(ExpirySet.UnixSeconds(1640995200L))
 *     .build();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hsetex/">HSETEX Command Documentation</a>
 * @see FieldConditionalChange
 * @see ExpirySet
 */
@Builder
public final class HSetExOptions {

    /** Field conditional change option (FNX/FXX) for controlling when fields are set. */
    private final FieldConditionalChange fieldConditionalChange;

    /** Expiry configuration for the hash fields. */
    private final ExpirySet expiry;

    /**
     * Private constructor used by the builder pattern.
     *
     * @param fieldConditionalChange the field conditional change option, may be null
     * @param expiry the expiry configuration, may be null
     */
    private HSetExOptions(FieldConditionalChange fieldConditionalChange, ExpirySet expiry) {
        this.fieldConditionalChange = fieldConditionalChange;
        this.expiry = expiry;
    }

    /**
     * Converts options into command arguments for the HSETEX command.
     *
     * <p>This method validates that the expiry options are compatible with HSETEX and generates the
     * appropriate command arguments in the correct order.
     *
     * <p>The argument order follows the HSETEX command specification:
     *
     * <ol>
     *   <li>Field conditional change (FNX/FXX) if specified
     *   <li>Expiry options (EX/PX/EXAT/PXAT/KEEPTTL) if specified
     * </ol>
     *
     * @return String[] containing the command arguments for these options
     * @throws IllegalArgumentException if expiry options are not compatible with HSETEX
     * @example
     *     <pre>{@code
     * HSetExOptions options = HSetExOptions.builder()
     *     .onlyIfNoneExist()
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     *
     * String[] args = options.toArgs(); // Returns ["FNX", "EX", "60"]
     * }</pre>
     */
    public String[] toArgs() {
        List<String> args = new ArrayList<>();

        // Add field conditional change argument if specified
        if (fieldConditionalChange != null) {
            args.add(fieldConditionalChange.getValkeyApi());
        }

        // Add expiry arguments if specified
        if (expiry != null) {
            // Validate that expiry is compatible with HSETEX command
            expiry.validateForCommand("HSETEX");
            args.addAll(Arrays.asList(expiry.toArgs()));
        }

        return args.toArray(new String[0]);
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * <p>Two HSetExOptions instances are considered equal if they have the same field conditional
     * change option and expiry configuration.
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
        HSetExOptions that = (HSetExOptions) obj;
        return Objects.equals(fieldConditionalChange, that.fieldConditionalChange)
                && Objects.equals(expiry, that.expiry);
    }

    /**
     * Returns a hash code value for the object.
     *
     * <p>The hash code is computed based on the field conditional change option and expiry
     * configuration.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(fieldConditionalChange, expiry);
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
     * HSetExOptions options = HSetExOptions.builder()
     *     .onlyIfNoneExist()
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     *
     * System.out.println(options.toString());
     * // Output: HSetExOptions{fieldConditionalChange=ONLY_IF_NONE_EXIST, expiry=ExpirySet{type=SECONDS, count=60}}
     * }</pre>
     */
    @Override
    public String toString() {
        return "HSetExOptions{"
                + "fieldConditionalChange="
                + fieldConditionalChange
                + ", expiry="
                + expiry
                + '}';
    }

    /**
     * Builder class for creating HSetExOptions instances with a fluent API.
     *
     * <p>This builder provides convenience methods for setting common field conditional change
     * options and supports method chaining for a clean, readable API.
     *
     * <p>The builder validates that only valid parameter combinations are set and ensures thread
     * safety during the building process.
     *
     * @since Valkey 9.0.0
     */
    public static class HSetExOptionsBuilder {

        /** Field conditional change option being built. */
        private FieldConditionalChange fieldConditionalChange;

        /** Expiry configuration being built. */
        private ExpirySet expiry;

        /**
         * Sets the field conditional change to only set fields if all of them already exist.
         *
         * <p>This is equivalent to the FXX option in the Valkey HSETEX command. When this option is
         * used, the command will only update existing fields and will not create new fields.
         *
         * @return this builder instance for method chaining
         * @example
         *     <pre>{@code
         * HSetExOptions options = HSetExOptions.builder()
         *     .onlyIfAllExist()
         *     .expiry(ExpirySet.Seconds(60L))
         *     .build();
         * }</pre>
         *
         * @see FieldConditionalChange#ONLY_IF_ALL_EXIST
         */
        public HSetExOptionsBuilder onlyIfAllExist() {
            this.fieldConditionalChange = FieldConditionalChange.ONLY_IF_ALL_EXIST;
            return this;
        }

        /**
         * Sets the field conditional change to only set fields if none of them already exist.
         *
         * <p>This is equivalent to the FNX option in the Valkey HSETEX command. When this option is
         * used, the command will only create new fields and will not update existing fields.
         *
         * @return this builder instance for method chaining
         * @example
         *     <pre>{@code
         * HSetExOptions options = HSetExOptions.builder()
         *     .onlyIfNoneExist()
         *     .expiry(ExpirySet.Milliseconds(5000L))
         *     .build();
         * }</pre>
         *
         * @see FieldConditionalChange#ONLY_IF_NONE_EXIST
         */
        public HSetExOptionsBuilder onlyIfNoneExist() {
            this.fieldConditionalChange = FieldConditionalChange.ONLY_IF_NONE_EXIST;
            return this;
        }

        /**
         * Sets the field conditional change option directly.
         *
         * <p>This method allows setting the field conditional change using the enum value directly,
         * which can be useful when the option is determined programmatically.
         *
         * @param fieldConditionalChange the field conditional change option to set, may be null
         * @return this builder instance for method chaining
         * @example
         *     <pre>{@code
         * FieldConditionalChange condition = someCondition ?
         *     FieldConditionalChange.ONLY_IF_ALL_EXIST :
         *     FieldConditionalChange.ONLY_IF_NONE_EXIST;
         *
         * HSetExOptions options = HSetExOptions.builder()
         *     .fieldConditionalChange(condition)
         *     .expiry(ExpirySet.Seconds(30L))
         *     .build();
         * }</pre>
         */
        public HSetExOptionsBuilder fieldConditionalChange(
                FieldConditionalChange fieldConditionalChange) {
            this.fieldConditionalChange = fieldConditionalChange;
            return this;
        }

        /**
         * Sets the expiry configuration for the hash fields.
         *
         * <p>This method accepts any ExpirySet configuration that is compatible with the HSETEX
         * command. The expiry will be validated when {@link #build()} is called or when {@link
         * HSetExOptions#toArgs()} is invoked.
         *
         * @param expiry the expiry configuration to set, may be null
         * @return this builder instance for method chaining
         * @throws IllegalArgumentException if expiry is not compatible with HSETEX (validation occurs
         *     later)
         * @example
         *     <pre>{@code
         * // Set expiration to 60 seconds from now
         * HSetExOptions options = HSetExOptions.builder()
         *     .expiry(ExpirySet.Seconds(60L))
         *     .build();
         *
         * // Keep existing expiration times
         * HSetExOptions options2 = HSetExOptions.builder()
         *     .expiry(ExpirySet.KeepExisting())
         *     .build();
         *
         * // Set expiration to specific Unix timestamp
         * HSetExOptions options3 = HSetExOptions.builder()
         *     .expiry(ExpirySet.UnixMilliseconds(1640995200000L))
         *     .build();
         * }</pre>
         *
         * @see ExpirySet
         */
        public HSetExOptionsBuilder expiry(ExpirySet expiry) {
            this.expiry = expiry;
            return this;
        }

        /**
         * Builds and returns a new HSetExOptions instance.
         *
         * <p>This method creates an immutable HSetExOptions instance with the configuration specified
         * in this builder. The resulting instance is thread-safe and can be reused across multiple
         * command invocations.
         *
         * <p>No validation is performed at build time - validation occurs when the options are
         * converted to command arguments via {@link HSetExOptions#toArgs()}. This allows for more
         * flexible usage patterns while still ensuring correctness at execution time.
         *
         * @return a new HSetExOptions instance with the specified configuration
         * @example
         *     <pre>{@code
         * HSetExOptions options = HSetExOptions.builder()
         *     .onlyIfNoneExist()
         *     .expiry(ExpirySet.Seconds(60L))
         *     .build();
         *
         * // The options instance is now immutable and thread-safe
         * CompletableFuture<Long> result1 = client.hsetex("hash1", fields1, options);
         * CompletableFuture<Long> result2 = client.hsetex("hash2", fields2, options);
         * }</pre>
         */
        public HSetExOptions build() {
            return new HSetExOptions(fieldConditionalChange, expiry);
        }
    }
}
