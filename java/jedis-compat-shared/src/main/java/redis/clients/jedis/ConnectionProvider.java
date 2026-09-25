/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.io.Closeable;

/**
 * Interface for providing connections to Redis instances. This is part of the Jedis compatibility
 * layer.
 */
public interface ConnectionProvider extends Closeable {

    /**
     * Get a connection from the provider
     *
     * @return a {@code Connection} carrying an address; consulted only on the standalone path,
     *     where its host and port seed the GLIDE client, and never used to issue commands
     */
    Connection getConnection();

    /**
     * Get the client configuration
     *
     * @return the client configuration this provider was built with
     */
    JedisClientConfig getClientConfig();

    /** Close the provider and all its connections */
    @Override
    void close();
}
