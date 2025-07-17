/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.managers;

import java.util.concurrent.CompletableFuture;

/**
 * Compatibility stub for ChannelFuture from the old UDS implementation.
 * This provides basic compatibility for tests that reference the old architecture.
 */
public class ChannelFuture {
    
    private final CompletableFuture<Void> future;
    
    /**
     * Create a new ChannelFuture.
     */
    public ChannelFuture() {
        this.future = new CompletableFuture<>();
    }
    
    /**
     * Create a ChannelFuture with a given CompletableFuture.
     */
    public ChannelFuture(CompletableFuture<Void> future) {
        this.future = future;
    }
    
    /**
     * Check if the future is done.
     */
    public boolean isDone() {
        return future.isDone();
    }
    
    /**
     * Check if the future was cancelled.
     */
    public boolean isCancelled() {
        return future.isCancelled();
    }
    
    /**
     * Check if the future completed successfully.
     */
    public boolean isSuccess() {
        return future.isDone() && !future.isCompletedExceptionally();
    }
    
    /**
     * Get the cause of failure if any.
     */
    public Throwable cause() {
        if (future.isCompletedExceptionally()) {
            try {
                future.get();
                return null;
            } catch (Exception e) {
                return e.getCause();
            }
        }
        return null;
    }
    
    /**
     * Cancel the future.
     */
    public boolean cancel() {
        return future.cancel(false);
    }
    
    /**
     * Add a listener to the future.
     */
    public ChannelFuture addListener(Object listener) {
        // No-op for compatibility
        return this;
    }
    
    /**
     * Await for the future to complete.
     */
    public ChannelFuture await() throws InterruptedException {
        try {
            future.get();
        } catch (Exception e) {
            // Convert to InterruptedException if needed
            if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            }
        }
        return this;
    }
    
    /**
     * Synchronously wait for completion.
     */
    public ChannelFuture sync() throws InterruptedException {
        return await();
    }
    
    /**
     * Get the underlying CompletableFuture.
     */
    public CompletableFuture<Void> getCompletableFuture() {
        return future;
    }
}