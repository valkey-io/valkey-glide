package io.valkey.glide.jni.managers;

import io.valkey.glide.jni.client.GlideJniClient;
import io.valkey.glide.jni.commands.CommandType;

import java.util.concurrent.CompletableFuture;

/**
 * JNI-based implementation of CommandManager that replaces UDS communication
 * with direct JNI calls to glide-core.
 */
public class JniCommandManager {
    
    private final GlideJniClient jniClient;
    
    public JniCommandManager(GlideJniClient jniClient) {
        this.jniClient = jniClient;
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
        return jniClient.executeStringCommand(commandType.getCommandName(), arguments);
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
        return jniClient.executeLongCommand(commandType.getCommandName(), arguments);
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
        return jniClient.executeDoubleCommand(commandType.getCommandName(), arguments);
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
        return jniClient.executeBooleanCommand(commandType.getCommandName(), arguments);
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
        return jniClient.executeArrayCommand(commandType.getCommandName(), arguments);
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
        return jniClient.executeObjectCommand(commandType.getCommandName(), arguments);
    }
}
