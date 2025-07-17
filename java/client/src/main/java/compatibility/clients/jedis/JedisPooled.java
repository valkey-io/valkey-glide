/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.models.configuration.GlideClientConfiguration;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

/**
 * JedisPooled compatibility wrapper for Valkey GLIDE client. This class provides a Jedis-like
 * pooled connection API while using Valkey GLIDE underneath. This is the recommended client
 * for standalone Redis/Valkey usage.
 */
public class JedisPooled extends UnifiedJedis {

    /** Create a new JedisPooled with default localhost:6379 connection. */
    public JedisPooled() {
        this("localhost", 6379);
    }

    /**
     * Create a new JedisPooled with specified host and port.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     */
    public JedisPooled(String host, int port) {
        this(host, port, DefaultJedisClientConfig.builder().build());
    }

    /**
     * Create a new JedisPooled with specified host, port and SSL configuration.
     *
     * @param host the Redis/Valkey server host
     * @param port the Redis/Valkey server port
     * @param useSsl whether to use SSL/TLS
     */
    public JedisPooled(String host, int port, boolean useSsl) {
        this(host, port, DefaultJedisClientConfig.builder().ssl(useSsl).build());
    }

    /**
     * Create a new JedisPooled with full configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param config the client configuration
     */
    public JedisPooled(String host, int port, JedisClientConfig config) {
        super(createGlideConfig(host, port, config), config);
    }

    /**
     * Create JedisPooled with SSL configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param ssl whether to use SSL
     * @param sslSocketFactory SSL socket factory
     * @param sslParameters SSL parameters
     * @param hostnameVerifier hostname verifier
     */
    public JedisPooled(
            String host,
            int port,
            boolean ssl,
            SSLSocketFactory sslSocketFactory,
            SSLParameters sslParameters,
            HostnameVerifier hostnameVerifier) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .ssl(ssl)
                        .sslSocketFactory(sslSocketFactory)
                        .sslParameters(sslParameters)
                        .hostnameVerifier(hostnameVerifier)
                        .build());
    }

    /**
     * Create JedisPooled with timeout configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param timeout the timeout in milliseconds
     */
    public JedisPooled(String host, int port, int timeout) {
        this(
                host,
                port,
                DefaultJedisClientConfig.builder()
                        .socketTimeoutMillis(timeout)
                        .connectionTimeoutMillis(timeout)
                        .build());
    }

    /**
     * Create JedisPooled with authentication.
     *
     * @param host the server host
     * @param port the server port
     * @param user the username
     * @param password the password
     */
    public JedisPooled(String host, int port, String user, String password) {
        this(host, port, DefaultJedisClientConfig.builder().user(user).password(password).build());
    }

    /**
     * Create JedisPooled with password authentication.
     *
     * @param host the server host
     * @param port the server port
     * @param password the password
     */
    public JedisPooled(String host, int port, String password) {
        this(host, port, DefaultJedisClientConfig.builder().password(password).build());
    }

    /**
     * Create GLIDE configuration from Jedis parameters.
     *
     * @param host the server host
     * @param port the server port
     * @param config the Jedis configuration
     * @return GLIDE client configuration
     */
    private static GlideClientConfiguration createGlideConfig(String host, int port, JedisClientConfig config) {
        // Validate configuration
        ConfigurationMapper.validateConfiguration(config);
        
        // Map Jedis config to GLIDE config
        return ConfigurationMapper.mapToGlideConfig(host, port, config);
    }
}
