/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.internal.GlideCoreClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;

/**
 * Connection manager that provides direct JNI calls to the Rust glide-core library.
 * This replaces the previous UDS-based ChannelHandler approach with improved performance
 * and Windows compatibility while maintaining the same interface.
 */
@RequiredArgsConstructor
public class ConnectionManager {

    private GlideCoreClient coreClient;
    private volatile boolean closed = false;

    /**
     * Make a connection request to Valkey Rust-core client using JNI.
     *
     * @param configuration Connection Request Configuration
     * @return CompletableFuture that completes when connection is established
     */
    public CompletableFuture<Void> connectToValkey(BaseClientConfiguration configuration) {
        if (closed) {
            return CompletableFuture.failedFuture(new ClosingException("Connection manager is closed"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Create the core client with JNI - this establishes the connection
                coreClient = new GlideCoreClient(configuration);
                
                // Verify connection was established
                if (!coreClient.isConnected()) {
                    throw new RuntimeException("Failed to establish connection to Valkey");
                }
                
                return null; // Success, return Void
            } catch (Exception e) {
                // Handle connection failures
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Failed to connect to Valkey", e);
            }
        });
    }

    /**
     * Get the core client instance.
     * This is used by CommandManager to access the native client handle.
     *
     * @return GlideCoreClient instance
     * @throws IllegalStateException if not connected
     */
    public GlideCoreClient getCoreClient() {
        if (coreClient == null) {
            throw new IllegalStateException("Not connected - call connectToValkey first");
        }
        if (closed) {
            throw new IllegalStateException("Connection manager is closed");
        }
        return coreClient;
    }

    /**
     * Check if the connection manager is closed.
     *
     * @return True if closed
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Close the connection and release resources.
     *
     * @return CompletableFuture that completes when connection is closed
     */
    public Future<Void> close() {
        return CompletableFuture.runAsync(() -> {
            if (!closed) {
                closed = true;
                if (coreClient != null) {
                    coreClient.close();
                    coreClient = null;
                }
            }
        });
    }

    /**
     * Exception handler for connection errors.
     *
     * @param e Exception that occurred
     * @return Never returns, always rethrows
     */
    private Void exceptionHandler(Throwable e) {
        close();
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        throw new RuntimeException(e);
    }

    /**
     * Factory method to create a ConnectionManager.
     *
     * @return New ConnectionManager instance
     */
    public static ConnectionManager create() {
        return new ConnectionManager();
    }
}
