/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.OpenTelemetry;
import glide.ffi.resolvers.NativeUtils;
import glide.ffi.resolvers.OpenTelemetryResolver;
import glide.internal.protocol.*;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * GLIDE core client transport using JNI. Replaces UDS-based communication. Provides direct JNI
 * access to glide-core with all routing and performance optimizations.
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

    private static final java.util.concurrent.ConcurrentHashMap<
                    Long, java.lang.ref.WeakReference<glide.api.BaseClient>>
            clients = new java.util.concurrent.ConcurrentHashMap<>();

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

    /** Request timeout in milliseconds for this client. */
    private final int requestTimeoutMs;

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

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
        private byte[][] subExact = new byte[0][];
        private byte[][] subPattern = new byte[0][];
        private byte[][] subSharded = new byte[0][];
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
            this.subExact = exact != null ? exact : new byte[0][];
            this.subPattern = pattern != null ? pattern : new byte[0][];
            this.subSharded = sharded != null ? sharded : new byte[0][];
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

        // Package-private getters for JNI access
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

        // Store the request timeout for this client instance
        this.requestTimeoutMs = config.getRequestTimeoutMs();

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
                            this.maxInflightRequests);
        } catch (RuntimeException e) {
            // Propagate the exception from the native layer with proper context
            String errorMsg = e.getMessage();
            if (errorMsg != null
                    && (errorMsg.contains("Connection refused")
                            || errorMsg.contains("Failed to create client"))) {
                throw e; // Already has proper message from Rust
            }
            // Wrap with more context if needed
            throw new RuntimeException("Failed to create client: " + errorMsg, e);
        }

        if (handle == 0) {
            String errorMsg = "Failed to create client - Connection refused";
            throw new RuntimeException(errorMsg);
        }

        this.nativeClientHandle.set(handle);

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
    public GlideCoreClient(long existingHandle, int maxInflight, int requestTimeout) {
        if (existingHandle == 0) {
            throw new IllegalArgumentException("Native handle cannot be zero");
        }

        // Store the provided parameters
        this.maxInflightRequests = maxInflight > 0 ? maxInflight : 0; // 0 means use native defaults
        this.requestTimeoutMs = requestTimeout > 0 ? requestTimeout : 5000;

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

        if (config.getCredentials() != null) {
            glide.api.models.configuration.ServerCredentials creds = config.getCredentials();
            result.credentials(creds.getUsername(), creds.getPassword());
        }

        if (config.getRequestTimeout() != null) {
            result.requestTimeout(config.getRequestTimeout());
        }

        // Use default connection timeout since method doesn't exist
        result.connectionTimeout(5000);

        if (config.getProtocol() != null) {
            result.protocol(config.getProtocol());
        }

        if (config.getClientName() != null) {
            result.clientName(config.getClientName());
        }

        if (config.getReconnectStrategy() != null) {
            result.reconnectStrategy(config.getReconnectStrategy());
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

            if (standaloneConfig.getDatabaseId() != null) {
                result.databaseId(standaloneConfig.getDatabaseId());
            }

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

    /** Create a new GlideCoreClient with basic configuration (for compatibility with UDS API). */
    public static GlideCoreClient createClient(Config config) {
        return new GlideCoreClient(config);
    }

    // ==================== COMMAND EXECUTION METHODS ====================

    /** Execute a Valkey command with automatic routing (RECOMMENDED for 95% of commands). */
    public CompletableFuture<Object> execute(String command, String... args) {
        return executeCommand(CommandRequest.auto(command, args));
    }

    /** Execute any server command using the Command object with automatic routing. */
    public CompletableFuture<Object> executeCommand(glide.internal.protocol.Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        return executeCommand(CommandRequest.auto(command.getType(), command.getArgumentsArray()));
    }

    /** Execute a binary command with mixed String/byte[] arguments using automatic routing. */
    public CompletableFuture<Object> executeBinaryCommand(
            BinaryCommandRequest.BinaryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("BinaryCommand cannot be null");
        }
        return executeBinaryCommand(BinaryCommandRequest.auto(command));
    }

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
                correlationId =
                        AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute binary command directly via JNI using protobuf bytes
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
                correlationId =
                        AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute command directly via JNI using protobuf bytes
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
    public CompletableFuture<Object> executeBatchAsync(byte[] batchRequestBytes) {
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
                correlationId =
                        AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute batch directly via JNI
            GlideNativeBridge.executeBatchAsync(handle, batchRequestBytes, correlationId);

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute cluster scan asynchronously using raw protobuf bytes (for compatibility with
     * CommandManager)
     */
    public CompletableFuture<Object> executeClusterScanAsync(byte[] requestBytes) {
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
                correlationId =
                        AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute cluster scan via JNI
            GlideNativeBridge.executeClusterScanAsync(handle, requestBytes, correlationId);

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
            correlationId = AsyncRegistry.register(future, 30000, this.maxInflightRequests, handle);
        } catch (glide.api.models.exceptions.RequestException e) {
            future.completeExceptionally(e);
            return future;
        }

        GlideNativeBridge.updateConnectionPassword(handle, password, immediateAuth, correlationId);
        return future;
    }

    /** Core command execution method using the enhanced routing architecture. */
    private CompletableFuture<Object> executeCommand(CommandRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CommandRequest cannot be null");
        }

        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create OpenTelemetry span if configured (matching UDS pattern)
            long spanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                spanPtr =
                        OpenTelemetryResolver.createLeakedOtelSpan(formatSpanName(request.getCommandName()));
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;

            try {
                // Use special registration for blocking commands with timeout=0 (infinite blocking)
                if (isBlockingCommandWithInfiniteTimeout(request)) {
                    correlationId =
                            AsyncRegistry.registerInfiniteBlockingCommand(
                                    future, this.maxInflightRequests, handle);
                } else {
                    correlationId =
                            AsyncRegistry.register(
                                    future, this.requestTimeoutMs, this.maxInflightRequests, handle);
                }
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute command directly via JNI
            GlideNativeBridge.executeCommandAsync(handle, request.toBytes(), correlationId);

            // Ensure span cleanup on completion
            final long finalSpanPtr = spanPtr;
            if (finalSpanPtr != 0) {
                future.whenComplete(
                        (result, throwable) -> {
                            OpenTelemetryResolver.dropOtelSpan(finalSpanPtr);
                        });
            }

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /** Core binary command execution method supporting mixed String/byte[] arguments. */
    private CompletableFuture<Object> executeBinaryCommand(BinaryCommandRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("BinaryCommandRequest cannot be null");
        }

        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(
                        new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create OpenTelemetry span if configured
            long spanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                spanPtr =
                        OpenTelemetryResolver.createLeakedOtelSpan(formatSpanName(request.getCommandName()));
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId =
                        AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                future.completeExceptionally(e);
                return future;
            }

            // Execute binary command via JNI
            GlideNativeBridge.executeBinaryCommandAsync(handle, request.toBytes(), correlationId);

            // Ensure span cleanup on completion
            final long finalSpanPtr = spanPtr;
            if (finalSpanPtr != 0) {
                future.whenComplete(
                        (result, throwable) -> {
                            OpenTelemetryResolver.dropOtelSpan(finalSpanPtr);
                        });
            }

            return future;

        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
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

    /** Check if a command is a blocking command that may need special handling */
    private static boolean isBlockingCommand(String commandName) {
        if (commandName == null) return false;
        String cmd = commandName.toUpperCase();
        return cmd.equals("BLPOP")
                || cmd.equals("BRPOP")
                || cmd.equals("BLMOVE")
                || cmd.equals("BLMPOP")
                || cmd.equals("BZPOPMIN")
                || cmd.equals("BZPOPMAX")
                || cmd.equals("BZMPOP")
                || cmd.equals("XREAD")
                || cmd.equals("XREADGROUP");
    }

    /** Check if this is a blocking command with timeout=0 (infinite timeout) */
    private boolean isBlockingCommandWithInfiniteTimeout(CommandRequest request) {
        if (request == null || request.getCommandName() == null) {
            return false;
        }

        String cmd = request.getCommandName().toUpperCase();

        // Check if this is a blocking command
        if (!isBlockingCommand(cmd)) {
            return false;
        }

        // For BLPOP, BRPOP, etc., the timeout is the last argument
        List<String> args = request.getArguments();
        if (args.size() > 0) {
            String lastArg = args.get(args.size() - 1);
            try {
                double timeout = Double.parseDouble(lastArg);
                // timeout=0 means block indefinitely
                return timeout == 0.0;
            } catch (NumberFormatException e) {
                // If we can't parse the timeout, assume it's not infinite
                return false;
            }
        }

        return false;
    }

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
            int maxInflightRequests) {
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
                maxInflightRequests);
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
}
