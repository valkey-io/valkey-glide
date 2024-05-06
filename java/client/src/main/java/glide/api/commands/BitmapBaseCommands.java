/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.BitMapOptions;
import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "Bitmap Commands" group for standalone and cluster
 * clients.
 *
 * @see <a href="https://redis.io/docs/latest/commands/?group=bitmap">Bitmap Commands</a>
 */
public interface BitmapBaseCommands {
    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/bitcount/">redis.io</a> for details.
     * @param key The key to count set bits of.
     * @return The number set bits in the string. Returns zero if the key is missing as it treated as
     *     an empty string.
     * @example
     *     <pre>{@code
     * Long payload = client.bitcount("myKey1").get();
     * assert payload == 2L; // Count of set bits in a string
     * }</pre>
     */
    CompletableFuture<Long> bitcount(String key);

    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/bitcount/">redis.io</a> for details.
     * @param key The key to count set bits of.
     * @param start The starting offset byte index.
     * @param end The ending offset byte index.
     * @return The number set bits in the string. Returns zero if the key is missing as it treated as
     *     an empty string.
     * @example
     *     <pre>{@code
     * Long payload = client.bitcount("myKey1", 1, 1).get();
     * assert payload == 2L; // Count of set bits in a string with offset
     * }</pre>
     */
    CompletableFuture<Long> bitcount(String key, long start, long end);

    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>.
     *
     * @see <a href="https://redis.io/commands/bitcount/">redis.io</a> for details.
     * @param key The key to count set bits of.
     * @param start The starting offset.
     * @param end The ending offset.
     * @param options The index offset type. Could be either {@link BitMapOptions#BIT} or {@link
     *     BitMapOptions#BYTE}.
     * @return The number set bits in the string. Returns zero if the key is missing as it treated as
     *     an empty string.
     * @example
     *     <pre>{@code
     * Long payload = client.bitcount("myKey1", 1, 1, BIT).get();
     * assert payload == 2L; // Count of set bits in a string with BIT index offset
     * }</pre>
     */
    CompletableFuture<Long> bitcount(String key, long start, long end, BitMapOptions options);
}
