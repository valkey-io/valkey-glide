/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Key-level conditional change options for the HSETEX command.
 *
 * <p>These options control when the HSETEX command should run based on the existence of the hash
 * key itself, as opposed to {@link FieldConditionalChange} which controls behavior based on the
 * existence of individual fields.
 *
 * @example
 *     <pre>{@code
 * // Only run the command if the key already exists
 * HashConditionalChange condition = HashConditionalChange.ONLY_IF_KEY_EXISTS;
 *
 * // Only run the command if the key does not already exist
 * HashConditionalChange condition = HashConditionalChange.ONLY_IF_KEY_DOES_NOT_EXIST;
 * }</pre>
 *
 * @since Valkey 9.1.0
 * @see <a href="https://valkey.io/commands/hsetex/">HSETEX Command Documentation</a>
 */
@RequiredArgsConstructor
@Getter
public enum HashConditionalChange {
    /**
     * Only set fields if the key already exists. Equivalent to <code>XX</code> in the Valkey API.
     *
     * <p>When this option is used, the HSETEX command will only run if the hash key already exists.
     * If the key does not exist, the entire operation fails.
     *
     * @example
     *     <pre>{@code
     * // This will only succeed if "myHash" already exists
     * HSetExOptions options = HSetExOptions.builder()
     *     .conditionalChange(HashConditionalChange.ONLY_IF_KEY_EXISTS)
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     * }</pre>
     */
    ONLY_IF_KEY_EXISTS("XX"),

    /**
     * Only set fields if the key does not already exist. Equivalent to <code>NX</code> in the Valkey
     * API.
     *
     * <p>When this option is used, the HSETEX command will only run if the hash key does not already
     * exist. If the key already exists, the entire operation fails.
     *
     * @example
     *     <pre>{@code
     * // This will only succeed if "myHash" does not already exist
     * HSetExOptions options = HSetExOptions.builder()
     *     .conditionalChange(HashConditionalChange.ONLY_IF_KEY_DOES_NOT_EXIST)
     *     .expiry(ExpirySet.Seconds(60L))
     *     .build();
     * }</pre>
     */
    ONLY_IF_KEY_DOES_NOT_EXIST("NX");

    /** The Valkey API string representation of this conditional change option. */
    private final String valkeyApi;
}
