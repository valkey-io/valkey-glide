/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/**
 * Jedis client configuration interface for compatibility with existing Jedis code. This interface
 * provides the same configuration options as Jedis while mapping them to appropriate Valkey GLIDE
 * configurations.
 */
public interface JedisClientConfig {

    /**
     * Get the connection timeout in milliseconds.
     *
     * @return connection timeout
     */
    int getConnectionTimeoutMillis();

    /**
     * Get the socket timeout in milliseconds.
     *
     * @return socket timeout
     */
    int getSocketTimeoutMillis();

    /**
     * Get the blocking socket timeout in milliseconds.
     *
     * @return blocking socket timeout
     */
    int getBlockingSocketTimeoutMillis();

    /**
     * Get the username for authentication.
     *
     * @return username
     */
    String getUser();

    /**
     * Get the password for authentication.
     *
     * @return password
     */
    String getPassword();

    /**
     * Get the database number to select.
     *
     * @return database number
     */
    int getDatabase();

    /**
     * Get the client name.
     *
     * @return client name
     */
    String getClientName();

    /**
     * Check if SSL/TLS is enabled.
     *
     * @return true if SSL is enabled
     */
    boolean isSsl();

    /**
     * Get the SSL socket factory.
     *
     * @return SSL socket factory
     */
    SSLSocketFactory getSslSocketFactory();

    /**
     * Get the SSL parameters.
     *
     * @return SSL parameters
     */
    SSLParameters getSslParameters();

    /**
     * Get the hostname verifier.
     *
     * @return hostname verifier
     */
    HostnameVerifier getHostnameVerifier();

    /**
     * Get the Redis protocol version.
     *
     * @return protocol version
     */
    RedisProtocol getRedisProtocol();

    /**
     * Check if the client should send a CLIENT SETNAME command.
     *
     * @return true if client name should be sent
     */
    default boolean isClientNameSent() {
        return getClientName() != null;
    }
}
