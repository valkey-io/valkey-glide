/** Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0 */
package glide.internal;

import glide.api.models.configuration.AwsCredentials;
import glide.api.models.configuration.GlideCredentialProvider;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Invokes a credentials provider method body without allowing blocked invocations to queue.
 *
 * <p>JNI owns one instance per native IAM callback. The explicit admission gate allows only one
 * provider body at a time, while the queue prevents executor handoff timing from rejecting an
 * admitted invocation. The single worker is a daemon so a provider that ignores interruption cannot
 * prevent JVM shutdown.
 */
final class CredentialsProviderInvoker implements AutoCloseable {
    private static final AtomicLong THREAD_NUMBER = new AtomicLong();
    private static final String BUSY_MESSAGE =
            "Credentials provider invocation rejected because a previous invocation is still busy; "
                    + "no invocation was queued";
    private static final String SHUTDOWN_MESSAGE =
            "Credentials provider invocation rejected because the invoker is shut down";

    private final GlideCredentialProvider provider;
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean invocationInFlight = new AtomicBoolean();
    private final AtomicReference<Admission> activeAdmission = new AtomicReference<>();

    CredentialsProviderInvoker(GlideCredentialProvider provider) {
        this(provider, CredentialsProviderInvoker::newDaemonThread);
    }

    CredentialsProviderInvoker(GlideCredentialProvider provider, ThreadFactory threadFactory) {
        this.provider = Objects.requireNonNull(provider, "provider");
        executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(1),
                        Objects.requireNonNull(threadFactory, "threadFactory"),
                        new ThreadPoolExecutor.AbortPolicy());
        executor.prestartCoreThread();
    }

    Future<CompletableFuture<AwsCredentials>> submit() {
        if (!invocationInFlight.compareAndSet(false, true)) {
            throw new RejectedExecutionException(BUSY_MESSAGE);
        }

        Admission admission = new Admission();
        activeAdmission.set(admission);
        InvocationTask task = new InvocationTask(provider, executor, admission);
        try {
            executor.execute(task);
            return task;
        } catch (RejectedExecutionException exception) {
            task.cancel(false);
            throw new RejectedExecutionException(SHUTDOWN_MESSAGE, exception);
        }
    }

    boolean isShutdown() {
        return executor.isShutdown();
    }

    boolean hasInvocationInFlight() {
        return invocationInFlight.get();
    }

    int queuedInvocationCount() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        List<Runnable> droppedTasks = executor.shutdownNow();
        for (Runnable task : droppedTasks) {
            ((Future<?>) task).cancel(false);
        }
        Admission admission = activeAdmission.get();
        if (admission != null) {
            admission.release();
        }
    }

    private static Thread newDaemonThread(Runnable task) {
        Thread thread =
                new Thread(task, "glide-iam-credentials-provider-" + THREAD_NUMBER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private final class Admission {
        private final AtomicBoolean released = new AtomicBoolean();

        private void release() {
            if (released.compareAndSet(false, true)) {
                activeAdmission.compareAndSet(this, null);
                invocationInFlight.set(false);
            }
        }
    }

    private static final class InvocationTask extends FutureTask<CompletableFuture<AwsCredentials>> {
        private final ThreadPoolExecutor executor;
        private final Admission admission;
        private final AtomicBoolean executionStarted = new AtomicBoolean();

        private InvocationTask(
                GlideCredentialProvider provider, ThreadPoolExecutor executor, Admission admission) {
            super(
                    () -> {
                        try {
                            return provider.getCredentials();
                        } finally {
                            admission.release();
                        }
                    });
            this.executor = executor;
            this.admission = admission;
        }

        @Override
        public void run() {
            executionStarted.set(true);
            try {
                super.run();
            } finally {
                admission.release();
            }
        }

        @Override
        protected void done() {
            if (!executionStarted.get()) {
                executor.remove(this);
                admission.release();
            }
        }
    }
}
