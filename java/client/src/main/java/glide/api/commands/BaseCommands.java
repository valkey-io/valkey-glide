package glide.api.commands;

import java.util.concurrent.CompletableFuture;

/** Base Commands interface to handle generic command and transaction requests. */
public interface BaseCommands {

    /**
     * Execute a @see{Command} by sending command via socket manager
     *
     * @param args arguments for the custom command
     * @return a CompletableFuture with response result from Redis
     */
    CompletableFuture<Object> customCommand(String[] args);
}
