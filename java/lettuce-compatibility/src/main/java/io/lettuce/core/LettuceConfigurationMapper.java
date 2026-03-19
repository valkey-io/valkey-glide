/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ProtocolVersion;
import glide.api.models.configuration.ServerCredentials;
import java.util.logging.Logger;

/** Utility class to map Lettuce RedisURI configurations to Valkey GLIDE configurations. */
public class LettuceConfigurationMapper {

    private static final Logger logger = Logger.getLogger(LettuceConfigurationMapper.class.getName());
    private static final String LETTUCE_ADAPTER_LIB_NAME = "GlideLettuceAdapter";

    /**
     * Convert Lettuce RedisURI configuration to GLIDE client configuration.
     *
     * @param redisURI the Lettuce RedisURI configuration
     * @return corresponding GLIDE configuration
     * @throws RedisException if configuration cannot be converted
     */
    public static GlideClientConfiguration mapToGlideConfig(RedisURI redisURI) {
        if (redisURI == null) {
            throw new RedisException("RedisURI cannot be null");
        }

        GlideClientConfiguration.GlideClientConfigurationBuilder builder =
                GlideClientConfiguration.builder();

        // Map basic connection settings
        mapConnectionSettings(redisURI, builder);

        // Map authentication and SSL/TLS settings
        mapCredentialsAndSsl(redisURI, builder);

        // Set libName for Lettuce compatibility layer
        builder.libName(LETTUCE_ADAPTER_LIB_NAME);

        // Default to RESP2 protocol (Lettuce default)
        builder.protocol(ProtocolVersion.RESP2);

        return builder.build();
    }

    /** Maps basic connection settings from RedisURI to GLIDE configuration. */
    private static void mapConnectionSettings(
            RedisURI redisURI, GlideClientConfiguration.GlideClientConfigurationBuilder builder) {

        // Address mapping
        builder.address(
                NodeAddress.builder().host(redisURI.getHost()).port(redisURI.getPort()).build());

        // Timeout mapping
        if (redisURI.getTimeout() != null
                && !redisURI.getTimeout().isZero()
                && !redisURI.getTimeout().isNegative()) {
            int timeoutMs = (int) redisURI.getTimeout().toMillis();
            if (timeoutMs > 0) {
                builder.requestTimeout(timeoutMs);
            }
        }

        // Database selection - log if non-default
        if (redisURI.getDatabase() != 0) {
            logger.warning(
                    "Database selection specified ("
                            + redisURI.getDatabase()
                            + "). GLIDE may handle database selection differently than Lettuce.");
        }
    }

    /** Maps authentication and SSL/TLS settings from RedisURI to GLIDE configuration. */
    private static void mapCredentialsAndSsl(
            RedisURI redisURI, GlideClientConfiguration.GlideClientConfigurationBuilder builder) {

        ServerCredentials.ServerCredentialsBuilder credentialsBuilder = null;

        // Handle authentication
        boolean hasUsername = redisURI.getUsername() != null && !redisURI.getUsername().isEmpty();
        boolean hasPassword = redisURI.getPassword() != null && !redisURI.getPassword().isEmpty();

        if (hasUsername || hasPassword) {
            credentialsBuilder = ServerCredentials.builder();

            if (hasUsername) {
                credentialsBuilder.username(redisURI.getUsername());
            }
            if (hasPassword) {
                credentialsBuilder.password(redisURI.getPassword());
            }
        }

        // Handle SSL/TLS
        if (redisURI.isSsl()) {
            builder.useTLS(true);

            if (credentialsBuilder == null) {
                credentialsBuilder = ServerCredentials.builder();
            }
        }

        // Set credentials if any were configured
        if (credentialsBuilder != null) {
            builder.credentials(credentialsBuilder.build());
        }
    }
}
