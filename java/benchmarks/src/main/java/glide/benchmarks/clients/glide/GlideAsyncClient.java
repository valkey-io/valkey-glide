/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.glide;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.valkey.glide.core.client.GlideClient;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** A Glide client with async capabilities */
public class GlideAsyncClient implements AsyncClient<String> {
    private GlideClient glideClient;

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {
        // Use our new JNI implementation directly
        GlideClient.Config config = new GlideClient.Config(
            java.util.Arrays.asList(connectionSettings.host + ":" + connectionSettings.port)
        );
        
        if (connectionSettings.useSsl) {
            config.useTls(true);
        }
        
        if (connectionSettings.clusterMode) {
            config.clusterMode(true);
        }

        try {
            glideClient = new GlideClient(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create JNI client", e);
        }
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        return glideClient.executeStringCommand("SET", new String[]{key, value});
    }

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        return glideClient.executeStringCommand("GET", new String[]{key});
    }

    @Override
    public void closeConnection() {
        if (glideClient != null) {
            glideClient.close();
        }
    }

    @Override
    public String getName() {
        return "glide";
    }
}
