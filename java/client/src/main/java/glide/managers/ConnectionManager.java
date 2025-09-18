/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.configuration.AdvancedBaseClientConfiguration;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
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

    /** Native client handle for operations */
    private long nativeClientHandle = 0;

    private int maxInflightRequests = 0;
    private int requestTimeoutMs = 5000;
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
                        String username = null;
                        String password = null;
                        if (configuration.getCredentials() != null) {
                            username = configuration.getCredentials().getUsername();
                            password = configuration.getCredentials().getPassword();
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
                                        : (int) GlideNativeBridge.getGlideCoreDefaultTimeoutMs();

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
                        byte[][] subExact = new byte[0][];
                        byte[][] subPattern = new byte[0][];
                        byte[][] subSharded = new byte[0][];
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

                        this.nativeClientHandle =
                                GlideNativeBridge.createClient(
                                        addresses,
                                        configuration.getDatabaseId() != null ? configuration.getDatabaseId() : 0,
                                        username,
                                        password,
                                        configuration.isUseTLS(),
                                        insecureTls,
                                        isCluster,
                                        requestTimeoutMs,
                                        connectionTimeoutMs,
                                        maxInflightRequests,
                                        configuration.getReadFrom() != null ? configuration.getReadFrom().name() : null,
                                        configuration.getClientAZ(),
                                        configuration.isLazyConnect(),
                                        configuration.getClientName(),
                                        protocolName,
                                        reconnectNumRetries,
                                        reconnectFactor,
                                        reconnectExponentBase,
                                        reconnectJitterPercent,
                                        subExact,
                                        subPattern,
                                        subSharded);

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
        return (int) GlideNativeBridge.getGlideCoreDefaultTimeoutMs();
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
