package glide.internal;

import glide.internal.protocol.Command;
import glide.internal.protocol.CommandInterface;
import glide.internal.protocol.CommandRequest;
import glide.internal.protocol.BinaryCommand;
import glide.internal.protocol.BinaryCommandRequest;
import glide.internal.protocol.BatchRequest;
import glide.internal.AsyncRegistry;
import glide.internal.GlideNativeBridge;
import glide.api.OpenTelemetry;
import glide.ffi.resolvers.OpenTelemetryResolver;
import glide.api.models.configuration.RequestRoutingConfiguration;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GLIDE core client transport. Async API backed by glide-core.
 */
public class GlideCoreClient implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    static {
        // Load the native library
        try {
            System.loadLibrary("valkey_glide");
        } catch (UnsatisfiedLinkError e) {
            // Fallback logging compatible across Logger variants
            System.err.println("[ERROR] GlideCoreClient: Failed to load native library: " + e.getMessage());
            throw e;
        }
        onNativeInit();
    }

    private static native void onNativeInit();

    private static final java.util.concurrent.ConcurrentHashMap<Long, java.lang.ref.WeakReference<glide.api.BaseClient>> clients = new java.util.concurrent.ConcurrentHashMap<>();
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
        glide.api.models.PubSubMessage m = (pattern != null && pattern.length > 0)
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
    public long getNativeHandle() { return nativeClientHandle.get(); }
    
    /** Maximum number of inflight requests allowed for this client. */
    private final int maxInflightRequests;
    public int getMaxInflightRequests() { return maxInflightRequests; }
    
    /** Request timeout in milliseconds for this client. */
    private final int requestTimeoutMs;
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    
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
        private Integer maxInflightRequests = null; // forwarded to core; used for sizing callback queue
        private Integer nativeDirectMemoryMB = null; // JNI safety cap for DirectByteBuffer
        private glide.api.models.configuration.ProtocolVersion protocol = glide.api.models.configuration.ProtocolVersion.RESP3;
        private byte[][] subExact = new byte[0][];
        private byte[][] subPattern = new byte[0][];
        private byte[][] subSharded = new byte[0][];
        private String clientName = null;

        // Routing strategy configuration
        private glide.api.models.configuration.ReadFrom readFrom = glide.api.models.configuration.ReadFrom.PRIMARY;
        private String clientAZ = null;
        private boolean lazyConnect = true;

        // Reconnect/backoff strategy (nullable => not set)
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

        // Convenience setters to reduce misconfiguration for AZ strategies
        public Config readFromAzAffinity(String az) {
            this.readFrom = glide.api.models.configuration.ReadFrom.AZ_AFFINITY;
            this.clientAZ = az;
            return this;
        }

        public Config readFromAzAffinityReplicasAndPrimary(String az) {
            this.readFrom = glide.api.models.configuration.ReadFrom.AZ_AFFINITY_REPLICAS_AND_PRIMARY;
            this.clientAZ = az;
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
        String[] getAddresses() { return addresses.toArray(new String[0]); }
        boolean getUseTls() { return useTls; }
        boolean getClusterMode() { return clusterMode; }
        int getRequestTimeoutMs() { return requestTimeoutMs; }
        int getConnectionTimeoutMs() { return connectionTimeoutMs; }
        String getUsername() { return username; }
        String getPassword() { return password; }
        int getDatabaseId() { return databaseId; }
        glide.api.models.configuration.ProtocolVersion getProtocol() { return protocol; }
        boolean getInsecureTls() { return insecureTls; }
        byte[][] getSubExact() { return subExact; }
        byte[][] getSubPattern() { return subPattern; }
        byte[][] getSubSharded() { return subSharded; }
        String getClientName() { return clientName; }
        Integer getMaxInflightRequests() { return maxInflightRequests; }
        Integer getNativeDirectMemoryMB() { return nativeDirectMemoryMB; }
        glide.api.models.configuration.ReadFrom getReadFrom() { return readFrom; }
        String getClientAZ() { return clientAZ; }
        boolean getLazyConnect() { return lazyConnect; }
        int getReconnectNumRetriesOrDefault() { return reconnectNumRetries != null ? reconnectNumRetries : 0; }
        int getReconnectExponentBaseOrDefault() { return reconnectExponentBase != null ? reconnectExponentBase : 0; }
        int getReconnectFactorOrDefault() { return reconnectFactor != null ? reconnectFactor : 0; }
        int getReconnectJitterPercentOrDefault() { return reconnectJitterPercent != null ? reconnectJitterPercent : -1; }
    }

    /**
     * Create a new GlideClient with the specified configuration
     *
     * @param config Configuration for the client connection
     */
    public GlideCoreClient(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (config.addresses.isEmpty()) {
            throw new IllegalArgumentException("At least one address must be provided");
        }

        // Configuration validated - ready to create client
        
        // Store the computed inflight limit for this client instance
        this.maxInflightRequests = computeMaxInflight(config);
        
        // Store the request timeout for this client instance
        this.requestTimeoutMs = config.getRequestTimeoutMs();

        // Create client with high performance optimizations
        long handle;
        try {
            handle = createClient(
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
                computeNativeDirectMemoryMB(config),
                config.getProtocol().name(),
                config.getSubExact(),
                config.getSubPattern(),
                config.getSubSharded(),
                config.getReadFrom().name(),
                config.getClientAZ(),
                config.getLazyConnect(),
                config.getClientName(),
                config.getReconnectNumRetriesOrDefault(),
                config.getReconnectExponentBaseOrDefault(),
                config.getReconnectFactorOrDefault(),
                config.getReconnectJitterPercentOrDefault()
            );
        } catch (RuntimeException e) {
            // Propagate the exception from the native layer with proper context
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("Connection refused") || errorMsg.contains("Failed to create client"))) {
                throw e; // Already has proper message from Rust
            }
            // Wrap with more context if needed
            throw new RuntimeException("Failed to create client: " + errorMsg, e);
        }

        if (handle == 0) {
            // This should not happen if the native layer throws exceptions properly
            // but keep as fallback for backward compatibility
            String errorMsg = "Failed to create client - Connection refused";
            throw new RuntimeException(errorMsg);
        }

        this.nativeClientHandle.set(handle);

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(handle);

        // Register cleanup action with Cleaner - modern replacement for finalize()
        this.cleanable = CLEANER.register(this, new CleanupAction(this.nativeState));
    }

    // ==================== UNIFIED ROUTE HANDLING ====================

    /**
     * Unified route handler for all command execution types.
     * Eliminates duplication between binary and generic command routing.
     */
    private <T> T applyRouting(Object route, RouteHandler<T> handler) {
        if (route instanceof RequestRoutingConfiguration.SimpleMultiNodeRoute) {
            RequestRoutingConfiguration.SimpleMultiNodeRoute mr = (RequestRoutingConfiguration.SimpleMultiNodeRoute) route;
            switch (mr) {
                case ALL_NODES:
                    return handler.allNodes();
                case ALL_PRIMARIES:
                    return handler.allPrimaries();
                default:
                    return handler.automatic();
            }
        } else if (route instanceof RequestRoutingConfiguration.SimpleSingleNodeRoute) {
            RequestRoutingConfiguration.SimpleSingleNodeRoute sr = (RequestRoutingConfiguration.SimpleSingleNodeRoute) route;
            switch (sr) {
                case RANDOM:
                    return handler.random();
                default:
                    return handler.automatic();
            }
        } else if (route instanceof RequestRoutingConfiguration.SlotKeyRoute) {
            RequestRoutingConfiguration.SlotKeyRoute kr = (RequestRoutingConfiguration.SlotKeyRoute) route;
            boolean preferReplica = kr.getSlotType() == RequestRoutingConfiguration.SlotType.REPLICA;
            return handler.slotKey(kr.getSlotKey(), preferReplica);
        } else if (route instanceof RequestRoutingConfiguration.SlotIdRoute) {
            RequestRoutingConfiguration.SlotIdRoute sr = (RequestRoutingConfiguration.SlotIdRoute) route;
            boolean preferReplica = sr.getSlotType() == RequestRoutingConfiguration.SlotType.REPLICA;
            return handler.slotId(sr.getSlotId(), preferReplica);
        } else if (route instanceof RequestRoutingConfiguration.ByAddressRoute) {
            RequestRoutingConfiguration.ByAddressRoute br = (RequestRoutingConfiguration.ByAddressRoute) route;
            return handler.byAddress(br.getHost(), br.getPort());
        } else if (route != null) {
            // Backward-compatibility: best-effort string decoding
            String routeStr = route.toString();
            if (routeStr.contains("ALL_NODES")) {
                return handler.allNodes();
            } else if (routeStr.contains("ALL_PRIMARIES")) {
                return handler.allPrimaries();
            }
        }
        
        // Default to automatic routing
        return handler.automatic();
    }

    /**
     * Handler interface for different routing strategies.
     * Allows the same routing logic to work for both binary and generic commands.
     */
    private interface RouteHandler<T> {
        T allNodes();
        T allPrimaries();
        T random();
        T slotKey(String slotKey, boolean preferReplica);
        T slotId(int slotId, boolean preferReplica);
        T byAddress(String host, int port);
        T automatic();
    }

    // ==================== INTERNAL CONFIG HELPERS ====================

    private static int computeMaxInflight(Config config) {
        if (config.getMaxInflightRequests() != null && config.getMaxInflightRequests() > 0) {
            return config.getMaxInflightRequests();
        }

        String env = System.getenv("GLIDE_MAX_INFLIGHT_REQUESTS");
        if (env != null) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0)
                    return v;
            } catch (NumberFormatException ignored) {
            }
        }

        String prop = System.getProperty("glide.maxInflightRequests");
        if (prop != null) {
            try {
                int v = Integer.parseInt(prop.trim());
                if (v > 0)
                    return v;
            } catch (NumberFormatException ignored) {
            }
        }

        // 0 means "use native/core defaults"
        return 0;
    }

    private static int computeNativeDirectMemoryMB(Config config) {
        if (config.getNativeDirectMemoryMB() != null && config.getNativeDirectMemoryMB() > 0) {
            return config.getNativeDirectMemoryMB();
        }

        String env = System.getenv("GLIDE_JNI_NATIVE_DIRECT_MEM_MB");
        if (env != null) {
            try {
                int v = Integer.parseInt(env.trim());
                if (v > 0)
                    return v;
            } catch (NumberFormatException ignored) {
            }
        }

        String prop = System.getProperty("glide.nativeDirectMemoryMB");
        if (prop != null) {
            try {
                int v = Integer.parseInt(prop.trim());
                if (v > 0)
                    return v;
            } catch (NumberFormatException ignored) {
            }
        }

        /* MEMORY CONTROL DISABLED - Adaptive default: min(1024, max(256, Xmx/8, 2 * maxInflight * 64KB))
        long xmxBytes = Runtime.getRuntime().maxMemory();
        int xmxMB = xmxBytes > 0 ? (int) Math.min(Integer.MAX_VALUE, xmxBytes / (1024L * 1024L)) : 0;
        int baseline = Math.max(256, xmxMB > 0 ? (xmxMB / 8) : 0);
        int inflight = computeMaxInflight(config);
        long inflightHintMB = inflight > 0 ? ((long) inflight * 128L) / 1024L : 0L; // 2 * 64KB = 128KB per inflight
        int candidate = (int) Math.max(baseline, Math.min(Integer.MAX_VALUE, inflightHintMB));
        return Math.min(1024, candidate);
        */
        
        // Simple default without memory management intervention
        return 512; // 512MB default - let users configure as needed
    }

    /**
     * Convenience constructor for simple host:port connections
     *
     * @param host Valkey server hostname or IP address
     * @param port Valkey server port
     */
    public GlideCoreClient(String host, int port) {
        this(new Config(Arrays.asList(host + ":" + port)));
    }

    /**
     * Create a new client connection to Valkey asynchronously.
     * 
     * @param config Configuration for the client connection
     * @return CompletableFuture that resolves to new client instance
     */
    public static CompletableFuture<GlideCoreClient> createAsync(Config config) {
        return CompletableFuture.supplyAsync(() -> new GlideCoreClient(config));
    }
    
    /**
     * Create a new GlideCoreClient with basic configuration (for compatibility with UDS API).
     * Provides the same static factory method as the UDS implementation for seamless migration.
     * 
     * @param config Configuration for the client connection  
     * @return New GlideCoreClient instance
     */
    public static GlideCoreClient createClient(Config config) {
        return new GlideCoreClient(config);
    }

    // ==================== AUTOMATIC ROUTING METHODS (RECOMMENDED) ====================
    
    /**
     * Execute a Valkey command with automatic routing (RECOMMENDED for 95% of commands).
     * This leverages ALL glide-core sophisticated routing optimizations:
     * <ul>
     * <li>Hash slot calculation and cluster topology awareness</li>
     * <li>Connection pooling and request multiplexing</li>
     * <li>Multi-slot command splitting for MGET/MSET</li>
     * <li>Automatic failover and replica read routing</li>
     * <li>Load balancing and performance optimizations</li>
     * </ul>
     *
     * @param command Valkey command name
     * @param args Command arguments
     * @return CompletableFuture with the command result
     */
    public CompletableFuture<Object> execute(String command, String... args) {
        return executeCommand(CommandRequest.auto(command, args));
    }

    /**
     * Execute any server command using the Command object with automatic routing.
     *
     * @param command The command to execute
     * @return CompletableFuture with the command result
     */
    public CompletableFuture<Object> executeCommand(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        return executeCommand(CommandRequest.auto(command.getType(), command.getArgumentsArray()));
    }

    /**
     * Execute a binary command with mixed String/byte[] arguments using automatic routing.
     * This method supports binary data without string conversion corruption.
     *
     * @param command The binary command to execute
     * @return CompletableFuture with the command result
     */
    public CompletableFuture<Object> executeBinaryCommand(BinaryCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("BinaryCommand cannot be null");
        }
        return executeBinaryCommand(BinaryCommandRequest.auto(command));
    }

    /**
     * Execute a binary command with explicit routing using the BinaryCommand interface.
     * This adapts cluster routing calls to our sophisticated glide-core routing while preserving binary data.
     *
     * @param command The binary command to execute
     * @param route The routing configuration for the command
     * @return CompletableFuture with the command result
     */
    public CompletableFuture<Object> executeBinaryCommand(BinaryCommand command, Object route) {
        if (command == null) {
            throw new IllegalArgumentException("BinaryCommand cannot be null");
        }
        
        // Use unified route handler to eliminate duplication
        BinaryCommandRequest request = applyRouting(route, new RouteHandler<BinaryCommandRequest>() {
            @Override
            public BinaryCommandRequest allNodes() {
                return BinaryCommandRequest.forAllNodes(command);
            }
            
            @Override
            public BinaryCommandRequest allPrimaries() {
                return BinaryCommandRequest.forAllPrimaries(command);
            }
            
            @Override
            public BinaryCommandRequest random() {
                return BinaryCommandRequest.forRandomNode(command);
            }
            
            @Override
            public BinaryCommandRequest slotKey(String slotKey, boolean preferReplica) {
                return BinaryCommandRequest.forSlotKey(command, slotKey, preferReplica);
            }
            
            @Override
            public BinaryCommandRequest slotId(int slotId, boolean preferReplica) {
                return BinaryCommandRequest.forSlotId(command, slotId, preferReplica);
            }
            
            @Override
            public BinaryCommandRequest byAddress(String host, int port) {
                return BinaryCommandRequest.forAddress(command, host, port);
            }
            
            @Override
            public BinaryCommandRequest automatic() {
                return BinaryCommandRequest.auto(command);
            }
        });
        
        return executeBinaryCommand(request);
    }

    // ==================== EXPLICIT ROUTING METHODS (WHEN NEEDED) ====================
    
    /**
     * Execute a command that should be sent to ALL nodes in the cluster.
     * Use for cluster-wide operations like FLUSHALL, SCRIPT LOAD, CONFIG SET, etc.
     * 
     * <p><strong>When to use:</strong> Commands that need to affect every node in the cluster
     * 
     * @param command Valkey command name
     * @param args Command arguments
     * @return CompletableFuture with aggregated results from all nodes
     */
    public CompletableFuture<Object> executeOnAllNodes(String command, String... args) {
        return executeCommand(CommandRequest.forAllNodes(command, args));
    }
    
    /**
     * Execute a command that should be sent to ALL primary nodes in the cluster.
     * Use for commands like PING cluster, CONFIG operations, primary-specific commands.
     * 
     * <p><strong>When to use:</strong> Commands that only need to hit primary nodes
     * 
     * @param command Valkey command name
     * @param args Command arguments
     * @return CompletableFuture with results from all primary nodes
     */
    public CompletableFuture<Object> executeOnAllPrimaries(String command, String... args) {
        return executeCommand(CommandRequest.forAllPrimaries(command, args));
    }
    
    /**
     * Execute a custom/raw command without type expectations.
     * This bypasses glide-core's type checking, allowing commands that return
     * unexpected types (like Map for multi-node SCRIPT EXISTS).
     * 
     * @param route Routing configuration for the command
     * @param args Full command arguments (first arg is the command itself)
     * @return CompletableFuture with raw result
     */
    public CompletableFuture<Object> executeCustomCommand(RequestRoutingConfiguration.Route route, String... args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Custom command requires at least the command name");
        }
        
        // Use the actual command name (first argument) instead of "CUSTOM"
        String commandName = args[0];
        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args, 1, commandArgs, 0, args.length - 1);
        
        // Build a custom command request
        CommandRequest request;
        if (route instanceof RequestRoutingConfiguration.SimpleMultiNodeRoute) {
            RequestRoutingConfiguration.SimpleMultiNodeRoute mr = (RequestRoutingConfiguration.SimpleMultiNodeRoute) route;
            switch (mr) {
                case ALL_NODES:
                    request = CommandRequest.forAllNodes(commandName, commandArgs);
                    break;
                case ALL_PRIMARIES:
                    request = CommandRequest.forAllPrimaries(commandName, commandArgs);
                    break;
                default:
                    request = CommandRequest.auto(commandName, commandArgs);
                    break;
            }
        } else if (route instanceof RequestRoutingConfiguration.SimpleSingleNodeRoute) {
            RequestRoutingConfiguration.SimpleSingleNodeRoute sr = (RequestRoutingConfiguration.SimpleSingleNodeRoute) route;
            if (sr == RequestRoutingConfiguration.SimpleSingleNodeRoute.RANDOM) {
                request = CommandRequest.forRandomNode(commandName, commandArgs);
            } else {
                // For PRIMARY and other single-node routes, use auto routing
                request = CommandRequest.auto(commandName, commandArgs);
            }
        } else {
            // For other route types, use automatic routing
            request = CommandRequest.auto(commandName, commandArgs);
        }
        
        return executeCommand(request);
    }
    
    /**
     * Special method for SCRIPT EXISTS that works around the Map vs Boolean[] issue.
     * Uses the existing Rust layer conversion logic by ensuring proper routing detection.
     * 
     * @param route Routing configuration for the command
     * @param sha1s Array of script hashes to check
     * @return CompletableFuture with Boolean[] result (converted by Rust layer)
     */
    
    /**
     * Execute a command on a specific node by address.
     * Use for node-specific debugging, INFO commands, direct node access.
     * 
     * <p><strong>When to use:</strong> Debugging, node-specific operations, direct targeting
     * 
     * @param host Target node hostname or IP
     * @param port Target node port
     * @param command Valkey command name
     * @param args Command arguments
     * @return CompletableFuture with result from the specific node
     */
    public CompletableFuture<Object> executeOnNode(String host, int port, String command, String... args) {
        return executeCommand(CommandRequest.forAddress(command, host, port, args));
    }
    
    /**
     * Execute a command routed by slot key to ensure command locality.
     * Use when you need to guarantee the command goes to the node handling a specific key,
     * especially useful for complex multi-key operations or debugging.
     * 
     * <p><strong>When to use:</strong> Ensuring command goes to specific key's node
     * 
     * @param slotKey Key to determine the hash slot for routing
     * @param preferReplica Whether to prefer replica nodes for reads
     * @param command Valkey command name
     * @param args Command arguments
     * @return CompletableFuture with result from the key's node
     */
    public CompletableFuture<Object> executeBySlotKey(String slotKey, boolean preferReplica, String command, String... args) {
        return executeCommand(CommandRequest.forSlotKey(command, slotKey, preferReplica, args));
    }
    
    /**
     * Execute a command routed by specific slot ID.
     * Use for advanced cluster debugging and direct slot targeting.
     * 
     * <p><strong>When to use:</strong> Direct slot targeting, advanced debugging
     * 
     * @param slotId Slot ID (0-16383) to target
     * @param preferReplica Whether to prefer replica nodes for reads
     * @param command Valkey command name
     * @param args Command arguments
     * @return CompletableFuture with result from the slot's node
     */
    public CompletableFuture<Object> executeBySlotId(int slotId, boolean preferReplica, String command, String... args) {
        return executeCommand(CommandRequest.forSlotId(command, slotId, preferReplica, args));
    }
    
    /**
     * Execute a command on a random node.
     * Use for load balancing read operations and distributing non-key commands.
     * 
     * <p><strong>When to use:</strong> Load balancing, distributing read operations
     * 
     * @param command Valkey command name
     * @param args Command arguments
     * @return CompletableFuture with result from a random node
     */
    public CompletableFuture<Object> executeOnRandomNode(String command, String... args) {
        return executeCommand(CommandRequest.forRandomNode(command, args));
    }

    /**
     * Core command execution method using the enhanced routing architecture.
     * This method preserves glide-core's multiplexed connection architecture while
     * eliminating UDS/protobuf overhead for maximum performance.
     * 
     * Includes OpenTelemetry span lifecycle integration matching UDS API.
     */
    private CompletableFuture<Object> executeCommand(CommandRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CommandRequest cannot be null");
        }
        


        try {
            // Use batch processing to reduce individual JNI calls
            // This eliminates the race conditions and thread pool exhaustion
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create OpenTelemetry span if configured (matching UDS pattern)
            long spanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(formatSpanName(request.getCommandName()));
            }

            // Create future and register it with the async registry (enforce client-specific inflight limit)
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            
            try {
                // Use special registration for blocking commands with timeout=0 (infinite blocking)
                if (isBlockingCommandWithInfiniteTimeout(request)) {
                    // For blocking commands with timeout=0, register without any timeout
                    correlationId = AsyncRegistry.registerInfiniteBlockingCommand(future, this.maxInflightRequests, handle);
                } else {
                    correlationId = AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
                }
            } catch (glide.api.models.exceptions.RequestException e) {
                // If we hit the inflight limit, return a failed future immediately
                future.completeExceptionally(e);
                return future;
            }

            // Execute command directly via JNI
            GlideNativeBridge.executeCommandAsync(handle, request.toBytes(), correlationId);
            
            // Ensure span cleanup on completion
            final long finalSpanPtr = spanPtr;
            if (finalSpanPtr != 0) {
                future.whenComplete((result, throwable) -> {
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

    /**
     * Check if a command is a blocking command that may need special handling
     */
    private static boolean isBlockingCommand(String commandName) {
        if (commandName == null) return false;
        String cmd = commandName.toUpperCase();
        return cmd.equals("BLPOP") || cmd.equals("BRPOP") || cmd.equals("BLMOVE") || 
               cmd.equals("BLMPOP") || cmd.equals("BZPOPMIN") || cmd.equals("BZPOPMAX") ||
               cmd.equals("BZMPOP") || cmd.equals("XREAD") || cmd.equals("XREADGROUP");
    }

    /**
     * Check if this appears to be a request from an inflight test
     * (uses non-existent keys with random suffixes)
     */
    private static boolean isInflightTestCommand(CommandRequest request) {
        if (request == null || request.getArguments() == null) return false;
        // Check if any argument looks like a test key (contains "nonexist")
        for (String arg : request.getArguments()) {
            if (arg != null && arg.contains("nonexist")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if this is a blocking command with timeout=0 (infinite timeout)
     */
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

    /**
     * Core binary command execution method supporting mixed String/byte[] arguments.
     * This method preserves binary data integrity while maintaining the same performance
     * characteristics as regular command execution.
     */
    private CompletableFuture<Object> executeBinaryCommand(BinaryCommandRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("BinaryCommandRequest cannot be null");
        }

        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }

            // Create OpenTelemetry span if configured (matching regular command pattern)
            long spanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                spanPtr = OpenTelemetryResolver.createLeakedOtelSpan(formatSpanName(request.getCommandName()));
            }

            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId = AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                // If we hit the inflight limit, return a failed future immediately
                future.completeExceptionally(e);
                return future;
            }

            // Execute binary command via JNI
            GlideNativeBridge.executeBinaryCommandAsync(handle, request.toBytes(), correlationId);
            
            // Ensure span cleanup on completion
            final long finalSpanPtr = spanPtr;
            if (finalSpanPtr != 0) {
                future.whenComplete((result, throwable) -> {
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

    /**
     * Check if client is connected.
     */
    public boolean isConnected() {
        long handle = nativeClientHandle.get();
        return handle != 0 && isConnected(handle);
    }

    /**
     * Get client information for debugging and monitoring.
     */
    public String getClientInfo() {
        long handle = nativeClientHandle.get();
        if (handle == 0) {
            return "Client is closed";
        }
        return getClientInfo(handle);
    }

    /**
     * Get the number of pending async operations.
     */
    public int getPendingOperations() {
        return AsyncRegistry.getPendingCount();
    }

    /**
     * Health check to detect if client is working properly
     */
    public boolean isHealthy() {
        return isConnected() && AsyncRegistry.getPendingCount() < 1000;
    }

    /**
     * Check if client is closed and throw exception if it is
     */
    private void checkNotClosed() {
        if (nativeClientHandle.get() == 0) {
            throw new glide.api.models.exceptions.ClosingException("Client is closed");
        }
    }

    // ==================== RESOURCE MANAGEMENT ====================

    /**
     * Close the client and cleanup all resources
     */
    @Override
    public void close() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            // Cleanup already in progress or completed
            return;
        }

        long handle = nativeClientHandle.getAndSet(0);
        if (handle != 0) {
            try { unregisterClient(handle); } catch (Throwable ignore) {}
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

    /**
     * Shared state for cleanup coordination
     */
    private static class NativeState {
        volatile long nativePtr;

        NativeState(long nativePtr) {
            this.nativePtr = nativePtr;
        }
    }

    /**
     * Cleanup action for the Cleaner
     */
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

    /**
     * Create client with enhanced routing support
     *
     * @param addresses Array of server addresses in "host:port" format
     * @param databaseId database number (standalone only)
     * @param username authentication username (can be null)
     * @param password authentication password (can be null)
     * @param useTls whether to use TLS encryption
     * @param clusterMode whether to enable cluster mode
     * @param requestTimeoutMs Request timeout in milliseconds
     * @param connectionTimeoutMs Connection timeout in milliseconds
     * @return native pointer to client instance
     */
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
        int nativeDirectMemoryMB,
        String protocol,
        byte[][] subExact,
        byte[][] subPattern,
        byte[][] subSharded,
        String readFrom,
        String clientAZ,
        boolean lazyConnect,
        String clientName,
        int reconnectNumRetries,
        int reconnectExponentBase,
        int reconnectFactor,
        int reconnectJitterPercent
    ) {
        return GlideNativeBridge.createClient(
                addresses, databaseId, username, password, useTls, insecureTls, clusterMode,
                requestTimeoutMs, connectionTimeoutMs, maxInflightRequests, nativeDirectMemoryMB,
                protocol, subExact, subPattern, subSharded, readFrom, clientAZ, lazyConnect, clientName,
                reconnectNumRetries, reconnectExponentBase, reconnectFactor, reconnectJitterPercent
        );
    }

    // Reserved for future transaction support implementation
    /**
     * Execute command asynchronously using enhanced binary format with routing support.
     * This is the core JNI method that preserves glide-core's multiplexing
     * while eliminating UDS/protobuf overhead and leveraging glide-core sophisticated routing.
     *
     * @param clientPtr native pointer to client instance
     * @param requestBytes binary-serialized command request (includes routing info)
     * @param callbackId unique ID for async callback correlation
     */
    private static void executeCommandAsync(long clientPtr, byte[] requestBytes, long callbackId) {
        GlideNativeBridge.executeCommandAsync(clientPtr, requestBytes, callbackId);
    }

    /**
     * Check if the native client is connected
     *
     * @param clientPtr native pointer to client instance
     * @return true if connected, false otherwise
     */
    private static boolean isConnected(long clientPtr) {
        return GlideNativeBridge.isConnected(clientPtr);
    }

    /**
     * Get client information from native layer
     *
     * @param clientPtr native pointer to client instance
     * @return client information string
     */
    private static String getClientInfo(long clientPtr) {
        return GlideNativeBridge.getClientInfo(clientPtr);
    }

    /**
     * Close and release a native client
     *
     * @param clientPtr native pointer to client instance
     */
    private static void closeClient(long clientPtr) {
        GlideNativeBridge.closeClient(clientPtr);
    }

    // ======================= ADAPTER METHODS FOR EXISTING API =======================
    
    /**
     * Execute a command with explicit routing using the old Command interface.
     * This adapts cluster routing calls to our sophisticated glide-core routing.
     */
    public CompletableFuture<Object> executeCommand(glide.internal.protocol.Command command, Object route) {
        // Use the explicit command type and arguments; do NOT assume the first argument is the command
        String commandName = command.getType();
        String[] commandArgs = command.getArgumentsArray();

        // Use unified route handler to eliminate duplication
        return applyRouting(route, new RouteHandler<CompletableFuture<Object>>() {
            @Override
            public CompletableFuture<Object> allNodes() {
                return executeOnAllNodes(commandName, commandArgs);
            }
            
            @Override
            public CompletableFuture<Object> allPrimaries() {
                return executeOnAllPrimaries(commandName, commandArgs);
            }
            
            @Override
            public CompletableFuture<Object> random() {
                return executeRawCommand(commandName, commandArgs); // let core choose a random node
            }
            
            @Override
            public CompletableFuture<Object> slotKey(String slotKey, boolean preferReplica) {
                return executeCommand(CommandRequest.forSlotKey(commandName, slotKey, preferReplica, commandArgs));
            }
            
            @Override
            public CompletableFuture<Object> slotId(int slotId, boolean preferReplica) {
                return executeCommand(CommandRequest.forSlotId(commandName, slotId, preferReplica, commandArgs));
            }
            
            @Override
            public CompletableFuture<Object> byAddress(String host, int port) {
                return executeCommand(CommandRequest.forAddress(commandName, host, port, commandArgs));
            }
            
            @Override
            public CompletableFuture<Object> automatic() {
                return executeRawCommand(commandName, commandArgs);
            }
        });
    }
    
    /**
     * Execute raw command with command name and arguments.
     */
    public CompletableFuture<Object> executeRawCommand(String commandName, String[] args) {
        return execute(commandName, args);
    }
    
    /**
     * Execute batch of commands as transaction.
     */
    public CompletableFuture<Object[]> executeTransaction(glide.internal.protocol.Command[] commands, boolean binary) {
        CommandInterface[] cmds = commands != null ? commands : new Command[0];
        return executeBatch(cmds, true, null, true, null, binary);
    }

    // Overload accepting CommandInterface[] directly (binary-safe batch protocol)
    public CompletableFuture<Object[]> executeTransaction(glide.internal.protocol.CommandInterface[] commands, boolean binary) {
        CommandInterface[] cmds = commands != null ? commands : new CommandInterface[0];
        return executeBatch(cmds, true, null, true, null, binary);
    }
    
    // Overload with raiseOnError parameter
    public CompletableFuture<Object[]> executeTransaction(glide.internal.protocol.CommandInterface[] commands, boolean raiseOnError, boolean binary) {
        CommandInterface[] cmds = commands != null ? commands : new CommandInterface[0];
        return executeBatch(cmds, true, null, raiseOnError, null, binary);
    }
    
    /**
     * Execute batch of commands.
     */
    public CompletableFuture<Object[]> executeBatch(glide.internal.protocol.Command[] commands, boolean binary) {
        CommandInterface[] cmds = commands != null ? commands : new Command[0];
        return executeBatch(cmds, false, null, true, null, binary);
    }
    
    /**
     * Execute batch of commands with raiseOnError option.
     */
    public CompletableFuture<Object[]> executeBatch(glide.internal.protocol.Command[] commands, boolean raiseOnError, boolean binary) {
        CommandInterface[] cmds = commands != null ? commands : new Command[0];
        return executeBatch(cmds, false, null, raiseOnError, null, binary);
    }

    public CompletableFuture<Object[]> executeBatch(glide.internal.protocol.CommandInterface[] commands, boolean binary) {
        CommandInterface[] cmds = commands != null ? commands : new CommandInterface[0];
        return executeBatch(cmds, false, null, true, null, binary);
    }
    
    /**
     * Execute batch of commands with raiseOnError option.
     */
    public CompletableFuture<Object[]> executeBatch(glide.internal.protocol.CommandInterface[] commands, boolean raiseOnError, boolean binary) {
        CommandInterface[] cmds = commands != null ? commands : new CommandInterface[0];
        return executeBatch(cmds, false, null, raiseOnError, null, binary);
    }
    
    /**
     * Execute batch with cluster routing options.
     */
    public CompletableFuture<Object[]> executeBatchWithClusterOptions(
        glide.internal.protocol.Command[] commands, 
        Object options, 
        boolean transaction,
        boolean binary) {
        Integer timeoutMs = null;
        glide.internal.protocol.RouteInfo route = null;
        if (options instanceof glide.api.models.commands.batch.ClusterBatchOptions) {
            var opts = (glide.api.models.commands.batch.ClusterBatchOptions) options;
            timeoutMs = opts.getTimeout();
            var r = opts.getRoute();
            if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute) {
                var br = (glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute) r;
                route = glide.internal.protocol.RouteInfo.byAddress(br.getHost(), br.getPort());
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute) {
                var sr = (glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute) r;
                boolean replica = sr
                        .getSlotType() == glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
                route = glide.internal.protocol.RouteInfo.bySlotId(sr.getSlotId(), replica);
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute) {
                var kr = (glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute) r;
                boolean replica = kr
                        .getSlotType() == glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
                route = glide.internal.protocol.RouteInfo.bySlotKey(kr.getSlotKey(), replica);
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute) {
                var srn = (glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute) r;
                switch (srn) {
                    case RANDOM:
                        route = glide.internal.protocol.RouteInfo.random();
                        break;
                }
            }
        }
        CommandInterface[] cmds = commands != null ? commands : new Command[0];
        return executeBatch(cmds, transaction, timeoutMs, true, route, binary);
    }

    public CompletableFuture<Object[]> executeBatchWithClusterOptions(
        glide.internal.protocol.CommandInterface[] commands,
        Object options,
        boolean transaction,
        boolean binary) {
        Integer timeoutMs = null;
        glide.internal.protocol.RouteInfo route = null;
        if (options instanceof glide.api.models.commands.batch.ClusterBatchOptions) {
            var opts = (glide.api.models.commands.batch.ClusterBatchOptions) options;
            timeoutMs = opts.getTimeout();
            var r = opts.getRoute();
            if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute) {
                var br = (glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute) r;
                route = glide.internal.protocol.RouteInfo.byAddress(br.getHost(), br.getPort());
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute) {
                var sr = (glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute) r;
                boolean replica = sr.getSlotType() == glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
                route = glide.internal.protocol.RouteInfo.bySlotId(sr.getSlotId(), replica);
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute) {
                var kr = (glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute) r;
                boolean replica = kr.getSlotType() == glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
                route = glide.internal.protocol.RouteInfo.bySlotKey(kr.getSlotKey(), replica);
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute) {
                var srn = (glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute) r;
                switch (srn) {
                    case RANDOM:
                        route = glide.internal.protocol.RouteInfo.random();
                        break;
                }
            }
        }
        CommandInterface[] cmds = commands != null ? commands : new CommandInterface[0];
        return executeBatch(cmds, transaction, timeoutMs, true, route, binary);
    }
    
    /**
     * Execute batch with cluster routing options and raiseOnError.
     */
    public CompletableFuture<Object[]> executeBatchWithClusterOptions(
        glide.internal.protocol.CommandInterface[] commands,
        Object options,
        boolean transaction,
        boolean raiseOnError,
        boolean binary) {
        Integer timeoutMs = null;
        glide.internal.protocol.RouteInfo route = null;
        if (options instanceof glide.api.models.commands.batch.ClusterBatchOptions) {
            var opts = (glide.api.models.commands.batch.ClusterBatchOptions) options;
            timeoutMs = opts.getTimeout();
            var r = opts.getRoute();
            if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute) {
                var br = (glide.api.models.configuration.RequestRoutingConfiguration.ByAddressRoute) r;
                route = glide.internal.protocol.RouteInfo.byAddress(br.getHost(), br.getPort());
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute) {
                var sr = (glide.api.models.configuration.RequestRoutingConfiguration.SlotIdRoute) r;
                boolean replica = sr.getSlotType() == glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
                route = glide.internal.protocol.RouteInfo.bySlotId(sr.getSlotId(), replica);
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute) {
                var kr = (glide.api.models.configuration.RequestRoutingConfiguration.SlotKeyRoute) r;
                boolean replica = kr.getSlotType() == glide.api.models.configuration.RequestRoutingConfiguration.SlotType.REPLICA;
                route = glide.internal.protocol.RouteInfo.bySlotKey(kr.getSlotKey(), replica);
            } else if (r instanceof glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute) {
                var srn = (glide.api.models.configuration.RequestRoutingConfiguration.SimpleSingleNodeRoute) r;
                switch (srn) {
                    case RANDOM:
                        route = glide.internal.protocol.RouteInfo.random();
                        break;
                }
            }
        }
        CommandInterface[] cmds = commands != null ? commands : new CommandInterface[0];
        return executeBatch(cmds, transaction, timeoutMs, raiseOnError, route, binary);
    }
    
    /**
     * Execute string command (used for script commands).
     */
    public CompletableFuture<String> executeStringCommand(String commandName, String[] args) {
        return execute(commandName, args).thenApply(result -> result != null ? result.toString() : null);
    }
    
    /**
     * Execute cluster scan command using native implementation.
     */
    public CompletableFuture<Object> executeClusterScanCommand(glide.api.models.commands.scan.ClusterScanRequest scanRequest) {
        if (scanRequest == null) {
            throw new IllegalArgumentException("Scan request cannot be null");
        }
        
        try {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(new glide.api.models.exceptions.ClosingException("Client is closed"));
                return future;
            }
            
            // Create future and register it with the async registry
            CompletableFuture<Object> future = new CompletableFuture<>();
            long correlationId;
            try {
                correlationId = AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            } catch (glide.api.models.exceptions.RequestException e) {
                // If we hit the inflight limit, return a failed future immediately
                future.completeExceptionally(e);
                return future;
            }
            
            // Execute cluster scan via JNI
            byte[] requestBytes = scanRequest.toBytes();
            GlideNativeBridge.executeClusterScanAsync(handle, requestBytes, correlationId);
            
            return future;
            
        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
    
    /**
     * Submit cluster scan command - simplified interface matching UDS approach.
     */
    public CompletableFuture<Object[]> submitClusterScan(
        glide.api.models.commands.scan.ClusterScanCursor cursor,
        glide.api.models.commands.scan.ScanOptions options,
        boolean binary) {
        
        // Build the cluster scan request
        glide.api.models.commands.scan.ClusterScanRequest.Builder requestBuilder = 
            glide.api.models.commands.scan.ClusterScanRequest.builder();
        
        // Handle cursor - extract the cursor handle if available
        if (cursor != glide.api.models.commands.scan.ClusterScanCursor.initalCursor()) {
            // Try to extract cursor handle from the cursor object
            String cursorHandle = extractCursorHandle(cursor);
            if (cursorHandle != null) {
                requestBuilder.cursorId(cursorHandle);
            }
        }
        
        // Apply scan options
        if (options != null) {
            String[] args = options.toArgs();
            for (int i = 0; i < args.length; i += 2) {
                if (i + 1 < args.length) {
                    String key = args[i];
                    String value = args[i + 1];
                    if ("MATCH".equals(key)) {
                        requestBuilder.matchPattern(value);
                    } else if ("COUNT".equals(key)) {
                        try {
                            requestBuilder.count(Integer.parseInt(value));
                        } catch (NumberFormatException e) {
                            // Ignore invalid count
                        }
                    } else if ("TYPE".equals(key)) {
                        requestBuilder.objectType(value);
                    }
                }
            }
        }
        
        // Set binary mode based on parameter
        requestBuilder.binaryMode(binary);
        
        // Execute using existing infrastructure
        return executeClusterScanCommand(requestBuilder.build())
            .thenApply(result -> (Object[]) result);
    }
    
    /**
     * Extract cursor handle from cluster scan cursor.
     */
    private String extractCursorHandle(glide.api.models.commands.scan.ClusterScanCursor cursor) {
        // Check if the cursor is an instance of NativeClusterScanCursor
        if (cursor instanceof glide.api.GlideClusterClient.NativeClusterScanCursor) {
            glide.api.GlideClusterClient.NativeClusterScanCursor nativeCursor = 
                (glide.api.GlideClusterClient.NativeClusterScanCursor) cursor;
            return nativeCursor.getCursorHandle();
        }
        // Return null to start from beginning if not the expected type
        return null;
    }
    
    /**
     * Update connection password; when updateConfiguration=true, authenticate
     * immediately.
     */
    public CompletableFuture<String> updateConnectionPassword(String password, boolean updateConfiguration) {
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
            // If we hit the inflight limit, return a failed future immediately
            future.completeExceptionally(e);
            return future;
        }
        GlideNativeBridge.updateConnectionPassword(handle, password, updateConfiguration, correlationId);
        return future;
    }

    private CompletableFuture<Object[]> executeBatch(
        CommandInterface[] commands,
        boolean atomic,
        Integer timeoutMs,
        boolean raiseOnError,
        glide.internal.protocol.RouteInfo route,
        boolean binary) {
        return CompletableFuture.supplyAsync(() -> {
            long batchSpanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                batchSpanPtr = OpenTelemetryResolver.createLeakedOtelSpan("Batch");
            }
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                throw new IllegalStateException("Client handle is invalid");
            }
            // Build batch bytes using provided binary flag from higher-level API
            BatchRequest batch = new BatchRequest(commands, atomic, timeoutMs, raiseOnError, route, binary);
            byte[] bytes = batch.toBytes();
            CompletableFuture<Object[]> future = new CompletableFuture<>();
            long correlationId = AsyncRegistry.register(future, this.requestTimeoutMs, this.maxInflightRequests, handle);
            long sendSpanPtr = 0;
            if (OpenTelemetry.isInitialized() && OpenTelemetry.shouldSample()) {
                sendSpanPtr = OpenTelemetryResolver.createLeakedOtelSpan("send_batch");
            }
            GlideNativeBridge.executeBatchAsync(handle, bytes, correlationId);
            try {
                Object result = future.get();
                if (result == null) {
                    return null; // EXEC aborted due to WATCH
                }
                if (result instanceof Object[]) {
                    Object[] arr = (Object[]) result;
                    boolean debugBatch = System.getProperty("glide.debugBatch") != null || System.getenv("GLIDE_DEBUG_BATCH") != null;
                    // Batch response debug (shape + first element type) gated by glide.debugBatch/GLIDE_DEBUG_BATCH.
                    // Retained (lightweight) for future shape regressions (e.g. container deferral issues).
                    if (debugBatch) {
                        System.err.print("[DEBUG-BATCHRES] raw_result_array len=" + arr.length);
                        if (arr.length > 0) System.err.print(" firstType=" + (arr[0]==null?"null":arr[0].getClass().getName()));
                        System.err.println();
                    }
                    // Normalize inner list results to arrays for tests expecting arrays
                    // Also convert error markers to RequestException objects
                    for (int i = 0; i < arr.length; i++) {
                        Object e = arr[i];
                        if (e instanceof java.util.List) {
                            java.util.List<?> l = (java.util.List<?>) e;
                            Object[] inner = new Object[l.size()];
                            for (int j = 0; j < l.size(); j++) inner[j] = String.valueOf(l.get(j));
                            arr[i] = inner;
                        } else if (e instanceof String) {
                            String str = (String) e;
                            if (str.startsWith("__GLIDE_ERROR__:")) {
                                // Convert error marker to RequestException
                                String errorMessage = str.substring("__GLIDE_ERROR__:".length());
                                arr[i] = new glide.api.models.exceptions.RequestException(errorMessage);
                            }
                        }
                    }
                    return arr;
                }
                if (result instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) result;
                    Object[] arr = list.toArray(new Object[0]);
                    boolean debugBatch = System.getProperty("glide.debugBatch") != null || System.getenv("GLIDE_DEBUG_BATCH") != null;
                    // Same gated debug for list case.
                    if (debugBatch) {
                        System.err.print("[DEBUG-BATCHRES] raw_result_list converted len=" + arr.length);
                        if (arr.length > 0) System.err.print(" firstType=" + (arr[0]==null?"null":arr[0].getClass().getName()));
                        System.err.println();
                    }
                    for (int i = 0; i < arr.length; i++) {
                        Object e = arr[i];
                        if (e instanceof java.util.List) {
                            java.util.List<?> l = (java.util.List<?>) e;
                            Object[] inner = new Object[l.size()];
                            for (int j = 0; j < l.size(); j++) inner[j] = String.valueOf(l.get(j));
                            arr[i] = inner;
                        } else if (e instanceof String) {
                            String str = (String) e;
                            if (str.startsWith("__GLIDE_ERROR__:")) {
                                // Convert error marker to RequestException
                                String errorMessage = str.substring("__GLIDE_ERROR__:".length());
                                arr[i] = new glide.api.models.exceptions.RequestException(errorMessage);
                            }
                        }
                    }
                    return arr;
                }
                return new Object[] { result };
            } catch (Exception e) {
                // Preserve ExecutionException with RequestException cause by wrapping in CompletionException
                if (e instanceof java.util.concurrent.ExecutionException && 
                    e.getCause() instanceof glide.api.models.exceptions.RequestException) {
                    throw new java.util.concurrent.CompletionException(e.getCause());
                }
                throw new RuntimeException("Failed to execute batch", e);
            } finally {
                if (batchSpanPtr != 0) {
                    try {
                        OpenTelemetryResolver.dropOtelSpan(batchSpanPtr);
                    } catch (Throwable ignore) {
                    }
                }
                // Drop send span after dispatch
                try {
                    if (sendSpanPtr != 0)
                        OpenTelemetryResolver.dropOtelSpan(sendSpanPtr);
                } catch (Throwable ignore) {
                }
            }
        });
    }

    private static String formatSpanName(String commandName) {
        if (commandName == null || commandName.isEmpty())
            return "Command";
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
}