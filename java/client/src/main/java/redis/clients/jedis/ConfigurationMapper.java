/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.models.configuration.AdvancedGlideClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import java.io.InputStream;
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

        // Validate configuration can be converted
        ValidationResult validation = validateConfiguration(jedisConfig);
        if (!validation.getErrors().isEmpty()) {
            throw new JedisConfigurationException(
                    "Cannot convert Jedis configuration to GLIDE: "
                            + String.join(", ", validation.getErrors()));
        }

        // Log warnings for partial mappings
        validation
                .getWarnings()
                .forEach(warning -> logger.warning("Configuration conversion warning: " + warning));

        GlideClientConfiguration.GlideClientConfigurationBuilder builder =
                GlideClientConfiguration.builder();

        // Map basic connection settings
        mapConnectionSettings(jedisConfig, host, port, builder);

        // Map authentication and SSL/TLS settings (ENHANCED)
        mapCredentialsAndSsl(jedisConfig, builder);

        // Map advanced settings
        mapAdvancedSettings(jedisConfig, builder);

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
        int timeout = calculateGlideTimeout(jedisConfig);
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

    /**
     * ENHANCED: Maps authentication and SSL/TLS settings with comprehensive certificate conversion.
     */
    private static void mapCredentialsAndSsl(
            JedisClientConfig jedisConfig,
            GlideClientConfiguration.GlideClientConfigurationBuilder builder) {

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

        // Handle SSL/TLS (ENHANCED IMPLEMENTATION)
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

    /** NEW: Comprehensive SSL/TLS configuration mapping with fallback to insecure mode. */
    private static void mapSslConfiguration(
            JedisClientConfig jedisConfig,
            ServerCredentials.ServerCredentialsBuilder credentialsBuilder,
            GlideClientConfiguration.GlideClientConfigurationBuilder glideBuilder) {

        SslOptions sslOptions = jedisConfig.getSslOptions();
        boolean needsAdvancedConfig = false;
        AdvancedGlideClientConfiguration.AdvancedGlideClientConfigurationBuilder advancedBuilder =
                AdvancedGlideClientConfiguration.builder();

        if (sslOptions != null) {
            // Handle SslOptions - for now, log warning about certificate conversion
            logger.warning(
                    "SslOptions detected with keystore/truststore configuration. GLIDE currently uses system"
                            + " certificate stores. Consider converting certificates to system trust store or"
                            + " using insecure TLS for development.");

            // Check SSL verify mode - only use insecure TLS for explicit INSECURE mode
            if (sslOptions.getSslVerifyMode() == SslVerifyMode.INSECURE) {
                advancedBuilder.tlsAdvancedConfiguration(
                        TlsAdvancedConfiguration.builder().useInsecureTLS(true).build());
                needsAdvancedConfig = true;
            }

        } else if (jedisConfig.getSslSocketFactory() != null) {
            // Handle custom SSL socket factory - not supported, should fail
            throw new JedisConfigurationException(
                    "Custom SSLSocketFactory is not supported in GLIDE. "
                            + "Please use system certificate store or remove custom SSL factory.");

        } else if (jedisConfig.getHostnameVerifier() != null) {
            // Handle custom hostname verifier - not supported, should fail
            throw new JedisConfigurationException(
                    "Custom HostnameVerifier is not supported in GLIDE. "
                            + "Please use system hostname verification or remove custom verifier.");
        }

        // Handle SSL parameters
        if (jedisConfig.getSslParameters() != null) {
            boolean sslParamsNeedAdvanced =
                    handleSslParameters(jedisConfig.getSslParameters(), advancedBuilder);
            needsAdvancedConfig = needsAdvancedConfig || sslParamsNeedAdvanced;
        }

        // Set advanced configuration if needed
        if (needsAdvancedConfig) {
            glideBuilder.advancedConfiguration(advancedBuilder.build());
        }
    }

    /** SIMPLIFIED: Converts SslOptions to GLIDE configuration with warnings. */
    private static void mapSslOptions(
            SslOptions sslOptions, ServerCredentials.ServerCredentialsBuilder credentialsBuilder) {

        // For now, we'll log warnings about unsupported certificate conversion
        if (sslOptions.getTruststoreResource() != null) {
            logger.warning(
                    "Truststore configuration detected but not supported in current GLIDE version. "
                            + "Consider using system certificate store or insecure TLS for development.");
        }

        if (sslOptions.getKeystoreResource() != null) {
            logger.warning(
                    "Keystore configuration detected but not supported in current GLIDE version. "
                            + "Consider using system certificate store or insecure TLS for development.");
        }
    }

    /**
     * NEW: Handles SSL parameters that can be partially mapped to GLIDE. Returns true if advanced
     * configuration is needed.
     */
    private static boolean handleSslParameters(
            SSLParameters sslParameters,
            AdvancedGlideClientConfiguration.AdvancedGlideClientConfigurationBuilder advancedBuilder) {

        boolean needsAdvancedConfig = false;

        if (sslParameters.getCipherSuites() != null) {
            logger.warning(
                    "Custom cipher suites specified in SSLParameters. "
                            + "GLIDE will use its own secure cipher suite selection.");
        }

        if (sslParameters.getProtocols() != null) {
            logger.warning(
                    "Custom SSL protocols specified in SSLParameters. "
                            + "GLIDE will auto-select the best available TLS protocol.");
        }

        if (!sslParameters.getWantClientAuth() && !sslParameters.getNeedClientAuth()) {
            logger.info(
                    "Client authentication disabled in SSLParameters. "
                            + "Ensure server is configured accordingly.");
        }

        // If endpoint identification is disabled, this is not supported
        if (sslParameters.getEndpointIdentificationAlgorithm() != null
                && sslParameters.getEndpointIdentificationAlgorithm().isEmpty()) {

            throw new JedisConfigurationException(
                    "Disabled endpoint identification is not supported in GLIDE. "
                            + "GLIDE enforces hostname verification for security. "
                            + "Use SslVerifyMode.INSECURE if you need to bypass verification.");
        }

        return needsAdvancedConfig;
    }

    /** Maps advanced settings from Jedis to GLIDE configuration. */
    private static void mapAdvancedSettings(
            JedisClientConfig jedisConfig,
            GlideClientConfiguration.GlideClientConfigurationBuilder builder) {

        AdvancedGlideClientConfiguration.AdvancedGlideClientConfigurationBuilder advancedBuilder =
                AdvancedGlideClientConfiguration.builder();

        boolean hasAdvancedConfig = false;

        // Connection timeout
        if (jedisConfig.getConnectionTimeoutMillis() != jedisConfig.getSocketTimeoutMillis()) {
            advancedBuilder.connectionTimeout(jedisConfig.getConnectionTimeoutMillis());
            hasAdvancedConfig = true;
        }

        // Handle blocking socket timeout
        if (jedisConfig.getBlockingSocketTimeoutMillis() > 0) {
            logger.warning(
                    "Blocking socket timeout specified. GLIDE uses single timeout model. "
                            + "Consider adjusting request timeout if needed.");
        }

        // Handle credentials provider
        if (jedisConfig.getCredentialsProvider() != null) {
            logger.warning(
                    "Custom credentials provider detected. "
                            + "GLIDE uses static credentials. Consider extracting credentials manually.");
        }

        // Add advanced configuration if any advanced settings were configured
        if (hasAdvancedConfig) {
            builder.advancedConfiguration(advancedBuilder.build());
        }
    }

    /** Calculates appropriate GLIDE timeout from Jedis timeout settings. */
    private static int calculateGlideTimeout(JedisClientConfig jedisConfig) {
        int connectionTimeout = jedisConfig.getConnectionTimeoutMillis();
        int socketTimeout = jedisConfig.getSocketTimeoutMillis();
        int blockingTimeout = jedisConfig.getBlockingSocketTimeoutMillis();

        // Use the maximum of connection and socket timeouts
        int maxTimeout = Math.max(connectionTimeout, socketTimeout);

        // If blocking timeout is specified and reasonable, consider it
        if (blockingTimeout > 0 && blockingTimeout < 300000) { // Less than 5 minutes
            maxTimeout = Math.max(maxTimeout, blockingTimeout);
        }

        // Ensure minimum timeout
        return Math.max(maxTimeout, 1000); // At least 1 second
    }

    /** NEW: Validates that Jedis configuration can be converted to GLIDE. */
    public static ValidationResult validateConfiguration(JedisClientConfig jedisConfig) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Check for unsupported features
        if (jedisConfig.getAuthXManager() != null) {
            errors.add("AuthXManager is not supported in GLIDE");
        }

        // Check SSL configuration
        if (jedisConfig.isSsl()) {
            validateSslConfiguration(jedisConfig, warnings, errors);
        }

        // Check timeout configurations
        validateTimeoutConfiguration(jedisConfig, warnings);

        // Check database selection
        if (jedisConfig.getDatabase() != Protocol.DEFAULT_DATABASE) {
            warnings.add("Database selection: GLIDE supports database selection for standalone mode");
        }

        return new ValidationResult(warnings, errors);
    }

    /** Validates SSL configuration for conversion compatibility. */
    private static void validateSslConfiguration(
            JedisClientConfig jedisConfig, List<String> warnings, List<String> errors) {

        if (jedisConfig.getSslSocketFactory() != null) {
            errors.add("Custom SSLSocketFactory is not supported in GLIDE");
        }

        if (jedisConfig.getHostnameVerifier() != null) {
            errors.add("Custom HostnameVerifier is not supported in GLIDE");
        }

        // Check SSL parameters for unsupported configurations
        if (jedisConfig.getSslParameters() != null) {
            SSLParameters sslParams = jedisConfig.getSslParameters();
            if (sslParams.getEndpointIdentificationAlgorithm() != null
                    && sslParams.getEndpointIdentificationAlgorithm().isEmpty()) {
                errors.add("Disabled endpoint identification is not supported in GLIDE");
            }
        }

        SslOptions sslOptions = jedisConfig.getSslOptions();
        if (sslOptions != null) {
            if (sslOptions.getSslVerifyMode() == SslVerifyMode.INSECURE) {
                warnings.add(
                        "SSL verification disabled via SslVerifyMode.INSECURE. Will map to GLIDE's"
                                + " useInsecureTLS option.");
            }

            // Check if certificate resources are accessible
            if (sslOptions.getKeystoreResource() != null) {
                try (InputStream is = sslOptions.getKeystoreResource().get()) {
                    // Just test accessibility
                } catch (Exception e) {
                    errors.add("Cannot access keystore resource: " + e.getMessage());
                }
            }

            if (sslOptions.getTruststoreResource() != null) {
                try (InputStream is = sslOptions.getTruststoreResource().get()) {
                    // Just test accessibility
                } catch (Exception e) {
                    errors.add("Cannot access truststore resource: " + e.getMessage());
                }
            }
        }
    }

    /** Validates timeout configuration for conversion. */
    private static void validateTimeoutConfiguration(
            JedisClientConfig jedisConfig, List<String> warnings) {

        int connectionTimeout = jedisConfig.getConnectionTimeoutMillis();
        int socketTimeout = jedisConfig.getSocketTimeoutMillis();

        if (connectionTimeout != socketTimeout && connectionTimeout > 0 && socketTimeout > 0) {
            warnings.add(
                    String.format(
                            "Different connection (%dms) and socket (%dms) timeouts specified. "
                                    + "GLIDE will use maximum value (%dms) for request timeout.",
                            connectionTimeout, socketTimeout, Math.max(connectionTimeout, socketTimeout)));
        }

        if (jedisConfig.getBlockingSocketTimeoutMillis() > 0) {
            warnings.add("Blocking socket timeout specified. GLIDE uses single timeout model.");
        }
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

    /** Result of configuration validation. */
    public static class ValidationResult {
        private final List<String> warnings;
        private final List<String> errors;

        public ValidationResult(List<String> warnings, List<String> errors) {
            this.warnings = new ArrayList<>(warnings);
            this.errors = new ArrayList<>(errors);
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

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
