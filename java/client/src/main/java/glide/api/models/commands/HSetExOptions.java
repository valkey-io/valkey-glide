/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Optional arguments for the {@link glide.api.BaseClient#hsetex(String, java.util.Map,
 * HSetExOptions) HSETEX} command.
 *
 * <p>HSETEX sets hash fields with expiration times and supports:
 *
 * <ul>
 *   <li>Key conditional changes (NX/XX) - control when fields are set based on key existence
 *   <li>Field conditional changes (FNX/FXX) - control when fields are set based on field existence
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
 *
 * // Set the fields only if the key already exists, and only if none of the fields already exist
 * HSetExOptions options4 = HSetExOptions.builder()
 *     .onlyIfKeyExists()
 *     .onlyIfNoneExist()
 *     .expiry(ExpirySet.Seconds(60L))
 *     .build();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hsetex/">HSETEX Command Documentation</a>
 * @see HashConditionalChange
 * @see FieldConditionalChange
 * @see ExpirySet
 */
@Builder
@EqualsAndHashCode
@ToString
public final class HSetExOptions {

    /**
     * Key-level conditional change option (NX/XX) for controlling when fields are set, based on the
     * existence of the hash key.
     *
     * @since Valkey 9.1.0
     */
    private final HashConditionalChange conditionalChange;

    /** Field conditional change option (FNX/FXX) for controlling when fields are set. */
    private final FieldConditionalChange fieldConditionalChange;

    /** Expiry configuration for the hash fields. */
    private final ExpirySet expiry;

    /**
     * Converts options into command arguments for the HSETEX command.
     *
     * <p>This method validates that the expiry options are compatible with HSETEX and generates the
     * appropriate command arguments in the correct order.
     *
     * <p>The argument order follows the HSETEX command specification:
     *
     * <ol>
     *   <li>Key conditional change (NX/XX) if specified
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

        if (conditionalChange != null) {
            args.add(conditionalChange.getValkeyApi());
        }

        if (fieldConditionalChange != null) {
            args.add(fieldConditionalChange.getValkeyApi());
        }

        if (expiry != null) {
            expiry.validateForCommand("HSETEX");
            args.addAll(Arrays.asList(expiry.toArgs()));
        }

        return args.toArray(new String[0]);
    }

    /** Builder class for {@link HSetExOptions}. */
    public static class HSetExOptionsBuilder {

        /**
         * Sets the key-level conditional change to only set fields if the key already exists.
         *
         * <p>This is equivalent to the XX option in the Valkey HSETEX command. If the key does not
         * exist, the value is not set.
         *
         * @return this builder instance for method chaining
         * @since Valkey 9.1.0
         * @see HashConditionalChange#ONLY_IF_KEY_EXISTS
         */
        public HSetExOptionsBuilder onlyIfKeyExists() {
            return conditionalChange(HashConditionalChange.ONLY_IF_KEY_EXISTS);
        }

        /**
         * Sets the key-level conditional change to only set fields if the key does not already exist.
         *
         * <p>This is equivalent to the NX option in the Valkey HSETEX command. If the key already
         * exists, the value is not set.
         *
         * @return this builder instance for method chaining
         * @since Valkey 9.1.0
         * @see HashConditionalChange#ONLY_IF_KEY_DOES_NOT_EXIST
         */
        public HSetExOptionsBuilder onlyIfKeyDoesNotExist() {
            return conditionalChange(HashConditionalChange.ONLY_IF_KEY_DOES_NOT_EXIST);
        }

        /**
         * Sets the field conditional change to only set fields if all of them already exist.
         *
         * <p>This is equivalent to the FXX option in the Valkey HSETEX command. When this option is
         * used, the command will only update existing fields and will not create new fields.
         *
         * @return this builder instance for method chaining
         * @see FieldConditionalChange#ONLY_IF_ALL_EXIST
         */
        public HSetExOptionsBuilder onlyIfAllExist() {
            return fieldConditionalChange(FieldConditionalChange.ONLY_IF_ALL_EXIST);
        }

        /**
         * Sets the field conditional change to only set fields if none of them already exist.
         *
         * <p>This is equivalent to the FNX option in the Valkey HSETEX command. When this option is
         * used, the command will only create new fields and will not update existing fields.
         *
         * @return this builder instance for method chaining
         * @see FieldConditionalChange#ONLY_IF_NONE_EXIST
         */
        public HSetExOptionsBuilder onlyIfNoneExist() {
            return fieldConditionalChange(FieldConditionalChange.ONLY_IF_NONE_EXIST);
        }
    }
}
