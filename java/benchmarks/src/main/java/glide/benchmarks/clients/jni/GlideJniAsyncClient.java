/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.jni;

import java.io.InputStream;
import java.lang.ref.Cleaner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;

/**
 * JNI-based Glide client for benchmarking against UDS implementation.
 *
 * This client provides identical API as GlideAsyncClient but uses direct JNI calls
 * instead of UDS+Protobuf communication for performance comparison.
 */
public class GlideJniAsyncClient implements AsyncClient<String> {
    private static final Cleaner CLEANER = Cleaner.create();

    static {
        System.out.println("Valkey-GLIDE JNI async client");
        try {
            // Try to load from JAR resources first (similar to how GLIDE loads its library)
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("linux")) {
                try (InputStream is = GlideJniAsyncClient.class.getResourceAsStream("/libglidejni.so")) {
                    if (is != null) {
                        // Create a temporary file and copy the library
                        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("libglidejni", ".so");
                        java.nio.file.Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        tempFile.toFile().deleteOnExit();
                        System.load(tempFile.toAbsolutePath().toString());
                        System.out.println("Successfully loaded JNI library from resources");
                    } else {
                        // Fallback to system library path
                        System.loadLibrary("glidejni");
                        System.out.println("Successfully loaded JNI library from system path");
                    }
                }
            } else {
                throw new UnsupportedOperationException("OS not supported. JNI client is only available on Linux systems.");
            }
        } catch (Exception e) {
            System.err.println("Failed to load JNI native library: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Native pointer to the client instance in Rust
     */
    private volatile long nativeClientPtr;

    /**
     * Cleaner resource to ensure native cleanup
     */
    private Cleaner.Cleanable cleanable;

    /**
     * Shared state for cleanup coordination
     */
    private NativeState nativeState;

    /**
     * Command type constants for the POC
     */
    public static final class CommandType {
        public static final int GET = 1;
        public static final int SET = 2;
        public static final int PING = 3;
    }

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {
        if (connectionSettings.useSsl) {
            throw new UnsupportedOperationException("JNI client does not support SSL in POC");
        }
        if (connectionSettings.clusterMode) {
            throw new UnsupportedOperationException("JNI client does not support cluster mode in POC");
        }

        this.nativeClientPtr = connect(connectionSettings.host, connectionSettings.port);
        if (this.nativeClientPtr == 0) {
            throw new RuntimeException("Failed to connect to Valkey: " +
                connectionSettings.host + ":" + connectionSettings.port);
        }

        // Create shared state for proper cleanup coordination
        this.nativeState = new NativeState(this.nativeClientPtr);

        // Register cleanup action with Cleaner - modern replacement for finalize()
        this.cleanable = CLEANER.register(this, new CleanupAction(this.nativeState));
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }

        checkNotClosed();

        // Execute asynchronously to match existing client behavior
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

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }

        checkNotClosed();

        // Execute asynchronously to match existing client behavior
        return CompletableFuture.supplyAsync(() -> {
            byte[] result = executeCommand(nativeClientPtr, CommandType.GET, key.getBytes());
            return result != null ? new String(result) : null;
        }, ForkJoinPool.commonPool());
    }

    @Override
    public void closeConnection() {
        long ptr = nativeClientPtr;
        if (ptr != 0) {
            nativeClientPtr = 0;
            // Safely cleanup using shared state
            nativeState.cleanup();
            cleanable.clean(); // This will be a no-op since state is already cleaned
        }
    }

    @Override
    public String getName() {
        return "glide-jni";
    }

    /**
     * Check if the client is closed
     */
    public boolean isClosed() {
        return nativeClientPtr == 0;
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
     */
    private static native long connect(String host, int port);

    /**
     * Disconnect and release a native client
     */
    private static native void disconnect(long clientPtr);

    /**
     * Execute a command - core function for POC benchmarking
     */
    private static native byte[] executeCommand(long clientPtr, int commandType, byte[] payload);
}
