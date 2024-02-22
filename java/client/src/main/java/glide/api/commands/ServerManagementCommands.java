/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.commands;

import glide.api.models.commands.InfoOptions;
import glide.api.models.commands.InfoOptions.Section;
import java.util.concurrent.CompletableFuture;

/**
 * Server Management Commands interface.
 *
 * @see <a href="https://redis.io/commands/?group=server">Server Management Commands</a>
 */
public interface ServerManagementCommands {

    /**
     * Get information and statistics about the Redis server using the {@link Section#DEFAULT} option.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @return Response from Redis containing a <code>String</code> with the information for the
     *     default sections.
     */
    CompletableFuture<String> info();

    /**
     * Get information and statistics about the Redis server.
     *
     * @see <a href="https://redis.io/commands/info/">redis.io</a> for details.
     * @param options A list of {@link Section} values specifying which sections of information to
     *     retrieve. When no parameter is provided, the {@link Section#DEFAULT} option is assumed.
     * @return Response from Redis containing a <code>String</code> with the information for the
     *     sections requested.
     */
    CompletableFuture<String> info(InfoOptions options);

    /**
     * Change the currently selected Redis database.
     *
     * @see <a href="https://redis.io/commands/select/">redis.io</a> for details.
     * @param index The index of the database to select.
     * @return A simple <code>OK</code> response.
     */
    CompletableFuture<String> select(long index);
}
