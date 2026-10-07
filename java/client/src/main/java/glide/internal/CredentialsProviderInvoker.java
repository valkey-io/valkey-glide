/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.models.configuration.AwsCredentials;
import glide.api.models.configuration.GlideCredentialProvider;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Invokes a credentials provider method body without allowing blocked invocations to queue.
 *
 * <p>JNI owns one instance per native IAM callback. The single worker is a daemon so a provider
 * that ignores interruption cannot prevent JVM shutdown.
 */
final class CredentialsProviderInvoker implements AutoCloseable {
    private static final AtomicLong THREAD_NUMBER = new AtomicLong();

    private final GlideCredentialProvider provider;
    private final ThreadPoolExecutor executor;

    CredentialsProviderInvoker(GlideCredentialProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
        ThreadFactory threadFactory =
                task -> {
                    Thread thread =
                            new Thread(task, "glide-iam-credentials-provider-" + THREAD_NUMBER.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                };
        executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new SynchronousQueue<>(),
                        threadFactory,
                        new ThreadPoolExecutor.AbortPolicy());
    }

    Future<CompletableFuture<AwsCredentials>> submit() {
        return executor.submit(provider::getCredentials);
    }

    boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
