/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Info;

import glide.api.commands.ClusterBaseCommands;
import glide.api.commands.ClusterServerCommands;
import glide.api.models.ClusterValue;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/**
 * Async (non-blocking) client for Redis in Cluster mode. Use {@link #CreateClient} to request a
 * client to Redis.
 */
public class RedisClusterClient extends BaseClient
        implements ClusterBaseCommands, ClusterServerCommands {

    protected RedisClusterClient(ConnectionManager connectionManager, CommandManager commandManager) {
        super(connectionManager, commandManager);
    }

    /**
     * Async request for an async (non-blocking) Redis client in Cluster mode.
     *
     * @param config Redis cluster client Configuration
     * @return A Future to connect and return a RedisClusterClient
     */
    public static CompletableFuture<RedisClusterClient> CreateClient(
            RedisClusterClientConfiguration config) {
        return CreateClient(config, RedisClusterClient::new);
    }

    @Override
    public CompletableFuture<ClusterValue<Object>> customCommand(String... args) {
        // TODO if a command returns a map as a single value, ClusterValue misleads user
        return commandManager.submitNewCommand(
                CustomCommand,
                args,
                Optional.empty(),
                response -> ClusterValue.of(handleObjectResponse(response)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ClusterValue<Object>> customCommand(
            @NonNull Route route, String... args) {
        return commandManager.submitNewCommand(
                CustomCommand,
                args,
                Optional.of(route),
                response ->
                        route.isSingleNodeRoute()
                                ? ClusterValue.ofSingleValue(handleObjectResponse(response))
                                : ClusterValue.ofMultiValue((Map<String, Object>) handleObjectResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info() {
        return commandManager.submitNewCommand(
                Info,
                new String[0],
                Optional.empty(),
                response -> ClusterValue.of(handleObjectResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(@NonNull Route route) {
        return commandManager.submitNewCommand(
                Info,
                new String[0],
                Optional.of(route),
                response -> ClusterValue.of(handleObjectResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(InfoOptions options) {
        return commandManager.submitNewCommand(
                Info,
                options.toArgs(),
                Optional.empty(),
                response -> ClusterValue.of(handleObjectResponse(response)));
    }

    @Override
    public CompletableFuture<ClusterValue<String>> info(InfoOptions options, @NonNull Route route) {
        return commandManager.submitNewCommand(
                Info,
                options.toArgs(),
                Optional.of(route),
                response -> ClusterValue.of(handleObjectResponse(response)));
    }
}
