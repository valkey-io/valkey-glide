package io.valkey.glide.managers;

import io.valkey.glide.core.client.GlideClient;
import io.valkey.glide.core.commands.CommandType;

import java.util.concurrent.CompletableFuture;

/**
 * Command manager that provides direct access to glide-core.
 */
public class CommandManager {

    private final GlideClient client;

    public CommandManager(GlideClient client) {
        this.client = client;
    }

    /**
     * Execute a command that returns a String response.
     *
     * @param commandType The command type to execute
     * @param arguments Command arguments
     * @return A CompletableFuture containing the String response
     */
    public CompletableFuture<String> executeStringCommand(
            CommandType commandType,
            String[] arguments) {
        return client.executeStringCommand(commandType.toString(), arguments);
    }

    /**
     * Execute a command that returns a Long response.
     *
     * @param commandType The command type to execute
     * @param arguments Command arguments
     * @return A CompletableFuture containing the Long response
     */
    public CompletableFuture<Long> executeLongCommand(
            CommandType commandType,
            String[] arguments) {
        return client.executeLongCommand(commandType.toString(), arguments);
    }

    /**
     * Execute a command that returns a Double response.
     *
     * @param commandType The command type to execute
     * @param arguments Command arguments
     * @return A CompletableFuture containing the Double response
     */
    public CompletableFuture<Double> executeDoubleCommand(
            CommandType commandType,
            String[] arguments) {
        return client.executeDoubleCommand(commandType.toString(), arguments);
    }

    /**
     * Execute a command that returns a Boolean response.
     *
     * @param commandType The command type to execute
     * @param arguments Command arguments
     * @return A CompletableFuture containing the Boolean response
     */
    public CompletableFuture<Boolean> executeBooleanCommand(
            CommandType commandType,
            String[] arguments) {
        return client.executeBooleanCommand(commandType.toString(), arguments);
    }

    /**
     * Execute a command that returns an Object array response.
     *
     * @param commandType The command type to execute
     * @param arguments Command arguments
     * @return A CompletableFuture containing the Object array response
     */
    public CompletableFuture<Object[]> executeArrayCommand(
            CommandType commandType,
            String[] arguments) {
        return client.executeArrayCommand(commandType.toString(), arguments);
    }

    /**
     * Execute a command that returns an Object response (for mixed/complex types).
     *
     * @param commandType The command type to execute
     * @param arguments Command arguments
     * @return A CompletableFuture containing the Object response
     */
    public CompletableFuture<Object> executeObjectCommand(
            CommandType commandType,
            String[] arguments) {
        return client.executeObjectCommand(commandType.toString(), arguments);
    }
}
