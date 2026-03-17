/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package io.lettuce.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Wraps a {@link CompletableFuture} as a {@link RedisFuture} so GLIDE's async API can be exposed as
 * Lettuce-compatible RedisFuture.
 *
 * @param <V> the result type
 */
final class CompletableFutureAdapter<V> implements RedisFuture<V> {

    private final CompletableFuture<V> future;

    CompletableFutureAdapter(CompletableFuture<V> future) {
        this.future = future;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public <U> RedisFuture<U> thenApply(Function<? super V, ? extends U> fn) {
        return new CompletableFutureAdapter<>(future.thenApply(fn));
    }

    @Override
    public <U> RedisFuture<U> thenApplyAsync(Function<? super V, ? extends U> fn) {
        return new CompletableFutureAdapter<>(future.thenApplyAsync(fn));
    }

    @Override
    public <U> RedisFuture<U> thenApplyAsync(
            Function<? super V, ? extends U> fn, java.util.concurrent.Executor executor) {
        return new CompletableFutureAdapter<>(future.thenApplyAsync(fn, executor));
    }

    @Override
    public RedisFuture<Void> thenAccept(Consumer<? super V> action) {
        return new CompletableFutureAdapter<>(future.thenAccept(action));
    }

    @Override
    public RedisFuture<Void> thenAcceptAsync(Consumer<? super V> action) {
        return new CompletableFutureAdapter<>(future.thenAcceptAsync(action));
    }

    @Override
    public RedisFuture<Void> thenAcceptAsync(
            Consumer<? super V> action, java.util.concurrent.Executor executor) {
        return new CompletableFutureAdapter<>(future.thenAcceptAsync(action, executor));
    }

    @Override
    public <U, W> RedisFuture<W> thenCombine(
            java.util.concurrent.CompletionStage<? extends U> other,
            BiFunction<? super V, ? super U, ? extends W> fn) {
        return new CompletableFutureAdapter<>(future.thenCombine(other, fn));
    }

    @Override
    public <U, W> RedisFuture<W> thenCombineAsync(
            java.util.concurrent.CompletionStage<? extends U> other,
            BiFunction<? super V, ? super U, ? extends W> fn) {
        return new CompletableFutureAdapter<>(future.thenCombineAsync(other, fn));
    }

    @Override
    public <U, W> RedisFuture<W> thenCombineAsync(
            java.util.concurrent.CompletionStage<? extends U> other,
            BiFunction<? super V, ? super U, ? extends W> fn,
            Executor executor) {
        return new CompletableFutureAdapter<>(future.thenCombineAsync(other, fn, executor));
    }

    @Override
    public <U> RedisFuture<Void> thenAcceptBoth(
            java.util.concurrent.CompletionStage<? extends U> other,
            BiConsumer<? super V, ? super U> action) {
        return new CompletableFutureAdapter<>(future.thenAcceptBoth(other, action));
    }

    @Override
    public <U> RedisFuture<Void> thenAcceptBothAsync(
            java.util.concurrent.CompletionStage<? extends U> other,
            BiConsumer<? super V, ? super U> action) {
        return new CompletableFutureAdapter<>(future.thenAcceptBothAsync(other, action));
    }

    @Override
    public <U> RedisFuture<Void> thenAcceptBothAsync(
            java.util.concurrent.CompletionStage<? extends U> other,
            BiConsumer<? super V, ? super U> action,
            Executor executor) {
        return new CompletableFutureAdapter<>(future.thenAcceptBothAsync(other, action, executor));
    }

    @Override
    public <U> RedisFuture<U> thenCompose(
            Function<? super V, ? extends java.util.concurrent.CompletionStage<U>> fn) {
        return new CompletableFutureAdapter<>(future.thenCompose(fn));
    }

    @Override
    public <U> RedisFuture<U> thenComposeAsync(
            Function<? super V, ? extends java.util.concurrent.CompletionStage<U>> fn) {
        return new CompletableFutureAdapter<>(future.thenComposeAsync(fn));
    }

    @Override
    public <U> RedisFuture<U> thenComposeAsync(
            Function<? super V, ? extends java.util.concurrent.CompletionStage<U>> fn,
            java.util.concurrent.Executor executor) {
        return new CompletableFutureAdapter<>(future.thenComposeAsync(fn, executor));
    }

    @Override
    public RedisFuture<V> whenComplete(BiConsumer<? super V, ? super Throwable> action) {
        return new CompletableFutureAdapter<>(future.whenComplete(action));
    }

    @Override
    public RedisFuture<V> whenCompleteAsync(BiConsumer<? super V, ? super Throwable> action) {
        return new CompletableFutureAdapter<>(future.whenCompleteAsync(action));
    }

    @Override
    public RedisFuture<V> whenCompleteAsync(
            BiConsumer<? super V, ? super Throwable> action, java.util.concurrent.Executor executor) {
        return new CompletableFutureAdapter<>(future.whenCompleteAsync(action, executor));
    }

    @Override
    public <U> RedisFuture<U> handle(BiFunction<? super V, Throwable, ? extends U> fn) {
        return new CompletableFutureAdapter<>(future.handle(fn));
    }

    @Override
    public <U> RedisFuture<U> handleAsync(BiFunction<? super V, Throwable, ? extends U> fn) {
        return new CompletableFutureAdapter<>(future.handleAsync(fn));
    }

    @Override
    public <U> RedisFuture<U> handleAsync(
            BiFunction<? super V, Throwable, ? extends U> fn, Executor executor) {
        return new CompletableFutureAdapter<>(future.handleAsync(fn, executor));
    }

    @Override
    public RedisFuture<V> exceptionally(Function<Throwable, ? extends V> fn) {
        return new CompletableFutureAdapter<>(future.exceptionally(fn));
    }

    @Override
    public RedisFuture<Void> thenRun(Runnable action) {
        return new CompletableFutureAdapter<>(future.thenRun(action));
    }

    @Override
    public RedisFuture<Void> thenRunAsync(Runnable action) {
        return new CompletableFutureAdapter<>(future.thenRunAsync(action));
    }

    @Override
    public RedisFuture<Void> thenRunAsync(Runnable action, Executor executor) {
        return new CompletableFutureAdapter<>(future.thenRunAsync(action, executor));
    }

    @Override
    public RedisFuture<Void> runAfterBoth(
            java.util.concurrent.CompletionStage<?> other, Runnable action) {
        return new CompletableFutureAdapter<>(future.runAfterBoth(other, action));
    }

    @Override
    public RedisFuture<Void> runAfterBothAsync(
            java.util.concurrent.CompletionStage<?> other, Runnable action) {
        return new CompletableFutureAdapter<>(future.runAfterBothAsync(other, action));
    }

    @Override
    public RedisFuture<Void> runAfterBothAsync(
            java.util.concurrent.CompletionStage<?> other, Runnable action, Executor executor) {
        return new CompletableFutureAdapter<>(future.runAfterBothAsync(other, action, executor));
    }

    @Override
    public RedisFuture<Void> runAfterEither(
            java.util.concurrent.CompletionStage<?> other, Runnable action) {
        return new CompletableFutureAdapter<>(future.runAfterEither(other, action));
    }

    @Override
    public RedisFuture<Void> runAfterEitherAsync(
            java.util.concurrent.CompletionStage<?> other, Runnable action) {
        return new CompletableFutureAdapter<>(future.runAfterEitherAsync(other, action));
    }

    @Override
    public RedisFuture<Void> runAfterEitherAsync(
            java.util.concurrent.CompletionStage<?> other, Runnable action, Executor executor) {
        return new CompletableFutureAdapter<>(future.runAfterEitherAsync(other, action, executor));
    }

    @Override
    public <U> RedisFuture<U> applyToEither(
            java.util.concurrent.CompletionStage<? extends V> other, Function<? super V, U> fn) {
        return new CompletableFutureAdapter<>(future.applyToEither(other, fn));
    }

    @Override
    public <U> RedisFuture<U> applyToEitherAsync(
            java.util.concurrent.CompletionStage<? extends V> other, Function<? super V, U> fn) {
        return new CompletableFutureAdapter<>(future.applyToEitherAsync(other, fn));
    }

    @Override
    public <U> RedisFuture<U> applyToEitherAsync(
            java.util.concurrent.CompletionStage<? extends V> other,
            Function<? super V, U> fn,
            Executor executor) {
        return new CompletableFutureAdapter<>(future.applyToEitherAsync(other, fn, executor));
    }

    @Override
    public RedisFuture<Void> acceptEither(
            java.util.concurrent.CompletionStage<? extends V> other, Consumer<? super V> action) {
        return new CompletableFutureAdapter<>(future.acceptEither(other, action));
    }

    @Override
    public RedisFuture<Void> acceptEitherAsync(
            java.util.concurrent.CompletionStage<? extends V> other, Consumer<? super V> action) {
        return new CompletableFutureAdapter<>(future.acceptEitherAsync(other, action));
    }

    @Override
    public RedisFuture<Void> acceptEitherAsync(
            java.util.concurrent.CompletionStage<? extends V> other,
            Consumer<? super V> action,
            Executor executor) {
        return new CompletableFutureAdapter<>(future.acceptEitherAsync(other, action, executor));
    }

    @Override
    public CompletableFuture<V> toCompletableFuture() {
        return future;
    }

    /**
     * Return the underlying CompletableFuture for interoperability.
     *
     * @return the CompletableFuture
     */
    CompletableFuture<V> unwrap() {
        return future;
    }
}
