/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.BitmapIndexType;
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
     * @param key The key for the string to count the set bits of.
     * @return The number of set bits in the string. Returns zero if the key is missing as it is
     *     treated as an empty string.
     * @example
     *     <pre>{@code
     * Long payload = client.bitcount("myKey1").get();
     * assert payload == 2L;The string stored at "myKey1" contains 2 set bits.
     * }</pre>
     */
    CompletableFuture<Long> bitcount(String key);

    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>. The
     * offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on. These
     * offsets can also be negative numbers indicating offsets starting at the end of the list, with
     * <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and
     * so on.
     *
     * @see <a href="https://redis.io/commands/bitcount/">redis.io</a> for details.
     * @param key The key for the string to count the set bits of.
     * @param start The starting offset byte index.
     * @param end The ending offset byte index.
     * @return The number of set bits in the string byte interval specified by <code>start</code> and
     *     <code>end</code>. Returns zero if the key is missing as it is treated as an empty string.
     * @example
     *     <pre>{@code
     * Long payload = client.bitcount("myKey1", 1, 3).get();
     * assert payload == 2L; // The second to forth bytes of the string stored at "myKey1" contains 2 set bits.
     * }</pre>
     */
    CompletableFuture<Long> bitcount(String key, long start, long end);

    /**
     * Counts the number of set bits (population counting) in a string stored at <code>key</code>. The
     * offsets <code>start</code> and <code>end</code> are zero-based indexes, with <code>0</code>
     * being the first element of the list, <code>1</code> being the next element and so on. These
     * offsets can also be negative numbers indicating offsets starting at the end of the list, with
     * <code>-1</code> being the last element of the list, <code>-2</code> being the penultimate, and
     * so on.
     *
     * @since Redis 7.0 and above
     * @see <a href="https://redis.io/commands/bitcount/">redis.io</a> for details.
     * @param key The key for the string to count the set bits of.
     * @param start The starting offset.
     * @param end The ending offset.
     * @param options The index offset type. Could be either {@link BitmapIndexType#BIT} or {@link
     *     BitmapIndexType#BYTE}.
     * @return The number of set bits in the string interval specified by <code>start</code>, <code>
     *     end</code>, and <code>options</code>. Returns zero if the key is missing as it is treated
     *     as an empty string.
     * @example
     *     <pre>{@code
     * Long payload = client.bitcount("myKey1", 1, 1, BIT).get();
     * assert payload == 1L; // Indicates that the second bit of the string stored at "myKey1" is set.
     * }</pre>
     */
    CompletableFuture<Long> bitcount(String key, long start, long end, BitmapIndexType options);
}
