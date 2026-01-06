/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static connection_request.ConnectionRequestOuterClass.*;

import glide.api.models.configuration.AdvancedBaseClientConfiguration;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.TlsAdvancedConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConfigurationError;
import glide.internal.AsyncRegistry;
import glide.internal.GlideNativeBridge;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;

/**
 * ConnectionManager that wraps GlideNativeBridge to handle native client connections. Manages the
 * lifecycle of native client instances and provides connection services.
 */
@RequiredArgsConstructor
public class ConnectionManager {

    /** Default library name for Java clients */
    private static final String DEFAULT_LIB_NAME = "GlideJava";

    /** Native client handle for operations */
    private long nativeClientHandle = 0;

    private int maxInflightRequests = 0;
    private int requestTimeoutMs = 5000;
    private ServerCredentials credentials;
    private volatile boolean isClosed = false;

    /**
     * Connect to Valkey using the native bridge.
     *
     * @param configuration Connection Configuration
     * @return CompletableFuture that completes when connection is established
     */
    public CompletableFuture<Void> connectToValkey(BaseClientConfiguration configuration) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        // Convert addresses to simple string array
                        String[] addresses =
                                configuration.getAddresses().stream()
                                        .map(addr -> addr.getHost() + ":" + addr.getPort())
                                        .toArray(String[]::new);

                        // Extract credentials
                        if (configuration.getCredentials() != null) {
                            this.credentials = configuration.getCredentials();
                        } else {
                            this.credentials = null;
                        }

                        // Determine client type
                        boolean isCluster = configuration instanceof GlideClusterClientConfiguration;

                        // Extract configuration values using core defaults
                        this.maxInflightRequests =
                                configuration.getInflightRequestsLimit() != null
                                        ? configuration.getInflightRequestsLimit()
                                        : GlideNativeBridge.getGlideCoreDefaultMaxInflightRequests();
                        this.requestTimeoutMs =
                                configuration.getRequestTimeout() != null
                                        ? configuration.getRequestTimeout()
                                        : (int) GlideNativeBridge.getGlideCoreDefaultRequestTimeoutMs();

                        boolean insecureTls = resolveInsecureTls(configuration);
                        int connectionTimeoutMs = resolveConnectionTimeout(configuration);
                        String protocolName =
                                configuration.getProtocol() != null ? configuration.getProtocol().name() : null;
                        BackoffStrategy reconnectStrategy = configuration.getReconnectStrategy();
                        int reconnectNumRetries =
                                reconnectStrategy != null && reconnectStrategy.getNumOfRetries() != null
                                        ? reconnectStrategy.getNumOfRetries()
                                        : 0;
                        int reconnectFactor =
                                reconnectStrategy != null && reconnectStrategy.getFactor() != null
                                        ? reconnectStrategy.getFactor()
                                        : 0;
                        int reconnectExponentBase =
                                reconnectStrategy != null && reconnectStrategy.getExponentBase() != null
                                        ? reconnectStrategy.getExponentBase()
                                        : 0;
                        int reconnectJitterPercent =
                                reconnectStrategy != null && reconnectStrategy.getJitterPercent() != null
                                        ? reconnectStrategy.getJitterPercent()
                                        : -1;

                        // Create native client through bridge
                        byte[][] subExact = glide.internal.GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
                        byte[][] subPattern = glide.internal.GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
                        byte[][] subSharded = glide.internal.GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
                        if (configuration.getSubscriptionConfiguration() != null) {
                            var sc = configuration.getSubscriptionConfiguration();
                            try {
                                if (sc
                                        instanceof glide.api.models.configuration.StandaloneSubscriptionConfiguration) {
                                    var subs =
                                            ((glide.api.models.configuration.StandaloneSubscriptionConfiguration) sc)
                                                    .getSubscriptions();
                                    var exact =
                                            subs.get(
                                                    glide.api.models.configuration.StandaloneSubscriptionConfiguration
                                                            .PubSubChannelMode.EXACT);
                                    var pattern =
                                            subs.get(
                                                    glide.api.models.configuration.StandaloneSubscriptionConfiguration
                                                            .PubSubChannelMode.PATTERN);
                                    if (exact != null) {
                                        subExact =
                                                exact.stream()
                                                        .map(glide.api.models.GlideString::getBytes)
                                                        .toArray(byte[][]::new);
                                    }
                                    if (pattern != null) {
                                        subPattern =
                                                pattern.stream()
                                                        .map(glide.api.models.GlideString::getBytes)
                                                        .toArray(byte[][]::new);
                                    }
                                } else if (sc
                                        instanceof glide.api.models.configuration.ClusterSubscriptionConfiguration) {
                                    var subs =
                                            ((glide.api.models.configuration.ClusterSubscriptionConfiguration) sc)
                                                    .getSubscriptions();
                                    var exact =
                                            subs.get(
                                                    glide.api.models.configuration.ClusterSubscriptionConfiguration
                                                            .PubSubClusterChannelMode.EXACT);
                                    var pattern =
                                            subs.get(
                                                    glide.api.models.configuration.ClusterSubscriptionConfiguration
                                                            .PubSubClusterChannelMode.PATTERN);
                                    var sharded =
                                            subs.get(
                                                    glide.api.models.configuration.ClusterSubscriptionConfiguration
                                                            .PubSubClusterChannelMode.SHARDED);
                                    if (exact != null) {
                                        subExact =
                                                exact.stream()
                                                        .map(glide.api.models.GlideString::getBytes)
                                                        .toArray(byte[][]::new);
                                    }
                                    if (pattern != null) {
                                        subPattern =
                                                pattern.stream()
                                                        .map(glide.api.models.GlideString::getBytes)
                                                        .toArray(byte[][]::new);
                                    }
                                    if (sharded != null) {
                                        subSharded =
                                                sharded.stream()
                                                        .map(glide.api.models.GlideString::getBytes)
                                                        .toArray(byte[][]::new);
                                    }
                                }
                            } catch (Throwable ignore) {
                            }
                        }

                        // Build ConnectionRequest protobuf
                        ConnectionRequest.Builder requestBuilder = ConnectionRequest.newBuilder();

                        // Add addresses
                        for (String addr : addresses) {
                            String[] parts = addr.split(":");
                            if (parts.length == 2) {
                                requestBuilder.addAddresses(
                                        NodeAddress.newBuilder()
                                                .setHost(parts[0])
                                                .setPort(Integer.parseInt(parts[1]))
                                                .build());
                            }
                        }

                        // Set TLS mode
                        if (configuration.isUseTLS()) {
                            if (insecureTls) {
                                requestBuilder.setTlsMode(TlsMode.InsecureTls);
                            } else {
                                requestBuilder.setTlsMode(TlsMode.SecureTls);
                            }
                        } else {
                            requestBuilder.setTlsMode(TlsMode.NoTls);
                        }

                        // Set authentication
                        if (credentials != null) {
                            AuthenticationInfo.Builder authBuilder = AuthenticationInfo.newBuilder();
                            if (credentials.getUsername() != null) {
                                authBuilder.setUsername(credentials.getUsername());
                            }
                            if (credentials.getPassword() != null) {
                                authBuilder.setPassword(credentials.getPassword());
                            }
                            // Set IAM credentials if present
                            if (credentials.getIamConfig() != null) {
                                var iamConfig = credentials.getIamConfig();
                                IamCredentials.Builder iamBuilder = IamCredentials.newBuilder();
                                iamBuilder.setClusterName(iamConfig.getClusterName());
                                iamBuilder.setRegion(iamConfig.getRegion());

                                // Map ServiceType enum to protobuf ServiceType
                                if (iamConfig.getService()
                                        == glide.api.models.configuration.ServiceType.ELASTICACHE) {
                                    iamBuilder.setServiceType(ServiceType.ELASTICACHE);
                                } else if (iamConfig.getService()
                                        == glide.api.models.configuration.ServiceType.MEMORYDB) {
                                    iamBuilder.setServiceType(ServiceType.MEMORYDB);
                                }

                                // Set optional refresh interval
                                if (iamConfig.getRefreshIntervalSeconds() != null) {
                                    iamBuilder.setRefreshIntervalSeconds(iamConfig.getRefreshIntervalSeconds());
                                }

                                authBuilder.setIamCredentials(iamBuilder.build());
                            }
                            requestBuilder.setAuthenticationInfo(authBuilder.build());
                        }

                        // Set cluster mode
                        requestBuilder.setClusterModeEnabled(isCluster);

                        // Set refresh topology from initial nodes for cluster mode
                        if (isCluster) {
                            GlideClusterClientConfiguration clusterConfig =
                                    (GlideClusterClientConfiguration) configuration;
                            if (clusterConfig.getAdvancedConfiguration() != null) {
                                requestBuilder.setRefreshTopologyFromInitialNodes(
                                        clusterConfig.getAdvancedConfiguration().isRefreshTopologyFromInitialNodes());
                            }
                        }

                        // Set timeouts
                        requestBuilder.setRequestTimeout(requestTimeoutMs);
                        requestBuilder.setConnectionTimeout(connectionTimeoutMs);
                        requestBuilder.setInflightRequestsLimit(maxInflightRequests);

                        // Set read from strategy
                        String readFromName = configuration.getReadFrom().name();
                        if ("PRIMARY".equals(readFromName)) {
                            requestBuilder.setReadFrom(ReadFrom.Primary);
                        } else if ("PREFER_REPLICA".equals(readFromName)) {
                            requestBuilder.setReadFrom(ReadFrom.PreferReplica);
                        } else if ("AZ_AFFINITY".equals(readFromName)) {
                            requestBuilder.setReadFrom(ReadFrom.AZAffinity);
                        } else if ("AZ_AFFINITY_REPLICAS_AND_PRIMARY".equals(readFromName)) {
                            requestBuilder.setReadFrom(ReadFrom.AZAffinityReplicasAndPrimary);
                        }

                        // Set client metadata
                        if (configuration.getClientAZ() != null) {
                            requestBuilder.setClientAz(configuration.getClientAZ());
                        }
                        if (configuration.getClientName() != null) {
                            requestBuilder.setClientName(configuration.getClientName());
                        }
                        if (configuration.getLibName() != null) {
                            requestBuilder.setLibName(configuration.getLibName());
                        } else {
                            requestBuilder.setLibName(DEFAULT_LIB_NAME);
                        }
                        requestBuilder.setLazyConnect(configuration.isLazyConnect());

                        // Set database ID
                        if (configuration.getDatabaseId() != null) {
                            requestBuilder.setDatabaseId(configuration.getDatabaseId());
                        }

                        // Set protocol version if specified
                        if (protocolName != null) {
                            if ("RESP2".equals(protocolName)) {
                                requestBuilder.setProtocol(ProtocolVersion.RESP2);
                            } else if ("RESP3".equals(protocolName)) {
                                requestBuilder.setProtocol(ProtocolVersion.RESP3);
                            }
                        }

                        // Set reconnect strategy
                        if (reconnectNumRetries > 0 || reconnectFactor > 0 || reconnectExponentBase > 0) {
                            ConnectionRetryStrategy.Builder retryBuilder = ConnectionRetryStrategy.newBuilder();
                            retryBuilder.setNumberOfRetries(reconnectNumRetries);
                            retryBuilder.setFactor(reconnectFactor);
                            retryBuilder.setExponentBase(reconnectExponentBase);
                            if (reconnectJitterPercent >= 0) {
                                retryBuilder.setJitterPercent(reconnectJitterPercent);
                            }
                            requestBuilder.setConnectionRetryStrategy(retryBuilder.build());
                        }

                        // Set root certificates if provided from user configuration
                        byte[] rootCerts = extractRootCertificates(configuration);
                        if (rootCerts != null) {
                            requestBuilder.addRootCerts(com.google.protobuf.ByteString.copyFrom(rootCerts));
                        }

                        // Set pubsub subscriptions
                        if (subExact.length > 0 || subPattern.length > 0 || subSharded.length > 0) {
                            PubSubSubscriptions.Builder subBuilder = PubSubSubscriptions.newBuilder();

                            if (subExact.length > 0) {
                                PubSubChannelsOrPatterns.Builder exactBuilder =
                                        PubSubChannelsOrPatterns.newBuilder();
                                for (byte[] channel : subExact) {
                                    exactBuilder.addChannelsOrPatterns(
                                            com.google.protobuf.ByteString.copyFrom(channel));
                                }
                                subBuilder.putChannelsOrPatternsByType(
                                        PubSubChannelType.Exact.getNumber(), exactBuilder.build());
                            }

                            if (subPattern.length > 0) {
                                PubSubChannelsOrPatterns.Builder patternBuilder =
                                        PubSubChannelsOrPatterns.newBuilder();
                                for (byte[] pattern : subPattern) {
                                    patternBuilder.addChannelsOrPatterns(
                                            com.google.protobuf.ByteString.copyFrom(pattern));
                                }
                                subBuilder.putChannelsOrPatternsByType(
                                        PubSubChannelType.Pattern.getNumber(), patternBuilder.build());
                            }

                            if (isCluster && subSharded.length > 0) {
                                PubSubChannelsOrPatterns.Builder shardedBuilder =
                                        PubSubChannelsOrPatterns.newBuilder();
                                for (byte[] sharded : subSharded) {
                                    shardedBuilder.addChannelsOrPatterns(
                                            com.google.protobuf.ByteString.copyFrom(sharded));
                                }
                                subBuilder.putChannelsOrPatternsByType(
                                        PubSubChannelType.Sharded.getNumber(), shardedBuilder.build());
                            }

                            requestBuilder.setPubsubSubscriptions(subBuilder.build());
                        }

                        // Set TCP_NODELAY option (only if explicitly configured)
                        AdvancedBaseClientConfiguration advanced = extractAdvancedConfiguration(configuration);
                        if (advanced != null && advanced.getTcpNoDelay() != null) {
                            requestBuilder.setTcpNodelay(advanced.getTcpNoDelay());
                        }

                        // Build and serialize to bytes
                        ConnectionRequest request = requestBuilder.build();
                        byte[] requestBytes = request.toByteArray();

                        // Create native client with protobuf bytes
                        this.nativeClientHandle = GlideNativeBridge.createClient(requestBytes);

                        if (nativeClientHandle == 0) {
                            throw new ClosingException("Failed to create client - Connection refused");
                        }

                        return null; // Success
                    } catch (Exception e) {
                        if (e instanceof ClosingException) {
                            throw e;
                        }
                        throw new RuntimeException("Failed to create client", e);
                    }
                });
    }

    /**
     * Close the connection.
     *
     * @return Future that completes when connection is closed
     */
    public Future<Void> closeConnection() {
        return CompletableFuture.supplyAsync(
                () -> {
                    if (!isClosed && nativeClientHandle != 0) {
                        try {
                            // Clean up any pending async operations for this client
                            AsyncRegistry.cleanupClient(nativeClientHandle);
                            GlideNativeBridge.closeClient(nativeClientHandle);
                        } finally {
                            isClosed = true;
                            nativeClientHandle = 0;
                        }
                    }
                    return null;
                });
    }

    /** Close the connection immediately (synchronous version). */
    public void closeConnectionSync() {
        if (isClosed) {
            return; // Already closed
        }

        try {
            // Mark as closed immediately to prevent new commands
            isClosed = true;

            // Clean up any pending async operations for this client
            AsyncRegistry.cleanupClient(nativeClientHandle);

            // Close the native client handle
            if (nativeClientHandle != 0) {
                GlideNativeBridge.closeClient(nativeClientHandle);
                nativeClientHandle = 0;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to close client", e);
        }
    }

    /** Get the native client handle for use by CommandManager. */
    public long getNativeClientHandle() {
        return nativeClientHandle;
    }

    /** Get max inflight requests setting. */
    public int getMaxInflightRequests() {
        return maxInflightRequests;
    }

    /** Get request timeout setting. */
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /** Check if the connection is closed. */
    public boolean isClosed() {
        return isClosed;
    }

    /** Check if the client is connected and ready for commands. */
    public boolean isConnected() {
        if (isClosed || nativeClientHandle == 0) {
            return false;
        }
        try {
            return GlideNativeBridge.isConnected(nativeClientHandle);
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns the credentials configured for this connection, if any. */
    public ServerCredentials getCredentials() {
        return credentials;
    }

    /** Update the cached password after a successful rotation (used by BaseClient). */
    public void updateStoredPassword(String password) {
        if (credentials == null) {
            return;
        }
        credentials =
                ServerCredentials.builder()
                        .username(credentials.getUsername())
                        .password(password != null ? password : "")
                        .build();
    }

    /** Get client information from the native layer. */
    public String getClientInfo() {
        if (isClosed || nativeClientHandle == 0) {
            throw new IllegalStateException("Client is closed");
        }
        return GlideNativeBridge.getClientInfo(nativeClientHandle);
    }

    private static int resolveConnectionTimeout(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = extractAdvancedConfiguration(configuration);
        if (advanced != null && advanced.getConnectionTimeout() != null) {
            return advanced.getConnectionTimeout();
        }
        return (int) GlideNativeBridge.getGlideCoreDefaultConnectionTimeoutMs();
    }

    private static boolean resolveInsecureTls(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = extractAdvancedConfiguration(configuration);
        if (advanced == null) {
            return false;
        }
        TlsAdvancedConfiguration tlsConfig = advanced.getTlsAdvancedConfiguration();
        if (tlsConfig != null && tlsConfig.isUseInsecureTLS()) {
            if (!configuration.isUseTLS()) {
                throw new ConfigurationError(
                        "`useInsecureTLS` cannot be enabled when `useTLS` is disabled.");
            }
            return true;
        }
        return false;
    }

    private static byte[] extractRootCertificates(BaseClientConfiguration configuration) {
        AdvancedBaseClientConfiguration advanced = extractAdvancedConfiguration(configuration);
        if (advanced == null) {
            return null;
        }
        TlsAdvancedConfiguration tlsConfig = advanced.getTlsAdvancedConfiguration();
        if (tlsConfig == null) {
            return null;
        }
        return tlsConfig.getRootCertificates();
    }

    private static AdvancedBaseClientConfiguration extractAdvancedConfiguration(
            BaseClientConfiguration configuration) {
        if (configuration instanceof GlideClientConfiguration) {
            return ((GlideClientConfiguration) configuration).getAdvancedConfiguration();
        }
        if (configuration instanceof GlideClusterClientConfiguration) {
            return ((GlideClusterClientConfiguration) configuration).getAdvancedConfiguration();
        }
        return null;
    }
}
