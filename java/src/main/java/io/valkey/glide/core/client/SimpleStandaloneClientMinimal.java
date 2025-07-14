package io.valkey.glide.core.client;

import io.valkey.glide.core.client.GlideClient;
import io.valkey.glide.core.commands.Command;
import io.valkey.glide.core.commands.CommandType;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal version of SimpleStandaloneClient for testing the refactored architecture.
 */
public class SimpleStandaloneClientMinimal {
    private final GlideClient client;
    
    public SimpleStandaloneClientMinimal(GlideClient client) {
        this.client = client;
    }
    
    /**
     * Create a new standalone client with default configuration.
     */
    public static SimpleStandaloneClientMinimal create() {
        // For now, just create a client with localhost:6379
        try {
            GlideClient client = new GlideClient("localhost", 6379);
            return new SimpleStandaloneClientMinimal(client);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create client", e);
        }
    }
    
    /**
     * Execute a GET command.
     */
    public CompletableFuture<String> get(String key) {
        return client.executeCommand(new Command(CommandType.GET, key))
                .thenApply(result -> result != null ? result.toString() : null);
    }
    
    /**
     * Execute a SET command.
     */
    public CompletableFuture<String> set(String key, String value) {
        return client.executeCommand(new Command(CommandType.SET, key, value))
                .thenApply(result -> result != null ? result.toString() : null);
    }
    
    /**
     * Execute a PING command.
     */
    public CompletableFuture<String> ping() {
        return client.executeCommand(new Command(CommandType.PING))
                .thenApply(result -> result != null ? result.toString() : "PONG");
    }
    
    /**
     * Close the client.
     */
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            // Log the error but don't throw
            System.err.println("Error closing client: " + e.getMessage());
        }
    }
}
