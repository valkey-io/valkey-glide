/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;

import glide.api.commands.GenericClusterCommands;
import glide.api.models.ClusterValue;
import glide.api.models.configuration.RedisClusterClientConfiguration;
import glide.api.models.configuration.RequestRoutingConfiguration.Route;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Async (non-blocking) client for Redis in Cluster mode. Use {@link #CreateClient} to request a
 * client to Redis.
 */
public class RedisClusterClient extends BaseClient implements GenericClusterCommands {

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
    public CompletableFuture<ClusterValue<Object>> customCommand(String[] args) {
        // TODO if a command returns a map as a single value, ClusterValue misleads user
        return commandManager.submitNewCommand(
                CustomCommand, args, response -> ClusterValue.of(handleObjectResponse(response)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ClusterValue<Object>> customCommand(String[] args, Route route) {
        return commandManager.submitNewCommand(
                CustomCommand,
                args,
                route,
                response ->
                        route.isSingleNodeRoute()
                                ? ClusterValue.ofSingleValue(handleObjectResponse(response))
                                : ClusterValue.ofMultiValue((Map<String, Object>) handleObjectResponse(response)));
    }
}
