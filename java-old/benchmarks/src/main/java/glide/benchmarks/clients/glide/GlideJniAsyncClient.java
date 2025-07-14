/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.glide;

import io.valkey.glide.jni.client.GlideJniClient;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * A high-performance JNI-based Glide client with async capabilities for benchmarking.
 * This client bypasses Unix Domain Sockets and uses direct JNI calls for maximum performance.
 */
public class GlideJniAsyncClient implements AsyncClient<String> {
    private GlideJniClient jniClient;

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {
        String address = connectionSettings.host + ":" + connectionSettings.port;
        
        GlideJniClient.Config config = new GlideJniClient.Config(Arrays.asList(address))
                .useTls(connectionSettings.useSsl)
                .clusterMode(connectionSettings.clusterMode)
                .requestTimeout(5000); // 5 second timeout for benchmarks
        
        try {
            jniClient = new GlideJniClient(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JNI client: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        if (jniClient == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client not connected"));
        }
        
        return jniClient.set(key, value);
    }

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        if (jniClient == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client not connected"));
        }
        
        return jniClient.get(key);
    }

    @Override
    public void closeConnection() {
        if (jniClient != null) {
            try {
                jniClient.close();
            } catch (Exception e) {
                // Log warning but don't throw
                System.err.println("Warning: Error closing JNI client: " + e.getMessage());
            } finally {
                jniClient = null;
            }
        }
    }

    @Override
    public String getName() {
        return "glide-jni";
    }
    
    /**
     * Get the underlying JNI client for direct access if needed.
     * 
     * @return The GlideJniClient instance
     */
    public GlideJniClient getJniClient() {
        return jniClient;
    }
}