/** Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.api;

import static redis_request.RedisRequestOuterClass.RequestType.CustomCommand;
import static redis_request.RedisRequestOuterClass.RequestType.Info;

import glide.api.commands.GenericCommands;
import glide.api.commands.ServerManagementCommands;
import glide.api.models.Transaction;
import glide.api.models.commands.InfoOptions;
import glide.api.models.configuration.RedisClientConfiguration;
import glide.managers.CommandManager;
import glide.managers.ConnectionManager;
import java.util.concurrent.CompletableFuture;
import lombok.NonNull;

/**
 * Async (non-blocking) client for Redis in Standalone mode. Use {@link #CreateClient} to request a
 * client to Redis.
 */
public class RedisClient extends BaseClient implements GenericCommands, ServerManagementCommands {

    protected RedisClient(ConnectionManager connectionManager, CommandManager commandManager) {
        super(connectionManager, commandManager);
    }

    /**
     * Async request for an async (non-blocking) Redis client in Standalone mode.
     *
     * @param config Redis client Configuration
     * @return A Future to connect and return a RedisClient
     */
    public static CompletableFuture<RedisClient> CreateClient(
            @NonNull RedisClientConfiguration config) {
        return CreateClient(config, RedisClient::new);
    }

    @Override
    public CompletableFuture<Object> customCommand(@NonNull String[] args) {
        return commandManager.submitNewCommand(CustomCommand, args, this::handleObjectOrNullResponse);
    }

    @Override
    public CompletableFuture<Object[]> exec(Transaction transaction) {
        return commandManager.submitNewCommand(transaction, this::handleArrayOrNullResponse);
    }

    @Override
    public CompletableFuture<String> info() {
        return commandManager.submitNewCommand(Info, new String[0], this::handleStringResponse);
    }

    @Override
    public CompletableFuture<String> info(@NonNull InfoOptions options) {
        return commandManager.submitNewCommand(Info, options.toArgs(), this::handleStringResponse);
    }
}
