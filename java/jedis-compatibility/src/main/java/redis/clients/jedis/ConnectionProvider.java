/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import java.io.Closeable;

/**
 * Interface for providing connections to Redis instances. This is part of the Jedis compatibility
 * layer.
 */
public interface ConnectionProvider extends Closeable {

    /** Get a connection from the provider */
    Connection getConnection();

    /** Get the client configuration */
    JedisClientConfig getClientConfig();

    /** Close the provider and all its connections */
    @Override
    void close();
}
