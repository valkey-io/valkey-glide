/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package redis.clients.jedis;

import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ServerCredentials;
import java.util.Set;
import java.util.logging.Logger;
import redis.clients.jedis.exceptions.JedisException;

/** Utility class to map Jedis cluster configurations to Valkey GLIDE cluster configurations. */
public class ClusterConfigurationMapper {

    private static final Logger logger = Logger.getLogger(ClusterConfigurationMapper.class.getName());

    /**
     * Validate cluster configuration for compatibility.
     *
     * @param jedisConfig the Jedis configuration to validate
     * @throws JedisException if configuration is invalid for cluster mode
     */
    public static void validateClusterConfiguration(JedisClientConfig jedisConfig) {
        // Validate database selection - clusters only support database 0
        if (jedisConfig.getDatabase() != 0) {
            throw new JedisException(
                    "Cluster mode only supports database 0, but database "
                            + jedisConfig.getDatabase()
                            + " was specified");
        }

        // Log warnings for configurations that might not work as expected in cluster mode
        if (jedisConfig.getSocketTimeoutMillis() <= 0) {
            logger.warning("Socket timeout not set - this may cause issues in cluster mode");
        }

        if (jedisConfig.getConnectionTimeoutMillis() <= 0) {
            logger.warning("Connection timeout not set - this may cause issues in cluster mode");
        }
    }

    /**
     * Convert Jedis cluster configuration to GLIDE cluster configuration.
     *
     * @param nodes the cluster nodes
     * @param jedisConfig the Jedis configuration
     * @return the GLIDE cluster configuration
     */
    public static GlideClusterClientConfiguration mapToGlideClusterConfig(
            Set<HostAndPort> nodes, JedisClientConfig jedisConfig) {

        GlideClusterClientConfiguration.GlideClusterClientConfigurationBuilder builder =
                GlideClusterClientConfiguration.builder();

        // Add all cluster nodes
        for (HostAndPort node : nodes) {
            builder.address(NodeAddress.builder().host(node.getHost()).port(node.getPort()).build());
        }

        // Map timeout configuration
        if (jedisConfig.getSocketTimeoutMillis() > 0) {
            builder.requestTimeout(jedisConfig.getSocketTimeoutMillis());
        }

        if (jedisConfig.getConnectionTimeoutMillis() > 0) {
            builder.requestTimeout(jedisConfig.getConnectionTimeoutMillis());
        }

        // Map authentication configuration
        if (jedisConfig.getUser() != null && jedisConfig.getPassword() != null) {
            builder.credentials(
                    ServerCredentials.builder()
                            .username(jedisConfig.getUser())
                            .password(jedisConfig.getPassword())
                            .build());
        } else if (jedisConfig.getPassword() != null) {
            builder.credentials(ServerCredentials.builder().password(jedisConfig.getPassword()).build());
        }

        // Map SSL configuration
        if (jedisConfig.isSsl()) {
            builder.useTLS(true);
        }

        // Map client name
        if (jedisConfig.getClientName() != null) {
            builder.clientName(jedisConfig.getClientName());
        }

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
}
