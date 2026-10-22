/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Key-level conditional change options for the HSETEX command.
 *
 * <p>These options control when the HSETEX command sets fields based on the existence of the hash
 * key itself.
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
     * <p>When this option is used, the HSETEX command will only set fields if the hash key already
     * exists. If the key does not exist, the value is not set.
     */
    ONLY_IF_KEY_EXISTS("XX"),

    /**
     * Only set fields if the key does not already exist. Equivalent to <code>NX</code> in the Valkey
     * API.
     *
     * <p>When this option is used, the HSETEX command will only set fields if the hash key does not
     * already exist. If the key already exists, the value is not set.
     */
    ONLY_IF_KEY_DOES_NOT_EXIST("NX");

    /** The Valkey API string representation of this conditional change option. */
    private final String valkeyApi;
}
