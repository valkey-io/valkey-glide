/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.jni;

import java.util.concurrent.CompletableFuture;

import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import io.valkey.glide.jni.client.GlideJniClient;

/**
 * JNI-based Glide client for benchmarking against UDS implementation.
 *
 * <p>This client provides identical API as GlideAsyncClient but uses direct JNI calls instead of
 * UDS+Protobuf communication for performance comparison.
 */
public class GlideJniAsyncClient implements AsyncClient<String> {

    /** The underlying JNI client */
    private GlideJniClient jniClient;

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {
        if (connectionSettings.useSsl) {
            throw new UnsupportedOperationException("JNI client does not support SSL in POC");
        }
        if (connectionSettings.clusterMode) {
            throw new UnsupportedOperationException("JNI client does not support cluster mode in POC");
        }

        // Use the existing GlideJniClient
        this.jniClient = new GlideJniClient(connectionSettings.host, connectionSettings.port);
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        if (jniClient == null) {
            throw new IllegalStateException("Client not connected");
        }
        return jniClient.set(key, value);
    }

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        if (jniClient == null) {
            throw new IllegalStateException("Client not connected");
        }
        return jniClient.get(key);
    }

    @Override
    public void closeConnection() {
        if (jniClient != null) {
            try {
                jniClient.close();
            } catch (Exception e) {
                // Log and ignore close errors
                System.err.println("Error closing JNI client: " + e.getMessage());
            } finally {
                jniClient = null;
            }
        }
    }

    @Override
    public String getName() {
        return "glide-jni";
    }

    /** Check if the client is closed */
    public boolean isClosed() {
        return jniClient == null || jniClient.isClosed();
    } // Test method for JNI loading
    public static void main(String[] args) {
        System.out.println("Testing JNI loading...");
        try {
            GlideJniAsyncClient client = new GlideJniAsyncClient();
            ConnectionSettings settings = new ConnectionSettings("127.0.0.1", 6379, false, false);

            client.connectToValkey(settings);
            System.out.println("JNI connect successful!");

            client.closeConnection();
            System.out.println("JNI disconnect successful!");
        } catch (Exception e) {
            System.err.println("JNI test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
