/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import static connection_request.ConnectionRequestOuterClass.*;

import glide.api.models.GlideString;
import glide.api.models.configuration.AddressResolver;
import glide.api.models.configuration.AdvancedBaseClientConfiguration;
import glide.api.models.configuration.AdvancedGlideClusterClientConfiguration;
import glide.api.models.configuration.BackoffStrategy;
import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.BaseSubscriptionConfiguration;
import glide.api.models.configuration.ClientCircuitBreakerConfiguration;
import glide.api.models.configuration.ClientSideCache;
import glide.api.models.configuration.ClusterSubscriptionConfiguration;
import glide.api.models.configuration.CompressionBackend;
import glide.api.models.configuration.CompressionConfiguration;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.configuration.IamAuthConfig;
import glide.api.models.configuration.NodeDiscoveryMode;
import glide.api.models.configuration.PeriodicChecksConfig;
import glide.api.models.configuration.PeriodicChecksManualInterval;
import glide.api.models.configuration.PeriodicChecksStatus;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.configuration.StandaloneSubscriptionConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.api.models.exceptions.ConfigurationError;
import glide.api.models.exceptions.GlideException;
import glide.internal.AsyncRegistry;
import glide.internal.ClientLibraryNameResolver;
import glide.internal.GlideNativeBridge;
import java.util.Map;
import java.util.Set;
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
    private volatile long nativeClientHandle = 0;

    private int maxInflightRequests = 0;
    private int requestTimeoutMs = 5000;
    private ServerCredentials credentials;
    private volatile boolean isClosed = false;

    /** Serialized protobuf ConnectionRequest bytes (stored for scope pool creation). */
    private volatile byte[] connectionRequestBytes;

    /**
     * True when this manager wraps a pool-borrowed native client. The pool owns the native
     * connection's lifecycle, so close() here must not tear it down.
     */
    private boolean poolBorrowed = false;

    /**
     * Backward-compatible pool-borrowed constructor that defers inflight/timeout to core defaults and
     * carries no credentials. Delegates to the full constructor.
     */
    public ConnectionManager(long nativeClientHandle, byte[] connectionRequestBytes) {
        this(nativeClientHandle, 0, 5000, null, connectionRequestBytes);
    }

    /**
     * Constructor for pool-borrowed clients. Seeds the fields the direct path sets in {@link
     * #connectToValkey}: the native handle and serialized ConnectionRequest that {@code
     * scopedConnection} reads, the resolved inflight/timeout values the getters report, and the
     * credentials the IAM guards ({@code refreshIamToken}, {@code updateConnectionPassword}) read.
     * Without the credentials a borrowed client from an IAM-configured pool would see them as null
     * and misbehave.
     */
    public ConnectionManager(
            long nativeClientHandle,
            int maxInflightRequests,
            int requestTimeoutMs,
            ServerCredentials credentials,
            byte[] connectionRequestBytes) {
        this.nativeClientHandle = nativeClientHandle;
        this.maxInflightRequests = maxInflightRequests;
        this.requestTimeoutMs = requestTimeoutMs;
        this.credentials = credentials;
        this.connectionRequestBytes = connectionRequestBytes;
        this.poolBorrowed = true;
    }

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
                        // Extract credentials
                        if (configuration.getCredentials() != null) {
                            this.credentials = configuration.getCredentials();
                        } else {
                            this.credentials = null;
                        }

                        // Cache the values other methods on this manager read back.
                        this.maxInflightRequests =
                                configuration.getInflightRequestsLimit() != null
                                        ? configuration.getInflightRequestsLimit()
                                        : GlideNativeBridge.getGlideCoreDefaultMaxInflightRequests();
                        this.requestTimeoutMs =
                                configuration.getRequestTimeout() != null
                                        ? configuration.getRequestTimeout()
                                        : (int) GlideNativeBridge.getGlideCoreDefaultRequestTimeoutMs();

                        // Build and serialize to bytes via the shared builder (same path pooled
                        // clients use), then keep the connect-only side effects below.
                        byte[] requestBytes = buildConnectionRequest(configuration).toByteArray();
                        this.connectionRequestBytes = requestBytes;

                        // Get the address resolver (may be null if not configured)
                        // The resolver is passed directly to native code which stores it as a global reference
                        // to prevent garbage collection while the client is alive
                        AddressResolver addressResolver = configuration.getAddressResolver().orElse(null);

                        // Build the IAM credentials provider when a custom credentials provider is
                        // set. The lambda is passed to native code as a global reference so that
                        // Rust can invoke it from any thread when it needs to sign a fresh IAM
                        // token.
                        glide.api.models.configuration.GlideCredentialProvider iamCredentialsProvider = null;
                        if (credentials != null && credentials.getIamConfig() != null) {
                            glide.api.models.configuration.GlideCredentialProvider configuredProvider =
                                    credentials.getIamConfig().getCredentialsProvider();
                            if (configuredProvider != null) {
                                iamCredentialsProvider = configuredProvider;
                            }
                        }

                        // Create native client with protobuf bytes
                        // Native code will store the resolver and IAM provider as global references
                        // if provided
                        this.nativeClientHandle =
                                GlideNativeBridge.createClient(
                                        requestBytes, addressResolver, iamCredentialsProvider);

                        if (nativeClientHandle == 0) {
                            throw new ClosingException("Failed to create client");
                        }

                        return null; // Success
                    } catch (Exception e) {
                        if (e instanceof GlideException) {
                            throw (GlideException) e;
                        }
                        throw new ClosingException("Failed to create client: " + e.getMessage());
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
                    if (poolBorrowed) {
                        // The pool owns the native handle and the wrapper is cached and reused across
                        // borrows, so this must not mark the manager closed. Releasing goes through the
                        // pool.
                        return null;
                    }
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

        if (poolBorrowed) {
            // The pool owns the native handle and the wrapper is cached and reused across borrows, so
            // this must not mark the manager closed. Releasing goes through the pool.
            return;
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
            if (e instanceof GlideException) {
                throw (GlideException) e;
            }
            throw new ClosingException("Failed to close client: " + e.getMessage());
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

    /** Get the serialized ConnectionRequest bytes for scope pool creation. */
    public byte[] getConnectionRequestBytes() {
        return connectionRequestBytes;
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
        AdvancedBaseClientConfiguration advanced = configuration.getAdvancedConfiguration();
        if (advanced != null && advanced.getConnectionTimeout() != null) {
            return advanced.getConnectionTimeout();
        }
        return (int) GlideNativeBridge.getGlideCoreDefaultConnectionTimeoutMs();
    }

    private static boolean resolveInsecureTls(BaseClientConfiguration configuration) {
        return TlsConfigHelper.resolveInsecureTls(configuration);
    }

    private static byte[] extractRootCertificates(BaseClientConfiguration configuration) {
        return TlsConfigHelper.extractRootCertificates(configuration);
    }

    private static byte[] extractClientCertificate(BaseClientConfiguration configuration) {
        return TlsConfigHelper.extractClientCertificate(configuration);
    }

    private static byte[] extractClientKey(BaseClientConfiguration configuration) {
        return TlsConfigHelper.extractClientKey(configuration);
    }

    private static String extractClientCertPath(BaseClientConfiguration configuration) {
        return TlsConfigHelper.extractClientCertPath(configuration);
    }

    private static String extractClientKeyPath(BaseClientConfiguration configuration) {
        return TlsConfigHelper.extractClientKeyPath(configuration);
    }

    private static ClientCertReloadConfig buildCertReloadConfig(
            BaseClientConfiguration configuration) {
        return TlsConfigHelper.buildCertReloadConfig(configuration);
    }

    /**
     * Builds the wire-format {@link ConnectionRequest} from a client configuration.
     *
     * <p>The single source of truth for translating a {@code BaseClientConfiguration} into protobuf.
     * Both direct clients ({@link #connectToValkey}) and pooled clients ({@code ClientPool}) build
     * their request here so a field added to the client contract cannot be silently omitted by a pool
     * with its own serializer.
     *
     * <p>Pure over {@code configuration}: it reads no connection state and has no side effects, so it
     * is safe to call before a connection exists (as the pool does at creation).
     *
     * <p>{@code addressResolver} and the IAM {@code credentialsProvider} are NOT part of the request:
     * they are Java callbacks handed to native code as global references, so a caller that supports
     * them must pass them to {@code GlideNativeBridge.createClient} separately.
     */
    public static ConnectionRequest buildConnectionRequest(BaseClientConfiguration configuration) {
        boolean isCluster = configuration instanceof GlideClusterClientConfiguration;
        ServerCredentials credentials = configuration.getCredentials();

        int maxInflightRequests =
                configuration.getInflightRequestsLimit() != null
                        ? configuration.getInflightRequestsLimit()
                        : GlideNativeBridge.getGlideCoreDefaultMaxInflightRequests();
        int requestTimeoutMs =
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

        byte[][] subExact = glide.internal.GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
        byte[][] subPattern = glide.internal.GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
        byte[][] subSharded = glide.internal.GlideCoreClient.EMPTY_2D_BYTE_ARRAY;
        if (configuration.getSubscriptionConfiguration() != null) {
            BaseSubscriptionConfiguration sc = configuration.getSubscriptionConfiguration();
            try {
                if (sc instanceof StandaloneSubscriptionConfiguration) {
                    Map<StandaloneSubscriptionConfiguration.PubSubChannelMode, Set<GlideString>> subs =
                            ((StandaloneSubscriptionConfiguration) sc).getSubscriptions();
                    Set<GlideString> exact =
                            subs.get(StandaloneSubscriptionConfiguration.PubSubChannelMode.EXACT);
                    Set<GlideString> pattern =
                            subs.get(StandaloneSubscriptionConfiguration.PubSubChannelMode.PATTERN);
                    if (exact != null) {
                        subExact = exact.stream().map(GlideString::getBytes).toArray(byte[][]::new);
                    }
                    if (pattern != null) {
                        subPattern = pattern.stream().map(GlideString::getBytes).toArray(byte[][]::new);
                    }
                } else if (sc instanceof ClusterSubscriptionConfiguration) {
                    Map<ClusterSubscriptionConfiguration.PubSubClusterChannelMode, Set<GlideString>> subs =
                            ((ClusterSubscriptionConfiguration) sc).getSubscriptions();
                    Set<GlideString> exact =
                            subs.get(ClusterSubscriptionConfiguration.PubSubClusterChannelMode.EXACT);
                    Set<GlideString> pattern =
                            subs.get(ClusterSubscriptionConfiguration.PubSubClusterChannelMode.PATTERN);
                    Set<GlideString> sharded =
                            subs.get(ClusterSubscriptionConfiguration.PubSubClusterChannelMode.SHARDED);
                    if (exact != null) {
                        subExact = exact.stream().map(GlideString::getBytes).toArray(byte[][]::new);
                    }
                    if (pattern != null) {
                        subPattern = pattern.stream().map(GlideString::getBytes).toArray(byte[][]::new);
                    }
                    if (sharded != null) {
                        subSharded = sharded.stream().map(GlideString::getBytes).toArray(byte[][]::new);
                    }
                }
            } catch (Throwable ignore) {
            }
        }

        ConnectionRequest.Builder requestBuilder = ConnectionRequest.newBuilder();

        for (glide.api.models.configuration.NodeAddress addr : configuration.getAddresses()) {
            requestBuilder.addAddresses(
                    NodeAddress.newBuilder().setHost(addr.getHost()).setPort(addr.getPort()).build());
        }

        if (configuration.isUseTLS()) {
            requestBuilder.setTlsMode(insecureTls ? TlsMode.InsecureTls : TlsMode.SecureTls);
        } else {
            requestBuilder.setTlsMode(TlsMode.NoTls);
        }

        if (credentials != null) {
            AuthenticationInfo.Builder authBuilder = AuthenticationInfo.newBuilder();
            if (credentials.getUsername() != null) {
                authBuilder.setUsername(credentials.getUsername());
            }
            if (credentials.getPassword() != null) {
                authBuilder.setPassword(credentials.getPassword());
            }
            if (credentials.getIamConfig() != null) {
                IamAuthConfig iamConfig = credentials.getIamConfig();
                IamCredentials.Builder iamBuilder = IamCredentials.newBuilder();
                iamBuilder.setClusterName(iamConfig.getClusterName());
                iamBuilder.setRegion(iamConfig.getRegion());
                if (iamConfig.getService() == glide.api.models.configuration.ServiceType.ELASTICACHE) {
                    iamBuilder.setServiceType(ServiceType.ELASTICACHE);
                } else if (iamConfig.getService() == glide.api.models.configuration.ServiceType.MEMORYDB) {
                    iamBuilder.setServiceType(ServiceType.MEMORYDB);
                }
                if (iamConfig.getRefreshIntervalSeconds() != null) {
                    iamBuilder.setRefreshIntervalSeconds(iamConfig.getRefreshIntervalSeconds());
                }
                authBuilder.setIamCredentials(iamBuilder.build());
            }
            requestBuilder.setAuthenticationInfo(authBuilder.build());
        }

        requestBuilder.setClusterModeEnabled(isCluster);

        if (isCluster) {
            GlideClusterClientConfiguration clusterConfig =
                    (GlideClusterClientConfiguration) configuration;
            AdvancedGlideClusterClientConfiguration advancedConfig =
                    clusterConfig.getAdvancedConfiguration();
            if (advancedConfig != null) {
                requestBuilder.setRefreshTopologyFromInitialNodes(
                        advancedConfig.isRefreshTopologyFromInitialNodes());
                PeriodicChecksConfig periodicChecks = advancedConfig.getPeriodicChecks();
                if (periodicChecks instanceof PeriodicChecksStatus) {
                    PeriodicChecksStatus status = (PeriodicChecksStatus) periodicChecks;
                    if (status == PeriodicChecksStatus.DISABLED) {
                        requestBuilder.setPeriodicChecksDisabled(PeriodicChecksDisabled.newBuilder().build());
                    }
                } else if (periodicChecks instanceof PeriodicChecksManualInterval) {
                    PeriodicChecksManualInterval manualInterval =
                            (PeriodicChecksManualInterval) periodicChecks;
                    requestBuilder.setPeriodicChecksManualInterval(
                            connection_request.ConnectionRequestOuterClass.PeriodicChecksManualInterval
                                    .newBuilder()
                                    .setDurationInSec(manualInterval.getDurationInSec())
                                    .build());
                }
            }
            if (clusterConfig.getRecoveryRequestsQueueSize() != null) {
                requestBuilder.setRecoveryRequestsQueueSize(clusterConfig.getRecoveryRequestsQueueSize());
            }
        }

        requestBuilder.setRequestTimeout(requestTimeoutMs);
        requestBuilder.setConnectionTimeout(connectionTimeoutMs);
        requestBuilder.setInflightRequestsLimit(maxInflightRequests);

        ClientCircuitBreakerConfiguration cbConfig =
                configuration.getClientCircuitBreakerConfiguration();
        if (cbConfig != null) {
            if (cbConfig.getWindowSizeMs() <= 0) {
                throw new IllegalArgumentException("windowSizeMs must be positive");
            }
            if (cbConfig.getFailureRateThreshold() <= 0.0f || cbConfig.getFailureRateThreshold() > 1.0f) {
                throw new IllegalArgumentException(
                        "failureRateThreshold must be between 0.0 (exclusive) and 1.0 (inclusive)");
            }
            if (cbConfig.getMinErrors() <= 0) {
                throw new IllegalArgumentException("minErrors must be positive");
            }
            if (cbConfig.getOpenTimeoutMs() <= 0) {
                throw new IllegalArgumentException("openTimeoutMs must be positive");
            }
            if (cbConfig.getConsecutiveSuccesses() <= 0) {
                throw new IllegalArgumentException("consecutiveSuccesses must be positive");
            }
            requestBuilder.setClientCircuitBreaker(
                    ClientCircuitBreakerConfig.newBuilder()
                            .setWindowSizeMs(cbConfig.getWindowSizeMs())
                            .setFailureRateThreshold(cbConfig.getFailureRateThreshold())
                            .setMinErrors(cbConfig.getMinErrors())
                            .setOpenTimeoutMs(cbConfig.getOpenTimeoutMs())
                            .setCountTimeouts(cbConfig.isCountTimeouts())
                            .setConsecutiveSuccesses(cbConfig.getConsecutiveSuccesses())
                            .build());
        }

        requestBuilder.setReadFrom(mapReadFrom(configuration.getReadFrom()));

        String clientAz = resolveClientAz(configuration);
        if (clientAz != null) {
            requestBuilder.setClientAz(clientAz);
        }
        if (configuration.getClientName() != null) {
            requestBuilder.setClientName(configuration.getClientName());
        }
        requestBuilder.setLibName(
                ClientLibraryNameResolver.resolve(
                        configuration.getLibName(), configuration.getClientInfoTag()));
        requestBuilder.setLazyConnect(configuration.isLazyConnect());

        if (configuration.getDatabaseId() != null) {
            requestBuilder.setDatabaseId(configuration.getDatabaseId());
        }

        if (protocolName != null) {
            if ("RESP2".equals(protocolName)) {
                requestBuilder.setProtocol(ProtocolVersion.RESP2);
            } else if ("RESP3".equals(protocolName)) {
                requestBuilder.setProtocol(ProtocolVersion.RESP3);
            }
        }

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

        byte[] rootCerts = extractRootCertificates(configuration);
        if (rootCerts != null) {
            requestBuilder.addRootCerts(com.google.protobuf.ByteString.copyFrom(rootCerts));
        }
        byte[] clientCert = extractClientCertificate(configuration);
        if (clientCert != null) {
            requestBuilder.setClientCert(com.google.protobuf.ByteString.copyFrom(clientCert));
        }
        byte[] clientKey = extractClientKey(configuration);
        if (clientKey != null) {
            requestBuilder.setClientKey(com.google.protobuf.ByteString.copyFrom(clientKey));
        }

        String clientCertPath = extractClientCertPath(configuration);
        String clientKeyPath = extractClientKeyPath(configuration);
        ClientCertReloadConfig certReloadConfig = buildCertReloadConfig(configuration);
        if (clientCertPath != null && clientKeyPath != null) {
            requestBuilder.setClientCertPath(clientCertPath);
            requestBuilder.setClientKeyPath(clientKeyPath);
            if (certReloadConfig != null) {
                requestBuilder.setCertReload(certReloadConfig);
            }
        }

        if (subExact.length > 0 || subPattern.length > 0 || subSharded.length > 0) {
            PubSubSubscriptions.Builder subBuilder = PubSubSubscriptions.newBuilder();
            if (subExact.length > 0) {
                PubSubChannelsOrPatterns.Builder exactBuilder = PubSubChannelsOrPatterns.newBuilder();
                for (byte[] channel : subExact) {
                    exactBuilder.addChannelsOrPatterns(com.google.protobuf.ByteString.copyFrom(channel));
                }
                subBuilder.putChannelsOrPatternsByType(
                        PubSubChannelType.Exact.getNumber(), exactBuilder.build());
            }
            if (subPattern.length > 0) {
                PubSubChannelsOrPatterns.Builder patternBuilder = PubSubChannelsOrPatterns.newBuilder();
                for (byte[] pattern : subPattern) {
                    patternBuilder.addChannelsOrPatterns(com.google.protobuf.ByteString.copyFrom(pattern));
                }
                subBuilder.putChannelsOrPatternsByType(
                        PubSubChannelType.Pattern.getNumber(), patternBuilder.build());
            }
            if (isCluster && subSharded.length > 0) {
                PubSubChannelsOrPatterns.Builder shardedBuilder = PubSubChannelsOrPatterns.newBuilder();
                for (byte[] sharded : subSharded) {
                    shardedBuilder.addChannelsOrPatterns(com.google.protobuf.ByteString.copyFrom(sharded));
                }
                subBuilder.putChannelsOrPatternsByType(
                        PubSubChannelType.Sharded.getNumber(), shardedBuilder.build());
            }
            requestBuilder.setPubsubSubscriptions(subBuilder.build());
        }

        AdvancedBaseClientConfiguration advanced = configuration.getAdvancedConfiguration();
        if (advanced != null && advanced.getTcpNoDelay() != null) {
            requestBuilder.setTcpNodelay(advanced.getTcpNoDelay());
        }
        if (advanced != null && advanced.getPubsubReconciliationIntervalMs() != null) {
            requestBuilder.setPubsubReconciliationIntervalMs(
                    advanced.getPubsubReconciliationIntervalMs());
        }

        if (configuration instanceof GlideClientConfiguration) {
            GlideClientConfiguration standaloneConfig = (GlideClientConfiguration) configuration;
            if (standaloneConfig.isReadOnly()) {
                requestBuilder.setReadOnly(true);
            }
            NodeDiscoveryMode mode = standaloneConfig.getNodeDiscoveryMode();
            if (mode == NodeDiscoveryMode.STATIC) {
                requestBuilder.setNodeDiscoveryMode(
                        connection_request.ConnectionRequestOuterClass.NodeDiscoveryMode.Static);
            } else if (mode == NodeDiscoveryMode.DISCOVER_ALL) {
                requestBuilder.setNodeDiscoveryMode(
                        connection_request.ConnectionRequestOuterClass.NodeDiscoveryMode.DiscoverAll);
            }
        }

        if (configuration.getCompressionConfiguration() != null) {
            CompressionConfiguration cc = configuration.getCompressionConfiguration();
            connection_request.ConnectionRequestOuterClass.CompressionConfig.Builder compressionBuilder =
                    connection_request.ConnectionRequestOuterClass.CompressionConfig.newBuilder();
            compressionBuilder.setEnabled(cc.isEnabled());
            compressionBuilder.setBackend(
                    cc.getBackend() == CompressionBackend.LZ4
                            ? connection_request.ConnectionRequestOuterClass.CompressionBackend.LZ4
                            : connection_request.ConnectionRequestOuterClass.CompressionBackend.ZSTD);
            compressionBuilder.setMinCompressionSize(cc.getMinCompressionSize());
            if (cc.getCompressionLevel() != null) {
                compressionBuilder.setCompressionLevel(cc.getCompressionLevel());
            }
            if (cc.getMaxDecompressedSize() != null) {
                compressionBuilder.setMaxDecompressedSize(cc.getMaxDecompressedSize());
            }
            requestBuilder.setCompressionConfig(compressionBuilder.build());
        }

        ClientSideCache clientSideCache = configuration.getClientSideCache();
        if (clientSideCache != null) {
            connection_request.ConnectionRequestOuterClass.ClientSideCache.Builder cacheBuilder =
                    connection_request.ConnectionRequestOuterClass.ClientSideCache.newBuilder();
            cacheBuilder.setCacheId(clientSideCache.getCacheId());
            cacheBuilder.setMaxCacheKb(clientSideCache.getMaxCacheKb());
            cacheBuilder.setEnableMetrics(clientSideCache.isEnableMetrics());
            cacheBuilder.setServerAssisted(clientSideCache.isServerAssisted());
            cacheBuilder.setEntryTtlMs(clientSideCache.getEntryTtlMs());
            if (clientSideCache.getEvictionPolicy() != null) {
                switch (clientSideCache.getEvictionPolicy()) {
                    case LRU:
                        cacheBuilder.setEvictionPolicy(
                                connection_request.ConnectionRequestOuterClass.EvictionPolicy.LRU);
                        break;
                    case LFU:
                        cacheBuilder.setEvictionPolicy(
                                connection_request.ConnectionRequestOuterClass.EvictionPolicy.LFU);
                        break;
                }
            }
            requestBuilder.setClientSideCache(cacheBuilder.build());
        }

        return requestBuilder.build();
    }

    /**
     * Maps a client {@link glide.api.models.configuration.ReadFrom} strategy onto its protobuf
     * counterpart.
     *
     * <p>Note that within this class the unqualified name {@code ReadFrom} refers to the protobuf
     * enum, hence the fully-qualified parameter type.
     *
     * @throws ConfigurationError if the strategy has no protobuf mapping. This makes a newly added
     *     strategy fail loudly rather than silently defaulting to {@code Primary}. A {@code
     *     GlideException} subtype is required: this runs from {@code buildConnectionRequest}, which
     *     on the direct-client path executes inside {@code connectToValkey}'s async body, whose
     *     handler rethrows {@code GlideException} unchanged but relabels anything else as a {@code
     *     ClosingException} — reporting a config mistake as a connection failure.
     */
    public static ReadFrom mapReadFrom(glide.api.models.configuration.ReadFrom readFrom) {
        switch (readFrom) {
            case PRIMARY:
                return ReadFrom.Primary;
            case PREFER_REPLICA:
                return ReadFrom.PreferReplica;
            case AZ_AFFINITY:
                return ReadFrom.AZAffinity;
            case AZ_AFFINITY_REPLICAS_AND_PRIMARY:
                return ReadFrom.AZAffinityReplicasAndPrimary;
            case ALL_NODES:
                return ReadFrom.AllNodes;
            case AZ_AFFINITY_ALL_NODES:
                return ReadFrom.AZAffinityAllNodes;
        }
        throw new ConfigurationError("Unsupported ReadFrom strategy: " + readFrom);
    }

    /**
     * Rejects an AZ-affinity read strategy that has no {@code clientAZ} to target.
     *
     * <p>Without this check the core silently downgrades the strategy to {@code PreferReplica}, so
     * reads would land on arbitrary nodes while the configuration suggested otherwise.
     *
     * <p>Called synchronously from {@code BaseClient.createClient}, alongside the PubSub/RESP2 check,
     * and from {@code ClientPool.create} ahead of its connectivity probe, so callers on either path
     * see a direct throw rather than an {@code ExecutionException} or a probe failure.
     *
     * @throws ConfigurationError if an AZ-affinity strategy is selected without a {@code clientAZ}.
     */
    public static void validateClientAz(BaseClientConfiguration configuration) {
        glide.api.models.configuration.ReadFrom readFrom = configuration.getReadFrom();
        if (!readFrom.requiresClientAz()) {
            return;
        }
        if (resolveClientAz(configuration) == null) {
            throw new ConfigurationError("clientAZ must be set when readFrom is set to " + readFrom);
        }
    }

    /**
     * The {@code clientAZ} as it should reach the core: trimmed, or null when absent or blank.
     *
     * <p>The core compares availability zones with exact equality and never trims ({@code
     * standalone_client.rs}, {@code connections_container.rs}), so a padded value such as {@code "
     * us-east-1a "} — easily produced by {@code getenv} or a file read — would satisfy validation,
     * engage the strategy, match no node, and silently spread reads cluster-wide. No real
     * availability-zone name carries surrounding whitespace, so trimming cannot break a value that
     * works today.
     *
     * <p>The shared request builder normalizes through here so no caller can forward a raw value.
     */
    public static String resolveClientAz(BaseClientConfiguration configuration) {
        String clientAz = configuration.getClientAZ();
        if (clientAz == null) {
            return null;
        }
        String trimmed = clientAz.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
