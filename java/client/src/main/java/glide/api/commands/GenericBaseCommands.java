/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/**
 * Generic Commands interface to handle generic commands for all server requests.
 *
 * @see <a href="https://redis.io/commands/?group=generic">Generic Commands</a>
 */
public interface GenericBaseCommands {

    /**
     * Removes the specified <code>keys</code>. A key is ignored if it does not exist.
     *
     * @see <a href="https://redis.io/commands/del/">redis.io</a> for details.
     * @param keys the keys we wanted to remove.
     * @return the number of keys that were removed.
     */
    CompletableFuture<Long> del(String[] keys);
}
