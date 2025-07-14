package io.valkey.glide.core.client;

import io.valkey.glide.core.commands.CommandType;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * High-performance client for Valkey GLIDE with direct glide-core integration.
 * <p>
 * This implementation bypasses Unix Domain Sockets and uses direct JNI calls
 * to the Rust glide-core for maximum performance while maintaining the same
 * async API as the standard Glide client.
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
    }    /**
     * Native pointer to the client instance in Rust
     */
    private volatile long nativeClientPtr;

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

        this.nativeClientPtr = createClient(
            config.getAddresses(),
            config.getDatabaseId(),
            config.getUsername(),
            config.getPassword(),
            config.getUseTls(),
            config.getClusterMode(),
            config.getRequestTimeoutMs(),
            config.getConnectionTimeoutMs()
        );

        if (this.nativeClientPtr == 0) {
            throw new RuntimeException("Failed to create client");
        }

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(this.nativeClientPtr);

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
     * Execute a GET command - matches BaseClient API
     *
     * @param key the key to get
     * @return CompletableFuture with the value as string, or null if key doesn't exist
     */
    public CompletableFuture<String> get(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        checkNotClosed();

        // Execute using native method
        try {
            String result = get(nativeClientPtr, key);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a SET command - matches BaseClient API
     *
     * @param key the key to set
     * @param value the value to set
     * @return CompletableFuture with "OK" response
     */
    public CompletableFuture<String> set(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }

        checkNotClosed();

        // Execute using native method
        try {
            boolean success = set(nativeClientPtr, key, value);
            return CompletableFuture.completedFuture(success ? "OK" : null);
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a PING command - matches BaseClient API
     *
     * @return CompletableFuture with "PONG" response
     */
    public CompletableFuture<String> ping() {
        checkNotClosed();

        // Execute using native method
        try {
            String result = ping(nativeClientPtr);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute any server command using the generic command execution system.
     * This method provides a unified interface for all server operations.
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
            Object result = executeCommand(nativeClientPtr, command.getCommand(), command.getArgumentsArray());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    // ==================== TYPED EXECUTION METHODS ====================
    // These methods provide direct typed returns, eliminating protobuf overhead

    /**
     * Execute a command expecting a String result.
     * Leverages glide-core's value_conversion.rs with ExpectedReturnType::BulkString
     *
     * @param command The command to execute
     * @param args Command arguments
     * @return CompletableFuture with String result
     */
    public CompletableFuture<String> executeStringCommand(String command, String[] args) {
        checkNotClosed();
        try {
            String result = executeStringCommand(nativeClientPtr, command, args);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a command expecting a Long result.
     * Leverages glide-core's value_conversion.rs for numeric types
     *
     * @param command The command to execute
     * @param args Command arguments
     * @return CompletableFuture with Long result
     */
    public CompletableFuture<Long> executeLongCommand(String command, String[] args) {
        checkNotClosed();
        try {
            long result = executeLongCommand(nativeClientPtr, command, args);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Long> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a command expecting a Double result.
     * Leverages glide-core's value_conversion.rs with ExpectedReturnType::Double
     *
     * @param command The command to execute
     * @param args Command arguments
     * @return CompletableFuture with Double result
     */
    public CompletableFuture<Double> executeDoubleCommand(String command, String[] args) {
        checkNotClosed();
        try {
            double result = executeDoubleCommand(nativeClientPtr, command, args);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Double> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a command expecting a Boolean result.
     * Leverages glide-core's value_conversion.rs with ExpectedReturnType::Boolean
     *
     * @param command The command to execute
     * @param args Command arguments
     * @return CompletableFuture with Boolean result
     */
    public CompletableFuture<Boolean> executeBooleanCommand(String command, String[] args) {
        checkNotClosed();
        try {
            boolean result = executeBooleanCommand(nativeClientPtr, command, args);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a command expecting an Object[] result.
     * Leverages glide-core's value_conversion.rs for array types
     *
     * @param command The command to execute
     * @param args Command arguments
     * @return CompletableFuture with Object[] result
     */
    public CompletableFuture<Object[]> executeArrayCommand(String command, String[] args) {
        checkNotClosed();
        try {
            Object[] result = executeArrayCommand(nativeClientPtr, command, args);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Object[]> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a command expecting any Object result (for complex types).
     * Uses glide-core's default value conversion
     *
     * @param command The command to execute
     * @param args Command arguments
     * @return CompletableFuture with Object result
     */
    public CompletableFuture<Object> executeObjectCommand(String command, String[] args) {
        checkNotClosed();
        try {
            // Convert String[] to byte[][] for the native method
            byte[][] byteArgs = new byte[args.length][];
            for (int i = 0; i < args.length; i++) {
                byteArgs[i] = args[i].getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            Object result = executeCommand(nativeClientPtr, command, byteArgs);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Convenience method to execute a command and return a typed result.
     * This method handles common type casting scenarios.
     *
     * @param command The command to execute
     * @param expectedType The expected return type
     * @param <T> The type to cast the result to
     * @return CompletableFuture with the typed result
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> executeCommand(Command command, Class<T> expectedType) {
        return executeCommand(command).thenApply(result -> {
            if (result == null) {
                return null;
            }
            if (expectedType.isInstance(result)) {
                return (T) result;
            }
            throw new ClassCastException("Expected " + expectedType.getSimpleName() +
                                       " but got " + result.getClass().getSimpleName());
        });
    }

    /**
     * Check if the client is closed
     *
     * @return true if the client is closed
     */
    public boolean isClosed() {
        return nativeClientPtr == 0;
    }    /**
     * Close the client and release native resources
     */
    @Override
    public void close() {
        long ptr = nativeClientPtr;
        if (ptr != 0) {
            nativeClientPtr = 0;
            // Safely cleanup using shared state
            nativeState.cleanup();
            cleanable.clean(); // This will be a no-op since state is already cleaned
        }
    }

    /**
     * Shared state for coordinating cleanup between explicit close() and Cleaner
     */
    private static class NativeState {
        private volatile long nativePtr;

        NativeState(long nativePtr) {
            this.nativePtr = nativePtr;
        }

        /**
         * Safely cleanup the native resource, ensuring it's only done once
         */
        synchronized void cleanup() {
            long ptr = nativePtr;
            if (ptr != 0) {
                nativePtr = 0;
                closeClient(ptr);
            }
        }
    }

    /**
     * Cleanup action for Cleaner - modern replacement for finalize()
     * Only runs if close() was not called explicitly
     */
    private static class CleanupAction implements Runnable {
        private final NativeState nativeState;

        CleanupAction(NativeState nativeState) {
            this.nativeState = nativeState;
        }

        @Override
        public void run() {
            // This will be a no-op if close() was already called
            nativeState.cleanup();
        }
    }

    /**
     * Check that the client is not closed
     */
    private void checkNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Client is closed");
        }
    }

    // Native method declarations - match the Rust JNI function signatures

    /**
     * Create a new Glide client with full configuration
     *
     * @param addresses Array of "host:port" addresses
     * @param databaseId Database ID to select
     * @param username Username for authentication (null if not used)
     * @param password Password for authentication (null if not used)
     * @param useTls Whether to use TLS encryption
     * @param clusterMode Whether to use cluster mode
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
     * Close and release a native client
     *
     * @param clientPtr native pointer to client instance
     */
    private static native void closeClient(long clientPtr);

    /**
     * Execute GET command
     *
     * @param clientPtr native pointer to client instance
     * @param key the key to get
     * @return value as string, or null if key doesn't exist
     */
    private static native String get(long clientPtr, String key);

    /**
     * Execute SET command
     *
     * @param clientPtr native pointer to client instance
     * @param key the key to set
     * @param value the value to set
     * @return true if successful
     */
    private static native boolean set(long clientPtr, String key, String value);

    /**
     * Execute PING command
     *
     * @param clientPtr native pointer to client instance
     * @return "PONG" or similar response
     */
    private static native String ping(long clientPtr);

    /**
     * Execute any command with arguments
     *
     * @param clientPtr native pointer to client instance
     * @param command the command name
     * @param args array of byte arrays containing command arguments
     * @return command result as Object (can be String, Long, byte[], Object[], etc.)
     */
    private static native Object executeCommand(long clientPtr, String command, byte[][] args);

    // ==================== TYPED NATIVE METHODS ====================
    // These provide direct typed returns, leveraging glide-core's value_conversion.rs

    /**
     * Execute a command expecting a String result
     * Uses glide-core's ExpectedReturnType::BulkString for conversion
     */
    private static native String executeStringCommand(long clientPtr, String command, String[] args);

    /**
     * Execute a command expecting a Long result
     * Uses glide-core's numeric type conversion
     */
    private static native long executeLongCommand(long clientPtr, String command, String[] args);

    /**
     * Execute a command expecting a Double result
     * Uses glide-core's ExpectedReturnType::Double for conversion
     */
    private static native double executeDoubleCommand(long clientPtr, String command, String[] args);

    /**
     * Execute a command expecting a Boolean result
     * Uses glide-core's ExpectedReturnType::Boolean for conversion
     */
    private static native boolean executeBooleanCommand(long clientPtr, String command, String[] args);

    /**
     * Execute a command expecting an Object[] result
     * Uses glide-core's array type conversion
     */
    private static native Object[] executeArrayCommand(long clientPtr, String command, String[] args);
}
