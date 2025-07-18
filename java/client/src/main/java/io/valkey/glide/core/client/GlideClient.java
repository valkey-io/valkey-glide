package io.valkey.glide.core.client;

import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;
import java.lang.ref.Cleaner;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Native handle to the client instance in Rust (atomic for thread safety)
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
            // Convert String[] to byte[][]
            String[] args = command.getArgumentsArray();
            byte[][] byteArgs = new byte[args.length][];
            for (int i = 0; i < args.length; i++) {
                byteArgs[i] = args[i].getBytes();
            }

            long handle = nativeClientHandle.get();
            Object result = executeCommand(handle, command.getType().getCommandName(), byteArgs);
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
            long handle = nativeClientHandle.get();
            String result = executeStringCommand(handle, command, args);
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
            long handle = nativeClientHandle.get();
            long result = executeLongCommand(handle, command, args);
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
            long handle = nativeClientHandle.get();
            double result = executeDoubleCommand(handle, command, args);
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
            long handle = nativeClientHandle.get();
            boolean result = executeBooleanCommand(handle, command, args);
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
            long handle = nativeClientHandle.get();
            Object[] result = executeArrayCommand(handle, command, args);
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
            long handle = nativeClientHandle.get();
            Object result = executeCommand(handle, command, byteArgs);
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
        return nativeClientHandle.get() == 0;
    }
    
    /**
     * Close the client and release native resources with atomic operations
     */
    @Override
    public void close() {
        // Atomic compare-and-swap ensures only one cleanup
        long handle = nativeClientHandle.getAndSet(0);
        if (handle != 0 && cleanupInProgress.compareAndSet(false, true)) {
            try {
                nativeState.cleanup();
                cleanable.clean();
            } finally {
                // Mark as permanently closed
                cleanupInProgress.set(true);
            }
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
     * Execute a raw command that's not in the CommandType enum.
     * This is used for custom commands that aren't predefined.
     *
     * @param commandName The command name
     * @param args The command arguments
     * @return A CompletableFuture containing the command result
     */
    public CompletableFuture<Object> executeRawCommand(String commandName, String[] args) {
        return CompletableFuture.supplyAsync(() -> {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                throw new IllegalStateException("Client is closed");
            }
            
            // Convert to byte arrays for JNI call
            byte[][] byteArgs = new byte[args.length][];
            for (int i = 0; i < args.length; i++) {
                byteArgs[i] = args[i].getBytes();
            }
            
            return executeCommand(handle, commandName, byteArgs);
        });
    }

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

    // ==================== SIMPLIFIED ROUTING NATIVE METHODS ====================
    // These methods accept Route objects directly - conversion happens in Rust

    /**
     * Execute a command with Route object support
     *
     * @param clientPtr Native client handle
     * @param command Command name
     * @param args Command arguments
     * @param route Java Route object (null for no routing)
     * @return Command result
     */
    private static native Object executeCommandWithRoute(long clientPtr, String command, byte[][] args, Object route);

    /**
     * Execute a command with Route object support expecting a String result
     *
     * @param clientPtr Native client handle
     * @param command Command name
     * @param args Command arguments
     * @param route Java Route object (null for no routing)
     * @return String result
     */
    private static native String executeStringCommandWithRoute(long clientPtr, String command, String[] args, Object route);

    /**
     * Execute multiple commands as a batch for optimal performance
     * This method implements bulk command execution to eliminate per-command round trips
     *
     * @param clientPtr Native client handle
     * @param commands Array of Command objects to execute
     * @param route Java Route object (null for no routing)
     * @param isAtomic Whether to execute as atomic transaction (MULTI/EXEC) or non-atomic batch
     * @return Array of command results
     */
    private static native Object[] executePipeline(long clientPtr, Object[] commands, Object route, boolean isAtomic);

    /**
     * Execute multiple commands as a batch with full ClusterBatchOptions support
     * This method implements bulk command execution with timeout and retry strategies
     *
     * @param clientPtr Native client handle
     * @param commands Array of Command objects to execute
     * @param route Java Route object (null for no routing)
     * @param isAtomic Whether to execute as atomic transaction (MULTI/EXEC) or non-atomic batch
     * @param timeoutMs Timeout in milliseconds (0 for default)
     * @param retryServerError Whether to retry on server errors like TRYAGAIN
     * @param retryConnectionError Whether to retry on connection errors
     * @return Array of command results
     */
    private static native Object[] executePipelineWithOptions(long clientPtr, Object[] commands, Object route, boolean isAtomic, int timeoutMs, boolean retryServerError, boolean retryConnectionError);

    // ==================== JAVA ROUTING METHODS ====================
    // These methods accept Java Route objects and convert them to native routing parameters

    /**
     * Execute multiple commands efficiently as a batch with optional routing
     * This method provides significant performance improvements over individual command execution
     *
     * @param commands Array of Command objects to execute
     * @param route The routing configuration (Route object, null for no routing)
     * @param isAtomic Whether to execute as atomic transaction (MULTI/EXEC) or non-atomic batch
     * @return CompletableFuture with array of command results
     */
    public CompletableFuture<Object[]> executeBatchWithRoute(Command[] commands, Object route, boolean isAtomic) {
        if (commands == null || commands.length == 0) {
            throw new IllegalArgumentException("Commands array cannot be null or empty");
        }

        checkNotClosed();

        try {
            long handle = nativeClientHandle.get();
            // Use the new optimized batch execution
            Object[] results = executePipeline(handle, commands, route, isAtomic);
            return CompletableFuture.completedFuture(results);
        } catch (Exception e) {
            CompletableFuture<Object[]> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute multiple commands efficiently as a non-atomic batch
     * This method provides significant performance improvements for batch operations
     *
     * @param commands Array of Command objects to execute
     * @return CompletableFuture with array of command results
     */
    public CompletableFuture<Object[]> executeBatch(Command[] commands) {
        return executeBatchWithRoute(commands, null, false);
    }

    /**
     * Execute multiple commands as an atomic transaction using MULTI/EXEC
     *
     * @param commands Array of Command objects to execute
     * @return CompletableFuture with array of command results
     */
    public CompletableFuture<Object[]> executeTransaction(Command[] commands) {
        return executeBatchWithRoute(commands, null, true);
    }

    /**
     * Execute multiple commands efficiently as a batch with full ClusterBatchOptions support
     * This method processes all ClusterBatchOptions features including routing, timeout, and retry strategies
     *
     * @param commands Array of Command objects to execute
     * @param options ClusterBatchOptions containing routing, timeout, and retry configuration
     * @param isAtomic Whether to execute as atomic transaction (MULTI/EXEC) or non-atomic batch
     * @return CompletableFuture with array of command results
     */
    public CompletableFuture<Object[]> executeBatchWithClusterOptions(Command[] commands, Object options, boolean isAtomic) {
        if (commands == null || commands.length == 0) {
            throw new IllegalArgumentException("Commands array cannot be null or empty");
        }

        checkNotClosed();

        try {
            long handle = nativeClientHandle.get();
            
            // Extract options from ClusterBatchOptions object
            Object route = null;
            int timeoutMs = 0;
            boolean retryServerError = false;
            boolean retryConnectionError = false;
            
            if (options != null) {
                try {
                    // Use reflection to extract ClusterBatchOptions fields
                    Class<?> optionsClass = options.getClass();
                    
                    // Extract timeout from BaseBatchOptions (parent class)
                    try {
                        java.lang.reflect.Method getTimeoutMethod = optionsClass.getMethod("getTimeout");
                        Object timeoutObj = getTimeoutMethod.invoke(options);
                        if (timeoutObj instanceof Integer) {
                            timeoutMs = (Integer) timeoutObj;
                        }
                    } catch (Exception e) {
                        // Timeout is optional, ignore if not found
                    }
                    
                    // Extract route from ClusterBatchOptions
                    try {
                        java.lang.reflect.Method getRouteMethod = optionsClass.getMethod("getRoute");
                        route = getRouteMethod.invoke(options);
                    } catch (Exception e) {
                        // Route is optional, ignore if not found
                    }
                    
                    // Extract retry strategy from ClusterBatchOptions
                    try {
                        java.lang.reflect.Method getRetryStrategyMethod = optionsClass.getMethod("getRetryStrategy");
                        Object retryStrategy = getRetryStrategyMethod.invoke(options);
                        
                        if (retryStrategy != null) {
                            Class<?> retryStrategyClass = retryStrategy.getClass();
                            
                            try {
                                java.lang.reflect.Method isRetryServerErrorMethod = retryStrategyClass.getMethod("isRetryServerError");
                                retryServerError = (Boolean) isRetryServerErrorMethod.invoke(retryStrategy);
                            } catch (Exception e) {
                                // Default to false
                            }
                            
                            try {
                                java.lang.reflect.Method isRetryConnectionErrorMethod = retryStrategyClass.getMethod("isRetryConnectionError");
                                retryConnectionError = (Boolean) isRetryConnectionErrorMethod.invoke(retryStrategy);
                            } catch (Exception e) {
                                // Default to false
                            }
                        }
                    } catch (Exception e) {
                        // Retry strategy is optional, ignore if not found
                    }
                    
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process ClusterBatchOptions: " + e.getMessage(), e);
                }
            }
            
            // Use the optimized batch execution with full options support
            Object[] results = executePipelineWithOptions(handle, commands, route, isAtomic, timeoutMs, retryServerError, retryConnectionError);
            return CompletableFuture.completedFuture(results);
        } catch (Exception e) {
            CompletableFuture<Object[]> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a command with Java Route object support
     *
     * @param command The command to execute
     * @param route The routing configuration (Route object)
     * @return CompletableFuture with the command result
     */
    public CompletableFuture<Object> executeCommand(Command command, Object route) {
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
            // Pass Route object directly to JNI - conversion happens in Rust
            Object result = executeCommandWithRoute(handle, command.getType().getCommandName(), byteArgs, route);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<Object> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * Execute a command with Java Route object support expecting a String result
     *
     * @param command The command to execute
     * @param args Command arguments
     * @param route The routing configuration (Route object)
     * @return CompletableFuture with String result
     */
    public CompletableFuture<String> executeStringCommand(String command, String[] args, Object route) {
        checkNotClosed();
        try {
            long handle = nativeClientHandle.get();
            // Pass Route object directly to JNI - conversion happens in Rust
            String result = executeStringCommandWithRoute(handle, command, args, route);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    // ==================== SIMPLIFIED ROUTING ====================
    // Routing is now handled directly in Rust JNI layer without Java-side conversion

    /**
     * Update the connection password for reconnection.
     *
     * @param password The new password to use (null to remove password)
     * @param immediateAuth Whether to authenticate immediately with the new password
     * @return A CompletableFuture containing "OK" on success
     */
    public CompletableFuture<String> updateConnectionPassword(String password, boolean immediateAuth) {
        return CompletableFuture.supplyAsync(() -> {
            long handle = nativeClientHandle.get();
            if (handle == 0) {
                throw new IllegalStateException("Client is closed");
            }
            
            return updateConnectionPasswordNative(handle, password, immediateAuth);
        });
    }

    /**
     * Native method to update connection password
     *
     * @param clientPtr Native client handle
     * @param password The new password (null to remove password)
     * @param immediateAuth Whether to authenticate immediately
     * @return "OK" on success
     */
    private static native String updateConnectionPasswordNative(long clientPtr, String password, boolean immediateAuth);
}
