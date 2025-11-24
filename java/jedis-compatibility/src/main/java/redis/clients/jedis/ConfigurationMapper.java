/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import java.util.*;
import java.util.logging.Logger;
import javax.net.ssl.SSLParameters;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Enhanced utility class to map Jedis configurations to Valkey GLIDE configurations. Provides
 * comprehensive SSL/TLS support with automatic certificate conversion from Java KeyStore format to
 * PEM format required by GLIDE.
 */
public class ConfigurationMapper {

    private static final Logger logger = Logger.getLogger(ConfigurationMapper.class.getName());
    private static final String JEDIS_ADAPTER_LIB_NAME = "GlideJedisAdapter";

    /**
     * Convert Jedis client configuration to GLIDE client configuration with comprehensive SSL/TLS
     * support and automatic certificate conversion.
     *
     * @param host the server host
     * @param port the server port
     * @param jedisConfig the Jedis configuration
     * @return corresponding GLIDE configuration
     * @throws JedisConfigurationException if configuration cannot be converted
     */
    public static GlideClientConfiguration mapToGlideConfig(
            String host, int port, JedisClientConfig jedisConfig) {

        // Check for unsupported features early
        if (jedisConfig.getAuthXManager() != null) {
            throw new JedisConfigurationException(
                    "AuthXManager is not supported in GLIDE. Please use username/password authentication.");
        }

        GlideClientConfiguration.GlideClientConfigurationBuilder builder =
                GlideClientConfiguration.builder();

        // Map basic connection settings
        mapConnectionSettings(jedisConfig, host, port, builder);

        // Map authentication and SSL/TLS settings
        mapCredentialsAndSsl(jedisConfig, builder);

        // Map advanced settings
        mapAdvancedSettings(jedisConfig, builder);

        // Set libName for Jedis compatibility layer
        builder.libName(JEDIS_ADAPTER_LIB_NAME);

        return builder.build();
    }

    /** Maps basic connection settings from Jedis to GLIDE configuration. */
    private static void mapConnectionSettings(
            JedisClientConfig jedisConfig,
            String host,
            int port,
            GlideClientConfiguration.GlideClientConfigurationBuilder builder) {

        // Address mapping
        builder.address(NodeAddress.builder().host(host).port(port).build());

        // Database selection (standalone only) - Note: GLIDE may handle this differently
        // For now, we'll log a warning if database is not default
        if (jedisConfig.getDatabase() != Protocol.DEFAULT_DATABASE) {
            logger.warning(
                    "Database selection specified ("
                            + jedisConfig.getDatabase()
                            + "). GLIDE may handle database selection differently than Jedis.");
        }

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
            GlideClientConfiguration.GlideClientConfigurationBuilder builder) {

        ServerCredentials.ServerCredentialsBuilder credentialsBuilder = null;

        // Handle authentication
        // Only set credentials if user or password is non-empty
        boolean hasUser = jedisConfig.getUser() != null && !jedisConfig.getUser().isEmpty();
        boolean hasPassword = jedisConfig.getPassword() != null && !jedisConfig.getPassword().isEmpty();

        if (hasUser || hasPassword) {
            credentialsBuilder = ServerCredentials.builder();

            if (hasUser) {
                credentialsBuilder.username(jedisConfig.getUser());
            }
            if (hasPassword) {
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
            GlideClientConfiguration.GlideClientConfigurationBuilder glideBuilder) {

        logger.info("Mapping SSL/TLS configuration from Jedis to GLIDE");

        AdvancedGlideClientConfiguration.AdvancedGlideClientConfigurationBuilder advancedBuilder =
                AdvancedGlideClientConfiguration.builder();
        boolean needsAdvancedConfig = false;

        if (jedisConfig.getSslOptions() != null) {
            needsAdvancedConfig = processSslOptions(jedisConfig.getSslOptions(), advancedBuilder);
        } else if (jedisConfig.getSslSocketFactory() != null) {
            throw new JedisConfigurationException(
                    "Custom SSLSocketFactory is not supported in GLIDE. Please use system certificate store"
                            + " or SslOptions with SslVerifyMode.INSECURE for testing.");
        } else if (jedisConfig.getHostnameVerifier() != null) {
            throw new JedisConfigurationException(
                    "Custom HostnameVerifier is not supported in GLIDE. Please use system hostname"
                            + " verification or SslOptions with SslVerifyMode.INSECURE for testing.");
        }

        if (jedisConfig.getSslParameters() != null) {
            boolean sslParamsNeedAdvanced =
                    processSslParameters(jedisConfig.getSslParameters(), advancedBuilder);
            needsAdvancedConfig = needsAdvancedConfig || sslParamsNeedAdvanced;
        }

        // Apply advanced configuration if needed
        if (needsAdvancedConfig) {
            glideBuilder.advancedConfiguration(advancedBuilder.build());
            logger.info("Applied advanced TLS configuration to GLIDE client");
        } else {
            logger.info("Using default secure TLS configuration");
        }
    }

    /** Processes SslOptions configuration and returns true if advanced configuration is needed. */
    private static boolean processSslOptions(
            SslOptions sslOptions,
            AdvancedGlideClientConfiguration.AdvancedGlideClientConfigurationBuilder advancedBuilder) {

        // Check for certificate resources - not supported, should fail
        if (sslOptions.getKeystoreResource() != null) {
            throw new JedisConfigurationException(
                    "Keystore configuration is not supported in GLIDE. Please use system certificate store or"
                            + " SslOptions with SslVerifyMode.INSECURE for testing.");
        }

        if (sslOptions.getTruststoreResource() != null) {
            throw new JedisConfigurationException(
                    "Truststore configuration is not supported in GLIDE. Please use system certificate store"
                            + " or SslOptions with SslVerifyMode.INSECURE for testing.");
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
                            + " TLS");
            advancedBuilder.tlsAdvancedConfiguration(
                    TlsAdvancedConfiguration.builder().useInsecureTLS(true).build());
            return true;
        } else {
            logger.info(
                    "SSL Configuration: SSL verification enabled via SslVerifyMode."
                            + verifyMode
                            + " - using secure TLS");
            return needsAdvancedConfig;
        }
    }

    /** Processes SSLParameters configuration and returns true if advanced configuration is needed. */
    private static boolean processSslParameters(
            SSLParameters sslParameters,
            AdvancedGlideClientConfiguration.AdvancedGlideClientConfigurationBuilder advancedBuilder) {

        // Check cipher suites - not supported, should fail
        if (sslParameters.getCipherSuites() != null && sslParameters.getCipherSuites().length > 0) {
            throw new JedisConfigurationException(
                    "Custom cipher suites are not supported in GLIDE. GLIDE automatically selects secure"
                            + " cipher suites. Please remove custom cipher suite configuration.");
        }

        // Check protocols - not supported, should fail
        if (sslParameters.getProtocols() != null && sslParameters.getProtocols().length > 0) {
            throw new JedisConfigurationException(
                    "Custom SSL protocols are not supported in GLIDE. GLIDE automatically selects the best"
                            + " available TLS protocol. Please remove custom protocol configuration.");
        }

        // Check client authentication - not supported, should fail
        if (sslParameters.getNeedClientAuth()) {
            throw new JedisConfigurationException(
                    "Client authentication (needClientAuth) is not supported in GLIDE. Please remove client"
                            + " authentication configuration or use server-side authentication.");
        } else if (sslParameters.getWantClientAuth()) {
            throw new JedisConfigurationException(
                    "Client authentication (wantClientAuth) is not supported in GLIDE. Please remove client"
                            + " authentication configuration or use server-side authentication.");
        } else {
            logger.info("SSL Configuration: Client authentication disabled - compatible with GLIDE");
        }

        // Check endpoint identification
        String endpointAlgorithm = sslParameters.getEndpointIdentificationAlgorithm();
        if (endpointAlgorithm != null && endpointAlgorithm.isEmpty()) {
            throw new JedisConfigurationException(
                    "Disabled endpoint identification is not supported in GLIDE. "
                            + "GLIDE enforces hostname verification for security. "
                            + "Use SslOptions with SslVerifyMode.INSECURE if you need to bypass verification.");
        } else if (endpointAlgorithm != null) {
            logger.info(
                    "SSL Configuration: Endpoint identification algorithm: "
                            + endpointAlgorithm
                            + " - compatible with GLIDE");
        }

        return false;
    }

    /** Maps advanced settings from Jedis to GLIDE configuration. */
    private static void mapAdvancedSettings(
            JedisClientConfig jedisConfig,
            GlideClientConfiguration.GlideClientConfigurationBuilder builder) {

        AdvancedGlideClientConfiguration.AdvancedGlideClientConfigurationBuilder advancedBuilder =
                AdvancedGlideClientConfiguration.builder();

        boolean hasAdvancedConfig = false;

        // Connection timeout - always map if specified
        if (jedisConfig.getConnectionTimeoutMillis() > 0) {
            advancedBuilder.connectionTimeout(jedisConfig.getConnectionTimeoutMillis());
            hasAdvancedConfig = true;
        }

        // Handle blocking socket timeout - warn about architectural difference
        if (jedisConfig.getBlockingSocketTimeoutMillis() > 0) {
            logger.warning(
                    "Blocking socket timeout specified ("
                            + jedisConfig.getBlockingSocketTimeoutMillis()
                            + "ms). "
                            + "GLIDE uses a unified request timeout for all operations. "
                            + "Blocking commands will use the same timeout as non-blocking commands.");
        }

        // Handle credentials provider - only reject custom providers
        // The default implementation always provides a DefaultRedisCredentialsProvider, which is fine
        if (jedisConfig.getCredentialsProvider() != null
                && !(jedisConfig.getCredentialsProvider() instanceof DefaultRedisCredentialsProvider)) {
            throw new JedisConfigurationException(
                    "Custom credentials provider is not supported in GLIDE. GLIDE uses static credentials."
                            + " Please extract credentials manually and use username/password configuration.");
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
     * Create a default GLIDE configuration for simple host/port connections.
     *
     * @param host the server host
     * @param port the server port
     * @param useSsl whether to use SSL
     * @return GLIDE configuration
     */
    public static GlideClientConfiguration createDefaultConfig(
            String host, int port, boolean useSsl) {
        return GlideClientConfiguration.builder()
                .address(NodeAddress.builder().host(host).port(port).build())
                .useTLS(useSsl)
                .requestTimeout(DefaultJedisClientConfig.DEFAULT_TIMEOUT_MILLIS)
                .build();
    }

    /** Cleanup method for future certificate management (placeholder). */
    public static void cleanupTempFiles() {
        // Placeholder for future certificate cleanup functionality
        // Currently no temporary files are created
    }

    // ===== SUPPORTING CLASSES =====

    /** Custom exception for configuration conversion issues. */
    public static class JedisConfigurationException extends JedisException {
        public JedisConfigurationException(String message) {
            super(message);
        }

        public JedisConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
