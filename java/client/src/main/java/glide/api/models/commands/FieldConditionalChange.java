/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Field-specific conditional change options for HSETEX command.
 *
 * <p>These options control when hash fields should be set based on their current existence state.
 * They provide fine-grained control over field creation and updates in hash field expiration
 * commands.
 *
 * @example
 *     <pre>{@code
 * // Only set fields if all of them already exist
 * FieldConditionalChange condition = FieldConditionalChange.ONLY_IF_ALL_EXIST;
 *
 * // Only set fields if none of them already exist
 * FieldConditionalChange condition = FieldConditionalChange.ONLY_IF_NONE_EXIST;
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hsetex/">HSETEX Command Documentation</a>
 */
@RequiredArgsConstructor
@Getter
public enum FieldConditionalChange {
    /**
     * Only set fields if all of them already exist. Equivalent to <code>FXX</code> in the Valkey API.
     *
     * <p>When this option is used, the HSETEX command will only update existing fields and will not
     * create new fields. If any of the specified fields do not exist, the entire operation fails.
     *
     * @example
     *     <pre>{@code
     * // This will only succeed if both "field1" and "field2" already exist in the hash
     * HSetExOptions options = HSetExOptions.builder()
     *     .fieldConditionalChange(FieldConditionalChange.ONLY_IF_ALL_EXIST)
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     * }</pre>
     */
    ONLY_IF_ALL_EXIST("FXX"),

    /**
     * Only set fields if none of them already exist. Equivalent to <code>FNX</code> in the Valkey
     * API.
     *
     * <p>When this option is used, the HSETEX command will only create new fields and will not update
     * existing fields. If any of the specified fields already exist, the entire operation fails.
     *
     * @example
     *     <pre>{@code
     * // This will only succeed if neither "field1" nor "field2" exist in the hash
     * HSetExOptions options = HSetExOptions.builder()
     *     .fieldConditionalChange(FieldConditionalChange.ONLY_IF_NONE_EXIST)
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     * }</pre>
     */
    ONLY_IF_NONE_EXIST("FNX");

    /** The Valkey API string representation of this conditional change option. */
    private final String valkeyApi;
}
