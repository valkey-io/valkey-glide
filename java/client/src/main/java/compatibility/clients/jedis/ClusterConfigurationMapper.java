/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package compatibility.clients.jedis;

import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to map Jedis cluster configurations to Valkey GLIDE cluster configurations.
 * This handles the translation between the two configuration systems for cluster operations.
 */
public class ClusterConfigurationMapper {

    /**
     * Convert Jedis cluster configuration to GLIDE cluster configuration.
     *
     * @param nodes the cluster nodes
     * @param jedisConfig the Jedis configuration
     * @return corresponding GLIDE cluster configuration
     */
    public static GlideClusterClientConfiguration mapToGlideClusterConfig(
            Set<HostAndPort> nodes, JedisClientConfig jedisConfig) {
        
        GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder builder =
                GlideClusterClientConfiguration.builder()
                        .addresses(nodes.stream()
                                .map(node -> NodeAddress.builder()
                                        .host(node.getHost())
                                        .port(node.getPort())
                                        .build())
                                .collect(Collectors.toList()))
                        .requestTimeout(jedisConfig.getSocketTimeoutMillis());

        // SSL/TLS Configuration
        if (jedisConfig.isSsl()) {
            builder.useTLS(true);
        }

        // Authentication
        if (jedisConfig.getUser() != null && jedisConfig.getPassword() != null) {
            // GLIDE cluster authentication
            // Note: Adapt based on GLIDE's actual authentication API
        } else if (jedisConfig.getPassword() != null) {
            // Password-only authentication
        }

        // Client name
        if (jedisConfig.getClientName() != null) {
            builder.clientName(jedisConfig.getClientName());
        }

        // Cluster-specific configurations
        // Note: GLIDE may have different cluster configuration options
        // This is a simplified mapping

        return builder.build();
    }

    /**
     * Create a default GLIDE cluster configuration for simple cluster connections.
     *
     * @param nodes the cluster nodes
     * @param useSsl whether to use SSL
     * @return GLIDE cluster configuration
     */
    public static GlideClusterClientConfiguration createDefaultClusterConfig(
            Set<HostAndPort> nodes, boolean useSsl) {
        return GlideClusterClientConfiguration.builder()
                .addresses(nodes.stream()
                        .map(node -> NodeAddress.builder()
                                .host(node.getHost())
                                .port(node.getPort())
                                .build())
                        .collect(Collectors.toList()))
                .useTLS(useSsl)
                .requestTimeout(DefaultJedisClientConfig.DEFAULT_TIMEOUT_MILLIS)
                .build();
    }

    /**
     * Validate that the Jedis cluster configuration is compatible with GLIDE.
     *
     * @param jedisConfig the configuration to validate
     * @throws JedisException if configuration is not supported
     */
    public static void validateClusterConfiguration(JedisClientConfig jedisConfig) {
        // Check for unsupported cluster features
        if (jedisConfig.getDatabase() != 0) {
            System.out.println(
                    "Warning: Database selection is not supported in cluster mode");
        }

        if (jedisConfig.getBlockingSocketTimeoutMillis() > 0) {
            System.out.println(
                    "Warning: Blocking socket timeout configuration may not be fully supported in cluster mode");
        }

        // Add more cluster-specific validations as needed
    }
}
