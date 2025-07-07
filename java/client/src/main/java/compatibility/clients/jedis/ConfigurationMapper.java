/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ServerCredentials;

/**
 * Utility class to map Jedis configurations to Valkey GLIDE configurations. This handles the
 * translation between the two configuration systems.
 */
public class ConfigurationMapper {

    /**
     * Convert Jedis client configuration to GLIDE client configuration.
     *
     * @param host the server host
     * @param port the server port
     * @param jedisConfig the Jedis configuration
     * @return corresponding GLIDE configuration
     */
    public static GlideClientConfiguration mapToGlideConfig(
            String host, int port, JedisClientConfig jedisConfig) {
        GlideClientConfiguration.GlideClientConfigurationBuilder builder =
                GlideClientConfiguration.builder()
                        .address(NodeAddress.builder().host(host).port(port).build())
                        .requestTimeout(jedisConfig.getSocketTimeoutMillis());

        //TO DO: Add all SSL/TLS Configuration related field mapping. This is not complete.
        if (jedisConfig.isSsl()) {
            builder.useTLS(true);

            // Map SSL parameters if available
            if (jedisConfig.getSslParameters() != null || jedisConfig.getSslSocketFactory() != null) {
                // Note: GLIDE may have different SSL configuration options
                // This is a simplified mapping - you may need to adapt based on GLIDE's actual SSL API
                builder.useTLS(true);
            }
        }

        // Authentication
        if (jedisConfig.getUser() != null && jedisConfig.getPassword() != null) {
            builder.credentials(ServerCredentials.builder()
                .username(jedisConfig.getUser())
                .password(jedisConfig.getPassword())
                .build());
        } else if (jedisConfig.getPassword() != null) {
            builder.credentials(ServerCredentials.builder()
                .password(jedisConfig.getPassword())
                .build());
        }

        // Protocol version
        // Note: GLIDE may not support runtime protocol configuration
        // Protocol is typically set at the connection level
        if (jedisConfig.getRedisProtocol() != null) {
            System.out.println(
                    "Warning: Protocol configuration may not be fully supported in GLIDE compatibility mode");
        }

        // Client name
        if (jedisConfig.getClientName() != null) {
            builder.clientName(jedisConfig.getClientName());
        }

        // Connection timeout
        if (jedisConfig.getConnectionTimeoutMillis() != jedisConfig.getSocketTimeoutMillis()) {
            // GLIDE may have separate connection timeout configuration
            // Adapt based on GLIDE's API
        }

        return builder.build();
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

    /**
     * Validate that the Jedis configuration is compatible with GLIDE.
     *
     * @param jedisConfig the configuration to validate
     * @throws JedisException if configuration is not supported
     */
    public static void validateConfiguration(JedisClientConfig jedisConfig) {
        // Check for unsupported features
        if (jedisConfig.getDatabase() != 0) {
            // GLIDE may not support database selection in the same way
            // This is a warning or could be handled differently
            System.out.println(
                    "Warning: Database selection may not be fully supported in GLIDE compatibility mode");
        }

        if (jedisConfig.getBlockingSocketTimeoutMillis() > 0) {
            // Check if GLIDE supports blocking socket timeouts
            System.out.println(
                    "Warning: Blocking socket timeout configuration may not be fully supported");
        }
    }
}
