/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.connectors.handlers;

import glide.api.models.PubSubMessage;
import glide.managers.BaseResponseResolver;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Stub MessageHandler implementation for JNI-based clients.
 * This is a minimal implementation to prevent crashes until full PubSub support is implemented.
 */
public class MessageHandler {
    
    private final PubSubMessageQueue messageQueue;
    private final Optional<Object> callback;
    private final Optional<Object> context;
    
    public MessageHandler(Optional<Object> callback, Optional<Object> context, BaseResponseResolver resolver) {
        this.callback = callback;
        this.context = context;
        this.messageQueue = new PubSubMessageQueue();
    }
    
    public PubSubMessageQueue getQueue() {
        return messageQueue;
    }
    
    /**
     * PubSub message queue implementation.
     * This is a stub implementation for now.
     */
    public static class PubSubMessageQueue {
        private final LinkedBlockingQueue<PubSubMessage> queue;
        
        public PubSubMessageQueue() {
            this.queue = new LinkedBlockingQueue<>();
        }
        
        public PubSubMessage popSync() {
            return queue.poll(); // Non-blocking poll, returns null if empty
        }
        
        public CompletableFuture<PubSubMessage> popAsync() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return queue.take(); // Blocking take
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            });
        }
        
        public void push(PubSubMessage message) {
            queue.offer(message);
        }
    }
    
    /**
     * Push kinds for PubSub messages.
     */
    public enum PushKind {
        Message,
        PMessage, 
        SMessage
    }
    
    /**
     * Exception for message callback errors.
     */
    public static class MessageCallbackException extends RuntimeException {
        public MessageCallbackException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
