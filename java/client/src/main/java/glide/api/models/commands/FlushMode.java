/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.api.models.configuration.RequestRoutingConfiguration.SingleNodeRoute;

// TODO add links to script flush
/**
 * Defines flushing mode for:
 *
 * <ul>
 *   <li><code>FLUSHALL</code> command implemented by {@link RedisClient#flushall(FlushMode)},
 *       {@link RedisClusterClient#flushall(FlushMode)}, and {@link
 *       RedisClusterClient#flushall(FlushMode, SingleNodeRoute)}.
 *   <li><code>FUNCTION FLUSH</code> command implemented by {@link
 *       RedisClient#functionFlush(FlushMode)}, {@link RedisClusterClient#functionFlush(FlushMode)},
 *       and {@link RedisClusterClient#functionFlush(FlushMode, Route)}.
 * </ul>
 *
 * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a> and <a
 *     href="https://valkey.io/commands/function-flush/">valkey.io</a>
 */
public enum FlushMode {
    /** Flushes synchronously. */
    SYNC,
    /** Flushes asynchronously. */
    ASYNC
}
