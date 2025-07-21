package io.valkey.glide.core.client;

import io.valkey.glide.core.commands.Command;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance client for Valkey GLIDE with direct glide-core integration.
 * <p>
 * This implementation uses direct JNI calls to the Rust glide-core for maximum 
 * performance while maintaining the same async API as the standard Glide client.
 */
public class GlideClient implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    static {
        // Load the native library
        try {
            System.loadLibrary("glidejni");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load native library: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Native handle to the client instance in Rust
     */
    private final AtomicLong nativeClientHandle = new AtomicLong(0);
    
    /**
     * Cleanup coordination flag to prevent double cleanup
     */
    private final AtomicBoolean cleanupInProgress = new AtomicBoolean(false);

    /**
     * Cleaner resource to ensure native cleanup
     */
    private final Cleaner.Cleanable cleanable;

    /**
     * Shared state for cleanup coordination
     */
    private final NativeState nativeState;

    /**
     * Configuration for GlideClient
     */
    public static class Config {
        private final List<String> addresses;
        private boolean useTls = false;
        private boolean clusterMode = false;
        private int requestTimeoutMs = 5000;
        private int connectionTimeoutMs = 5000;
        private String username = null;
        private String password = null;
        private int databaseId = 0;

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

        // Package-private getters for JNI access
        String[] getAddresses() { return addresses.toArray(new String[0]); }
        boolean getUseTls() { return useTls; }
        boolean getClusterMode() { return clusterMode; }
        int getRequestTimeoutMs() { return requestTimeoutMs; }
        int getConnectionTimeoutMs() { return connectionTimeoutMs; }
        String getUsername() { return username; }
        String getPassword() { return password; }
        int getDatabaseId() { return databaseId; }
    }

    /**
     * Create a new GlideClient with the specified configuration
     *
     * @param config Configuration for the client connection
     */
    public GlideClient(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        if (config.addresses.isEmpty()) {
            throw new IllegalArgumentException("At least one address must be provided");
        }

        // Create client with high performance optimizations
        long handle = createClient(
            config.getAddresses(),
            config.getDatabaseId(),
            config.getUsername(),
            config.getPassword(),
            config.getUseTls(),
            config.getClusterMode(),
            config.getRequestTimeoutMs(),
            config.getConnectionTimeoutMs()
        );

        if (handle == 0) {
            throw new RuntimeException("Failed to create client");
        }

        this.nativeClientHandle.set(handle);

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(handle);

        // Register cleanup action with Cleaner - modern replacement for finalize()
        this.cleanable = CLEANER.register(this, new CleanupAction(this.nativeState));
    }

    /**
     * Convenience constructor for simple host:port connections
     *
     * @param host Valkey server hostname or IP address
     * @param port Valkey server port
     */
    public GlideClient(String host, int port) {
        this(new Config(Arrays.asList(host + ":" + port)));
    }

    /**
     * Execute any server command using the generic command execution system.
     *
     * @param command The command to execute
     * @return CompletableFuture with the command result
     */
    public CompletableFuture<Object> executeCommand(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        checkNotClosed();

        try {
            // Convert String[] to byte[][]
            String[] args = command.getArgumentsArray();
            byte[][] byteArgs = new byte[args.length][];
            for (int i = 0; i < args.length; i++) {
                byteArgs[i] = args[i].getBytes();
            }

            long handle = nativeClientHandle.get();
            // Execute command through high performance pipeline
            Object result = executeCommand(handle, command.getType(), byteArgs);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Check if client is closed and throw exception if it is
     */
    private void checkNotClosed() {
        if (nativeClientHandle.get() == 0) {
            throw new IllegalStateException("Client is closed");
        }
    }

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
            closeClient(handle);
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
                // Use client cleanup
                closeClient(ptr);
            }
        }
    }

    // ==================== NATIVE METHODS ====================

    /**
     * Create client
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
    private static native long createClient(
        String[] addresses,
        int databaseId,
        String username,
        String password,
        boolean useTls,
        boolean clusterMode,
        int requestTimeoutMs,
        int connectionTimeoutMs
    );

    /**
     * Execute command
     *
     * @param clientPtr native pointer to client instance
     * @param command the command name
     * @param args array of byte arrays containing command arguments
     * @return command result as Object
     */
    private static native Object executeCommand(long clientPtr, String command, byte[][] args);

    /**
     * Close and release a native client
     *
     * @param clientPtr native pointer to client instance
     */
    private static native void closeClient(long clientPtr);
}