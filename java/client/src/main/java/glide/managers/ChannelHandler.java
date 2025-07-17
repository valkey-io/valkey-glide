/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.Response;
import glide.protobuf.CommandRequest;
import glide.protobuf.ConnectionRequestOuterClass;
import java.util.concurrent.CompletableFuture;

/**
 * Compatibility stub for ChannelHandler from the old UDS implementation.
 * This provides basic compatibility for tests that reference the old architecture.
 */
public class ChannelHandler {
    
    protected CallbackDispatcher callbackDispatcher;
    
    /**
     * Compatibility stub for channel handler.
     */
    public ChannelHandler() {
        // Basic initialization
        this.callbackDispatcher = new CallbackDispatcher();
    }
    
    /**
     * Compatibility constructor for tests.
     */
    public ChannelHandler(CallbackDispatcher callbackDispatcher, Object socket, Object threadPoolResource) {
        this.callbackDispatcher = callbackDispatcher;
    }
    
    /**
     * Compatibility stub for getting channel handler.
     */
    public static ChannelHandler getInstance() {
        return new ChannelHandler();
    }
    
    /**
     * Compatibility stub for sending command.
     */
    public Object sendCommand(Object command) {
        // Return null for compatibility
        return null;
    }
    
    /**
     * Compatibility stub for closing channel.
     */
    public ChannelFuture close() {
        // Return a completed future for compatibility
        return new ChannelFuture(CompletableFuture.completedFuture(null));
    }
    
    /**
     * Compatibility stub for writing command request.
     */
    public CompletableFuture<Response> write(CommandRequest.Builder request, boolean flush) {
        // Return a completed future for compatibility
        return CompletableFuture.completedFuture(new Response("OK"));
    }
    
    /**
     * Compatibility stub for connecting.
     */
    public CompletableFuture<Response> connect(ConnectionRequestOuterClass.ConnectionRequest request) {
        // Return a completed future for compatibility
        return CompletableFuture.completedFuture(new Response("OK"));
    }
}