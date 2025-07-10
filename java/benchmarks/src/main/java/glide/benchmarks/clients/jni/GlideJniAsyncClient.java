/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.jni;

import java.util.concurrent.CompletableFuture;
import java.util.Arrays;

import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import io.valkey.glide.jni.GlideJniClient;

/**
 * High-performance JNI-based Glide client for benchmarking.
 *
 * <p>This client provides direct JNI calls to glide-core instead of UDS+Protobuf 
 * communication, eliminating serialization overhead and IPC latency for maximum performance.
 */
public class GlideJniAsyncClient implements AsyncClient<String> {

    /** The underlying JNI client */
    private GlideJniClient jniClient;

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {
        if (connectionSettings.useSsl) {
            throw new UnsupportedOperationException("JNI client does not support SSL in this implementation");
        }
        if (connectionSettings.clusterMode) {
            throw new UnsupportedOperationException("JNI client does not support cluster mode in this implementation");
        }

        // Create configuration for the new JNI client
        GlideJniClient.Config config = new GlideJniClient.Config(
            Arrays.asList(connectionSettings.host + ":" + connectionSettings.port)
        )
        .databaseId(0)  // Use default database
        .requestTimeout(250);  // Match benchmark timeout

        this.jniClient = new GlideJniClient(config);
        
        // Verify connection with a ping
        try {
            String pong = jniClient.ping();
            if (!"PONG".equals(pong)) {
                throw new RuntimeException("Failed to connect - unexpected ping response: " + pong);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Valkey server", e);
        }
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        if (jniClient == null) {
            throw new IllegalStateException("Client not connected");
        }
        return jniClient.setAsync(key, value).thenApply(success -> success ? "OK" : null);
    }

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        if (jniClient == null) {
            throw new IllegalStateException("Client not connected");
        }
        return jniClient.getAsync(key);
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
        return "glide-jni-direct";
    }

    /** Check if the client is closed */
    public boolean isClosed() {
        return jniClient == null;
    }

    /** Test method for JNI loading and basic functionality */
    public static void main(String[] args) {
        System.out.println("Testing JNI client...");
        try {
            GlideJniAsyncClient client = new GlideJniAsyncClient();
            ConnectionSettings settings = new ConnectionSettings("127.0.0.1", 6379, false, false);

            System.out.println("Connecting to Valkey...");
            client.connectToValkey(settings);
            System.out.println("JNI connect successful!");

            // Test basic operations
            System.out.println("Testing SET operation...");
            String setResult = client.waitForResult(client.asyncSet("test_key", "test_value"));
            System.out.println("SET result: " + setResult);

            System.out.println("Testing GET operation...");
            String getValue = client.waitForResult(client.asyncGet("test_key"));
            System.out.println("GET result: " + getValue);

            if ("test_value".equals(getValue)) {
                System.out.println("JNI client test PASSED!");
            } else {
                System.err.println("JNI client test FAILED - expected 'test_value', got: " + getValue);
            }

            client.closeConnection();
            System.out.println("JNI disconnect successful!");
        } catch (Exception e) {
            System.err.println("JNI test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}