/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Information about an event's latency spike time series.
 *
 * @see <a href="https://valkey.io/commands/latency-latest/">valkey.io</a> for details.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class LatencyEventInfo {

    /** The name of the event. */
    private final String eventName;

    /** The time of the latest latency spike, as a Unix timestamp in seconds. */
    private final long latestTime;

    /** The duration of the latest latency spike, in milliseconds. */
    private final long latestDuration;

    /** The all-time maximum duration of a latency spike, in milliseconds. */
    private final long maxDuration;

    /**
     * The sum of all latency spike durations in the event's time series, in milliseconds.<br>
     * Only populated for Valkey 8.1+.
     */
    private final Optional<Long> sum;

    /**
     * The number of latency spikes recorded in the event's time series.<br>
     * Only populated for Valkey 8.1+.
     */
    private final Optional<Long> count;
}
