/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Represents the result of an asynchronous Redis command. Compatible with Lettuce's RedisFuture.
 * Extends {@link java.util.concurrent.CompletionStage} for composition and {@link Future} for
 * blocking wait. {@link java.util.concurrent.CompletableFuture} implements this interface.
 *
 * @param <V> the result type
 */
public interface RedisFuture<V> extends java.util.concurrent.CompletionStage<V>, Future<V> {

    /**
     * Wait for the result to become available (alias for {@link Future#get()}).
     *
     * @param timeout maximum time to wait
     * @param unit time unit
     * @return the result
     * @throws java.util.concurrent.ExecutionException if the computation threw an exception
     * @throws java.util.concurrent.TimeoutException if the wait timed out
     * @throws InterruptedException if the current thread was interrupted
     */
    default V await(long timeout, TimeUnit unit)
            throws java.util.concurrent.ExecutionException,
                    java.util.concurrent.TimeoutException,
                    InterruptedException {
        return get(timeout, unit);
    }

    /**
     * Wait for the result with no timeout (alias for {@link Future#get()}).
     *
     * @return the result
     * @throws java.util.concurrent.ExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted
     */
    default V await() throws java.util.concurrent.ExecutionException, InterruptedException {
        return get();
    }
}
