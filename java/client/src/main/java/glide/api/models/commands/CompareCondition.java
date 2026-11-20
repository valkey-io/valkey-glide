/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.models.GlideString;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A condition that compares the current value of a key against a comparison value.
 *
 * <p>Not supported when compression is enabled. See <a
 * href="https://glide.valkey.io/concepts/client-features/compression/#incompatible-commands">Valkey
 * GLIDE – Data Compression</a>.
 *
 * @see <a href="https://valkey.io/commands/delex/">valkey.io</a>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CompareCondition {

    /** The kind of comparison. */
    private final Condition condition;

    /** The value to compare against the current value of the key. */
    private final GlideString comparisonValue;

    /** Kinds of value comparison. */
    @RequiredArgsConstructor
    private enum Condition {
        IF_EQUALS("IFEQ"),
        IF_NOT_EQUALS("IFNE");

        private final String valkeyApi;
    }

    /**
     * A condition that is met if the key exists and its current value is equal to <code>value</code>
     * (<code>IFEQ comparison-value</code>).
     *
     * @param value The comparison value.
     * @return The value comparison condition.
     */
    public static CompareCondition ifEquals(@NonNull String value) {
        return ifEquals(GlideString.gs(value));
    }

    /**
     * A condition that is met if the key exists and its current value is equal to <code>value</code>
     * (<code>IFEQ comparison-value</code>).
     *
     * @param value The comparison value.
     * @return The value comparison condition.
     */
    public static CompareCondition ifEquals(@NonNull GlideString value) {
        return new CompareCondition(Condition.IF_EQUALS, value);
    }

    /**
     * A condition that is met if the key does not exist or its current value is not equal to <code>
     * value</code> (<code>IFNE comparison-value</code>).
     *
     * @param value The comparison value.
     * @return The value comparison condition.
     */
    public static CompareCondition ifNotEquals(@NonNull String value) {
        return ifNotEquals(GlideString.gs(value));
    }

    /**
     * A condition that is met if the key does not exist or its current value is not equal to <code>
     *  value</code> (<code>IFNE comparison-value</code>).
     *
     * @param value The comparison value.
     * @return The value comparison condition.
     */
    public static CompareCondition ifNotEquals(@NonNull GlideString value) {
        return new CompareCondition(Condition.IF_NOT_EQUALS, value);
    }

    /**
     * Converts the value comparison into a GlideString[] to pass to the command.
     *
     * @return GlideString[]
     */
    public GlideString[] toArgs() {
        return new GlideString[] {GlideString.gs(condition.valkeyApi), comparisonValue};
    }
}
