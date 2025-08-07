/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/** JedisClientConfig compatibility interface for Valkey GLIDE wrapper. */
public interface JedisClientConfig {

    /**
     * Get the connection timeout in milliseconds.
     *
     * @return connection timeout
     */
    default int getConnectionTimeoutMillis() {
        return 2000;
    }

    /**
     * Get the socket timeout in milliseconds.
     *
     * @return socket timeout
     */
    default int getSocketTimeoutMillis() {
        return 2000;
    }

    /**
     * Get the blocking socket timeout in milliseconds.
     *
     * @return blocking socket timeout
     */
    default int getBlockingSocketTimeoutMillis() {
        return 0;
    }

    /**
     * Get the username for authentication.
     *
     * @return username
     */
    default String getUser() {
        return null;
    }

    /**
     * Get the password for authentication.
     *
     * @return password
     */
    default String getPassword() {
        return null;
    }

    /**
     * Get the database number.
     *
     * @return database number
     */
    default int getDatabase() {
        return 0;
    }

    /**
     * Get the client name.
     *
     * @return client name
     */
    default String getClientName() {
        return null;
    }

    /**
     * Check if SSL is enabled.
     *
     * @return true if SSL is enabled
     */
    default boolean isSsl() {
        return false;
    }

    /**
     * Get the SSL socket factory.
     *
     * @return SSL socket factory
     */
    default SSLSocketFactory getSslSocketFactory() {
        return null;
    }

    /**
     * Get the SSL parameters.
     *
     * @return SSL parameters
     */
    default SSLParameters getSslParameters() {
        return null;
    }

    /**
     * Get the hostname verifier for SSL connections.
     *
     * @return hostname verifier
     */
    default HostnameVerifier getHostnameVerifier() {
        return null;
    }

    /**
     * Get the Redis protocol version.
     *
     * @return protocol version
     */
    default RedisProtocol getRedisProtocol() {
        return RedisProtocol.getDefault();
    }

    /**
     * Check if client name is set.
     *
     * @return true if client name is set
     */
    default boolean isClientNameSet() {
        return getClientName() != null;
    }
}
