/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.internal.GlideCoreClient;

/**
 * Utility class to convert integration test configurations to GLIDE client
 * configurations.
 */
public final class ConfigurationConverter {

    private ConfigurationConverter() {
        // Utility class - no instantiation
    }

    /**
     * Convert GlideClientConfiguration to JNI Config for standalone client.
     */
    public static GlideCoreClient.Config convertStandaloneConfig(GlideClientConfiguration config) {
        return convertBaseConfig(config, false);
    }

    /**
     * Convert GlideClusterClientConfiguration to JNI Config for cluster client.
     */
    public static GlideCoreClient.Config convertClusterConfig(GlideClusterClientConfiguration config) {
        return convertBaseConfig(config, true);
    }

    /**
     * Convert base client configuration with cluster mode flag.
     */
    private static GlideCoreClient.Config convertBaseConfig(
            glide.api.models.configuration.BaseClientConfiguration config, 
            boolean clusterMode) {
        
        // Extract addresses from config
        java.util.List<String> addresses = new java.util.ArrayList<>();

        if (config.getAddresses() != null) {
            for (var address : config.getAddresses()) {
                addresses.add(address.getHost() + ":" + address.getPort());
            }
        }

        // Create and configure the GLIDE client config
        GlideCoreClient.Config jniConfig = new GlideCoreClient.Config(addresses);

        // Set cluster mode
        jniConfig.clusterMode(clusterMode);

        // Apply TLS settings
        jniConfig.useTls(config.isUseTLS());

        if (config.getRequestTimeout() != null) {
            jniConfig.requestTimeout(config.getRequestTimeout());
        }

        // Apply credentials if present
        if (config.getCredentials() != null) {
            jniConfig.credentials(
                config.getCredentials().getUsername(),
                config.getCredentials().getPassword()
            );
        }

        // Apply database selection for standalone only
        if (!clusterMode && config instanceof GlideClientConfiguration) {
            GlideClientConfiguration standaloneConfig = (GlideClientConfiguration) config;
            if (standaloneConfig.getDatabaseId() != null) {
                jniConfig.databaseId(standaloneConfig.getDatabaseId());
            }
        }

        if (config.getProtocol() != null) {
            jniConfig.protocol(config.getProtocol());
        }

        // Apply routing strategy configuration
        jniConfig.readFrom(config.getReadFrom());
        jniConfig.clientAZ(config.getClientAZ());
        jniConfig.lazyConnect(config.isLazyConnect());

        // Apply client name if provided
        if (config.getClientName() != null && !config.getClientName().trim().isEmpty()) {
            jniConfig.clientName(config.getClientName());
        }

        // Warn in Java if AZ strategy is used without clientAZ configured
        var rf = config.getReadFrom();
        var az = config.getClientAZ();
        if ((rf == glide.api.models.configuration.ReadFrom.AZ_AFFINITY
                || rf == glide.api.models.configuration.ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY)
                && (az == null || az.trim().isEmpty())) {
            glide.api.logging.Logger.warn(
                    "ConfigurationConverter",
                    "ReadFrom=" + rf
                            + " requires non-empty clientAZ; falling back behavior may occur (PreferReplica).");
        }

        // Subscriptions (binary-safe)
        java.util.List<byte[]> exact = new java.util.ArrayList<>();
        java.util.List<byte[]> pattern = new java.util.ArrayList<>();
        java.util.List<byte[]> sharded = new java.util.ArrayList<>();
        var subCfg = config.getSubscriptionConfiguration();
        if (subCfg != null) {
            if (!clusterMode && subCfg instanceof glide.api.models.configuration.StandaloneSubscriptionConfiguration) {
                var s = (glide.api.models.configuration.StandaloneSubscriptionConfiguration) subCfg;
                var map = s.getSubscriptions();
                if (map != null) {
                    var ex = glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode.EXACT;
                    var pt = glide.api.models.configuration.StandaloneSubscriptionConfiguration.PubSubChannelMode.PATTERN;
                    if (map.containsKey(ex)) for (glide.api.models.GlideString gs : map.get(ex)) exact.add(gs.getBytes());
                    if (map.containsKey(pt)) for (glide.api.models.GlideString gs : map.get(pt)) pattern.add(gs.getBytes());
                }
            } else if (clusterMode && subCfg instanceof glide.api.models.configuration.ClusterSubscriptionConfiguration) {
                var s = (glide.api.models.configuration.ClusterSubscriptionConfiguration) subCfg;
                var map = s.getSubscriptions();
                if (map != null) {
                    var ex = glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode.EXACT;
                    var pt = glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode.PATTERN;
                    var sh = glide.api.models.configuration.ClusterSubscriptionConfiguration.PubSubClusterChannelMode.SHARDED;
                    if (map.containsKey(ex)) for (glide.api.models.GlideString gs : map.get(ex)) exact.add(gs.getBytes());
                    if (map.containsKey(pt)) for (glide.api.models.GlideString gs : map.get(pt)) pattern.add(gs.getBytes());
                    if (map.containsKey(sh)) for (glide.api.models.GlideString gs : map.get(sh)) sharded.add(gs.getBytes());
                }
            }
        }
        jniConfig.subscriptions(exact.toArray(new byte[0][]), pattern.toArray(new byte[0][]), sharded.toArray(new byte[0][]));

        // Advanced settings
        var adv = clusterMode
                ? ((GlideClusterClientConfiguration) config).getAdvancedConfiguration()
                : ((GlideClientConfiguration) config).getAdvancedConfiguration();
        if (adv != null) {
            if (adv.getConnectionTimeout() != null) {
                jniConfig.connectionTimeout(adv.getConnectionTimeout());
            }
            var tlsAdv = adv.getTlsAdvancedConfiguration();
            if (tlsAdv != null && tlsAdv.isUseInsecureTLS() && config.isUseTLS()) {
                jniConfig.useInsecureTls(true);
            }
        }

        // Inflight limit
        if (config.getInflightRequestsLimit() != null) {
            jniConfig.maxInflightRequests(config.getInflightRequestsLimit());
        }

        // Reconnect/backoff
        if (config.getReconnectStrategy() != null) {
            jniConfig.reconnectStrategy(config.getReconnectStrategy());
        }

        return jniConfig;
    }
}