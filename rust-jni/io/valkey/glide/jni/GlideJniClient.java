package io.valkey.glide.jni;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-performance JNI client for Valkey GLIDE that provides direct integration
 * with the Rust glide-core library, eliminating Unix Domain Socket overhead.
 */
public class GlideJniClient implements AutoCloseable {
    
    static {
        System.loadLibrary("glidejni");
    }
    
    private final long clientPtr;
    private final ExecutorService executor;
    private volatile boolean closed = false;
    
    /**
     * Configuration for creating a new GlideJniClient.
     */
    public static class Config {
        private final List<String> addresses;
        private int databaseId = -1;
        private String username;
        private String password;
        private boolean useTls = false;
        private boolean clusterMode = false;
        private int requestTimeoutMs = 250;
        
        public Config(List<String> addresses) {
            this.addresses = addresses;
        }
        
        public Config databaseId(int databaseId) {
            this.databaseId = databaseId;
            return this;
        }
        
        public Config credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
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
    }
    
    /**
     * Create a new GlideJniClient with the specified configuration.
     * 
     * @param config Configuration for the client
     * @throws RuntimeException if client creation fails
     */
    public GlideJniClient(Config config) {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "glide-jni-async");
            t.setDaemon(true);
            return t;
        });
        
        this.clientPtr = createClient(
            config.addresses.toArray(new String[0]),
            config.databaseId,
            config.username,
            config.password,
            config.useTls,
            config.clusterMode,
            config.requestTimeoutMs
        );
        
        if (this.clientPtr == 0) {
            throw new RuntimeException("Failed to create JNI client");
        }
    }
    
    /**
     * Execute GET command synchronously.
     * 
     * @param key The key to get
     * @return The value associated with the key, or null if not found
     * @throws RuntimeException if the operation fails
     */
    public String get(String key) {
        checkClosed();
        return get(clientPtr, key);
    }
    
    /**
     * Execute GET command asynchronously.
     * 
     * @param key The key to get
     * @return CompletableFuture that completes with the value or null
     */
    public CompletableFuture<String> getAsync(String key) {
        checkClosed();
        return CompletableFuture.supplyAsync(() -> get(key), executor);
    }
    
    /**
     * Execute SET command synchronously.
     * 
     * @param key The key to set
     * @param value The value to set
     * @return true if successful
     * @throws RuntimeException if the operation fails
     */
    public boolean set(String key, String value) {
        checkClosed();
        return set(clientPtr, key, value);
    }
    
    /**
     * Execute SET command asynchronously.
     * 
     * @param key The key to set
     * @param value The value to set
     * @return CompletableFuture that completes with true if successful
     */
    public CompletableFuture<Boolean> setAsync(String key, String value) {
        checkClosed();
        return CompletableFuture.supplyAsync(() -> set(key, value), executor);
    }
    
    /**
     * Execute DEL command synchronously.
     * 
     * @param key The key to delete
     * @return The number of keys deleted
     * @throws RuntimeException if the operation fails
     */
    public int del(String key) {
        checkClosed();
        return del(clientPtr, key);
    }
    
    /**
     * Execute DEL command asynchronously.
     * 
     * @param key The key to delete
     * @return CompletableFuture that completes with the number of keys deleted
     */
    public CompletableFuture<Integer> delAsync(String key) {
        checkClosed();
        return CompletableFuture.supplyAsync(() -> del(key), executor);
    }
    
    /**
     * Execute PING command synchronously.
     * 
     * @return "PONG" response
     * @throws RuntimeException if the operation fails
     */
    public String ping() {
        checkClosed();
        return ping(clientPtr);
    }
    
    /**
     * Execute PING command asynchronously.
     * 
     * @return CompletableFuture that completes with "PONG" response
     */
    public CompletableFuture<String> pingAsync() {
        checkClosed();
        return CompletableFuture.supplyAsync(() -> ping(), executor);
    }
    
    /**
     * Close the client and free resources.
     */
    @Override
    public void close() {
        if (!closed) {
            closed = true;
            executor.shutdown();
            if (clientPtr != 0) {
                closeClient(clientPtr);
            }
        }
    }
    
    /**
     * Check if the client is closed and throw an exception if it is.
     */
    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("Client is closed");
        }
    }
    
    // Native method declarations
    private static native long createClient(
        String[] addresses,
        int databaseId,
        String username,
        String password,
        boolean useTls,
        boolean clusterMode,
        int requestTimeoutMs
    );
    
    private static native void closeClient(long clientPtr);
    
    private static native String get(long clientPtr, String key);
    
    private static native boolean set(long clientPtr, String key, String value);
    
    private static native int del(long clientPtr, String key);
    
    private static native String ping(long clientPtr);
}