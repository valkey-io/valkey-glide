/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import java.util.Set;
import java.util.logging.Logger;
import javax.net.ssl.SSLParameters;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Enhanced utility class to map Jedis cluster configurations to Valkey GLIDE cluster
 * configurations. Provides comprehensive validation and SSL/TLS support with automatic certificate
 * conversion.
 */
public class ClusterConfigurationMapper {

    private static final Logger logger = Logger.getLogger(ClusterConfigurationMapper.class.getName());
    private static final String JEDIS_ADAPTER_LIB_NAME = "GlideJedisAdapter";

    /**
     * Convert Jedis cluster configuration to GLIDE cluster configuration with comprehensive
     * validation and SSL/TLS support.
     *
     * @param nodes the cluster nodes
     * @param jedisConfig the Jedis configuration
     * @return the GLIDE cluster configuration
     * @throws JedisException if configuration is invalid for cluster mode
     */
    public static GlideClusterClientConfiguration mapToGlideClusterConfig(
            Set<HostAndPort> nodes, JedisClientConfig jedisConfig) {

        // Check for unsupported features early
        // This feature will be added later:
        // https://github.com/valkey-io/valkey-glide/wiki/General-Concepts#best-practices-for-refreshing-iam-tokens-and-re-authentication
        if (jedisConfig.getAuthXManager() != null) {
            throw new JedisException(
                    "AuthXManager is not supported in GLIDE cluster mode. Please use username/password"
                            + " authentication.");
        }

        // Validate database selection - clusters only support database 0
        if (jedisConfig.getDatabase() != Protocol.DEFAULT_DATABASE) {
            throw new JedisException(
                    "Cluster mode only supports database 0, but database "
                            + jedisConfig.getDatabase()
                            + " was specified. Please remove database selection for cluster mode.");
        }

        // Validate credentials provider - only reject custom providers
        if (jedisConfig.getCredentialsProvider() != null
                && !(jedisConfig.getCredentialsProvider() instanceof DefaultRedisCredentialsProvider)) {
            throw new JedisException(
                    "Custom credentials provider is not supported in GLIDE cluster mode. "
                            + "Please extract credentials manually and use username/password configuration.");
        }

        // Handle blocking socket timeout - warn about architectural difference
        if (jedisConfig.getBlockingSocketTimeoutMillis() > 0) {
            logger.warning(
                    "Blocking socket timeout specified ("
                            + jedisConfig.getBlockingSocketTimeoutMillis()
                            + "ms). "
                            + "GLIDE uses a unified request timeout for all operations in cluster mode. "
                            + "Blocking commands will use the same timeout as non-blocking commands.");
        }

        GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder builder =
                GlideClusterClientConfiguration.builder();

        // Map cluster nodes
        mapClusterNodes(nodes, builder);

        // Map basic connection settings
        mapConnectionSettings(jedisConfig, builder);

        // Map authentication and SSL/TLS settings
        mapCredentialsAndSsl(jedisConfig, builder);

        // Map advanced settings
        mapAdvancedSettings(jedisConfig, builder);

        // Set libName for Jedis compatibility layer
        builder.libName(JEDIS_ADAPTER_LIB_NAME);

        return builder.build();
    }

    /**
     * Convert Jedis cluster configuration to GLIDE cluster configuration with default settings.
     *
     * @param nodes the cluster nodes
     * @return the GLIDE cluster configuration with default Jedis client config
     */
    public static GlideClusterClientConfiguration mapToGlideClusterConfig(Set<HostAndPort> nodes) {
        return mapToGlideClusterConfig(nodes, DefaultJedisClientConfig.builder().build());
    }

    /** Maps cluster nodes to GLIDE configuration. */
    private static void mapClusterNodes(
            Set<HostAndPort> nodes,
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder builder) {

        if (nodes == null || nodes.isEmpty()) {
            throw new JedisException("Cluster nodes cannot be null or empty");
        }

        // Add all cluster nodes
        for (HostAndPort node : nodes) {
            if (node.getHost() == null || node.getHost().trim().isEmpty()) {
                throw new JedisException("Cluster node host cannot be null or empty");
            }
            if (node.getPort() <= 0 || node.getPort() > 65535) {
                throw new JedisException(
                        "Cluster node port must be between 1 and 65535, got: " + node.getPort());
            }

            builder.address(NodeAddress.builder().host(node.getHost()).port(node.getPort()).build());
        }

        logger.info("Mapped " + nodes.size() + " cluster nodes to GLIDE configuration");
    }

    /** Maps basic connection settings from Jedis to GLIDE cluster configuration. */
    private static void mapConnectionSettings(
            JedisClientConfig jedisConfig,
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder builder) {

        // Client name
        if (jedisConfig.getClientName() != null) {
            builder.clientName(jedisConfig.getClientName());
        }

        // Timeout mapping - GLIDE uses single timeout concept
        int timeout = calculateGlideRequestTimeout(jedisConfig);
        if (timeout > 0) {
            builder.requestTimeout(timeout);
        }

        // Protocol version
        if (jedisConfig.getRedisProtocol() != null) {
            builder.protocol(jedisConfig.getRedisProtocol().toGlideProtocol());
        } else {
            // Ensure Jedis default behavior (RESP2) is maintained
            builder.protocol(ProtocolVersion.RESP2);
        }
    }

    /** Maps authentication and SSL/TLS settings with comprehensive certificate conversion. */
    private static void mapCredentialsAndSsl(
            JedisClientConfig jedisConfig,
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder builder) {

        ServerCredentials.ServerCredentialsBuilder credentialsBuilder = null;

        // Handle authentication
        if (jedisConfig.getUser() != null || jedisConfig.getPassword() != null) {
            credentialsBuilder = ServerCredentials.builder();

            if (jedisConfig.getUser() != null) {
                credentialsBuilder.username(jedisConfig.getUser());
            }
            if (jedisConfig.getPassword() != null) {
                credentialsBuilder.password(jedisConfig.getPassword());
            }
        }

        // Handle SSL/TLS
        if (jedisConfig.isSsl()) {
            builder.useTLS(true);

            if (credentialsBuilder == null) {
                credentialsBuilder = ServerCredentials.builder();
            }

            mapSslConfiguration(jedisConfig, credentialsBuilder, builder);
        }

        // Set credentials if any were configured
        if (credentialsBuilder != null) {
            builder.credentials(credentialsBuilder.build());
        }
    }

    /**
     * Comprehensive SSL/TLS configuration mapping with structured validation and clear logging. Maps
     * Jedis SSL configuration to GLIDE TLS configuration with appropriate fallbacks.
     */
    private static void mapSslConfiguration(
            JedisClientConfig jedisConfig,
            ServerCredentials.ServerCredentialsBuilder credentialsBuilder,
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder glideBuilder) {

        logger.info("Mapping SSL/TLS configuration from Jedis to GLIDE cluster mode");

        AdvancedGlideClusterClientConfiguration.AdvancedGlideClusterClientConfigurationBuilder
                advancedBuilder = AdvancedGlideClusterClientConfiguration.builder();
        boolean needsAdvancedConfig = false;

        if (jedisConfig.getSslOptions() != null) {
            needsAdvancedConfig = processSslOptions(jedisConfig.getSslOptions(), advancedBuilder);
        } else if (jedisConfig.getSslSocketFactory() != null) {
            throw new JedisException(
                    "Custom SSLSocketFactory is not supported in GLIDE cluster mode. Please use system"
                            + " certificate store or SslOptions with SslVerifyMode.INSECURE for testing.");
        } else if (jedisConfig.getHostnameVerifier() != null) {
            throw new JedisException(
                    "Custom HostnameVerifier is not supported in GLIDE cluster mode. Please use system"
                            + " hostname verification or SslOptions with SslVerifyMode.INSECURE for testing.");
        }

        if (jedisConfig.getSslParameters() != null) {
            boolean sslParamsNeedAdvanced =
                    processSslParameters(jedisConfig.getSslParameters(), advancedBuilder);
            needsAdvancedConfig = needsAdvancedConfig || sslParamsNeedAdvanced;
        }

        // Apply advanced configuration if needed
        if (needsAdvancedConfig) {
            glideBuilder.advancedConfiguration(advancedBuilder.build());
            logger.info("Applied advanced TLS configuration to GLIDE cluster client");
        } else {
            logger.info("Using default secure TLS configuration for cluster mode");
        }
    }

    /** Processes SslOptions configuration and returns true if advanced configuration is needed. */
    private static boolean processSslOptions(
            SslOptions sslOptions,
            AdvancedGlideClusterClientConfiguration.AdvancedGlideClusterClientConfigurationBuilder
                    advancedBuilder) {

        // Check for certificate resources - not supported, should fail
        if (sslOptions.getKeystoreResource() != null) {
            throw new JedisException(
                    "Keystore configuration is not supported in GLIDE cluster mode. Please use system"
                            + " certificate store or SslOptions with SslVerifyMode.INSECURE for testing.");
        }

        if (sslOptions.getTruststoreResource() != null) {
            throw new JedisException(
                    "Truststore configuration is not supported in GLIDE cluster mode. Please use system"
                            + " certificate store or SslOptions with SslVerifyMode.INSECURE for testing.");
        }

        boolean needsAdvancedConfig = false;

        // Process SSL parameters if present
        if (sslOptions.getSslParameters() != null) {
            boolean sslParamsNeedAdvanced =
                    processSslParameters(sslOptions.getSslParameters(), advancedBuilder);
            needsAdvancedConfig = needsAdvancedConfig || sslParamsNeedAdvanced;
        }

        // Handle SSL verify mode
        SslVerifyMode verifyMode = sslOptions.getSslVerifyMode();
        if (verifyMode == SslVerifyMode.INSECURE) {
            logger.warning(
                    "SSL Configuration: SSL verification disabled via SslVerifyMode.INSECURE - using insecure"
                            + " TLS in cluster mode");
            advancedBuilder.tlsAdvancedConfiguration(
                    TlsAdvancedConfiguration.builder().useInsecureTLS(true).build());
            return true;
        } else {
            logger.info(
                    "SSL Configuration: SSL verification enabled via SslVerifyMode."
                            + verifyMode
                            + " - using secure TLS in cluster mode");
            return needsAdvancedConfig;
        }
    }

    /** Processes SSLParameters configuration and returns true if advanced configuration is needed. */
    private static boolean processSslParameters(
            SSLParameters sslParameters,
            AdvancedGlideClusterClientConfiguration.AdvancedGlideClusterClientConfigurationBuilder
                    advancedBuilder) {

        // Check cipher suites - not supported, should fail
        if (sslParameters.getCipherSuites() != null && sslParameters.getCipherSuites().length > 0) {
            throw new JedisException(
                    "Custom cipher suites are not supported in GLIDE cluster mode. GLIDE automatically"
                            + " selects secure cipher suites. Please remove custom cipher suite configuration.");
        }

        // Check protocols - not supported, should fail
        if (sslParameters.getProtocols() != null && sslParameters.getProtocols().length > 0) {
            throw new JedisException(
                    "Custom SSL protocols are not supported in GLIDE cluster mode. GLIDE automatically"
                            + " selects the best available TLS protocol. Please remove custom protocol"
                            + " configuration.");
        }

        // Check client authentication - not supported, should fail
        if (sslParameters.getNeedClientAuth()) {
            throw new JedisException(
                    "Client authentication (needClientAuth) is not supported in GLIDE cluster mode. Please"
                            + " remove client authentication configuration or use server-side authentication.");
        } else if (sslParameters.getWantClientAuth()) {
            throw new JedisException(
                    "Client authentication (wantClientAuth) is not supported in GLIDE cluster mode. Please"
                            + " remove client authentication configuration or use server-side authentication.");
        } else {
            logger.info(
                    "SSL Configuration: Client authentication disabled - compatible with GLIDE cluster mode");
        }

        // Check endpoint identification
        String endpointAlgorithm = sslParameters.getEndpointIdentificationAlgorithm();
        if (endpointAlgorithm != null && endpointAlgorithm.isEmpty()) {
            throw new JedisException(
                    "Disabled endpoint identification is not supported in GLIDE cluster mode. "
                            + "GLIDE enforces hostname verification for security. "
                            + "Use SslOptions with SslVerifyMode.INSECURE if you need to bypass verification.");
        } else if (endpointAlgorithm != null) {
            logger.info(
                    "SSL Configuration: Endpoint identification algorithm: "
                            + endpointAlgorithm
                            + " - compatible with GLIDE cluster mode");
        }

        return false;
    }

    /** Maps advanced settings from Jedis to GLIDE cluster configuration. */
    private static void mapAdvancedSettings(
            JedisClientConfig jedisConfig,
            GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder builder) {

        AdvancedGlideClusterClientConfiguration.AdvancedGlideClusterClientConfigurationBuilder
                advancedBuilder = AdvancedGlideClusterClientConfiguration.builder();

        boolean hasAdvancedConfig = false;

        // Connection timeout - always map if specified
        if (jedisConfig.getConnectionTimeoutMillis() > 0) {
            advancedBuilder.connectionTimeout(jedisConfig.getConnectionTimeoutMillis());
            hasAdvancedConfig = true;
        }

        // Add advanced configuration if any advanced settings were configured
        if (hasAdvancedConfig) {
            builder.advancedConfiguration(advancedBuilder.build());
        }
    }

    /** Calculates appropriate GLIDE request timeout from Jedis socket timeout settings. */
    private static int calculateGlideRequestTimeout(JedisClientConfig jedisConfig) {
        // Note: connectionTimeout is handled separately in mapAdvancedSettings()
        // This method only handles the requestTimeout mapping

        // Use socket timeout as the base for request timeout
        // Socket timeout in Jedis represents the time to wait for command responses
        return jedisConfig.getSocketTimeoutMillis();
    }

    /**
     * Create a default GLIDE cluster configuration for simple cluster connections.
     *
     * @param nodes the cluster nodes
     * @param useSsl whether to use SSL
     * @return GLIDE cluster configuration
     */
    public static GlideClusterClientConfiguration createDefaultConfig(
            Set<HostAndPort> nodes, boolean useSsl) {

        if (nodes == null || nodes.isEmpty()) {
            throw new JedisException("Cluster nodes cannot be null or empty");
        }

        // Create a simple config with SSL setting
        JedisClientConfig config =
                DefaultJedisClientConfig.builder()
                        .ssl(useSsl)
                        .socketTimeoutMillis(DefaultJedisClientConfig.DEFAULT_TIMEOUT_MILLIS)
                        .build();

        return mapToGlideClusterConfig(nodes, config);
    }

    /**
     * Create a cluster connection provider for UnifiedJedis cluster mode.
     *
     * @param nodes the cluster nodes
     * @param clientConfig the client configuration
     * @param poolConfig the pool configuration (ignored in GLIDE)
     * @return a cluster connection provider
     */
    public static ClusterConnectionProvider createClusterConnectionProvider(
            Set<HostAndPort> nodes, JedisClientConfig clientConfig, Object poolConfig) {
        return new ClusterConnectionProvider(nodes, clientConfig);
    }

    // ===== SUPPORTING CLASSES =====

    /** Custom exception for cluster configuration conversion issues. */
    public static class JedisClusterConfigurationException extends JedisException {
        public JedisClusterConfigurationException(String message) {
            super(message);
        }

        public JedisClusterConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
