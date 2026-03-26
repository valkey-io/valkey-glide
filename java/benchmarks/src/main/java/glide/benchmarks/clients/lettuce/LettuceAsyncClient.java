/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.benchmarks.clients.lettuce;

import glide.benchmarks.clients.AsyncClient;
import glide.benchmarks.utils.ConnectionSettings;
import io.lettuce.core.*;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisKeyAsyncCommands;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** A Lettuce client with async capabilities see: https://lettuce.io/ */
public class LettuceAsyncClient implements AsyncClient<String> {
    static final int ASYNC_OPERATION_TIMEOUT_SEC = 1;

    private AbstractRedisClient client;
    private RedisStringAsyncCommands<String, String> asyncCommands;
    private RedisKeyAsyncCommands<String, String> keyAsyncCommands; // <-- ADD

    private StatefulConnection<String, String> connection;

    @Override
    public void connectToValkey(ConnectionSettings connectionSettings) {

        RedisURI uri =
                RedisURI.builder()
                        .withHost(connectionSettings.host)
                        .withPort(connectionSettings.port)
                        .withSsl(connectionSettings.useSsl)
                        .withVerifyPeer(false)
                        .withTimeout(Duration.ofSeconds(5))
                        .build();
        SocketOptions socketOptions =
                SocketOptions.builder().connectTimeout(Duration.ofMillis(1000)).build();

        TimeoutOptions timeoutOptions =
                TimeoutOptions.builder().fixedTimeout(Duration.ofMillis(100)).build();

        if (!connectionSettings.clusterMode) {
            client = RedisClient.create(uri);
            connection = ((RedisClient) client).connect();
            var commands = ((StatefulRedisConnection<String, String>) connection).async();
            asyncCommands = commands;
            keyAsyncCommands = commands; // <-- ADD (same object, different interface)
        } else {
            ClusterTopologyRefreshOptions topologyRefreshOptions =
                    ClusterTopologyRefreshOptions.builder()
                            .enablePeriodicRefresh(Duration.ofSeconds(600))
                            .dynamicRefreshSources(false)
                            .build();

            ClusterClientOptions clusterClientOptions =
                    ClusterClientOptions.builder()
                            .socketOptions(socketOptions)
                            .timeoutOptions(timeoutOptions)
                            .topologyRefreshOptions(topologyRefreshOptions)
                            .build();

            RedisClusterClient clusterClient = RedisClusterClient.create(uri);
            clusterClient.setOptions(clusterClientOptions);
            client = clusterClient;
            connection = clusterClient.connect();
            ((StatefulRedisClusterConnection<String, String>) connection)
                    .setReadFrom(ReadFrom.MASTER_PREFERRED);
            var commands = ((StatefulRedisClusterConnection<String, String>) connection).async();
            asyncCommands = commands;
            keyAsyncCommands = commands; // <-- ADD (same object, different interface)
        }
        connection.setTimeout(Duration.ofMillis(100));
    }

    private static final long GET_TIMEOUT_MS = 100;
    private static final long SET_TIMEOUT_MS = 100;

    @Override
    public RedisFuture<String> asyncSet(String key, String value) {
        RedisFuture<String> future = asyncCommands.set(key, value);
        future.toCompletableFuture().orTimeout(SET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return future;
    }

    @Override
    public RedisFuture<Long> asyncDel(String[] keys) {
        RedisFuture<Long> future = keyAsyncCommands.del(keys); // <-- USE keyAsyncCommands
        future.toCompletableFuture().orTimeout(SET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return future;
    }

    @Override
    public RedisFuture<String> asyncGet(String key) {
        RedisFuture<String> future = asyncCommands.get(key);
        future.toCompletableFuture().orTimeout(GET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        return future;
    }

    @Override
    public void closeConnection() {
        connection.close();
        client.shutdown();
    }

    @Override
    public String getName() {
        return "lettuce";
    }
}
