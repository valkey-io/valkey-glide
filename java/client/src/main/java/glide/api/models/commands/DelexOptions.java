/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.models.GlideString;
import glide.utils.ArgsBuilder;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Arguments for the {@link glide.api.BaseClient#delex(String, DelexOptions) DELEX} command.
 *
 * <p>Not supported when compression is enabled. See <a
 * href="https://glide.valkey.io/concepts/client-features/compression/#incompatible-commands">Valkey
 * GLIDE – Data Compression</a>.
 *
 * @since Valkey 9.2.0
 * @see <a href="https://valkey.io/commands/delex/">DELEX Command Documentation</a>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public final class DelexOptions {

    /** The condition that the key must meet to be deleted. */
    private final ConditionalDelete conditionalDelete;

    /** Value to compare against the key. */
    private final GlideString comparisonValue;

    /** Conditions that the key must meet to be deleted. */
    @RequiredArgsConstructor
    private enum ConditionalDelete {
        ONLY_IF_EQUAL("IFEQ"),
        ONLY_IF_NOT_EQUAL("IFNE");

        private final String valkeyApi;
    }

    /**
     * Only delete the key if its value equals <code>value</code>. Equivalent to <code>IFEQ value
     * </code> in the Valkey API.
     *
     * @param value the value to compare against the key
     * @return the options
     */
    public static DelexOptions onlyIfEqualTo(@NonNull String value) {
        return onlyIfEqualTo(GlideString.gs(value));
    }

    /**
     * Only delete the key if its value equals <code>value</code>. Equivalent to <code>IFEQ value
     * </code> in the Valkey API.
     *
     * @param value the value to compare against the key
     * @return the options
     */
    public static DelexOptions onlyIfEqualTo(@NonNull GlideString value) {
        return new DelexOptions(ConditionalDelete.ONLY_IF_EQUAL, value);
    }

    /**
     * Only delete the key if its value does not equal <code>value</code>. Equivalent to <code>
     * IFNE value</code> in the Valkey API.
     *
     * @param value the value to compare against the key
     * @return the options
     */
    public static DelexOptions onlyIfNotEqualTo(@NonNull String value) {
        return onlyIfNotEqualTo(GlideString.gs(value));
    }

    /**
     * Only delete the key if its value does not equal <code>value</code>. Equivalent to <code>
     * IFNE value</code> in the Valkey API.
     *
     * @param value the value to compare against the key
     * @return the options
     */
    public static DelexOptions onlyIfNotEqualTo(@NonNull GlideString value) {
        return new DelexOptions(ConditionalDelete.ONLY_IF_NOT_EQUAL, value);
    }

    /**
     * Converts options into command arguments for the DELEX command.
     *
     * @return GlideString[] containing the command arguments for these options
     * @example
     *     <pre>{@code
     * GlideString[] args = DelexOptions.onlyIfEqualTo("value").toArgs(); // Returns ["IFEQ", "value"]
     * }</pre>
     */
    public GlideString[] toArgs() {
        return new ArgsBuilder().add(conditionalDelete.valkeyApi).add(comparisonValue).toArray();
    }
}
