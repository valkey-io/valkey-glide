/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.configuration.BaseClientConfiguration;
import glide.api.models.configuration.GlideClusterClientConfiguration;
import glide.api.models.exceptions.ClosingException;
import glide.internal.AsyncRegistry;
import glide.internal.GlideNativeBridge;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;

/**
 * JNI-based ConnectionManager that wraps GlideNativeBridge. Provides the same interface as UDS
 * ConnectionManager but uses JNI instead of Unix Domain Sockets.
 */
@RequiredArgsConstructor
public class ConnectionManager {

    /** Native client handle for JNI operations */
    private long nativeClientHandle = 0;

    private int maxInflightRequests = 0;
    private int requestTimeoutMs = 5000;
    private volatile boolean isClosed = false;

    /**
     * Connect to Valkey using JNI bridge instead of UDS.
     *
     * @param configuration Connection Configuration
     * @return CompletableFuture that completes when connection is established
     */
    public CompletableFuture<Void> connectToValkey(BaseClientConfiguration configuration) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        // Convert addresses to simple string array
                        String[] addresses =
                                configuration.getAddresses().stream()
                                        .map(addr -> addr.getHost() + ":" + addr.getPort())
                                        .toArray(String[]::new);

                        // Extract credentials
                        String username = null;
                        String password = null;
                        if (configuration.getCredentials() != null) {
                            username = configuration.getCredentials().getUsername();
                            password = configuration.getCredentials().getPassword();
                        }

                        // Determine client type
                        boolean isCluster = configuration instanceof GlideClusterClientConfiguration;

                        // Extract configuration values using core defaults
                        this.maxInflightRequests =
                                configuration.getInflightRequestsLimit() != null
                                        ? configuration.getInflightRequestsLimit()
                                        : GlideNativeBridge.getGlideCoreDefaultMaxInflightRequests();
                        this.requestTimeoutMs =
                                configuration.getRequestTimeout() != null
                                        ? configuration.getRequestTimeout()
                                        : (int) GlideNativeBridge.getGlideCoreDefaultTimeoutMs();

                        // Create native client through JNI bridge
                        this.nativeClientHandle =
                                GlideNativeBridge.createClient(
                                        addresses,
                                        configuration.getDatabaseId() != null ? configuration.getDatabaseId() : 0,
                                        username,
                                        password,
                                        configuration.isUseTLS(),
                                        false, // insecureTls - default value
                                        isCluster,
                                        requestTimeoutMs,
                                        (int)
                                                GlideNativeBridge
                                                        .getGlideCoreDefaultTimeoutMs(), // connectionTimeoutMs - core default
                                        maxInflightRequests,
                                        configuration.getReadFrom() != null
                                                ? configuration.getReadFrom().name()
                                                : null, // readFrom
                                        configuration.getClientAZ(), // clientAz
                                        configuration.isLazyConnect(), // lazyConnect
                                        configuration.getClientName());

                        if (nativeClientHandle == 0) {
                            throw new ClosingException("Failed to create client - connection refused");
                        }

                        return null; // Success
                    } catch (Exception e) {
                        if (e instanceof ClosingException) {
                            throw e;
                        }
                        throw new RuntimeException("Failed to create JNI client", e);
                    }
                });
    }

    /**
     * Close the connection.
     *
     * @return Future that completes when connection is closed
     */
    public Future<Void> closeConnection() {
        return CompletableFuture.supplyAsync(
                () -> {
                    if (!isClosed && nativeClientHandle != 0) {
                        try {
                            // Clean up any pending async operations for this client
                            AsyncRegistry.cleanupClient(nativeClientHandle);
                            GlideNativeBridge.closeClient(nativeClientHandle);
                        } finally {
                            isClosed = true;
                            nativeClientHandle = 0;
                        }
                    }
                    return null;
                });
    }

    /** Close the connection immediately (synchronous version). */
    public void closeConnectionSync() {
        if (isClosed) {
            return; // Already closed
        }

        try {
            // Mark as closed immediately to prevent new commands
            isClosed = true;

            // Clean up any pending async operations for this client
            AsyncRegistry.cleanupClient(nativeClientHandle);

            // Close the native client handle
            if (nativeClientHandle != 0) {
                GlideNativeBridge.closeClient(nativeClientHandle);
                nativeClientHandle = 0;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to close client", e);
        }
    }

    /** Get the native client handle for use by CommandManager. */
    public long getNativeClientHandle() {
        return nativeClientHandle;
    }

    /** Get max inflight requests setting. */
    public int getMaxInflightRequests() {
        return maxInflightRequests;
    }

    /** Get request timeout setting. */
    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    /** Check if the connection is closed. */
    public boolean isClosed() {
        return isClosed;
    }

    /** Check if the client is connected and ready for commands. */
    public boolean isConnected() {
        if (isClosed || nativeClientHandle == 0) {
            return false;
        }
        try {
            return GlideNativeBridge.isConnected(nativeClientHandle);
        } catch (Exception e) {
            return false;
        }
    }

    /** Get client information from the native layer. */
    public String getClientInfo() {
        if (isClosed || nativeClientHandle == 0) {
            throw new IllegalStateException("Client is closed");
        }
        return GlideNativeBridge.getClientInfo(nativeClientHandle);
    }
}
