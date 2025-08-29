/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Optional arguments for the {@link glide.api.BaseClient#hgetex(String, String[], HGetExOptions)
 * HGETEX} command.
 *
 * <p>HGETEX retrieves hash field values and optionally sets their expiration time in a single
 * atomic operation. This class supports:
 *
 * <ul>
 *   <li>Standard expiry options (EX/PX/EXAT/PXAT) - set expiration times
 *   <li>PERSIST option - remove expiration from fields
 * </ul>
 *
 * <p>This class provides compile-time safety by only allowing parameter combinations that are valid
 * for the HGETEX command. It excludes options like KEEPTTL (use PERSIST instead), field conditional
 * changes (which are only for HSETEX), and expiration conditions (which are only for HEXPIRE-family
 * commands).
 *
 * <p>All instances of this class are immutable and thread-safe after construction.
 *
 * @example
 *     <pre>{@code
 * // Retrieve fields and set them to expire in 60 seconds
 * HGetExOptions options = HGetExOptions.builder()
 *     .expiry(HGetExExpiry.Seconds(60L))
 *     .build();
 *
 * String[] values = client.hgetex("myHash", new String[]{"field1", "field2"}, options).get();
 *
 * // Retrieve fields and remove their expiration (make persistent)
 * HGetExOptions options2 = HGetExOptions.builder()
 *     .expiry(HGetExExpiry.Persist())
 *     .build();
 *
 * // Retrieve fields and set expiration to specific Unix timestamp
 * HGetExOptions options3 = HGetExOptions.builder()
 *     .expiry(HGetExExpiry.UnixMilliseconds(1640995200000L))
 *     .build();
 *
 * // Retrieve fields without changing expiration (no options needed)
 * String[] values2 = client.hgetex("myHash", new String[]{"field1", "field2"}, null).get();
 * }</pre>
 *
 * @since Valkey 9.0.0
 * @see <a href="https://valkey.io/commands/hgetex/">HGETEX Command Documentation</a>
 * @see HGetExExpiry
 */
@Builder
@EqualsAndHashCode
@ToString
public final class HGetExOptions {

    /** Expiry configuration for the hash fields after retrieval. */
    private final HGetExExpiry expiry;

    /**
     * Converts options into command arguments for the HGETEX command.
     *
     * <p>This method generates the appropriate command arguments based on the expiry configuration.
     * If no expiry is specified, it returns an empty array, allowing the HGETEX command to retrieve
     * field values without modifying their expiration.
     *
     * <p>The argument order follows the HGETEX command specification:
     *
     * <ol>
     *   <li>Expiry options (EX/PX/EXAT/PXAT/PERSIST) if specified
     * </ol>
     *
     * @return String[] containing the command arguments for these options, or empty array if no
     *     options
     * @example
     *     <pre>{@code
     * HGetExOptions options1 = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Seconds(60L))
     *     .build();
     * String[] args1 = options1.toArgs(); // Returns ["EX", "60"]
     *
     * HGetExOptions options2 = HGetExOptions.builder()
     *     .expiry(HGetExExpiry.Persist())
     *     .build();
     * String[] args2 = options2.toArgs(); // Returns ["PERSIST"]
     *
     * HGetExOptions options3 = HGetExOptions.builder().build();
     * String[] args3 = options3.toArgs(); // Returns []
     * }</pre>
     */
    public String[] toArgs() {
        if (expiry == null) {
            return new String[0];
        }
        return expiry.toArgs();
    }
}
