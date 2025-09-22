/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Represents the strategy used to determine how and when to reconnect, in case of connection
 * failures. The time between attempts grows exponentially, following the formula <code>
 * rand(0 ... factor *
 * (exponentBase ^ N))</code>, where <code>N</code> is the number of failed attempts, and <code>
 * rand(...)</code> applies a jitter of up to <code>jitterPercent</code>% to introduce randomness
 * and reduce retry storms.
 *
 * <p>Once the maximum value is reached, that will remain the time between retry attempts until a
 * reconnect attempt is successful. The client will attempt to reconnect indefinitely.
 *
 * @example
 *     <pre>{@code
 * BackoffStrategy reconnectionConfiguration = BackoffStrategy.builder()
 *     .numOfRetries(5)
 *     .exponentBase(2)
 *     .factor(100)  // 100 milliseconds base delay
 *     .jitterPercent(20)
 *     .build()
 * }</pre>
 */
@Getter
@Builder
@ToString
@SuppressFBWarnings(
        value = "CT_CONSTRUCTOR_THROW",
        justification = "Builder enforces non-null invariants and throws before exposing instance")
public class BackoffStrategy {
    /**
     * Number of retry attempts that the client should perform when disconnected from the server,
     * where the time between retries increases. Once the retries have reached the maximum value, the
     * time between retries will remain constant until a reconnect attempt is successful.
     */
    @NonNull private final Integer numOfRetries;

    /**
     * The multiplier that will be applied to the waiting time between each retry. This value is
     * specified in milliseconds.
     */
    @NonNull private final Integer factor;

    /** The exponent base configured for the strategy. */
    @NonNull private final Integer exponentBase;

    /** The Jitter percent on the calculated duration. If not set, a default value will be used. */
    private final Integer jitterPercent;
}
