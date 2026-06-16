/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents data about an event's latency spike time series.
 *
 * @see <a href="https://valkey.io/commands/latency-latest/">valkey.io</a> for details.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class LatencyInfo {

    /** The name of the event. */
    private final String eventName;

    /** The time of the latest latency spike for the event, as a Unix timestamp in seconds. */
    private final long time;

    /** The duration of the latest latency spike for the event, in milliseconds. */
    private final long latest;

    /** The all-time maximum duration of a latency spike for the event, in milliseconds. */
    private final long maximum;

    /**
     * The sum of all latency spike durations in the event's time series, in milliseconds.
     * Only populated for Valkey 8.1+ servers.
     */
    private final Optional<Long> sum;

    /**
     * The number of latency spikes recorded in the event's time series.
     * Only populated for Valkey 8.1+ servers.
     */
    private final Optional<Long> count;
}
