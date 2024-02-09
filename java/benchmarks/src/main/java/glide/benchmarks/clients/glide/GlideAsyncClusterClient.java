/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.glide;

import glide.api.RedisClient;
import glide.api.RedisClusterClient;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** A Glide client with async capabilities */
public class GlideAsyncClusterClient implements AsyncClient<String> {
    private RedisClusterClient redisClient;

    @Override
    public void connectToRedis(ConnectionSettings connectionSettings) {
        if (!connectionSettings.clusterMode) {
            throw new RuntimeException("Use client GlideAsyncClient");
        }
        RedisClusterClientConfiguration config =
            RedisClusterClientConfiguration.builder()
                        .address(
                                NodeAddress.builder()
                                        .host(connectionSettings.host)
                                        .port(connectionSettings.port)
                                        .build())
                        .useTLS(connectionSettings.useSsl)
                        .build();

        try {
            redisClient = RedisClusterClient.CreateClient(config).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public CompletableFuture<String> asyncSet(String key, String value) {
        return redisClient.customCommand(new String[] {"Set", key, value}).thenApply(r -> "Ok");
    }

    @Override
    public CompletableFuture<String> asyncGet(String key) {
        return redisClient.customCommand(new String[] {"Get", key}).thenApply(r -> (String) r.getSingleValue());
    }

    @Override
    public void closeConnection() {
        try {
            redisClient.close();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "Glide Cluster Async";
    }
}
