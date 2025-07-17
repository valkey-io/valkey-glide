/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import glide.api.models.Response;
import java.util.concurrent.CompletableFuture;

/**
 * Compatibility stub for CallbackDispatcher from the old UDS implementation.
 * This provides basic compatibility for tests that reference the old architecture.
 */
public class CallbackDispatcher {
    
    /**
     * Compatibility stub for callback dispatcher.
     */
    public CallbackDispatcher() {
        // Basic initialization
    }
    
    /**
     * Compatibility stub for getting callback dispatcher.
     */
    public static CallbackDispatcher getInstance() {
        return new CallbackDispatcher();
    }
    
    /**
     * Compatibility stub for registering callback.
     */
    public void registerCallback(String id, Object callback) {
        // No-op for compatibility
    }
    
    /**
     * Compatibility stub for unregistering callback.
     */
    public void unregisterCallback(String id) {
        // No-op for compatibility
    }
    
    /**
     * Compatibility stub for dispatching callback.
     */
    public void dispatch(String id, Object result) {
        // No-op for compatibility
    }
    
    /**
     * Compatibility stub for registering request.
     */
    public RequestId registerRequest() {
        return new RequestId();
    }
    
    /**
     * Compatibility stub for registering connection.
     */
    public CompletableFuture<Response> registerConnection() {
        return CompletableFuture.completedFuture(new Response("OK"));
    }
    
    /**
     * Compatibility stub for RequestId.
     */
    public static class RequestId {
        private final CompletableFuture<Response> future;
        
        public RequestId() {
            this.future = CompletableFuture.completedFuture(new Response("OK"));
        }
        
        public CompletableFuture<Response> getValue() {
            return future;
        }
    }
}