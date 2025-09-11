/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.BaseSubscriptionConfiguration;
import glide.internal.GlideNativeBridge;

import java.util.Optional;

/**
 * ClientBuilder provides a bridge between the legacy UDS-based client creation
 * and the new JNI-based implementation. This class handles the creation of native
 * client handles and wraps them in ClientParams for the new JNI constructors.
 */
public class ClientBuilder {
    private final long nativeHandle;
    private final int maxInflight;
    private final int requestTimeout;
    private final Optional<BaseSubscriptionConfiguration> subscriptionConfiguration;

    /**
     * Create a ClientBuilder from a configuration.
     * This internally creates the JNI native client.
     *
     * @param config The client configuration
     */
    public ClientBuilder(BaseClientConfiguration config) {
        try {
            // Convert addresses to simple string array
            String[] addresses = config.getAddresses().stream()
                .map(addr -> addr.getHost() + ":" + addr.getPort())
                .toArray(String[]::new);
            
            // Extract credentials
            String username = null;
            String password = null;
            if (config.getCredentials() != null) {
                username = config.getCredentials().getUsername();
                password = config.getCredentials().getPassword();
            }
            
            // Determine client type
            boolean isCluster = config instanceof glide.api.models.configuration.GlideClusterClientConfiguration;
            
            // Get database ID for standalone clients
            int databaseId = 0;
            if (!isCluster && config instanceof glide.api.models.configuration.GlideClientConfiguration) {
                glide.api.models.configuration.GlideClientConfiguration standaloneConfig = 
                    (glide.api.models.configuration.GlideClientConfiguration) config;
                if (standaloneConfig.getDatabaseId() != null) {
                    databaseId = standaloneConfig.getDatabaseId();
                }
            }
            
            // Store timeout and inflight settings
            this.requestTimeout = config.getRequestTimeout() != null ? config.getRequestTimeout() : 5000;
            this.maxInflight = 0; // Use defaults
            this.subscriptionConfiguration = Optional.ofNullable(config.getSubscriptionConfiguration());
            
            // Create native client
            this.nativeHandle = GlideNativeBridge.createClient(
                addresses,
                databaseId,
                username,
                password,
                config.isUseTLS(),
                false, // insecure TLS - TODO: extract from config
                isCluster,
                this.requestTimeout,
                getConnectionTimeoutFromConfig(config),
                this.maxInflight
            );
            
            if (this.nativeHandle == 0) {
                throw new RuntimeException("Failed to create client - connection refused");
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create client", e);
        }
    }

    /**
     * Get the native client handle.
     *
     * @return The native client handle
     */
    public long getNativeHandle() {
        return nativeHandle;
    }

    /**
     * Get the maximum inflight requests setting.
     *
     * @return The max inflight requests
     */
    public int getMaxInflight() {
        return maxInflight;
    }

    /**
     * Get the request timeout setting.
     *
     * @return The request timeout in milliseconds
     */
    public int getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Get the subscription configuration.
     *
     * @return Optional subscription configuration
     */
    public Optional<BaseSubscriptionConfiguration> getSubscriptionConfiguration() {
        return subscriptionConfiguration;
    }

    /**
     * Convert this ClientBuilder to ClientParams for JNI constructors.
     *
     * @return ClientParams instance
     */
    public BaseClient.ClientParams toClientParams() {
        return new BaseClient.ClientParams(nativeHandle, maxInflight, requestTimeout, subscriptionConfiguration);
    }

    /**
     * Extract connection timeout from configuration, handling both standalone and cluster configs.
     */
    private static int getConnectionTimeoutFromConfig(BaseClientConfiguration config) {
        // Default value from Rust core documentation: 2000ms
        int defaultConnectionTimeout = 2000;
        
        if (config instanceof glide.api.models.configuration.GlideClientConfiguration) {
            glide.api.models.configuration.GlideClientConfiguration standaloneConfig = 
                (glide.api.models.configuration.GlideClientConfiguration) config;
            if (standaloneConfig.getAdvancedConfiguration() != null && 
                standaloneConfig.getAdvancedConfiguration().getConnectionTimeout() != null) {
                return standaloneConfig.getAdvancedConfiguration().getConnectionTimeout();
            }
        } else if (config instanceof glide.api.models.configuration.GlideClusterClientConfiguration) {
            glide.api.models.configuration.GlideClusterClientConfiguration clusterConfig = 
                (glide.api.models.configuration.GlideClusterClientConfiguration) config;
            if (clusterConfig.getAdvancedConfiguration() != null && 
                clusterConfig.getAdvancedConfiguration().getConnectionTimeout() != null) {
                return clusterConfig.getAdvancedConfiguration().getConnectionTimeout();
            }
        }
        
        return defaultConnectionTimeout;
    }
}
