/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents the time and latency for a latency spike.
 *
 * @see <a href="https://valkey.io/commands/latency-history/">valkey.io</a> for details.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public final class LatencyEntry {

    /** The time of the latency spike, as a Unix timestamp in seconds. */
    private final long time;

    /** The duration of the latency spike, in milliseconds. */
    private final long latency;
}
