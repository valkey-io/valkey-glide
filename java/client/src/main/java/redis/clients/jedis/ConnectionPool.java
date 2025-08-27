/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import redis.clients.jedis.util.Pool;

/**
 * ConnectionPool compatibility stub for Valkey GLIDE wrapper.
 *
 * @deprecated ConnectionPool is not supported in the GLIDE compatibility layer. Use JedisPool for
 *     connection pooling instead. See <a
 *     href="https://github.com/valkey-io/valkey-glide/blob/main/java/MIGRATION.md">Migration
 *     guide</a> for more details.
 */
@Deprecated
public class ConnectionPool extends Pool<Connection> {

    public ConnectionPool() {
        throw new UnsupportedOperationException(
                "ConnectionPool is not supported in GLIDE compatibility layer. GLIDE uses a different"
                        + " connection management architecture. Please use JedisPool for connection pooling"
                        + " instead. See migration guide:"
                        + " https://github.com/valkey-io/valkey-glide/blob/main/java/MIGRATION.md");
    }

    @Override
    public Connection getResource() {
        throw new UnsupportedOperationException(
                "ConnectionPool is not supported in GLIDE compatibility layer. Use JedisPool instead.");
    }
}
