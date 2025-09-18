/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.OpenTelemetry;
import glide.api.models.exceptions.ClosingException;
import glide.ffi.resolvers.NativeUtils;
import glide.ffi.resolvers.OpenTelemetryResolver;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GLIDE core client transport. Provides direct native access to glide-core with all routing and
 * performance optimizations.
 */
public class GlideCoreClient implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    static {
        // Load the native library
        try {
            NativeUtils.loadGlideLib();
        } catch (Exception e) {
            // Use proper logging instead of System.err.println
            glide.api.logging.Logger.log(
                    glide.api.logging.Logger.Level.ERROR,
                    "GlideCoreClient",
                    "Failed to load native library: " + e.getMessage());
            throw new RuntimeException("Failed to load native library", e);
        }
        onNativeInit();
    }

    private static native void onNativeInit();

    private static native void freeNativeBuffer(long id);

    private static final java.util.concurrent.ConcurrentHashMap<
                    Long, java.lang.ref.WeakReference<glide.api.BaseClient>>
            clients = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Empty 2D byte array constant for reuse in various contexts (script params, subPattern, etc.)
     */
    public static final byte[][] EMPTY_2D_BYTE_ARRAY = new byte[0][];

    public static void registerClient(long handle, glide.api.BaseClient client) {
        clients.put(handle, new java.lang.ref.WeakReference<>(client));
    }

    public static void unregisterClient(long handle) {
        clients.remove(handle);
    }

    // Called by native on push (binary-safe)
    private static void onNativePush(long handle, byte[] message, byte[] channel, byte[] pattern) {
        glide.api.models.GlideString msg = glide.api.models.GlideString.of(message);
        glide.api.models.GlideString ch = glide.api.models.GlideString.of(channel);
        glide.api.models.PubSubMessage m =
                (pattern != null && pattern.length > 0)
                        ? new glide.api.models.PubSubMessage(msg, ch, glide.api.models.GlideString.of(pattern))
                        : new glide.api.models.PubSubMessage(msg, ch);
        var ref = clients.get(handle);
        if (ref != null) {
            var c = ref.get();
            if (c != null) c.__enqueuePubSubMessage(m);
        }
    }

    // Register a Java Cleaner to free native memory when the given ByteBuffer is
    // GC'd
    static void registerNativeBufferCleaner(java.nio.ByteBuffer buffer, long id) {
        if (buffer == null || id == 0) return;
        CLEANER.register(
                buffer,
                () -> {
                    try {
                        freeNativeBuffer(id);
                    } catch (Throwable ignored) {
                    }
                });
    }

    /** Handle for the native client resource. */
    private final AtomicLong nativeClientHandle = new AtomicLong(0);

    public long getNativeHandle() {
        return nativeClientHandle.get();
    }

    /** Maximum number of inflight requests allowed for this client. */
    private final int maxInflightRequests;

    public int getMaxInflightRequests() {
        return maxInflightRequests;
    }

    // Removed requestTimeoutMs field - Rust handles all timeouts

    /** Cleanup coordination flag. */
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    /** Cleaner to ensure native cleanup. */
    private final Cleaner.Cleanable cleanable;

    /** Shared state for cleanup coordination. */
    private final NativeState nativeState;

    /** Client configuration. */
    public static class Config {
        private final List<String> addresses;
        private boolean useTls = false;
        private boolean clusterMode = false;
        private boolean insecureTls = false;
        private int requestTimeoutMs = 5000;
        private int connectionTimeoutMs = 5000;
        private String username = null;
        private String password = null;
        private int databaseId = 0;
        private Integer maxInflightRequests = null;
        private Integer nativeDirectMemoryMB = null;
        private glide.api.models.configuration.ProtocolVersion protocol =
                glide.api.models.configuration.ProtocolVersion.RESP3;
        private byte[][] subExact = EMPTY_2D_BYTE_ARRAY;
        private byte[][] subPattern = EMPTY_2D_BYTE_ARRAY;
        private byte[][] subSharded = EMPTY_2D_BYTE_ARRAY;
        private String clientName = null;
        private glide.api.models.configuration.ReadFrom readFrom =
                glide.api.models.configuration.ReadFrom.PRIMARY;
        private String clientAZ = null;
        private boolean lazyConnect = true;
        private Integer reconnectNumRetries = null;
        private Integer reconnectFactor = null;
        private Integer reconnectExponentBase = null;
        private Integer reconnectJitterPercent = null;

        public Config(List<String> addresses) {
            this.addresses = addresses;
        }

        public Config useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        public Config clusterMode(boolean clusterMode) {
            this.clusterMode = clusterMode;
            return this;
        }

        public Config useInsecureTls(boolean insecure) {
            this.insecureTls = insecure;
            return this;
        }

        public Config requestTimeout(int timeoutMs) {
            this.requestTimeoutMs = timeoutMs;
            return this;
        }

        public Config connectionTimeout(int timeoutMs) {
            this.connectionTimeoutMs = timeoutMs;
            return this;
        }

        public Config credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Config databaseId(int databaseId) {
            this.databaseId = databaseId;
            return this;
        }

        public Config maxInflightRequests(int maxInflight) {
            this.maxInflightRequests = maxInflight;
            return this;
        }

        public Config nativeDirectMemoryMB(int mb) {
            this.nativeDirectMemoryMB = mb;
            return this;
        }

        public Config protocol(glide.api.models.configuration.ProtocolVersion protocol) {
            if (protocol != null) this.protocol = protocol;
            return this;
        }

        public Config subscriptions(byte[][] exact, byte[][] pattern, byte[][] sharded) {
            this.subExact = exact != null ? exact : EMPTY_2D_BYTE_ARRAY;
            this.subPattern = pattern != null ? pattern : EMPTY_2D_BYTE_ARRAY;
            this.subSharded = sharded != null ? sharded : EMPTY_2D_BYTE_ARRAY;
            return this;
        }

        public Config clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Config readFrom(glide.api.models.configuration.ReadFrom readFrom) {
            this.readFrom = readFrom != null ? readFrom : glide.api.models.configuration.ReadFrom.PRIMARY;
            return this;
        }

        public Config clientAZ(String clientAZ) {
            this.clientAZ = clientAZ;
            return this;
        }

        public Config lazyConnect(boolean lazy) {
            this.lazyConnect = lazy;
            return this;
        }

        public Config reconnectStrategy(glide.api.models.configuration.BackoffStrategy strategy) {
            if (strategy != null) {
                this.reconnectNumRetries = strategy.getNumOfRetries();
                this.reconnectFactor = strategy.getFactor();
                this.reconnectExponentBase = strategy.getExponentBase();
                this.reconnectJitterPercent = strategy.getJitterPercent();
            }
            return this;
        }

        // Package-private getters for native access
        String[] getAddresses() {
            return addresses.toArray(new String[0]);
        }

        boolean getUseTls() {
            return useTls;
        }

        boolean getClusterMode() {
            return clusterMode;
        }

        int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        String getUsername() {
            return username;
        }

        String getPassword() {
            return password;
        }

        int getDatabaseId() {
            return databaseId;
        }

        glide.api.models.configuration.ProtocolVersion getProtocol() {
            return protocol;
        }

        boolean getInsecureTls() {
            return insecureTls;
        }

        byte[][] getSubExact() {
            return subExact;
        }

        byte[][] getSubPattern() {
            return subPattern;
        }

        byte[][] getSubSharded() {
            return subSharded;
        }

        String getClientName() {
            return clientName;
        }

        Integer getMaxInflightRequests() {
            return maxInflightRequests;
        }

        Integer getNativeDirectMemoryMB() {
            return nativeDirectMemoryMB;
        }

        glide.api.models.configuration.ReadFrom getReadFrom() {
            return readFrom;
        }

        String getClientAZ() {
            return clientAZ;
        }

        boolean getLazyConnect() {
            return lazyConnect;
        }

        int getReconnectNumRetriesOrDefault() {
            return reconnectNumRetries != null ? reconnectNumRetries : 0;
        }

        int getReconnectExponentBaseOrDefault() {
            return reconnectExponentBase != null ? reconnectExponentBase : 0;
        }

        int getReconnectFactorOrDefault() {
            return reconnectFactor != null ? reconnectFactor : 0;
        }

        int getReconnectJitterPercentOrDefault() {
            return reconnectJitterPercent != null ? reconnectJitterPercent : -1;
        }
    }

    /** Create a new GlideClient with the specified configuration */
    public GlideCoreClient(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (config.addresses.isEmpty()) {
            throw new IllegalArgumentException("At least one address must be provided");
        }

        // Store the computed inflight limit for this client instance
        this.maxInflightRequests = computeMaxInflight(config);

        // Request timeout is passed to Rust for its timeout handling

        // Create client with simplified parameters
        long handle;
        try {
            handle =
                    createClient(
                            config.getAddresses(),
                            config.getDatabaseId(),
                            config.getUsername(),
                            config.getPassword(),
                            config.getUseTls(),
                            config.getInsecureTls(),
                            config.getClusterMode(),
                            config.getRequestTimeoutMs(),
                            config.getConnectionTimeoutMs(),
                            this.maxInflightRequests,
                            config.getReadFrom() != null ? config.getReadFrom().name() : null,
                            config.getClientAZ(),
                            config.getLazyConnect(),
                            config.getClientName(),
                            config.getProtocol() != null ? config.getProtocol().name() : null,
                            config.getReconnectNumRetriesOrDefault(),
                            config.getReconnectFactorOrDefault(),
                            config.getReconnectExponentBaseOrDefault(),
                            config.getReconnectJitterPercentOrDefault(),
                            config.getSubExact(),
                            config.getSubPattern(),
                            config.getSubSharded());
        } catch (RuntimeException e) {
            // Propagate the exception from the native layer with proper context
            String errorMsg = e.getMessage();
            if (errorMsg != null
                    && (errorMsg.contains("Connection refused")
                            || errorMsg.contains("Failed to create client"))) {
                throw e; // Already has proper message from Rust
            }
            // Wrap with more context if needed
            throw new ClosingException("Failed to create client: " + errorMsg);
        }

        if (handle == 0) {
            String errorMsg = "Failed to create client - Connection refused";
            throw new ClosingException(errorMsg);
        }

        this.nativeClientHandle.set(handle);

        // Register for PubSub push delivery
        try {
            registerClient(handle, null); // will be replaced by BaseClient after it is constructed
        } catch (Throwable ignore) {
        }

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(handle);

        // Register cleanup action with Cleaner - modern replacement for finalize()
        this.cleanable = CLEANER.register(this, new CleanupAction(this.nativeState));
    }

    /** Constructor accepting BaseClientConfiguration (for compatibility with existing API) */
    public GlideCoreClient(glide.api.models.configuration.BaseClientConfiguration configuration) {
        this(convertFromBaseClientConfiguration(configuration));
    }

    /** Convenience constructor for simple host:port connections */
    public GlideCoreClient(String host, int port) {
        this(new Config(Arrays.asList(host + ":" + port)));
    }

    /** Constructor that wraps an existing native client handle (for BaseClient integration) */
    public GlideCoreClient(long existingHandle, int maxInflight) {
        if (existingHandle == 0) {
            throw new IllegalArgumentException("Native handle cannot be zero");
        }

        // Store the provided parameters
        this.maxInflightRequests = maxInflight > 0 ? maxInflight : 0; // 0 means use native defaults

        // Use the existing native handle
        this.nativeClientHandle.set(existingHandle);

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(existingHandle);

        // Register cleanup action with Cleaner - but don't double-close since handle is managed
        // externally
        this.cleanable = CLEANER.register(this, new CleanupAction(this.nativeState));
    }

    /** Convert BaseClientConfiguration to our internal Config format */
    private static Config convertFromBaseClientConfiguration(
            glide.api.models.configuration.BaseClientConfiguration config) {
        // Extract addresses from NodeAddress objects
        List<String> addresses =
                config.getAddresses().stream()
                        .map(addr -> addr.getHost() + ":" + addr.getPort())
                        .collect(java.util.stream.Collectors.toList());

        Config result = new Config(addresses);

        // Map configuration fields
        result.useTls(config.isUseTLS());
        result.useInsecureTls(resolveInsecureTls(config));

        if (config.getCredentials() != null) {
            glide.api.models.configuration.ServerCredentials creds = config.getCredentials();
            result.credentials(creds.getUsername(), creds.getPassword());
        }

        if (config.getRequestTimeout() != null) {
            result.requestTimeout(config.getRequestTimeout());
        }

        result.connectionTimeout(resolveConnectionTimeout(config));

        if (config.getProtocol() != null) {
            result.protocol(config.getProtocol());
        }

        if (config.getClientName() != null) {
            result.clientName(config.getClientName());
        }

        if (config.getReconnectStrategy() != null) {
            result.reconnectStrategy(config.getReconnectStrategy());
        }

        if (config.getDatabaseId() != null) {
            result.databaseId(config.getDatabaseId());
        }

        // Handle cluster vs standalone specific configurations
        if (config instanceof glide.api.models.configuration.GlideClusterClientConfiguration) {
            glide.api.models.configuration.GlideClusterClientConfiguration clusterConfig =
                    (glide.api.models.configuration.GlideClusterClientConfiguration) config;
            result.clusterMode(true);

            if (clusterConfig.getReadFrom() != null) {
                result.readFrom(clusterConfig.getReadFrom());
            }
        } else if (config instanceof glide.api.models.configuration.GlideClientConfiguration) {
            glide.api.models.configuration.GlideClientConfiguration standaloneConfig =
                    (glide.api.models.configuration.GlideClientConfiguration) config;
            result.clusterMode(false);

            if (standaloneConfig.getReadFrom() != null) {
                result.readFrom(standaloneConfig.getReadFrom());
            }
        }

        return result;
    }

    /** Create a new client connection to Valkey asynchronously. */
    public static CompletableFuture<GlideCoreClient> createAsync(Config config) {
        return CompletableFuture.supplyAsync(() -> new GlideCoreClient(config));
    }

    /** Create a new GlideCoreClient with basic configuration. */
    public static GlideCoreClient createClient(Config config) {
        return new GlideCoreClient(config);
    }

    // ==================== COMMAND EXECUTION METHODS ====================

    /**
     * Execute binary command asynchronously using raw protobuf bytes (for compatibility with
     * CommandManager)
     */
    public CompletableFuture<Object> executeBinaryCommandAsync(byte[] requestBytes) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                // Rust handles all timeout logic - Java just waits for response
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute binary command directly using protobuf bytes
            GlideNativeBridge.executeBinaryCommandAsync(handle, requestBytes, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute command asynchronously using raw protobuf bytes (for compatibility with CommandManager)
     */
    public CompletableFuture<Object> executeCommandAsync(byte[] requestBytes) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                // Rust handles all timeout logic - Java just waits for response
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute command directly using protobuf bytes
            GlideNativeBridge.executeCommandAsync(handle, requestBytes, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute batch asynchronously using raw protobuf bytes (for compatibility with CommandManager)
     */
    public CompletableFuture<Object> executeBatchAsync(
            byte[] batchRequestBytes, boolean expectUtf8Response) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute batch directly
            GlideNativeBridge.executeBatchAsync(
                    handle, batchRequestBytes, expectUtf8Response, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /** Execute cluster scan asynchronously with proper cursor lifecycle management */
    public CompletableFuture<Object> executeClusterScanAsync(
            String cursorId,
            String matchPattern,
            long count,
            String objectType,
            boolean expectUtf8Response) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute cluster scan with proper cursor management via dedicated bridge
            GlideNativeBridge.executeClusterScanAsync(
                    handle, cursorId, matchPattern, count, objectType, expectUtf8Response, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /** Update connection password (for compatibility with CommandManager) */
    public CompletableFuture<String> updateConnectionPassword(
            String password, boolean immediateAuth) {
        long handle = nativeClientHandle.get();
        if (handle == 0) {
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(new glide.api.models.exceptions.ClosingException("Client is closed"));
            return f;
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        long correlationId;
        try {
            correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
        } catch (glide.api.models.exceptions.RequestException e) {
            future.completeExceptionally(e);
            return future;
        }

        GlideNativeBridge.updateConnectionPassword(handle, password, immediateAuth, correlationId);
        return future;
    }

    // ==================== CLIENT STATUS AND INFO METHODS ====================

    /** Check if client is connected. */
    public boolean isConnected() {
        long handle = nativeClientHandle.get();
        return handle != 0 && isConnected(handle);
    }

    /** Get client information for debugging and monitoring. */
    public String getClientInfo() {
        long handle = nativeClientHandle.get();
        if (handle == 0) {
            return "Client is closed";
        }
        return getClientInfo(handle);
    }

    /** Get the number of pending async operations. */
    public int getPendingOperations() {
        return AsyncRegistry.getPendingCount();
    }

    /** Health check to detect if client is working properly */
    public boolean isHealthy() {
        return isConnected() && AsyncRegistry.getPendingCount() < 1000;
    }

    // ==================== HELPER METHODS ====================

    private static int computeMaxInflight(Config config) {
        if (config.getMaxInflightRequests() != null && config.getMaxInflightRequests() > 0) {
            return config.getMaxInflightRequests();
        }

        String env = System.getenv("GLIDE_MAX_INFLIGHT_REQUESTS");
        if (env != null) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
            }
        }

        String prop = System.getProperty("glide.maxInflightRequests");
        if (prop != null) {
            try {
                int v = Integer.parseInt(prop.trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
            }
        }

        return 0; // 0 means "use native/core defaults"
    }

    private static int computeNativeDirectMemoryMB(Config config) {
        if (config.getNativeDirectMemoryMB() != null && config.getNativeDirectMemoryMB() > 0) {
            return config.getNativeDirectMemoryMB();
        }

        // Simple default without memory management intervention
        return 512; // 512MB default - let users configure as needed
    }

    private static int resolveConnectionTimeout(
            glide.api.models.configuration.BaseClientConfiguration config) {
        glide.api.models.configuration.AdvancedBaseClientConfiguration advanced =
                extractAdvancedConfiguration(config);
        if (advanced != null && advanced.getConnectionTimeout() != null) {
            return advanced.getConnectionTimeout();
        }
        return (int) GlideNativeBridge.getGlideCoreDefaultTimeoutMs();
    }

    private static boolean resolveInsecureTls(
            glide.api.models.configuration.BaseClientConfiguration config) {
        glide.api.models.configuration.AdvancedBaseClientConfiguration advanced =
                extractAdvancedConfiguration(config);
        if (advanced == null) {
            return false;
        }
        glide.api.models.configuration.TlsAdvancedConfiguration tlsConfig =
                advanced.getTlsAdvancedConfiguration();
        if (tlsConfig != null && tlsConfig.isUseInsecureTLS()) {
            if (!config.isUseTLS()) {
                throw new glide.api.models.exceptions.ConfigurationError(
                        "`useInsecureTLS` cannot be enabled when `useTLS` is disabled.");
            }
            return true;
        }
        return false;
    }

    private static glide.api.models.configuration.AdvancedBaseClientConfiguration
            extractAdvancedConfiguration(glide.api.models.configuration.BaseClientConfiguration config) {
        if (config instanceof glide.api.models.configuration.GlideClientConfiguration) {
            return ((glide.api.models.configuration.GlideClientConfiguration) config)
                    .getAdvancedConfiguration();
        }
        if (config instanceof glide.api.models.configuration.GlideClusterClientConfiguration) {
            return ((glide.api.models.configuration.GlideClusterClientConfiguration) config)
                    .getAdvancedConfiguration();
        }
        return null;
    }

    // Removed blocking command detection - Rust handles all timeout logic

    private static String formatSpanName(String commandName) {
        if (commandName == null || commandName.isEmpty()) return "Command";
        String primary = commandName;
        int space = commandName.indexOf(' ');
        if (space > 0) {
            primary = commandName.substring(0, space);
        }
        if (primary.length() == 1) {
            return primary.toUpperCase();
        }
        String lower = primary.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // ==================== RESOURCE MANAGEMENT ====================

    /** Close the client and cleanup all resources */
    @Override
    public void close() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            // Cleanup already in progress or completed
            return;
        }

        long handle = nativeClientHandle.getAndSet(0);
        if (handle != 0) {
            try {
                unregisterClient(handle);
            } catch (Throwable ignore) {
            }
            try {
                // Clean up per-client inflight tracking
                AsyncRegistry.cleanupClient(handle);
                closeClient(handle);
            } finally {
                // Reset AsyncRegistry only when no clients remain (test isolation / full shutdown)
                if (clients.isEmpty()) {
                    AsyncRegistry.reset();
                }
            }
        }

        // Also trigger the cleaner cleanup (safe to call multiple times)
        cleanable.clean();
    }

    /** Shared state for cleanup coordination */
    private static class NativeState {
        volatile long nativePtr;

        NativeState(long nativePtr) {
            this.nativePtr = nativePtr;
        }
    }

    /** Cleanup action for the Cleaner */
    private static class CleanupAction implements Runnable {
        private final NativeState nativeState;

        CleanupAction(NativeState nativeState) {
            this.nativeState = nativeState;
        }

        @Override
        public void run() {
            long ptr = nativeState.nativePtr;
            if (ptr != 0) {
                nativeState.nativePtr = 0;
                // Clean up per-client inflight tracking
                AsyncRegistry.cleanupClient(ptr);
                closeClient(ptr);
            }
        }
    }

    // ==================== NATIVE METHODS ====================

    /** Create client with enhanced routing support */
    private static long createClient(
            String[] addresses,
            int databaseId,
            String username,
            String password,
            boolean useTls,
            boolean insecureTls,
            boolean clusterMode,
            int requestTimeoutMs,
            int connectionTimeoutMs,
            int maxInflightRequests,
            String readFrom,
            String clientAz,
            boolean lazyConnect,
            String clientName,
            String protocol,
            int reconnectNumRetries,
            int reconnectFactor,
            int reconnectExponentBase,
            int reconnectJitterPercent,
            byte[][] subExact,
            byte[][] subPattern,
            byte[][] subSharded) {
        return GlideNativeBridge.createClient(
                addresses,
                databaseId,
                username,
                password,
                useTls,
                insecureTls,
                clusterMode,
                requestTimeoutMs,
                connectionTimeoutMs,
                maxInflightRequests,
                readFrom,
                clientAz,
                lazyConnect,
                clientName,
                protocol,
                reconnectNumRetries,
                reconnectFactor,
                reconnectExponentBase,
                reconnectJitterPercent,
                subExact,
                subPattern,
                subSharded);
    }

    /** Check if the native client is connected */
    private static boolean isConnected(long clientPtr) {
        return GlideNativeBridge.isConnected(clientPtr);
    }

    /** Get client information from native layer */
    private static String getClientInfo(long clientPtr) {
        return GlideNativeBridge.getClientInfo(clientPtr);
    }

    /** Close and release a native client */
    private static void closeClient(long clientPtr) {
        GlideNativeBridge.closeClient(clientPtr);
    }

    /** Execute script via native invoke_script path */
    public CompletableFuture<Object> executeScriptAsync(
            String hash,
            byte[][] keys,
            byte[][] args,
            boolean hasRoute,
            int routeType,
            String routeParam,
            boolean expectUtf8Response) {
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId = AsyncRegistry.register(future, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            GlideNativeBridge.executeScriptAsync(
                    handle,
                    correlationId,
                    hash,
                    keys != null ? keys : EMPTY_2D_BYTE_ARRAY,
                    args != null ? args : EMPTY_2D_BYTE_ARRAY,
                    hasRoute,
                    routeType,
                    routeParam,
                    expectUtf8Response);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}
