/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api.models.commands;

import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;

/**
 * Defines flushing mode for <code>FLUSHALL</code> command implemented by {@link
 * RedisClient#flushall(FlushMode)}, {@link RedisClusterClient#flushall(FlushMode)}, and {@link
 * RedisClusterClient#flushall(FlushMode, Route)}.
 *
 * @see <a href="https://valkey.io/commands/flushall/">valkey.io</a>
 */
public enum FlushMode {
    /** Flushes the databases synchronously. */
    SYNC,
    /** Flushes the databases asynchronously. */
    ASYNC
}
