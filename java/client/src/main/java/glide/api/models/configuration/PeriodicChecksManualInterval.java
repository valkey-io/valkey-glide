/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.configuration;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Represents a manually configured interval for periodic checks.
 *
 * @example
 *     <pre>{@code
 * PeriodicChecksManualInterval intervalConfig = PeriodicChecksManualInterval.builder()
 *     .durationInSec(30)
 *     .build();
 * }</pre>
 *
 * @see PeriodicChecksStatus
 */
@Getter
@Builder
@ToString
public class PeriodicChecksManualInterval implements PeriodicChecksConfig {
    /** The duration in seconds for the interval between periodic checks. */
    @NonNull private final Integer durationInSec;
}
