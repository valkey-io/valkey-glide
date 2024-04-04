/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Supports commands and transactions for the "HyperLogLog Commands" group for standalone and
 * cluster clients.
 *
 * @see <a href="https://redis.io/commands/?group=hyperloglog">HyperLogLog Commands</a>
 */
public interface HyperLogLogBaseCommands {

    /**
     * Adds all elements to the HyperLogLog data structure stored at the specified <code>key</code>.
     * <br>
     * Creates a new structure if the <code>key</code> does not exist.
     *
     * <p>When no <code>elements</code> are provided, and <code>key</code> exists and is a
     * HyperLogLog, then no operation is performed. If <code>key</code> does not exist, then the
     * HyperLogLog structure is created.
     *
     * @see <a href="https://redis.io/commands/pfadd/">redis.io</a> for details.
     * @param key The <code>key</code> of the HyperLogLog data structure to add elements into.
     * @param elements An array of members to add to the HyperLogLog stored at <code>key</code>.
     * @return If the HyperLogLog is newly created, or if the HyperLogLog approximated cardinality is
     *     altered, then returns <code>1</code>. Otherwise, returns <code>0</code>.
     * @example
     *     <pre>{@code
     * Long result = client.pfadd("hll_1", new String[] { "a", "b", "c" }).get();
     * assert result == 1L; // A data structure was created or modified
     *
     * result = client.pfadd("hll_2", new String[0]).get();
     * assert result == 1L; // A new empty data structure was created
     * }</pre>
     */
    CompletableFuture<Long> pfadd(String key, String[] elements);
}
