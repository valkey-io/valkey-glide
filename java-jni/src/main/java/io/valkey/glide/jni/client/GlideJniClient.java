package io.valkey.glide.jni.client;

import java.lang.ref.Cleaner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * JNI client for POC benchmarking against UDS implementation.
 * <p>
 * This implementation provides the SAME API as the UDS BaseClient - returning
 * CompletableFuture for all operations to ensure fair performance comparison.
 * The goal is to measure the performance difference of JNI vs UDS+Protobuf
 * while maintaining identical async semantics.
 */
public class GlideJniClient implements AutoCloseable {
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
     * Command type constants for the POC
     */
    public static final class CommandType {
        public static final int GET = 1;
        public static final int SET = 2;
        public static final int PING = 3;
    }

    /**
     * Create a new GlideJniClient connected to Valkey
     *
     * @param host Valkey server hostname or IP address
     * @param port Valkey server port
     */
    public GlideJniClient(String host, int port) {
        if (host == null) {
            throw new IllegalArgumentException("Host cannot be null");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }        this.nativeClientPtr = connect(host, port);
        if (this.nativeClientPtr == 0) {
            throw new RuntimeException("Failed to connect to Valkey: " + host + ":" + port);
        }

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(this.nativeClientPtr);

        // Register cleanup action with Cleaner - modern replacement for finalize()
        this.cleanable = CLEANER.register(this, new CleanupAction(this.nativeState));
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

        // Execute asynchronously to match UDS BaseClient behavior
        return CompletableFuture.supplyAsync(() -> {
            byte[] result = executeCommand(nativeClientPtr, CommandType.GET, key.getBytes());
            return result != null ? new String(result) : null;
        }, ForkJoinPool.commonPool());
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

        // Execute asynchronously to match UDS BaseClient behavior
        return CompletableFuture.supplyAsync(() -> {
            // Simple payload format: key_length(4 bytes) + key + value
            byte[] keyBytes = key.getBytes();
            byte[] valueBytes = value.getBytes();
            byte[] payload = new byte[4 + keyBytes.length + valueBytes.length];

            // Write key length
            payload[0] = (byte) (keyBytes.length >>> 24);
            payload[1] = (byte) (keyBytes.length >>> 16);
            payload[2] = (byte) (keyBytes.length >>> 8);
            payload[3] = (byte) keyBytes.length;

            // Write key
            System.arraycopy(keyBytes, 0, payload, 4, keyBytes.length);

            // Write value
            System.arraycopy(valueBytes, 0, payload, 4 + keyBytes.length, valueBytes.length);

            byte[] result = executeCommand(nativeClientPtr, CommandType.SET, payload);
            return new String(result); // Should be "OK"
        }, ForkJoinPool.commonPool());
    }

    /**
     * Execute a PING command - matches BaseClient API
     *
     * @return CompletableFuture with "PONG" response
     */
    public CompletableFuture<String> ping() {
        checkNotClosed();

        // Execute asynchronously to match UDS BaseClient behavior
        return CompletableFuture.supplyAsync(() -> {
            byte[] result = executeCommand(nativeClientPtr, CommandType.PING, new byte[0]);
            return new String(result); // Should be "PONG"
        }, ForkJoinPool.commonPool());
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
                disconnect(ptr);
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

    // Native method declarations - match the Rust function signatures

    /**
     * Connect to Valkey and create a native client
     *
     * @param host Valkey server hostname or IP address
     * @param port Valkey server port
     * @return native pointer to client instance
     */
    private static native long connect(String host, int port);

    /**
     * Disconnect and release a native client
     *
     * @param clientPtr native pointer to client instance
     */
    private static native void disconnect(long clientPtr);

    /**
     * Execute a command - core function for POC benchmarking
     *
     * @param clientPtr native pointer to client instance
     * @param commandType command type (GET=1, SET=2, PING=3)
     * @param payload command payload as bytes
     * @return response as byte array
     */
    private static native byte[] executeCommand(long clientPtr, int commandType, byte[] payload);
}
